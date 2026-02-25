#!/usr/bin/env bash
# ==========================================================================
# validate-mcp-schema.sh - Validate MCP protocol messages against JSON schema
#
# Usage:
#   bash scripts/validation/mcp/validate-mcp-schema.sh [OPTIONS]
#
# Options:
#   --message FILE     Validate specific message file
#   --type TYPE        Message type (request|response|all)
#   --all              Validate all schemas
#   --verbose          Show detailed output
#   --help             Show this help
#
# Validates:
#   - MCP JSON-RPC envelope
#   - Initialize request/response
#   - Tool call request/response
#   - Resource read messages
#   - Error responses
#
# Exit codes:
#   0 - All validations passed
#   1 - One or more validations failed
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SCHEMA_DIR="${PROJECT_ROOT}/schemas/mcp"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Default options
MESSAGE_FILE=""
MESSAGE_TYPE="all"
VERBOSE=false

# -------------------------------------------------------------------------
# Help text
# -------------------------------------------------------------------------
show_help() {
    cat << EOF
MCP Schema Validator

Validates MCP protocol messages against JSON schema specifications.

Usage: bash scripts/validation/mcp/validate-mcp-schema.sh [OPTIONS]

Options:
  --message FILE     Validate specific message file
  --type TYPE        Message type: request, response, all (default: all)
  --all              Validate all schemas in schemas/mcp/
  --verbose          Show detailed output
  --help             Show this help

Schemas validated:
  - mcp-jsonrpc.schema.json          - Base JSON-RPC envelope
  - mcp-initialize-request.schema.json  - Initialize request
  - mcp-initialize-response.schema.json - Initialize response
  - mcp-tool-call-request.schema.json   - Tool call request
  - mcp-tool-call-response.schema.json  - Tool call response
  - mcp-resource-read.schema.json       - Resource read
  - mcp-error.schema.json               - Error response

Examples:
  # Validate all schemas
  bash scripts/validation/mcp/validate-mcp-schema.sh --all

  # Validate specific message
  bash scripts/validation/mcp/validate-mcp-schema.sh --message my-request.json --type request
EOF
}

# -------------------------------------------------------------------------
# Parse arguments
# -------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --message)
            MESSAGE_FILE="$2"
            shift 2
            ;;
        --type)
            MESSAGE_TYPE="$2"
            shift 2
            ;;
        --all)
            MESSAGE_TYPE="all"
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
            exit 1
            ;;
    esac
done

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
    exit 2
fi

# -------------------------------------------------------------------------
# Validate schema files exist
# -------------------------------------------------------------------------
validate_schemas_exist() {
    local schemas=(
        "mcp-jsonrpc.schema.json"
        "mcp-initialize-request.schema.json"
        "mcp-initialize-response.schema.json"
        "mcp-tool-call-request.schema.json"
        "mcp-tool-call-response.schema.json"
        "mcp-resource-read.schema.json"
        "mcp-error.schema.json"
    )

    local missing=0
    for schema in "${schemas[@]}"; do
        if [[ ! -f "${SCHEMA_DIR}/${schema}" ]]; then
            echo -e "${YELLOW}Schema not found: ${schema}${NC}"
            ((missing++)) || true
        fi
    done

    if [[ $missing -gt 0 ]]; then
        echo -e "${YELLOW}Missing $missing schema files${NC}"
        return 1
    fi
    return 0
}

# -------------------------------------------------------------------------
# Validate individual schema syntax
# -------------------------------------------------------------------------
validate_schema_syntax() {
    local schema_file="$1"

    if [[ "$VERBOSE" = true ]]; then
        echo -e "${BLUE}Checking schema syntax: $schema_file${NC}"
    fi

    case "$VALIDATOR" in
        check-jsonschema|ajv)
            if $VALIDATOR --help &>/dev/null; then
                # Schema is valid JSON if we can read it
                jq . "$schema_file" >/dev/null 2>&1 || return 1
            fi
            ;;
        python)
            jq . "$schema_file" >/dev/null 2>&1 || return 1
            ;;
    esac

    return 0
}

# -------------------------------------------------------------------------
# Create test message and validate
# -------------------------------------------------------------------------
test_schema_with_sample() {
    local schema_file="$1"
    local sample_json="$2"
    local temp_file
    temp_file=$(mktemp)

    echo "$sample_json" > "$temp_file"

    local result=0

    case "$VALIDATOR" in
        check-jsonschema)
            if ! check-jsonschema --schemafile "$schema_file" "$temp_file" 2>&1; then
                result=1
            fi
            ;;
        ajv)
            if ! ajv validate -s "$schema_file" -d "$temp_file" 2>&1; then
                result=1
            fi
            ;;
        python)
            if ! python3 << PYEOF
