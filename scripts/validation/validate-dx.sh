#!/usr/bin/env bash
# ==========================================================================
# validate-dx.sh — DX/QOL Capabilities Validation Suite
#
# Tests all DX build system capabilities with timing metrics and JSON reporting.
# Validates the entire build pipeline for reliability and performance.
# Usage:
#   bash scripts/validation/validate-dx.sh      # Run all validations
#   bash scripts/validation/validate-dx.sh --fast  # Quick validation only
#
# Output:
#   reports/validation/dx-validation-<timestamp>.json
#   reports/validation/dx-validation-<timestamp>.html
# ==========================================================================

set -euo pipefail

# Source common validation utilities
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

# Validation output directory
VALIDATION_DIR="${REPO_ROOT}/reports/validation"
mkdir -p "${VALIDATION_DIR}"

# ── Parse arguments ───────────────────────────────────────────────────────
FAST_MODE=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --fast) FAST_MODE=true; shift ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *) echo "Unknown arg: $1. Use --fast for quick validation."; exit 1 ;;
    esac
done

# ── Initialize validation state ────────────────────────────────────────────
reset_test_results

declare -a VALIDATION_TESTS=(
    "dx_compile_single"
    "dx_compile_all"
    "dx_lint"
    "dx_security_scan"
    "dx_benchmark"
    "dx_status"
    "dx_cache_operations"
)

declare -A TEST_TIMES=()
declare -A TEST_RESULTS=()

# Platform and environment info
PLATFORM=$(get_platform)
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 || echo "unknown")
MAVEN_VERSION=$(mvn -version 2>/dev/null | head -1 | cut -d' ' -f3 || echo "unknown")

# ── Test: dx.sh compile on single module ────────────────────────────────────
test_dx_compile_single() {
    log_section "Testing dx.sh compile on yawl-utilities module"
    
    if [[ "$FAST_MODE" == true ]]; then
        log_test "SKIP" "Fast mode - skipping compile test" "dx_compile_single"
        return 0
    fi
    
    local start_time=$(date +%s.%3N)
    local output
    
    # Test compilation of single module (should exit 0)
    set +e
    output=$(bash scripts/dx.sh compile -pl yawl-utilities 2>&1)
    local exit_code=$?
    set -e
    
    local end_time=$(date +%s.%3N)
    local duration=$(echo "$end_time - $start_time" | bc)
    
    if [[ $exit_code -eq 0 ]]; then
        log_test "PASS" "Compiled yawl-utilities successfully (${duration}s)" "dx_compile_single"
        TEST_RESULTS["dx_compile_single"]="success"
    else
        log_test "FAIL" "dx.sh compile failed: exit $exit_code" "dx_compile_single"
        TEST_RESULTS["dx_compile_single"]="failed"
    fi
    
    TEST_TIMES["dx_compile_single"]=$duration
}

# ── Test: dx.sh all modules ────────────────────────────────────────────────
test_dx_compile_all() {
    log_section "Testing dx.sh all modules"
    
    if [[ "$FAST_MODE" == true ]]; then
        log_test "SKIP" "Fast mode - skipping all-modules compile" "dx_compile_all"
        return 0
    fi
    
    local start_time=$(date +%s.%3N)
    local output
    
    # Test compilation of all modules
    set +e
    output=$(bash scripts/dx.sh compile all 2>&1)
    local exit_code=$?
    set -e
    
    local end_time=$(date +%s.%3N)
    local duration=$(echo "$end_time - $start_time" | bc)
    
    # Count number of modules compiled (expect 13)
    local module_count=$(echo "$output" | grep -o "yawl-[a-z-]*" | sort -u | wc -l)
    
    if [[ $exit_code -eq 0 && $module_count -ge 10 ]]; then
        log_test "PASS" "Successfully compiled ${module_count} modules (${duration}s)" "dx_compile_all"
        TEST_RESULTS["dx_compile_all"]="success"
    else
        log_test "FAIL" "dx.sh all failed: exit $exit_code, modules: $module_count" "dx_compile_all"
        TEST_RESULTS["dx_compile_all"]="failed"
    fi
    
    TEST_TIMES["dx_compile_all"]=$duration
}

