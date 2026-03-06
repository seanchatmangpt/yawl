#!/usr/bin/env bash
# Standalone helper — locates OTP 28.3.1 in .erlmcp/ and prints libei path.
# NOT invoked by Maven build or any automated hook. Run manually.
# Sets -Derlang.library.path= for running yawl-erlang native tests.
#
# For macOS: Use build-otp28-from-source.sh to build from source.
# Do NOT use Hex.pm prebuilts - they are Linux ELF binaries.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ERLMCP_DIR="${YAWL_ROOT}/.erlmcp"
OTP_VERSION="28.3.1"
OTP_DIR="${ERLMCP_DIR}/otp-${OTP_VERSION}"
ERL_BIN="${OTP_DIR}/bin/erl"

if [[ ! -f "$ERL_BIN" ]]; then
    echo "OTP ${OTP_VERSION} not found at ${OTP_DIR}" >&2
    echo "" >&2
    echo "To install OTP ${OTP_VERSION} for macOS:" >&2
    echo "  ./scripts/build-otp28-from-source.sh" >&2
    echo "" >&2
    echo "This will build native macOS Mach-O binaries from source." >&2
    echo "" >&2
    echo "Do NOT use Hex.pm prebuilts - they are Linux ELF binaries!" >&2
    echo "Do NOT use Homebrew erlang - it only provides static libraries!" >&2
    exit 1
fi

OTP_ROOT=$("$ERL_BIN" -eval 'io:format("~s",[code:root_dir()])' -s init stop -noshell 2>/dev/null)
if [[ -z "$OTP_ROOT" ]]; then
    echo "ERROR: Failed to query OTP root from $ERL_BIN" >&2
    exit 1
fi

# Find erl_interface directory (note: under lib/ not lib/erlang/lib for installed OTP)
EI_DIR=$(ls -d "$OTP_ROOT/lib/erl_interface-"*/lib 2>/dev/null | head -1)
if [[ -z "$EI_DIR" ]]; then
    echo "ERROR: erl_interface not found under $OTP_ROOT/lib/" >&2
    exit 1
fi

# Detect platform and find appropriate library
case "$(uname -s)" in
    Darwin)
        # macOS: Look for dylib
        EI_LIB="${EI_DIR}/libei.dylib"
        if [[ ! -f "$EI_LIB" ]]; then
            # Try to build dylib from static library
            echo "WARN: libei.dylib not found, attempting to build from static..." >&2
            if [[ -f "${EI_DIR}/libei.a" ]]; then
                # Build dylib inline
                WORK_DIR=$(mktemp -d)
                cd "$WORK_DIR"
                ar -x "${EI_DIR}/libei.a"
                clang -dynamiclib \
                    -o "${EI_DIR}/libei.dylib" \
                    -install_name @rpath/libei.dylib \
                    -current_version 1.0.0 \
                    -compatibility_version 1.0.0 \
                    *.o \
                    -lpthread \
                    -lm
                rm -rf "$WORK_DIR"
                EI_LIB="${EI_DIR}/libei.dylib"
                echo "Built: ${EI_LIB}"
            else
                echo "ERROR: Neither libei.dylib nor libei.a found at ${EI_DIR}" >&2
                exit 1
            fi
        fi
        ;;
    Linux)
        # Linux: Look for .so
        EI_LIB="${EI_DIR}/libei.so"
        if [[ ! -f "$EI_LIB" ]]; then
            # Try to build .so from static library
            echo "WARN: libei.so not found, attempting to build from static..." >&2
            if [[ -f "${EI_DIR}/libei.a" ]]; then
                # Create .so from .a
                WORK_DIR=$(mktemp -d)
                cd "$WORK_DIR"
                ar -x "${EI_DIR}/libei.a"
                gcc -shared -o "${EI_DIR}/libei.so" *.o -lpthread -lm
                rm -rf "$WORK_DIR"
                EI_LIB="${EI_DIR}/libei.so"
            else
                echo "ERROR: Neither libei.so nor libei.a found at ${EI_DIR}" >&2
                exit 1
            fi
        fi
        ;;
    *)
        echo "ERROR: Unsupported platform: $(uname -s)" >&2
        exit 1
        ;;
esac

if [[ ! -f "$EI_LIB" ]]; then
    echo "ERROR: libei library not found at $EI_LIB" >&2
    exit 1
fi

EI_INCLUDE=$(ls -d "$OTP_ROOT/lib/erl_interface-"*/include 2>/dev/null | head -1)

echo "OTP_ROOT=$OTP_ROOT"
echo "EI_LIB=$EI_LIB"
echo "EI_INCLUDE=$EI_INCLUDE"
echo ""
echo "Use with Maven tests: -Derlang.library.path=$(dirname "$EI_LIB")"
