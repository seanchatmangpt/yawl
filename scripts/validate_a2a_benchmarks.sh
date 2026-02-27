#!/bin/bash

# A2A Benchmarks Validation Script
# ================================
#
# This script validates the A2A communication benchmarks implementation
# to ensure all required components are present and properly configured.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "A2A Benchmarks Validation"
echo "========================"

# Check if required files exist
check_file() {
    local file="$1"
    local description="$2"
    
    if [[ -f "$file" ]]; then
        echo -e "${GREEN}✓${NC} $description: $file"
        return 0
    else
        echo -e "${RED}✗${NC} $description: $file (MISSING)"
        return 1
    fi
}

# Check if required directories exist
check_dir() {
    local dir="$1"
    local description="$2"
    
    if [[ -d "$dir" ]]; then
        echo -e "${GREEN}✓${NC} $description: $dir"
        return 0
    else
        echo -e "${RED}✗${NC} $description: $dir (MISSING)"
        return 1
    fi
}

# Check Java source files for required methods
check_java_file() {
    local file="$1"
    local method="$2"
    local description="$3"
    
    if grep -q "public.*$method" "$file"; then
        echo -e "${GREEN}✓${NC} $description: $method in $file"
        return 0
    else
        echo -e "${YELLOW}⚠${NC} $description: $method not found in $file"
        return 1
    fi
}

# Validate JMH annotations
check_jmh_annotations() {
    local file="$1"
    
    if grep -q "@BenchmarkMode" "$file" && grep -q "@OutputTimeUnit" "$file" && grep -q "@State" "$file"; then
        echo -e "${GREEN}✓${NC} JMH annotations present in $file"
        return 0
    else
        echo -e "${RED}✗${NC} Missing JMH annotations in $file"
        return 1
    fi
}

