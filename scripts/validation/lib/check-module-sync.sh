#!/usr/bin/env bash
set -euo pipefail
#
# Module Sync Validation Script
# Verifies that declared modules match actual directories in Maven project
#

# Source common utilities
source "$(dirname "$0")/common.sh"

log_section "Checking Module Sync"

# Expected modules from pom.xml parent modules
declare -a expected_modules=(
    "yawl-utilities"
    "yawl-elements"
    "yawl-authentication"
    "yawl-engine"
    "yawl-stateless"
    "yawl-resourcing"
    "yawl-scheduling"
    "yawl-security"
    "yawl-integration"
    "yawl-monitoring"
    "yawl-webapps"
    "yawl-control-panel"
)

# Find actual directories
declare -a actual_modules
while IFS= read -r -d '' dir; do
    # Extract module name from directory path
    module_name=$(basename "$dir")
    # Skip directories that don't match the naming pattern
    if [[ "$module_name" =~ ^yawl- ]]; then
        actual_modules+=("$module_name")
    fi
done < <(find . -maxdepth 2 -type d -name "yawl-*" -not -path "./.git/*" -not -path "./node_modules/*" -not -path "./venv/*" -print0)

# Check for missing modules
declare -a missing_modules
for expected in "${expected_modules[@]}"; do
    found=false
    for actual in "${actual_modules[@]}"; do
        if [[ "$expected" == "$actual" ]]; then
            found=true
            break
        fi
    done
    if [[ "$found" == "false" ]]; then
        missing_modules+=("$expected")
    fi
done

# Check for extra modules
declare -a extra_modules
for actual in "${actual_modules[@]}"; do
    found=false
    for expected in "${expected_modules[@]}"; do
        if [[ "$expected" == "$actual" ]]; then
            found=true
            break
        fi
    done
    if [[ "$found" == "false" ]]; then
        extra_modules+=("$actual")
    fi
done

# Log results
log_test "PASS" "Found ${#actual_modules[@]} actual modules" "module-sync"

if [[ ${#missing_modules[@]} -gt 0 ]]; then
    log_warning "Missing declared modules: ${missing_modules[*]}"
    log_test "WARN" "Missing modules detected" "module-sync-missing"
fi

if [[ ${#extra_modules[@]} -gt 0 ]]; then
    log_warning "Extra modules not declared in pom.xml: ${extra_modules[*]}"
    log_test "WARN" "Extra modules detected" "module-sync-extra"
fi

# Output results
if [[ ${#missing_modules[@]} -eq 0 && ${#extra_modules[@]} -eq 0 ]]; then
    log_success "Module sync validation passed - all declared modules exist and match directories"
else
    log_error "Module sync validation failed - discrepancies found"
fi

# Output JSON if requested
if [[ "$1" == "--json" ]]; then
    output_json "module-sync-results.json"
fi