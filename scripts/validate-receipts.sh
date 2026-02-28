#!/usr/bin/env bash
# ==========================================================================
# validate-receipts.sh — Parse and validate dx.sh receipt files
#
# This script parses JSON receipt files from dx.sh validation phases,
# extracts violations, and formats results for GitHub Actions integration.
#
# Usage:
#   bash scripts/validate-receipts.sh guard-receipt.json invariant-receipt.json
#   bash scripts/validate-receipts.sh --check-all
#   bash scripts/validate-receipts.sh --summary path/to/receipt.json
#
# Environment:
#   RECEIPT_DIR     Directory containing receipts (default: .claude/receipts)
#   VERBOSE         Show detailed violation information (default: 0)
#   MAX_VIOLATIONS  Maximum violations to display (default: 10)
#
# Exit Codes:
#   0 = All receipts GREEN (validation passed)
#   1 = Missing receipt file or parse error
#   2 = RED status found (validation failed)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# ── Configuration ──────────────────────────────────────────────────────
RECEIPT_DIR="${RECEIPT_DIR:-.claude/receipts}"
VERBOSE="${VERBOSE:-0}"
MAX_VIOLATIONS="${MAX_VIOLATIONS:-10}"

# Color codes
C_GREEN='\033[0;32m'
C_RED='\033[0;31m'
C_YELLOW='\033[1;33m'
C_CYAN='\033[0;36m'
C_RESET='\033[0m'

# ── Helper Functions ───────────────────────────────────────────────────

# Print colored message
log_info() {
    printf "${C_CYAN}INFO${C_RESET}: %s\n" "$1" >&2
}

log_success() {
    printf "${C_GREEN}PASS${C_RESET}: %s\n" "$1" >&2
}

log_error() {
    printf "${C_RED}FAIL${C_RESET}: %s\n" "$1" >&2
}

log_warning() {
    printf "${C_YELLOW}WARN${C_RESET}: %s\n" "$1" >&2
}

# Parse receipt JSON and extract status
parse_receipt_status() {
    local receipt_file="$1"

    if [[ ! -f "$receipt_file" ]]; then
        log_error "Receipt file not found: $receipt_file"
        return 1
    fi

    # Validate JSON
    if ! jq empty "$receipt_file" 2>/dev/null; then
        log_error "Invalid JSON in receipt file: $receipt_file"
        return 1
    fi

    # Extract status field
    local status
    status=$(jq -r '.status // "UNKNOWN"' "$receipt_file" 2>/dev/null || echo "UNKNOWN")
    echo "$status"
}

# Get violations count from receipt
count_violations() {
    local receipt_file="$1"

    if [[ ! -f "$receipt_file" ]]; then
        return 1
    fi

    # Try multiple field names for violation count
    local count
    count=$(jq '.violations_found // (.violations | length) // 0' "$receipt_file" 2>/dev/null || echo "0")
    echo "$count"
}

# Extract top N violations from receipt
extract_violations() {
    local receipt_file="$1"
    local limit="${2:-$MAX_VIOLATIONS}"

    if [[ ! -f "$receipt_file" ]]; then
        return 1
    fi

    jq -r '.violations[]? | select(. != null) | "\(.pattern // .invariant):\(.file // "unknown"):\(.line // "?"):\(.content // .issue // "no details")"' \
        "$receipt_file" 2>/dev/null | head -n "$limit" || true
}

# Format receipt as GitHub markdown
format_for_github() {
    local receipt_file="$1"
    local phase_name="${2:-VALIDATION}"

    if [[ ! -f "$receipt_file" ]]; then
        echo "## $phase_name"
        echo ""
        echo "**Status**: FILE NOT FOUND"
        return 1
    fi

    local status
    local violations_count
    local timestamp
    local files_scanned

    status=$(jq -r '.status // "UNKNOWN"' "$receipt_file" 2>/dev/null || echo "UNKNOWN")
    violations_count=$(jq '.violations_found // (.violations | length) // 0' "$receipt_file" 2>/dev/null || echo "0")
    timestamp=$(jq -r '.timestamp // "unknown"' "$receipt_file" 2>/dev/null || echo "unknown")
    files_scanned=$(jq '.java_files_scanned // .files_scanned // "N/A"' "$receipt_file" 2>/dev/null || echo "N/A")

    # Header
    echo "## $phase_name"
    echo ""

    # Status badge
    if [[ "$status" == "GREEN" ]]; then
        echo "**Status**: ${C_GREEN}✓ PASSED${C_RESET}"
    else
        echo "**Status**: ${C_RED}✗ FAILED${C_RESET}"
    fi
    echo ""

    # Summary table
    echo "| Metric | Value |"
    echo "|--------|-------|"
    echo "| Status | $status |"
    echo "| Violations | $violations_count |"
    echo "| Files Scanned | $files_scanned |"
    echo "| Timestamp | $timestamp |"
    echo ""

    # Violations if any
    if [[ "$violations_count" -gt 0 ]]; then
        echo "### Violations"
        echo ""
        echo "\`\`\`"
        extract_violations "$receipt_file" "$MAX_VIOLATIONS"
        echo "\`\`\`"
        echo ""

        if [[ "$violations_count" -gt "$MAX_VIOLATIONS" ]]; then
            echo "**Note**: Showing first $MAX_VIOLATIONS of $violations_count violations. See receipt file for full details."
            echo ""
        fi
    fi

    # Remediation if FAILED
    if [[ "$status" != "GREEN" ]]; then
        echo "### Remediation"
        echo ""
        local next_action
        next_action=$(jq -r '.next_action // "Fix violations and re-run validation"' "$receipt_file" 2>/dev/null || echo "Fix violations and re-run validation")
        echo "- $next_action"
        echo ""
    fi
}

