#!/bin/bash

################################################################################
# YAWL v5.2 Rollback Script
#
# Purpose: Perform quick or extended rollback to previous version
# Usage: ./scripts/rollback.sh [--version v5.1|--backup-dir /path] [--no-restore-db]
# Exit Codes: 0 = success, 1 = partial failure, 2 = critical failure
#
# This script provides automated rollback procedures matching the
# ROLLBACK-PROCEDURES.md documentation.
################################################################################

set -euo pipefail

# Configuration
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
readonly BACKUP_BASE_DIR="${BACKUP_BASE_DIR:-/backup/yawl}"
readonly CATALINA_HOME="${CATALINA_HOME:-/opt/apache-tomcat-10.1.13}"
readonly TIMESTAMP=$(date +%Y%m%d-%H%M%S)
readonly DIAG_DIR="/var/log/yawl-rollback-diagnostics-${TIMESTAMP}"

# Command-line flags
TARGET_VERSION=""
BACKUP_DIR=""
RESTORE_DATABASE=true
GRACEFUL_SHUTDOWN=true
FORCE_KILL=false
SKIP_VALIDATION=false
VERBOSE=false

# Color codes
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m'

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
    --version VERSION       Target version to rollback to (v5.1, v5.0, etc)
                           If not specified, uses most recent backup
    --backup-dir DIR        Explicit backup directory path
    --no-restore-db         Skip database restoration (config+files only)
    --force-kill            Force kill Tomcat (default: graceful shutdown)
    --skip-validation       Skip post-rollback validation
    --verbose               Enable debug output
    -h, --help              Show this help message

EXAMPLES:
    # Quick rollback to most recent backup
    sudo $0

    # Rollback to specific version
    sudo $0 --version v5.1

    # Rollback using specific backup directory
    sudo $0 --backup-dir /backup/yawl/yawl-20260215-093022

    # Config-only rollback (no database restore)
    sudo $0 --no-restore-db

    # Force kill Tomcat for faster rollback
    sudo $0 --force-kill
EOF
    exit 0
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --version)
                TARGET_VERSION="$2"
                shift 2
                ;;
            --backup-dir)
                BACKUP_DIR="$2"
                shift 2
                ;;
            --no-restore-db)
                RESTORE_DATABASE=false
                shift
                ;;
            --force-kill)
                FORCE_KILL=true
                GRACEFUL_SHUTDOWN=false
                shift
                ;;
            --skip-validation)
                SKIP_VALIDATION=true
                shift
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
# Prerequisite Checks
################################################################################

verify_prerequisites() {
    log_info "Verifying rollback prerequisites..."

    # Check if running as root
    if [[ $EUID -ne 0 ]]; then
        log_error "This script must be run as root or with sudo"
        return 2
    fi
    log_success "Running as root"

    # Check required commands
    local required_cmds=("date" "mkdir" "cp" "rm" "pkill" "psql")
    for cmd in "${required_cmds[@]}"; do
        if ! command -v "$cmd" &> /dev/null; then
            log_error "Required command not found: $cmd"
            return 2
        fi
    done
    log_success "All required commands available"

    # Verify diagnostic directory
    mkdir -p "$DIAG_DIR"
    log_success "Diagnostic directory: $DIAG_DIR"

    return 0
}

################################################################################
# Backup Discovery
################################################################################

find_backup_directory() {
    log_info "Searching for backup directory..."

    # If explicit backup directory provided
    if [[ -n "$BACKUP_DIR" ]]; then
        if [[ -d "$BACKUP_DIR" ]] || [[ -f "${BACKUP_DIR}.tar.gz" ]]; then
            log_success "Using specified backup: $BACKUP_DIR"
            return 0
        else
            log_error "Backup not found: $BACKUP_DIR"
            return 2
        fi
    fi

    # If version specified, find matching backup
    if [[ -n "$TARGET_VERSION" ]]; then
        # Search for backup matching version in directory name or manifest
        local version_backup=""
        for backup_dir in "$BACKUP_BASE_DIR"/yawl-*; do
            if [[ -d "$backup_dir" ]]; then
                if [[ -f "$backup_dir/MANIFEST.txt" ]]; then
                    if grep -q "v${TARGET_VERSION}\|version.*${TARGET_VERSION}" "$backup_dir/MANIFEST.txt"; then
                        version_backup="$backup_dir"
                        break
                    fi
                fi
            fi
        done

        if [[ -z "$version_backup" ]]; then
            log_error "No backup found for version: $TARGET_VERSION"
            log_info "Available backups:"
            ls -d "$BACKUP_BASE_DIR"/yawl-* 2>/dev/null | head -5
            return 2
        fi

        BACKUP_DIR="$version_backup"
        log_success "Found backup for $TARGET_VERSION: $BACKUP_DIR"
        return 0
    fi

    # Find most recent backup
    local latest_backup=$(ls -d "$BACKUP_BASE_DIR"/yawl-* 2>/dev/null | sort -r | head -1)

    if [[ -z "$latest_backup" ]]; then
        log_error "No backups found in: $BACKUP_BASE_DIR"
        log_error "Run: sudo scripts/backup-before-deploy.sh"
        return 2
    fi

    BACKUP_DIR="$latest_backup"
    log_success "Using most recent backup: $(basename "$BACKUP_DIR")"
    return 0
}

