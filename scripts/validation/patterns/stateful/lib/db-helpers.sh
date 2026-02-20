#!/bin/bash

# YAWL Database Helper Functions for Stateful Validation
# Source this file to use the functions: source db-helpers.sh
# Provides database operations for verifying case persistence and rollback

set -euo pipefail

# Database connection variables
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-yawl}"
DB_USER="${DB_USER:-yawl}"
DB_PASS="${DB_PASS:-yawl_password}"
DB_CONTAINER="${DB_CONTAINER:-yawl-postgres}"

# Execute SQL query via docker exec
# Usage: db_query "SELECT * FROM case WHERE case_id = 'xxx';"
db_query() {
    local sql="$1"
    local timeout="${2:-30}"

    docker exec "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -t -A -c "$sql" 2>/dev/null
}

# Check if case exists in database
# Usage: if db_case_exists "CASE-001"; then ...
db_case_exists() {
    local case_id="$1"

    local result=$(db_query "SELECT COUNT(*) FROM case WHERE case_id = '$case_id';")
    local count=$(echo "$result" | tr -d '[:space:]')

    if [ "$count" -gt 0 ] 2>/dev/null; then
        return 0
    else
        return 1
    fi
}

# Get case status from database
# Usage: status=$(db_case_status "CASE-001")
db_case_status() {
    local case_id="$1"

    local result=$(db_query "SELECT status FROM case WHERE case_id = '$case_id';")
    echo "$result" | tr -d '[:space:]'
}

# Verify case persistence after completion
# Usage: if db_verify_persistence "CASE-001"; then ...
db_verify_persistence() {
    local case_id="$1"

    log_info "Verifying case persistence in database: $case_id"

    # Check if case exists
    if ! db_case_exists "$case_id"; then
        log_error "Case not persisted in database: $case_id"
        return 1
    fi

    # Check case status is complete
    local status=$(db_case_status "$case_id")
    if [ "$status" != "complete" ]; then
        log_error "Case not complete in database: $case_id (status: $status)"
        return 1
    fi

    # Verify work items exist for the case
    local workitem_count=$(db_get_work_item_count "$case_id")
    if [ "$workitem_count" -lt 1 ]; then
        log_error "No work items found for case: $case_id"
        return 1
    fi

    log_info "Case successfully persisted and complete: $case_id (work items: $workitem_count)"
    return 0
}

# Verify cancellation rollback worked correctly
# Usage: if db_verify_cancellation "CASE-001"; then ...
db_verify_cancellation() {
    local case_id="$1"

    log_info "Verifying case cancellation in database: $case_id"

    # Check if case exists
    if ! db_case_exists "$case_id"; then
        log_error "Case not found after cancellation: $case_id"
        return 1
    fi

    # Check case status is terminated or cancelled
    local status=$(db_case_status "$case_id")
    if [ "$status" != "terminated" ] && [ "$status" != "cancelled" ]; then
        log_error "Case not terminated/cancelled in database: $case_id (status: $status)"
        return 1
    fi

    # Verify active work items were cleaned up
    local active_items=$(db_query "SELECT COUNT(*) FROM workitem WHERE case_id = '$case_id' AND status = 'active';")
    if [ "$active_items" -gt 0 ] 2>/dev/null; then
        log_warn "Found $active_items active work items for cancelled case: $case_id"
    fi

    log_info "Case successfully cancelled and terminated: $case_id"
    return 0
}

# Get work item count for a case
# Usage: count=$(db_get_work_item_count "CASE-001")
db_get_work_item_count() {
    local case_id="$1"

    local result=$(db_query "SELECT COUNT(*) FROM workitem WHERE case_id = '$case_id';")
    echo "$result" | tr -d '[:space:]'
}

# Get case creation timestamp
# Usage: created=$(db_get_case_created_at "CASE-001")
db_get_case_created_at() {
    local case_id="$1"

    local result=$(db_query "SELECT created_at FROM case WHERE case_id = '$case_id';")
    echo "$result"
}

# Get case completion timestamp
# Usage: completed=$(db_get_case_completed_at "CASE-001")
db_get_case_completed_at() {
    local case_id="$1"

    local result=$(db_query "SELECT completed_at FROM case WHERE case_id = '$case_id';")
    echo "$result"
}

# Get specification ID for a case
# Usage: spec_id=$(db_get_case_specification "CASE-001")
db_get_case_specification() {
    local case_id="$1"

    local result=$(db_query "SELECT specification_id FROM case WHERE case_id = '$case_id';")
    echo "$result"
}

# Get all cases for a specification
# Usage: cases=$(db_get_cases_for_spec "WCP-01-Sequence")
db_get_cases_for_spec() {
    local spec_uri="$1"

    db_query "SELECT case_id, status, created_at FROM case WHERE specification_id = '$spec_uri' ORDER BY created_at DESC;"
}

# Check database connectivity
# Usage: if db_test_connection; then ...
db_test_connection() {
    if docker exec "$DB_CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" 2>/dev/null; then
        return 0
    else
        log_error "Database connection failed"
        return 1
    fi
}

# Wait for database to be ready
# Usage: wait_for_database 30
wait_for_database() {
    local timeout="${1:-30}"
    local interval="${2:-2}"
    local elapsed=0

    log_info "Waiting for database to be ready..."

    while [ $elapsed -lt $timeout ]; do
        if db_test_connection 2>/dev/null; then
            log_info "Database connection established"
            return 0
        fi
        sleep $interval
        elapsed=$((elapsed + interval))
    done

    log_error "Database not ready after ${timeout} seconds"
    return 1
}

