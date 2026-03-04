#!/usr/bin/env escript
%% -*- erlang -*-

%% Complete Comprehensive DSPy Test Suite
%% Tests all DSPy functions with various providers and scenarios

main(_) ->
    io:format("=== COMPLETE COMPREHENSIVE DSPy NIF TEST SUITE ===~n~n"),

    % Test counters
    Tests = [
        {"Test 1: NIF Loading", fun test_nif_loading/0},
        {"Test 2: DSPy Init with All Providers", fun test_dspy_init_providers/0},
        {"Test 3: DSPy Predict Various", fun test_dspy_predict_various/0},
        {"Test 4: DSPy Load Examples", fun test_dspy_load_examples/0},
        {"Test 5: Error Handling", fun test_error_handling/0},
        {"Test 6: Configuration Persistence", fun test_configuration_persistence/0},
        {"Test 7: Concurrent Predictions", fun test_concurrent_predictions/0}
    ],

    % Run all tests
    Results = lists:map(fun({Name, TestFun}) ->
        io:format("=== ~p ===~n", [Name]),
        try
            case TestFun() of
                pass ->
                    io:format("PASS: ~p~n", [Name]),
                    pass;
                fail ->
                    io:format("FAIL: ~p~n", [Name]),
                    fail
            end
        catch
            E:R ->
                io:format("FAIL: ~p - Error: ~p:~p~n", [Name, E, R]),
                fail
        end,
        io:format("~n")
    end, Tests),

    % Final summary
    Passed = length([R || R <- Results, R =:= pass]),
    Total = length(Results),

    io:format("=== FINAL TEST SUMMARY ===~n"),
    io:format("Tests Passed: ~p/~p~n", [Passed, Total]),
    case Total > 0 of
        true -> io:format("Success Rate: ~.1f%%%~n", [Passed/Total*100]);
        false -> io:format("Success Rate: 0%%%~n")
    end,

    case Passed of
        Total ->
            io:format("✅ ALL TESTS PASSED~n"),
            % Update tasks #20 and #25
            io:format("✓ Task #20: DSPy NIF Integration - COMPLETED~n"),
            io:format("✓ Task #25: DSPy NIF Testing - COMPLETED~n");
        _ ->
            io:format("❌ SOME TESTS FAILED~n"),
            Failed = lists:seq(1, Total) -- lists:seq(1, Passed),
            io:format("Failed test numbers: ~p~n", [Failed])
    end.

%% Test 1: NIF Loading
test_nif_loading() ->
    try
        code:ensure_loaded(yawl_ml_bridge),
        case yawl_ml_bridge:ping() of
            {ok, <<"pong">>} -> pass;
            _ -> fail
        end
    catch
        _:_ -> fail
    end.

%% Test 2: DSPy Init with All Providers
test_dspy_init_providers() ->
    Providers = [
        {groq, "{\"provider\":\"groq\",\"model\":\"llama-3.3-70b-versatile\"}"},
        {openai, "{\"provider\":\"openai\",\"model\":\"gpt-4\"}"},
        {anthropic, "{\"provider\":\"anthropic\",\"model\":\"claude-3-opus-20240229\"}"}
    ],

    Results = lists:map(fun({Name, Config}) ->
        io:format("  Testing ~p... ", [Name]),
        try
            case yawl_ml_bridge:dspy_init(iolist_to_binary(Config)) of
                ok ->
                    io:format("PASS~n"),
                    pass;
                {error, _} ->
                    io:format("FAIL~n"),
                    fail
            end
        catch
            _:_ ->
                io:format("FAIL~n"),
                fail
        end
    end, Providers),

    length([R || R <- Results, R =:= pass]) =:= length(Providers).

%% Test 3: DSPy Predict with Various Input/Output Combinations
test_dspy_predict_various() ->
    TestCases = [
        {"basic_qa",
            "{\"inputs\":[{\"name\":\"question\",\"type\":\"string\"}],\"outputs\":[{\"name\":\"answer\",\"type\":\"string\"}]}",
            "{\"question\":\"What is the capital of France?\"}"},
        {"math_question",
            "{\"inputs\":[{\"name\":\"question\",\"type\":\"string\"}],\"outputs\":[{\"name\":\"answer\",\"type\":\"string\"}]}",
            "{\"question\":\"What is 2+2?\"}"},
        {"multiple_inputs",
            "{\"inputs\":[{\"name\":\"question\",\"type\":\"string\"},{\"name\":\"context\",\"type\":\"string\"}],\"outputs\":[{\"name\":\"answer\",\"type\":\"string\"}]}",
            "{\"question\":\"What is AI?\",\"context\":\"Artificial Intelligence\"}"}
    ],

    Results = lists:map(fun({Name, Signature, Inputs}) ->
        io:format("  Testing ~q... ", [Name]),
        try
            case yawl_ml_bridge:dspy_predict(
                    iolist_to_binary(Signature),
                    iolist_to_binary(Inputs),
                    <<"none">>) of
                {ok, Result} when is_binary(Result) ->
                    io:format("PASS~n"),
                    pass;
                {ok, _} ->
                    io:format("FAIL (wrong type)~n"),
                    fail;
                {error, _} ->
                    io:format("FAIL (error)~n"),
                    fail
            end
        catch
            _:_ ->
                io:format("FAIL (crash)~n"),
                fail
        end
    end, TestCases),

    length([R || R <- Results, R =:= pass]) =:= length(TestCases).

