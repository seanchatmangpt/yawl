#!/usr/bin/env escript
%% -*- erlang -*-
%% Verification script for NIF function naming

main(_) ->
    io:format("=== NIF Function Verification ===~n"),

    % Check if we can load the module
    case code:ensure_loaded(process_mining_bridge) of
        {module, process_mining_bridge} ->
            io:format("✓ Module loaded successfully~n");
        {error, Reason} ->
            io:format("✗ Failed to load module: ~p~n", [Reason]),
            halt(1)
    end,

    % Check NIF status - need to start the server first
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("✓ Gen server started~n"),
            case process_mining_bridge:get_nif_status() of
                {ok, {nif_status, true}} ->
                    io:format("✓ NIF library is available~n");
                {ok, {nif_status, false}} ->
                    io:format("⚠ NIF library not available (using fallbacks)~n");
                Error1 ->
                    io:format("✗ Error checking NIF status: ~p~n", [Error1])
            end,
            process_mining_bridge:stop();
        {error, {already_started, _Pid}} ->
            io:format("✓ Gen server already running~n"),
            case process_mining_bridge:get_nif_status() of
                {ok, {nif_status, true}} ->
                    io:format("✓ NIF library is available~n");
                {ok, {nif_status, false}} ->
                    io:format("⚠ NIF library not available (using fallbacks)~n");
                Error2 ->
                    io:format("✗ Error checking NIF status: ~p~n", [Error2])
            end;
        {error, Reason} ->
            io:format("✗ Failed to start gen server: ~p~n", [Reason]),
            ok
    end,

    % Test basic NIF functions
    io:format("~n--- Testing Basic NIF Functions ---~n"),
    case process_mining_bridge:nop() of
        {ok, ok} -> io:format("✓ nop/0 works~n");
        {error, nif_not_loaded} -> io:format("⚠ nop/0 not loaded (expected)~n");
        Error3 -> io:format("✗ nop/0 failed: ~p~n", [Error3])
    end,

    case process_mining_bridge:int_passthrough(42) of
        {ok, 42} -> io:format("✓ int_passthrough/1 works~n");
        {error, nif_not_loaded} -> io:format("⚠ int_passthrough/1 not loaded (expected)~n");
        Error4 -> io:format("✗ int_passthrough/1 failed: ~p~n", [Error4])
    end,

    % Test DFG functions
    io:format("~n--- Testing DFG Functions ---~n"),
    case process_mining_bridge:discover_dfg(make_ref()) of
        {error, nif_not_loaded} -> io:format("⚠ discover_dfg/1 not loaded (expected)~n");
        {error, {not_an_OCEL, _}} -> io:format("✓ discover_dfg/1 called but handle invalid (expected)~n");
        Error5 -> io:format("✗ discover_dfg/1 failed: ~p~n", [Error5])
    end,

    case process_mining_bridge:discover_dfg_nif(make_ref()) of
        {error, nif_not_loaded} -> io:format("⚠ discover_dfg_nif/1 not loaded (expected)~n");
        Error6 -> io:format("✗ discover_dfg_nif/1 failed: ~p~n", [Error6])
    end,

    io:format("~n=== Verification Complete ===~n").