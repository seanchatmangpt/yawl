-module(test_final).
-export([run/0]).

run() ->
    io:format("Testing NIF functions...~n"),
    process_mining_bridge:init_nif(),
    io:format("init_nif called~n"),
    
    case process_mining_bridge:nop() of
        {ok, Result} -> io:format("nop() = ~p~n", [Result]);
        {error, Reason} -> io:format("nop() ERROR: ~p~n", [Reason])
    end,
    
    case process_mining_bridge:int_passthrough(42) of
        {ok, Result} -> io:format("int_passthrough(42) = ~p~n", [Result]);
        {error, Reason} -> io:format("int_passthrough(42) ERROR: ~p~n", [Reason])
    end,
    
    case process_mining_bridge:atom_passthrough(ok) of
        {ok, Result} -> io:format("atom_passthrough(ok) = ~p~n", [Result]);
        {error, Reason} -> io:format("atom_passthrough(ok) ERROR: ~p~n", [Reason])
    end.
