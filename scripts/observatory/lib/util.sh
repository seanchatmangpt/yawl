#!/usr/bin/env bash
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

# Accumulation arrays for receipt
declare -a REFUSALS=()
declare -a WARNINGS=()

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
detect_java_version() {
    java -version 2>&1 | head -1 | grep -oP '(?<=")\d+' | head -1 || echo "unknown"
}
detect_maven_version() {
    mvn --version 2>/dev/null | head -1 | grep -oP '\d+\.\d+\.\d+' | head -1 || echo "unknown"
}

# ── Directory setup ───────────────────────────────────────────────────────
ensure_output_dirs() {
    mkdir -p "$FACTS_DIR" "$DIAGRAMS_DIR" "$YAWL_DIR" "$RECEIPTS_DIR"
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
    grep -oP "(?<=<${xpath_like}>)[^<]+" "$file" 2>/dev/null | head -1
}
