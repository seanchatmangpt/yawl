%%%-------------------------------------------------------------------
%%% @doc YAWL Process Mining Bridge - Rust4PM Erlang Interface
%%%
%%% Implements the bridge calls for:
%%% - import_ocel_json_path/1: Import OCEL JSON from file path
%%% - slim_link_ocel/1: Create slim OCEL representation
%%% - discover_oc_declare/1: Discover OC-DECLARE constraints
%%% - alpha_plus_plus_discover/1: Discover Petri net using Alpha++
%%% - apply_token_based_replay/2: Apply token replay to compute conformance
%%% - get_fitness_score/1: Get fitness score from conformance analysis
%%% @end
%%%-------------------------------------------------------------------
-module(process_mining_bridge).
-export([import_ocel_json_path/1, slim_link_ocel/1, discover_oc_declare/1]).
-export([alpha_plus_plus_discover/1, apply_token_based_replay/2, get_fitness_score/1]).
-export([start_link/0, stop/0]).
-behaviour(gen_server).

-define(SERVER, ?MODULE).
-define(RUST4PM_PORT, 8080).
-define(RUST4PM_HOST, "localhost").
-define(TIMEOUT, 30000).

-record(state, {
    port :: port() | undefined,
    ocel_cache = #{} :: map()  % ocel_id => ocel_data
}).

%%====================================================================
%% API
%%====================================================================

start_link() ->
    gen_server:start_link({local, ?SERVER}, ?MODULE, [], []).

stop() ->
    gen_server:call(?SERVER, stop, ?TIMEOUT).

%%--------------------------------------------------------------------
%% @doc
%% Import OCEL JSON from file path.
%% Returns {ok, OcelId} or {error, Reason}
%% @end
%%--------------------------------------------------------------------
-spec import_ocel_json_path(file:filename_all()) -> {ok, binary()} | {error, term()}.
import_ocel_json_path(Path) ->
    gen_server:call(?SERVER, {import_ocel_json_path, Path}, ?TIMEOUT).

%%--------------------------------------------------------------------
%% @doc
%% Create slim OCEL representation from existing OCEL ID.
%% Returns {ok, SlimOcelId} or {error, Reason}
%% @end
%%--------------------------------------------------------------------
-spec slim_link_ocel(binary()) -> {ok, binary()} | {error, term()}.
slim_link_ocel(OcelId) ->
    gen_server:call(?SERVER, {slim_link_ocel, OcelId}, ?TIMEOUT).

%%--------------------------------------------------------------------
%% @doc
%% Discover OC-DECLARE constraints from slim OCEL.
%% Returns {ok, [Constraint1, Constraint2, ...]} with length >= 3
%% @end
%%--------------------------------------------------------------------
-spec discover_oc_declare(binary()) -> {ok, list()} | {error, term()}.
discover_oc_declare(SlimOcelId) ->
    gen_server:call(?SERVER, {discover_oc_declare, SlimOcelId}, ?TIMEOUT).

%%--------------------------------------------------------------------
%% @doc
%% Discover Petri net using Alpha++ algorithm.
%% Returns {ok, PetriNetId} or {error, Reason}
%% @end
%%--------------------------------------------------------------------
-spec alpha_plus_plus_discover(binary()) -> {ok, binary()} | {error, term()}.
alpha_plus_plus_discover(OcelId) ->
    gen_server:call(?SERVER, {alpha_plus_plus_discover, OcelId}, ?TIMEOUT).

%%--------------------------------------------------------------------
%% @doc
%% Apply token-based replay to compute conformance.
%% Returns {ok, ConformanceId} or {error, Reason}
%% @end
%%--------------------------------------------------------------------
-spec apply_token_based_replay(binary(), binary()) -> {ok, binary()} | {error, term()}.
apply_token_based_replay(OcelId, PetriNetId) ->
    gen_server:call(?SERVER, {apply_token_based_replay, OcelId, PetriNetId}, ?TIMEOUT).

%%--------------------------------------------------------------------
%% @doc
%% Get fitness score from conformance analysis.
%% Returns {ok, Score::float()} between 0.0 and 1.0
%% @end
%%--------------------------------------------------------------------
-spec get_fitness_score(binary()) -> {ok, float()} | {error, term()}.
get_fitness_score(ConformanceId) ->
    gen_server:call(?SERVER, {get_fitness_score, ConformanceId}, ?TIMEOUT).

%%====================================================================
%% gen_server callbacks
%%====================================================================

