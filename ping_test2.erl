%% Ping test with proper response handling
-module(ping_test2).
-export([run/0]).

run() ->
    io:format("Testing ping functionality...~n"),
    
    %% Start the server first
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("✓ Server started~n"),
            
            %% Try to ping
            case process_mining_bridge:ping() of
                pong ->
                    io:format("✓ Ping successful: pong~n");
                {ok, {pong, true}} ->
                    io:format("✓ Ping successful: {ok, {pong, true}}~n");
                {error, Error} ->
                    io:format("✗ Ping failed: ~p~n", [Error])
            end,
            
            %% Stop server
            process_mining_bridge:stop(),
            io:format("✓ Test completed~n");
        
        {error, Error} ->
            io:format("✗ Failed to start server: ~p~n", [Error])
    end.
