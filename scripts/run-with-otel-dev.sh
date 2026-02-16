#!/bin/bash
# Run YAWL with OpenTelemetry Java Agent - Development Mode
# This script uses logging exporters for local development (no external collector needed)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(dirname "$SCRIPT_DIR")"

# Configuration
OTEL_AGENT_JAR="${YAWL_ROOT}/target/agents/opentelemetry-javaagent.jar"
OTEL_CONFIG="${YAWL_ROOT}/observability/opentelemetry/agent-config-dev.properties"

# Environment variables for development
export OTEL_SERVICE_NAME="yawl-engine-dev"
export OTEL_SERVICE_VERSION="5.2-SNAPSHOT"
export OTEL_RESOURCE_ATTRIBUTES="deployment.environment=development,service.namespace=yawl-workflows"

# Use logging exporters (no external collector needed)
export OTEL_TRACES_EXPORTER="logging"
export OTEL_METRICS_EXPORTER="logging,prometheus"
export OTEL_LOGS_EXPORTER="logging"

# Sample everything in development
export OTEL_TRACES_SAMPLER="always_on"

# Prometheus metrics on localhost
export OTEL_EXPORTER_PROMETHEUS_PORT="9464"

# Enable debug mode
export OTEL_JAVAAGENT_DEBUG="true"
export OTEL_JAVAAGENT_LOGGING="application"

echo "=================================================="
echo "Starting YAWL with OpenTelemetry (Development)"
echo "=================================================="
echo "Service Name: ${OTEL_SERVICE_NAME}"
echo "Trace Exporter: ${OTEL_TRACES_EXPORTER}"
echo "Metrics Exporters: ${OTEL_METRICS_EXPORTER}"
echo "Prometheus Port: ${OTEL_EXPORTER_PROMETHEUS_PORT}"
echo "Debug Mode: Enabled"
echo "=================================================="

# Check if OpenTelemetry agent JAR exists
if [ ! -f "$OTEL_AGENT_JAR" ]; then
    echo "ERROR: OpenTelemetry Java agent not found at: $OTEL_AGENT_JAR"
    echo "Please run 'mvn clean package' to download the agent."
    exit 1
fi

# Build Java agent arguments
JAVA_OPTS="${JAVA_OPTS} -javaagent:${OTEL_AGENT_JAR}"
if [ -f "$OTEL_CONFIG" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Dotel.javaagent.configuration-file=${OTEL_CONFIG}"
fi

# Development JVM options
JAVA_OPTS="${JAVA_OPTS} -Xmx2g -Xms1g"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseG1GC"

export JAVA_OPTS

echo "JAVA_OPTS: ${JAVA_OPTS}"
echo "=================================================="

# Start Tomcat
if [ -n "$CATALINA_HOME" ]; then
    echo "Starting Tomcat in development mode..."
    "${CATALINA_HOME}/bin/catalina.sh" run
else
    echo "ERROR: CATALINA_HOME not set. Please set it to your Tomcat installation directory."
    exit 1
fi
