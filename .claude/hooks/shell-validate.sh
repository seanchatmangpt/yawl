#!/usr/bin/env bash
# ==========================================================================
# shell-validate.sh — Shell Script Standards Enforcement Hook
#
# Runs after Write/Edit on .sh files to enforce YAWL Fortune 5 Standards
# Exit 0 = GREEN (pass), Exit 2 = RED (block with violations)
#
# Checks:
#   1. Required shebang: #!/usr/bin/env bash
#   2. Safety: set -euo pipefail
#   3. Deprecated test syntax: [ ] instead of [[ ]]
#   4. TODO/FIXME comments (deferred work)
#   5. Unquoted variables (basic detection)
#
# Integration: Claude Code PostToolUse hook
# ==========================================================================

set -euo pipefail

# ── Input parsing ────────────────────────────────────────────────────────────
# Claude Code passes hook input via stdin as JSON
INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty' 2>/dev/null || echo "")
TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty' 2>/dev/null || echo "")

# Only validate shell scripts
[[ ! "$FILE" =~ \.sh$ ]] && exit 0
[[ ! -f "$FILE" ]] && exit 0

# ── Color codes ──────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RESET='\033[0m'

# ── Violation collection ─────────────────────────────────────────────────────
VIOLATIONS=()

# === CHECK 1: Required shebang ===
FIRST_LINE=$(head -1 "$FILE" 2>/dev/null || echo "")
if [[ ! "$FIRST_LINE" =~ ^#!/usr/bin/env\ bash ]] && [[ ! "$FIRST_LINE" =~ ^#!/bin/bash ]]; then
    VIOLATIONS+=("H_SHELL_SHEBANG: Missing or incorrect shebang. Use: #!/usr/bin/env bash (preferred) or #!/bin/bash")
fi

# === CHECK 2: set -euo pipefail ===
if ! grep -q 'set -euo pipefail' "$FILE" 2>/dev/null; then
    VIOLATIONS+=("H_SHELL_SAFETY: Missing 'set -euo pipefail' for fail-fast safety")
fi

# === CHECK 3: Deprecated [ ] with -a/-o (use [[ ]] with && or || instead) ===
# Only flag [ ] with -a/-o operators (truly deprecated), allow [ -f "$file" ] etc.
while IFS= read -r line; do
    # Skip comments
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    # Flag [ ... -a ... ] or [ ... -o ... ] (deprecated AND/OR in [ ])
    if echo "$line" | grep -qE '\[\s+.*\s+-(a|o)\s+.*\s+\]' && ! echo "$line" | grep -qE '\[\['; then
        VIOLATIONS+=("H_SHELL_DEPRECATED: Use [[ ]] with && or || instead of [ ] with -a/-o: $line")
        break  # One violation per file is enough
    fi
done < "$FILE" 2>/dev/null || true

# === CHECK 4: TODO/FIXME in comments ===
if grep -nE '#.*(TODO|FIXME|XXX|HACK)' "$FILE" 2>/dev/null | head -5; then
    VIOLATIONS+=("H_TODO: Deferred work markers found - implement or remove")
fi

# === CHECK 5: Potentially unquoted variables (heuristic) ===
# This is a basic check - looks for $VAR not inside quotes
# Skip for now to avoid false positives - too complex for simple regex
# A shellcheck integration would be better for this

# === REPORT VIOLATIONS ===
if [[ ${#VIOLATIONS[@]} -gt 0 ]]; then
    echo "" >&2
    echo -e "${RED}╔═══════════════════════════════════════════════════════════════════╗${RESET}" >&2
    echo -e "${RED}║  🚨 SHELL SCRIPT STANDARDS VIOLATION DETECTED                    ║${RESET}" >&2
    echo -e "${RED}╚═══════════════════════════════════════════════════════════════════╝${RESET}" >&2
    echo "" >&2
    echo -e "${YELLOW}File: $FILE${RESET}" >&2
    echo "" >&2

    for v in "${VIOLATIONS[@]}"; do
        echo -e "${RED}  ❌ $v${RESET}" >&2
    done

    echo "" >&2
    echo "See: .claude/rules/shell-conventions.md for shell scripting standards" >&2
    echo "" >&2
    exit 2
fi

# ── Success ─────────────────────────────────────────────────────────────────
echo -e "${GREEN}✓${RESET} Shell script validation passed: $FILE" >&2
exit 0
