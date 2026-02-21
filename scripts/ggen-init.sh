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

log_info "Installing ggen CLI from crates.io..."

# Ensure cargo is in PATH
if [[ ! -f "$HOME/.cargo/env" ]]; then
    log_error "cargo environment not properly initialized"
    exit 2
fi
# shellcheck source=/dev/null
source "$HOME/.cargo/env"

if ! command -v cargo &> /dev/null; then
    log_error "cargo not found even after Rust install"
    exit 2
fi

# Check if ggen is already installed
if command -v ggen &> /dev/null; then
    GGEN_VERSION=$(ggen --version 2>&1 || true)
    log_success "ggen already installed: ${GGEN_VERSION}"
else
    log_info "ggen not found. Installing via cargo..."
    if ! cargo install ggen-cli 2>&1 | tail -5; then
        log_error "Failed to install ggen-cli (network error or crates.io unavailable)"
        exit 1
    fi

    # Verify installation
    if ! command -v ggen &> /dev/null; then
        log_error "ggen installation verification failed"
        exit 2
    fi

    GGEN_VERSION=$(ggen --version 2>&1 || true)
    log_success "ggen installed: ${GGEN_VERSION}"
fi

# ── Create ggen workspace ─────────────────────────────────────────────────

WORKSPACE_DIR="${REPO_ROOT}/ggen-workspace"

if [[ -d "$WORKSPACE_DIR" ]]; then
    log_warn "ggen workspace already exists at: ${WORKSPACE_DIR}"
else
    log_info "Creating ggen workspace at: ${WORKSPACE_DIR}"

    if ! ggen new yawl-workflows --output-dir "${WORKSPACE_DIR}" 2>&1 | tail -5; then
        log_warn "ggen workspace creation had issues, but may be partial"
        # Don't fail here—workspace may be partially initialized
    fi

    if [[ ! -d "$WORKSPACE_DIR" ]]; then
        log_error "ggen workspace creation failed"
        exit 2
    fi

    log_success "ggen workspace created: ${WORKSPACE_DIR}"
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
log_info "Workspace: ${WORKSPACE_DIR}"
log_info "Ready for graph generation. Use: bash scripts/ggen-sync.sh"

exit 0
