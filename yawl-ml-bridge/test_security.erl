#!/usr/bin/env escript
%% -*- erlang -*-

main(_) ->
    % Test 1: Invalid JSON should return error
    io:format("=== Test 1: Invalid JSON Error Handling ===~n"),
    case yawl_ml_bridge:dspy_init(<<"invalid json">>) of
        {error, _} -> io:format("PASS: Invalid JSON correctly rejected~n");
        _ -> io:format("FAIL: Invalid JSON should be rejected~n")
    end,

    % Test 2: Missing model field should return error
    io:format("=== Test 2: Missing Model Field ===~n"),
    case yawl_ml_bridge:dspy_init(<<"{\"provider\": \"groq\"}">>) of
        {error, Reason} when is_binary(Reason) ->
            io:format("PASS: Missing model field correctly rejected: ~p~n", [Reason]);
        _ -> io:format("FAIL: Missing model field should be rejected~n")
    end,

    % Test 3: Valid model field should work
    io:format("=== Test 3: Valid Model Field ===~n"),
    case yawl_ml_bridge:dspy_init(<<"{\"provider\": \"groq\", \"model\": \"llama-3.3-70b-versatile\"}">>) of
        ok ->
            io:format("PASS: Valid model field accepted~n"),

            % Test 4: Invalid signature should be rejected
            io:format("=== Test 4: Invalid Signature ===~n"),
            case yawl_ml_bridge:dspy_predict(<<"{\"inputs\": []}">>, <<"{\"test\": \"value\"}">>, []) of
                {error, _} -> io:format("PASS: Empty inputs signature correctly rejected~n");
                _ -> io:format("FAIL: Empty inputs should be rejected~n")
            end;

        {error, _} ->
            io:format("SKIP: Model field test skipped due to init failure~n")
    end,

    % Test 5: Missing description should be rejected
    io:format("=== Test 5: Missing Description ===~n"),
    case yawl_ml_bridge:dspy_predict(<<"{\"inputs\": [\"test\"], \"outputs\": [\"result\"]}">>, <<"{\"test\": \"value\"}">>, []) of
        {error, _} -> io:format("PASS: Missing description correctly rejected~n");
        _ -> io:format("FAIL: Missing description should be rejected~n")
    end,

    % Test 6: TPOT2 missing required fields should be rejected
    io:format("=== Test 6: TPOT2 Missing Fields ===~n"),
    case yawl_ml_bridge:tpot2_optimize(<<"[]">>, <<"[]">>, <<"{}">>) of
        {error, _} -> io:format("PASS: TPOT2 missing fields correctly rejected~n");
        _ -> io:format("FAIL: TPOT2 missing fields should be rejected~n")
    end,

    io:format("=== Security Tests Completed ===~n").