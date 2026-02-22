#!/usr/bin/env bash
# ==========================================================================
# observability-dashboard.sh — Real-Time Project Observability Dashboard
#
# Collects and displays project health metrics
# Output: Terminal display + .yawl/metrics/*.json
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

METRICS_DIR=".yawl/metrics"
FACTS_DIR="docs/v6/latest/facts"

# Colors
readonly C_RESET='\033[0m'
readonly C_BOLD='\033[1m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_YELLOW='\033[93m'
readonly C_BLUE='\033[94m'
readonly C_CYAN='\033[96m'
readonly C_GRAY='\033[90m'
readonly SYM_CHECK='✓'
readonly SYM_CROSS='✗'

mkdir -p "${METRICS_DIR}"

# ── Utility functions
print_header() {
    printf "\n${C_BOLD}${C_CYAN}YAWL v6.0 Project Observability Dashboard${C_RESET}\n"
    printf "${C_CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${C_RESET}\n"
}

print_section() {
    printf "\n${C_BOLD}${C_YELLOW}■ $1${C_RESET}\n"
}

print_metric() {
    local label="$1"
    local value="$2"
    printf "  %-35s : ${C_BOLD}%-20s${C_RESET}\n" "$label" "$value"
}

print_status() {
    local label="$1"
    local status="$2"
    local msg="${3:-}"

    if [[ "$status" == "GREEN" ]]; then
        printf "  ${C_GREEN}${SYM_CHECK}${C_RESET} %-30s GREEN  %s\n" "$label" "$msg"
    elif [[ "$status" == "YELLOW" ]]; then
        printf "  ${C_YELLOW}⚠${C_RESET} %-30s YELLOW %s\n" "$label" "$msg"
    else
        printf "  ${C_RED}${SYM_CROSS}${C_RESET} %-30s RED    %s\n" "$label" "$msg"
    fi
}

# ── Data collection
collect_build_metrics() {
    local build_log="/tmp/dx-build-log.txt"
    local build_json="${METRICS_DIR}/build-metrics.json"

    if [[ ! -f "$build_log" ]]; then
        echo '{"status":"no_recent_build"}' > "$build_json"
        return
    fi

    local test_count=$(grep -c "Running " "$build_log" 2>/dev/null || echo 0)
    local failures=$(grep -c "FAILURE\|ERROR" "$build_log" 2>/dev/null || echo 0)

    cat > "$build_json" << EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "test_count": $test_count,
  "failures": $failures,
  "success": $([ "$failures" -eq 0 ] && echo "true" || echo "false")
}
EOF
}

collect_coverage_metrics() {
    local coverage_json="$FACTS_DIR/coverage.json"
    local metrics_json="${METRICS_DIR}/coverage-metrics.json"

    if [[ ! -f "$coverage_json" ]]; then
        echo '{}' > "$metrics_json"
        return
    fi

    jq '{
        timestamp: .generated_at,
        line_pct: .aggregate.line_pct,
        branch_pct: .aggregate.branch_pct,
        target_line_pct: .aggregate.target_line_pct,
        target_branch_pct: .aggregate.target_branch_pct,
        meets_line_target: .aggregate.meets_line_target,
        meets_branch_target: .aggregate.meets_branch_target
    }' "$coverage_json" > "$metrics_json" 2>/dev/null || echo '{}' > "$metrics_json"
}

collect_test_metrics() {
    local tests_json="$FACTS_DIR/tests.json"
    local metrics_json="${METRICS_DIR}/test-metrics.json"

    if [[ ! -f "$tests_json" ]]; then
        echo '{}' > "$metrics_json"
        return
    fi

    jq '{
        timestamp: .generated_at,
        total_test_files: .summary.total_test_files,
        modules_with_tests: .summary.modules_with_tests,
        junit5_count: .summary.junit5_count,
        junit4_count: .summary.junit4_count
    }' "$tests_json" > "$metrics_json" 2>/dev/null || echo '{}' > "$metrics_json"
}

