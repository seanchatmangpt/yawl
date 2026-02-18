#!/bin/bash
set -euo pipefail

# YAWL Maven Test Skill
# Provides convenient shortcuts for test execution and coverage analysis
# Usage: /yawl-test [--module=MODULE] [--coverage] [--verbose] [--no-dx]
#
# By default uses scripts/dx.sh for fast feedback: auto-detects changed modules,
# runs with agent-dx profile (2C parallelism, fail-fast, no JaCoCo overhead).
# Use --coverage to enable JaCoCo, or --no-dx for full Maven commands.

MODULE="${MODULE:-}"
COVERAGE="${COVERAGE:-false}"
VERBOSE="${VERBOSE:-false}"
USE_DX="${USE_DX:-true}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print status messages
log_info() { echo -e "${GREEN}[test]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[test]${NC} $*"; }
log_error() { echo -e "${RED}[test]${NC} $*"; exit 1; }
log_test() { echo -e "${BLUE}[test]${NC} $*"; }

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --module=*) MODULE="${1#--module=}"; shift ;;
    --module) MODULE="$2"; shift 2 ;;
    --coverage) COVERAGE=true; USE_DX=false; shift ;;
    --verbose) VERBOSE=true; shift ;;
    --no-dx) USE_DX=false; shift ;;
    *) shift ;;
  esac
done

# Fast path: delegate to dx.sh (default)
if [[ "$USE_DX" == "true" && "$COVERAGE" == "false" ]]; then
  DX_SCRIPT="${REPO_ROOT}/scripts/dx.sh"
  if [[ -x "$DX_SCRIPT" ]]; then
    DX_ARGS=""
    if [[ -n "$MODULE" ]]; then
      DX_ARGS="-pl $MODULE"
    fi
    [[ "$VERBOSE" == "true" ]] && export DX_VERBOSE=1
    log_test "Fast path: bash scripts/dx.sh $DX_ARGS"
    exec bash "$DX_SCRIPT" $DX_ARGS
  fi
fi

# Standard path: full Maven test with optional coverage
TEST_CMD="mvn clean test"

# Add module flag if specified
if [ -n "$MODULE" ]; then
  TEST_CMD="$TEST_CMD -pl $MODULE"
fi

# Add verbose flag
if [ "$VERBOSE" = "false" ]; then
  TEST_CMD="$TEST_CMD -q"
fi

log_test "Test Suite Execution"
log_info "Running: $TEST_CMD"

# Execute tests
if eval "$TEST_CMD"; then
  TEST_PASSED=true
  log_info "All tests passed"
else
  TEST_PASSED=false
  log_error "Tests failed"
fi

# Generate coverage report if requested
if [ "$COVERAGE" = "true" ] && [ "$TEST_PASSED" = "true" ]; then
  log_info "Generating JaCoCo coverage report..."

  COVERAGE_CMD="mvn jacoco:report"
  if [ -n "$MODULE" ]; then
    COVERAGE_CMD="$COVERAGE_CMD -pl $MODULE"
  fi

  if eval "$COVERAGE_CMD"; then
    log_info "Coverage report generated"
    if [ -f "target/site/jacoco/index.html" ]; then
      log_warn "View coverage report at: target/site/jacoco/index.html"
    fi
  else
    log_warn "Coverage report generation skipped (JaCoCo not configured)"
  fi
fi

exit 0
