#!/bin/bash
# Intelligence Layer Hook — PreTask Integration
# Non-invasive sourcing into pre-task.sh
# Purpose: Validate ticket acceptance criteria before tool use

set -e

HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAWL_ROOT="$(cd "$HOOK_DIR/../.." && pwd)"
INTELLIGENCE_BIN="$YAWL_ROOT/rust/yawl-hooks/target/release/yawl-jira"

if [ ! -x "$INTELLIGENCE_BIN" ]; then
    exit 0  # Non-blocking; continue task
fi

# PreTask validation: check if active ticket has open criteria
if output=$("$INTELLIGENCE_BIN" pre-write 2>/dev/null); then
    if echo "$output" | grep -q '"decision": "approve"'; then
        echo "[✓ Intelligence] PreTask check passed"
    fi
fi

exit 0
