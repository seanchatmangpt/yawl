#!/usr/bin/env bash
#
# Process Manager Library for Shell Testing
#
# Manages background processes for integration testing:
# - Start/stop services
# - Health checks
# - Cleanup on exit
# - Process groups
#
# Usage:
#   source scripts/shell-test/process-manager.sh
#   start_process "my-server" "java -jar server.jar" 8080
#   stop_process "my-server"

set -euo pipefail

# Colors
if [ "${NO_COLOR:-}" = "1" ] || [ ! -t 1 ]; then
    RED=""
    GREEN=""
    YELLOW=""
    BLUE=""
    NC=""
else
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    NC='\033[0m'
fi

# Process registry
declare -A PROCESS_REGISTRY
declare -A PROCESS_PIDS
declare -A PROCESS_PORTS

# Default startup timeout
PROCESS_TIMEOUT="${PROCESS_TIMEOUT:-30}"

# Initialize cleanup trap
_process_manager_init() {
    if [ -z "${PROCESS_MANAGER_INITIALIZED:-}" ]; then
        trap 'cleanup_all_processes' EXIT
        PROCESS_MANAGER_INITIALIZED=true
    fi
}

# Start a background process
# Usage: start_process <name> <command> [port] [working_dir]
start_process() {
    local name="$1"
    local command="$2"
    local port="${3:-}"
    local workdir="${4:-.}"

    _process_manager_init

    # Check if already running
    if [ -n "${PROCESS_PIDS[$name]:-}" ]; then
        if kill -0 "${PROCESS_PIDS[$name]}" 2>/dev/null; then
            echo -e "${YELLOW}Process '$name' already running (PID ${PROCESS_PIDS[$name]})${NC}"
            return 0
        fi
    fi

    echo -e "${BLUE}Starting process: $name${NC}"
    echo "  Command: $command"
    [ -n "$port" ] && echo "  Port: $port"
    echo "  Working directory: $workdir"

    # Create log file
    local log_file="/tmp/${name}.log"
    local pid_file="/tmp/${name}.pid"

    # Start process in background
    (
        cd "$workdir"
        exec $command > "$log_file" 2>&1
    ) &

    local pid=$!
    echo $pid > "$pid_file"

    # Register process
    PROCESS_PIDS[$name]=$pid
    PROCESS_REGISTRY[$name]="$command"
    [ -n "$port" ] && PROCESS_PORTS[$name]=$port

    echo -e "${GREEN}Started '$name' with PID $pid${NC}"
    echo "  Log file: $log_file"

    # Wait for port if specified
    if [ -n "$port" ]; then
        if ! wait_for_process_port "$name" "$PROCESS_TIMEOUT"; then
            echo -e "${RED}Failed to start '$name' - port $port not available${NC}"
            show_process_log "$name" 20
            stop_process "$name" 2>/dev/null || true
            return 1
        fi
    fi

    return 0
}

# Stop a process by name
# Usage: stop_process <name> [timeout]
stop_process() {
    local name="$1"
    local timeout="${2:-10}"

    local pid="${PROCESS_PIDS[$name]:-}"

    if [ -z "$pid" ]; then
        echo -e "${YELLOW}Process '$name' not found in registry${NC}"
        return 0
    fi

    if ! kill -0 "$pid" 2>/dev/null; then
        echo -e "${YELLOW}Process '$name' (PID $pid) already stopped${NC}"
        unset PROCESS_PIDS[$name]
        unset PROCESS_REGISTRY[$name]
        unset PROCESS_PORTS[$name]
        rm -f "/tmp/${name}.pid"
        return 0
    fi

    echo -e "${BLUE}Stopping process: $name (PID $pid)${NC}"

    # Send SIGTERM
    kill -TERM "$pid" 2>/dev/null || true

    # Wait for graceful shutdown
    local count=0
    while kill -0 "$pid" 2>/dev/null && [ $count -lt $timeout ]; do
        sleep 1
        count=$((count + 1))
    done

    # Force kill if still running
    if kill -0 "$pid" 2>/dev/null; then
        echo -e "${YELLOW}Force killing '$name' (PID $pid)${NC}"
        kill -KILL "$pid" 2>/dev/null || true
        sleep 1
    fi

    # Cleanup
    unset PROCESS_PIDS[$name]
    unset PROCESS_REGISTRY[$name]
    unset PROCESS_PORTS[$name]
    rm -f "/tmp/${name}.pid"

    echo -e "${GREEN}Stopped '$name'${NC}"
    return 0
}

# Kill process by port
# Usage: kill_process_on_port <port>
kill_process_on_port() {
    local port="$1"

    local pid
    pid=$(lsof -t -i :"$port" 2>/dev/null || true)

    if [ -n "$pid" ]; then
        echo -e "${YELLOW}Killing process $pid on port $port${NC}"
        kill -TERM "$pid" 2>/dev/null || true
        sleep 2
        kill -KILL "$pid" 2>/dev/null || true
    fi
}

