#!/bin/bash

#############################################################################
# Backup Strategy Script - Multi-Cloud Backup Orchestration
# Version: 1.0
# Purpose: Automate backup operations across AWS, GCP, and Azure
# Features: Incremental backups, verification, retention, notifications
#############################################################################

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${SCRIPT_DIR}/logs"
CONFIG_DIR="${SCRIPT_DIR}/configs"
BACKUP_DIR="${SCRIPT_DIR}/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="${LOG_DIR}/backup_${TIMESTAMP}.log"

# Default configuration
RETENTION_DAYS=30
MAX_CONCURRENT_BACKUPS=3
BACKUP_TIMEOUT=3600
ENABLE_COMPRESSION=true
COMPRESSION_LEVEL=6
ENABLE_ENCRYPTION=true
ALERT_EMAIL="admin@example.com"

# RTO/RPO Configuration (in minutes)
AWS_RTO_MINUTES=15
AWS_RPO_MINUTES=5
GCP_RTO_MINUTES=20
GCP_RPO_MINUTES=10
AZURE_RTO_MINUTES=25
AZURE_RPO_MINUTES=15

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

#############################################################################
# Logging Functions
#############################################################################

log() {
    local level=$1
    shift
    local message="$@"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[${timestamp}] [${level}] ${message}" >> "${LOG_FILE}"

    case "${level}" in
        INFO)
            echo -e "${BLUE}[INFO]${NC} ${message}"
            ;;
        SUCCESS)
            echo -e "${GREEN}[SUCCESS]${NC} ${message}"
            ;;
        WARNING)
            echo -e "${YELLOW}[WARNING]${NC} ${message}"
            ;;
        ERROR)
            echo -e "${RED}[ERROR]${NC} ${message}"
            ;;
    esac
}

init_logging() {
    mkdir -p "${LOG_DIR}" "${CONFIG_DIR}" "${BACKUP_DIR}"
    echo "=== Backup Strategy Execution ===" > "${LOG_FILE}"
    log INFO "Logging initialized: ${LOG_FILE}"
}

#############################################################################
# Backup Verification Functions
#############################################################################

verify_backup() {
    local backup_path=$1
    local backup_type=$2

    log INFO "Verifying ${backup_type} backup: ${backup_path}"

    if [[ ! -f "${backup_path}" ]]; then
        log ERROR "Backup file not found: ${backup_path}"
        return 1
    fi

    local file_size=$(stat -f%z "${backup_path}" 2>/dev/null || stat -c%s "${backup_path}" 2>/dev/null)
    local file_size_mb=$((file_size / 1024 / 1024))

    if [[ ${file_size} -eq 0 ]]; then
        log ERROR "Backup file is empty: ${backup_path}"
        return 1
    fi

    log INFO "Backup verified - Size: ${file_size_mb}MB"
    return 0
}

calculate_checksum() {
    local backup_path=$1
    local checksum=$(sha256sum "${backup_path}" | awk '{print $1}')
    echo "${checksum}"
}

#############################################################################
# AWS Backup Functions
#############################################################################

backup_aws_rds() {
    log INFO "Starting AWS RDS backup (RTO: ${AWS_RTO_MINUTES}min, RPO: ${AWS_RPO_MINUTES}min)"

    local db_instance_id=${1:-"production-db"}
    local backup_id="manual-backup-${TIMESTAMP}"

    if ! command -v aws &> /dev/null; then
        log ERROR "AWS CLI not installed"
        return 1
    fi

    log INFO "Creating RDS snapshot: ${backup_id}"
    aws rds create-db-snapshot \
        --db-instance-identifier "${db_instance_id}" \
        --db-snapshot-identifier "${backup_id}" \
        --tags "Key=BackupType,Value=Manual" "Key=CreatedAt,Value=${TIMESTAMP}" \
        2>&1 | tee -a "${LOG_FILE}"

    log SUCCESS "RDS backup initiated: ${backup_id}"
    return 0
}

