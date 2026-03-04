#!/bin/bash

# Test script for YAWL Process Mining NIF
# This script verifies the NIF integration

echo "=== Testing YAWL Process Mining NIF ==="

# Change to the Erlang app directory
cd ../../yawl-erlang-bridge/yawl-erlang-bridge

echo "1. Checking if NIF library exists..."
if [ -f "priv/yawl_process_mining.dylib" ]; then
    echo "✓ NIF library found at priv/yawl_process_mining.dylib"
else
    echo "✗ NIF library not found"
    exit 1
fi

echo ""
echo "2. Starting Erlang and testing NIF..."
# Start Erlang and test the NIF
erl -pa ebin -pa deps/*/ebin -eval '
    % Start the application
    case application:start(process_mining_bridge) of
        ok ->
            io:format("✓ Application started successfully~n"),
            % Test ping
            case process_mining_bridge:ping() of
                {ok, {pong, true}} ->
                    io:format("✓ NIF is loaded and working~n");
                {ok, {pong, false}} ->
                    io:format("✓ Application started but NIF not found~n");
                {error, Reason} ->
                    io:format("✗ Ping failed: ~p~n", [Reason])
            end,
            % Check NIF status
            case process_mining_bridge:get_nif_status() of
                {ok, {nif_status, true}} ->
                    io:format("✓ NIF library file exists~n");
                {ok, {nif_status, false}} ->
                    io:format("✗ NIF library file not found~n");
                {error, Reason} ->
                    io:format("✗ Status check failed: ~p~n", [Reason])
            end;
        {error, {already_started, _}} ->
            io:format("✓ Application already started~n");
        {error, Reason} ->
            io:format("✗ Failed to start application: ~p~n", [Reason])
    end,
    init:stop().'