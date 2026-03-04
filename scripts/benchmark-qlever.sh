#!/usr/bin/env bash
#
# YAWL SPARQL Engine Benchmark Script
#
# IMPORTANT: QLever is an embedded Java/C++ FFI bridge (NOT Docker, NOT HTTP)
# This script runs JMH benchmarks using QLeverEmbeddedSparqlEngine directly.
#
# Usage: ./scripts/benchmark-qlever.sh [--help] [engine-type]
#   engine-type: qlever-embedded, oxigraph, or "all" to run all

set -euo pipefail

# Show help function
show_help() {
    cat << EOF
Usage: $0 [--help] [engine-type]

This script runs JMH benchmarks for SPARQL engine implementations.

IMPORTANT: QLever is an embedded FFI engine, NOT a Docker HTTP service.
All QLever benchmarks use QLeverEmbeddedSparqlEngine (in-process).

Arguments:
  engine-type    Specific engine to benchmark (qlever-embedded, oxigraph)
                 "all" to run all engines (default)

Options:
  --help         Show this help message and exit

Environment variables:
  BENCHMARK_UPLOAD_URL  URL to upload benchmark results (optional)

Examples:
  $0                    # Run all benchmarks
  $0 qlever-embedded    # Run only QLever embedded benchmark
  $0 oxigraph           # Run only Oxigraph benchmark

Prerequisites:
  - QLever embedded: Build native library first
    cd yawl-qlever && mvn compile
  - Oxigraph: Start yawl-native service
    ./scripts/start-yawl-native.sh
EOF
}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
JMH_ARGS="-rf json -rff benchmark-results.json -wi 3 -i 5 -f 1 -t 1"
OUTPUT_DIR="benchmark-results"

# Default engine types to benchmark (NO qlever-http - QLever is embedded only)
ENGINE_TYPES=("qlever-embedded" "oxigraph")

# Parse command line arguments
if [[ $# -eq 1 ]]; then
    case $1 in
        "qlever-embedded"|"oxigraph")
            ENGINE_TYPES=("$1")
            ;;
        "all")
            # Run all engines
            ;;
        "--help")
            show_help
            exit 0
            ;;
        "qlever-http")
            echo -e "${RED}Error: qlever-http is not supported.${NC}"
            echo "QLever is an embedded FFI engine, not an HTTP service."
            echo "Use 'qlever-embedded' instead."
            exit 1
            ;;
        *)
            echo -e "${RED}Error: Invalid engine type: $1${NC}"
            show_help
            exit 1
            ;;
    esac
fi

# Show help if --help is the first argument
if [[ "${1:-}" == "--help" ]]; then
    show_help
    exit 0
fi

# Check if we're in the right directory
if [[ ! -f "pom.xml" ]]; then
    echo -e "${RED}Error: Must run from the YAWL root directory${NC}"
    exit 1
fi

# Function to check if an engine is available
check_engine() {
    local engine_type=$1

    echo -e "${BLUE}Checking $engine_type...${NC}"

    case $engine_type in
        "qlever-embedded")
            if [ -f "yawl-qlever/target/classes/org/yawlfoundation/yawl/qlever/QLeverEmbeddedSparqlEngine.class" ]; then
                echo -e "${GREEN}✓ QLever embedded engine is compiled${NC}"
                return 0
            else
                echo -e "${RED}✗ QLever embedded engine not compiled${NC}"
                echo "  Run: cd yawl-qlever && mvn compile"
                return 1
            fi
            ;;
        "oxigraph")
            if curl -s -f "http://localhost:8083/sparql/health" > /dev/null 2>&1; then
                echo -e "${GREEN}✓ Oxigraph is available${NC}"
                return 0
            else
                echo -e "${RED}✗ Oxigraph is not available at localhost:8083${NC}"
                echo "  Run: ./scripts/start-yawl-native.sh"
                return 1
            fi
            ;;
    esac
}

# Function to run benchmark for a specific engine
run_benchmark() {
    local engine_type=$1
    local output_file="benchmark-results/${engine_type}-results.json"

    echo -e "\n${BLUE}===========================================${NC}"
    echo -e "${BLUE}Benchmarking $engine_type${NC}"
    echo -e "${BLUE}===========================================${NC}"

    # Run the benchmark
    echo -e "${YELLOW}Running JMH benchmark...${NC}"
    if mvn -pl yawl-benchmark clean install -q; then
        java -jar yawl-benchmark/target/benchmarks.jar $JMH_ARGS \
            -rf json \
            -rff "$output_file" \
            QLeverBenchmark \
            -p engineType="$engine_type"

        echo -e "${GREEN}Benchmark completed for $engine_type${NC}"
        echo "Results saved to: $output_file"

        # Print summary
        if [[ -f "$output_file" ]]; then
            echo -e "\n${YELLOW}Benchmark Summary for $engine_type:${NC}"
            jq '.benchmarks[] | "\(.benchmark): \(.score.error) ms (p99: \(.score.p99))"' "$output_file" 2>/dev/null || \
            echo "Results available in JSON file"
        fi
    else
        echo -e "${RED}Failed to build benchmark module${NC}"
        return 1
    fi
}

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Print banner
echo -e "${BLUE}"
echo "================================================"
echo "  YAWL SPARQL Engine Benchmark Suite"
echo ""
echo "  IMPORTANT: QLever is EMBEDDED FFI (not HTTP)"
echo "================================================"
echo -e "${NC}"

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

# Check Java
if ! java -version > /dev/null 2>&1; then
    echo -e "${RED}Error: Java is not installed${NC}"
    exit 1
fi

# Check Maven
if ! mvn -version > /dev/null 2>&1; then
    echo -e "${RED}Error: Maven is not installed${NC}"
    exit 1
fi

# Check if required engines are available
echo ""
for engine_type in "${ENGINE_TYPES[@]}"; do
    check_engine "$engine_type" || true
done

# Run benchmarks
echo -e "\n${BLUE}Starting benchmarks...${NC}"

for engine_type in "${ENGINE_TYPES[@]}"; do
    run_benchmark "$engine_type"

    # Small delay between benchmarks
    sleep 2
done

# Generate summary report
echo -e "\n${BLUE}================================================${NC}"
echo -e "${BLUE}Benchmark Summary${NC}"
echo -e "${BLUE}================================================${NC}"

for engine_type in "${ENGINE_TYPES[@]}"; do
    result_file="$OUTPUT_DIR/${engine_type}-results.json"
    if [[ -f "$result_file" ]]; then
        echo -e "\n${GREEN}$engine_type:${NC}"
        echo -e "${YELLOW}Results file:${NC} $result_file"

        # Try to extract key metrics
        if command -v jq > /dev/null; then
            echo -e "${YELLOW}Average execution times:${NC}"
            jq '.benchmarks[] | "\(.benchmark): \(.score.error) ± \(.score.error / .score.mean * 100 | round)%"' "$result_file" 2>/dev/null || \
            echo "Run 'jq' to analyze JSON results"
        fi
    else
        echo -e "${RED}$engine_type: No results generated${NC}"
    fi
done

echo -e "\n${GREEN}All benchmarks completed!${NC}"
echo "Results are saved in the $OUTPUT_DIR directory."

# Optional: Upload results if configured
if [ ! -z "$BENCHMARK_UPLOAD_URL" ]; then
    echo -e "\n${YELLOW}Uploading results to $BENCHMARK_UPLOAD_URL...${NC}"
    # Add upload logic here if needed
fi
