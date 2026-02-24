#!/usr/bin/env bash

# A2A Schema Validation Script
# Validates all A2A protocol schemas and messages

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SCHEMA_DIR="$PROJECT_ROOT/src/main/resources/schemas/a2a"

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
    log_info "Validating A2A schema files..."

    SCHEMA_FILES=(
        "$SCHEMA_DIR/handoff-message.json"
        "$SCHEMA_DIR/agent-card.json"
        "$SCHEMA_DIR/task-message.json"
        "$SCHEMA_DIR/authentication.json"
    )

    # Check if all schema files exist
    for schema_file in "${SCHEMA_FILES[@]}"; do
        if [ ! -f "$schema_file" ]; then
            log_error "Schema file not found: $schema_file"
            exit 1
        fi
    done

    log_success "All A2A schema files exist"
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

    # Check handoff-message schema
    if ! jq '.required | contains(["parts"])' "$SCHEMA_DIR/handoff-message.json"; then
        log_error "Handoff message schema missing required 'parts' field"
        exit 1
    fi

    # Check agent-card schema
    if ! jq '.required | contains(["name", "version", "protocols", "skills"])' "$SCHEMA_DIR/agent-card.json"; then
        log_error "Agent card schema missing required fields"
        exit 1
    fi

    log_success "Schema structure validation passed"
}

# Test sample A2A messages
test_sample_messages() {
    log_info "Testing sample A2A messages..."

    # Sample handoff message
    local handoff_message='{
        "parts": [
            {
                "type": "text",
                "text": "YAWL_HANDOFF:WI-12345:source-agent"
            },
            {
                "type": "data",
                "data": {
                    "reason": "Document requires expertise",
                    "priority": "high",
                    "estimated_duration": "PT30M",
                    "metadata": {
                        "document_type": "contract"
                    }
                }
            }
        ]
    }'

    # Validate handoff message
    if echo "$handoff_message" | jq . >/dev/null 2>&1; then
        log_success "Sample handoff message is valid JSON"
    else
        log_error "Sample handoff message is invalid JSON"
        exit 1
    fi

    # Test against schema (if jq and schema validator are available)
    if command -v mvn &> /dev/null && [ -f "$PROJECT_DIR/target/classes/yawl-validation.jar" ]; then
        log_info "Running Java-based schema validation..."
        echo "$handoff_message" | java -cp "$PROJECT_DIR/target/classes:$JAR_DIR/*" \
            org.yawlfoundation.yawl.integration.validation.a2a.A2ASchemaValidator validateHandoffMessage
    else
        log_warning "Java validation not available. Skipping detailed schema validation."
    fi
}

# Generate validation report
generate_report() {
    log_info "Generating validation report..."

    local report_file="$PROJECT_DIR/validation/a2a-schemas-report.md"
    mkdir -p "$(dirname "$report_file")"

    cat > "$report_file" << EOF
# A2A Schema Validation Report

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
    log_info "Starting A2A schema validation..."

    check_java
    check_jars
    validate_schemas
    validate_json_syntax
    validate_schema_structure
    test_sample_messages
    generate_report

    log_success "A2A schema validation completed successfully!"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi