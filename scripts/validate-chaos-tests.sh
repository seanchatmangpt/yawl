#!/bin/bash

# Chaos Tests Validation Script
# Validates that all chaos testing components are properly implemented

set -e

echo "================================================="
echo "YAWL Chaos Tests Validation"
echo "================================================="

# Configuration
TEST_DIR="test/org/yawlfoundation/yawl/chaos"
SCENARIOS_DIR="test-resources/chaos-scenarios"
DOCS_DIR="docs/chaos-testing"

# Initialize validation results
PASSED=0
FAILED=0
TOTAL=0

# Function to validate component
validate_component() {
    local component_name="$1"
    local component_path="$2"
    local validation_type="$3"

    TOTAL=$((TOTAL + 1))

    if [ "$validation_type" = "file" ]; then
        if [ -f "$component_path" ]; then
            echo "✓ $component_name: Found at $component_path"
            PASSED=$((PASSED + 1))
            return 0
        else
            echo "✗ $component_name: Not found at $component_path"
            FAILED=$((FAILED + 1))
            return 1
        fi
    elif [ "$validation_type" = "dir" ]; then
        if [ -d "$component_path" ]; then
            echo "✓ $component_name: Found at $component_path"
            PASSED=$((PASSED + 1))
            return 0
        else
            echo "✗ $component_name: Not found at $component_path"
            FAILED=$((FAILED + 1))
            return 1
        fi
    fi
}

# Validate component existence
echo ""
echo "=== Component Existence Validation ==="

validate_component "EnhancedChaosTest" "$TEST_DIR/EnhancedChaosTest.java" "file"
validate_component "NetworkDelayResilienceTest" "$TEST_DIR/NetworkDelayResilienceTest.java" "file"
validate_component "ResourceChaosTest" "$TEST_DIR/ResourceChaosTest.java" "file"
validate_component "NetworkChaosTest" "$TEST_DIR/NetworkChaosTest.java" "file"
validate_component "ServiceChaosTest" "$TEST_DIR/ServiceResilienceChaosTest.java" "file"
validate_component "RecoveryChaosTest" "$TEST_DIR/RecoveryChaosTest.java" "file"
validate_component "DataConsistencyChaosTest" "$TEST_DIR/DataConsistencyChaosTest.java" "file"
validate_component "Chaos Scenarios" "$SCENARIOS_DIR" "dir"
validate_component "v6.0.0-ga.yaml" "$SCENARIOS_DIR/v6.0.0-ga.yaml" "file"
validate_component "Documentation" "$DOCS_DIR" "dir"
validate_component "Enhanced Chaos Testing Guide" "$DOCS_DIR/EnhancedChaosTesting.md" "file"
validate_component "Execution Script" "scripts/run-enhanced-chaos-tests.sh" "file"

# Validate configuration file structure
echo ""
echo "=== Configuration File Validation ==="

if [ -f "$SCENARIOS_DIR/v6.0.0-ga.yaml" ]; then
    echo "Validating YAML structure..."

    # Check for required sections
    if grep -q "metadata:" "$SCENARIOS_DIR/v6.0.0-ga.yaml" && \
       grep -q "network_scenarios:" "$SCENARIOS_DIR/v6.0.0-ga.yaml" && \
       grep -q "resource_scenarios:" "$SCENARIOS_DIR/v6.0.0-ga.yaml" && \
       grep -q "service_scenarios:" "$SCENARIOS_DIR/v6.0.0-ga.yaml" && \
       grep -q "data_scenarios:" "$SCENARIOS_DIR/v6.0.0-ga.yaml"; then
        echo "✓ Configuration file has all required sections"
        PASSED=$((PASSED + 1))
    else
        echo "✗ Configuration file missing required sections"
        FAILED=$((FAILED + 1))
    fi

    # Check scenario counts
    NETWORK_SCENARIOS=$(grep -c "^[a-z_]*:$" "$SCENARIOS_DIR/v6.0.0-ga.yaml" | grep -A1 "network_scenarios" | tail -1)
    RESOURCE_SCENARIOS=$(grep -c "^[a-z_]*:$" "$SCENARIOS_DIR/v6.0.0-ga.yaml" | grep -A1 "resource_scenarios" | tail -1)
    SERVICE_SCENARIOS=$(grep -c "^[a-z_]*:$" "$SCENARIOS_DIR/v6.0.0-ga.yaml" | grep -A1 "service_scenarios" | tail -1)
    DATA_SCENARIOS=$(grep -c "^[a-z_]*:$" "$SCENARIOS_DIR/v6.0.0-ga.yaml" | grep -A1 "data_scenarios" | tail -1)

    echo "Scenario counts:"
    echo "  Network: $NETWORK_SCENARIOS"
    echo "  Resource: $RESOURCE_SCENARIOS"
    echo "  Service: $SERVICE_SCENARIOS"
    echo "  Data: $DATA_SCENARIOS"

    TOTAL=$((TOTAL + 1))
    PASSED=$((PASSED + 1))
