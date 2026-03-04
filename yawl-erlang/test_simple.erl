%% Simple test script
-module(test_simple).
-export([run/0]).

run() ->
    %% Just load the modules to check if they compile correctly
    io:format("Testing process_mining_bridge...~n"),
    case code:ensure_loaded(process_mining_bridge) of
        {module, process_mining_bridge} ->
            io:format("process_mining_bridge loaded successfully~n");
        {error, Reason} ->
            io:format("Failed to load process_mining_bridge: ~p~n", [Reason])
    end,

    io:format("Testing rust4pm...~n"),
    case code:ensure_loaded(rust4pm) of
        {module, rust4pm} ->
            io:format("rust4pm loaded successfully~n");
        {error, Reason} ->
            io:format("Failed to load rust4pm: ~p~n", [Reason])
    end,

    io:format("Testing process_mining_sup...~n"),
    case code:ensure_loaded(process_mining_sup) of
        {module, process_mining_sup} ->
            io:format("process_mining_sup loaded successfully~n");
        {error, Reason} ->
            io:format("Failed to load process_mining_sup: ~p~n", [Reason])
    end,

    ok.