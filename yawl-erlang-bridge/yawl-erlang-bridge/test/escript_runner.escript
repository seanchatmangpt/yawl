#!/usr/bin/env escript
%% -*- erlang -*-
%%! -pa ebin -pa _build/default/lib/*/ebin

%% Escript runner for YAWL Process Mining Bridge tests
%% Provides easy execution of test suites from command line

main(Args) ->
    io:format("~n=== YAWL Process Mining Bridge Test Runner ===~n~n"),

    %% Parse arguments
    case parse_args(Args) of
        {help, _} ->
            print_usage();
        {test, Tests} ->
            run_tests(Tests);
        {error, Reason} ->
            io:format("Error: ~p~n", [Reason]),
            print_usage(),
            halt(1)
    end.

%% Parse command line arguments
parse_args([]) ->
    {test, all};
parse_args(["help" | _]) ->
    {help, all};
parse_args(["-h" | _]) ->
    {help, all};
parse_args(["--help" | _]) ->
    {help, all};
parse_args(["all" | _]) ->
    {test, all};
parse_args(Tests) ->
    %% Convert test names to atoms
    TestAtoms = lists:map(fun(Arg) ->
        case string:to_existing_atom(Arg) of
            {'error', _} ->
                io:format("Warning: Unknown test module '~p', skipping~n", [Arg]),
                false;
            Atom ->
                Atom
        end
    end, Tests),

    %% Filter out invalid modules
    ValidTests = [T || T <- TestAtoms, T =/= false],

    case ValidTests of
        [] ->
            {error, "No valid test modules specified"};
        _ ->
            {test, ValidTests}
    end.

%% Print usage instructions
print_usage() ->
    io:format("Usage: escript escript_runner.escript [options] [test_modules...]~n"),
    io:format("~nOptions:~n"),
    io:format("  help, -h, --help    Show this help message~n"),
    io:format("  all                 Run all test modules (default)~n"),
    io:format("~nTest Modules:~n"),
    io:format("  test_nif_loading   Test NIF loading and initialization~n"),
    io:format("  test_bridge_api    Test all public API functions~n"),
    io:format("  test_error_handling Test error handling paths~n"),
    io:format("  test_ocel_operations Test OCEL-specific operations~n"),
    io:format("  test_suite          Run all tests with summary~n"),
    io:format("~nExamples:~n"),
    io:format("  escript escript_runner.escript all~n"),
    io:format("  escript escript_runner.escript test_nif_loading test_bridge_api~n"),
    io:format("  escript escript_runner.escript test_ocel_operations~n"),
    io:format("~n").

%% Run the specified tests
run_tests(all) ->
    io:format("Running all tests...~n"),
    run_test_modules([test_nif_loading, test_bridge_api, test_error_handling, test_ocel_operations]);
run_tests(Tests) ->
    io:format("Running tests: ~p~n", [Tests]),
    run_test_modules(Tests).

%% Run individual test modules
run_test_modules(Modules) ->
    %% Start required applications
    case start_applications() of
        ok ->
            %% Load test modules
            LoadedModules = load_test_modules(Modules),

            %% Run tests
            Results = [run_single_test(Mod) || Mod <- LoadedModules],

            %% Report results
            report_results(Results, Modules),

            %% Cleanup
            cleanup_applications(),

            %% Exit with appropriate code
            exit_with_code(Results);
        {error, Reason} ->
            io:format("Failed to start applications: ~p~n", [Reason]),
            halt(1)
    end.

%% Load test modules
load_test_modules(Modules) ->
    io:format("Loading test modules...~n"),
    Loaded = lists:foldl(fun(Module, Acc) ->
        case Module:module_info(compile) of
            CompileInfo when is_list(CompileInfo) ->
                io:format("  ✓ ~p loaded~n", [Module]),
                [Module | Acc];
            _ ->
                io:format("  ✗ Failed to load ~p~n", [Module]),
                Acc
        end
    end, [], Modules),

    case Loaded of
        [] ->
            io:format("Error: No test modules could be loaded~n"),
            halt(1);
        _ ->
            Loaded
    end.

%% Run a single test module
run_single_test(Module) ->
    io:format("~n--- Running ~p ---~n", [Module]),

    %% Run tests with timeout
    try
        %% Use a supervisor process to handle timeouts
        Pid = spawn(fun() ->
            try
                %% Run EUnit tests
                case eunit:test(Module, [verbose]) of
                    {ok, Results} ->
                        io:format("  ✓ All tests passed~n"),
                        {Module, passed, Results};
                    {error, Reason} ->
                        io:format("  ✗ Tests failed: ~p~n", [Reason]),
                        {Module, failed, Reason}
                end
            catch
                Class:Error ->
                    io:format("  ✗ Test crashed: ~p:~p~n~p~n", [Class, Error, erlang:get_stacktrace()]),
                    {Module, crashed, {Class, Error}}
            end
        end),

        %% Wait for completion with timeout
        receive
            {Pid, Result} ->
                Result
        after
            300000 -> % 5 minute timeout
                io:format("  ✗ Test timed out after 5 minutes~n"),
                exit(Pid, kill),
                {Module, timeout, timeout}
        end
    catch
        Error:Reason ->
            io:format("  ✗ Failed to run tests: ~p:~p~n", [Error, Reason]),
            {Module, exception, {Error, Reason}}
    end.

%% Start required applications
start_applications() ->
    try
        %% Start core applications
        application:start(sasl),
        application:start(crypto),
        application:start(mnesia),

        %% Initialize Mnesia
        case mnesia:create_schema([node()]) of
            ok -> io:format("  Mnesia schema created~n");
            {error, {already_exists, _}} -> io:format("  Mnesia schema exists~n")
        end,

        %% Create handle_registry table
        case mnesia:create_table(handle_registry, [
            {attributes, record_info(fields, handle_registry)},
            {disc_copies, [node()]},
            {type, set}
        ]) of
            {atomic, ok} -> io:format("  handle_registry table created~n");
            {aborted, {already_exists, handle_registry}} ->
                io:format("  handle_registry table exists~n")
        end,

        %% Start process mining bridge
        case application:ensure_all_started(process_mining_bridge) of
            {ok, Apps} ->
                io:format("  Applications started: ~p~n", [Apps]),
                timer:sleep(1000), % Wait for initialization
                ok;
            {error, Reason} ->
                io:format("  Failed to start applications: ~p~n", [Reason]),
                {error, Reason}
        end
    catch
        Error:Reason ->
            io:format("  Exception starting applications: ~p:~p~n", [Error, Reason]),
            {error, Reason}
    end.

%% Stop applications
cleanup_applications() ->
    application:stop(process_mining_bridge),
    application:stop(mnesia),
    application:stop(crypto),
    application:stop(sasl).

%% Report test results
report_results(Results, OriginalModules) ->
    io:format("~n=== Test Results Summary ===~n"),

    Passed = [R || R <- Results, element(2, R) =:= passed],
    Failed = [R || R <- Results, element(2, R) =/= passed],

    io:format("Total test modules: ~p~n", [length(Results)]),
    io:format("Passed: ~p~n", [length(Passed)]),
    io:format("Failed: ~p~n", [length(Failed)]),

    %% Report failed tests in detail
    lists:foreach(fun({Module, _Status, Details}) ->
        case is_map(Details) of
            true ->
                %% EUnit results
                io:format("  ~p: ", [Module]),
                case maps:get(failed, Details, 0) of
                    0 -> io:format("✓ All tests passed~n");
                    N -> io:format("✗ ~p tests failed~n", [N])
                end;
            false ->
                %% Other failures
                io:format("  ~p: ✗ ~p~n", [Module, Details])
        end
    end, Failed),

    %% Module count verification
    if
        length(Results) < length(OriginalModules) ->
            Skipped = length(OriginalModules) - length(Results),
            io:format("Skipped modules: ~p~n", [Skipped]);
        true ->
            ok
    end.

%% Determine exit code based on results
exit_with_code(Results) ->
    Failed = [R || R <- Results, element(2, R) =/= passed],

    case Failed of
        [] ->
            io:format("~n🎉 All tests passed!~n"),
            halt(0);
        _ ->
            io:format("~n❌ Some tests failed.~n"),
            halt(1)
    end.

%% Emergency exit for serious errors
emergency_exit(Reason) ->
    io:format("Critical error: ~p~n", [Reason]),
    halt(1).