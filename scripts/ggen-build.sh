#!/usr/bin/env bash
# ==========================================================================
# ggen-build.sh — Build Phase Orchestration (Λ in GODSPEED flow)
#
# Executes: Generate → Compile → Test → Validate
# Emits build receipts to .ggen/build-receipt.json
# Integrates with ggen code generation and Maven build system.
#
# Usage:
#   bash scripts/ggen-build.sh                    # Generate → Compile → Test → Validate
#   bash scripts/ggen-build.sh --phase lambda     # Same as above
#   bash scripts/ggen-build.sh --phase compile    # Compile only
#   bash scripts/ggen-build.sh --phase test       # Test only (assumes compiled)
#   bash scripts/ggen-build.sh --phase validate   # Validate only (static analysis)
#   bash scripts/ggen-build.sh --force            # Skip cache, rebuild
#   bash scripts/ggen-build.sh --no-cache         # Skip cache checks
#
# Environment:
#   GGEN_BUILD_CACHE=1      Enable build cache (default: 1)
#   GGEN_BUILD_VERBOSE=1    Show Maven output (default: 0)
#   GGEN_BUILD_TIMEOUT=300  Timeout per phase in seconds (default: 300)
#
# Exit codes:
#   0 = all phases GREEN
#   1 = compile failed (temporary, retry may help)
#   2 = test failed (permanent, code fix needed)
#   3 = validation failed (code quality issue)
#   4 = generate failed (ggen error)
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Color codes
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_BLUE='\033[94m'
readonly C_YELLOW='\033[93m'
readonly C_CYAN='\033[96m'

# Helpers
log_info()    { printf "${C_CYAN}[BUILD]${C_RESET} ${C_BLUE}%s${C_RESET}\n" "$*"; }
log_success() { printf "${C_GREEN}[✓]${C_RESET} %s\n" "$*"; }
log_warn()    { printf "${C_YELLOW}[!]${C_RESET} %s\n" "$*"; }
log_error()   { printf "${C_RED}[✗]${C_RESET} %s\n" "$*" >&2; }

# Configuration
PHASE="${1:-lambda}"
GGEN_BUILD_CACHE="${GGEN_BUILD_CACHE:-1}"
GGEN_BUILD_VERBOSE="${GGEN_BUILD_VERBOSE:-0}"
GGEN_BUILD_TIMEOUT="${GGEN_BUILD_TIMEOUT:-300}"
FORCE_REBUILD=0
SKIP_CACHE=0

# Parse args
while [[ $# -gt 0 ]]; do
    case "$1" in
        --phase)      PHASE="$2"; shift 2 ;;
        --force)      FORCE_REBUILD=1; shift ;;
        --no-cache)   SKIP_CACHE=1; shift ;;
        -v|--verbose) GGEN_BUILD_VERBOSE=1; shift ;;
        --help|-h)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0
            ;;
        *) log_error "Unknown arg: $1"; exit 1 ;;
    esac
done

# Create .ggen directory
mkdir -p "${REPO_ROOT}/.ggen"
RECEIPT_FILE="${REPO_ROOT}/.ggen/build-receipt.json"
FAILURE_LOG="/tmp/ggen-build-failure.log"
CACHE_FILE="${REPO_ROOT}/.ggen/build-cache.json"

# ──────────────────────────────────────────────────────────────────────────
# Helper: Emit receipt (JSON line)
# ──────────────────────────────────────────────────────────────────────────
emit_receipt() {
    local phase_name="$1" status="$2" elapsed_ms="$3" details="${4:-null}"

    # Build JSON object inline
    local json="{\"phase\":\"${phase_name}\",\"status\":\"${status}\",\"timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"elapsed_ms\":${elapsed_ms}}"

    if [[ "$details" != "null" ]]; then
        json="${json%\}},\"details\":${details}}"
    fi

    echo "$json" >> "${RECEIPT_FILE}"
}

# Helper: Parse Maven log for metrics
parse_maven_metrics() {
    local log_file="$1"
    local modules=0
    local tests=0

    modules=$(grep -c "Building " "$log_file" 2>/dev/null || echo 0)
    tests=$(grep -c "Running " "$log_file" 2>/dev/null || echo 0)

    echo "{\"modules\":${modules},\"tests\":${tests}}"
}

