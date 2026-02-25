#!/usr/bin/env bash
#
# YAWL Pattern Demo Runner
#
# Run YAWL Van Der Aalst workflow pattern demonstrations.
# Based on the PatternDemoRunner class in yawl-mcp-a2a-app module.
#
# Usage:
#   ./run-vdaalst-demo.sh                    # Run basic patterns (WCP-1 through WCP-5)
#   ./run-vdaalst-demo.sh --all              # Run all 43+ patterns
#   ./run-vdaalst-demo.sh --pattern WCP-1   # Run specific pattern
#   ./run-vdaalst-demo.sh --category BASIC   # Run all patterns in a category
#   ./run-vdaalst-demo.sh --help             # Show help
#

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
JAR_PATH="$PROJECT_ROOT/yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar"
MAIN_CLASS="org.yawlfoundation.yawl.mcp.a2a.demo.PatternDemoRunner"
CLASSPATH="$JAR_PATH:$PROJECT_ROOT/yawl-engine/target/yawl-engine-6.0.0-Beta.jar:$PROJECT_ROOT/yawl-stateless/target/yawl-stateless-6.0.0-Beta.jar:$PROJECT_ROOT/yawl-elements/target/yawl-elements-6.0.0-Beta.jar"

# Default options
DEFAULT_PATTERNS="WCP-1,WCP-2,WCP-3,WCP-4,WCP-5"
DEFAULT_CATEGORY=""
DEFAULT_TIMEOUT=300
DEFAULT_FORMAT=console
DEFAULT_OUTPUT=""
DEFAULT_PARALLEL=true
DEFAULT_TOKEN_ANALYSIS=true
DEFAULT_WITH_COMMENTARY=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_color() {
    local color="$1"
    local message="$2"
    echo -e "${color}${message}${NC}"
}

# Function to print usage
print_usage() {
    cat << EOF
YAWL Pattern Demo Runner - Run Van Der Aalst workflow pattern demonstrations

Usage: $0 [OPTIONS]

Default: Run basic patterns (WCP-1 through WCP-5)

Options:
  -p, --pattern PATTERNS      Specific pattern IDs (comma-separated)
                             Example: --pattern "WCP-1,WCP-2,WCP-10"

  -c, --category CATEGORY     Category name (BASIC, BRANCHING, MULTI_INSTANCE,
                             STATE_BASED, DISTRIBUTED, EVENT_DRIVEN, AI_ML,
                             ENTERPRISE, AGENT)

  -a, --all                   Run all available patterns (43+)

  -t, --timeout SECONDS       Execution timeout per pattern (default: $DEFAULT_TIMEOUT)

  -f, --format FORMAT         Output format: console, json, markdown, html
                             (default: $DEFAULT_FORMAT)

  -o, --output PATH           Output file path (default: stdout)

  --sequential                Disable parallel execution

  --no-token-analysis         Disable token savings analysis

  --with-commentary          Include Wil van der Aalst commentary

  -h, --help                 Show this help message

Examples:
  $0                                   # Run basic patterns
  $0 --pattern WCP-1                 # Run WCP-1 (Sequence)
  $0 --pattern WCP-1,WCP-2,WCP-3    # Run multiple patterns
  $0 --category BASIC                # Run all basic patterns
  $0 --all --format html             # Run all patterns, generate HTML report
  $0 --pattern WCP-1 --format json   # WCP-1 with JSON output

Pattern Categories:
  BASIC        - Fundamental control flow (WCP-1 through WCP-6)
  BRANCHING    - Decision and synchronization patterns
  MULTI_INSTANCE - Multiple instance patterns
  STATE_BASED  - State-driven patterns
  DISTRIBUTED   - Distributed workflow patterns
  EVENT_DRIVEN - Event-based patterns
  AI_ML        - AI/ML integration patterns
  ENTERPRISE   - Enterprise patterns
  AGENT        - Agent-based patterns

EOF
}

