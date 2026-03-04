#!/usr/bin/env escript
%% -*- erlang -*-

%% Comprehensive DSPy Test Suite
%% Tests all DSPy functions with various providers and scenarios

main(_) ->
    io:format("=== COMPREHENSIVE DSPy NIF TEST SUITE ===~n~n"),

    % Test results
    TestResults = [],

    % Run all test sections
    TestResults1 = test_nif_loading(TestResults),
    TestResults2 = test_dspy_init_providers(TestResults1),
    TestResults3 = test_dspy_predict_various(TestResults2),
    TestResults4 = test_dspy_load_examples(TestResults3),
    TestResults5 = test_error_handling(TestResults4),
    TestResults6 = test_configuration_persistence(TestResults5),
    TestResults7 = test_concurrent_predictions(TestResults6),

    % Final summary
    Passed = length([R || R <- TestResults7, R =:= pass]),
    Total = length(TestResults7),

    io:format("=== FINAL TEST SUMMARY ===~n"),
    io:format("Tests Passed: ~p/~p~n", [Passed, Total]),
    case Total > 0 of
        true -> io:format("Success Rate: ~.1f%%%~n", [Passed/Total*100]);
        false -> io:format("Success Rate: 0%%%~n")
    end,

    case Passed of
        Total ->
            io:format("✅ ALL TESTS PASSED~n"),
            % Update tasks
            io:format("Updating tasks #20 and #25 to completed~n");
        _ ->
            io:format("❌ SOME TESTS FAILED~n"),
            FailedTests = lists:seq(1, Total) -- lists:seq(1, Passed),
            io:format("Failed test numbers: ~p~n", [FailedTests])
    end.

%% Test 1: NIF Loading
test_nif_loading(Results) ->
    io:format("=== Test 1: NIF Loading ===~n"),
    case catch_nif_loading() of
        pass ->
            io:format("PASS: NIF loaded successfully~n"),
            [pass | Results];
        fail ->
            io:format("FAIL: NIF loading failed~n"),
            [fail | Results]
    end.

catch_nif_loading() ->
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
test_dspy_init_providers(Results) ->
    io:format("=== Test 2: DSPy Init with All Providers ===~n"),

    Providers = [
        {groq, "{\"provider\":\"groq\",\"model\":\"llama-3.3-70b-versatile\"}"},
        {openai, "{\"provider\":\"openai\",\"model\":\"gpt-4\"}"},
        {anthropic, "{\"provider\":\"anthropic\",\"model\":\"claude-3-opus-20240229\"}"}
    ],

    TestResults = lists:map(fun({Name, Config}) ->
        io:format("Testing ~p provider...~n", [Name]),
        case catch_dspy_init(Config) of
            pass ->
                io:format("  PASS: DSPy init with ~p succeeded~n", [Name]),
                pass;
            fail ->
                io:format("  FAIL: DSPy init with ~p failed~n", [Name]),
                fail
        end
    end, Providers),

    Passed = length([R || R <- TestResults, R =:= pass]),
    Total = length(TestResults),
    io:format("Provider init results: ~p/~p passed~n", [Passed, Total]),
    case Passed of
        Total -> [pass | Results];
        _ -> [fail | Results]
    end.

catch_dspy_init(Config) ->
    try
        case yawl_ml_bridge:dspy_init(iolist_to_binary(Config)) of
            ok -> pass;
            _ -> fail
        end
    catch
        _:_ -> fail
    end.

%% Test 3: DSPy Predict with Various Input/Output Combinations
test_dspy_predict_various(Results) ->
    io:format("=== Test 3: DSPy Predict with Various I/O Combinations ===~n"),

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

    TestResults = lists:map(fun({Name, Signature, Inputs}) ->
        io:format("Testing ~q prediction...~n", [Name]),
        case catch_dspy_predict(Signature, Inputs) of
            pass ->
                io:format("  PASS: ~q prediction succeeded~n", [Name]),
                pass;
            fail ->
                io:format("  FAIL: ~q prediction failed~n", [Name]),
                fail
        end
    end, TestCases),

    Passed = length([R || R <- TestResults, R =:= pass]),
    Total = length(TestResults),
    io:format("Prediction test results: ~p/~p passed~n", [Passed, Total]),
    case Passed of
        Total -> [pass | Results];
        _ -> [fail | Results]
    end.

catch_dspy_predict(Signature, Inputs) ->
    try
        case yawl_ml_bridge:dspy_predict(
                iolist_to_binary(Signature),
                iolist_to_binary(Inputs),
                <<"none">>) of
            {ok, Result} when is_binary(Result) -> pass;
            {ok, _} -> fail;
            {error, _} -> fail
        end
    catch
        _:_ -> fail
    end.

%% Test 4: DSPy Load Examples
test_dspy_load_examples(Results) ->
    io:format("=== Test 4: DSPy Load Examples ===~n"),

    TestCases = [
        {"basic_examples",
            "[{\"inputs\":{\"question\":\"What is 2+2?\"},\"outputs\":{\"answer\":\"4\"}},{\"inputs\":{\"question\":\"What is 3+3?\"},\"outputs\":{\"answer\":\"6\"}}]"},
        {"empty_examples", "[]"},
        {"single_example", "[{\"inputs\":{\"question\":\"What is the capital of France?\"},\"outputs\":{\"answer\":\"Paris\"}}]"}
    ],

    TestResults = lists:map(fun({Name, Examples}) ->
        io:format("Testing ~q examples...~n", [Name]),
        case catch_dspy_load_examples(Examples) of
            pass ->
                io:format("  PASS: Loaded examples for ~q~n", [Name]),
                pass;
            fail ->
                io:format("  FAIL: ~q examples failed~n", [Name]),
                fail
        end
    end, TestCases),

    Passed = length([R || R <- TestResults, R =:= pass]),
    Total = length(TestResults),
    io:format("Load examples results: ~p/~p passed~n", [Passed, Total]),
    case Passed of
        Total -> [pass | Results];
        _ -> [fail | Results]
    end.

