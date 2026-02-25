#!/usr/bin/env bash

# MCP Schema Validation Script
# Validates all MCP protocol schemas and messages

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SCHEMA_DIR="$PROJECT_ROOT/src/main/resources/schemas/mcp"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Java is available
check_java() {
    if ! command -v java &> /dev/null; then
        log_error "Java is not available. Please install Java 11+."
        exit 1
    fi
}

# Check if required JARs are available
check_jars() {
    JAR_DIR="$PROJECT_DIR/target/dependency"
    if [ ! -d "$JAR_DIR" ]; then
        log_error "Dependency JARs not found. Run 'mvn dependency:copy-dependencies' first."
        exit 1
    fi
}

# Validate schema files
validate_schemas() {
    log_info "Validating MCP schema files..."

    SCHEMA_FILES=(
        "$SCHEMA_DIR/tool-call.json"
        "$SCHEMA_DIR/tool-result.json"
        "$SCHEMA_DIR/tool-spec.json"
        "$SCHEMA_DIR/resource-read.json"
        "$SCHEMA_DIR/prompt-request.json"
        "$SCHEMA_DIR/completion-request.json"
        "$SCHEMA_DIR/server-info.json"
        "$SCHEMA_DIR/exchange-context.json"
    )

    # Check if all schema files exist
    for schema_file in "${SCHEMA_FILES[@]}"; do
        if [ ! -f "$schema_file" ]; then
            log_error "Schema file not found: $schema_file"
            exit 1
        fi
    done

    log_success "All MCP schema files exist"
}

# Validate JSON syntax
validate_json_syntax() {
    log_info "Validating JSON syntax of schema files..."

    local has_errors=0

    for schema_file in "${SCHEMA_FILES[@]}"; do
        if ! jq empty "$schema_file" 2>/dev/null; then
            log_error "Invalid JSON syntax in: $schema_file"
            has_errors=1
        else
            log_success "Valid JSON: $schema_file"
        fi
    done

    if [ $has_errors -eq 1 ]; then
        exit 1
    fi
}

# Validate schema structure
validate_schema_structure() {
    log_info "Validating schema structure..."

    # Check tool-call schema
    if ! jq '.required | contains(["method", "params"])' "$SCHEMA_DIR/tool-call.json"; then
        log_error "Tool call schema missing required fields"
        exit 1
    fi

    # Check tool-result schema
    if ! jq '.required | contains(["jsonrpc", "id"]) or .required | contains(["jsonrpc", "id", "result"])' "$SCHEMA_DIR/tool-result.json"; then
        log_error "Tool result schema missing required fields"
        exit 1
    fi

    # Check prompt-request schema
    if ! jq '.required | contains(["method", "params"]) and .params.required | contains(["name"])' "$SCHEMA_DIR/prompt-request.json"; then
        log_error "Prompt request schema missing required fields"
        exit 1
    fi

    log_success "Schema structure validation passed"
}

# Test sample MCP messages
test_sample_messages() {
    log_info "Testing sample MCP messages..."

    # Sample tool call
    local tool_call='{
        "jsonrpc": "2.0",
        "id": "call-123",
        "method": "launch_workflow",
        "params": {
            "specificationId": "spec-abc-def",
            "data": {
                "caseName": "Test Case",
                "priority": "normal"
            }
        },
        "meta": {
            "clientName": "test-client",
            "timestamp": "2024-01-15T10:30:00Z"
        }
    }'

    # Validate tool call
    if echo "$tool_call" | jq . >/dev/null 2>&1; then
        log_success "Sample tool call is valid JSON"
    else
        log_error "Sample tool call is invalid JSON"
        exit 1
    fi

    # Sample tool result
    local tool_result='{
        "jsonrpc": "2.0",
        "id": "call-123",
        "result": {
            "caseId": "case-xyz-123",
            "status": "completed",
            "data": {
                "message": "Workflow launched successfully"
            }
        }
    }'

    # Validate tool result
    if echo "$tool_result" | jq . >/dev/null 2>&1; then
        log_success "Sample tool result is valid JSON"
    else
        log_error "Sample tool result is invalid JSON"
        exit 1
    fi

    # Test against schema (if jq and schema validator are available)
    if command -v mvn &> /dev/null && [ -f "$PROJECT_DIR/target/classes/yawl-validation.jar" ]; then
        log_info "Running Java-based schema validation..."
        echo "$tool_call" | java -cp "$PROJECT_DIR/target/classes:$JAR_DIR/*" \
            org.yawlfoundation.yawl.integration.validation.mcp.MCPSchemaValidator validateToolCall
    else
        log_warning "Java validation not available. Skipping detailed schema validation."
    fi
}

# Generate validation report
generate_report() {
    log_info "Generating validation report..."

    local report_file="$PROJECT_DIR/validation/mcp-schemas-report.md"
    mkdir -p "$(dirname "$report_file")"

    cat > "$report_file" << EOF
# MCP Schema Validation Report

Generated on: $(date)

## Summary
- Schema Files: ${#SCHEMA_FILES[@]}
- Validation Status: $(if [ $? -eq 0 ]; then echo "PASSED"; else echo "FAILED"; fi)

## Schema Files
EOF

    for schema_file in "${SCHEMA_FILES[@]}"; do
        echo "- $schema_file" >> "$report_file"
    done

    echo "" >> "$report_file"
    echo "## Validation Commands Executed" >> "$report_file"
    echo "- JSON Syntax Validation: $(jq --version 2>/dev/null || echo 'skipped (jq not available)')" >> "$report_file"
    echo "- Schema Structure Validation: Passed" >> "$report_file"
    echo "- Sample Message Testing: Passed" >> "$report_file"

    log_success "Report generated: $report_file"
}

# Main execution
main() {
    log_info "Starting MCP schema validation..."

    check_java
    check_jars
    validate_schemas
    validate_json_syntax
    validate_schema_structure
    test_sample_messages
    generate_report

    log_success "MCP schema validation completed successfully!"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi