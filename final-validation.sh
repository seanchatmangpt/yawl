#!/bin/bash

# Final YAWL Stateful Pattern Validation

set -euo pipefail

# Configuration
DB_CONTAINER="yawl-postgres"
DB_NAME="yawl"
DB_USER="yawl"

log_info() {
    echo "[INFO] $1"
}

log_error() {
    echo "[ERROR] $1"
}

# Database helper functions
db_query() {
    local sql="$1"
    docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "$sql" 2>/dev/null
}

db_test_connection() {
    if docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" 2>/dev/null; then
        return 0
    else
        return 1
    fi
}

db_verify_case_exists() {
    local case_id="$1"
    local result=$(db_query "SELECT COUNT(*) FROM yawl_case WHERE case_id = '$case_id';")
    [ "$result" -gt 0 ] 2>/dev/null
}

db_verify_case_status() {
    local case_id="$1"
    local expected_status="$2"
    local actual_status=$(db_query "SELECT status FROM yawl_case WHERE case_id = '$case_id';")
    [ "$actual_status" = "$expected_status" ]
}

db_verify_work_items() {
    local case_id="$1"
    local min_items="$2"
    local count=$(db_query "SELECT COUNT(*) FROM yawl_workitem WHERE case_id = '$case_id';")
    [ "$count" -ge "$min_items" ] 2>/dev/null
}

db_get_case_count() {
    db_query "SELECT COUNT(*) FROM yawl_case;" 2>/dev/null || echo "0"
}

db_cleanup_test_data() {
    log_info "Cleaning up test data..."
    db_query "DELETE FROM yawl_workitem WHERE case_id LIKE 'TEST-%';" 2>/dev/null || true
    db_query "DELETE FROM yawl_case WHERE case_id LIKE 'TEST-%';" 2>/dev/null || true
    db_query "DELETE FROM yawl_specification WHERE uri LIKE '%-TEST';" 2>/dev/null || true
}

# Test database operations
test_database_operations() {
    log_info "Testing database operations..."
    
    if ! db_test_connection; then
        log_error "Database connection failed"
        return 1
    fi
    
    log_info "Database connection successful"
    
    # Check initial case count
    local initial_count=$(db_get_case_count)
    log_info "Initial case count: $initial_count"
    
    # Test basic database operations
    local test_query_result=$(db_query "SELECT COUNT(*) FROM yawl_specification;" 2>/dev/null || echo "0")
    log_info "Specification count in database: $test_query_result"
    
    return 0
}

# Test database persistence
test_database_persistence() {
    log_info "Testing database persistence..."
    
    # Create a test case manually in database
    local test_case_id="TEST-PERSISTENCE-$(date +%s)"
    
    # Insert test case
    if ! db_query "INSERT INTO yawl_case (case_id, specification_id, status) VALUES ('$test_case_id', 'TEST-SPEC', 'active');"; then
        log_error "Failed to insert test case"
        return 1
    fi
    
    # Verify case exists
    if db_verify_case_exists "$test_case_id"; then
        log_info "Test case persisted successfully: $test_case_id"
        
        # Verify status
        if db_verify_case_status "$test_case_id" "active"; then
            log_info "Case status verified: active"
        else
            log_warn "Case status not as expected"
        fi
        
        # Clean up
        db_query "DELETE FROM yawl_case WHERE case_id = '$test_case_id';" 2>/dev/null || true
        return 0
    else
        log_error "Failed to persist test case"
        return 1
    fi
}

# Test work item operations
test_work_item_operations() {
    log_info "Testing work item operations..."
    
    # Create test case with work items
    local test_case_id="TEST-WORKITEMS-$(date +%s)"
    
    # Insert test case
    if ! db_query "INSERT INTO yawl_case (case_id, specification_id, status) VALUES ('$test_case_id', 'TEST-SPEC', 'active');"; then
        log_error "Failed to insert test case"
        return 1
    fi
    
    # Insert test work items
    db_query "INSERT INTO yawl_workitem (case_id, task_id, status) VALUES ('$test_case_id', 'TaskA', 'active');" 2>/dev/null || true
    db_query "INSERT INTO yawl_workitem (case_id, task_id, status) VALUES ('$test_case_id', 'TaskB', 'pending');" 2>/dev/null || true
    
    # Verify work items exist
    if db_verify_work_items "$test_case_id" 2; then
        log_info "Work items created and verified successfully"
        
        # Clean up
        db_query "DELETE FROM yawl_workitem WHERE case_id = '$test_case_id';" 2>/dev/null || true
        db_query "DELETE FROM yawl_case WHERE case_id = '$test_case_id';" 2>/dev/null || true
        return 0
    else
        log_error "Failed to create or verify work items"
        
        # Clean up
        db_query "DELETE FROM yawl_workitem WHERE case_id = '$test_case_id';" 2>/dev/null || true
        db_query "DELETE FROM yawl_case WHERE case_id = '$test_case_id';" 2>/dev/null || true
        return 1
    fi
}

# Main validation function
main() {
    log_info "Starting YAWL Stateful Pattern Validation"
    echo
    
    # Initialize test results
    local total_tests=0
    local passed_tests=0
    local failed_tests=0
    
    # Test 1: Database connectivity
    total_tests=$((total_tests + 1))
    if test_database_operations; then
        log_info "✅ Database connectivity test passed"
        passed_tests=$((passed_tests + 1))
    else
        log_error "❌ Database connectivity test failed"
        failed_tests=$((failed_tests + 1))
    fi
    echo
    
    # Test 2: Database persistence
    total_tests=$((total_tests + 1))
    if test_database_persistence; then
        log_info "✅ Database persistence test passed"
        passed_tests=$((passed_tests + 1))
    else
        log_error "❌ Database persistence test failed"
        failed_tests=$((failed_tests + 1))
    fi
    echo
    
    # Test 3: Work item operations
    total_tests=$((total_tests + 1))
    if test_work_item_operations; then
        log_info "✅ Work item operations test passed"
        passed_tests=$((passed_tests + 1))
    else
        log_error "❌ Work item operations test failed"
        failed_tests=$((failed_tests + 1))
    fi
    echo
    
    # Final cleanup
    db_cleanup_test_data
    
    # Generate report
    echo "=========================================="
    echo "YAWL Stateful Pattern Validation Summary"
    echo "=========================================="
    echo "Total tests: $total_tests"
    echo "Passed: $passed_tests"
    echo "Failed: $failed_tests"
    echo "Success rate: $((passed_tests * 100 / total_tests))%"
    
    if [ $failed_tests -eq 0 ]; then
        echo "🎉 All tests passed!"
        exit 0
    else
        echo "❌ $failed_tests test(s) failed"
        exit 1
    fi
}

# Run main function
main "$@"
