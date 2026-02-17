#!/bin/bash
# =============================================================================
# YAWL Engine Docker Entrypoint Script
# =============================================================================
# Production-ready container startup script for YAWL workflow engine.
# Handles: environment validation, database connectivity, initialization,
# signal handling, JVM configuration, and graceful shutdown.
#
# Usage:
#   docker run yawl/engine                    # Production mode
#   docker run -e YAWL_MODE=dev yawl/engine   # Development mode
# =============================================================================

set -euo pipefail

# =============================================================================
# Constants and Configuration
# =============================================================================

readonly SCRIPT_NAME="yawl-entrypoint"
readonly SCRIPT_VERSION="1.0.0"
readonly APP_USER="yawl"
readonly APP_GROUP="yawl"
readonly APP_HOME="/app"
readonly LOG_DIR="${APP_HOME}/logs"
readonly DATA_DIR="${APP_HOME}/data"
readonly CONFIG_DIR="${APP_HOME}/config"
readonly TEMP_DIR="${APP_HOME}/temp"
readonly JAR_FILE="${APP_HOME}/yawl.jar"
readonly PID_FILE="${APP_HOME}/yawl.pid"

# Database wait configuration
readonly DB_MAX_RETRIES="${DB_MAX_RETRIES:-30}"
readonly DB_RETRY_INTERVAL="${DB_RETRY_INTERVAL:-2}"
readonly DB_CONNECT_TIMEOUT="${DB_CONNECT_TIMEOUT:-5}"

# Health check configuration
readonly HEALTH_PORT="${HEALTH_PORT:-8080}"
readonly HEALTH_STARTUP_BUFFER="${HEALTH_STARTUP_BUFFER:-10}"

# =============================================================================
# Logging Functions
# =============================================================================

log() {
    local level="$1"
    shift
    local message="$*"
    local timestamp
    timestamp=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
    echo "[$timestamp] [$level] [$SCRIPT_NAME] $message"
}

log_info()  { log "INFO" "$@"; }
log_warn()  { log "WARN" "$@"; }
log_error() { log "ERROR" "$@" >&2; }
log_debug() {
    if [[ "${YAWL_DEBUG:-false}" == "true" ]]; then
        log "DEBUG" "$@"
    fi
}

# =============================================================================
# Environment Variable Configuration
# =============================================================================

