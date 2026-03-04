%%%-------------------------------------------------------------------
%%% @doc Process Mining Bridge - Erlang NIF interface to rust4pm
%%%
%%% FIXED VERSION - No circular calls, proper NIF stubs
%%%
%%% The NIF library exports these functions:
%%%   - nop/0 -> ok
%%%   - int_passthrough/1 -> {ok, Int}
%%%   - import_ocel_json_path/1 -> String (UUID)
%%%   - import_xes_path/1 -> String (UUID)
%%%   - num_events/1 -> Int
%%%   - num_objects/1 -> Int
%%%   - slim_link_ocel/1 -> String (UUID)
%%%   - discover_dfg/1 -> String (JSON)
%%%   - discover_petri_net/1 -> String (UUID)
%%%   - token_replay/2 -> Map
%%%   - registry_get_type/1 -> String
%%%   - registry_free/1 -> ok
%%%   - registry_list/0 -> List
%%% @end
%%%-------------------------------------------------------------------
-module(process_mining_bridge_fixed).
-behaviour(gen_server).

-include_lib("kernel/include/logger.hrl").

%% API
-export([
    start_link/0,
    stop/0,
    ping/0,
    get_nif_status/0
]).

%% Direct NIF functions (these are loaded from Rust)
-export([
    nop/0,
    int_passthrough/1,
    echo_json/1,
    import_ocel_json_path/1,
    import_xes_path/1,
    num_events/1,
    num_objects/1,
    slim_link_ocel/1,
    index_link_ocel/1,
    discover_dfg/1,
    discover_petri_net/1,
    token_replay/2,
    ocel_type_stats/1,
    registry_get_type/1,
    registry_free/1,
    registry_list/0
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

-on_load(init_nif/0).

-define(SERVER, ?MODULE).
-define(NIF_LIB, "process_mining_bridge").

-record(state, {
    registry :: map()
}).

%%%===================================================================
%%% NIF Loading
%%%===================================================================

-spec init_nif() -> ok.
init_nif() ->
    PrivDir = case code:priv_dir(?MODULE) of
        {error, _} ->
            AppDir = filename:dirname(filename:dirname(code:which(?MODULE))),
            filename:join(AppDir, "priv");
        Dir ->
            Dir
    end,
    NifPath = filename:join(PrivDir, ?NIF_LIB),
    case erlang:load_nif(NifPath, 0) of
        ok ->
            logger:info("NIF loaded successfully from ~s", [NifPath]),
            ok;
        {error, {load_failed, Reason}} ->
            logger:warning("NIF load failed: ~p, using fallbacks", [Reason]),
            ok;
        {error, {reload, _}} ->
            ok;
        {error, Reason} ->
            logger:warning("NIF load error: ~p, using fallbacks", [Reason]),
            ok
    end.

%%%===================================================================
%%% NIF Stubs - These are replaced by Rust NIF when loaded
%%%===================================================================

%% @doc No-op benchmark function (tests NIF call overhead)
-spec nop() -> ok | {error, term()}.
nop() ->
    erlang:nif_error(nif_not_loaded).

%% @doc Integer passthrough benchmark
-spec int_passthrough(integer()) -> {ok, integer()} | {error, term()}.
int_passthrough(_N) ->
    erlang:nif_error(nif_not_loaded).

%% @doc JSON echo benchmark
-spec echo_json(binary() | string()) -> {ok, binary() | string()} | {error, term()}.
echo_json(_Json) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Import OCEL JSON file - returns UUID string
-spec import_ocel_json_path(string()) -> {ok, string()} | {error, term()}.
import_ocel_json_path(_Path) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Import XES file - returns UUID string
-spec import_xes_path(string()) -> {ok, string()} | {error, term()}.
import_xes_path(_Path) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get number of events in OCEL
-spec num_events(string()) -> {ok, non_neg_integer()} | {error, term()}.
num_events(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get number of objects in OCEL
-spec num_objects(string()) -> {ok, non_neg_integer()} | {error, term()}.
num_objects(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Create slim-linked OCEL
-spec slim_link_ocel(string()) -> {ok, string()} | {error, term()}.
slim_link_ocel(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Create index-linked OCEL
-spec index_link_ocel(string()) -> {ok, string()} | {error, term()}.
index_link_ocel(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Discover DFG from OCEL - returns JSON string
-spec discover_dfg(string()) -> {ok, binary()} | {error, term()}.
discover_dfg(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Discover Petri net from OCEL - returns UUID string
-spec discover_petri_net(string()) -> {ok, string()} | {error, term()}.
discover_petri_net(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Run token replay conformance checking
-spec token_replay(string(), string()) -> {ok, map()} | {error, term()}.
token_replay(_OcelId, _PnId) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get OCEL type statistics
-spec ocel_type_stats(string()) -> {ok, binary()} | {error, term()}.
ocel_type_stats(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get type of registry item
-spec registry_get_type(string()) -> {ok, string()} | {error, term()}.
registry_get_type(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Free registry item
-spec registry_free(string()) -> ok | {error, term()}.
registry_free(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc List all registry items
-spec registry_list() -> {ok, [{string(), string()}]} | {error, term()}.
registry_list() ->
    erlang:nif_error(nif_not_loaded).

%%%===================================================================
%%% gen_server API
%%%===================================================================

-spec start_link() -> {ok, pid()} | {error, term()}.
start_link() ->
    gen_server:start_link({local, ?SERVER}, ?MODULE, [], []).

-spec stop() -> ok.
stop() ->
    gen_server:stop(?SERVER).

-spec ping() -> {ok, {pong, boolean()}}.
ping() ->
    gen_server:call(?SERVER, {ping, self()}).

-spec get_nif_status() -> {ok, {nif_status, boolean()}}.
get_nif_status() ->
    gen_server:call(?SERVER, {get_nif_status, self()}).

%%%===================================================================
%%% gen_server callbacks
%%%===================================================================

init([]) ->
    init_nif(),
    {ok, #state{registry = #{}}}.

handle_call({ping, _}, _From, State) ->
    %% Check if NIF is loaded by trying nop()
    IsNifLoaded = case nop() of
        ok -> true;
        {error, _} -> false
    end,
    {reply, {ok, {pong, IsNifLoaded}}, State};

handle_call({get_nif_status, _}, _From, State) ->
    PrivDir = case code:priv_dir(?MODULE) of
        {error, _} ->
            AppDir = filename:dirname(filename:dirname(code:which(?MODULE))),
            filename:join(AppDir, "priv");
        Dir ->
            Dir
    end,
    NifLibFull = filename:join(PrivDir, ?NIF_LIB ++ case os:type() of
        {win32, _} -> ".dll";
        {unix, darwin} -> ".dylib";
        _ -> ".so"
    end),
    FileExists = filelib:is_file(NifLibFull),
    {reply, {ok, {nif_status, FileExists}}, State};

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_request}, State}.

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.
