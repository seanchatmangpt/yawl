#!/usr/bin/env bash
#
# Standalone Pattern Demo Runner - Direct execution without Spring Boot
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Configuration
JAR_PATH="$PROJECT_ROOT/yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar"
MAIN_CLASS="org.yawlfoundation.yawl.mcp.a2a.demo.PatternDemoRunner"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# Function to check if command is available
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to run pattern demo
run_pattern_demo() {
    local args=("$@")

    print_info "Running pattern demo with arguments: ${args[*]}"

    # Try different approaches
    if [[ -f "$JAR_PATH" ]]; then
        # Approach 1: Try to extract classes from JAR
        print_status "Found JAR at $JAR_PATH"

        # Create a temporary directory for extracted classes
        local temp_dir=$(mktemp -d)
        trap "rm -rf $temp_dir" EXIT

        # Extract only the demo classes
        if jar tf "$JAR_PATH" | grep "org/yawlfoundation/yawl/mcp/a2a/demo/PatternDemoRunner.class" > /dev/null; then
            print_status "PatternDemoRunner found in JAR, extracting..."
            jar xf "$JAR_PATH" "org/yawlfoundation/yawl/mcp/a2a/demo/" -C "$temp_dir" 2>/dev/null || true

            # Build classpath
            local classpath="$JAR_PATH"

            # Add dependencies from target/dependency
            local deps_dir="$PROJECT_ROOT/yawl-mcp-a2a-app/target/dependency"
            if [[ -d "$deps_dir" ]]; then
                local dep_jars=$(find "$deps_dir" -name "*.jar" | tr '\n' ':')
                if [[ -n "$dep_jars" ]]; then
                    classpath="$classpath:$dep_jars"
                fi
            fi

            # Try to run
            if [[ -d "$temp_dir/org/yawlfoundation/yawl/mcp/a2a/demo" ]]; then
                print_status "Running extracted demo classes..."
                if java -cp "$classpath" "$MAIN_CLASS" "${args[@]}" 2>/dev/null; then
                    return 0
                fi
            fi
        fi

        # Approach 2: Try to run with explicit classpath exclusion
        print_status "Trying classpath exclusion method..."

        # Build classpath without Spring Boot auto-configuration
        local classpath="$JAR_PATH"

        # Add YAWL core dependencies
        local yawl_jars=(
            "$PROJECT_ROOT/yawl-engine/target/yawl-engine-6.0.0-Beta.jar"
            "$PROJECT_ROOT/yawl-stateless/target/yawl-stateless-6.0.0-Beta.jar"
            "$PROJECT_ROOT/yawl-elements/target/yawl-elements-6.0.0-Beta.jar"
            "$PROJECT_ROOT/yawl-integration/target/yawl-integration-6.0.0-Beta.jar"
            "$PROJECT_ROOT/yawl-utilities/target/yawl-utilities-6.0.0-Beta.jar"
        )

        for jar in "${yawl_jars[@]}"; do
            if [[ -f "$jar" ]]; then
                classpath="$classpath:$jar"
            fi
        done

        # Add demo-specific dependencies
        local demo_deps=(
            "$PROJECT_ROOT/yawl-mcp-a2a-app/target/dependency/jackson-datatype-jdk8-2.19.4.jar"
            "$PROJECT_ROOT/yawl-mcp-a2a-app/target/dependency/jackson-databind-2.19.4.jar"
            "$PROJECT_ROOT/yawl-mcp-a2a-app/target/dependency/jackson-core-2.19.4.jar"
        )

        for dep in "${demo_deps[@]}"; do
            if [[ -f "$dep" ]]; then
                classpath="$classpath:$dep"
            fi
        done

        # Run with minimal JVM args
        java -cp "$classpath" -Dspring.autoconfigure.exclude=org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseConfiguration "$MAIN_CLASS" "${args[@]}" 2>/dev/null && return 0
    fi

    # Approach 3: Try to run the pattern demo directly from source
    print_status "Trying direct source execution..."
    local src_dir="$PROJECT_ROOT/yawl-mcp-a2a-app/src/main/java"
    if [[ -d "$src_dir" ]]; then
        print_status "Found source directory, compiling PatternDemoRunner..."

        # Create temp directory
        local build_dir=$(mktemp -d)
        trap "rm -rf $build_dir" EXIT

        # Copy PatternDemoRunner source
        cp -r "$src_dir/org/yawlfoundation/yawl/mcp/a2a/demo" "$build_dir/"

        # Build classpath
        local build_classpath=""
        local jars=(
            "$JAR_PATH"
            "$PROJECT_ROOT/yawl-engine/target/yawl-engine-6.0.0-Beta.jar"
            "$PROJECT_ROOT/yawl-stateless/target/yawl-stateless-6.0.0-Beta.jar"
            "$PROJECT_ROOT/yawl-elements/target/yawl-elements-6.0.0-Beta.jar"
            "$PROJECT_ROOT/yawl-integration/target/yawl-integration-6.0.0-Beta.jar"
            "$PROJECT_ROOT/yawl-utilities/target/yawl-utilities-6.0.0-Beta.jar"
            "$PROJECT_ROOT/yawl-mcp-a2a-app/target/dependency/jackson-datatype-jdk8-2.19.4.jar"
            "$PROJECT_ROOT/yawl-mcp-a2a-app/target/dependency/jackson-databind-2.19.4.jar"
            "$PROJECT_ROOT/yawl-mcp-a2a-app/target/dependency/jackson-core-2.19.4.jar"
        )

        for jar in "${jars[@]}"; do
            if [[ -f "$jar" ]]; then
                build_classpath="$build_classpath:$jar"
            fi
        done

        # Compile and run
        if [[ -n "$build_classpath" ]]; then
            print_status "Compiling PatternDemoRunner..."
            javac -cp "$build_classpath" -d "$build_dir" "$build_dir/org/yawlfoundation/yawl/mcp/a2a/demo"/*.java 2>/dev/null && {
                print_status "Running PatternDemoRunner..."
                java -cp "$build_classpath:$build_dir" "$MAIN_CLASS" "${args[@]}" 2>/dev/null && return 0
            }
        fi
    fi

    # All approaches failed
    return 1
}

# Main function
main() {
    # Check if java is available
    if ! command_exists java; then
        print_error "Java is not installed or not in PATH"
        exit 1
    fi

    # Parse arguments
    local demo_args=()
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help|-h)
                cat << EOF
Standalone Pattern Demo Runner - Run Van Der Aalst workflow patterns

Usage: $0 [OPTIONS]

Default: Run basic patterns (WCP-1 through WCP-5)

Options:
  --pattern PATTERNS      Specific pattern IDs (comma-separated)
                         Example: --pattern "WCP-1,WCP-2,WCP-10"

  --category CATEGORY     Category name (BASIC, BRANCHING, MULTI_INSTANCE,
                         STATE_BASED, DISTRIBUTED, EVENT_DRIVEN, AI_ML,
                         ENTERPRISE, AGENT)

  --all                   Run all available patterns (43+)

  --format FORMAT         Output format: console, json, markdown, html
                         (default: console)

  --timeout SECONDS       Execution timeout per pattern (default: 300)

  --output PATH           Output file path (default: stdout)

  --sequential            Disable parallel execution

  --token-report          Include token savings analysis

  --with-commentary       Include Wil van der Aalst commentary

  -h, --help             Show this help message

Examples:
  $0                                   # Run basic patterns
  $0 --pattern WCP-1                  # Run WCP-1 (Sequence)
  $0 --pattern WCP-1,WCP-2,WCP-3     # Run multiple patterns
  $0 --category BASIC                 # Run all basic patterns
  $0 --all --format html              # Run all patterns, generate HTML report
  $0 --pattern WCP-1 --format json   # WCP-1 with JSON output

EOF
                exit 0
                ;;
            *)
                demo_args+=("$1")
                shift
                ;;
        esac
    done

    # Check if JAR exists
    if [[ ! -f "$JAR_PATH" ]]; then
        print_error "JAR file not found at $JAR_PATH"
        print_info "Please build the project first:"
        print_info "  mvn -DskipTests package -pl yawl-mcp-a2a-app"
        exit 1
    fi

    # Print what we're about to run
    if [[ "${demo_args[*]}" =~ "--all" ]]; then
        echo "======================================================================"
        print_info "        Running ALL patterns (43+)"
    elif [[ "${demo_args[*]}" =~ "--category" ]]; then
        echo "======================================================================"
        print_info "        Running patterns by category"
    elif [[ "${demo_args[*]}" =~ "--pattern" ]]; then
        echo "======================================================================"
        print_info "        Running specific patterns"
    else
        echo "======================================================================"
        print_info "        Running basic patterns (WCP-1 through WCP-5)"
    fi
    echo "======================================================================"
    echo

    # Run the demo
    if run_pattern_demo "${demo_args[@]}"; then
        echo
        print_status "Pattern demo completed successfully!"
        exit 0
    else
        echo
        print_error "Pattern demo failed"
        print_info "Troubleshooting:"
        print_info "1. Check Java version: java -version (should be 21+)"
        print_info "2. Verify all JAR files exist"
        print_info "3. Check if dependencies are available"
        print_info "4. Try building again: mvn -DskipTests package -pl yawl-mcp-a2a-app"
        exit 1
    fi
}

# Run with all arguments
main "$@"