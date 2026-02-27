#!/usr/bin/env bash
# ==========================================================================
# manage-baselines.sh - Baseline Management for YAWL v6.0.0-GA
#
# Manages performance baselines for regression detection and comparison
# Usage:
#   ./manage-baselines.sh [command] [options]
#
# Commands:
#   create      - Create new baseline from current results
#   list        - List available baselines
#   compare     - Compare against baseline
#   clean       - Clean old baselines
#   export      - Export baseline data
#   import      - Import baseline data
#
# Examples:
#   ./manage-baselines.sh create --label "v6.0.0-release"
#   ./manage-baselines.sh compare --baseline-id 20240201-120000
#   ./manage-baselines.sh list --sort date
# ==========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Color codes
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_BLUE='\033[94m'
readonly C_YELLOW='\033[93m'
readonly C_CYAN='\033[96m'
readonly C_MAGENTA='\033[95m'
readonly E_OK='✓'
readonly E_FAIL='✗'

# Default configuration
BASELINES_DIR="benchmark-results/baselines"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RETENTION_DAYS=90
DEFAULT_LABEL="baseline"
DRY_RUN=false
VERBOSE=false

# Parse command line arguments
parse_arguments() {
    local command="$1"
    shift

    case "$command" in
        create)
            create_baseline "$@"
            ;;
        list)
            list_baselines "$@"
            ;;
        compare)
            compare_baselines "$@"
            ;;
        clean)
            clean_baselines "$@"
            ;;
        export)
            export_baselines "$@"
            ;;
        import)
            import_baselines "$@"
            ;;
        help|-h|--help)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown command: $command"
            show_help
            exit 1
            ;;
    esac
}

show_help() {
    cat << EOF
YAWL v6.0.0-GA Baseline Management Script

This script manages performance baselines for regression detection and comparison.

Usage:
  ./manage-baselines.sh <command> [options]

Commands:
  create      - Create new baseline from current results
  list        - List available baselines
  compare     - Compare against baseline
  clean       - Clean old baselines
  export      - Export baseline data
  import      - Import baseline data

Options:
  --label NAME     Baseline label (default: baseline)
  --retention N   Retention period in days (default: 90)
  --dry-run       Show what would be done without executing
  --verbose, -v    Enable verbose output
  --help, -h      Show this help message

Create Options:
  --source DIR    Source directory (default: benchmark-results/latest)
  --profile NAME  Benchmark profile name

List Options:
  --sort FIELD    Sort by: date|label|size (default: date)
  --limit N       Limit results to N entries

Compare Options:
  --baseline ID   Baseline ID to compare against
  --current DIR   Current results directory
  --format FMT    Output format: text|json|html (default: text)
  --threshold N   Percentage threshold for regression (default: 10)

Clean Options:
  --force         Force cleaning without confirmation
  --keep N        Keep N most recent baselines (alternative to retention)

Export Options:
  --output FILE   Output file (default: baselines-export-YYYYMMDD.tar.gz)
  --include PATTERN Include pattern (e.g., "jmh_*")

Import Options:
  --input FILE    Input file
  --overwrite     Overwrite existing baselines

Examples:
  ./manage-baselines.sh create --label "v6.0.0-release"
  ./manage-baselines.sh compare --baseline-id 20240201-120000
  ./manage-baselines.sh list --sort date --limit 10
  ./manage-baselines.sh clean --force
  ./manage-baselines.sh export --include "jmh_*"
EOF
}

