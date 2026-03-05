#!/usr/bin/env bash
# Validation Wrapper — Run dx.sh all and report status
set -euo pipefail

RALPH_HOME="${RALPH_HOME:-.}"
LOGS_DIR="${RALPH_HOME}/.claude/.ralph-logs"

mkdir -p "${LOGS_DIR}"

# Run dx.sh all with output capture
run_validation() {
    local log_file="${LOGS_DIR}/validation-$(date +%s).log"

    echo "Running: dx.sh all" >&2
    if bash scripts/dx.sh all > "${log_file}" 2>&1; then
        echo "GREEN" >&2
        echo "GREEN"
        return 0
    else
        local exit_code=$?
        echo "RED (exit code: ${exit_code})" >&2
        echo "RED"
        return ${exit_code}
    fi
}

# Report validation with context
report_validation() {
    local status="$1"
    local iteration="${2:-unknown}"

    cat <<EOF >&2
---
Validation Report (Iteration ${iteration})
Status: ${status}
Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)
---
EOF

    return 0
}

# Main
main() {
    local iteration="${1:-1}"

    report_validation "PENDING" "${iteration}"

    if run_validation; then
        report_validation "GREEN" "${iteration}"
        return 0
    else
        local exit_code=$?
        report_validation "RED" "${iteration}"
        return ${exit_code}
    fi
}

main "$@"
