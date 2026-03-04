#!/bin/bash

# YAWL BEAM Bridge Startup Script
#
# This script starts the BEAM node with Unix domain socket transport
# for JVM↔BEAM boundary communication.
#
# Usage: ./start-beam-bridge.sh [options]
#
# Options:
#   -n, --node-name NODE_NAME     Erlang node name (default: yaws_bridge@localhost)
#   -c, --cookie COOKIE           Erlang cookie (default: yawl)
#   -d, --directory DIR           Socket directory (default: /tmp/yawl-erlang)
#   -p, --port PORT               EPMD port (default: 4369)
#   -v, --verbose                 Verbose output
#   -h, --help                   Show this help message

set -euo pipefail

# Default configuration
NODE_NAME="yaws_bridge@localhost"
COOKIE="yawl"
SOCKET_DIR="/tmp/yawl-erlang"
EPMD_PORT=4369
VERBOSE=false

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
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

# Function to show usage
show_usage() {
    echo "Usage: $0 [options]"
    echo ""
    echo "Options:"
    echo "  -n, --node-name NODE_NAME     Erlang node name (default: yaws_bridge@localhost)"
    echo "  -c, --cookie COOKIE           Erlang cookie (default: yawl)"
    echo "  -d, --directory DIR           Socket directory (default: /tmp/yawl-erlang)"
    echo "  -p, --port PORT               EPMD port (default: 4369)"
    echo "  -v, --verbose                 Verbose output"
    echo "  -h, --help                   Show this help message"
    echo ""
    echo "Environment variables:"
    echo "  ERL_EPMD_PORT                 Override default EPMD port"
    echo "  ERL_AFLAGS                   Additional Erlang arguments"
    echo "  ERL_ZFLAGS                   Additional zotonic flags"
}

# Function to check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."

    # Check if erl is available
    if ! command -v erl &> /dev/null; then
        print_error "Erlang runtime (erl) not found"
        exit 1
    fi

    # Check if epmd is available
    if ! command -v epmd &> /dev/null; then
        print_error "Erlang port mapper daemon (epmd) not found"
        exit 1
    fi

    # Check if OTP version is compatible
    local otp_version=$(erl -noshell -eval 'io:format("~s", [erlang:system_info(otp_release)]).' -s erlang halt)
    if [[ ! "$otp_version" =~ ^[0-9]+\.[0-9]+ ]]; then
        print_warning "Could not determine OTP version, continuing anyway"
    else
        print_info "Found OTP version: $otp_version"
    fi

    print_success "Prerequisites check passed"
}

# Function to create socket directory
create_socket_directory() {
    print_info "Creating socket directory: $SOCKET_DIR"

    if ! mkdir -p "$SOCKET_DIR"; then
        print_error "Failed to create socket directory: $SOCKET_DIR"
        exit 1
    fi

    # Set appropriate permissions
    chmod 700 "$SOCKET_DIR"

    print_success "Socket directory created and secured"
}

# Function to start EPMD (if not already running)
start_epmd() {
    print_info "Starting EPMD on port $EPMD_PORT"

    # Check if EPMD is already running
    if epmd -port "$EPMD_PORT" &> /dev/null; then
        print_info "EPMD already running on port $EPMD_PORT"
        return 0
    fi

    # Start EPMD in background
    epmd -port "$EPMD_PORT" &
    local epmd_pid=$!

    # Wait a moment for EPMD to start
    sleep 1

    # Check if EPMD started successfully
    if ! epmd -port "$EPMD_PORT" &> /dev/null; then
        print_error "Failed to start EPMD on port $EPMD_PORT"
        kill $epmd_pid &> /dev/null || true
        exit 1
    fi

    print_success "EPMD started successfully"

    # Store PID for cleanup
    echo $epmd_pid > "$SOCKET_DIR/epmd.pid"
}

