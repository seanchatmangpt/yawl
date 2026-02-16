#!/bin/bash
set -euo pipefail

# YAWL Maven Test Skill
# Provides convenient shortcuts for test execution and coverage analysis
# Usage: /yawl-test [--module=MODULE] [--coverage] [--verbose]

MODULE="${MODULE:-}"
COVERAGE="${COVERAGE:-false}"
VERBOSE="${VERBOSE:-false}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print status messages
log_info() { echo -e "${GREEN}ℹ️${NC} $*"; }
log_warn() { echo -e "${YELLOW}⚠️${NC} $*"; }
log_error() { echo -e "${RED}❌${NC} $*"; exit 1; }
log_test() { echo -e "${BLUE}🧪${NC} $*"; }

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --module=*) MODULE="${1#--module=}"; shift ;;
    --module) MODULE="$2"; shift 2 ;;
    --coverage) COVERAGE=true; shift ;;
    --verbose) VERBOSE=true; shift ;;
    *) shift ;;
  esac
done

# Build Maven command
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
  log_info "✅ All tests passed"
else
  TEST_PASSED=false
  log_error "❌ Tests failed"
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
