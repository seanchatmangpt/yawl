#!/bin/bash

# Observatory Common Library
# Provides shared utilities for observatory scripts

set -euo pipefail

# =============================================================================
# Configuration
# =============================================================================

# Colors for output
readonly RED='\033[0;31m'
readonly YELLOW='\033[1;33m'
readonly GREEN='\033[0;32m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Output directories
readonly OBSERVATORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly OUTPUT_DIR="$OBSERVATORY_ROOT/docs/v6/latest"
readonly FACTS_DIR="$OUTPUT_DIR/facts"
readonly DIAGRAMS_DIR="$OUTPUT_DIR/diagrams"
readonly RECEIPTS_DIR="$OUTPUT_DIR/receipts"
readonly INDEX_FILE="$OUTPUT_DIR/INDEX.md"

# =============================================================================
# Logging Functions
# =============================================================================

# Log with level prefix
log_with_level() {
    local level="$1"
    shift
    local message="$*"
    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    case "$level" in
        "ERROR") echo -e "${RED}[ERROR]${NC} [$timestamp] $message" >&2 ;;
        "WARN")  echo -e "${YELLOW}[WARN]${NC}  [$timestamp] $message" >&2 ;;
        "INFO")  echo -e "${GREEN}[INFO]${NC}  [$timestamp] $message" ;;
        "DEBUG") echo -e "${BLUE}[DEBUG]${NC} [$timestamp] $message" ;;
        *)       echo "[$timestamp] $message" ;;
    esac
}

# Public logging functions
info() { log_with_level "INFO" "$*"; }
warn() { log_with_level "WARN" "$*"; }
error() { log_with_level "ERROR" "$*"; }
debug() { if [[ "${DEBUG:-0}" == "1" ]]; then log_with_level "DEBUG" "$*"; fi; }

# =============================================================================
# JSON Helper Functions
# =============================================================================

# Check if jq is available
check_jq() {
    if ! command -v jq &> /dev/null; then
        error "jq is required but not installed. Please install jq."
        exit 1
    fi
}

# Validate JSON file
validate_json() {
    local file="$1"
    if ! jq empty "$file" 2>/dev/null; then
        error "Invalid JSON in file: $file"
        return 1
    fi
}

# Create JSON object from key-value pairs
make_json() {
    local -A params=("$@")
    local json="{"
    local first=true

    for key in "${!params[@]}"; do
        if [[ "$first" == "true" ]]; then
            first=false
        else
            json+=", "
        fi
        json+="\"$key\": \"${params[$key]}\""
    done
    json+="}"
    echo "$json"
}

# Add receipt entry
add_receipt() {
    local name="$1"
    local status="$2"
    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    local duration="${3:-0}"

    local receipt
    receipt=$(make_json \
        name "$name" \
        status "$status" \
        timestamp "$timestamp" \
        duration_ms "$duration" \
        exit_code "${4:-0}")

    echo "$receipt" >> "$RECEIPTS_DIR/observatory-receipts.jsonl"
}

# Get total execution time
get_duration_ms() {
    local start_ms="$1"
    local current_ms
    current_ms=$(date +%s%3N)
    echo $((current_ms - start_ms))
}

# =============================================================================
# File Discovery Functions
# =============================================================================

# Find all Java files
find_java_files() {
    local search_dir="${1:-.}"
    find "$search_dir" -name "*.java" -not -path "./node_modules/*" -not -path "./target/*" | sort
}

# Find all pom.xml files
find_pom_files() {
    local search_dir="${1:-.}"
    find "$search_dir" -name "pom.xml" -not -path "./node_modules/*" | sort
}

# Find all XML/XSD files
find_xml_files() {
    local search_dir="${1:-.}"
    find "$search_dir" -name "*.xml" -not -path "./node_modules/*" | sort
}

# Find all YAML/YML files
find_yaml_files() {
    local search_dir="${1:-.}"
    find "$search_dir" -name "*.yml" -o -name "*.yaml" -not -path "./node_modules/*" | sort
}

# =============================================================================
# Output Directory Setup
# =============================================================================

# Ensure output directories exist
setup_output_dirs() {
    info "Setting up output directories..."

    # Create main output directory
    mkdir -p "$OUTPUT_DIR"

    # Create subdirectories
    mkdir -p "$FACTS_DIR"
    mkdir -p "$DIAGRAMS_DIR"
    mkdir -p "$RECEIPTS_DIR"

    # Create receipts file if it doesn't exist
    touch "$RECEIPTS_DIR/observatory-receipts.jsonl"
}

# Clean output directory (optional)
clean_output_dirs() {
    info "Cleaning output directories..."

    if [[ "${CLEAN_OUTPUT:-0}" == "1" ]]; then
        rm -rf "$FACTS_DIR"/* "$DIAGRAMS_DIR"/* "$RECEIPTS_DIR"/*
        info "Cleaned all output directories"
    fi
}

# =============================================================================
# File Checksums
# =============================================================================

# Get file checksum
get_file_checksum() {
    local file="$1"
    if [[ -f "$file" ]]; then
        sha256sum "$file" | cut -d' ' -f1
    else
        echo ""
    fi
}

# Check if file changed
file_changed() {
    local file="$1"
    local checksum_file="$2"

    if [[ ! -f "$checksum_file" ]]; then
        return 0
    fi

    local current_checksum
    current_checksum=$(get_file_checksum "$file")
    local stored_checksum
    stored_checksum=$(cat "$checksum_file")

    [[ "$current_checksum" != "$stored_checksum" ]]
}

# Update file checksum
update_checksum() {
    local file="$1"
    local checksum_file="$2"

    if [[ -f "$file" ]]; then
        get_file_checksum "$file" > "$checksum_file"
    fi
}

# =============================================================================
# Progress Tracking
# =============================================================================

# Update progress
update_progress() {
    local step="$1"
    local total="$2"
    local message="$3"

    local percentage
    percentage=$(( (step * 100) / total ))
    printf "\rProgress: [%-50s] %d%% - %s" \
        "$(printf '#' $((percentage / 2)))" \
        "$percentage" \
        "$message"
}

# Complete progress line
complete_progress() {
    printf "\n"
}

# =============================================================================
# Error Handling
# =============================================================================

# Error trap
error_handler() {
    local exit_code=$?
    local line_number=$1
    local command=$2
    local error_message="${3:-Unknown error}"

    error "Error on line $line_number: '$command'"
    error "Error: $error_message"
    error "Exit code: $exit_code"

    # Clean up temporary files
    cleanup_temp_files

    exit "$exit_code"
}

# Set error trap
trap 'error_handler ${LINENO} "${BASH_COMMAND}"' ERR

# Cleanup function
cleanup_temp_files() {
    # Add any temporary file cleanup here
    :
}

# =============================================================================
# Initialization
# =============================================================================

# Initialize observatory environment
observatory_init() {
    debug "Initializing observatory..."

    # Check dependencies
    check_jq

    # Setup output directories
    setup_output_dirs

    debug "Observatory initialized successfully"
}