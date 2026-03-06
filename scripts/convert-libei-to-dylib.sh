#!/usr/bin/env bash
# Convert libei.a (static) to libei.dylib (shared) for Panama FFM
#
# This script extracts object files from the static libei.a and creates
# a shared library (libei.dylib) that can be loaded via Panama FFM.
#
# Usage:
#   ./scripts/convert-libei-to-dylib.sh [OTP_DIR]
#
# Arguments:
#   OTP_DIR - Optional path to OTP installation (default: ~/cre/.erlmcp/otp-28.3.1)
#
# Output:
#   Creates libei.dylib in the same directory as libei.a
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Configuration
OTP_DIR="${1:-$HOME/cre/.erlmcp/otp-28.3.1}"
EI_VERSION=$(basename "$OTP_DIR"/lib/erlang/lib/erl_interface-* 2>/dev/null | sed 's/erl_interface-//' || echo "5.6.2")
EI_LIB_DIR="${OTP_DIR}/lib/erlang/lib/erl_interface-${EI_VERSION}/lib"
EI_INCLUDE_DIR="${OTP_DIR}/lib/erlang/lib/erl_interface-${EI_VERSION}/include"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# Verify inputs
verify_inputs() {
    log_info "Verifying OTP installation..."

    if [[ ! -d "$OTP_DIR" ]]; then
        log_error "OTP directory not found: ${OTP_DIR}"
        log_error "Install OTP first or provide correct path"
        exit 1
    fi

    if [[ ! -f "${EI_LIB_DIR}/libei.a" ]]; then
        log_error "libei.a not found at ${EI_LIB_DIR}/libei.a"
        exit 1
    fi

    log_info "Found erl_interface ${EI_VERSION} at ${EI_LIB_DIR}"
}

