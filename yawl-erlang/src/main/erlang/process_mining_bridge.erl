%% @copyright 2026 YAWL Foundation
%% @author YAWL Erlang Team
%% @doc Process mining bridge gen_server for NIF integration

-module(process_mining_bridge).
-behaviour(gen_server).
-include("process_mining_bridge.hrl").

%%====================================================================
%% Exports
%%====================================================================

%% API
-export([
    start_link/0,
    start_link/1,
    stop/0,
    import_ocel_json_path/1,
    slim_link_ocel/1,
    discover_oc_declare/1,
    token_replay/2
]).

%% gen_server callbacks
-export([
    init/1,
    handle_call/3,
    handle_cast/2,
    handle_info/2,
    terminate/2,
    code_change/3
]).

%%====================================================================
%% Records
%%====================================================================

-type operation_id() :: binary().

-record(state, {
    %% Process mining registry (ETS table name)
    registry :: atom(),

    %% Configuration
    config :: map(),

    %% Statistics
    stats :: map(),

    %% Process registry for active operations
    active_ops :: map()
}).

%%====================================================================
%% API
%%====================================================================

%% @doc Start the process mining bridge server with default config
-spec start_link() -> {ok, pid()} | {error, any()}.
start_link() ->
    start_link(#{}).

%% @doc Start the process mining bridge server with custom config
-spec start_link(config()) -> {ok, pid()} | {error, any()}.
start_link(Config) when is_map(Config) ->
    gen_server:start_link({local, ?SERVER}, ?MODULE, Config, []).

%% @doc Stop the process mining bridge server
-spec stop() -> ok.
stop() ->
    gen_server:call(?SERVER, stop).

%% @doc Import OCEL JSON from file path
-spec import_ocel_json_path(file:name_all()) -> import_ocel_result().
import_ocel_json_path(File) ->
    call_import(#import_ocel_config{
        file_path = File,
        event_key = "event",
        lifecycle_key = "lifecycle",
        object_types = ["activity", "object"],
        attributes = [],
        timestamp_format = "RFC3339"
    }).

%% @doc Slim link OCEL using specified strategy
-spec slim_link_ocel(atom()) -> slim_link_result().
slim_link_ocel(Table) ->
    call_slim_link(#slim_link_config{
        target_table = Table,
        strategy = direct,
        similarity_threshold = ?DEFAULT_SIMILARITY_THRESHOLD,
        max_iterations = 100
    }).

%% @doc Discover DECLARE process model from OCEL
-spec discover_oc_declare(atom()) -> discover_result().
discover_oc_declare(Table) ->
    call_discover(#discover_config{
        algorithm = declare,
        threshold = 0.9,
        sample_size = 1000,
        max_duration = 30000
    }).

%% @doc Token replay on OCEL table
-spec token_replay(atom(), map()) -> replay_result().
token_replay(Table, ConfigMap) ->
    ReplayConfig = parse_replay_config(ConfigMap),
    call_replay(Table, ReplayConfig).

%%====================================================================
%% gen_server Callbacks
%%====================================================================

-define(SERVER, ?MODULE).

%%====================================================================
%% gen_server callbacks
%%====================================================================

init(Config) ->
    process_flag(trap_exit, true),

    %% Create ETS table for operation registry
    RegistryName = maps:get(registry, Config, ?DEFAULT_REGISTRY),
    ets:new(RegistryName, [set, public, named_table, {write_concurrency, true}]),

    %% Initialize state
    State = #state{
        registry = RegistryName,
        config = normalize_config(Config),
        stats = init_stats(),
        active_ops = #{}
    },

    %% Load NIF module
    case load_nif() of
        ok ->
            {ok, State};
        {error, Reason} ->
            {stop, Reason}
    end.

