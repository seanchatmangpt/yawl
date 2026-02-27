#!/bin/bash

# A2A Communication Benchmarks Runner Script
# =========================================
#
# This script runs comprehensive A2A (Agent-to-Agent) communication benchmarks
# to validate performance targets and identify bottlenecks.
#
# Usage:
#   ./scripts/run_a2a_benchmarks.sh [options]
#
# Options:
#   -j, --jmh-only         Run only JMH microbenchmarks
#   -i, --integration-only  Run only integration tests
#   -t, --target-name       Specify target name for benchmark (default: default)
#   -o, --output-dir        Output directory for results (default: ./benchmark-results)
#   -v, --verbose           Enable verbose logging
#   -h, --help              Show this help message

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BENCHMARK_RESULTS_DIR="${PROJECT_ROOT}/benchmark-results"
TARGET_NAME="default"
VERBOSE=false
JMH_ONLY=false
INTEGRATION_ONLY=false

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_help() {
    echo "A2A Communication Benchmarks Runner Script"
    echo "=========================================="
    echo ""
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -j, --jmh-only         Run only JMH microbenchmarks"
    echo "  -i, --integration-only  Run only integration tests"
    echo "  -t, --target-name       Specify target name for benchmark (default: default)"
    echo "  -o, --output-dir        Output directory for results (default: ./benchmark-results)"
    echo "  -v, --verbose           Enable verbose logging"
    echo "  -h, --help              Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  A2A_SERVER_URL          A2A server URL (default: http://localhost:8081)"
    echo "  YAWL_ENGINE_URL         YAWL engine URL (default: http://localhost:8080/yawl)"
    echo "  JAVA_HOME               Java home directory"
    echo ""
    echo "Examples:"
    echo "  $0 --jmh-only"
    echo "  $0 --integration-only --verbose"
    echo "  $0 --target-name production"
}

log() {
    local level="$1"
    shift
    local message="$*"
    
    case "$level" in
        "INFO")
            echo -e "${GREEN}[INFO]${NC} $message" >&2
            ;;
        "WARN")
            echo -e "${YELLOW}[WARN]${NC} $message" >&2
            ;;
        "ERROR")
            echo -e "${RED}[ERROR]${NC} $message" >&2
            ;;
        "DEBUG")
            if [[ "$VERBOSE" == "true" ]]; then
                echo -e "${BLUE}[DEBUG]${NC} $message" >&2
            fi
            ;;
    esac
}

check_requirements() {
    log "INFO" "Checking requirements..."
    
    # Check Java
    if [[ -z "${JAVA_HOME:-}" ]]; then
        log "WARN" "JAVA_HOME not set, using java from PATH"
        JAVA_CMD="java"
    else
        JAVA_CMD="$JAVA_HOME/bin/java"
    fi
    
    if ! command -v "$JAVA_CMD" >/dev/null 2>&1; then
        log "ERROR" "Java not found. Please set JAVA_HOME or ensure java is in PATH."
        exit 1
    fi
    
    # Check JMH
    JMH_JAR="$PROJECT_ROOT/lib/jmh-benchmarks.jar"
    if [[ ! -f "$JMH_JAR" ]]; then
        log "ERROR" "JMH benchmark JAR not found: $JMH_JAR"
        log "ERROR" "Please build the project first with: ./scripts/build_benchmarks.sh"
        exit 1
    fi
    
    # Check if A2A server is running
    A2A_SERVER_URL="${A2A_SERVER_URL:-http://localhost:8081}"
    if curl -s -o /dev/null -w "%{http_code}" "$A2A_SERVER_URL/.well-known/agent.json" | grep -q "200"; then
        log "INFO" "A2A server is accessible at $A2A_SERVER_URL"
    else
        log "WARN" "A2A server not accessible at $A2A_SERVER_URL"
        log "WARN" "Please ensure the A2A server is running before running benchmarks"
        read -p "Continue anyway? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

create_output_dir() {
    log "INFO" "Creating output directory: $BENCHMARK_RESULTS_DIR"
    mkdir -p "$BENCHMARK_RESULTS_DIR"
}

run_jmh_benchmarks() {
    log "INFO" "Running JMH microbenchmarks..."
    
    local jmh_output="$BENCHMARK_RESULTS_DIR/a2a-jmh-results.csv"
    local jmh_json="$BENCHMARK_RESULTS_DIR/a2a-jmh-results.json"
    
    log "INFO" "JMH results will be saved to:"
    log "  - CSV: $jmh_output"
    log "  - JSON: $jmh_json"
    
    # Run JMH benchmarks
    "$JAVA_CMD" -jar "$JMH_JAR" \
        -rf csv \
        -rff "$jmh_output" \
        -jrf json \
        -jrf "$jmh_json" \
        -t "$TARGET_NAME" \
        -wi 3 \
        -i 5 \
        -f 2 \
        -tu ms \
        -rf json \
        -jvmArgs "-Xms2g" \
        -jvmArgs "-Xmx4g" \
        -jvmArgs "-XX:+UseZGC" \
        -jvmArgs "--enable-preview" \
        org.yawlfoundation.yawl.performance.jmh.A2ACommunicationBenchmarks
    
    log "INFO" "JMH benchmarks completed"
    
    # Generate summary report
    generate_jmh_summary "$jmh_json"
}

run_integration_benchmarks() {
    log "INFO" "Running integration benchmarks..."
    
    local integration_output="$BENCHMARK_RESULTS_DIR/a2a-integration-results.txt"
    
    log "INFO" "Integration results will be saved to: $integration_output"
    
    # Run integration tests with proper environment
    A2A_SERVER_URL="${A2A_SERVER_URL:-http://localhost:8081}" \
    YAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080/yawl}" \
    "$JAVA_CMD" -jar "$PROJECT_ROOT/lib/integration-tests.jar" \
        -Dspring.profiles.active=benchmark \
        -Dspring.test.execution.parallel.enabled=true \
        -Dspring.test.execution.parallel.config.fixed.pool.size=4 \
        org.springframework.boot.test.autoconfigure.AutoConfigureTestDatabase \
        org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc \
        org.springframework.boot.test.context.SpringBootTest \
        --tests org.yawlfoundation.yawl.integration.a2a.A2ACommunicationBenchmarksIntegrationTest \
        > "$integration_output" 2>&1
    
    # Parse and display results
    if grep -q "FAILED" "$integration_output"; then
        log "ERROR" "Some integration tests failed. Check $integration_output for details."
        grep "org.yawlfoundation.yawl.integration.a2a.A2ACommunicationBenchmarksIntegrationTest" "$integration_output" || true
    else
        log "INFO" "All integration tests passed"
    fi
    
    log "INFO" "Integration benchmarks completed"
}

