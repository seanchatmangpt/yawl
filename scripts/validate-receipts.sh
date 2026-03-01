#!/usr/bin/env bash
################################################################################
# YAWL Receipt Validation Utility
# 
# Parses JSON receipts from dx.sh validation phases and reports results.
# Supports both local development and CI/CD environments.
#
# Usage:
#   validate-receipts.sh parse <receipt_file>
#   validate-receipts.sh check <receipt_file>
#   validate-receipts.sh summarize <receipt_file> [limit]
#   validate-receipts.sh format-github <receipt_file>
#   validate-receipts.sh check-all <directory>
#
# Exit Codes:
#   0 = All receipts GREEN or no violations
#   1 = Transient error (file not found, parse error)
#   2 = Violations found (RED status)
################################################################################

set -euo pipefail

# Color codes for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

################################################################################
# FUNCTION: has_jq
# Check if jq is available for JSON parsing
################################################################################
has_jq() {
    command -v jq &> /dev/null
}

################################################################################
# FUNCTION: parse_json_bash_fallback
# Simple JSON key extraction using bash (fallback if jq not available)
# Extracts value for a given key from a simple JSON object
################################################################################
parse_json_bash_fallback() {
    local json="$1"
    local key="$2"
    
    # Simple regex to extract value from JSON
    local value=$(echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*[^,}]*" | cut -d: -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | tr -d '"')
    echo "$value"
}

################################################################################
# FUNCTION: parse_receipt_json
# Reads guard-receipt.json or similar and extracts metadata
# 
# Outputs shell variables:
#   - RECEIPT_STATUS (GREEN|RED)
#   - FILES_SCANNED (number)
#   - TOTAL_VIOLATIONS (number)
#   - PHASE (guards|invariants|etc)
#
# Returns: 0 on success, 1 on file not found or parse error
################################################################################
parse_receipt_json() {
    local receipt_file="$1"
    
    # Check if file exists
    if [[ ! -f "$receipt_file" ]]; then
        echo "ERROR: Receipt file not found: $receipt_file" >&2
        return 1
    fi
    
    # Read entire file
    local content
    if ! content=$(cat "$receipt_file" 2>/dev/null); then
        echo "ERROR: Failed to read receipt file: $receipt_file" >&2
        return 1
    fi
    
    # Validate JSON syntax
    if has_jq; then
        if ! jq . <<< "$content" > /dev/null 2>&1; then
            echo "ERROR: Invalid JSON in receipt file: $receipt_file" >&2
            return 1
        fi
    fi
    
    # Extract values using jq if available, otherwise fallback
    if has_jq; then
        RECEIPT_STATUS=$(jq -r '.status // "UNKNOWN"' <<< "$content")
        FILES_SCANNED=$(jq -r '.files_scanned // 0' <<< "$content")
        TOTAL_VIOLATIONS=$(jq -r '.summary.total_violations // (.violations | length)' <<< "$content")
        PHASE=$(jq -r '.phase // "unknown"' <<< "$content")
    else
        RECEIPT_STATUS=$(echo "$content" | grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)
        FILES_SCANNED=$(echo "$content" | grep -o '"files_scanned"[[:space:]]*:[[:space:]]*[0-9]*' | cut -d: -f2 | tr -d ' ')
        TOTAL_VIOLATIONS=$(echo "$content" | grep -o '"total_violations"[[:space:]]*:[[:space:]]*[0-9]*' | cut -d: -f2 | tr -d ' ')
        PHASE=$(echo "$content" | grep -o '"phase"[[:space:]]*:[[:space:]]*"[^"]*"' | cut -d'"' -f4)
    fi
    
    # Set defaults
    RECEIPT_STATUS="${RECEIPT_STATUS:-UNKNOWN}"
    FILES_SCANNED="${FILES_SCANNED:-0}"
    TOTAL_VIOLATIONS="${TOTAL_VIOLATIONS:-0}"
    PHASE="${PHASE:-unknown}"
    
    return 0
}