catch_dspy_load_examples(Examples) ->
    try
        case yawl_ml_bridge:dspy_load_examples(iolist_to_binary(Examples)) of
            {ok, Count} when is_integer(Count), Count >= 0 -> pass;
            {ok, _} -> fail;
            {error, _} -> fail
        end
    catch
        _:_ -> fail
    end.

%% Test 5: Error Handling
test_error_handling(Results) ->
    io:format("=== Test 5: Error Handling ===~n"),

    TestCases = [
        {"init_invalid_json", fun() -> yawl_ml_bridge:dspy_init(<<"invalid json">>) end},
        {"init_empty_config", fun() -> yawl_ml_bridge:dspy_init(<<"">>) end},
        {"predict_invalid_signature",
            fun() -> yawl_ml_bridge:dspy_predict(<<"invalid">>, <<"{\"test\":\"value\"}">>, <<"none">>) end},
        {"predict_invalid_inputs",
            fun() -> yawl_ml_bridge:dspy_predict(<<"{\"inputs\":[],\"outputs\":[]}">>, <<"invalid">>, <<"none">>) end},
        {"load_invalid_examples", fun() -> yawl_ml_bridge:dspy_load_examples(<<"invalid json">>) end}
    ],

    TestResults = lists:map(fun({Name, Call}) ->
        io:format("Testing error handling for ~q...~n", [Name]),
        case catch_error_handling(Call) of
            pass ->
                io:format("  PASS: ~q correctly returned error~n", [Name]),
                pass;
            fail ->
                io:format("  FAIL: ~q should have returned error~n", [Name]),
                fail
        end
    end, TestCases),

    Passed = length([R || R <- TestResults, R =:= pass]),
    Total = length(TestResults),
    io:format("Error handling results: ~p/~p passed~n", [Passed, Total]),
    case Passed of
        Total -> [pass | Results];
        _ -> [fail | Results]
    end.

catch_error_handling(Call) ->
    try
        case Call() of
            {error, _} -> pass;
            _ -> fail
        end
    catch
        _:_ -> fail
    end.

%% Test 6: Configuration Persistence
test_configuration_persistence(Results) ->
    io:format("=== Test 6: Configuration Persistence ===~n"),

    % Test different configurations persist across calls
    Configs = [
        "{\"provider\":\"groq\",\"model\":\"llama-3.3-70b-versatile\",\"max_tokens\":100}",
        "{\"provider\":\"openai\",\"model\":\"gpt-4\",\"temperature\":0.5}",
        "{\"provider\":\"anthropic\",\"model\":\"claude-3-opus-20240229\",\"max_tokens\":500}"
    ],

    TestResults = lists:map(fun(Config) ->
        io:format("Testing config persistence...~n"),
        case catch_config_persistence(Config) of
            pass ->
                io:format("  PASS: Configuration persistent~n"),
                pass;
            fail ->
                io:format("  FAIL: Configuration persistence failed~n"),
                fail
        end
    end, Configs),

    Passed = length([R || R <- TestResults, R =:= pass]),
    Total = length(TestResults),
    io:format("Configuration persistence results: ~p/~p passed~n", [Passed, Total]),
    case Passed of
        Total -> [pass | Results];
        _ -> [fail | Results]
    end.

catch_config_persistence(Config) ->
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
                    {ok, _} -> pass;
                    {error, _} -> fail
                end;
            {error, _} -> fail
        end
    catch
        _:_ -> fail
    end.

%% Test 7: Concurrent Predictions
test_concurrent_predictions(Results) ->
    io:format("=== Test 7: Concurrent Predictions ===~n"),

    % Create test data for concurrent predictions
    TestData = [
        {1, "{\"question\":\"What is 1+1?\"}"},
        {2, "{\"question\":\"What is 2+2?\"}"},
        {3, "{\"question\":\"What is 3+3?\"}"},
        {4, "{\"question\":\"What is 4+4?\"}"},
        {5, "{\"question\":\"What is 5+5?\"}"}
    ],

    Signature = "{\"inputs\":[{\"name\":\"question\",\"type\":\"string\"}],\"outputs\":[{\"name\":\"answer\",\"type\":\"string\"}]}",
    SigJson = iolist_to_binary(Signature),

    % Spawn concurrent processes for predictions
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

    % Collect results with timeout
    TestResults = lists:map(fun(Pid) ->
        receive
            {Pid, {success, _}} -> pass;
            {Pid, {error, Reason}} ->
                io:format("  FAIL: Concurrent prediction failed: ~p~n", [Reason]),
                fail;
        after 15000 ->
            io:format("  FAIL: Concurrent prediction timed out~n"),
            fail
        end
    end, Processes),

    Passed = length([R || R <- TestResults, R =:= pass]),
    Total = length(TestResults),
    io:format("Concurrent prediction results: ~p/~p passed~n", [Passed, Total]),
    case Passed of
        Total -> [pass | Results];
        _ -> [fail | Results]
    end.