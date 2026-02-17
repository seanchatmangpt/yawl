#!/bin/bash
# =============================================================================
# YAWL v6.0.0-Alpha Database and Data Backup Script
# =============================================================================
# Production-ready backup solution for YAWL workflow engine.
# Supports: PostgreSQL, MySQL, H2 databases, and file-based data.
#
# Features:
#   - Multi-database support (PostgreSQL, MySQL, H2)
#   - File backup (specifications, logs, configuration)
#   - Compression (gzip by default)
#   - Encryption support (GPG)
#   - Retention policy management
#   - Scheduled backup support (cron)
#   - External storage (NFS, S3, SFTP)
#   - Comprehensive logging
#   - Backup verification
#   - Rotation and cleanup
#
# Usage:
#   ./backup.sh                           # Full backup with defaults
#   ./backup.sh --type=database           # Database only
#   ./backup.sh --type=files              # Files only
#   ./backup.sh --encrypt                 # Enable GPG encryption
#   ./backup.sh --verify                  # Verify after backup
#   ./backup.sh --retention=30            # Keep 30 days of backups
#   ./backup.sh --dry-run                 # Show what would be done
#
# Cron Examples:
#   # Daily backup at 2 AM
#   0 2 * * * /app/docker/backup.sh >> /app/logs/backup.log 2>&1
#
#   # Weekly full backup on Sunday at 3 AM
#   0 3 * * 0 /app/docker/backup.sh --type=full >> /app/logs/backup.log 2>&1
#
#   # Hourly database-only backup
#   0 * * * * /app/docker/backup.sh --type=database >> /app/logs/backup.log 2>&1
#
# Environment Variables (see .env.example for defaults):
#   BACKUP_PATH          - Backup destination directory (default: /app/backups)
#   BACKUP_RETENTION_DAYS - Days to keep backups (default: 7)
#   BACKUP_COMPRESS      - Enable compression (default: true)
#   BACKUP_ENCRYPT       - Enable GPG encryption (default: false)
#   BACKUP_GPG_KEY       - GPG key ID for encryption
#   BACKUP_VERIFY        - Verify backup integrity (default: true)
#   BACKUP_TYPE          - Backup type: full, database, files (default: full)
#   DB_TYPE              - Database type: postgres, mysql, h2
#   DB_HOST              - Database host
#   DB_PORT              - Database port
#   DB_NAME              - Database name
#   DB_USER              - Database username
#   DB_PASSWORD          - Database password
#
# Exit Codes:
#   0 - Success
#   1 - General error
#   2 - Configuration error
#   3 - Database backup failed
#   4 - File backup failed
#   5 - Encryption failed
#   6 - Verification failed
#   7 - Retention cleanup failed
#   8 - External storage upload failed
# =============================================================================

set -euo pipefail

# =============================================================================
# Script Information
# =============================================================================

readonly SCRIPT_NAME="yawl-backup"
readonly SCRIPT_VERSION="1.0.0"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly APP_HOME="/app"

# =============================================================================
# Configuration (with environment variable overrides)
# =============================================================================

# Backup settings
BACKUP_PATH="${BACKUP_PATH:-/app/backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
BACKUP_COMPRESS="${BACKUP_COMPRESS:-true}"
BACKUP_ENCRYPT="${BACKUP_ENCRYPT:-false}"
BACKUP_GPG_KEY="${BACKUP_GPG_KEY:-}"
BACKUP_VERIFY="${BACKUP_VERIFY:-true}"
BACKUP_TYPE="${BACKUP_TYPE:-full}"
BACKUP_EXTERNAL_PATH="${BACKUP_EXTERNAL_PATH:-}"
BACKUP_S3_BUCKET="${BACKUP_S3_BUCKET:-}"
BACKUP_SFTP_HOST="${BACKUP_SFTP_HOST:-}"
BACKUP_DRY_RUN="${BACKUP_DRY_RUN:-false}"

# Database settings
DB_TYPE="${DB_TYPE:-postgres}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-yawl}"
DB_USER="${DB_USER:-yawl}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_PASSWORD_FILE="${DB_PASSWORD_FILE:-}"

