#!/bin/bash
set -euo pipefail

# YAWL File Watcher with Auto-Test
# Monitors src/ and test/ directories and runs tests on changes
# Usage: ./watch-and-test.sh [--all|--compile-only|MODULE]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# Configuration
DEBOUNCE_SECONDS=2
WATCH_ENABLED=true
COMPILE_ONLY=false
SPECIFIC_MODULE=""
USE_MAVEN=true
LAST_RUN_TIME=0

# Logging functions
log_info() { echo -e "${GREEN}✓${NC} $*"; }
log_warn() { echo -e "${YELLOW}⚠${NC} $*"; }
log_error() { echo -e "${RED}✗${NC} $*"; }
log_watch() { echo -e "${BLUE}👁${NC}  $*"; }
log_running() { echo -e "${YELLOW}⏳${NC} $*"; }
log_success() { echo -e "${GREEN}✓${NC} $*"; }
log_fail() { echo -e "${RED}✗${NC} $*"; }
log_header() { echo -e "${CYAN}===${NC} $*"; }

# Get timestamp
timestamp() { date '+%H:%M:%S'; }

# Print banner
print_banner() {
    echo -e "${CYAN}"
    cat << 'EOF'
╔═══════════════════════════════════════════════╗
║     YAWL File Watcher & Auto-Test             ║
║     Press Ctrl+C to stop watching             ║
╚═══════════════════════════════════════════════╝
EOF
    echo -e "${NC}"
}

# Parse arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --all)
                SPECIFIC_MODULE=""
                shift
                ;;
            --compile-only)
                COMPILE_ONLY=true
                shift
                ;;
            --ant)
                USE_MAVEN=false
                shift
                ;;
            yawl-*)
                SPECIFIC_MODULE="$1"
                shift
                ;;
            help|--help|-h)
                show_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1. Use --help for usage."
                ;;
        esac
    done
}

# Show help
show_help() {
    cat << EOF
${CYAN}YAWL File Watcher & Auto-Test${NC}

${YELLOW}Usage:${NC}
  $0 [options]

${YELLOW}Options:${NC}

  ${GREEN}--all${NC}              Watch entire project (default)
                      Runs full test suite on changes

  ${GREEN}--compile-only${NC}     Only compile, skip tests
                      Fast feedback for syntax errors

  ${GREEN}--ant${NC}              Use Ant instead of Maven
                      Legacy build system

  ${GREEN}<module>${NC}           Watch specific module
                      Example: $0 yawl-engine

  ${GREEN}help${NC}               Show this help message

${YELLOW}Examples:${NC}

  # Watch entire project with tests
  $0 --all

  # Watch and compile only (fast)
  $0 --compile-only

  # Watch specific module
  $0 yawl-engine

  # Use Ant build
  $0 --ant

${YELLOW}Features:${NC}

  - Monitors src/ and test/ directories
  - 2-second debounce (batches rapid changes)
  - Color-coded output (green=pass, red=fail)
  - Re-runs on next save if tests fail
  - Graceful Ctrl+C handling

${YELLOW}Implementation:${NC}

  Uses inotifywait if available, otherwise falls back to
  polling mode (checks every 2 seconds).

EOF
}

# Check if inotifywait is available
has_inotify() {
    command -v inotifywait &> /dev/null
}

# Run build/test based on configuration
run_build() {
    local start_time=$(date +%s)

    # Prevent running too frequently
    local current_time=$(date +%s)
    local time_since_last=$((current_time - LAST_RUN_TIME))
    if [[ $time_since_last -lt $DEBOUNCE_SECONDS ]]; then
        return 0
    fi
    LAST_RUN_TIME=$current_time

    echo ""
    log_header "Build Started at $(timestamp)"
    echo ""

    local build_cmd=""
    local build_target=""

    if [[ "$USE_MAVEN" == "true" ]]; then
        # Maven build
        if [[ -n "$SPECIFIC_MODULE" ]]; then
            build_target="module: $SPECIFIC_MODULE"
            if [[ "$COMPILE_ONLY" == "true" ]]; then
                build_cmd="mvn -q compile -pl $SPECIFIC_MODULE -am -DskipTests=true"
            else
                build_cmd="mvn -q test -pl $SPECIFIC_MODULE -am -DskipITs=true"
            fi
        else
            build_target="all modules"
            if [[ "$COMPILE_ONLY" == "true" ]]; then
                build_cmd="mvn -q compile -DskipTests=true"
            else
                build_cmd="mvn -q test -DskipITs=true"
            fi
        fi
    else
        # Ant build
        build_target="Ant build"
        if [[ "$COMPILE_ONLY" == "true" ]]; then
            build_cmd="cd $PROJECT_ROOT/legacy/ant-build && ant compile"
        else
            build_cmd="cd $PROJECT_ROOT/legacy/ant-build && ant compile && ant unitTest"
        fi
    fi

    log_running "Building: $build_target"

    # Run build and capture output
    local build_output
    local build_status=0

    if build_output=$(cd "$PROJECT_ROOT" && eval "$build_cmd" 2>&1); then
        build_status=0
    else
        build_status=$?
    fi

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    echo ""

    if [[ $build_status -eq 0 ]]; then
        log_success "BUILD PASSED in ${duration}s"
        echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

        # Show summary if tests ran
        if [[ "$COMPILE_ONLY" == "false" ]]; then
            echo "$build_output" | grep -E "Tests run:|BUILD SUCCESS" | tail -5 || true
        fi
    else
        log_fail "BUILD FAILED in ${duration}s"
        echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

        # Show errors
        echo "$build_output" | grep -E "ERROR|FAILURE|Failed|Error" | head -20 || true
        echo ""
        log_warn "Fix errors and save to re-run"
    fi

    echo ""
    log_watch "Watching for changes (Ctrl+C to stop)..."
    echo ""
}

