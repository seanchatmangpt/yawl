#!/bin/bash
# Wrapper for yawl-hooks observatory binary.
# Gracefully degrades when the binary is absent (local envs without cargo build).
# When binary IS present (remote/CI/post-build), delegates fully via exec.

BINARY="${CLAUDE_PROJECT_DIR}/scripts/observatory/target/release/yawl-hooks"

if [[ -x "$BINARY" ]]; then
    exec "$BINARY" "$@"
fi

# Binary not present — exit cleanly (Ψ facts skip gracefully on local)
exit 0
