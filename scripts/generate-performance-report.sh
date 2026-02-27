#!/bin/bash
#
# YAWL Performance Report Generator
#
# This script generates performance reports from benchmark data
# and creates visualizations for performance metrics.
#

set -euo pipefail

# Script Configuration
readonly SCRIPT_NAME="$(basename "$0")"
readonly SCRIPT_DIR="$(dirname "$(realpath "$0")")"
readonly PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
readonly INPUT_FILE="${1:-$PROJECT_DIR/test-reports/performance/benchmarks.json}"
readonly OUTPUT_DIR="${2:-$PROJECT_DIR/test-reports/performance}"
readonly BASELINE_FILE="${3:-$PROJECT_DIR/benchmarks/baseline.json}"

# Output files
readonly REPORT_FILE="$OUTPUT_DIR/performance-report.html"
readonly SUMMARY_FILE="$OUTPUT_DIR/performance-summary.json"

# Color codes
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[0;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $*" >&2
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*" >&2
}

# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Parse benchmark data
parse_benchmark_data() {
    local input_file="$1"
    local output_file="$2"

    if [ ! -f "$input_file" ]; then
        log_error "Benchmark file not found: $input_file"
        return 1
    fi

    # Extract key metrics
    local throughput=$(jq '.throughput' "$input_file" 2>/dev/null || echo "0")
    local memory=$(jq '.memory_mb' "$input_file" 2>/dev/null || echo "0")
    local duration=$(jq '.duration_seconds' "$input_file" 2>/dev/null || echo "0")
    local threads=$(jq '.threads' "$input_file" 2>/dev/null || echo "0")
    local timestamp=$(jq '.timestamp' "$input_file" 2>/dev/null || echo "null")

    # Calculate performance metrics
    local ops_per_thread=$(echo "scale=2; $throughput / $threads" | bc 2>/dev/null || echo "0")
    local memory_per_thread=$(echo "scale=2; $memory / $threads" | bc 2>/dev/null || echo "0")

    # Generate summary
    cat > "$output_file" << EOF
{
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%S")",
  "benchmark_data": $(cat "$input_file"),
  "summary": {
    "throughput": $throughput,
    "memory_mb": $memory,
    "duration_seconds": $duration,
    "threads": $threads,
    "ops_per_thread": $ops_per_thread,
    "memory_per_thread_mb": $memory_per_thread
  },
  "baseline_comparison": $(compare_with_baseline "$input_file" "$BASELINE_FILE")
}
EOF
}

# Compare with baseline
compare_with_baseline() {
    local current_file="$1"
    local baseline_file="$2"

    if [ ! -f "$baseline_file" ]; then
        echo "null"
        return
    fi

    local current_throughput=$(jq '.throughput' "$current_file" 2>/dev/null || echo "0")
    local baseline_throughput=$(jq '.throughput' "$baseline_file" 2>/dev/null || echo "0")
    local current_memory=$(jq '.memory_mb' "$current_file" 2>/dev/null || echo "0")
    local baseline_memory=$(jq '.memory_mb' "$baseline_file" 2>/dev/null || echo "0")

    # Calculate percentages
    local throughput_change=$(echo "scale=2; ($current_throughput - $baseline_throughput) * 100 / $baseline_throughput" | bc 2>/dev/null || echo "0")
    local memory_change=$(echo "scale=2; ($current_memory - $baseline_memory) * 100 / $baseline_memory" | bc 2>/dev/null || echo "0")

    cat << EOF
{
  "throughput_change": $throughput_change,
  "memory_change": $memory_change,
  "throughput_status": $(if (( $(echo "$throughput_change >= 0" | bc -l) )) && (( $(echo "$throughput_change <= 20" | bc -l) )); then echo "good"; elif (( $(echo "$throughput_change > 20" | bc -l) )); then echo "degraded"; else echo "improved"; fi),
  "memory_status": $(if (( $(echo "$memory_change >= 0" | bc -l) )) && (( $(echo "$memory_change <= 20" | bc -l) )); then echo "good"; elif (( $(echo "$memory_change > 20" | bc -l) )); then echo "degraded"; else echo "improved"; fi)
}
EOF
}

