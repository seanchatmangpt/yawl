#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ERLANG_MODULE_DIR="${YAWL_ROOT}/yawl-erlang"
EBIN_RESOURCE_DIR="${ERLANG_MODULE_DIR}/src/main/resources/org/yawlfoundation/yawl/erlang/ebin"

# Try to find erl binary
ERL_BIN=""
if command -v erl &>/dev/null; then
    ERL_BIN="$(command -v erl)"
elif [ -x "${YAWL_ROOT}/.erlmcp/otp-28.3.1/bin/erl" ]; then
    ERL_BIN="${YAWL_ROOT}/.erlmcp/otp-28.3.1/bin/erl"
    export PATH="${YAWL_ROOT}/.erlmcp/otp-28.3.1/bin:${PATH}"
fi

if [ -z "${ERL_BIN}" ]; then
    echo "WARNING: OTP/Erlang not found — skipping Erlang beam compilation." >&2
    echo "         Install OTP 28 or set .erlmcp/otp-28.3.1/ to compile .erl → .beam" >&2
    exit 0
fi

# Try to find rebar3
REBAR3_BIN=""
if command -v rebar3 &>/dev/null; then
    REBAR3_BIN="$(command -v rebar3)"
elif [ -x "${YAWL_ROOT}/.erlmcp/rebar3" ]; then
    REBAR3_BIN="${YAWL_ROOT}/.erlmcp/rebar3"
elif [ -x "${ERLANG_MODULE_DIR}/rebar3" ]; then
    REBAR3_BIN="${ERLANG_MODULE_DIR}/rebar3"
fi

if [ -z "${REBAR3_BIN}" ]; then
    echo "WARNING: rebar3 not found — skipping Erlang beam compilation." >&2
    echo "         Install rebar3 or place it at .erlmcp/rebar3" >&2
    exit 0
fi

echo "INFO: Compiling Erlang sources with rebar3..."
cd "${ERLANG_MODULE_DIR}"
"${REBAR3_BIN}" compile

EBIN_SRC="${ERLANG_MODULE_DIR}/_build/default/lib/yawl/ebin"
if [ ! -d "${EBIN_SRC}" ]; then
    echo "ERROR: rebar3 compiled but no ebin at ${EBIN_SRC}" >&2
    exit 1
fi

mkdir -p "${EBIN_RESOURCE_DIR}"
cp "${EBIN_SRC}"/*.beam "${EBIN_RESOURCE_DIR}/" 2>/dev/null || true
echo "INFO: .beam files copied to ${EBIN_RESOURCE_DIR}"
