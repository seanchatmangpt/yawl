#!/bin/bash
#
# YAWL Database Backup Script
# Version: 1.0.0
#
# Performs automated database backup with verification and retention.
# Supports PostgreSQL, MySQL, and H2 databases.
#
# Usage:
#   ./db-backup.sh [--type=daily|weekly|monthly|manual] [--verify]
#
# Target: Automated backups, 99.99% uptime
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Configuration
BACKUP_TYPE="${BACKUP_TYPE:-daily}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/yawl}"
VERIFY_BACKUP="${VERIFY_BACKUP:-false}"
COMPRESS_BACKUP="${COMPRESS_BACKUP:-true}"

# Retention (days)
DAILY_RETENTION=7
WEEKLY_RETENTION=28
MONTHLY_RETENTION=365

# Database connection (from environment or defaults)
DB_TYPE="${DB_TYPE:-postgresql}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-yawl}"
DB_USER="${DB_USER:-yawl}"
DB_PASSWORD="${DB_PASSWORD:-}"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --type=*)
            BACKUP_TYPE="${1#*=}"
            shift
            ;;
        --verify)
            VERIFY_BACKUP=true
            shift
            ;;
        --help)
            echo "Usage: $0 [--type=daily|weekly|monthly|manual] [--verify]"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Setup
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
BACKUP_FILE="${BACKUP_DIR}/yawl_${BACKUP_TYPE}_${TIMESTAMP}.sql"
LOG_FILE="/var/log/yawl/backup.log"

mkdir -p "${BACKUP_DIR}"
mkdir -p "$(dirname "${LOG_FILE}")"

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${LOG_FILE}"
}

# PostgreSQL backup
backup_postgresql() {
    log "Starting PostgreSQL backup: ${BACKUP_FILE}"
    
    local options=(
        "-h" "${DB_HOST}"
        "-p" "${DB_PORT}"
        "-U" "${DB_USER}"
        "-F" "p"
        "-f" "${BACKUP_FILE}"
        "--verbose"
    )
    
    if [[ -n "${DB_PASSWORD}" ]]; then
        PGPASSWORD="${DB_PASSWORD}" pg_dump "${options[@]}" "${DB_NAME}"
    else
        pg_dump "${options[@]}" "${DB_NAME}"
    fi
    
    return $?
}

# MySQL backup
backup_mysql() {
    log "Starting MySQL backup: ${BACKUP_FILE}"
    
    local options=(
        "-h" "${DB_HOST}"
        "-P" "${DB_PORT}"
        "-u" "${DB_USER}"
        "--single-transaction"
        "--routines"
        "--triggers"
    )
    
    if [[ -n "${DB_PASSWORD}" ]]; then
        options+=("-p${DB_PASSWORD}")
    fi
    
    mysqldump "${options[@]}" "${DB_NAME}" > "${BACKUP_FILE}"
    return $?
}

# H2 backup
backup_h2() {
    log "Starting H2 backup: ${BACKUP_FILE}"
    
    # H2 backup via Java
    java -cp "${PROJECT_ROOT}/lib/*" \
        org.h2.tools.Script \
        -url "jdbc:h2:${DB_NAME}" \
        -user "${DB_USER}" \
        -password "${DB_PASSWORD}" \
        -script "${BACKUP_FILE}"
    
    return $?
}

# Verify backup integrity
verify_backup() {
    local file="$1"
    log "Verifying backup: ${file}"
    
    # Check file exists and has content
    if [[ ! -f "${file}" ]]; then
        log "ERROR: Backup file not found: ${file}"
        return 1
    fi
    
    local size=$(stat -f%z "${file}" 2>/dev/null || stat -c%s "${file}" 2>/dev/null || echo 0)
    if [[ ${size} -lt 1000 ]]; then
        log "ERROR: Backup file too small: ${size} bytes"
        return 1
    fi
    
    # Check for expected SQL content
    if ! grep -q -E "(CREATE TABLE|INSERT INTO)" "${file}" 2>/dev/null; then
        log "ERROR: Backup does not contain expected SQL content"
        return 1
    fi
    
    # Calculate checksum
    local checksum=$(sha256sum "${file}" | cut -d' ' -f1)
    log "Backup verified. Size: ${size} bytes, SHA256: ${checksum}"
    
    echo "${checksum}" > "${file}.sha256"
    return 0
}

