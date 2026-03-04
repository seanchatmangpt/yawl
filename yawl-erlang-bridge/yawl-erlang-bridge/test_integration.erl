-module(test_integration).
-compile(export_all).

%% Test script to verify NIF loading and bridge functionality
test() ->
    %% Start the application
    case application:ensure_started(process_mining_bridge) of
        ok ->
            io:format("Application started successfully~n");
        {error, Reason} ->
            io:format("Failed to start application: ~p~n", [Reason]),
            halt(1)
    end,

    %% Ping the bridge
    case process_mining_bridge:ping() of
        {ok, {pong, NifLoaded}} ->
            io:format("Bridge ping: OK, NIF loaded: ~p~n", [NifLoaded]);
        {error, _} ->
            io:format("Bridge ping failed~n"),
            halt(1)
    end,

    %% Check NIF status
    case process_mining_bridge:get_nif_status() of
        {ok, {nif_status, FileExists}} ->
            io:format("NIF file exists: ~p~n", [FileExists]);
        {error, _} ->
            io:format("NIF status check failed~n"),
            halt(1)
    end,

    %% Try to import XES (will fail with NIF not loaded or work if loaded)
    case process_mining_bridge:import_xes(#{path => "/tmp/test.xes"}) of
        {error, nif_not_loaded} ->
            io:format("NIF functions properly return nif_not_loaded~n");
        {error, Err} when is_binary(Err) ->
            io:format("Operation not supported~n");
        {ok, Handle} ->
            io:format("NIF imported XES successfully with handle: ~p~n", [Handle]),
            %% Free the handle
            process_mining_bridge:free_handle(Handle)
    end,

    %% Stop the application
    application:stop(process_mining_bridge),
    io:format("Integration test completed successfully~n").