#!/bin/bash

# YAWL v6.0.0-GA Test Infrastructure Validator
# This script validates the test infrastructure without attempting full test execution

TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
REPORT_DIR="test-validation-$TIMESTAMP"
mkdir -p "$REPORT_DIR"

echo "YAWL v6.0.0-GA Test Infrastructure Validator"
echo "=========================================="
echo "Timestamp: $TIMESTAMP"
echo "Report: $REPORT_DIR"
echo ""

# Function to check file existence and basic properties
validate_file() {
    local file="$1"
    local expected_type="$2"
    local description="$3"
    local file_path="$4"

    echo -n "Checking $description: "

    if [ -f "$file_path" ]; then
        echo "✅ EXISTS"
        echo "  Path: $file_path"

        # Check file size
        local size=$(wc -c < "$file_path")
        echo "  Size: $size bytes"

        # Check if it's readable
        if [ -r "$file_path" ]; then
            echo "  Status: ✅ Readable"

            # Check for expected content based on type
            case "$expected_type" in
                "java")
                    if grep -q "public class" "$file_path" 2>/dev/null; then
                        echo "  Content: ✅ Valid Java class"
                    else
                        echo "  Content: ⚠️ Not a valid Java class"
                    fi
                    ;;
                "script")
                    if [ -x "$file_path" ]; then
                        echo "  Executable: ✅ Yes"
                    else
                        echo "  Executable: ⚠️ No"
                    fi
                    ;;
                "config")
                    if grep -q "=" "$file_path" 2>/dev/null; then
                        echo "  Content: ✅ Config format"
                    else
                        echo "  Content: ⚠️ Not config format"
                    fi
                    ;;
                "doc")
                    if grep -q "#" "$file_path" 2>/dev/null || grep -q "===" "$file_path" 2>/dev/null; then
                        echo "  Content: ✅ Documentation"
                    else
                        echo "  Content: ⚠️ Not documentation"
                    fi
                    ;;
            esac
        else
            echo "  Status: ❌ Not readable"
        fi
        return 0
    else
        echo "❌ MISSING"
        return 1
    fi
}

# Validation results
declare -a validation_results
validation_count=0

# === TEST CATEGORY VALIDATIONS ===

echo "### 1. Unit Tests Validation ###"
echo ""

validate_file "SimpleTest.java" "java" "Simple Test" "test/org/yawlfoundation/yawl/performance/SimpleTest.java"
validation_results[$validation_count]=$?
((validation_count++))

validate_file "BenchmarkRunner.java" "java" "Benchmark Runner" "test/org/yawlfoundation/yawl/performance/BenchmarkRunner.java"
validation_results[$validation_count]=$?
((validation_count++))

validate_file "BenchmarkConfig.java" "java" "Benchmark Configuration" "test/org/yawlfoundation/yawl/performance/BenchmarkConfig.java"
validation_results[$validation_count]=$?
((validation_count++))

echo ""
echo "### 2. JMH Benchmarks Validation ###"
echo ""

validate_file "AllBenchmarksRunner.java" "java" "JMH Benchmarks Runner" "test/org/yawlfoundation/yawl/performance/jmh/AllBenchmarksRunner.java"
validation_results[$validation_count]=$?
((validation_count++))

validate_file "validate-benchmarks.sh" "script" "JMH Validation Script" "test/org/yawlfoundation/yawl/performance/jmh/validate-benchmarks.sh"
validation_results[$validation_count]=$?
((validation_count++))

echo ""
echo "### 3. Integration Tests Validation ###"
echo ""

validate_file "IntegrationBenchmarks.java" "java" "Integration Benchmarks" "test/org/yawlfoundation/yawl/integration/benchmark/IntegrationBenchmarks.java"
validation_results[$validation_count]=$?
((validation_count++))

validate_file "BenchmarkSuite.java" "java" "Benchmark Suite" "test/org/yawlfoundation/yawl/integration/benchmark/BenchmarkSuite.java"
validation_results[$validation_count]=$?
((validation_count++))

echo ""
echo "### 4. Chaos Engineering Validation ###"
echo ""

validate_file "run_chaos_tests.sh" "script" "Chaos Test Script" "yawl-integration/src/test/scripts/run_chaos_tests.sh"
validation_results[$validation_count]=$?
((validation_count++))

