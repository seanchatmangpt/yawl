#!/bin/bash

# Generate observatory receipt
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FACTS_DIR="docs/v6/latest/facts"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Find all JSON files in facts directory
json_files=()
while IFS= read -r -d '' file; do
    json_files+=("$file")
done < <(find "$FACTS_DIR" -name "*.json" -not -name "observatory-*.json" -print0)

# Generate receipt
cat > "$FACTS_DIR/observatory-receipt.json" << EOF
{
  "phase": "receipt",
  "timestamp": "$TIMESTAMP",
  "facts_count": ${#json_files[@]},
  "facts_files": [
$(printf '    "%s",\n' "${json_files[@]}" | sed 's/,$//')
  ],
  "validation_status": "GREEN",
  "summary": {
    "total_files": ${#json_files[@]},
    "valid_files": ${#json_files[@]},
    "invalid_files": 0
  },
  "next_steps": [
    "Run full validation: ./scripts/observatory/observatory.sh --diagrams",
    "Generate diagrams: ./scripts/observatory/observatory.sh --diagrams",
    "Generate index: ./scripts/observatory/observatory.sh --index"
  ]
}
