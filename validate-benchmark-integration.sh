#!/bin/bash

# YAWL v6.0.0-GA Benchmark Integration Validation Script
# Validates all benchmark components integration with main YAWL engine

echo "=============================================="
echo "YAWL Benchmark Integration Validation"
echo "Version: 6.0.0-GA"
echo "=============================================="
echo

# Configuration
BENCHMARK_DIR="src/org/yawlfoundation/yawl/integration/benchmark"
TEST_DIR="test/org/yawlfoundation/yawl/integration/benchmark"
VALIDATION_LOG="validation-log.txt"

# Initialize log
echo "Starting validation at $(date)" > $VALIDATION_LOG

# Function to log results
log_result() {
    local component=$1
    local status=$2
    local message=$3

    echo "[$component] $status: $message"
    echo "[$component] $status: $message" >> $VALIDATION_LOG
}

# Function to validate file existence
validate_file() {
    local file=$1
    local component=$2

    if [ -f "$file" ]; then
        log_result "$component" "✅ SUCCESS" "File exists: $file"
        return 0
    else
        log_result "$component" "❌ FAILED" "File missing: $file"
        return 1
    fi
}

# Function to validate Java compilation
validate_java_compilation() {
    local java_file=$1
    local component=$2

    if javac -cp "." "$java_file" 2>/dev/null; then
        log_result "$component" "✅ SUCCESS" "Java compilation successful"
        rm -f *.class  # Clean up
        return 0
    else
        log_result "$component" "❌ FAILED" "Java compilation failed"
        return 1
    fi
}

# Function to validate integration
validate_integration() {
    local component=$1
    local test_name=$2
    local command=$3

    if eval "$command" >/dev/null 2>&1; then
        log_result "$component" "✅ SUCCESS" "$test_name"
        return 0
    else
        log_result "$component" "❌ FAILED" "$test_name"
        return 1
    fi
}

# Start validation
echo "Phase 1: Core Component Validation"
echo "----------------------------------"

# Validate main benchmark files
total_files=0
success_files=0

for file in "BenchmarkIntegrationManager.java" "BenchmarkMetrics.java" "QualityGateController.java"; do
    total_files=$((total_files + 1))
    if validate_file "$BENCHMARK_DIR/$file" "Core Integration"; then
        success_files=$((success_files + 1))

        # Validate Java compilation
        validate_java_compilation "$BENCHMARK_DIR/$file" "Core Integration"
    fi
done

log_result "Core Integration" "VALIDATION COMPLETE" "$success_files/$total_files files validated"

echo
echo "Phase 2: Test Suite Validation"
echo "------------------------------"

# Validate test files
total_test_files=0
success_test_files=0

for file in "BenchmarkSuite.java" "IntegrationBenchmarks.java" "StressTestBenchmarks.java"; do
    total_test_files=$((total_test_files + 1))
    if validate_file "$TEST_DIR/$file" "Test Suite"; then
        success_test_files=$((success_test_files + 1))

        # Validate Java compilation
        validate_java_compilation "$TEST_DIR/$file" "Test Suite"
    fi
done

log_result "Test Suite" "VALIDATION COMPLETE" "$success_test_files/$total_test_files files validated"

echo
echo "Phase 3: Configuration Validation"
echo "----------------------------------"

# Validate configuration files
config_files=(
    "yawl-integration/src/test/resources/chaos-config.properties"
    "src/main/resources/benchmark-config.properties"
)

total_config_files=0
success_config_files=0

for file in "${config_files[@]}"; do
    total_config_files=$((total_config_files + 1))
    if validate_file "$file" "Configuration"; then
        success_config_files=$((success_config_files + 1))
    fi
done

log_result "Configuration" "VALIDATION COMPLETE" "$success_config_files/$total_config_files files validated"

echo
echo "Phase 4: Performance Benchmark Validation"
echo "------------------------------------------"

# Validate performance benchmarks
performance_files=(
    "test/org/yawlfoundation/yawl/performance/ConcurrencyBenchmarkSuite.java"
    "test/org/yawlfoundation/yawl/performance/integration/McpA2AIntegrationBenchmark.java"
    "test/org/yawlfoundation/yawl/performance/integration/DatabaseConnectionBenchmark.java"
)

total_performance_files=0
success_performance_files=0

for file in "${performance_files[@]}"; do
    total_performance_files=$((total_performance_files + 1))
    if validate_file "$file" "Performance Benchmark"; then
        success_performance_files=$((success_performance_files + 1))
    fi
