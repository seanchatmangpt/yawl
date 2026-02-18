#!/usr/bin/env bash
#
# error-handling.sh - Error handling library for observatory scripts
#
# Provides standardized error handling, logging, and cleanup functions.
# Source this file to use: source lib/error-handling.sh
#
# Exit Codes:
#   0   - Success
#   1   - General error
#   2   - Misuse of shell command
#   3   - Missing dependency
#   4   - Permission denied
#   5   - Configuration error
#   6   - Network error
#   7   - Timeout
#   8   - Invalid input
#   64-78 - Reserved for future use (EX_* from sysexits.h)

set -o nounset
set -o pipefail

# Exit codes (EX_* from sysexits.h where applicable)
readonly EXIT_SUCCESS=0
readonly EXIT_FAILURE=1
readonly EXIT_MISUSE=2
readonly EXIT_MISSING_DEP=3
readonly EXIT_PERMISSION=4
readonly EXIT_CONFIG=5
readonly EXIT_NETWORK=6
readonly EXIT_TIMEOUT=7
readonly EXIT_INVALID_INPUT=8
readonly EXIT_DATAERR=65
readonly EXIT_NOINPUT=66
readonly EXIT_NOUSER=67
readonly EXIT_NOHOST=68
readonly EXIT_UNAVAILABLE=69
readonly EXIT_SOFTWARE=70
readonly EXIT_OSERR=71
readonly EXIT_OSFILE=72
readonly EXIT_CANTCREAT=73
readonly EXIT_IOERR=74
readonly EXIT_TEMPFAIL=75
readonly EXIT_PROTOCOL=76
readonly EXIT_NOPERM=77
readonly EXIT_CONFIG_ERROR=78

# Color definitions for terminal output
if [[ -t 1 ]] && command -v tput &>/dev/null; then
    readonly COLOR_RED=$(tput setaf 1 2>/dev/null || echo '')
    readonly COLOR_GREEN=$(tput setaf 2 2>/dev/null || echo '')
    readonly COLOR_YELLOW=$(tput setaf 3 2>/dev/null || echo '')
    readonly COLOR_BLUE=$(tput setaf 4 2>/dev/null || echo '')
    readonly COLOR_MAGENTA=$(tput setaf 5 2>/dev/null || echo '')
    readonly COLOR_CYAN=$(tput setaf 6 2>/dev/null || echo '')
    readonly COLOR_RESET=$(tput sgr0 2>/dev/null || echo '')
    readonly COLOR_BOLD=$(tput bold 2>/dev/null || echo '')
else
    readonly COLOR_RED=''
    readonly COLOR_GREEN=''
    readonly COLOR_YELLOW=''
    readonly COLOR_BLUE=''
    readonly COLOR_MAGENTA=''
    readonly COLOR_CYAN=''
    readonly COLOR_RESET=''
    readonly COLOR_BOLD=''
fi

# Log level constants
readonly LOG_LEVEL_DEBUG=0
readonly LOG_LEVEL_INFO=1
readonly LOG_LEVEL_WARN=2
readonly LOG_LEVEL_ERROR=3
readonly LOG_LEVEL_FATAL=4

# Current log level (default to INFO unless DEBUG is set)
if [[ "${DEBUG:-0}" == "1" ]]; then
    CURRENT_LOG_LEVEL=$LOG_LEVEL_DEBUG
else
    CURRENT_LOG_LEVEL=$LOG_LEVEL_INFO
fi

# Log file (optional - set LOG_FILE environment variable)
LOG_FILE="${LOG_FILE:-}"

# Script name for logging
SCRIPT_NAME="${SCRIPT_NAME:-$(basename "${BASH_SOURCE[-1]:-$0}")}"

# Cleanup tracking
declare -a CLEANUP_FUNCTIONS=()
declare -a CLEANUP_FILES=()
declare -a CLEANUP_DIRS=()

#------------------------------------------------------------------------------
# Internal Functions
#------------------------------------------------------------------------------

# Get timestamp for log messages
_timestamp() {
    date '+%Y-%m-%d %H:%M:%S'
}

# Get ISO8601 timestamp for structured logging
_timestamp_iso() {
    date -u '+%Y-%m-%dT%H:%M:%SZ'
}

# Write to log file if configured
_write_log_file() {
    local level="$1"
    local message="$2"

    if [[ -n "$LOG_FILE" ]]; then
        echo "$(_timestamp_iso) [$level] [$SCRIPT_NAME] $message" >> "$LOG_FILE" 2>/dev/null || true
    fi
}

