#!/usr/bin/env bash
# ==========================================================================
# output-aggregation.sh - JSON/JUnit output aggregation for validation scripts
#
# Provides functions to aggregate results from parallel validation runs
# into unified JSON and JUnit XML output formats for CI/CD integration.
#
# Usage:
#   source scripts/validation/lib/output-aggregation.sh
#
# Functions:
#   agg_init                - Initialize aggregation state
#   agg_add_result          - Add a single test result
#   agg_add_suite           - Add results from a suite
#   agg_merge_json          - Merge multiple JSON result files
#   agg_output_json         - Output aggregated JSON
#   agg_output_junit        - Output aggregated JUnit XML
#   agg_output_summary      - Output human-readable summary
#   agg_get_exit_code       - Get exit code based on results
#
# Environment:
#   AGG_OUTPUT_DIR          - Directory for output files (default: docs/validation)
#   AGG_PARALLEL_RESULTS    - Directory for intermediate results (default: /tmp/yawl-validation-$$)
# ==========================================================================
set -euo pipefail

# Colors
readonly _AGG_RED='\033[0;31m'
readonly _AGG_GREEN='\033[0;32m'
readonly _AGG_YELLOW='\033[1;33m'
readonly _AGG_RESET='\033[0m'

# State file for parallel execution
_AGG_STATE_FILE=""
_AGG_LOCK_FILE=""

# -------------------------------------------------------------------------
# Initialize aggregation state
# Arguments:
#   $1 - Suite name (e.g., "documentation", "observatory")
#   $2 - Output directory (optional, default: docs/validation)
# -------------------------------------------------------------------------
agg_init() {
    local suite_name="${1:-validation}"
    local output_dir="${2:-${AGG_OUTPUT_DIR:-docs/validation}}"

    # Create output directory
    mkdir -p "${output_dir}"

    # Set up state file for this suite
    _AGG_STATE_FILE="${output_dir}/.agg-state-${suite_name}.json"
    _AGG_LOCK_FILE="${output_dir}/.agg-lock-${suite_name}"

    # Initialize state
    cat > "${_AGG_STATE_FILE}" << EOF
{
  "suite": "${suite_name}",
  "timestamp": "$(date -u '+%Y-%m-%dT%H:%M:%SZ')",
  "total": 0,
  "passed": 0,
  "failed": 0,
  "skipped": 0,
  "warnings": 0,
  "results": [],
  "errors": []
}
EOF

    export AGG_OUTPUT_DIR="${output_dir}"
    export AGG_SUITE_NAME="${suite_name}"
}

# -------------------------------------------------------------------------
# Add a single test result to aggregation
# Arguments:
#   $1 - Test name
#   $2 - Status: PASS, FAIL, SKIP, WARN
#   $3 - Message (optional)
#   $4 - Duration in milliseconds (optional)
# -------------------------------------------------------------------------
agg_add_result() {
    local test_name="${1:-unknown}"
    local status="${2:-SKIP}"
    local message="${3:-}"
    local duration="${4:-0}"

    if [[ -z "${_AGG_STATE_FILE}" ]] || [[ ! -f "${_AGG_STATE_FILE}" ]]; then
        echo "ERROR: agg_init not called" >&2
        return 1
    fi

    # Use file locking for parallel safety
    (
        flock -x 200 2>/dev/null || true

        # Read current state
        local state
        state=$(cat "${_AGG_STATE_FILE}")

        # Update counters
        local total passed failed skipped warnings
        total=$(echo "${state}" | jq -r '.total')
        passed=$(echo "${state}" | jq -r '.passed')
        failed=$(echo "${state}" | jq -r '.failed')
        skipped=$(echo "${state}" | jq -r '.skipped')
        warnings=$(echo "${state}" | jq -r '.warnings')

        total=$((total + 1))
        case "${status}" in
            PASS)  passed=$((passed + 1)) ;;
            FAIL)  failed=$((failed + 1)) ;;
            SKIP)  skipped=$((skipped + 1)) ;;
            WARN)  warnings=$((warnings + 1)); passed=$((passed + 1)) ;;
        esac

        # Create result object
        local result
        result=$(jq -n \
            --arg name "${test_name}" \
            --arg status "${status}" \
            --arg message "${message}" \
            --argjson duration "${duration}" \
            '{name: $name, status: $status, message: $message, duration_ms: $duration}')

        # Update state
        echo "${state}" | jq \
            --argjson total "${total}" \
            --argjson passed "${passed}" \
            --argjson failed "${failed}" \
            --argjson skipped "${skipped}" \
            --argjson warnings "${warnings}" \
            --argjson result "${result}" \
            '.total = $total | .passed = $passed | .failed = $failed | .skipped = $skipped | .warnings = $warnings | .results += [$result]' \
            > "${_AGG_STATE_FILE}.tmp"
        mv "${_AGG_STATE_FILE}.tmp" "${_AGG_STATE_FILE}"

    ) 200>"${_AGG_LOCK_FILE}"
}

