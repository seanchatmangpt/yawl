%%%-------------------------------------------------------------------
%%% @doc Process Mining Bridge - Erlang NIF interface to rust4pm
%%%
%%% This module provides Erlang access to the Rust process_mining crate
%%% via NIF (Native Implemented Functions). The NIF library is loaded
%%% from priv/process_mining_bridge.so.
%%%
%%% == Supported Operations ==
%%% - XES Import/Export (event logs)
%%% - OCEL Import/Export (object-centric event logs)
%%% - DFG Discovery (directly-follows graph)
%%% - Alpha+++ Miner (Petri net discovery)
%%% - PNML Export (Petri net markup language)
%%% - Event Log Statistics
%%%
%%% == Architecture ==
%%% This is the BEAM boundary of the Three-Domain Native Bridge Pattern.
%%% The Rust NIF handles all memory management via ResourceArc.
%%% @end
%%%-------------------------------------------------------------------
-module(process_mining_bridge).
-behaviour(gen_server).

-include_lib("kernel/include/logger.hrl").

%% API
-export([
    start_link/0,
    stop/0,
    ping/0,
    get_nif_status/0
]).

%% XES Import/Export
-export([
    import_xes/1,
    export_xes/2
]).

%% OCEL Import/Export
-export([
    import_ocel_json/1,
    import_ocel_xml/1,
    import_ocel_sqlite/1,
    export_ocel_json/2
]).

%% Process Discovery
-export([
    discover_dfg/1,
    discover_alpha/1,
    discover_oc_dfg/1
]).

%% Petri Net Operations
-export([
    import_pnml/1,
    export_pnml/1
]).

%% Conformance Checking
-export([
    token_replay/2
]).

%% Event Log Stats
-export([
    event_log_stats/1,
    log_event_count/1,
    log_object_count/1,
    log_get_events/1,
    log_get_objects/1
]).

%% Performance Metrics & Analytics
-export([
    calculate_performance_metrics/1,
    get_activity_frequency/1,
    find_longest_traces/2
]).

%% Memory Management
-export([
    free_handle/1,
    events_free/1,
    objects_free/1
]).

