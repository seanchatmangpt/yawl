#!/bin/bash
set -euo pipefail

# YAWL Developer Workflow Script
# Convenient wrapper for common development tasks
# Usage: ./dev-workflow.sh <command> [options]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Logging functions
log_info() { echo -e "${GREEN}✓${NC} $*"; }
log_warn() { echo -e "${YELLOW}⚠${NC} $*"; }
log_error() { echo -e "${RED}✗${NC} $*"; exit 1; }
log_header() { echo -e "${CYAN}===${NC} $*"; }
log_task() { echo -e "${BLUE}→${NC} $*"; }

# Get timestamp
timestamp() { date '+%H:%M:%S'; }

# Check if Maven is available
check_maven() {
    if ! command -v mvn &> /dev/null; then
        log_error "Maven not found. Please install Maven first."
    fi
}

# Check if Ant is available
check_ant() {
    if ! command -v ant &> /dev/null; then
        log_error "Ant not found. Please install Ant first."
    fi
}

# Run Maven command with timing
run_maven() {
    local cmd="$*"
    log_task "Running: mvn $cmd"
    START_TIME=$(date +%s)

    if cd "$PROJECT_ROOT" && mvn $cmd; then
        END_TIME=$(date +%s)
        DURATION=$((END_TIME - START_TIME))
        log_info "Maven build completed in ${DURATION}s"
        return 0
    else
        END_TIME=$(date +%s)
        DURATION=$((END_TIME - START_TIME))
        log_error "Maven build failed after ${DURATION}s"
        return 1
    fi
}

# Run Ant command with timing
run_ant() {
    local target="$1"
    log_task "Running: ant $target"
    START_TIME=$(date +%s)

    if cd "$PROJECT_ROOT/legacy/ant-build" && ant "$target"; then
        END_TIME=$(date +%s)
        DURATION=$((END_TIME - START_TIME))
        log_info "Ant build completed in ${DURATION}s"
        return 0
    else
        END_TIME=$(date +%s)
        DURATION=$((END_TIME - START_TIME))
        log_error "Ant build failed after ${DURATION}s"
        return 1
    fi
}

# Show help
show_help() {
    cat << EOF
${CYAN}YAWL Developer Workflow${NC}

${YELLOW}Usage:${NC}
  $0 <command> [options]

${YELLOW}Commands:${NC}

  ${GREEN}quick${NC}              Fast compile + unit tests (Maven)
                      Recommended for quick feedback
                      Time: ~30-60s

  ${GREEN}compile${NC}            Compile only, no tests (Maven)
                      Fast syntax check
                      Time: ~20-30s

  ${GREEN}test${NC}               Run all unit tests (Maven)
                      Includes compilation
                      Time: ~60-90s

  ${GREEN}verify${NC}             Full verification with integration tests
                      Time: ~2-5 minutes

  ${GREEN}module <name>${NC}      Compile + test specific module
                      Example: $0 module yawl-engine
                      Time: ~10-30s

  ${GREEN}full${NC}               Full build with all checks
                      Includes compile, test, verify
                      Time: ~5-10 minutes

  ${GREEN}clean${NC}              Clean build artifacts (Maven + Ant)
                      Removes target/ directories

  ${GREEN}ant-compile${NC}        Use legacy Ant build (compile only)
                      Time: ~18s

  ${GREEN}ant-test${NC}           Use legacy Ant build (compile + test)
                      Time: ~45-60s

  ${GREEN}watch${NC}              Start file watcher (auto-test on change)
                      Calls watch-and-test.sh

  ${GREEN}status${NC}             Show git status and build health
                      Quick overview of workspace

  ${GREEN}help${NC}               Show this help message

${YELLOW}Examples:${NC}

  # Quick feedback loop
  $0 quick

  # Test specific module after changes
  $0 module yawl-engine

  # Full validation before commit
  $0 full

  # Clean and rebuild
  $0 clean && $0 quick

  # Use Ant for faster compile
  $0 ant-compile

${YELLOW}Recommended Workflow:${NC}

  1. Make changes to code
  2. Run: $0 quick          (fast feedback)
  3. Fix any failures
  4. Run: $0 full           (before commit)
  5. Commit if all pass

EOF
}

