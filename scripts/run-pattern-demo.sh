#!/bin/bash

# Pattern Demo Runner - Standalone Shell Script
# Bypasses Spring Boot JAR classloading issues by using Maven exec:java
# Usage: ./run-pattern-demo.sh [OPTIONS]

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
MAVEN_MODULE="yawl-mcp-a2a-app"
MAIN_CLASS="org.yawlfoundation.yawl.mcp.a2a.demo.PatternDemoRunner"

# Colors for console output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
PATTERN=""
CATEGORY=""
ALL=false
FORMAT="console"
VERBOSE=false
FAIL_FAST=false

# Help message
show_help() {
    cat << EOF
Pattern Demo Runner - Standalone Shell Script

Runs the PatternDemoRunner class using Maven exec:java, bypassing Spring Boot JAR classloading issues.

USAGE:
    ./run-pattern-demo.sh [OPTIONS]

OPTIONS:
    -p, --pattern PATTERN     Run specific pattern (e.g., WCP-1, WCP-2)
    -c, --category CATEGORY   Run all patterns in category (e.g., BASIC, ADVANCED)
    -a, --all                Run all patterns
    -f, --format FORMAT      Output format: console|json|markdown (default: console)
    -v, --verbose            Enable verbose output
    -x, --fail-fast          Stop on first failure
    -h, --help               Show this help message

EXAMPLES:
    ./run-pattern-demo.sh --all
    ./run-pattern-demo.sh --pattern WCP-1
    ./run-pattern-demo.sh --category BASIC --format json
    ./run-pattern-demo.sh --all --format markdown --verbose

EOF
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -p|--pattern)
                PATTERN="$2"
                shift 2
                ;;
            -c|--category)
                CATEGORY="$2"
                shift 2
                ;;
            -a|--all)
                ALL=true
                shift
                ;;
            -f|--format)
                FORMAT="$2"
                case $FORMAT in
                    console|json|markdown) ;;
                    *)
                        echo -e "${RED}Error: Invalid format '$FORMAT'. Must be console|json|markdown${NC}" >&2
                        exit 1
                        ;;
                esac
                shift 2
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -x|--fail-fast)
                FAIL_FAST=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                echo -e "${RED}Error: Unknown option '$1'${NC}" >&2
                echo "Use --help for usage information."
                exit 1
                ;;
        esac
    done
}

# Validate arguments
validate_args() {
    local has_pattern=false
    local has_category=false

    if [[ -n "$PATTERN" ]]; then
        has_pattern=true
    fi

    if [[ -n "$CATEGORY" ]]; then
        has_category=true
    fi

    
    # Check if multiple options are selected
    local options_selected=0
    if [[ "$has_pattern" == true ]]; then
        options_selected=$((options_selected + 1))
    fi
    if [[ "$has_category" == true ]]; then
        options_selected=$((options_selected + 1))
    fi
    if [[ "$ALL" == true ]]; then
        options_selected=$((options_selected + 1))
    fi

    
    if [[ $options_selected -gt 1 ]]; then
        echo -e "${RED}Error: Cannot specify multiple options. Choose one: --pattern, --category, or --all${NC}" >&2
        show_help
        exit 1
    fi

    if [[ $options_selected -eq 0 ]]; then
        echo -e "${RED}Error: Must specify one of: --pattern, --category, or --all${NC}" >&2
        show_help
        exit 1
    fi
}

# Build Maven arguments
build_maven_args() {
    local args=()

    # Main class
    args+=("-Dexec.mainClass=$MAIN_CLASS")

    # Command line arguments
    local exec_args=()

    if [[ -n "$PATTERN" ]]; then
        exec_args+=("--pattern=\"$PATTERN\"")
        if [[ "$VERBOSE" == true ]]; then
            echo -e "${BLUE}Running pattern: $PATTERN${NC}"
        fi
    fi

    if [[ -n "$CATEGORY" ]]; then
        exec_args+=("--category=\"$CATEGORY\"")
        if [[ "$VERBOSE" == true ]]; then
            echo -e "${BLUE}Running category: $CATEGORY${NC}"
        fi
    fi

    if [[ "$ALL" == true ]]; then
        exec_args+=("--all")
        if [[ "$VERBOSE" == true ]]; then
            echo -e "${BLUE}Running all patterns${NC}"
        fi
    fi

    exec_args+=("--format=\"$FORMAT\"")

    if [[ "$FAIL_FAST" == true ]]; then
        exec_args+=("--fail-fast")
    fi

    # Join arguments
    args+=("-Dexec.args=${exec_args[*]}")

    echo "${args[@]}"
}

# Check prerequisites
check_prerequisites() {
    # Check if Maven is installed
    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}Error: Maven is not installed or not in PATH${NC}" >&2
        exit 1
    fi

    # Check if we're in the project root
    if [[ ! -f "$PROJECT_ROOT/pom.xml" ]]; then
        echo -e "${RED}Error: pom.xml not found. Run this script from the project root or make sure it's accessible${NC}" >&2
        exit 1
    fi

    # Check if the target module exists
    if [[ ! -d "$PROJECT_ROOT/yawl-mcp-a2a-app" ]]; then
        echo -e "${RED}Error: Module $MAVEN_MODULE not found${NC}" >&2
        exit 1
    fi

    if [[ "$VERBOSE" == true ]]; then
        echo -e "${GREEN}✓ Maven found$(mvn -version | head -n1)${NC}"
        echo -e "${GREEN}✓ Project root: $PROJECT_ROOT${NC}"
        echo -e "${GREEN}✓ Module found: $MAVEN_MODULE${NC}"
    fi
}

# Run the pattern demo
run_pattern_demo() {
    local maven_args
    maven_args=$(build_maven_args)

    if [[ "$VERBOSE" == true ]]; then
        echo -e "${YELLOW}Building Maven command...${NC}"
        echo "mvn exec:java -pl $MAVEN_MODULE ${maven_args[*]}"
        echo
    fi

    echo -e "${BLUE}Running Pattern Demo Runner...${NC}"
    echo "Format: $FORMAT"
    [[ "$FAIL_FAST" == true ]] && echo -e "${YELLOW}Fail-fast enabled${NC}"
    echo

    # Change to project root and run Maven
    cd "$PROJECT_ROOT"

    # Capture exit code
    if mvn exec:java -pl "$MAVEN_MODULE" $maven_args; then
        echo
        echo -e "${GREEN}✓ Pattern demo completed successfully${NC}"
        exit 0
    else
        local exit_code=$?
        echo
        echo -e "${RED}✗ Pattern demo failed with exit code $exit_code${NC}" >&2
        exit $exit_code
    fi
}

# Main execution
main() {
    echo "Pattern Demo Runner"
    echo "==================="
    echo

    # Parse arguments
    parse_args "$@"

    # Validate arguments
    validate_args

    # Check prerequisites
    check_prerequisites

    # Run the demo
    run_pattern_demo
}

# Entry point
main "$@"