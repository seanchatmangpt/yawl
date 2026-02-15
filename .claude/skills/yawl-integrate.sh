#!/bin/bash
# YAWL Integration Skill - Claude Code 2026 Best Practices
# Usage: /yawl-integrate [--mcp|--a2a|--status|--stop]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_usage() {
    cat << 'EOF'
YAWL Integration Skill - Manage MCP/A2A integrations

Usage: /yawl-integrate [option]

Options:
  --status      Check integration status (default)
  --mcp         Start MCP server integration
  --a2a         Start A2A server integration
  --stop        Stop running integrations
  -h, --help    Show this help message

Environment Variables:
  ZHIPU_API_KEY     Z.AI API key for MCP integration
  A2A_SERVER_PORT   Port for A2A server (default: 8082)

Examples:
  /yawl-integrate --status
  /yawl-integrate --mcp
  /yawl-integrate --a2a
  /yawl-integrate --stop
EOF
}

# Parse arguments
ACTION="status"

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        --mcp)
            ACTION="mcp"
            shift
            ;;
        --a2a)
            ACTION="a2a"
            shift
            ;;
        --status)
            ACTION="status"
            shift
            ;;
        --stop)
            ACTION="stop"
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

check_environment() {
    echo -e "${BLUE}[yawl-integrate] Checking integration environment...${NC}"
    echo ""

    # Check Java
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
        echo -e "  ${GREEN}Java ${JAVA_VERSION} installed${NC}"
    else
        echo -e "  ${RED}Java not found${NC}"
    fi

    # Check ZHIPU_API_KEY
    if [[ -n "${ZHIPU_API_KEY:-}" ]]; then
        echo -e "  ${GREEN}ZHIPU_API_KEY is set${NC}"
    else
        echo -e "  ${YELLOW}ZHIPU_API_KEY not set (required for MCP)${NC}"
    fi

    # Check for running processes
    echo ""
    echo "Running integrations:"

    if pgrep -f "YawlMCPServer" > /dev/null 2>&1; then
        echo -e "  ${GREEN}MCP Server: running${NC}"
    else
        echo -e "  ${YELLOW}MCP Server: not running${NC}"
    fi

    if pgrep -f "YawlA2AServer" > /dev/null 2>&1; then
        echo -e "  ${GREEN}A2A Server: running${NC}"
    else
        echo -e "  ${YELLOW}A2A Server: not running${NC}"
    fi
}

start_mcp() {
    echo -e "${BLUE}[yawl-integrate] Starting MCP Server...${NC}"

    if [[ -z "${ZHIPU_API_KEY:-}" ]]; then
        echo -e "${RED}Error: ZHIPU_API_KEY environment variable is required${NC}"
        echo "Set it with: export ZHIPU_API_KEY=your_key_here"
        exit 1
    fi

    cd "${PROJECT_ROOT}"

    if [[ -x "./run-mcp-server.sh" ]]; then
        ./run-mcp-server.sh
    else
        echo -e "${YELLOW}run-mcp-server.sh not found, using direct Java execution${NC}"
        java -cp "classes:build/3rdParty/lib/*" org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
    fi
}

start_a2a() {
    echo -e "${BLUE}[yawl-integrate] Starting A2A Server...${NC}"

    cd "${PROJECT_ROOT}"

    if [[ -x "./run-a2a-server.sh" ]]; then
        ./run-a2a-server.sh
    else
        echo -e "${YELLOW}run-a2a-server.sh not found, using direct Java execution${NC}"
        java -cp "classes:build/3rdParty/lib/*" org.yawlfoundation.yawl.integration.a2a.YawlA2AServer
    fi
}

stop_integrations() {
    echo -e "${BLUE}[yawl-integrate] Stopping integration servers...${NC}"

    if pgrep -f "YawlMCPServer" > /dev/null 2>&1; then
        pkill -f "YawlMCPServer"
        echo -e "  ${GREEN}MCP Server stopped${NC}"
    fi

    if pgrep -f "YawlA2AServer" > /dev/null 2>&1; then
        pkill -f "YawlA2AServer"
        echo -e "  ${GREEN}A2A Server stopped${NC}"
    fi

    echo -e "${GREEN}[yawl-integrate] All integrations stopped${NC}"
}

# Execute action
case "${ACTION}" in
    status)
        check_environment
        ;;
    mcp)
        start_mcp
        ;;
    a2a)
        start_a2a
        ;;
    stop)
        stop_integrations
        ;;
esac
