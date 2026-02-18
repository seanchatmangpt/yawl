#!/usr/bin/env bash
# ==========================================================================
# aot-run.sh — AOT-Optimized Execution
#
# Runs YAWL with AOT optimizations for faster startup and better performance.
#
# Usage:
#   bash scripts/aot-run.sh                  # Run with AOT (dev)
#   bash scripts/aot-run.sh --profile=ci    # Run with CI profile
#   bash scripts/aot-run.sh --debug         # Run with debug flags
#   bash scripts/aot-run.sh --jfr           # Run with JFR recording
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Configuration ─────────────────────────────────────────────────────────
PROFILE="dev"
DEBUG=false
JFR=false
AOT_CACHE="target/spring-boot-app.aot.properties"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --profile=*) PROFILE="${1#*=}"; shift ;;
        --debug) DEBUG=true; shift ;;
        --jfr) JFR=true; shift ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo "  --profile=dev|ci|prod   : Maven profile (default: dev)"
            echo "  --debug                 : Run with debug flags"
            echo "  --jfr                   : Run with JFR recording"
            echo "  -h                      : Show this help"
            exit 0
            ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
done

# ── JVM Arguments ──────────────────────────────────────────────────────────
JAVA_ARGS=(
    "-XX:+UseCompactObjectHeaders"
    "-XX:+UseZGC"
    "-XX:+ZGenerational"
    "-XX:+UseStringDeduplication"
    "-XX:+TieredCompilation"
    "-XX:TieredStopAtLevel=4"
    "-XX:CompileThreshold=10000"
    "-Djava.util.concurrent.ForkJoinPool.common.parallelism=24"
    "-Dfile.encoding=UTF-8"
    "--enable-preview"
)

# Add debug flags
if [[ "$DEBUG" == "true" ]]; then
    JAVA_ARGS+=(
        "-Xdebug"
        "-Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
        "-Dspring.profiles.active=debug"
    )
fi

# Add JFR flags
if [[ "$JFR" == "true" ]]; then
    JAVA_ARGS+=(
        "-XX:+UnlockCommercialFeatures"
        "-XX:+FlightRecorder"
        "-XX:StartFlightRecording:name=aot-runtime,settings=profile,duration=60s,filename=target/runtime-profile.jfr"
    )
fi

# ── Execute ───────────────────────────────────────────────────────────────
echo "AOT Runtime: Starting YAWL with AOT optimizations..."
echo "Profile: $PROFILE"

# Build if needed
if [[ ! -f "target/yawl-app.jar" ]]; then
    echo "Building application..."
    mvn package -P$PROFILE
fi

# Run with AOT
if [[ -f "$AOT_CACHE" ]]; then
    echo "Using AOT cache: $AOT_CACHE"
    java "${JAVA_ARGS[@]}" \
        -Dspring.aot.enabled=true \
        -Dspring.aot.config.location="$AOT_CACHE" \
        -jar target/yawl-app.jar \
        --spring.profiles.active=$PROFILE
else
    echo "AOT cache not found, running without optimizations"
    java "${JAVA_ARGS[@]}" -jar target/yawl-app.jar --spring.profiles.active=$PROFILE
fi