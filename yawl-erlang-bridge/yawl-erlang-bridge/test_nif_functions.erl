#!/usr/bin/env escript
%% -*- erlang -*-
%%! -pa ebin

main(_) ->
    io:format("Testing NIF functions...~n"),

    % Test nop function
    io:format("Testing nop()..."),
    case process_mining_bridge:nop() of
        {ok, Result} ->
            io:format(" SUCCESS: ~p~n", [Result]);
        {error, Reason} ->
            io:format(" ERROR: ~p~n", [Reason])
    end,

    % Test int_passthrough
    io:format("Testing int_passthrough(42)..."),
    case process_mining_bridge:int_passthrough(42) of
        {ok, Result} ->
            io:format(" SUCCESS: ~p~n", [Result]);
        {error, Reason} ->
            io:format(" ERROR: ~p~n", [Reason])
    end,

    % Test atom_passthrough
    io:format("Testing atom_passthrough(ok)..."),
    case process_mining_bridge:atom_passthrough(ok) of
        {ok, Result} ->
            io:format(" SUCCESS: ~p~n", [Result]);
        {error, Reason} ->
            io:format(" ERROR: ~p~n", [Reason])
    end,

    % Try to call the problematic function
    io:format("Testing small_list_passthrough([a,b,c])..."),
    case process_mining_bridge:small_list_passthrough([a,b,c]) of
        {ok, Result} ->
            io:format(" SUCCESS: ~p~n", [Result]);
        {error, Reason} ->
            io:format(" ERROR: ~p~n", [Reason])
    end,

    io:format("Test complete.~n").
