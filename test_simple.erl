#!/usr/bin/env escript
%%! -pa ebin

main(_) ->
    % Start the process
    case process_mining_bridge:start_link() of
        {ok, Pid} ->
            io:format('Process started: ~p~n', [Pid]),

            % Try to ping
            case process_mining_bridge:ping() of
                ok ->
                    io:format('Ping successful~n');
                {ok, {pong, NifLoaded}} ->
                    io:format('Ping successful, NIF loaded: ~p~n', [NifLoaded]);
                {error, Reason} ->
                    io:format('Ping failed: ~p~n', [Reason])
            end,

            % Stop the process
            process_mining_bridge:stop(),
            halt(0);
        {error, Reason} ->
            io:format('Failed to start process: ~p~n', [Reason]),
            halt(1)
    end.