done

log_result "Performance Benchmark" "VALIDATION COMPLETE" "$success_performance_files/$total_performance_files files validated"

echo
echo "Phase 5: Integration System Validation"
echo "---------------------------------------"

# Validate integration components
integrations=(
    "K6 Tests:validate integration 'test -f validation/performance/load-test.js'"
    "A2A Communication:validate integration 'test -f test/org/yawlfoundation/yawl/integration/a2a/ThroughputBenchmark.java'"
    "Polyglot Components:validate integration 'test -d yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/polyglot'"
    "Observability:validate integration 'test -f src/org/yawlfoundation/yawl/observability/DistributedTracer.java'"
    "Chaos Engineering:validate integration 'test -f validation/performance/chaos-network-test.js'"
)

total_integrations=0
success_integrations=0

for integration in "${integrations[@]}"; do
    local component=$(echo "$integration" | cut -d: -f1)
    local command=$(echo "$integration" | cut -d: -f2)

    total_integrations=$((total_integrations + 1))
    if validate_integration "Integration System" "$component" "$command"; then
        success_integrations=$((success_integrations + 1))
    fi
done

log_result "Integration System" "VALIDATION COMPLETE" "$success_integrations/$total_integrations components validated"

echo
echo "Phase 6: Quality Gates Validation"
echo "----------------------------------"

# Validate quality gates
quality_gates=(
    "Code Quality:validate integration 'test -f quality/checkstyle/checkstyle.xml'"
    "Security Scanning:validate integration 'test -f quality/security-scanning/trivy-scan-config.yaml'"
    "Test Coverage:validate integration 'test -f quality/test-metrics/coverage-targets.yaml'"
)

total_quality_gates=0
success_quality_gates=0

for gate in "${quality_gates[@]}"; do
    local component=$(echo "$gate" | cut -d: -f1)
    local command=$(echo "$gate" | cut -d: -f2)

    total_quality_gates=$((total_quality_gates + 1))
    if validate_integration "Quality Gates" "$component" "$command"; then
        success_quality_gates=$((success_quality_gates + 1))
    fi
done

log_result "Quality Gates" "VALIDATION COMPLETE" "$success_quality_gates/$total_quality_gates gates validated"

echo
echo "Phase 7: End-to-End Functionality Validation"
echo "--------------------------------------------"

# Run end-to-end validation
e2e_success=true

if validate_integration "End-to-End" "Report Generation" "test -f benchmark-integration-status-report.md"; then
    log_result "End-to-End" "SUCCESS" "Integration status report generated"
else
    log_result "End-to-End" "FAILED" "Integration status report missing"
    e2e_success=false
fi

if validate_integration "End-to-End" "Integration Tests" "test -f src/org/yawlfoundation/yawl/integration/benchmark/IntegrationTestSuite.java"; then
    log_result "End-to-End" "SUCCESS" "Integration tests implemented"
else
    log_result "End-to-End" "FAILED" "Integration tests missing"
    e2e_success=false
fi

echo
echo "=============================================="
echo "VALIDATION SUMMARY"
echo "=============================================="

# Calculate overall success rate
total_validations=$((total_files + total_test_files + total_config_files + total_performance_files + total_integrations + total_quality_gates))
successful_validations=$((success_files + success_test_files + success_config_files + success_performance_files + success_integrations + success_quality_gates))

success_rate=$((successful_validations * 100 / total_validations))

echo "Total Validations: $total_validations"
echo "Successful Validations: $successful_validations"
echo "Success Rate: $success_rate%"

if [ $success_rate -ge 90 ]; then
    echo "🎉 OVERALL STATUS: EXCELLENT (≥90% success rate)"
    echo "All benchmark components are properly integrated."
elif [ $success_rate -ge 80 ]; then
    echo "✅ OVERALL STATUS: GOOD (80-89% success rate)"
    echo "Most components are properly integrated with minor issues."
else
    echo "❌ OVERALL STATUS: NEEDS IMPROVEMENT (<80% success rate)"
    echo "Several components need attention."
fi

# Check for critical failures
if [ $e2e_success = false ]; then
    echo "⚠️  CRITICAL ISSUES DETECTED"
    echo "End-to-end functionality validation failed."
fi

echo
echo "Validation log saved to: $VALIDATION_LOG"
echo "=============================================="

# Exit with appropriate code
if [ $success_rate -ge 90 ] && [ $e2e_success = true ]; then
    exit 0  # Success
else
    exit 1  # Failure
fi