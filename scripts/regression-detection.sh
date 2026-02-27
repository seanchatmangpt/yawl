#!/bin/bash

# YAWL Performance Regression Detection Script

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# The project root is the directory containing the script's parent
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

echo "🔍 YAWL Performance Regression Detection"
echo "======================================"
echo "Working directory: $PROJECT_ROOT"
echo "Script directory: $SCRIPT_DIR"

# Default files are in project root
BASELINE_FILE="${1:-performance-baseline.json}"
CURRENT_FILE="${2:-benchmark-results.json}"
THRESHOLD_PERCENT="${3:-10}"

echo "Baseline file: $BASELINE_FILE"
echo "Current file: $CURRENT_FILE"
echo "Regression threshold: ${THRESHOLD_PERCENT}%"

# Check files
if [ ! -f "$BASELINE_FILE" ]; then
    echo "❌ Error: Baseline file not found: $BASELINE_FILE"
    exit 1
fi

if [ ! -f "$CURRENT_FILE" ]; then
    echo "❌ Error: Current results file not found: $CURRENT_FILE"
    exit 1
fi

# Check jq
if ! command -v jq &> /dev/null; then
    echo "❌ Error: jq is required but not installed"
    exit 1
fi

REGRESSIONS_FOUND=0
TOTAL_BENCHMARKS=0

echo ""
echo "📊 Comparing performance metrics..."

# Process benchmarks
for benchmark in $(jq -r '.[].benchmark' "$BASELINE_FILE"); do
    TOTAL_BENCHMARKS=$((TOTAL_BENCHMARKS + 1))
    
    BASELINE_VALUE=$(jq -r ".[] | select(.benchmark == \"$benchmark\") | .primaryMetric.score" "$BASELINE_FILE")
    CURRENT_VALUE=$(jq -r ".[] | select(.benchmark == \"$benchmark\") | .primaryMetric.score" "$CURRENT_FILE")
    
    if [ -z "$BASELINE_VALUE" ] || [ -z "$CURRENT_VALUE" ]; then
        echo "⚠️  Warning: Skipping benchmark $benchmark - missing data"
        continue
    fi
    
    # Clean values
    BASELINE_NUM=$(echo "$BASELINE_VALUE" | sed 's/ms//' | sed 's/op//')
    CURRENT_NUM=$(echo "$CURRENT_VALUE" | sed 's/ms//' | sed 's/op//')
    
    if [ -z "$BASELINE_NUM" ] || [ -z "$CURRENT_NUM" ]; then
        echo "⚠️  Warning: Skipping benchmark $benchmark - invalid value"
        continue
    fi
    
    # Calculate percentage change
    if [ "$(echo "$BASELINE_NUM == 0" | bc -l)" = "1" ]; then
        PERCENT_CHANGE="inf"
    else
        PERCENT_CHANGE=$(echo "scale=2; ($CURRENT_NUM - $BASELINE_NUM) / $BASELINE_NUM * 100" | bc)
    fi
    
    # Check for regression
    if [ "$(echo "$PERCENT_CHANGE > $THRESHOLD_PERCENT" | bc -l)" = "1" ]; then
        REGRESSIONS_FOUND=$((REGRESSIONS_FOUND + 1))
        echo "❌ REGRESSION: $benchmark"
        echo "   Baseline: $BASELINE_VALUE"
        echo "   Current:  $CURRENT_VALUE"
        echo "   Change:   ${PERCENT_CHANGE}% increase"
    else
        echo "✅ OK: $benchmark (${PERCENT_CHANGE}% change)"
    fi
done

echo ""
echo "📋 Summary:"
echo "Total benchmarks: $TOTAL_BENCHMARKS"
echo "Regressions: $REGRESSIONS_FOUND"

# Create report
REPORT="performance-regression-report-$(date +%Y%m%d-%H%M%S).json"
echo "{
    \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
    \"total_benchmarks\": $TOTAL_BENCHMARKS,
    \"regressions_found\": $REGRESSIONS_FOUND,
    \"threshold_percent\": $THRESHOLD_PERCENT,
    \"status\": \"$([ $REGRESSIONS_FOUND -gt 0 ] && echo \"REGRESSION\" || echo \"PASS\")\"
}" > "$REPORT"

if [ "$REGRESSIONS_FOUND" -gt 0 ]; then
    echo ""
    echo "🔴 Performance regressions detected!"
    echo "📄 Report: $REPORT"
    exit 1
else
    echo ""
    echo "✅ No regressions detected"
    echo "📄 Report: $REPORT"
    exit 0
fi
