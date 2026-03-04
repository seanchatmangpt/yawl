%% Minimal test script to check if NIF loading works
-module(test_minimal).
-export([main/1]).

main(_) ->
    io:format("Starting minimal test...~n"),

    %% Try to load the NIF
    case code:ensure_loaded(process_mining_bridge) of
        {module, process_mining_bridge} ->
            io:format("process_mining_bridge module loaded~n");
        _ ->
            io:format("Failed to load process_mining_bridge~n"),
            halt(1)
    end,

    %% Try to call a simple function
    case process_mining_bridge:nop() of
        ok ->
            io:format("NIF nop() works~n");
        {error, Reason} ->
            io:format("NIF nop() failed: ~p~n", [Reason]),
            halt(1)
    end,

    %% Try to test OCEL import
    TestFile = "/Users/sac/yawl/test/pi-sprint-ocel.json",
    case file:read_file(TestFile) of
        {ok, _} ->
            io:format("Test file exists~n");
        {error, _} ->
            io:format("Test file not found~n"),
            halt(1)
    end,

    %% Try to import OCEL (this should work if NIF is properly loaded)
    case process_mining_bridge:import_ocel_json_path(TestFile) of
        {ok, _OcelId} ->
            io:format("OCEL import successful!~n"),
            halt(0);
        {error, Error} ->
            io:format("OCEL import failed: ~p~n", [Error]),
            halt(1)
    end.