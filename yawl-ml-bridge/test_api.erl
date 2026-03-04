%% Test YAWL ML Bridge API
-module(test_api).
-export([run/0]).

run() ->
    io:format("=== YAWL ML Bridge API Test ===\n"),
    
    %% Test ping
    io:format("\n1. Testing ping...\n"),
    case yawl_ml_bridge:ping() of
        {ok, <<"pong">>} -> io:format("✓ Ping successful\n");
        {ok, pong} -> io:format("✓ Ping successful\n");
        Error -> io:format("✗ Ping failed: ~p\n", [Error])
    end,
    
    %% Test status
    io:format("\n2. Testing status...\n"),
    case catch yawl_ml_bridge:status() of
        {'EXIT', _} -> io:format("⚠ Status not available (gen_server not started)\n");
        {ok, Status} -> io:format("✓ Status: ~p\n", [Status]);
        _Other -> io:format("⚠ Status check failed\n")
    end,
    
    %% Test DSPy init
    io:format("\n3. Testing DSPy initialization...\n"),
    Config = <<"{\"provider\": \"groq\", \"model\": \"llama-3.1-70b-chat\"}">>,
    case yawl_ml_bridge:dspy_init(Config) of
        {ok, _} -> io:format("✓ DSPy init successful\n");
        {error, Reason} -> io:format("✗ DSPy init failed: ~p\n", [Reason])
    end,
    
    io:format("\n=== Test Complete ===\n").
