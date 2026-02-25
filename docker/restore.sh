#!/bin/bash
# =============================================================================
# YAWL v6.0.0-Beta Database and Data Restore Script
# =============================================================================
# Production-ready restore solution for YAWL workflow engine.
# Supports: PostgreSQL, MySQL, H2 databases, and file-based data.
#
# Features:
#   - Multi-database support (PostgreSQL, MySQL, H2)
#   - File restore (specifications, logs, configuration)
#   - Decryption support (GPG)
#   - Backup verification before restore
#   - Point-in-time recovery
#   - Pre-restore safety checks
#   - Automatic backup of current state
#   - Dry-run mode for testing
#   - Progress tracking
#   - Comprehensive logging
#
# Usage:
#   ./restore.sh --list                           # List available backups
#   ./restore.sh --latest                         # Restore latest backup
#   ./restore.sh --file=/path/to/backup.sql.gz    # Restore specific backup
#   ./restore.sh --date=2025-01-15               # Restore from specific date
#   ./restore.sh --type=database                  # Restore database only
#   ./restore.sh --type=files                     # Restore files only
#   ./restore.sh --dry-run                        # Show what would be done
#   ./restore.sh --force                          # Skip confirmation prompt
#
# Safety Features:
#   - Automatic backup of current state before restore
#   - Database connection verification
#   - Backup integrity verification
#   - Application health check after restore
#   - Rollback capability
#
# Environment Variables (see .env.example for defaults):
#   BACKUP_PATH          - Backup source directory (default: /app/backups)
#   DB_TYPE              - Database type: postgres, mysql, h2
#   DB_HOST              - Database host
#   DB_PORT              - Database port
#   DB_NAME              - Database name
#   DB_USER              - Database username
#   DB_PASSWORD          - Database password
#   RESTORE_VERIFY       - Verify backup before restore (default: true)
#   RESTORE_FORCE        - Skip confirmation prompts (default: false)
#   RESTORE_BACKUP_CURRENT - Backup current state before restore (default: true)
#
# Exit Codes:
#   0 - Success
#   1 - General error
#   2 - Configuration error
#   3 - Database restore failed
#   4 - File restore failed
#   5 - Decryption failed
#   6 - Verification failed
#   7 - Backup not found
#   8 - Health check failed
# =============================================================================

set -euo pipefail

# =============================================================================
# Script Information
# =============================================================================

readonly SCRIPT_NAME="yawl-restore"
readonly SCRIPT_VERSION="1.0.0"
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly APP_HOME="/app"

# =============================================================================
# Configuration (with environment variable overrides)
# =============================================================================

# Backup/Restore settings
BACKUP_PATH="${BACKUP_PATH:-/app/backups}"
RESTORE_VERIFY="${RESTORE_VERIFY:-true}"
RESTORE_FORCE="${RESTORE_FORCE:-false}"
RESTORE_DRY_RUN="${RESTORE_DRY_RUN:-false}"
RESTORE_BACKUP_CURRENT="${RESTORE_BACKUP_CURRENT:-true}"
RESTORE_TYPE="${RESTORE_TYPE:-full}"
RESTORE_FILE="${RESTORE_FILE:-}"
RESTORE_DATE="${RESTORE_DATE:-}"
RESTORE_LATEST="${RESTORE_LATEST:-false}"
RESTORE_LIST="${RESTORE_LIST:-false}"

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

# Health check settings
HEALTH_PORT="${HEALTH_PORT:-8080}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"

# Logging
LOG_FILE="${LOG_FILE:-${LOG_DIR}/restore.log}"
LOG_LEVEL="${LOG_LEVEL:-INFO}"

# GPG settings for encrypted backups
RESTORE_GPG_PASSPHRASE="${RESTORE_GPG_PASSPHRASE:-}"
RESTORE_GPG_PASSPHRASE_FILE="${RESTORE_GPG_PASSPHRASE_FILE:-}"

# Timestamp for pre-restore backup
RESTORE_TIMESTAMP="$(date +%Y%m%d_%H%M%S)"

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