validate_file "chaos-config.properties" "config" "Chaos Configuration" "yawl-integration/src/test/resources/chaos-config.properties"
validation_results[$validation_count]=$?
((validation_count++))

echo ""
echo "### 5. Production Tests Validation ###"
echo ""

validate_file "CloudScalingBenchmark.java" "java" "Production Load Benchmark" "test/org/yawlfoundation/yawl/performance/production/CloudScalingBenchmark.java"
validation_results[$validation_count]=$?
((validation_count++))

echo ""
echo "### 6. Polyglot Integration Validation ###"
echo ""

validate_file "TPOT2IntegrationBenchmark.java" "java" "TPOT2 Integration" "test/org/yawlfoundation/yawl/graalpy/performance/TPOT2IntegrationBenchmark.java"
validation_results[$validation_count]=$?
((validation_count++))

validate_file "GraalPyMemoryBenchmark.java" "java" "GraalPy Memory Benchmark" "test/org/yawlfoundation/yawl/graalpy/performance/GraalPyMemoryBenchmark.java"
validation_results[$validation_count]=$?
((validation_count++))

echo ""
echo "### 7. Configuration Files Validation ###"
echo ""

validate_file "pom.xml" "config" "Maven Configuration" "test/org/yawlfoundation/yawl/performance/pom.xml"
validation_results[$validation_count]=$?
((validation_count++))

validate_file "BaselineMeasurements.md" "doc" "Performance Baseline" "test/org/yawlfoundation/yawl/performance/BaselineMeasurements.md"
validation_results[$validation_count]=$?
((validation_count++))

# === GENERATE VALIDATION REPORT ===

echo ""
echo "### Validation Summary ###"
echo ""

# Count successful validations
successful=0
for result in "${validation_results[@]}"; do
    if [ $result -eq 0 ]; then
        ((successful++))
    fi
done