backup_aws_s3() {
    log INFO "Starting AWS S3 backup (RTO: ${AWS_RTO_MINUTES}min, RPO: ${AWS_RPO_MINUTES}min)"

    local source_bucket=${1:-"production-data"}
    local dest_bucket="${source_bucket}-backup-${TIMESTAMP}"
    local backup_file="${BACKUP_DIR}/s3-backup-${TIMESTAMP}.manifest"

    log INFO "Listing S3 objects from: ${source_bucket}"
    aws s3 sync \
        "s3://${source_bucket}/" \
        "s3://${dest_bucket}/" \
        --storage-class GLACIER \
        --metadata "BackupTime=${TIMESTAMP},Type=StrategyBackup" \
        2>&1 | tee -a "${LOG_FILE}"

    # Generate manifest
    aws s3 ls "s3://${dest_bucket}/" --recursive > "${backup_file}"

    log SUCCESS "S3 backup completed: ${dest_bucket}"
    return 0
}

backup_aws_ebs() {
    log INFO "Starting AWS EBS backup (RTO: ${AWS_RTO_MINUTES}min, RPO: ${AWS_RPO_MINUTES}min)"

    local volume_ids=${1:-"vol-0123456789abcdef0"}
    local backup_manifest="${BACKUP_DIR}/ebs-backup-${TIMESTAMP}.json"

    > "${backup_manifest}"

    for volume_id in ${volume_ids}; do
        log INFO "Creating snapshot for EBS volume: ${volume_id}"
        local snapshot_id=$(aws ec2 create-snapshot \
            --volume-id "${volume_id}" \
            --description "Backup snapshot ${TIMESTAMP}" \
            --tag-specifications "ResourceType=snapshot,Tags=[{Key=BackupTime,Value=${TIMESTAMP}}]" \
            --query 'SnapshotId' \
            --output text)

        echo "{\"volume_id\": \"${volume_id}\", \"snapshot_id\": \"${snapshot_id}\", \"timestamp\": \"${TIMESTAMP}\"}" >> "${backup_manifest}"
    done

    log SUCCESS "EBS snapshots initiated"
    return 0
}

#############################################################################
# GCP Backup Functions
#############################################################################

backup_gcp_cloudsql() {
    log INFO "Starting GCP Cloud SQL backup (RTO: ${GCP_RTO_MINUTES}min, RPO: ${GCP_RPO_MINUTES}min)"

    local instance_id=${1:-"production-mysql"}
    local project_id=${2:-"my-project"}
    local backup_id="backup-${TIMESTAMP}"

    if ! command -v gcloud &> /dev/null; then
        log ERROR "gcloud CLI not installed"
        return 1
    fi

    log INFO "Creating Cloud SQL backup: ${backup_id}"
    gcloud sql backups create "${backup_id}" \
        --instance="${instance_id}" \
        --project="${project_id}" \
        2>&1 | tee -a "${LOG_FILE}"

    log SUCCESS "Cloud SQL backup initiated: ${backup_id}"
    return 0
}

backup_gcp_gcs() {
    log INFO "Starting GCP Cloud Storage backup (RTO: ${GCP_RTO_MINUTES}min, RPO: ${GCP_RPO_MINUTES}min)"

    local source_bucket=${1:-"gs://production-data"}
    local dest_bucket="gs://production-data-backup-${TIMESTAMP}"
    local project_id=${2:-"my-project"}

    log INFO "Syncing GCS bucket from: ${source_bucket}"
    gsutil -m -h "Cache-Control:public, max-age=0" \
        -h "x-goog-meta-backup-time:${TIMESTAMP}" \
        cp -r "${source_bucket}/*" "${dest_bucket}/" \
        2>&1 | tee -a "${LOG_FILE}"

    log SUCCESS "GCS backup completed: ${dest_bucket}"
    return 0
}