# Module names for Maven
MODULE_NAMES=(
    "yawl-utilities"
    "yawl-elements"
    "yawl-engine"
    "yawl-stateless"
    "yawl-resourcing"
    "yawl-worklet"
    "yawl-scheduling"
    "yawl-integration"
    "yawl-monitoring"
    "yawl-control-panel"
)

# Validate module name
validate_module() {
    local module="$1"
    for valid_module in "${MODULE_NAMES[@]}"; do
        if [[ "$module" == "$valid_module" ]]; then
            return 0
        fi
    done
    log_error "Invalid module: $module. Valid modules: ${MODULE_NAMES[*]}"
}

# Main command dispatcher
main() {
    if [[ $# -eq 0 ]]; then
        show_help
        exit 0
    fi

    local command="$1"
    shift

    log_header "YAWL Developer Workflow - $(timestamp)"
    echo ""

    case "$command" in
        quick)
            check_maven
            log_header "Quick Build: Compile + Unit Tests"
            run_maven "clean test -DskipITs=true"
            ;;

        compile)
            check_maven
            log_header "Compile Only (No Tests)"
            run_maven "clean compile -DskipTests=true"
            ;;

        test)
            check_maven
            log_header "Run All Unit Tests"
            run_maven "test -DskipITs=true"
            ;;

        verify)
            check_maven
            log_header "Full Verification (Unit + Integration Tests)"
            run_maven "clean verify"
            ;;

        module)
            check_maven
            if [[ $# -eq 0 ]]; then
                log_error "Module name required. Usage: $0 module <name>"
            fi
            local module="$1"
            validate_module "$module"
            log_header "Module Build: $module"
            run_maven "clean test -pl $module -am -DskipITs=true"
            ;;

        full)
            check_maven
            log_header "Full Build Pipeline"
            log_task "Step 1/3: Clean"
            run_maven "clean"
            log_task "Step 2/3: Compile + Test"
            run_maven "test -DskipITs=true"
            log_task "Step 3/3: Verify"
            run_maven "verify"
            log_info "Full build pipeline completed successfully"
            ;;

        clean)
            log_header "Cleaning Build Artifacts"
            if command -v mvn &> /dev/null; then
                log_task "Cleaning Maven artifacts"
                cd "$PROJECT_ROOT" && mvn clean || true
            fi
            if command -v ant &> /dev/null && [[ -f "$PROJECT_ROOT/legacy/ant-build/build.xml" ]]; then
                log_task "Cleaning Ant artifacts"
                cd "$PROJECT_ROOT/legacy/ant-build" && ant clean || true
            fi
            log_info "Clean completed"
            ;;

        ant-compile)
            check_ant
            log_header "Ant Build: Compile Only"
            run_ant "compile"
            ;;

        ant-test)
            check_ant
            log_header "Ant Build: Compile + Test"
            run_ant "compile"
            run_ant "unitTest"
            ;;

        watch)
            log_header "Starting File Watcher"
            if [[ -x "$SCRIPT_DIR/watch-and-test.sh" ]]; then
                exec "$SCRIPT_DIR/watch-and-test.sh" "$@"
            else
                log_error "watch-and-test.sh not found or not executable"
            fi
            ;;

        status)
            log_header "Build Health Status"
            echo ""
            log_task "Git Status:"
            cd "$PROJECT_ROOT" && git status --short || true
            echo ""
            log_task "Maven Modules:"
            if [[ -f "$PROJECT_ROOT/pom.xml" ]]; then
                cd "$PROJECT_ROOT" && grep '<module>' pom.xml | sed 's/.*<module>\(.*\)<\/module>/  - \1/' || true
            fi
            echo ""
            log_task "Build Files:"
            [[ -d "$PROJECT_ROOT/target" ]] && echo "  Maven artifacts: target/" || echo "  Maven artifacts: none"
            [[ -d "$PROJECT_ROOT/build/yawl" ]] && echo "  Ant artifacts: build/yawl/" || echo "  Ant artifacts: none"
            ;;

        help|--help|-h)
            show_help
            ;;

        *)
            log_error "Unknown command: $command. Run '$0 help' for usage."
            ;;
    esac

    echo ""
    log_info "Workflow completed at $(timestamp)"
}

main "$@"