configure_environment() {
    log_info "Configuring environment variables..."

    # Operation mode: production (default) or development
    export YAWL_MODE="${YAWL_MODE:-production}"
    log_info "Running in ${YAWL_MODE} mode"

    # Database configuration with defaults
    export DB_TYPE="${DB_TYPE:-postgres}"
    export DB_HOST="${DB_HOST:-localhost}"
    export DB_PORT="${DB_PORT:-5432}"
    export DB_NAME="${DB_NAME:-yawl}"
    export DB_USER="${DB_USER:-yawl}"
    export DB_SCHEMA="${DB_SCHEMA:-public}"
    export DB_SSL_MODE="${DB_SSL_MODE:-prefer}"

    # Handle database password securely
    if [[ -z "${DB_PASSWORD:-}" ]] && [[ -z "${DB_PASSWORD_FILE:-}" ]]; then
        if [[ "$YAWL_MODE" == "production" ]]; then
            log_error "DB_PASSWORD or DB_PASSWORD_FILE must be set in production mode"
            exit 1
        else
            log_warn "DB_PASSWORD not set - using empty password for development"
            export DB_PASSWORD=""
        fi
    elif [[ -n "${DB_PASSWORD_FILE:-}" ]] && [[ -f "$DB_PASSWORD_FILE" ]]; then
        local db_pass
        db_pass=$(cat "$DB_PASSWORD_FILE")
        export DB_PASSWORD="$db_pass"
        log_info "Loaded database password from file: $DB_PASSWORD_FILE"
    fi

    # YAWL engine credentials
    export YAWL_USERNAME="${YAWL_USERNAME:-admin}"
    if [[ -z "${YAWL_PASSWORD:-}" ]] && [[ -z "${YAWL_PASSWORD_FILE:-}" ]]; then
        if [[ "$YAWL_MODE" == "production" ]]; then
            log_error "YAWL_PASSWORD or YAWL_PASSWORD_FILE must be set in production mode"
            exit 1
        else
            log_warn "YAWL_PASSWORD not set - using default for development"
            export YAWL_PASSWORD="YAWL"
        fi
    elif [[ -n "${YAWL_PASSWORD_FILE:-}" ]] && [[ -f "$YAWL_PASSWORD_FILE" ]]; then
        local yawl_pass
        yawl_pass=$(cat "$YAWL_PASSWORD_FILE")
        export YAWL_PASSWORD="$yawl_pass"
        log_info "Loaded YAWL password from file: $YAWL_PASSWORD_FILE"
    fi

    # YAWL engine URL
    export YAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080/yawl}"

    # Feature flags
    export ENABLE_PERSISTENCE="${ENABLE_PERSISTENCE:-true}"
    export ENABLE_LOGGING="${ENABLE_LOGGING:-true}"
    export ENABLE_RESOURCE_SERVICE="${ENABLE_RESOURCE_SERVICE:-true}"
    export ENABLE_WORKLET_SERVICE="${ENABLE_WORKLET_SERVICE:-true}"
    export ENABLE_MCP_SERVER="${ENABLE_MCP_SERVER:-false}"

    # Logging configuration
    export LOG_LEVEL="${LOG_LEVEL:-INFO}"
    export LOGGING_LEVEL_ROOT="${LOGGING_LEVEL_ROOT:-${LOG_LEVEL}}"
    export LOGGING_LEVEL_ORG_YAWLFOUNDATION="${LOGGING_LEVEL_ORG_YAWLFOUNDATION:-DEBUG}"

    # Timezone configuration
    export TZ="${TZ:-UTC}"
    if [[ -f "/usr/share/zoneinfo/$TZ" ]]; then
        log_info "Setting timezone to: $TZ"
    else
        log_warn "Timezone $TZ not found, using UTC"
        export TZ="UTC"
    fi

    # Resource service URL
    export RESOURCE_SERVICE_URL="${RESOURCE_SERVICE_URL:-http://resource-service:8080/ib}"

    # MCP server configuration
    if [[ "$ENABLE_MCP_SERVER" == "true" ]]; then
        export MCP_TRANSPORT="${MCP_TRANSPORT:-stdio}"
        export MCP_HTTP_PORT="${MCP_HTTP_PORT:-8081}"
        log_info "MCP server enabled with transport: $MCP_TRANSPORT"
    fi

    # Observability configuration
    export OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME:-yawl-engine}"
    export OTEL_TRACES_EXPORTER="${OTEL_TRACES_EXPORTER:-otlp}"
    export OTEL_METRICS_EXPORTER="${OTEL_METRICS_EXPORTER:-prometheus}"
    export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-}"

    # Spring profiles
    if [[ "$YAWL_MODE" == "development" ]]; then
        export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-development}"
    else
        export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-production}"
    fi

    # Spring Boot actuator configuration
    export MANAGEMENT_HEALTH_PROBES_ENABLED="${MANAGEMENT_HEALTH_PROBES_ENABLED:-true}"
    export MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS="${MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS:-when-authorized}"
    export MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE="${MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE:-health,info,metrics,prometheus}"

    log_info "Environment configuration complete"
}

# =============================================================================
# JVM Configuration for Containers
# =============================================================================