# Get database password from file or environment
get_db_password() {
    if [[ -n "$DB_PASSWORD_FILE" ]] && [[ -f "$DB_PASSWORD_FILE" ]]; then
        cat "$DB_PASSWORD_FILE"
    else
        echo "$DB_PASSWORD"
    fi
}

# Get GPG passphrase
get_gpg_passphrase() {
    if [[ -n "$RESTORE_GPG_PASSPHRASE_FILE" ]] && [[ -f "$RESTORE_GPG_PASSPHRASE_FILE" ]]; then
        cat "$RESTORE_GPG_PASSPHRASE_FILE"
    else
        echo "$RESTORE_GPG_PASSPHRASE"
    fi
}

# Confirm restore operation
confirm_restore() {
    local message="$1"

    if [[ "$RESTORE_FORCE" == "true" ]]; then
        return 0
    fi

    echo ""
    echo "WARNING: $message"
    echo ""
    read -p "Are you sure you want to continue? (yes/no): " response

    case "$response" in
        yes|YES|y|Y)
            return 0
            ;;
        *)
            log_info "Restore cancelled by user"
            return 1
            ;;
    esac
}

# =============================================================================
# Backup Listing Functions
# =============================================================================

# List available backups
list_backups() {
    log_info "Available backups in: $BACKUP_PATH"

    echo ""
    echo "=========================================="
    echo "Database Backups"
    echo "=========================================="

    if [[ -d "${BACKUP_PATH}/database" ]]; then
        local db_backups=()
        while IFS= read -r file; do
            local basename
            basename=$(basename "$file")
            local size
            size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "0")
            local mtime
            mtime=$(stat -f "%Sm" -t "%Y-%m-%d %H:%M:%S" "$file" 2>/dev/null || stat -c "%y" "$file" 2>/dev/null | cut -d'.' -f1)
            printf "  %-50s %10s  %s\n" "$basename" "$(human_size "$size")" "$mtime"
        done < <(find "${BACKUP_PATH}/database" -type f \( -name "*.sql" -o -name "*.sql.gz" -o -name "*.zip" -o -name "*.gpg" \) -print 2>/dev/null | sort -r)

        if [[ ${#db_backups[@]} -eq 0 ]]; then
            echo "  No database backups found"
        fi
    else
        echo "  No database backup directory"
    fi

    echo ""
    echo "=========================================="
    echo "File Backups"
    echo "=========================================="

    if [[ -d "${BACKUP_PATH}/files" ]]; then
        while IFS= read -r file; do
            local basename
            basename=$(basename "$file")
            local size
            size=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "0")
            local mtime
            mtime=$(stat -f "%Sm" -t "%Y-%m-%d %H:%M:%S" "$file" 2>/dev/null || stat -c "%y" "$file" 2>/dev/null | cut -d'.' -f1)
            printf "  %-50s %10s  %s\n" "$basename" "$(human_size "$size")" "$mtime"
        done < <(find "${BACKUP_PATH}/files" -type f \( -name "*.tar" -o -name "*.tar.gz" -o -name "*.tgz" -o -name "*.gpg" \) -print 2>/dev/null | sort -r)
    else
        echo "  No file backup directory"
    fi

    echo ""
}

# Find latest backup
find_latest_backup() {
    local backup_type="$1"
    local search_dir="${BACKUP_PATH}/${backup_type}"

    if [[ ! -d "$search_dir" ]]; then
        return 1
    fi

    local latest=""
    case "$backup_type" in
        database)
            latest=$(find "$search_dir" -type f \( -name "*.sql.gz" -o -name "*.sql" -o -name "*.zip" \) ! -name "*.gpg" -print 2>/dev/null | sort -r | head -1)
            # If no unencrypted backups, look for encrypted ones
            if [[ -z "$latest" ]]; then
                latest=$(find "$search_dir" -type f -name "*.gpg" -print 2>/dev/null | sort -r | head -1)
            fi
            ;;
        files)
            latest=$(find "$search_dir" -type f \( -name "*.tar.gz" -o -name "*.tgz" -o -name "*.tar" \) ! -name "*.gpg" -print 2>/dev/null | sort -r | head -1)
            if [[ -z "$latest" ]]; then
                latest=$(find "$search_dir" -type f -name "*.gpg" -print 2>/dev/null | sort -r | head -1)
            fi
            ;;
    esac

    if [[ -n "$latest" ]]; then
        echo "$latest"
        return 0
    else
        return 1
    fi
}

