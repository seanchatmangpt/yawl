#!/bin/bash

# Self-Play Test Orchestrator Example Script
# This script demonstrates how to use the Self-Play Test Orchestrator

echo "=== YAWL Self-Play Test Orchestrator Example ==="
echo ""

# Check if we're in the correct directory
if [ ! -f "pom.xml" ]; then
    echo "Error: pom.xml not found. Please run this script from the YAWL project root."
    exit 1
fi

# Set default configuration
ENGINE_URL=${YAWL_ENGINE_URL:-"http://localhost:8080/yawl"}
USERNAME=${YAWL_USERNAME:-"admin"}
PASSWORD=${YAWL_PASSWORD:-"admin"}
ITERATIONS=${YAWL_ITERATIONS:-"3"}
OUTPUT_DIR=${YAWL_OUTPUT_DIR:-"./self-play-results"}

echo "Configuration:"
echo "  Engine URL: $ENGINE_URL"
echo "  Username: $USERNAME"
echo "  Password: $PASSWORD"
echo "  Iterations: $ITERATIONS"
echo "  Output Directory: $OUTPUT_DIR"
echo ""

# First, compile the project
echo "Building YAWL project..."
mvn clean compile -q
if [ $? -ne 0 ]; then
    echo "Error: Build failed. Please check the project configuration."
    exit 1
fi
echo "Build complete."
echo ""

# Compile test classes
echo "Compiling test classes..."
mvn test-compile -q

# Run the orchestrator with current configuration
echo "Starting Self-Play Test Orchestrator..."
echo "This will run $ITERATIONS test iterations."
echo ""

# Set Java options for better performance
JAVA_OPTS="-Xmx2g -Xms1g"

# Build classpath
CLASSPATH=$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)
CLASSPATH="target/test-classes:target/classes:$CLASSPATH"

# Run the orchestrator
java $JAVA_OPTS -cp "$CLASSPATH" \
    org.yawlfoundation.yawl.integration.selfplay.SelfPlayOrchestrator \
    --engine-url "$ENGINE_URL" \
    --username "$USERNAME" \
    --password "$PASSWORD" \
    --iterations "$ITERATIONS" \
    --output-dir "$OUTPUT_DIR"

# Check the result
if [ $? -eq 0 ]; then
    echo ""
    echo "=== Test Execution Complete ==="
    echo "Results saved to: $OUTPUT_DIR/self-play-report.json"
    echo ""

    # Display summary if the report file exists
    if [ -f "$OUTPUT_DIR/self-play-report.json" ]; then
        echo "Test Summary:"
        grep -E "(total_iterations|success_rate|total_duration)" "$OUTPUT_DIR/self-play-report.json" | head -10
    fi
else
    echo ""
    echo "=== Test Execution Failed ==="
    echo "Check the logs above for details."
    exit 1
fi

echo ""
echo "Example completed successfully."