# Create new baseline
create_baseline() {
    local label="$DEFAULT_LABEL"
    local source_dir=""
    local profile=""

    while [[ $# -gt 0 ]]; do
        case $1 in
            --label)
                label="$2"
                shift 2
                ;;
            --source)
                source_dir="$2"
                shift 2
                ;;
            --profile)
                profile="$2"
                shift 2
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --verbose|-v)
                VERBOSE=true
                shift
                ;;
            *)
                echo "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    # Set default source directory
    if [[ -z "$source_dir" ]]; then
        if [[ -d "benchmark-results/latest" ]]; then
            source_dir="benchmark-results/latest"
        elif [[ -d "benchmark-results" ]]; then
            # Find most recent results directory
            source_dir=$(find benchmark-results -maxdepth 1 -type d -name "*[0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]" | sort | tail -1)
        else
            echo "${C_RED}ERROR: No benchmark results found${C_RESET}"
            exit 1
        fi
    fi

    if [[ ! -d "$source_dir" ]]; then
        echo "${C_RED}ERROR: Source directory not found: $source_dir${C_RESET}"
        exit 1
    fi

    # Create baseline directory
    local baseline_id="${TIMESTAMP}"
    local baseline_dir="${BASELINES_DIR}/${baseline_id}"
    local baseline_metadata="${baseline_dir}/metadata.json"

    echo "${C_CYAN}Creating baseline: $label${C_RESET}"
    echo "${C_CYAN}Source: $source_dir${C_RESET}"
    echo ""

    if [[ "$DRY_RUN" == true ]]; then
        echo "${C_YELLOW}Dry run - baseline would be created:${C_RESET}"
        echo "  ID: $baseline_id"
        echo "  Label: $label"
        echo "  Source: $source_dir"
        echo "  Location: $baseline_dir"
        return
    fi

    # Create baseline directory structure
    mkdir -p "$baseline_dir"

    # Create metadata
    cat > "$baseline_metadata" << EOF
{
    "baseline_id": "$baseline_id",
    "label": "$label",
    "profile": "$profile",
    "created_at": "$(date -Iseconds)",
    "source_directory": "$source_dir",
    "benchmark_suite": "yawl-v6.0.0-ga",
    "version": "6.0.0-GA",
    "environment": "$(uname -s)-$(uname -m)-$(java -version 2>&1 | head -n1)",
    "retention_days": $RETENTION_DAYS,
    "metrics": {},
    "summary": {}
}
EOF

    # Copy key result files
    local key_files=(
        "jmh/results.json"
        "stress-test-summary.json"
        "chaos-test-summary.json"
        "regression-test-summary.json"
        "performance-test-summary.json"
    )

    for file_pattern in "${key_files[@]}"; do
        local source_file=$(find "$source_dir" -name "${file_pattern##*/}" | head -1)
        if [[ -n "$source_file" ]]; then
            cp "$source_file" "$baseline_dir/"
            echo "${C_GREEN}${E_OK} Copied: $file_pattern${C_RESET}"
        fi
    done

    # Copy processed results if available
    if [[ -d "$source_dir/processed" ]]; then
        cp -r "$source_dir/processed" "$baseline_dir/"
        echo "${C_GREEN}${E_OK} Copied processed results${C_RESET}"
    fi

    # Create baseline summary
    create_baseline_summary "$baseline_dir"

    echo "${C_GREEN}${E_OK} Baseline created successfully${C_RESET}"
    echo "Baseline ID: $baseline_id"
    echo "Location: $baseline_dir"
}

