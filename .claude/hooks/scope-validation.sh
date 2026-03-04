#!/bin/bash
# Scope Validation Hook - Enforces LITERAL interpretation of "all"
# Fires on UserPromptSubmit to detect scope keywords
#
# Exit 0 = continue
# Exit 2 = block with warning (not used currently, just logs)

set -euo pipefail

# Read the user prompt
INPUT=$(cat)
PROMPT=$(echo "$INPUT" | jq -r '.prompt // empty' 2>/dev/null || echo "")

# Detect scope keywords that require literal interpretation
SCOPE_KEYWORDS="\\b(all|ALL|every|EVERY|complete|COMPLETE|entire|ENTIRE|full|FULL)\\b"
SUBSET_KEYWORDS="\\b(some|partial|subset|relevant|important|selected)\\b"

if echo "$PROMPT" | grep -qE "$SCOPE_KEYWORDS"; then
    # Log to memory that strict scope was requested
    MEMORY_DIR="${CLAUDE_PROJECT_DIR:-.}/.claude/memory"
    mkdir -p "$MEMORY_DIR"

    cat > "$MEMORY_DIR/scope-instruction.json" <<EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "scope_mode": "LITERAL",
  "user_prompt": $(echo "$PROMPT" | jq -Rs .),
  "detected_keywords": "all/every/complete",
  "instruction": "Interpret ALL literally - no partial work allowed"
}
EOF

    # Output warning to stderr (visible to Claude)
    echo "" >&2
    echo "🎯 SCOPE DETECTED: User specified ALL/EVERY/COMPLETE" >&2
    echo "   → Interpreting LITERALLY: Process EVERYTHING, not a subset" >&2
    echo "   → When user says 'all files', commit ALL files" >&2
    echo "   → When user says 'all changes', include ALL changes" >&2
    echo "" >&2
fi

exit 0
