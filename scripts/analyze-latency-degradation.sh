#!/bin/bash
#
# YAWL Latency Degradation Curve Analysis
#
# Analyzes latency percentile data from stress tests to answer:
# "How does latency scale with case count?"
#
# Output:
#  - degradation-{OPERATION}-{timestamp}.json (latency curves)
#  - degradation-summary-{timestamp}.json (cliff analysis)
#  - degradation-trends-{timestamp}.md (trend report)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../" && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/degradation-analysis"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Create combined data file
COMBINED_DATA="/tmp/combined-latency-${TIMESTAMP}.json"

echo "=================================================="
echo "YAWL Latency Degradation Analysis"
echo "=================================================="
echo "Timestamp: $TIMESTAMP"
echo "Output: $OUTPUT_DIR"
echo ""

# 1. Collect and combine latency percentile files
echo "1. Collecting and combining latency percentile files..."
LATENCY_FILES=$(find "$PROJECT_ROOT" -maxdepth 1 -name "latency-percentiles-*.json" -type f 2>/dev/null | sort)

if [ -z "$LATENCY_FILES" ]; then
    echo "ERROR: No latency percentile files found!"
    exit 1
fi

FILE_COUNT=$(echo "$LATENCY_FILES" | wc -l)
echo "   Found $FILE_COUNT latency data file(s)"
for f in $LATENCY_FILES; do
    echo "   - $(basename $f)"
done

# Combine all JSON files using jq
jq -s 'add' $LATENCY_FILES > "$COMBINED_DATA"
echo "   Combined into: $(basename $COMBINED_DATA)"
echo ""

# 2. Generate degradation curves for each operation
echo "2. Generating degradation curves by operation type..."

OPERATIONS=("CASE_LAUNCH" "WORK_ITEM_CHECKOUT" "WORK_ITEM_COMPLETE" "TASK_EXECUTION")

for operation in "${OPERATIONS[@]}"; do
    echo "   Processing: $operation"
    
    OUTPUT_FILE="$OUTPUT_DIR/degradation-${operation}-${TIMESTAMP}.json"
    
    # Use jq to filter and process data
    jq --arg op "$operation" '
        [.[] | select(.operation == $op)] |
        sort_by(.case_count) |
        {
            operation: .[0].operation,
            scenario_type: .[0].scenario,
            case_counts: [.[] | .case_count],
            p50_latencies_ms: [.[] | .p50_ms],
            p95_latencies_ms: [.[] | .p95_ms],
            p99_latencies_ms: [.[] | .p99_ms],
            samples: length
        }
    ' "$COMBINED_DATA" > "$OUTPUT_FILE"
    
    echo "      Wrote: $(basename $OUTPUT_FILE)"
done

echo ""

# 3. Generate summary with degradation factors
echo "3. Analyzing degradation factors (peak / baseline)..."

SUMMARY_FILE="$OUTPUT_DIR/degradation-summary-${TIMESTAMP}.json"

jq '
    [.[] | {operation, case_count, p95_ms}] |
    group_by(.operation) |
    map({
        operation: .[0].operation,
        baseline_case_count: (.[0] | .case_count),
        baseline_p95_ms: (.[0] | .p95_ms),
        peak_case_count: (.[-1] | .case_count),
        peak_p95_ms: (.[-1] | .p95_ms),
        degradation_factor: ((.[-1] | .p95_ms) / (.[0] | .p95_ms) | (. * 100 | round) / 100),
        is_significant: ((.[-1] | .p95_ms) / (.[0] | .p95_ms) > 2.0),
        data_points: length
    }) |
    sort_by(.degradation_factor) | reverse
' "$COMBINED_DATA" > "$SUMMARY_FILE"

echo "   Wrote: $(basename $SUMMARY_FILE)"
echo ""

# 4. Detect cliff points (where degradation > 1.5x in one interval)
echo "4. Detecting performance cliffs (>1.5x degradation/interval)..."

CLIFF_FILE="$OUTPUT_DIR/cliff-detection-${TIMESTAMP}.json"

