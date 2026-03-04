%% Test NIF loading from current priv dir
-module(test_nif).
-export([test/0]).

test() ->
    io:format("Testing NIF from priv dir...~n"),
    
    NifPath = "priv/yawl_process_mining",
    io:format("Looking for NIF at: ~s~n", [NifPath]),
    
    case filelib:is_file(NifPath ++ ".dylib") of
        true ->
            io:format("✓ Found .dylib~n");
        false ->
            io:format("✗ No .dylib found~n")
    end,
    
    case filelib:is_file(NifPath ++ ".so") of
        true ->
            io:format("✓ Found .so~n");
        false ->
            io:format("✗ No .so found~n")
    end,
    
    %% Try loading with .dylib extension (macOS)
    case erlang:load_nif(NifPath ++ ".dylib", 0) of
        ok ->
            io:format("✓ NIF (.dylib) loaded successfully~n");
        {error, {load_failed, Reason1}} ->
            io:format("✗ NIF (.dylib) load failed: ~p~n", [Reason1])
    end,
    
    %% Try loading with .so extension
    case erlang:load_nif(NifPath ++ ".so", 0) of
        ok ->
            io:format("✓ NIF (.so) loaded successfully~n");
        {error, {load_failed, Reason2}} ->
            io:format("✗ NIF (.so) load failed: ~p~n", [Reason2])
    end,
    
    io:format("Test completed~n").
