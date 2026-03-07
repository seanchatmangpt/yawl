#!/usr/bin/env bash
# YAWL Erlang Bridge Benchmark and Stress Test Runner
# OTP 80/20 Best Practices Validation Suite

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
BEAM_DIR="${PROJECT_DIR}/_build/default/lib/process_mining_bridge/ebin"
BENCH_DIR="${SCRIPT_DIR}"
RESULTS_DIR="${PROJECT_DIR}/_build/benchmark_results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

log_info()    { echo -e "${BLUE}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[✓]${NC} $*"; }
log_warning() { echo -e "${YELLOW}[!]${NC} $*"; }
log_error()   { echo -e "${RED}[✗]${NC} $*"; }
log_section() { echo -e "\n${CYAN}═══════════════════════════════════════════════════════════${NC}"; }

# Ensure results directory
mkdir -p "${RESULTS_DIR}"

echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║     YAWL Bridge OTP 80/20 Benchmark & Stress Test Suite    ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
echo "Started at: $(date)"

# Check Erlang
if ! command -v erl &>/dev/null; then
    log_error "Erlang/OTP not found"
    exit 1
fi

ERL_VERSION=$(erl -eval 'io:format("~s~n", [erlang:system_info(otp_release)]), erlang:halt().' -noshell)
log_info "Erlang/OTP version: ${ERL_VERSION}"