################################################################################
# FUNCTION: check_receipt_status
# Reads receipt and returns appropriate exit code
# Returns: 0 if GREEN, 2 if RED, 1 if error
################################################################################
check_receipt_status() {
    local receipt_file="$1"
    
    if ! parse_receipt_json "$receipt_file"; then
        return 1
    fi
    
    if [[ "$RECEIPT_STATUS" == "GREEN" ]]; then
        return 0
    elif [[ "$RECEIPT_STATUS" == "RED" ]]; then
        return 2
    else
        echo "WARNING: Unknown receipt status: $RECEIPT_STATUS" >&2
        return 1
    fi
}

################################################################################
# FUNCTION: summarize_violations
# Extract top N violations from receipt and format for display
# Outputs formatted violation list
################################################################################
summarize_violations() {
    local receipt_file="$1"
    local limit="${2:-10}"  # Default to 10 violations
    
    if [[ ! -f "$receipt_file" ]]; then
        echo "ERROR: Receipt file not found: $receipt_file" >&2
        return 1
    fi
    
    local content
    if ! content=$(cat "$receipt_file" 2>/dev/null); then
        echo "ERROR: Failed to read receipt file: $receipt_file" >&2
        return 1
    fi
    
    if ! has_jq; then
        echo "WARNING: jq not available, showing raw violations" >&2
        # Fallback: show limited raw content
        head -100 "$receipt_file"
        return 0
    fi
    
    # Extract violations using jq and format
    jq -r ".violations | .[:$limit] | .[] | 
        \"Pattern: \\(.pattern) | File: \\(.file) | Line: \\(.line) | \\(.content[0:60])\"" \
        <<< "$content" 2>/dev/null || {
        echo "No violations found in receipt"
        return 0
    }
}

