#!/usr/bin/env escript
%% -*- erlang -*-
%%! -pa ebin -pa _build/default/lib/*/ebin

%% Comprehensive Property Test Runner for OCEL Parsing
%% Runs all property tests and generates detailed report

main(_) ->
    io:format("=== Comprehensive OCEL Property Test Suite ===~n~n"),

    %% Initialize
    code:add_patha("../ebin"),
    code:add_patha("../_build/default/lib/*/ebin"),

    %% Load test modules
    io:format("Loading test modules...~n"),
    case load_test_modules() of
        ok ->
            io:format("✓ All modules loaded successfully~n~n");
        {error, Module, Reason} ->
            io:format("✗ Failed to load ~p: ~p~n", [Module, Reason]),
            halt(1)
    end,

    %% Run comprehensive test suite
    Results = run_comprehensive_tests(),

    %% Generate final report
    generate_report(Results),

    %% Determine final result
    case check_results(Results) of
        all_passed ->
            io:format("~n🎉 ALL PROPERTY TESTS PASSED! 🎉~n"),
            halt(0);
        some_failed ->
            io:format("~n❌ SOME PROPERTY TESTS FAILED~n"),
            halt(2)
    end.

%%====================================================================
%% Module Loading
%%====================================================================

load_test_modules() ->
    Modules = [
        {test_fixtures, "Test fixtures"},
        {ocel_property_tests, "Property tests module"},
        {run_all_property_tests, "Comprehensive test runner"}
    ],

    load_modules(Modules, ok).

load_modules([], ok) -> ok;
load_modules([{Module, Desc} | Rest], ok) ->
    case code:ensure_loaded(Module) of
        {module, Module} ->
            io:format("✓ ~p loaded~n", [Module]),
            load_modules(Rest, ok);
        {error, Reason} ->
            io:format("✗ ~p failed to load: ~p~n", [Module, Reason]),
            load_modules(Rest, {error, Module, Reason})
    end;
load_modules(_, Error) -> Error.

%%====================================================================
%% Comprehensive Test Suite
%%====================================================================

run_comprehensive_tests() ->
    io:format("Running comprehensive test suite...~n~n"),

    %% Test categories with different case counts
    Categories = [
        {basic, "Basic OCEL Tests", 100},
        {edge_cases, "Edge Case Tests", 50},
        {stress, "Stress Tests", 10},
        {invalid, "Invalid Input Tests", 50}
    ],

    run_categories(Categories, []).

run_categories([], Results) -> Results;
run_categories([{Type, Desc, Cases} | Rest], Acc) ->
    io:format("=== ~s (~p cases) ===~n", [Desc, Cases]),

    CategoryResults = run_category_tests(Type, Cases),

    io:format("Category results: ~p/~p passed~n~n",
        [lists:foldl(fun({passed, _}, P) -> P + 1; (_, P) -> P end, 0, CategoryResults),
         length(CategoryResults)]),

    run_categories(Rest, Acc ++ CategoryResults).

%% Basic OCEL tests
run_category_tests(basic, Cases) ->
    Tests = [
        {fun ocel_property_tests:prop_parse_any_ocel/0, "Parse any OCEL"},
        {fun ocel_property_tests:prop_parse_singleton_ocel/0, "Parse singleton OCEL"},
        {fun ocel_property_tests:prop_parse_empty_events/0, "Parse with empty events"},
        {fun ocel_property_tests:prop_parse_empty_objects/0, "Parse with empty objects"}
    ],
    run_tests(Tests, Cases).

%% Edge case tests
run_category_tests(edge_cases, Cases) ->
    Tests = [
        {fun ocel_property_tests:prop_parse_minimal_ocel/0, "Parse minimal OCEL"},
        {fun ocel_property_tests:prop_parse_maximal_ocel/0, "Parse maximal OCEL"},
        {fun ocel_property_tests:prop_parse_boundary_cases/0, "Parse boundary cases"}
    ],
    run_tests(Tests, Cases).

%% Stress tests
run_category_tests(stress, Cases) ->
    Tests = [
        {fun ocel_property_tests:prop_parse_large_ocel/0, "Parse large OCEL"},
        {fun ocel_property_tests:prop_parse_stress_test/0, "Parse stress test data"},
        {fun ocel_property_tests:prop_parse_many_events/0, "Parse with many events"},
        {fun ocel_property_tests:prop_parse_many_objects/0, "Parse with many objects"}
    ],
    run_tests(Tests, Cases).

%% Invalid input tests
run_category_tests(invalid, Cases) ->
    Tests = [
        {fun ocel_property_tests:prop_parse_malformed_json/0, "Parse malformed JSON"},
        {fun ocel_property_tests:prop_parse_missing_fields/0, "Parse with missing fields"},
        {fun ocel_property_tests:prop_parse_invalid_types/0, "Parse with invalid types"},
        {fun ocel_property_tests:prop_parse_unicode_event_ids/0, "Parse Unicode event IDs"},
        {fun ocel_property_tests:prop_parse_unicode_attributes/0, "Parse Unicode attributes"}
    ],
    run_tests(Tests, Cases).

%%====================================================================
 individual test execution
%%====================================================================

run_tests([], _) -> [];
run_tests([{TestFun, Desc} | Rest], NumTests) ->
    io:format("  Running: ~s...~n", [Desc]),

    Start = erlang:monotonic_time(millisecond),
    Result = proper:quickcheck(TestFun, [{numtests, NumTests}, {verbose, false}]),
    End = erlang:monotonic_time(millisecond),
    Duration = End - Start,

    case Result of
        true ->
            io:format("    ✓ PASS (~p ms)~n", [Duration]),
            [{passed, Desc, Duration} | run_tests(Rest, NumTests)];
        false ->
            io:format("    ✗ FAIL (~p ms)~n", [Duration]),
            [{failed, Desc, Duration} | run_tests(Rest, NumTests)]
    end.

%%====================================================================
%% Report Generation
%%====================================================================

generate_report(Results) ->
    io:format("=== COMPREHENSIVE TEST REPORT ===~n~n"),

    %% Summary statistics
    TotalTests = length(Results),
    Passed = lists:foldl(fun({passed, _, _}, P) -> P + 1; (_, P) -> P end, 0, Results),
    Failed = TotalTests - Passed,

    io:format("Total Tests: ~p~n", [TotalTests]),
    io:format("Passed: ~p (~.1f%)~n", [Passed, (Passed / TotalTests) * 100]),
    io:format("Failed: ~p (~.1f%)~n", [Failed, (Failed / TotalTests) * 100]),

    %% Performance metrics
    TotalTime = lists:foldl(fun({_, _, Duration}, Acc) -> Acc + Duration end, 0, Results),
    AvgTime = if TotalTests > 0 -> TotalTime / TotalTests; true -> 0 end,

    io:format("Total Test Time: ~p ms~n", [TotalTime]),
    io:format("Average Test Time: ~.2f ms~n", [AvgTime]),

    %% Detailed results
    io:format("~n=== DETAILED RESULTS ===~n"),
    lists:foreach(fun(Result) ->
        case Result of
            {passed, Desc, Duration} ->
                io:format("✓ ~p (~p ms)~n", [Desc, Duration]);
            {failed, Desc, Duration} ->
                io:format("✗ ~p (~p ms)~n", [Desc, Duration])
        end
    end, Results).

%%====================================================================
%% Result Analysis
%%====================================================================

check_results(Results) ->
    Failed = lists:filter(fun({failed, _, _}) -> true; (_) -> false end, Results),
    case Failed of
        [] -> all_passed;
        _ -> some_failed
    end.