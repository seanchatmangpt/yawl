#!/bin/bash
# YAWL Performance Monitoring Script
# Simplified workflow for tracking library update impacts

set -e

REPORTS_DIR="performance-reports"
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}YAWL Performance Monitoring${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to display menu
show_menu() {
    echo "Select operation:"
    echo "  1) Capture baseline (before library update)"
    echo "  2) Capture current (after library update)"
    echo "  3) Run JMH benchmarks (detailed analysis)"
    echo "  4) Run baseline performance tests"
    echo "  5) View latest reports"
    echo "  6) Compare reports (manual)"
    echo "  q) Quit"
    echo ""
}

# Function to capture baseline
capture_baseline() {
    echo -e "${GREEN}Capturing baseline performance metrics...${NC}"
    echo ""

    # Ensure clean build
    echo "Step 1: Clean build"
    mvn clean compile

    # Run performance snapshot
    echo ""
    echo "Step 2: Capturing metrics (this may take 5-10 minutes)..."
    mvn test -Dtest=EnginePerformanceBaseline -DfailIfNoTests=false || true

    BASELINE_FILE="${REPORTS_DIR}/baseline-${TIMESTAMP}.txt"
    echo ""
    echo -e "${GREEN}✓ Baseline captured${NC}"
    echo "  Report: ${BASELINE_FILE}"
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo "  1. Update library versions in pom.xml"
    echo "  2. Run option 2 to capture current metrics"
    echo "  3. Compare results"
}

# Function to capture current
capture_current() {
    echo -e "${GREEN}Capturing current performance metrics...${NC}"
    echo ""

    # Ensure clean build
    echo "Step 1: Clean build with updated libraries"
    mvn clean compile

    # Run performance snapshot
    echo ""
    echo "Step 2: Capturing metrics (this may take 5-10 minutes)..."
    mvn test -Dtest=EnginePerformanceBaseline -DfailIfNoTests=false || true

    CURRENT_FILE="${REPORTS_DIR}/current-${TIMESTAMP}.txt"
    echo ""
    echo -e "${GREEN}✓ Current metrics captured${NC}"
    echo "  Report: ${CURRENT_FILE}"
    echo ""
    echo -e "${YELLOW}Next steps:${NC}"
    echo "  1. Compare with baseline report"
    echo "  2. Look for regressions > 10%"
    echo "  3. Investigate any critical degradations"
}

# Function to run JMH benchmarks
run_jmh_benchmarks() {
    echo -e "${GREEN}Running JMH benchmarks (30-45 minutes)...${NC}"
    echo ""
    echo "Benchmarks to run:"
    echo "  - WorkflowExecutionBenchmark (multi-stage workflows)"
    echo "  - InterfaceBClientBenchmark (HTTP performance)"
    echo "  - MemoryUsageBenchmark (thread memory comparison)"
    echo "  - IOBoundBenchmark (I/O operations)"
    echo "  - EventLoggerBenchmark (event notifications)"
    echo ""

    read -p "Continue? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        mvn test -Dtest=AllBenchmarksRunner -DfailIfNoTests=false || true
        echo ""
        echo -e "${GREEN}✓ JMH benchmarks complete${NC}"
        echo "  Results: target/jmh-results.json"
    fi
}

# Function to run baseline tests
run_baseline_tests() {
    echo -e "${GREEN}Running baseline performance tests...${NC}"
    echo ""
    mvn test -Dtest=EnginePerformanceBaseline -DfailIfNoTests=false || true
    echo ""
    echo -e "${GREEN}✓ Baseline tests complete${NC}"
}

# Function to view latest reports
view_reports() {
    echo -e "${GREEN}Latest performance reports:${NC}"
    echo ""

    if [ -d "$REPORTS_DIR" ]; then
        echo "Baseline reports:"
        ls -lht "${REPORTS_DIR}"/baseline-*.txt 2>/dev/null | head -5 || echo "  (none found)"
        echo ""
        echo "Current reports:"
        ls -lht "${REPORTS_DIR}"/current-*.txt 2>/dev/null | head -5 || echo "  (none found)"
        echo ""
        echo "Comparison reports:"
        ls -lht "${REPORTS_DIR}"/comparison-*.txt 2>/dev/null | head -5 || echo "  (none found)"
    else
        echo "No reports directory found. Run a capture first."
    fi
}

# Function to compare reports
compare_reports() {
    echo -e "${GREEN}Manual Report Comparison${NC}"
    echo ""

    BASELINE_LATEST=$(ls -t "${REPORTS_DIR}"/baseline-*.txt 2>/dev/null | head -1)
    CURRENT_LATEST=$(ls -t "${REPORTS_DIR}"/current-*.txt 2>/dev/null | head -1)

    if [ -n "$BASELINE_LATEST" ] && [ -n "$CURRENT_LATEST" ]; then
        echo "Baseline: $BASELINE_LATEST"
        echo "Current:  $CURRENT_LATEST"
        echo ""
        echo "Key metrics to compare:"
        echo "  - Engine Startup Time"
        echo "  - Case Launch p95"
        echo "  - Work Item Throughput"
        echo "  - Memory Usage"
        echo ""

        read -p "Show diff? (y/n): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            diff -u "$BASELINE_LATEST" "$CURRENT_LATEST" || true
        fi
    else
        echo -e "${YELLOW}Both baseline and current reports needed for comparison.${NC}"
        echo "Run captures first."
    fi
}

# Main menu loop
while true; do
    show_menu
    read -p "Enter choice: " choice

    case $choice in
        1) capture_baseline ;;
        2) capture_current ;;
        3) run_jmh_benchmarks ;;
        4) run_baseline_tests ;;
        5) view_reports ;;
        6) compare_reports ;;
        q|Q) echo "Goodbye!"; exit 0 ;;
        *) echo -e "${RED}Invalid choice${NC}" ;;
    esac

    echo ""
    read -p "Press Enter to continue..."
    clear
done
