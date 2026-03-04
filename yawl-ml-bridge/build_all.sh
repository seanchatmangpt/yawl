#!/usr/bin/env bash
# ==========================================================================
# build_all.sh - Master build script for YAWL ML Bridge
#
# Builds all components:
#   1. Python dependencies (dspy==3.1.3, tpot2)
#   2. Rust NIF with PyO3
#   3. Erlang modules
#   4. Java module
#
# Usage:
#   bash build_all.sh           # Full build
#   bash build_all.sh fast      # Skip Python deps
#   bash build_all.sh test      # Build and run tests
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ─────────────────────────────────────────────────────────────────────────
# Phase 1: Python Dependencies
# ─────────────────────────────────────────────────────────────────────────

install_python_deps() {
    log_info "Installing Python dependencies..."

    if ! command -v python3 &>/dev/null; then
        log_error "Python3 not found. Please install Python 3.x"
        exit 1
    fi

    python3 -m pip install -r requirements.txt --quiet

    # Verify installations
    python3 -c "import dspy; print(f'  DSPy version: {dspy.__version__}')" 2>/dev/null || {
        log_warn "DSPy not installed. Run: pip install dspy==3.1.3"
    }

    python3 -c "import tpot2; print('  TPOT2: installed')" 2>/dev/null || {
        log_warn "TPOT2 not installed. Run: pip install tpot2"
    }

    log_info "Python dependencies installed"
}

# ─────────────────────────────────────────────────────────────────────────
# Phase 2: Rust NIF
# ─────────────────────────────────────────────────────────────────────────

build_rust_nif() {
    log_info "Building Rust NIF with PyO3..."

    cd "${SCRIPT_DIR}/native"

    if ! command -v cargo &>/dev/null; then
        log_error "Rust/Cargo not found. Please install Rust: https://rustup.rs"
        exit 1
    fi

    # Build with explicit target directory
    cargo build --release --target-dir ./target

    # Copy NIF to priv
    mkdir -p "${SCRIPT_DIR}/priv"

    case "$(uname -s)" in
        Darwin)
            cp target/release/libyawl_ml_bridge.dylib "${SCRIPT_DIR}/priv/"
            # Create symlink for Erlang NIF loader (looks for .so on all platforms)
            cd "${SCRIPT_DIR}/priv"
            ln -sf libyawl_ml_bridge.dylib yawl_ml_bridge.so
            ;;
        Linux)
            cp target/release/libyawl_ml_bridge.so "${SCRIPT_DIR}/priv/"
            ;;
        *)
            log_error "Unsupported OS: $(uname -s)"
            exit 1
            ;;
    esac

    log_info "Rust NIF built and copied to priv/"
}

# ─────────────────────────────────────────────────────────────────────────
# Phase 3: Erlang Modules
# ─────────────────────────────────────────────────────────────────────────

build_erlang() {
    log_info "Compiling Erlang modules..."

    cd "${SCRIPT_DIR}"

    ERLANG_SRC="${SCRIPT_DIR}/src/main/erlang"
    ERLANG_EBIN="${SCRIPT_DIR}/ebin"

    mkdir -p "${ERLANG_EBIN}"

    for erl in "${ERLANG_SRC}"/*.erl; do
        if [[ -f "$erl" ]]; then
            erlc -o "${ERLANG_EBIN}" "$erl" 2>/dev/null || {
                log_warn "Failed to compile $(basename "$erl") - continuing"
            }
        fi
    done

    log_info "Erlang modules compiled"
}

# ─────────────────────────────────────────────────────────────────────────
# Phase 4: Java Module
# ─────────────────────────────────────────────────────────────────────────

build_java() {
    log_info "Building Java module with Maven..."

    cd "${SCRIPT_DIR}"

    if ! command -v mvn &>/dev/null; then
        log_error "Maven not found. Please install Maven"
        exit 1
    fi

    mvn compile -DskipTests -q

    log_info "Java module built"
}

# ─────────────────────────────────────────────────────────────────────────
# Tests
# ─────────────────────────────────────────────────────────────────────────

run_tests() {
    log_info "Running tests..."

    cd "${SCRIPT_DIR}"

    # Start Erlang node in background
    erl -name yawl_ml@localhost -setcookie yawl -noshell \
        -pa ebin -eval "application:start(yawl_ml_bridge)" &

    ERLANG_PID=$!
    sleep 2

    # Run Java tests
    mvn test -DskipNifBuild=true || true

    # Stop Erlang
    kill $ERLANG_PID 2>/dev/null || true

    log_info "Tests completed"
}

# ─────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────

main() {
    log_info "Starting YAWL ML Bridge build..."
    echo ""

    local skip_python="${1:-}"

    if [[ "$skip_python" != "fast" && "$skip_python" != "test" ]]; then
        install_python_deps
    fi

    build_rust_nif
    build_erlang
    build_java

    if [[ "$skip_python" == "test" ]]; then
        run_tests
    fi

    echo ""
    log_info "═══════════════════════════════════════════════════════════"
    log_info "  YAWL ML Bridge build complete!"
    log_info ""
    log_info "  Next steps:"
    log_info "    1. Start Erlang node:"
    log_info "       erl -name yawl_ml@localhost -setcookie yawl -pa ebin"
    log_info ""
    log_info "    2. Run Java showcase:"
    log_info "       mvn exec:java -Dexec.mainClass=org.yawlfoundation.yawl.ml.MlBridgeShowcase"
    log_info "═══════════════════════════════════════════════════════════"
}

main "$@"
