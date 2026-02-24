#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# util.sh — Observatory shared utility functions
# Part of: YAWL V6 Code Analysis Observatory
# ==========================================================================

# ── Constants ─────────────────────────────────────────────────────────────
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
OUT_DIR="${REPO_ROOT}/docs/v6/latest"
FACTS_DIR="${OUT_DIR}/facts"
DIAGRAMS_DIR="${OUT_DIR}/diagrams"
YAWL_DIR="${DIAGRAMS_DIR}/yawl"
RECEIPTS_DIR="${OUT_DIR}/receipts"
PERF_DIR="${OUT_DIR}/performance"
PERF_HISTORY_DIR="${REPO_ROOT}/docs/v6/performance-history"

# Accumulation arrays for receipt
declare -a REFUSALS=()
declare -a WARNINGS=()

# ── Performance Metrics Accumulators ──────────────────────────────────────
declare -A PHASE_TIMINGS=()
declare -A PHASE_MEMORY=()
declare -A OPERATION_TIMINGS=()
declare -a PERFORMANCE_LOG=()

# ── Timing ────────────────────────────────────────────────────────────────
epoch_ms() {
    if date +%s%N >/dev/null 2>&1; then
        echo $(( $(date +%s%N) / 1000000 ))
    else
        python3 -c 'import time; print(int(time.time()*1000))' 2>/dev/null || echo $(( $(date +%s) * 1000 ))
    fi
}

timer_start() { _TIMER_START=$(epoch_ms); }
timer_elapsed_ms() { echo $(( $(epoch_ms) - _TIMER_START )); }

# ── Memory Monitoring ─────────────────────────────────────────────────────
get_memory_kb() {
    local mem_kb=0
    if [[ -f "/proc/$$/status" ]]; then
        mem_kb=$(awk '/VmRSS:/ {print $2}' /proc/$$/status 2>/dev/null || echo 0)
    elif command -v ps >/dev/null 2>&1; then
        mem_kb=$(ps -o rss= -p $$ 2>/dev/null || echo 0)
    fi
    echo "$mem_kb"
}

get_memory_mb() {
    local kb
    kb=$(get_memory_kb)
    echo $(( kb / 1024 ))
}

record_memory() {
    local phase="$1"
    PHASE_MEMORY["${phase}"]=$(get_memory_kb)
}

# ── Phase Timing Recording ────────────────────────────────────────────────
record_phase_timing() {
    local phase="$1"
    local elapsed_ms="$2"
    PHASE_TIMINGS["${phase}"]="${elapsed_ms}"
    record_memory "${phase}"
    PERFORMANCE_LOG+=("{\"phase\":\"${phase}\",\"elapsed_ms\":${elapsed_ms},\"memory_kb\":$(get_memory_kb)}")
}

# ── Operation Timing (for individual operations within phases) ─────────────
record_operation() {
    local operation="$1"
    local elapsed_ms="$2"
    local current="${OPERATION_TIMINGS[${operation}]:-0}"
    OPERATION_TIMINGS["${operation}"]=$(( current + elapsed_ms ))
}

