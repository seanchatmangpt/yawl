#!/usr/bin/env bash
# Build Erlang/OTP 28 from source on macOS
# Produces libei.dylib for Panama FFM bindings
#
# Usage:
#   ./scripts/build-otp28-from-source.sh [OTP_VERSION]
#
# Arguments:
#   OTP_VERSION - Optional version to build (default: 28.3.1)
#
# Requirements:
#   - Xcode Command Line Tools: xcode-select --install
#   - Homebrew dependencies: openssl@3, (optional: wxwidgets, libiodbc, fop)
#
# Output:
#   Installs to: .erlmcp/otp-{version}/
#   libei.dylib: .erlmcp/otp-{version}/lib/erl_interface-{version}/lib/libei.dylib
#
# IMPORTANT: Do NOT use Hex.pm prebuilts - they are Linux ELF binaries!
# This script builds native macOS Mach-O binaries from source.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ERLMCP_DIR="${YAWL_ROOT}/.erlmcp"

# Configuration
OTP_VERSION="${1:-28.3.1}"
OTP_TARBALL="otp_src_${OTP_VERSION}.tar.gz"
# Use official Erlang source from erlang.org
OTP_DOWNLOAD_URL="https://www.erlang.org/ftp/otp/${OTP_VERSION%%.*}/${OTP_VERSION}/otp_src_${OTP_VERSION}.tar.gz"
BUILD_DIR="${ERLMCP_DIR}/build/otp-${OTP_VERSION}"
INSTALL_DIR="${ERLMCP_DIR}/otp-${OTP_VERSION}"
NPROC=$(sysctl -n hw.ncpu 2>/dev/null || echo 4)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
log_step()  { echo -e "${BLUE}[STEP]${NC} $*"; }

# Check prerequisites
check_prerequisites() {
    log_step "Checking prerequisites..."

    # Check for Xcode tools
    if ! command -v clang &>/dev/null; then
        log_error "Xcode Command Line Tools not found."
        log_error "Run: xcode-select --install"
        exit 1
    fi

    # Check for Homebrew
    if ! command -v brew &>/dev/null; then
        log_error "Homebrew not found. Please install from https://brew.sh"
        exit 1
    fi

    # Required: OpenSSL (for crypto)
    if ! brew list openssl@3 &>/dev/null; then
        log_info "Installing openssl@3..."
        brew install openssl@3
    fi

    # Optional: wxWidgets for observer/debugger
    if ! brew list wxwidgets &>/dev/null 2>&1; then
        log_warn "wxwidgets not installed - observer/debugger will be unavailable"
    fi

    log_info "Prerequisites satisfied."
}

# Download OTP source
download_otp_source() {
    log_step "Downloading OTP ${OTP_VERSION} source..."

    mkdir -p "${ERLMCP_DIR}/downloads"
    local download_path="${ERLMCP_DIR}/downloads/${OTP_TARBALL}"

    if [[ -f "$download_path" ]]; then
        log_info "Source tarball already exists: ${download_path}"
    else
        # Try erlang.org first, fallback to GitHub
        log_info "Downloading from erlang.org..."
        if ! curl -fSL --progress-bar -o "${download_path}" "${OTP_DOWNLOAD_URL}" 2>/dev/null; then
            log_warn "Download from erlang.org failed, trying GitHub..."
            local github_url="https://github.com/erlang/otp/releases/download/OTP-${OTP_VERSION}/otp_src_${OTP_VERSION}.tar.gz"
            curl -fSL --progress-bar -o "${download_path}" "${github_url}"
        fi
        log_info "Downloaded to: ${download_path}"
    fi

    # Extract
    log_info "Extracting source to ${BUILD_DIR}..."
    rm -rf "${BUILD_DIR}"
    mkdir -p "${BUILD_DIR}"
    tar -xzf "${download_path}" -C "${BUILD_DIR}" --strip-components=1
}

# Configure OTP for macOS
configure_otp() {
    log_step "Configuring OTP ${OTP_VERSION} for macOS..."

    cd "${BUILD_DIR}"

    # Get OpenSSL paths
    local openssl_prefix
    openssl_prefix=$(brew --prefix openssl@3)

    # Get wxWidgets paths (optional)
    local wx_prefix
    wx_prefix=$(brew --prefix wxwidgets 2>/dev/null || echo "")

    # Set environment for finding dependencies
    export CFLAGS="-O2 -fPIC ${CFLAGS:-}"
    export LDFLAGS="-fPIC ${LDFLAGS:-}"

    # Configure options for macOS
    local configure_args=(
        --prefix="${INSTALL_DIR}"
        --enable-darwin-64bit
        --with-ssl="${openssl_prefix}"
        --disable-debug
        --without-javac
    )

    # Add wxWidgets if available
    if [[ -n "$wx_prefix" && -f "${wx_prefix}/bin/wx-config" ]]; then
        configure_args+=(
            --enable-wx
            --with-wx-config="${wx_prefix}/bin/wx-config"
        )
        log_info "wxWidgets enabled for observer/debugger"
    else
        configure_args+=(--without-wx)
        log_warn "Building without wxWidgets (no observer/debugger)"
    fi

    log_info "Running configure with: ${configure_args[*]}"
    ./configure "${configure_args[@]}" 2>&1 | tee "${ERLMCP_DIR}/configure.log"

    log_info "Configuration complete."
}

