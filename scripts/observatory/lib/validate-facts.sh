#!/bin/bash

# Validate all generated fact files
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FACTS_DIR="docs/v6/latest/facts"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Required fields for all fact files
REQUIRED_FIELDS=("generated_at" "generator" "data")

validate_json_file() {
    local file="$1"
    local filename=$(basename "$file")
    
    # Skip observatory system files
    if [[ "$filename" == "observatory-"* ]]; then
        return 0
    fi
    
    # Check if file exists and is not empty
    if [[ ! -f "$file" ]]; then
        log_error "File not found: $filename"
        return 1
    fi
    
    if [[ ! -s "$file" ]]; then
        log_error "File is empty: $filename"
        return 1
    fi
    
    # Validate JSON syntax
    if ! jq empty "$file" 2>/dev/null; then
        log_error "Invalid JSON syntax: $filename"
        return 1
    fi
    
    # Check required fields
    for field in "${REQUIRED_FIELDS[@]}"; do
        if ! jq -e ".${field} | length > 0" "$file" >/dev/null 2>&1; then
            log_error "Missing required field '$field': $filename"
            return 1
        fi
    done
    
    # Check if data field is not empty
    if ! jq -e ".data | length > 0" "$file" >/dev/null 2>&1; then
        log_error "Empty data field: $filename"
        return 1
    fi
    
    log_info "Validated: $filename"
    return 0
}

# Main validation
log_info "Starting fact validation at $TIMESTAMP"

# Find all JSON files in facts directory, excluding observatory files
json_files=()
while IFS= read -r -d '' file; do
    filename=$(basename "$file")
    if [[ "$filename" != "observatory-"* ]]; then
        json_files+=("$file")
    fi
done < <(find "$FACTS_DIR" -name "*.json" -print0)

if [[ ${#json_files[@]} -eq 0 ]]; then
    log_warn "No JSON fact files found in $FACTS_DIR"
    exit 0
fi

# Validate each file
failed_files=()
success_count=0

for file in "${json_files[@]}"; do
    if validate_json_file "$file"; then
        ((success_count++))
    else
        failed_files+=("$file")
    fi
done

# Summary
total_files=${#json_files[@]}
log_info "Validation complete: $success_count/$total_files fact files valid"

if [[ ${#failed_files[@]} -gt 0 ]]; then
    log_error "Failed validation for ${#failed_files[@]} fact files:"
    for file in "${failed_files[@]}"; do
        echo "  - $(basename "$file")"
    done
    exit 1
fi

log_info "All fact files are valid"
exit 0
