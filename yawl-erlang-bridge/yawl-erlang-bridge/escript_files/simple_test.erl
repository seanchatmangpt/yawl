#!/usr/bin/env escript
%%! -pa ebin

main(_) ->
    io:format('=== JTBD 1: DFG DISCOVERY ===~n~n'),

    %% Check if NIF is loaded
    case process_mining_bridge:ping() of
        {ok, {pong, true}} ->
            io:format('NIF is loaded~n');
        {ok, {pong, false}} ->
            io:format('NIF is not loaded~n');
        Error ->
            io:format('Ping failed: ~p~n', [Error])
    end,

    %% Try to get NIF status
    NifStatus = process_mining_bridge:get_nif_status(),
    io:format('NIF status: ~p~n', [NifStatus]).