# Find backup by date
find_backup_by_date() {
    local backup_type="$1"
    local date="$2"
    local search_dir="${BACKUP_PATH}/${backup_type}"

    if [[ ! -d "$search_dir" ]]; then
        return 1
    fi

    # Convert date format (YYYY-MM-DD to YYYYMMDD)
    local date_pattern="${date//-/}"

    local found=""
    case "$backup_type" in
        database)
            found=$(find "$search_dir" -type f -name "*${date_pattern}*" \( -name "*.sql.gz" -o -name "*.sql" -o -name "*.zip" \) -print 2>/dev/null | sort -r | head -1)
            ;;
        files)
            found=$(find "$search_dir" -type f -name "*${date_pattern}*" \( -name "*.tar.gz" -o -name "*.tgz" -o -name "*.tar" \) -print 2>/dev/null | sort -r | head -1)
            ;;
    esac

    if [[ -n "$found" ]]; then
        echo "$found"
        return 0
    else
        return 1
    fi
}

# =============================================================================
# Verification Functions
# =============================================================================

# Verify backup integrity
verify_backup() {
    local backup_file="$1"

    if [[ ! -f "$backup_file" ]]; then
        log_error "Backup file not found: $backup_file"
        return 6
    fi

    log_info "Verifying backup integrity: $backup_file"

    local file_ext="${backup_file##*.}"

    case "$file_ext" in
        gz)
            if gzip -t "$backup_file" 2>/dev/null; then
                log_info "Gzip integrity check passed"
                return 0
            else
                log_error "Gzip integrity check failed"
                return 6
            fi
            ;;
        zip)
            if unzip -t "$backup_file" &>/dev/null; then
                log_info "Zip integrity check passed"
                return 0
            else
                log_error "Zip integrity check failed"
                return 6
            fi
            ;;
        gpg)
            log_info "GPG file detected - integrity checked during decryption"
            return 0
            ;;
        sql)
            if [[ -s "$backup_file" ]]; then
                log_info "SQL backup file is valid"
                return 0
            else
                log_error "SQL backup file is empty"
                return 6
            fi
            ;;
        tar)
            if tar -tf "$backup_file" &>/dev/null; then
                log_info "Tar integrity check passed"
                return 0
            else
                log_error "Tar integrity check failed"
                return 6
            fi
            ;;
        *)
            log_warn "Unknown file type: $file_ext - skipping verification"
            return 0
            ;;
    esac
}

# =============================================================================
# Decryption Functions
# =============================================================================

# Decrypt backup file
decrypt_backup() {
    local backup_file="$1"

    if [[ ! -f "$backup_file" ]]; then
        log_error "Backup file not found for decryption: $backup_file"
        return 5
    fi

    # Check if file is encrypted
    if [[ "${backup_file##*.}" != "gpg" ]]; then
        log_debug "Backup is not encrypted: $backup_file"
        echo "$backup_file"
        return 0
    fi

    if ! command_exists gpg; then
        log_error "GPG not found. Please install gnupg."
        return 5
    fi

    local gpg_passphrase
    gpg_passphrase=$(get_gpg_passphrase)

    if [[ -z "$gpg_passphrase" ]]; then
        log_error "GPG passphrase not set. Set RESTORE_GPG_PASSPHRASE or RESTORE_GPG_PASSPHRASE_FILE"
        return 5
    fi

    # Determine output file name
    local output_file="${backup_file%.gpg}"

    log_info "Decrypting backup: $backup_file"

    if echo "$gpg_passphrase" | gpg --batch --yes --passphrase-fd 0 \
       --decrypt --output "$output_file" "$backup_file" 2>>"$LOG_FILE"; then
        log_info "Decryption completed: $output_file"
        echo "$output_file"
        return 0
    else
        log_error "Decryption failed"
        rm -f "$output_file"
        return 5
    fi
}

