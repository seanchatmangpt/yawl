#!/usr/bin/env bash
#
# Report Generation Library
#
# Generates test reports in multiple formats:
# - JUnit XML (for CI/CD integration)
# - Markdown (for human reading)
# - JSON (for dashboards and tooling)
#
# Usage:
#   source scripts/shell-test/report.sh
#   generate_junit_xml "reports/junit.xml"
#   generate_markdown_report "reports/TEST_REPORT.md"

set -euo pipefail

# Report configuration
REPORT_NAME="${REPORT_NAME:-YAWL Shell Tests}"
REPORT_PROJECT="${REPORT_PROJECT:-YAWL}"

# Generate JUnit XML report
# Usage: generate_junit_xml <output_file>
generate_junit_xml() {
    local output_file="$1"
    local output_dir
    output_dir=$(dirname "$output_file")
    mkdir -p "$output_dir"

    local total_tests=0
    local total_failures=0
    local total_errors=0
    local total_time=0

    # Collect test results from phase directories
    local testcases=""
    for phase_dir in "$TEST_DIR"/*/; do
        [ -d "$phase_dir" ] || continue

        local phase_name
        phase_name=$(basename "$phase_dir")
        local phase_num="${phase_name%%-*}"
        local phase_desc="${phase_name#*-}"

        # Check for results file
        local results_file="$phase_dir/results.json"
        if [ -f "$results_file" ]; then
            local tests
            tests=$(jq -r '.tests // 0' "$results_file")
            local failures
            failures=$(jq -r '.failures // 0' "$results_file")
            local errors
            errors=$(jq -r '.errors // 0' "$results_file")
            local time
            time=$(jq -r '.duration // 0' "$results_file")

            total_tests=$((total_tests + tests))
            total_failures=$((total_failures + failures))
            total_errors=$((total_errors + errors))
            total_time=$(echo "$total_time + $time" | bc 2>/dev/null || echo "$total_time")

            # Add test cases
            local cases
            cases=$(jq -r '.testcases[] | @base64' "$results_file" 2>/dev/null || true)
            for case_b64 in $cases; do
                local case_json
                case_json=$(echo "$case_b64" | base64 -d)
                local name
                name=$(echo "$case_json" | jq -r '.name')
                local status
                status=$(echo "$case_json" | jq -r '.status')
                local duration
                duration=$(echo "$case_json" | jq -r '.duration // 0')

                if [ "$status" = "passed" ]; then
                    testcases+="    <testcase name=\"$name\" classname=\"$phase_desc\" time=\"$duration\"/>\n"
                else
                    local message
                    message=$(echo "$case_json" | jq -r '.message // "Test failed"')
                    testcases+="    <testcase name=\"$name\" classname=\"$phase_desc\" time=\"$duration\">\n"
                    testcases+="      <failure message=\"$message\"><![CDATA[$message]]></failure>\n"
                    testcases+="    </testcase>\n"
                fi
            done
        else
            # No results file, create a single test case based on phase result
            local phase_result="${PHASE_RESULTS[$phase_num]:-0:0}"
            local result_code="${phase_result%%:*}"

            total_tests=$((total_tests + 1))
            total_time=$((total_time + 1))

            if [ "$result_code" != "0" ]; then
                total_failures=$((total_failures + 1))
                testcases+="    <testcase name=\"$phase_desc\" classname=\"shell-tests\" time=\"1\">\n"
                testcases+="      <failure message=\"Phase $phase_num failed\"><![CDATA[Phase $phase_num: $phase_desc failed]]></failure>\n"
                testcases+="    </testcase>\n"
            else
                testcases+="    <testcase name=\"$phase_desc\" classname=\"shell-tests\" time=\"1\"/>\n"
            fi
        fi
    done

    # Generate XML
    cat > "$output_file" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="$REPORT_NAME" tests="$total_tests" failures="$total_failures" errors="$total_errors" time="$total_time">
  <properties>
    <property name="project" value="$REPORT_PROJECT"/>
    <property name="timestamp" value="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"/>
  </properties>
$(echo -e "$testcases")
</testsuite>
EOF

    echo "Generated JUnit XML: $output_file"
}

# Generate Markdown report
# Usage: generate_markdown_report <output_file>
generate_markdown_report() {
    local output_file="$1"
    local output_dir
    output_dir=$(dirname "$output_file")
    mkdir -p "$output_dir"

    local report_date
    report_date=$(date -u +"%Y-%m-%d %H:%M:%S UTC")

    cat > "$output_file" <<EOF
# YAWL Shell Test Report

**Generated:** $report_date
**Project:** $REPORT_PROJECT

---

## Summary

| Phase | Description | Status | Duration |
|-------|-------------|--------|----------|
EOF

    local total_duration=0
    local all_passed=true

    for phase_dir in "$TEST_DIR"/*/; do
        [ -d "$phase_dir" ] || continue

        local phase_name
        phase_name=$(basename "$phase_dir")
        local phase_num="${phase_name%%-*}"
        local phase_desc="${phase_name#*-}"

        # Get result from phase results
        local phase_result="${PHASE_RESULTS[$phase_num]:-0:0}"
        local result_code="${phase_result%%:*}"
        local duration="${phase_result##*:}"
        duration="${duration:-0}"

        total_duration=$((total_duration + duration))

        local status_icon status_text
        if [ "$result_code" = "0" ]; then
            status_icon="✅"
            status_text="PASSED"
        else
            status_icon="❌"
            status_text="FAILED"
            all_passed=false
        fi

        echo "| $phase_num | $phase_desc | $status_icon $status_text | ${duration}s |" >> "$output_file"
    done

    cat >> "$output_file" <<EOF

**Total Duration:** ${total_duration}s

---

EOF

    if [ "$all_passed" = "true" ]; then
        echo "**Result:** ✅ ALL TESTS PASSED" >> "$output_file"
    else
        echo "**Result:** ❌ SOME TESTS FAILED" >> "$output_file"
    fi

    # Add environment info
    cat >> "$output_file" <<EOF

---

## Environment

- **OS:** $(uname -s) $(uname -r)
- **Java:** $(java -version 2>&1 | head -1 || echo "N/A")
- **Ant:** $(ant -version 2>/dev/null | head -1 || echo "N/A")
- **Shell:** ${SHELL:-/bin/bash}

---

## Test Details

EOF

    # Add details for each phase
    for phase_dir in "$TEST_DIR"/*/; do
        [ -d "$phase_dir" ] || continue

        local phase_name
        phase_name=$(basename "$phase_dir")
        local phase_desc="${phase_name#*-}"

        echo "### Phase: $phase_desc" >> "$output_file"
        echo "" >> "$output_file"

        # Include log if available
        local log_file="$phase_dir/test.log"
        if [ -f "$log_file" ]; then
            echo '```' >> "$output_file"
            tail -50 "$log_file" >> "$output_file"
            echo '```' >> "$output_file"
            echo "" >> "$output_file"
        fi
    done

    echo "Generated Markdown report: $output_file"
}

# Generate JSON metrics
# Usage: generate_metrics_json <output_file>
generate_metrics_json() {
    local output_file="$1"
    local output_dir
    output_dir=$(dirname "$output_file")
    mkdir -p "$output_dir"

    local phases_json="[]"

    for phase_dir in "$TEST_DIR"/*/; do
        [ -d "$phase_dir" ] || continue

        local phase_name
        phase_name=$(basename "$phase_dir")
        local phase_num="${phase_name%%-*}"
        local phase_desc="${phase_name#*-}"

        local phase_result="${PHASE_RESULTS[$phase_num]:-0:0}"
        local result_code="${phase_result%%:*}"
        local duration="${phase_result##*:}"
        duration="${duration:-0}"

        local status="passed"
        [ "$result_code" != "0" ] && status="failed"

        phases_json=$(echo "$phases_json" | jq --arg num "$phase_num" \
                                          --arg desc "$phase_desc" \
                                          --arg status "$status" \
                                          --argjson duration "$duration" \
                                          '. + [{"phase": $num, "description": $desc, "status": $status, "duration": $duration}]')
    done

    local total_passed=0
    local total_failed=0

    for result in "${PHASE_RESULTS[@]}"; do
        local code="${result#*:}"
        code="${code%%:*}"
        if [ "$code" = "0" ]; then
            total_passed=$((total_passed + 1))
        else
            total_failed=$((total_failed + 1))
        fi
    done

    jq -n \
        --arg name "$REPORT_NAME" \
        --arg project "$REPORT_PROJECT" \
        --arg timestamp "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
        --argjson passed "$total_passed" \
        --argjson failed "$total_failed" \
        --argjson phases "$phases_json" \
        '{
            name: $name,
            project: $project,
            timestamp: $timestamp,
            summary: {
                total: ($passed + $failed),
                passed: $passed,
                failed: $failed,
                pass_rate: (($passed / (($passed + $failed) * 1.0)) * 100 | floor)
            },
            phases: $phases
        }' > "$output_file"

    echo "Generated JSON metrics: $output_file"
}

# Generate all reports
# Usage: generate_all_reports <report_dir>
generate_all_reports() {
    local report_dir="${1:-$REPORT_DIR}"
    local timestamp
    timestamp=$(date +"%Y%m%d_%H%M%S")
    local run_dir="$report_dir/$timestamp"
    mkdir -p "$run_dir"

    echo "Generating reports in: $run_dir"

    generate_junit_xml "$run_dir/junit.xml"
    generate_markdown_report "$run_dir/TEST_REPORT.md"
    generate_metrics_json "$run_dir/metrics.json"

    # Create latest symlink
    rm -f "$report_dir/latest"
    ln -s "$run_dir" "$report_dir/latest"

    echo ""
    echo "Reports generated:"
    echo "  - JUnit XML: $run_dir/junit.xml"
    echo "  - Markdown:  $run_dir/TEST_REPORT.md"
    echo "  - JSON:      $run_dir/metrics.json"
    echo "  - Latest:    $report_dir/latest"
}
