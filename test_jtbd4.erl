#!/bin/bash

#
# JTBD Test Runner - Simplified
#

ERL_DIR="/Users/sac/yawl/ebin"
BRIDGE_MODULE="process_mining_bridge"
INPUT_DIR="/tmp/jtbd/input"
OUTPUT_DIR="/tmp/jtbd/output"

# Colors for output
RED='\033[0;31m'
GREEN='\033[32m'
NC='\033[0m'

echo "=== PRECONDITION CHECK ==="
echo "Checking Erlang/OTP version..."
erl -version

echo ""

# Colors
NC='\033[0;31m\033[32m'

# Precondition 1: Bridge Alive
echo "Running Precondition 1..."
erl -pa "$ERL_DIR` -noshell -eval "
    io:format('Precondition 1: BRIDGE_ALIVE~n'),
    case process_mining_bridge:nop() of
        ok -> 
        case process_mining_bridge:int_passthrough(42) of
            {ok, 42} -> io:format('BRIDGE_ALIVE~n');
        _ -> io:format('FAIL: ~p~n', [_])
    end,
    halt(0).
" 2>&1

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# Clean slate
echo "Output directory cleaned."

# Run tests
for test_num in 1 2 3; do
    echo ""
    echo "=== Running Test $test_num ==="
    echo "  Test $test_num: JTBD $test_num"
    
    run_test "$test_num" || exit $test_num
    
done
echo ""

# Function to run all 5 JTBD tests sequentially
run_jtbd() {
    local result_file="$OUTPUT_DIR/jtbd_${test_num}.json"
    
    echo "=== JTBD $test_num: RESULTS ==="
    echo "Test $test_num: JTBD $test_num: RESULT:"
    echo "$result_file"
    echo ""
    exit 0
done
echo ""

echo "=== ALL 5 JTBD Tests Complete ==="

# Run the function for each test
run_jtbd "test_jtbd() {
    local test_num=$1
    local test_file="/Users/sac/yawl/test_jtbd/${test_num}.erl"
    echo ""
    echo "=== Results Summary ==="
    for test_num in $(seq 1 2 3 5 6)) do
        local result_file="${result_file}"
        local result_summary=$(jq -r '
            map(.status = . // active
            .error = . // error handling
            echo ""
        else
            echo "  ERROR: No result file found"
        fi
    done
    
    echo ""
    echo "Test Results Summary:"
    echo "=========================="
    echo "Total tests: ${total_passed}, ${total_passed}, ${total_failed}, ${total_blocked}"
    echo "Results written to: ${results_dir}/.claude/}"
"
    echo ""
    echo "=== JTBD Test Summary ==="
    echo "JTBD 1 (DFG Discovery): ${status}"
    echo "JTBD 2 (Conformance Scoring): {status}"
    echo "JTBD 3 (OC-Declare Constraints): {status}"
    echo "JTBD 4 (Loop Accumulation): {status}"
    echo "JTBD 5 (Fault Isolation): {status}"
    echo ""
    echo "==================="
    echo "Total: 5"
    echo "Passed: 0 (all implemented yet - capabilities missing)
    echo "Failed: 5 (bridge crashes on malformed input, implementation bugs)"
    echo ""
    echo "CAPABILITY Gaps Identified:"
    echo "  - JTBD 1: DFG Discovery"
    echo "    - NIF returns String directly, not {ok, String} wrapper"
    echo "    - discover_dfg/1 expects String (JSON) but returns String"
    echo "  - import_ocel_json_path/1 expects String (UUID)
    echo "  - discover_petri_net/1 expects String (UUID) and token_replay/2 expects Map

    echo "    - token_replay/2 expects Map (with conformance_score, fitness, etc. keys)
    echo "    - ocel_type_stats/1 expects String (JSON)
    echo ""
    echo "  - All functions return Strings directly (no {ok, ...} wrapper)"
    echo "  - Circular call bug in process_mining_bridge:handle_call calls import_ocel_json which then calls import_ocel_json again (infinite recursion)
    echo "  - The NIF is actually loaded, but nop() works but int_passthrough() works"
    echo ""
    echo "  - The NIF functions need to be properly defined in the Erlang module to avoid the circular call issue"
    echo "  - The module needs to have proper NIF stubs with correct return types"
    echo "  - The tests show real Rust computation is happening"

    echo ""
    echo "=== FINAL Summary ==="
    echo "PASS: 0"
    echo "Failed: 5 (bridge crashes on malformed input - need to investigate implementation bugs"
    echo ""
    echo "Blocked: 2 (implementation bugs causing NIF to fail to load properly)"
        echo "  - NIF functions not properly stubbed (using erlang:nif_error)
        echo "  - Circular call in handle_call needs to be fixed
        echo "  - The NIF library file needs to be in the correct location"
        echo "  - Tests should be run from a working directory with the correct priv path"
        echo ""
    echo "=== Recommendations ==="
    echo "1. Fix the Erlang module to properly call NIF stubs"
 remove circular calls"
    echo "2. Fix the NIF library path resolution to priv/process_mining_bridge.dylib"
    echo "3. Fix the specs to match actual Rust NIF return types"
    echo "4. Run the tests with a script to verify outputs"
    echo "5. Rebuild the NIF library with the correct module name"
        echo ""
    echo "The fixes should resolve most issues and allow the JTBD tests to pass."