# -------------------------------------------------------------------------
# Add error message to aggregation
# Arguments:
#   $1 - Error message
#   $2 - Error details (optional)
# -------------------------------------------------------------------------
agg_add_error() {
    local error_msg="${1:-}"
    local error_details="${2:-}"

    if [[ -z "${_AGG_STATE_FILE}" ]] || [[ ! -f "${_AGG_STATE_FILE}" ]]; then
        return 1
    fi

    (
        flock -x 200 2>/dev/null || true

        local state
        state=$(cat "${_AGG_STATE_FILE}")

        local error
        error=$(jq -n \
            --arg msg "${error_msg}" \
            --arg details "${error_details}" \
            '{message: $msg, details: $details, timestamp: (now | todateiso8601)}')

        echo "${state}" | jq --argjson error "${error}" '.errors += [$error]' \
            > "${_AGG_STATE_FILE}.tmp"
        mv "${_AGG_STATE_FILE}.tmp" "${_AGG_STATE_FILE}"

    ) 200>"${_AGG_LOCK_FILE}"
}

# -------------------------------------------------------------------------
# Add results from a sub-suite
# Arguments:
#   $1 - Path to sub-suite JSON results file
# -------------------------------------------------------------------------
agg_add_suite() {
    local suite_file="${1:-}"

    if [[ ! -f "${suite_file}" ]]; then
        agg_add_error "Suite file not found: ${suite_file}"
        return 1
    fi

    if [[ -z "${_AGG_STATE_FILE}" ]] || [[ ! -f "${_AGG_STATE_FILE}" ]]; then
        return 1
    fi

    (
        flock -x 200 2>/dev/null || true

        local state
        state=$(cat "${_AGG_STATE_FILE}")

        local suite_results
        suite_results=$(jq -c '.results[]' "${suite_file}" 2>/dev/null)

        while IFS= read -r result; do
            if [[ -n "${result}" ]]; then
                state=$(echo "${state}" | jq --argjson result "${result}" '.results += [$result]')

                # Update counters based on status
                local status
                status=$(echo "${result}" | jq -r '.status')
                case "${status}" in
                    PASS)  state=$(echo "${state}" | jq '.total += 1 | .passed += 1') ;;
                    FAIL)  state=$(echo "${state}" | jq '.total += 1 | .failed += 1') ;;
                    SKIP)  state=$(echo "${state}" | jq '.total += 1 | .skipped += 1') ;;
                    WARN)  state=$(echo "${state}" | jq '.total += 1 | .passed += 1 | .warnings += 1') ;;
                esac
            fi
        done <<< "${suite_results}"

        echo "${state}" > "${_AGG_STATE_FILE}"

    ) 200>"${_AGG_LOCK_FILE}"
}

