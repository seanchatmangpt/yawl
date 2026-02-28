#!/bin/bash

# Generate observatory index
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

# Collect all fact files
fact_files=()
while IFS= read -r -d '' file; do
    filename=$(basename "$file")
    if [[ "$filename" != "observatory-"* ]]; then
        fact_files+=("$filename")
    fi
done < <(find "$FACTS_DIR" -name "*.json" -print0)

# Generate index
cat > "$FACTS_DIR/observatory-index.json" << EOF
{
  "version": "6.0.0",
  "generated_at": "$TIMESTAMP",
  "total_facts": ${#fact_files[@]},
  "facts": {
$(for file in "${fact_files[@]}"; do
    cat << JSON_ENTRY
    "$file": {
      "type": "$(echo "$file" | cut -d'-' -f1)",
      "generated": true,
      "size": "$(du -h "$FACTS_DIR/$file" | cut -f1)"
    },
JSON_ENTRY
done | sed 's/,$//')
  },
  "navigation": {
    "facts": "$FACTS_DIR/",
    "diagrams": "docs/v6/latest/diagrams/",
    "receipt": "$FACTS_DIR/observatory-receipt.json",
    "index": "$FACTS_DIR/observatory-index.json"
  },
  "quick_links": {
    "modules": "$FACTS_DIR/modules-facts.json",
    "tests": "$FACTS_DIR/tests-facts.json",
    "gates": "$FACTS_DIR/gates-facts.json"
  }
}
