#!/bin/bash

# YAWL Erlang Bridge Benchmark Runner
# This script runs all benchmark suites for the Erlang ↔ Rust NIF bridge

set -e

# Configuration
BEAM_DIR="../../ebin"
BENCH_DIR="."
TEST_LOG_DIR="/tmp"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== YAWL Erlang Bridge Benchmark Suite ===${NC}"
echo "Starting at $(date)"

# Check if Erlang is available
if ! command -v erl &> /dev/null; then
    echo -e "${RED}Error: Erlang/OTP not found${NC}"
    exit 1
fi

# Check if Rust NIF is compiled
if [ ! -f "$BEAM_DIR/yawl_process_mining.so" ]; then
    echo -e "${YELLOW}Warning: NIF library not found at $BEAM_DIR/yawl_process_mining.so${NC}"
    echo "Please compile the Rust NIF first:"
    echo "  cd ../../"
    echo "  cargo build --release"
    echo "  cp target/release/libyawl_process_mining.so ebin/"
    exit 1
fi

# Create test directories
mkdir -p "$TEST_LOG_DIR"

# Compile benchmark modules
echo -e "${YELLOW}Compiling benchmark modules...${NC}"
erlc -o "$BEAM_DIR" "$BENCH_DIR"/*.erl

# Function to run individual benchmark
run_benchmark() {
    local bench_name="$1"
    local bench_module="$2"
    local args="$3"
    
    echo -e "\n${GREEN}Running $bench_name...${NC}"
    echo "----------------------------------------"
    
    if [ -n "$args" ]; then
        erl -pa "$BEAM_DIR" -s "$bench_module" start "$args" -s init stop
    else
        erl -pa "$BEAM_DIR" -s "$bench_module" start -s init stop
    fi
}

# Run benchmarks
echo -e "\n${YELLOW}=== Starting Benchmarks ===${NC}"

# 1. NIF Overhead Benchmark
run_benchmark "NIF Overhead" "benchmark_nif_overhead" ""

# 2. Data Marshalling Benchmark
run_benchmark "Data Marshalling" "benchmark_data_marshalling" ""

# 3. Process Mining Operations Benchmark
run_benchmark "Process Mining Operations" "benchmark_pm_operations" "default"

# 4. Concurrency Benchmark
run_benchmark "Concurrency" "benchmark_concurrency" "50 1000"

echo -e "\n${GREEN}=== All Benchmarks Complete ===${NC}"
echo "Results saved to test directories:"
echo "  - Test logs: $TEST_LOG_DIR/"
echo "  - NIF library: $BEAM_DIR/"
