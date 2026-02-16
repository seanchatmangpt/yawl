#!/bin/bash
# Run YAWL with OpenTelemetry Java Agent - Production Mode
# This script demonstrates zero-code instrumentation using the OpenTelemetry Java agent

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(dirname "$SCRIPT_DIR")"

# Configuration
OTEL_AGENT_JAR="${YAWL_ROOT}/target/agents/opentelemetry-javaagent.jar"
OTEL_CONFIG="${YAWL_ROOT}/observability/opentelemetry/agent-config.properties"
YAWL_WAR="${CATALINA_HOME}/webapps/yawl.war"

# Environment variables (override with your own values)
export OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME:-yawl-engine}"
export OTEL_SERVICE_VERSION="${OTEL_SERVICE_VERSION:-5.2}"
export OTEL_RESOURCE_ATTRIBUTES="${OTEL_RESOURCE_ATTRIBUTES:-deployment.environment=production,service.namespace=yawl-workflows}"

# OTLP Exporter configuration
export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://otel-collector:4318}"
export OTEL_EXPORTER_OTLP_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-http/protobuf}"

# Trace configuration
export OTEL_TRACES_EXPORTER="${OTEL_TRACES_EXPORTER:-otlp}"
export OTEL_TRACES_SAMPLER="${OTEL_TRACES_SAMPLER:-parentbased_traceidratio}"
export OTEL_TRACES_SAMPLER_ARG="${OTEL_TRACES_SAMPLER_ARG:-0.1}"

# Metrics configuration
export OTEL_METRICS_EXPORTER="${OTEL_METRICS_EXPORTER:-otlp,prometheus}"
export OTEL_EXPORTER_PROMETHEUS_PORT="${OTEL_EXPORTER_PROMETHEUS_PORT:-9464}"

# Logs configuration
export OTEL_LOGS_EXPORTER="${OTEL_LOGS_EXPORTER:-otlp}"

# Java agent configuration
export OTEL_JAVAAGENT_LOGGING="${OTEL_JAVAAGENT_LOGGING:-application}"

echo "=================================================="
echo "Starting YAWL with OpenTelemetry Instrumentation"
echo "=================================================="
echo "Service Name: ${OTEL_SERVICE_NAME}"
echo "Service Version: ${OTEL_SERVICE_VERSION}"
echo "OTLP Endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT}"
echo "Trace Sampler: ${OTEL_TRACES_SAMPLER} (${OTEL_TRACES_SAMPLER_ARG})"
echo "Metrics Exporters: ${OTEL_METRICS_EXPORTER}"
echo "Prometheus Port: ${OTEL_EXPORTER_PROMETHEUS_PORT}"
echo "=================================================="

# Check if OpenTelemetry agent JAR exists
if [ ! -f "$OTEL_AGENT_JAR" ]; then
    echo "ERROR: OpenTelemetry Java agent not found at: $OTEL_AGENT_JAR"
    echo "Please run 'mvn clean package' to download the agent."
    exit 1
fi

# Check if configuration file exists
if [ ! -f "$OTEL_CONFIG" ]; then
    echo "WARNING: OpenTelemetry configuration file not found at: $OTEL_CONFIG"
    echo "Using environment variables only."
    OTEL_CONFIG_ARG=""
else
    OTEL_CONFIG_ARG="-Dotel.javaagent.configuration-file=${OTEL_CONFIG}"
fi

# Build Java agent arguments
JAVA_OPTS="${JAVA_OPTS} -javaagent:${OTEL_AGENT_JAR}"
JAVA_OPTS="${JAVA_OPTS} ${OTEL_CONFIG_ARG}"

# Additional JVM options for production
JAVA_OPTS="${JAVA_OPTS} -Xmx4g -Xms2g"
JAVA_OPTS="${JAVA_OPTS} -XX:+UseG1GC"
JAVA_OPTS="${JAVA_OPTS} -XX:MaxGCPauseMillis=200"
JAVA_OPTS="${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom"

export JAVA_OPTS

echo "JAVA_OPTS: ${JAVA_OPTS}"
echo "=================================================="

# Start Tomcat with OpenTelemetry instrumentation
if [ -n "$CATALINA_HOME" ]; then
    echo "Starting Tomcat..."
    "${CATALINA_HOME}/bin/catalina.sh" run
else
    echo "ERROR: CATALINA_HOME not set. Please set it to your Tomcat installation directory."
    exit 1
fi
