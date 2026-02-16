#!/bin/bash
# Java 25 Feature Validator - Post-tool-use hook
# Validates modern Java 25 usage patterns

set -euo pipefail

INPUT=$(cat)
FILE=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

# Only validate Java source files
[[ ! "$FILE" =~ \.java$ ]] && exit 0

echo "[java25-validate] Checking Java 25 best practices: $FILE" >&2

VIOLATIONS=()

# CHECK 1: Virtual Thread Pinning
if grep -qE 'synchronized\s*\(' "$FILE" 2>/dev/null; then
    VIOLATIONS+=("⚠️  synchronized blocks can pin virtual threads")
    VIOLATIONS+=("   Consider: ReentrantLock for virtual thread compatibility")
fi

# CHECK 2: Record Candidates
if grep -qE 'class\s+\w+.*\{\s*private final' "$FILE" 2>/dev/null; then
    if ! grep -qE '\brecord\b' "$FILE" 2>/dev/null; then
        VIOLATIONS+=("💡 Class with private final fields - consider record")
    fi
fi

# CHECK 3: Old instanceof + cast
if grep -qE 'if\s*\(\s*\w+\s+instanceof\s+\w+\s*\).*\(\w+\)' "$FILE" 2>/dev/null; then
    VIOLATIONS+=("💡 Old instanceof + cast - use pattern variables")
fi

# CHECK 4: Multi-line string concatenation
if grep -qE '"\s*\+\s*$' "$FILE" 2>/dev/null; then
    VIOLATIONS+=("💡 String concatenation - consider text blocks")
fi

# REPORT (informational only)
if [ ${#VIOLATIONS[@]} -gt 0 ]; then
    echo "" >&2
    echo "Java 25 Best Practices Report: $FILE" >&2
    for violation in "${VIOLATIONS[@]}"; do
        echo "  $violation" >&2
    done
    echo "" >&2
fi

exit 0  # Always pass (informational)
