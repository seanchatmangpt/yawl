#!/bin/bash

# Simple verification script for regression detection framework

echo "=== YAWL Regression Detection Framework Verification ==="
echo

# Check if all components exist
components=(
    "scripts/regression-detection.sh"
    "scripts/regression-detection/README.md"
    "examples/regression-detection-example.sh"
    "test/regression-detection-test.sh"
    ".github/workflows/performance-regression.yml"
    "docs/v6/latest/performance/baseline-metrics.md"
)

all_good=true

for component in "${components[@]}"; do
    if [[ -f "$component" ]]; then
        echo "✅ $component"
    else
        echo "❌ $component"
        all_good=false
    fi
done

echo

# Check key directories
for dir in performance/baselines performance/results performance/reports; do
    if [[ -d "$dir" ]]; then
        echo "✅ $dir"
    else
        echo "❌ $dir"
        all_good=false
    fi
done

echo

# Check benchmark classes
for benchmark in "test/org/yawlfoundation/yawl/performance/BenchmarkRunner.java" "test/org/yawlfoundation/yawl/performance/jmh/AllBenchmarksRunner.java"; do
    if [[ -f "$benchmark" ]]; then
        echo "✅ $benchmark"
    else
        echo "❌ $benchmark"
        all_good=false
    fi
done

echo

if [[ "$all_good" == true ]]; then
    echo "🎉 All regression detection framework components are in place!"
    echo
    echo "Next steps:"
    echo "1. Run: ./scripts/regression-detection.sh --dry-run"
    echo "2. Read: scripts/regression-detection/README.md"
    echo "3. View: .github/workflows/performance-regression.yml"
else
    echo "❌ Some components are missing."
fi

echo
