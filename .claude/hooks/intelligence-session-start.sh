#!/bin/bash
# Intelligence Layer Hook — SessionStart Integration
# Non-invasive sourcing into session-start.sh
# Purpose: Inject ticket context + spawn async scout

set -e

HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(cd "$HOOK_DIR/../.." && pwd)"
INTELLIGENCE_BIN="$YAWL_ROOT/rust/yawl-hooks/target/release/yawl-jira"

if [ ! -x "$INTELLIGENCE_BIN" ]; then
    echo "[⚠ Intelligence] yawl-jira binary not found at $INTELLIGENCE_BIN" >&2
    exit 0  # Non-blocking; continue session
fi

# SessionStart injection: <50ms target
if output=$("$INTELLIGENCE_BIN" inject --session 2>/dev/null); then
    if [ -n "$output" ] && [ "$output" != '{"additionalContext": ""}' ]; then
        echo "[✓ Intelligence] Session initialized with ticket context"
        # In real hook context, this would be injected to Claude's context
        # For now, output can be captured by hook harness
    fi
fi

# Spawn async scout (non-blocking)
SCOUT_BIN="$YAWL_ROOT/rust/yawl-hooks/target/release/yawl-scout"
if [ -x "$SCOUT_BIN" ]; then
    nohup "$SCOUT_BIN" fetch >/dev/null 2>&1 &
    echo "[✓ Intelligence] Scout spawned (async fetch)"
fi

exit 0
