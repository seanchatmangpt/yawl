#!/usr/bin/env bash
# Standalone helper — locates OTP 28.3.1 in .erlmcp/ and prints libei.so path.
# NOT invoked by Maven build or any automated hook. Run manually.
# Sets -Derlang.library.path= for running yawl-erlang native tests.
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
    echo "To install OTP ${OTP_VERSION} manually (Hex.pm Bob builds):" >&2
    echo "  mkdir -p ${ERLMCP_DIR}" >&2
    echo "  cd ${ERLMCP_DIR}" >&2
    echo "  curl -fsSL https://builds.hex.pm/builds/otp/amd64/ubuntu-22.04/OTP-${OTP_VERSION}.tar.gz | tar -xz" >&2
    echo "  mv OTP-${OTP_VERSION} otp-${OTP_VERSION}" >&2
    echo "  cd otp-${OTP_VERSION} && ./Install -minimal \$(pwd)" >&2
    echo "" >&2
    echo "Do NOT use: apt-get install erlang (that installs OTP 25, not OTP 28)" >&2
    exit 1
fi

OTP_ROOT=$("$ERL_BIN" -eval 'io:format("~s",[code:root_dir()])' -s init stop -noshell 2>/dev/null)
if [[ -z "$OTP_ROOT" ]]; then
    echo "ERROR: Failed to query OTP root from $ERL_BIN" >&2
    exit 1
fi

EI_DIR=$(ls -d "$OTP_ROOT/lib/erl_interface-"*/lib 2>/dev/null | head -1)
if [[ -z "$EI_DIR" ]]; then
    echo "ERROR: erl_interface not found under $OTP_ROOT/lib/" >&2
    exit 1
fi

EI_LIB="${EI_DIR}/libei.so"
if [[ ! -f "$EI_LIB" ]]; then
    echo "ERROR: libei.so not found at $EI_LIB" >&2
    exit 1
fi

EI_INCLUDE=$(ls -d "$OTP_ROOT/lib/erl_interface-"*/include 2>/dev/null | head -1)

echo "OTP_ROOT=$OTP_ROOT"
echo "EI_LIB=$EI_LIB"
echo "EI_INCLUDE=$EI_INCLUDE"
echo ""
echo "Use with Maven tests: -Derlang.library.path=$EI_LIB"
