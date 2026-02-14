#!/bin/bash
# 80/20 Innovation #1: One-Command Development Workflow
# Usage: ./.claude/quick-start.sh [test|build|run|clean]

set -euo pipefail

ACTION="${1:-test}"

case "$ACTION" in
  test)
    echo "🧪 Running tests..."
    ant -f build/build.xml unitTest | tail -20
    ;;
  build)
    echo "🔨 Building YAWL..."
    ant -f build/build.xml compile && echo "✅ Build successful"
    ;;
  run)
    echo "🚀 Running YAWL Control Panel..."
    java -jar output/YawlControlPanel-5.2.jar
    ;;
  clean)
    echo "🧹 Cleaning build artifacts..."
    ant -f build/build.xml clean && echo "✅ Clean complete"
    ;;
  env)
    echo "🔍 Environment Check..."
    java -cp classes org.yawlfoundation.yawl.util.EnvironmentDetector
    ;;
  *)
    echo "Usage: $0 {test|build|run|clean|env}"
    echo ""
    echo "Commands:"
    echo "  test  - Run unit tests (102 tests)"
    echo "  build - Compile all sources (875 files)"
    echo "  run   - Launch YAWL Control Panel"
    echo "  clean - Remove build artifacts"
    echo "  env   - Check current environment"
    exit 1
    ;;
esac
