#!/usr/bin/env bash
#
# Property Test Runner for OCEL Parsing
# Runs property-based tests using proper library
#

set -euo pipefail

# Configuration
BEAM_DIR="../../_build/test/lib/process_mining_bridge/ebin"
TEST_DIR="property_tests"
MODULE="ocel_property_tests"
REBAR_CMD="rebar3"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*"
}

# Check if rebar3 is available
if ! command -v "$REBAR_CMD" &>/dev/null; then
    log_error "rebar3 not found. Please install rebar3 first."
    exit 1
fi

# Check if proper is available
if ! grep -q "proper" "$REBAR_CMD".config 2>/dev/null; then
    log_warn "proper dependency not found in rebar.config. Adding it..."

    # Add proper to dependencies if not present
    if grep -q "deps" "$REBAR_CMD".config; then
        sed -i.tmp '/deps = \[/a\    {proper, "1.4.0"},' "$REBAR_CMD".config
        rm "$REBAR_CMD".config.tmp
    fi
fi

# Build the project
log_info "Building project with rebar3..."
"$REBAR_CMD" compile

if [ $? -ne 0 ]; then
    log_error "Build failed. Please check the build logs."
    exit 1
fi

# Change to the test directory if it exists
if [ -d "$TEST_DIR" ]; then
    cd "$TEST_DIR"
    log_info "Changed to test directory: $TEST_DIR"
else
    log_error "Test directory '$TEST_DIR' not found."
    exit 1
fi

# Start Erlang with the test module
log_info "Starting Erlang to run property tests..."
log_info "This may take several minutes for comprehensive testing..."

# Run the property tests in erl
"$REBAR_CMD" as test shell --eval "
    % Ensure paths are set up
    code:add_patha(\"$BEAM_DIR\"),
    code:add_patha(\"../ebin\"),

    % Load required modules
    try
        application:start(gproc),
        application:start(uuid),
        application:start(lager),
        process_mining_bridge:start_link(),

        % Run property tests
        case ocel_property_tests:run_property_tests() of
            ok ->
                io:format(\"~nAll property tests completed successfully!~n\"),
                halt(0);
            {failed, FailedTests} ->
                io:format(\"~nSome property tests failed:~n\"),
                lists:foreach(fun({failed, Desc}) ->
                    io:format(\"  - ~s~n\", [Desc])
                end, FailedTests),
                halt(2)
        end
    catch
        Error:Reason ->
            io:format(\"Error running tests: ~p: ~p~n\", [Error, Reason]),
            halt(1)
    end
"