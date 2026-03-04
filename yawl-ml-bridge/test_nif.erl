#!/usr/bin/env escript
%% -*- erlang -*-

main(_) ->
    % Test NIF loading
    io:format("=== Test 1: NIF Loading ===~n"),
    code:ensure_loaded(yawl_ml_bridge),
    io:format("NIF loaded successfully~n~n"),
    
    % Test ping
    io:format("=== Test 2: Ping ===~n"),
    case yawl_ml_bridge:ping() of
        {ok, <<"pong">>} -> io:format("PASS: Ping returned {ok, <<\"pong\">>}~n");
        _ -> io:format("FAIL: Ping returned unexpected value~n")
    end,
    io:format("~n"),
    
    % Test status
    io:format("=== Test 3: Status ===~n"),
    case yawl_ml_bridge:status() of
        {ok, Status} when is_map(Status) -> io:format("PASS: Status returned ~p~n", [Status]);
        _ -> io:format("FAIL: Status returned unexpected value~n")
    end,
    io:format("~n"),
    
    % Test dspy_init with Groq
    io:format("=== Test 4: DSPy Init with Groq ===~n"),
    GroqConfig = "{\"provider\":\"groq\",\"model\":\"llama-3.3-70b-versatile\"}",
    case yawl_ml_bridge:dspy_init(iolist_to_binary(GroqConfig)) of
        ok -> io:format("PASS: DSPy init with Groq succeeded~n");
        _ -> io:format("FAIL: DSPy init failed~n")
    end,
    io:format("~n"),
    
    % Test dspy_predict
    io:format("=== Test 5: DSPy Predict ===~n"),
    Signature = <<"{\"description\": \"Simple math question\", \"inputs\": [\"question\"], \"outputs\": [\"answer\"]}">>,
    Input = <<"{\"question\": \"What is 2+2?\"}">>,
    case yawl_ml_bridge:dspy_predict(Signature, Input, []) of
        {ok, Result} -> io:format("PASS: DSPy predict succeeded: ~p~n", [Result]);
        {error, _} -> io:format("FAIL: DSPy predict failed with error~n");
        _ -> io:format("FAIL: DSPy predict returned unexpected value~n")
    end,
    io:format("~n"),
    
    % Test dspy_load_examples
    io:format("=== Test 6: DSPy Load Examples ===~n"),
    Examples = <<"[{\"inputs\": {\"question\": \"What is the capital of France?\"}, \"outputs\": {\"answer\": \"Paris\"}}, {\"inputs\": {\"question\": \"What is the capital of Japan?\"}, \"outputs\": {\"answer\": \"Tokyo\"}}]">>,
    case yawl_ml_bridge:dspy_load_examples(Examples) of
        {ok, Count} when is_integer(Count) -> io:format("PASS: DSPy loaded ~p examples~n", [Count]);
        _ -> io:format("FAIL: DSPy load examples failed~n")
    end,
    io:format("~n"),
    
    % Test tpot2_init
    io:format("=== Test 7: TPOT2 Init ===~n"),
    case yawl_ml_bridge:tpot2_init(<<"{}">>) of
        ok -> io:format("PASS: TPOT2 init succeeded~n");
        _ -> io:format("FAIL: TPOT2 init failed~n")
    end,
    io:format("~n"),
    
    % Test error handling
    io:format("=== Test 8: Error Handling ===~n"),
    case yawl_ml_bridge:dspy_init(<<"invalid json">>) of
        {error, _} -> io:format("PASS: Error handling worked~n");
        _ -> io:format("FAIL: Expected error but got success~n")
    end,
    io:format("~n"),
    
    io:format("=== All Tests Completed ===~n").