# Convert static to shared library
convert_to_dylib() {
    log_info "Converting libei.a to libei.dylib..."

    local work_dir="${EI_LIB_DIR}/.dylib-build"
    local dylib_path="${EI_LIB_DIR}/libei.dylib"

    # Check if already exists
    if [[ -f "$dylib_path" ]]; then
        log_info "libei.dylib already exists at ${dylib_path}"
        return 0
    fi

    # Create work directory
    rm -rf "$work_dir"
    mkdir -p "$work_dir"

    # Extract object files from static library
    log_info "Extracting object files from libei.a..."
    cd "$work_dir"
    ar -x "${EI_LIB_DIR}/libei.a"

    # Count extracted files
    local obj_count
    obj_count=$(find . -name "*.o" | wc -l | tr -d ' ')
    log_info "Extracted ${obj_count} object files"

    # Collect all object files
    local obj_files=()
    while IFS= read -r -d '' obj; do
        obj_files+=("$obj")
    done < <(find . -name "*.o" -print0)

    if [[ ${#obj_files[@]} -eq 0 ]]; then
        log_error "No object files found in static library"
        rm -rf "$work_dir"
        exit 1
    fi

    # Build dylib
    # -install_name: sets the library's install name for linking
    # -current_version / -compatibility_version: for versioning
    # -lpthread -lm: required dependencies
    log_info "Building libei.dylib..."

    clang -dynamiclib \
        -o "$dylib_path" \
        -install_name @rpath/libei.dylib \
        -current_version 1.0.0 \
        -compatibility_version 1.0.0 \
        "${obj_files[@]}" \
        -lpthread \
        -lm

    # Verify the dylib was created
    if [[ ! -f "$dylib_path" ]]; then
        log_error "Failed to create libei.dylib"
        rm -rf "$work_dir"
        exit 1
    fi

    # Set permissions
    chmod 644 "$dylib_path"

    # Create versioned symlink
    ln -sf libei.dylib "${EI_LIB_DIR}/libei.1.dylib"

    # Cleanup
    rm -rf "$work_dir"

    log_info "Successfully created: ${dylib_path}"
}

# Also create libei_st.dylib (thread-safe version)
convert_st_to_dylib() {
    log_info "Converting libei_st.a to libei_st.dylib..."

    local work_dir="${EI_LIB_DIR}/.dylib-build-st"
    local dylib_path="${EI_LIB_DIR}/libei_st.dylib"

    # Check if already exists
    if [[ -f "$dylib_path" ]]; then
        log_info "libei_st.dylib already exists at ${dylib_path}"
        return 0
    fi

    # Create work directory
    rm -rf "$work_dir"
    mkdir -p "$work_dir"

    # Extract object files from static library
    log_info "Extracting object files from libei_st.a..."
    cd "$work_dir"
    ar -x "${EI_LIB_DIR}/libei_st.a"

    # Collect all object files
    local obj_files=()
    while IFS= read -r -d '' obj; do
        obj_files+=("$obj")
    done < <(find . -name "*.o" -print0)

    if [[ ${#obj_files[@]} -eq 0 ]]; then
        log_error "No object files found in static library"
        rm -rf "$work_dir"
        exit 1
    fi

    # Build dylib
    log_info "Building libei_st.dylib..."

    clang -dynamiclib \
        -o "$dylib_path" \
        -install_name @rpath/libei_st.dylib \
        -current_version 1.0.0 \
        -compatibility_version 1.0.0 \
        "${obj_files[@]}" \
        -lpthread \
        -lm

    chmod 644 "$dylib_path"
    ln -sf libei_st.dylib "${EI_LIB_DIR}/libei_st.1.dylib"

    # Cleanup
    rm -rf "$work_dir"

    log_info "Successfully created: ${dylib_path}"
}

# Verify the dylib
verify_dylib() {
    log_info "Verifying libei.dylib..."

    local dylib_path="${EI_LIB_DIR}/libei.dylib"

    if command -v file &>/dev/null; then
        log_info "Library type: $(file "${dylib_path}")"
    fi

    # Check for required symbols
    if command -v nm &>/dev/null; then
        log_info "Checking for ei_connect symbol..."
        if nm "${dylib_path}" | grep -q "ei_connect"; then
            log_info "✓ ei_connect symbol found"
        else
            log_warn "⚠ ei_connect symbol not found"
        fi

        log_info "Checking for ei_xencode_version symbol..."
        if nm "${dylib_path}" | grep -q "ei_xencode_version"; then
            log_info "✓ ei_xencode_version symbol found"
        else
            log_warn "⚠ ei_xencode_version symbol not found"
        fi
    fi

    # Check library dependencies
    if command -v otool &>/dev/null; then
        log_info "Library dependencies:"
        otool -L "${dylib_path}" | head -5
    fi
}

# Print usage summary
print_summary() {
    echo ""
    echo "=========================================="
    echo "libei.dylib Conversion Complete"
    echo "=========================================="
    echo "OTP_DIR:     ${OTP_DIR}"
    echo "EI_VERSION:  ${EI_VERSION}"
    echo "EI_LIB:      ${EI_LIB_DIR}/libei.dylib"
    echo "EI_INCLUDE:  ${EI_INCLUDE_DIR}"
    echo ""
    echo "Use with Maven tests:"
    echo "  -Derlang.library.path=${EI_LIB_DIR}"
    echo ""
    echo "Or set environment:"
    echo "  export ERLANG_LIBRARY_PATH=${EI_LIB_DIR}"
    echo ""
    echo "For YAWL, create symlink:"
    echo "  mkdir -p ${YAWL_ROOT}/.erlmcp"
    echo "  ln -sf ${OTP_DIR} ${YAWL_ROOT}/.erlmcp/otp-28.3.1"
    echo "=========================================="
}

# Main entry point
main() {
    log_info "Converting libei.a to libei.dylib"
    log_info "OTP directory: ${OTP_DIR}"

    verify_inputs
    convert_to_dylib
    convert_st_to_dylib
    verify_dylib
    print_summary

    log_info "Conversion complete!"
}

main "$@"
