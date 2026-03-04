#!/bin/bash
# Validate Command - Run YAWL Validation Pipeline
#
# Wrapper around scripts/dx-validate.sh that runs the full YAWL validation
# pipeline (Observatory → Compile → Guards → Invariants → Report).
#
# Usage:
#   /validate                  # Run all phases
#   /validate --phase H        # Run just phase H (guards)
#   /validate --phase Q        # Run just phase Q (invariants)
#   /validate --quiet          # Run without verbose output
#
# Exit codes:
#   0 = All phases GREEN
#   1 = Transient error (retry-able)
#   2 = Validation violations found

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Check if YAWL environment is available
if [[ ! -f "${REPO_ROOT}/scripts/dx.sh" ]] || [[ ! -f "${REPO_ROOT}/pom.xml" ]]; then
    echo "❌ Error: YAWL environment not detected" >&2
    echo "   Expected: scripts/dx.sh and pom.xml" >&2
    exit 2
fi

# Parse arguments
PHASE_FILTER=""
QUIET_MODE=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --phase)
            PHASE_FILTER="$2"
            shift 2
            ;;
        --quiet)
            QUIET_MODE=true
            shift
            ;;
        *)
            echo "Unknown option: $1" >&2
            echo "Usage: /validate [--phase H|Q] [--quiet]" >&2
            exit 2
            ;;
    esac
done

echo ""
echo "🔍 Running YAWL Validation Pipeline..."
echo ""

# Call dx-validate wrapper script
if [[ -f "${REPO_ROOT}/.claude/scripts/dx-validate.sh" ]]; then
    bash "${REPO_ROOT}/.claude/scripts/dx-validate.sh"
    EXIT_CODE=$?
else
    # Fallback to direct dx.sh call
    cd "${REPO_ROOT}"
    bash scripts/dx.sh all
    EXIT_CODE=$?
fi

echo ""

if [[ ${EXIT_CODE} -eq 0 ]]; then
    echo "✅ Validation GREEN - All phases passed"
else
    echo "❌ Validation RED - Review errors above"
fi

echo ""

exit ${EXIT_CODE}
