#!/bin/bash

# Generate modules facts
set -euo pipefail

FACTS_DIR="docs/v6/latest/facts"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Get module information from pom.xml
MODULES_COUNT=$(find . -name "pom.xml" | wc -l)
MODULES=$(find . -name "pom.xml" -exec basename {} \; | sort | tr '\n' ',' | sed 's/,$//')

# Generate facts
cat > "$FACTS_DIR/modules-facts.json" << EOF
{
  "generated_at": "$TIMESTAMP",
  "generator": "generate-modules-facts.sh",
  "data": {
    "modules_count": $MODULES_COUNT,
    "modules": ["$(echo $MODULES | sed 's/,/", "/g')"],
    "total_modules": $MODULES_COUNT,
    "java_modules": $(find . -name "*.java" | wc -l),
    "test_modules": $(find . -name "*Test.java" | wc -l)
  }
}
