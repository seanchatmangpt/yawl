#!/usr/bin/env bash
# YAWL Build Performance Benchmark
# Measures build performance under various conditions

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${PROJECT_ROOT}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}=== YAWL Build Performance Benchmark ===${NC}"
echo "Date: $(date)"
echo "CPU: $(nproc) cores"
echo "Memory: $(free -h | grep Mem | awk '{print $2}')"
echo "Maven: $(mvn --version 2>/dev/null | head -1 || echo 'Not found')"
echo ""

# Check for Maven Daemon
if command -v mvnd >/dev/null 2>&1; then
    echo -e "${GREEN}Maven Daemon (mvnd) available${NC}"
    HAS_MVND=true
else
    echo -e "${BLUE}Tip: Install Maven Daemon for 3-5x faster builds${NC}"
    echo "  SDKMAN: sdk install mvnd"
    echo "  Homebrew: brew install mvnd"
    HAS_MVND=false
fi
echo ""

# Function to time a command
time_command() {
    local label="$1"
    shift
    local start end duration

    echo -e "${BLUE}--- $label ---${NC}"
    start=$(date +%s.%N)
    "$@" 2>&1 | tail -3
    end=$(date +%s.%N)
    duration=$(echo "$end - $start" | bc)
    echo -e "${GREEN}Duration: ${duration}s${NC}"
    echo ""
    echo "$duration"
}

# Store results
declare -a RESULTS

# Benchmark 1: Clean build (warm cache)
echo -e "${BLUE}Benchmark 1: Clean Build (warm cache)${NC}"
duration=$(time_command "Clean Build" mvn clean compile -T 1C -q)
RESULTS+=("Clean Build: ${duration}s")

# Benchmark 2: No-op build (everything cached)
echo -e "${BLUE}Benchmark 2: No-op Build (fully cached)${NC}"
duration=$(time_command "No-op Build" mvn compile -T 1C -q)
RESULTS+=("No-op Build: ${duration}s")

# Benchmark 3: Parallel build with tests
echo -e "${BLUE}Benchmark 3: Parallel Build with Tests${NC}"
duration=$(time_command "Test Build" mvn test -DskipTests=false -T 1C -q 2>/dev/null || echo "Tests skipped")
RESULTS+=("Test Build: ${duration}s")

# Benchmark 4: Maven Daemon (if available)
if [ "$HAS_MVND" = true ]; then
    echo -e "${BLUE}Benchmark 4: Maven Daemon Build${NC}"
    duration=$(time_command "Maven Daemon" mvnd clean compile -q)
    RESULTS+=("Maven Daemon: ${duration}s")
fi

# Summary
echo ""
echo -e "${BLUE}=== Benchmark Summary ===${NC}"
echo ""
for result in "${RESULTS[@]}"; do
    echo "  $result"
done

# Performance targets
echo ""
echo -e "${BLUE}=== Performance Targets ===${NC}"
echo "  Clean build: < 180s (3 min)"
echo "  No-op build: < 30s"
echo "  With mvnd: < 120s (2 min)"
echo ""

# Check if targets met
clean_time=$(echo "${RESULTS[0]}" | grep -oE '[0-9]+\.[0-9]+')
if (( $(echo "$clean_time < 180" | bc -l) )); then
    echo -e "${GREEN}Clean build target: PASSED${NC}"
else
    echo -e "${RED}Clean build target: FAILED (${clean_time}s > 180s)${NC}"
fi