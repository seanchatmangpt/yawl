#!/usr/bin/env bash
# =============================================================================
# YAWL Database Backup Script
# =============================================================================
# Creates and manages PostgreSQL backups for Docker Compose or Kubernetes.
#
# Usage:
#   ./backup.sh create [docker|k8s] [namespace]
#   ./backup.sh restore <backup-file> [docker|k8s]
#   ./backup.sh list [docker|k8s]
#   ./backup.sh prune <days> [docker|k8s]
#
# Examples:
#   ./backup.sh create docker                          # Backup Docker DB
#   ./backup.sh create k8s yawl                        # Backup K8s DB
#   ./backup.sh list docker                            # List Docker backups
#   ./backup.sh restore ./backups/yawl_20260214.sql.gz docker
#   ./backup.sh prune 30 docker                        # Remove backups > 30 days
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-${SCRIPT_DIR}/../../backups}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Database defaults (override via environment)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-yawl}"
DB_NAME="${DB_NAME:-yawl}"
DB_PASSWORD="${DB_PASSWORD:-yawl123}"

mkdir -p "$BACKUP_DIR"

create_docker_backup() {
    local backup_file="${BACKUP_DIR}/yawl_${TIMESTAMP}.sql.gz"
    echo "Creating Docker Compose database backup..."
    echo "  Target: ${backup_file}"

    docker exec -e PGPASSWORD="${DB_PASSWORD}" yawl-postgres \
        pg_dump -U "${DB_USER}" -d "${DB_NAME}" --no-owner --no-privileges \
        | gzip > "${backup_file}"

    local size
    size=$(stat -c%s "${backup_file}" 2>/dev/null || stat -f%z "${backup_file}" 2>/dev/null || echo "unknown")
    echo "  Backup completed: ${size} bytes"
    echo "  File: ${backup_file}"
}

create_k8s_backup() {
    local namespace="${1:-yawl}"
    local backup_file="${BACKUP_DIR}/yawl_k8s_${namespace}_${TIMESTAMP}.sql.gz"
    echo "Creating Kubernetes database backup..."
    echo "  Namespace: ${namespace}"
    echo "  Target: ${backup_file}"

    # Find the PostgreSQL pod
    local pg_pod
    pg_pod=$(kubectl get pods -n "${namespace}" -l app.kubernetes.io/name=postgresql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)

    if [ -z "$pg_pod" ]; then
        echo "ERROR: PostgreSQL pod not found in namespace '${namespace}'"
        exit 1
    fi

    echo "  PostgreSQL pod: ${pg_pod}"

    kubectl exec -n "${namespace}" "${pg_pod}" -- \
        pg_dump -U "${DB_USER}" -d "${DB_NAME}" --no-owner --no-privileges \
        | gzip > "${backup_file}"

    local size
    size=$(stat -c%s "${backup_file}" 2>/dev/null || stat -f%z "${backup_file}" 2>/dev/null || echo "unknown")
    echo "  Backup completed: ${size} bytes"
    echo "  File: ${backup_file}"
}

restore_docker_backup() {
    local backup_file="$1"

    if [ ! -f "$backup_file" ]; then
        echo "ERROR: Backup file not found: ${backup_file}"
        exit 1
    fi

    echo "Restoring Docker Compose database from backup..."
    echo "  Source: ${backup_file}"
    echo ""
    echo "  WARNING: This will overwrite the current database."
    read -p "  Continue? (y/N): " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        echo "  Aborted."
        exit 0
    fi

    echo "  Dropping and recreating database..."
    docker exec -e PGPASSWORD="${DB_PASSWORD}" yawl-postgres \
        psql -U "${DB_USER}" -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${DB_NAME}' AND pid <> pg_backend_pid();" postgres
    docker exec -e PGPASSWORD="${DB_PASSWORD}" yawl-postgres \
        psql -U "${DB_USER}" -c "DROP DATABASE IF EXISTS ${DB_NAME};" postgres
    docker exec -e PGPASSWORD="${DB_PASSWORD}" yawl-postgres \
        psql -U "${DB_USER}" -c "CREATE DATABASE ${DB_NAME};" postgres

    echo "  Restoring data..."
    gunzip -c "${backup_file}" | docker exec -i -e PGPASSWORD="${DB_PASSWORD}" yawl-postgres \
        psql -U "${DB_USER}" -d "${DB_NAME}"

    echo "  Restore completed."
}