configure_jvm() {
    log_info "Configuring JVM options for container environment..."

    local jvm_opts=""

    # Container-aware memory settings (Java 25 native support)
    jvm_opts+="-XX:+UseContainerSupport "

    # Memory allocation percentages (respects container limits)
    local max_ram_percent="${JVM_MAX_RAM_PERCENTAGE:-75.0}"
    local init_ram_percent="${JVM_INIT_RAM_PERCENTAGE:-50.0}"
    jvm_opts+="-XX:MaxRAMPercentage=${max_ram_percent} "
    jvm_opts+="-XX:InitialRAMPercentage=${init_ram_percent} "

    # Garbage collector selection (ZGC for low latency, G1GC as fallback)
    local gc_type="${JVM_GC_TYPE:-ZGC}"
    case "$gc_type" in
        ZGC|zgc)
            jvm_opts+="-XX:+UseZGC "
            jvm_opts+="-XX:+ZGenerational "
            ;;
        G1GC|g1gc)
            jvm_opts+="-XX:+UseG1GC "
            ;;
        ParallelGC|parallel)
            jvm_opts+="-XX:+UseParallelGC "
            ;;
        *)
            log_warn "Unknown GC type: $gc_type, using ZGC"
            jvm_opts+="-XX:+UseZGC "
            jvm_opts+="-XX:+ZGenerational "
            ;;
    esac

    # String deduplication for memory efficiency
    jvm_opts+="-XX:+UseStringDeduplication "

    # Out of memory handling
    jvm_opts+="-XX:+ExitOnOutOfMemoryError "
    jvm_opts+="-XX:+HeapDumpOnOutOfMemoryError "
    jvm_opts+="-XX:HeapDumpPath=${LOG_DIR}/heap-dump.hprof "

    # Secure random for faster startup
    jvm_opts+="-Djava.security.egd=file:/dev/./urandom "

    # Temp directory
    jvm_opts+="-Djava.io.tmpdir=${TEMP_DIR} "

    # File encoding
    jvm_opts+="-Dfile.encoding=UTF-8 "

    # Headless mode for server operation
    jvm_opts+="-Djava.awt.headless=true "

    # Virtual thread configuration (Java 21+ standard, optimized in Java 25)
    local vt_parallelism="${JVM_VT_PARALLELISM:-200}"
    local vt_max_pool="${JVM_VT_MAX_POOL:-256}"
    jvm_opts+="-Djdk.virtualThreadScheduler.parallelism=${vt_parallelism} "
    jvm_opts+="-Djdk.virtualThreadScheduler.maxPoolSize=${vt_max_pool} "

    # Virtual thread pinning diagnostics (development mode)
    if [[ "$YAWL_MODE" == "development" ]]; then
        jvm_opts+="-Djdk.tracePinnedThreads=short "
    fi

    # JIT compiler optimization
    jvm_opts+="-XX:+TieredCompilation "
    jvm_opts+="-XX:TieredStopAtLevel=4 "

    # Additional custom JVM options
    if [[ -n "${JAVA_OPTS_CUSTOM:-}" ]]; then
        jvm_opts+="${JAVA_OPTS_CUSTOM} "
    fi

    export JAVA_OPTS="${jvm_opts% }"
    log_info "JVM options configured: ${JAVA_OPTS}"

    # Log memory configuration
    if command -v free &>/dev/null; then
        log_debug "Container memory info: $(free -h | grep Mem)"
    fi
}

# =============================================================================
# Directory Setup
# =============================================================================

setup_directories() {
    log_info "Setting up application directories..."

    local dirs=(
        "$LOG_DIR"
        "$DATA_DIR"
        "$CONFIG_DIR"
        "$TEMP_DIR"
        "${APP_HOME}/specifications"
    )

    for dir in "${dirs[@]}"; do
        if [[ ! -d "$dir" ]]; then
            mkdir -p "$dir"
            log_info "Created directory: $dir"
        fi
    done

    # Set permissions if running as root (will drop privileges later)
    if [[ "$(id -u)" == "0" ]]; then
        chown -R "${APP_USER}:${APP_GROUP}" "${dirs[@]}"
        log_info "Set ownership of directories to ${APP_USER}:${APP_GROUP}"
    fi
}

