%% Simple test for NIF integration
-module(clean_test).
-export([test/0]).

test() ->
    io:format("Testing NIF integration...~n"),

    %% Try to load the module
    case code:ensure_loaded(process_mining_bridge) of
        {module, process_mining_bridge} ->
            io:format("✓ Module loaded successfully~n");
        {error, Reason} ->
            io:format("✗ Failed to load module: ~p~n", [Reason]),
            halt(1)
    end,

    %% Check if NIF file exists
    PrivDir = "_build/default/lib/process_mining_bridge/priv",
    NifPath = filename:join(PrivDir, "yawl_process_mining"),
    io:format("Looking for NIF at: ~s~n", [NifPath]),

    case filelib:is_file(NifPath ++ ".so") of
        true ->
            io:format("✓ NIF library found at: ~s.so~n", [NifPath]);
        false ->
            io:format("✗ NIF library not found at: ~s.so~n", [NifPath])
    end,

    %% Try to load NIF directly
    case erlang:load_nif(NifPath ++ ".so", 0) of
        ok ->
            io:format("✓ NIF loaded successfully~n");
        {error, {load_failed, Reason2}} ->
            io:format("✗ NIF load failed: ~p~n", [Reason2])
    end,

    io:format("Test completed~n").
