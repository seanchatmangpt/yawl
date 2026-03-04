%% Simple test to check if NIF loads
-module(test_nif).
-export([start_test/0]).

start_test() ->
    case code:ensure_loaded(process_mining_bridge) of
        {module, _} ->
            io:format("Module loaded successfully~n");
        {error, Reason} ->
            io:format("Failed to load module: ~p~n", [Reason])
    end.