# Build OTP
build_otp() {
    log_step "Building OTP ${OTP_VERSION} (using ${NPROC} parallel jobs)..."

    cd "${BUILD_DIR}"

    # Build with multiple jobs
    log_info "This may take 10-30 minutes depending on your machine..."
    make -j"${NPROC}" 2>&1 | tee "${ERLMCP_DIR}/build.log"

    log_info "Build complete."
}

# Install OTP
install_otp() {
    log_step "Installing OTP ${OTP_VERSION} to ${INSTALL_DIR}..."

    cd "${BUILD_DIR}"

    make install 2>&1 | tee "${ERLMCP_DIR}/install.log"

    log_info "Installation complete."
}

# Build libei.dylib from object files
# This is needed because OTP's erl_interface only builds static lib by default
build_libei_dylib() {
    log_step "Building libei.dylib from static library..."

    # Find the erl_interface install directory (note: it's under lib/erlang/lib/)
    local ei_install_dir="${INSTALL_DIR}/lib/erlang/lib/erl_interface-"*
    ei_install_dir=$(echo ${ei_install_dir})  # Expand glob

    if [[ ! -d "$ei_install_dir" ]]; then
        log_error "erl_interface not installed at ${INSTALL_DIR}/lib/erlang/lib/erl_interface-*"
        exit 1
    fi

    log_info "Found erl_interface at: ${ei_install_dir}"

    # Check if dylib already exists
    local dylib_path="${ei_install_dir}/lib/libei.dylib"
    if [[ -f "$dylib_path" ]]; then
        log_info "libei.dylib already exists: ${dylib_path}"
        return 0
    fi

    # Find static library
    local static_lib="${ei_install_dir}/lib/libei.a"
    if [[ ! -f "$static_lib" ]]; then
        log_error "Static library not found: ${static_lib}"
        exit 1
    fi

    log_info "Found static library: ${static_lib}"

    # Create temp directory for extraction
    local extract_dir="${ERLMCP_DIR}/ei-extract-$$"
    mkdir -p "${extract_dir}"
    cd "${extract_dir}"

    # Extract objects from static library
    log_info "Extracting object files from static library..."
    ar -x "${static_lib}"

    # Count extracted objects
    local obj_count
    obj_count=$(ls -1 *.o 2>/dev/null | wc -l | tr -d ' ')
    log_info "Extracted ${obj_count} object files"

    if [[ "$obj_count" -eq 0 ]]; then
        log_error "No object files extracted from static library"
        rm -rf "${extract_dir}"
        exit 1
    fi

    # Build dylib
    log_info "Building libei.dylib..."

    clang -dynamiclib \
        -o "${dylib_path}" \
        -install_name @rpath/libei.dylib \
        -current_version 1.0.0 \
        -compatibility_version 1.0.0 \
        *.o \
        -lpthread \
        -lm \
        2>&1 | tee "${ERLMCP_DIR}/dylib-build.log" || {
        log_error "Failed to build dylib"
        rm -rf "${extract_dir}"
        exit 1
    }

    if [[ -f "${dylib_path}" ]]; then
        log_info "Successfully built: ${dylib_path}"
        file "${dylib_path}"
        chmod 644 "${dylib_path}"

        # Create versioned symlink
        ln -sf libei.dylib "${ei_install_dir}/lib/libei.1.dylib"
    else
        log_error "Dylib not created"
        rm -rf "${extract_dir}"
        exit 1
    fi

    # Cleanup
    rm -rf "${extract_dir}"
}