handle_call({import_ocel, Config = #import_ocel_config{}}, From, State) ->
    %% Generate operation ID
    OpId = generate_operation_id(),

    %% Store operation in registry
    OpInfo = #{
        id => OpId,
        type => import,
        config => Config,
        from => From,
        start_time => erlang:system_time(millisecond),
        status => running
    },
    ets:insert(State#state.registry, {OpId, OpInfo}),
    State1 = store_operation(State, OpId, OpInfo),

    %% Call NIF (or return error if not loaded)
    case nif_available() of
        true ->
            %% Execute NIF operation
            try rust4pm:import_ocel_json_path(Config#import_ocel_config.file_path) of
                Result ->
                    handle_import_result(OpId, Result, State1)
            catch
                Class:Error ->
                    handle_import_error(OpId, {Class, Error}, State1)
            end;
        false ->
            %% NIF not loaded, return error immediately
            Response = ?ERR_NIF_NOT_LOADED,
            gen_server:reply(From, Response),
            OpInfo1 = maps:update(status, failed, OpInfo),
            State2 = update_operation(State, OpId, OpInfo1),
            {noreply, State2}
    end;

handle_call({slim_link, Config = #slim_link_config{}}, From, State) ->
    OpId = generate_operation_id(),

    OpInfo = #{
        id => OpId,
        type => slim_link,
        config => Config,
        from => From,
        start_time => erlang:system_time(millisecond),
        status => running
    },
    ets:insert(State#state.registry, {OpId, OpInfo}),
    State1 = store_operation(State, OpId, OpInfo),

    case nif_available() of
        true ->
            try rust4pm:slim_link_ocel(Config#slim_link_config.target_table) of
                Result ->
                    handle_slim_link_result(OpId, Result, State1)
            catch
                Class:Error ->
                    handle_slim_link_error(OpId, {Class, Error}, State1)
            end;
        false ->
            Response = ?ERR_NIF_NOT_LOADED,
            gen_server:reply(From, Response),
            OpInfo1 = maps:update(status, failed, OpInfo),
            State2 = update_operation(State, OpId, OpInfo1),
            {noreply, State2}
    end;

handle_call({discover, Config = #discover_config{}}, From, State) ->
    OpId = generate_operation_id(),

    OpInfo = #{
        id => OpId,
        type => discover,
        config => Config,
        from => From,
        start_time => erlang:system_time(millisecond),
        status => running
    },
    ets:insert(State#state.registry, {OpId, OpInfo}),
    State1 = store_operation(State, OpId, OpInfo),

    case nif_available() of
        true ->
            try rust4pm:discover_oc_declare(Config#discover_config.table) of
                Result ->
                    handle_discover_result(OpId, Result, State1)
            catch
                Class:Error ->
                    handle_discover_error(OpId, {Class, Error}, State1)
            end;
        false ->
            Response = ?ERR_NIF_NOT_LOADED,
            gen_server:reply(From, Response),
            OpInfo1 = maps:update(status, failed, OpInfo),
            State2 = update_operation(State, OpId, OpInfo1),
            {noreply, State2}
    end;

handle_call({replay, Table, Config = #replay_config{}}, From, State) ->
    OpId = generate_operation_id(),

    OpInfo = #{
        id => OpId,
        type => replay,
        config => Config,
        from => From,
        start_time => erlang:system_time(millisecond),
        status => running
    },
    ets:insert(State#state.registry, {OpId, OpInfo}),
    State1 = store_operation(State, OpId, OpInfo),

    case nif_available() of
        true ->
            try rust4pm:token_replay(Table, Config) of
                Result ->
                    handle_replay_result(OpId, Result, State1)
            catch
                Class:Error ->
                    handle_replay_error(OpId, {Class, Error}, State1)
            end;
        false ->
            Response = ?ERR_NIF_NOT_LOADED,
            gen_server:reply(From, Response),
            OpInfo1 = maps:update(status, failed, OpInfo),
            State2 = update_operation(State, OpId, OpInfo1),
            {noreply, State2}
    end;

handle_call(stop, _From, State) ->
    %% Cancel all active operations
    ActiveOps = State#state.active_ops,
    lists:foreach(fun(OpId) ->
        cancel_operation(OpId, State)
    end, maps:keys(ActiveOps)),

    %% Shutdown
    {stop, normal, ok, State};

handle_call({get_operation, OpId}, _From, State) ->
    case ets:lookup(State#state.registry, OpId) of
        [{OpId, OpInfo}] ->
            {reply, {ok, OpInfo}, State};
        [] ->
            {reply, {error, not_found}, State}
    end;

handle_call(get_stats, _From, State) ->
    {reply, {ok, State#state.stats}, State};

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_call}, State}.

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info({'DOWN', _Ref, process, _Pid, _Reason}, State) ->
    %% Handle process termination
    {noreply, State};

handle_info({operation_complete, OpId, Result}, State) ->
    %% Handle async operation completion
    case ets:lookup(State#state.registry, OpId) of
        [{OpId, OpInfo}] ->
            gen_server:reply(OpInfo#{from}, Result),
            OpInfo1 = maps:update(status, completed, OpInfo),
            State1 = update_operation(State, OpId, OpInfo1),
            %% Update statistics
            State2 = update_stats(State1, OpInfo1),
            {noreply, State2};
        [] ->
            {noreply, State}
    end;

handle_info({operation_error, OpId, Error}, State) ->
    case ets:lookup(State#state.registry, OpId) of
        [{OpId, OpInfo}] ->
            gen_server:reply(OpInfo#{from}, Error),
            OpInfo1 = maps:update(status, failed, OpInfo),
            State1 = update_operation(State, OpId, OpInfo1),
            State2 = update_stats(State1, OpInfo1),
            {noreply, State2};
        [] ->
            {noreply, State}
    end;

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, State) ->
    %% Clean up ETS table
    ets:delete(State#state.registry),
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

%%====================================================================
%% Internal Functions
%%====================================================================

%% @private Generate unique operation ID
-spec generate_operation_id() -> binary().
generate_operation_id() ->
    iolist_to_binary(io_lib:format("~p", [erlang:unique_integer([positive])])).

%% @private Check if NIF is available
-spec nif_available() -> boolean().
nif_available() ->
    case erlang:function_exported(rust4pm, import_ocel_json_path, 1) of
        true -> true;
        false -> false
    end.

%% @private Load NIF module
-spec load_nif() -> ok | {error, any()}.
load_nif() ->
    case code:ensure_loaded(rust4pm) of
        {module, rust4pm} ->
            ok;
        {error, not_loaded} ->
            case erlang:function_exported(rust4pm, load_nif, 0) of
                true ->
                    try rust4pm:load_nif() of
                        ok -> ok;
                        Error -> {error, Error}
                    catch
                        Class:Error -> {error, {Class, Error}}
                    end;
                false ->
                    ok  %% NIF will be loaded automatically
            end
    end.

%% @private Parse configuration map into record
-spec parse_replay_config(map()) -> #replay_config{}.
parse_replay_config(ConfigMap) when is_map(ConfigMap) ->
    #replay_config{
        alignment_mode = maps:get(alignment_mode, ConfigMap, optimal),
        fitness_threshold = maps:get(fitness_threshold, ConfigMap, ?DEFAULT_FITNESS_THRESHOLD),
        precision_threshold = maps:get(precision_threshold, ConfigMap, ?DEFAULT_PRECISION_THRESHOLD),
        cost_threshold = maps:get(cost_threshold, ConfigMap, ?DEFAULT_COST_THRESHOLD)
    }.

%% @private Handle import operation result
-spec handle_import_result(operation_id(), any(), state()) -> {noreply, state()}.
handle_import_result(OpId, Result, State) ->
    case Result of
        ok ->
            Response = ok,
            notify_operation_complete(OpId, Response);
        {ok, Data} ->
            Response = {ok, Data},
            notify_operation_complete(OpId, Response);
        Error ->
            notify_operation_error(OpId, Error)
    end,

    %% Clean up operation
    OpInfo = maps:remove(from, maps:get(OpId, State#state.active_ops)),
    State1 = update_operation(State, OpId, OpInfo),
    {noreply, State1}.

%% @private Handle import operation error
-spec handle_import_error(operation_id(), any(), state()) -> {noreply, state()}.
handle_import_error(OpId, Error, State) ->
    notify_operation_error(OpId, Error),

    OpInfo = maps:get(OpId, State#state.active_ops),
    OpInfo1 = maps:update(status, failed, OpInfo),
    State1 = update_operation(State, OpId, OpInfo1),
    {noreply, State1}.

%% @private Handle slim link operation result
-spec handle_slim_link_result(operation_id(), any(), state()) -> {noreply, state()}.
handle_slim_link_result(OpId, Result, State) ->
    case Result of
        {ok, Similarity} when is_float(Similarity) ->
            Response = {ok, Similarity},
            notify_operation_complete(OpId, Response);
        Error ->
            notify_operation_error(OpId, Error)
    end,

    OpInfo = maps:get(OpId, State#state.active_ops),
    State1 = update_operation(State, OpId, OpInfo),
    {noreply, State1}.

%% @private Handle slim link operation error
-spec handle_slim_link_error(operation_id(), any(), state()) -> {noreply, state()}.
handle_slim_link_error(OpId, Error, State) ->
    notify_operation_error(OpId, Error),

    OpInfo = maps:get(OpId, State#state.active_ops),
    OpInfo1 = maps:update(status, failed, OpInfo),
    State1 = update_operation(State, OpId, OpInfo1),
    {noreply, State1}.

%% @private Handle discover operation result
-spec handle_discover_result(operation_id(), any(), state()) -> {noreply, state()}.
handle_discover_result(OpId, Result, State) ->
    case Result of
        {ok, Patterns} when is_list(Patterns) ->
            Response = {ok, Patterns},
            notify_operation_complete(OpId, Response);
        Error ->
            notify_operation_error(OpId, Error)
    end,

    OpInfo = maps:get(OpId, State#state.active_ops),
    State1 = update_operation(State, OpId, OpInfo),
    {noreply, State1}.

%% @private Handle discover operation error
-spec handle_discover_error(operation_id(), any(), state()) -> {noreply, state()}.
handle_discover_error(OpId, Error, State) ->
    notify_operation_error(OpId, Error),

    OpInfo = maps:get(OpId, State#state.active_ops),
    OpInfo1 = maps:update(status, failed, OpInfo),
    State1 = update_operation(State, OpId, OpInfo1),
    {noreply, State1}.

%% @private Handle replay operation result
-spec handle_replay_result(operation_id(), any(), state()) -> {noreply, state()}.
handle_replay_result(OpId, Result, State) ->
    case Result of
        {ok, Report} when is_map(Report) ->
            Response = {ok, Report},
            notify_operation_complete(OpId, Response);
        Error ->
            notify_operation_error(OpId, Error)
    end,

    OpInfo = maps:get(OpId, State#state.active_ops),
    State1 = update_operation(State, OpId, OpInfo),
    {noreply, State1}.

%% @private Handle replay operation error
-spec handle_replay_error(operation_id(), any(), state()) -> {noreply, state()}.
handle_replay_error(OpId, Error, State) ->
    notify_operation_error(OpId, Error),

    OpInfo = maps:get(OpId, State#state.active_ops),
    OpInfo1 = maps:update(status, failed, OpInfo),
    State2 = update_operation(State, OpId, OpInfo1),
    {noreply, State2}.

%% @private Notify operation completion
-spec notify_operation_complete(operation_id(), any()) -> ok.
notify_operation_complete(OpId, Result) ->
    gen_server:cast(?SERVER, {notify_complete, OpId, Result}).

%% @private Notify operation error
-spec notify_operation_error(operation_id(), any()) -> ok.
notify_operation_error(OpId, Error) ->
    gen_server:cast(?SERVER, {notify_error, OpId, Error}).

%% @private Store operation in state
-spec store_operation(state(), operation_id(), map()) -> state().
store_operation(State = #state{active_ops = Ops}, OpId, OpInfo) ->
    State#state{active_ops = Ops#{OpId => OpInfo}}.

%% @private Update operation in state
-spec update_operation(state(), operation_id(), map()) -> state().
update_operation(State = #state{active_ops = Ops}, OpId, OpInfo) ->
    State#state{active_ops = Ops#{OpId => OpInfo}}.

%% @private Cancel operation
-spec cancel_operation(operation_id(), state()) -> ok.
cancel_operation(OpId, _State) ->
    %% Implementation would send cancel signal to NIF
    ok.

%% @private Initialize statistics
-spec init_stats() -> map().
init_stats() ->
    #{
        total_operations => 0,
        successful_operations => 0,
        failed_operations => 0,
        average_duration => 0
    }.

%% @private Update statistics
-spec update_stats(state(), map()) -> state().
update_stats(State, OpInfo) ->
    Stats = State#state.stats,
    StartTime = OpInfo#{start_time},
    Duration = erlang:system_time(millisecond) - StartTime,

    AvgDuration = case Stats#{total_operations} of
        0 -> Duration;
        N ->
            (Stats#{average_duration} * N + Duration) / (N + 1)
    end,

    NewStats = Stats#{
        total_operations => Stats#{total_operations} + 1,
        successful_operations => Stats#{successful_operations} + 1,
        failed_operations => Stats#{failed_operations} + (if OpInfo#{status} =:= failed -> 1; true -> 0 end),
        average_duration => AvgDuration
    },

    State#state{stats = NewStats}.

%% @private Normalize configuration
-spec normalize_config(map()) -> map().
normalize_config(Config) when is_map(Config) ->
    Defaults = #{
        registry => ?DEFAULT_REGISTRY,
        max_operations => 100,
        timeout => 30000  %% 30 seconds
    },
    maps:merge(Defaults, Config).

%% @private API wrapper for import
-spec call_import(#import_ocel_config{}) -> import_ocel_result().
call_import(Config) ->
    gen_server:call(?SERVER, {import_ocel, Config}).

%% @private API wrapper for slim link
-spec call_slim_link(#slim_link_config{}) -> slim_link_result().
call_slim_link(Config) ->
    gen_server:call(?SERVER, {slim_link, Config}).

%% @private API wrapper for discover
-spec call_discover(#discover_config{}) -> discover_result().
call_discover(Config) ->
    gen_server:call(?SERVER, {discover, Config}).

%% @private API wrapper for replay
-spec call_replay(atom(), #replay_config{}) -> replay_result().
call_replay(Table, Config) ->
    gen_server:call(?SERVER, {replay, Table, Config}).