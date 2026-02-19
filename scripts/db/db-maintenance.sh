#!/bin/bash
#
# YAWL Database Maintenance Script
# Version: 1.0.0
#
# Performs routine database maintenance:
# - Vacuum analyze (PostgreSQL)
# - Index maintenance
# - Statistics collection
# - Archive cleanup
#
# Usage:
#   ./db-maintenance.sh [--dry-run] [--full]
#
# Options:
#   --dry-run    Show what would be done without executing
#   --full       Perform full maintenance (VACUUM FULL, REINDEX)
#
# Target: Sub-second query response, 99.99% uptime
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Default configuration
DRY_RUN=false
FULL_MAINTENANCE=false
LOG_FILE="/var/log/yawl/db-maintenance.log"

# Database connection (from environment or defaults)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-yawl}"
DB_USER="${DB_USER:-yawl}"
DB_PASSWORD="${DB_PASSWORD:-}"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --full)
            FULL_MAINTENANCE=true
            shift
            ;;
        --help)
            echo "Usage: $0 [--dry-run] [--full]"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Logging function
log() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[${timestamp}] $1" | tee -a "${LOG_FILE}"
}

# Execute SQL command
execute_sql() {
    local sql="$1"
    if [[ "$DRY_RUN" == "true" ]]; then
        log "[DRY-RUN] Would execute: $sql"
        return 0
    fi
    
    if [[ -n "$DB_PASSWORD" ]]; then
        PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "$sql"
    else
        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "$sql"
    fi
}

# Main maintenance function
main() {
    log "Starting database maintenance (dry-run=${DRY_RUN}, full=${FULL_MAINTENANCE})"
    
    local start_time=$(date +%s)
    
    # 1. Analyze tables for query optimization
    log "Step 1: Analyzing tables..."
    execute_sql "ANALYZE VERBOSE Work_Items;"
    execute_sql "ANALYZE VERBOSE RUNNER_STATES;"
    execute_sql "ANALYZE VERBOSE logEvent;"
    execute_sql "ANALYZE VERBOSE logTaskInstance;"
    execute_sql "ANALYZE VERBOSE auditEvent;"
    
    # 2. Vacuum tables (reclaim space)
    log "Step 2: Vacuuming tables..."
    if [[ "$FULL_MAINTENANCE" == "true" ]]; then
        # VACUUM FULL locks the table - only use during maintenance windows
        execute_sql "VACUUM FULL VERBOSE Work_Items;"
        execute_sql "VACUUM FULL VERBOSE logEvent;"
    else
        execute_sql "VACUUM VERBOSE Work_Items;"
        execute_sql "VACUUM VERBOSE RUNNER_STATES;"
        execute_sql "VACUUM VERBOSE logEvent;"
        execute_sql "VACUUM VERBOSE logTaskInstance;"
        execute_sql "VACUUM VERBOSE auditEvent;"
    fi
    
    # 3. Reindex if full maintenance
    if [[ "$FULL_MAINTENANCE" == "true" ]]; then
        log "Step 3: Reindexing tables..."
        execute_sql "REINDEX TABLE Work_Items;"
        execute_sql "REINDEX TABLE RUNNER_STATES;"
        execute_sql "REINDEX TABLE logEvent;"
        execute_sql "REINDEX TABLE logTaskInstance;"
    fi
    
    # 4. Check table bloat
    log "Step 4: Checking table bloat..."
    execute_sql "
        SELECT 
            schemaname || '.' || relname AS table,
            n_dead_tup AS dead_tuples,
            n_live_tup AS live_tuples,
            ROUND(100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0), 2) AS bloat_pct
        FROM pg_stat_user_tables
        WHERE n_dead_tup > 1000
        ORDER BY n_dead_tup DESC
        LIMIT 10;
    "
    
    # 5. Check index usage
    log "Step 5: Checking index usage..."
    execute_sql "
        SELECT 
            schemaname || '.' || relname AS table,
            indexrelname AS index,
            idx_scan AS scans,
            pg_size_pretty(pg_relation_size(indexrelid)) AS size
        FROM pg_stat_user_indexes
        WHERE idx_scan < 100
          AND pg_relation_size(indexrelid) > 1024 * 1024
        ORDER BY pg_relation_size(indexrelid) DESC
        LIMIT 10;
    "
    
    # 6. Update statistics
    log "Step 6: Updating statistics..."
    execute_sql "SELECT pg_stat_reset();"
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    log "Database maintenance completed in ${duration} seconds"
    
    # 7. Report health
    if [[ "$DRY_RUN" == "false" ]]; then
        log "Current table sizes:"
        execute_sql "
            SELECT 
                relname AS table,
                pg_size_pretty(pg_total_relation_size(relid)) AS total_size,
                n_live_tup AS rows
            FROM pg_stat_user_tables
            ORDER BY pg_total_relation_size(relid) DESC
            LIMIT 10;
        "
    fi
}

# Run main function
main "$@"
