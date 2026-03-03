#!/bin/bash
# Validates TTL files on Write/Edit
# Reads PostToolUse JSON from stdin
# Exit codes: 0 = valid, 2 = invalid (block)

set -euo pipefail

# Read JSON from stdin
INPUT=$(cat)

# Extract file_path from JSON
FILE_PATH=$(echo "$INPUT" | python3 -c "
import json, sys
try:
    d = json.load(sys.stdin)
    print(d.get('tool_input', {}).get('file_path', ''))
except Exception:
    print('')
" 2>/dev/null || echo "")

# Only check TTL files
if [[ "$FILE_PATH" != *.ttl ]]; then
  exit 0
fi

# Check if file exists (it should, since this is PostToolUse)
if [ ! -f "$FILE_PATH" ]; then
  echo "TTL validation skipped: file not found at $FILE_PATH"
  exit 0
fi

echo "=== TTL VALIDATION: $FILE_PATH ==="

# Check Turtle syntax with rapper if available
if command -v rapper &> /dev/null; then
  if rapper -i turtle "$FILE_PATH" > /dev/null 2>&1; then
    echo "Turtle syntax: VALID"
  else
    echo "Turtle syntax: INVALID"
    echo "File will not load into QLever. Errors:"
    rapper -i turtle "$FILE_PATH" 2>&1 | head -20 || true
    exit 2
  fi
else
  echo "Turtle syntax: SKIPPED (rapper not installed)"
  echo "Install with: brew install raptor or apt-get install raptor2-utils"
fi

# Count triples (approximate)
TRIPLE_COUNT=$(grep -c "^\s*[^#]" "$FILE_PATH" 2>/dev/null || echo 0)
echo "Approximate triple count: $TRIPLE_COUNT"

# Count NativeCall triples
NATIVE_CALL_COUNT=$(grep -c "a <https://bridgecore.io/vocab#NativeCall>" "$FILE_PATH" 2>/dev/null || grep -c "NativeCall" "$FILE_PATH" 2>/dev/null || echo 0)
echo "NativeCall references: $NATIVE_CALL_COUNT"

# Bridge-specific validation
if [[ "$FILE_PATH" == *pm-bridge* ]]; then
  if [ "$NATIVE_CALL_COUNT" -lt 30 ]; then
    echo "WARNING: pm-bridge TTL has $NATIVE_CALL_COUNT NativeCall — expected 30+"
    echo "This may indicate incomplete process-mining bridge ontology"
  else
    echo "pm-bridge NativeCall count: OK (>= 30)"
  fi
fi

if [[ "$FILE_PATH" == *dm-bridge* ]]; then
  if [ "$NATIVE_CALL_COUNT" -lt 56 ]; then
    echo "WARNING: dm-bridge TTL has $NATIVE_CALL_COUNT NativeCall — expected 56+"
    echo "This may indicate incomplete decision-mining bridge ontology"
  else
    echo "dm-bridge NativeCall count: OK (>= 56)"
  fi
fi

# Check for common TTL errors
if grep -q '"""\s*"""' "$FILE_PATH" 2>/dev/null; then
  echo "WARNING: Empty multi-line string found"
fi

if grep -qE ';\s*\.\s*$' "$FILE_PATH" 2>/dev/null; then
  echo "INFO: Trailing semicolons before periods (valid but unusual)"
fi

echo "=== TTL VALIDATION COMPLETE ==="

exit 0
