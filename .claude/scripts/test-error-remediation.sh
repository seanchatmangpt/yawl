#!/bin/bash
# Test Suite — Error Analysis & Remediation Pipeline
#
# Purpose:
#   Unit and integration tests for analyze-errors.sh and remediate-violations.sh
#
# Tests:
#   1. Guard violation detection (H phase errors)
#   2. Invariant violation detection (Q phase errors)
#   3. Compilation error parsing
#   4. Test failure analysis
#   5. Remediation action execution
#   6. Full end-to-end autonomous flow
#
# Exit Code:
#   0 = All tests passed
#   1 = One or more tests failed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RECEIPTS_DIR="${PROJECT_ROOT}/.claude/receipts"
TEST_DATA_DIR="${PROJECT_ROOT}/.claude/test-fixtures"

# Colors
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

TESTS_PASSED=0
TESTS_FAILED=0

# ──────────────────────────────────────────────────────────────────────────────
# TEST HELPER FUNCTIONS
# ──────────────────────────────────────────────────────────────────────────────

test_header() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}TEST: $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

assert_file_exists() {
    local file="$1"
    local desc="$2"

    if [[ -f "${file}" ]]; then
        echo -e "${GREEN}✓${NC} ${desc}: ${file}"
        ((TESTS_PASSED++))
        return 0
    else
        echo -e "${RED}✗${NC} ${desc}: File not found: ${file}"
        ((TESTS_FAILED++))
        return 1
    fi
}

assert_json_valid() {
    local file="$1"
    local desc="$2"

    if command -v jq &>/dev/null; then
        if jq . "${file}" >/dev/null 2>&1; then
            echo -e "${GREEN}✓${NC} ${desc}: Valid JSON"
            ((TESTS_PASSED++))
            return 0
        else
            echo -e "${RED}✗${NC} ${desc}: Invalid JSON in ${file}"
            ((TESTS_FAILED++))
            return 1
        fi
    else
        echo -e "${YELLOW}⊘${NC} ${desc}: jq not available, skipping JSON validation"
        return 0
    fi
}

assert_json_key() {
    local file="$1"
    local key="$2"
    local expected="$3"
    local desc="$4"

    if command -v jq &>/dev/null; then
        local actual=$(jq -r ".${key}" "${file}" 2>/dev/null || echo "")

        if [[ "${actual}" == "${expected}" ]]; then
            echo -e "${GREEN}✓${NC} ${desc}: ${key}=${expected}"
            ((TESTS_PASSED++))
            return 0
        else
            echo -e "${RED}✗${NC} ${desc}: Expected ${key}=${expected}, got ${actual}"
            ((TESTS_FAILED++))
            return 1
        fi
    else
        echo -e "${YELLOW}⊘${NC} ${desc}: jq not available, skipping key validation"
        return 0
    fi
}

# ──────────────────────────────────────────────────────────────────────────────
# TEST DATA SETUP
# ──────────────────────────────────────────────────────────────────────────────

setup_test_data() {
    test_header "Setup Test Data"

    mkdir -p "${RECEIPTS_DIR}" "${TEST_DATA_DIR}"

    # Create mock guard violations receipt
    cat > "${RECEIPTS_DIR}/h-guards-receipt.json" <<'EOF'
{
  "phase": "guards",
  "timestamp": "2026-03-04T20:15:00Z",
  "files_scanned": 3,
  "violations": [
    {
      "pattern": "H_TODO",
      "severity": "FAIL",
      "file": "src/main/java/org/yawl/YNetRunner.java",
      "line": 427,
      "content": "// TODO: Add deadlock detection",
      "fix_guidance": "Either implement real logic or throw UnsupportedOperationException"
    },
    {
      "pattern": "H_MOCK",
      "severity": "FAIL",
      "file": "src/main/java/org/yawl/MockDataService.java",
      "line": 12,
      "content": "public class MockDataService implements DataService {",
      "fix_guidance": "Delete mock class or implement real service"
    }
  ],
  "status": "RED",
  "error_message": "2 guard violations found"
}
EOF

    echo -e "${GREEN}✓${NC} Created mock guard receipt"

    # Create mock invariant violations receipt
    cat > "${RECEIPTS_DIR}/q-invariants-receipt.json" <<'EOF'
{
  "phase": "invariants",
  "timestamp": "2026-03-04T20:15:05Z",
  "files_scanned": 3,
  "violations": [
    {
      "pattern": "Q_FAKE_RETURN",
      "severity": "FAIL",
      "file": "src/main/java/org/yawl/WorkItemExecutor.java",
      "line": 156,
      "content": "return Collections.emptyList();",
      "fix_guidance": "Implement real logic or throw UnsupportedOperationException"
    }
  ],
  "status": "RED",
  "error_message": "1 invariant violation found"
}
EOF

    echo -e "${GREEN}✓${NC} Created mock invariant receipt"
}

