%%%-------------------------------------------------------------------
%%% @doc rust4pm NIF - Erlang interface to Rust process mining library
%%%
%%% This module provides NIF functions for process mining:
%%% - OCEL2 JSON parsing
%%% - DFG (Directly-Follows Graph) discovery
%%% - Token replay conformance checking
%%%
%%% Architecture:
%%%   Java -> Erlang (via libei) -> NIF (rust4pm_nif) -> Rust algorithms
%%%
%%% The NIF library is loaded from:
%%%   1. RUST4PM_NIF_PATH environment variable
%%%   2. ../priv/rust4pm_nif.dylib (relative to ebin)
%%%   3. ../../rust/target/release/librust4pm_nif.dylib (dev mode)
%%% @end
%%%-------------------------------------------------------------------
-module(rust4pm_nif).
-author('YAWL Foundation').

%% API exports
-export([
    start/0,
    ping/0,
    nif_version/0,
    parse_ocel2_json/1,
    log_event_count/1,
    log_object_count/1,
    log_event_type_stats/1,
    log_object_type_stats/1,
    discover_dfg/1,
    check_conformance/2
]).

-on_load(init/0).

-define(NIF_LIB, "rust4pm_nif").

%%%===================================================================
%%% NIF Loading
%%%===================================================================

init() ->
    NifPath = find_nif_library(),
    case erlang:load_nif(NifPath, 0) of
        ok ->
            logger:info("rust4pm_nif loaded successfully from ~s", [NifPath]),
            ok;
        {error, {load_failed, Reason}} ->
            logger:warning("rust4pm_nif load failed: ~p, using pure Erlang fallbacks", [Reason]),
            ok;
        {error, {reload, _}} ->
            ok;
        {error, Reason} ->
            logger:warning("rust4pm_nif load error: ~p, using pure Erlang fallbacks", [Reason]),
            ok
    end.

find_nif_library() ->
    %% Try environment variable first
    case os:getenv("RUST4PM_NIF_PATH") of
        false ->
            %% Erlang load_nif ALWAYS adds .so extension (NIF convention)
            %% So we need to check for files without the .so extension that Erlang will add
            PrivDir = priv_dir(),

            %% Check for actual file with .so extension (symlink on macOS)
            PrivNifWithSo = filename:join(PrivDir, "lib" ++ ?NIF_LIB ++ ".so"),
            PrivNifWithDylib = filename:join(PrivDir, "lib" ++ ?NIF_LIB ++ ".dylib"),

            NifPath = case {filelib:is_file(PrivNifWithSo), filelib:is_file(PrivNifWithDylib)} of
                {true, _} ->
                    %% Return path WITHOUT extension - Erlang will add .so
                    filename:join(PrivDir, "lib" ++ ?NIF_LIB);
                {_, true} ->
                    %% Create symlink with .so extension
                    case file:make_symlink("lib" ++ ?NIF_LIB ++ ".dylib", PrivNifWithSo) of
                        ok -> filename:join(PrivDir, "lib" ++ ?NIF_LIB);
                        _ -> filename:join(PrivDir, "lib" ++ ?NIF_LIB)
                    end;
                _ ->
                    %% Try development path
                    DevDir = filename:join([code:root_dir(), "..", "..", "rust", "target", "release"]),
                    DevNifWithSo = filename:join(DevDir, "lib" ++ ?NIF_LIB ++ ".so"),
                    DevNifWithDylib = filename:join(DevDir, "lib" ++ ?NIF_LIB ++ ".dylib"),

                    case {filelib:is_file(DevNifWithSo), filelib:is_file(DevNifWithDylib)} of
                        {true, _} -> filename:join(DevDir, "lib" ++ ?NIF_LIB);
                        {_, true} ->
                            %% Create symlink
                            case file:make_symlink("lib" ++ ?NIF_LIB ++ ".dylib", DevNifWithSo) of
                                ok -> filename:join(DevDir, "lib" ++ ?NIF_LIB);
                                _ -> filename:join(DevDir, "lib" ++ ?NIF_LIB)
                            end;
                        _ -> ?NIF_LIB
                    end
            end,
            NifPath;
        Path ->
            Path
    end.

priv_dir() ->
    case code:priv_dir(?MODULE) of
        {error, _} ->
            EbinDir = filename:dirname(code:which(?MODULE)),
            filename:join(filename:dirname(EbinDir), "priv");
        Dir ->
            Dir
    end.

%%%===================================================================
%%% NIF Stubs - Replaced by Rust when loaded
%%%===================================================================

%% @doc Start/reload the NIF
start() ->
    init(),
    ping().

%% @doc Health check
-spec ping() -> {ok, string()} | {error, term()}.
ping() ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get NIF version
-spec nif_version() -> {ok, string()} | {error, term()}.
nif_version() ->
    erlang:nif_error(nif_not_loaded).

%% @doc Parse OCEL2 JSON and return a handle
-spec parse_ocel2_json(binary()) -> {ok, reference()} | {error, term()}.
parse_ocel2_json(_JsonBinary) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get event count from OCEL log
-spec log_event_count(reference()) -> {ok, non_neg_integer()} | {error, term()}.
log_event_count(_Handle) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get object count from OCEL log
-spec log_object_count(reference()) -> {ok, non_neg_integer()} | {error, term()}.
log_object_count(_Handle) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get event type statistics as JSON
-spec log_event_type_stats(reference()) -> {ok, string()} | {error, term()}.
log_event_type_stats(_Handle) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Get object type statistics as JSON
-spec log_object_type_stats(reference()) -> {ok, string()} | {error, term()}.
log_object_type_stats(_Handle) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Discover DFG from OCEL log
-spec discover_dfg(reference()) -> {ok, string()} | {error, term()}.
discover_dfg(_Handle) ->
    erlang:nif_error(nif_not_loaded).

%% @doc Check conformance using token replay
-spec check_conformance(reference(), string()) -> {ok, string()} | {error, term()}.
check_conformance(_Handle, _PetriNetPnml) ->
    erlang:nif_error(nif_not_loaded).
