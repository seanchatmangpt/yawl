#!/bin/bash
# Q-Validate Script: Invariants Phase Validator
# Validates generated code against Q-phase invariants before git commit
# Exit 0: GREEN (all invariants satisfied)
# Exit 2: RED (violations detected)

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RECEIPT_DIR="${SCRIPT_DIR}/.claude/receipts"
RECEIPT_FILE="${RECEIPT_DIR}/invariant-receipt.json"
EMIT_DIR="${SCRIPT_DIR}/emit"

# Helper functions
log_info() { echo -e "\033[0;32m[Q-VALIDATE] $1\033[0m"; }
log_error() { echo -e "\033[0;31m[Q-VALIDATE] $1\033[0m"; }
log_warning() { echo -e "\033[1;33m[Q-VALIDATE] $1\033[0m"; }
log_step() { echo -e "\033[1;36m[Q-VALIDATE] $1\033[0m"; }

# Validation functions
validate_q1() {
    log_step "Checking Q1: real_impl ∨ throw..."
    
    violations=0
    violations_file="/tmp/q1_violations.txt"
    
    # Empty void methods
    grep -r -n --include="*.java" \
        -E "^\s*(public|private|protected)\s+(void|synchronized|static)\s+\w+\s*\([^)]*\)\s*\{\s*\}" \
        "${EMIT_DIR}" 2>/dev/null || true | tee -a "$violations_file"
    
    # Simple return statements
    grep -r -n --include="*.java" \
        -E "public\s+(String|List|Map|Integer|Boolean|Set)\s+\w+\s*\([^)]*\)\s*\{\s*return\s+(null|\"\"|Collections\.(emptyList|emptyMap|emptySet)|new\s+(HashMap|ArrayList|HashSet)\(\))\s*;\s*\}" \
        "${EMIT_DIR}" 2>/dev/null || true | tee -a "$violations_file"
    
    violations=$(grep -c '^' "$violations_file" 2>/dev/null || echo 0)
    
    if [ "$violations" -gt 0 ]; then
        log_error "❌ Found $violations Q1 violations (empty/stub methods)"
        return 1
    else
        log_info "✅ No Q1 violations found"
        return 0
    fi
}

validate_q2() {
    log_step "Checking Q2: ¬mock (no mock/stub/fake objects)..."
    
    violations=0
    violations_file="/tmp/q2_violations.txt"
    
    # Mock/stub/fake class declarations
    grep -r -n --include="*.java" \
        -E "^\s*(public|private|protected)?\s*(class|interface|enum)\s+(Mock|Stub|Fake|Demo)[A-Za-z0-9_]*\s*(extends|implements|\{)" \
        "${EMIT_DIR}" 2>/dev/null || true | tee -a "$violations_file"
    
    violations=$(grep -c '^' "$violations_file" 2>/dev/null || echo 0)
    
    if [ "$violations" -gt 0 ]; then
        log_error "❌ Found $violations Q2 violations (mock/stub objects)"
        return 1
    else
        log_info "✅ No Q2 violations found"
        return 0
    fi
}

validate_q3() {
    log_step "Checking Q3: ¬silent_fallback (no silent exception handling)..."
    
    violations=0
    violations_file="/tmp/q3_violations.txt"
    
    # Catch blocks returning fake data
    grep -r -n --include="*.java" \
        -E "catch\s*\([^)]+\)\s*\{[^}]*return\s+(null|\"\"|\{\}|Collections\.\w+|new\s+(HashMap|ArrayList|HashSet|LinkedList|TreeSet|TreeMap)|Arrays\.\w+|emptyList|emptyMap|emptySet|mockData|fakeData|testData|stubData|demoData)\s*;\s*\}" \
        "${EMIT_DIR}" 2>/dev/null || true | tee -a "$violations_file"
    
    violations=$(grep -c '^' "$violations_file" 2>/dev/null || echo 0)
    
    if [ "$violations" -gt 0 ]; then
        log_error "❌ Found $violations Q3 violations (silent fallbacks)"
        return 1
    else
        log_info "✅ No Q3 violations found"
        return 0
    fi
}

validate_q4() {
    log_step "Checking Q4: ¬lie (documentation-code consistency) [SEMI-IMPLEMENTED]..."
    
    violations=0
    violations_file="/tmp/q4_violations.txt"
    
    # Basic javadoc mismatches
    grep -r -n --include="*.java" \
        -E "/\*\*\s*@return[^*]*never[^*]*null[^*]*\*/\s*\w+\s*\([^)]*\)\s*\{\s*return\s+null\s*;\s*\}" \
        "${EMIT_DIR}" 2>/dev/null || true | tee -a "$violations_file"
    
    violations=$(grep -c '^' "$violations_file" 2>/dev/null || echo 0)
    
    if [ "$violations" -gt 0 ]; then
        log_error "❌ Found $violations Q4 violations (documentation mismatches)"
        return 1
    else
        log_info "✅ No Q4 violations found (basic checks only)"
        return 0
    fi
}

