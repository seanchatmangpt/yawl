#!/bin/bash
# Intelligence Layer Hook — PostEdit Integration
# Non-invasive sourcing into post-edit.sh
# Purpose: Compute typed deltas and record corrections

set -e

HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(cd "$HOOK_DIR/../.." && pwd)"
INTELLIGENCE_BIN="$YAWL_ROOT/rust/yawl-hooks/target/release/yawl-jira"
SESSION_ID="${CLAUDE_SESSION_ID:-default}"

if [ ! -x "$INTELLIGENCE_BIN" ]; then
    exit 0  # Non-blocking; continue
fi

# PostEdit delta recording: store typed delta receipt
# In real deployment, old_content and new_content would be passed
# For now, this is a stub that records the delta path
if [ -n "$1" ]; then
    file_path="$1"

    # Create deltas directory if needed
    mkdir -p "$YAWL_ROOT/.claude/context/deltas/$SESSION_ID"

    # Call yawl-jira post-write (would receive file paths in full implementation)
    if output=$("$INTELLIGENCE_BIN" post-write "$file_path" "$SESSION_ID" 2>/dev/null); then
        echo "[✓ Intelligence] Delta recorded for $file_path"
    fi
fi

exit 0
