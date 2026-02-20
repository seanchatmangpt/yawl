#!/bin/bash

# Test the validation system components
# Usage: bash scripts/validation/test-validation-system.sh

set -euo pipefail

# Source common library for functions
source "$(dirname "$0")/lib/common.sh"

log_section "Testing YAWL Validation System"
echo

# Test 1: Check common library
echo "1. Testing common library..."
    log_success "Common library loaded successfully"
    log_info "Platform: $(get_platform)"
    log_info "Timestamp: $(get_timestamp)"

# Test 2: Check API helpers
echo
echo "2. Testing API helpers..."
if source scripts/validation/docker/api-helpers.sh; then
    log_success "API helpers loaded successfully"

    # Check if functions exist
    if declare -f yawl_connect > /dev/null; then
        log_success "yawl_connect function exists"
    fi
    if declare -f yawl_upload_spec > /dev/null; then
        log_success "yawl_upload_spec function exists"
    fi
    if declare -f yawl_complete_case > /dev/null; then
        log_success "yawl_complete_case function exists"
    fi
else
    log_error "Failed to load API helpers"
    exit 1
fi

# Test 3: Check database helpers (if postgres is running)
echo
echo "3. Testing database helpers..."
if command -v docker > /dev/null; then
    # Check if postgres is running
    if docker ps | grep -q yawl-postgres; then
        if source scripts/validation/patterns/stateful/lib/db-helpers.sh; then
            log_success "Database helpers loaded successfully"

            # Test database connection
            if db_test_connection; then
                log_success "Database connection test passed"
            else
                log_error "Database connection failed"
            fi
        else
            log_error "Failed to load database helpers"
        fi
    else
        log_info "PostgreSQL not running, skipping database tests"
    fi
else
    log_info "Docker not available, skipping database tests"
fi

# Test 4: Check validation scripts
echo
echo "4. Checking validation scripts..."
validation_scripts=(
    "scripts/validation/validate-observatory.sh"
    "scripts/validation/validate-dx.sh"
    "scripts/validation/patterns/stateless/run-all-patterns.sh"
    "scripts/validation/patterns/stateless/validate-basic.sh"
    "scripts/validation/patterns/stateful/run-all-patterns.sh"
)

for script in "${validation_scripts[@]}"; do
    if [ -f "$script" ]; then
        log_success "Found script: $script"
        if [ -x "$script" ]; then
            log_success "Script is executable"
        else
            log_error "Script is not executable"
            chmod +x "$script"
        fi
    else
        log_error "Script not found: $script"
    fi
done

# Test 5: Check directory structure
echo
echo "5. Checking directory structure..."
required_dirs=(
    "scripts/validation/docker"
    "scripts/validation/lib"
    "scripts/validation/patterns/stateless"
    "scripts/validation/patterns/stateful/lib"
    "docs/v6/latest/validation"
    "docs/v6/latest/facts"
)

for dir in "${required_dirs[@]}"; do
    if [ -d "$dir" ]; then
        log_success "Directory exists: $dir"
    else
        log_error "Directory not found: $dir"
    fi
done

# Test 6: Check docker compose file
echo
echo "6. Checking Docker Compose..."
if [ -f "docker-compose.yml" ]; then
    log_success "Docker Compose file found"

    # Check if services are defined
    if grep -q "yawl-engine:" docker-compose.yml; then
        log_success "yawl-engine service defined"
    fi
    if grep -q "yawl-engine-prod:" docker-compose.yml; then
        log_success "yawl-engine-prod service defined"
    fi
    if grep -q "postgres:" docker-compose.yml; then
        log_success "postgres service defined"
    fi
else
    log_error "Docker Compose file not found"
fi

echo
log_header "Validation System Test Complete"
echo "All tests completed. Ready for full validation suite execution."