# =============================================================================
# Database Connectivity
# =============================================================================

wait_for_database() {
    log_info "Checking database connectivity..."

    local db_host="$DB_HOST"
    local db_port="$DB_PORT"
    local db_type="$DB_TYPE"

    # Skip database check if persistence is disabled
    if [[ "$ENABLE_PERSISTENCE" != "true" ]]; then
        log_info "Persistence disabled, skipping database connectivity check"
        return 0
    fi

    # For embedded H2, skip connectivity check
    if [[ "$db_type" == "h2" ]] || [[ "$db_host" == "localhost" && "$db_type" == "h2" ]]; then
        log_info "Using embedded H2 database, skipping connectivity check"
        return 0
    fi

    local retry_count=0
    local connected=false

    log_info "Waiting for $db_type database at $db_host:$db_port..."

    while [[ $retry_count -lt $DB_MAX_RETRIES ]]; do
        case "$db_type" in
            postgres|postgresql)
                if command -v pg_isready &>/dev/null; then
                    if pg_isready -h "$db_host" -p "$db_port" -U "$DB_USER" -t "$DB_CONNECT_TIMEOUT" >/dev/null 2>&1; then
                        connected=true
                        break
                    fi
                elif command -v nc &>/dev/null; then
                    if nc -z -w "$DB_CONNECT_TIMEOUT" "$db_host" "$db_port" 2>/dev/null; then
                        connected=true
                        break
                    fi
                else
                    log_warn "Neither pg_isready nor nc available, attempting TCP connection via bash"
                    if (echo >/dev/tcp/"$db_host"/"$db_port") 2>/dev/null; then
                        connected=true
                        break
                    fi
                fi
                ;;
            mysql|mariadb)
                if command -v mysqladmin &>/dev/null; then
                    if mysqladmin ping -h "$db_host" -P "$db_port" -u "$DB_USER" --password="$DB_PASSWORD" --connect-timeout="$DB_CONNECT_TIMEOUT" >/dev/null 2>&1; then
                        connected=true
                        break
                    fi
                elif command -v nc &>/dev/null; then
                    if nc -z -w "$DB_CONNECT_TIMEOUT" "$db_host" "$db_port" 2>/dev/null; then
                        connected=true
                        break
                    fi
                else
                    if (echo >/dev/tcp/"$db_host"/"$db_port") 2>/dev/null; then
                        connected=true
                        break
                    fi
                fi
                ;;
            oracle)
                if command -v nc &>/dev/null; then
                    if nc -z -w "$DB_CONNECT_TIMEOUT" "$db_host" "$db_port" 2>/dev/null; then
                        connected=true
                        break
                    fi
                else
                    if (echo >/dev/tcp/"$db_host"/"$db_port") 2>/dev/null; then
                        connected=true
                        break
                    fi
                fi
                ;;
            sqlserver)
                if command -v nc &>/dev/null; then
                    if nc -z -w "$DB_CONNECT_TIMEOUT" "$db_host" "$db_port" 2>/dev/null; then
                        connected=true
                        break
                    fi
                else
                    if (echo >/dev/tcp/"$db_host"/"$db_port") 2>/dev/null; then
                        connected=true
                        break
                    fi
                fi
                ;;
            *)
                log_warn "Unknown database type: $db_type, attempting generic TCP check"
                if command -v nc &>/dev/null; then
                    if nc -z -w "$DB_CONNECT_TIMEOUT" "$db_host" "$db_port" 2>/dev/null; then
                        connected=true
                        break
                    fi
                else
                    if (echo >/dev/tcp/"$db_host"/"$db_port") 2>/dev/null; then
                        connected=true
                        break
                    fi
                fi
                ;;
        esac

        retry_count=$((retry_count + 1))
        log_debug "Database connection attempt $retry_count/$DB_MAX_RETRIES failed, retrying in ${DB_RETRY_INTERVAL}s..."
        sleep "$DB_RETRY_INTERVAL"
    done

    if [[ "$connected" == "true" ]]; then
        log_info "Database connection established: $db_type at $db_host:$db_port"
        return 0
    else
        log_error "Failed to connect to database after $DB_MAX_RETRIES attempts"
        if [[ "$YAWL_MODE" == "production" ]]; then
            log_error "Database connectivity required in production mode"
            return 1
        else
            log_warn "Continuing without database in development mode"
            return 0
        fi
    fi
}