# ──────────────────────────────────────────────────────────────────────────
# PHASE 0: Generate (ggen → YAWL code)
# ──────────────────────────────────────────────────────────────────────────
phase_generate() {
    log_info "Phase 0: Generate (ggen)"
    local start_ms=$(python3 -c "import time; print(int(time.time() * 1000))")

    if ! bash "${SCRIPT_DIR}/ggen-sync.sh"; then
        local end_ms=$(python3 -c "import time; print(int(time.time() * 1000))")
        local elapsed=$((end_ms - start_ms))
        log_error "ggen generation failed"
        emit_receipt "generate" "FAIL" "$elapsed" '{"error":"ggen-sync failed"}'
        return 4
    fi

    local end_ms=$(python3 -c "import time; print(int(time.time() * 1000))")
    local elapsed=$((end_ms - start_ms))
    log_success "ggen generation completed in ${elapsed}ms"
    emit_receipt "generate" "GREEN" "$elapsed" '{"status":"generated"}'
    return 0
}

# ──────────────────────────────────────────────────────────────────────────
# PHASE 1: Compile
# ──────────────────────────────────────────────────────────────────────────
phase_compile() {
    log_info "Phase 1: Compile"
    local start_ms=$(python3 -c "import time; print(int(time.time() * 1000))")

    # Build Maven command
    local mvn_cmd="mvn"
    if command -v mvnd >/dev/null 2>&1; then
        mvn_cmd="mvnd"
    fi

    local mvn_args=(
        "-P" "agent-dx"
        "compile"
    )
    [[ "$GGEN_BUILD_VERBOSE" != "1" ]] && mvn_args+=("-q")
    [[ "$SKIP_CACHE" == "1" ]] && mvn_args+=("clean")

    local log_file="/tmp/ggen-compile.log"

    if timeout "$GGEN_BUILD_TIMEOUT" "$mvn_cmd" "${mvn_args[@]}" > "$log_file" 2>&1; then
        local end_ms=$(python3 -c "import time; print(int(time.time() * 1000))")
        local elapsed=$((end_ms - start_ms))
        local metrics=$(parse_maven_metrics "$log_file")

        log_success "Compilation succeeded in ${elapsed}ms"
        emit_receipt "compile" "GREEN" "$elapsed" "$metrics"
        return 0
    else
        local end_ms=$(python3 -c "import time; print(int(time.time() * 1000))")
        local elapsed=$((end_ms - start_ms))

        log_error "Compilation failed in ${elapsed}ms"
        cat "$log_file" > "$FAILURE_LOG"
        tail -20 "$log_file" >&2
        emit_receipt "compile" "FAIL" "$elapsed" '{"error":"compilation failed"}'
        return 1
    fi
}

# ──────────────────────────────────────────────────────────────────────────
# PHASE 2: Test
# ──────────────────────────────────────────────────────────────────────────
phase_test() {
    log_info "Phase 2: Test"
    local start_ms=$(python3 -c "import time; print(int(time.time() * 1000))")

    local mvn_cmd="mvn"
    if command -v mvnd >/dev/null 2>&1; then
        mvn_cmd="mvnd"
    fi

    local mvn_args=(
        "-P" "agent-dx"
        "test"
    )
    [[ "$GGEN_BUILD_VERBOSE" != "1" ]] && mvn_args+=("-q")

    local log_file="/tmp/ggen-test.log"

    if timeout "$GGEN_BUILD_TIMEOUT" "$mvn_cmd" "${mvn_args[@]}" > "$log_file" 2>&1; then
        local end_ms=$(python3 -c "import time; print(int(time.time() * 1000))")
        local elapsed=$((end_ms - start_ms))
        local metrics=$(parse_maven_metrics "$log_file")

        log_success "Tests passed in ${elapsed}ms"
        emit_receipt "test" "GREEN" "$elapsed" "$metrics"
        return 0
    else
        local end_ms=$(python3 -c "import time; print(int(time.time() * 1000))")
        local elapsed=$((end_ms - start_ms))

        log_error "Tests failed in ${elapsed}ms"
        cat "$log_file" > "$FAILURE_LOG"
        tail -30 "$log_file" >&2
        emit_receipt "test" "FAIL" "$elapsed" '{"error":"test execution failed"}'
        return 2
    fi
}

