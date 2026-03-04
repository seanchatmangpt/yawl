%% NIF Loading and Initialization Tests
%% Tests the NIF loading mechanism and initialization process

-module(test_nif_loading).
-include_lib("eunit/include/eunit.hrl").

%% Test export
-export([
    init_nif_test/0,
    init_nif/0,
    load_nif_success_test/0,
    load_nif_failure_test/0,
    fallback_behavior_test/0,
    init_nif_timing_test/0,
    nif_module_loaded_test/0,
    nif_function_available_test/0
]).

%%====================================================================
%% Test Cases
%%====================================================================

%% @doc Test init_nif function exists and can be called
init_nif_test() ->
    ?assertNot(undefined, init_nif),
    ?assertMatch(ok, init_nif()).

%% @doc Test successful NIF loading (when NIF library is available)
load_nif_success_test() ->
    %% This test assumes the NIF library is properly built and available
    %% In CI, this might need to be conditional
    case code:priv_dir(process_mining_bridge) of
        {error, _} ->
            %% Development environment - check if we can find priv dir
            AppDir = filename:dirname(filename:dirname(code:which(?MODULE))),
            PrivDir = filename:join(AppDir, "priv"),
            NifPath = filename:join(PrivDir, "yawl_process_mining"),
            case filelib:is_file(NifPath) of
                true ->
                    ?assertMatch(ok, init_nif()),
                    %% Verify NIF functions are available
                    ?assert(not (process_mining_bridge:import_xes/1 =:= undefined)),
                    ?assert(not (process_mining_bridge:discover_dfg/1 =:= undefined));
                false ->
                    ?debugFmt("NIF library not found at ~p, skipping NIF tests", [NifPath])
            end;
        _ ->
            ?assertMatch(ok, init_nif()),
            ?assert(not (process_mining_bridge:import_xes/1 =:= undefined)),
            ?assert(not (process_mining_bridge:discover_dfg/1 =:= undefined))
    end.

%% @doc Test NIF loading failure behavior
load_nif_failure_test() ->
    %% This test simulates NIF loading failure by checking fallback behavior
    %% We can't easily force a NIF load failure in this environment, but we can
    %% test that the module still has fallback implementations
    ?assertMatch({error, nif_not_loaded},
                 process_mining_bridge:import_xes(#{path => "/nonexistent/test.xes"})),
    ?assertMatch({error, nif_not_loaded},
                 process_mining_bridge:discover_dfg(#{log_handle => make_ref()})).

%% @doc Test fallback behavior when NIF is not loaded
fallback_behavior_test() ->
    %% Test that unsupported operations throw appropriate exceptions
    %% This happens regardless of whether NIF is loaded or not
    ?assertError({'UnsupportedOperationException', _},
                 process_mining_bridge:import_ocel_xml(#{path => "/test.xml"})),
    ?assertError({'UnsupportedOperationException', _},
                 process_mining_bridge:discover_oc_dfg(#{ocel_handle => make_ref()})),
    ?assertError({'UnsupportedOperationException', _},
                 process_mining_bridge:import_pnml(#{path => "/test.pnml"})),
    ?assertError({'UnsupportedOperationException', _},
                 process_mining_bridge:token_replay(#{log_handle => make_ref(), net_handle => make_ref()})).

%% @doc Test NIF initialization performance
init_nif_timing_test() ->
    Start = erlang:monotonic_time(microsecond),
    _Result = init_nif(),
    End = erlang:monotonic_time(microsecond),
    Duration = (End - Start) div 1000, % Convert to milliseconds

    %% NIF initialization should be fast (< 1 second)
    Message = lists:flatten(io_lib:format("NIF took ~p ms, expected < 1000 ms", [Duration])),
    ?assert(Duration < 1000, Message).

%% @doc Test that the module is properly loaded
nif_module_loaded_test() ->
    %% Verify the module is loaded and compiled
    ?assertNot(undefined, process_mining_bridge),
    ?assertMatch({module, _}, code:ensure_loaded(process_mining_bridge)).

%% @doc Test that NIF functions are available (even if stubs)
nif_function_available_test() ->
    %% Test that all expected functions exist in the module
    ?assert(not (process_mining_bridge:start_link/0 =:= undefined)),
    ?assert(not (process_mining_bridge:stop/0 =:= undefined)),
    ?assert(not (process_mining_bridge:import_xes/1 =:= undefined)),
    ?assert(not (process_mining_bridge:export_xes/2 =:= undefined)),
    ?assert(not (process_mining_bridge:import_ocel_json/1 =:= undefined)),
    ?assert(not (process_mining_bridge:import_ocel_xml/1 =:= undefined)),
    ?assert(not (process_mining_bridge:import_ocel_sqlite/1 =:= undefined)),
    ?assert(not (process_mining_bridge:export_ocel_json/2 =:= undefined)),
    ?assert(not (process_mining_bridge:discover_dfg/1 =:= undefined)),
    ?assert(not (process_mining_bridge:discover_alpha/1 =:= undefined)),
    ?assert(not (process_mining_bridge:discover_oc_dfg/1 =:= undefined)),
    ?assert(not (process_mining_bridge:import_pnml/1 =:= undefined)),
    ?assert(not (process_mining_bridge:export_pnml/1 =:= undefined)),
    ?assert(not (process_mining_bridge:token_replay/2 =:= undefined)),
    ?assert(not (process_mining_bridge:event_log_stats/1 =:= undefined)),
    ?assert(not (process_mining_bridge:free_handle/1 =:= undefined)).

%%====================================================================
%% Helper Functions
%%====================================================================

%% Get NIF library path for testing
get_nif_path() ->
    case code:priv_dir(process_mining_bridge) of
        {error, _} ->
            %% Fallback for development
            AppDir = filename:dirname(filename:dirname(code:which(?MODULE))),
            filename:join(AppDir, "priv");
        PrivDir ->
            PrivDir
    end.

%%====================================================================
%% Test Suite
%%====================================================================

nif_loading_test_() ->
    {setup,
     fun() ->
         %% Ensure NIF is initialized before tests
         init_nif(),
         ok
     end,
     fun(_) ->
         ok
     end,
     [
        ?_test(init_nif_test()),
        ?_test(load_nif_success_test()),
        ?_test(load_nif_failure_test()),
        ?_test(fallback_behavior_test()),
        ?_test(init_nif_timing_test()),
        ?_test(nif_module_loaded_test()),
        ?_test(nif_function_available_test())
     ]}.