# Verify installation
verify_installation() {
    log_step "Verifying OTP ${OTP_VERSION} installation..."

    local erl_bin="${INSTALL_DIR}/bin/erl"
    if [[ ! -f "$erl_bin" ]]; then
        log_error "erl binary not found at ${erl_bin}"
        exit 1
    fi

    # Verify it's actually a macOS binary
    local erlexec="${INSTALL_DIR}/lib/erlang/erts-"*/bin/erlexec
    erlexec=$(echo ${erlexec})

    if file "${erlexec}" 2>/dev/null | grep -q "ELF"; then
        log_error "Installed binary is Linux ELF, not macOS Mach-O!"
        log_error "You may have downloaded prebuilt Linux binaries instead of building from source."
        exit 1
    fi

    # Verify OTP version
    local installed_version
    installed_version=$("$erl_bin" -eval 'io:format("~s~n", [erlang:system_info(otp_release)])' -s init stop -noshell 2>/dev/null)
    log_info "Installed OTP version: ${installed_version}"

    # Find libei (note: under lib/erlang/lib/)
    local ei_dir="${INSTALL_DIR}/lib/erlang/lib/erl_interface-"*
    ei_dir=$(echo ${ei_dir})

    if [[ ! -d "$ei_dir" ]]; then
        log_error "erl_interface not found at ${INSTALL_DIR}/lib/erlang/lib/erl_interface-*"
        exit 1
    fi

    local ei_lib="${ei_dir}/lib/libei.dylib"

    if [[ ! -f "$ei_lib" ]]; then
        # Check for static library as fallback
        local ei_static="${ei_dir}/lib/libei.a"
        if [[ -f "$ei_static" ]]; then
            log_warn "Only static library found: ${ei_static}"
            log_warn "Building dylib from static library..."
            build_libei_dylib
        else
            log_error "libei library not found at ${ei_dir}/lib/"
            exit 1
        fi
    fi

    ei_lib="${ei_dir}/lib/libei.dylib"
    if [[ ! -f "$ei_lib" ]]; then
        log_error "libei.dylib not found after build attempt"
        exit 1
    fi

    log_info "libei found: ${ei_lib}"

    # Check library architecture
    log_info "Library type: $(file "${ei_lib}")"

    # Check symbols
    if command -v nm &>/dev/null; then
        log_info "Checking ei_connect symbol..."
        if nm "${ei_lib}" 2>/dev/null | grep -q "ei_connect"; then
            log_info "✓ ei_connect symbol found"
        else
            log_warn "⚠ ei_connect symbol not found"
        fi
    fi

    # Print summary
    echo ""
    echo "=========================================="
    echo "OTP ${OTP_VERSION} Installation Summary"
    echo "=========================================="
    echo "OTP_ROOT:    ${INSTALL_DIR}/lib/erlang"
    echo "ERL_BIN:     ${erl_bin}"
    echo "EI_LIB:      ${ei_lib}"
    echo "EI_INCLUDE:  ${ei_dir}/include"
    echo ""
    echo "Use with Maven tests:"
    echo "  -Derlang.library.path=${ei_dir}/lib"
    echo ""
    echo "Or set environment:"
    echo "  export ERLANG_LIBRARY_PATH=${ei_dir}/lib"
    echo ""
    echo "Or use build-erlang.sh:"
    echo "  ./scripts/build-erlang.sh"
    echo "=========================================="
}

# Cleanup build artifacts
cleanup() {
    log_step "Cleaning up build artifacts..."
    rm -rf "${BUILD_DIR}"
    log_info "Build directory removed."
}

# Main entry point
main() {
    log_info "Building Erlang/OTP ${OTP_VERSION} from source for macOS"
    log_info "Target directory: ${INSTALL_DIR}"

    # Check if already installed and is macOS native
    if [[ -f "${INSTALL_DIR}/bin/erl" ]]; then
        # Check if it's a macOS binary (not Linux ELF)
        local erlexec="${INSTALL_DIR}/lib/erlang/erts-"*/bin/erlexec
        erlexec=$(echo ${erlexec})

        if [[ -f "$erlexec" ]] && file "$erlexec" 2>/dev/null | grep -q "Mach-O"; then
            log_info "OTP ${OTP_VERSION} already installed (macOS native) at ${INSTALL_DIR}"

            # Check for dylib
            local ei_dir="${INSTALL_DIR}/lib/erlang/lib/erl_interface-"*
            ei_dir=$(echo ${ei_dir})
            local dylib="${ei_dir}/lib/libei.dylib"

            if [[ -f "$dylib" ]]; then
                log_info "libei.dylib already exists"
                verify_installation
                exit 0
            else
                log_info "libei.dylib missing, building from static library..."
                build_libei_dylib
                verify_installation
                exit 0
            fi
        else
            log_warn "Existing installation is Linux ELF, removing..."
            rm -rf "${INSTALL_DIR}"
        fi
    fi

    check_prerequisites
    download_otp_source
    configure_otp
    build_otp
    install_otp
    build_libei_dylib
    verify_installation

    # Optional: cleanup (uncomment to save disk space)
    # cleanup

    log_info "Build complete!"
}

# Run main
main "$@"
