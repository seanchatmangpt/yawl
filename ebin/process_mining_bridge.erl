%%%-------------------------------------------------------------------
%%% @doc Process Mining Bridge - Direct NIF interface to rust4pm
%%%
%%% This module provides DIRECT access to the Rust NIF functions.
%%% No gen_server wrapper - the NIF functions are called directly.
%%%
%%% NIF functions exported by Rust (rustler::init!):
%%%   - nop/0 -> ok
%%%   - int_passthrough/1 -> {ok, Int}
%%%   - import_ocel_json_path/1 -> String (UUID)
%%%   - import_xes_path/1 -> String (UUID)
%%%   - num_events/1 -> Int
%%%   - num_objects/1 -> Int
%%%   - slim_link_ocel/1 -> String (UUID)
%%%   - index_link_ocel/1 -> String (UUID)
%%%   - discover_dfg/1 -> String (JSON)
%%%   - discover_petri_net/1 -> String (UUID)
%%%   - token_replay/2 -> Map
%%%   - ocel_type_stats/1 -> String (JSON)
%%%   - registry_get_type/1 -> String
%%%   - registry_free/1 -> ok
%%%   - registry_list/0 -> List
%%%===================================================================
-module(process_mining_bridge).
-author('YAWL Process Mining Team <yawl@example.com>').

-on_load(init_nif/0).

-define(NIF_LIB, "process_mining_bridge").

%%%===================================================================
%%% NIF Loading
%%%===================================================================

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
        ok -> ok;
        {error, {reload, _}} -> ok;
        {error, Reason} ->
            logger:warning("NIF load failed: ~p, using fallbacks", [Reason]),
            ok
    end.

%%%===================================================================
%%% NIF Stubs - These are replaced by Rust when NIF loads
%%%===================================================================

%% @doc No-op benchmark function (tests NIF call overhead)
-spec nop() -> ok.
nop() ->
    erlang:nif_error(nif_not_loaded).

%% @doc Integer passthrough benchmark
-spec int_passthrough(integer()) -> {ok, integer()}.
int_passthrough(_N) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Echo JSON - for testing
-spec echo_json(binary() | string()) -> {ok, binary() | string()} | {error, term()}.
echo_json(_Json) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Import OCEL JSON file - returns UUID string directly
-spec import_ocel_json_path(string()) -> string().
import_ocel_json_path(_Path) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Import XES file - returns UUID string directly
-spec import_xes_path(string()) -> string().
import_xes_path(_Path) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get number of events in OCEL
-spec num_events(string()) -> non_neg_integer().
num_events(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get number of objects in OCEL
-spec num_objects(string()) -> non_neg_integer().
num_objects(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Create slim-linked OCEL
-spec slim_link_ocel(string()) -> string().
slim_link_ocel(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Create index-linked OCEL
-spec index_link_ocel(string()) -> string().
index_link_ocel(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Discover DFG - returns JSON string
-spec discover_dfg(string()) -> string().
discover_dfg(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Discover Petri net - returns UUID string
-spec discover_petri_net(string()) -> string().
discover_petri_net(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Token replay conformance checking
-spec token_replay(string(), string()) -> map().
token_replay(_OcelId, _PnId) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get OCEL type statistics
-spec ocel_type_stats(string()) -> string().
ocel_type_stats(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get registry item type
-spec registry_get_type(string()) -> string().
registry_get_type(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Free registry item
-spec registry_free(string()) -> ok.
registry_free(_Id) ->
    erlang:nif_error(nif_not_loaded).

%% @doc List registry items
-spec registry_list() -> [{string(), string()}].
registry_list() ->
    erlang:nif_error(nif_not_loaded).
