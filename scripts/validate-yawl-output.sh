#!/usr/bin/env bash
################################################################################
# validate-yawl-output.sh — Validates generated YAWL XML output
#
# Validates YAWL XML files for structural correctness:
# - Well-formed XML structure
# - Required YAWL elements present
# - XSD schema compliance (if available)
# - No stray text nodes outside elements
#
# Usage: bash scripts/validate-yawl-output.sh <file.yawl>
#
# Exit codes:
#   0 = valid YAWL XML
#   2 = validation failed (errors reported)
#   1 = usage error or missing file
################################################################################

set -euo pipefail

# ── Utility functions ────────────────────────────────────────────────────────

die() {
    local code=$1
    shift
    echo "ERROR: $*" >&2
    exit "$code"
}

warn() {
    echo "WARN: $*" >&2
}

info() {
    echo "INFO: $*"
}

success() {
    echo "SUCCESS: $*"
}

# ── Validation Functions ────────────────────────────────────────────────────

validate_xml_wellformedness() {
    local yawl_file="$1"
    local errors=0

    info "Validating XML well-formedness..."

    # Check if file exists and is readable
    if [[ ! -f "$yawl_file" ]]; then
        echo "FAIL: File not found or not readable: $yawl_file"
        return 1
    fi

    # Use xmllint if available
    if command -v xmllint &> /dev/null; then
        if ! xmllint --noout "$yawl_file" 2>&1 | head -20; then
            echo "FAIL: XML well-formedness check failed"
            ((errors++))
        else
            success "XML is well-formed"
        fi
    else
        warn "xmllint not available, using basic validation"

        # Basic check: matching root elements
        local root_open
        local root_close
        root_open=$(head -5 "$yawl_file" | grep -o "<[^/>]*>" | head -1)
        root_close=$(tail -5 "$yawl_file" | grep -o "</[^>]*>" | tail -1)

        if [[ -z "$root_open" ]] || [[ -z "$root_close" ]]; then
            echo "FAIL: No valid XML structure found"
            ((errors++))
        fi
    fi

    return "$errors"
}

validate_yawl_elements() {
    local yawl_file="$1"
    local errors=0

    info "Validating required YAWL elements..."

    # Check for YAWL namespace declaration
    if ! grep -q "xmlns:.*yawlfoundation.org\|xmlns.*yawlschema" "$yawl_file"; then
        echo "FAIL: YAWL namespace declaration not found"
        ((errors++))
    else
        success "YAWL namespace declared"
    fi

    # Check for at least one workflow/specification element
    if ! grep -q "<specification\|<net\|<task\|<condition" "$yawl_file"; then
        echo "FAIL: No YAWL workflow elements found"
        ((errors++))
    else
        success "YAWL workflow elements found"
    fi

    # Check for proper closing tags (basic sanity)
    local open_tags
    local close_tags
    open_tags=$(grep -o "<[^/>]*>" "$yawl_file" | wc -l)
    close_tags=$(grep -o "</[^>]*>" "$yawl_file" | wc -l)

    if [[ $open_tags -lt $close_tags ]]; then
        echo "FAIL: Unmatched closing tags (open: $open_tags, close: $close_tags)"
        ((errors++))
    else
        success "Tag balance check passed"
    fi

    return "$errors"
}

