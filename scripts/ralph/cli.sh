#!/usr/bin/env bash
# Ralph CLI — Entry point for /ralph skill in Claude Code
set -euo pipefail

RALPH_HOME="${RALPH_HOME:-.}"

source "${RALPH_HOME}/scripts/ralph/utils.sh"

# Parse arguments
parse_args() {
    local description=""
    local max_iterations=10
    local timeout_mins=120
    local resume_mode=0

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --description)
                description="$2"
                shift 2
                ;;
            --max-iterations)
                max_iterations="$2"
                shift 2
                ;;
            --timeout)
                timeout_mins="$2"
                shift 2
                ;;
            --resume)
                resume_mode=1
                shift
                ;;
            -h|--help)
                print_help
                exit 0
                ;;
            *)
                # Treat as positional description if not a flag
                if [[ -z "$description" && ! "$1" =~ ^-- ]]; then
                    description="$1"
                fi
                shift
                ;;
        esac
    done

    echo "${description}|${max_iterations}|${timeout_mins}|${resume_mode}"
}

# Print help
print_help() {
    cat <<EOF
Ralph — Autonomous YAWL Validation & Agent Loop

Usage: /ralph <description> [options]

Options:
  --max-iterations N    Maximum loop iterations (default: 10)
  --timeout MINS        Timeout in minutes (default: 120)
  --resume              Resume previous loop if interrupted
  --help                Show this message

Examples:
  /ralph "Fix all broken tests"
  /ralph "Add new feature" --max-iterations 20
  /ralph --resume

EOF
}

# Invoke the loop
invoke_loop() {
    local description="$1"
    local max_iterations="$2"
    local timeout_mins="$3"
    local resume_mode="$4"

    if [[ -z "$description" ]]; then
        echo "ERROR: Description required" >&2
        echo "Usage: /ralph \"description\" [--max-iterations N] [--timeout MINS]" >&2
        return 1
    fi

    log_info "Ralph CLI invoked"
    log_info "Description: ${description}"
    log_info "Max iterations: ${max_iterations}"
    log_info "Timeout: ${timeout_mins} minutes"

    # Invoke the orchestrator
    bash "${RALPH_HOME}/.claude/hooks/ralph-loop.sh" \
        "${description}" \
        "${max_iterations}" \
        "${timeout_mins}"
}

# Main
main() {
    # Parse arguments
    local args
    args=$(parse_args "$@")
    IFS='|' read -r description max_iterations timeout_mins resume_mode <<< "$args"

    # Invoke loop
    invoke_loop "${description}" "${max_iterations}" "${timeout_mins}" "${resume_mode}"
}

# If sourced, don't run main
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