################################################################################
# FUNCTION: format_for_github
# Format receipt as GitHub markdown for job summary
# Outputs markdown suitable for $GITHUB_STEP_SUMMARY
################################################################################
format_for_github() {
    local receipt_file="$1"
    
    if ! parse_receipt_json "$receipt_file"; then
        echo "## Validation Report - Parse Error"
        echo "Failed to parse receipt: $receipt_file"
        return 1
    fi
    
    # Build markdown header
    local status_emoji="✅"
    if [[ "$RECEIPT_STATUS" == "RED" ]]; then
        status_emoji="❌"
    fi
    
    cat << MARKDOWN
## $status_emoji Validation Report: Phase '$PHASE'

**Status**: \`$RECEIPT_STATUS\`

### Summary
- **Files Scanned**: $FILES_SCANNED
- **Violations Found**: $TOTAL_VIOLATIONS

MARKDOWN

    # Add violation details if present
    if [[ ! -f "$receipt_file" ]] || [[ "$TOTAL_VIOLATIONS" == "0" ]]; then
        echo "No violations detected. ✓"
        return 0
    fi
    
    local content
    content=$(cat "$receipt_file")
    
    if has_jq; then
        local summary=$(jq '.summary' <<< "$content" 2>/dev/null || echo "{}")
        
        # Show pattern breakdown if available
        if [[ "$summary" != "{}" ]]; then
            cat << MARKDOWN

### Violations by Pattern

MARKDOWN
            jq -r '.summary | to_entries[] | select(.value > 0) | "- **\(.key)**: \(.value)"' \
                <<< "$content" 2>/dev/null || true
        fi
        
        # Show first 5 violations with details
        if [[ "$TOTAL_VIOLATIONS" != "0" ]]; then
            cat << MARKDOWN

### Top Violations

MARKDOWN
            jq -r '.violations[] | "- **\(.pattern)** at \(.file):\(.line)\n  ```\n  \(.content)\n  ```\n  Fix: \(.fix_guidance)"' \
                <<< "$content" 2>/dev/null | head -80 || true
        fi
    else
        echo "### Violation Details"
        echo "\`\`\`"
        head -50 "$receipt_file"
        echo "\`\`\`"
    fi
    
    return 0
}

################################################################################
# FUNCTION: check_all_receipts
# Check all receipt files in a directory
# Returns: 0 if all GREEN, 2 if any RED, 1 if errors
################################################################################
check_all_receipts() {
    local receipt_dir="$1"
    
    if [[ ! -d "$receipt_dir" ]]; then
        echo "ERROR: Receipt directory not found: $receipt_dir" >&2
        return 1
    fi
    
    local receipt_count=0
    local failed_receipts=()
    local exit_code=0
    
    # Find all receipt files
    while IFS= read -r receipt_file; do
        receipt_count=$((receipt_count + 1))
        echo "Checking receipt: $receipt_file"
        
        if ! check_receipt_status "$receipt_file"; then
            failed_receipts+=("$receipt_file")
            exit_code=2
        fi
    done < <(find "$receipt_dir" -name "*-receipt.json" -type f 2>/dev/null)
    
    # Report summary
    echo ""
    echo "================================"
    if [[ $receipt_count -eq 0 ]]; then
        echo "No receipt files found in: $receipt_dir"
        return 0
    fi
    
    echo "Receipts checked: $receipt_count"
    if [[ ${#failed_receipts[@]} -gt 0 ]]; then
        echo "Failed receipts: ${#failed_receipts[@]}"
        for failed in "${failed_receipts[@]}"; do
            echo "  - $failed"
        done
    else
        echo "All receipts GREEN ✓"
    fi
    echo "================================"
    
    return $exit_code
}

################################################################################
# MAIN ENTRY POINT
################################################################################
main() {
    local command="${1:-}"
    
    case "$command" in
        parse)
            if [[ $# -lt 2 ]]; then
                echo "Usage: $0 parse <receipt_file>" >&2
                return 1
            fi
            parse_receipt_json "$2"
            return $?
            ;;
        
        check)
            if [[ $# -lt 2 ]]; then
                echo "Usage: $0 check <receipt_file>" >&2
                return 1
            fi
            check_receipt_status "$2"
            return $?
            ;;
        
        summarize)
            if [[ $# -lt 2 ]]; then
                echo "Usage: $0 summarize <receipt_file> [limit]" >&2
                return 1
            fi
            summarize_violations "$2" "${3:-10}"
            return $?
            ;;
        
        format-github)
            if [[ $# -lt 2 ]]; then
                echo "Usage: $0 format-github <receipt_file>" >&2
                return 1
            fi
            format_for_github "$2"
            return $?
            ;;
        
        check-all)
            if [[ $# -lt 2 ]]; then
                echo "Usage: $0 check-all <directory>" >&2
                return 1
            fi
            check_all_receipts "$2"
            return $?
            ;;
        
        *)
            cat << USAGE
YAWL Receipt Validation Utility

Usage:
  $0 parse <receipt_file>              Parse receipt and output metadata
  $0 check <receipt_file>              Check receipt status (exit code 0=GREEN, 2=RED)
  $0 summarize <receipt_file> [limit]  Show top violations (default limit=10)
  $0 format-github <receipt_file>      Format receipt for GitHub job summary
  $0 check-all <directory>             Check all receipts in directory

Examples:
  # Parse a single receipt
  $0 parse .yawl/receipts/guard-receipt.json

  # Check if validation passed
  if $0 check .yawl/receipts/guard-receipt.json; then
    echo "Validation passed"
  fi

  # Show violations in GitHub Actions
  echo "$($0 format-github .yawl/receipts/guard-receipt.json)" >> \$GITHUB_STEP_SUMMARY

  # Check all receipts
  $0 check-all .yawl/receipts/

Exit Codes:
  0 = Success (GREEN or no violations)
  1 = Transient error (file not found, parse error)
  2 = Violations found (RED status)

USAGE
            return 1
            ;;
    esac
}

# Run main with all arguments
main "$@"
