#!/bin/bash

# JTBD 4 - Loop Accumulation with QLever Test Runner
# This script runs the JTBD 4 test for conformance score accumulation

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Function to print colored output
print_status() {
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

# Function to check dependencies
check_dependencies() {
    print_status "Checking dependencies..."

    # Check if erl is available
    if ! command -v erl >/dev/null 2>&1; then
        print_error "Erlang/OTP is not installed or not in PATH"
        exit 1
    fi

    # Check if the beam files exist
    if [ ! -f "jtbd_4_qlever_accumulation.beam" ]; then
        print_warning "Beam file not found, compiling..."
        erl -pa ebin -pa src -pa test/jtbd -eval "
            case c(jtbd_4_qlever_accumulation) of
                {ok, _} -> io:format('Module compiled successfully~n');
                {error, Errors} ->
                    io:format('Compilation errors: ~p~n', [Errors]),
                    halt(1)
            end,
            halt(0)" -noshell
    fi

    # Check if process_mining_bridge is compiled
    if [ ! -f "../ebin/process_mining_bridge.beam" ]; then
        print_error "process_mining_bridge not compiled. Please build the project first."
        exit 1
    fi

    print_success "Dependencies checked"
}

# Function to create test data directories
create_test_data() {
    print_status "Creating test data directories..."

    # Create input and output directories
    mkdir -p /tmp/jtbd/input
    mkdir -p /tmp/jtbd/output

    # Check if input files exist, create if not
    if [ ! -f "/tmp/jtbd/input/pi-sprint-ocel.json" ]; then
        print_warning "Creating pi-sprint-ocel.json..."
        cat > /tmp/jtbd/input/pi-sprint-ocel.json << 'EOF'
{
    "events": [
        {
            "id": "event1",
            "type": "start",
            "timestamp": "2024-01-01T10:00:00Z",
            "source": ["object1"],
            "attributes": {
                "resource": "user1",
                "cost": 100
            }
        },
        {
            "id": "event2",
            "type": "complete",
            "timestamp": "2024-01-01T10:30:00Z",
            "source": ["object1"],
            "attributes": {
                "resource": "user2",
                "cost": 50
            }
        }
    ],
    "objects": [
        {
            "id": "object1",
            "type": "order",
            "attributes": {
                "status": "completed",
                "amount": 150
            }
        }
    ]
}
EOF
    fi

    if [ ! -f "/tmp/jtbd/input/pi-sprint-ocel-v2.json" ]; then
        print_warning "Creating pi-sprint-ocel-v2.json..."
        cat > /tmp/jtbd/input/pi-sprint-ocel-v2.json << 'EOF'
{
    "events": [
        {
            "id": "event1",
            "type": "start",
            "timestamp": "2024-01-02T10:00:00Z",
            "source": ["object1"],
            "attributes": {
                "resource": "user1",
                "cost": 120
            }
        },
        {
            "id": "event2",
            "type": "complete",
            "timestamp": "2024-01-02T10:25:00Z",
            "source": ["object1"],
            "attributes": {
                "resource": "user2",
                "cost": 60
            }
        },
        {
            "id": "event3",
            "type": "approve",
            "timestamp": "2024-01-02T10:45:00Z",
            "source": ["object1"],
            "attributes": {
                "resource": "manager1",
                "cost": 30
            }
        }
    ],
    "objects": [
        {
            "id": "object1",
            "type": "order",
            "attributes": {
                "status": "completed",
                "amount": 180,
                "priority": "high"
            }
        }
    ]
}
EOF
    fi

    print_success "Test data directories created"
}

# Function to check if QLever is running
check_qlever() {
    print_status "Checking QLever availability..."

    if curl -s http://localhost:7001/ >/dev/null 2>&1; then
        print_success "QLever is running at http://localhost:7001"
        return 0
    else
        print_warning "QLever is not running at http://localhost:7001"
        print_warning "Test will use mock storage"
        return 1
    fi
}

# Function to run the test
run_test() {
    print_status "Running JTBD 4 test..."

    # Start the process_mining_bridge application if not already running
    erl -pa ebin -pa src -pa test/jtbd -eval "
        case application:ensure_all_started(process_mining_bridge) of
            {ok, Apps} ->
                io:format('Started applications: ~p~n', [Apps]),
                timer:sleep(500);  % Wait for initialization
            {error, Reason} ->
                io:format('Failed to start applications: ~p~n', [Reason]),
                halt(1)
        end,

        % Initialize random seed
        rand:seed(exs1024, {erlang:monotonic_time(), erlang:unique_integer(), erlang:system_time()}),

        % Run the test
        case jtbd_4_qlever_accumulation:run() of
            {ok, Result} ->
                io:format('Test completed successfully:~n~p~n', [Result]),
                halt(0);
            {error, Reason} ->
                io:format('Test failed: ~p~n', [Reason]),
                halt(1)
        end" -noshell
}

# Function to display results
show_results() {
    print_status "Displaying test results..."

    if [ -f "/tmp/jtbd/output/conformance-history.json" ]; then
        echo ""
        echo "=== Test Results ==="
        cat /tmp/jtbd/output/conformance-history.json | jq '.' 2>/dev/null || cat /tmp/jtbd/output/conformance-history.json
        echo ""
    else
        print_warning "No output file found. Test may have failed."
    fi
}

# Function to cleanup
cleanup() {
    print_status "Cleaning up..."

    # Optional: Clean up temporary files
    # read -p "Clean up temporary files? (y/N): " -n 1 -r
    # echo
    # if [[ $REPLY =~ ^[Yy]$ ]]; then
    #     rm -rf /tmp/jtbd/input/*
    #     rm -f /tmp/jtbd/output/*
    #     print_success "Cleanup completed"
    # fi
}

# Main execution
main() {
    echo "================================================"
    echo "JTBD 4 - Loop Accumulation with QLever Test Runner"
    echo "================================================"
    echo ""

    # Check dependencies
    check_dependencies

    # Create test data
    create_test_data

    # Check QLever availability
    check_qlever

    # Run the test
    echo ""
    print_status "Starting test execution..."
    echo "------------------------------------------------"

    if run_test; then
        echo "------------------------------------------------"
        print_success "Test completed successfully!"
        show_results
    else
        echo "------------------------------------------------"
        print_error "Test failed!"
        exit 1
    fi

    # Cleanup
    cleanup
}

# Handle script interruption
trap 'print_status "Script interrupted"; cleanup; exit 1' INT TERM

# Run main function
main "$@"