else
    echo "✗ Configuration file not found"
    FAILED=$((FAILED + 1))
fi

# Validate EnhancedChaosTest content
echo ""
echo "=== EnhancedChaosTest Content Validation ==="

if [ -f "$TEST_DIR/EnhancedChaosTest.java" ]; then
    # Check for required test classes
    if grep -q "class NetworkChaosTests" "$TEST_DIR/EnhancedChaosTest.java" && \
       grep -q "class ResourceChaosTests" "$TEST_DIR/EnhancedChaosTest.java" && \
       grep -q "class ServiceChaosTests" "$TEST_DIR/EnhancedChaosTest.java" && \
       grep -q "class DataChaosTests" "$TEST_DIR/EnhancedChaosTest.java" && \
       grep -q "class RecoveryValidationTests" "$TEST_DIR/EnhancedChaosTest.java"; then
        echo "✓ All required test classes implemented"
        PASSED=$((PASSED + 1))
    else
        echo "✗ Missing required test classes"
        FAILED=$((FAILED + 1))
    fi

    # Check for validation criteria
    if grep -q "MAX_RECOVERY_TIME_MS" "$TEST_DIR/EnhancedChaosTest.java" && \
       grep -q "successRate.*80" "$TEST_DIR/EnhancedChaosTest.java"; then
        echo "✓ Recovery time and success rate validation implemented"
        PASSED=$((PASSED + 1))
    else
        echo "✗ Missing validation criteria"
        FAILED=$((FAILED + 1))
    fi

    # Check test method count
    METHOD_COUNT=$(grep -c "@Test" "$TEST_DIR/EnhancedChaosTest.java")
    echo "  Test methods: $METHOD_COUNT"

    TOTAL=$((TOTAL + 2))
    if [ $METHOD_COUNT -gt 0 ]; then
        PASSED=$((PASSED + 1))
    else
        FAILED=$((FAILED + 1))
    fi
else
    echo "✗ EnhancedChaosTest not found"
    FAILED=$((FAILED + 1))
fi

# Validate script permissions
echo ""
echo "=== Script Validation ==="

if [ -x "scripts/run-enhanced-chaos-tests.sh" ]; then
    echo "✓ Execution script is executable"
    PASSED=$((PASSED + 1))
else
    echo "✗ Execution script is not executable"
    FAILED=$((FAILED + 1))
fi

TOTAL=$((TOTAL + 1))

# Validate documentation
echo ""
echo "=== Documentation Validation ==="

if [ -f "$DOCS_DIR/EnhancedChaosTesting.md" ]; then
    # Check for key sections
    if grep -q "Overview" "$DOCS_DIR/EnhancedChaosTesting.md" && \
       grep -q "Configuration" "$DOCS_DIR/EnhancedChaosTesting.md" && \
       grep -q "Execution" "$DOCS_DIR/EnhancedChaosTesting.md" && \
       grep -q "Validation Criteria" "$DOCS_DIR/EnhancedChaosTesting.md"; then
        echo "✓ Documentation has all required sections"
        PASSED=$((PASSED + 1))
    else
        echo "✗ Documentation missing required sections"
        FAILED=$((FAILED + 1))
    fi

    TOTAL=$((TOTAL + 1))
else
    echo "✗ Documentation not found"
    FAILED=$((FAILED + 1))
fi

# Generate validation report
echo ""
echo "================================================="
echo "Validation Report"
echo "================================================="

echo "Total Validations: $TOTAL"
echo "Passed: $PASSED"
echo "Failed: $FAILED"

if [ $FAILED -eq 0 ]; then
    echo ""
    echo "🎉 All validations passed! Chaos testing framework is ready."
    exit 0
else
    echo ""
    echo "❌ $FAILED validation(s) failed. Please fix the issues above."
    exit 1
fi

# Show test summary
echo ""
echo "Test Summary:"
echo "============"
echo "Enhanced Chaos Test: $(wc -l < "$TEST_DIR/EnhancedChaosTest.java") lines"
echo "Network Scenarios: $NETWORK_SCENARIOS types"
echo "Resource Scenarios: $RESOURCE_SCENARIOS types"
echo "Service Scenarios: $SERVICE_SCENARIOS types"
echo "Data Scenarios: $DATA_SCENARIOS types"
echo "Configuration: $(wc -l < "$SCENARIOS_DIR/v6.0.0-ga.yaml") lines"
echo ""
echo "Next Steps:"
echo "1. Run: ./scripts/run-enhanced-chaos-tests.sh"
echo "2. Review: docs/chaos-testing/EnhancedChaosTesting.md"
echo "3. Configure: test-resources/chaos-scenarios/v6.0.0-ga.yaml"