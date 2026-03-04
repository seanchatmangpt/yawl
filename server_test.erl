%% Test server startup
-module(server_test).
-export([run/0]).

run() ->
    io:format("Testing server startup...~n"),
    
    %% Start the server
    case process_mining_bridge:start_link() of
        {ok, Pid} ->
            io:format("✓ Server started: ~p~n", [Pid]),
            
            %% Try to get status
            case process_mining_bridge:get_nif_status() of
                {ok, Status} ->
                    io:format("✓ NIF status: ~p~n", [Status]);
                {error, Error} ->
                    io:format("✗ NIF error: ~p~n", [Error])
            end,
            
            %% Stop server
            process_mining_bridge:stop(),
            io:format("✓ Server stopped~n");
        
        {error, {already_started, Pid}} ->
            io:format("✗ Server already running: ~p~n", [Pid]),
            process_mining_bridge:stop();
        
        {error, Error} ->
            io:format("✗ Server start failed: ~p~n", [Error])
    end.
