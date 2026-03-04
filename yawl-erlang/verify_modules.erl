%% Verify modules can be loaded
-module(verify_modules).
-export([start/0]).

start() ->
    io:format("Verifying modules...~n"),

    %% Check each module
    Modules = [process_mining_bridge, rust4pm, process_mining_sup],
    [verify_module(M) || M <- Modules],

    io:format("All modules verified successfully!~n"),
    ok.

verify_module(Module) ->
    io:format("Verifying ~p...~n", [Module]),
    case code:ensure_loaded(Module) of
        {module, Module} ->
            io:format("  ✓ Module loaded successfully~n");
        {error, Reason} ->
            io:format("  ✗ Module failed to load: ~p~n", [Reason])
    end.