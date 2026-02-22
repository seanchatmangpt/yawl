#!/bin/bash
# =============================================================================
# test-run.sh — YAWL Engine Container Entry Point
# =============================================================================
# Entry point for YAWL engine containers:
# - Starts the Spring Boot actuator application
# - Ensures proper signal handling for graceful shutdown
# - Supports both development and production profiles
#
# Usage:
#   docker run -e SPRING_PROFILES_ACTIVE=development yawl-engine:6.0.0-alpha
# =============================================================================

set -euo pipefail

# Configuration
APP_JAR="/app/yawl-engine.jar"
HEALTH_URL="http://localhost:8080/actuator/health/liveness"
MANAGEMENT_URL="http://localhost:9090/actuator/health"
ENGINE_URL="http://localhost:8080/"

# Wait for database to be ready (if using external database)
wait_for_database() {
    local db_type="${DB_TYPE:-h2}"
    if [[ "$db_type" == "postgres" ]]; then
        echo "Waiting for PostgreSQL to be ready..."
        for i in {1..30}; do
            if nc -z -w 1 "$DB_HOST" "$DB_PORT" 2>/dev/null; then
                echo "PostgreSQL is ready"
                return 0
            fi
            echo "Waiting for PostgreSQL... ($i/30)"
            sleep 2
        done
        echo "PostgreSQL is not ready. Continuing anyway..."
    fi
}

# Graceful shutdown handler
graceful_shutdown() {
    echo "[test-run] SIGTERM received - initiating graceful shutdown"

    # Signal the JVM to shutdown gracefully
    if [[ -n "$JVM_PID" ]]; then
        kill -TERM "$JVM_PID" 2>/dev/null || true

        # Wait for up to 55 seconds for graceful shutdown
        local waited=0
        while kill -0 "$JVM_PID" 2>/dev/null && [[ $waited -lt 55 ]]; do
            sleep 1
            (( waited++ ))
        done

        # Force kill if still running
        if kill -0 "$JVM_PID" 2>/dev/null; then
            echo "[test-run] JVM did not stop in time - sending KILL"
            kill -KILL "$JVM_PID" 2>/dev/null || true
        fi
    fi

    echo "[test-run] Shutdown complete"
    exit 0
}

# Set up signal handlers
trap graceful_shutdown SIGTERM SIGINT

echo "[test-run] Starting YAWL Engine v6.0.0"
echo "[test-run] Java: $(java -version 2>&1 | head -1)"
echo "[test-run] Profile: ${SPRING_PROFILES_ACTIVE:-development}"
echo "[test-run] Database: ${DB_TYPE:-h2}"

# Wait for database if needed
wait_for_database

# Start the application
echo "[test-run] Starting Spring Boot application: $APP_JAR"

# Launch JVM in background so we can handle signals
if [[ -f "$APP_JAR" ]]; then
    # Launch with proper JVM options
    exec java ${JAVA_OPTS} \
        -jar "$APP_JAR" \
        --spring.config.location=classpath:/,/app/config/ &

    JVM_PID=$!
    echo "[test-run] JVM PID: $JVM_PID"

    # Wait for the JVM to exit
    wait "$JVM_PID"
    EXIT_CODE=$?
    echo "[test-run] JVM exited with code $EXIT_CODE"
    exit $EXIT_CODE
else
    echo "[test-run] ERROR: Application JAR not found: $APP_JAR"
    echo "[test-run] Check the Docker build process"
    exit 1
fi