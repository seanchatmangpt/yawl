#!/usr/bin/env bash
#
# Pattern Demo Wrapper - Multiple execution strategies
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

# Function to run with classpath
run_with_classpath() {
    local args=("$@")

    print_status "Running with classpath..."

    # Build classpath
    local classpath="$JAR_PATH"

    # Add all YAWL dependencies
    local deps=(
        "$PROJECT_ROOT/yawl-engine/target/yawl-engine-6.0.0-Beta.jar"
        "$PROJECT_ROOT/yawl-stateless/target/yawl-stateless-6.0.0-Beta.jar"
        "$PROJECT_ROOT/yawl-elements/target/yawl-elements-6.0.0-Beta.jar"
        "$PROJECT_ROOT/yawl-integration/target/yawl-integration-6.0.0-Beta.jar"
        "$PROJECT_ROOT/yawl-utilities/target/yawl-utilities-6.0.0-Beta.jar"
    )

    for dep in "${deps[@]}"; do
        if [[ -f "$dep" ]]; then
            classpath="$classpath:$dep"
        fi
    done

    # Add dependencies from target/dependency
    local deps_dir="$PROJECT_ROOT/yawl-mcp-a2a-app/target/dependency"
    if [[ -d "$deps_dir" ]]; then
        classpath="$classpath:$(find "$deps_dir" -name "*.jar" | tr '\n' ':')"
    fi

    # Execute with java
    java -cp "$classpath" "$MAIN_CLASS" "${args[@]}"
}

# Function to run with jar
run_with_jar() {
    local args=("$@")

    print_status "Running with JAR..."
    java -jar "$JAR_PATH" "${args[@]}"
}

# Function to run with direct compilation
run_with_compile() {
    local args=("$@")

    print_warning "JAR failed, trying direct compilation..."

    # Create a temporary directory
    local temp_dir=$(mktemp -d)
    trap "rm -rf $temp_dir" EXIT

    # Copy source files
    local src_dir="$PROJECT_ROOT/yawl-mcp-a2a-app/src/main/java/org/yawlfoundation/yawl/mcp/a2a/demo"
    if [[ -d "$src_dir" ]]; then
        cp -r "$src_dir" "$temp_dir"
    else
        print_error "Source directory not found: $src_dir"
        return 1
    fi

    # Try to compile with classpath
    print_status "Compiling demo classes..."

    # Build a comprehensive classpath for compilation
    local compile_classpath="$JAR_PATH"
    local deps=(
        "$PROJECT_ROOT/yawl-engine/target/yawl-engine-6.0.0-Beta.jar"
        "$PROJECT_ROOT/yawl-stateless/target/yawl-stateless-6.0.0-Beta.jar"
        "$PROJECT_ROOT/yawl-elements/target/yawl-elements-6.0.0-Beta.jar"
        "$PROJECT_ROOT/yawl-integration/target/yawl-integration-6.0.0-Beta.jar"
        "$PROJECT_ROOT/yawl-utilities/target/yawl-utilities-6.0.0-Beta.jar"
    )

    for dep in "${deps[@]}"; do
        if [[ -f "$dep" ]]; then
            compile_classpath="$compile_classpath:$dep"
        fi
    done

    # Compile
    if javac -cp "$compile_classpath" -d "$temp_dir" "$temp_dir"/*.java; then
        print_status "Compilation successful!"
        # Run
        java -cp "$temp_dir:$compile_classpath" "$MAIN_CLASS" "${args[@]}"
    else
        print_error "Compilation failed"
        return 1
    fi
}

# Main function
main() {
    # Parse arguments (excluding script name)
    local demo_args=()
    while [[ $# -gt 0 ]]; do
        demo_args+=("$1")
        shift
    done

    # Check if help is requested
    for arg in "${demo_args[@]}"; do
        if [[ "$arg" == "--help" || "$arg" == "-h" ]]; then
            # Show help directly
            echo "YAWL Pattern Demo Wrapper"
            echo
            echo "Usage: $0 [options]"
            echo
            echo "Options:"
            echo "  --pattern PATTERNS     Specific pattern IDs (e.g., WCP-1,WCP-2)"
            echo "  --category CATEGORY    Pattern category (e.g., BASIC, BRANCHING)"
            echo "  --all                 Run all patterns"
            echo "  --format FORMAT       Output format: console, json, markdown, html"
            echo "  --timeout SECONDS     Timeout per pattern"
            echo "  -h, --help           Show this help"
            echo
            echo "Examples:"
            echo "  $0 --pattern WCP-1"
            echo "  $0 --category BASIC"
            echo "  $0 --all --format json"
            return 0
        fi
    done

    # Try different execution methods
    if [[ -f "$JAR_PATH" ]]; then
        print_status "JAR found at $JAR_PATH"

        # Method 1: Run with jar
        if run_with_jar "${demo_args[@]}"; then
            return 0
        fi

        # Method 2: Run with classpath
        if run_with_classpath "${demo_args[@]}"; then
            return 0
        fi
    else
        print_warning "JAR not found"
    fi

    # Method 3: Run with direct compilation
    if run_with_compile "${demo_args[@]}"; then
        return 0
    fi

    # If all methods failed
    print_error "All execution methods failed"
    echo
    echo "Troubleshooting suggestions:"
    echo "1. Ensure the project is built: mvn clean package -pl yawl-mcp-a2a-app"
    echo "2. Check Java version: java -version (should be compatible with Java 21+)"
    echo "3. Verify all dependencies are available"
    echo "4. Try building with: mvn -DskipTests package -pl yawl-mcp-a2a-app"
    return 1
}

# Run main with all arguments
main "$@"