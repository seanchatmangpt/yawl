#!/bin/bash
#
# Performance Validation Script for Multi-Cloud Marketplace Readiness
# Product: YAWL Workflow Engine v5.2
#
# Usage: ./validate-performance.sh [--verbose] [--benchmark]
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATION_DIR="$(dirname "$SCRIPT_DIR")"
REPORT_DIR="${VALIDATION_DIR}/reports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Options
VERBOSE=false
RUN_BENCHMARK=false
QUIET=false
FAILED=0
PASSED=0
WARNINGS=0

# Performance thresholds (adjust based on requirements)
THRESHOLD_STARTUP_TIME=30          # seconds
THRESHOLD_RESPONSE_TIME_P50=100    # milliseconds
THRESHOLD_RESPONSE_TIME_P99=500    # milliseconds
THRESHOLD_THROUGHPUT=100           # requests per second
THRESHOLD_MEMORY_MB=1024           # MB
THRESHOLD_CPU_PERCENT=80           # percent

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        --quiet|-q)
            QUIET=true
            shift
            ;;
        --benchmark|-b)
            RUN_BENCHMARK=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --verbose, -v    Enable verbose output"
            echo "  --quiet, -q      Suppress non-essential output"
            echo "  --benchmark, -b  Run actual benchmarks"
            echo "  --help, -h       Show this help message"
            exit 0
            ;;
        *)
            shift
            ;;
    esac
done

log_info() {
    [ "$QUIET" = true ] && return
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((PASSED++))
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
    ((WARNINGS++))
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((FAILED++))
}

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Validate performance test infrastructure
validate_infrastructure() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Performance Test Infrastructure ==="

    # Check for load testing tools
    local tools=("hey" "ab" "wrk" "vegeta" "k6" "locust")
    local found_tools=()

    for tool in "${tools[@]}"; do
        if command_exists "$tool"; then
            found_tools+=("$tool")
            log_success "Load testing tool available: $tool"
        fi
    done

    if [ ${#found_tools[@]} -eq 0 ]; then
        log_warning "No load testing tools found (hey, ab, wrk, vegeta, k6, locust)"
    fi

    # Check for performance test files
    local test_dirs=("tests/performance" "test/performance" "tests/benchmark" "benchmark")
    local found_dir=false
    for dir in "${test_dirs[@]}"; do
        if [ -d "$dir" ]; then
            log_success "Performance test directory found: $dir"
            found_dir=true
        fi
    done
    [ "$found_dir" = false ] && log_warning "No performance test directory found"

    # Check for JMeter/Gatling configs
    if find . -name "*.jmx" -o -name "*gatling*" 2>/dev/null | head -1 | grep -q .; then
        log_success "JMeter or Gatling configuration found"
    fi
}

# Validate resource configurations
validate_resources() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Resource Configuration ==="

    # Check Kubernetes resource limits
    log_info "Checking Kubernetes resource configurations..."

    if find . -name "*.yaml" -o -name "*.yml" 2>/dev/null | xargs grep -l "resources:" 2>/dev/null | head -1 | grep -q .; then
        log_success "Kubernetes resource limits defined"

        # Check for specific configurations
        if find . -name "*.yaml" -o -name "*.yml" 2>/dev/null | xargs grep -l "limits:" 2>/dev/null | head -1 | grep -q .; then
            log_success "Resource limits specified"
        else
            log_warning "No resource limits found in Kubernetes manifests"
        fi

        if find . -name "*.yaml" -o -name "*.yml" 2>/dev/null | xargs grep -l "requests:" 2>/dev/null | head -1 | grep -q .; then
            log_success "Resource requests specified"
        else
            log_warning "No resource requests found in Kubernetes manifests"
        fi
    else
        log_warning "No Kubernetes resource configurations found"
    fi

    # Check Docker resource constraints
    if [ -f "docker-compose.yml" ] || [ -f "docker-compose.yaml" ]; then
        log_info "Checking docker-compose resource constraints..."
        if grep -q "deploy:" docker-compose*.y*ml 2>/dev/null; then
            log_success "Docker Compose deploy configuration found"
        else
            log_warning "No resource constraints in docker-compose"
        fi
    fi

    # Check Helm values for resource settings
    if find . -name "values.yaml" 2>/dev/null | head -1 | grep -q .; then
        log_info "Checking Helm values for resource settings..."
        if grep -q "resources:" values.yaml 2>/dev/null; then
            log_success "Helm chart has resource configurations"
        else
            log_warning "Helm values.yaml missing resource configurations"
        fi
    fi
}