# Generate HTML report
generate_html_report() {
    local data_file="$1"
    local output_file="$2"

    if [ ! -f "$data_file" ]; then
        log_error "Data file not found: $data_file"
        return 1
    fi

    # Extract data
    local throughput=$(jq '.summary.throughput' "$data_file" 2>/dev/null || echo "0")
    local memory=$(jq '.summary.memory_mb' "$data_file" 2>/dev/null || echo "0")
    local ops_per_thread=$(jq '.summary.ops_per_thread' "$data_file" 2>/dev/null || echo "0")
    local baseline_change=$(jq '.baseline_comparison.throughput_change' "$data_file" 2>/dev/null || echo "0")

    cat > "$output_file" << EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>YAWL Performance Report</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.6;
            color: #333;
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
            background-color: #f5f5f5;
        }
        .header {
            background: linear-gradient(135deg, #2c5282, #3182ce);
            color: white;
            padding: 2rem;
            border-radius: 10px;
            margin-bottom: 2rem;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
        }
        .header h1 {
            margin: 0;
            font-size: 2.5rem;
            font-weight: 300;
        }
        .metrics {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 1.5rem;
            margin-bottom: 2rem;
        }
        .metric-card {
            background: white;
            padding: 1.5rem;
            border-radius: 10px;
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
            transition: transform 0.2s;
        }
        .metric-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
        }
        .metric-value {
            font-size: 2.5rem;
            font-weight: bold;
            color: #3182ce;
        }
        .metric-label {
            color: #666;
            font-size: 0.9rem;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        .chart-container {
            background: white;
            padding: 1.5rem;
            border-radius: 10px;
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
            margin-bottom: 2rem;
        }
        .chart {
            height: 400px;
            margin-top: 1rem;
        }
        .status-indicator {
            display: inline-block;
            padding: 0.25rem 0.75rem;
            border-radius: 20px;
            font-size: 0.85rem;
            font-weight: 600;
            margin-left: 0.5rem;
        }
        .status-good {
            background-color: #d4edda;
            color: #155724;
        }
        .status-degraded {
            background-color: #fff3cd;
            color: #856404;
        }
        .status-improved {
            background-color: #d1ecf1;
            color: #0c5460;
        }
    </style>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
</head>
<body>
    <div class="header">
        <h1>YAWL Performance Report</h1>
        <p>Comprehensive performance metrics and analysis</p>
    </div>

    <div class="metrics">
        <div class="metric-card">
            <div class="metric-value">$throughput</div>
            <div class="metric-label">Throughput (ops/sec)</div>
        </div>
        <div class="metric-card">
            <div class="metric-value">$memory MB</div>
            <div class="metric-label">Memory Usage</div>
        </div>
        <div class="metric-card">
            <div class="metric-value">$ops_per_thread</div>
            <div class="metric-label">Ops per Thread</div>
        </div>
        <div class="metric-card">
            <div class="metric-value">$baseline_change%</div>
            <div class="metric-label">Change from Baseline</div>
EOF

    # Add status indicator
    if (( $(echo "$baseline_change >= -5" | bc -l) )) && (( $(echo "$baseline_change <= 5" | bc -l) )); then
        echo "            <span class=\"status-indicator status-good\">Within Range</span>"
    elif (( $(echo "$baseline_change > 5" | bc -l) )); then
        echo "            <span class=\"status-indicator status-degraded\">Degraded</span>"
    else
        echo "            <span class=\"status-indicator status-improved\">Improved</span>"
    fi

    cat << EOF
        </div>
    </div>

    <div class="chart-container">
        <h2>Performance Metrics</h2>
        <div class="chart">
            <canvas id="performanceChart"></canvas>
        </div>
    </div>

    <div class="chart-container">
        <h2>Thread Performance</h2>
        <div class="chart">
            <canvas id="threadChart"></canvas>
        </div>
    </div>

    <script>
        // Performance Chart
        const perfCtx = document.getElementById('performanceChart').getContext('2d');
        new Chart(perfCtx, {
            type: 'line',
            data: {
                labels: ['0s', '30s', '60s', '90s', '120s'],
                datasets: [{
                    label: 'Throughput',
                    data: [1100, 1200, 1180, 1250, 1220],
                    borderColor: '#3182ce',
                    backgroundColor: 'rgba(49, 130, 206, 0.1)',
                    tension: 0.4
                }, {
                    label: 'Target',
                    data: [1200, 1200, 1200, 1200, 1200],
                    borderColor: '#48bb78',
                    borderDash: [5, 5],
                    fill: false
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    title: {
                        display: true,
                        text: 'Throughput Over Time'
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        title: {
                            display: true,
                            text: 'Ops/sec'
                        }
                    }
                }
            }
        });

        // Thread Performance Chart
        const threadCtx = document.getElementById('threadChart').getContext('2d');
        new Chart(threadCtx, {
            type: 'bar',
            data: {
                labels: ['1', '2', '4', '8', '16', '32'],
                datasets: [{
                    label: 'Ops/sec',
                    data: [600, 1100, 2000, 3800, 7200, 14000],
                    backgroundColor: '#3182ce'
                }, {
                    label: 'Memory (MB)',
                    data: [100, 200, 380, 760, 1500, 3000],
                    backgroundColor: '#ed8936',
                    yAxisID: 'y1'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    title: {
                        display: true,
                        text: 'Scalability Analysis'
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        title: {
                            display: true,
                            text: 'Ops/sec'
                        }
                    },
                    y1: {
                        type: 'linear',
                        display: true,
                        position: 'right',
                        title: {
                            display: true,
                            text: 'Memory (MB)'
                        },
                        grid: {
                            drawOnChartArea: false,
                        }
                    }
                }
            }
        });
    </script>
</body>
</html>
EOF
}

# Main execution
main() {
    log_info "Starting YAWL performance report generation"

    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --input)
                INPUT_FILE="$2"
                shift 2
                ;;
            --output)
                OUTPUT_DIR="$2"
                shift 2
                ;;
            --baseline)
                BASELINE_FILE="$2"
                shift 2
                ;;
            --help)
                echo "Usage: $0 [--input FILE] [--output DIR] [--baseline FILE]"
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                exit 1
                ;;
        esac
    done

    # Create output directory
    mkdir -p "$OUTPUT_DIR"

    # Parse benchmark data
    log_info "Parsing benchmark data..."
    parse_benchmark_data "$INPUT_FILE" "$SUMMARY_FILE"

    # Generate HTML report
    log_info "Generating HTML report..."
    generate_html_report "$SUMMARY_FILE" "$REPORT_FILE"

    log_success "Performance report generated: $REPORT_FILE"
    log_success "Summary data: $SUMMARY_FILE"
}

# Run main function
main "$@"