# Create baseline summary
create_baseline_summary() {
    local baseline_dir="$1"
    local summary_file="$baseline_dir/summary.json"

    # Extract key metrics from baseline files
    local summary_data='{"metrics": {}, "totals": {}}'

    # JMH metrics
    if [[ -f "$baseline_dir/results.json" ]]; then
        python3 << EOF > "$summary_file.tmp"
import json
import sys

# Read baseline metadata
with open('$baseline_dir/metadata.json', 'r') as f:
    metadata = json.load(f)

# Initialize summary
summary = {
    "baseline_id": metadata["baseline_id"],
    "label": metadata["label"],
    "created_at": metadata["created_at"],
    "metrics": {},
    "totals": {}
}

# Extract JMH metrics
try:
    with open('$baseline_dir/results.json', 'r') as f:
        jmh_data = json.load(f)

    total_metrics = 0
    total_score = 0

    for benchmark in jmh_data.get('benchmarks', []):
        name = benchmark['benchmark']
        for primary in benchmark.get('primaryMetrics', []):
            metric_name = primary['metric']
            score = primary['score']
            unit = primary['unit']

            if name not in summary['metrics']:
                summary['metrics'][name] = {}

            summary['metrics'][name][metric_name] = {
                'score': score,
                'unit': unit
            }

            total_metrics += 1
            total_score += score

    summary['totals'] = {
        'benchmarks': len(jmh_data.get('benchmarks', [])),
        'metrics': total_metrics,
        'average_score': total_score / total_metrics if total_metrics > 0 else 0
    }

except Exception as e:
    print(f"Error processing JMH data: {e}")

# Write summary
with open('$summary_file', 'w') as f:
    json.dump(summary, f, indent=2)
EOF
        mv "$summary_file.tmp" "$summary_file"
        echo "${C_GREEN}${E_OK} Created baseline summary${C_RESET}"
    fi
}