jq '
    [.[] | {operation, case_count, p95_ms}] |
    group_by(.operation) |
    map(
        . as $opdata |
        {
            operation: $opdata[0].operation,
            cliffs: (
                $opdata | sort_by(.case_count) |
                [
                    .[] as $curr |
                    if ($curr | IN(.[0])) | not then
                        (.[.[] | .case_count | select(. < $curr.case_count)] | max) as $prev |
                        if $prev and ($curr.p95_ms / $prev.p95_ms > 1.5) then
                            {
                                case_count: $curr.case_count,
                                previous_p95_ms: $prev.p95_ms,
                                current_p95_ms: $curr.p95_ms,
                                cliff_factor: (($curr.p95_ms / $prev.p95_ms) * 100 | round) / 100
                            }
                        else empty end
                    else empty end
                ]
            )
        } |
        select(.cliffs | length > 0)
    )
' "$COMBINED_DATA" > "$CLIFF_FILE"

CLIFF_COUNT=$(jq '[.[] | .cliffs[]] | length' "$CLIFF_FILE" 2>/dev/null || echo "0")
echo "   Detected $CLIFF_COUNT cliff point(s)"
echo "   Wrote: $(basename $CLIFF_FILE)"
echo ""

# 5. Generate CSV export for charting
echo "5. Exporting data for charting..."

CSV_FILE="$OUTPUT_DIR/latency-curves-${TIMESTAMP}.csv"

cat > "$CSV_FILE" << 'CSV_HEADER'
Case_Count,CASE_LAUNCH_p95_ms,WORK_ITEM_CHECKOUT_p95_ms,WORK_ITEM_COMPLETE_p95_ms,TASK_EXECUTION_p95_ms
CSV_HEADER

