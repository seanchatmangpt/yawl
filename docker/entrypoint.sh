#!/bin/bash
# YAWL v6.0.0 container entrypoint
# Handles graceful shutdown on SIGTERM from Kubernetes preStop or pod eviction.

set -euo pipefail

# Shutdown hook: waits for active work items to complete (up to 60s)
graceful_shutdown() {
    echo "[entrypoint] SIGTERM received - initiating graceful shutdown"
    echo "[entrypoint] Sending TERM to JVM (PID ${JVM_PID})"
    kill -TERM "${JVM_PID}" 2>/dev/null || true

    # Give the JVM up to 55 seconds to finish (terminationGracePeriodSeconds=60)
    local waited=0
    while kill -0 "${JVM_PID}" 2>/dev/null && [[ ${waited} -lt 55 ]]; do
        sleep 1
        (( waited++ ))
    done

    if kill -0 "${JVM_PID}" 2>/dev/null; then
        echo "[entrypoint] JVM did not stop in time - sending KILL"
        kill -KILL "${JVM_PID}" 2>/dev/null || true
    fi

    echo "[entrypoint] Shutdown complete"
    exit 0
}

trap graceful_shutdown SIGTERM SIGINT

# Validate required environment
: "${DB_HOST:?DB_HOST must be set}"
: "${DB_PORT:?DB_PORT must be set}"
: "${DB_NAME:?DB_NAME must be set}"
: "${DB_USER:?DB_USER must be set}"
: "${DB_PASSWORD:?DB_PASSWORD must be set - inject via Kubernetes Secret}"

echo "[entrypoint] Starting YAWL v6.0.0"
echo "[entrypoint] Java: $(java -version 2>&1 | head -1)"
echo "[entrypoint] DB host: ${DB_HOST}:${DB_PORT}/${DB_NAME}"

# Launch JVM in background so the trap above can intercept signals
# shellcheck disable=SC2086
exec java ${JAVA_OPTS} \
    -jar /app/yawl.jar \
    --spring.config.location=classpath:/,/app/config/ &

JVM_PID=$!
echo "[entrypoint] JVM PID: ${JVM_PID}"

# Wait for the JVM; propagate exit code
wait "${JVM_PID}"
EXIT_CODE=$?
echo "[entrypoint] JVM exited with code ${EXIT_CODE}"
exit "${EXIT_CODE}"
