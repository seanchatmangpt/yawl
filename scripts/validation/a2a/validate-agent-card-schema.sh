#!/usr/bin/env bash
# ==========================================================================
# validate-agent-card-schema.sh - Validate A2A agent cards against JSON schema
#
# Usage:
#   bash scripts/validation/a2a/validate-agent-card-schema.sh [OPTIONS]
#
# Options:
#   --agent-card FILE   Validate specific agent card file
#   --all               Validate all agent cards in schemas/a2a/examples/
#   --fix               Attempt to fix common issues
#   --verbose           Show detailed validation output
#   --help              Show this help
#
# Exit codes:
#   0 - All validations passed
#   1 - One or more validations failed
#   2 - Warnings only
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SCHEMA_FILE="${PROJECT_ROOT}/schemas/a2a/agent-card.schema.json"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Default options
AGENT_CARD=""
VALIDATE_ALL=false
FIX=false
VERBOSE=false

# -------------------------------------------------------------------------
# Help text
# -------------------------------------------------------------------------
show_help() {
    cat << EOF
A2A Agent Card Schema Validator

Validates agent card JSON documents against the A2A agent-card.schema.json specification.

Usage: bash scripts/validation/a2a/validate-agent-card-schema.sh [OPTIONS]

Options:
  --agent-card FILE   Validate specific agent card file
  --all               Validate all agent cards in schemas/a2a/examples/
  --fix               Attempt to fix common issues (NOT IMPLEMENTED)
  --verbose           Show detailed validation output
  --help              Show this help

Examples:
  # Validate single agent card
  bash scripts/validation/a2a/validate-agent-card-schema.sh --agent-card my-agent.json

  # Validate all examples
  bash scripts/validation/a2a/validate-agent-card-schema.sh --all

  # Verbose validation
  bash scripts/validation/a2a/validate-agent-card-schema.sh --all --verbose
EOF
}

# -------------------------------------------------------------------------
# Parse arguments
# -------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --agent-card)
            AGENT_CARD="$2"
            shift 2
            ;;
        --all)
            VALIDATE_ALL=true
            shift
            ;;
        --fix)
            FIX=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}" >&2
            show_help
            exit 1
            ;;
    esac
done

# -------------------------------------------------------------------------
# Check for schema file
# -------------------------------------------------------------------------
if [[ ! -f "$SCHEMA_FILE" ]]; then
    echo -e "${RED}ERROR: Schema file not found: $SCHEMA_FILE${NC}" >&2
    exit 1
fi

# -------------------------------------------------------------------------
# Find JSON schema validator
# -------------------------------------------------------------------------
find_validator() {
    if command -v check-jsonschema &>/dev/null; then
        echo "check-jsonschema"
    elif command -v ajv &>/dev/null; then
        echo "ajv"
    elif command -v python3 &>/dev/null && python3 -c "import jsonschema" 2>/dev/null; then
        echo "python"
    else
        echo ""
    fi
}

VALIDATOR=$(find_validator)

if [[ -z "$VALIDATOR" ]]; then
    echo -e "${YELLOW}WARNING: No JSON schema validator found.${NC}"
    echo "Install one of: check-jsonschema, ajv-cli, or python jsonschema"
    echo ""
    echo "  pip install check-jsonschema"
    echo "  npm install -g ajv-cli"
    echo "  pip install jsonschema"
    exit 2
fi

echo -e "${BLUE}Using validator: ${VALIDATOR}${NC}"
echo ""

# -------------------------------------------------------------------------
# Validate function
# -------------------------------------------------------------------------
validate_file() {
    local file="$1"
    local result=0

    if [[ ! -f "$file" ]]; then
        echo -e "${RED}FAIL: File not found: $file${NC}"
        return 1
    fi

    if [[ "$VERBOSE" = true ]]; then
        echo -e "${BLUE}Validating: $file${NC}"
    fi

    case "$VALIDATOR" in
        check-jsonschema)
            if check-jsonschema --schemafile "$SCHEMA_FILE" "$file" 2>&1; then
                echo -e "${GREEN}PASS: $file${NC}"
            else
                echo -e "${RED}FAIL: $file${NC}"
                result=1
            fi
            ;;
        ajv)
            if ajv validate -s "$SCHEMA_FILE" -d "$file" 2>&1; then
                echo -e "${GREEN}PASS: $file${NC}"
            else
                echo -e "${RED}FAIL: $file${NC}"
                result=1
            fi
            ;;
        python)
            if python3 << PYEOF
import json
import jsonschema
import sys

try:
    with open('$SCHEMA_FILE') as f:
        schema = json.load(f)
    with open('$file') as f:
        doc = json.load(f)
    jsonschema.validate(doc, schema)
    sys.exit(0)
except jsonschema.ValidationError as e:
    print(f"Validation error: {e.message}")
    sys.exit(1)
except Exception as e:
    print(f"Error: {e}")
    sys.exit(1)
PYEOF
            then
                echo -e "${GREEN}PASS: $file${NC}"
            else
                echo -e "${RED}FAIL: $file${NC}"
                result=1
            fi
            ;;
    esac

    return $result
}

# -------------------------------------------------------------------------
# Main
# -------------------------------------------------------------------------
TOTAL=0
PASSED=0
FAILED=0

echo "========================================="
echo "  A2A Agent Card Schema Validation"
echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "========================================="
echo ""

if [[ -n "$AGENT_CARD" ]]; then
    # Validate single file
    TOTAL=1
    if validate_file "$AGENT_CARD"; then
        PASSED=1
    else
        FAILED=1
    fi
elif [[ "$VALIDATE_ALL" = true ]]; then
    # Validate all examples
    EXAMPLES_DIR="${PROJECT_ROOT}/schemas/a2a/examples"

    if [[ -d "$EXAMPLES_DIR" ]]; then
        for file in "$EXAMPLES_DIR"/*.json; do
            if [[ -f "$file" ]]; then
                ((TOTAL++)) || true
                if validate_file "$file"; then
                    ((PASSED++)) || true
                else
                    ((FAILED++)) || true
                fi
            fi
        done
    else
        echo -e "${YELLOW}No examples directory found: $EXAMPLES_DIR${NC}"
    fi
else
    # Default: validate example if exists
    EXAMPLE_FILE="${PROJECT_ROOT}/schemas/a2a/examples/agent-card.example.json"
    if [[ -f "$EXAMPLE_FILE" ]]; then
        TOTAL=1
        if validate_file "$EXAMPLE_FILE"; then
            PASSED=1
        else
            FAILED=1
        fi
    else
        echo -e "${YELLOW}No agent card files to validate.${NC}"
        echo "Use --agent-card FILE or --all to specify files."
        exit 0
    fi
fi

echo ""
echo "========================================="
echo "  Summary"
echo "========================================="
echo "  Total:   $TOTAL"
echo -e "  ${GREEN}Passed:  $PASSED${NC}"
echo -e "  ${RED}Failed:  $FAILED${NC}"
echo ""

if [[ $FAILED -gt 0 ]]; then
    exit 1
elif [[ $TOTAL -eq 0 ]]; then
    exit 2
else
    exit 0
fi