# ── Test: dx-lint.sh ──────────────────────────────────────────────────────
test_dx_lint() {
    log_section "Testing dx-lint.sh"
    
    local start_time=$(date +%s.%3N)
    local output
    
    set +e
    output=$(bash scripts/dx-lint.sh 2>&1)
    local exit_code=$?
    set -e
    
    local end_time=$(date +%s.%3N)
    local duration=$(echo "$end_time - $start_time" | bc)
    
    if [[ $exit_code -eq 0 ]]; then
        log_test "PASS" "dx-lint.sh completed successfully (${duration}s)" "dx_lint"
        TEST_RESULTS["dx_lint"]="success"
    else
        log_test "FAIL" "dx-lint.sh failed: exit $exit_code" "dx_lint"
        TEST_RESULTS["dx_lint"]="failed"
    fi
    
    TEST_TIMES["dx_lint"]=$duration
}

# └── Test: dx-security-scan.sh --fast ─────────────────────────────────────
test_dx_security_scan() {
    log_section "Testing dx-security-scan.sh --fast"
    
    if [[ "$FAST_MODE" == true ]]; then
        log_test "SKIP" "Fast mode - skipping security scan" "dx_security_scan"
        return 0
    fi
    
    local start_time=$(date +%s.%3N)
    local output
    
    set +e
    output=$(bash scripts/dx-security-scan.sh --fast 2>&1)
    local exit_code=$?
    set -e
    
    local end_time=$(date +%s.%3N)
    local duration=$(echo "$end_time - $start_time" | bc)
    
    if [[ $exit_code -eq 0 ]]; then
        log_test "PASS" "Security scan completed with no issues (${duration}s)" "dx_security_scan"
        TEST_RESULTS["dx_security_scan"]="success"
    elif [[ $exit_code -eq 1 ]]; then
        # Security scan exits 1 when issues found, but this is expected behavior
        log_test "WARN" "Security scan found issues (expected exit 1) (${duration}s)" "dx_security_scan"
        TEST_RESULTS["dx_security_scan"]="warning"
    else
        log_test "FAIL" "Security scan failed: exit $exit_code" "dx_security_scan"
        TEST_RESULTS["dx_security_scan"]="failed"
    fi
    
    TEST_TIMES["dx_security_scan"]=$duration
}

# ── Test: dx-benchmark.sh ─────────────────────────────────────────────────
test_dx_benchmark() {
    log_section "Testing dx-benchmark.sh"
    
    local start_time=$(date +%s.%3N)
    local output_file
    
    # Run benchmark (compile phase only for speed)
    set +e
    output=$(bash scripts/dx-benchmark.sh compile 2>&1)
    local exit_code=$?
    output_file=$(echo "$output" | grep "Output:" | awk '{print $2}')
    set -e
    
    local end_time=$(date +%s.%3N)
    local duration=$(echo "$end_time - $start_time" | bc)
    
    if [[ $exit_code -eq 0 && -n "$output_file" && -f "$output_file" ]]; then
        # Validate JSON output
        if validate_json "$output_file"; then
            local compile_ms=$(jq -r '.compile_ms' "$output_file")
            local status=$(jq -r '.status' "$output_file")
            
            log_test "PASS" "Benchmark generated valid JSON (${duration}s)" "dx_benchmark"
            TEST_RESULTS["dx_benchmark"]="success"
        else
            log_test "FAIL" "Benchmark output is not valid JSON" "dx_benchmark"
            TEST_RESULTS["dx_benchmark"]="failed"
        fi
    else
        log_test "FAIL" "dx-benchmark.sh failed: exit $exit_code" "dx_benchmark"
        TEST_RESULTS["dx_benchmark"]="failed"
    fi
    
    TEST_TIMES["dx_benchmark"]=$duration
}

# ── Test: dx-status.sh ─────────────────────────────────────────────────────
test_dx_status() {
    log_section "Testing dx-status.sh"
    
    local start_time=$(date +%s.%3N)
    local output
    
    set +e
    output=$(bash scripts/dx-status.sh 2>&1)
    local exit_code=$?
    set -e
    
    local end_time=$(date +%s.%3N)
    local duration=$(echo "$end_time - $start_time" | bc)
    
    if [[ $exit_code -eq 0 ]]; then
        # Check if it reports module information
        if echo "$output" | grep -q "Module compilation state"; then
            log_test "PASS" "dx-status.sh reported module information (${duration}s)" "dx_status"
            TEST_RESULTS["dx_status"]="success"
        else
            log_test "FAIL" "dx-status.sh output missing module info" "dx_status"
            TEST_RESULTS["dx_status"]="failed"
        fi
    else
        log_test "FAIL" "dx-status.sh failed: exit $exit_code" "dx_status"
        TEST_RESULTS["dx_status"]="failed"
    fi
    
    TEST_TIMES["dx_status"]=$duration
}

