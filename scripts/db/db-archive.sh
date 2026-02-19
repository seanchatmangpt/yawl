#!/bin/bash
#
# YAWL Data Archiving Script
# Version: 1.0.0
#
# Archives old log data to maintain database performance.
# Implements time-based archiving for high-growth tables.
#
# Usage:
#   ./db-archive.sh [--dry-run] [--retention-days=90]
#
# Target: Keep production tables under 10M rows for optimal performance
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Configuration
DRY_RUN=false
LOG_RETENTION_DAYS=90
AUDIT_RETENTION_DAYS=365
WORKITEM_RETENTION_DAYS=30
BATCH_SIZE=10000
ARCHIVE_DIR="${ARCHIVE_DIR:-/var/archive/yawl}"

# Database connection
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-yawl}"
DB_USER="${DB_USER:-yawl}"
DB_PASSWORD="${DB_PASSWORD:-}"

LOG_FILE="/var/log/yawl/archive.log"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --retention-days=*)
            LOG_RETENTION_DAYS="${1#*=}"
            shift
            ;;
        --help)
            echo "Usage: $0 [--dry-run] [--retention-days=N]"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

mkdir -p "${ARCHIVE_DIR}"
mkdir -p "$(dirname "${LOG_FILE}")"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${LOG_FILE}"
}

# Execute SQL
execute_sql() {
    local sql="$1"
    if [[ -n "${DB_PASSWORD}" ]]; then
        PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t -A -c "${sql}"
    else
        psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -t -A -c "${sql}"
    fi
}

# Calculate cutoff epoch (milliseconds)
calculate_cutoff() {
    local days=$1
    python3 -c "import time; print(int((time.time() - ${days} * 86400) * 1000))"
}

