%% @copyright 2026 YAWL Foundation
%% @author YAWL Erlang Team
%% @doc Rust4PM NIF loading stub

-module(rust4pm).

-on_load(load_nif/0).

%%====================================================================
%% NIF Interface (will be loaded from Rust shared library)
%%====================================================================

%% OCEL JSON import functions
-export([import_ocel_json_path/1]).

%% Process mining functions
-export([slim_link_ocel/1, discover_oc_declare/1, token_replay/2]).

%% Alpha++ discovery
-export([alpha_plus_plus_discover/1]).

%% Fitness scoring
-export([get_fitness_score/1]).

%%====================================================================
%% NIF stubs (return error until NIF is loaded)
%%====================================================================

import_ocel_json_path(_Path) ->
    nif_not_loaded().

slim_link_ocel(_Table) ->
    nif_not_loaded().

discover_oc_declare(_Table) ->
    nif_not_loaded().

token_replay(_Table, _Config) ->
    nif_not_loaded().

alpha_plus_plus_discover(_OcelId) ->
    nif_not_loaded().

get_fitness_score(_ConformanceId) ->
    nif_not_loaded().

%%====================================================================
%% NIF loading
%%====================================================================

load_nif() ->
    %% Attempt to load the NIF shared library
    %% The shared library should be named 'rust4pm' and be in the path
    %% or specified via -pa flag
    case erlang:load_nif("./rust4pm", 0) of
        ok -> ok;
        {error, {load_failed, _}} = Error ->
            %% Try alternative paths
            case erlang:load_nif("./deps/rust4pm/ebin/rust4pm", 0) of
                ok -> ok;
                {error, {load_failed, _}} = Error2 ->
                    case erlang:load_nif("/usr/local/lib/erlang/lib/rust4pm-1.0/priv/rust4pm", 0) of
                        ok -> ok;
                        {error, _} = Error3 ->
                            Error3
                    end
            end
    end.

%%====================================================================
%% Internal functions
%%====================================================================

nif_not_loaded() ->
    {error, nif_not_loaded}.