# Application paths
LOG_DIR="${LOG_DIR:-${APP_HOME}/logs}"
DATA_DIR="${DATA_DIR:-${APP_HOME}/data}"
CONFIG_DIR="${CONFIG_DIR:-${APP_HOME}/config}"
SPEC_DIR="${SPEC_DIR:-${APP_HOME}/specifications}"

# Logging
LOG_FILE="${LOG_FILE:-${LOG_DIR}/backup.log}"
LOG_LEVEL="${LOG_LEVEL:-INFO}"
LOG_MAX_SIZE="${LOG_MAX_SIZE:-100M}"
LOG_MAX_FILES="${LOG_MAX_FILES:-10}"

# Timestamp for backup files
BACKUP_TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_DATE="$(date +%Y-%m-%d)"
BACKUP_HOUR="$(date +%H)"

# =============================================================================
# Logging Functions
# =============================================================================

get_log_level_num() {
    case "$1" in
        DEBUG) echo 0 ;;
        INFO)  echo 1 ;;
        WARN)  echo 2 ;;
        ERROR) echo 3 ;;
        *)     echo 1 ;;
    esac
}

log() {
    local level="$1"
    shift
    local message="$*"
    local timestamp
    timestamp=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

    local current_level
    current_level=$(get_log_level_num "$LOG_LEVEL")
    local message_level
    message_level=$(get_log_level_num "$level")

    if (( message_level >= current_level )); then
        echo "[$timestamp] [$level] [$SCRIPT_NAME] $message"

        # Write to log file
        local log_dir
        log_dir=$(dirname "$LOG_FILE")
        if [[ -d "$log_dir" ]]; then
            echo "[$timestamp] [$level] [$SCRIPT_NAME] $message" >> "$LOG_FILE" 2>/dev/null || true
        fi
    fi
}

log_debug() { log "DEBUG" "$@"; }
log_info()  { log "INFO" "$@"; }
log_warn()  { log "WARN" "$@"; }
log_error() { log "ERROR" "$@" >&2; }

# =============================================================================
# Utility Functions
# =============================================================================

# Check if a command exists
command_exists() {
    command -v "$1" &>/dev/null
}

# Get human-readable size
human_size() {
    local bytes=$1
    local units=('B' 'KB' 'MB' 'GB' 'TB')
    local unit=0
    while (( bytes > 1024 )) && (( unit < 4 )); do
        bytes=$((bytes / 1024))
        ((unit++))
    done
    echo "${bytes}${units[$unit]}"
}

# Calculate directory size
dir_size() {
    local dir="$1"
    if [[ -d "$dir" ]]; then
        du -sb "$dir" 2>/dev/null | cut -f1
    else
        echo "0"
    fi
}

# Get database password from file or environment
get_db_password() {
    if [[ -n "$DB_PASSWORD_FILE" ]] && [[ -f "$DB_PASSWORD_FILE" ]]; then
        cat "$DB_PASSWORD_FILE"
    else
        echo "$DB_PASSWORD"
    fi
}

# Ensure backup directory exists
ensure_backup_dir() {
    local backup_subdir="$1"
    local full_path="${BACKUP_PATH}/${backup_subdir}"

    if [[ ! -d "$full_path" ]]; then
        if [[ "$BACKUP_DRY_RUN" == "true" ]]; then
            log_info "[DRY-RUN] Would create directory: $full_path"
        else
            mkdir -p "$full_path"
            log_info "Created backup directory: $full_path"
        fi
    fi
}

# Rotate log file if too large
rotate_log() {
    if [[ -f "$LOG_FILE" ]]; then
        local log_size
        log_size=$(stat -f%z "$LOG_FILE" 2>/dev/null || stat -c%s "$LOG_FILE" 2>/dev/null || echo "0")
        local max_bytes
        max_bytes=$(echo "$LOG_MAX_SIZE" | sed 's/M/*1024*1024/;s/K/*1024/;s/G/*1024*1024*1024/' | bc)

        if (( log_size > max_bytes )); then
            local rotated="${LOG_FILE}.$(date +%Y%m%d%H%M%S)"
            mv "$LOG_FILE" "$rotated"
            gzip "$rotated" 2>/dev/null || true
            log_info "Rotated log file: $rotated"

            # Clean old rotated logs
            find "$(dirname "$LOG_FILE")" -name "$(basename "$LOG_FILE").*.gz" -mtime +$LOG_MAX_FILES -delete 2>/dev/null || true
        fi
    fi
}