# =============================================================================
# Database Initialization
# =============================================================================

initialize_database() {
    log_info "Checking database initialization..."

    # Skip if persistence is disabled
    if [[ "$ENABLE_PERSISTENCE" != "true" ]]; then
        log_info "Persistence disabled, skipping database initialization"
        return 0
    fi

    # Check for H2 embedded database - needs file-based initialization
    if [[ "$DB_TYPE" == "h2" ]]; then
        log_info "Using H2 embedded database - schema will be auto-created"
        return 0
    fi

    # For external databases, check if initialization script exists
    local init_script="${CONFIG_DIR}/init-db.sql"
    if [[ -f "$init_script" ]]; then
        log_info "Database initialization script found: $init_script"
        # The application's Hibernate auto-ddl or Flyway/Liquibase handles schema
        # This script can contain seed data if needed
    fi

    # Check if we should run schema migrations
    if [[ "${RUN_SCHEMA_MIGRATION:-false}" == "true" ]]; then
        log_info "Schema migration requested"
        # Application handles this via Flyway/Liquibase if configured
    fi

    log_info "Database initialization check complete"
}

# =============================================================================
# Signal Handling for Graceful Shutdown
# =============================================================================

# Global variable to track the Java process PID
YAWL_PID=""

cleanup() {
    local signal="$1"
    log_info "Received $signal signal, initiating graceful shutdown..."

    if [[ -n "$YAWL_PID" ]] && kill -0 "$YAWL_PID" 2>/dev/null; then
        log_info "Sending SIGTERM to YAWL process (PID: $YAWL_PID)"

        # Send SIGTERM for graceful shutdown
        kill -TERM "$YAWL_PID" 2>/dev/null

        # Wait for graceful shutdown with timeout
        local shutdown_timeout="${SHUTDOWN_TIMEOUT:-30}"
        local elapsed=0

        while kill -0 "$YAWL_PID" 2>/dev/null && [[ $elapsed -lt $shutdown_timeout ]]; do
            sleep 1
            elapsed=$((elapsed + 1))
            log_debug "Waiting for YAWL to shutdown... (${elapsed}s/${shutdown_timeout}s)"
        done

        # Force kill if still running
        if kill -0 "$YAWL_PID" 2>/dev/null; then
            log_warn "YAWL did not shutdown gracefully within ${shutdown_timeout}s, forcing termination"
            kill -KILL "$YAWL_PID" 2>/dev/null
        fi

        log_info "YAWL process terminated"
    fi

    # Cleanup PID file
    if [[ -f "$PID_FILE" ]]; then
        rm -f "$PID_FILE"
    fi

    log_info "Shutdown complete"
    exit 0
}

setup_signal_handlers() {
    log_info "Setting up signal handlers for graceful shutdown..."

    # Trap SIGTERM (docker stop), SIGINT (ctrl+c), and SIGQUIT
    trap 'cleanup SIGTERM' TERM
    trap 'cleanup SIGINT' INT
    trap 'cleanup SIGQUIT' QUIT

    log_info "Signal handlers configured"
}

# =============================================================================
# Non-Root User Handling
# =============================================================================

