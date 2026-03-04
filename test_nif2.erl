%% Test NIF loading with correct path
-module(test_nif2).
-export([test/0]).

test() ->
    io:format("Testing NIF loading...~n"),
    
    %% Try with .dylib extension
    case erlang:load_nif("priv/libprocess_mining_bridge.dylib", 0) of
        ok ->
            io:format("✓ NIF loaded successfully~n");
        {error, {load_failed, Reason1}} ->
            io:format("✗ NIF load failed (.dylib): ~p~n", [Reason1])
    end,
    
    %% Try with .so extension
    case erlang:load_nif("priv/process_mining_bridge.so", 0) of
        ok ->
            io:format("✓ NIF loaded successfully~n");
        {error, {load_failed, Reason2}} ->
            io:format("✗ NIF load failed (.so): ~p~n", [Reason2])
    end,
    
    io:format("Test completed~n").
