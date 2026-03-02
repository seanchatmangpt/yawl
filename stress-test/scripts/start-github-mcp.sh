#!/bin/bash

# YAWL GitHub MCP Server Startup Script
#
# This script starts the GitHub MCP server with proper configuration
# and environment setup.

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
JAVA_MAIN="org.yawlfoundation.yawl.integration.mcp.github.GitHubMcpDemo"
JAVA_OPTS="-Xmx512m -Xms256m"
SERVER_PORT=8083

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    local level=$1
    shift
    local message="$*"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')

    case $level in
        "INFO")
            echo -e "${GREEN}[INFO]${NC} [$timestamp] $message"
            ;;
        "WARN")
            echo -e "${YELLOW}[WARN]${NC} [$timestamp] $message"
            ;;
        "ERROR")
            echo -e "${RED}[ERROR]${NC} [$timestamp] $message"
            ;;
        *)
            echo -e "${BLUE}[LOG]${NC} [$timestamp] $message"
            ;;
    esac
}

# Check if Java is available
check_java() {
    if ! command -v java &> /dev/null; then
        log "ERROR" "Java is not installed or not in PATH"
        exit 1
    fi

    local java_version=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$java_version" -lt 21 ]; then
        log "WARN" "Java version might be too old. Java 21+ recommended."
    fi

    log "INFO" "Java version: $(java -version 2>&1 | head -n1)"
}

# Check if port is available
check_port() {
    local port=$1

    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null; then
        log "ERROR" "Port $port is already in use"
        exit 1
    fi

    log "INFO" "Port $port is available"
}

# Load configuration
load_config() {
    local config_file="$PROJECT_ROOT/src/main/resources/application.yml"

    if [ -f "$config_file" ]; then
        log "INFO" "Loading configuration from $config_file"
        # Extract values using grep (simplified)
        GITHUB_ACCESS_TOKEN=$(grep "access-token:" "$config_file" | head -1 | cut -d':' -f2 | tr -d ' "')
        GITHUB_DEFAULT_REPO=$(grep "default-repo:" "$config_file" | head -1 | cut -d':' -f2 | tr -d ' "')
        SERVER_PORT=$(grep "port:" "$config_file" | head -1 | cut -d':' -f2 | tr -d ' ')
    else
        log "WARN" "Configuration file not found, using defaults"
    fi

    # Override with environment variables if set
    GITHUB_ACCESS_TOKEN="${GITHUB_ACCESS_TOKEN:-}"
    GITHUB_DEFAULT_REPO="${GITHUB_DEFAULT_REPO:-}"
    SERVER_PORT="${SERVER_PORT:-8083}"
}

# Check GitHub configuration
check_github_config() {
    if [ -z "$GITHUB_ACCESS_TOKEN" ]; then
        log "WARN" "GITHUB_ACCESS_TOKEN not set. Demo will run in limited mode."
        log "WARN" "Set the environment variable: export GITHUB_ACCESS_TOKEN=your_token_here"
    fi

    if [ -z "$GITHUB_DEFAULT_REPO" ]; then
        log "WARN" "GITHUB_DEFAULT_REPO not set. Using default 'yawlfoundation/yawl'"
        GITHUB_DEFAULT_REPO="yawlfoundation/yawl"
    fi

    log "INFO" "GitHub repository: $GITHUB_DEFAULT_REPO"
}

# Build the project if needed
build_project() {
    if [ ! -d "$PROJECT_ROOT/target/classes" ] || [ ! -f "$PROJECT_ROOT/target/classes/org/yawlfoundation/yawl/integration/mcp/github/GitHubMcpServer.class" ]; then
        log "INFO" "Building project..."
        cd "$PROJECT_ROOT"

        if ! mvn clean compile -q -DskipTests; then
            log "ERROR" "Failed to build project"
            exit 1
        fi

        log "INFO" "Project built successfully"
        cd "$SCRIPT_DIR"
    fi
}

