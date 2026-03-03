-module(process_mining_bridge).
-behaviour(gen_server).

-on_load(init_nif/0).

%% API
-export([start_link/0]).

%% Public API - matching process_mining crate (v0.5.2) capabilities
%% See: https://docs.rs/process_mining/0.5.2/process_mining/
-export([
    %% XES Import/Export (core::io::Importable/Exportable)
    import_xes/1,
    export_xes/2,

    %% OCEL 2.0 Support
    import_ocel_json/1,
    import_ocel_xml/1,
    import_ocel_sqlite/1,

    %% Process Discovery (discovery module)
    discover_dfg/1,
    discover_alpha/1,
    discover_oc_dfg/1,

    %% Petri Net Operations (core::PetriNet)
    import_pnml/1,
    export_pnml/1,

    %% Conformance Checking (conformance module)
    token_replay/2,

    %% Event Log Stats
    event_log_stats/1,

    %% Memory Management
    free_handle/1
]).

%% gen_server callbacks
-export([init/1, handle_call/3, handle_cast/2, handle_info/2, terminate/2, code_change/3]).

-define(SERVER, ?MODULE).
-define(NIF_LIB, "yawl_process_mining").

-record(state, {
    registry :: map()
}).

%% Mnesia record for handle registry
-record(handle_registry, {
    id :: binary(),
    handle :: reference(),
    type :: xes_log | ocel | petri_net | dfg,
    timestamp :: erlang:timestamp()
}).

%%====================================================================
%% NIF Initialization
%%====================================================================

init_nif() ->
    Priv = case code:priv_dir(?MODULE) of
        {error, _} -> "priv";
        Dir -> Dir
    end,
    LibPath = filename:join(Priv, ?NIF_LIB),
    erlang:load_nif(LibPath, 0).

%%====================================================================
%% API Functions
%%====================================================================

start_link() ->
    gen_server:start_link({local, ?SERVER}, ?MODULE, [], []).

