-module(data_modelling_bridge).
-behaviour(gen_server).
-export([start_link/0]).
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-record(state, {
    schema_cache :: map(),
    validation_queue :: queue:queue(),
    stats :: map()
}).

-record(capability_registry, {
    id :: binary(),
    name :: atom(),
    type :: string(),
    status :: string(),
    metadata :: map(),
    created :: integer(),
    updated :: integer()
}).

start_link() ->
    gen_server:start_link({local, data_modelling_bridge}, ?MODULE, [], []).

init([]) ->
    process_flag(trap_exit, true),

    %% Initialize state
    State = #state{
        schema_cache = #{},
        validation_queue = queue:new(),
        stats = #{
            schema_versions => 0,
            validations => 0,
            errors => 0
        }
    },

    %% Initialize schema cache from Mnesia
    load_initial_schemas(),

    %% Start periodic schema sync
    erlang:send_after(60000, self(), sync_schemas),

    {ok, State}.

handle_call(get_schema, {Pid, _Ref}, State) ->
    SchemaType = case Processed = erlang:process_info(Pid, current_function) of
        undefined -> unknown;
        {current_function, {_, _, _}} -> erlang:atom_to_binary(element(2, Processed), utf8)
    end,

    %% Find schema for the type
    Schema = case maps:get(SchemaType, State#state.schema_cache, undefined) of
        undefined -> {error, schema_not_found};
        SchemaDef -> {ok, SchemaDef}
    end,

    {reply, Schema, State};

handle_call({validate_data, {Pid, _Ref}, Data}, _From, State) ->
    %% Add validation to queue
    ValidationId = erlang:ref_to_list(make_ref()),
    Validation = #{
        id => ValidationId,
        data => Data,
        validator => Pid,
        timestamp => erlang:system_time(millisecond)
    },

    NewQueue = queue:in(Validation, State#state.validation_queue),

    %% Update stats
    NewStats = maps:update(
        validations,
        maps:get(validations, State#state.stats) + 1,
        State#state.stats
    ),

    %% Process validation immediately if queue is small
    case queue:len(NewQueue) < 10 of
        true ->
            case process_validation(Validation) of
                {ok, Result} ->
                    gen_server:reply(Pid, {ok, Result}),
                    {noreply, State#state{validation_queue = queue:out(NewQueue), stats = NewStats}};
                {error, Reason} ->
                    gen_server:reply(Pid, {error, Reason}),
                    {noreply, State#state{validation_queue = queue:out(NewQueue), stats = NewStats}}
            end;
        false ->
            %% Process asynchronously
            erlang:send_after(100, self(), process_validation),
            {reply, {queued, ValidationId}, State#state{validation_queue = NewQueue, stats = NewStats}}
    end;

handle_call(get_stats, _From, State) ->
    {reply, {ok, State#state.stats}, State};

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_call}, State}.

handle_cast({update_schema, SchemaType, SchemaDef}, State) ->
    %% Update schema in cache
    NewCache = maps:put(SchemaType, SchemaDef, State#state.schema_cache),

    %% Update stats
    NewStats = maps:update(
        schema_versions,
        maps:get(schema_versions, State#state.stats) + 1,
        State#state.stats
    ),

    %% Store schema in Mnesia
    store_schema(SchemaType, SchemaDef),

    {noreply, State#state{schema_cache = NewCache, stats = NewStats}};

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info(process_validation, State) ->
    %% Process one validation from queue
    case queue:out(State#state.validation_queue) of
        {{value, Validation}, RestQueue} ->
            case process_validation(Validation) of
                {ok, Result} ->
                    %% Notify validator
                    gen_server:cast(Validation#{validator => validator}, {validation_result, Validation#{id => id}, Result}),
                    {noreply, State#state{validation_queue = RestQueue}};
                {error, Reason} ->
                    %% Notify validator of error
                    gen_server:cast(Validation#{validator => validator}, {validation_error, Validation#{id => id}, Reason}),
                    {noreply, State#state{validation_queue = RestQueue}}
            end;
        {empty, _} ->
            {noreply, State}
    end;

handle_info(sync_schemas, State) ->
    %% Sync schemas with external systems
    case sync_external_schemas() of
        ok ->
            %% Continue periodic sync
            erlang:send_after(60000, self(), sync_schemas),
            {noreply, State};
        {error, Reason} ->
            %% Retry sooner if sync fails
            erlang:send_after(30000, self(), sync_schemas),
            lager:warning("Schema sync failed: ~p", [Reason]),
            {noreply, State}
    end;

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    %% Cleanup resources
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

load_initial_schemas() ->
    %% Load existing schemas from Mnesia
    mnesia:transaction(fun() ->
        mnesia:foldl(fun(Schema, Acc) ->
            SchemaType = Schema#capability_registry.id,
            maps:put(SchemaType, Schema#capability_registry.metadata, Acc)
        end, #{}, capability_registry)
    end).

store_schema(SchemaType, SchemaDef) ->
    mnesia:transaction(fun() ->
        mnesia:write(#capability_registry{
            id = SchemaType,
            name = binary_to_atom(SchemaType, utf8),
            type = "schema",
            status = "active",
            metadata = SchemaDef
        })
    end).

process_validation(Validation) ->
    try
        Data = maps:get(data, Validation),
        SchemaType = maps:get(type, Data, generic),

        %% Find validation rules
        ValidationRules = get_validation_rules(SchemaType),

        %% Execute validation
        case execute_validation(Data, ValidationRules) of
            {ok, _} = Result ->
                Result;
            {error, Reason} ->
                lager:error("Validation failed: ~p", [Reason]),
                {error, Reason}
        end
    catch
        Error ->
            {error, {validation_failed, Error}}
    end.

get_validation_rules(SchemaType) ->
    %% Return validation rules based on schema type
    case SchemaType of
        "case" -> get_case_validation_rules();
        "workflow" -> get_workflow_validation_rules();
        "resource" -> get_resource_validation_rules();
        _ -> get_generic_validation_rules()
    end.

get_case_validation_rules() ->
    %% Define case-specific validation rules
    [
        {required, [<<"case_id">>, <<"workflow_id">>]},
        {types, [
            {<<"case_id">>, binary},
            {<<"workflow_id">>, binary},
            {<<"status">>, binary},
            {<<"data">>, map}
        ]},
        {enum_status, [<<"created">>, <<"running">>, <<"completed">>, <<"cancelled">>]}
    ].

get_workflow_validation_rules() ->
    %% Define workflow-specific validation rules
    [
        {required, [<<"id">>, <<"name">>]},
        {types, [
            {<<"id">>, binary},
            {<<"name">>, binary},
            {<<"version">>, integer},
            {<<"definition">>, map}
        ]}
    ].

get_resource_validation_rules() ->
    %% Define resource-specific validation rules
    [
        {required, [<<"id">>, <<"type">>, <<"status">>]},
        {types, [
            {<<"id">>, binary},
            {<<"type">>, binary},
            {<<"status">>, binary},
            {<<"load">>, integer}
        ]}
    ].

get_generic_validation_rules() ->
    %% Default validation rules
    [
        {required, []},
        {types, []}
    ].

execute_validation(Data, Rules) ->
    %% Check required fields
    RequiredFields = proplists:get_value(required, Rules, []),
    lists:foreach(fun(Field) ->
        case maps:get(Field, Data, undefined) of
            undefined -> throw({missing_field, Field});
            _ -> ok
        end
    end, RequiredFields),

    %% Check field types
    TypeRules = proplists:get_value(types, Rules, []),
    lists:foreach(fun({Field, ExpectedType}) ->
        case maps:get(Field, Data, undefined) of
            undefined -> ok;
            Value when ExpectedType == binary ->
                case is_binary(Value) of
                    true -> ok;
                    false -> throw({invalid_type, Field, binary, Value})
                end;
            Value when ExpectedType == integer ->
                case is_integer(Value) of
                    true -> ok;
                    false -> throw({invalid_type, Field, integer, Value})
                end;
            Value when ExpectedType == map ->
                case is_map(Value) of
                    true -> ok;
                    false -> throw({invalid_type, Field, map, Value})
                end
        end
    end, TypeRules),

    {ok, valid}.

sync_external_schemas() ->
    %% External schema synchronization requires:
    %% 1. Schema registry endpoint configuration
    %% 2. Authentication credentials
    %% 3. Schema version negotiation protocol
    %% Implement when integrating with external schema services.
    throw({unsupported_operation, <<"sync_external_schemas requires external schema registry configuration. Implement when integrating with external services.">>}).