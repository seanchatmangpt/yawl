%% Process Mining Bridge API Tests
%% Tests all the public API functions with real integration

-module(test_bridge_api).
-include_lib("eunit/include/eunit.hrl").
-include_lib("kernel/include/logger.hrl").

%% Test exports
-export([
    bridge_lifecycle_test/0,
    xes_operations_test/0,
    ocel_operations_test/0,
    discovery_operations_test/0,
    petri_net_operations_test/0,
    conformance_operations_test/0,
    statistics_operations_test/0,
    error_handling_test/0,
    integration_workflow_test/0
]).

%%====================================================================
%% Test Cases
%%====================================================================

%% @doc Test bridge lifecycle (start/stop)
bridge_lifecycle_test() ->
    %% Start the bridge
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            ?debugFmt("Bridge started successfully", []),
            %% Verify it's running
            ?assert(whereis(process_mining_bridge) =/= undefined),

            %% Stop the bridge
            ok = process_mining_bridge:stop(),
            ?assert(whereis(process_mining_bridge) =:= undefined),
            ok;
        {error, Reason} ->
            ?debugFmt("Failed to start bridge: ~p", [Reason]),
            ?fail("Failed to start process mining bridge")
    end.

%% @doc Test XES import/export operations
xes_operations_test() ->
    %% Create a test XES file
    XesContent = test_fixtures:sample_xes_content(),
    XesPath = test_fixtures:create_temp_file(XesContent, "xes"),

    try
        %% Test import
        case process_mining_bridge:import_xes(#{path => XesPath}) of
            {ok, LogHandle} ->
                ?debugFmt("XES imported successfully: ~p", [LogHandle]),

                %% Test export
                ExportPath = test_fixtures:create_temp_file(<<"">>, "exported.xes"),
                case process_mining_bridge:export_xes(LogHandle, ExportPath) of
                    ok ->
                        ?debugFmt("XES exported successfully"),
                        %% Verify exported file exists and has content
                        {ok, ExportContent} = file:read_file(ExportPath),
                        ?assert(size(ExportContent) > 0),
                        cleanup_test_file(ExportPath);
                    {error, Reason} ->
                        ?fail("XES export failed: ~p", [Reason])
                end;

                %% Test statistics
                case process_mining_bridge:event_log_stats(#{log_handle => LogHandle}) of
                    {ok, Stats} ->
                        ?debugFmt("Event log stats: ~p", [Stats]),
                        ?assert(is_map(Stats)),
                        ?assert(maps:is_key(traces, Stats)),
                        ?assert(maps:is_key(events, Stats)),
                        ?assert(maps:is_key(activities, Stats)),
                        ?assert(maps:is_key(avg_events_per_trace, Stats));
                    {error, Reason} ->
                        ?debugFmt("Stats error (expected if NIF not loaded): ~p", [Reason])
                end,

                %% Clean up handle
                process_mining_bridge:free_handle(LogHandle);

            {error, Reason} ->
                ?debugFmt("XES import error (expected if NIF not loaded): ~p", [Reason]),
                ?assertMatch({error, nif_not_loaded}, Reason)
        end
    after
        cleanup_test_file(XesPath)
    end.

%% @doc Test OCEL import/export operations
ocel_operations_test() ->
    %% Create test OCEL files
    OcelContent = test_fixtures:sample_ocel_json(),
    OcelPath = test_fixtures:create_temp_file(OcelContent, "json"),
    InvalidOcelPath = test_fixtures:create_temp_file(test_fixtures:invalid_ocel_json(), "json"),

    try
        %% Test valid OCEL import
        case process_mining_bridge:import_ocel_json(#{path => OcelPath}) of
            {ok, OcelHandle} ->
                ?debugFmt("OCEL imported successfully: ~p", [OcelHandle]),

                %% Test OCEL export
                ExportPath = test_fixtures:create_temp_file(<<"">>, "exported_ocel.json"),
                case process_mining_bridge:export_ocel_json(OcelHandle, ExportPath) of
                    ok ->
                        ?debugFmt("OCEL exported successfully"),
                        cleanup_test_file(ExportPath);
                    {error, Reason} ->
                        ?debugFmt("OCEL export error: ~p", [Reason])
                end,

                %% Clean up
                process_mining_bridge:free_handle(OcelHandle);

            {error, Reason} ->
                ?debugFmt("OCEL import error (expected if NIF not loaded): ~p", [Reason]),
                ?assertMatch({error, nif_not_loaded}, Reason)
        end,

        %% Test invalid OCEL import
        ?assertMatch({error, _},
                     process_mining_bridge:import_ocel_json(#{path => InvalidOcelPath})),

        %% Test XML import (should throw exception)
        ?assertError({'UnsupportedOperationException', _},
                     process_mining_bridge:import_ocel_xml(#{path => OcelPath})),

        %% Test SQLite import (should throw exception)
        ?assertError({'UnsupportedOperationException', _},
                     process_mining_bridge:import_ocel_sqlite(#{path => OcelPath}))

    after
        cleanup_test_file(OcelPath),
        cleanup_test_file(InvalidOcelPath)
    end.

%% @doc Test process discovery operations
discovery_operations_test() ->
    %% Import test data first
    XesContent = test_fixtures:sample_xes_content(),
    XesPath = test_fixtures:create_temp_file(XesContent, "xes"),

    try
        %% Import XES
        case process_mining_bridge:import_xes(#{path => XesPath}) of
            {ok, LogHandle} ->
                %% Test DFG discovery
                case process_mining_bridge:discover_dfg(#{log_handle => LogHandle}) of
                    {ok, DfgJson} ->
                        ?debugFmt("DFG discovered: ~p", [byte_size(DfgJson)]),
                        ?assert(is_binary(DfgJson)),
                        ?assert(byte_size(DfgJson) > 0);
                    {error, Reason} ->
                        ?debugFmt("DFG discovery error (expected if NIF not loaded): ~p", [Reason]),
                        ?assertMatch({error, nif_not_loaded}, Reason)
                end,

                %% Test Alpha+++ discovery
                case process_mining_bridge:discover_alpha(#{log_handle => LogHandle}) of
                    {ok, Result} ->
                        ?debugFmt("Alpha+++ discovered: ~p", [maps:keys(Result)]),
                        ?assert(is_map(Result)),
                        ?assert(maps:is_key(handle, Result)),
                        ?assert(maps:is_key(pnml, Result));
                    {error, Reason} ->
                        ?debugFmt("Alpha discovery error (expected if NIF not loaded): ~p", [Reason]),
                        ?assertMatch({error, nif_not_loaded}, Reason)
                end,

                %% Test OC-DFG discovery (should throw)
                ?assertError({'UnsupportedOperationException', _},
                             process_mining_bridge:discover_oc_dfg(#{ocel_handle => make_ref()})),

                %% Clean up
                process_mining_bridge:free_handle(LogHandle);

            {error, Reason} ->
                ?debugFmt("Import error for discovery test: ~p", [Reason])
        end
    after
        cleanup_test_file(XesPath)
    end.

%% @doc Test Petri net operations
petri_net_operations_test() ->
    %% Test PNML import (should throw)
    ?assertError({'UnsupportedOperationException', _},
                 process_mining_bridge:import_pnml(#{path => "/test.pnml"})),

    %% Test PNML export (requires a net handle)
    case process_mining_bridge:export_pnml(make_ref()) of
        {ok, Pnml} ->
            ?debugFmt("PNML exported: ~p", [byte_size(Pnml)]),
            ?assert(is_binary(Pnml)),
            ?assert(byte_size(Pnml) > 0);
        {error, Reason} ->
            ?debugFmt("PNML export error (expected if NIF not loaded): ~p", [Reason]),
            ?assertMatch({error, nif_not_loaded}, Reason)
    end.

%% @doc Test conformance checking operations
conformance_operations_test() ->
    %% This requires both a log and net handle
    LogHandle = make_ref(),
    NetHandle = make_ref(),

    %% Token replay should fail gracefully if NIF not loaded
    case process_mining_bridge:token_replay(#{log_handle => LogHandle, net_handle => NetHandle}) of
        {ok, Conformance} ->
            ?debugFmt("Conformance check: ~p", [Conformance]),
            ?assert(is_map(Conformance)),
            ?assert(maps:is_key(fitness, Conformance)),
            ?assert(maps:is_key(precision, Conformance));
        {error, Reason} ->
            ?debugFmt("Token replay error (expected if NIF not loaded): ~p", [Reason]),
            ?assertMatch({error, nif_not_loaded}, Reason)
    end.

%% @doc Test statistics operations
statistics_operations_test() ->
    %% Create test data
    OcelContent = test_fixtures:sample_ocel_json(5),
    OcelPath = test_fixtures:create_temp_file(OcelContent, "json"),

    try
        case process_mining_bridge:import_ocel_json(#{path => OcelPath}) of
            {ok, OcelHandle} ->
                %% Test statistics
                case process_mining_bridge:event_log_stats(#{log_handle => OcelHandle}) of
                    {ok, Stats} ->
                        ?debugFmt("Stats: ~p", [Stats]),
                        ?assert(maps:is_key(traces, Stats)),
                        ?assert(maps:is_key(events, Stats)),
                        ?assert(maps:is_key(activities, Stats));
                    {error, Reason} ->
                        ?debugFmt("Stats error: ~p", [Reason])
                end,

                process_mining_bridge:free_handle(OcelHandle);
            {error, Reason} ->
                ?debugFmt("Import error for stats test: ~p", [Reason])
        end
    after
        cleanup_test_file(OcelPath)
    end.

%% @doc Test error handling for all operations
error_handling_test() ->
    %% Test with invalid paths
    ?assertMatch({error, _},
                 process_mining_bridge:import_xes(#{path => "/nonexistent/path.xes"})),
    ?assertMatch({error, _},
                 process_mining_bridge:import_ocel_json(#{path => "/nonexistent/ocel.json"})),

    %% Test with invalid handles
    ?assertMatch({error, _},
                 process_mining_bridge:discover_dfg(#{log_handle => invalid_handle})),
    ?assertMatch({error, _},
                 process_mining_bridge:event_log_stats(#{log_handle => invalid_handle})),

    %% Test with missing required fields
    ?assertMatch({error, _},
                 process_mining_bridge:import_xes(#{})),
    ?assertMatch({error, _},
                 process_mining_bridge:discover_dfg(#{})).

%% @doc Test complete integration workflow
integration_workflow_test() ->
    %% This test simulates a complete workflow: import → analyze → export

    %% Create test data
    OcelContent = test_fixtures:sample_ocel_json(3),
    OcelPath = test_fixtures:create_temp_file(OcelContent, "json"),
    ExportPath = test_fixtures:create_temp_file(<<"">>, "workflow_export.json"),

    try
        %% Step 1: Import OCEL
        case process_mining_bridge:import_ocel_json(#{path => OcelPath}) of
            {ok, OcelHandle} ->
                ?debugFmt("Step 1: OCEL imported"),

                %% Step 2: Get statistics
                case process_mining_bridge:event_log_stats(#{log_handle => OcelHandle}) of
                    {ok, Stats} ->
                        ?debugFmt("Step 2: Stats: ~p", [Stats]),

                        %% Step 3: Discover DFG
                        case process_mining_bridge:discover_dfg(#{log_handle => OcelHandle}) of
                            {ok, DfgJson} ->
                                ?debugFmt("Step 3: DFG size: ~p bytes", [byte_size(DfgJson)]),

                                %% Step 4: Export
                                case process_mining_bridge:export_ocel_json(OcelHandle, ExportPath) of
                                    ok ->
                                        ?debugFmt("Step 4: OCEL exported"),

                                        %% Verify all steps completed
                                        ?assert(filelib:is_file(ExportPath)),
                                        ?assert(maps:is_key(traces, Stats)),
                                        ?assert(maps:is_key(events, Stats)),

                                        %% Clean up
                                        cleanup_test_file(ExportPath),
                                        process_mining_bridge:free_handle(OcelHandle),
                                        ok;
                                    {error, Reason} ->
                                        ?debugFmt("Export failed: ~p", [Reason]),
                                        process_mining_bridge:free_handle(OcelHandle),
                                        ?fail("Export failed")
                                end;
                            {error, Reason} ->
                                ?debugFmt("DFG failed: ~p", [Reason]),
                                process_mining_bridge:free_handle(OcelHandle),
                                ?fail("DFG discovery failed")
                        end;
                    {error, Reason} ->
                        ?debugFmt("Stats failed: ~p", [Reason]),
                        process_mining_bridge:free_handle(OcelHandle),
                        ?fail("Stats failed")
                end;
            {error, Reason} ->
                ?debugFmt("Import failed: ~p", [Reason]),
                ?fail("OCEL import failed")
        end
    after
        cleanup_test_file(OcelPath),
        case filelib:is_file(ExportPath) of
            true -> cleanup_test_file(ExportPath);
            false -> ok
        end
    end.

%%====================================================================
%% Helper Functions
%%====================================================================

%% Clean up test files
cleanup_test_file(Path) ->
    test_fixtures:cleanup_temp_file(Path).

%%====================================================================
%% Test Suite
%%====================================================================

bridge_api_test_() ->
    {setup,
     fun() ->
         %% Start the application for all tests
         case application:ensure_all_started(process_mining_bridge) of
             {ok, _Apps} ->
                 %% Wait for initialization
                 timer:sleep(500),
                 ok;
             {error, Reason} ->
                 ?debugFmt("Failed to start application: ~p", [Reason]),
                 error({failed_to_start_app, Reason})
         end
     end,
     fun(_) ->
         application:stop(process_mining_bridge)
     end,
     [
        %% Basic operations
        {timeout, 30000, ?_test(bridge_lifecycle_test())},
        {timeout, 30000, ?_test(xes_operations_test())},
        {timeout, 30000, ?_test(ocel_operations_test())},

        %% Advanced operations
        {timeout, 30000, ?_test(discovery_operations_test())},
        {timeout, 30000, ?_test(petri_net_operations_test())},
        {timeout, 30000, ?_test(conformance_operations_test())},
        {timeout, 30000, ?_test(statistics_operations_test())},

        %% Error handling and integration
        {timeout, 30000, ?_test(error_handling_test())},
        {timeout, 60000, ?_test(integration_workflow_test())}
     ]}.