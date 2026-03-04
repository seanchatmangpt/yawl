#!/usr/bin/env escript
%% -*- erlang -*-
%% Simple test runner for YAWL Process Mining Bridge

main(_) ->
    io:format("=== YAWL Process Mining Bridge Test Runner ===~n~n"),

    %% Test if we can compile basic modules
    TestModules = [
        test_fixtures,
        test_nif_loading,
        test_bridge_api,
        test_error_handling,
        test_ocel_operations,
        test_suite
    ],

    Results = lists:map(fun(Module) ->
        case compile:file(Module, [return_warnings, report_errors]) of
            {ok, Module} ->
                io:format("✓ ~p: Compiled successfully~n", [Module]),
                {Module, success};
            {ok, Module, Warnings} ->
                io:format("⚠ ~p: Compiled with warnings: ~p~n", [Module, Warnings]),
                {Module, warning};
            {error, Errors, Warnings} ->
                io:format("✗ ~p: Failed to compile~n", [Module]),
                io:format("  Errors: ~p~n", [Errors]),
                io:format("  Warnings: ~p~n", [Warnings]),
                {Module, error}
        end
    end, TestModules),

    %% Report summary
    Passed = length([R || R <- Results, element(2, R) =:= success]),
    Warned = length([R || R <- Results, element(2, R) =:= warning]),
    Failed = length([R || R <- Results, element(2, R) =:= error]),

    io:format("~n=== Test Compilation Summary ===~n"),
    io:format("Total modules: ~p~n", [length(TestModules)]),
    io:format("Passed: ~p~n", [Passed]),
    io:format("Warnings: ~p~n", [Warned]),
    io:format("Failed: ~p~n", [Failed]),

    case Failed of
        0 ->
            io:format("🎉 All modules compiled successfully!~n");
        _ ->
            io:format("❌ Some modules failed to compile.~n")
    end,

    %% Test function availability
    io:format("~n=== Testing Function Availability ===~n"),
    test_functions(),

    %% Cleanup
    lists:foreach(fun(Module) ->
        case code:purge(Module) of
            true -> io:format("Purged ~p~n", [Module]);
            false -> ok
        end,
        case code:delete(Module) of
            true -> io:format("Deleted ~p~n", [Module]);
            false -> ok
        end
    end, TestModules),

    halt().

test_functions() ->
    %% Test if functions are exported correctly
    ExpectedFunctions = [
        {test_bridge_api, [bridge_lifecycle_test, xes_operations_test, ocel_operations_test]},
        {test_nif_loading, [init_nif_test, load_nif_success_test]},
        {test_ocel_operations, [ocel_import_export_test, ocel_data_integrity_test]},
        {test_error_handling, [invalid_inputs_test, missing_dependencies_test]}
    ],

    lists:foreach(fun({Module, Functions}) ->
        case code:ensure_loaded(Module) of
            {module, Module} ->
                lists:foreach(fun(Function) ->
                    case erlang:function_exported(Module, Function, 0) of
                        true ->
                            io:format("✓ ~p:~p/0 exists~n", [Module, Function]);
                        false ->
                            io:format("✗ ~p:~p/0 missing~n", [Module, Function])
                    end
                end, Functions);
            {error, Reason} ->
                io:format("✗ ~p failed to load: ~p~n", [Module, Reason])
        end
    end, ExpectedFunctions).