# Check single receipt and return exit code
check_receipt_status() {
    local receipt_file="$1"

    if [[ ! -f "$receipt_file" ]]; then
        log_error "Receipt not found: $receipt_file"
        return 1
    fi

    local status
    status=$(parse_receipt_status "$receipt_file") || return 1

    case "$status" in
        GREEN)
            return 0
            ;;
        RED)
            return 2
            ;;
        *)
            log_warning "Unknown status: $status"
            return 1
            ;;
    esac
}

# Summarize violations with counts by type
summarize_violations() {
    local receipt_file="$1"

    if [[ ! -f "$receipt_file" ]]; then
        log_error "Receipt not found: $receipt_file"
        return 1
    fi

    local total_violations
    total_violations=$(count_violations "$receipt_file") || return 1

    if [[ "$total_violations" -eq 0 ]]; then
        log_success "No violations found"
        return 0
    fi

    log_warning "Found $total_violations violation(s)"

    # Show violations by type
    jq -r '.violations[]? | select(. != null) | .pattern // .invariant' "$receipt_file" 2>/dev/null | \
        sort | uniq -c | sort -rn | while read count type; do
        printf "  %s: %s\n" "$type" "$count" >&2
    done || true
}

# Check all receipt files in directory
check_all_receipts() {
    local receipts_dir="${1:-$RECEIPT_DIR}"
    local all_passed=true
    local failed_receipts=()

    if [[ ! -d "$receipts_dir" ]]; then
        log_error "Receipts directory not found: $receipts_dir"
        return 1
    fi

    log_info "Checking all receipts in: $receipts_dir"

    for receipt in "$receipts_dir"/*-receipt.json; do
        if [[ ! -f "$receipt" ]]; then
            continue
        fi

        local basename_receipt
        basename_receipt=$(basename "$receipt")
        log_info "Validating: $basename_receipt"

        if check_receipt_status "$receipt"; then
            log_success "$basename_receipt"
        else
            log_error "$basename_receipt"
            all_passed=false
            failed_receipts+=("$basename_receipt")
        fi
    done

    if [[ "$all_passed" == false ]]; then
        log_error "Failed receipts: ${failed_receipts[*]}"
        return 2
    fi

    return 0
}

# Main GitHub output functions
output_to_github() {
    # Redirect formatted output to GITHUB_STEP_SUMMARY if available
    if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
        cat >> "$GITHUB_STEP_SUMMARY"
    fi
}

# ── Main Logic ─────────────────────────────────────────────────────────

main() {
    if [[ $# -eq 0 ]]; then
        log_error "Usage: $0 [receipt1.json] [receipt2.json] ... | --check-all | --summary <receipt>"
        return 1
    fi

    local exit_code=0

    case "${1:-}" in
        --check-all)
            check_all_receipts "${2:-$RECEIPT_DIR}"
            exit_code=$?
            ;;
        --summary)
            if [[ -z "${2:-}" ]]; then
                log_error "Missing receipt file path for --summary"
                return 1
            fi
            summarize_violations "$2"
            exit_code=$?
            ;;
        --github-output)
            # Format all provided receipt files for GitHub Actions
            shift
            {
                echo "# Validation Results"
                echo ""
                for receipt in "$@"; do
                    if [[ -f "$receipt" ]]; then
                        local phase_name
                        phase_name=$(jq -r '.phase // "UNKNOWN"' "$receipt" 2>/dev/null | tr '[:lower:]' '[:upper:]')
                        format_for_github "$receipt" "$phase_name"
                    fi
                done
            } | output_to_github
            ;;
        *)
            # Validate provided receipt files
            local all_passed=true
            for receipt in "$@"; do
                if [[ ! -f "$receipt" ]]; then
                    log_error "Not found: $receipt"
                    exit_code=1
                    all_passed=false
                    continue
                fi

                local phase
                phase=$(jq -r '.phase // "unknown"' "$receipt" 2>/dev/null || echo "unknown")

                if check_receipt_status "$receipt"; then
                    log_success "$phase receipt is GREEN"
                    if [[ "$VERBOSE" == "1" ]]; then
                        format_for_github "$receipt" "${phase^^}"
                    fi
                else
                    log_error "$phase receipt is RED"
                    if [[ "$VERBOSE" == "1" ]]; then
                        format_for_github "$receipt" "${phase^^}"
                    fi
                    all_passed=false
                    exit_code=2
                fi
            done

            if [[ "$all_passed" == false ]]; then
                return 2
            fi
            ;;
    esac

    return $exit_code
}

# Execute main function
main "$@"
