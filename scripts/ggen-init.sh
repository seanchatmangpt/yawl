#!/usr/bin/env bash
# ==========================================================================
# ggen-init.sh — Initialize ggen (Graph Generation CLI) for YAWL
#
# Detects Rust toolchain, installs if missing, and sets up ggen workspace.
# ggen is a code generator that processes RDF/TTL graphs and produces
# YAWL workflow definitions via Tera templates.
#
# Usage:
#   bash scripts/ggen-init.sh
#
# Exit codes:
#   0 = success (ggen installed and verified)
#   1 = transient error (retry may help, e.g., network)
#   2 = permanent error (unrecoverable, e.g., unsupported OS)
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ── Helper functions ──────────────────────────────────────────────────────

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

log_debug() {
    # Debug logging (silent unless VERBOSE=1)
    if [[ "${VERBOSE:-0}" == "1" ]]; then
        echo -e "${BLUE}[DEBUG]${NC} $*"
    fi
}

# ── Check Rust toolchain ──────────────────────────────────────────────────

log_info "Checking Rust toolchain..."

if command -v rustc &> /dev/null; then
    RUST_VERSION=$(rustc --version)
    log_success "Rust toolchain found: ${RUST_VERSION}"
else
    log_warn "Rust toolchain not found. Installing..."

    # Detect OS for installer URL
    OS_TYPE=$(uname -s)
    if [[ "$OS_TYPE" != "Linux" && "$OS_TYPE" != "Darwin" ]]; then
        log_error "Unsupported OS: $OS_TYPE (only Linux and macOS supported)"
        exit 2
    fi

    # Install Rust using rustup
    log_info "Downloading rustup installer..."
    if ! curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs > /tmp/rustup-init.sh 2>/dev/null; then
        log_error "Failed to download Rust installer (network error)"
        exit 1
    fi

    log_info "Running Rust installer..."
    if ! bash /tmp/rustup-init.sh -y --quiet 2>&1; then
        log_error "Failed to install Rust"
        rm -f /tmp/rustup-init.sh
        exit 2
    fi

    rm -f /tmp/rustup-init.sh

    # Source cargo environment
    if [[ -f "$HOME/.cargo/env" ]]; then
        # shellcheck source=/dev/null
        source "$HOME/.cargo/env"
    else
        log_error "Rust installation succeeded but \$HOME/.cargo/env not found"
        exit 2
    fi

    # Verify installation
    if ! command -v rustc &> /dev/null; then
        log_error "Rust installation verification failed"
        exit 2
    fi

    RUST_VERSION=$(rustc --version)
    log_success "Rust installed: ${RUST_VERSION}"
fi

# ── Install ggen CLI ──────────────────────────────────────────────────────

log_info "Setting up ggen CLI wrapper..."

# Create ggen command in scripts directory (Python-based wrapper)
GGEN_WRAPPER="${REPO_ROOT}/scripts/ggen-wrapper.py"
GGEN_COMMAND="${REPO_ROOT}/scripts/ggen"

if [[ ! -f "$GGEN_WRAPPER" ]]; then
    log_error "ggen-wrapper.py not found at: ${GGEN_WRAPPER}"
    exit 2
fi

log_debug "ggen wrapper found: ${GGEN_WRAPPER}"

# Make ggen command executable
if [[ ! -x "$GGEN_COMMAND" ]]; then
    log_info "Making ggen command executable..."
    chmod +x "$GGEN_COMMAND"
fi

# Verify Python dependencies
log_info "Verifying Python dependencies..."

if ! python3 -c "import rdflib" 2>/dev/null; then
    log_warn "rdflib not found. Installing via pip..."
    if ! python3 -m pip install rdflib >/dev/null 2>&1; then
        log_error "Failed to install rdflib"
        exit 1
    fi
    log_success "rdflib installed"
fi

if ! python3 -c "import jinja2" 2>/dev/null; then
    log_warn "jinja2 not found. Installing via pip..."
    if ! python3 -m pip install jinja2 >/dev/null 2>&1; then
        log_error "Failed to install jinja2"
        exit 1
    fi
    log_success "jinja2 installed"
fi

# Add scripts directory to PATH for this session
export PATH="${REPO_ROOT}/scripts:${PATH}"

# Verify ggen is accessible
if command -v ggen &> /dev/null; then
    GGEN_VERSION=$(ggen --version 2>&1 || true)
    log_success "ggen wrapper ready: ${GGEN_VERSION}"
else
    log_error "ggen command not accessible after setup"
    exit 2
fi

# ── Verify ggen CLI ───────────────────────────────────────────────────────

log_info "Verifying ggen version..."

if ! GGEN_OUT=$(ggen --version 2>&1); then
    log_error "ggen --version failed"
    exit 2
fi

log_success "ggen verification successful: ${GGEN_OUT}"

# ── Summary ───────────────────────────────────────────────────────────────

log_success "ggen initialization complete"
log_info "ggen CLI: $(which ggen)"
log_info "Ready for graph generation. Use: bash scripts/ggen-sync.sh"

exit 0
