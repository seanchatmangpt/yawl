%% Test starting the process mining bridge
-module(test_start).
-export([run/0]).

run() ->
    io:format("Testing process_mining_bridge:start_link()...~n"),
    case process_mining_bridge:start_link() of
        {ok, Pid} ->
            io:format("Started successfully: ~p~n", [Pid]),
            process_mining_bridge:stop(),
            io:format("Stopped successfully~n");
        {error, Reason} ->
            io:format("Failed to start: ~p~n", [Reason])
    end,
    init:stop().