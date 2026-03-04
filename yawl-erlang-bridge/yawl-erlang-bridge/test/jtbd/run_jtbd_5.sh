#!/bin/bash

# JTBD 5 Fault Isolation Test Runner
# This script runs the fault isolation test and outputs results to JSON

set -e

# Ensure test directories exist
mkdir -p /tmp/jtbd/output

# Start Erlang and run the test
echo "Running JTBD 5: Fault Isolation / Crash Recovery Test..."

# Compile the test
erlc -I src -o test/jtbd test/jtbd/jtbd_5_fault_isolation.erl

# Run the test
erl -pa ebin -pa test/jtbd -pa src -eval "
    case jtbd_5_fault_isolation:run() of
        {ok, ProofMap} ->
            % Convert map to JSON-like format
            Json = jsx:encode(maps:to_list(ProofMap)),
            file:write_file('/tmp/jtbd/output/crash-recovery-proof.json', Json),
            io:format('Test completed successfully~n'),
            halt(0);
        {error, Reason} ->
            io:format('Test failed: ~p~n', [Reason]),
            halt(1)
    end
" -noshell -sname jtbd_test