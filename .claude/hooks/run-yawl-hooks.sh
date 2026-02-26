#!/bin/bash
# Dispatcher for YAWL intelligence hooks.
#
# Reads Claude Code hook input JSON from stdin, dispatches to the compiled
# yawl-jira and yawl-scout binaries based on hook_event_name.
#
# Hook semantics (output on stdout, consumed by Claude Code):
#   UserPromptSubmit  → {"additionalContext": "..."} (ticket + delta slice)
#   PreToolUse        → {"decision": "approve", "reason": "..."} (scope check)
#   PostToolUse async → receipts written to disk; scout refresh spawned
#   Stop              → session receipt flushed to receipts/
#   PreCompact        → session receipt flushed before context compression

HOOKS_DIR="${CLAUDE_PROJECT_DIR}/.claude/hooks"
YAWL_JIRA="${HOOKS_DIR}/yawl-jira"
YAWL_SCOUT="${HOOKS_DIR}/yawl-scout"
REPO_ROOT="${CLAUDE_PROJECT_DIR}"

# jq is required to parse hook input JSON
if ! command -v jq &>/dev/null; then
    cat > /dev/null
    exit 0
fi

# Read hook input once (stdin is a one-shot stream)
INPUT=$(cat)

# Determine which hook event fired
HOOK_EVENT=$(printf '%s' "$INPUT" | jq -r '.hook_event_name // empty' 2>/dev/null)

case "$HOOK_EVENT" in

    "UserPromptSubmit")
        # Inject ticket context + relevant delta slice into Claude's context.
        # yawl-jira reads {"prompt": "..."} from stdin, outputs {"additionalContext": "..."}.
        if [[ -x "$YAWL_JIRA" ]]; then
            printf '%s' "$INPUT" | "$YAWL_JIRA" --repo-root "$REPO_ROOT" inject prompt || true
        fi
        ;;

    "PreToolUse")
        # Blast-radius / ticket-scope check before a write.
        # yawl-jira pre-write reads flat JSON with file_path, outputs {"decision":"approve","reason":"..."}.
        if [[ -x "$YAWL_JIRA" ]]; then
            TOOL_INPUT=$(printf '%s' "$INPUT" | jq '.tool_input // {}' 2>/dev/null)
            printf '%s' "$TOOL_INPUT" | "$YAWL_JIRA" --repo-root "$REPO_ROOT" pre-write || true
        fi
        ;;

    "PostToolUse")
        # Record typed delta receipt for the file change.
        # Also kick off an async live-intelligence refresh via scout.
        if [[ -x "$YAWL_JIRA" ]]; then
            printf '%s' "$INPUT" | "$YAWL_JIRA" --repo-root "$REPO_ROOT" post-write || true
        fi
        if [[ -x "$YAWL_SCOUT" ]]; then
            # fetch --async spawns itself in background and returns immediately
            "$YAWL_SCOUT" --repo-root "$REPO_ROOT" fetch --async 2>/dev/null || true
        fi
        ;;

    "Stop")
        # Flush all delta receipts from this session into a signed session receipt.
        if [[ -x "$YAWL_JIRA" ]]; then
            "$YAWL_JIRA" --repo-root "$REPO_ROOT" checkpoint || true
        fi
        ;;

    "PreCompact")
        # Preserve session state before Claude's context window is compressed.
        if [[ -x "$YAWL_JIRA" ]]; then
            "$YAWL_JIRA" --repo-root "$REPO_ROOT" checkpoint || true
        fi
        ;;

    *)
        # Unknown or missing event — exit cleanly (no-op)
        ;;

esac

exit 0
