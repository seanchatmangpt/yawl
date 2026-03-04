%% Simple test for YAWL ML Bridge
-module(test_bridge_simple).
-export([run/0]).

run() ->
    io:format("=== YAWL ML Bridge Simple Test ===\n"),
    
    %% Test 1: NIF loading
    io:format("\n1. Testing NIF loading...\n"),
    case yawl_ml_bridge:ping() of
        {ok, <<"pong">>} -> io:format("✓ NIF loaded successfully\n");
        {ok, pong} -> io:format("✓ NIF loaded successfully\n");
        Error -> io:format("✗ NIF loading failed: ~p\n", [Error])
    end,
    
    %% Test 2: DSPy initialization
    io:format("\n2. Testing DSPy initialization...\n"),
    case test_dspy_init() of
        {ok, _} -> io:format("✓ DSPy initialized successfully\n");
        Error2 -> io:format("✗ DSPy initialization failed: ~p\n", [Error2])
    end,
    
    %% Test 3: Status check
    io:format("\n3. Testing bridge status...\n"),
    case yawl_ml_bridge:status() of
        {ok, Status} -> io:format("✓ Status: ~p\n", [Status]);
        Error3 -> io:format("✗ Status check failed: ~p\n", [Error3])
    end,
    
    io:format("\n=== Test Complete ===\n").

test_dspy_init() ->
    Config = #{provider => groq, model => "llama-3.1-70b-chat"},
    yawl_ml_bridge:dspy_init_api(Config).