# ── Performance Report Generation ─────────────────────────────────────────
generate_performance_report() {
    local report_file="$PERF_DIR/performance-report.json"
    mkdir -p "$PERF_DIR"

    local total_ms="${TOTAL_ELAPSED:-0}"
    local peak_mem_kb=0
    for mem in "${PHASE_MEMORY[@]}"; do
        [[ "$mem" -gt "$peak_mem_kb" ]] && peak_mem_kb="$mem"
    done

    # Build phase timings JSON
    local phase_json=""
    local first=true
    for phase in "${!PHASE_TIMINGS[@]}"; do
        $first || phase_json+=","
        first=false
        local timing="${PHASE_TIMINGS[${phase}]}"
        local mem="${PHASE_MEMORY[${phase}]:-0}"
        phase_json+=$'\n'"    \"${phase}\": {\"elapsed_ms\": ${timing}, \"memory_kb\": ${mem}}"
    done

    # Build operation timings JSON
    local op_json=""
    first=true
    for op in "${!OPERATION_TIMINGS[@]}"; do
        $first || op_json+=","
        first=false
        local timing="${OPERATION_TIMINGS[${op}]}"
        op_json+=$'\n'"    \"${op}\": ${timing}"
    done

    cat > "$report_file" << PERF_EOF
{
  "run_id": "${RUN_ID}",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "total_elapsed_ms": ${total_ms},
  "peak_memory_kb": ${peak_mem_kb},
  "phase_timings": {${phase_json}
  },
  "operation_timings_ms": {${op_json}
  },
  "performance_log": [
$(printf '%s\n' "${PERFORMANCE_LOG[@]}" | awk 'NR>1{printf ",\n"} {printf "    %s", $0}')
  ],
  "system_info": {
    "os": "$(uname -s 2>/dev/null || echo 'unknown')",
    "arch": "$(uname -m 2>/dev/null || echo 'unknown')",
    "cores": $(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 1),
    "shell": "${BASH_VERSION:-unknown}"
  }
}
PERF_EOF

    echo "$report_file"
}

# ── Performance History Tracking ───────────────────────────────────────────
update_performance_history() {
    mkdir -p "$PERF_HISTORY_DIR"
    local history_file="$PERF_HISTORY_DIR/performance-history.jsonl"
    local report_file="$PERF_DIR/performance-report.json"

    if [[ -f "$report_file" ]]; then
        # Append as JSONL (one JSON object per line)
        jq -c . "$report_file" >> "$history_file" 2>/dev/null || \
            python3 -c "import json; print(json.dumps(json.load(open('$report_file'))))" >> "$history_file" 2>/dev/null || \
            cat "$report_file" >> "$history_file"
    fi

    # Keep last 100 entries
    if [[ -f "$history_file" ]]; then
        local lines
        lines=$(wc -l < "$history_file")
        if [[ "$lines" -gt 100 ]]; then
            tail -n 100 "$history_file" > "${history_file}.tmp"
            mv "${history_file}.tmp" "$history_file"
        fi
    fi
}