jq -r '
    [.[] | {case_count, operation, p95_ms}] |
    group_by(.case_count) |
    map(
        . as $group |
        {
            case_count: $group[0].case_count,
            case_launch: ($group[] | select(.operation == "CASE_LAUNCH") | .p95_ms | . // 0),
            checkout: ($group[] | select(.operation == "WORK_ITEM_CHECKOUT") | .p95_ms | . // 0),
            complete: ($group[] | select(.operation == "WORK_ITEM_COMPLETE") | .p95_ms | . // 0),
            execution: ($group[] | select(.operation == "TASK_EXECUTION") | .p95_ms | . // 0)
        }
    ) |
    sort_by(.case_count) |
    .[] |
    "\(.case_count),\(.case_launch),\(.checkout),\(.complete),\(.execution)"
' "$COMBINED_DATA" >> "$CSV_FILE"

echo "   Wrote: $(basename $CSV_FILE)"
echo ""

# 6. Generate markdown trend report
echo "6. Generating trend analysis report..."

TREND_FILE="$OUTPUT_DIR/degradation-trends-${TIMESTAMP}.md"

cat > "$TREND_FILE" << 'REPORT_EOF'
# YAWL Latency Degradation Analysis Report

**Generated**: $(date)

## Executive Summary

This analysis examines how latency scales with case count across four critical operations:
- **CASE_LAUNCH**: Creating new workflow cases
- **WORK_ITEM_CHECKOUT**: Retrieving work items for execution
- **WORK_ITEM_COMPLETE**: Completing work item execution
- **TASK_EXECUTION**: Task transition latency

---

## Degradation Classification

### Linear Degradation (Good ✓)
- **Pattern**: Latency increases linearly with case count
- **Meaning**: System scales predictably; performance degrades proportionally
- **Target**: p95 latency < 500ms at 1M cases

### Exponential Degradation (Warning ⚠)
- **Pattern**: Latency increases faster than linearly at higher case counts
- **Meaning**: Contention/GC pressure increasing faster than load
- **Investigation**: Lock contention or garbage collection overhead

### Cliff Degradation (Critical ✗)
- **Pattern**: Sudden >1.5x latency jump between case count intervals
- **Meaning**: System hitting capacity limit or resource saturation
- **Threshold**: >1.5x factor increase between consecutive measurements
- **Action**: Optimize bottleneck immediately

---

## Degradation Curves

### Summary Table

REPORT_EOF

# Append degradation summary
jq -r '.[] | "| \(.operation) | \(.baseline_p95_ms)ms @ \(.baseline_case_count)K | \(.peak_p95_ms)ms @ \(.peak_case_count)K | \(.degradation_factor)x | \(if .is_significant then "**YES**" else "no" end) |"' "$SUMMARY_FILE" >> "$TREND_FILE"

cat >> "$TREND_FILE" << 'REPORT_EOF'

---

## Key Findings

REPORT_EOF

# Add summary statistics
echo "" >> "$TREND_FILE"
echo "### Degradation Summary" >> "$TREND_FILE"
echo "" >> "$TREND_FILE"

jq -r '.[] | "- **\(.operation)**: \(.baseline_p95_ms)ms → \(.peak_p95_ms)ms (\(.degradation_factor)x)\n  - Status: \(if .is_significant then "⚠️ SIGNIFICANT" else "✓ Linear" end)"' "$SUMMARY_FILE" >> "$TREND_FILE"

# Add cliff analysis if cliffs detected
if [ "$CLIFF_COUNT" -gt 0 ]; then
    echo "" >> "$TREND_FILE"
    echo "### Performance Cliffs Detected: $CLIFF_COUNT" >> "$TREND_FILE"
    echo "" >> "$TREND_FILE"
    jq -r '.[] | "**\(.operation)**\n" + (.cliffs | map("- Case count \(.case_count): \(.previous_p95_ms)ms → \(.current_p95_ms)ms (\(.cliff_factor)x)") | join("\n"))' "$CLIFF_FILE" >> "$TREND_FILE"
fi

cat >> "$TREND_FILE" << 'REPORT_EOF'

---

## Performance Targets vs Actual

Based on YAWL v6.0.0 performance specification:

| Operation | Target p95 | Measured (100K) | Measured (1M) | Status |
|-----------|-----------|-----------------|---------------|--------|
| CASE_LAUNCH | <500ms | -- | -- | TBD |
| WORK_ITEM_CHECKOUT | <200ms | -- | -- | TBD |
| WORK_ITEM_COMPLETE | <300ms | -- | -- | TBD |
| TASK_EXECUTION | <100ms | -- | -- | TBD |

---

## Recommendations

### If Linear Degradation (<1.5x/interval)
- ✓ System scaling appropriately
- Continue monitoring for breaking points
- No immediate action required

### If Exponential Degradation (1.5-2.0x/interval)
- ⚠️ Investigate contention sources
- Profile YNetRunner locks (YWorkItem, YNetElement access)
- Measure garbage collection overhead
- Consider thread pool tuning

### If Cliff Detected (>2.0x total degradation)
- ✗ System capacity limit reached
- Identify bottleneck (lock, DB, GC)
- Scale horizontally or optimize core subsystem
- Add capacity planning policy

REPORT_EOF

echo "   Wrote: $(basename $TREND_FILE)"
echo ""

# 7. Generate JSON verdict
echo "7. Generating performance verdict..."

VERDICT_FILE="$OUTPUT_DIR/performance-verdict-${TIMESTAMP}.json"

jq '{
    timestamp: "'$(date -Iseconds)'",
    analysis_complete: true,
    file_count: ('$(echo $LATENCY_FILES | wc -w)'),
    total_measurements: (. | length),
    operations_analyzed: ([.[] | .operation] | unique | length),
    case_count_range: {
        min: ([.[] | .case_count] | min),
        max: ([.[] | .case_count] | max)
    },
    degradation_analysis: (
        [.[] | {operation, case_count, p95_ms}] |
        group_by(.operation) |
        map({
            operation: .[0].operation,
            factor: ((.[-1].p95_ms / .[0].p95_ms) * 100 | round) / 100,
            baseline_ms: .[0].p95_ms,
            peak_ms: .[-1].p95_ms,
            is_significant: ((.[-1].p95_ms / .[0].p95_ms) > 2.0)
        })
    )
}' "$COMBINED_DATA" > "$VERDICT_FILE"

echo "   Wrote: $(basename $VERDICT_FILE)"
echo ""

# Cleanup
rm -f "$COMBINED_DATA"

# Final summary
echo "=================================================="
echo "Analysis Complete!"
echo "=================================================="
echo ""
echo "Output Directory: $OUTPUT_DIR"
echo ""
echo "Generated Files:"
ls -lh "$OUTPUT_DIR" | tail -n +2 | awk '{print "  " $9 " (" $5 ")"}'
echo ""

# Print verdict
echo "Key Findings:"
jq -r '.degradation_analysis | .[] | "  \(.operation): \(.factor)x (\(.baseline_ms)ms → \(.peak_ms)ms)"' "$VERDICT_FILE"

echo ""
echo "Interpretation Guide:"
echo "  • <1.2x: Excellent scaling"
echo "  • 1.2-1.5x: Good scaling"
echo "  • 1.5-2.0x: Monitor closely"
echo "  • >2.0x: Critical - action required"
echo ""