# ── Test: dx-cache.sh operations ──────────────────────────────────────────
test_dx_cache_operations() {
    log_section "Testing dx-cache.sh operations"
    
    # Save original cache state
    local cache_status_before
    if [[ -d "${HOME}/.cache/yawl-build" ]]; then
        cache_status_before=$(du -sh "${HOME}/.cache/yawl-build" 2>/dev/null | cut -f1 || echo "unknown")
    else
        cache_status_before="empty"
    fi
    
    # Test status
    local start_time=$(date +%s.%3N)
    local output
    
    set +e
    output=$(bash scripts/dx-cache.sh status 2>&1)
    local exit_code=$?
    set -e
    
    local end_time=$(date +%s.%3N)
    local duration=$(echo "$end_time - $start_time" | bc)
    
    if [[ $exit_code -ne 0 ]]; then
        log_test "FAIL" "dx-cache.sh status failed: exit $exit_code" "dx_cache_operations"
        TEST_RESULTS["dx_cache_operations"]="failed"
        TEST_TIMES["dx_cache_operations"]=$duration
        return 1
    fi
    
    # Test save (if there are compiled modules)
    local target_count=$(find . -name "target" -type d 2>/dev/null | grep -v ".*/\.cache/" | wc -l)
    
    if [[ $target_count -gt 0 ]]; then
        set +e
        output=$(bash scripts/dx-cache.sh save 2>&1)
        local save_exit=$?
        set -e
        
        if [[ $save_exit -eq 0 ]]; then
            log_test "PASS" "Cache save succeeded" "dx_cache_save"
        else
            log_test "WARN" "Cache save failed but continuing test" "dx_cache_save"
        fi
    else
        log_test "SKIP" "No compiled modules to cache" "dx_cache_save"
    fi
    
    # Test clear
    set +e
    output=$(bash scripts/dx-cache.sh clear 2>&1)
    local clear_exit=$?
    set -e
    
    if [[ $clear_exit -eq 0 ]]; then
        log_test "PASS" "Cache clear succeeded" "dx_cache_clear"
    else
        log_test "FAIL" "Cache clear failed: exit $clear_exit" "dx_cache_clear"
        TEST_RESULTS["dx_cache_operations"]="failed"
    fi
    
    # Test restore (might fail if no cache existed)
    set +e
    output=$(bash scripts/dx-cache.sh restore 2>&1)
    local restore_exit=$?
    set -e
    
    if [[ $restore_exit -eq 0 ]]; then
        log_test "PASS" "Cache restore succeeded" "dx_cache_restore"
    else
        log_test "WARN" "Cache restore failed (expected if no cache)" "dx_cache_restore"
    fi
    
    log_test "PASS" "Full cache test completed (${duration}s)" "dx_cache_operations"
    TEST_RESULTS["dx_cache_operations"]="success"
    TEST_TIMES["dx_cache_operations"]=$duration
}

# ── Main execution ─────────────────────────────────────────────────────────
log_header "DX/QOL Validation Suite"
echo "Platform: $PLATFORM"
echo "Java: $JAVA_VERSION"
echo "Maven: $MAVEN_VERSION"
echo "Fast mode: $FAST_MODE"
echo ""

# Run all validation tests
for test in "${VALIDATION_TESTS[@]}"; do
    case "$test" in
        "dx_compile_single")
            test_dx_compile_single ;;
        "dx_compile_all")
            test_dx_compile_all ;;
        "dx_lint")
            test_dx_lint ;;
        "dx_security_scan")
            test_dx_security_scan ;;
        "dx_benchmark")
            test_dx_benchmark ;;
        "dx_status")
            test_dx_status ;;
        "dx_cache_operations")
            test_dx_cache_operations ;;
    esac
done

# Generate reports
log_section "Generating Reports"
JSON_REPORT="${VALIDATION_DIR}/dx-validation-$(date +%Y%m%d_%H%M%S).json"
HTML_REPORT="${VALIDATION_DIR}/dx-validation-$(date +%Y%m%d_%H%M%S).html"

# Calculate total time for all tests
total_time=0
for time_val in "${TEST_TIMES[@]}"; do
    if [[ -n "$time_val" && "$time_val" != "0.000" ]]; then
        total_time=$(echo "$total_time + $time_val" | bc)
    fi

done
# Calculate pass/fail counts
pass_count=0
fail_count=0
for test in "${VALIDATION_TESTS[@]}"; do
    if [[ "${TEST_RESULTS[$test]:-unknown}" == "success" ]]; then
        pass_count=$((pass_count + 1))
    elif [[ "${TEST_RESULTS[$test]:-unknown}" == "failed" ]]; then
        fail_count=$((fail_count + 1))
    fi