# Wait for process to be ready on port
# Usage: wait_for_process_port <name> [timeout]
wait_for_process_port() {
    local name="$1"
    local timeout="${2:-30}"

    local port="${PROCESS_PORTS[$name]:-}"
    if [ -z "$port" ]; then
        echo -e "${YELLOW}No port registered for '$name'${NC}"
        return 0
    fi

    echo -n "Waiting for '$name' on port $port"
    local start
    start=$(date +%s)

    while true; do
        if nc -z localhost "$port" 2>/dev/null; then
            echo -e " ${GREEN}OK${NC}"
            return 0
        fi

        local elapsed=$(($(date +%s) - start))
        if [ $elapsed -ge $timeout ]; then
            echo -e " ${RED}TIMEOUT${NC}"
            return 1
        fi

        # Check if process died
        local pid="${PROCESS_PIDS[$name]:-}"
        if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
            echo -e " ${RED}DIED${NC}"
            return 1
        fi

        echo -n "."
        sleep 1
    done
}

# Wait for process HTTP health check
# Usage: wait_for_process_health <name> <path> [timeout]
wait_for_process_health() {
    local name="$1"
    local path="$2"
    local timeout="${3:-30}"

    local port="${PROCESS_PORTS[$name]:-}"
    if [ -z "$port" ]; then
        echo -e "${RED}No port registered for '$name'${NC}"
        return 1
    fi

    local url="http://localhost:$port$path"

    echo -n "Waiting for '$name' health at $path"
    local start
    start=$(date +%s)

    while true; do
        local code
        code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null) || code="000"

        if [ "$code" = "200" ]; then
            echo -e " ${GREEN}OK${NC}"
            return 0
        fi

        local elapsed=$(($(date +%s) - start))
        if [ $elapsed -ge $timeout ]; then
            echo -e " ${RED}TIMEOUT${NC}"
            return 1
        fi

        echo -n "."
        sleep 1
    done
}

# Check if process is running
# Usage: is_process_running <name>
is_process_running() {
    local name="$1"
    local pid="${PROCESS_PIDS[$name]:-}"

    if [ -z "$pid" ]; then
        return 1
    fi

    kill -0 "$pid" 2>/dev/null
}

# Get process PID
# Usage: get_process_pid <name>
get_process_pid() {
    local name="$1"
    echo "${PROCESS_PIDS[$name]:-}"
}

# Get process port
# Usage: get_process_port <name>
get_process_port() {
    local name="$1"
    echo "${PROCESS_PORTS[$name]:-}"
}

# Show process log
# Usage: show_process_log <name> [lines]
show_process_log() {
    local name="$1"
    local lines="${2:-50}"

    local log_file="/tmp/${name}.log"

    if [ -f "$log_file" ]; then
        echo -e "${BLUE}=== Log for '$name' (last $lines lines) ===${NC}"
        tail -n "$lines" "$log_file"
        echo -e "${BLUE}======================================${NC}"
    else
        echo -e "${YELLOW}No log file found for '$name'${NC}"
    fi
}

# Follow process log
# Usage: follow_process_log <name>
follow_process_log() {
    local name="$1"
    local log_file="/tmp/${name}.log"

    if [ -f "$log_file" ]; then
        tail -f "$log_file"
    else
        echo -e "${RED}No log file found for '$name'${NC}"
        return 1
    fi
}

# List all registered processes
# Usage: list_processes
list_processes() {
    echo -e "${BLUE}Registered Processes:${NC}"
    for name in "${!PROCESS_PIDS[@]}"; do
        local pid="${PROCESS_PIDS[$name]}"
        local port="${PROCESS_PORTS[$name]:-}"
        local status

        if kill -0 "$pid" 2>/dev/null; then
            status="${GREEN}running${NC}"
        else
            status="${RED}stopped${NC}"
        fi

        echo "  $name:"
        echo "    PID: $pid"
        echo "    Port: ${port:-N/A}"
        echo -e "    Status: $status"
    done
}

# Cleanup all registered processes
# Usage: cleanup_all_processes
cleanup_all_processes() {
    echo ""
    echo -e "${BLUE}Cleaning up processes...${NC}"

    for name in "${!PROCESS_PIDS[@]}"; do
        stop_process "$name" 5 2>/dev/null || true
    done

    echo -e "${GREEN}Cleanup complete${NC}"
}

# Start Java process
# Usage: start_java_process <name> <class> <port> [jvm_args]
start_java_process() {
    local name="$1"
    local class="$2"
    local port="$3"
    local jvm_args="${4:-}"

    local command="java $jvm_args -cp classes $class"
    start_process "$name" "$command" "$port"
}

# Start Node.js process
# Usage: start_node_process <name> <script> <port>
start_node_process() {
    local name="$1"
    local script="$2"
    local port="$3"

    start_process "$name" "node $script" "$port"
}

# Start Python process
# Usage: start_python_process <name> <script> <port>
start_python_process() {
    local name="$1"
    local script="$2"
    local port="$3"

    start_process "$name" "python3 $script" "$port"
}

# Ensure port is available before starting
# Usage: ensure_port_available <port>
ensure_port_available() {
    local port="$1"

    if nc -z localhost "$port" 2>/dev/null; then
        echo -e "${YELLOW}Port $port is in use, attempting to free it...${NC}"
        kill_process_on_port "$port"
        sleep 2

        if nc -z localhost "$port" 2>/dev/null; then
            echo -e "${RED}Port $port is still in use${NC}"
            return 1
        fi
    fi

    return 0
}