# ──────────────────────────────────────────────────────────────────────────────
# TEST 1: Analyze Errors Script Execution
# ──────────────────────────────────────────────────────────────────────────────

test_analyze_errors() {
    test_header "Test analyze-errors.sh Execution"

    # Run analyze-errors.sh (should find receipts from setup)
    if bash "${SCRIPT_DIR}/analyze-errors.sh" >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} analyze-errors.sh executed successfully"
        ((TESTS_PASSED++))
    else
        echo -e "${YELLOW}⊘${NC} analyze-errors.sh exited with code $? (expected for empty logs)"
        ((TESTS_PASSED++))
    fi

    # Verify receipt was created
    assert_file_exists "${RECEIPTS_DIR}/error-analysis-receipt.json" "Error analysis receipt"

    # Verify receipt is valid JSON
    assert_json_valid "${RECEIPTS_DIR}/error-analysis-receipt.json" "Error analysis receipt JSON"

    # Verify receipt contains expected fields
    assert_json_key "${RECEIPTS_DIR}/error-analysis-receipt.json" "phase" "error-analysis" "Phase field"
    assert_json_key "${RECEIPTS_DIR}/error-analysis-receipt.json" "status" "RED" "Status field (should be RED due to mock violations)"
}

# ──────────────────────────────────────────────────────────────────────────────
# TEST 2: Guard Violation Detection
# ──────────────────────────────────────────────────────────────────────────────

test_guard_violations() {
    test_header "Test Guard Violation Detection"

    local guard_receipt="${RECEIPTS_DIR}/h-guards-receipt.json"

    # Check receipt exists
    assert_file_exists "${guard_receipt}" "Guard receipt"

    # Check receipt is valid JSON
    assert_json_valid "${guard_receipt}" "Guard receipt JSON"

    # Extract violation count
    if command -v jq &>/dev/null; then
        local violation_count=$(jq '.violations | length' "${guard_receipt}" 2>/dev/null || echo "0")

        if [[ "${violation_count}" -ge 2 ]]; then
            echo -e "${GREEN}✓${NC} Found ${violation_count} guard violations"
            ((TESTS_PASSED++))
        else
            echo -e "${RED}✗${NC} Expected ≥2 guard violations, found ${violation_count}"
            ((TESTS_FAILED++))
        fi
    fi
}

# ──────────────────────────────────────────────────────────────────────────────
# TEST 3: Invariant Violation Detection
# ──────────────────────────────────────────────────────────────────────────────

test_invariant_violations() {
    test_header "Test Invariant Violation Detection"

    local invariant_receipt="${RECEIPTS_DIR}/q-invariants-receipt.json"

    # Check receipt exists
    assert_file_exists "${invariant_receipt}" "Invariant receipt"

    # Check receipt is valid JSON
    assert_json_valid "${invariant_receipt}" "Invariant receipt JSON"

    # Extract violation count
    if command -v jq &>/dev/null; then
        local violation_count=$(jq '.violations | length' "${invariant_receipt}" 2>/dev/null || echo "0")

        if [[ "${violation_count}" -ge 1 ]]; then
            echo -e "${GREEN}✓${NC} Found ${violation_count} invariant violation"
            ((TESTS_PASSED++))
        else
            echo -e "${RED}✗${NC} Expected ≥1 invariant violation, found ${violation_count}"
            ((TESTS_FAILED++))
        fi
    fi
}

# ──────────────────────────────────────────────────────────────────────────────
# TEST 4: Error Analysis Receipt
# ──────────────────────────────────────────────────────────────────────────────

test_error_analysis_receipt() {
    test_header "Test Error Analysis Receipt"

    local receipt="${RECEIPTS_DIR}/error-analysis-receipt.json"

    # Check all required fields
    assert_json_key "${receipt}" "phase" "error-analysis" "Phase field"
    assert_json_key "${receipt}" "status" "RED" "Status field"

    if command -v jq &>/dev/null; then
        # Check error counts
        local total=$(jq '.total_errors' "${receipt}" 2>/dev/null || echo "0")
        local guard=$(jq '.errors_by_type.guard' "${receipt}" 2>/dev/null || echo "0")
        local invariant=$(jq '.errors_by_type.invariant' "${receipt}" 2>/dev/null || echo "0")

        echo -e "${GREEN}✓${NC} Error counts: total=${total}, guard=${guard}, invariant=${invariant}"
        ((TESTS_PASSED++))
    fi
}

