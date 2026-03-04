#!/bin/bash

# Start Erlang application and run JTBD tests
set -euo pipefail

echo "Starting YAWL Process Mining Bridge application..."

# Start Erlang with application
erl -name jtbd_test@localhost -pa ebin -pa _build/default/lib/*/ebin \
    -eval '
    %% Start Mnesia first
    case mnesia:start() of
        ok ->
            io:format("Mnesia started~n"),
            %% Start the application
            case application:start(process_mining_bridge) of
        ok ->
            io:format("Application started successfully~n"),
            %% Load JTBD runner
            case c(jtbd_runner) of
                ok ->
                    io:format("JTBD runner loaded~n"),
                    %% Run all tests
                    case jtbd_runner:run_all() of
                        Results ->
                            io:format("Test results: ~p~n", [Results]),
                            halt(0)
                    end;
                {error, Reason} ->
                    io:format("Failed to load JTBD runner: ~p~n", [Reason]),
                    halt(1)
            end;
        {error, Reason} ->
            io:format("Failed to start application: ~p~n", [Reason]),
            halt(1)
    end
    ' -noshell