# Main function
main() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════╗"
    echo "║             Q-VALIDATE: INVARIANTS PHASE                  ║"
    echo "╚════════════════════════════════════════════════════════════╝"
    echo ""
    
    # Check if emit directory exists
    if [ ! -d "${EMIT_DIR}" ]; then
        log_warning "No emit directory found at ${EMIT_DIR}"
        mkdir -p "$RECEIPT_DIR"
        cat > "$RECEIPT_FILE" << JSON_EOF
{
  "phase": "invariants",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "code_directory": "${EMIT_DIR}",
  "java_files_scanned": 0,
  "total_violations": 0,
  "invariant_results": {
    "Q1_real_impl_or_throw": "SKIPPED",
    "Q2_no_mock_objects": "SKIPPED", 
    "Q3_no_silent_fallback": "SKIPPED",
    "Q4_no_lies": "SKIPPED"
  },
  "status": "GREEN",
  "message": "No code to validate",
  "exit_code": 0
}
JSON_EOF
        log_info "✅ No code to validate. Proceeding to Ω phase."
        exit 0
    fi
    
    log_info "Validating code in: ${EMIT_DIR}"
    
    # Clean up
    rm -f /tmp/q[1-4]_violations.txt
    
    # Run validations
    q1_pass=0 q2_pass=0 q3_pass=0 q4_pass=0
    q1_violations=0 q2_violations=0 q3_violations=0 q4_violations=0
    
    if ! validate_q1; then q1_pass=1; q1_violations=$(grep -c '^' /tmp/q1_violations.txt 2>/dev/null || echo 0); fi
    if ! validate_q2; then q2_pass=1; q2_violations=$(grep -c '^' /tmp/q2_violations.txt 2>/dev/null || echo 0); fi
    if ! validate_q3; then q3_pass=1; q3_violations=$(grep -c '^' /tmp/q3_violations.txt 2>/dev/null || echo 0); fi
    if ! validate_q4; then q4_pass=1; q4_violations=$(grep -c '^' /tmp/q4_violations.txt 2>/dev/null || echo 0); fi
    
    # Generate receipt
    total_violations=$((q1_violations + q2_violations + q3_violations + q4_violations))
    q1_status=$([ "$q1_pass" -eq 0 ] && echo "PASS" || echo "FAIL")
    q2_status=$([ "$q2_pass" -eq 0 ] && echo "PASS" || echo "FAIL")
    q3_status=$([ "$q3_pass" -eq 0 ] && echo "PASS" || echo "FAIL")
    q4_status=$([ "$q4_pass" -eq 0 ] && echo "PASS" || echo "FAIL")
    status=$([ "$total_violations" -eq 0 ] && echo "GREEN" || echo "RED")
    message=$([ "$total_violations" -eq 0 ] && echo "All invariants satisfied" || echo "Invariant violations detected")
    exit_code=$([ "$total_violations" -eq 0 ] && echo 0 || echo 2)
    
    mkdir -p "$RECEIPT_DIR"
    
    cat > "$RECEIPT_FILE" << JSON_EOF
{
  "phase": "invariants",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "code_directory": "${EMIT_DIR}",
  "java_files_scanned": $(find "${EMIT_DIR}" -name "*.java" | wc -l),
  "total_violations": ${total_violations},
  "invariant_results": {
    "Q1_real_impl_or_throw": "${q1_status} (${q1_violations} violations)",
    "Q2_no_mock_objects": "${q2_status} (${q2_violations} violations)",
    "Q3_no_silent_fallback": "${q3_status} (${q3_violations} violations)",
    "Q4_no_lies": "${q4_status} (${q4_violations} violations)"
  },
  "status": "${status}",
  "message": "${message}",
  "exit_code": ${exit_code}
}
JSON_EOF
    
    # Summary report
    echo ""
    echo "══════════════════════════════════════════════════════════════"
    echo "                 Q-VALIDATE SUMMARY"
    echo "══════════════════════════════════════════════════════════════"
    echo ""
    
    if [ "$total_violations" -eq 0 ]; then
        echo -e "  ✅ All invariants satisfied!"
        echo ""
        echo "  Q1: real_impl ∨ throw → PASS"
        echo "  Q2: ¬mock objects → PASS" 
        echo "  Q3: ¬silent fallback → PASS"
        echo "  Q4: ¬lie (documentation consistency) → PASS (basic checks)"
        echo ""
        log_info "Proceeding to Ω (Git) phase..."
        exit 0
    else
        echo -e "  ❌ Total violations: ${total_violations}"
        echo ""
        [ "$q1_violations" -gt 0 ] && echo "  Q1: ${q1_violations} empty/stub methods (real∨throw)"
        [ "$q2_violations" -gt 0 ] && echo "  Q2: ${q2_violations} mock/stub objects detected"
        [ "$q3_violations" -gt 0 ] && echo "  Q3: ${q3_violations} silent fallback patterns"
        [ "$q4_violations" -gt 0 ] && echo "  Q4: ${q4_violations} documentation mismatches"
        echo ""
        echo "  See receipt: ${RECEIPT_FILE}"
        echo ""
        log_error "Fix violations before committing"
        exit 2
    fi
}

main "$@"