%% Test 4: DSPy Load Examples
test_dspy_load_examples() ->
    TestCases = [
        {"basic_examples",
            "[{\"inputs\":{\"question\":\"What is 2+2?\"},\"outputs\":{\"answer\":\"4\"}},{\"inputs\":{\"question\":\"What is 3+3?\"},\"outputs\":{\"answer\":\"6\"}}]"},
        {"empty_examples", "[]"},
        {"single_example", "[{\"inputs\":{\"question\":\"What is the capital of France?\"},\"outputs\":{\"answer\":\"Paris\"}}]"}
    ],

    Results = lists:map(fun({Name, Examples}) ->
        io:format("  Testing ~q... ", [Name]),
        try
            case yawl_ml_bridge:dspy_load_examples(iolist_to_binary(Examples)) of
                {ok, Count} when is_integer(Count), Count >= 0 ->
                    io:format("PASS (~p examples)~n", [Count]),
                    pass;
                {ok, _} ->
                    io:format("FAIL (bad count)~n"),
                    fail;
                {error, _} ->
                    io:format("FAIL (error)~n"),
                    fail
            end
        catch
            _:_ ->
                io:format("FAIL (crash)~n"),
                fail
        end
    end, TestCases),

    length([R || R <- Results, R =:= pass]) =:= length(TestCases).

%% Test 5: Error Handling
test_error_handling() ->
    TestCases = [
        {"init_invalid_json", fun() -> yawl_ml_bridge:dspy_init(<<"invalid json">>) end},
        {"init_empty_config", fun() -> yawl_ml_bridge:dspy_init(<<"">>) end},
        {"predict_invalid_signature",
            fun() -> yawl_ml_bridge:dspy_predict(<<"invalid">>, <<"{\"test\":\"value\"}">>, <<"none">>) end},
        {"predict_invalid_inputs",
            fun() -> yawl_ml_bridge:dspy_predict(<<"{\"inputs\":[],\"outputs\":[]}">>, <<"invalid">>, <<"none">>) end},
        {"load_invalid_examples", fun() -> yawl_ml_bridge:dspy_load_examples(<<"invalid json">>) end}
    ],

    Results = lists:map(fun({Name, Call}) ->
        io:format("  Testing ~q... ", [Name]),
        try
            case Call() of
                {error, _} ->
                    io:format("PASS~n"),
                    pass;
                _ ->
                    io:format("FAIL (should error)~n"),
                    fail
            end
        catch
            _:_ ->
                io:format("FAIL (should error but crashed)~n"),
                fail
        end
    end, TestCases),

    length([R || R <- Results, R =:= pass]) =:= length(TestCases).

%% Test 6: Configuration Persistence
test_configuration_persistence() ->
    Configs = [
        "{\"provider\":\"groq\",\"model\":\"llama-3.3-70b-versatile\",\"max_tokens\":100}",
        "{\"provider\":\"openai\",\"model\":\"gpt-4\",\"temperature\":0.5}",
        "{\"provider\":\"anthropic\",\"model\":\"claude-3-opus-20240229\",\"max_tokens\":500}"
    ],

    Results = lists:map(fun(Config) ->
        io:format("  Testing config persistence... "),
        try
            % Init with config
            case yawl_ml_bridge:dspy_init(iolist_to_binary(Config)) of
                ok ->
                    % Try prediction with this config
                    Signature = "{\"inputs\":[{\"name\":\"question\",\"type\":\"string\"}],\"outputs\":[{\"name\":\"answer\",\"type\":\"string\"}]}",
                    Inputs = "{\"question\":\"Test question\"}",
                    case yawl_ml_bridge:dspy_predict(
                            iolist_to_binary(Signature),
                            iolist_to_binary(Inputs),
                            <<"none">>) of
                        {ok, _} ->
                            io:format("PASS~n"),
                            pass;
                        {error, _} ->
                            io:format("FAIL~n"),
                            fail
                    end;
                {error, _} ->
                    io:format("FAIL (init error)~n"),
                    fail
            end
        catch
            _:_ ->
                io:format("FAIL (crash)~n"),
                fail
        end
    end, Configs),

    length([R || R <- Results, R =:= pass]) =:= length(Configs).

%% Test 7: Concurrent Predictions
test_concurrent_predictions() ->
    TestData = [
        {1, "{\"question\":\"What is 1+1?\"}"},
        {2, "{\"question\":\"What is 2+2?\"}"},
        {3, "{\"question\":\"What is 3+3?\"}"},
        {4, "{\"question\":\"What is 4+4?\"}"},
        {5, "{\"question\":\"What is 5+5?\"}"}
    ],

    Signature = "{\"inputs\":[{\"name\":\"question\",\"type\":\"string\"}],\"outputs\":[{\"name\":\"answer\",\"type\":\"string\"}]}",
    SigJson = iolist_to_binary(Signature),

    % Spawn concurrent processes
    Processes = lists:map(fun({Id, Inputs}) ->
        spawn(fun() ->
            InJson = iolist_to_binary(Inputs),
            case yawl_ml_bridge:dspy_predict(SigJson, InJson, <<"none">>) of
                {ok, Result} ->
                    Id ! {self(), {success, Result}};
                {error, Reason} ->
                    Id ! {self(), {error, Reason}}
            end
        end)
    end, TestData),

    % Collect results
    Results = lists:map(fun(Pid) ->
        receive
            {Pid, {success, _}} -> pass;
            {Pid, {error, Reason}} ->
                io:format("  Concurrent prediction failed: ~p~n", [Reason]),
                fail
        after 15000 ->
            io:format("  Concurrent prediction timed out~n"),
            fail
        end
    end, Processes),

    length([R || R <- Results, R =:= pass]) =:= length(Results).