collect_code_metrics() {
    local modules_json="$FACTS_DIR/modules.json"
    local metrics_json="${METRICS_DIR}/code-metrics.json"

    if [[ ! -f "$modules_json" ]]; then
        echo '{}' > "$metrics_json"
        return
    fi

    local total_src=0
    local total_test=0
    local module_count=$(jq '.modules | length' "$modules_json" 2>/dev/null || echo 0)

    while IFS= read -r line; do
        if [[ $line =~ \"src_files\":[[:space:]]*([0-9]+) ]]; then
            total_src=$((total_src + ${BASH_REMATCH[1]}))
        fi
        if [[ $line =~ \"test_files\":[[:space:]]*([0-9]+) ]]; then
            total_test=$((total_test + ${BASH_REMATCH[1]}))
        fi
    done < "$modules_json"

    cat > "$metrics_json" << EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "module_count": $module_count,
  "total_src_files": $total_src,
  "total_test_files": $total_test,
  "src_to_test_ratio": $(awk "BEGIN {printf \"%.2f\", ($total_src > 0 ? $total_test / $total_src : 0)}")
}
EOF
}

collect_quality_metrics() {
    local metrics_json="${METRICS_DIR}/quality-metrics.json"

    local spotbugs=$(jq -r '.total // 0' "$FACTS_DIR/spotbugs-findings.json" 2>/dev/null || echo 0)
    local pmd=$(jq -r '.total // 0' "$FACTS_DIR/pmd-violations.json" 2>/dev/null || echo 0)
    local checkstyle=$(jq -r '.total // 0' "$FACTS_DIR/checkstyle-warnings.json" 2>/dev/null || echo 0)

    cat > "$metrics_json" << EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "spotbugs_findings": $spotbugs,
  "pmd_violations": $pmd,
  "checkstyle_warnings": $checkstyle,
  "total_quality_issues": $((spotbugs + pmd + checkstyle))
}
EOF
}

# ── Display functions
display_build_health() {
    local json="${METRICS_DIR}/build-metrics.json"
    print_section "Build Health"

    [[ ! -f "$json" ]] && { echo "  No data"; return; }

    local count=$(jq -r '.test_count // 0' "$json" 2>/dev/null || echo 0)
    local fail=$(jq -r '.failures // 0' "$json" 2>/dev/null || echo 0)
    local ok=$(jq -r '.success // false' "$json" 2>/dev/null || echo false)

    if [[ "$ok" == "true" ]]; then
        print_status "Last Build" "GREEN" "All tests passed"
    else
        print_status "Last Build" "RED" "Build or tests failed"
    fi

    print_metric "Tests Executed" "$count"
    print_metric "Test Failures" "$fail"
}

display_coverage_health() {
    local json="${METRICS_DIR}/coverage-metrics.json"
    print_section "Code Coverage"

    [[ ! -f "$json" ]] && { echo "  No data"; return; }

    local line=$(jq -r '.line_pct // 0' "$json" 2>/dev/null || echo 0)
    local branch=$(jq -r '.branch_pct // 0' "$json" 2>/dev/null || echo 0)
    local tline=$(jq -r '.target_line_pct // 65' "$json" 2>/dev/null || echo 65)
    local tbranch=$(jq -r '.target_branch_pct // 55' "$json" 2>/dev/null || echo 55)
    local mline=$(jq -r '.meets_line_target // false' "$json" 2>/dev/null || echo false)
    local mbranch=$(jq -r '.meets_branch_target // false' "$json" 2>/dev/null || echo false)

    if [[ "$mline" == "true" ]]; then
        print_status "Line Coverage" "GREEN" "${line}% (target: ${tline}%)"
    else
        print_status "Line Coverage" "RED" "${line}% (target: ${tline}%)"
    fi

    if [[ "$mbranch" == "true" ]]; then
        print_status "Branch Coverage" "GREEN" "${branch}% (target: ${tbranch}%)"
    else
        print_status "Branch Coverage" "RED" "${branch}% (target: ${tbranch}%)"
    fi
}

display_test_health() {
    local json="${METRICS_DIR}/test-metrics.json"
    print_section "Test Inventory"

    [[ ! -f "$json" ]] && { echo "  No data"; return; }

    print_metric "Test Files" "$(jq -r '.total_test_files // 0' "$json" 2>/dev/null || echo 0)"
    print_metric "Modules with Tests" "$(jq -r '.modules_with_tests // 0' "$json" 2>/dev/null || echo 0)"
    print_metric "JUnit 5 Tests" "$(jq -r '.junit5_count // 0' "$json" 2>/dev/null || echo 0)"
    print_metric "JUnit 4 Tests" "$(jq -r '.junit4_count // 0' "$json" 2>/dev/null || echo 0)"
}

display_code_health() {
    local json="${METRICS_DIR}/code-metrics.json"
    print_section "Code Metrics"

    [[ ! -f "$json" ]] && { echo "  No data"; return; }

    print_metric "Modules" "$(jq -r '.module_count // 0' "$json" 2>/dev/null || echo 0)"
    print_metric "Source Files" "$(jq -r '.total_src_files // 0' "$json" 2>/dev/null || echo 0)"
    print_metric "Test Files" "$(jq -r '.total_test_files // 0' "$json" 2>/dev/null || echo 0)"
    print_metric "Test/Source Ratio" "$(jq -r '.src_to_test_ratio // 0' "$json" 2>/dev/null || echo 0)"
}

display_quality_health() {
    local json="${METRICS_DIR}/quality-metrics.json"
    print_section "Code Quality"

    [[ ! -f "$json" ]] && { echo "  No data"; return; }

    local total=$(jq -r '.total_quality_issues // 0' "$json" 2>/dev/null || echo 0)

    if [[ "$total" -eq 0 ]]; then
        print_status "Quality Issues" "GREEN" "No issues detected"
    elif [[ "$total" -lt 5 ]]; then
        print_status "Quality Issues" "YELLOW" "$total minor issues"
    else
        print_status "Quality Issues" "RED" "$total issues found"
    fi
}

# ── Export function
export_json() {
    local export_file="${METRICS_DIR}/dashboard-snapshot.json"

    jq -n \
        --slurpfile build "${METRICS_DIR}/build-metrics.json" \
        --slurpfile coverage "${METRICS_DIR}/coverage-metrics.json" \
        --slurpfile tests "${METRICS_DIR}/test-metrics.json" \
        --slurpfile code "${METRICS_DIR}/code-metrics.json" \
        --slurpfile quality "${METRICS_DIR}/quality-metrics.json" \
        '{
            timestamp: (now | todate),
            build: $build[0],
            coverage: $coverage[0],
            tests: $tests[0],
            code: $code[0],
            quality: $quality[0]
        }' > "$export_file" 2>/dev/null || true

    echo "Exported to: $export_file"
}

# ── Main
main() {
    case "${1:-}" in
        --export)
            collect_build_metrics
            collect_coverage_metrics
            collect_test_metrics
            collect_code_metrics
            collect_quality_metrics
            export_json
            ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            ;;
        *)
            collect_build_metrics
            collect_coverage_metrics
            collect_test_metrics
            collect_code_metrics
            collect_quality_metrics

            print_header
            display_build_health
            display_coverage_health
            display_test_health
            display_code_health
            display_quality_health

            printf "\n${C_GRAY}Updated: $(date)${C_RESET}\n"
            printf "${C_GRAY}Metrics: ${METRICS_DIR}${C_RESET}\n\n"
            ;;
    esac
}

main "$@"