# Function to create BEAM startup script
create_beam_script() {
    print_info "Creating BEAM node startup script"

    local beam_script="$SOCKET_DIR/start_beam_bridge.erl"

    cat > "$beam_script" << 'EOF'
% YAWL BEAM Bridge Startup Script
% This script configures and starts the BEAM node for Unix domain socket transport

-module(yawl_beam_bridge).
-behaviour(application).

-export([start/2, stop/1, init/1]).

% Define the application start function
start(_Type, _Args) ->
    % Initialize Unix domain socket transport
    case init_unix_socket() of
        {ok, SocketPath} ->
            % Register the bridge service
            case register_bridge_service() of
                ok ->
                    yawl_bridge:start_link(SocketPath);
                {error, Reason} ->
                    {error, {service_registration_failed, Reason}}
            end;
        {error, Reason} ->
            {error, Reason}
    end.

% Define the application stop function
stop(_State) ->
    ok.

% Initialize Unix domain socket transport
init_unix_socket() ->
    % Create socket directory if it doesn't exist
    SocketDir = "/tmp/yawl-erlang",
    case file:make_dir(SocketDir) of
        ok -> ok;
        {error, eexist} -> ok;
        {error, Reason} -> {error, {failed_to_create_socket_dir, Reason}}
    end,

    % Generate socket path
    SocketPath = filename:join(SocketDir, "yaws_bridge.sock"),

    % Clean up any existing socket
    file:delete(SocketPath),

    % Configure for Unix domain socket transport
    application:set_env(kernel, inet_dist_use_interface, {127, 0, 0, 1}),
    application:set_env(kernel, inet_dist_listen_min, 9100),
    application:set_env(kernel, inet_dist_listen_max, 9199),

    % Set the distribution protocol to local
    application:set_env(kernel, proto_dist, "local"),

    {ok, SocketPath}.

% Register the bridge service
register_bridge_service() ->
    % Define service registration
    ServiceSpec = #{
        id => yawl_bridge,
        start => {yawl_bridge, start_link, []},
        restart => permanent,
        shutdown => 5000,
        type => worker,
        modules => [yawl_bridge]
    },

    % Start the supervisor
    case supervisor:start_link({local, yawl_bridge_sup}, yaws_bridge_sup, [ServiceSpec]) of
        {ok, _Pid} -> ok;
        {error, Reason} -> {error, Reason}
    end.

% Define the bridge supervisor
yaws_bridge_sup(SupervisorName, [ServiceSpec]) ->
    supervisor:start_link(SupervisorName, yaws_bridge_sup, [ServiceSpec]).

yaws_bridge_sup(_SupervisorName, [ServiceSpec]) ->
    % Define the supervisor specification
    SupSpec = #{
        strategy => one_for_one,
        intensity => 3,
        period => 10,
        children => [ServiceSpec]
    },
    {ok, {SupSpec, []}}.
EOF

    print_success "BEAM startup script created: $beam_script"
}

# Function to start the BEAM node
start_beam_node() {
    print_info "Starting BEAM node: $NODE_NAME"

    # Set up Erlang arguments
    local erl_args=""

    # Cookie
    erl_args="$erl_args -setcookie $COOKIE"

    # Application directory
    erl_args="$erl_args -pa $(dirname "$0/../ebin")"

    # Additional arguments from environment
    if [[ -n "${ERL_AFLAGS:-}" ]]; then
        erl_args="$erl_args $ERL_AFLAGS"
    fi

    # Start the BEAM node
    if [[ "$VERBOSE" == true ]]; then
        print_info "Starting BEAM node with arguments: $erl_args"
    fi

    # Run erl in the background
    erl $erl_args \
        -name "$NODE_NAME" \
        -epmd_port "$EPMD_PORT" \
        -boot start_clean \
        -config $(dirname "$0/../config/yaws_bridge.config") \
        -s yawl_bridge \
        -noshell \
        -noinput \
        -sasl \
        -mnesia \
        -detached \
        > "$SOCKET_DIR/beam_bridge.log" 2>&1 &

    local beam_pid=$!
    echo $beam_pid > "$SOCKET_DIR/beam_bridge.pid"

    print_success "BEAM node started with PID: $beam_pid"
}

