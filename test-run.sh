#!/bin/bash
# Test script to run YAWL engine directly

# Set Java options
export JAVA_OPTS="
    -XX:+UseContainerSupport
    -XX:MaxRAMPercentage=75.0
    -XX:InitialRAMPercentage=50.0
    -XX:+UseZGC
    -XX:+UseStringDeduplication
    -XX:+ExitOnOutOfMemoryError
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath=/app/logs/heap-dump.hprof
    -Djava.security.egd=file:/dev/./urandom
    -Djava.io.tmpdir=/app/temp
    -Dfile.encoding=UTF-8
    -Djdk.virtualThreadScheduler.parallelism=200
    -Djdk.virtualThreadScheduler.maxPoolSize=256
    -Djdk.tracePinnedThreads=short
"

# Create directories
mkdir -p /app/logs /app/data /app/temp /app/config /app/specifications

# Find the JAR
JAR_FILE="/app/yawl-engine.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "Looking for yawl-engine.jar..."
    JAR_FILE=$(find /work -name "yawl-engine-*.jar" | head -1)
    if [ -z "$JAR_FILE" ]; then
        echo "Error: No yawl-engine.jar found"
        exit 1
    fi
fi

echo "Using JAR: $JAR_FILE"

# Run with java -cp to specify main class
echo "Starting YAWL engine..."
java $JAVA_OPTS -cp "$JAR_FILE" org.yawlfoundation.yawl.controlpanel.YControlPanel &

# Wait for startup
echo "Waiting for engine to start..."
sleep 10

# Check if it's running
if curl -sf http://localhost:8080/ > /dev/null 2>&1; then
    echo "YAWL engine is running on http://localhost:8080/"
    tail -f /dev/null
else
    echo "YAWL engine failed to start"
    exit 1
fi