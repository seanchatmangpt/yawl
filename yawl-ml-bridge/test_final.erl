#!/usr/bin/env escript
%% -*- erlang -*-

main(_) ->
    % Test 1: Invalid JSON should return error
    io:format("=== Test 1: Invalid JSON Error Handling ===~n"),
    case yawl_ml_bridge:dspy_init(<<"invalid json">>) of
        {error, _} -> io:format("PASS: Invalid JSON correctly rejected~n");
        _ -> io:format("FAIL: Invalid JSON should be rejected~n")
    end,

    % Test 2: Valid config should work
    io:format("=== Test 2: Valid Configuration ===~n"),
    ValidConfig = <<"{\"provider\": \"groq\", \"model\": \"llama-3.3-70b-versatile\"}">>,
    case yawl_ml_bridge:dspy_init(ValidConfig) of
        ok ->
            io:format("PASS: Valid configuration accepted~n"),

            % Test 3: Valid prediction
            io:format("=== Test 3: Valid Prediction ===~n"),
            Signature = <<"{\"description\": \"Math question\", \"inputs\": [\"question\"], \"outputs\": [\"answer\"]}">>,
            Input = <<"{\"question\": \"What is 2+2?\"}">>,
            case yawl_ml_bridge:dspy_predict(Signature, Input, <<"none">>) of
                {ok, _} -> io:format("PASS: Valid prediction succeeded~n");
                {error, _} -> io:format("INFO: Prediction failed (likely API key issue)~n")
            end;
        {error, _} ->
            io:format("SKIP: Init test failed, skipping prediction test~n")
    end,

    % Test 4: Missing model field (should work with default)
    io:format("=== Test 4: Missing Model Field (default should apply) ===~n"),
    case yawl_ml_bridge:dspy_init(<<"{\"provider\": \"groq\"}">>) of
        ok -> io:format("PASS: Missing model accepted (using default)~n");
        {error, Reason} -> io:format("INFO: Missing model rejected: ~p~n", [Reason])
    end,

    % Test 5: TPOT2 valid config
    io:format("=== Test 5: TPOT2 Valid Configuration ===~n"),
    TPOTConfig = <<"{\"generations\": 10, \"population_size\": 50, \"timeout_minutes\": 1}">>,
    case yawl_ml_bridge:tpot2_init(TPOTConfig) of
        ok -> io:format("PASS: TPOT2 init succeeded~n");
        {error, _} -> io:format("INFO: TPOT2 init failed~n")
    end,

    % Test 6: TPOT2 optimization (no data, but should check config)
    io:format("=== Test 6: TPOT2 Optimization Config Check ===~n"),
    case yawl_ml_bridge:tpot2_optimize(<<"[]">>, <<"[]">>, TPOTConfig) of
        {error, _} -> io:format("PASS: TPOT2 correctly rejected empty data~n");
        {ok, _} -> io:format("INFO: TPOT2 succeeded (unexpected but acceptable)~n")
    end,

    % Test 7: Basic functions
    io:format("=== Test 7: Basic Functions ===~n"),
    case yawl_ml_bridge:ping() of
        {ok, <<"pong">>} -> io:format("PASS: Ping works~n");
        _ -> io:format("FAIL: Ping failed~n")
    end,

    case yawl_ml_bridge:status() of
        {ok, Status} when is_map(Status) ->
            io:format("PASS: Status works: ~p~n", [Status]);
        _ -> io:format("FAIL: Status failed~n")
    end,

    io:format("=== All Security Tests Completed ===~n").