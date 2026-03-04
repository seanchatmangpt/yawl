%% Simple test script
io:format("Starting test...~n"),

%% Check if we can load the NIF
case code:ensure_loaded(process_mining_bridge) of
    {module, process_mining_bridge} ->
        io:format("Module loaded successfully~n");
    {error, Reason} ->
        io:format("Failed to load module: ~p~n", [Reason])
end,

%% Try to ping
try
    case process_mining_bridge:ping() of
        {ok, {pong, NifLoaded}} ->
            io:format("Ping successful, NIF loaded: ~p~n", [NifLoaded]);
        {error, Reason} ->
            io:format("Ping failed: ~p~n", [Reason])
    end
catch
    Error:Reason ->
        io:format("Exception during ping: ~p:~p~n", [Error, Reason])
end,

io:format("Test completed~n").