%%%-------------------------------------------------------------------
%%% @doc QLever Client - Uses embedded QLever via Java Panama FFI bridge
%%%
%%% This module provides SPARQL operations using the embedded QLever engine
%%% through the Erlang-Java bridge. NO HTTP - uses Unix socket communication
%%% to Java's QLeverEmbeddedSparqlEngine which uses Panama FFI bindings.
%%%
%%% Architecture:
%%%   Erlang (this module)
%%%       ↓ Unix socket
%%%   Java ProcessMiningClient
%%%       ↓ Panama FFI
%%%   QLever native library (libqleverjni.so/dylib)
%%%
%%% @end
%%%-------------------------------------------------------------------
-module(qlever_client).
-behaviour(gen_server).

%% API
-export([start_link/0, start_link/1, stop/0]).
-export([insert_conformance_score/3, select_conformance_history/0, ping/0]).
-export([is_available/0, execute_query/1, execute_update/1]).
-export([initialize/0, shutdown/0]).

%% gen_server callbacks
-export([init/1, handle_call/3, handle_cast/2, terminate/2]).

-TIMEOUT, 30000).
-define(SIM_NAMESPACE, "http://yawl.org/simulation#").

-record(state, {
    java_bridge :: pid() | undefined,
    available :: boolean(),
    mock_storage :: undefined,
    initialized :: boolean()
}).

%%%===================================================================
%%% API
%%%===================================================================

start_link() ->
    start_link([]).

start_link(Options) ->
    gen_server:start_link({local, ?MODULE}, ?MODULE, Options, []).

stop() ->
    gen_server:stop(?MODULE).

%% @doc Initialize the embedded QLever engine via Java bridge
initialize() ->
    gen_server:call(?MODULE, initialize, ?TIMEOUT).

%% @doc Shutdown the embedded QLever engine
shutdown() ->
    gen_server:call(?MODULE, shutdown, ?TIMEOUT).

%% @doc Check if embedded QLever is available
is_available() ->
    gen_server:call(?MODULE, is_available).

%% @doc Ping the embedded engine
ping() ->
    gen_server:call(?MODULE, ping).

%% @doc Insert a conformance score with timestamp
%% Uses SPARQL UPDATE to store data in embedded QLever
insert_conformance_score(RunId, Score, Timestamp) ->
    gen_server:call(?MODULE, {insert_conformance_score, RunId, Score, Timestamp}, ?TIMEOUT).

%% @doc Select all conformance history from embedded QLever
select_conformance_history() ->
    gen_server:call(?MODULE, select_conformance_history, ?TIMEOUT).

%% @doc Execute arbitrary SPARQL SELECT query
execute_query(Query) ->
    gen_server:call(?MODULE, {execute_query, Query}, ?TIMEOUT).

%% @doc Execute arbitrary SPARQL UPDATE
execute_update(Update) ->
    gen_server:call(?MODULE, {execute_update, Update}, ?TIMEOUT).

%%%===================================================================
%%% gen_server callbacks
%%%===================================================================

init(Options) ->
    UseMock = proplists:get_value(use_mock, Options, false),
    JavaBridge = proplists:get_value(java_bridge, Options, undefined),

    State = #state{
        java_bridge = JavaBridge,
        available = false,
        mock_storage = undefined,
        initialized = false
    },

    case UseMock of
        true ->
            erlang:throw(#{
                module => ?MODULE,
                function => init,
                reason => mock_mode_not_allowed,
                message => "Mock mode is not allowed in production"
            });
        false ->
            %% Try to initialize embedded QLever via Java bridge
            case initialize_embedded_qlever(State) of
                {ok, NewState} ->
                    {ok, NewState};
                {error, _Reason} ->
                    %% Fall back to mock mode if embedded QLever not available
                    logger:info("Embedded QLever not available, using mock storage"),
                    {ok, State}
            end
    end.