# Function to check prerequisites
check_prerequisites() {
    print_color $BLUE "Checking prerequisites..."

    # Check if required JAR exists
    if [[ ! -f "$JAR_PATH" ]]; then
        print_color $RED "Error: JAR file not found at $JAR_PATH"
        print_color $YELLOW "Attempting to build the project..."
        if ! build_project; then
            print_color $RED "Build failed. Please check the project setup."
            exit 1
        fi
    fi

    # Check Java version
    if ! command -v java &> /dev/null; then
        print_color $RED "Error: Java is not installed or not in PATH"
        exit 1
    fi

    local java_version=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
    print_color $GREEN "Java version: $java_version"

    # Check if PatternDemoRunner class exists
    if ! java -cp "$CLASSPATH" "$MAIN_CLASS" --help > /dev/null 2>&1; then
        print_color $RED "Error: Cannot find PatternDemoRunner class or dependencies"
        print_color $YELLOW "Please ensure the project is properly built"
        exit 1
    fi

    print_color $GREEN "Prerequisites satisfied!"
}

# Function to build the project
build_project() {
    print_color $BLUE "Building yawl-mcp-a2a-app module..."

    # Try to build the project
    if mvn -DskipTests -Dmaven.test.skip=true -pl yawl-mcp-a2a-app package -Dmaven.javadoc.skip=true; then
        print_color $GREEN "Build completed successfully!"

        # Check if JAR was created
        if [[ ! -f "$JAR_PATH" ]]; then
            print_color $RED "Error: JAR file still not found after build"
            return 1
        fi

        return 0
    else
        print_color $RED "Build failed!"
        print_color $YELLOW "Please check the project setup and try again"
        return 1
    fi
}

# Function to parse command line arguments
parse_arguments() {
    local args=()

    while [[ $# -gt 0 ]]; do
        case $1 in
            -p|--pattern)
                shift
                PATTERNS="$1"
                ;;
            -c|--category)
                shift
                CATEGORY="$1"
                ;;
            -a|--all)
                ALL_PATTERNS=true
                ;;
            -t|--timeout)
                shift
                TIMEOUT="$1"
                ;;
            -f|--format)
                shift
                FORMAT="$1"
                ;;
            -o|--output)
                shift
                OUTPUT="$1"
                ;;
            --sequential)
                PARALLEL=false
                ;;
            --no-token-analysis)
                TOKEN_ANALYSIS=false
                ;;
            --with-commentary)
                WITH_COMMENTARY=true
                ;;
            -h|--help)
                print_usage
                exit 0
                ;;
            *)
                print_color $RED "Unknown option: $1"
                print_usage
                exit 1
                ;;
        esac
        shift
    done

    # Set defaults
    : "${PATTERNS:=$DEFAULT_PATTERNS}"
    : "${CATEGORY:=$DEFAULT_CATEGORY}"
    : "${TIMEOUT:=$DEFAULT_TIMEOUT}"
    : "${FORMAT:=$DEFAULT_FORMAT}"
    : "${OUTPUT:=$DEFAULT_OUTPUT}"
    : "${PARALLEL:=$DEFAULT_PARALLEL}"
    : "${TOKEN_ANALYSIS:=$DEFAULT_TOKEN_ANALYSIS}"
    : "${WITH_COMMENTARY:=$DEFAULT_WITH_COMMENTARY}"
}

# Function to build command arguments
build_command_args() {
    local cmd_args=()

    # Timeout
    cmd_args+=("--timeout" "$TIMEOUT")

    # Format
    cmd_args+=("--format" "$FORMAT")

    # Output file
    if [[ -n "$OUTPUT" ]]; then
        cmd_args+=("--output" "$OUTPUT")
    fi

    # Parallel execution
    if [[ "$PARALLEL" == "true" ]]; then
        cmd_args+=("--parallel")
    else
        cmd_args+=("--sequential")
    fi

    # Token analysis
    if [[ "$TOKEN_ANALYSIS" == "true" ]]; then
        cmd_args+=("--token-report")
    fi

    # Commentary
    if [[ "$WITH_COMMENTARY" == "true" ]]; then
        cmd_args+=("--with-commentary")
    fi

    # Pattern or category selection
    if [[ "$ALL_PATTERNS" == "true" ]]; then
        cmd_args+=("--all")
    elif [[ -n "$CATEGORY" ]]; then
        cmd_args+=("--category" "$CATEGORY")
    elif [[ -n "$PATTERNS" ]]; then
        cmd_args+=("--pattern" "$PATTERN_ARGS")
    fi

    echo "${cmd_args[@]}"
}

# Function to show progress
show_progress() {
    local pattern_id="$1"
    local status="$2"
    local duration="$3"

    if [[ "$status" == "SUCCESS" ]]; then
        print_color $GREEN "[$pattern_id] SUCCESS ($duration)"
    else
        print_color $RED "[$pattern_id] FAILED ($duration)"
    fi
}

