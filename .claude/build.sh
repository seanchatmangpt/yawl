#!/bin/bash
# YAWL Build Wrapper Script
# Convenient way to run common build commands
# Usage: ./.claude/build.sh [command] [args...]

if ! command -v ant &> /dev/null; then
    if [ -d "/tmp/apache-ant-1.10.14" ]; then
        export PATH="/tmp/apache-ant-1.10.14/bin:$PATH"
    else
        echo "Ant not found. Please run session-start-hook first."
        exit 1
    fi
fi

cd "$(git rev-parse --show-toplevel 2>/dev/null || echo '.')/build" 2>/dev/null || cd ./build

COMMAND="${1:-compile}"

case "$COMMAND" in
    build)
        ant clean compile
        ;;
    test)
        ant unitTest
        ;;
    clean)
        ant clean
        ;;
    all)
        ant clean compile unitTest build_controlPanel.jar
        ;;
    cp)
        ant build_controlPanel.jar
        ;;
    compile)
        ant compile
        ;;
    package)
        ant buildAll
        ;;
    help)
        echo "YAWL Build Commands:"
        echo "  build          - Clean and compile"
        echo "  test           - Run unit tests"
        echo "  clean          - Remove build artifacts"
        echo "  all            - Full cycle (clean, compile, test, package)"
        echo "  compile        - Compile source files only"
        echo "  cp             - Build Control Panel JAR"
        echo "  package        - Build all components"
        echo "  help           - Show this help message"
        ;;
    *)
        ant "$COMMAND" "${@:2}"
        ;;
esac