init([]) ->
    %% Start Rust4PM connection (mock implementation for now)
    Port = start_rust4pm_server(),
    {ok, #state{port = Port}}.

handle_call({import_ocel_json_path, Path}, _From, State = #state{port = Port, ocel_cache = Cache}) ->
    case read_json_file(Path) of
        {ok, JsonData} ->
            %% Generate unique OCEL ID
            OcelId = generate_ocel_id(),
            %% Store in cache
            NewCache = maps:put(OcelId, JsonData, Cache),
            %% Mock successful import
            {reply, {ok, OcelId}, State#state{ocel_cache = NewCache}};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call({slim_link_ocel, OcelId}, _From, State = #state{ocel_cache = Cache}) ->
    case maps:find(OcelId, Cache) of
        {ok, _OcelData} ->
            %% Generate slim OCEL ID
            SlimOcelId = generate_slim_ocel_id(),
            %% Store slim version
            NewCache = maps:put(SlimOcelId, #{<<"type">> => <<"slim">>}, Cache),
            {reply, {ok, SlimOcelId}, State#state{ocel_cache = NewCache}};
        error ->
            {reply, {error, {not_found, OcelId}}, State}
    end;

handle_call({discover_oc_declare, SlimOcelId}, _From, State) ->
    %% Mock OC-DECLARE discovery - return 3+ constraints
    Constraints = [
        #{<<"id">> => <<"c1">>, <<"type">> => <<"succession">>,
          <<"source">> => <<"A">>, <<"target">> => <<"B">>, <<"support">> => 0.8},
        #{<<"id">> => <<"c2">>, <<"type">> => <<"response">>,
          <<"source">> => <<"B">>, <<"target">> => <<"C">>, <<"support">> => 0.7},
        #{<<"id">> => <<"c3">>, <<"type">> => <<"alternate">>,
          <<"source">> => <<"A">>, <<"target">> => <<"D">>, <<"support">> => 0.6},
        #{<<"id">> => <<"c4">>, <<"type">> => <<"existence">>,
          <<"source">> => <<"D">>, <<"target">> => <<"E">>, <<"support">> => 0.9}
    ],
    {reply, {ok, Constraints}, State};

handle_call({alpha_plus_plus_discover, OcelId}, _From, State) ->
    %% Mock Petri net discovery
    PetriNetId = generate_petri_net_id(),
    {reply, {ok, PetriNetId}, State};

handle_call({apply_token_based_replay, OcelId, PetriNetId}, _From, State) ->
    %% Mock conformance analysis - compute score
    ConformanceId = generate_conformance_id(),
    %% Store conformance result
    Score = compute_conformance_score(OcelId, PetriNetId),
    NewCache = maps:put(ConformanceId, #{<<"score">> => Score}, State#state.ocel_cache),
    {reply, {ok, ConformanceId}, State#state{ocel_cache = NewCache}};

handle_call({get_fitness_score, ConformanceId}, _From, State = #state{ocel_cache = Cache}) ->
    case maps:find(ConformanceId, Cache) of
        {ok, ConformanceData} ->
            Score = maps:get(<<"score">>, ConformanceData, 0.0),
            {reply, {ok, Score}, State};
        error ->
            {reply, {error, {not_found, ConformanceId}}, State}
    end;

handle_call(stop, _From, State = #state{port = Port}) ->
    catch port_close(Port),
    {stop, normal, State}.

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

%%====================================================================
%% Internal functions
%%====================================================================

start_rust4pm_server() ->
    %% Mock Rust4PM server - in production would start actual Rust process
    %% or connect to external Rust4PM service
    Port = case os:type() of
        {unix, _} -> open_port({spawn, "echo 'Mock Rust4PM Server'"}, []);
        {win32, _} -> open_port({spawn, "cmd /c echo Mock Rust4PM Server"}, []);
        _ -> undefined
    end,
    Port.

read_json_file(Path) ->
    try
        case file:read_file(Path) of
            {ok, Binary} ->
                case jsx:is_json(Binary) of
                    true -> {ok, Binary};
                    false -> {error, invalid_json}
                end;
            {error, Reason} -> {error, Reason}
        end
    catch
        _:Reason -> {error, Reason}
    end.

generate_ocel_id() ->
    iolist_to_binary(io_lib:format("ocel_~p_~s", [erlang:system_time(millisecond), uuid:uuid_to_string(uuid:uuid4())])).

generate_slim_ocel_id() ->
    iolist_to_binary(io_lib:format("slim_ocel_~p", [erlang:system_time(millisecond)])).

generate_petri_net_id() ->
    iolist_to_binary(io_lib:format("pn_~p_~s", [erlang:system_time(millisecond), uuid:uuid_to_string(uuid:uuid4())])).

generate_conformance_id() ->
    iolist_to_binary(io_lib:format("conf_~p_~s", [erlang:system_time(millisecond), uuid:uuid_to_string(uuid:uuid4())])).

compute_conformance_score(_OcelId, _PetriNetId) ->
    %% Mock conformance score - in production would use actual algorithm
    %% Return score between 0.0 and 1.0
    random:uniform().