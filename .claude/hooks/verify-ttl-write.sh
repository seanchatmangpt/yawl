#!/bin/bash
# PostToolUse hook: validate .ttl files on every Write|Edit
# Reads PostToolUse JSON from stdin.
# Exit 0 = pass/skip, exit 2 = block (invalid Turtle syntax)

set -euo pipefail

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | python3 -c "
import json, sys
d = json.load(sys.stdin)
print(d.get('tool_input', {}).get('file_path', ''))
" 2>/dev/null || echo "")

# Only act on .ttl files
if [[ "$FILE_PATH" != *.ttl ]]; then
    exit 0
fi

echo "TTL file written: $FILE_PATH"

# Validate Turtle syntax with rapper (if available)
if command -v rapper &> /dev/null; then
    if rapper -i turtle "$FILE_PATH" > /dev/null 2>&1; then
        echo "Turtle syntax: VALID"
    else
        echo "Turtle syntax: INVALID — file will not load into QLever correctly"
        exit 2
    fi
fi

# Count NativeCall triples in the written file
NATIVE_CALL_COUNT=$(grep -c "NativeCall" "$FILE_PATH" 2>/dev/null || echo 0)
echo "NativeCall triples in written file: $NATIVE_CALL_COUNT"

# Bridge file expected counts
if [[ "$FILE_PATH" == *pm-bridge* ]]; then
    if [[ "$NATIVE_CALL_COUNT" -lt 30 ]]; then
        echo "WARNING: pm-bridge TTL has $NATIVE_CALL_COUNT NativeCall triples — expected 30"
    fi
fi
if [[ "$FILE_PATH" == *dm-bridge* ]]; then
    if [[ "$NATIVE_CALL_COUNT" -lt 56 ]]; then
        echo "WARNING: dm-bridge TTL has $NATIVE_CALL_COUNT NativeCall triples — expected 56"
    fi
fi

exit 0
