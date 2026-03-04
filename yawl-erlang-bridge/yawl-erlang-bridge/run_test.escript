#!/usr/bin/env escript

%% escript main function
main(_) ->
    io:format("Starting NIF test...~n"),

    %% Add ebin to path
    code:add_patha("ebin"),
    code:add_patha("../ebin"),

    %% Check if we can load the NIF
    case code:ensure_loaded(process_mining_bridge) of
        {module, process_mining_bridge} ->
            io:format("Module loaded successfully~n");
        {error, Reason1} ->
            io:format("Failed to load module: ~p~n", [Reason1])
    end,

    %% Start all required dependencies
    Apps = [kernel, stdlib, mnesia, lager, telemetry, logger],
    [begin
        case application:ensure_started(App) of
            ok ->
                io:format("Started ~p successfully~n", [App]);
            {error, ReasonApp} ->
                io:format("Failed to start ~p: ~p~n", [App, ReasonApp])
        end
    end || App <- Apps],

    %% Start the application
    case application:ensure_started(process_mining_bridge) of
        ok ->
            io:format("Application started successfully~n");
        {error, AppReason} ->
            io:format("Failed to start application: ~p~n", [AppReason])
    end,

    %% Try to ping
    try
        case process_mining_bridge:ping() of
            {ok, {pong, NifLoaded}} ->
                io:format("Ping successful, NIF loaded: ~p~n", [NifLoaded]);
            {error, Reason2} ->
                io:format("Ping failed: ~p~n", [Reason2])
        end
    catch
        Error3:Reason3 ->
            io:format("Exception during ping: ~p:~p~n", [Error3, Reason3])
    end,

    %% Check NIF file existence
    try
        case process_mining_bridge:get_nif_status() of
            {ok, {nif_status, FileExists}} ->
                io:format("NIF file exists: ~p~n", [FileExists]);
            {error, Reason4} ->
                io:format("NIF status check failed: ~p~n", [Reason4])
        end
    catch
        Error5:Reason5 ->
            io:format("Exception during NIF check: ~p:~p~n", [Error5, Reason5])
    end,

    io:format("Test completed~n").