#!/usr/bin/env bash
# ==========================================================================
# Claude Code Context Protection Hook
#
# Prevents modification of protected files based on session context.
# Called before Write/Edit operations.
#
# Environment Variables:
#   CLAUDE_CODE_CONTEXT - Session context: "local", "web", "ci"
#   CLAUDE_SKIP_PROTECTION - Set to "true" to bypass protection
#
# Exit Codes:
#   0 - File modification allowed
#   2 - File modification blocked (protected)
# ==========================================================================

set -euo pipefail

# Get the target file from arguments
TARGET_FILE="${1:-}"
CONTEXT="${CLAUDE_CODE_CONTEXT:-local}"
SKIP="${CLAUDE_SKIP_PROTECTION:-false}"

# If skipping protection, allow all
if [[ "$SKIP" == "true" ]]; then
    exit 0
fi

# CI context has full access
if [[ "$CONTEXT" == "ci" ]]; then
    exit 0
fi

# Load configuration
CONFIG_FILE="$(dirname "$0")/../context/environment.toml"
if [[ ! -f "$CONFIG_FILE" ]]; then
    # Config not found, allow all
    exit 0
fi

# Get protected files for current context
case "$CONTEXT" in
    web)
        PROTECTED=$(grep -A20 "web_protected" "$CONFIG_FILE" | grep '"' | cut -d'"' -f2 2>/dev/null || true)
        ;;
    local)
        PROTECTED=$(grep -A20 "local_protected" "$CONFIG_FILE" | grep '"' | cut -d'"' -f2 2>/dev/null || true)
        ;;
    *)
        PROTECTED=""
        ;;
esac

# Check if target file is protected
for protected_file in $PROTECTED; do
    if [[ "$TARGET_FILE" == "$protected_file" ]] || [[ "$TARGET_FILE" == *"/$protected_file" ]]; then
        echo "BLOCKED: $TARGET_FILE is protected in $CONTEXT context"
        echo ""
        echo "To modify this file:"
        echo "  1. Use CLAUDE_CODE_CONTEXT=local session, OR"
        echo "  2. Set CLAUDE_SKIP_PROTECTION=true, OR"
        echo "  3. Use a Maven profile instead of modifying the file"
        echo ""
        echo "Protected files for $CONTEXT context are listed in:"
        echo "  .claude/context/environment.toml"
        exit 2
    fi
done

# File modification allowed
exit 0
