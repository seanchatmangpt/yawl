#!/bin/bash

# Generate gates facts
set -euo pipefail

FACTS_DIR="docs/v6/latest/facts"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Count gate-related files
GATE_FILES=$(find . -name "*Gate*" | wc -l)
GATE_CLASSES=$(find . -name "*.java" -exec grep -l "class.*Gate" {} \; | wc -l)
GATE_METHODS=$(find . -name "*.java" -exec grep -c "gate" {} \; | awk '{sum+=$1} END {print sum}')

# Generate facts
cat > "$FACTS_DIR/gates-facts.json" << EOF
{
  "generated_at": "$TIMESTAMP",
  "generator": "generate-gates-facts.sh",
  "data": {
    "gate_files_count": $GATE_FILES,
    "gate_classes_count": $GATE_CLASSES,
    "gate_methods_count": $GATE_METHODS,
    "total_gates": $GATE_CLASSES,
    "validation_gates": $(find . -name "*Validator*" | wc -l),
    "security_gates": $(find . -name "*Security*" | wc -l)
  }
}