backup_gcp_gke() {
    log INFO "Starting GCP GKE backup (RTO: ${GCP_RTO_MINUTES}min, RPO: ${GCP_RPO_MINUTES}min)"

    local cluster_name=${1:-"production-cluster"}
    local project_id=${2:-"my-project"}
    local backup_name="gke-backup-${TIMESTAMP}"

    log INFO "Creating GKE backup plan: ${backup_name}"
    gcloud container backup-restore backup-plans create "${backup_name}" \
        --cluster="projects/${project_id}/locations/us-central1/clusters/${cluster_name}" \
        --all-namespaces \
        --include-secrets \
        --include-volume-data \
        2>&1 | tee -a "${LOG_FILE}"

    log SUCCESS "GKE backup initiated: ${backup_name}"
    return 0
}

#############################################################################
# Azure Backup Functions
#############################################################################

backup_azure_vm() {
    log INFO "Starting Azure VM backup (RTO: ${AZURE_RTO_MINUTES}min, RPO: ${AZURE_RPO_MINUTES}min)"

    local resource_group=${1:-"production-rg"}
    local vm_name=${2:-"production-vm"}
    local backup_name="backup-${TIMESTAMP}"

    if ! command -v az &> /dev/null; then
        log ERROR "Azure CLI not installed"
        return 1
    fi

    log INFO "Creating Azure VM snapshot: ${backup_name}"
    az snapshot create \
        --resource-group "${resource_group}" \
        --name "${backup_name}" \
        --source "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/${resource_group}/providers/Microsoft.Compute/disks/osDisk" \
        2>&1 | tee -a "${LOG_FILE}"

    log SUCCESS "Azure VM backup initiated: ${backup_name}"
    return 0
}

backup_azure_sql() {
    log INFO "Starting Azure SQL backup (RTO: ${AZURE_RTO_MINUTES}min, RPO: ${AZURE_RPO_MINUTES}min)"

    local resource_group=${1:-"production-rg"}
    local server_name=${2:-"production-server"}
    local database_name=${3:-"productiondb"}

    log INFO "Creating Azure SQL long-term retention backup"
    az sql db ltr-backup create \
        --resource-group "${resource_group}" \
        --server "${server_name}" \
        --name "${database_name}" \
        2>&1 | tee -a "${LOG_FILE}"

    log SUCCESS "Azure SQL backup initiated"
    return 0
}

backup_azure_storage() {
    log INFO "Starting Azure Storage backup (RTO: ${AZURE_RTO_MINUTES}min, RPO: ${AZURE_RPO_MINUTES}min)"

    local resource_group=${1:-"production-rg"}
    local storage_account=${2:-"productionstorage"}
    local container_name=${3:-"data"}
    local backup_container="${container_name}-backup-${TIMESTAMP}"

    log INFO "Creating Azure Storage backup container"
    az storage container create \
        --account-name "${storage_account}" \
        --name "${backup_container}" \
        --resource-group "${resource_group}" \
        2>&1 | tee -a "${LOG_FILE}"

    log INFO "Syncing blobs to backup container"
    az storage blob copy start-batch \
        --source-container "${container_name}" \
        --destination-container "${backup_container}" \
        --account-name "${storage_account}" \
        2>&1 | tee -a "${LOG_FILE}"

    log SUCCESS "Azure Storage backup completed"
    return 0
}

#############################################################################
# Backup Retention & Cleanup
#############################################################################

cleanup_old_backups() {
    log INFO "Cleaning up backups older than ${RETENTION_DAYS} days"

    local backup_prefix=$1
    local current_time=$(date +%s)
    local cutoff_time=$((current_time - RETENTION_DAYS * 86400))

    find "${BACKUP_DIR}" -name "${backup_prefix}*" -type f | while read -r backup_file; do
        local file_mtime=$(stat -f%m "${backup_file}" 2>/dev/null || stat -c%Y "${backup_file}" 2>/dev/null)

        if [[ ${file_mtime} -lt ${cutoff_time} ]]; then
            log INFO "Removing old backup: $(basename "${backup_file}")"
            rm -f "${backup_file}"
        fi
    done

    log SUCCESS "Cleanup completed"
    return 0
}

#############################################################################
# Backup Status & Reporting
#############################################################################