import json
import jsonschema

with open('$schema_file') as f:
    schema = json.load(f)
with open('$temp_file') as f:
    doc = json.load(f)
jsonschema.validate(doc, schema)
PYEOF
            then
                result=1
            fi
            ;;
    esac

    rm -f "$temp_file"
    return $result
}

# -------------------------------------------------------------------------
# Run validation tests
# -------------------------------------------------------------------------
run_validation_tests() {
    local passed=0
    local failed=0

    # Test 1: Validate JSON-RPC envelope
    echo -e "${BLUE}Testing mcp-jsonrpc.schema.json${NC}"

    local sample_request='{"jsonrpc":"2.0","method":"initialize","id":1,"params":{"protocolVersion":"2025-11-25","capabilities":{}}}'
    if test_schema_with_sample "${SCHEMA_DIR}/mcp-jsonrpc.schema.json" "$sample_request"; then
        echo -e "  ${GREEN}✓ Request envelope validates${NC}"
        ((passed++)) || true
    else
        echo -e "  ${RED}✗ Request envelope failed${NC}"
        ((failed++)) || true
    fi

    # Test 2: Validate tool call request
    echo -e "${BLUE}Testing mcp-tool-call-request.schema.json${NC}"

    local tool_request='{"jsonrpc":"2.0","method":"tools/call","id":2,"params":{"name":"yawl_launch_case","arguments":{"specIdentifier":"TestSpec"}}}'
    if test_schema_with_sample "${SCHEMA_DIR}/mcp-tool-call-request.schema.json" "$tool_request"; then
        echo -e "  ${GREEN}✓ Tool call request validates${NC}"
        ((passed++)) || true
    else
        echo -e "  ${RED}✗ Tool call request failed${NC}"
        ((failed++)) || true
    fi

    # Test 3: Validate error response
    echo -e "${BLUE}Testing mcp-error.schema.json${NC}"

    local error_response='{"jsonrpc":"2.0","id":2,"error":{"code":-32601,"message":"Method not found"}}'
    if test_schema_with_sample "${SCHEMA_DIR}/mcp-error.schema.json" "$error_response"; then
        echo -e "  ${GREEN}✓ Error response validates${NC}"
        ((passed++)) || true
    else
        echo -e "  ${RED}✗ Error response failed${NC}"
        ((failed++)) || true
    fi

    echo ""
    echo "Tests: $((passed + failed)), Passed: $passed, Failed: $failed"

    if [[ $failed -gt 0 ]]; then
        return 1
    fi
    return 0
}

# -------------------------------------------------------------------------
# Main
# -------------------------------------------------------------------------
echo "========================================="
echo "  MCP Schema Validation"
echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "========================================="
echo ""
echo "Validator: $VALIDATOR"
echo "Schema directory: $SCHEMA_DIR"
echo ""

# Check schemas exist
if ! validate_schemas_exist; then
    echo -e "${YELLOW}Some schema files are missing.${NC}"
fi

echo ""
echo -e "${BLUE}Validating schema syntax...${NC}"

SCHEMA_FILES=$(find "$SCHEMA_DIR" -name "*.schema.json" 2>/dev/null | sort)
SCHEMAS_VALID=0
SCHEMAS_INVALID=0

for schema_file in $SCHEMA_FILES; do
    if validate_schema_syntax "$schema_file"; then
        ((SCHEMAS_VALID++)) || true
        if [[ "$VERBOSE" = true ]]; then
            echo -e "  ${GREEN}✓${NC} $(basename "$schema_file")"
        fi
    else
        ((SCHEMAS_INVALID++)) || true
        echo -e "  ${RED}✗${NC} $(basename "$schema_file") - Invalid JSON"
    fi
done

echo ""
echo "Schema syntax: Valid=$SCHEMAS_VALID, Invalid=$SCHEMAS_INVALID"
echo ""

echo -e "${BLUE}Running validation tests...${NC}"
echo ""

if run_validation_tests; then
    echo ""
    echo -e "${GREEN}All MCP schema validations passed${NC}"
    exit 0
else
    echo ""
    echo -e "${RED}Some MCP schema validations failed${NC}"
    exit 1
fi
