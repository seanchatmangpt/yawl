#!/usr/bin/env bash
# ==========================================================================
# build.sh - Build YAWL ML Bridge NIF
#
# Usage:
#   bash build.sh           # Build release NIF
#   bash build.sh dev       # Build debug NIF
#   bash build.sh clean     # Clean build artifacts
#   bash build.sh test      # Build and test
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

# Determine OS-specific extension
case "$(uname -s)" in
    Darwin) LIB_EXT="dylib" ;;
    Linux)  LIB_EXT="so" ;;
    *)      echo "Unsupported OS: $(uname -s)"; exit 1 ;;
esac

LIB_NAME="libyawl_ml_bridge.${LIB_EXT}"
PRIV_DIR="${SCRIPT_DIR}/../priv"

build_release() {
    echo "Building release NIF..."
    cargo build --release

    echo "Copying to ${PRIV_DIR}/..."
    mkdir -p "${PRIV_DIR}"

    if [ -f "target/release/${LIB_NAME}" ]; then
        cp "target/release/${LIB_NAME}" "${PRIV_DIR}/"
    elif [ -f "../target/release/${LIB_NAME}" ]; then
        cp "../target/release/${LIB_NAME}" "${PRIV_DIR}/"
    else
        echo "ERROR: Built library not found!"
        exit 1
    fi

    echo "✅ Built and copied ${LIB_NAME}"
    ls -la "${PRIV_DIR}/${LIB_NAME}"
}

build_debug() {
    echo "Building debug NIF..."
    cargo build

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
    echo "Testing ML Bridge NIF..."

    # Check Python dependencies
    echo "Checking Python dependencies..."
    python3 -c "import dspy; print(f'  dspy version: {dspy.__version__}')" 2>/dev/null || echo "  ⚠️  dspy not installed (pip install dspy==3.1.3)"
    python3 -c "import tpot2; print('  tpot2: installed')" 2>/dev/null || echo "  ⚠️  tpot2 not installed (pip install tpot2)"

    # Test NIF loading
    echo ""
    echo "Testing NIF loading..."

    erl -noshell -eval "
        io:format('Loading NIF...~n'),
        PrivDir = \"$PRIV_DIR\",
        NifPath = filename:join(PrivDir, \"yawl_ml_bridge\"),
        case erlang:load_nif(NifPath, 0) of
            ok -> io:format('✅ NIF loaded successfully~n');
            {error, Reason} -> io:format('❌ NIF load failed: ~p~n', [Reason])
        end,
        halt(0).
    "
}

case "${1:-release}" in
    dev|debug) build_debug ;;
    clean) clean ;;
    test) test_nif ;;
    *) build_release ;;
esac