total=${#validation_results[@]}
success_rate=$((successful * 100 / total))

echo "Files Checked: $total"
echo "Successful: $successful"
echo "Success Rate: $success_rate%"

# Generate validation report
cat > "$REPORT_DIR/validation-report.json" << EOF
{
  "timestamp": "$TIMESTAMP",
  "validation_summary": {
    "total_files": $total,
    "successful_validations": $successful,
    "success_rate": "$success_rate%"
  },
  "test_categories": {
    "unit_tests": {
      "files_checked": 3,
      "successful": $(for i in {0..2}; do if [ ${validation_results[$i]} -eq 0 ]; then echo "true"; else echo "false"; fi; done | paste -sd, -),
      "critical_missing": $(for i in {0..2}; do if [ ${validation_results[$i]} -ne 0 ]; then echo "true"; else echo "false"; fi; done | grep -c true)
    },
    "jmh_benchmarks": {
      "files_checked": 2,
      "successful": $(for i in {3..4}; do if [ ${validation_results[$i]} -eq 0 ]; then echo "true"; else echo "false"; fi; done | paste -sd, -),
      "critical_missing": $(for i in {3..4}; do if [ ${validation_results[$i]} -ne 0 ]; then echo "true"; else echo "false"; fi; done | grep -c true)
    },
    "integration_tests": {
      "files_checked": 2,
      "successful": $(for i in {5..6}; do if [ ${validation_results[$i]} -eq 0 ]; then echo "true"; else echo "false"; fi; done | paste -sd, -),
      "critical_missing": $(for i in {5..6}; do if [ ${validation_results[$i]} -ne 0 ]; then echo "true"; else echo "false"; fi; done | grep -c true)
    },
    "chaos_engineering": {
      "files_checked": 2,
      "successful": $(for i in {7..8}; do if [ ${validation_results[$i]} -eq 0 ]; then echo "true"; else echo "false"; fi; done | paste -sd, -),
      "critical_missing": $(for i in {7..8}; do if [ ${validation_results[$i]} -ne 0 ]; then echo "true"; else echo "false"; fi; done | grep -c true)
    },
    "production_tests": {
      "files_checked": 1,
      "successful": $(if [ ${validation_results[9]} -eq 0 ]; then echo "true"; else echo "false"; fi),
      "critical_missing": $(if [ ${validation_results[9]} -ne 0 ]; then echo "true"; else echo "false"; fi)
    },
    "polyglot_integration": {
      "files_checked": 2,
      "successful": $(for i in {10..11}; do if [ ${validation_results[$i]} -eq 0 ]; then echo "true"; else echo "false"; fi; done | paste -sd, -),
      "critical_missing": $(for i in {10..11}; do if [ ${validation_results[$i]} -ne 0 ]; then echo "true"; else echo "false"; fi; done | grep -c true)
    },
    "configuration_files": {
      "files_checked": 2,
      "successful": $(for i in {12..13}; do if [ ${validation_results[$i]} -eq 0 ]; then echo "true"; else echo "false"; fi; done | paste -sd, -),
      "critical_missing": $(for i in {12..13}; do if [ ${validation_results[$i]} -ne 0 ]; then echo "true"; else echo "false"; fi; done | grep -c true)
    }
  },
  "infrastructure_status": {
    "overall_health": "$([ $success_rate -ge 80 ] && echo "GOOD" || [ $success_rate -ge 50 ] && echo "FAIR" || echo "POOR")",
    "critical_issues": $([ $success_rate -lt 50 ] && echo "true" || echo "false"),
    "recommended_action": "$(if [ $success_rate -ge 80 ]; then echo 'Proceed with test execution'; else echo 'Fix critical issues first'; fi)"
  }
}
EOF

# Generate HTML report
cat > "$REPORT_DIR/validation-report.html" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>YAWL v6.0.0-GA Test Infrastructure Validation</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background: #2c3e50; color: white; padding: 20px; }
        .summary { background: #ecf0f1; padding: 15px; margin: 20px 0; border-radius: 5px; }
        .status-good { background: #d4edda; color: #155724; padding: 10px; }
        .status-fair { background: #fff3cd; color: #856404; padding: 10px; }
        .status-poor { background: #f8d7da; color: #721c24; padding: 10px; }
        .file-status { margin: 10px 0; padding: 10px; border: 1px solid #ddd; border-radius: 3px; }
        .file-status.success { background: #d4edda; }
        .file-status.error { background: #f8d7da; }
    </style>
</head>
<body>
    <div class="header">
        <h1>YAWL v6.0.0-GA Test Infrastructure Validation</h1>
        <p>Generated: $(date)</p>
        <p>Report Directory: $REPORT_DIR</p>
    </div>

    <div class="summary">
        <h2>Validation Summary</h2>
        <p><strong>Files Checked:</strong> $total</p>
        <p><strong>Successful Validations:</strong> $successful</p>
        <p><strong>Success Rate:</strong> $success_rate%</p>

        <div class="status-$([ $success_rate -ge 80 ] && echo "good" || [ $success_rate -ge 50 ] && echo "fair" || echo "poor")">
            <strong>Overall Status:</strong> $(if [ $success_rate -ge 80 ]; then echo "GOOD - Ready for test execution"; else [ $success_rate -ge 50 ] && echo "FAIR - Some issues need fixing" || echo "POOR - Critical issues must be fixed"; fi)
        </div>
    </div>

    <h2>Validation Details</h2>
    <p>Full validation results available in: <a href="validation-report.json">JSON format</a></p>
</body>
</html>
EOF

echo ""
echo "=== VALIDATION COMPLETE ==="
echo "Files Checked: $total"
echo "Successful: $successful"
echo "Success Rate: $success_rate%"
echo ""
echo "Overall Status: $(if [ $success_rate -ge 80 ]; then echo "GOOD"; elif [ $success_rate -ge 50 ]; then echo "FAIR"; else echo "POOR"; fi)"
echo ""
echo "Reports saved to: $REPORT_DIR/"
echo "  - validation-report.json"
echo "  - validation-report.html"

# Provide recommendations
if [ $success_rate -lt 50 ]; then
    echo ""
    echo "🚨 CRITICAL ISSUES DETECTED"
    echo "Recommendation: Fix critical infrastructure issues before proceeding"
elif [ $success_rate -lt 80 ]; then
    echo ""
    echo "⚠️ SOME ISSUES DETECTED"
    echo "Recommendation: Address remaining issues before full test execution"
else
    echo ""
    echo "✅ INFRASTRUCTURE LOOKS GOOD"
    echo "Recommendation: Ready to proceed with test execution"
fi