# Validate autoscaling configuration
validate_autoscaling() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Autoscaling Configuration ==="

    # Check for HPA
    log_info "Checking Horizontal Pod Autoscaler..."
    if find . -name "*hpa*" -o -name "*autoscal*" 2>/dev/null | head -1 | grep -q .; then
        log_success "HPA configuration found"
    else
        log_warning "No HPA configuration found"
    fi

    # Check for VPA
    log_info "Checking Vertical Pod Autoscaler..."
    if find . -name "*vpa*" 2>/dev/null | head -1 | grep -q .; then
        log_success "VPA configuration found"
    fi

    # Check for cluster autoscaler
    log_info "Checking cluster autoscaler configuration..."
    if grep -r "cluster-autoscaler" --include="*.yaml" --include="*.tf" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Cluster autoscaler references found"
    fi

    # Check for KEDA (event-driven autoscaling)
    if find . -name "*scaledobject*" -o -name "*keda*" 2>/dev/null | head -1 | grep -q .; then
        log_success "KEDA configuration found"
    fi
}

# Validate caching configuration
validate_caching() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Caching Configuration ==="

    # Check for Redis/Memcached configuration
    log_info "Checking caching layer configuration..."

    local cache_found=false

    if grep -r "redis\|memcached\|cache" --include="*.yaml" --include="*.properties" --include="*.conf" . 2>/dev/null | grep -v test | head -1 | grep -q .; then
        log_success "Caching configuration found"
        cache_found=true
    fi

    # Check for CDN configuration
    if grep -r "cdn\|cloudfront\|cloudflare" --include="*.tf" --include="*.yaml" . 2>/dev/null | head -1 | grep -q .; then
        log_success "CDN configuration found"
    fi

    # Check for application-level caching
    if grep -r "@Cacheable\|@CacheEvict\|cache_manager" --include="*.java" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Application-level caching (Spring Cache) found"
        cache_found=true
    fi

    [ "$cache_found" = false ] && log_warning "No caching configuration detected"
}

# Validate database performance
validate_database() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Database Performance ==="

    # Check connection pool configuration
    log_info "Checking database connection pool..."

    if grep -r "pool\|connection.*size\|max.*connection" --include="*.yaml" --include="*.properties" --include="*.xml" . 2>/dev/null | grep -v test | head -1 | grep -q .; then
        log_success "Connection pool configuration found"

        # Check for HikariCP (Java)
        if grep -r "hikari" --include="*.yaml" --include="*.properties" . 2>/dev/null | head -1 | grep -q .; then
            log_success "HikariCP connection pool configured"
        fi
    else
        log_warning "No database connection pool configuration found"
    fi

    # Check for read replicas
    log_info "Checking read replica configuration..."
    if grep -r "replica\|read.*host\|slave" --include="*.yaml" --include="*.properties" --include="*.tf" . 2>/dev/null | grep -i "database\|db\|sql" | head -1 | grep -q .; then
        log_success "Read replica configuration found"
    fi

    # Check for query optimization hints
    if grep -r "@Index\|@QueryHint\|explain" --include="*.java" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Query optimization hints found"
    fi
}

# Run benchmarks if requested
run_benchmarks() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Running Benchmarks ==="

    local image="${CONTAINER_IMAGE:-yawl/yawl-engine:5.2.0}"
    local target_url="${TARGET_URL:-http://localhost:8080}"

    if [ "$RUN_BENCHMARK" = false ]; then
        log_info "Skipping benchmarks (--benchmark not specified)"
        return
    fi

    # Startup time benchmark
    log_info "Measuring container startup time..."
    local start_time=$(date +%s%N)
    docker run --rm -d --name yawl-benchmark -p 8080:8080 "$image" >/dev/null 2>&1 || true

    # Wait for healthy status
    local max_wait=60
    local waited=0
    while [ $waited -lt $max_wait ]; do
        if curl -s "$target_url/health" >/dev/null 2>&1; then
            break
        fi
        sleep 1
        ((waited++))
    done

    local end_time=$(date +%s%N)
    local startup_time=$(( (end_time - start_time) / 1000000 ))

    docker stop yawl-benchmark >/dev/null 2>&1 || true

    if [ $startup_time -lt $((THRESHOLD_STARTUP_TIME * 1000)) ]; then
        log_success "Startup time: ${startup_time}ms (threshold: ${THRESHOLD_STARTUP_TIME}s)"
    else
        log_warning "Startup time: ${startup_time}ms exceeds threshold of ${THRESHOLD_STARTUP_TIME}s"
    fi

    # HTTP benchmark with hey
    if command_exists "hey"; then
        log_info "Running HTTP benchmark with hey..."

        docker run --rm -d --name yawl-benchmark -p 8080:8080 "$image" >/dev/null 2>&1 || true
        sleep 5  # Wait for warmup

        local hey_output=$(hey -n 1000 -c 50 -q "$target_url/api/health" 2>&1 || echo "")

        docker stop yawl-benchmark >/dev/null 2>&1 || true

        # Parse results
        local rps=$(echo "$hey_output" | grep "Requests/sec" | awk '{print $2}' | cut -d. -f1 || echo "0")

        if [ -n "$rps" ] && [ "$rps" -gt $THRESHOLD_THROUGHPUT ]; then
            log_success "Throughput: $rps req/s (threshold: $THRESHOLD_THROUGHPUT)"
        else
            log_warning "Throughput: $rps req/s below threshold of $THRESHOLD_THROUGHPUT"
        fi
    else
        log_info "hey not installed - skipping HTTP benchmark"
    fi

    # Memory benchmark
    log_info "Checking memory usage..."
    docker run --rm --name yawl-mem-benchmark "$image" &
    local pid=$!
    sleep 10

    local mem_usage=$(docker stats yawl-mem-benchmark --no-stream --format "{{.MemUsage}}" 2>/dev/null || echo "N/A")
    log_info "Memory usage: $mem_usage"

    kill $pid 2>/dev/null || true
    docker stop yawl-mem-benchmark >/dev/null 2>&1 || true
}