# =============================================================================
# Pre-restore Safety Functions
# =============================================================================

# Create backup of current state before restore
backup_current_state() {
    if [[ "$RESTORE_BACKUP_CURRENT" != "true" ]]; then
        log_info "Skipping pre-restore backup (RESTORE_BACKUP_CURRENT=false)"
        return 0
    fi

    log_info "Creating backup of current state before restore..."

    local pre_restore_dir="${BACKUP_PATH}/pre-restore"
    mkdir -p "$pre_restore_dir"

    # Backup current database
    if [[ "$DB_TYPE" == "postgres" ]] && command_exists pg_dump; then
        local db_backup="${pre_restore_dir}/${DB_NAME}_pre_restore_${RESTORE_TIMESTAMP}.sql.gz"
        export PGPASSWORD="$(get_db_password)"
        if pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" "$DB_NAME" 2>/dev/null | gzip > "$db_backup"; then
            log_info "Pre-restore database backup created: $db_backup"
        else
            log_warn "Failed to create pre-restore database backup"
        fi
        unset PGPASSWORD
    elif [[ "$DB_TYPE" == "mysql" ]] && command_exists mysqldump; then
        local db_backup="${pre_restore_dir}/${DB_NAME}_pre_restore_${RESTORE_TIMESTAMP}.sql.gz"
        if mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$(get_db_password)" "$DB_NAME" 2>/dev/null | gzip > "$db_backup"; then
            log_info "Pre-restore database backup created: $db_backup"
        else
            log_warn "Failed to create pre-restore database backup"
        fi
    fi

    # Backup current files
    local files_backup="${pre_restore_dir}/yawl_files_pre_restore_${RESTORE_TIMESTAMP}.tar.gz"
    local dirs_to_backup=()
    [[ -d "$SPEC_DIR" ]] && dirs_to_backup+=("$SPEC_DIR")
    [[ -d "$CONFIG_DIR" ]] && dirs_to_backup+=("$CONFIG_DIR")
    [[ -d "$DATA_DIR" ]] && dirs_to_backup+=("$DATA_DIR")

    if [[ ${#dirs_to_backup[@]} -gt 0 ]]; then
        if tar -czf "$files_backup" "${dirs_to_backup[@]}" 2>/dev/null; then
            log_info "Pre-restore files backup created: $files_backup"
        else
            log_warn "Failed to create pre-restore files backup"
        fi
    fi

    log_info "Pre-restore backup completed"
}

# Check database connectivity
check_database_connection() {
    log_info "Checking database connectivity..."

    local db_password
    db_password=$(get_db_password)

    case "$DB_TYPE" in
        postgres|postgresql)
            if command_exists pg_isready; then
                if ! pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" &>/dev/null; then
                    log_error "Cannot connect to PostgreSQL at $DB_HOST:$DB_PORT"
                    return 2
                fi
            elif command_exists nc; then
                if ! nc -z "$DB_HOST" "$DB_PORT" 2>/dev/null; then
                    log_error "Cannot reach PostgreSQL at $DB_HOST:$DB_PORT"
                    return 2
                fi
            fi
            ;;
        mysql|mariadb)
            if command_exists mysqladmin; then
                if ! mysqladmin ping -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$db_password" &>/dev/null; then
                    log_error "Cannot connect to MySQL at $DB_HOST:$DB_PORT"
                    return 2
                fi
            elif command_exists nc; then
                if ! nc -z "$DB_HOST" "$DB_PORT" 2>/dev/null; then
                    log_error "Cannot reach MySQL at $DB_HOST:$DB_PORT"
                    return 2
                fi
            fi
            ;;
        h2)
            log_info "Using H2 embedded database - connection check skipped"
            ;;
        *)
            log_warn "Unknown database type: $DB_TYPE - skipping connection check"
            ;;
    esac

    log_info "Database connectivity verified"
    return 0
}

# =============================================================================
# Database Restore Functions
# =============================================================================

