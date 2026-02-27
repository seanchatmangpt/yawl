#!/bin/bash

# YAWL Performance Regression Detection - Example Script
# =====================================================
#
# This script demonstrates how to use the regression detection framework
# in various scenarios.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$PROJECT_ROOT"

echo "YAWL Performance Regression Detection Example"
echo "==========================================="

# Example 1: Basic regression detection
echo
echo "Example 1: Basic Regression Detection"
echo "------------------------------------"
echo "Command: ./scripts/regression-detection.sh --dry-run"
echo "This would:"
echo "1. Run all benchmarks"
echo "2. Compare against most recent baseline"
echo "3. Generate regression report"
echo "4. Exit with code 0 (no regressions) or 2 (regressions found)"

# Example 2: Custom thresholds
echo
echo "Example 2: Custom Thresholds"
echo "-----------------------------"
echo "Command: ./scripts/regression-detection.sh --dry-run -t 10 -p 0.01"
echo "This would use stricter thresholds:"
echo "- 10% regression threshold"
echo "- p-value of 0.01 (more strict significance testing)"

# Example 3: CI/CD mode
echo
echo "Example 3: CI/CD Pipeline Mode"
echo "-------------------------------"
echo "In CI/CD mode, the framework would:"
echo "1. Run benchmarks automatically on PRs"
echo "2. Save results as new baselines"
echo "3. Create GitHub issues for regressions"
echo "4. Send Slack notifications"
echo "5. Exit with code 2 if regressions detected (failing the build)"
echo "Command: ./scripts/regression-detection.sh --ci-mode --save-baseline"

# Example 4: Compare specific files
echo
echo "Example 4: Compare Specific Files"
echo "----------------------------------"
echo "Let's create example files and compare them:"

# Create example baseline file
mkdir -p example/performance/baselines
cat > example/performance/baselines/example-baseline.json << JSON
{
  "benchmarkResults": [
    {
      "benchmark": "org.yawlfoundation.yawl.performance.YAWLEngineBenchmarks",
      "p50": 125.0,
      "p95": 380.0,
      "p99": 520.0,
      "mean": 150.0,
      "stddev": 45.0
    },
    {
      "benchmark": "org.yawlfoundation.yawl.performance.ConcurrencyBenchmarks",
      "p50": 45.0,
      "p95": 180.0,
      "p99": 220.0,
      "mean": 65.0,
      "stddev": 25.0
    }
  ]
}
JSON

# Create example current results
mkdir -p example/performance/results
cat > example/performance/results/example-current.json << JSON
{
  "benchmarkResults": [
    {
      "benchmark": "org.yawlfoundation.yawl.performance.YAWLEngineBenchmarks",
      "p50": 140.0,
      "p95": 450.0,
      "p99": 580.0,
      "mean": 175.0,
      "stddev": 55.0
    },
    {
      "benchmark": "org.yawlfoundation.yawl.performance.ConcurrencyBenchmarks",
      "p50": 50.0,
      "p95": 240.0,
      "p99": 280.0,
      "mean": 85.0,
      "stddev": 35.0
    }
  ]
}
JSON

echo "Comparing example files:"
echo "Command: ./scripts/regression-detection.sh -b example/performance/baselines/example-baseline.json -c example/performance/results/example-current.json"
echo "Output would show regression analysis with 18.4% degradation in YAWLEngineBenchmarks"

# Example 5: Performance trend analysis
echo
echo "Example 5: Performance Trend Analysis"
echo "-----------------------------------"
echo "For long-term trend analysis, you can:"
echo "1. Run regression detection regularly"
echo "2. Collect historical baselines"
echo "3. Analyze trends over time"
echo "4. Identify gradual degradation"
echo ""
echo "Example workflow:"
echo "- Weekly regression checks (scheduled)"
echo "- Monthly trend analysis"
echo "- Quarterly optimization reviews"
echo "Command: ./scripts/regression-detection.sh --threshold 5 --save-baseline"

# Example 6: Optimizing for speed
echo
echo "Example 6: Performance Optimization"
echo "----------------------------------"
echo "For performance optimization:"
echo "1. Establish baseline: ./scripts/regression-detection.sh --save-baseline"
echo "2. Identify optimization opportunities from report"
echo "3. Implement changes in code"
echo "4. Compare against new baseline: ./scripts/regression-detection.sh --threshold 5"

echo
echo "For more information, see:"
echo "- scripts/regression-detection/README.md"
echo "- docs/v6/latest/performance/baseline-metrics.md"
echo -e "\n🎯 Ready to detect performance regressions in YAWL!"
