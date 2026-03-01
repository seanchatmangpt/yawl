#!/usr/bin/env bash
# aggregate-gc-comparison.sh — Parse multi-GC benchmark results into comparison table
#
# Reads: results/gc-comparison/bench-<GC>.json
# Outputs:
#   - Markdown comparison table (thesis Appendix D format) to stdout
#   - .claude/receipts/gc-comparison-<date>.json (machine-readable)
#
# Usage:
#   bash scripts/aggregate-gc-comparison.sh
#   bash scripts/aggregate-gc-comparison.sh --receipt-only  # JSON only, no stdout table

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
RESULTS_DIR="$PROJECT_ROOT/results/gc-comparison"
RECEIPT_DIR="$PROJECT_ROOT/.claude/receipts"

RECEIPT_ONLY=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --receipt-only) RECEIPT_ONLY=true ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
    shift
done

# ── Parse JMH JSON for a single GC result file ──────────────────────────────
parse_gc_result() {
    local file="$1"
    local gc="$2"
    
    if [[ ! -f "$file" ]]; then
        echo "N/A|N/A|N/A|N/A"
        return
    fi
    
    python3 - "$file" "$gc" <<'PYEOF'
import sys, json

file = sys.argv[1]
gc   = sys.argv[2]

try:
    with open(file) as f:
        results = json.load(f)
except (FileNotFoundError, json.JSONDecodeError):
    print("N/A|N/A|N/A|N/A")
    sys.exit(0)

throughput    = "N/A"
bytes_agent   = "N/A"
latency_p50   = "N/A"
gc_count      = "N/A"

for r in results:
    name = r.get("benchmark", "")
    score = r.get("primaryMetric", {}).get("score", 0)

    if "messageThroughput" in name:
        throughput = f"{score:,.0f}"
    elif "bytesPerAgent" in name:
        bytes_agent = f"{score:,.0f}"
    elif "schedulingLatency" in name and "Histogram" not in name:
        latency_p50 = f"{score/1000:.1f}µs"
    elif "gcImpact" in name:
        gc_count = f"{score:.0f}"

print(f"{throughput}|{bytes_agent}|{latency_p50}|{gc_count}")
PYEOF
}

# ── Main ───────────────────────────────────────────────────────────────────
main() {
    if [[ ! -d "$RESULTS_DIR" ]]; then
        echo "ERROR: Results directory not found: $RESULTS_DIR" >&2
        echo "Run: bash scripts/benchmark-multi-gc.sh first" >&2
        exit 1
    fi
    
    local date_str
    date_str=$(date +%Y%m%d)
    
    mkdir -p "$RECEIPT_DIR"
    
    # Find all result files
    local gcs=()
    for f in "$RESULTS_DIR"/bench-*.json; do
        [[ -f "$f" ]] || continue
        gc=$(basename "$f" .json | sed 's/bench-//')
        gcs+=("$gc")
    done
    
    if [[ ${#gcs[@]} -eq 0 ]]; then
        echo "ERROR: No bench-*.json files in $RESULTS_DIR" >&2
        exit 1
    fi
    
    # Build comparison data
    declare -A throughputs bytes_agents latencies gc_counts
    for gc in "${gcs[@]}"; do
        local parsed
        parsed="$(parse_gc_result "$RESULTS_DIR/bench-${gc}.json" "$gc")"
        IFS='|' read -r throughputs[$gc] bytes_agents[$gc] latencies[$gc] gc_counts[$gc] <<< "$parsed"
    done
    
    # ── Markdown table (thesis Appendix D format) ───────────────────────────
    if [[ "$RECEIPT_ONLY" == "false" ]]; then
        echo ""
        echo "## GC Comparative Study — YAWL Virtual-Thread Actor Framework"
        echo ""
        echo "| GC Algorithm | Throughput (msg/s) | Bytes/Actor | Latency p50 | GC Events |"
        echo "|---|---|---|---|---|"
        for gc in "${gcs[@]}"; do
            printf "| %-12s | %-18s | %-11s | %-11s | %-9s |\n" \
                "$gc" \
                "${throughputs[$gc]}" \
                "${bytes_agents[$gc]}" \
                "${latencies[$gc]}" \
                "${gc_counts[$gc]}"
        done
        echo ""
        echo "> Generated: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
        echo "> Source: results/gc-comparison/bench-*.json"
        echo ""
    fi
    
    # ── JSON receipt ────────────────────────────────────────────────────────
    local receipt_file="$RECEIPT_DIR/gc-comparison-${date_str}.json"
    
    python3 - "${gcs[@]}" <<PYEOF > "$receipt_file"
import sys, json, datetime

gcs = sys.argv[1:]
results_dir = "$RESULTS_DIR"

comparison = {
    "generated": datetime.datetime.utcnow().isoformat() + "Z",
    "source": "benchmark-multi-gc.sh",
    "thesis_ref": "F1 — GC comparative study",
    "results": {}
}

for gc in gcs:
    try:
        with open(f"{results_dir}/bench-{gc}.json") as f:
            data = json.load(f)
        comparison["results"][gc] = {
            "benchmark_count": len(data),
            "benchmarks": [r.get("benchmark", "").split(".")[-1] for r in data]
        }
        for r in data:
            name = r.get("benchmark", "").split(".")[-1]
            score = r.get("primaryMetric", {}).get("score", 0)
            unit = r.get("primaryMetric", {}).get("scoreUnit", "")
            comparison["results"][gc][name] = {"score": round(score, 2), "unit": unit}
    except Exception as e:
        comparison["results"][gc] = {"error": str(e)}

print(json.dumps(comparison, indent=2))
PYEOF
    
    echo "Receipt: $receipt_file"
}

main "$@"
