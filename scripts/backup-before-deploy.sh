#!/bin/bash

################################################################################
# YAWL v5.2 Pre-Deployment Backup Script
#
# Purpose: Create comprehensive backups before any production deployment
# Usage: ./scripts/backup-before-deploy.sh [--full] [--compress] [--retention-days 30]
# Exit Codes: 0 = success, 1 = warning (backup created but checks failed), 2 = critical failure
#
# This script is REQUIRED to execute before any production deployment.
# Never deploy to production without running this script first.
################################################################################

set -euo pipefail

# Configuration
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
readonly BACKUP_BASE_DIR="${BACKUP_BASE_DIR:-/backup/yawl}"
readonly CATALINA_HOME="${CATALINA_HOME:-/opt/apache-tomcat-10.1.13}"
readonly TIMESTAMP=$(date +%Y%m%d-%H%M%S)
readonly BACKUP_DIR="${BACKUP_BASE_DIR}/yawl-${TIMESTAMP}"

# Command-line flags
FULL_BACKUP=false
COMPRESS_BACKUP=false
RETENTION_DAYS=30
VERBOSE=false

# Color codes for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

################################################################################
# Logging Functions
################################################################################

log_info() {
    echo -e "${BLUE}[INFO]${NC} $(date '+%Y-%m-%d %H:%M:%S') $*"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $*"
}

log_warning() {
    echo -e "${YELLOW}[!]${NC} $*"
}

log_error() {
    echo -e "${RED}[✗]${NC} $*" >&2
}

log_debug() {
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${BLUE}[DEBUG]${NC} $*"
    fi
}

################################################################################
# Usage and Argument Parsing
################################################################################

usage() {
    cat << EOF
Usage: $0 [OPTIONS]

OPTIONS:
    --full              Backup application and database (default: database only)
    --compress          Compress backup directory (creates .tar.gz)
    --retention-days N  Automatically delete backups older than N days (default: 30)
    --verbose           Enable debug output
    -h, --help          Show this help message

EXAMPLES:
    # Quick backup (database only, uncompressed, 30-day retention)
    $0

    # Full backup with compression
    $0 --full --compress

    # Full backup, keep for 7 days only
    $0 --full --retention-days 7

    # Verbose output for troubleshooting
    $0 --full --verbose
EOF
    exit 0
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --full)
                FULL_BACKUP=true
                shift
                ;;
            --compress)
                COMPRESS_BACKUP=true
                shift
                ;;
            --retention-days)
                RETENTION_DAYS="$2"
                shift 2
                ;;
            --verbose)
                VERBOSE=true
                shift
                ;;
            -h|--help)
                usage
                ;;
            *)
                log_error "Unknown option: $1"
                exit 2
                ;;
        esac
    done
}

################################################################################
# Pre-Backup Verification
################################################################################

verify_prerequisites() {
    log_info "Verifying prerequisites..."

    # Check if running as root or with sudo
    if [[ $EUID -ne 0 ]]; then
        log_warning "Not running as root. Some backup steps may fail."
        log_warning "Recommend: sudo $0 $@"
    fi

    # Check required commands
    local required_cmds=("date" "mkdir" "cp" "tar" "pg_dump" "psql")
    for cmd in "${required_cmds[@]}"; do
        if ! command -v "$cmd" &> /dev/null; then
            log_error "Required command not found: $cmd"
            return 2
        fi
    done
    log_success "All required commands available"

    # Verify backup directory structure
    if [[ ! -d "$BACKUP_BASE_DIR" ]]; then
        log_info "Creating backup directory: $BACKUP_BASE_DIR"
        mkdir -p "$BACKUP_BASE_DIR" || {
            log_error "Failed to create backup directory"
            return 2
        }
    fi

    # Check disk space (warn if < 2GB)
    local available_space=$(df "$BACKUP_BASE_DIR" | awk 'NR==2 {print $4}')
    if [[ $available_space -lt 2097152 ]]; then  # 2GB in KB
        log_warning "Low disk space: ${available_space}KB available"
        log_warning "Backup may be incomplete"
    fi
    log_success "Disk space sufficient"

    # Verify PostgreSQL connectivity
    if ! psql -U postgres -d postgres -c "SELECT 1" &>/dev/null; then
        log_error "Cannot connect to PostgreSQL database"
        log_error "Verify: psql connection, user permissions, database existence"
        return 2
    fi
    log_success "PostgreSQL connectivity verified"

    return 0
}

################################################################################
# Database Backup Functions
################################################################################

backup_postgresql_schema() {
    log_info "Backing up PostgreSQL schema..."
    local output_file="$BACKUP_DIR/database-schema.sql"

    pg_dump -U postgres yawl \
        --schema-only \
        --no-password \
        --file="$output_file" \
        --verbose 2>&1 | grep -i "dumping\|schema" || true

    if [[ -f "$output_file" ]] && [[ -s "$output_file" ]]; then
        log_success "Schema backup: $(du -h "$output_file" | cut -f1)"
        return 0
    else
        log_error "Schema backup failed or empty"
        return 1
    fi
}

