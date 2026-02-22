#!/bin/bash

# YAWL Performance Benchmark Runner Script
# Usage: ./scripts/benchmark.sh [options] [benchmark-class]

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
BENCHMARK_DIR="$PROJECT_ROOT/yawl-benchmark"
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseCompactObjectHeaders -XX:+UseG1GC"
DEFAULT_BENCHMARK="org.yawlfoundation.yawl.benchmark.YAWLEngineBenchmarks"
DEFAULT_ITERATIONS=10
DEFAULT_WARMUP=5

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print usage
print_usage() {
    echo "YAWL Performance Benchmark Runner"
    echo ""
    echo "Usage: $0 [options] [benchmark-class]"
    echo ""
    echo "Options:"
    echo "  -h, --help              Show this help message"
    echo "  -n, --iterations N     Number of measurement iterations (default: $DEFAULT_ITERATIONS)"
    echo "  -w, --warmup N         Number of warmup iterations (default: $DEFAULT_WARMUP)"
    echo "  -t, --threads N        Number of threads (default: auto)"
    echo "  -f, --forks N          Number of fork JVMs (default: 3)"
    echo "  -o, --output FILE      Output file for results (default: results/benchmark-$(date +%Y%m%d-%H%M%S).json)"
    echo "  -v, --verbose          Verbose output"
    echo "  -d, --debug            Debug mode"
    echo ""
    echo "Benchmark Classes:"
    echo "  YAWLEngineBenchmarks          - Core engine performance"
    echo "  WorkflowPatternBenchmarks    - Control flow patterns"
    echo "  ConcurrencyBenchmarks         - Multi-threaded performance"
    echo "  MemoryBenchmarks             - Memory usage and GC"
    echo "  IntegrationBenchmarks        - Integration components"
    echo "  StressTestBenchmarks         - Stress testing"
    echo ""
    echo "Examples:"
    echo "  $0 YAWLEngineBenchmarks                    # Run engine benchmarks"
    echo "  $0 -n 20 -w 10 ConcurrencyBenchmarks       # With more iterations"
    echo "  $0 -o results/my-benchmark.json MemoryBenchmarks  # Custom output"
}

# Parse arguments
BENCHMARK_CLASS=$DEFAULT_BENCHMARK
ITERATIONS=$DEFAULT_ITERATIONS
WARMUP=$DEFAULT_WARMUP
THREADS=""
FORKS=3
OUTPUT_FILE=""
VERBOSE=false
DEBUG=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        -n|--iterations)
            ITERATIONS="$2"
            shift 2
            ;;
        -w|--warmup)
            WARMUP="$2"
            shift 2
            ;;
        -t|--threads)
            THREADS="$2"
            shift 2
            ;;
        -f|--forks)
            FORKS="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -d|--debug)
            DEBUG=true
            shift
            ;;
        -*)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
        *)
            BENCHMARK_CLASS="org.yawlfoundation.yawl.benchmark.$1"
            shift
            ;;
    esac
done

# Set default output file if not specified
if [[ -z "$OUTPUT_FILE" ]]; then
    OUTPUT_FILE="results/benchmark-$(date +%Y%m%d-%H%M%S).json"
fi

# Create results directory
mkdir -p "$(dirname "$OUTPUT_FILE")"

# Print configuration
echo -e "${BLUE}YAWL Performance Benchmark Runner${NC}"
echo -e "${BLUE}==================================${NC}"
echo -e "${GREEN}Benchmark Class: ${BENCHMARK_CLASS}${NC}"
echo -e "${GREEN}Iterations: ${ITERATIONS}${NC}"
echo -e "${GREEN}Warmup: ${WARMUP}${NC}"
if [[ -n "$THREADS" ]]; then
    echo -e "${GREEN}Threads: ${THREADS}${NC}"
fi
echo -e "${GREEN}Forks: ${FORKS}${NC}"
echo -e "${GREEN}Output: ${OUTPUT_FILE}${NC}"
echo ""

# Check if benchmark directory exists
if [[ ! -d "$BENCHMARK_DIR" ]]; then
    echo -e "${RED}Error: Benchmark directory not found: $BENCHMARK_DIR${NC}"
    echo "Please run 'mvn clean install' first to build the benchmark module."
    exit 1
fi

# Build command
JMH_ARGS=()
JMH_ARGS+=("-rf json") # Results format
JMH_ARGS+=("-rff" "$OUTPUT_FILE") # Results file
JMH_ARGS+=("-wi" "$WARMUP") # Warmup iterations
JMH_ARGS+=("-i" "$ITERATIONS") # Measurement iterations
JMH_ARGS+=("-f" "$FORKS") # Forks

if [[ -n "$THREADS" ]]; then
    JMH_ARGS+=("-t" "$THREADS") # Threads
fi

# Add benchmark class
JMH_ARGS+=("$BENCHMARK_CLASS")

# Build full command
JMH_JAR="$BENCHMARK_DIR/target/yawl-benchmark.jar"
if [[ ! -f "$JMH_JAR" ]]; then
    echo -e "${YELLOW}Warning: JMH JAR not found, building first...${NC}"
    (cd "$BENCHMARK_DIR" && mvn clean package -DskipTests)
fi

FULL_CMD="java $JAVA_OPTS -jar $JMH_JAR ${JMH_ARGS[*]}"

if $VERBOSE || $DEBUG; then
    echo -e "${BLUE}Command:${NC}"
    echo "$FULL_CMD"
    echo ""
fi

# Run benchmarks
echo -e "${BLUE}Starting benchmarks...${NC}"
if $DEBUG; then
    echo -e "${YELLOW}Debug mode: Printing command only${NC}"
    echo "$FULL_CMD"
    exit 0
fi

# Execute benchmark
cd "$BENCHMARK_DIR"
eval "$FULL_CMD"

# Check result
if [[ $? -eq 0 ]]; then
    echo -e "${GREEN}✓ Benchmarks completed successfully${NC}"
    echo -e "${GREEN}Results saved to: ${OUTPUT_FILE}${NC}"
else
    echo -e "${RED}✗ Benchmarks failed${NC}"
    exit 1
fi

# Display summary
echo ""
echo -e "${BLUE}Summary${NC}"
echo -e "${BLUE}=======${NC}"

if [[ -f "$OUTPUT_FILE" && command -v jq &> /dev/null ]]; then
    echo -e "${GREEN}Benchmark class:${NC} $(jq -r '.benchmark' "$OUTPUT_FILE" 2>/dev/null || echo 'N/A')"
    echo -e "${GREEN}Iterations:${NC} $ITERATIONS"
    echo -e "${GREEN}Forks:${NC} $FORKS"
fi

echo ""
echo -e "${BLUE}Next steps:${NC}"
echo "1. Review results: ${OUTPUT_FILE}"
echo "2. Compare against baseline: docs/v6/latest/performance/baseline-metrics.md"
echo "3. Run with different classes: $0 ConcurrencyBenchmarks"

exit 0