drop_privileges() {
    # If running as root, drop to non-root user
    if [[ "$(id -u)" == "0" ]]; then
        log_info "Running as root, dropping privileges to ${APP_USER}..."

        # Ensure all files are owned by the app user
        chown -R "${APP_USER}:${APP_GROUP}" "$APP_HOME"

        # Use gosu or su-exec to drop privileges if available
        if command -v gosu &>/dev/null; then
            exec gosu "$APP_USER" "$@"
        elif command -v su-exec &>/dev/null; then
            exec su-exec "$APP_USER" "$@"
        else
            # Fallback to su
            exec su -s /bin/bash "$APP_USER" -c "$*"
        fi
    fi
}

# =============================================================================
# Health Check Helper
# =============================================================================

wait_for_health() {
    local startup_timeout="${STARTUP_TIMEOUT:-120}"
    local elapsed=0
    local interval="${HEALTH_CHECK_INTERVAL:-5}"

    log_info "Waiting for application to become healthy (timeout: ${startup_timeout}s)..."

    # Give the application some time to start
    sleep "$HEALTH_STARTUP_BUFFER"

    while [[ $elapsed -lt $startup_timeout ]]; do
        if curl -sf "http://localhost:${HEALTH_PORT}/actuator/health/liveness" >/dev/null 2>&1; then
            log_info "Application is healthy (liveness check passed)"
            return 0
        elif curl -sf "http://localhost:${HEALTH_PORT}/actuator/health" >/dev/null 2>&1; then
            log_info "Application is healthy (health endpoint responding)"
            return 0
        elif nc -z localhost "$HEALTH_PORT" 2>/dev/null; then
            log_info "Application is healthy (port ${HEALTH_PORT} is listening)"
            return 0
        fi

        elapsed=$((elapsed + interval))
        sleep "$interval"
        log_debug "Health check attempt, elapsed: ${elapsed}s/${startup_timeout}s"
    done

    log_warn "Application health check timed out after ${startup_timeout}s"
    return 1
}

# =============================================================================
# Application Startup
# =============================================================================

start_application() {
    log_info "Starting YAWL application..."

    # Verify JAR file exists
    if [[ ! -f "$JAR_FILE" ]]; then
        log_error "Application JAR not found: $JAR_FILE"
        exit 1
    fi

    # Build the Java command
    local java_cmd="java ${JAVA_OPTS}"

    # Add Spring Boot-specific options
    java_cmd+=" -Dserver.port=${HEALTH_PORT}"
    java_cmd+=" -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}"

    # Add database URL as Spring property if using JDBC
    if [[ -n "${DB_HOST:-}" ]] && [[ "$DB_TYPE" != "h2" ]]; then
        local db_url=""
        case "$DB_TYPE" in
            postgres|postgresql)
                db_url="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
                ;;
            mysql|mariadb)
                db_url="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
                ;;
            oracle)
                db_url="jdbc:oracle:thin:@${DB_HOST}:${DB_PORT}:${DB_NAME}"
                ;;
            sqlserver)
                db_url="jdbc:sqlserver://${DB_HOST}:${DB_PORT};databaseName=${DB_NAME}"
                ;;
        esac
        if [[ -n "$db_url" ]]; then
            java_cmd+=" -Dspring.datasource.url=${db_url}"
            java_cmd+=" -Dspring.datasource.username=${DB_USER}"
            java_cmd+=" -Dspring.datasource.driver-class-name=$(get_driver_class)"
        fi
    fi

    java_cmd+=" -jar ${JAR_FILE}"

    log_info "Java command: ${java_cmd}"

    # Start the application in background to handle signals
    $java_cmd &
    YAWL_PID=$!
    echo "$YAWL_PID" > "$PID_FILE"

    log_info "YAWL started with PID: $YAWL_PID"

    # Wait for the application to exit
    wait "$YAWL_PID"
    local exit_code=$?

    log_info "YAWL exited with code: $exit_code"
    return $exit_code
}

