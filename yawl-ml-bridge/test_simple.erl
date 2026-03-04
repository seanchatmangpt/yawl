%% Simple test without variables
-module(test_simple).
-export([run/0]).

run() ->
    io:format("=== Simple YAWL ML Bridge Test ===\n"),
    
    %% Test ping
    io:format("\n1. Testing ping...\n"),
    case yawl_ml_bridge:ping() of
        {ok, <<"pong">>} -> io:format("✓ Ping successful\n");
        {ok, pong} -> io:format("✓ Ping successful\n");
        _ -> io:format("✗ Ping failed\n")
    end,
    
    %% Test status
    io:format("\n2. Testing status...\n"),
    case catch yawl_ml_bridge:status() of
        {'EXIT', _} -> io:format("⚠ Status not available\n");
        {ok, Status} -> io:format("✓ Status: ~p\n", [Status]);
        _ -> io:format("✗ Status check failed\n")
    end,
    
    %% Test DSPy init
    io:format("\n3. Testing DSPy init...\n"),
    Config = "{\"provider\": \"groq\", \"model\": \"llama-3.1-70b-chat\"}",
    case yawl_ml_bridge:dspy_init(Config) of
        ok -> io:format("✓ DSPy init successful\n");
        {error, _} -> io:format("✗ DSPy init failed\n")
    end,
    
    io:format("\n=== Simple Test Complete ===\n").