# -------------------------------------------------------------------------
# Merge multiple JSON result files
# Arguments:
#   $1 - Pattern or directory containing JSON files
#   $2 - Output file (optional)
# -------------------------------------------------------------------------
agg_merge_json() {
    local input_pattern="${1:-}"
    local output_file="${2:-${AGG_OUTPUT_DIR}/aggregated-results.json}"

    local merged_state
    merged_state=$(jq -n '{
        suite: "aggregated",
        timestamp: (now | todateiso8601),
        total: 0,
        passed: 0,
        failed: 0,
        skipped: 0,
        warnings: 0,
        results: [],
        errors: [],
        suites: []
    }')

    local file_count=0
    for json_file in ${input_pattern}; do
        if [[ -f "${json_file}" ]] && jq empty "${json_file}" 2>/dev/null; then
            local suite_name
            suite_name=$(jq -r '.suite // "unknown"' "${json_file}")

            # Merge counters
            local total passed failed skipped warnings
            total=$(jq -r '.total // 0' "${json_file}")
            passed=$(jq -r '.passed // 0' "${json_file}")
            failed=$(jq -r '.failed // 0' "${json_file}")
            skipped=$(jq -r '.skipped // 0' "${json_file}")
            warnings=$(jq -r '.warnings // 0' "${json_file}")

            merged_state=$(echo "${merged_state}" | jq \
                --argjson total "${total}" \
                --argjson passed "${passed}" \
                --argjson failed "${failed}" \
                --argjson skipped "${skipped}" \
                --argjson warnings "${warnings}" \
                '.total += $total | .passed += $passed | .failed += $failed | .skipped += $skipped | .warnings += $warnings')

            # Merge results
            local results
            results=$(jq -c '.results // []' "${json_file}")
            merged_state=$(echo "${merged_state}" | jq --argjson results "${results}" '.results += $results')

            # Merge errors
            local errors
            errors=$(jq -c '.errors // []' "${json_file}")
            merged_state=$(echo "${merged_state}" | jq --argjson errors "${errors}" '.errors += $errors')

            # Track suites
            merged_state=$(echo "${merged_state}" | jq \
                --arg name "${suite_name}" \
                --arg file "${json_file}" \
                '.suites += [{name: $name, file: $file}]')

            file_count=$((file_count + 1))
        fi
    done

    echo "${merged_state}" | jq '.' > "${output_file}"
    echo "${file_count}"  # Return count of merged files
}

# -------------------------------------------------------------------------
# Output aggregated results as JSON
# Arguments:
#   $1 - Output file (optional, default: stdout)
# -------------------------------------------------------------------------
agg_output_json() {
    local output_file="${1:-}"

    if [[ -z "${_AGG_STATE_FILE}" ]] || [[ ! -f "${_AGG_STATE_FILE}" ]]; then
        echo "ERROR: agg_init not called" >&2
        return 1
    fi

    local state
    state=$(cat "${_AGG_STATE_FILE}")

    # Add final metadata
    state=$(echo "${state}" | jq \
        --arg commit "$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')" \
        --arg branch "$(git branch --show-current 2>/dev/null || echo 'unknown')" \
        '. + {commit: $commit, branch: $branch}')

    if [[ -n "${output_file}" ]]; then
        echo "${state}" | jq '.' > "${output_file}"
        echo "${output_file}"
    else
        echo "${state}" | jq '.'
    fi
}