restore_k8s_backup() {
    local backup_file="$1"
    local namespace="${2:-yawl}"

    if [ ! -f "$backup_file" ]; then
        echo "ERROR: Backup file not found: ${backup_file}"
        exit 1
    fi

    echo "Restoring Kubernetes database from backup..."
    echo "  Namespace: ${namespace}"
    echo "  Source: ${backup_file}"
    echo ""
    echo "  WARNING: This will overwrite the current database."
    read -p "  Continue? (y/N): " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        echo "  Aborted."
        exit 0
    fi

    local pg_pod
    pg_pod=$(kubectl get pods -n "${namespace}" -l app.kubernetes.io/name=postgresql -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)

    if [ -z "$pg_pod" ]; then
        echo "ERROR: PostgreSQL pod not found in namespace '${namespace}'"
        exit 1
    fi

    echo "  Restoring to pod: ${pg_pod}"

    kubectl exec -n "${namespace}" "${pg_pod}" -- \
        psql -U "${DB_USER}" -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${DB_NAME}' AND pid <> pg_backend_pid();" postgres
    kubectl exec -n "${namespace}" "${pg_pod}" -- \
        psql -U "${DB_USER}" -c "DROP DATABASE IF EXISTS ${DB_NAME};" postgres
    kubectl exec -n "${namespace}" "${pg_pod}" -- \
        psql -U "${DB_USER}" -c "CREATE DATABASE ${DB_NAME};" postgres

    gunzip -c "${backup_file}" | kubectl exec -i -n "${namespace}" "${pg_pod}" -- \
        psql -U "${DB_USER}" -d "${DB_NAME}"

    echo "  Restore completed."
}

list_backups() {
    echo "=========================================="
    echo "  YAWL Database Backups"
    echo "  Directory: ${BACKUP_DIR}"
    echo "=========================================="
    echo ""

    if [ ! -d "$BACKUP_DIR" ] || [ -z "$(ls -A "$BACKUP_DIR"/*.sql.gz 2>/dev/null)" ]; then
        echo "  No backups found."
        return
    fi

    printf "  %-40s %10s  %s\n" "FILENAME" "SIZE" "DATE"
    printf "  %-40s %10s  %s\n" "--------" "----" "----"

    for f in "$BACKUP_DIR"/*.sql.gz; do
        local fname size date_str
        fname=$(basename "$f")
        size=$(stat -c%s "$f" 2>/dev/null || stat -f%z "$f" 2>/dev/null || echo "?")
        date_str=$(stat -c%y "$f" 2>/dev/null | cut -d. -f1 || stat -f%Sm "$f" 2>/dev/null || echo "?")

        # Human-readable size
        if [ "$size" -gt 1073741824 ] 2>/dev/null; then
            size="$(echo "scale=1; $size/1073741824" | bc)G"
        elif [ "$size" -gt 1048576 ] 2>/dev/null; then
            size="$(echo "scale=1; $size/1048576" | bc)M"
        elif [ "$size" -gt 1024 ] 2>/dev/null; then
            size="$(echo "scale=1; $size/1024" | bc)K"
        else
            size="${size}B"
        fi

        printf "  %-40s %10s  %s\n" "$fname" "$size" "$date_str"
    done

    echo ""
    local total_count total_size
    total_count=$(ls -1 "$BACKUP_DIR"/*.sql.gz 2>/dev/null | wc -l)
    total_size=$(du -sh "$BACKUP_DIR" 2>/dev/null | awk '{print $1}')
    echo "  Total: ${total_count} backup(s), ${total_size}"
}

prune_backups() {
    local days="$1"
    echo "Pruning backups older than ${days} days..."

    local count
    count=$(find "$BACKUP_DIR" -name "yawl_*.sql.gz" -mtime +"$days" 2>/dev/null | wc -l)

    if [ "$count" -eq 0 ]; then
        echo "  No backups to prune."
        return
    fi

    echo "  Found ${count} backup(s) older than ${days} days."
    find "$BACKUP_DIR" -name "yawl_*.sql.gz" -mtime +"$days" -print -delete
    echo "  Pruned ${count} backup(s)."
}

# Main dispatch
ACTION="${1:-help}"
shift || true

case "$ACTION" in
    create)
        MODE="${1:-docker}"
        NAMESPACE="${2:-yawl}"
        case "$MODE" in
            docker) create_docker_backup ;;
            k8s|kubernetes) create_k8s_backup "$NAMESPACE" ;;
            *) echo "Unknown mode: $MODE"; exit 1 ;;
        esac
        ;;
    restore)
        BACKUP_FILE="${1:?Backup file required}"
        MODE="${2:-docker}"
        NAMESPACE="${3:-yawl}"
        case "$MODE" in
            docker) restore_docker_backup "$BACKUP_FILE" ;;
            k8s|kubernetes) restore_k8s_backup "$BACKUP_FILE" "$NAMESPACE" ;;
            *) echo "Unknown mode: $MODE"; exit 1 ;;
        esac
        ;;
    list)
        list_backups
        ;;
    prune)
        DAYS="${1:?Number of days required}"
        prune_backups "$DAYS"
        ;;
    *)
        echo "YAWL Database Backup Tool"
        echo ""
        echo "Usage:"
        echo "  $0 create [docker|k8s] [namespace]"
        echo "  $0 restore <backup-file> [docker|k8s] [namespace]"
        echo "  $0 list"
        echo "  $0 prune <days>"
        echo ""
        echo "Environment variables:"
        echo "  BACKUP_DIR  - Backup directory (default: ./backups)"
        echo "  DB_USER     - Database user (default: yawl)"
        echo "  DB_NAME     - Database name (default: yawl)"
        echo "  DB_PASSWORD - Database password (default: yawl123)"
        ;;
esac