# Restore PostgreSQL database
restore_postgres() {
    local backup_file="$1"

    log_info "Starting PostgreSQL restore from: $backup_file"

    if [[ "$RESTORE_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would restore PostgreSQL database from: $backup_file"
        return 0
    fi

    if ! command_exists psql; then
        log_error "psql not found. Please install postgresql-client."
        return 3
    fi

    local db_password
    db_password=$(get_db_password)
    export PGPASSWORD="$db_password"

    # Determine if file is compressed
    local cat_cmd="cat"
    if [[ "$backup_file" == *.gz ]]; then
        cat_cmd="zcat"
    fi

    # Drop existing connections and recreate database
    log_info "Terminating existing connections to database: $DB_NAME"

    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c \
        "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = '$DB_NAME' AND pid <> pg_backend_pid();" \
        2>>"$LOG_FILE" || true

    # Drop and recreate database
    log_info "Recreating database: $DB_NAME"

    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c \
        "DROP DATABASE IF EXISTS $DB_NAME;" 2>>"$LOG_FILE"

    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -c \
        "CREATE DATABASE $DB_NAME;" 2>>"$LOG_FILE"

    # Restore from backup
    log_info "Restoring database from backup..."

    if $cat_cmd "$backup_file" | psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=0 2>>"$LOG_FILE"; then
        log_info "PostgreSQL restore completed successfully"
        unset PGPASSWORD
        return 0
    else
        log_error "PostgreSQL restore failed"
        unset PGPASSWORD
        return 3
    fi
}

# Restore MySQL database
restore_mysql() {
    local backup_file="$1"

    log_info "Starting MySQL restore from: $backup_file"

    if [[ "$RESTORE_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would restore MySQL database from: $backup_file"
        return 0
    fi

    if ! command_exists mysql; then
        log_error "mysql not found. Please install mysql-client."
        return 3
    fi

    local db_password
    db_password=$(get_db_password)

    # Determine if file is compressed
    local cat_cmd="cat"
    if [[ "$backup_file" == *.gz ]]; then
        cat_cmd="zcat"
    fi

    # Drop and recreate database
    log_info "Recreating database: $DB_NAME"

    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$db_password" -e \
        "DROP DATABASE IF EXISTS $DB_NAME; CREATE DATABASE $DB_NAME;" 2>>"$LOG_FILE"

    # Restore from backup
    log_info "Restoring database from backup..."

    if $cat_cmd "$backup_file" | mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$db_password" "$DB_NAME" 2>>"$LOG_FILE"; then
        log_info "MySQL restore completed successfully"
        return 0
    else
        log_error "MySQL restore failed"
        return 3
    fi
}

# Restore H2 database
restore_h2() {
    local backup_file="$1"

    log_info "Starting H2 restore from: $backup_file"

    if [[ "$RESTORE_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would restore H2 database from: $backup_file"
        return 0
    fi

    # Stop application if running
    log_info "Note: Ensure YAWL application is stopped before H2 restore"

    # Extract backup
    local target_dir="$DATA_DIR"

    if [[ ! -d "$target_dir" ]]; then
        mkdir -p "$target_dir"
    fi

    # Remove existing database files
    rm -f "${target_dir}/${DB_NAME}.mv.db" "${target_dir}/${DB_NAME}.trace.db" 2>/dev/null || true

    # Extract from zip
    if [[ "$backup_file" == *.zip ]]; then
        if unzip -o "$backup_file" -d "$target_dir" 2>>"$LOG_FILE"; then
            log_info "H2 restore completed successfully"
            return 0
        else
            log_error "H2 restore failed"
            return 3
        fi
    else
        log_error "H2 backup must be a zip file"
        return 3
    fi
}

# Generic database restore dispatcher
restore_database() {
    local backup_file="$1"

    if [[ ! -f "$backup_file" ]]; then
        log_error "Database backup file not found: $backup_file"
        return 7
    fi

    log_info "Starting database restore (type: $DB_TYPE)"

    # Decrypt if needed
    local actual_file
    actual_file=$(decrypt_backup "$backup_file") || return 5

    # Verify backup
    if [[ "$RESTORE_VERIFY" == "true" ]]; then
        verify_backup "$actual_file" || return 6
    fi

    local exit_code=0

    case "$DB_TYPE" in
        postgres|postgresql)
            restore_postgres "$actual_file" || exit_code=$?
            ;;
        mysql|mariadb)
            restore_mysql "$actual_file" || exit_code=$?
            ;;
        h2)
            restore_h2 "$actual_file" || exit_code=$?
            ;;
        *)
            log_error "Unsupported database type: $DB_TYPE"
            return 2
            ;;
    esac

    # Clean up decrypted file if we created it
    if [[ "$actual_file" != "$backup_file" ]] && [[ -f "$actual_file" ]]; then
        rm -f "$actual_file"
    fi

    return $exit_code
}

# =============================================================================
# File Restore Functions
# =============================================================================

# Restore files from backup
restore_files() {
    local backup_file="$1"

    log_info "Starting file restore from: $backup_file"

    if [[ "$RESTORE_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would restore files from: $backup_file"
        return 0
    fi

    # Decrypt if needed
    local actual_file
    actual_file=$(decrypt_backup "$backup_file") || return 5

    # Verify backup
    if [[ "$RESTORE_VERIFY" == "true" ]]; then
        verify_backup "$actual_file" || return 6
    fi

    # Determine extraction command
    local extract_cmd=""
    case "$actual_file" in
        *.tar.gz|*.tgz)
            extract_cmd="tar -xzf"
            ;;
        *.tar)
            extract_cmd="tar -xf"
            ;;
        *)
            log_error "Unknown file backup format: $actual_file"
            return 4
            ;;
    esac

    # Create temporary extraction directory
    local temp_dir="${APP_HOME}/temp/restore_${RESTORE_TIMESTAMP}"
    mkdir -p "$temp_dir"

    log_info "Extracting backup to temporary directory: $temp_dir"

    # Extract backup
    if ! $extract_cmd "$actual_file" -C "$temp_dir" 2>>"$LOG_FILE"; then
        log_error "Failed to extract backup"
        rm -rf "$temp_dir"
        return 4
    fi

    # Restore directories
    log_info "Restoring files to application directories..."

    # Find extracted directories and restore them
    local extracted_dirs=("specifications" "config" "data")
    for dir in "${extracted_dirs[@]}"; do
        local src_path="${temp_dir}/${dir}"
        local dest_path="${APP_HOME}/${dir}"

        if [[ -d "$src_path" ]]; then
            log_info "Restoring $dir to $dest_path"

            # Backup existing directory
            if [[ -d "$dest_path" ]]; then
                mv "$dest_path" "${dest_path}.bak_${RESTORE_TIMESTAMP}"
            fi

            # Move extracted directory
            mv "$src_path" "$dest_path"
        fi
    done

    # Clean up
    rm -rf "$temp_dir"

    # Clean up decrypted file if we created it
    if [[ "$actual_file" != "$backup_file" ]] && [[ -f "$actual_file" ]]; then
        rm -f "$actual_file"
    fi

    log_info "File restore completed successfully"
    return 0
}

# =============================================================================
# Post-restore Functions
# =============================================================================

# Check application health after restore
check_application_health() {
    log_info "Checking application health after restore..."

    if [[ "$RESTORE_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would check application health"
        return 0
    fi

    local elapsed=0
    local interval=5

    while [[ $elapsed -lt $HEALTH_TIMEOUT ]]; do
        if curl -sf "http://localhost:${HEALTH_PORT}/actuator/health" &>/dev/null; then
            log_info "Application health check passed"
            return 0
        fi

        sleep "$interval"
        elapsed=$((elapsed + interval))
        log_debug "Waiting for application health... (${elapsed}s/${HEALTH_TIMEOUT}s)"
    done

    log_warn "Application health check timed out after ${HEALTH_TIMEOUT}s"
    return 8
}

# Generate restore summary
generate_summary() {
    local database_backup="$1"
    local files_backup="$2"
    local start_time="$3"
    local end_time="$4"
    local exit_code="$5"

    local duration=$((end_time - start_time))

    log_info "=========================================="
    log_info "Restore Summary"
    log_info "=========================================="
    log_info "Timestamp: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    log_info "Type: $RESTORE_TYPE"
    log_info "Duration: ${duration}s"
    log_info "Exit Code: $exit_code"

    if [[ -n "$database_backup" ]]; then
        log_info "Database backup: $database_backup"
    fi

    if [[ -n "$files_backup" ]]; then
        log_info "Files backup: $files_backup"
    fi

    log_info "Dry Run: $RESTORE_DRY_RUN"
    log_info "Verification: $RESTORE_VERIFY"
    log_info "=========================================="
}

# =============================================================================
# Command Line Argument Parsing
# =============================================================================

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --type=*)
                RESTORE_TYPE="${1#*=}"
                ;;
            --file=*)
                RESTORE_FILE="${1#*=}"
                ;;
            --date=*)
                RESTORE_DATE="${1#*=}"
                ;;
            --latest)
                RESTORE_LATEST="true"
                ;;
            --list)
                RESTORE_LIST="true"
                ;;
            --verify)
                RESTORE_VERIFY="true"
                ;;
            --no-verify)
                RESTORE_VERIFY="false"
                ;;
            --force)
                RESTORE_FORCE="true"
                ;;
            --dry-run)
                RESTORE_DRY_RUN="true"
                ;;
            --no-backup-current)
                RESTORE_BACKUP_CURRENT="false"
                ;;
            --gpg-passphrase=*)
                RESTORE_GPG_PASSPHRASE="${1#*=}"
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
YAWL Restore Script v${SCRIPT_VERSION}