# Function to build Java command with error handling
build_java_command() {
    local java_args=()
    local demo_args=()

    # Base Java arguments
    if command -v java &> /dev/null; then
        # Try different Java options if needed
        if java -version 2>&1 | grep -q "23"; then
            # Java 23 - enable preview features
            java_args+=("--enable-preview")
        fi
    fi

    # Classpath
    if [[ -f "$JAR_PATH" ]]; then
        java_args+=("-cp" "$JAR_PATH")
    else
        # Fall back to classpath from individual JARs
        java_args+=("-cp" "$CLASSPATH")
    fi

    # Main class
    java_args+=("$MAIN_CLASS")

    # Demo arguments
    demo_args+=("--timeout" "$TIMEOUT")
    demo_args+=("--format" "$FORMAT")

    if [[ -n "$OUTPUT" ]]; then
        demo_args+=("--output" "$OUTPUT")
    fi

    if [[ "$PARALLEL" == "true" ]]; then
        demo_args+=("--parallel")
    else
        demo_args+=("--sequential")
    fi

    if [[ "$TOKEN_ANALYSIS" == "true" ]]; then
        demo_args+=("--token-report")
    fi

    if [[ "$WITH_COMMENTARY" == "true" ]]; then
        demo_args+=("--with-commentary")
    fi

    # Pattern selection
    if [[ "$ALL_PATTERNS" == "true" ]]; then
        demo_args+=("--all")
    elif [[ -n "$CATEGORY" ]]; then
        demo_args+=("--category" "$CATEGORY")
    elif [[ -n "$PATTERNS" ]]; then
        demo_args+=("--pattern" "$PATTERNS")
    fi

    # Return as an array
    echo "${java_args[*]}" "${demo_args[*]}"
}

# Function to show progress
show_progress() {
    local pattern_id="$1"
    local status="$2"
    local duration="$3"

    if [[ "$status" == "SUCCESS" ]]; then
        print_color $GREEN "[$pattern_id] SUCCESS ($duration)"
    else
        print_color $RED "[$pattern_id] FAILED ($duration)"
    fi
}

# Main execution
main() {
    # Parse arguments
    parse_arguments "$@"

    # Print banner
    echo "======================================================================"
    print_color $BLUE "        YAWL Pattern Demo Runner - Van Der Aalst Patterns"
    echo "======================================================================"
    echo

    # Print what we're about to run
    if [[ "$ALL_PATTERNS" == "true" ]]; then
        echo "Running ALL patterns (43+)..."
    elif [[ -n "$CATEGORY" ]]; then
        echo "Running patterns in category: $CATEGORY"
    elif [[ -n "$PATTERNS" ]]; then
        echo "Running patterns: $PATTERNS"
    else
        echo "Running default patterns: $DEFAULT_PATTERNS"
    fi

    echo "Timeout per pattern: $TIMEOUT seconds"
    echo "Output format: $FORMAT"
    echo "Execution mode: $([[ "$PARALLEL" == "true" ]] && echo "parallel" || echo "sequential")"
    echo

    # Build arguments for the wrapper
    local wrapper_args=()

    # Pattern selection
    if [[ "$ALL_PATTERNS" == "true" ]]; then
        wrapper_args+=("--all")
    elif [[ -n "$CATEGORY" ]]; then
        wrapper_args+=("--category" "$CATEGORY")
    elif [[ -n "$PATTERNS" ]]; then
        wrapper_args+=("--pattern" "$PATTERNS")
    fi

    # Other options
    wrapper_args+=("--format" "$FORMAT")
    wrapper_args+=("--timeout" "$TIMEOUT")

    if [[ "$PARALLEL" == "false" ]]; then
        wrapper_args+=("--sequential")
    fi

    if [[ "$TOKEN_ANALYSIS" == "true" ]]; then
        wrapper_args+=("--token-report")
    fi

    if [[ "$WITH_COMMENTARY" == "true" ]]; then
        wrapper_args+=("--with-commentary")
    fi

    if [[ -n "$OUTPUT" ]]; then
        wrapper_args+=("--output" "$OUTPUT")
    fi

    # Execute the demo using the wrapper
    echo "Running pattern demo..."
    "$SCRIPT_DIR/pattern-demo-wrapper.sh" "${wrapper_args[@]}"
}

# Run main function with all arguments
main "$@"