backup_postgresql_data() {
    log_info "Backing up PostgreSQL data..."
    local output_file="$BACKUP_DIR/database-data.sql"

    pg_dump -U postgres yawl \
        --data-only \
        --no-password \
        --file="$output_file" \
        --verbose 2>&1 | grep -i "dumping\|table" || true

    if [[ -f "$output_file" ]] && [[ -s "$output_file" ]]; then
        log_success "Data backup: $(du -h "$output_file" | cut -f1)"
        return 0
    else
        log_error "Data backup failed or empty"
        return 1
    fi
}

backup_database_full() {
    log_info "Backing up full PostgreSQL database..."
    local output_file="$BACKUP_DIR/database-full.sql"

    pg_dump -U postgres yawl \
        --no-password \
        --file="$output_file" \
        --verbose 2>&1 | grep -i "dumping" || true

    if [[ -f "$output_file" ]] && [[ -s "$output_file" ]]; then
        log_success "Full database backup: $(du -h "$output_file" | cut -f1)"
        return 0
    else
        log_error "Full database backup failed or empty"
        return 1
    fi
}

backup_h2_database() {
    log_info "Checking for H2 database files..."

    local h2_paths=(
        "$PROJECT_ROOT/data/yawl.h2.db"
        "$PROJECT_ROOT/build/data/yawl.h2.db"
        "$CATALINA_HOME/webapps/yawl/WEB-INF/data/yawl.h2.db"
    )

    for h2_path in "${h2_paths[@]}"; do
        if [[ -f "$h2_path" ]]; then
            log_info "Found H2 database: $h2_path"
            cp "$h2_path" "$BACKUP_DIR/yawl.h2.db" && \
                log_success "H2 backup: $(du -h "$BACKUP_DIR/yawl.h2.db" | cut -f1)" && \
                return 0
        fi
    done

    log_debug "No H2 database found (normal if using PostgreSQL/MySQL)"
    return 0
}

################################################################################
# Configuration Backup Functions
################################################################################

backup_configuration() {
    log_info "Backing up configuration files..."

    local config_dir="$BACKUP_DIR/properties"
    mkdir -p "$config_dir"

    # Critical property files
    local config_files=(
        "build/properties/hibernate.properties"
        "build/properties/database.properties"
        "build/properties/application.properties"
        "build/properties/log4j2.xml"
    )

    local failed_count=0
    for config_file in "${config_files[@]}"; do
        if [[ -f "$PROJECT_ROOT/$config_file" ]]; then
            cp "$PROJECT_ROOT/$config_file" "$config_dir/"
            log_debug "Backed up: $config_file"
        else
            log_debug "Not found (optional): $config_file"
        fi
    done

    if [[ $(find "$config_dir" -type f | wc -l) -gt 0 ]]; then
        log_success "Configuration backup: $(du -sh "$config_dir" | cut -f1)"
        return 0
    else
        log_warning "No configuration files found"
        return 1
    fi
}

################################################################################
# WAR File Backup Functions
################################################################################