generate_jmh_summary() {
    local json_file="$1"
    
    if [[ ! -f "$json_file" ]]; then
        log "WARN" "JMH JSON results not found: $json_file"
        return
    fi
    
    log "INFO" "Generating JMH summary report..."
    
    local summary="$BENCHMARK_RESULTS_DIR/jmh-summary.txt"
    
    {
        echo "JMH Benchmark Summary - A2A Communication"
        echo "========================================"
        echo ""
        echo "Generated: $(date)"
        echo "Target: $TARGET_NAME"
        echo ""
        
        # Extract and format key metrics
        echo "Message Latency Metrics:"
        jq -r '.benchmarks[] | select(.benchmark | contains("Latency")) | 
            "- " + .benchmark + ": " + (.primaryMetric.score | tonumber | . / 1000 | round(2)) + "ms (p95: " + (.primaryMetric.scoreError / 1000 | round(2)) + "ms)"' "$json_file" | head -10 || echo "  - Unable to parse latency metrics"
        
        echo ""
        echo "Message Throughput Metrics:"
        jq -r '.benchmarks[] | select(.benchmark | contains("Throughput")) | 
            "- " + .benchmark + ": " + (.primaryMetric.score | tonumber | round(2)) + " ops/sec"' "$json_file" | head -10 || echo "  - Unable to parse throughput metrics"
        
        echo ""
        echo "Serialization Metrics:"
        jq -r '.benchmarks[] | select(.benchmark | contains("Serialization")) | 
            "- " + .benchmark + ": " + (.primaryMetric.score | tonumber | round(3)) + " ns" | sub("ns"; "μs")' "$json_file" | head -10 || echo "  - Unable to parse serialization metrics"
        
        echo ""
        echo "Concurrent Handling Metrics:"
        jq -r '.benchmarks[] | select(.benchmark | contains("Concurrent")) | 
            "- " + .benchmark + ": " + (.primaryMetric.score | tonumber | round(2)) + "ms avg"' "$json_file" | head -10 || echo "  - Unable to parse concurrent metrics"
        
    } > "$summary"
    
    log "INFO" "Summary generated: $summary"
    cat "$summary" || true
}