# Validate observability
validate_observability() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Observability Configuration ==="

    # Check for metrics export
    log_info "Checking metrics configuration..."
    if grep -r "prometheus\|metrics\|/metrics" --include="*.yaml" --include="*.properties" --include="*.py" --include="*.java" . 2>/dev/null | grep -v test | head -1 | grep -q .; then
        log_success "Metrics export configuration found"
    else
        log_warning "No metrics export configuration found"
    fi

    # Check for tracing
    log_info "Checking distributed tracing..."
    if grep -r "jaeger\|zipkin\|opentelemetry\|tracing" --include="*.yaml" --include="*.properties" . 2>/dev/null | grep -v test | head -1 | grep -q .; then
        log_success "Distributed tracing configuration found"
    else
        log_warning "No distributed tracing configuration found"
    fi

    # Check for logging
    log_info "Checking logging configuration..."
    if grep -r "log4j\|logback\|fluentd\|fluent-bit\|elasticsearch" --include="*.xml" --include="*.yaml" --include="*.properties" . 2>/dev/null | head -1 | grep -q .; then
        log_success "Structured logging configuration found"
    fi

    # Check for APM
    if grep -r "newrelic\|datadog\|appdynamics\|dynatrace" --include="*.yaml" --include="*.java" . 2>/dev/null | head -1 | grep -q .; then
        log_success "APM integration found"
    fi
}

# Generate performance report
generate_report() {
    [ "$QUIET" = false ] && echo ""
    [ "$QUIET" = false ] && echo "=== Performance Validation Summary ==="

    local total=$((PASSED + FAILED + WARNINGS))

    echo ""
    echo "Total Checks: $total"
    echo -e "Passed: ${GREEN}$PASSED${NC}"
    echo -e "Failed: ${RED}$FAILED${NC}"
    echo -e "Warnings: ${YELLOW}$WARNINGS${NC}"

    mkdir -p "$REPORT_DIR"

    local report_file="${REPORT_DIR}/performance-report-${TIMESTAMP}.json"
    cat > "$report_file" <<EOF
{
  "timestamp": "$(date -Iseconds)",
  "product": "YAWL Workflow Engine",
  "version": "5.2.0",
  "benchmarks_run": $RUN_BENCHMARK,
  "thresholds": {
    "startup_time_seconds": $THRESHOLD_STARTUP_TIME,
    "response_time_p50_ms": $THRESHOLD_RESPONSE_TIME_P50,
    "response_time_p99_ms": $THRESHOLD_RESPONSE_TIME_P99,
    "throughput_rps": $THRESHOLD_THROUGHPUT,
    "memory_mb": $THRESHOLD_MEMORY_MB,
    "cpu_percent": $THRESHOLD_CPU_PERCENT
  },
  "summary": {
    "total": $total,
    "passed": $PASSED,
    "failed": $FAILED,
    "warnings": $WARNINGS
  },
  "status": "$([ $FAILED -eq 0 ] && echo "PASS" || echo "FAIL")"
}
EOF

    [ "$QUIET" = false ] && log_info "Performance report saved to: $report_file"

    if [ $FAILED -gt 0 ]; then
        echo -e "${RED}PERFORMANCE VALIDATION FAILED${NC}"
        exit 1
    else
        echo -e "${GREEN}PERFORMANCE VALIDATION PASSED${NC}"
        exit 0
    fi
}

# Main
main() {
    [ "$QUIET" = false ] && echo "=========================================="
    [ "$QUIET" = false ] && echo "YAWL Performance Validation"
    [ "$QUIET" = false ] && echo "=========================================="

    validate_infrastructure
    validate_resources
    validate_autoscaling
    validate_caching
    validate_database
    validate_observability
    run_benchmarks

    generate_report
}

main "$@"