# =============================================================================
# Database Backup Functions
# =============================================================================

# Backup PostgreSQL database
backup_postgres() {
    log_info "Starting PostgreSQL backup for database: $DB_NAME"

    local backup_file="${BACKUP_PATH}/database/${DB_NAME}_${BACKUP_TIMESTAMP}.sql"
    local backup_final="${backup_file}"

    # Add compression extension if enabled
    if [[ "$BACKUP_COMPRESS" == "true" ]]; then
        backup_final="${backup_file}.gz"
    fi

    # Get password
    local db_password
    db_password=$(get_db_password)

    if [[ "$BACKUP_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would backup PostgreSQL database to: $backup_final"
        return 0
    fi

    # Check for pg_dump
    if ! command_exists pg_dump; then
        log_error "pg_dump not found. Please install postgresql-client."
        return 3
    fi

    # Build pg_dump command
    local pg_opts=(
        "--host=$DB_HOST"
        "--port=$DB_PORT"
        "--username=$DB_USER"
        "--dbname=$DB_NAME"
        "--format=plain"
        "--no-owner"
        "--no-acl"
        "--verbose"
    )

    # Set password via environment
    export PGPASSWORD="$db_password"

    log_debug "Running pg_dump with options: ${pg_opts[*]}"

    # Execute backup
    if [[ "$BACKUP_COMPRESS" == "true" ]]; then
        if pg_dump "${pg_opts[@]}" 2>>"$LOG_FILE" | gzip > "$backup_final"; then
            log_info "PostgreSQL backup completed: $backup_final"
            echo "$backup_final"
        else
            log_error "PostgreSQL backup failed"
            rm -f "$backup_final"
            unset PGPASSWORD
            return 3
        fi
    else
        if pg_dump "${pg_opts[@]}" > "$backup_final" 2>>"$LOG_FILE"; then
            log_info "PostgreSQL backup completed: $backup_final"
            echo "$backup_final"
        else
            log_error "PostgreSQL backup failed"
            rm -f "$backup_final"
            unset PGPASSWORD
            return 3
        fi
    fi

    unset PGPASSWORD
    return 0
}

# Backup MySQL database
backup_mysql() {
    log_info "Starting MySQL backup for database: $DB_NAME"

    local backup_file="${BACKUP_PATH}/database/${DB_NAME}_${BACKUP_TIMESTAMP}.sql"
    local backup_final="${backup_file}"

    if [[ "$BACKUP_COMPRESS" == "true" ]]; then
        backup_final="${backup_file}.gz"
    fi

    local db_password
    db_password=$(get_db_password)

    if [[ "$BACKUP_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would backup MySQL database to: $backup_final"
        return 0
    fi

    # Check for mysqldump
    if ! command_exists mysqldump; then
        log_error "mysqldump not found. Please install mysql-client."
        return 3
    fi

    # Build mysqldump command
    local mysql_opts=(
        "--host=$DB_HOST"
        "--port=$DB_PORT"
        "--user=$DB_USER"
        "--password=$db_password"
        "--single-transaction"
        "--routines"
        "--triggers"
        "--events"
        "--verbose"
        "$DB_NAME"
    )

    log_debug "Running mysqldump (password hidden)"

    # Execute backup
    if [[ "$BACKUP_COMPRESS" == "true" ]]; then
        if mysqldump "${mysql_opts[@]}" 2>>"$LOG_FILE" | gzip > "$backup_final"; then
            log_info "MySQL backup completed: $backup_final"
            echo "$backup_final"
        else
            log_error "MySQL backup failed"
            rm -f "$backup_final"
            return 3
        fi
    else
        if mysqldump "${mysql_opts[@]}" > "$backup_final" 2>>"$LOG_FILE"; then
            log_info "MySQL backup completed: $backup_final"
            echo "$backup_final"
        else
            log_error "MySQL backup failed"
            rm -f "$backup_final"
            return 3
        fi
    fi

    return 0
}

# Backup H2 database
backup_h2() {
    log_info "Starting H2 database backup for database: $DB_NAME"

    local backup_file="${BACKUP_PATH}/database/${DB_NAME}_${BACKUP_TIMESTAMP}.zip"

    if [[ "$BACKUP_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would backup H2 database to: $backup_file"
        return 0
    fi

    # H2 database files
    local db_file="${DATA_DIR}/${DB_NAME}.mv.db"
    local db_trace="${DATA_DIR}/${DB_NAME}.trace.db"

    # Check if database file exists
    if [[ ! -f "$db_file" ]]; then
        log_warn "H2 database file not found: $db_file"
        # Try alternative locations
        db_file="${APP_HOME}/${DB_NAME}.mv.db"
        if [[ ! -f "$db_file" ]]; then
            log_error "H2 database file not found in any location"
            return 3
        fi
    fi

    # Create backup using zip
    local files_to_backup=("$db_file")
    if [[ -f "$db_trace" ]]; then
        files_to_backup+=("$db_trace")
    fi

    if zip -j "$backup_file" "${files_to_backup[@]}" 2>>"$LOG_FILE"; then
        log_info "H2 backup completed: $backup_file"
        echo "$backup_file"
    else
        log_error "H2 backup failed"
        rm -f "$backup_file"
        return 3
    fi

    return 0
}

# Generic database backup dispatcher
backup_database() {
    ensure_backup_dir "database"

    log_info "Starting database backup (type: $DB_TYPE)"

    local backup_file=""
    local exit_code=0

    case "$DB_TYPE" in
        postgres|postgresql)
            backup_file=$(backup_postgres) || exit_code=$?
            ;;
        mysql|mariadb)
            backup_file=$(backup_mysql) || exit_code=$?
            ;;
        h2)
            backup_file=$(backup_h2) || exit_code=$?
            ;;
        *)
            log_error "Unsupported database type: $DB_TYPE"
            return 2
            ;;
    esac

    if [[ $exit_code -eq 0 ]] && [[ -n "$backup_file" ]] && [[ "$BACKUP_ENCRYPT" == "true" ]]; then
        encrypt_backup "$backup_file" || return 5
    fi

    return $exit_code
}

# =============================================================================
# File Backup Functions
# =============================================================================

# Backup application files
backup_files() {
    log_info "Starting file backup"

    local backup_file="${BACKUP_PATH}/files/yawl_files_${BACKUP_TIMESTAMP}.tar"
    local backup_final="${backup_file}"

    if [[ "$BACKUP_COMPRESS" == "true" ]]; then
        backup_final="${backup_file}.gz"
    fi

    if [[ "$BACKUP_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would backup files to: $backup_final"
        return 0
    fi

    ensure_backup_dir "files"

    # Collect directories to backup
    local dirs_to_backup=()

    if [[ -d "$SPEC_DIR" ]]; then
        dirs_to_backup+=("$SPEC_DIR")
        log_debug "Including: $SPEC_DIR"
    fi

    if [[ -d "$CONFIG_DIR" ]]; then
        dirs_to_backup+=("$CONFIG_DIR")
        log_debug "Including: $CONFIG_DIR"
    fi

    if [[ -d "$DATA_DIR" ]]; then
        dirs_to_backup+=("$DATA_DIR")
        log_debug "Including: $DATA_DIR"
    fi

    if [[ ${#dirs_to_backup[@]} -eq 0 ]]; then
        log_warn "No directories found to backup"
        return 0
    fi

    # Create tar archive
    local tar_opts=(
        "--create"
        "--file=$backup_file"
        "--verbose"
        "--preserve-permissions"
        "--same-owner"
    )

    # Exclude patterns
    local exclude_patterns=(
        "--exclude=*.log"
        "--exclude=*.tmp"
        "--exclude=*.temp"
        "--exclude=*.bak"
        "--exclude=.DS_Store"
        "--exclude=Thumbs.db"
        "--exclude=*.h2.db"  # Exclude H2 files from file backup (backed up separately)
        "--exclude=*.mv.db"
        "--exclude=*.trace.db"
    )

    log_debug "Creating tar archive: $backup_file"

    if tar "${tar_opts[@]}" "${exclude_patterns[@]}" -C "$APP_HOME" \
        $(printf -- "-C %s .\n" "${dirs_to_backup[@]}" | xargs) 2>>"$LOG_FILE"; then

        # Compress if enabled
        if [[ "$BACKUP_COMPRESS" == "true" ]]; then
            if gzip -f "$backup_file"; then
                backup_file="$backup_final"
            else
                log_error "Failed to compress backup"
                rm -f "$backup_file"
                return 4
            fi
        fi

        log_info "File backup completed: $backup_file"
        echo "$backup_file"

        # Encrypt if enabled
        if [[ "$BACKUP_ENCRYPT" == "true" ]]; then
            encrypt_backup "$backup_file" || return 5
        fi
    else
        log_error "File backup failed"
        rm -f "$backup_file"
        return 4
    fi

    return 0
}

# =============================================================================
# Encryption Functions
# =============================================================================

# Encrypt backup file with GPG
encrypt_backup() {
    local backup_file="$1"

    if [[ ! -f "$backup_file" ]]; then
        log_error "Backup file not found for encryption: $backup_file"
        return 5
    fi

    if [[ -z "$BACKUP_GPG_KEY" ]]; then
        log_error "BACKUP_GPG_KEY not set for encryption"
        return 5
    fi

    if ! command_exists gpg; then
        log_error "GPG not found. Please install gnupg."
        return 5
    fi

    local encrypted_file="${backup_file}.gpg"

    if [[ "$BACKUP_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would encrypt: $backup_file -> $encrypted_file"
        return 0
    fi

    log_info "Encrypting backup: $backup_file"

    if gpg --batch --yes --trust-model always \
           --encrypt --recipient "$BACKUP_GPG_KEY" \
           --output "$encrypted_file" "$backup_file" 2>>"$LOG_FILE"; then
        # Remove unencrypted backup
        rm -f "$backup_file"
        log_info "Backup encrypted: $encrypted_file"
        echo "$encrypted_file"
    else
        log_error "Encryption failed for: $backup_file"
        rm -f "$encrypted_file"
        return 5
    fi

    return 0
}

# =============================================================================
# Verification Functions
# =============================================================================

# Verify backup integrity
verify_backup() {
    local backup_file="$1"

    if [[ ! -f "$backup_file" ]]; then
        log_error "Backup file not found for verification: $backup_file"
        return 6
    fi

    log_info "Verifying backup integrity: $backup_file"

    local file_ext="${backup_file##*.}"

    case "$file_ext" in
        gz|tgz)
            if gzip -t "$backup_file" 2>/dev/null; then
                log_info "Gzip integrity check passed: $backup_file"
                return 0
            else
                log_error "Gzip integrity check failed: $backup_file"
                return 6
            fi
            ;;
        zip)
            if unzip -t "$backup_file" &>/dev/null; then
                log_info "Zip integrity check passed: $backup_file"
                return 0
            else
                log_error "Zip integrity check failed: $backup_file"
                return 6
            fi
            ;;
        gpg)
            # Cannot verify GPG without private key
            log_warn "Cannot verify encrypted backup without decryption key: $backup_file"
            return 0
            ;;
        sql)
            # SQL files - basic check
            if [[ -s "$backup_file" ]]; then
                log_info "SQL backup file exists and is non-empty: $backup_file"
                return 0
            else
                log_error "SQL backup file is empty: $backup_file"
                return 6
            fi
            ;;
        tar)
            if tar -tf "$backup_file" &>/dev/null; then
                log_info "Tar integrity check passed: $backup_file"
                return 0
            else
                log_error "Tar integrity check failed: $backup_file"
                return 6
            fi
            ;;
        *)
            log_warn "Unknown file type for verification: $file_ext"
            # Basic file existence check
            if [[ -s "$backup_file" ]]; then
                return 0
            else
                return 6
            fi
            ;;
    esac
}

# =============================================================================
# Retention Policy Functions
# =============================================================================

# Clean old backups based on retention policy
apply_retention_policy() {
    log_info "Applying retention policy (keep last $BACKUP_RETENTION_DAYS days)"

    if [[ "$BACKUP_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would delete backups older than $BACKUP_RETENTION_DAYS days"
        return 0
    fi

    local deleted_count=0
    local freed_space=0

    # Clean database backups
    if [[ -d "${BACKUP_PATH}/database" ]]; then
        while IFS= read -r -d '' file; do
            local file_size
            file_size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "0")
            log_debug "Deleting old database backup: $file"
            rm -f "$file"
            ((deleted_count++))
            ((freed_space+=file_size))
        done < <(find "${BACKUP_PATH}/database" -type f -mtime +$BACKUP_RETENTION_DAYS -print0 2>/dev/null)
    fi

    # Clean file backups
    if [[ -d "${BACKUP_PATH}/files" ]]; then
        while IFS= read -r -d '' file; do
            local file_size
            file_size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "0")
            log_debug "Deleting old file backup: $file"
            rm -f "$file"
            ((deleted_count++))
            ((freed_space+=file_size))
        done < <(find "${BACKUP_PATH}/files" -type f -mtime +$BACKUP_RETENTION_DAYS -print0 2>/dev/null)
    fi

    # Clean empty directories
    find "${BACKUP_PATH}" -type d -empty -delete 2>/dev/null || true

    if (( deleted_count > 0 )); then
        log_info "Retention cleanup: deleted $deleted_count backup(s), freed $(human_size $freed_space)"
    else
        log_info "Retention cleanup: no old backups to delete"
    fi

    return 0
}

# =============================================================================
# External Storage Functions
# =============================================================================

# Upload backup to S3
upload_to_s3() {
    local backup_file="$1"

    if [[ -z "$BACKUP_S3_BUCKET" ]]; then
        log_debug "S3 bucket not configured, skipping S3 upload"
        return 0
    fi

    if ! command_exists aws; then
        log_error "AWS CLI not found. Please install awscli."
        return 8
    fi

    local s3_path="s3://${BACKUP_S3_BUCKET}/yawl-backups/${BACKUP_DATE}/$(basename "$backup_file")"

    if [[ "$BACKUP_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would upload to S3: $s3_path"
        return 0
    fi

    log_info "Uploading backup to S3: $s3_path"

    if aws s3 cp "$backup_file" "$s3_path" --storage-class STANDARD_IA 2>>"$LOG_FILE"; then
        log_info "S3 upload completed: $s3_path"
    else
        log_error "S3 upload failed"
        return 8
    fi

    return 0
}

# Upload backup to SFTP
upload_to_sftp() {
    local backup_file="$1"

    if [[ -z "$BACKUP_SFTP_HOST" ]]; then
        log_debug "SFTP host not configured, skipping SFTP upload"
        return 0
    fi

    local sftp_path="${BACKUP_SFTP_HOST}/yawl-backups/${BACKUP_DATE}/"

    if [[ "$BACKUP_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would upload to SFTP: $sftp_path"
        return 0
    fi

    log_info "Uploading backup to SFTP: $sftp_path"

    # Create remote directory and upload
    if echo -e "mkdir -p ${sftp_path}\nput ${backup_file} ${sftp_path}\n" | sftp -b - "$BACKUP_SFTP_HOST" 2>>"$LOG_FILE"; then
        log_info "SFTP upload completed"
    else
        log_error "SFTP upload failed"
        return 8
    fi

    return 0
}

# Copy to external path (NFS, mounted volume)
copy_to_external() {
    local backup_file="$1"

    if [[ -z "$BACKUP_EXTERNAL_PATH" ]]; then
        log_debug "External path not configured, skipping external copy"
        return 0
    fi

    if [[ ! -d "$BACKUP_EXTERNAL_PATH" ]]; then
        log_warn "External path does not exist: $BACKUP_EXTERNAL_PATH"
        return 0
    fi

    local dest_path="${BACKUP_EXTERNAL_PATH}/${BACKUP_DATE}/"

    if [[ "$BACKUP_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would copy to external path: $dest_path"
        return 0
    fi

    mkdir -p "$dest_path"

    log_info "Copying backup to external path: $dest_path"

    if cp -p "$backup_file" "$dest_path"; then
        log_info "External copy completed: ${dest_path}$(basename "$backup_file")"
    else
        log_error "External copy failed"
        return 8
    fi

    return 0
}

# =============================================================================
# Backup Summary and Reporting
# =============================================================================

# Generate backup summary
generate_summary() {
    local database_backup="$1"
    local files_backup="$2"
    local start_time="$3"
    local end_time="$4"

    local duration=$((end_time - start_time))

    log_info "=========================================="
    log_info "Backup Summary"
    log_info "=========================================="
    log_info "Timestamp: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    log_info "Type: $BACKUP_TYPE"
    log_info "Duration: ${duration}s"

    if [[ -n "$database_backup" ]] && [[ -f "$database_backup" ]]; then
        local db_size
        db_size=$(stat -f%z "$database_backup" 2>/dev/null || stat -c%s "$database_backup" 2>/dev/null || echo "0")
        log_info "Database backup: $database_backup ($(human_size $db_size))"
    fi

    if [[ -n "$files_backup" ]] && [[ -f "$files_backup" ]]; then
        local files_size
        files_size=$(stat -f%z "$files_backup" 2>/dev/null || stat -c%s "$files_backup" 2>/dev/null || echo "0")
        log_info "Files backup: $files_backup ($(human_size $files_size))"
    fi

    # Calculate total backup size
    local total_size=0
    while IFS= read -r -d '' file; do
        local size
        size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "0")
        ((total_size+=size))
    done < <(find "${BACKUP_PATH}" -type f -name "*${BACKUP_TIMESTAMP}*" -print0 2>/dev/null)

    log_info "Total backup size: $(human_size $total_size)"
    log_info "Retention policy: $BACKUP_RETENTION_DAYS days"
    log_info "Encryption: $BACKUP_ENCRYPT"
    log_info "Verification: $BACKUP_VERIFY"
    log_info "=========================================="
}

# =============================================================================
# Command Line Argument Parsing
# =============================================================================

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --type=*)
                BACKUP_TYPE="${1#*=}"
                ;;
            --retention=*)
                BACKUP_RETENTION_DAYS="${1#*=}"
                ;;
            --encrypt)
                BACKUP_ENCRYPT="true"
                ;;
            --gpg-key=*)
                BACKUP_GPG_KEY="${1#*=}"
                ;;
            --verify)
                BACKUP_VERIFY="true"
                ;;
            --no-verify)
                BACKUP_VERIFY="false"
                ;;
            --compress)
                BACKUP_COMPRESS="true"
                ;;
            --no-compress)
                BACKUP_COMPRESS="false"
                ;;
            --dry-run)
                BACKUP_DRY_RUN="true"
                ;;
            --s3=*)
                BACKUP_S3_BUCKET="${1#*=}"
                ;;
            --sftp=*)
                BACKUP_SFTP_HOST="${1#*=}"
                ;;
            --external=*)
                BACKUP_EXTERNAL_PATH="${1#*=}"
                ;;
            --help)
                show_help
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 2
                ;;
        esac
        shift
    done
}