# Compress backup
compress_backup() {
    local file="$1"
    log "Compressing backup: ${file}"
    
    gzip -f "${file}"
    
    if [[ -f "${file}.gz" ]]; then
        local compressed_size=$(stat -f%z "${file}.gz" 2>/dev/null || stat -c%s "${file}.gz" 2>/dev/null || echo 0)
        log "Compressed size: ${compressed_size} bytes"
        echo "${file}.gz"
    else
        echo "${file}"
    fi
}

# Enforce retention policy
enforce_retention() {
    log "Enforcing retention policy..."
    
    case "${BACKUP_TYPE}" in
        daily)
            find "${BACKUP_DIR}" -name "yawl_daily_*.sql.gz" -mtime +${DAILY_RETENTION} -delete 2>/dev/null || true
            find "${BACKUP_DIR}" -name "yawl_daily_*.sql" -mtime +${DAILY_RETENTION} -delete 2>/dev/null || true
            ;;
        weekly)
            find "${BACKUP_DIR}" -name "yawl_weekly_*.sql.gz" -mtime +${WEEKLY_RETENTION} -delete 2>/dev/null || true
            find "${BACKUP_DIR}" -name "yawl_weekly_*.sql" -mtime +${WEEKLY_RETENTION} -delete 2>/dev/null || true
            ;;
        monthly)
            find "${BACKUP_DIR}" -name "yawl_monthly_*.sql.gz" -mtime +${MONTHLY_RETENTION} -delete 2>/dev/null || true
            find "${BACKUP_DIR}" -name "yawl_monthly_*.sql" -mtime +${MONTHLY_RETENTION} -delete 2>/dev/null || true
            ;;
    esac
    
    log "Retention policy enforced"
}

# Main
main() {
    log "Starting YAWL database backup (type=${BACKUP_TYPE})"
    
    local start_time=$(date +%s)
    
    # Execute backup based on database type
    case "${DB_TYPE}" in
        postgresql|postgres|psql)
            backup_postgresql
            ;;
        mysql|mariadb)
            backup_mysql
            ;;
        h2)
            backup_h2
            ;;
        *)
            log "ERROR: Unsupported database type: ${DB_TYPE}"
            exit 1
            ;;
    esac
    
    if [[ $? -ne 0 ]]; then
        log "ERROR: Backup failed"
        exit 1
    fi
    
    # Verify backup
    if [[ "${VERIFY_BACKUP}" == "true" ]]; then
        if ! verify_backup "${BACKUP_FILE}"; then
            log "ERROR: Backup verification failed"
            exit 1
        fi
    fi
    
    # Compress backup
    local final_file="${BACKUP_FILE}"
    if [[ "${COMPRESS_BACKUP}" == "true" ]]; then
        final_file=$(compress_backup "${BACKUP_FILE}")
    fi
    
    # Enforce retention
    enforce_retention
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    log "Backup completed successfully in ${duration} seconds"
    log "Backup file: ${final_file}"
    
    # Send notification (if configured)
    if [[ -n "${NOTIFICATION_WEBHOOK:-}" ]]; then
        curl -s -X POST "${NOTIFICATION_WEBHOOK}" \
            -H "Content-Type: application/json" \
            -d "{\"type\":\"backup\",\"status\":\"success\",\"file\":\"${final_file}\",\"duration\":${duration}}" \
            > /dev/null 2>&1 || true
    fi
}

main "$@"
