#!/usr/bin/env escript
%% -*- erlang -*-
%%! -pa _build/default/lib/*/ebin -pa ebin -pa priv

%% Test script for Process Mining Example
%% Run from: /Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge

main(_Args) ->
    io:format("~n=== YAWL Process Mining NIF Test ===~n~n"),

    %% Start required applications
    io:format("Starting applications...~n"),
    application:start(sasl),
    application:start(crypto),
    application:start(mnesia),

    %% Initialize Mnesia schema if needed
    case mnesia:create_schema([node()]) of
        ok -> io:format("  Mnesia schema created~n");
        {error, {already_exists, _}} -> io:format("  Mnesia schema exists~n")
    end,

    %% Create handle_registry table if needed
    case mnesia:create_table(handle_registry, [
        {attributes, record_info(fields, handle_registry)},
        {disc_copies, [node()]},
        {type, set}
    ]) of
        {atomic, ok} -> io:format("  handle_registry table created~n");
        {aborted, {already_exists, handle_registry}} ->
            io:format("  handle_registry table exists~n")
    end,

    %% Start the process mining bridge
    io:format("~nStarting process_mining_bridge...~n"),
    case process_mining_bridge:start_link() of
        {ok, _Pid} ->
            io:format("  Bridge started successfully~n~n"),

            %% Run the example
            XesPath = "/Users/sac/yawl/yawl-rust4pm/rust4pm/examples/sample_log.xes",
            io:format("Running pm_example:quick_start()...~n~n"),

            pm_example:quick_start(),

            io:format("\n~n=== Full Workflow Test ===~n"),
            case pm_example:run_complete(XesPath) of
                {ok, Result} ->
                    io:format("~n~n=== XES TEST PASSED ===~n"),
                    io:format("Result keys: ~p~n", [maps:keys(Result)]),

                    %% Now run OCEL example
                    io:format("~n=== Running OCEL Example ===~n"),
                    pm_example:run_ocel_example(),
                    halt(0);

                {error, Reason} ->
                    io:format("~n~n=== XES TEST FAILED ===~n"),
                    io:format("Error: ~p~n", [Reason]),
                    halt(1)
            end;

        {error, {already_started, _}} ->
            io:format("  Bridge already started~n"),
            run_example();

        {error, Reason} ->
            io:format("~n~n=== BRIDGE START FAILED ===~n"),
            io:format("Error: ~p~n", [Reason]),
            io:format("~nHint: Check that the NIF library is in priv/~n"),
            halt(1)
    end.

run_example() ->
    XesPath = "/Users/sac/yawl/yawl-rust4pm/rust4pm/examples/sample_log.xes",
    case pm_example:run_complete(XesPath) of
        {ok, Result} ->
            io:format("~n~n=== TEST PASSED ===~n"),
            io:format("Result keys: ~p~n", [maps:keys(Result)]),
            halt(0);
        {error, Reason} ->
            io:format("~n~n=== TEST FAILED ===~n"),
            io:format("Error: ~p~n", [Reason]),
            halt(1)
    end.
