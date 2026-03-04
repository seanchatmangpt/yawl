#!/bin/bash
#
# YAWL Erlang Bridge - BEAM Node Startup Script
#
# This script starts a BEAM node with Unix domain socket transport
# for JVM↔BEAM communication.
#

set -euo pipefail

# Configuration
NODE_NAME="yawl_beam@localhost"
COOKIE=${YAWL_ERLANG_COOKIE:-"yawl-cookie"}
EBIN_DIR="ebin"
ERL_FLAGS="-proto_dist local -setcookie $COOKIE -pa $EBIN_DIR"
LOG_DIR="/tmp/yawl-erlang"
LOG_FILE="$LOG_DIR/beam-bridge.log"

# Create log directory if it doesn't exist
mkdir -p "$LOG_DIR"

# Function to check if Erlang is installed
check_erlang() {
    if ! command -v erl &> /dev/null; then
        echo "Error: Erlang is not installed or not in PATH"
        echo "Please install Erlang/OTP from https://www.erlang.org/downloads"
        exit 1
    fi
}

# Function to check if node is already running
is_node_running() {
    local node_name="$1"
    if pgrep -f "erl.*-name.*$node_name" > /dev/null; then
        return 0
    else
        return 1
    fi
}

# Function to start BEAM node
start_node() {
    local node_name="$1"
    local cookie="$2"

    echo "Starting BEAM node: $node_name"
    echo "Cookie: $cookie"
    echo "Using Unix domain socket transport"
    echo "Log file: $LOG_FILE"
    echo ""

    # Start BEAM node with local distribution protocol
    erl -name "$node_name" \
        $ERL_FLAGS \
        -s process_mining_sup start_link \
        -noinput \
        -detached \
        > "$LOG_FILE" 2>&1

    if [ $? -eq 0 ]; then
        echo "BEAM node started successfully"
        echo "Node name: $node_name"
        echo "Protocol: local (Unix domain socket)"
        echo "Log file: $LOG_FILE"

        # Wait a moment for node to initialize
        sleep 2

        # Check if node is running
        if is_node_running "$node_name"; then
            echo "Node is running and ready for connections"
            return 0
        else
            echo "Warning: Node startup may have failed, check log file: $LOG_FILE"
            return 1
        fi
    else
        echo "Failed to start BEAM node"
        echo "Check log file: $LOG_FILE"
        exit 1
    fi
}

# Function to show status
show_status() {
    local node_name="$1"

    if is_node_running "$node_name"; then
        echo "BEAM node is running: $node_name"
        echo "PID: $(pgrep -f "erl.*-name.*$node_name")"
        return 0
    else
        echo "BEAM node is not running: $node_name"
        return 1
    fi
}

# Function to stop node
stop_node() {
    local node_name="$1"

    if is_node_running "$node_name"; then
        echo "Stopping BEAM node: $node_name"
        # Stop the node gracefully
        erl -name "$node_name" \
            -setcookie "$COOKIE" \
            -eval "erlang:halt(), init:stop()" \
            -noshell \
            -detached

        # Wait for process to terminate
        sleep 2

        if is_node_running "$node_name"; then
            echo "Force stopping node"
            pkill -f "erl.*-name.*$node_name"
            sleep 2
        fi

        if is_node_running "$node_name"; then
            echo "Error: Failed to stop node"
            exit 1
        else
            echo "Node stopped successfully"
        fi
    else
        echo "Node is not running"
    fi
}

# Main script logic
main() {
    case "${1:-start}" in
        start)
            check_erlang
            if is_node_running "$NODE_NAME"; then
                echo "BEAM node is already running: $NODE_NAME"
                exit 0
            fi
            start_node "$NODE_NAME" "$COOKIE"
            ;;
        stop)
            stop_node "$NODE_NAME"
            ;;
        restart)
            stop_node "$NODE_NAME"
            sleep 1
            start_node "$NODE_NAME" "$COOKIE"
            ;;
        status)
            show_status "$NODE_NAME"
            ;;
        log)
            if [ -f "$LOG_FILE" ]; then
                echo "=== BEAM Node Log ==="
                tail -f "$LOG_FILE"
            else
                echo "Log file not found: $LOG_FILE"
                exit 1
            fi
            ;;
        *)
            echo "Usage: $0 {start|stop|restart|status|log}"
            echo ""
            echo "Commands:"
            echo "  start   - Start the BEAM node (default)"
            echo "  stop    - Stop the BEAM node"
            echo "  restart - Restart the BEAM node"
            echo "  status  - Show node status"
            echo "  log     - Follow node log output"
            echo ""
            echo "Environment variables:"
            echo "  YAWL_ERLANG_COOKIE - Erlang cookie (default: yawl-cookie)"
            exit 1
            ;;
    esac
}

main "$@"