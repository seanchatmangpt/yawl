#!/bin/bash
# =============================================================================
# YAWL MCP/A2A/Z.AI Self-Play Test Runner
# =============================================================================
# This script runs the self-play integration tests for MCP, A2A, and Z.AI
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================"
echo "YAWL Integration Test Runner"
echo "========================================"
echo ""

# Check for Z.AI API key
if [ -z "$ZAI_API_KEY" ]; then
    echo "Warning: ZAI_API_KEY not set. Tests will run in mock mode."
    echo "Set ZAI_API_KEY for full AI-powered tests."
    echo ""
fi

# Build if needed
if [ ! -d "classes" ]; then
    echo "Building YAWL..."
    ant -f build/build.xml compile
    echo ""
fi

# Run tests
echo "Running self-play integration tests..."
echo ""

# Compile and run the test class
CLASSPATH="classes:build/3rdParty/lib/*"

# Check if Z.AI SDK is available
if [ -f "build/3rdParty/lib/zai-sdk-0.3.0.jar" ]; then
    echo "Z.AI SDK found, running with full AI support"
    java -cp "$CLASSPATH" org.yawlfoundation.yawl.integration.test.SelfPlayTest
else
    echo "Z.AI SDK not found, running in compatibility mode"
    echo "(SDK will be downloaded on first build with Ivy)"
    java -cp "$CLASSPATH" org.yawlfoundation.yawl.integration.test.SelfPlayTest
fi

echo ""
echo "Test run completed."
