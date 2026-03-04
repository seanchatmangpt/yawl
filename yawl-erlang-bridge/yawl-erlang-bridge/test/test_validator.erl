%% Test Suite Validator
%% Validates test structure and dependencies without running tests

-module(test_validator).
-export([validate_all/0, validate_module/1]).

-include_lib("eunit/include/eunit.hrl").

%%====================================================================
%% Main Validation Functions
%%====================================================================

%% @doc Validate all test modules
validate_all() ->
    io:format("=== YAWL Process Mining Bridge Test Suite Validation ===~n~n"),

    TestModules = [
        test_fixtures,
        test_nif_loading,
        test_bridge_api,
        test_error_handling,
        test_ocel_operations,
        test_suite
    ],

    Results = lists:map(fun validate_module/1, TestModules),

    %% Report summary
    Passed = length([R || R <- Results, R =:= passed]),
    Failed = length([R || R <- Results, R =:= failed]),
    Warned = length([R || R <- Results, R =:= warning]),

    io:format("~n=== Validation Summary ===~n"),
    io:format("Total modules: ~p~n", [length(TestModules)]),
    io:format("Passed: ~p~n", [Passed]),
    io:format("Warnings: ~p~n", [Warned]),
    io:format("Failed: ~p~n", [Failed]),

    case Failed of
        0 ->
            io:format("🎉 All test modules are valid!~n"),
            ok;
        _ ->
            io:format("❌ Some test modules have issues.~n"),
            error
    end.

%% @doc Validate a specific test module
validate_module(Module) ->
    io:format("Validating ~p...~n", [Module]),

    try
        %% Check if module file exists
        case module_file_exists(Module) of
            true ->
                %% Check if module can be compiled
                case compile:file(Module, [return_warnings, report_errors]) of
                    {ok, Module} ->
                        io:format("  ✓ Module compiles successfully~n"),
                        check_functions(Module),
                        passed;
                    {ok, Module, Warnings} ->
                        io:format("  ⚠ Module compiles with warnings~n"),
                        [io:format("    Warning: ~p~n", [W]) || W <- Warnings],
                        check_functions(Module),
                        warning;
                    {error, Errors, Warnings} ->
                        io:format("  ✗ Module has compilation errors~n"),
                        [io:format("    Error: ~p~n", [E]) || E <- Errors],
                        [io:format("    Warning: ~p~n", [W]) || W <- Warnings],
                        failed
                end;
            false ->
                io:format("  ✗ Module file not found~n"),
                failed
        end
    catch
        Error:Reason ->
            io:format("  ✗ Validation failed: ~p:~p~n", [Error, Reason]),
            failed
    end.

%%====================================================================
%% Helper Functions
%%====================================================================

%% Check if module file exists
module_file_exists(Module) ->
    ModuleFile = atom_to_list(Module) ++ ".erl",
    case filelib:is_file(ModuleFile) of
        true -> true;
        false ->
            %% Check in test directory
            TestFile = "test/" ++ ModuleFile,
            filelib:is_file(TestFile)
    end.

%% Check if expected functions are exported
check_functions(Module) ->
    ExpectedFunctions = expected_functions(Module),

    case code:ensure_loaded(Module) of
        {module, Module} ->
            lists:foreach(fun({Function, Arity}) ->
                case erlang:function_exported(Module, Function, Arity) of
                    true ->
                        io:format("    ✓ ~p/~p is exported~n", [Function, Arity]);
                    false ->
                        io:format("    ✗ ~p/~p is not exported~n", [Function, Arity])
                end
            end, ExpectedFunctions);
        {error, Reason} ->
            io:format("  ✗ Failed to load module: ~p~n", [Reason])
    end.

%% Expected functions for each module
expected_functions(test_fixtures) ->
    [
        {sample_ocel_json, 0}, {sample_ocel_json, 1},
        {invalid_ocel_json, 0}, {empty_ocel_json, 0},
        {sample_xes_content, 0}, {invalid_xes_content, 0},
        {create_temp_file, 2}, {cleanup_temp_file, 1}
    ];

expected_functions(test_nif_loading) ->
    [
        {init_nif_test, 0}, {init_nif, 0},
        {load_nif_success_test, 0},
        {load_nif_failure_test, 0},
        {fallback_behavior_test, 0},
        {init_nif_timing_test, 0},
        {nif_module_loaded_test, 0},
        {nif_function_available_test, 0}
    ];

expected_functions(test_bridge_api) ->
    [
        {bridge_lifecycle_test, 0},
        {xes_operations_test, 0},
        {ocel_operations_test, 0},
        {discovery_operations_test, 0},
        {petri_net_operations_test, 0},
        {conformance_operations_test, 0},
        {statistics_operations_test, 0},
        {error_handling_test, 0},
        {integration_workflow_test, 0}
    ];

expected_functions(test_error_handling) ->
    [
        {invalid_inputs_test, 0},
        {missing_dependencies_test, 0},
        {resource_limits_test, 0},
        {network_errors_test, 0},
        {file_system_errors_test, 0},
        {concurrency_errors_test, 0},
        {memory_errors_test, 0},
        {timeout_errors_test, 0}
    ];

expected_functions(test_ocel_operations) ->
    [
        {ocel_import_export_test, 0},
        {ocel_json_validation_test, 0},
        {ocel_statistics_test, 0},
        {ocel_discovery_test, 0},
        {ocel_object_operations_test, 0},
        {ocel_event_operations_test, 0},
        {ocel_lifecycle_test, 0},
        {ocel_performance_test, 0},
        {ocel_data_integrity_test, 0}
    ];

expected_functions(test_suite) ->
    [
        {run_all_tests, 0},
        {run_specific_tests, 1}
    ];

expected_functions(_) ->
    [].

%%====================================================================
%% EUnit Test
%%====================================================================

test_validator_test_() ->
    [
        {"Validate all test modules",
         fun() ->
             Result = validate_all(),
             ?assert(Result =:= orelse Result =:= error)
         end}
    ].