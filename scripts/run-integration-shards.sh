#!/usr/bin/env bash
# ==========================================================================
# run-integration-shards.sh - Execute integration tests by database type
#
# Usage:
#   bash scripts/run-integration-shards.sh [database-type]
#
# Examples:
#   bash scripts/run-integration-shards.sh          # Run all integration tests
#   bash scripts/run-integration-shards.sh h2       # Run H2 integration tests
#   bash scripts/run-integration-shards.sh postgres # Run PostgreSQL tests
#   bash scripts/run-integration-shards.sh mysql    # Run MySQL tests
#
# Database types are tagged via @Tag("integration-h2") etc.
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Parse arguments
DB_TYPE="${1:-all}"

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_RESET='\033[0m'

# Map database type to Maven profile and JUnit tag
case "$DB_TYPE" in
    h2)
        PROFILE="integration-h2"
        TAG="integration-h2"
        ;;
    postgres)
        PROFILE="integration-postgres"
        TAG="integration-postgres"
        ;;
    mysql)
        PROFILE="integration-mysql"
        TAG="integration-mysql"
        ;;
    all)
        PROFILE="integration"
        TAG="integration"
        ;;
    *)
        echo "Unknown database type: $DB_TYPE"
        echo "Valid options: h2, postgres, mysql, all"
        exit 1
        ;;
esac

echo ""
printf "${C_CYAN}Running ${DB_TYPE} integration tests${C_RESET}\n"

START_TIME=$(date +%s)

# Run integration tests with the appropriate profile
mvn verify \
    -P "$PROFILE" \
    -Dmaven.test.skip=false \
    -DskipUnitTests=true \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Dtest="**/*IT" \
    -DexcludedGroups="stress,breaking-point" \
    -q

EXIT_CODE=$?

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

if [[ $EXIT_CODE -eq 0 ]]; then
    printf "${C_GREEN}✓ ${DB_TYPE} integration tests passed (${ELAPSED}s)${C_RESET}\n"
else
    printf "${C_RED}✗ ${DB_TYPE} integration tests failed (${ELAPSED}s)${C_RESET}\n"
fi

exit $EXIT_CODE
