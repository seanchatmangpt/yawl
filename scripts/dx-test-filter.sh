#!/usr/bin/env bash
# ==========================================================================
# dx-test-filter.sh — Quick test filtering
#
# Run specific tests without typing full Maven command.
#
# Usage:
#   bash scripts/dx-test-filter.sh TestClassName
#   bash scripts/dx-test-filter.sh "*RetryObservability*"
#   bash scripts/dx-test-filter.sh Test -pl yawl-stateless
#
# Environment:
#   DX_MODULE=""       Specify module (default: auto-detect from file changes)
#   DX_PATTERN=""      Specify test class pattern (first arg or env var)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Color codes
readonly C_RESET='\033[0m'
readonly C_BLUE='\033[94m'
readonly C_CYAN='\033[96m'
readonly C_YELLOW='\033[93m'

# Parse arguments
TEST_PATTERN="${1:-}"
EXPLICIT_MODULE="${DX_MODULE:-}"

# Handle -pl module specification
if [[ "$1" == "-pl" ]]; then
    EXPLICIT_MODULE="$2"
    TEST_PATTERN="${3:-}"
fi

if [[ -z "$TEST_PATTERN" ]]; then
    printf "${C_YELLOW}Usage:${C_RESET} dx-test-filter <pattern> [-pl module]\n"
    printf "${C_CYAN}Examples:${C_RESET}\n"
    printf "  bash scripts/dx-test-filter.sh TestYStatelessMarking\n"
    printf "  bash scripts/dx-test-filter.sh '*Marking*'\n"
    printf "  bash scripts/dx-test-filter.sh 'Test*' -pl yawl-stateless\n"
    exit 1
fi

# Determine module
MODULE_ARGS=""
if [[ -n "$EXPLICIT_MODULE" ]]; then
    MODULE_ARGS="-pl $EXPLICIT_MODULE -amd"
    printf "${C_BLUE}Target:${C_RESET} module=$EXPLICIT_MODULE | pattern=$TEST_PATTERN\n"
else
    printf "${C_BLUE}Target:${C_RESET} pattern=$TEST_PATTERN (all modules)\n"
fi

# Run tests with pattern matching
printf "${C_CYAN}Running tests...${C_RESET}\n\n"
mvn test \
    -P agent-dx \
    $MODULE_ARGS \
    -Dtest="$TEST_PATTERN" \
    -DfailIfNoTests=false \
    --fail-fast

exit $?
