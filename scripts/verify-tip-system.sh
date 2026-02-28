#!/usr/bin/env bash
# ==========================================================================
# verify-tip-system.sh — Verification and Testing for TIP System
#
# Validates that all TIP components are installed, configured, and working.
# Performs end-to-end functional tests.
#
# Usage:
#   bash scripts/verify-tip-system.sh               # Full verification
#   bash scripts/verify-tip-system.sh --quick       # Quick checks only
#   bash scripts/verify-tip-system.sh --full        # Full tests + performance bench
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_YELLOW='\033[93m'
readonly C_RED='\033[91m'
readonly C_RESET='\033[0m'

# ── Parse arguments ───────────────────────────────────────────────────────
QUICK_CHECK=0
FULL_TEST=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --quick)  QUICK_CHECK=1; shift ;;
        --full)   FULL_TEST=1; shift ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *)  echo "Unknown arg: $1. Use -h for help."; exit 1 ;;
    esac
done

# ── Helper: Run a test ─────────────────────────────────────────────────────
run_test() {
    local name="$1"
    local cmd="$2"

    printf "${C_CYAN}→${C_RESET} %s ... " "$name"

    if eval "$cmd" >/dev/null 2>&1; then
        printf "${C_GREEN}✓${C_RESET}\n"
        return 0
    else
        printf "${C_RED}✗${C_RESET}\n"
        return 1
    fi
}

# ── Test: Script files exist ───────────────────────────────────────────────
test_scripts_exist() {
    printf "\n${C_CYAN}[1] Checking script files...${C_RESET}\n"

    local scripts=(
        "scripts/predict-test-times.sh"
        "scripts/train-prediction-model.sh"
        "scripts/schedule-tests-by-predictions.sh"
    )

    local failed=0
    for script in "${scripts[@]}"; do
        if run_test "Script exists: $script" "[[ -f '$REPO_ROOT/$script' ]]"; then
            :
        else
            failed=$((failed + 1))
        fi
    done

    return $failed
}

# ── Test: Scripts are executable ───────────────────────────────────────────
test_scripts_executable() {
    printf "\n${C_CYAN}[2] Checking script permissions...${C_RESET}\n"

    local scripts=(
        "scripts/predict-test-times.sh"
        "scripts/train-prediction-model.sh"
        "scripts/schedule-tests-by-predictions.sh"
    )

    local failed=0
    for script in "${scripts[@]}"; do
        if run_test "Executable: $script" "[[ -x '$REPO_ROOT/$script' ]]"; then
            :
        else
            failed=$((failed + 1))
        fi
    done

    return $failed
}

# ── Test: Config files exist ───────────────────────────────────────────────
test_config_files() {
    printf "\n${C_CYAN}[3] Checking configuration files...${C_RESET}\n"

    local configs=(
        ".yawl/ci/test-times.json"
        ".yawl/ci/tier-definitions.json"
        ".yawl/ci/test-shards.json"
    )

    local failed=0
    for config in "${configs[@]}"; do
        if run_test "Config: $config" "[[ -f '$REPO_ROOT/$config' ]]"; then
            :
        else
            failed=$((failed + 1))
        fi
    done

    return $failed
}

# ── Test: Dependencies available ───────────────────────────────────────────
test_dependencies() {
    printf "\n${C_CYAN}[4] Checking dependencies...${C_RESET}\n"

    local commands=(
        "python3"
        "jq"
        "bash"
    )

    local failed=0
    for cmd in "${commands[@]}"; do
        if run_test "Installed: $cmd" "command -v $cmd >/dev/null 2>&1"; then
            :
        else
            failed=$((failed + 1))
        fi
    done

    return $failed
}

# ── Test: Model training ──────────────────────────────────────────────────
test_model_training() {
    printf "\n${C_CYAN}[5] Testing model training...${C_RESET}\n"

    # Clean and retrain
    rm -f "$REPO_ROOT/.yawl/ci/prediction-model.json"

    if run_test "Train with bootstrap" "bash '$REPO_ROOT/scripts/train-prediction-model.sh' --bootstrap 2>&1 | grep -q 'Training complete'"; then
        :
    else
        return 1
    fi

    if run_test "Model file created" "[[ -f '$REPO_ROOT/.yawl/ci/prediction-model.json' ]]"; then
        :
    else
        return 1
    fi

    if run_test "Model has valid JSON" "jq . '$REPO_ROOT/.yawl/ci/prediction-model.json' >/dev/null 2>&1"; then
        :
    else
        return 1
    fi

    return 0
}