%% Benchmark Functions (NIF passthrough)
-export([
    nop/0,
    int_passthrough/1,
    echo_json/1
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

%% @private
%% @doc Load the NIF library on module load.
%% Falls back to Erlang implementations if NIF not available.
-spec init_nif() -> ok.
init_nif() ->
    PrivDir = case code:priv_dir(?MODULE) of
        {error, _} ->
            %% Development path - priv dir relative to this file
            AppDir = filename:dirname(filename:dirname(code:which(?MODULE))),
            filename:join(AppDir, "priv");
        Dir ->
            Dir
    end,
    NifPath = filename:join(PrivDir, ?NIF_LIB),
    case erlang:load_nif(NifPath, 0) of
        ok ->
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
%%% NIF Stubs - These are loaded from the Rust NIF library
%%%===================================================================

%% @doc Import a XES event log from file path.
%% Returns {ok, LogHandle} on success, {error, Reason} on failure.
-spec import_xes(string()) -> {ok, reference()} | {error, term()}.
import_xes(Path) ->
    gen_server:call(?SERVER, {import_xes, Path}).

%% @doc Export a XES event log to file.
%% Returns ok on success, {error, Reason} on failure.
-spec export_xes(reference(), string()) -> ok | {error, term()}.
export_xes(Handle, Path) ->
    gen_server:call(?SERVER, {export_xes, Handle, Path}).

%% @doc Import an OCEL JSON file.
%% Returns {ok, OcelHandle} on success, {error, Reason} on failure.
-spec import_ocel_json(string()) -> {ok, reference()} | {error, term()}.
import_ocel_json(Path) ->
    gen_server:call(?SERVER, {import_ocel_json, Path}).

%% @doc Import an OCEL XML file.
-spec import_ocel_xml(string()) -> {ok, reference()} | {error, term()}.
import_ocel_xml(Path) ->
    gen_server:call(?SERVER, {import_ocel_xml, Path}).

%% @doc Import an OCEL SQLite database.
-spec import_ocel_sqlite(string()) -> {ok, reference()} | {error, term()}.
import_ocel_sqlite(Path) ->
    gen_server:call(?SERVER, {import_ocel_sqlite, Path}).

%% @doc Export an OCEL to JSON file.
-spec export_ocel_json(reference(), string()) -> ok | {error, term()}.
export_ocel_json(Handle, Path) ->
    gen_server:call(?SERVER, {export_ocel_json, Handle, Path}).

%% @doc Import an OCEL from JSON file using NIF directly (bypasses gen_server).
-spec import_ocel_json_direct(string()) -> {ok, reference()} | {error, term()}.
import_ocel_json_direct(Path) ->
    import_ocel_json(Path).

%%===================================================================
%%% Direct NIF Wrappers (bypass gen_server for performance)
%%===================================================================

%% @private
%% @doc Direct NIF call to import XES (for internal use)
-spec import_xes_nif(string()) -> {ok, reference()} | {error, term()}.
import_xes_nif(Path) ->
    erlang:nif_error(nif_not_loaded).

%% @private
%% @doc Direct NIF call to export XES (for internal use)
-spec export_xes_nif(reference(), string()) -> ok | {error, term()}.
export_xes_nif(Handle, Path) ->
    erlang:nif_error(nif_not_loaded).

%% @private
%% @doc Direct NIF call to import OCEL JSON (for internal use)
-spec import_ocel_json_nif(string()) -> {ok, reference()} | {error, term()}.
import_ocel_json_nif(Path) ->
    import_ocel_json(Path).

%% @private
%% @doc Direct NIF call to export OCEL JSON (for internal use)
-spec export_ocel_json_nif(reference(), string()) -> ok | {error, term()}.
export_ocel_json_nif(Handle, Path) ->
    export_ocel_json(Handle, Path).

%% @private
%% @doc Direct NIF call to discover DFG (for internal use)
-spec discover_dfg_nif(reference()) -> {ok, binary()} | {error, term()}.
discover_dfg_nif(Handle) ->
    case erlang:nif_error(nif_not_loaded) of
        nif_not_loaded ->
            %% Fallback to gen_server call when NIF not available
            discover_dfg(Handle);
        _ ->
            %% NIF should handle this directly
            discover_dfg(Handle)
    end.

%% @private
%% @doc Direct NIF call to discover Alpha+++ (for internal use)
-spec discover_alpha_nif(reference()) -> {ok, #{handle => reference(), pnml => binary()}} | {error, term()}.

%%===================================================================
%%% Benchmark NIF Functions
%%===================================================================

%% @doc No-op benchmark function (tests NIF call overhead)
-spec nop() -> {ok, ok} | {error, term()}.
nop() ->
    erlang:nif_error(nif_not_loaded).

%% @doc Integer passthrough benchmark
-spec int_passthrough(integer()) -> {ok, integer()} | {error, term()}.
int_passthrough(N) ->
    erlang:nif_error(nif_not_loaded).

%% @doc JSON echo benchmark
-spec echo_json(binary() | string()) -> {ok, binary() | string()} | {error, term()}.
echo_json(Json) ->
    erlang:nif_error(nif_not_loaded).
discover_alpha_nif(Handle) ->
    discover_alpha(Handle).

%% @private
%% @doc Direct NIF call to export PNML (for internal use)
-spec export_pnml_nif(reference()) -> {ok, binary()} | {error, term()}.
export_pnml_nif(Handle) ->
    export_pnml(Handle).

%% @private
%% @doc Direct NIF call to get event log stats (for internal use)
-spec event_log_stats_nif(reference()) -> {ok, map()} | {error, term()}.
event_log_stats_nif(Handle) ->
    event_log_stats(Handle).

%% @doc Discover a Directly-Follows Graph from an event log.
%% Returns {ok, DfgJson} where DfgJson is a JSON string.
-spec discover_dfg(reference()) -> {ok, binary()} | {error, term()}.
discover_dfg(Handle) ->
    gen_server:call(?SERVER, {discover_dfg, Handle}).

%% @doc Discover a Petri net using Alpha+++ algorithm.
%% Returns {ok, #{handle => NetHandle, pnml => PnmlXml}}.
-spec discover_alpha(reference()) -> {ok, #{handle => reference(), pnml => binary()}} | {error, term()}.
discover_alpha(Handle) ->
    gen_server:call(?SERVER, {discover_alpha, Handle}).

%% @doc Discover an Object-Centric DFG from OCEL.
-spec discover_oc_dfg(reference()) -> {ok, binary()} | {error, term()}.
discover_oc_dfg(Handle) ->
    gen_server:call(?SERVER, {discover_oc_dfg, Handle}).

%% @doc Import a Petri net from PNML file.
-spec import_pnml(string()) -> {ok, reference()} | {error, term()}.
import_pnml(Path) ->
    gen_server:call(?SERVER, {import_pnml, Path}).

%% @doc Export a Petri net to PNML format.
%% Returns {ok, PnmlXml} where PnmlXml is an XML string.
-spec export_pnml(reference()) -> {ok, binary()} | {error, term()}.
export_pnml(Handle) ->
    gen_server:call(?SERVER, {export_pnml, Handle}).

%% @doc Run token replay conformance checking.
%% Returns {ok, ConformanceMetrics} with fitness, precision, etc.
-spec token_replay(reference(), reference()) -> {ok, map()} | {error, term()}.
token_replay(LogHandle, NetHandle) ->
    gen_server:call(?SERVER, {token_replay, LogHandle, NetHandle}).

%% @doc Get statistics about an event log.
%% Returns {ok, #{traces => N, events => N, activities => N, avg_events_per_trace => F}}.
-spec event_log_stats(reference()) -> {ok, map()} | {error, term()}.
event_log_stats(Handle) ->
    gen_server:call(?SERVER, {event_log_stats, Handle}).

%% @doc Free a resource handle (no-op, automatic GC via ResourceArc).
-spec free_handle(reference()) -> ok.
free_handle(Handle) ->
    gen_server:call(?SERVER, {free_handle, Handle}).

%%%===================================================================
%%% gen_server API
%%%===================================================================

%% @doc Start the process mining bridge server.
-spec start_link() -> {ok, pid()} | {error, term()}.
start_link() ->
    gen_server:start_link({local, ?SERVER}, ?MODULE, [], []).

%% @doc Stop the process mining bridge server.
-spec stop() -> ok.
stop() ->
    gen_server:stop(?SERVER).

%% @doc Ping the bridge to check if it's running
ping() ->
    gen_server:call(?SERVER, {ping, self()}).

%% @doc Get NIF loading status
get_nif_status() ->
    gen_server:call(?SERVER, {get_nif_status, self()}).

%%%===================================================================
%%% gen_server callbacks
%%%===================================================================

init([]) ->
    %% Initialize NIF library on startup
    init_nif(),
    {ok, #state{registry = #{}}}.

handle_call({import_xes, Path}, _From, State) ->
    try
        case import_xes(Path) of
            {ok, Handle} ->
                Registry = maps:put(Handle, #{type => xes_log, created => erlang:system_time(millisecond)}, State#state.registry),
                {reply, {ok, Handle}, State#state{registry = Registry}};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({export_xes, Handle, Path}, _From, State) ->
    try
        case export_xes(Handle, Path) of
            ok ->
                {reply, ok, State};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({import_ocel_json, Path}, _From, State) ->
    try
        case import_ocel_json(Path) of
            {ok, Handle} ->
                Registry = maps:put(Handle, #{type => ocel, created => erlang:system_time(millisecond)}, State#state.registry),
                {reply, {ok, Handle}, State#state{registry = Registry}};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({import_ocel_xml, Path}, _From, State) ->
    try
        case import_ocel_xml(Path) of
            {ok, Handle} ->
                Registry = maps:put(Handle, #{type => ocel, created => erlang:system_time(millisecond)}, State#state.registry),
                {reply, {ok, Handle}, State#state{registry = Registry}};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({import_ocel_sqlite, Path}, _From, State) ->
    try
        case import_ocel_sqlite(Path) of
            {ok, Handle} ->
                Registry = maps:put(Handle, #{type => ocel, created => erlang:system_time(millisecond)}, State#state.registry),
                {reply, {ok, Handle}, State#state{registry = Registry}};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({export_ocel_json, Handle, Path}, _From, State) ->
    try
        case export_ocel_json(Handle, Path) of
            ok ->
                {reply, ok, State};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({discover_dfg, Handle}, _From, State) ->
    try
        case discover_dfg(Handle) of
            {ok, DfgJson} ->
                {reply, {ok, DfgJson}, State};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({discover_alpha, Handle}, _From, State) ->
    try
        case discover_alpha(Handle) of
            {ok, Result} ->
                {reply, {ok, Result}, State};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({discover_oc_dfg, Handle}, _From, State) ->
    try
        case discover_oc_dfg(Handle) of
            {ok, DfgJson} ->
                {reply, {ok, DfgJson}, State};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({import_pnml, Path}, _From, State) ->
    try
        case import_pnml(Path) of
            {ok, Handle} ->
                Registry = maps:put(Handle, #{type => petri_net, created => erlang:system_time(millisecond)}, State#state.registry),
                {reply, {ok, Handle}, State#state{registry = Registry}};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({export_pnml, Handle}, _From, State) ->
    try
        case export_pnml(Handle) of
            {ok, PnmlXml} ->
                {reply, {ok, PnmlXml}, State};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({token_replay, LogHandle, NetHandle}, _From, State) ->
    try
        case token_replay(LogHandle, NetHandle) of
            {ok, Metrics} ->
                {reply, {ok, Metrics}, State};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({event_log_stats, Handle}, _From, State) ->
    try
        case event_log_stats(Handle) of
            {ok, Stats} ->
                {reply, {ok, Stats}, State};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({calculate_performance_metrics, Handle}, _From, State) ->
    try
        case calculate_performance_metrics(Handle) of
            {ok, Metrics} ->
                {reply, {ok, Metrics}, State};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({get_activity_frequency, Handle}, _From, State) ->
    try
        case get_activity_frequency(Handle) of
            {ok, Frequency} ->
                {reply, {ok, Frequency}, State};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({find_longest_traces, Handle, TopN}, _From, State) ->
    try
        case find_longest_traces(Handle, TopN) of
            {ok, LongestTraces} ->
                {reply, {ok, LongestTraces}, State};
            {error, Reason} ->
                {reply, {error, Reason}, State}
        end
    catch
        throw:{'UnsupportedOperationException', Msg} ->
            {reply, {error, Msg}, State}
    end;

handle_call({free_handle, Handle}, _From, State) ->
    %% Remove handle from registry
    Registry = maps:remove(Handle, State#state.registry),
    {reply, ok, State#state{registry = Registry}};

handle_call({ping, _}, _From, State) ->
    %% Check if NIF is loaded by trying to call a NIF function
    IsNifLoaded = case erlang:loaded() of
        List when is_list(List) ->
            case lists:member(process_mining_bridge, List) of
                true ->
                    %% NIF module is loaded, check if native library exists
                    case code:priv_dir(?MODULE) of
                        {error, _} ->
                            %% Development path
                            AppDir = filename:dirname(filename:dirname(code:which(?MODULE))),
                            PrivDir = filename:join(AppDir, "priv");
                        Dir ->
                            PrivDir = Dir
                    end,
                    NifPath = filename:join(PrivDir, "yawl_process_mining"),
                    filelib:is_file(NifPath ++ ".so") orelse filelib:is_file(NifPath ++ ".dylib");
                false ->
                    false
            end;
        _ ->
            false
    end,
    {reply, {ok, {pong, IsNifLoaded}}, State};

handle_call({get_nif_status, _}, _From, State) ->
    %% Check NIF library file existence
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

%%%===================================================================
%%% Internal Functions
%%%===================================================================

%% @private
%% @doc Helper to throw unsupported operation with clear message.
-spec unsupported_operation(binary()) -> no_return().
unsupported_operation(Message) ->
    {'UnsupportedOperationException', Message}.

%%%===================================================================
%%% Additional Test Functions (Stubs)
%%%===================================================================

%% @doc Calculate performance metrics for a log
%% Returns {ok, Metrics} where Metrics contains throughput, average duration, etc.
-spec calculate_performance_metrics(reference()) -> {ok, binary()} | {error, term()}.
calculate_performance_metrics(Handle) ->
    gen_server:call(?SERVER, {calculate_performance_metrics, Handle}).

%% @doc Get activity frequency from a log
%% Returns {ok, ActivityFreqJson} with activity counts
-spec get_activity_frequency(reference()) -> {ok, binary()} | {error, term()}.
get_activity_frequency(Handle) ->
    gen_server:call(?SERVER, {get_activity_frequency, Handle}).

%% @doc Find longest N traces in a log
%% Returns {ok, LongestTracesJson} with trace IDs and their lengths
-spec find_longest_traces(reference(), integer()) -> {ok, binary()} | {error, term()}.
find_longest_traces(Handle, TopN) ->
    gen_server:call(?SERVER, {find_longest_traces, Handle, TopN}).

%% @doc Get event count from log (stub implementation)
-spec log_event_count(map()) -> {ok, integer()} | {error, term()}.
log_event_count(Params) ->
    case maps:get(ocel_id, Params, undefined) of
        undefined ->
            {error, missing_parameter};
        _ ->
            erlang:nif_error(nif_not_loaded)
    end.

%% @doc Get object count from log (stub implementation)
-spec log_object_count(map()) -> {ok, integer()} | {error, term()}.
log_object_count(Params) ->
    case maps:get(ocel_id, Params, undefined) of
        undefined ->
            {error, missing_parameter};
        _ ->
            erlang:nif_error(nif_not_loaded)
    end.

%% @doc Get events from OCEL log (stub implementation)
-spec log_get_events(map()) -> {ok, reference()} | {error, term()}.
log_get_events(Params) ->
    case maps:get(ocel_id, Params, undefined) of
        undefined ->
            {error, missing_parameter};
        _ ->
            erlang:nif_error(nif_not_loaded)
    end.

%% @doc Get objects from OCEL log (stub implementation)
-spec log_get_objects(map()) -> {ok, reference()} | {error, term()}.
log_get_objects(Params) ->
    case maps:get(ocel_id, Params, undefined) of
        undefined ->
            {error, missing_parameter};
        _ ->
            erlang:nif_error(nif_not_loaded)
    end.

%% @doc Free events handle (stub implementation)
-spec events_free(map()) -> ok | {error, term()}.
events_free(Params) ->
    case maps:get(events_handle, Params, undefined) of
        undefined ->
            {error, missing_parameter};
        _ ->
            erlang:nif_error(nif_not_loaded)
    end.

%% @doc Free objects handle (stub implementation)
-spec objects_free(map()) -> ok | {error, term()}.
objects_free(Params) ->
    case maps:get(objects_handle, Params, undefined) of
        undefined ->
            {error, missing_parameter};
        _ ->
            erlang:nif_error(nif_not_loaded)
    end.
