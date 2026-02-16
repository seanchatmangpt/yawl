#!/bin/bash
# YAWL Performance Test Runner
# Executes complete performance baseline and load testing suite
#
# Usage:
#   ./scripts/run-performance-tests.sh [options]
#
# Options:
#   --baseline-only    Run only baseline measurements
#   --load-only        Run only load tests
#   --quick            Run quick performance check (reduced iterations)
#   --full             Run full suite (default)
#   --jmh              Run JMH benchmarks (if available)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RESULTS_DIR="$PROJECT_DIR/test-results/performance"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Parse arguments
MODE="full"
while [[ $# -gt 0 ]]; do
    case $1 in
        --baseline-only)
            MODE="baseline"
            shift
            ;;
        --load-only)
            MODE="load"
            shift
            ;;
        --quick)
            MODE="quick"
            shift
            ;;
        --full)
            MODE="full"
            shift
            ;;
        --jmh)
            MODE="jmh"
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Create results directory
mkdir -p "$RESULTS_DIR"

echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║         YAWL v5.2 Performance Testing Suite                 ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}Mode:${NC} $MODE"
echo -e "${YELLOW}Results:${NC} $RESULTS_DIR"
echo -e "${YELLOW}Timestamp:${NC} $TIMESTAMP"
echo ""

# System information
echo -e "${BLUE}System Information:${NC}"
echo "  Java Version: $(java -version 2>&1 | head -n 1)"
echo "  Available Memory: $(free -h | grep Mem | awk '{print $7}')"
echo "  CPU Cores: $(nproc)"
echo ""

# JVM settings for performance testing
export MAVEN_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

cd "$PROJECT_DIR"

case $MODE in
    baseline)
        echo -e "${GREEN}Running baseline measurements only...${NC}"
        mvn test -Dtest=EnginePerformanceBaseline 2>&1 | tee "$RESULTS_DIR/baseline-$TIMESTAMP.log"
        ;;
    
    load)
        echo -e "${GREEN}Running load tests only...${NC}"
        mvn test -Dtest=LoadTestSuite 2>&1 | tee "$RESULTS_DIR/load-$TIMESTAMP.log"
        ;;
    
    quick)
        echo -e "${GREEN}Running quick performance check...${NC}"
        mvn test -Dtest=EnginePerformanceBaseline#testCaseLaunchLatency 2>&1 | tee "$RESULTS_DIR/quick-$TIMESTAMP.log"
        ;;
    
    full)
        echo -e "${GREEN}Running full performance test suite...${NC}"
        mvn test -Dtest=PerformanceTestSuite 2>&1 | tee "$RESULTS_DIR/full-$TIMESTAMP.log"
        ;;
    
    jmh)
        echo -e "${YELLOW}JMH benchmarks not yet implemented${NC}"
        exit 1
        ;;
esac

EXIT_CODE=$?

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"

if [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✓ Performance tests completed successfully${NC}"
else
    echo -e "${RED}✗ Performance tests failed${NC}"
fi

echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "${YELLOW}Results saved to:${NC} $RESULTS_DIR"
echo ""

# Extract key metrics if available
if [ -f "$RESULTS_DIR/baseline-$TIMESTAMP.log" ]; then
    echo -e "${BLUE}Key Metrics:${NC}"
    grep -A 5 "===" "$RESULTS_DIR/baseline-$TIMESTAMP.log" 2>/dev/null || true
fi

exit $EXIT_CODE
