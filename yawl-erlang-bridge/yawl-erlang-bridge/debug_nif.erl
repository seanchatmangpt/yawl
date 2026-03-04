-module(debug_nif).
-export([start/0]).

start() ->
    io:format("About to call init_nif...~n"),
    process_mining_bridge:init_nif(),
    io:format("init_nif called~n"),
    case process_mining_bridge:nop() of
        {ok, Result} -> io:format("nop() = ~p~n", [Result]);
        {error, Reason} -> io:format("nop() error: ~p~n", [Reason])
    end.