################################################################################
# Pre-Rollback Diagnostics
################################################################################

capture_pre_rollback_state() {
    log_info "Capturing pre-rollback diagnostics..."

    # Capture running processes
    ps aux | grep java > "$DIAG_DIR/processes-before.log"

    # Capture error logs
    if [[ -d "$CATALINA_HOME/logs" ]]; then
        cp "$CATALINA_HOME/logs"/*.log "$DIAG_DIR/" 2>/dev/null || true
    fi

    # Capture current database state
    if command -v pg_dump &>/dev/null; then
        log_debug "Capturing current database state..."
        pg_dump -U postgres yawl > "$DIAG_DIR/database-failed-state.sql" 2>/dev/null || \
            log_warning "Failed to capture database state"
    fi

    # Capture current configuration
    if [[ -d "$PROJECT_ROOT/build/properties" ]]; then
        cp -r "$PROJECT_ROOT/build/properties" "$DIAG_DIR/properties-failed" 2>/dev/null || true
    fi

    log_success "Diagnostics captured: $DIAG_DIR"
}

################################################################################
# Service Shutdown
################################################################################

shutdown_tomcat_gracefully() {
    log_info "Initiating graceful Tomcat shutdown..."

    # Find Tomcat process
    local pid=$(pgrep -f "org.apache.catalina.startup.Bootstrap" || echo "")

    if [[ -z "$pid" ]]; then
        log_info "Tomcat not running"
        return 0
    fi

    log_debug "Tomcat PID: $pid"

    # Try graceful shutdown script first
    if [[ -x "$CATALINA_HOME/bin/shutdown.sh" ]]; then
        log_info "Executing shutdown.sh..."
        timeout 30 "$CATALINA_HOME/bin/shutdown.sh" || log_warning "shutdown.sh timeout"
    fi

    # Send SIGTERM if still running
    if ps -p "$pid" > /dev/null 2>&1; then
        log_info "Sending SIGTERM to process $pid..."
        kill -15 "$pid"

        # Wait up to 30 seconds for graceful termination
        local count=0
        while ps -p "$pid" > /dev/null 2>&1 && [[ $count -lt 30 ]]; do
            sleep 1
            ((count++))
        done

        if [[ $count -lt 30 ]]; then
            log_success "Tomcat shut down gracefully after ${count}s"
            return 0
        fi
    else
        log_success "Tomcat terminated"
        return 0
    fi

    # If still running and force kill enabled
    if ps -p "$pid" > /dev/null 2>&1 && [[ "$FORCE_KILL" == "true" ]]; then
        log_warning "Forcing shutdown with SIGKILL..."
        kill -9 "$pid"
        sleep 2
        log_success "Tomcat force killed"
        return 0
    fi

    # If still running and no force kill
    if ps -p "$pid" > /dev/null 2>&1; then
        log_error "Tomcat failed to shutdown after 30 seconds"
        log_error "Use: --force-kill to force termination"
        return 1
    fi

    return 0
}

################################################################################
# Service Shutdown (all)
################################################################################

shutdown_all_services() {
    log_info "Shutting down all YAWL services..."

    local services=("yawl-app" "yawl-web" "yawl-stateless")

    for service in "${services[@]}"; do
        if systemctl is-active --quiet "$service" 2>/dev/null; then
            log_info "Stopping $service..."
            systemctl stop "$service" || log_warning "Failed to stop $service"
        fi
    done

    # Also shutdown Tomcat directly
    shutdown_tomcat_gracefully || log_warning "Tomcat shutdown had issues"

    sleep 2
    log_success "All services stopped"
    return 0
}

################################################################################
# Configuration Restoration
################################################################################

restore_configuration() {
    log_info "Restoring configuration files..."

    if [[ ! -d "$BACKUP_DIR/properties" ]]; then
        log_warning "No configuration backup found"
        return 1
    fi

    local config_target="$PROJECT_ROOT/build/properties"
    mkdir -p "$config_target"

    # Restore each configuration file
    for config_file in "$BACKUP_DIR/properties"/*; do
        if [[ -f "$config_file" ]]; then
            local filename=$(basename "$config_file")
            log_debug "Restoring: $filename"
            cp "$config_file" "$config_target/$filename"
        fi
    done

    # Verify critical properties
    if [[ -f "$config_target/hibernate.properties" ]]; then
        if grep -q "hibernate.dialect\|jdbc.url" "$config_target/hibernate.properties"; then
            log_success "Hibernate configuration restored"
        else
            log_warning "Hibernate configuration may be incomplete"
        fi
    fi

    return 0
}

################################################################################
# WAR File Restoration
################################################################################

restore_war_files() {
    log_info "Restoring WAR files..."

    if [[ ! -d "$BACKUP_DIR/war-files" ]]; then
        log_debug "No WAR backup found (may use source deployment)"
        return 0
    fi

    local webapps_dir="$CATALINA_HOME/webapps"
    mkdir -p "$webapps_dir"

    # Remove current deployment
    log_info "Removing current deployment..."
    rm -rf "$webapps_dir/yawl"* 2>/dev/null || true

    # Restore WAR files
    local war_count=0
    for war_file in "$BACKUP_DIR/war-files"/*.war; do
        if [[ -f "$war_file" ]]; then
            log_debug "Restoring: $(basename "$war_file")"
            cp "$war_file" "$webapps_dir/"
            ((war_count++))
        fi
    done

    if [[ $war_count -gt 0 ]]; then
        log_success "Restored $war_count WAR files"
    else
        log_warning "No WAR files found in backup"
    fi

    return 0
}

################################################################################
# Tomcat Configuration Restoration
################################################################################

restore_tomcat_configuration() {
    log_info "Restoring Tomcat configuration..."

    if [[ ! -d "$BACKUP_DIR/tomcat-conf" ]]; then
        log_debug "No Tomcat configuration backup found"
        return 0
    fi

    if [[ ! -d "$CATALINA_HOME/conf" ]]; then
        log_warning "Tomcat conf directory not found"
        return 1
    fi

    # Backup current configuration first
    log_debug "Backing up current Tomcat config..."
    cp -r "$CATALINA_HOME/conf" "$DIAG_DIR/tomcat-conf-failed"

    # Restore from backup
    log_info "Restoring Tomcat configuration..."
    cp -r "$BACKUP_DIR/tomcat-conf"/* "$CATALINA_HOME/conf/"

    log_success "Tomcat configuration restored"
    return 0
}

################################################################################
# Database Restoration
################################################################################

restore_database_postgresql() {
    log_info "Restoring PostgreSQL database..."

    if [[ "$RESTORE_DATABASE" != "true" ]]; then
        log_debug "Database restoration disabled"
        return 0
    fi

    # Determine which backup file to use (full > schema+data > schema)
    local db_backup=""
    if [[ -f "$BACKUP_DIR/database-full.sql" ]]; then
        db_backup="$BACKUP_DIR/database-full.sql"
    elif [[ -f "$BACKUP_DIR/database-schema.sql" ]]; then
        db_backup="$BACKUP_DIR/database-schema.sql"
        if [[ -f "$BACKUP_DIR/database-data.sql" ]]; then
            log_info "Restoring schema and data separately..."
        fi
    else
        log_error "No database backup found"
        return 2
    fi

    log_info "Using database backup: $(basename "$db_backup")"

    # Connect to database and restore
    log_info "Restoring database..."
    if ! psql -U postgres yawl < "$db_backup" > "$DIAG_DIR/restore.log" 2>&1; then
        log_error "Database restore failed"
        log_error "Check: $DIAG_DIR/restore.log"
        return 1
    fi

    # If separate data file exists, restore it too
    if [[ -f "$BACKUP_DIR/database-data.sql" ]] && [[ "$db_backup" != "$BACKUP_DIR/database-full.sql" ]]; then
        log_info "Restoring data..."
        psql -U postgres yawl < "$BACKUP_DIR/database-data.sql" >> "$DIAG_DIR/restore.log" 2>&1 || \
            log_warning "Data restoration had issues"
    fi

    # Verify restoration
    local row_count=$(psql -U postgres yawl -t -c "SELECT COUNT(*) FROM workflow_case;" 2>/dev/null || echo "0")
    log_success "Database restored (workflow cases: $row_count)"

    return 0
}

################################################################################
# H2 Database Restoration
################################################################################

restore_h2_database() {
    log_info "Checking for H2 database in backup..."

    if [[ ! -f "$BACKUP_DIR/yawl.h2.db" ]]; then
        log_debug "No H2 database in backup"
        return 0
    fi

    log_info "Restoring H2 database..."

    local h2_target=""
    if [[ -d "$PROJECT_ROOT/data" ]]; then
        h2_target="$PROJECT_ROOT/data"
    elif [[ -d "$CATALINA_HOME/webapps/yawl/WEB-INF/data" ]]; then
        h2_target="$CATALINA_HOME/webapps/yawl/WEB-INF/data"
    else
        mkdir -p "$PROJECT_ROOT/data"
        h2_target="$PROJECT_ROOT/data"
    fi

    cp "$BACKUP_DIR/yawl.h2.db" "$h2_target/"
    log_success "H2 database restored to: $h2_target"

    return 0
}

################################################################################
# Build Artifacts Restoration
################################################################################

restore_build_artifacts() {
    log_info "Restoring build artifacts..."

    if [[ ! -d "$BACKUP_DIR/build-artifacts" ]]; then
        log_debug "No build artifacts in backup"
        return 0
    fi

    if [[ -d "$BACKUP_DIR/build-artifacts/classes" ]]; then
        log_debug "Restoring compiled classes..."
        rm -rf "$PROJECT_ROOT/build/classes"
        cp -r "$BACKUP_DIR/build-artifacts/classes" "$PROJECT_ROOT/build/"
    fi

    if [[ -d "$BACKUP_DIR/build-artifacts/lib" ]]; then
        log_debug "Restoring WAR libraries..."
        rm -rf "$PROJECT_ROOT/build/lib"
        cp -r "$BACKUP_DIR/build-artifacts/lib" "$PROJECT_ROOT/build/"
    fi

    log_success "Build artifacts restored"
    return 0
}

################################################################################
# Service Startup
################################################################################

start_tomcat() {
    log_info "Starting Tomcat..."

    if [[ ! -x "$CATALINA_HOME/bin/startup.sh" ]]; then
        log_error "Tomcat startup script not found: $CATALINA_HOME/bin/startup.sh"
        return 2
    fi

    # Clear any leftover processes
    pkill -9 java || true
    sleep 2

    # Start Tomcat
    "$CATALINA_HOME/bin/startup.sh"
    log_info "Tomcat startup script executed"

    # Wait for startup
    log_info "Waiting for Tomcat to start (up to 60 seconds)..."
    local startup_timeout=60
    local elapsed=0

    while [[ $elapsed -lt $startup_timeout ]]; do
        if curl -s http://localhost:8080/yawl/ib > /dev/null 2>&1; then
            log_success "Tomcat started successfully"
            return 0
        fi

        sleep 2
        ((elapsed += 2))
        echo -ne "\rStartup progress: ${elapsed}s..."
    done

    log_error "Tomcat failed to start within 60 seconds"
    return 1
}

start_services() {
    log_info "Starting YAWL services..."

    local services=("yawl-app" "yawl-web")

    for service in "${services[@]}"; do
        if systemctl is-enabled "$service" 2>/dev/null; then
            log_info "Starting $service..."
            systemctl start "$service" || log_warning "Failed to start $service"
        fi
    done

    sleep 5
    log_success "Services started"
}

################################################################################
# Post-Rollback Validation
################################################################################

validate_rollback() {
    if [[ "$SKIP_VALIDATION" == "true" ]]; then
        log_debug "Validation skipped (--skip-validation)"
        return 0
    fi

    log_info "Validating rollback..."
    local validation_failed=0

    # Test REST API endpoint
    log_debug "Testing REST API..."
    if curl -s http://localhost:8080/yawl/ib > /dev/null 2>&1; then
        log_success "REST API responsive"
    else
        log_warning "REST API not responding"
        validation_failed=1
    fi

    # Test database endpoint
    log_debug "Testing database connectivity..."
    if curl -s http://localhost:8080/yawl/api/cases > /dev/null 2>&1; then
        log_success "Database connectivity OK"
    else
        log_warning "Database connectivity failed"
        validation_failed=1
    fi

    # Check logs for errors
    log_debug "Checking application logs..."
    if [[ -f "$CATALINA_HOME/logs/catalina.out" ]]; then
        if tail -50 "$CATALINA_HOME/logs/catalina.out" | grep -i "error\|exception" > /dev/null; then
            log_warning "Application errors detected in logs"
            validation_failed=1
        else
            log_success "Application logs clean"
        fi
    fi

    return $validation_failed
}

################################################################################
# Rollback Summary
################################################################################

print_rollback_summary() {
    local status=$1

    cat << EOF

${GREEN}============================================================${NC}
${GREEN}ROLLBACK SUMMARY${NC}
${GREEN}============================================================${NC}

Rollback Timestamp: $TIMESTAMP
Backup Used: $(basename "$BACKUP_DIR")
Diagnostics: $DIAG_DIR

EOF

    if [[ $status -eq 0 ]]; then
        cat << EOF
${GREEN}Status: ROLLBACK SUCCESSFUL${NC}

Application is now running the previous version.

Next Steps:
1. Run smoke tests: bash scripts/smoke-test.sh
2. Verify critical workflows are functional
3. Monitor application logs for 15 minutes
4. Notify stakeholders of incident resolution

To investigate the failed deployment:
- Review diagnostics: $DIAG_DIR
- Analyze backup: $BACKUP_DIR
- File incident report with RCA

Rollback Retention:
- Failed deployment logs: $DIAG_DIR
- Previous backup will be deleted in 30 days
  (configure with: backup-before-deploy.sh --retention-days N)

EOF
    else
        cat << EOF
${YELLOW}Status: ROLLBACK PARTIAL/FAILED${NC}

Some steps may not have completed successfully.

Troubleshooting:
1. Check application logs: tail -f $CATALINA_HOME/logs/catalina.out
2. Review diagnostics: ls -la $DIAG_DIR
3. Manual rollback steps in: docs/ROLLBACK-PROCEDURES.md

Critical Issues:
- Ensure database is accessible
- Verify Tomcat startup script has execute permissions
- Check disk space for restore operations

Emergency Contact:
- Infrastructure Team: devops@yawl-team.org

EOF
    fi

    cat << EOF
${GREEN}============================================================${NC}

EOF
}

################################################################################
# Main Rollback Execution
################################################################################

main() {
    log_info "YAWL v5.2 Rollback Script"
    log_info "=========================="

    # Verify prerequisites
    verify_prerequisites || exit 2

    # Find backup
    find_backup_directory || exit 2

    # Confirm backup exists and is valid
    if [[ ! -d "$BACKUP_DIR" ]] && [[ ! -f "${BACKUP_DIR}.tar.gz" ]]; then
        log_error "Backup directory not accessible: $BACKUP_DIR"
        exit 2
    fi

    # Extract if compressed
    if [[ -f "${BACKUP_DIR}.tar.gz" ]] && [[ ! -d "$BACKUP_DIR" ]]; then
        log_info "Extracting compressed backup..."
        tar -xzf "${BACKUP_DIR}.tar.gz" -C "$BACKUP_BASE_DIR"
    fi

    log_success "Backup confirmed: $(du -sh "$BACKUP_DIR" | cut -f1)"

    # Capture pre-rollback state
    capture_pre_rollback_state

    # Shutdown services
    shutdown_all_services || log_warning "Service shutdown incomplete"

    # Restore configuration
    restore_configuration || log_warning "Configuration restore incomplete"

    # Restore Tomcat configuration
    restore_tomcat_configuration

    # Restore WAR files if available
    restore_war_files

    # Restore database
    restore_database_postgresql || restore_h2_database || log_warning "Database restore incomplete"

    # Restore build artifacts if available
    restore_build_artifacts

    # Start services
    start_tomcat || {
        log_error "Tomcat startup failed"
        exit 1
    }

    start_services

    # Wait for stability
    log_info "Allowing services to stabilize (15 seconds)..."
    sleep 15

    # Validate
    validate_rollback
    local validation_status=$?

    # Print summary
    print_rollback_summary $validation_status

    # Exit with appropriate code
    if [[ $validation_status -eq 0 ]]; then
        log_success "Rollback completed successfully"
        exit 0
    else
        log_warning "Rollback completed with warnings"
        exit 1
    fi
}

# Execute rollback with error handling
parse_arguments "$@"
main
exit $?