%% XES Import - uses process_mining::core::EventLog::import()
%% Returns {ok, LogHandle} | {error, Reason}
import_xes(#{path := Path}) ->
    gen_server:call(?SERVER, {import_xes, Path}).

%% XES Export - uses process_mining::core::EventLog::export()
export_xes(#{log_handle := Handle, path := Path}) ->
    gen_server:call(?SERVER, {export_xes, Handle, Path}).

%% OCEL 2.0 JSON Import - uses process_mining::core::OCEL::import_ocel_json()
import_ocel_json(#{path := Path}) ->
    gen_server:call(?SERVER, {import_ocel_json, Path}).

%% OCEL 2.0 XML Import
import_ocel_xml(#{path := Path}) ->
    gen_server:call(?SERVER, {import_ocel_xml, Path}).

%% OCEL 2.0 SQLite Import
import_ocel_sqlite(#{path := Path}) ->
    gen_server:call(?SERVER, {import_ocel_sqlite, Path}).

%% DFG Discovery - uses process_mining::discovery::dfg::discover_dfg()
%% Returns {ok, DfgJson} | {error, Reason}
discover_dfg(#{log_handle := Handle}) ->
    gen_server:call(?SERVER, {discover_dfg, Handle}).

%% Alpha Miner - uses process_mining::discovery::alpha::discover_alpha()
%% Returns {ok, PnmlXml} | {error, Reason}
discover_alpha(#{log_handle := Handle}) ->
    gen_server:call(?SERVER, {discover_alpha, Handle}).

%% Object-Centric DFG Discovery
discover_oc_dfg(#{ocel_handle := Handle}) ->
    gen_server:call(?SERVER, {discover_oc_dfg, Handle}).

%% PNML Import - uses process_mining::core::PetriNet::import()
import_pnml(#{path := Path}) ->
    gen_server:call(?SERVER, {import_pnml, Path}).

%% PNML Export - uses process_mining::core::PetriNet::export()
export_pnml(#{net_handle := Handle}) ->
    gen_server:call(?SERVER, {export_pnml, Handle}).

%% Token Replay Conformance - uses process_mining::conformance::token_replay()
%% Returns {ok, #{fitness => float, missing => int, remaining => int, consumed => int}}
token_replay(#{log_handle := LogHandle, net_handle := NetHandle}) ->
    gen_server:call(?SERVER, {token_replay, LogHandle, NetHandle}).

%% Event Log Stats
event_log_stats(#{log_handle := Handle}) ->
    gen_server:call(?SERVER, {event_log_stats, Handle}).

%% Free Handle
free_handle(#{handle := Handle}) ->
    gen_server:call(?SERVER, {free_handle, Handle}).

%%====================================================================
%% gen_server callbacks
%%====================================================================

init([]) ->
    process_flag(trap_exit, true),
    lager:info("Process mining bridge starting with process_mining crate v0.5.2"),

    %% Initialize Mnesia registry
    Registry = init_registry(),
    {ok, #state{registry = Registry}}.

init_registry() ->
    case mnesia:transaction(fun() -> mnesia:all_objects(handle_registry) end) of
        {atomic, Objects} ->
            lists:foldl(fun(#handle_registry{id = Id, handle = Handle}, Acc) ->
                maps:put(Id, Handle, Acc)
            end, #{}, Objects);
        {aborted, _Reason} ->
            #{}
    end.

handle_call({import_xes, Path}, _From, State) ->
    case nif_import_xes(Path) of
        {ok, Handle} ->
            Id = generate_id(),
            ok = store_handle(Id, Handle, xes_log),
            {reply, {ok, #{id => Id, handle => Handle}}, State};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call({export_xes, Handle, Path}, _From, State) ->
    Result = nif_export_xes(Handle, Path),
    {reply, Result, State};

handle_call({import_ocel_json, Path}, _From, State) ->
    case nif_import_ocel_json(Path) of
        {ok, Handle} ->
            Id = generate_id(),
            ok = store_handle(Id, Handle, ocel),
            {reply, {ok, #{id => Id, handle => Handle}}, State};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call({discover_dfg, Handle}, _From, State) ->
    Result = nif_discover_dfg(Handle),
    {reply, Result, State};

handle_call({discover_alpha, Handle}, _From, State) ->
    case nif_discover_alpha(Handle) of
        {ok, PnmlXml} ->
            %% Parse PNML and store handle
            case nif_import_pnml_from_string(PnmlXml) of
                {ok, NetHandle} ->
                    Id = generate_id(),
                    ok = store_handle(Id, NetHandle, petri_net),
                    {reply, {ok, #{id => Id, pnml => PnmlXml, handle => NetHandle}}, State};
                {error, _} ->
                    %% Still return PNML even if we can't parse it
                    {reply, {ok, #{pnml => PnmlXml}}, State}
            end;
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call({import_pnml, Path}, _From, State) ->
    case nif_import_pnml(Path) of
        {ok, Handle} ->
            Id = generate_id(),
            ok = store_handle(Id, Handle, petri_net),
            {reply, {ok, #{id => Id, handle => Handle}}, State};
        {error, Reason} ->
            {reply, {error, Reason}, State}
    end;

handle_call({export_pnml, Handle}, _From, State) ->
    Result = nif_export_pnml(Handle),
    {reply, Result, State};

handle_call({token_replay, LogHandle, NetHandle}, _From, State) ->
    Result = nif_token_replay(LogHandle, NetHandle),
    {reply, Result, State};

handle_call({event_log_stats, Handle}, _From, State) ->
    Result = nif_event_log_stats(Handle),
    {reply, Result, State};

handle_call({free_handle, Handle}, _From, State) ->
    nif_free_handle(Handle),
    {reply, ok, State};

handle_call(_Request, _From, State) ->
    {reply, {error, unknown_call}, State}.

handle_cast(_Msg, State) ->
    {noreply, State}.

handle_info(_Info, State) ->
    {noreply, State}.

terminate(_Reason, _State) ->
    lager:info("Process mining bridge terminating"),
    cleanup_all_handles(),
    ok.

code_change(_OldVsn, State, _Extra) ->
    {ok, State}.

%%====================================================================
%% Internal Functions
%%====================================================================

generate_id() ->
    Id = crypto:strong_rand_bytes(16),
    binary:encode_hex(Id).

store_handle(Id, Handle, Type) ->
    Timestamp = erlang:timestamp(),
    Record = #handle_registry{
        id = Id,
        handle = Handle,
        type = Type,
        timestamp = Timestamp
    },
    mnesia:transaction(fun() -> mnesia:write(Record) end).

cleanup_all_handles() ->
    mnesia:transaction(fun() ->
        case mnesia:all_objects(handle_registry) of
            Objects when is_list(Objects) ->
                lists:foreach(fun(#handle_registry{handle = Handle}) ->
                    nif_free_handle(Handle)
                end, Objects);
            _ -> ok
        end
    end).

%%====================================================================
%% NIF Stubs - Implemented in Rust using rustler + process_mining crate
%%====================================================================

%% XES Import - process_mining::core::EventLog::import(Path)
nif_import_xes(_Path) ->
    {error, nif_not_loaded}.

%% XES Export - process_mining::core::EventLog::export(Path)
nif_export_xes(_Handle, _Path) ->
    {error, nif_not_loaded}.

%% OCEL JSON Import - process_mining::core::OCEL::import_ocel_json(Path)
nif_import_ocel_json(_Path) ->
    {error, nif_not_loaded}.

%% DFG Discovery - process_mining::discovery::dfg::discover_dfg(&log)
nif_discover_dfg(_Handle) ->
    {error, nif_not_loaded}.

%% Alpha Miner - process_mining::discovery::alpha::discover_alpha(&log)
nif_discover_alpha(_Handle) ->
    {error, nif_not_loaded}.

%% PNML Import - process_mining::core::PetriNet::import(Path)
nif_import_pnml(_Path) ->
    {error, nif_not_loaded}.

%% PNML Import from String
nif_import_pnml_from_string(_Pnml) ->
    {error, nif_not_loaded}.

%% PNML Export - process_mining::core::PetriNet::export()
nif_export_pnml(_Handle) ->
    {error, nif_not_loaded}.

%% Token Replay - process_mining::conformance::token_replay(&log, &net)
nif_token_replay(_LogHandle, _NetHandle) ->
    {error, nif_not_loaded}.

%% Event Log Stats
nif_event_log_stats(_Handle) ->
    {error, nif_not_loaded}.

%% Free Handle
nif_free_handle(_Handle) ->
    ok.