# Format message with optional prefix
_format_message() {
    local prefix="$1"
    local message="$2"
    echo "${prefix}${message}${COLOR_RESET}"
}

#------------------------------------------------------------------------------
# Public Logging Functions
#------------------------------------------------------------------------------

# Log a debug message (only shown when DEBUG=1)
# Usage: debug "message"
debug() {
    local message="${1:-}"

    if [[ $CURRENT_LOG_LEVEL -le $LOG_LEVEL_DEBUG ]]; then
        local formatted
        formatted=$(_format_message "${COLOR_CYAN}[DEBUG]${COLOR_RESET} " "$message")
        echo "$formatted" >&2
        _write_log_file "DEBUG" "$message"
    fi
}

# Log an informational message
# Usage: info "message"
info() {
    local message="${1:-}"

    if [[ $CURRENT_LOG_LEVEL -le $LOG_LEVEL_INFO ]]; then
        local formatted
        formatted=$(_format_message "${COLOR_GREEN}[INFO]${COLOR_RESET} " "$message")
        echo "$formatted" >&2
        _write_log_file "INFO" "$message"
    fi
}

# Log a warning message
# Usage: warn "message"
warn() {
    local message="${1:-}"

    if [[ $CURRENT_LOG_LEVEL -le $LOG_LEVEL_WARN ]]; then
        local formatted
        formatted=$(_format_message "${COLOR_YELLOW}${COLOR_BOLD}[WARN]${COLOR_RESET} " "$message")
        echo "$formatted" >&2
        _write_log_file "WARN" "$message"
    fi
}

# Log an error message (does not exit)
# Usage: error "message"
error() {
    local message="${1:-}"

    if [[ $CURRENT_LOG_LEVEL -le $LOG_LEVEL_ERROR ]]; then
        local formatted
        formatted=$(_format_message "${COLOR_RED}${COLOR_BOLD}[ERROR]${COLOR_RESET} " "$message")
        echo "$formatted" >&2
        _write_log_file "ERROR" "$message"
    fi
}

# Log a fatal error and exit
# Usage: die "message" [exit_code]
die() {
    local message="${1:-An error occurred}"
    local exit_code="${2:-$EXIT_FAILURE}"

    local formatted
    formatted=$(_format_message "${COLOR_RED}${COLOR_BOLD}[FATAL]${COLOR_RESET} " "$message")
    echo "$formatted" >&2
    _write_log_file "FATAL" "$message (exit code: $exit_code)"

    exit "$exit_code"
}

# Log success message
# Usage: success "message"
success() {
    local message="${1:-}"
    local formatted
    formatted=$(_format_message "${COLOR_GREEN}${COLOR_BOLD}[OK]${COLOR_RESET} " "$message")
    echo "$formatted" >&2
    _write_log_file "SUCCESS" "$message"
}

#------------------------------------------------------------------------------
# Error Trapping
#------------------------------------------------------------------------------

# Store the original set options
_ORIGINAL_ERREXIT=""
_ORIGINAL_PIPEFAIL=""