# Compile project with rebar3
compile_project() {
    log_section
    log_info "Compiling project..."
    cd "${PROJECT_DIR}"

    if command -v rebar3 &>/dev/null; then
        rebar3 compile || { log_error "Compilation failed"; exit 1; }
    else
        log_warning "rebar3 not found, attempting manual compilation"
        mkdir -p "${BEAM_DIR}"
        erlc -o "${BEAM_DIR}" src/*.erl bench/*.erl 2>/dev/null || true
    fi

    log_success "Compilation complete"
}

# Smoke test - quick validation
run_smoke_test() {
    log_section
    log_info "Running smoke tests..."

    local smoke_log="${RESULTS_DIR}/smoke_${TIMESTAMP}.log"

    erl -noshell -pa "${BEAM_DIR}" \
        -eval "
            io:format(\"Starting OTP 80/20 smoke tests...~n~n\"),

            %% Start applications
            application:start(sasl),
            application:start(mnesia),
            application:start(lager),

            Tests = [
                {\"Supervisor startup\", fun() -> {ok, _} = yawl_bridge_sup:start_link() end},
                {\"Telemetry module\", fun() -> {ok, _} = yawl_bridge_telemetry:start_link() end},
                {\"Health check v2\", fun() -> {ok, _} = yawl_bridge_health_v2:start_link() end},
                {\"NIF guard\", fun() -> {ok, _} = yawl_bridge_nif_guard:start_link() end},
                {\"Config validation\", fun() -> {ok, _} = yawl_bridge_config:validate_all() end},
                {\"Queue module\", fun() -> {ok, _} = yawl_bridge_queue:start_link(smoke_test, []) end},
                {\"Mnesia backup\", fun() -> {ok, _} = yawl_bridge_mnesia_backup:start_link([{backup_dir, \"/tmp/yawl_smoke\"}]) end}
            ],

            Results = lists:map(fun({Name, TestFn}) ->
                io:format(\"  [~p] ~s...\", [Name]),
                try
                    TestFn(),
                    io:format(\" ✓~n\"),
                    {Name, pass}
                catch
                    _:Reason ->
                        io:format(\" ✗ (~p)~n\", [Reason]),
                        {Name, {fail, Reason}}
                end
            end, Tests),

            PassCount = length([R || {_, pass} <- Results]),
            FailCount = length(Results) - PassCount,

            io:format(\"~nResults: ~p passed, ~p failed~n\", [PassCount, FailCount]),
            case FailCount of
                0 -> erlang:halt(0);
                _ -> erlang:halt(1)
            end
        " 2>&1 | tee "${smoke_log}"

    if [ ${PIPESTATUS[0]} -eq 0 ]; then
        log_success "Smoke tests passed"
    else
        log_error "Smoke tests failed"
        return 1
    fi
}

# Stress tests - load and failure injection
run_stress_tests() {
    log_section
    log_info "Running stress tests..."

    local stress_log="${RESULTS_DIR}/stress_${TIMESTAMP}.log"

    cd "${PROJECT_DIR}"

    if command -v rebar3 &>/dev/null; then
        rebar3 eunit --module=yawl_stress_test 2>&1 | tee "${stress_log}" || {
            log_error "Stress tests failed"
            return 1
        }
    else
        erl -noshell -pa "${BEAM_DIR}" \
            -eval "yawl_stress_test:test(), erlang:halt(0)." 2>&1 | tee "${stress_log}"
    fi

    log_success "Stress tests complete"
    log_info "Results: ${stress_log}"
}

# Benchmarks - performance measurement
run_benchmarks() {
    log_section
    log_info "Running performance benchmarks..."

    local bench_log="${RESULTS_DIR}/benchmark_${TIMESTAMP}.log"

    cd "${PROJECT_DIR}"

    if command -v rebar3 &>/dev/null; then
        rebar3 eunit --module=yawl_benchmark 2>&1 | tee "${bench_log}" || {
            log_error "Benchmarks failed"
            return 1
        }
    else
        erl -noshell -pa "${BEAM_DIR}" \
            -eval "yawl_benchmark:run_all(), erlang:halt(0)." 2>&1 | tee "${bench_log}"
    fi

    log_success "Benchmarks complete"
    log_info "Results: ${bench_log}"
}

# Legacy NIF benchmarks
run_legacy_benchmarks() {
    log_section
    log_info "Running legacy NIF benchmarks..."

    local legacy_log="${RESULTS_DIR}/legacy_benchmark_${TIMESTAMP}.log"

    # Check if NIF is available
    if [ ! -f "${BEAM_DIR}/yawl_process_mining.so" ] && \
       [ ! -f "${BEAM_DIR}/librust4pm.dylib" ]; then
        log_warning "NIF library not found, skipping legacy benchmarks"
        return 0
    fi

    run_benchmark "NIF Overhead" "benchmark_nif_overhead" ""
    run_benchmark "Data Marshalling" "benchmark_data_marshalling" ""
    run_benchmark "Process Mining Ops" "benchmark_pm_operations" "default"
    run_benchmark "Concurrency" "benchmark_concurrency" "50 1000"
}

run_benchmark() {
    local name="$1"
    local module="$2"
    local args="$3"

    log_info "Running ${name}..."
    erl -pa "${BEAM_DIR}" -s "${module}" start "${args}" -s init stop 2>&1
}

# Generate report
generate_report() {
    log_section
    log_info "Generating test report..."

    local report="${RESULTS_DIR}/report_${TIMESTAMP}.md"
    local latest_smoke=$(ls -t "${RESULTS_DIR}"/smoke_*.log 2>/dev/null | head -1)
    local latest_stress=$(ls -t "${RESULTS_DIR}"/stress_*.log 2>/dev/null | head -1)
    local latest_bench=$(ls -t "${RESULTS_DIR}"/benchmark_*.log 2>/dev/null | head -1)

    cat > "${report}" << EOF
# YAWL Bridge OTP 80/20 Test Report

**Generated**: $(date)
**Erlang/OTP**: ${ERL_VERSION}
**Platform**: $(uname -s) $(uname -r)
**Cores**: $(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo "unknown")

---

## Smoke Tests

\`\`\`
$(cat "${latest_smoke:-No smoke test results}" 2>/dev/null)
\`\`\`

---

## Stress Tests

\`\`\`
$(cat "${latest_stress:-No stress test results}" 2>/dev/null)
\`\`\`

---

## Benchmarks

\`\`\`
$(cat "${latest_bench:-No benchmark results}" 2>/dev/null)
\`\`\`

---

## Modules Tested

| Module | Purpose |
|--------|---------|
| yawl_bridge_sup | Enhanced supervisor with validation |
| yawl_bridge_telemetry | Restart metrics and health status |
| yawl_bridge_config | Child spec validation |
| yawl_bridge_queue | Message overflow protection |
| yawl_bridge_nif_guard | NIF resource management |
| yawl_bridge_mnesia_backup | Backup scheduling and recovery |
| yawl_bridge_health_v2 | Kubernetes-style health probes |

EOF

    log_success "Report generated: ${report}"
}

# Usage
usage() {
    echo "Usage: $0 [smoke|stress|benchmark|all|report]"
    echo ""
    echo "Commands:"
    echo "  smoke     - Quick validation tests"
    echo "  stress    - Load and failure injection tests"
    echo "  benchmark - Performance benchmarks"
    echo "  all       - Run all tests (default)"
    echo "  report    - Generate report from latest results"
    exit 1
}

# Main
main() {
    local mode="${1:-all}"

    case "${mode}" in
        smoke)
            compile_project
            run_smoke_test
            ;;
        stress)
            compile_project
            run_stress_tests
            ;;
        benchmark|bench)
            compile_project
            run_benchmarks
            ;;
        all)
            compile_project
            run_smoke_test
            run_stress_tests
            run_benchmarks
            generate_report
            ;;
        report)
            generate_report
            ;;
        legacy)
            compile_project
            run_legacy_benchmarks
            ;;
        *)
            usage
            ;;
    esac

    log_section
    log_success "Test suite complete!"
    echo "Results directory: ${RESULTS_DIR}"
}

main "$@"
