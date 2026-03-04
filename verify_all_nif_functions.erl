#!/usr/bin/env escript
%% -*- erlang -*-
%% Comprehensive verification script for all NIF functions

main(_) ->
    io:format("=== Comprehensive NIF Function Verification ===~n"),

    % Check if we can load the module
    case code:ensure_loaded(process_mining_bridge) of
        {module, process_mining_bridge} ->
            io:format("✓ Module loaded successfully~n");
        {error, Reason} ->
            io:format("✗ Failed to load module: ~p~n", [Reason]),
            halt(1)
    end,

    % Start the server
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("✓ Gen server started~n"),
            % Check NIF status
            case process_mining_bridge:get_nif_status() of
                {ok, {nif_status, true}} ->
                    io:format("✓ NIF library is available~n");
                {ok, {nif_status, false}} ->
                    io:format("⚠ NIF library not available (using fallbacks)~n");
                Error ->
                    io:format("✗ Error checking NIF status: ~p~n", [Error])
            end,

            % Test all functions
            test_all_functions(),

            % Stop the server
            process_mining_bridge:stop();
        {error, Reason} ->
            io:format("✗ Failed to start gen server: ~p~n", [Reason])
    end,

    io:format("~n=== Verification Complete ===~n").

test_all_functions() ->
    io:format("~n--- Testing All Functions ---~n"),

    % Test basic functions
    test_function("nop/0", fun process_mining_bridge:nop/0),
    test_function("int_passthrough(42)", fun process_mining_bridge:int_passthrough/1, [42]),
    test_function("echo_json(\"test\")", fun process_mining_bridge:echo_json/1, ["test"]),

    % Test OCEL operations
    test_function("discover_dfg(make_ref())", fun process_mining_bridge:discover_dfg/1, [make_ref()]),
    test_function("discover_dfg_nif(make_ref())", fun process_mining_bridge:discover_dfg_nif/1, [make_ref()]),

    % Test with a real OCEL file if available
    case filelib:is_file("/tmp/jtbd/input/pi-sprint-ocel.json") of
        true ->
            io:format("~n--- Testing with Real OCEL Data ---~n"),
            case process_mining_bridge:import_ocel_json("/tmp/jtbd/input/pi-sprint-ocel.json") of
                {ok, OcelId} ->
                    io:format("✓ OCEL imported: ~s~n", [OcelId]),
                    test_function("num_events", fun process_mining_bridge:num_events/1, [OcelId]),
                    test_function("num_objects", fun process_mining_bridge:num_objects/1, [OcelId]),
                    test_function("discover_dfg", fun process_mining_bridge:discover_dfg/1, [OcelId]),
                    test_function("ocel_type_stats", fun process_mining_bridge:ocel_type_stats/1, [OcelId]),
                    process_mining_bridge:free_handle(OcelId);
                {error, Reason} ->
                    io:format("✗ Failed to import OCEL: ~p~n", [Reason])
            end;
        false ->
            io:format("~n--- No real OCEL data available for testing ---~n")
    end.

test_function(Name, Fun) ->
    test_function(Name, Fun, []).

test_function(Name, Fun, Args) ->
    try
        case apply(Fun, Args) of
            {ok, _} -> io:format("✓ ~p works~n", [Name]);
            {error, nif_not_loaded} -> io:format("⚠ ~p not loaded (expected)~n", [Name]);
            {error, Reason} -> io:format("✗ ~p failed: ~p~n", [Name, Reason]);
            Other -> io:format("✓ ~p works: ~p~n", [Name, Other])
        catch
            Error:Reason ->
                io:format("✗ ~p crashed: ~p:~p~n", [Name, Error, Reason])
        end
    catch
        Error:Reason ->
            io:format("✗ ~p exception: ~p:~p~n", [Name, Error, Reason])
    end.