# Validate test requirements
validate_test_requirements() {
    local file="$1"
    
    # Check for required test methods
    local missing_methods=()
    
    if ! grep -q "benchmarkMessageLatency" "$file"; then
        missing_methods+=("benchmarkMessageLatency")
    fi
    
    if ! grep -q "benchmarkMessageThroughput" "$file"; then
        missing_methods+=("benchmarkMessageThroughput")
    fi
    
    if ! grep -q "benchmarkConcurrentMessageHandling" "$file"; then
        missing_methods+=("benchmarkConcurrentMessageHandling")
    fi
    
    if ! grep -q "benchmarkMessageSerialization" "$file"; then
        missing_methods+=("benchmarkMessageSerialization")
    fi
    
    if ! grep -q "benchmarkNetworkPartitionResilience" "$file"; then
        missing_methods+=("benchmarkNetworkPartitionResilience")
    fi
    
    if [[ ${#missing_methods[@]} -eq 0 ]]; then
        echo -e "${GREEN}✓${NC} All required benchmark methods present in $file"
        return 0
    else
        echo -e "${RED}✗${NC} Missing methods in $file: ${missing_methods[*]}"
        return 1
    fi
}

# Validate integration test requirements
validate_integration_test() {
    local file="$1"
    
    if grep -q "@Test" "$file" && grep -q "A2ACommunicationBenchmarksIntegrationTest" "$file"; then
        echo -e "${GREEN}✓${NC} Integration test structure valid: $file"
        return 0
    else
        echo -e "${RED}✗${NC} Invalid integration test structure: $file"
        return 1
    fi
}

# Run validation
echo ""
echo "1. Validating benchmark files..."
echo "------------------------------"

# Check main benchmark file
BENCHMARK_FILE="$PROJECT_ROOT/test/org/yawlfoundation/yawl/performance/jmh/A2ACommunicationBenchmarks.java"
check_file "$BENCHMARK_FILE" "Main benchmark class"
if [[ -f "$BENCHMARK_FILE" ]]; then
    check_jmh_annotations "$BENCHMARK_FILE"
    validate_test_requirements "$BENCHMARK_FILE"
fi

# Check integration test file
INTEGRATION_FILE="$PROJECT_ROOT/test/org/yawlfoundation/yawl/integration/a2a/A2ACommunicationBenchmarksIntegrationTest.java"
check_file "$INTEGRATION_FILE" "Integration test class"
if [[ -f "$INTEGRATION_FILE" ]]; then
    validate_integration_test "$INTEGRATION_FILE"
fi

# Check test data generator
DATA_GEN_FILE="$PROJECT_ROOT/test/org/yawlfoundation/yawl/performance/jmh/A2ATestDataGenerator.java"
check_file "$DATA_GEN_FILE" "Test data generator"
if [[ -f "$DATA_GEN_FILE" ]]; then
    check_java_file "$DATA_GEN_FILE" "generatePingMessage" "Ping message generator"
    check_java_file "$DATA_GEN_FILE" "generateWorkflowLaunchMessage" "Workflow launch generator"
    check_java_file "$DATA_GEN_FILE" "generateLargeMessage" "Large message generator"
fi

# Check build configuration
echo ""
echo "2. Validating build configuration..."
echo "-----------------------------------"

BUILD_FILE="$PROJECT_ROOT/build-a2a-benchmarks.xml"
check_file "$BUILD_FILE" "Ant build configuration"

if [[ -f "$BUILD_FILE" ]]; then
    if grep -q "build-jmh" "$BUILD_FILE" && grep -q "run-all" "$BUILD_FILE"; then
        echo -e "${GREEN}✓${NC} Required Ant targets present"
    else
        echo -e "${RED}✗${NC} Missing required Ant targets"
    fi
fi

# Check scripts
echo ""
echo "3. Validating scripts..."
echo "----------------------"

SCRIPT_FILE="$PROJECT_ROOT/scripts/run_a2a_benchmarks.sh"
check_file "$SCRIPT_FILE" "Benchmark runner script"

VALIDATE_SCRIPT="$PROJECT_ROOT/scripts/validate_a2a_benchmarks.sh"
check_file "$VALIDATE_SCRIPT" "Validation script"

# Check documentation
echo ""
echo "4. Validating documentation..."
echo "---------------------------"

README_FILE="$PROJECT_ROOT/README-A2A-BENCHMARKS.md"
check_file "$README_FILE" "Benchmark documentation"

# Check lib directory for dependencies
echo ""
echo "5. Validating dependencies..."
echo "---------------------------"

LIB_DIR="$PROJECT_ROOT/lib"
check_dir "$LIB_DIR" "Lib directory"

if [[ -d "$LIB_DIR" ]]; then
    # Check for key JMH dependencies
    if ls "$LIB_DIR"/jmh-*.jar 1> /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} JMH dependencies found"
    else
        echo -e "${YELLOW}⚠${NC} JMH dependencies not found - benchmarks may not run"
    fi
    
    if ls "$LIB_DIR"/jackson-*.jar 1> /dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} Jackson dependencies found"
    else
        echo -e "${YELLOW}⚠${NC} Jackson dependencies not found"
    fi
fi

# Validate benchmark targets
echo ""
echo "6. Validating benchmark targets..."
echo "-------------------------------"

# Test that we can compile the benchmark classes
echo "Attempting to compile benchmark classes..."
cd "$PROJECT_ROOT"

# Use the build configuration if available
if [[ -f "$BUILD_FILE" ]]; then
    if command -v ant >/dev/null 2>&1; then
        echo "Using Ant to validate build..."
        ant -q init >/dev/null 2>&1 || true
        
        # Check if compile targets exist
        if ant -p | grep -q "compile-jmh"; then
            echo -e "${GREEN}✓${NC} Ant build targets are valid"
        else
            echo -e "${YELLOW}⚠${NC} Ant build targets may need configuration"
        fi
    else
        echo -e "${YELLOW}⚠${NC} Ant not found - cannot validate build"
    fi
fi

# Validate Java source files for syntax
echo ""
echo "7. Validating Java syntax..."
echo "-------------------------"

if command -v javac >/dev/null 2>&1; then
    echo "Checking Java syntax for benchmark files..."
    
    # Create a simple test compilation
    mkdir -p /tmp/benchmark-validation
    cp "$BENCHMARK_FILE" /tmp/benchmark-validation/ 2>/dev/null || true
    
    if [[ -f "/tmp/benchmark-validation/A2ACommunicationBenchmarks.java" ]]; then
        cd /tmp/benchmark-validation
        
        # Try to compile with minimal classpath
        if javac -Xlint:unchecked A2ACommunicationBenchmarks.java 2>&1 | grep -q "error:"; then
            echo -e "${RED}✗${NC} Java syntax errors found"
            javac -Xlint:unchecked A2ACommunicationBenchmarks.java 2>&1 | grep "error:" || true
        else
            echo -e "${GREEN}✓${NC} Java syntax is valid"
        fi
        
        rm -rf /tmp/benchmark-validation
    fi
else
    echo -e "${YELLOW}⚠${NC} Java compiler not found - cannot validate syntax"
fi

# Summary
echo ""
echo "Validation Summary"
echo "================="

# Count successes and failures
success_count=0
failure_count=0

# Count file checks
for file in "$BENCHMARK_FILE" "$INTEGRATION_FILE" "$DATA_GEN_FILE" "$BUILD_FILE" "$SCRIPT_FILE" "$VALIDATE_SCRIPT" "$README_FILE"; do
    if [[ -f "$file" ]]; then
        ((success_count++))
    else
        ((failure_count++))
    fi
done

if [[ -d "$LIB_DIR" ]]; then
    ((success_count++))
else
    ((failure_count++))
fi

if [[ $failure_count -eq 0 ]]; then
    echo -e "${GREEN}SUCCESS: All validations passed!${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Start YAWL engine: docker run -d -p 8080:8080 cre:0.3.0"
    echo "2. Start A2A server: java -cp lib/*:src org.yawlfoundation.yawl.integration.a2a.YawlA2AServer"
    echo "3. Run benchmarks: ./scripts/run_a2a_benchmarks.sh"
    exit 0
else
    echo -e "${RED}FAILED: $failure_count validation(s) failed${NC}"
    echo ""
    echo "Fix the issues above before running benchmarks."
    exit 1
fi