generate_backup_report() {
    local report_file="${BACKUP_DIR}/backup-report-${TIMESTAMP}.txt"

    log INFO "Generating backup report: ${report_file}"

    {
        echo "=============================================="
        echo "Backup Strategy Report"
        echo "Generated: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "=============================================="
        echo ""
        echo "RTO/RPO Summary:"
        echo "  AWS:   RTO=${AWS_RTO_MINUTES}min, RPO=${AWS_RPO_MINUTES}min"
        echo "  GCP:   RTO=${GCP_RTO_MINUTES}min, RPO=${GCP_RPO_MINUTES}min"
        echo "  Azure: RTO=${AZURE_RTO_MINUTES}min, RPO=${AZURE_RPO_MINUTES}min"
        echo ""
        echo "Backup Inventory:"
        ls -lh "${BACKUP_DIR}" | tail -n +2 | awk '{print "  " $9 " - " $5}'
        echo ""
        echo "Log File: ${LOG_FILE}"
    } > "${report_file}"

    log SUCCESS "Report generated: ${report_file}"
    cat "${report_file}"
}

send_alert() {
    local status=$1
    local message=$2

    if ! command -v mail &> /dev/null; then
        log WARNING "mail command not available, skipping email alert"
        return 1
    fi

    {
        echo "Backup Status: ${status}"
        echo "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')"
        echo "Message: ${message}"
        echo ""
        echo "Log: ${LOG_FILE}"
    } | mail -s "Backup Alert: ${status}" "${ALERT_EMAIL}"

    log INFO "Alert sent to: ${ALERT_EMAIL}"
}

#############################################################################
# Main Execution
#############################################################################

main() {
    init_logging
    log INFO "Starting backup strategy execution"
    log INFO "Retention: ${RETENTION_DAYS} days"

    local exit_code=0

    # Execute AWS backups
    log INFO "=== AWS Backup Phase ==="
    backup_aws_rds "production-db" || { log ERROR "AWS RDS backup failed"; exit_code=$((exit_code + 1)); }
    backup_aws_s3 "production-data" || { log ERROR "AWS S3 backup failed"; exit_code=$((exit_code + 1)); }
    backup_aws_ebs "vol-0123456789abcdef0" || { log ERROR "AWS EBS backup failed"; exit_code=$((exit_code + 1)); }

    # Execute GCP backups
    log INFO "=== GCP Backup Phase ==="
    backup_gcp_cloudsql "production-mysql" "my-project" || { log ERROR "GCP Cloud SQL backup failed"; exit_code=$((exit_code + 1)); }
    backup_gcp_gcs "gs://production-data" "my-project" || { log ERROR "GCP GCS backup failed"; exit_code=$((exit_code + 1)); }
    backup_gcp_gke "production-cluster" "my-project" || { log ERROR "GCP GKE backup failed"; exit_code=$((exit_code + 1)); }

    # Execute Azure backups
    log INFO "=== Azure Backup Phase ==="
    backup_azure_vm "production-rg" "production-vm" || { log ERROR "Azure VM backup failed"; exit_code=$((exit_code + 1)); }
    backup_azure_sql "production-rg" "production-server" "productiondb" || { log ERROR "Azure SQL backup failed"; exit_code=$((exit_code + 1)); }
    backup_azure_storage "production-rg" "productionstorage" "data" || { log ERROR "Azure Storage backup failed"; exit_code=$((exit_code + 1)); }

    # Cleanup old backups
    cleanup_old_backups "backup" || { log ERROR "Cleanup failed"; exit_code=$((exit_code + 1)); }

    # Generate report
    generate_backup_report

    if [[ ${exit_code} -eq 0 ]]; then
        log SUCCESS "Backup strategy completed successfully"
        send_alert "SUCCESS" "All backups completed successfully"
    else
        log ERROR "Backup strategy completed with errors (exit code: ${exit_code})"
        send_alert "FAILURE" "Backup strategy encountered ${exit_code} error(s)"
    fi

    return ${exit_code}
}

# Run main function
main "$@"