# Start the server
start_server() {
    log "INFO" "Starting YAWL GitHub MCP Server..."
    log "INFO" "Server will run on port $SERVER_PORT"

    # Set classpath
    local classpath="$PROJECT_ROOT/target/classes"

    # Start server in background
    cd "$PROJECT_ROOT"

    # Set environment variables
    export GITHUB_ACCESS_TOKEN
    export GITHUB_DEFAULT_REPO
    export GITHUB_MCP_PORT=$SERVER_PORT

    # Start Java process
    if [ -n "$GITHUB_ACCESS_TOKEN" ]; then
        java $JAVA_OPTS -cp "$classpath" $JAVA_MAIN &
    else
        java $JAVA_OPTS -cp "$classpath" $JAVA_MAIN &
    fi

    local server_pid=$!
    echo $server_pid > "$PROJECT_ROOT/.github-mcp-pid"

    log "INFO" "GitHub MCP Server started with PID: $server_pid"
    log "INFO" "Use 'kill $server_pid' to stop the server"
    log "INFO" "Use 'tail -f $PROJECT_ROOT/logs/github-mcp-server.log' to view logs"

    # Check if server started successfully
    sleep 2
    if ! lsof -Pi :$SERVER_PORT -sTCP:LISTEN -t >/dev/null; then
        log "ERROR" "Failed to start server on port $SERVER_PORT"
        exit 1
    fi

    log "INFO" "Server is running successfully!"
}

# Stop the server
stop_server() {
    local pid_file="$PROJECT_ROOT/.github-mcp-pid"

    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        log "INFO" "Stopping GitHub MCP Server (PID: $pid)..."

        # Kill the process
        if kill -0 $pid 2>/dev/null; then
            kill $pid

            # Wait for process to exit
            for i in {1..10}; do
                if ! kill -0 $pid 2>/dev/null; then
                    log "INFO" "Server stopped successfully"
                    rm -f "$pid_file"
                    return 0
                fi
                sleep 1
            done

            # Force kill if still running
            if kill -0 $pid 2>/dev/null; then
                log "WARN" "Force killing server..."
                kill -9 $pid
                rm -f "$pid_file"
            fi
        else
            log "WARN" "Server process $pid not found"
            rm -f "$pid_file"
        fi
    else
        log "WARN" "No server PID file found"
    fi
}

# Show status
show_status() {
    local pid_file="$PROJECT_ROOT/.github-mcp-pid"

    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 $pid 2>/dev/null; then
            log "INFO" "GitHub MCP Server is running (PID: $pid)"
            log "INFO" "Port: $SERVER_PORT"
            log "INFO" "Config repo: ${GITHUB_DEFAULT_REPO:-'Not set'}"

            # Show recent logs
            if [ -f "$PROJECT_ROOT/logs/github-mcp-server.log" ]; then
                echo -e "${BLUE}Recent logs:${NC}"
                tail -5 "$PROJECT_ROOT/logs/github-mcp-server.log"
            fi
        else
            log "ERROR" "Server process $pid is not running"
        fi
    else
        log "WARN" "GitHub MCP Server is not running"
    fi
}

# Main execution
main() {
    case "${1:-start}" in
        "start")
            check_java
            check_port $SERVER_PORT
            load_config
            check_github_config
            build_project
            start_server
            ;;
        "stop")
            stop_server
            ;;
        "restart")
            stop_server
            sleep 1
            check_java
            check_port $SERVER_PORT
            load_config
            check_github_config
            build_project
            start_server
            ;;
        "status")
            show_status
            ;;
        "logs")
            if [ -f "$PROJECT_ROOT/logs/github-mcp-server.log" ]; then
                tail -f "$PROJECT_ROOT/logs/github-mcp-server.log"
            else
                log "WARN" "Log file not found"
            fi
            ;;
        "help"|"-h"|"--help")
            echo "YAWL GitHub MCP Server Control Script"
            echo ""
            echo "Usage: $0 [command]"
            echo ""
            echo "Commands:"
            echo "  start     - Start the GitHub MCP server"
            echo "  stop      - Stop the GitHub MCP server"
            echo "  restart   - Restart the GitHub MCP server"
            echo "  status    - Show server status"
            echo "  logs      - Follow server logs"
            echo "  help      - Show this help message"
            echo ""
            echo "Environment variables:"
            echo "  GITHUB_ACCESS_TOKEN    - GitHub personal access token"
            echo "  GITHUB_DEFAULT_REPO    - Default repository (owner/repo)"
            echo "  GITHUB_MCP_PORT        - Server port (default: 8083)"
            ;;
        *)
            log "ERROR" "Unknown command: $1"
            echo "Use '$0 help' for usage information"
            exit 1
            ;;
    esac
}

# Run main function
main "$@"