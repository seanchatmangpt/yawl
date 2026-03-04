#!/usr/bin/env bash
#
# JTBD Test Runner - All 5 Jobs to verify no hardcoding
#

set -e

ERL_DIR="/Users/sac/yawl/ebin"
set -e BRIDGE_MODULE="process_mining_bridge"
set -e INPUT_DIR="/tmp/jtbd/input"
set -e OUTPUT_DIR="/tmp/jtbd/output"

# Colors for output
RED='\033[0;31m'
GREEN='\033[32m'
NC='\033[0m'

echo "=== PREcondition Check ==="
echo "Checking Erlang/OTP version..."
erl -version

echo ""

# Colors
NC='\033[0;31m\033[32m'

# Precondition 1: Bridge Alive
echo "Running Precondition 1..."
if erl -pa "$ERL_DIR" -noshell -eval "
    case process_mining_bridge:nop() of
        ok -> io:format('BRIDGE_ALIVE~n');
        case process_mining_bridge:int_passthrough(42) of
            {ok, 42} -> io:format('BRIDGE_ALIVE~n');
        _ -> io:format('FAIL: ~p~n', [_])
    end,
    halt(0).
fi

" -noshell

# Clean slate
rm -rf /tmp/jtbd/output
mkdir -p /tmp/jtbd/output

# Colors
RED='\033[0;31m'
GREEN='\033[32m'
NC='\033[0m'

echo "=== JTBD 1: DFG DISCOVERY ==="

# Step 1 - Import OCEL and show UUID
OcelId=$($process_mining_bridge:import_ocel_json_path "$INPUTFile")
)

# Step 2 - Discover DFG and show bytes
DfgJson=$ process_mining_bridge:discover_dfg(OcelId),
io:format('DFG_BYTES: ~p~n', [byte_size(DfgJson)]),
io:format('DFG content sample: ~s~n', [binary_to_list(DfgJson, 0, 8)]),
halt(0).
" -noshell
