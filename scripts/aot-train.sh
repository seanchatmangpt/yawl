#!/usr/bin/env bash
# ==========================================================================
# aot-train.sh — AOT Profile Training for YAWL
#
# Runs the application with JFR profiling to collect hot path data for AOT.
#
# Usage:
#   bash scripts/aot-train.sh                  # Run with dev profile
#   bash scripts/aot-train.sh --profile=ci    # Run with CI profile
#   bash scripts/aot-train.sh --duration=30    # Run for 30 seconds
#   bash scripts/aot-train.sh --modules=all    # Train all modules
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Configuration ─────────────────────────────────────────────────────────
PROFILE="dev"
DURATION=30
MODULES="yawl-engine"
JFR_FILE="target/profile.jfr"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --profile=*) PROFILE="${1#*=}"; shift ;;
        --duration=*) DURATION="${1#*=}"; shift ;;
        --modules=*) MODULES="${1#*=}"; shift ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo "  --profile=dev|ci|prod   : Maven profile (default: dev)"
            echo "  --duration=seconds       : Duration to run (default: 30)"
            echo "  --modules=module1,...   : Modules to train (default: yawl-engine)"
            echo "  -h                      : Show this help"
            exit 0
            ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
done

# ── Setup ─────────────────────────────────────────────────────────────────
echo "AOT Training: Starting profile training..."
echo "Profile: $PROFILE"
echo "Duration: ${DURATION}s"
echo "Modules: $MODULES"
echo "JFR Output: $JFR_FILE"

# Create target directory
mkdir -p target

# Clean previous JFR files
rm -f target/*.jfr

# ── Start JFR Recording ─────────────────────────────────────────────────────
echo "Starting JFR recording..."

# Start background process with JFR
java -XX:+UnlockCommercialFeatures \
     -XX:+FlightRecorder \
     -XX:StartFlightRecording:name=aot-training,settings=profile,duration=${DURATION}s,filename="$JFR_FILE" \
     -jar target/yawl-app.jar &
JAVA_PID=$!

echo "Running application with PID: $JAVA_PID"
echo "Training for ${DURATION} seconds..."

# Wait for the process
sleep $DURATION

# Check if process is still running
if kill -0 $JAVA_PID 2>/dev/null; then
    echo "Stopping application..."
    kill -TERM $JAVA_PID
    wait $JAVA_PID
    echo "Application stopped"
else
    echo "Application already stopped"
fi

# ── Generate AOT Cache ──────────────────────────────────────────────────────
echo "Generating AOT cache..."

# Run with AOT enabled
mvn compile -P$PROFILE -Dspring.aot.enabled=true \
    -Dspring.aot.generate=true \
    -Dspring.aot.generate-write-to=target/spring-boot-app.aot.properties

# ── Analyze Results ──────────────────────────────────────────────────────
echo "Analyzing profiling results..."

if [[ -f "$JFR_FILE" ]]; then
    echo "✓ JFR file created: $JFR_FILE"

    # Basic JFR analysis
    java -jar ~/.m2/repository/org/openjdk/jmc/jmc-jfr/8.2.0/jmc-jfr-8.2.0.jar "$JFR_FILE" &

    echo "✓ AOT training completed!"
    echo "  - Profile duration: ${DURATION}s"
    echo "  - JFR file size: $(du -h "$JFR_FILE" | cut -f1)"
    echo "  - AOT cache: target/spring-boot-app.aot.properties"
else
    echo "✗ JFR file not created"
    exit 1
fi