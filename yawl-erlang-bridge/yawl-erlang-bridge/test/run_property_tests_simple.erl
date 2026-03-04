#!/usr/bin/env escript
%% -*- erlang -*-
%%! -pa ebin -pa _build/default/lib/*/ebin

%% Simple Property Test Runner for OCEL Parsing
%% This script runs basic property tests and reports results

main(_) ->
    io:format("=== Simple OCEL Property Tests ===~n~n"),

    %% Load required modules
    code:add_patha("../ebin"),
    code:add_patha("../_build/default/lib/*/ebin"),

    %% Try to load the test modules
    case code:ensure_loaded(ocel_property_tests) of
        {module, ocel_property_tests} ->
            io:format("✓ Loaded ocel_property_tests~n");
        {error, Reason} ->
            io:format("✗ Failed to load ocel_property_tests: ~p~n", [Reason]),
            halt(1)
    end,

    %% Run basic property tests
    Tests = [
        {fun ocel_property_tests:prop_parse_any_ocel/0, "Parse any OCEL"},
        {fun ocel_property_tests:prop_parse_empty_events/0, "Parse empty events"},
        {fun ocel_property_tests:prop_parse_empty_objects/0, "Parse empty objects"},
        {fun ocel_property_tests:prop_parse_singleton_ocel/0, "Parse singleton OCEL"}
    ],

    Results = run_tests(Tests, 50),  %% 50 tests each

    io:format("~n=== Summary ===~n"),
    io:format("Tests run: ~p~n", [length(Results)]),
    io:format("Passed: ~p~n", [lists:foldl(fun({passed, _}, Acc) -> Acc + 1; (_, Acc) -> Acc end, 0, Results)]),
    io:format("Failed: ~p~n", [lists:foldl(fun({failed, _}, Acc) -> Acc + 1; (_, Acc) -> Acc end, 0, Results)]),

    case lists:any(fun({failed, _}) -> true; (_) -> false end, Results) of
        true ->
            io:format("~nSome property tests failed.~n"),
            halt(2);
        false ->
            io:format("~nAll property tests passed!~n"),
            halt(0)
    end.

run_tests([], _) -> [];
run_tests([{TestFun, Desc} | Rest], NumTests) ->
    io:format("Running: ~s...~n", [Desc]),

    case proper:quickcheck(TestFun, [{numtests, NumTests}, {verbose, false}]) of
        true ->
            io:format("  ✓ PASS~n"),
            [{passed, Desc} | run_tests(Rest, NumTests)];
        false ->
            io:format("  ✗ FAIL~n"),
            [{failed, Desc} | run_tests(Rest, NumTests)]
    end.