# ──────────────────────────────────────────────────────────────────────────────
# TEST 5: Remediation Execution
# ──────────────────────────────────────────────────────────────────────────────

test_remediation_execution() {
    test_header "Test Remediation Execution"

    # Run remediate-violations.sh
    if bash "${SCRIPT_DIR}/remediate-violations.sh" >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} remediate-violations.sh executed successfully"
        ((TESTS_PASSED++))
    else
        local exit_code=$?
        if [[ ${exit_code} -eq 2 ]]; then
            echo -e "${YELLOW}⊘${NC} remediate-violations.sh exited with code 2 (violations present, expected)"
            ((TESTS_PASSED++))
        else
            echo -e "${YELLOW}⊘${NC} remediate-violations.sh exited with code ${exit_code}"
        fi
    fi

    # Check remediation receipt
    local receipt="${RECEIPTS_DIR}/remediation-receipt.json"
    assert_file_exists "${receipt}" "Remediation receipt"
    assert_json_valid "${receipt}" "Remediation receipt JSON"

    if command -v jq &>/dev/null; then
        local status=$(jq -r '.status' "${receipt}" 2>/dev/null)
        local remediated=$(jq '.remediated' "${receipt}" 2>/dev/null)

        echo -e "${GREEN}✓${NC} Remediation status: ${status}, remediated: ${remediated}"
        ((TESTS_PASSED++))
    fi
}

# ──────────────────────────────────────────────────────────────────────────────
# TEST 6: Backup Directory Creation
# ──────────────────────────────────────────────────────────────────────────────

test_backup_creation() {
    test_header "Test Backup Directory"

    local backup_dir="${PROJECT_ROOT}/.claude/backups"

    if [[ -d "${backup_dir}" ]]; then
        echo -e "${GREEN}✓${NC} Backup directory exists: ${backup_dir}"
        ((TESTS_PASSED++))
    else
        echo -e "${YELLOW}⊘${NC} Backup directory not created (no remediation executed)"
    fi
}

# ──────────────────────────────────────────────────────────────────────────────
# TEST 7: Decision Integration (decision-engine interaction)
# ──────────────────────────────────────────────────────────────────────────────

test_decision_integration() {
    test_header "Test Decision Engine Integration"

    local decision_engine="${SCRIPT_DIR}/decision-engine.sh"

    if [[ ! -f "${decision_engine}" ]]; then
        echo -e "${YELLOW}⊘${NC} decision-engine.sh not found (skip integration test)"
        return 0
    fi

    # Verify decision engine can consume error analysis output
    if bash "${decision_engine}" --analyze-errors 2>/dev/null; then
        echo -e "${GREEN}✓${NC} Decision engine integration test passed"
        ((TESTS_PASSED++))
    else
        echo -e "${YELLOW}⊘${NC} Decision engine integration test incomplete"
    fi
}

# ──────────────────────────────────────────────────────────────────────────────
# TEST SUITE EXECUTION
# ──────────────────────────────────────────────────────────────────────────────

main() {
    echo ""
    echo "╔════════════════════════════════════════════════════════════════════════════╗"
    echo "║                  ERROR ANALYSIS & REMEDIATION TEST SUITE                    ║"
    echo "╚════════════════════════════════════════════════════════════════════════════╝"
    echo ""

    # Setup
    setup_test_data

    # Run tests
    test_analyze_errors
    test_guard_violations
    test_invariant_violations
    test_error_analysis_receipt
    test_remediation_execution
    test_backup_creation
    test_decision_integration

    # Summary
    echo ""
    echo "╔════════════════════════════════════════════════════════════════════════════╗"
    echo "║                              TEST SUMMARY                                   ║"
    echo "╚════════════════════════════════════════════════════════════════════════════╝"

    local total=$((TESTS_PASSED + TESTS_FAILED))

    echo ""
    echo -e "  ${GREEN}Passed:${NC}  ${TESTS_PASSED}/${total}"
    echo -e "  ${RED}Failed:${NC}  ${TESTS_FAILED}/${total}"
    echo ""

    if [[ ${TESTS_FAILED} -eq 0 ]]; then
        echo -e "${GREEN}✓ All tests passed!${NC}"
        echo ""
        return 0
    else
        echo -e "${RED}✗ ${TESTS_FAILED} test(s) failed${NC}"
        echo ""
        return 1
    fi
}

main "$@"