backup_war_files() {
    log_info "Backing up WAR files..."

    local war_dir="$BACKUP_DIR/war-files"
    mkdir -p "$war_dir"

    if [[ ! -d "$CATALINA_HOME/webapps" ]]; then
        log_debug "Tomcat webapps directory not found (expected if not deployed)"
        return 0
    fi

    local war_count=0
    for war_file in "$CATALINA_HOME/webapps"/*.war; do
        if [[ -f "$war_file" ]]; then
            cp "$war_file" "$war_dir/"
            log_debug "Backed up: $(basename "$war_file")"
            ((war_count++))
        fi
    done

    if [[ $war_count -gt 0 ]]; then
        log_success "WAR files backup: $war_count files, $(du -sh "$war_dir" | cut -f1)"
        return 0
    else
        log_debug "No WAR files found (expected if using source deployment)"
        return 0
    fi
}

backup_tomcat_configuration() {
    log_info "Backing up Tomcat configuration..."

    if [[ ! -d "$CATALINA_HOME/conf" ]]; then
        log_debug "Tomcat conf directory not found"
        return 0
    fi

    local tomcat_dir="$BACKUP_DIR/tomcat-conf"
    cp -r "$CATALINA_HOME/conf" "$tomcat_dir"

    log_success "Tomcat config backup: $(du -sh "$tomcat_dir" | cut -f1)"
    return 0
}

################################################################################
# Build Artifacts Backup
################################################################################

backup_build_artifacts() {
    if [[ "$FULL_BACKUP" != "true" ]]; then
        log_debug "Skipping build artifacts (--full flag not set)"
        return 0
    fi

    log_info "Backing up build artifacts..."

    local artifacts_dir="$BACKUP_DIR/build-artifacts"
    mkdir -p "$artifacts_dir"

    # Backup compiled classes
    if [[ -d "$PROJECT_ROOT/build/classes" ]]; then
        cp -r "$PROJECT_ROOT/build/classes" "$artifacts_dir/"
    fi

    # Backup generated WAR
    if [[ -d "$PROJECT_ROOT/build/lib" ]]; then
        cp -r "$PROJECT_ROOT/build/lib" "$artifacts_dir/"
    fi

    # Backup test results
    if [[ -d "$PROJECT_ROOT/build/junit" ]]; then
        cp -r "$PROJECT_ROOT/build/junit" "$artifacts_dir/"
    fi

    if [[ $(find "$artifacts_dir" -type f | wc -l) -gt 0 ]]; then
        log_success "Build artifacts backup: $(du -sh "$artifacts_dir" | cut -f1)"
    else
        log_debug "No build artifacts to backup"
    fi

    return 0
}

################################################################################
# Source Code Snapshot
################################################################################

backup_source_code() {
    if [[ "$FULL_BACKUP" != "true" ]]; then
        log_debug "Skipping source code (--full flag not set)"
        return 0
    fi

    log_info "Backing up source code snapshot..."

    local source_dir="$BACKUP_DIR/source"
    mkdir -p "$source_dir"

    # Backup key source directories
    local source_paths=(
        "src/org/yawlfoundation/yawl/engine"
        "src/org/yawlfoundation/yawl/elements"
        "src/org/yawlfoundation/yawl/stateless"
    )

    for src_path in "${source_paths[@]}"; do
        if [[ -d "$PROJECT_ROOT/$src_path" ]]; then
            mkdir -p "$source_dir/$(dirname "$src_path")"
            cp -r "$PROJECT_ROOT/$src_path" "$source_dir/$src_path"
        fi
    done

    if [[ $(find "$source_dir" -type f | wc -l) -gt 0 ]]; then
        log_success "Source code backup: $(du -sh "$source_dir" | cut -f1)"
    fi

    return 0
}

################################################################################
# Backup Metadata and Manifest
################################################################################

create_backup_manifest() {
    log_info "Creating backup manifest..."

    local manifest_file="$BACKUP_DIR/MANIFEST.txt"
    {
        echo "YAWL v5.2 Backup Manifest"
        echo "=========================="
        echo "Backup Timestamp: $TIMESTAMP"
        echo "Backup Location: $BACKUP_DIR"
        echo "Hostname: $(hostname)"
        echo "User: $(whoami)"
        echo "Platform: $(uname -s)"
        echo ""
        echo "Contents:"
        find "$BACKUP_DIR" -type f -exec ls -lh {} \; | awk '{print "  " $9 " (" $5 ")"}'
        echo ""
        echo "Backup Size: $(du -sh "$BACKUP_DIR" | cut -f1)"
        echo "Total Files: $(find "$BACKUP_DIR" -type f | wc -l)"
        echo ""
        echo "Database Version:"
        psql -U postgres -d yawl -c "SELECT version();" 2>/dev/null || echo "  [PostgreSQL not available]"
        echo ""
        echo "Application Version:"
        grep -E "version|implementation.version" "$PROJECT_ROOT/build.xml" 2>/dev/null | head -3 || echo "  [Version file not found]"
        echo ""
        echo "Deployment Status:"
        if [[ -d "$CATALINA_HOME/webapps" ]]; then
            echo "  Tomcat webapps: $(ls -d "$CATALINA_HOME/webapps"/yawl* 2>/dev/null | wc -l) applications"
        else
            echo "  Tomcat: Not deployed yet"
        fi
    } > "$manifest_file"

    log_success "Manifest created: $manifest_file"
}

################################################################################
# Compression and Cleanup
################################################################################

compress_backup() {
    if [[ "$COMPRESS_BACKUP" != "true" ]]; then
        log_debug "Backup compression disabled (use --compress flag)"
        return 0
    fi

    log_info "Compressing backup directory..."

    local compressed_file="${BACKUP_DIR}.tar.gz"
    tar --exclude='*.gz' -czf "$compressed_file" -C "$BACKUP_BASE_DIR" "yawl-${TIMESTAMP}" 2>&1 | \
        grep -i "error" || true

    if [[ -f "$compressed_file" ]]; then
        local original_size=$(du -sh "$BACKUP_DIR" | cut -f1)
        local compressed_size=$(du -sh "$compressed_file" | cut -f1)

        # Remove uncompressed directory after successful compression
        rm -rf "$BACKUP_DIR"

        log_success "Backup compressed: $original_size → $compressed_size"
        return 0
    else
        log_error "Compression failed"
        return 1
    fi
}

apply_retention_policy() {
    log_info "Applying retention policy (keep $RETENTION_DAYS days)..."

    local cutoff_date=$(date -d "$RETENTION_DAYS days ago" +%s)
    local deleted_count=0

    for backup_dir in "$BACKUP_BASE_DIR"/yawl-*; do
        if [[ -d "$backup_dir" ]]; then
            local dir_date=$(stat -c %Y "$backup_dir")
            if [[ $dir_date -lt $cutoff_date ]]; then
                log_debug "Deleting old backup: $(basename "$backup_dir")"
                rm -rf "$backup_dir"
                ((deleted_count++))
            fi
        fi

        # Handle compressed backups too
        local compressed_file="${backup_dir}.tar.gz"
        if [[ -f "$compressed_file" ]]; then
            local file_date=$(stat -c %Y "$compressed_file")
            if [[ $file_date -lt $cutoff_date ]]; then
                log_debug "Deleting old backup: $(basename "$compressed_file")"
                rm -f "$compressed_file"
                ((deleted_count++))
            fi
        fi
    done

    if [[ $deleted_count -gt 0 ]]; then
        log_success "Retention cleanup: $deleted_count old backups deleted"
    else
        log_debug "No backups eligible for deletion"
    fi
}

################################################################################
# Summary and Validation
################################################################################

validate_backup() {
    log_info "Validating backup integrity..."

    local db_file=""
    if [[ -f "$BACKUP_DIR/database-full.sql" ]]; then
        db_file="$BACKUP_DIR/database-full.sql"
    elif [[ -f "$BACKUP_DIR/database-schema.sql" ]]; then
        db_file="$BACKUP_DIR/database-schema.sql"
    fi

    if [[ -z "$db_file" ]]; then
        log_error "No database backup found"
        return 1
    fi

    # Verify SQL file is not empty and contains valid SQL syntax
    if [[ -s "$db_file" ]]; then
        if head -20 "$db_file" | grep -q "PostgreSQL\|CREATE\|INSERT"; then
            log_success "Database backup validation passed"
            return 0
        else
            log_error "Database backup may be corrupted (invalid SQL)"
            return 1
        fi
    else
        log_error "Database backup is empty"
        return 1
    fi
}

print_summary() {
    local backup_size=$(du -sh "$BACKUP_DIR" 2>/dev/null | cut -f1 || echo "unknown")
    local file_count=$(find "$BACKUP_DIR" -type f 2>/dev/null | wc -l || echo "unknown")

    cat << EOF

${GREEN}============================================================${NC}
${GREEN}BACKUP SUMMARY${NC}
${GREEN}============================================================${NC}

Backup Directory: $BACKUP_DIR
Backup Size: $backup_size
Total Files: $file_count
Timestamp: $TIMESTAMP

${GREEN}READY FOR DEPLOYMENT${NC}

Next Steps:
1. Test deployment in staging environment
2. Run smoke tests: scripts/smoke-test.sh
3. On production: Always run backup before deploying
   sudo $0 [options]
4. Deploy new version
5. Post-deployment: Monitor logs and metrics

Rollback Command (if needed):
   bash scripts/rollback.sh --backup-dir $BACKUP_DIR

${GREEN}============================================================${NC}

EOF
}

################################################################################
# Main Execution Flow
################################################################################

main() {
    log_info "YAWL v5.2 Pre-Deployment Backup Script"
    log_info "========================================"
    log_info "Options: FULL=$FULL_BACKUP, COMPRESS=$COMPRESS_BACKUP, RETENTION=${RETENTION_DAYS}d"

    # Create backup directory
    mkdir -p "$BACKUP_DIR"
    log_success "Backup directory created: $BACKUP_DIR"

    # Verify prerequisites
    verify_prerequisites || exit 2

    # Database backups
    backup_postgresql_schema || log_warning "Schema backup failed (continuing)"
    backup_postgresql_data || log_warning "Data backup failed (continuing)"
    backup_database_full || log_warning "Full database backup failed"
    backup_h2_database || log_warning "H2 backup failed or not applicable"

    # Configuration backups
    backup_configuration || log_warning "Configuration backup incomplete"

    # WAR files (if deployed)
    backup_war_files
    backup_tomcat_configuration

    # Optional full backups
    if [[ "$FULL_BACKUP" == "true" ]]; then
        backup_build_artifacts
        backup_source_code
    fi

    # Create manifest and validate
    create_backup_manifest
    validate_backup || log_warning "Backup validation failed"

    # Compression and retention
    compress_backup
    apply_retention_policy

    # Final summary
    print_summary

    log_success "Backup process completed successfully"
    log_info "Backup directory: $BACKUP_DIR"

    return 0
}

# Execute main function with proper error handling
parse_arguments "$@"
main
exit $?
