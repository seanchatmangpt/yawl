#!/bin/bash

#
# YAWL Integration Examples - Test Runner
#
# This script compiles and runs all MCP/A2A integration examples.
# It checks prerequisites and provides helpful error messages.
#
# Usage:
#   ./run-examples.sh [example-name]
#
# Examples:
#   ./run-examples.sh                    # Run all examples
#   ./run-examples.sh mcp-server         # Run only MCP server example
#   ./run-examples.sh ai-agent           # Run AI agent example
#

set -e  # Exit on error

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
YAWL_ROOT="/home/user/yawl"
ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080/yawl/ib}"
EXAMPLES_DIR="$YAWL_ROOT/exampleSpecs/orderfulfillment/integration_examples"
CLASSPATH="$YAWL_ROOT/classes:$YAWL_ROOT/build/3rdParty/lib/*:$YAWL_ROOT/build/3rdParty/lib/hibernate/*"

# Print colored message
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "\n${GREEN}========================================${NC}"
    echo -e "${GREEN}$1${NC}"
    echo -e "${GREEN}========================================${NC}\n"
}

# Check prerequisites
check_prerequisites() {
    print_header "Checking Prerequisites"

    # Check Java
    if ! command -v java &> /dev/null; then
        print_error "Java not found. Install Java 21 or higher."
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
    if [ "$JAVA_VERSION" -lt 21 ]; then
        print_error "Java 21+ required. Current version: $JAVA_VERSION"
        exit 1
    fi
    print_success "Java $JAVA_VERSION found"

    # Check YAWL Engine
    print_info "Checking YAWL Engine at $ENGINE_URL..."
    if curl -s --connect-timeout 5 "$ENGINE_URL" > /dev/null 2>&1; then
        print_success "YAWL Engine is running"
    else
        print_warning "YAWL Engine not reachable at $ENGINE_URL"
        print_warning "Start engine with: docker-compose up -d yawl-engine"
        print_warning "Or set YAWL_ENGINE_URL environment variable"
    fi

    # Check Z.AI API Key (optional)
    if [ -z "$ZAI_API_KEY" ]; then
        print_warning "ZAI_API_KEY not set. AI features will be limited."
        print_info "Set with: export ZAI_API_KEY=your_key_here"
    else
        print_success "ZAI_API_KEY is set"
    fi

    # Check compiled classes
    if [ ! -d "$YAWL_ROOT/classes/org/yawlfoundation/yawl/engine" ]; then
        print_warning "YAWL classes not compiled. Compiling now..."
        compile_yawl
    else
        print_success "YAWL classes found"
    fi
}

# Compile YAWL
compile_yawl() {
    print_header "Compiling YAWL"

    cd "$YAWL_ROOT"

    if [ -f "build/build.xml" ]; then
        print_info "Using Ant to compile..."
        ant -f build/build.xml compile
        print_success "YAWL compiled successfully"
    else
        print_error "build.xml not found. Cannot compile."
        exit 1
    fi
}

# Compile examples
compile_examples() {
    print_header "Compiling Integration Examples"

    cd "$EXAMPLES_DIR"

    print_info "Compiling Java files..."

    javac -cp "$CLASSPATH" -d "$YAWL_ROOT/classes" *.java

    if [ $? -eq 0 ]; then
        print_success "Examples compiled successfully"
    else
        print_error "Compilation failed"
        exit 1
    fi
}

# Run example
run_example() {
    local EXAMPLE_CLASS=$1
    local EXAMPLE_NAME=$2

    print_header "Running: $EXAMPLE_NAME"

    java -cp "$CLASSPATH" \
        "org.yawlfoundation.yawl.examples.integration.$EXAMPLE_CLASS"

    if [ $? -eq 0 ]; then
        print_success "$EXAMPLE_NAME completed"
    else
        print_error "$EXAMPLE_NAME failed"
    fi
}

# Run all examples
run_all_examples() {
    run_example "McpServerExample" "MCP Server Example"
    echo ""
    run_example "McpClientExample" "MCP Client Example"
    echo ""
    run_example "A2aServerExample" "A2A Server Example"
    echo ""
    run_example "A2aClientExample" "A2A Client Example"
    echo ""
    run_example "OrderFulfillmentIntegration" "Order Fulfillment Integration"
    echo ""
    run_example "AiAgentExample" "AI Agent Example"
}

# Main execution
main() {
    local EXAMPLE_CHOICE="${1:-all}"

    print_header "YAWL Integration Examples"
    print_info "YAWL Root: $YAWL_ROOT"
    print_info "Engine URL: $ENGINE_URL"
    print_info "Examples: $EXAMPLES_DIR"
    echo ""

    check_prerequisites
    compile_examples

    case "$EXAMPLE_CHOICE" in
        "all")
            run_all_examples
            ;;
        "mcp-server")
            run_example "McpServerExample" "MCP Server Example"
            ;;
        "mcp-client")
            run_example "McpClientExample" "MCP Client Example"
            ;;
        "a2a-server")
            run_example "A2aServerExample" "A2A Server Example"
            ;;
        "a2a-client")
            run_example "A2aClientExample" "A2A Client Example"
            ;;
        "order-fulfillment")
            run_example "OrderFulfillmentIntegration" "Order Fulfillment Integration"
            ;;
        "ai-agent")
            run_example "AiAgentExample" "AI Agent Example"
            ;;
        *)
            print_error "Unknown example: $EXAMPLE_CHOICE"
            echo ""
            echo "Available examples:"
            echo "  all               - Run all examples (default)"
            echo "  mcp-server        - MCP Server Example"
            echo "  mcp-client        - MCP Client Example"
            echo "  a2a-server        - A2A Server Example"
            echo "  a2a-client        - A2A Client Example"
            echo "  order-fulfillment - Complete Order Fulfillment"
            echo "  ai-agent          - AI Agent for Approvals"
            exit 1
            ;;
    esac

    print_header "All Examples Complete!"
    print_info "Review output above for results"
    print_info "Check README.md for troubleshooting tips"
}

# Run main
main "$@"
