#!/bin/bash
# Claude Code SessionStart Hook for YAWL Project
# Runs automatically when a Claude Code web session starts

set -e

echo "Starting YAWL project setup..."

cd "$(git rev-parse --show-toplevel 2>/dev/null || echo '.')"

# Ensure Ant is available
if ! command -v ant &> /dev/null; then
    echo "Installing Apache Ant..."
    if [ ! -d "/tmp/apache-ant-1.10.14" ]; then
        echo "Downloading Apache Ant 1.10.14..."
        cd /tmp
        wget -q https://archive.apache.org/dist/ant/binaries/apache-ant-1.10.14-bin.tar.gz
        tar -xzf apache-ant-1.10.14-bin.tar.gz
        cd -
    fi
    export PATH="/tmp/apache-ant-1.10.14/bin:$PATH"
fi

# Verify Java is available
if ! command -v java &> /dev/null; then
    echo "Java is not installed."
    exit 1
fi

echo "Building YAWL project..."
cd build
ant clean compile 2>&1 | grep -E "^\[|BUILD|ERROR" || true

echo "Running unit tests..."
ant unitTest 2>&1 | grep -E "Tests run:|BUILD|FAILED" || true

echo "Building YAWL Control Panel..."
ant build_controlPanel.jar 2>&1 | grep -E "Building jar:|BUILD" || true

echo "SessionStart Hook completed!"