# Watch using inotifywait
watch_with_inotify() {
    log_info "Using inotifywait for file monitoring"

    local watch_paths=(
        "$PROJECT_ROOT/src"
        "$PROJECT_ROOT/test"
    )

    # Show what we're watching
    echo ""
    log_watch "Watching directories:"
    for path in "${watch_paths[@]}"; do
        if [[ -d "$path" ]]; then
            echo "  - $path"
        fi
    done
    echo ""

    # Initial build
    run_build

    # Watch for changes
    while true; do
        # Wait for file changes (modify, create, delete, move)
        if inotifywait -q -r -e modify,create,delete,move \
            --exclude '\.git|target/|\.class$|\.swp$|~$' \
            "${watch_paths[@]}" >/dev/null 2>&1; then

            # Debounce: wait for additional changes
            sleep "$DEBOUNCE_SECONDS"

            # Drain any additional events
            while inotifywait -q -r -t 1 -e modify,create,delete,move \
                --exclude '\.git|target/|\.class$|\.swp$|~$' \
                "${watch_paths[@]}" >/dev/null 2>&1; do
                :
            done

            run_build
        fi
    done
}

# Watch using polling (fallback)
watch_with_polling() {
    log_warn "inotify-tools not found, using polling mode"
    log_info "Install inotify-tools for better performance: apt-get install inotify-tools"

    echo ""
    log_watch "Polling for changes every ${DEBOUNCE_SECONDS}s"
    echo ""

    # Calculate initial checksums
    local last_checksum=""

    get_checksum() {
        find "$PROJECT_ROOT/src" "$PROJECT_ROOT/test" \
            -type f \( -name "*.java" -o -name "*.xml" -o -name "*.properties" \) \
            -newer /tmp/yawl-watch-marker 2>/dev/null \
            -exec stat -c '%Y %n' {} \; 2>/dev/null | md5sum | cut -d' ' -f1 || echo ""
    }

    # Create marker file
    touch /tmp/yawl-watch-marker

    # Initial build
    run_build

    # Update marker
    touch /tmp/yawl-watch-marker

    # Watch loop
    while true; do
        sleep "$DEBOUNCE_SECONDS"

        local current_checksum=$(get_checksum)

        if [[ -n "$current_checksum" ]] && [[ "$current_checksum" != "$last_checksum" ]]; then
            last_checksum="$current_checksum"
            run_build

            # Update marker
            touch /tmp/yawl-watch-marker
        fi
    done
}

# Cleanup on exit
cleanup() {
    echo ""
    log_info "Stopping file watcher"
    rm -f /tmp/yawl-watch-marker
    exit 0
}

# Main
main() {
    # Parse arguments
    parse_args "$@"

    # Print banner
    print_banner

    # Show configuration
    log_header "Configuration"
    echo "  Build System: $([ "$USE_MAVEN" == "true" ] && echo "Maven" || echo "Ant")"
    echo "  Mode: $([ "$COMPILE_ONLY" == "true" ] && echo "Compile Only" || echo "Compile + Test")"
    echo "  Target: ${SPECIFIC_MODULE:-All Modules}"
    echo "  Debounce: ${DEBOUNCE_SECONDS}s"
    echo ""

    # Setup signal handlers
    trap cleanup SIGINT SIGTERM

    # Start watching
    if has_inotify; then
        watch_with_inotify
    else
        watch_with_polling
    fi
}

main "$@"