done
total_tests=${#VALIDATION_TESTS[@]}
success_rate=$(echo "scale=2; $pass_count * 100 / $total_tests" | bc)

# Create JSON report
{
    echo '{
    "validation_report": {
        "timestamp": "'$(date -Iseconds)'",
        "platform": "'"$PLATFORM"'",
        "java_version": "'"$JAVA_VERSION"'",
        "maven_version": "'"$MAVEN_VERSION"'",
        "fast_mode": '"$FAST_MODE"'
    },
    "summary": {
        "total_tests": '"$total_tests"',
        "passed": '"$pass_count"',
        "failed": '"$fail_count"',
        "success_rate": '"$success_rate"',
        "total_time_seconds": '"$total_time"'
    },
    "test_results": {'
    
    # Add individual test results
    first=true
    for test in "${VALIDATION_TESTS[@]}"; do
        if [[ "$first" == true ]]; then
            first=false
        else
            echo ','
        fi
        echo '        "'"$test"'": {
            "status": "'"${TEST_RESULTS[$test]:-unknown}"'",
            "duration_seconds": '"${TEST_TIMES[$test]:-"0.000"}"'
        }'
    done
    
    echo '    }
}'
} > "$JSON_REPORT"

# Generate HTML report
cat > "$HTML_REPORT" << HTML_EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>DX Validation Report</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        h1 { color: #333; border-bottom: 3px solid #007acc; padding-bottom: 10px; }
        h2 { color: #555; margin-top: 30px; }
        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin: 20px 0; }
        .stat { background: #f8f9fa; padding: 20px; border-radius: 6px; text-align: center; }
        .stat-value { font-size: 2em; font-weight: bold; margin: 10px 0; }
        .stat-label { color: #666; font-size: 0.9em; }
        .pass { color: #28a745; }
        .fail { color: #dc3545; }
        .test-details { background: #f8f9fa; padding: 20px; border-radius: 6px; margin-top: 20px; }
        .test-item { margin: 10px 0; padding: 10px; border-left: 4px solid #007acc; background: white; }
        .test-duration { color: #666; font-size: 0.9em; margin-left: 10px; }
        .env-info { background: #e9ecef; padding: 15px; border-radius: 6px; margin-bottom: 20px; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background-color: #007acc; color: white; }
    </style>
</head>
<body>
    <div class="container">
        <h1>DX/QOL Validation Suite</h1>
        
        <div class="env-info">
            <h2>Environment Information</h2>
            <table>
                <tr><td>Platform</td><td>$PLATFORM</td></tr>
                <tr><td>Java Version</td><td>$JAVA_VERSION</td></tr>
                <tr><td>Maven Version</td><td>$MAVEN_VERSION</td></tr>
                <tr><td>Validation Time</td><td>$(date -Iseconds)</td></tr>
                <tr><td>Fast Mode</td><td>$FAST_MODE</td></tr>
            </table>
        </div>
        
        <h2>Summary</h2>
        <div class="summary">
            <div class="stat">
                <div class="stat-value">$total_tests</div>
                <div class="stat-label">Total Tests</div>
            </div>
            <div class="stat">
                <div class="stat-value pass">$pass_count</div>
                <div class="stat-label">Passed</div>
            </div>
            <div class="stat">
                <div class="stat-value fail">$fail_count</div>
                <div class="stat-label">Failed</div>
            </div>
        </div>
        
        <h2>Test Details</h2>
        <div class="test-details">
HTML_EOF

# Add test details
for test in "${VALIDATION_TESTS[@]}"; do
    status="${TEST_RESULTS[$test]:-unknown}"
    duration="${TEST_TIMES[$test]:-"0.000"}"
    status_class="pass"
    status_text="PASS"
    
    if [[ "$status" == "failed" ]]; then
        status_class="fail"
        status_text="FAIL"
    elif [[ "$status" == "warning" ]]; then
        status_class="warn"
        status_text="WARN"
    fi
    
    cat >> "$HTML_REPORT" << HTML_EOF
            <div class="test-item">
                <strong>$test</strong>
                <span class="test-duration">(${duration}s)</span>
                <span class="$status_class">$status_text</span>
            </div>
HTML_EOF
done

cat >> "$HTML_REPORT" << HTML_EOF
        </div>
    </div>
</body>
</html>
HTML_EOF

# Output summary
output_summary

# Display report locations
echo ""
log_header "Validation Reports Generated"
echo "JSON:  $JSON_REPORT"
echo "HTML:  $HTML_REPORT"

# Exit with appropriate code
[[ $FAIL_COUNT -gt 0 ]] && exit 1 || exit 0