# -------------------------------------------------------------------------
# Output aggregated results as JUnit XML
# Arguments:
#   $1 - Output file (optional, default: stdout)
# -------------------------------------------------------------------------
agg_output_junit() {
    local output_file="${1:-}"

    if [[ -z "${_AGG_STATE_FILE}" ]] || [[ ! -f "${_AGG_STATE_FILE}" ]]; then
        echo "ERROR: agg_init not called" >&2
        return 1
    fi

    local state
    state=$(cat "${_AGG_STATE_FILE}")

    local suite_name timestamp total passed failed skipped warnings
    suite_name=$(echo "${state}" | jq -r '.suite')
    timestamp=$(echo "${state}" | jq -r '.timestamp')
    total=$(echo "${state}" | jq -r '.total')
    passed=$(echo "${state}" | jq -r '.passed')
    failed=$(echo "${state}" | jq -r '.failed')
    skipped=$(echo "${state}" | jq -r '.skipped')
    warnings=$(echo "${state}" | jq -r '.warnings')

    local junit_xml
    junit_xml='<?xml version="1.0" encoding="UTF-8"?>'
    junit_xml+=$'\n''<testsuites>'
    junit_xml+=$'\n'"  <testsuite name=\"${suite_name}\" tests=\"${total}\" failures=\"${failed}\" skipped=\"${skipped}\" time=\"0\" timestamp=\"${timestamp}\">"

    # Add properties
    junit_xml+=$'\n''    <properties>'
    junit_xml+=$'\n'"      <property name=\"passed\" value=\"${passed}\"/>"
    junit_xml+=$'\n'"      <property name=\"warnings\" value=\"${warnings}\"/>"
    junit_xml+=$'\n'"      <property name=\"commit\" value=\"$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')\"/>"
    junit_xml+=$'\n''    </properties>'

    # Add test cases
    local results
    results=$(echo "${state}" | jq -c '.results[]')
    while IFS= read -r result; do
        if [[ -n "${result}" ]]; then
            local name status message duration
            name=$(echo "${result}" | jq -r '.name')
            status=$(echo "${result}" | jq -r '.status')
            message=$(echo "${result}" | jq -r '.message // ""')
            duration=$(echo "${result}" | jq -r '.duration_ms // 0')
            # Convert ms to seconds for JUnit
            local duration_sec
            duration_sec=$(echo "scale=3; ${duration} / 1000" | bc 2>/dev/null || echo "0")

            # Escape XML special characters in name and message
            name=$(echo "${name}" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g; s/"/\&quot;/g')
            message=$(echo "${message}" | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g; s/"/\&quot;/g')

            junit_xml+=$'\n'"    <testcase name=\"${name}\" classname=\"${suite_name}\" time=\"${duration_sec}\">"

            case "${status}" in
                FAIL)
                    junit_xml+=$'\n'"      <failure message=\"${message:-Test failed}\"/>"
                    ;;
                SKIP)
                    junit_xml+=$'\n'"      <skipped message=\"${message:-Skipped}\"/>"
                    ;;
                WARN)
                    junit_xml+=$'\n'"      <system-out>WARNING: ${message}</system-out>"
                    ;;
            esac

            junit_xml+=$'\n'"    </testcase>"
        fi
    done <<< "${results}"

    # Add errors as system-err
    local errors
    errors=$(echo "${state}" | jq -c '.errors[]')
    if [[ -n "${errors}" ]]; then
        junit_xml+=$'\n''    <system-err>'
        while IFS= read -r error; do
            if [[ -n "${error}" ]]; then
                local err_msg err_details
                err_msg=$(echo "${error}" | jq -r '.message')
                err_details=$(echo "${error}" | jq -r '.details // ""')
                junit_xml+=$'\n'"      ERROR: ${err_msg}"
                [[ -n "${err_details}" ]] && junit_xml+=$'\n'"      Details: ${err_details}"
            fi
        done <<< "${errors}"
        junit_xml+=$'\n''    </system-err>'
    fi

    junit_xml+=$'\n''  </testsuite>'
    junit_xml+=$'\n''</testsuites>'

    if [[ -n "${output_file}" ]]; then
        echo "${junit_xml}" > "${output_file}"
        echo "${output_file}"
    else
        echo "${junit_xml}"
    fi
}

