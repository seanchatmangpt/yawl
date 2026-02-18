#!/bin/bash
set -euo pipefail

# YAWL Maven Build Skill
# Provides convenient shortcuts for common Maven build operations
# Usage: /yawl-build [target] [--verbose] [--quiet] [--module=MODULE] [--dx]
#
# The --dx flag (default for compile/test) uses scripts/dx.sh for the fastest
# possible build-test loop: auto-detects changed modules, incremental
# compilation, agent-dx profile (2C parallelism, no overhead).

TARGET="${1:-compile}"
VERBOSE="${VERBOSE:-false}"
QUIET="${QUIET:-false}"
MODULE="${MODULE:-}"
USE_DX="${USE_DX:-auto}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print status messages
log_info() { echo -e "${GREEN}[build]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[build]${NC} $*"; }
log_error() { echo -e "${RED}[build]${NC} $*"; exit 1; }

# Parse arguments
while [[ $# -gt 1 ]]; do
  case "$1" in
    --verbose) VERBOSE=true; shift ;;
    --quiet) QUIET=true; shift ;;
    --module=*) MODULE="${1#--module=}"; shift ;;
    --module) MODULE="$2"; shift 2 ;;
    --dx) USE_DX=true; shift ;;
    --no-dx) USE_DX=false; shift ;;
    *) shift ;;
  esac
done

# Fast path: delegate to dx.sh for compile/test targets
if [[ "$USE_DX" == "auto" || "$USE_DX" == "true" ]]; then
  DX_SCRIPT="${REPO_ROOT}/scripts/dx.sh"
  if [[ -x "$DX_SCRIPT" ]]; then
    case "$TARGET" in
      compile)
        DX_ARGS="compile"
        [[ -n "$MODULE" ]] && DX_ARGS="-pl $MODULE compile"
        log_info "Fast path: bash scripts/dx.sh $DX_ARGS"
        exec bash "$DX_SCRIPT" $DX_ARGS
        ;;
      test|unitTest)
        DX_ARGS=""
        [[ -n "$MODULE" ]] && DX_ARGS="-pl $MODULE"
        log_info "Fast path: bash scripts/dx.sh $DX_ARGS"
        exec bash "$DX_SCRIPT" $DX_ARGS
        ;;
    esac
  fi
fi

# Standard path: full Maven commands for non-dx targets
BUILD_CMD="mvn"

# Add module flag if specified
if [ -n "$MODULE" ]; then
  BUILD_CMD="$BUILD_CMD -pl $MODULE"
fi

# Add verbose/quiet flags
if [ "$VERBOSE" = "true" ]; then
  BUILD_CMD="$BUILD_CMD -X"
elif [ "$QUIET" = "false" ]; then
  BUILD_CMD="$BUILD_CMD -q"
fi

# Map Maven targets to actual Maven goals
case "$TARGET" in
  compile)
    BUILD_CMD="$BUILD_CMD clean compile"
    log_info "Building: Compile source code"
    ;;
  test|unitTest)
    BUILD_CMD="$BUILD_CMD clean test"
    log_info "Testing: Running JUnit test suite"
    ;;
  package|build|buildAll)
    BUILD_CMD="$BUILD_CMD clean package"
    log_info "Building: Full build with package"
    ;;
  buildWebApps)
    BUILD_CMD="$BUILD_CMD clean package -DskipTests"
    log_info "Building: WAR packages (skipping tests)"
    ;;
  clean)
    BUILD_CMD="$BUILD_CMD clean"
    log_info "Cleaning: Build artifacts"
    ;;
  install)
    BUILD_CMD="$BUILD_CMD clean install"
    log_info "Installing: Building and installing to local repository"
    ;;
  javadoc)
    BUILD_CMD="$BUILD_CMD javadoc:javadoc"
    log_info "Building: Javadoc documentation"
    ;;
  verify)
    BUILD_CMD="$BUILD_CMD clean verify"
    log_info "Verifying: Build and test"
    ;;
  *)
    log_error "Unknown target: $TARGET. Valid targets: compile, test, package, buildAll, buildWebApps, clean, install, javadoc, verify"
    ;;
esac

# Execute build
log_info "Executing: $BUILD_CMD"
if eval "$BUILD_CMD"; then
  log_info "Build completed successfully"
  exit 0
else
  log_error "Build failed with exit code $?"
fi