# Archive table data
archive_table() {
    local table=$1
    local time_column=$2
    local retention_days=$3
    local archive_table="${table}_archive"
    
    local cutoff_epoch=$(calculate_cutoff "${retention_days}")
    
    log "Archiving ${table} older than ${retention_days} days (epoch < ${cutoff_epoch})"
    
    # Count records to archive
    local count=$(execute_sql "SELECT COUNT(*) FROM ${table} WHERE ${time_column} < ${cutoff_epoch};")
    log "Found ${count} records to archive from ${table}"
    
    if [[ "${count}" -eq 0 ]]; then
        log "No records to archive from ${table}"
        return 0
    fi
    
    if [[ "${DRY_RUN}" == "true" ]]; then
        log "[DRY-RUN] Would archive ${count} records from ${table}"
        return 0
    fi
    
    # Create archive table if not exists
    execute_sql "CREATE TABLE IF NOT EXISTS ${archive_table} (LIKE ${table} INCLUDING ALL);"
    execute_sql "ALTER TABLE ${archive_table} ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;"
    
    # Archive in batches
    local archived=0
    while true; do
        # Insert batch into archive
        local inserted=$(execute_sql "
            INSERT INTO ${archive_table}
            SELECT *, CURRENT_TIMESTAMP FROM ${table}
            WHERE ${time_column} < ${cutoff_epoch}
            AND NOT EXISTS (
                SELECT 1 FROM ${archive_table} WHERE ${archive_table}.id = ${table}.id
            )
            LIMIT ${BATCH_SIZE}
            RETURNING 1;
        " | wc -l)
        
        if [[ ${inserted} -eq 0 ]]; then
            break
        fi
        
        # Delete archived records from main table
        execute_sql "
            DELETE FROM ${table}
            WHERE ${time_column} < ${cutoff_epoch}
            AND id IN (
                SELECT id FROM ${archive_table}
                WHERE archived_at >= CURRENT_TIMESTAMP - INTERVAL '1 minute'
            );
        "
        
        archived=$((archived + inserted))
        log "Archived ${archived}/${count} records from ${table}"
        
        # Small delay to reduce load
        sleep 0.1
    done
    
    log "Completed archiving ${archived} records from ${table}"
}

# Export archive to file
export_archive() {
    local table=$1
    local archive_table="${table}_archive"
    local export_file="${ARCHIVE_DIR}/${archive_table}_$(date '+%Y%m%d').sql"
    
    log "Exporting ${archive_table} to ${export_file}"
    
    if [[ "${DRY_RUN}" == "true" ]]; then
        log "[DRY-RUN] Would export ${archive_table}"
        return 0
    fi
    
    if [[ -n "${DB_PASSWORD}" ]]; then
        PGPASSWORD="${DB_PASSWORD}" pg_dump \
            -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
            -t "${archive_table}" "${DB_NAME}" > "${export_file}"
    else
        pg_dump \
            -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
            -t "${archive_table}" "${DB_NAME}" > "${export_file}"
    fi
    
    # Compress
    gzip -f "${export_file}"
    
    log "Exported and compressed: ${export_file}.gz"
}

# Cleanup completed work items
cleanup_workitems() {
    local cutoff_date=$(date -d "-${WORKITEM_RETENTION_DAYS} days" '+%Y-%m-%d %H:%M:%S')
    
    log "Cleaning up work items completed before ${cutoff_date}"
    
    # Count items to delete
    local count=$(execute_sql "
        SELECT COUNT(*) FROM Work_Items
        WHERE status IN ('Completed', 'Failed', 'Cancelled')
        AND enablement_time < '${cutoff_date}';
    ")
    
    log "Found ${count} completed work items to cleanup"
    
    if [[ "${DRY_RUN}" == "true" ]]; then
        log "[DRY-RUN] Would delete ${count} work items"
        return 0
    fi
    
    # Delete in batches
    while true; do
        local deleted=$(execute_sql "
            DELETE FROM Work_Items
            WHERE status IN ('Completed', 'Failed', 'Cancelled')
            AND enablement_time < '${cutoff_date}'
            AND thisid IN (
                SELECT thisid FROM Work_Items
                WHERE status IN ('Completed', 'Failed', 'Cancelled')
                AND enablement_time < '${cutoff_date}'
                LIMIT ${BATCH_SIZE}
            )
            RETURNING 1;
        " | wc -l)
        
        if [[ ${deleted} -eq 0 ]]; then
            break
        fi
        
        log "Deleted ${deleted} work items"
        sleep 0.1
    done
}

# Report statistics
report_stats() {
    log "Current table statistics:"
    
    for table in Work_Items RUNNER_STATES logEvent logTaskInstance auditEvent; do
        local count=$(execute_sql "SELECT COUNT(*) FROM ${table};" 2>/dev/null || echo "N/A")
        log "  ${table}: ${count} rows"
    done
    
    # Archive table sizes
    log "Archive table statistics:"
    for table in logEvent_archive logTaskInstance_archive auditEvent_archive; do
        local count=$(execute_sql "SELECT COUNT(*) FROM ${table};" 2>/dev/null || echo "0")
        log "  ${table}: ${count} rows"
    done
}

# Main
main() {
    log "Starting data archiving (dry-run=${DRY_RUN})"
    log "Retention: log=${LOG_RETENTION_DAYS}d, audit=${AUDIT_RETENTION_DAYS}d, workitems=${WORKITEM_RETENTION_DAYS}d"
    
    local start_time=$(date +%s)
    
    # Archive log events
    archive_table "logEvent" "eventTime" "${LOG_RETENTION_DAYS}"
    
    # Archive task instances
    archive_table "logTaskInstance" "taskInstanceID" "${LOG_RETENTION_DAYS}"
    
    # Archive audit events
    archive_table "auditEvent" "eventTime" "${AUDIT_RETENTION_DAYS}"
    
    # Cleanup work items
    cleanup_workitems
    
    # Export archives to files (weekly)
    if [[ $(date '+%u') -eq 7 ]]; then
        log "Weekly archive export"
        export_archive "logEvent"
        export_archive "logTaskInstance"
        export_archive "auditEvent"
    fi
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    log "Archiving completed in ${duration} seconds"
    
    # Report
    report_stats
}

main "$@"
