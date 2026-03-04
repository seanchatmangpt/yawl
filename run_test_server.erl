%%!/usr/bin/env escript
%% -*- erlang -*-
%%! -pa ebin

main(_) ->
    % Start the server
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("Server started successfully~n"),
            % Keep it running
            receive
                after 5000 -> ok
            end;
        {error, Reason} ->
            io:format("Failed to start server: ~p~n", [Reason])
    end.
