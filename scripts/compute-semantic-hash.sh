#!/usr/bin/env bash
# ==========================================================================
# compute-semantic-hash.sh — Compute semantic hash for Java files
#
# Computes a semantic hash that is invariant to formatting changes
# but changes when code semantics change.
#
# Usage: bash scripts/compute-semantic-hash.sh <file>
# Output: JSON with hash and metadata
# ==========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [[ $# -lt 1 ]]; then
    echo '{"error": "Usage: compute-semantic-hash.sh <file>"}'
    exit 1
fi

FILE="$1"

if [[ ! -f "$FILE" ]]; then
    echo "{\"error\": \"File not found: $FILE\"}"
    exit 1
fi

# Remove comments and normalize whitespace to get semantic content
semantic_content=$(
    cat "$FILE" | \
    sed '/{/,/}/!d' | \
    tr '\n' ' ' | \
    sed 's|//.*$||g' | \
    sed 's|/\*.*\*/||g' | \
    tr -s ' ' | \
    tr -cd '[:alnum:]{}()[];,.<>+-=!&|' | \
    sed 's/[[:space:]]*//g'
)

# Compute hash of semantic content
hash=$(echo -n "$semantic_content" | shasum -a 256 | cut -d' ' -f1)

# Output JSON
cat << EOF
{
  "file": "$FILE",
  "hash": "$hash",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

exit 0
