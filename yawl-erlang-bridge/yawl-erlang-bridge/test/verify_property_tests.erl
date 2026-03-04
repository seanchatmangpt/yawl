#!/usr/bin/env escript
%% -*- erlang -*-
%%! -pa ebin -pa _build/default/lib/*/ebin

%% Verification Script for Property Tests
%% This script verifies that all property test components are properly configured

main(_) ->
    io:format("=== Property Tests Verification ===~n~n"),

    %% Check if we can load all required modules
    case verify_modules() of
        ok ->
            io:format("✓ All modules can be loaded~n");
        {error, Missing} ->
            io:format("✗ Missing modules: ~p~n", [Missing]),
            halt(1)
    end,

    %% Check if proper dependency is available
    case verify_proper_dependency() of
        ok ->
            io:format("✓ proper dependency is available~n");
        {error, Reason} ->
            io:format("✗ proper dependency issue: ~p~n", [Reason]),
            halt(1)
    end,

    %% Run a quick test to ensure the property tests work
    case verify_property_test_execution() of
        ok ->
            io:format("✓ Property tests can execute~n");
        {error, Reason} ->
            io:format("✗ Property test execution failed: ~p~n", [Reason]),
            halt(1)
    end,

    io:format("~n=== All Verifications Passed! ===~n").

%%====================================================================
%% Module Verification
%%====================================================================

verify_modules() ->
    RequiredModules = [
        test_fixtures,
        ocel_property_tests,
        run_all_property_tests
    ],

    verify_modules(RequiredModules, []).

verify_modules([], Missing) when Missing == [] -> ok;
verify_modules([], Missing) -> {error, Missing};
verify_modules([Mod | Rest], Missing) ->
    case code:ensure_loaded(Mod) of
        {module, Mod} ->
            verify_modules(Rest, Missing);
        {error, _} ->
            verify_modules(Rest, [Mod | Missing])
    end.

%%====================================================================
%% Proper Dependency Verification
%%====rop====================================================================

verify_proper_dependency() ->
    case code:ensure_loaded(proper) of
        {module, proper} ->
            %% Try to run a simple test
            case catch proper:quickcheck(true, [{numtests, 1}, {verbose, false}]) of
                true -> ok;
                _ -> {error, "proper quickcheck failed"}
            end;
        {error, Reason} ->
            {error, Reason}
    end.

%%====================================================================
%% Property Test Execution Verification
%%====================================================================

verify_property_test_execution() ->
    %% Try to run a basic property test
    TestFun = fun ocel_property_tests:prop_parse_any_ocel/0,

    case catch proper:quickcheck(TestFun, [{numtests, 5}, {verbose, false}]) of
        true -> ok;
        {'EXIT', {noproc, _}} ->
            {error, "NIF process not running"};
        {'EXIT', Reason} ->
            {error, "Test execution failed: " ++ io_lib:format("~p", [Reason])};
        _ ->
            ok
    end.