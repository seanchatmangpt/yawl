%% Comprehensive Test Suite for Process Mining Bridge
%% Main test coordinator that runs all tests

-module(test_suite).
-export([run_all_tests/0, run_specific_tests/1]).

%% Import all test modules
-include_lib("eunit/include/eunit.hrl").

%%====================================================================
%% Main Test Functions
%%====================================================================

%% @doc Run all tests in the suite
run_all_tests() ->
    io:format("=== YAWL Process Mining Bridge Test Suite ===~n~n"),

    %% Start required applications
    start_applications(),

    %% Run test suites
    Results = [
        run_nif_tests(),
        run_api_tests(),
        run_error_tests(),
        run_ocel_tests(),
        run_jtbd_tests()
    ],

    %% Report summary
    report_test_summary(Results),

    %% Cleanup
    cleanup_applications().

%% @doc Run specific test modules
run_specific_tests(TestModules) ->
    io:format("=== Running specific tests: ~p ===~n~n", [TestModules]),

    %% Start required applications
    start_applications(),

    %% Run requested test modules
    Results = [run_specific_module(Mod) || Mod <- TestModules],

    %% Report summary
    report_test_summary(Results),

    %% Cleanup
    cleanup_applications().

%%====================================================================
%% Test Execution Functions
%%====================================================================

%% Run NIF loading tests
run_nif_tests() ->
    io:format("Running NIF loading tests...~n"),
    case eunit:test(test_nif_loading) of
        {ok, Results} ->
            {test_nif_loading, Results};
        {error, Reason} ->
            {test_nif_loading, {error, Reason}}
    end.

%% Run API tests
run_api_tests() ->
    io:format("Running API tests...~n"),
    case eunit:test(test_bridge_api) of
        {ok, Results} ->
            {test_bridge_api, Results};
        {error, Reason} ->
            {test_bridge_api, {error, Reason}}
    end.

%% Run error handling tests
run_error_tests() ->
    io:format("Running error handling tests...~n"),
    case eunit:test(test_error_handling) of
        {ok, Results} ->
            {test_error_handling, Results};
        {error, Reason} ->
            {test_error_handling, {error, Reason}}
    end.

%% Run OCEL operation tests
run_ocel_tests() ->
    io:format("Running OCEL operation tests...~n"),
    case eunit:test(test_ocel_operations) of
        {ok, Results} ->
            {test_ocel_operations, Results};
        {error, Reason} ->
            {test_ocel_operations, {error, Reason}}
    end.

%% Run JTBD tests
run_jtbd_tests() ->
    io:format("Running JTBD tests...~n"),
    case eunit:test(jtbd_1_dfg_discovery) of
        {ok, Results} ->
            {jtbd_1_dfg_discovery, Results};
        {error, Reason} ->
            {jtbd_1_dfg_discovery, {error, Reason}}
    end.

%% Run specific test module
run_specific_module(Module) ->
    io:format("Running ~p tests...~n", [Module]),
    case eunit:test(Module) of
        {ok, Results} ->
            {Module, Results};
        {error, Reason} ->
            {Module, {error, Reason}}
    end.

%%====================================================================
%% Application Management
%%====================================================================

%% Start required applications
start_applications() ->
    io:format("Starting required applications...~n"),

    %% Start core applications
    application:start(sasl),
    application:start(crypto),
    application:start(mnesia),

    %% Initialize M schema if needed
    case mnesia:create_schema([node()]) of
        ok -> io:format("  Mnesia schema created~n");
        {error, {already_exists, _}} -> io:format("  Mnesia schema exists~n")
    end,

    %% Create handle_registry table if needed
    case mnesia:create_table(handle_registry, [
        {attributes, record_info(fields, handle_registry)},
        {disc_copies, [node()]},
        {type, set}
    ]) of
        {atomic, ok} -> io:format("  handle_registry table created~n");
        {aborted, {already_exists, handle_registry}} ->
            io:format("  handle_registry table exists~n")
    end,

    %% Start the process mining bridge application
    case application:ensure_all_started(process_mining_bridge) of
        {ok, Apps} ->
            io:format("  Applications started: ~p~n", [Apps]),
            timer:sleep(1000), % Wait for initialization
            ok;
        {error, Reason} ->
            io:format("  Failed to start applications: ~p~n", [Reason]),
            error({failed_to_start_apps, Reason})
    end.

%% Stop applications
cleanup_applications() ->
    io:format("Stopping applications...~n"),
    application:stop(process_mining_bridge),
    application:stop(mnesia),
    application:stop(crypto),
    application:stop(sasl).

%%====================================================================
%% Test Reporting
%%====================================================================

%% Report test results summary
report_test_summary(Results) ->
    io:format("~n=== Test Results Summary ===~n"),

    Passed = 0,
    Failed = 0,

    lists:foreach({Module, Result}, Results) ->
        case Result of
            {ok, TestResults} ->
                io:format("✓ ~p: PASSED~n", [Module]),
                Passed = Passed + 1,
                io:format("  Details: ~p~n", [TestResults]);
            {error, Reason} ->
                io:format("✗ ~p: FAILED~n", [Module]),
                Failed = Failed + 1,
                io:format("  Reason: ~p~n", [Reason])
        end
    end,

    io:format("~n=== Summary ===~n"),
    io:format("Total test modules: ~p~n", [length(Results)]),
    io:format("Passed: ~p~n", [Passed]),
    io:format("Failed: ~p~n", [Failed]),

    case Failed of
        0 -> io:format("🎉 All tests passed!~n");
        _ -> io:format("❌ Some tests failed.~n")
    end.

%%====================================================================
%% Command Line Interface
%%====================================================================

%% Main entry point
main(_Args) ->
    case init:get_plain_arguments() of
        ["all"] ->
            run_all_tests();
        [Module] ->
            run_specific_tests([list_to_atom(Module)]);
        ["specific" | Modules] ->
            run_specific_tests(lists:map(fun list_to_atom/1, Modules));
        _ ->
            io:format("Usage:~n"),
            io:format("  erl -pa ebin -pa _build/default/lib/*/ebin~n"),
            io:format("  c(test_suite).~n"),
            io:format("  test_suite:run_all_tests().~n"),
            io:format("  test_suite:run_specific_tests([test_module]).~n"),
            io:format("~nAvailable test modules:~n"),
            io:format("  - test_nif_loading~n"),
            io:format("  - test_bridge_api~n"),
            io:format("  - test_error_handling~n"),
            io:format("  - test_ocel_operations~n"),
            io:format("  - test_suite (all tests)~n")
    end.

%%====================================================================
%% Test Execution Helpers
%%====================================================================

%% Run with proper error handling
safe_run_test(Module, Fun) ->
    try
        Result = Fun(),
        {ok, Result}
    catch
        Error:Reason ->
            {error, {Error, Reason, erlang:get_stacktrace()}}
    end.

%%====================================================================
%% EUnit Integration
%%====================================================================

%% EUnit test suite
test_suite_test_() ->
    {setup,
     fun() ->
         %% Setup for EUnit tests
         start_applications()
     end,
     fun(_) ->
         %% Cleanup after EUnit tests
         cleanup_applications()
     end,
     [
        %% Run all test modules
        {timeout, 300000,
         fun() ->
             run_all_tests()
         end}
     ]}.

%%====================================================================
%% Test Configuration
%%====================================================================

%% Test timeout configuration (in milliseconds)
-define(TEST_TIMEOUT, 30000).

%% Maximum concurrent test processes
-define(MAX_CONCURRENT, 10).

%%====================================================================
%% End of File
%%====================================================================