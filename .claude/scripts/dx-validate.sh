#!/bin/bash
# DX Validation Wrapper Script
#
# Runs the complete YAWL validation pipeline (dx.sh all) and captures output
# to a structured receipt file for analysis and tracking.
#
# Pipeline phases:
#   Ψ (Observe):   Observatory scans codebase, generates facts
#   Λ (Compile):   Maven compilation
#   H (Guards):    Hyper-standards validation (7 forbidden patterns)
#   Q (Invariants): Real implementation checks
#   Ω (Report):    Summary reporting
#
# Output:
#   Returns exit code from dx.sh all (0 = all green, 2 = violations found)
#   Writes receipt to .claude/receipts/dx-validate-receipt.json

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RECEIPTS_DIR="${REPO_ROOT}/.claude/receipts"
RECEIPT_FILE="${RECEIPTS_DIR}/dx-validate-receipt.json"

# Create receipts directory
mkdir -p "${RECEIPTS_DIR}"

# ──────────────────────────────────────────────────────────────────────────────
# RUN DX.SH VALIDATION PIPELINE
# ──────────────────────────────────────────────────────────────────────────────

VALIDATION_LOG="/tmp/dx-validate-$$.log"
VALIDATION_START=$(date +%s%N)
VALIDATION_EXIT=0

echo "🚀 Starting YAWL validation pipeline..."
echo ""

cd "${REPO_ROOT}"

# Run dx.sh all with logging
if bash scripts/dx.sh all > "${VALIDATION_LOG}" 2>&1; then
    VALIDATION_EXIT=0
else
    VALIDATION_EXIT=$?
fi

VALIDATION_END=$(date +%s%N)
VALIDATION_DURATION=$(( (VALIDATION_END - VALIDATION_START) / 1000000 ))  # Convert to milliseconds

# ──────────────────────────────────────────────────────────────────────────────
# PARSE VALIDATION OUTPUT & BUILD RECEIPT
# ──────────────────────────────────────────────────────────────────────────────

# Extract phase results from log output
PHASE_PSI_OK=$(grep -q "Ψ" "${VALIDATION_LOG}" && echo "true" || echo "false")
PHASE_LAMBDA_OK=$(grep -q "Λ" "${VALIDATION_LOG}" && echo "true" || echo "false")
PHASE_H_OK=$(grep -q "✅ Guard validation" "${VALIDATION_LOG}" && echo "true" || echo "false")
PHASE_Q_OK=$(grep -q "✅ Invariant validation" "${VALIDATION_LOG}" && echo "true" || echo "false")
PHASE_OMEGA_OK=$(grep -q "Report" "${VALIDATION_LOG}" && echo "true" || echo "false")

# Count violations
GUARD_VIOLATIONS=$(grep -c "H_\(TODO\|MOCK\|STUB\|EMPTY\|FALLBACK\|LIE\|SILENT\)" "${VALIDATION_LOG}" || echo "0")
INVARIANT_VIOLATIONS=$(grep -c "real_impl ∨ throw" "${VALIDATION_LOG}" || echo "0")

# Overall status
if [[ ${VALIDATION_EXIT} -eq 0 ]]; then
    OVERALL_STATUS="GREEN"
else
    OVERALL_STATUS="RED"
fi

# ──────────────────────────────────────────────────────────────────────────────
# WRITE RECEIPT FILE (JSON)
# ──────────────────────────────────────────────────────────────────────────────

cat > "${RECEIPT_FILE}" << EOF
{
  "phase": "dx-validate",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "duration_ms": ${VALIDATION_DURATION},
  "status": "${OVERALL_STATUS}",
  "exit_code": ${VALIDATION_EXIT},
  "pipeline": {
    "psi": {
      "name": "Observe (Observatory)",
      "status": "${PHASE_PSI_OK}"
    },
    "lambda": {
      "name": "Compile (Maven)",
      "status": "${PHASE_LAMBDA_OK}"
    },
    "h": {
      "name": "Guards (Hyper-standards)",
      "status": "${PHASE_H_OK}",
      "violations": ${GUARD_VIOLATIONS}
    },
    "q": {
      "name": "Invariants",
      "status": "${PHASE_Q_OK}",
      "violations": ${INVARIANT_VIOLATIONS}
    },
    "omega": {
      "name": "Report",
      "status": "${PHASE_OMEGA_OK}"
    }
  },
  "log_file": "${VALIDATION_LOG}"
}
EOF

# ──────────────────────────────────────────────────────────────────────────────
# DISPLAY SUMMARY & SAVE LOG
# ──────────────────────────────────────────────────────────────────────────────

echo ""
echo "═══════════════════════════════════════════════════════════════════════════"
echo "📊 YAWL Validation Summary"
echo "───────────────────────────────────────────────────────────────────────────"
echo "Status: ${OVERALL_STATUS}"
echo "Duration: ${VALIDATION_DURATION}ms"
echo ""
echo "Pipeline Phases:"
echo "  Ψ (Observe):    $([[ "${PHASE_PSI_OK}" == "true" ]] && echo "✅" || echo "❌")"
echo "  Λ (Compile):    $([[ "${PHASE_LAMBDA_OK}" == "true" ]] && echo "✅" || echo "❌")"
echo "  H (Guards):     $([[ "${PHASE_H_OK}" == "true" ]] && echo "✅" || echo "❌") (${GUARD_VIOLATIONS} violations)"
echo "  Q (Invariants): $([[ "${PHASE_Q_OK}" == "true" ]] && echo "✅" || echo "❌") (${INVARIANT_VIOLATIONS} violations)"
echo "  Ω (Report):     $([[ "${PHASE_OMEGA_OK}" == "true" ]] && echo "✅" || echo "❌")"
echo ""
echo "Receipt: ${RECEIPT_FILE}"
echo "Log: ${VALIDATION_LOG}"
echo "═══════════════════════════════════════════════════════════════════════════"
echo ""

# If validation failed, show relevant error output
if [[ ${VALIDATION_EXIT} -ne 0 ]]; then
    echo "❌ Validation failed. Last 30 lines of log:"
    echo "───────────────────────────────────────────────────────────────────────────"
    tail -30 "${VALIDATION_LOG}" || true
    echo ""
fi

exit ${VALIDATION_EXIT}
