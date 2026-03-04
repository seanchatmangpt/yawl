#!/usr/bin/env escript
%% -*- erlang -*-
%%! -pa ebin

main(_) ->
    io:format("Testing NIF with minimal setup...~n"),

    % Only test the core functions, avoid anything that might reference small_list_passthrough
    io:format("Testing nop()..."),
    case process_mining_bridge:nop() of
        {ok, ok} -> io:format(" SUCCESS~n");
        _ -> io:format(" ERROR~n")
    end,

    io:format("Testing int_passthrough(42)..."),
    case process_mining_bridge:int_passthrough(42) of
        {ok, 42} -> io:format(" SUCCESS~n");
        _ -> io:format(" ERROR~n")
    end,

    io:format("Test complete.~n").