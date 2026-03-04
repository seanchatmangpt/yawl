-module(test_minimal).
-export([start/0]).

start() ->
    process_mining_bridge:start_link(),
    Result = process_mining_bridge:nop(),
    io:format("Result: ~p~n", [Result]),
    Result.