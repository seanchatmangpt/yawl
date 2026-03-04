%% Simple test for NIF integration
-module(test_simple).
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

    %% Check NIF loading
    case erlang:loaded() of
        List when is_list(List) ->
            case lists:keymember(process_mining_bridge, 1, List) of
                true ->
                    io:format("✓ Module is in loaded list~n");
                false ->
                    io:format("✗ Module not in loaded list~n")
            end
    end,

    %% Check if NIF file exists
    case code:priv_dir(process_mining_bridge) of
        {error, bad_name} ->
            %% Development path
            AppDir = filename:dirname(filename:dirname(code:which(?MODULE))),
            PrivDir = filename:join(AppDir, "priv");
        Dir ->
            PrivDir = Dir
    end,

    NifPath = filename:join(PrivDir, "yawl_process_mining"),
    io:format("Looking for NIF at: ~s~n", [NifPath]),

    case filelib:is_file(NifPath ++ ".so") of
        true ->
            io:format("✓ NIF library found at: ~s.so~n", [NifPath]);
        false ->
            io:format("✗ NIF library not found at: ~s.so~n", [NifPath])
    end,

    io:format("Test completed~n").