# ── Diagram Speed Analysis ────────────────────────────────────────────────
analyze_diagram_speed() {
    local history_file="$PERF_HISTORY_DIR/performance-history.jsonl"
    local analysis_file="$PERF_DIR/diagram-speed-analysis.json"

    if [[ ! -f "$history_file" ]]; then
        echo '{"status": "no_history", "message": "No performance history available yet"}' > "$analysis_file"
        return
    fi

    # Extract diagram timings from history using Python
    python3 << 'PYEOF' "$history_file" "$analysis_file"
import json
import sys
from collections import defaultdict

history_file = sys.argv[1]
output_file = sys.argv[2]

diagram_times = defaultdict(list)
run_count = 0

with open(history_file, 'r') as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        try:
            data = json.loads(line)
            run_count += 1
            phases = data.get('phase_timings', {})
            if 'diagrams' in phases:
                diagram_times['diagrams_total'].append(phases['diagrams'].get('elapsed_ms', 0))
            ops = data.get('operation_timings_ms', {})
            for op, timing in ops.items():
                if 'diagram' in op.lower() or 'emit_' in op.lower():
                    diagram_times[op].append(timing)
        except (json.JSONDecodeError, KeyError, TypeError):
            continue

result = {
    "status": "analyzed",
    "runs_analyzed": run_count,
    "diagram_performance": {},
    "trend": "insufficient_data"
}

for op, times in diagram_times.items():
    if times:
        avg = sum(times) / len(times)
        min_t = min(times)
        max_t = max(times)
        recent = times[-5:] if len(times) >= 5 else times
        recent_avg = sum(recent) / len(recent)
        trend = "stable"
        if len(times) >= 3:
            first_third = sum(times[:len(times)//3]) / max(1, len(times)//3)
            last_third = sum(times[-(len(times)//3):]) / max(1, len(times)//3)
            if last_third > first_third * 1.2:
                trend = "slowing"
            elif last_third < first_third * 0.8:
                trend = "improving"
        result["diagram_performance"][op] = {
            "count": len(times),
            "avg_ms": round(avg, 2),
            "min_ms": min_t,
            "max_ms": max_t,
            "recent_avg_ms": round(recent_avg, 2),
            "trend": trend
        }

if diagram_times.get('diagrams_total'):
    result["trend"] = result["diagram_performance"]["diagrams_total"]["trend"]

with open(output_file, 'w') as f:
    json.dump(result, f, indent=2)
PYEOF
}

# ── Performance Summary for Receipt ────────────────────────────────────────
generate_performance_summary() {
    local summary_file="$PERF_DIR/summary.json"

    local facts_time="${PHASE_TIMINGS[facts]:-${FACTS_ELAPSED:-0}}"
    local diagrams_time="${PHASE_TIMINGS[diagrams]:-${DIAGRAMS_ELAPSED:-0}}"
    local yawl_time="${PHASE_TIMINGS[yawl_xml]:-${YAWL_XML_ELAPSED:-0}}"
    local receipt_time="${PHASE_TIMINGS[receipt]:-${RECEIPT_ELAPSED:-0}}"
    local total_time="${TOTAL_ELAPSED:-0}"

    local peak_mem=0
    for mem in "${PHASE_MEMORY[@]}"; do
        [[ "$mem" -gt "$peak_mem" ]] && peak_mem="$mem"
    done

    # Calculate throughput
    local facts_count=$(ls "$FACTS_DIR"/*.json 2>/dev/null | wc -l | tr -d ' ')
    local diagrams_count=$(ls "$DIAGRAMS_DIR"/*.mmd 2>/dev/null | wc -l | tr -d ' ')
    local total_outputs=$(( facts_count + diagrams_count + 1 ))

    local throughput=0
    if [[ "$total_time" -gt 0 ]]; then
        throughput=$(echo "scale=2; $total_outputs * 1000 / $total_time" | bc 2>/dev/null || echo "0")
    fi

    cat > "$summary_file" << SUMMARY_EOF
{
  "run_id": "${RUN_ID}",
  "timing": {
    "total_ms": ${total_time},
    "total_sec": $(echo "scale=3; $total_time / 1000" | bc 2>/dev/null || echo "0"),
    "facts_ms": ${facts_time},
    "diagrams_ms": ${diagrams_time},
    "yawl_xml_ms": ${yawl_time},
    "receipt_ms": ${receipt_time}
  },
  "memory": {
    "peak_kb": ${peak_mem},
    "peak_mb": $(( peak_mem / 1024 ))
  },
  "throughput": {
    "outputs_per_sec": ${throughput},
    "facts_count": ${facts_count},
    "diagrams_count": ${diagrams_count}
  },
  "status": "completed"
}
SUMMARY_EOF

    echo "$summary_file"
}

# ── SHA-256 ───────────────────────────────────────────────────────────────
sha256_of_file() {
    local file="$1"
    if [[ ! -f "$file" ]]; then echo "sha256:missing"; return; fi
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$file" | awk '{print "sha256:" $1}'
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$file" | awk '{print "sha256:" $1}'
    else
        echo "sha256:unavailable"
    fi
}

# ── JSON helpers ──────────────────────────────────────────────────────────
json_escape() {
    local s="$1"
    s="${s//\\/\\\\}"
    s="${s//\"/\\\"}"
    s="${s//$'\n'/\\n}"
    s="${s//$'\t'/\\t}"
    printf '%s' "$s"
}

json_str() { printf '"%s"' "$(json_escape "$1")"; }

json_arr() {
    local first=true
    printf '['
    for item in "$@"; do
        $first || printf ','
        first=false
        printf '"%s"' "$(json_escape "$item")"
    done
    printf ']'
}

json_num() { printf '%d' "$1"; }

# ── Git info ──────────────────────────────────────────────────────────────
git_branch() { git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown"; }
git_commit() { git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo "unknown"; }
git_dirty() {
    if git -C "$REPO_ROOT" diff --quiet HEAD 2>/dev/null && \
       git -C "$REPO_ROOT" diff --cached --quiet HEAD 2>/dev/null; then
        echo "false"
    else
        echo "true"
    fi
}

# ── Toolchain ─────────────────────────────────────────────────────────────

# Get Java info as compact JSON (for receipt embedding)
# Always outputs valid JSON, never fails
get_java_info_json() {
    local result
    result=$(detect_java_version 2>/dev/null)
    local exit_code=$?

    if [[ "$exit_code" -ne 0 ]]; then
        # detect_java_version already outputs JSON error object on failure
        # Just ensure it's valid and compact
        echo "$result" | jq -c '.' 2>/dev/null || echo '{"error":"detection_failed"}'
        return
    fi

    # Success - wrap version in JSON object
    echo "{\"version\": \"${result}\"}"
}

# Detect Java version with robust error handling
# Returns: major version number (e.g., "17", "21") on success
#          JSON error object on failure (machine-parseable)
# Exit code: 0 on success, 1 on failure
detect_java_version() {
    local version_output
    local exit_code=0

    # Check if java command exists
    if ! command -v java >/dev/null 2>&1; then
        log_error "FAILED: 'java' command not found in PATH"
        echo '{"error": "java_not_found", "raw_output": "", "message": "java command not in PATH"}'
        return 1
    fi

    # Capture both stdout and stderr, handle command failure
    version_output=$(java -version 2>&1) || exit_code=$?

    if [[ "$exit_code" -ne 0 ]]; then
        log_error "FAILED: 'java -version' exited with code $exit_code"
        log_error "Output: $version_output"
        echo "{\"error\": \"java_version_failed\", \"raw_output\": $(json_str "$version_output"), \"exit_code\": $exit_code}"
        return 1
    fi

    # Get first meaningful line for parsing (skip JAVA_TOOL_OPTIONS noise)
    local first_line
    first_line=$(echo "$version_output" | grep -v "^Picked up" | head -1)

    # Handle multiple formats:
    # - "java version "17.0.1"" (Oracle)
    # - "openjdk version "17.0.1"" (OpenJDK)
    # - "java version "1.8.0_301"" (Java 8 format)
    # - "openjdk 17.0.1 2021-10-19" (some distributions)

    local version
    # First try: extract from quoted version string (most common)
    version=$(echo "$first_line" | sed -n 's/.*version *"\([0-9]*\).*".*/\1/p' | head -1)

    # If version is 1, it's the old 1.x.x format, get the major version
    if [[ "$version" == "1" ]]; then
        version=$(echo "$first_line" | sed -n 's/.*version *"1\.\([0-9]*\).*/\1/p' | head -1)
    fi

    # Second try: unquoted format like "openjdk 17.0.1"
    if [[ -z "$version" ]]; then
        version=$(echo "$first_line" | sed -n 's/[a-zA-Z]* *\([0-9]*\).*/\1/p' | head -1)
    fi

    # Fail fast if version not detected - Toyota Production System
    if [[ -z "$version" ]]; then
        log_error "FAILED: Cannot detect Java version from: $first_line"
        log_error "Ensure 'java -version' outputs a recognizable format"
        echo "{\"error\": \"detection_failed\", \"raw_output\": $(json_str "$first_line"), \"message\": \"Unrecognized java -version output format\"}"
        return 1
    fi

    echo "$version"
}

# Detect Maven version
# Returns: version string (e.g., "3.9.6") on success, "unknown" on failure
detect_maven_version() {
    local version_output
    local exit_code=0

    if ! command -v mvn >/dev/null 2>&1; then
        echo "unknown"
        return 1
    fi

    version_output=$(mvn --version 2>&1) || exit_code=$?

    if [[ "$exit_code" -ne 0 ]]; then
        log_warn "mvn --version failed with exit code $exit_code"
        echo "unknown"
        return 1
    fi

    echo "$version_output" | grep -v "^Picked up" | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "unknown"
}

# Check toolchain availability and versions
# Returns: JSON object with status for each tool (java, mvn, python3)
# Exit code: 0 if all tools available, 1 if any critical tool missing
check_toolchain() {
    local java_status="unknown"
    local java_version="unknown"
    local mvn_status="unknown"
    local mvn_version="unknown"
    local python_status="unknown"
    local python_version="unknown"
    local all_ok=true

    # Check Java
    if command -v java >/dev/null 2>&1; then
        java_status="installed"
        java_version=$(detect_java_version 2>/dev/null) || {
            java_status="error"
            java_version="detection_failed"
            all_ok=false
        }
        # Validate java_version is numeric
        if [[ ! "$java_version" =~ ^[0-9]+$ ]]; then
            java_status="error"
            all_ok=false
        fi
    else
        java_status="not_found"
        all_ok=false
    fi

    # Check Maven
    if command -v mvn >/dev/null 2>&1; then
        mvn_status="installed"
        mvn_version=$(detect_maven_version 2>/dev/null) || mvn_version="unknown"
        if [[ "$mvn_version" == "unknown" ]]; then
            mvn_status="error"
            all_ok=false
        fi
    else
        mvn_status="not_found"
        all_ok=false
    fi

    # Check Python3
    if command -v python3 >/dev/null 2>&1; then
        python_status="installed"
        local py_output
        py_output=$(python3 --version 2>&1) || {
            python_status="error"
            python_version="detection_failed"
            all_ok=false
        }
        if [[ "$python_status" == "installed" ]]; then
            python_version=$(echo "$py_output" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
            if [[ -z "$python_version" ]]; then
                python_version=$(echo "$py_output" | grep -oE '[0-9]+\.[0-9]+' | head -1)
            fi
            if [[ -z "$python_version" ]]; then
                python_version="unknown"
                python_status="error"
                all_ok=false
            fi
        fi
    else
        python_status="not_found"
        all_ok=false
    fi

    # Output JSON
    cat << TOOLCHAIN_EOF
{
  "java": {"status": "$java_status", "version": "$java_version"},
  "mvn": {"status": "$mvn_status", "version": "$mvn_version"},
  "python3": {"status": "$python_status", "version": "$python_version"},
  "all_available": $all_ok
}
TOOLCHAIN_EOF

    if [[ "$all_ok" == "true" ]]; then
        return 0
    else
        return 1
    fi
}

# ── Directory setup ───────────────────────────────────────────────────────
ensure_output_dirs() {
    mkdir -p "$FACTS_DIR" "$DIAGRAMS_DIR" "$YAWL_DIR" "$RECEIPTS_DIR" "$PERF_DIR" "$PERF_HISTORY_DIR"
}

# ── Run ID ────────────────────────────────────────────────────────────────
generate_run_id() { date -u +%Y%m%dT%H%M%SZ; }

# ── Logging ───────────────────────────────────────────────────────────────
if [[ -t 1 ]]; then
    _C='\033[0;36m'; _W='\033[0;33m'; _R='\033[0;31m'
    _G='\033[0;32m'; _B='\033[1m'; _N='\033[0m'
else
    _C=''; _W=''; _R=''; _G=''; _B=''; _N=''
fi

log_info()  { echo -e "${_C}[observatory]${_N} $*"; }
log_warn()  { echo -e "${_W}[observatory]${_N} WARN: $*" >&2; WARNINGS+=("$*"); }
log_error() { echo -e "${_R}[observatory]${_N} ERROR: $*" >&2; }
log_ok()    { echo -e "${_G}[observatory]${_N} $*"; }

# ── Refusal registration ─────────────────────────────────────────────────
add_refusal() {
    local code="$1" message="$2" witness="$3"
    REFUSALS+=("{\"code\":$(json_str "$code"),\"message\":$(json_str "$message"),\"witness\":${witness}}")
}

# ── Module discovery ──────────────────────────────────────────────────────
discover_modules() {
    grep '<module>' "$REPO_ROOT/pom.xml" | sed 's/.*<module>\(.*\)<\/module>.*/\1/' | tr -d ' '
}

# ── POM value extraction (lightweight XML parsing) ────────────────────────
pom_value() {
    local file="$1" xpath_like="$2"
    sed -n "s|.*<${xpath_like}>\([^<]*\)</${xpath_like}>.*|\1|p" "$file" 2>/dev/null | head -1
}