# ──────────────────────────────────────────────────────────────────────────
# PHASE 3: Validate (Static Analysis)
# ──────────────────────────────────────────────────────────────────────────
phase_validate() {
    log_info "Phase 3: Validate (Static Analysis)"
    local start_ms=$(python3 -c "import time; print(int(time.time() * 1000))")

    local mvn_cmd="mvn"
    if command -v mvnd >/dev/null 2>&1; then
        mvn_cmd="mvnd"
    fi

    # Run analysis via observatory-analysis profile
    local mvn_args=(
        "-P" "observatory-analysis"
        "verify"
    )
    [[ "$GGEN_BUILD_VERBOSE" != "1" ]] && mvn_args+=("-q")

    local log_file="/tmp/ggen-validate.log"

    if timeout "$GGEN_BUILD_TIMEOUT" "$mvn_cmd" "${mvn_args[@]}" > "$log_file" 2>&1; then
        local end_ms=$(python3 -c "import time; print(int(time.time() * 1000))")
        local elapsed=$((end_ms - start_ms))

        # Parse analysis results (if files exist)
        local spotbugs_issues=0
        local pmd_violations=0
        local checkstyle_errors=0

        if [[ -f "target/spotbugsXml.xml" ]]; then
            spotbugs_issues=$(grep -c "BugInstance" "target/spotbugsXml.xml" 2>/dev/null || echo 0)
        fi
        if [[ -f "target/pmd.xml" ]]; then
            pmd_violations=$(grep -c "violation" "target/pmd.xml" 2>/dev/null || echo 0)
        fi
        if [[ -f "target/checkstyle-result.xml" ]]; then
            checkstyle_errors=$(grep -c "error" "target/checkstyle-result.xml" 2>/dev/null || echo 0)
        fi

        local details="{\"spotbugs\":${spotbugs_issues},\"pmd\":${pmd_violations},\"checkstyle\":${checkstyle_errors}}"

        if [[ $spotbugs_issues -gt 0 || $pmd_violations -gt 0 || $checkstyle_errors -gt 0 ]]; then
            log_warn "Analysis found issues: SpotBugs=$spotbugs_issues PMD=$pmd_violations Checkstyle=$checkstyle_errors"
            emit_receipt "validate" "WARN" "$elapsed" "$details"
        else
            log_success "Analysis passed in ${elapsed}ms"
            emit_receipt "validate" "GREEN" "$elapsed" "$details"
        fi
        return 0
    else
        local end_ms=$(python3 -c "import time; print(int(time.time() * 1000))")
        local elapsed=$((end_ms - start_ms))

        log_error "Validation failed in ${elapsed}ms"
        cat "$log_file" > "$FAILURE_LOG"
        tail -20 "$log_file" >&2
        emit_receipt "validate" "FAIL" "$elapsed" '{"error":"static analysis failed"}'
        return 3
    fi
}

# ──────────────────────────────────────────────────────────────────────────
# Main: Execute requested phase(s)
# ──────────────────────────────────────────────────────────────────────────

# Reset receipt file
: > "${RECEIPT_FILE}"

case "$PHASE" in
    lambda)
        # Full pipeline: Generate → Compile → Test → Validate
        log_info "Starting full Λ pipeline"
        phase_generate || exit 4
        phase_compile || exit 1
        phase_test || exit 2
        phase_validate || exit 3
        ;;
    generate)
        phase_generate || exit 4
        ;;
    compile)
        phase_compile || exit 1
        ;;
    test)
        phase_test || exit 2
        ;;
    validate)
        phase_validate || exit 3
        ;;
    *)
        log_error "Unknown phase: $PHASE (try: lambda, generate, compile, test, validate)"
        exit 1
        ;;
esac

log_success "All phases GREEN"
log_info "Receipt: ${RECEIPT_FILE}"
exit 0