# Error handler function called by trap
_error_handler() {
    local exit_code=$?
    local line_no="${1:-unknown}"
    local command="${BASH_COMMAND:-unknown}"

    # Only show error if not in a subshell exit
    if [[ $exit_code -ne 0 ]]; then
        error "Command failed at line $line_no: '$command' (exit code: $exit_code)"
        error "Stack trace:"
        local i
        for ((i=1; i<${#FUNCNAME[@]}; i++)); do
            error "  ${FUNCNAME[$i]}() at ${BASH_SOURCE[$i]}:${BASH_LINENO[$((i-1))]}"
        done
    fi

    return $exit_code
}

# Set up error trapping with errexit and pipefail
# Usage: trap_errors
trap_errors() {
    # Enable strict error handling
    set -o errexit

    # Set up ERR trap to catch errors
    trap '_error_handler ${LINENO}' ERR

    debug "Error trapping enabled"
}

# Disable error trapping (for cleanup or known-error sections)
# Usage: untrap_errors
untrap_errors() {
    set +o errexit
    trap - ERR
    debug "Error trapping disabled"
}

#------------------------------------------------------------------------------
# Cleanup Functions
#------------------------------------------------------------------------------

# Register a function to be called on exit
# Usage: register_cleanup_function function_name
register_cleanup_function() {
    local func_name="${1:-}"

    if [[ -z "$func_name" ]]; then
        error "register_cleanup_function: function name required"
        return 1
    fi

    CLEANUP_FUNCTIONS+=("$func_name")
    debug "Registered cleanup function: $func_name"
}

# Register a file to be deleted on exit
# Usage: register_cleanup_file /path/to/file
register_cleanup_file() {
    local file_path="${1:-}"

    if [[ -z "$file_path" ]]; then
        error "register_cleanup_file: file path required"
        return 1
    fi

    CLEANUP_FILES+=("$file_path")
    debug "Registered cleanup file: $file_path"
}

# Register a directory to be deleted on exit
# Usage: register_cleanup_dir /path/to/directory
register_cleanup_dir() {
    local dir_path="${1:-}"

    if [[ -z "$dir_path" ]]; then
        error "register_cleanup_dir: directory path required"
        return 1
    fi

    CLEANUP_DIRS+=("$dir_path")
    debug "Registered cleanup directory: $dir_path"
}

# Create a temporary file and register it for cleanup
# Usage: tmp_file=$(create_temp_file)
create_temp_file() {
    local prefix="${1:-yawlobs}"
    local tmp_file

    tmp_file=$(mktemp -t "${prefix}.XXXXXX") || {
        error "Failed to create temporary file"
        return 1
    }

    register_cleanup_file "$tmp_file"
    echo "$tmp_file"
}

# Create a temporary directory and register it for cleanup
# Usage: tmp_dir=$(create_temp_dir)
create_temp_dir() {
    local prefix="${1:-yawlobs}"
    local tmp_dir

    tmp_dir=$(mktemp -d -t "${prefix}.XXXXXX") || {
        error "Failed to create temporary directory"
        return 1
    }

    register_cleanup_dir "$tmp_dir"
    echo "$tmp_dir"
}

# Execute all registered cleanup operations
# Usage: _execute_cleanup [exit_code]
_execute_cleanup() {
    local exit_code="${1:-0}"

    debug "Executing cleanup (exit code: $exit_code)"

    # Execute cleanup functions in reverse order (LIFO)
    local i
    for ((i=${#CLEANUP_FUNCTIONS[@]}-1; i>=0; i--)); do
        local func="${CLEANUP_FUNCTIONS[$i]}"
        debug "Calling cleanup function: $func"
        "$func" 2>/dev/null || true
    done

    # Remove cleanup files
    for file_path in "${CLEANUP_FILES[@]}"; do
        if [[ -f "$file_path" ]]; then
            debug "Removing cleanup file: $file_path"
            rm -f "$file_path" 2>/dev/null || true
        fi
    done

    # Remove cleanup directories
    for dir_path in "${CLEANUP_DIRS[@]}"; do
        if [[ -d "$dir_path" ]]; then
            debug "Removing cleanup directory: $dir_path"
            rm -rf "$dir_path" 2>/dev/null || true
        fi
    done

    # Clear arrays
    CLEANUP_FUNCTIONS=()
    CLEANUP_FILES=()
    CLEANUP_DIRS=()
}

# Main cleanup function to be called on exit
# Usage: cleanup_on_exit
cleanup_on_exit() {
    local exit_code=$?
    _execute_cleanup "$exit_code"
}

# Set up exit trap for cleanup
# Usage: setup_cleanup
setup_cleanup() {
    trap cleanup_on_exit EXIT
    debug "Exit trap configured for cleanup"
}

#------------------------------------------------------------------------------
# Input Validation Functions
#------------------------------------------------------------------------------

# Check if a command exists
# Usage: require_command cmd1 cmd2 ...
require_command() {
    local missing=0
    local cmd

    for cmd in "$@"; do
        if ! command -v "$cmd" &>/dev/null; then
            error "Required command not found: $cmd"
            missing=1
        fi
    done

    if [[ $missing -eq 1 ]]; then
        return $EXIT_MISSING_DEP
    fi

    return 0
}

# Check if a file exists and is readable
# Usage: require_file /path/to/file
require_file() {
    local file_path="${1:-}"

    if [[ -z "$file_path" ]]; then
        error "require_file: file path required"
        return $EXIT_INVALID_INPUT
    fi

    if [[ ! -f "$file_path" ]]; then
        error "File not found: $file_path"
        return $EXIT_NOINPUT
    fi

    if [[ ! -r "$file_path" ]]; then
        error "File not readable: $file_path"
        return $EXIT_PERMISSION
    fi

    return 0
}

# Check if a directory exists and is accessible
# Usage: require_directory /path/to/dir
require_directory() {
    local dir_path="${1:-}"

    if [[ -z "$dir_path" ]]; then
        error "require_directory: directory path required"
        return $EXIT_INVALID_INPUT
    fi

    if [[ ! -d "$dir_path" ]]; then
        error "Directory not found: $dir_path"
        return $EXIT_NOINPUT
    fi

    if [[ ! -x "$dir_path" ]]; then
        error "Directory not accessible: $dir_path"
        return $EXIT_PERMISSION
    fi

    return 0
}

# Validate that a variable is set
# Usage: require_var VAR_NAME "description"
require_var() {
    local var_name="${1:-}"
    local description="${2:-$var_name}"

    if [[ -z "$var_name" ]]; then
        error "require_var: variable name required"
        return $EXIT_INVALID_INPUT
    fi

    # Use indirect expansion to check variable value
    local var_value="${!var_name:-}"

    if [[ -z "$var_value" ]]; then
        error "Required variable not set: $var_name ($description)"
        return $EXIT_CONFIG
    fi

    return 0
}

#------------------------------------------------------------------------------
# Retry Logic
#------------------------------------------------------------------------------

# Retry a command with exponential backoff
# Usage: retry max_attempts initial_delay command [args...]
retry() {
    local max_attempts="${1:-3}"
    local initial_delay="${2:-1}"
    shift 2
    local cmd=("$@")

    if [[ ${#cmd[@]} -eq 0 ]]; then
        error "retry: command required"
        return $EXIT_INVALID_INPUT
    fi

    local attempt=1
    local delay="$initial_delay"
    local exit_code

    while [[ $attempt -le $max_attempts ]]; do
        debug "Attempt $attempt/$max_attempts: ${cmd[*]}"

        if "${cmd[@]}"; then
            return 0
        fi
        exit_code=$?

        if [[ $attempt -lt $max_attempts ]]; then
            warn "Command failed (attempt $attempt/$max_attempts), retrying in ${delay}s..."
            sleep "$delay"
            delay=$((delay * 2))
        fi

        ((attempt++))
    done

    error "Command failed after $max_attempts attempts: ${cmd[*]}"
    return $exit_code
}

#------------------------------------------------------------------------------
# Signal Handling
#------------------------------------------------------------------------------

# Handle interrupt signals gracefully
# Usage: handle_interrupt
handle_interrupt() {
    info "Interrupted by user"
    cleanup_on_exit
    exit 130  # 128 + SIGINT(2)
}

# Set up signal handlers
# Usage: setup_signal_handlers
setup_signal_handlers() {
    trap handle_interrupt INT TERM
    debug "Signal handlers configured"
}

#------------------------------------------------------------------------------
# Initialization
#------------------------------------------------------------------------------

# Initialize error handling for a script
# Usage: init_error_handling
init_error_handling() {
    trap_errors
    setup_cleanup
    setup_signal_handlers
    debug "Error handling initialized"
}

#------------------------------------------------------------------------------
# Self-test
#------------------------------------------------------------------------------

# Run self-test if executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    echo "Running error-handling.sh self-test..."

    test_logging() {
        echo "Testing log functions..."
        DEBUG=1

        debug "This is a debug message"
        info "This is an info message"
        warn "This is a warning message"
        error "This is an error message"
        success "This is a success message"

        echo "Log functions: PASS"
    }

    test_temp_files() {
        echo "Testing temporary file creation..."
        local tmp_file
        tmp_file=$(create_temp_file "test")

        if [[ -f "$tmp_file" ]]; then
            echo "Test content" > "$tmp_file"
            info "Created temp file: $tmp_file"
            echo "Temp file creation: PASS"
        else
            error "Failed to create temp file"
            exit 1
        fi
    }

    test_require_command() {
        echo "Testing require_command..."
        if require_command bash; then
            echo "require_command (existing): PASS"
        else
            error "require_command failed for 'bash'"
            exit 1
        fi

        # This should fail but not exit because errexit is off during test
        set +e
        if require_command nonexistent_command_xyz 2>/dev/null; then
            error "require_command should have failed for nonexistent command"
            exit 1
        else
            echo "require_command (nonexistent): PASS"
        fi
        set -e
    }

    # Run tests
    init_error_handling
    test_logging
    test_temp_files
    test_require_command

    success "All self-tests passed!"
fi