show_help() {
    cat << EOF
YAWL Backup Script v${SCRIPT_VERSION}

Usage: $(basename "$0") [OPTIONS]

Options:
    --type=TYPE         Backup type: full, database, files (default: full)
    --retention=DAYS    Days to keep backups (default: 7)
    --encrypt           Enable GPG encryption
    --gpg-key=KEY       GPG key ID for encryption
    --verify            Verify backup integrity (default: true)
    --no-verify         Skip backup verification
    --compress          Enable compression (default: true)
    --no-compress       Disable compression
    --dry-run           Show what would be done without executing
    --s3=BUCKET         Upload to S3 bucket
    --sftp=HOST         Upload to SFTP host
    --external=PATH     Copy to external path
    --help              Show this help message

Environment Variables:
    BACKUP_PATH         Backup destination directory
    BACKUP_RETENTION_DAYS Retention days
    BACKUP_COMPRESS     Enable compression
    BACKUP_ENCRYPT      Enable encryption
    BACKUP_GPG_KEY      GPG key for encryption
    BACKUP_VERIFY       Verify backups
    DB_TYPE             Database type
    DB_HOST             Database host
    DB_NAME             Database name
    DB_USER             Database username
    DB_PASSWORD         Database password

Examples:
    # Full backup with defaults
    $(basename "$0")

    # Database-only backup with 30-day retention
    $(basename "$0") --type=database --retention=30

    # Encrypted backup uploaded to S3
    $(basename "$0") --encrypt --gpg-key=admin@example.org --s3=my-backups-bucket

    # Dry run to see what would be backed up
    $(basename "$0") --dry-run

Cron Example:
    # Daily backup at 2 AM
    0 2 * * * /app/docker/backup.sh >> /app/logs/backup.log 2>&1
EOF
}

