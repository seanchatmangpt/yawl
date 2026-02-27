#!/bin/bash

# YAWL Performance Regression Detection Framework Demo
# ===================================================
#
# This script demonstrates the regression detection framework with simulated data.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$PROJECT_ROOT"

echo "🔍 YAWL Performance Regression Detection Framework Demo"
echo "========================================================"
echo

# Ensure directories exist
mkdir -p performance/baselines performance/results performance/reports

# Create a baseline (simulated)
cat > performance/baselines/baseline-demo.json << JSON
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
      "benchmark": "org.yawlfoundation.yawl.performance.WorkflowPatternBenchmarks",
      "p50": 45.0,
      "p95": 180.0,
      "p99": 220.0,
      "mean": 65.0,
      "stddev": 25.0
    },
    {
      "benchmark": "org.yawlfoundation.yawl.performance.ConcurrencyBenchmarks",
      "p50": 35.0,
      "p95": 150.0,
      "p99": 190.0,
      "mean": 55.0,
      "stddev": 20.0
    },
    {
      "benchmark": "org.yawlfoundation.yawl.performance.MemoryBenchmarks",
      "p50": 250.0,
      "p95": 450.0,
      "p99": 550.0,
      "mean": 300.0,
      "stddev": 75.0
    }
  ]
}
JSON

echo "Step 1: Created baseline performance data"
echo "📁 baseline-demo.json"
echo

# Scenario 1: No regression (good performance)
echo "Scenario 1: No Regression - Performance within acceptable range"
echo "--------------------------------------------------------------"

cat > performance/results/current-good.json << JSON
{
  "benchmarkResults": [
    {
      "benchmark": "org.yawlfoundation.yawl.performance.YAWLEngineBenchmarks",
      "p50": 130.0,
      "p95": 390.0,
      "p99": 530.0,
      "mean": 155.0,
      "stddev": 47.0
    },
    {
      "benchmark": "org.yawlfoundation.yawl.performance.WorkflowPatternBenchmarks",
      "p50": 47.0,
      "p95": 185.0,
      "p99": 225.0,
      "mean": 67.0,
      "stddev": 26.0
    },
    {
      "benchmark": "org.yawlfoundation.yawl.performance.ConcurrencyBenchmarks",
      "p50": 36.0,
      "p95": 155.0,
      "p99": 195.0,
      "mean": 57.0,
      "stddev": 21.0
    },
    {
      "benchmark": "org.yawlfoundation.yawl.performance.MemoryBenchmarks",
      "p50": 255.0,
      "p95": 460.0,
      "p99": 560.0,
      "mean": 305.0,
      "stddev": 78.0
    }
  ]
}
JSON

echo "📁 current-good.json (simulated performance data with slight improvement)"
echo

echo "Running regression detection..."
if ./scripts/regression-detection.sh -b performance/baselines/baseline-demo.json \
  -c performance/results/current-good.json \
  -o performance/reports/good-performance-report.md \
  -q; then
  echo "✅ No regressions detected!"
  echo "📄 Report: performance/reports/good-performance-report.md"
else
  echo "❌ Unexpected: regression detected in good performance"
fi

echo

# Scenario 2: Regression detected
echo "Scenario 2: Regression Detected - Performance degradation"
echo "--------------------------------------------------------"

cat > performance/results/current-bad.json << JSON
{
  "benchmarkResults": [
    {
      "benchmark": "org.yawlfoundation.yawl.performance.YAWLEngineBenchmarks",
      "p50": 200.0,
      "p95": 500.0,
      "p99": 680.0,
      "mean": 250.0,
      "stddev": 80.0
    },
    {
      "benchmark": "org.yawlfoundation.yawl.performance.WorkflowPatternBenchmarks",
      "p50": 80.0,
      "p95": 300.0,
      "p99": 380.0,
      "mean": 120.0,
      "stddev": 60.0
    },
    {
      "benchmark": "org.yawlfoundation.yawl.performance.ConcurrencyBenchmarks",
      "p50": 60.0,
      "p95": 250.0,
      "p99": 320.0,
      "mean": 95.0,
      "stddev": 50.0
    },
    {
      "benchmark": "org.yawlfoundation.yawl.performance.MemoryBenchmarks",
      "p50": 400.0,
      "p95": 700.0,
      "p99": 850.0,
      "mean": 500.0,
      "stddev": 120.0
    }
  ]
}
JSON

echo "📁 current-bad.json (simulated performance data with significant degradation)"
echo

echo "Running regression detection with lower threshold..."
if ./scripts/regression-detection.sh -b performance/baselines/baseline-demo.json \
  -c performance/results/current-bad.json \
  -o performance/reports/bad-performance-report.md \
  -t 25 \
  -p 0.05 \
  -q; then
  echo "❌ Unexpected: No regressions detected!"
else
  echo "✅ Regressions detected as expected!"
  echo "📄 Report: performance/reports/bad-performance-report.md"
fi

echo

# Scenario 3: CI mode simulation
echo "Scenario 3: CI/CD Integration Simulation"
echo "----------------------------------------"

echo "Simulating CI/CD pipeline with auto-save baseline..."
echo "Command: ./scripts/regression-detection.sh --ci-mode --save-baseline --cleanup-old --dry-run -q"

echo "In real CI/CD:"
echo "1. Benchmarks would run automatically"
echo "2. Results compared against baseline"
echo "3. If regressions found:"
echo "   - GitHub issue created"
echo "   - Slack notification sent"
echo "   - Build would fail"
echo "4. If no regressions:"
echo "   - Results saved as new baseline"
echo "   - Build passes"
echo

# Show statistical analysis
echo "Statistical Analysis Demo"
echo "-------------------------"

echo "T-Test calculation example:"
cat > temp-analysis.json << JSON
{
  "baseline": [125, 130, 128, 135, 132],
  "current": [200, 210, 195, 205, 198]
}
JSON

echo "Baseline mean: $(jq '.baseline | add/length' temp-analysis.json)"
echo "Current mean: $(jq '.current | add/length' temp-analysis.json)"
echo "Percentage increase: $(( ($(jq '.current | add/length' temp-analysis.json) - $(jq '.baseline | add/length' temp-analysis.json)) * 100 / $(jq '.baseline | add/length' temp-analysis.json) ))%"

rm -f temp-analysis.json

echo
echo "🎯 Regression Detection Framework Demo Complete!"
echo
echo "Next steps:"
echo "1. Try: ./scripts/regression-detection.sh --dry-run"
echo "2. Read: scripts/regression-detection/README.md"
echo "3. View: performance/reports/*.md"
echo "4. Integrate: .github/workflows/performance-regression.yml"