# -------------------------------------------------------------------------
# Output human-readable summary
# Arguments:
#   $1 - Output file (optional, default: stderr)
# -------------------------------------------------------------------------
agg_output_summary() {
    local output_file="${1:-}"

    if [[ -z "${_AGG_STATE_FILE}" ]] || [[ ! -f "${_AGG_STATE_FILE}" ]]; then
        echo "ERROR: agg_init not called" >&2
        return 1
    fi

    local state
    state=$(cat "${_AGG_STATE_FILE}")

    local suite_name total passed failed skipped warnings
    suite_name=$(echo "${state}" | jq -r '.suite')
    total=$(echo "${state}" | jq -r '.total')
    passed=$(echo "${state}" | jq -r '.passed')
    failed=$(echo "${state}" | jq -r '.failed')
    skipped=$(echo "${state}" | jq -r '.skipped')
    warnings=$(echo "${state}" | jq -r '.warnings')

    local summary=""
    summary+=$'\n'"========================================="
    summary+=$'\n'"  ${suite_name} Validation Summary"
    summary+=$'\n'"  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    summary+=$'\n'"========================================="
    summary+=$'\n'""
    summary+=$'\n'"  Total:    ${total}"
    summary+=$'\n'"  ${_AGG_GREEN}Passed:   ${passed}${_AGG_RESET}"
    summary+=$'\n'"  ${_AGG_RED}Failed:   ${failed}${_AGG_RESET}"
    summary+=$'\n'"  Skipped:  ${skipped}"
    summary+=$'\n'"  ${_AGG_YELLOW}Warnings: ${warnings}${_AGG_RESET}"
    summary+=$'\n'"========================================="

    # Show failed tests
    if [[ "${failed}" -gt 0 ]]; then
        summary+=$'\n'""
        summary+=$'\n'"Failed Tests:"
        local failed_tests
        failed_tests=$(echo "${state}" | jq -r '.results[] | select(.status == "FAIL") | .name')
        while IFS= read -r test; do
            [[ -n "${test}" ]] && summary+=$'\n'"  ${_AGG_RED}X${_AGG_RESET} ${test}"
        done <<< "${failed_tests}"
    fi

    # Show warnings
    if [[ "${warnings}" -gt 0 ]]; then
        summary+=$'\n'""
        summary+=$'\n'"Warnings:"
        local warn_tests
        warn_tests=$(echo "${state}" | jq -r '.results[] | select(.status == "WARN") | "\(.name): \(.message // "No message")"')
        while IFS= read -r test; do
            [[ -n "${test}" ]] && summary+=$'\n'"  ${_AGG_YELLOW}!${_AGG_RESET} ${test}"
        done <<< "${warn_tests}"
    fi

    if [[ "${failed}" -gt 0 ]]; then
        summary+=$'\n'""
        summary+=$'\n'"${_AGG_RED}VALIDATION FAILED${_AGG_RESET}"
    else
        summary+=$'\n'""
        summary+=$'\n'"${_AGG_GREEN}VALIDATION PASSED${_AGG_RESET}"
    fi

    if [[ -n "${output_file}" ]]; then
        echo -e "${summary}" > "${output_file}"
    else
        echo -e "${summary}" >&2
    fi
}

# -------------------------------------------------------------------------
# Get exit code based on results
# Returns: 0 if all passed, 1 if any failed, 2 if warnings only
# -------------------------------------------------------------------------
agg_get_exit_code() {
    if [[ -z "${_AGG_STATE_FILE}" ]] || [[ ! -f "${_AGG_STATE_FILE}" ]]; then
        return 1
    fi

    local state
    state=$(cat "${_AGG_STATE_FILE}")

    local failed warnings
    failed=$(echo "${state}" | jq -r '.failed')
    warnings=$(echo "${state}" | jq -r '.warnings')

    if [[ "${failed}" -gt 0 ]]; then
        return 1
    elif [[ "${warnings}" -gt 0 ]]; then
        return 2
    else
        return 0
    fi
}

# -------------------------------------------------------------------------
# Cleanup temporary files
# -------------------------------------------------------------------------
agg_cleanup() {
    [[ -n "${_AGG_STATE_FILE}" ]] && rm -f "${_AGG_STATE_FILE}" 2>/dev/null
    [[ -n "${_AGG_LOCK_FILE}" ]] && rm -f "${_AGG_LOCK_FILE}" 2>/dev/null
}

# Register cleanup on exit
trap agg_cleanup EXIT