# =============================================================================
# Main Execution
# =============================================================================

main() {
    local start_time
    start_time=$(date +%s)

    log_info "=========================================="
    log_info "YAWL Backup Script v${SCRIPT_VERSION}"
    log_info "=========================================="
    log_info "Backup type: $BACKUP_TYPE"
    log_info "Backup path: $BACKUP_PATH"

    # Parse command line arguments
    parse_args "$@"

    # Rotate log if needed
    rotate_log

    # Ensure main backup directory exists
    if [[ "$BACKUP_DRY_RUN" != "true" ]]; then
        mkdir -p "$BACKUP_PATH"
    fi

    local database_backup=""
    local files_backup=""
    local exit_code=0

    # Execute backup based on type
    case "$BACKUP_TYPE" in
        full)
            database_backup=$(backup_database) || exit_code=$?
            files_backup=$(backup_files) || exit_code=$?
            ;;
        database)
            database_backup=$(backup_database) || exit_code=$?
            ;;
        files)
            files_backup=$(backup_files) || exit_code=$?
            ;;
        *)
            log_error "Unknown backup type: $BACKUP_TYPE"
            exit 2
            ;;
    esac

    # Verify backups if enabled
    if [[ "$BACKUP_VERIFY" == "true" ]] && [[ "$BACKUP_DRY_RUN" != "true" ]]; then
        if [[ -n "$database_backup" ]] && [[ -f "$database_backup" ]]; then
            verify_backup "$database_backup" || log_warn "Database backup verification failed"
        fi
        if [[ -n "$files_backup" ]] && [[ -f "$files_backup" ]]; then
            verify_backup "$files_backup" || log_warn "Files backup verification failed"
        fi
    fi

    # Upload to external storage
    if [[ "$BACKUP_DRY_RUN" != "true" ]]; then
        if [[ -n "$database_backup" ]] && [[ -f "$database_backup" ]]; then
            upload_to_s3 "$database_backup" || log_warn "S3 upload failed for database backup"
            upload_to_sftp "$database_backup" || log_warn "SFTP upload failed for database backup"
            copy_to_external "$database_backup" || log_warn "External copy failed for database backup"
        fi
        if [[ -n "$files_backup" ]] && [[ -f "$files_backup" ]]; then
            upload_to_s3 "$files_backup" || log_warn "S3 upload failed for files backup"
            upload_to_sftp "$files_backup" || log_warn "SFTP upload failed for files backup"
            copy_to_external "$files_backup" || log_warn "External copy failed for files backup"
        fi
    fi

    # Apply retention policy
    apply_retention_policy || log_warn "Retention cleanup failed"

    # Generate summary
    local end_time
    end_time=$(date +%s)
    generate_summary "$database_backup" "$files_backup" "$start_time" "$end_time"

    if [[ $exit_code -eq 0 ]]; then
        log_info "Backup completed successfully"
    else
        log_error "Backup completed with errors (exit code: $exit_code)"
    fi

    exit $exit_code
}

# =============================================================================
# Script Entry Point
# =============================================================================

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