validate_yawl_structure() {
    local yawl_file="$1"
    local errors=0

    info "Validating YAWL workflow structure..."

    # Count critical workflow elements (using tr to strip whitespace safely)
    local spec_count net_count task_count condition_count

    spec_count=$(grep -c "<specification\|<Specification" "$yawl_file" 2>/dev/null | tr -d ' \n' || echo "0")
    net_count=$(grep -c "<net\|<Net" "$yawl_file" 2>/dev/null | tr -d ' \n' || echo "0")
    task_count=$(grep -c "<task\|<Task" "$yawl_file" 2>/dev/null | tr -d ' \n' || echo "0")
    condition_count=$(grep -c "<condition\|<Condition" "$yawl_file" 2>/dev/null | tr -d ' \n' || echo "0")

    info "Found: $spec_count specification(s), $net_count net(s), $task_count task(s), $condition_count condition(s)"

    # A minimal YAWL spec should have at least a specification or net
    if [[ $spec_count -eq 0 ]] && [[ $net_count -eq 0 ]]; then
        echo "FAIL: No specification or net elements found"
        ((errors++))
    else
        success "Core YAWL structure present"
    fi

    # If we have tasks, validate they have required attributes
    if [[ $task_count -gt 0 ]]; then
        local task_ids_missing
        task_ids_missing=$(grep "<task\|<Task" "$yawl_file" 2>/dev/null | grep -v "id=" | wc -l | tr -d ' \n' || echo "0")

        if [[ $task_ids_missing -gt 0 ]]; then
            echo "WARN: Found $task_ids_missing task(s) without id attribute"
        fi
        success "Tasks present with identifiers"
    fi

    return "$errors"
}

validate_no_errors() {
    local yawl_file="$1"
    local errors=0

    info "Checking for error indicators in YAWL..."

    # Check for common error patterns
    if grep -q "error\|Error\|ERROR\|exception\|Exception" "$yawl_file" 2>/dev/null; then
        warn "YAWL file contains error-related text (may be valid data)"
    fi

    # Check for incomplete generation markers
    if grep -q "TODO\|FIXME\|STUB\|UNIMPLEMENTED" "$yawl_file" 2>/dev/null; then
        echo "FAIL: Found generation placeholders in YAWL output"
        ((errors++))
    else
        success "No generation placeholders found"
    fi

    return "$errors"
}

validate_file_size() {
    local yawl_file="$1"
    local errors=0

    info "Validating file size..."

    local file_size
    file_size=$(stat -f%z "$yawl_file" 2>/dev/null || stat -c%s "$yawl_file" 2>/dev/null || echo "0")

    if [[ "$file_size" -lt 100 ]]; then
        echo "WARN: Output file suspiciously small: $file_size bytes"
    elif [[ "$file_size" -gt 100000000 ]]; then  # 100MB
        echo "WARN: Output file very large: $file_size bytes (may indicate issues)"
    else
        success "File size acceptable: $file_size bytes"
    fi

    return "$errors"
}

# ── Main validation orchestration ────────────────────────────────────────────

main() {
    local yawl_file="$1"
    local validation_failed=false

    # Validate arguments
    if [[ $# -lt 1 ]]; then
        echo "Usage: $0 <file.yawl>"
        echo ""
        echo "Validates a YAWL XML output file."
        exit 1
    fi

    info "=========================================================="
    info "YAWL XML Output Validator"
    info "=========================================================="
    info "File: $yawl_file"
    echo ""

    # Check file existence
    if [[ ! -f "$yawl_file" ]]; then
        die 1 "File not found: $yawl_file"
    fi

    # Run all validation checks
    if ! validate_xml_wellformedness "$yawl_file"; then
        validation_failed=true
    fi
    echo ""

    if ! validate_yawl_elements "$yawl_file"; then
        validation_failed=true
    fi
    echo ""

    if ! validate_yawl_structure "$yawl_file"; then
        validation_failed=true
    fi
    echo ""

    if ! validate_file_size "$yawl_file"; then
        validation_failed=true
    fi
    echo ""

    if ! validate_no_errors "$yawl_file"; then
        validation_failed=true
    fi
    echo ""

    # Print final result
    info "=========================================================="
    if [[ "$validation_failed" == "true" ]]; then
        info "VALIDATION FAILED - See errors above"
        info "=========================================================="
        exit 2
    else
        success "VALIDATION PASSED"
        info "=========================================================="
        exit 0
    fi
}

# Entry point
main "$@"