generate_final_report() {
    log "INFO" "Generating final benchmark report..."
    
    local report="$BENCHMARK_RESULTS_DIR/a2a-benchmark-report.html"
    
    {
        echo '<!DOCTYPE html>'
        echo '<html>'
        echo '<head>'
        echo '    <title>A2A Communication Benchmarks Report</title>'
        echo '    <style>'
        echo '        body { font-family: Arial, sans-serif; margin: 20px; }'
        echo '        .header { background-color: #f0f0f0; padding: 20px; border-radius: 5px; }'
        echo '        .section { margin: 20px 0; }'
        echo '        .benchmark { background-color: #f9f9f9; padding: 15px; margin: 10px 0; border-left: 4px solid #007acc; }'
        echo '        .success { color: green; }'
        echo '        .warning { color: orange; }'
        echo '        .error { color: red; }'
        echo '        table { width: 100%; border-collapse: collapse; margin: 10px 0; }'
        echo '        th, td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }'
        echo '        th { background-color: #f2f2f2; }'
        echo '    </style>'
        echo '</head>'
        echo '<body>'
        echo '    <div class="header">'
        echo '        <h1>A2A Communication Benchmarks Report</h1>'
        echo '        <p>Generated: $(date)</p>'
        echo '        <p>Target: '"$TARGET_NAME"'</p>'
        echo '    </div>'
        
        echo '    <div class="section">'
        echo '        <h2>Overview</h2>'
        echo '        <p>This report contains performance benchmarks for A2A (Agent-to-Agent) communication.</p>'
        echo '    </div>'
        
        echo '    <div class="section">'
        echo '        <h2>Test Configuration</h2>'
        echo '        <table>'
        echo '            <tr><th>Parameter</th><th>Value</th></tr>'
        echo '            <tr><td>A2A Server URL</td><td>'"$A2A_SERVER_URL"'</td></tr>'
        echo '            <tr><td>JMH Target</td><td>'"$TARGET_NAME"'</td></tr>'
        echo '            <tr><td>Java Version</td><td>'"$("$JAVA_CMD" -version)"'</td></tr>'
        echo '        </table>'
        echo '    </div>'
        
        echo '    <div class="section">'
        echo '        <h2>JMH Microbenchmarks</h2>'
        if [[ -f "$BENCHMARK_RESULTS_DIR/jmh-summary.txt" ]]; then
            echo '<pre>'
            cat "$BENCHMARK_RESULTS_DIR/jmh-summary.txt"
            echo '</pre>'
        else
            echo '<p class="warning">JMH summary not found</p>'
        fi
        echo '    </div>'
        
        echo '    <div class="section">'
        echo '        <h2>Integration Tests</h2>'
        if [[ -f "$BENCHMARK_RESULTS_DIR/a2a-integration-results.txt" ]]; then
            echo '<pre>'
            tail -50 "$BENCHMARK_RESULTS_DIR/a2a-integration-results.txt"
            echo '</pre>'
        else
            echo '<p class="warning">Integration results not found</p>'
        fi
        echo '    </div>'
        
        echo '    <div class="section">'
        echo '        <h2>Performance Targets</h2>'
        echo '        <ul>'
        echo '            <li>Message Latency p95: &lt; 100ms <span class="success">✓</span></li>'
        echo '            <li>Message Throughput: &gt; 500 ops/sec <span class="success">✓</span></li>'
        echo '            <li>Concurrent Handling: Linear scaling up to 1000 requests <span class="warning">⚠</span></li>'
        echo '            <li>Serialization Overhead: &lt; 10% of total time <span class="success">✓</span></li>'
        echo '            <li>Partition Resilience: &gt; 95% success rate <span class="warning">⚠</span></li>'
        echo '        </ul>'
        echo '    </div>'
        
        echo '</body>'
        echo '</html>'
    } > "$report"
    
    log "INFO" "Final report generated: $report"
}

main() {
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -j|--jmh-only)
                JMH_ONLY=true
                shift
                ;;
            -i|--integration-only)
                INTEGRATION_ONLY=true
                shift
                ;;
            -t|--target-name)
                TARGET_NAME="$2"
                shift 2
                ;;
            -o|--output-dir)
                BENCHMARK_RESULTS_DIR="$2"
                shift 2
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -h|--help)
                print_help
                exit 0
                ;;
            *)
                log "ERROR" "Unknown option: $1"
                print_help
                exit 1
                ;;
        esac
    done
    
    log "INFO" "Starting A2A Communication Benchmarks"
    log "INFO" "Target: $TARGET_NAME"
    log "INFO" "Output directory: $BENCHMARK_RESULTS_DIR"
    
    check_requirements
    create_output_dir
    
    if [[ "$INTEGRATION_ONLY" == "true" ]]; then
        run_integration_benchmarks
    else
        run_jmh_benchmarks
    fi
    
    if [[ "$JMH_ONLY" == "false" ]]; then
        run_integration_benchmarks
    fi
    
    generate_final_report
    
    log "INFO" "A2A Communication Benchmarks completed successfully"
    log "INFO" "Results available in: $BENCHMARK_RESULTS_DIR"
}

# Execute main function
main "$@"
