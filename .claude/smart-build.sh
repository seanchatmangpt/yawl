#!/bin/bash
set -euo pipefail

# YAWL Smart Build Script
# Intelligently detects what changed and runs appropriate Maven build
# Usage: ./smart-build.sh [--force] [--parallel] [--module=MODULE]

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Logging functions
log_info() { echo -e "${GREEN}✓${NC} $*"; }
log_warn() { echo -e "${YELLOW}⚠${NC} $*"; }
log_error() { echo -e "${RED}✗${NC} $*"; exit 1; }
log_build() { echo -e "${BLUE}🔨${NC} $*"; }

# Parse command line arguments
FORCE_FULL=false
PARALLEL=false
SPECIFIC_MODULE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --force) FORCE_FULL=true; shift ;;
    --parallel) PARALLEL=true; shift ;;
    --module=*) SPECIFIC_MODULE="${1#--module=}"; shift ;;
    --module) SPECIFIC_MODULE="$2"; shift 2 ;;
    *) shift ;;
  esac
done

log_info "YAWL Smart Build Detector"

# Detect git changes
if [ "$FORCE_FULL" = "true" ]; then
  log_warn "Force full build requested"
  BUILD_TARGET="package"
  MODULES=""
else
  # Check if there are any changes
  if git diff --quiet HEAD 2>/dev/null; then
    if git diff --cached --quiet 2>/dev/null; then
      log_warn "No changes detected, skipping build"
      exit 0
    fi
  fi

  # Determine what changed
  CHANGED_FILES=$(git diff --name-only HEAD 2>/dev/null || echo "")
  STAGED_FILES=$(git diff --cached --name-only 2>/dev/null || echo "")
  ALL_CHANGES="$CHANGED_FILES $STAGED_FILES"

  # Detect change type
  if echo "$ALL_CHANGES" | grep -q "pom.xml"; then
    log_warn "pom.xml changed, running full build with tests"
    BUILD_TARGET="verify"
    MODULES=""
  elif echo "$ALL_CHANGES" | grep -q "\.java$"; then
    # Check if tests changed
    if echo "$ALL_CHANGES" | grep -q "test.*\.java$"; then
      log_info "Test files changed, running tests"
      BUILD_TARGET="test"
    else
      log_info "Source files changed, compiling + testing"
      BUILD_TARGET="test"
    fi

    # Extract module names from changed files
    MODULES=""
    for file in $ALL_CHANGES; do
      if [[ "$file" == src/* ]]; then
        # Extract module from path: src/org/yawlfoundation/yawl/engine/... -> yawl-engine
        MODULE_NAME=$(echo "$file" | cut -d'/' -f5)
        case "$MODULE_NAME" in
          engine) MODULES="$MODULES yawl-engine" ;;
          elements) MODULES="$MODULES yawl-elements" ;;
          stateless) MODULES="$MODULES yawl-stateless" ;;
          integration) MODULES="$MODULES yawl-integration" ;;
          resourcing) MODULES="$MODULES yawl-resourcing" ;;
          worklet) MODULES="$MODULES yawl-worklet" ;;
          monitoring) MODULES="$MODULES yawl-monitoring" ;;
          scheduling) MODULES="$MODULES yawl-scheduling" ;;
          utilities) MODULES="$MODULES yawl-utilities" ;;
        esac
      fi
    done
  elif echo "$ALL_CHANGES" | grep -q "\.(xml|md|properties)$"; then
    log_info "Configuration files changed, recompiling"
    BUILD_TARGET="compile"
  else
    log_info "Other files changed, running compile"
    BUILD_TARGET="compile"
  fi

  # Deduplicate modules
  if [ -n "$MODULES" ]; then
    MODULES=$(echo "$MODULES" | tr ' ' '\n' | sort -u | tr '\n' ' ')
  fi
fi

# Override with specific module if provided
if [ -n "$SPECIFIC_MODULE" ]; then
  MODULES="$SPECIFIC_MODULE"
fi

# Build Maven command
MVN_CMD="mvn"

# Add module selection
if [ -n "$MODULES" ]; then
  MODULES_LIST=$(echo "$MODULES" | tr ' ' ',')
  MVN_CMD="$MVN_CMD -pl $MODULES_LIST"
  log_info "Targeting modules: $MODULES"
fi

# Add target
MVN_CMD="$MVN_CMD clean $BUILD_TARGET"

# Add parallel if requested
if [ "$PARALLEL" = "true" ]; then
  MVN_CMD="$MVN_CMD -T 1C"
  log_info "Parallel build enabled (1 thread per core)"
fi

# Summary
echo ""
log_build "Build Command: $MVN_CMD"
echo ""

# Execute build
if eval "$MVN_CMD"; then
  log_info "Build completed successfully"
  echo ""

  # Show timing
  if command -v date &> /dev/null; then
    log_info "Build finished at $(date '+%H:%M:%S')"
  fi
  exit 0
else
  log_error "Build failed with exit code $?"
fi