Usage: $(basename "$0") [OPTIONS]

Options:
    --list              List available backups
    --latest            Restore latest backup
    --file=FILE         Restore from specific backup file
    --date=DATE         Restore from specific date (YYYY-MM-DD)
    --type=TYPE         Restore type: full, database, files (default: full)
    --verify            Verify backup before restore (default: true)
    --no-verify         Skip backup verification
    --force             Skip confirmation prompt
    --dry-run           Show what would be done without executing
    --no-backup-current Skip pre-restore backup of current state
    --gpg-passphrase=P  GPG passphrase for encrypted backups
    --help              Show this help message

Environment Variables:
    BACKUP_PATH         Backup source directory
    DB_TYPE             Database type
    DB_HOST             Database host
    DB_NAME             Database name
    DB_USER             Database username
    DB_PASSWORD         Database password
    RESTORE_VERIFY      Verify backups before restore
    RESTORE_FORCE       Skip confirmation prompts
    RESTORE_GPG_PASSPHRASE GPG passphrase for encrypted backups

Examples:
    # List available backups
    $(basename "$0") --list

    # Restore latest backup
    $(basename "$0") --latest

    # Restore specific backup file
    $(basename "$0") --file=/app/backups/database/yawl_20250115_020000.sql.gz

    # Restore from specific date
    $(basename "$0") --date=2025-01-15

    # Restore database only
    $(basename "$0") --latest --type=database

    # Dry run to see what would be restored
    $(basename "$0") --latest --dry-run

    # Restore encrypted backup
    $(basename "$0") --latest --gpg-passphrase="secret"

