#!/usr/bin/env bash
# benchmark-multi-gc.sh — Run AgentBenchmark under multiple GC algorithms
#
# Closes thesis F1: GC comparative study (ZGC vs G1GC vs Shenandoah)
# Results written to: results/gc-comparison/bench-<GC>.json
#
# Usage:
#   bash scripts/benchmark-multi-gc.sh              # all available GCs
#   bash scripts/benchmark-multi-gc.sh --dry-run    # verify structure only
#   bash scripts/benchmark-multi-gc.sh --gc G1GC    # single GC
#
# Requirements:
#   - Java 25 (temurin-25)
#   - Maven 3.x
#   - 2GB+ heap available
#   - benchmark profile in yawl-benchmark/pom.xml

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
RESULTS_DIR="$PROJECT_ROOT/results/gc-comparison"
BENCHMARK_JAR="$PROJECT_ROOT/yawl-benchmark/target/benchmarks.jar"
MODULE="yawl-benchmark"

# ── Argument parsing ────────────────────────────────────────────────────────
DRY_RUN=false
SPECIFIC_GC=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run) DRY_RUN=true ;;
        --gc) SPECIFIC_GC="$2"; shift ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
    shift
done

# ── GC availability detection ───────────────────────────────────────────────
detect_available_gcs() {
    local gcs=()
    
    # ZGC — available in OpenJDK 15+
    if java -XX:+UseZGC -version >/dev/null 2>&1; then
        gcs+=("ZGC")
    fi
    
    # G1GC — available in OpenJDK 7+
    if java -XX:+UseG1GC -version >/dev/null 2>&1; then
        gcs+=("G1GC")
    fi
    
    # Shenandoah — Red Hat build or OpenJDK 17+ with Shenandoah
    if java -XX:+UseShenandoahGC -version >/dev/null 2>&1; then
        gcs+=("Shenandoah")
    fi
    
    echo "${gcs[@]}"
}

# ── JVM flags per GC ───────────────────────────────────────────────────────
gc_flags() {
    local gc="$1"
    case "$gc" in
        ZGC)        echo "-XX:+UseZGC" ;;
        G1GC)       echo "-XX:+UseG1GC" ;;
        Shenandoah) echo "-XX:+UseShenandoahGC" ;;
        Serial)     echo "-XX:+UseSerialGC" ;;
        *)          echo "Unknown GC: $gc" >&2; exit 1 ;;
    esac
}

# ── Main ───────────────────────────────────────────────────────────────────
main() {
    echo "=== YAWL Actor Framework — Multi-GC Benchmark Harness ==="
    echo "Project: $PROJECT_ROOT"
    echo "Results: $RESULTS_DIR"
    echo ""
    
    if [[ "$DRY_RUN" == "true" ]]; then
        echo "[DRY RUN] Verifying structure..."
        echo "  Script directory: $SCRIPT_DIR"
        echo "  Project root: $PROJECT_ROOT"
        echo "  Results dir: $RESULTS_DIR (will be created)"
        echo "  Benchmark module: $MODULE"
        echo "  Benchmark JAR: $BENCHMARK_JAR (requires build)"
        echo ""
        echo "[DRY RUN] Detecting available GCs..."
        if java -XX:+UseZGC -version >/dev/null 2>&1; then echo "  Available: ZGC"; fi
        if java -XX:+UseG1GC -version >/dev/null 2>&1; then echo "  Available: G1GC"; fi
        if java -XX:+UseShenandoahGC -version >/dev/null 2>&1; then echo "  Available: Shenandoah"; fi
        echo ""
        echo "[DRY RUN] Build command: mvn package -P benchmark -pl $MODULE"
        echo "[DRY RUN] Would run: AgentBenchmark with each available GC"
        echo "[DRY RUN] Aggregate: bash scripts/aggregate-gc-comparison.sh"
        return 0
    fi
    
    # Build benchmark JAR if needed
    if [[ ! -f "$BENCHMARK_JAR" ]]; then
        echo "Building benchmark JAR..."
        cd "$PROJECT_ROOT"
        mvn package -P benchmark -pl "$MODULE" -q -DskipTests
        echo "Built: $BENCHMARK_JAR"
    fi
    
    mkdir -p "$RESULTS_DIR"
    
    # Determine which GCs to run
    local gcs_to_run=()
    if [[ -n "$SPECIFIC_GC" ]]; then
        gcs_to_run=("$SPECIFIC_GC")
    else
        read -ra gcs_to_run <<< "$(detect_available_gcs)"
    fi
    
    if [[ ${#gcs_to_run[@]} -eq 0 ]]; then
        echo "ERROR: No supported GC algorithms detected" >&2
        exit 1
    fi
    
    echo "GCs to benchmark: ${gcs_to_run[*]}"
    echo ""
    
    # Run benchmark under each GC
    for gc in "${gcs_to_run[@]}"; do
        local flags
        flags="$(gc_flags "$gc")"
        local result_file="$RESULTS_DIR/bench-${gc}.json"
        
        echo "─────────────────────────────────────────────────"
        echo "Running: $gc  (flags: $flags)"
        echo "Output:  $result_file"
        echo ""
        
        java $flags \
            -Xms2g -Xmx4g \
            --enable-preview \
            -XX:+UseCompactObjectHeaders \
            -Djdk.virtualThreadScheduler.parallelism=4 \
            -jar "$BENCHMARK_JAR" \
            AgentBenchmark \
            -f 1 \
            -wi 2 -w 2s \
            -i 5 -r 2s \
            -rf json \
            -rff "$result_file" \
            2>&1 | tee "$RESULTS_DIR/bench-${gc}.log"
        
        if [[ -f "$result_file" ]]; then
            echo "✓ Results saved: $result_file"
        else
            echo "✗ No results file produced for $gc" >&2
        fi
        echo ""
    done
    
    echo "=== All GC benchmarks complete ==="
    echo "Aggregate results: bash scripts/aggregate-gc-comparison.sh"
}

main "$@"