get_driver_class() {
    case "$DB_TYPE" in
        postgres|postgresql)
            echo "org.postgresql.Driver"
            ;;
        mysql|mariadb)
            echo "com.mysql.cj.jdbc.Driver"
            ;;
        oracle)
            echo "oracle.jdbc.OracleDriver"
            ;;
        sqlserver)
            echo "com.microsoft.sqlserver.jdbc.SQLServerDriver"
            ;;
        h2)
            echo "org.h2.Driver"
            ;;
        *)
            echo "org.postgresql.Driver"
            ;;
    esac
}

# =============================================================================
# Development Mode Helpers
# =============================================================================

configure_development_mode() {
    if [[ "$YAWL_MODE" == "development" ]]; then
        log_info "Configuring development mode settings..."

        # Enable debug logging
        export LOGGING_LEVEL_ROOT="DEBUG"
        export LOGGING_LEVEL_ORG_YAWLFOUNDATION="TRACE"

        # Enable Spring DevTools if available
        export SPRING_DEVTOOLS_RESTART_ENABLED="true"

        # Disable some production hardening
        export MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS="always"

        # Enable remote debugging if requested
        if [[ "${REMOTE_DEBUG:-false}" == "true" ]]; then
            local debug_port="${DEBUG_PORT:-5005}"
            export JAVA_OPTS="${JAVA_OPTS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${debug_port}"
            log_info "Remote debugging enabled on port ${debug_port}"
        fi

        log_info "Development mode configured"
    fi
}

# =============================================================================
# Pre-flight Checks
# =============================================================================

preflight_checks() {
    log_info "Running pre-flight checks..."

    # Check Java version
    if ! command -v java &>/dev/null; then
        log_error "Java runtime not found"
        exit 1
    fi

    local java_version
    java_version=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    log_info "Java version: $java_version"

    if [[ "$java_version" -lt 21 ]]; then
        log_error "Java 21 or higher is required (found Java $java_version)"
        exit 1
    fi

    # Check required directories
    if [[ ! -d "$APP_HOME" ]]; then
        log_error "Application home directory not found: $APP_HOME"
        exit 1
    fi

    # Check memory limits
    if [[ -f /sys/fs/cgroup/memory.max ]]; then
        local mem_limit
        mem_limit=$(cat /sys/fs/cgroup/memory.max)
        if [[ "$mem_limit" != "max" ]]; then
            local mem_mb=$((mem_limit / 1024 / 1024))
            log_info "Container memory limit: ${mem_mb}MB"
            if [[ "$mem_mb" -lt 512 ]]; then
                log_warn "Memory limit below 512MB may cause issues"
            fi
        fi
    fi

    log_info "Pre-flight checks passed"
}

# =============================================================================
# Main Entry Point
# =============================================================================

main() {
    log_info "============================================================"
    log_info "YAWL Engine Container Startup v${SCRIPT_VERSION}"
    log_info "============================================================"

    # Run pre-flight checks
    preflight_checks

    # Configure environment
    configure_environment

    # Configure development mode if enabled
    configure_development_mode

    # Configure JVM
    configure_jvm

    # Setup directories
    setup_directories

    # Setup signal handlers
    setup_signal_handlers

    # Wait for database
    wait_for_database

    # Initialize database
    initialize_database

    # Drop privileges if running as root
    if [[ "$(id -u)" == "0" ]]; then
        log_info "Dropping privileges to non-root user: ${APP_USER}"
        # Re-exec this script as the app user
        if command -v gosu &>/dev/null; then
            exec gosu "$APP_USER" "$0" "$@"
        elif command -v su-exec &>/dev/null; then
            exec su-exec "$APP_USER" "$0" "$@"
        else
            exec su -s /bin/bash "$APP_USER" -c "\"$0\" $*"
        fi
    fi

    # Start the application
    log_info "============================================================"
    log_info "Starting YAWL Engine..."
    log_info "============================================================"

    start_application
}

# =============================================================================
# Script Execution
# =============================================================================

# Handle direct execution vs sourcing
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