# Function to wait for BEAM node to be ready
wait_for_beam() {
    print_info "Waiting for BEAM node to be ready..."

    local max_wait=30
    local wait_count=0

    while [[ $wait_count -lt $max_wait ]]; do
        if erl -noshell -name "test@localhost" -setcookie "$COOKIE" \
            -eval 'io:format("Node is ready~n"), erlang:halt()' \
            -noinput &> /dev/null; then
            print_success "BEAM node is ready"
            return 0
        fi

        sleep 1
        wait_count=$((wait_count + 1))
    done

    print_error "BEAM node did not become ready within $max_wait seconds"
    print_info "Check log file: $SOCKET_DIR/beam_bridge.log"
    return 1
}

# Function to check if BEAM node is running
check_beam_status() {
    if [[ -f "$SOCKET_DIR/beam_bridge.pid" ]]; then
        local beam_pid=$(cat "$SOCKET_DIR/beam_bridge.pid")
        if kill -0 "$beam_pid" &> /dev/null; then
            print_success "BEAM node is running with PID: $beam_pid"
            return 0
        else
            print_error "BEAM process $beam_pid is not running"
            return 1
        fi
    else
        print_error "BEAM PID file not found"
        return 1
    fi
}

# Function to stop the BEAM node
stop_beam_node() {
    if [[ -f "$SOCKET_DIR/beam_bridge.pid" ]]; then
        local beam_pid=$(cat "$SOCKET_DIR/beam_bridge.pid")
        print_info "Stopping BEAM node with PID: $beam_pid"

        # Send graceful shutdown
        if kill -TERM "$beam_pid" &> /dev/null; then
            # Wait for graceful shutdown
            local max_wait=10
            local wait_count=0

            while kill -0 "$beam_pid" &> /dev/null && [[ $wait_count -lt $max_wait ]]; do
                sleep 1
                wait_count=$((wait_count + 1))
            done

            # Force kill if still running
            if kill -0 "$beam_pid" &> /dev/null; then
                print_warning "Force killing BEAM node"
                kill -KILL "$beam_pid" || true
            fi
        fi

        # Clean up PID file
        rm -f "$SOCKET_DIR/beam_bridge.pid"
        print_success "BEAM node stopped"
    else
        print_warning "No BEAM node PID file found"
    fi
}

# Function to cleanup resources
cleanup() {
    print_info "Cleaning up resources..."

    # Stop BEAM node
    stop_beam_node

    # Stop EPMD if we started it
    if [[ -f "$SOCKET_DIR/epmd.pid" ]]; then
        local epmd_pid=$(cat "$SOCKET_DIR/epmd.pid")
        print_info "Stopping EPMD with PID: $epmd_pid"
        kill $epmd_pid &> /dev/null || true
        rm -f "$SOCKET_DIR/epmd.pid"
    fi

    print_success "Cleanup completed"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -n|--node-name)
            NODE_NAME="$2"
            shift 2
            ;;
        -c|--cookie)
            COOKIE="$2"
            shift 2
            ;;
        -d|--directory)
            SOCKET_DIR="$2"
            shift 2
            ;;
        -p|--port)
            EPMD_PORT="$2"
            shift 2
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Override with environment variables
EPMD_PORT="${ERL_EPMD_PORT:-$EPMD_PORT}"

# Main execution
case "${1:-start}" in
    start)
        check_prerequisites
        create_socket_directory
        create_beam_script
        start_epmd
        start_beam_node
        wait_for_beam
        ;;

    stop)
        check_beam_status || exit 0
        stop_beam_node
        ;;

    restart)
        stop_beam_node
        sleep 2
        $0 start
        ;;

    status)
        if check_beam_status; then
            print_info "BEAM node is running"
        else
            print_error "BEAM node is not running"
            exit 1
        fi
        ;;

    *)
        print_error "Unknown command: $1"
        show_usage
        exit 1
        ;;
esac

# Set up signal handlers
trap cleanup EXIT INT TERM

print_success "YAWL BEAM Bridge startup completed successfully"