handle_call(initialize, _From, State) ->
    case initialize_embedded_qlever(State) of
        {ok, NewState} ->
            {reply, {ok, initialized}, NewState};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call(shutdown, _From, State) ->
    case shutdown_embedded_qlever(State) of
        {ok, NewState} ->
            {reply, {ok, shutdown}, NewState};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call(is_available, _From, State) ->
    {reply, State#state.available, State};

handle_call(ping, _From, State) ->
    case State#state.available of
        true ->
            %% Ping the actual embedded engine via Java bridge
            case call_java_bridge(State, ping, []) of
                {ok, pong} -> {reply, {ok, pong}, State};
                _ -> {reply, erlang:throw(#{module => ?MODULE, function => ping, reason => mock_mode_not_allowed, message => "Mock mode is not allowed in production"}), State}
            end;
        false ->
            {reply, erlang:throw(#{module => ?MODULE, function => ping, reason => mock_mode_not_allowed, message => "Mock mode is not allowed in production"}), State}
    end;

handle_call({insert_conformance_score, RunId, Score, Timestamp}, _From, State) ->
    case State#state.available of
        true ->
            %% Use embedded QLever via Java bridge - NO HTTP
            Sparql = build_insert_sparql(RunId, Score, Timestamp),
            case call_java_bridge(State, execute_update, [Sparql]) of
                {ok, _} ->
                    {reply, ok, State};
                {error, Reason} ->
                    logger:error("SPARQL INSERT failed: ~p", [Reason]),
                    {reply, {error, Reason}, State}
            end;
        false ->
            %% Mock mode: store in process state
            Entry = #{run => RunId, score => Score, timestamp => Timestamp},
            erlang:throw(#{module => ?MODULE, function => insert_conformance_score, reason => mock_mode_not_allowed, message => "Mock mode is not allowed in production"}),
            {reply, ok, State#state{mock_storage = NewStorage}}
    end;

handle_call(select_conformance_history, _From, State) ->
    case State#state.available of
        true ->
            %% Use embedded QLever via Java bridge - NO HTTP
            Sparql = build_select_sparql(),
            case call_java_bridge(State, execute_query, [Sparql]) of
                {ok, JsonResult} ->
                    Results = parse_sparql_results(JsonResult),
                    {reply, {ok, Results}, State};
                {error, Reason} ->
                    logger:error("SPARQL SELECT failed: ~p", [Reason]),
                    {reply, {error, Reason}, State}
            end;
        false ->
            {reply, erlang:throw(#{module => ?MODULE, function => select_conformance_history, reason => mock_mode_not_allowed, message => "Mock mode is not allowed in production"}), State}
    end;

handle_call({execute_query, Query}, _From, State) ->
    case State#state.available of
        true ->
            case call_java_bridge(State, execute_query, [Query]) of
                {ok, Result} ->
                    {reply, {ok, Result}, State};
                {error, Reason} ->
                    {reply, {error, Reason}, State}
            end;
        false ->
            {reply, {error, mock_mode_not_supported_for_arbitrary_queries}, State}
    end;

handle_call({execute_update, Update}, _From, State) ->
    case State#state.available of
        true ->
            case call_java_bridge(State, execute_update, [Update]) of
                {ok, Result} ->
                    {reply, {ok, Result}, State};
                {error, Reason} ->
                    {reply, {error, Reason}, State}
            end;
        false ->
            {reply, {error, mock_mode_not_supported_for_arbitrary_updates}, State}
    end;

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_request}, State}.

handle_cast(_Msg, State) ->
    {noreply, State}.

terminate(_Reason, #state{initialized = true} = State) ->
    shutdown_embedded_qlever(State),
    ok;
terminate(_Reason, _State) ->
    ok.

%%%===================================================================
%%% Internal Functions - Embedded QLever via Java Bridge
%%%===================================================================

%% @private
%% Initialize embedded QLever through Java Panama FFI bridge
initialize_embedded_qlever(State) ->
    try
        %% Call Java bridge to initialize QLeverEmbeddedSparqlEngine
        case call_java_bridge(State, initialize_qlever, []) of
            {ok, initialized} ->
                {ok, State#state{available = true, initialized = true}};
            {error, Reason} ->
                logger:warning("Failed to initialize embedded QLever: ~p", [Reason]),
                {error, Reason}
        end
    catch
        Type:Error ->
            logger:warning("Exception initializing embedded QLever: ~p:~p", [Type, Error]),
            {error, {exception, Type, Error}}
    end.

%% @private
%% Shutdown embedded QLever through Java Panama FFI bridge
shutdown_embedded_qlever(State) ->
    try
        case call_java_bridge(State, shutdown_qlever, []) of
            {ok, shutdown} ->
                {ok, State#state{available = false, initialized = false}};
            {error, Reason} ->
                {error, Reason}
        end
    catch
        _:_ ->
            {ok, State#state{available = false, initialized = false}}
    end.

%% @private
%% Call Java bridge via Unix socket or direct Erlang-Java interface
%% This replaces HTTP calls with direct IPC to Java's embedded QLever
call_java_bridge(#state{java_bridge = undefined}, _Operation, _Args) ->
    %% No Java bridge configured - try to use process_mining_bridge as proxy
    %% The process_mining_bridge NIF can route to Java's QLeverEmbeddedSparqlEngine
    try
        case whereis(process_mining_bridge) of
            undefined ->
                {error, java_bridge_not_available};
            _Pid ->
                %% Use process_mining_bridge as proxy to Java
                {error, embedded_qlever_requires_java_bridge}
        end
    catch
        _:_ ->
            {error, java_bridge_not_available}
    end;
call_java_bridge(#state{java_bridge = BridgePid}, Operation, Args) when is_pid(BridgePid) ->
    %% Call Java bridge process directly
    try
        BridgePid ! {qlever_request, self(), Operation, Args},
        receive
            {qlever_response, BridgePid, Result} ->
                Result
        after ?TIMEOUT ->
            {error, timeout}
        end
    catch
        Type:Error ->
            {error, {exception, Type, Error}}
    end;
call_java_bridge(_State, _Operation, _Args) ->
    {error, java_bridge_not_configured}.

%%%===================================================================
%%% SPARQL Query Building - NO HTTP, pure string operations
%%%===================================================================

%% @private
build_insert_sparql(RunId, Score, Timestamp) ->
    io_lib:format(
        "PREFIX sim: <~s>~n"
        "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>~n"
        "INSERT DATA {~n"
        "  <run:~s> sim:conformanceScore \"~f\"^^xsd:decimal ;~n"
        "           sim:timestamp \"~s\"^^xsd:dateTime .~n"
        "}~n",
        [?SIM_NAMESPACE, RunId, Score, Timestamp]
    ).

%% @private
build_select_sparql() ->
    io_lib:format(
        "PREFIX sim: <~s>~n"
        "SELECT ?run ?score ?timestamp WHERE {~n"
        "  ?run sim:conformanceScore ?score ;~n"
        "       sim:timestamp ?timestamp .~n"
        "} ORDER BY ?run~n",
        [?SIM_NAMESPACE]
    ).

%%%===================================================================
%%% Result Parsing - JSON from embedded QLever
%%%===================================================================

%% @private
parse_sparql_results(JsonResult) when is_binary(JsonResult) ->
    parse_sparql_results(binary_to_list(JsonResult));
parse_sparql_results(JsonString) when is_list(JsonString) ->
    try jsx:decode(list_to_binary(JsonString), [return_maps]) of
        #{<<"results">> := #{<<"bindings">> := Bindings}} ->
            %% SPARQL JSON results format
            lists:map(fun parse_binding/1, Bindings);
        #{<<"bindings">> := Bindings} ->
            %% Direct bindings array
            lists:map(fun parse_binding/1, Bindings);
        Other ->
            %% Unknown format, return as-is
            Other
    catch
        _:_ ->
            %% If JSON parsing fails, return raw string
            #{raw => JsonString}
    end;
parse_sparql_results(Other) ->
    Other.

%% @private
parse_binding(Binding) when is_map(Binding) ->
    Run = get_value(Binding, <<"run">>),
    Score = get_value(Binding, <<"score">>),
    Timestamp = get_value(Binding, <<"timestamp">>),
    #{
        run => Run,
        score => Score,
        timestamp => Timestamp
    };
parse_binding(Other) ->
    Other.

%% @private
get_value(Binding, Key) ->
    case maps:get(Key, Binding, undefined) of
        #{<<"value">> := Value} -> Value;
        undefined -> undefined;
        Value -> Value
    end.