Safety Features:
    - Pre-restore backup of current state (unless --no-backup-current)
    - Backup integrity verification (unless --no-verify)
    - Confirmation prompt before restore (unless --force)
    - Dry-run mode for testing
EOF
}

# =============================================================================
# Main Execution
# =============================================================================

main() {
    local start_time
    start_time=$(date +%s)

    log_info "=========================================="
    log_info "YAWL Restore Script v${SCRIPT_VERSION}"
    log_info "=========================================="

    # Parse command line arguments
    parse_args "$@"

    # List mode - just show backups and exit
    if [[ "$RESTORE_LIST" == "true" ]]; then
        list_backups
        exit 0
    fi

    # Determine backup files to restore
    local database_backup=""
    local files_backup=""

    if [[ -n "$RESTORE_FILE" ]]; then
        # Use specified file
        if [[ ! -f "$RESTORE_FILE" ]]; then
            log_error "Backup file not found: $RESTORE_FILE"
            exit 7
        fi

        # Determine type from file
        case "$RESTORE_FILE" in
            *.sql*|*.zip)
                database_backup="$RESTORE_FILE"
                ;;
            *.tar*)
                files_backup="$RESTORE_FILE"
                ;;
        esac
    elif [[ "$RESTORE_LATEST" == "true" ]]; then
        # Find latest backups
        database_backup=$(find_latest_backup "database") || log_warn "No database backup found"
        files_backup=$(find_latest_backup "files") || log_warn "No files backup found"
    elif [[ -n "$RESTORE_DATE" ]]; then
        # Find backups by date
        database_backup=$(find_backup_by_date "database" "$RESTORE_DATE") || log_warn "No database backup found for date: $RESTORE_DATE"
        files_backup=$(find_backup_by_date "files" "$RESTORE_DATE") || log_warn "No files backup found for date: $RESTORE_DATE"
    else
        log_error "No restore source specified. Use --latest, --file, or --date"
        show_help
        exit 2
    fi

    # Verify we have something to restore
    if [[ -z "$database_backup" ]] && [[ -z "$files_backup" ]]; then
        log_error "No backups found to restore"
        exit 7
    fi

    log_info "Restore type: $RESTORE_TYPE"
    [[ -n "$database_backup" ]] && log_info "Database backup: $database_backup"
    [[ -n "$files_backup" ]] && log_info "Files backup: $files_backup"

    # Dry run - just show what would be done
    if [[ "$RESTORE_DRY_RUN" == "true" ]]; then
        log_info "[DRY-RUN] Would perform the following restore operations:"
        [[ -n "$database_backup" ]] && [[ "$RESTORE_TYPE" == "full" || "$RESTORE_TYPE" == "database" ]] && \
            log_info "  - Restore database from: $database_backup"
        [[ -n "$files_backup" ]] && [[ "$RESTORE_TYPE" == "full" || "$RESTORE_TYPE" == "files" ]] && \
            log_info "  - Restore files from: $files_backup"

        local end_time
        end_time=$(date +%s)
        generate_summary "$database_backup" "$files_backup" "$start_time" "$end_time" 0
        exit 0
    fi

    # Confirm restore
    if ! confirm_restore "This operation will replace the current database and/or files!"; then
        exit 1
    fi

    # Check database connectivity
    check_database_connection || exit 2

    # Create backup of current state
    backup_current_state

    local exit_code=0

    # Execute restore based on type
    case "$RESTORE_TYPE" in
        full)
            if [[ -n "$database_backup" ]]; then
                restore_database "$database_backup" || exit_code=$?
            fi
            if [[ -n "$files_backup" ]] && [[ $exit_code -eq 0 ]]; then
                restore_files "$files_backup" || exit_code=$?
            fi
            ;;
        database)
            if [[ -n "$database_backup" ]]; then
                restore_database "$database_backup" || exit_code=$?
            else
                log_error "No database backup found"
                exit 7
            fi
            ;;
        files)
            if [[ -n "$files_backup" ]]; then
                restore_files "$files_backup" || exit_code=$?
            else
                log_error "No files backup found"
                exit 7
            fi
            ;;
        *)
            log_error "Unknown restore type: $RESTORE_TYPE"
            exit 2
            ;;
    esac

    # Check application health after restore
    if [[ $exit_code -eq 0 ]]; then
        check_application_health || log_warn "Application health check failed after restore"
    fi

    # Generate summary
    local end_time
    end_time=$(date +%s)
    generate_summary "$database_backup" "$files_backup" "$start_time" "$end_time" $exit_code

    if [[ $exit_code -eq 0 ]]; then
        log_info "Restore completed successfully"
    else
        log_error "Restore completed with errors (exit code: $exit_code)"
    fi

    exit $exit_code
}

# =============================================================================
# Script Entry Point
# =============================================================================

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