# Clean up test data from database
# Usage: db_cleanup_test_data
db_cleanup_test_data() {
    log_info "Cleaning up test data from database..."

    # Delete test cases and related data
    db_query "DELETE FROM workitem WHERE case_id LIKE 'CASE-%' OR case_id LIKE 'CASE-ENT-%';" 2>/dev/null || true
    db_query "DELETE FROM case WHERE case_id LIKE 'CASE-%' OR case_id LIKE 'CASE-ENT-%';" 2>/dev/null || true
    db_query "DELETE FROM caseexec WHERE case_id LIKE 'CASE-%' OR case_id LIKE 'CASE-ENT-%';" 2>/dev/null || true
    db_query "DELETE FROM specification WHERE uri LIKE '%-Stateful';" 2>/dev/null || true

    log_info "Test data cleanup completed"
}

# Get database size in bytes
# Usage: size=$(db_get_size)
db_get_size() {
    local result=$(db_query "SELECT pg_database_size('$DB_NAME');" 2>/dev/null || echo "0")
    echo "$result" | tr -d '[:space:]'
}

# Get table row counts for verification
# Usage: db_get_table_stats
db_get_table_stats() {
    echo "Database Statistics:"
    db_query "SELECT 'case' as table_name, COUNT(*) as row_count FROM case
              UNION ALL
              SELECT 'workitem', COUNT(*) FROM workitem
              UNION ALL
              SELECT 'specification', COUNT(*) FROM specification;"
}

# Verify database integrity
# Usage: if db_verify_integrity; then ...
db_verify_integrity() {
    log_info "Verifying database integrity..."

    # Check for orphaned work items (work items without corresponding case)
    local orphaned=$(db_query "SELECT COUNT(*) FROM workitem w LEFT JOIN case c ON w.case_id = c.case_id WHERE c.case_id IS NULL;" 2>/dev/null || echo "0")
    orphaned=$(echo "$orphaned" | tr -d '[:space:]')

    if [ "$orphaned" -gt 0 ] 2>/dev/null; then
        log_error "Found $orphaned orphaned work items"
        return 1
    fi

    # Check for invalid case status values
    local invalid_status=$(db_query "SELECT COUNT(*) FROM case WHERE status NOT IN ('active', 'complete', 'terminated', 'cancelled', 'suspended');" 2>/dev/null || echo "0")
    invalid_status=$(echo "$invalid_status" | tr -d '[:space:]')

    if [ "$invalid_status" -gt 0 ] 2>/dev/null; then
        log_error "Found $invalid_status cases with invalid status"
        return 1
    fi

    log_info "Database integrity verified"
    return 0
}

# Get performance metrics from database
# Usage: db_get_metrics "CASE-001"
db_get_metrics() {
    local case_id="$1"

    db_query "SELECT
        COUNT(*) as workitem_count,
        MAX(created_at) as last_created,
        MIN(created_at) as first_created,
        COUNT(CASE WHEN status = 'complete' THEN 1 END) as completed_items
    FROM workitem
    WHERE case_id = '$case_id';"
}

# Backup database
# Usage: backup_file=$(db_backup)
db_backup() {
    local backup_file="/tmp/yawl_test_backup_$(date +%Y%m%d_%H%M%S).sql"

    log_info "Creating database backup: $backup_file"
    docker exec "$DB_CONTAINER" pg_dump -U "$DB_USER" -d "$DB_NAME" > "$backup_file" 2>/dev/null

    if [ -f "$backup_file" ] && [ -s "$backup_file" ]; then
        log_info "Database backup created successfully"
        echo "$backup_file"
        return 0
    else
        log_error "Database backup failed"
        return 1
    fi
}

# Restore database from backup
# Usage: db_restore "/tmp/backup.sql"
db_restore() {
    local backup_file="$1"

    if [ ! -f "$backup_file" ]; then
        log_error "Backup file not found: $backup_file"
        return 1
    fi

    log_info "Restoring database from backup: $backup_file"
    docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" < "$backup_file" 2>/dev/null

    if [ $? -eq 0 ]; then
        log_info "Database restored successfully"
        return 0
    else
        log_error "Database restore failed"
        return 1
    fi
}

# Initialize database for testing
# Usage: db_init_test
db_init_test() {
    log_info "Initializing database for testing..."

    # Wait for database to be ready
    if ! wait_for_database 30; then
        log_error "Database initialization failed - cannot connect"
        return 1
    fi

    # Clean up any existing test data
    db_cleanup_test_data

    # Verify integrity
    db_verify_integrity 2>/dev/null || true

    log_info "Database initialization completed"
}

# Get recent cases with their status
# Usage: db_get_recent_cases 10
db_get_recent_cases() {
    local limit="${1:-10}"

    db_query "SELECT case_id, status, created_at, completed_at
              FROM case
              ORDER BY created_at DESC
              LIMIT $limit;"
}

# Check if specification is loaded
# Usage: if db_specification_exists "WCP-01-Sequence"; then ...
db_specification_exists() {
    local spec_uri="$1"

    local result=$(db_query "SELECT COUNT(*) FROM specification WHERE uri = '$spec_uri';")
    local count=$(echo "$result" | tr -d '[:space:]')

    [ "$count" -gt 0 ] 2>/dev/null
}

# Export functions for use in other scripts
export -f db_query db_case_exists db_case_status db_verify_persistence db_verify_cancellation
export -f db_get_work_item_count db_get_case_created_at db_test_connection wait_for_database
export -f db_cleanup_test_data db_get_size db_verify_integrity db_init_test
