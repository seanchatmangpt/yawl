%% Test application loading
-module(test_app).

-export([start/0]).

start() ->
    io:format("Starting test...~n"),

    % Try to start the application
    case application:start(process_mining_bridge) of
        ok ->
            io:format("✓ Application started successfully~n"),

            % Try to call a NIF function
            try
                process_mining_bridge:nop(),
                io:format("✓ nop() called successfully~n")
            catch
                Error:Reason ->
                    io:format("✗ Error calling nop(): ~p:~p~n", [Error, Reason])
            end;

        {error, {already_started, Pid}} ->
            io:format("✓ Application already running with PID: ~p~n", [Pid]);

        {error, Reason} ->
            io:format("✗ Failed to start application: ~p~n", [Reason])
    end,

    % Check NIF status
    case process_mining_bridge:get_nif_status() of
        {ok, Status} ->
            io:format("✓ NIF status: ~p~n", [Status]);
        {error, Reason2} ->
            io:format("✗ NIF error: ~p~n", [Reason2])
    end,

    init:stop().