# List available baselines
list_baselines() {
    local sort_field="date"
    local limit=0

    while [[ $# -gt 0 ]]; do
        case $1 in
            --sort)
                sort_field="$2"
                shift 2
                ;;
            --limit)
                limit="$2"
                shift 2
                ;;
            --verbose|-v)
                VERBOSE=true
                shift
                ;;
            *)
                echo "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    echo "${C_CYAN}Available baselines:${C_RESET}"
    echo ""

    # Find baseline directories
    local baselines=()
    for baseline_dir in "${BASELINES_DIR}"/*; do
        if [[ -d "$baseline_dir" && -f "$baseline_dir/metadata.json" ]]; then
            baselines+=("$baseline_dir")
        fi
    done

    if [[ ${#baselines[@]} -eq 0 ]]; then
        echo "${C_YELLOW}No baselines found${C_RESET}"
        return
    fi

    # Sort baselines
    case "$sort_field" in
        date)
            # Already sorted by directory name (timestamp)
            ;;
        label)
            # Sort by label in metadata
            baselines=($(printf '%s\n' "${baselines[@]}" | \
                python3 -c "
import json, sys, os
baselines = sys.stdin.read().strip().split('\n')
baseline_labels = []
for baseline in baselines:
    if baseline and os.path.exists(baseline + '/metadata.json'):
        with open(baseline + '/metadata.json', 'r') as f:
            metadata = json.load(f)
        baseline_labels.append((baseline, metadata.get('label', '')))
baseline_labels.sort(key=lambda x: x[1])
for baseline, _ in baseline_labels:
    print(baseline)
" | head -${limit:-999999}))
            ;;
        size)
            # Sort by directory size
            baselines=($(printf '%s\n' "${baselines[@]}" | \
                python3 -c "
import os, sys
baselines = sys.stdin.read().strip().split('\n')
baseline_sizes = []
for baseline in baselines:
    if baseline and os.path.exists(baseline):
        size = sum(os.path.getsize(os.path.join(dirpath, filename))
                  for dirpath, dirnames, filenames in os.walk(baseline)
                  for filename in filenames)
        baseline_sizes.append((baseline, size))
baseline_sizes.sort(key=lambda x: x[1], reverse=True)
for baseline, _ in baseline_sizes:
    print(baseline)
" | head -${limit:-999999}))
            ;;
    esac

    # Display baselines
    for baseline_dir in "${baselines[@]}"; do
        if [[ -f "$baseline_dir/metadata.json" ]]; then
            local baseline_id=$(basename "$baseline_dir")
            local label=$(jq -r '.label' "$baseline_dir/metadata.json" 2>/dev/null || echo "unknown")
            local created_at=$(jq -r '.created_at' "$baseline_dir/metadata.json" 2>/dev/null || echo "unknown")
            local profile=$(jq -r '.profile' "$baseline_dir/metadata.json" 2>/dev/null || echo "unknown")

            printf "${C_BLUE}%s${C_RESET} - %s\n" "$baseline_id" "$label"
            printf "  Created: $created_at\n"
            printf "  Profile: $profile\n"
            printf "  Size: $(du -sh "$baseline_dir" | cut -f1)\n"
            echo ""
        fi
    done

    echo "${C_CYAN}Total baselines: ${#baselines[@]}${C_RESET}"
}

# Compare baselines
compare_baselines() {
    local baseline_id=""
    local current_dir=""
    local output_format="text"
    local threshold=10.0

    while [[ $# -gt 0 ]]; do
        case $1 in
            --baseline)
                baseline_id="$2"
                shift 2
                ;;
            --current)
                current_dir="$2"
                shift 2
                ;;
            --format)
                output_format="$2"
                shift 2
                ;;
            --threshold)
                threshold="$2"
                shift 2
                ;;
            --verbose|-v)
                VERBOSE=true
                shift
                ;;
            *)
                echo "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    # Validate baseline ID
    if [[ -z "$baseline_id" ]]; then
        echo "${C_RED}ERROR: Baseline ID required${C_RESET}"
        exit 1
    fi

    local baseline_dir="${BASELINES_DIR}/${baseline_id}"
    if [[ ! -d "$baseline_dir" ]]; then
        echo "${C_RED}ERROR: Baseline not found: $baseline_id${C_RESET}"
        exit 1
    fi

    # Set default current directory
    if [[ -z "$current_dir" ]]; then
        current_dir="benchmark-results/latest"
        if [[ ! -d "$current_dir" ]]; then
            current_dir=$(find benchmark-results -maxdepth 1 -type d -name "*[0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]" | sort | tail -1)
        fi
    fi

    echo "${C_CYAN}Comparing baselines:${C_RESET}"
    echo "${C_CYAN}Baseline: $baseline_id${C_RESET}"
    echo "${C_CYAN}Current: $current_dir${C_RESET}"
    echo "${C_CYAN}Threshold: $threshold%${C_RESET}"
    echo ""

    # Perform comparison
    local comparison_file="${baseline_dir}/comparison-$(date +%Y%m%d-%H%M%S).json"

    python3 << EOF > "$comparison_file"
import json
import sys
import os

# Read baseline metadata
with open('$baseline_dir/metadata.json', 'r') as f:
    baseline_metadata = json.load(f)

baseline_id = baseline_metadata['baseline_id']
baseline_label = baseline_metadata['label']

# Read current results if available
current_data = {}
if os.path.exists('$current_dir/processed/jmh-processed.json'):
    with open('$current_dir/processed/jmh-processed.json', 'r') as f:
        current_data = json.load(f)

# Read baseline summary
baseline_summary = {}
if os.path.exists('$baseline_dir/summary.json'):
    with open('$baseline_dir/summary.json', 'r') as f:
        baseline_summary = json.load(f)

# Create comparison report
comparison = {
    "comparison_id": "$(date +%Y%m%d-%H%M%S)",
    "baseline": {
        "id": baseline_id,
        "label": baseline_label,
        "created_at": baseline_metadata['created_at']
    },
    "current": {
        "directory": "$current_dir",
        "timestamp": "$(date -Iseconds)"
    },
    "threshold": $threshold,
    "regressions": [],
    "improvements": [],
    "no_change": [],
    "summary": {
        "total_metrics": 0,
        "regressions": 0,
        "improvements": 0,
        "no_change": 0
    }
}

# Compare metrics
for benchmark_name, benchmark_data in baseline_summary.get('metrics', {}).items():
    if benchmark_name in current_data.get('jmh_metrics', {}):
        for metric_name, metric_data in benchmark_data.items():
            current_metric = current_data['jmh_metrics'][benchmark_name].get(metric_name, {})

            if current_metric:
                baseline_value = metric_data.get('score', 0)
                current_value = current_metric.get('score', 0)

                if baseline_value > 0:
                    change = ((current_value - baseline_value) / baseline_value) * 100

                    comparison_entry = {
                        "benchmark": benchmark_name,
                        "metric": metric_name,
                        "baseline_value": baseline_value,
                        "current_value": current_value,
                        "change_percent": change,
                        "unit": metric_data.get('unit', '')
                    }

                    if abs(change) > $threshold:
                        if change > 0:
                            comparison["regressions"].append(comparison_entry)
                        else:
                            comparison["improvements"].append(comparison_entry)
                    else:
                        comparison["no_change"].append(comparison_entry)

# Update summary
comparison["summary"] = {
    "total_metrics": len(comparison["regressions"]) + len(comparison["improvements"]) + len(comparison["no_change"]),
    "regressions": len(comparison["regressions"]),
    "improvements": len(comparison["improvements"]),
    "no_change": len(comparison["no_change"])
}

# Write comparison
with open('$comparison_file', 'w') as f:
    json.dump(comparison, f, indent=2)

print("Comparison completed: $comparison_file")
EOF

    # Display results based on format
    case "$output_format" in
        text)
            display_text_comparison "$comparison_file"
            ;;
        json)
            cat "$comparison_file"
            ;;
        html)
            generate_html_comparison "$comparison_file"
            ;;
    esac

    echo "${C_GREEN}${E_OK} Comparison completed${C_RESET}"
}

# Display text comparison
display_text_comparison() {
    local comparison_file="$1"

    if [[ ! -f "$comparison_file" ]]; then
        echo "${C_RED}ERROR: Comparison file not found${C_RESET}"
        return
    fi

    python3 -c "
import json
import sys

with open('$comparison_file', 'r') as f:
    comparison = json.load(f)

print('${C_CYAN}=== Baseline Comparison Report ===${C_RESET}')
print()
print('Baseline: {} ({})'.format(
    comparison['baseline']['id'],
    comparison['baseline']['label']
))
print('Current: {} ({})'.format(
    comparison['current']['directory'],
    comparison['current']['timestamp']
))
print()

print('${C_BLUE}Summary:${C_RESET}')
print('  Total metrics: {}'.format(comparison['summary']['total_metrics']))
print('  Regressions: {}'.format(comparison['summary']['regressions']))
print('  Improvements: {}'.format(comparison['summary']['improvements']))
print('  No change: {}'.format(comparison['summary']['no_change']))
print()

if comparison['regressions']:
    print('${C_RED}🔴 Regressions:${C_RESET}')
    for regression in comparison['regressions']:
        print('  - {}: {} ({}%)'.format(
            regression['benchmark'],
            regression['metric'],
            round(regression['change_percent'], 2)
        ))
    print()

if comparison['improvements']:
    print('${C_GREEN}🟢 Improvements:${C_RESET}')
    for improvement in comparison['improvements']:
        print('  - {}: {} ({}%)'.format(
            improvement['benchmark'],
            improvement['metric'],
            round(improvement['change_percent'], 2)
        ))
    print()

if not comparison['regressions']:
    print('${C_GREEN}✅ No significant regressions detected${C_RESET}')
"
}

# Generate HTML comparison
generate_html_comparison() {
    local comparison_file="$1"

    if [[ ! -f "$comparison_file" ]]; then
        echo "${C_RED}ERROR: Comparison file not found${C_RESET}"
        return
    fi

    local html_file="${comparison_file%.json}.html"

    python3 << EOF > "$html_file"
import json

with open('$comparison_file', 'r') as f:
    comparison = json.load(f)

html = '''
<!DOCTYPE html>
<html>
<head>
    <title>Baseline Comparison Report</title>
    <style>
        body {{ font-family: Arial, sans-serif; margin: 20px; }}
        .header {{ background: #f0f0f0; padding: 20px; border-radius: 8px; }}
        .summary {{ margin: 20px 0; }}
        .regression {{ background: #f8d7da; border: 1px solid #dc3545; padding: 15px; margin: 10px 0; }}
        .improvement {{ background: #d4edda; border: 1px solid #28a745; padding: 15px; margin: 10px 0; }}
        .stable {{ background: #e2e3e5; border: 1px solid #6c757d; padding: 15px; margin: 10px 0; }}
        table {{ width: 100%; border-collapse: collapse; margin: 10px 0; }}
        th, td {{ border: 1px solid #ddd; padding: 8px; text-align: left; }}
        th {{ background: #f2f2f2; }}
    </style>
</head>
<body>
    <div class=\"header\">
        <h1>Baseline Comparison Report</h1>
        <p>Baseline: {} ({})</p>
        <p>Current: {} ({})</p>
    </div>

    <div class=\"summary\">
        <h2>Summary</h2>
        <table>
            <tr><th>Total Metrics</th><td>{}</td></tr>
            <tr><th>Regressions</th><td>{}</td></tr>
            <tr><th>Improvements</th><td>{}</td></tr>
            <tr><th>No Change</th><td>{}</td></tr>
        </table>
    </div>
'''.format(
    comparison['baseline']['id'],
    comparison['baseline']['label'],
    comparison['current']['directory'],
    comparison['current']['timestamp'],
    comparison['summary']['total_metrics'],
    comparison['summary']['regressions'],
    comparison['summary']['improvements'],
    comparison['summary']['no_change']
)

if comparison['regressions']:
    html += '''
    <div class=\"regression\">
        <h2>🔴 Regressions</h2>
        <table>
            <tr><th>Benchmark</th><th>Metric</th><th>Change</th></tr>
'''
    for regression in comparison['regressions']:
        html += '''
            <tr><td>{}</td><td>{}</td><td>{:.2f}%</td></tr>
'''.format(regression['benchmark'], regression['metric'], regression['change_percent'])
    html += '''
        </table>
    </div>
'''

if comparison['improvements']:
    html += '''
    <div class=\"improvement\">
        <h2>🟢 Improvements</h2>
        <table>
            <tr><th>Benchmark</th><th>Metric</th><th>Change</th></tr>
'''
    for improvement in comparison['improvements']:
        html += '''
            <tr><td>{}</td><td>{}</td><td>{:.2f}%</td></tr>
'''.format(improvement['benchmark'], improvement['metric'], improvement['change_percent'])
    html += '''
        </table>
    </div>
'''

html += '''
</body>
</html>
'''

with open('$html_file', 'w') as f:
    f.write(html)

print('HTML report generated: $html_file')
EOF

    echo "${C_GREEN}${E_OK} HTML comparison generated: $html_file${C_RESET}"
}

# Clean old baselines
clean_baselines() {
    local force=false
    local keep=0

    while [[ $# -gt 0 ]]; do
        case $1 in
            --force)
                force=true
                shift
                ;;
            --keep)
                keep="$2"
                shift 2
                ;;
            --verbose|-v)
                VERBOSE=true
                shift
            ;;
            *)
                echo "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    echo "${C_CYAN}Cleaning baselines:${C_RESET}"
    echo ""

    # Find baseline directories
    local baselines=()
    for baseline_dir in "${BASELINES_DIR}"/*; do
        if [[ -d "$baseline_dir" && -f "$baseline_dir/metadata.json" ]]; then
            baselines+=("$baseline_dir")
        fi
    done

    if [[ ${#baselines[@]} -eq 0 ]]; then
        echo "${C_YELLOW}No baselines to clean${C_RESET}"
        return
    fi

    # Sort by creation date
    baselines=($(printf '%s\n' "${baselines[@]}" | python3 -c "
import json, sys, os
baselines = sys.stdin.read().strip().split('\n')
baseline_dates = []
for baseline in baselines:
    if baseline and os.path.exists(baseline + '/metadata.json'):
        with open(baseline + '/metadata.json', 'r') as f:
            metadata = json.load(f)
        created_at = metadata.get('created_at', '1970-01-01T00:00:00')
        baseline_dates.append((baseline, created_at))
baseline_dates.sort(key=lambda x: x[1])
for baseline, _ in baseline_dates:
    print(baseline)
"))

    if [[ $keep -gt 0 ]]; then
        # Keep N most recent baselines
        local to_remove=("${baselines[@]:$keep}")
    else
        # Remove baselines older than retention period
        local cutoff_date=$(date -d "-$RETENTION_DAYS days" -Iseconds)
        local to_remove=()
        for baseline_dir in "${baselines[@]}"; do
            local created_at=$(jq -r '.created_at' "$baseline_dir/metadata.json" 2>/dev/null || echo "1970-01-01T00:00:00")
            if [[ "$created_at" < "$cutoff_date" ]]; then
                to_remove+=("$baseline_dir")
            fi
        done
    fi

    if [[ ${#to_remove[@]} -eq 0 ]]; then
        echo "${C_GREEN}No baselines to remove${C_RESET}"
        return
    fi

    if [[ "$force" == false ]]; then
        echo "The following baselines will be removed:"
        for baseline_dir in "${to_remove[@]}"; do
            local baseline_id=$(basename "$baseline_dir")
            local label=$(jq -r '.label' "$baseline_dir/metadata.json" 2>/dev/null || echo "unknown")
            echo "  - $baseline_id ($label)"
        done
        echo ""
        read -p "Proceed with cleanup? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "Cleanup cancelled"
            return
        fi
    fi

    # Remove baselines
    for baseline_dir in "${to_remove[@]}"; do
        if [[ -d "$baseline_dir" ]]; then
            local baseline_id=$(basename "$baseline_dir")
            rm -rf "$baseline_dir"
            echo "${C_GREEN}${E_OK} Removed: $baseline_id${C_RESET}"
        fi
    done

    echo "${C_GREEN}${E_OK} Cleanup completed${C_RESET}"
}

# Export baselines
export_baselines() {
    local output_file=""
    local include_pattern=""
    local timestamp=$(date +%Y%m%d)

    while [[ $# -gt 0 ]]; do
        case $1 in
            --output)
                output_file="$2"
                shift 2
                ;;
            --include)
                include_pattern="$2"
                shift 2
                ;;
            --verbose|-v)
                VERBOSE=true
                shift
                ;;
            *)
                echo "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    # Set default output file
    if [[ -z "$output_file" ]]; then
        output_file="baselines-export-${timestamp}.tar.gz"
    fi

    echo "${C_CYAN}Exporting baselines:${C_RESET}"
    echo "${C_CYAN}Output: $output_file${C_RESET}"
    echo ""

    # Create temporary directory for export
    local export_dir="/tmp/baselines-export-$timestamp"
    mkdir -p "$export_dir"

    # Copy baselines
    local baselines=()
    for baseline_dir in "${BASELINES_DIR}"/*; do
        if [[ -d "$baseline_dir" && -f "$baseline_dir/metadata.json" ]]; then
            baselines+=("$baseline_dir")
        fi
    done

    for baseline_dir in "${baselines[@]}"; do
        local baseline_id=$(basename "$baseline_dir")

        # Apply include pattern if specified
        if [[ -n "$include_pattern" ]]; then
            # Only copy files matching pattern
            find "$baseline_dir" -name "${include_pattern}" -exec cp -r {} "$export_dir/" \; 2>/dev/null || true
        else
            # Copy entire baseline
            cp -r "$baseline_dir" "$export_dir/"
        fi
    done

    # Create export manifest
    cat > "$export_dir/manifest.json" << EOF
{
    "export_timestamp": "$(date -Iseconds)",
    "exported_by": "$(whoami)@$(hostname)",
    "total_baselines": ${#baselines[@]},
    "baselines": [],
    "include_pattern": "$include_pattern"
}
EOF

    # Add baseline IDs to manifest
    for baseline_dir in "${baselines[@]}"; do
        local baseline_id=$(basename "$baseline_dir")
        local label=$(jq -r '.label' "$baseline_dir/metadata.json" 2>/dev/null || echo "unknown")
        echo "    \"${baseline_id}\": {\"label\": \"${label}\"}," >> "$export_dir/manifest.json"
    done

    # Complete manifest
    sed -i '$ s/,$//' "$export_dir/manifest.json"
    echo "}" >> "$export_dir/manifest.json"

    # Create archive
    if [[ "$VERBOSE" == true ]]; then
        tar -czf "$output_file" -C "$export_dir" .
    else
        tar -czf "$output_file" -C "$export_dir" . > /dev/null 2>&1
    fi

    # Clean up
    rm -rf "$export_dir"

    echo "${C_GREEN}${E_OK} Export completed${C_RESET}"
    echo "Export file: $output_file"
}

# Import baselines
import_baselines() {
    local input_file=""
    local overwrite=false

    while [[ $# -gt 0 ]]; do
        case $1 in
            --input)
                input_file="$2"
                shift 2
            ;;
            --overwrite)
                overwrite=true
                shift
            ;;
            --verbose|-v)
                VERBOSE=true
                shift
            ;;
            *)
                echo "Unknown option: $1"
                exit 1
            ;;
        esac
    done

    if [[ -z "$input_file" ]]; then
        echo "${C_RED}ERROR: Input file required${C_RESET}"
        exit 1
    fi

    if [[ ! -f "$input_file" ]]; then
        echo "${C_RED}ERROR: Input file not found: $input_file${C_RESET}"
        exit 1
    fi

    echo "${C_CYAN}Importing baselines:${C_RESET}"
    echo "${C_CYAN}Input: $input_file${C_RESET}"
    echo ""

    # Create temporary directory for extraction
    local import_dir="/tmp/baselines-import-$(date +%s)"
    mkdir -p "$import_dir"

    # Extract archive
    tar -xzf "$input_file" -C "$import_dir" > /dev/null 2>&1

    # Check manifest
    if [[ ! -f "$import_dir/manifest.json" ]]; then
        echo "${C_RED}ERROR: Invalid export file - manifest not found${C_RESET}"
        rm -rf "$import_dir"
        exit 1
    fi

    # Read manifest
    local manifest=$(python3 -c "import json; print(open('$import_dir/manifest.json').read())")

    # Import baselines
    local imported_count=0
    local skipped_count=0

    for baseline_dir in "$import_dir"/*; do
        if [[ -d "$baseline_dir" && -f "$baseline_dir/metadata.json" ]]; then
            local baseline_id=$(basename "$baseline_dir")
            local target_dir="${BASELINES_DIR}/${baseline_id}"

            if [[ -d "$target_dir" && "$overwrite" == false ]]; then
                echo "${C_YELLOW}Skipping existing baseline: $baseline_id${C_RESET}"
                ((skipped_count++))
            else
                if [[ -d "$target_dir" ]]; then
                    rm -rf "$target_dir"
                fi
                mv "$baseline_dir" "${BASELINES_DIR}/"
                echo "${C_GREEN}${E_OK} Imported baseline: $baseline_id${C_RESET}"
                ((imported_count++))
            fi
        fi
    done

    # Clean up
    rm -rf "$import_dir"

    echo "${C_GREEN}${E_OK} Import completed${C_RESET}"
    echo "Imported: $imported_count baselines"
    if [[ $skipped_count -gt 0 ]]; then
        echo "Skipped: $skipped_count existing baselines"
    fi
}

# Main execution
main() {
    parse_arguments "$@"
}

# Execute main function with all arguments
main "$@"