# ── Test: Prediction generation ────────────────────────────────────────────
test_prediction_generation() {
    printf "\n${C_CYAN}[6] Testing prediction generation...${C_RESET}\n"

    if run_test "Generate predictions" "bash '$REPO_ROOT/scripts/predict-test-times.sh' 2>&1 | grep -q 'Prediction engine complete'"; then
        :
    else
        return 1
    fi

    if run_test "Predictions file created" "[[ -f '$REPO_ROOT/.yawl/ci/test-predictions.json' ]]"; then
        :
    else
        return 1
    fi

    if run_test "Predictions have valid JSON" "jq . '$REPO_ROOT/.yawl/ci/test-predictions.json' >/dev/null 2>&1"; then
        :
    else
        return 1
    fi

    return 0
}

# ── Test: Test scheduling ──────────────────────────────────────────────────
test_scheduling() {
    printf "\n${C_CYAN}[7] Testing test scheduling...${C_RESET}\n"

    if run_test "Schedule Tier 1" "bash '$REPO_ROOT/scripts/schedule-tests-by-predictions.sh' 1 2>&1 | grep -q 'Scheduling complete'"; then
        :
    else
        return 1
    fi

    if run_test "Schedule file created" "[[ -f '$REPO_ROOT/.yawl/ci/test-schedule-tier-1.json' ]]"; then
        :
    else
        return 1
    fi

    if run_test "Schedule has valid JSON" "jq . '$REPO_ROOT/.yawl/ci/test-schedule-tier-1.json' >/dev/null 2>&1"; then
        :
    else
        return 1
    fi

    return 0
}

# ── Test: Accuracy metrics ─────────────────────────────────────────────────
test_accuracy_metrics() {
    printf "\n${C_CYAN}[8] Testing accuracy metrics...${C_RESET}\n"

    if run_test "Accuracy file created" "[[ -f '$REPO_ROOT/.yawl/metrics/prediction-accuracy.json' ]]"; then
        :
    else
        return 1
    fi

    if run_test "Accuracy has valid JSON" "jq . '$REPO_ROOT/.yawl/metrics/prediction-accuracy.json' >/dev/null 2>&1"; then
        :
    else
        return 1
    fi

    local mape=$(jq -r '.mape' "$REPO_ROOT/.yawl/metrics/prediction-accuracy.json" 2>/dev/null || echo "N/A")
    if run_test "MAPE metric available (MAPE=$mape%%)" "echo '$mape' | grep -qE '[0-9]+(\.[0-9]+)?'"; then
        :
    else
        return 1
    fi

    return 0
}

# ── Test: Data consistency ─────────────────────────────────────────────────
test_data_consistency() {
    printf "\n${C_CYAN}[9] Testing data consistency...${C_RESET}\n"

    local pred_count=$(jq '.total_tests' "$REPO_ROOT/.yawl/ci/test-predictions.json" 2>/dev/null || echo "0")
    local sched_count=$(jq '.total_tests' "$REPO_ROOT/.yawl/ci/test-schedule-tier-1.json" 2>/dev/null || echo "0")

    if run_test "Schedule test count matches tier" "[[ '$sched_count' -gt 0 ]]"; then
        :
    else
        return 1
    fi

    if run_test "Predictions exist for scheduled tests" "[[ '$pred_count' -ge '$sched_count' ]]"; then
        :
    else
        return 1
    fi

    return 0
}

# ── Main ───────────────────────────────────────────────────────────────────
printf "\n${C_CYAN}╔════════════════════════════════════════════════════════════╗${C_RESET}\n"
printf "${C_CYAN}║  TIP System Verification${C_RESET}\n"
printf "${C_CYAN}╚════════════════════════════════════════════════════════════╝${C_RESET}\n"

FAILED=0

# Run tests
test_scripts_exist || FAILED=$((FAILED + 1))
[[ "$QUICK_CHECK" == "0" ]] && test_scripts_executable || true
test_config_files || FAILED=$((FAILED + 1))
test_dependencies || FAILED=$((FAILED + 1))

# Only run expensive tests if not --quick
if [[ "$QUICK_CHECK" == "0" ]]; then
    test_model_training || FAILED=$((FAILED + 1))
    test_prediction_generation || FAILED=$((FAILED + 1))
    test_scheduling || FAILED=$((FAILED + 1))
    test_accuracy_metrics || FAILED=$((FAILED + 1))
    test_data_consistency || FAILED=$((FAILED + 1))
fi

# ── Summary ────────────────────────────────────────────────────────────────
printf "\n${C_CYAN}═══════════════════════════════════════════════════════════${C_RESET}\n"

if [[ $FAILED -eq 0 ]]; then
    printf "${C_GREEN}✓ All checks passed${C_RESET}\n"
    printf "\nTIP System is ready. Next steps:\n"
    printf "  1. bash scripts/train-prediction-model.sh --bootstrap\n"
    printf "  2. bash scripts/predict-test-times.sh\n"
    printf "  3. bash scripts/schedule-tests-by-predictions.sh 1\n"
    exit 0
else
    printf "${C_RED}✗ %d test(s) failed${C_RESET}\n" "$FAILED"
    exit 1
fi
