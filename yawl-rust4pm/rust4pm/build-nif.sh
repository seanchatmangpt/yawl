#!/usr/bin/env bash
# ==========================================================================
# build-nif.sh - Build Rust NIF for process mining bridge
#
# Usage:
#   bash build-nif.sh           # Build release NIF
#   bash build-nif.sh dev       # Build debug NIF
#   bash build-nif.sh clean     # Clean build artifacts
#   bash build-nif.sh test      # Build and run basic test
# ==========================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

ERLANG_BRIDGE_DIR="/Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge"
PRIV_DIR="${ERLANG_BRIDGE_DIR}/priv"

# Determine OS-specific extension
case "$(uname -s)" in
    Darwin) LIB_EXT="dylib" ;;
    Linux)  LIB_EXT="so" ;;
    *)      echo "Unsupported OS"; exit 1 ;;
esac

LIB_NAME="libprocess_mining_bridge.${LIB_EXT}"

build_release() {
    echo "Building release NIF..."
    cargo build --release

    # Find the target directory (could be in workspace root)
    TARGET_DIR=""
    if [ -f "target/release/${LIB_NAME}" ]; then
        TARGET_DIR="target/release"
    elif [ -f "../target/release/${LIB_NAME}" ]; then
        TARGET_DIR="../target/release"
    else
        echo "ERROR: Built library not found!"
        exit 1
    fi

    echo "Copying from ${TARGET_DIR}/ to ${PRIV_DIR}/..."
    mkdir -p "${PRIV_DIR}"
    cp "${TARGET_DIR}/${LIB_NAME}" "${PRIV_DIR}/"

    echo "✅ Built and copied ${LIB_NAME}"
    ls -la "${PRIV_DIR}/${LIB_NAME}"
}

build_debug() {
    echo "Building debug NIF..."
    cargo build

    echo "Copying to ${PRIV_DIR}/..."
    mkdir -p "${PRIV_DIR}"
    cp "target/debug/${LIB_NAME}" "${PRIV_DIR}/"

    echo "✅ Built and copied ${LIB_NAME} (debug)"
    ls -la "${PRIV_DIR}/${LIB_NAME}"
}

clean() {
    echo "Cleaning build artifacts..."
    cargo clean
    rm -f "${PRIV_DIR}/${LIB_NAME}"
    echo "✅ Cleaned"
}

test_nif() {
    build_release

    echo ""
    echo "Testing NIF functions..."

    cd "${ERLANG_BRIDGE_DIR}"
    erlc -o ebin src/process_mining_bridge.erl 2>/dev/null || true

    erl -pa ebin -eval "
        io:format('Testing NIF functions:~n'),
        io:format('  nop() = ~p~n', [process_mining_bridge:nop()]),
        io:format('  int_passthrough(42) = ~p~n', [process_mining_bridge:int_passthrough(42)]),

        io:format('~nTesting OCEL import...~n'),
        case process_mining_bridge:import_ocel_json_path(\"/tmp/jtbd/input/pi-sprint-ocel.json\") of
            OcelId when is_list(OcelId) ->
                io:format('  OCEL_ID: ~s~n', [OcelId]),
                io:format('  num_events: ~p~n', [process_mining_bridge:num_events(OcelId)]),
                io:format('  num_objects: ~p~n', [process_mining_bridge:num_objects(OcelId)]);
            Err -> io:format('  Error: ~p~n', [Err])
        end,
        halt(0).
    " -noshell
}

case "${1:-release}" in
    dev|debug) build_debug ;;
    clean) clean ;;
    test) test_nif ;;
    *) build_release ;;
esac
