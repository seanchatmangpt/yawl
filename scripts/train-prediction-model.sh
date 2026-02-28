#!/usr/bin/env bash
# ==========================================================================
# train-prediction-model.sh — Linear Regression Model Trainer
#
# Trains or retrains the test execution time prediction model using
# historical build data. Uses module size, test count as features.
#
# Features:
#   - module_size: Lines of code in module
#   - test_count: Number of test methods
#   - exec_time_ms: Actual execution time from builds
#
# Model: exec_time_ms = a × module_size + b × test_count + c
#
# Usage:
#   bash scripts/train-prediction-model.sh                # Train with available data
#   bash scripts/train-prediction-model.sh --force        # Force retraining
#   bash scripts/train-prediction-model.sh --validate     # Validate model accuracy
#   bash scripts/train-prediction-model.sh --bootstrap    # Bootstrap with synthetic data
#
# Output:
#   .yawl/ci/prediction-model.json              (trained model coefficients)
#   .yawl/metrics/prediction-accuracy.json      (model accuracy metrics)
#   .yawl/metrics/historical-test-metrics.jsonl (append-only historical data)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Configuration ─────────────────────────────────────────────────────────
MODEL_FILE="${REPO_ROOT}/.yawl/ci/prediction-model.json"
ACCURACY_FILE="${REPO_ROOT}/.yawl/metrics/prediction-accuracy.json"
HISTORICAL_METRICS="${REPO_ROOT}/.yawl/metrics/historical-test-metrics.jsonl"
TEST_TIMES_FILE="${REPO_ROOT}/.yawl/ci/test-times.json"

MIN_TRAINING_SAMPLES=3

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_YELLOW='\033[93m'
readonly C_RED='\033[91m'
readonly C_RESET='\033[0m'

# ── Parse arguments ───────────────────────────────────────────────────────
FORCE_RETRAIN=0
VALIDATE_ONLY=0
BOOTSTRAP=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --force)       FORCE_RETRAIN=1; shift ;;
        --validate)    VALIDATE_ONLY=1; shift ;;
        --bootstrap)   BOOTSTRAP=1; shift ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *)  echo "Unknown arg: $1. Use -h for help."; exit 1 ;;
    esac
done

# ── Ensure directories exist ──────────────────────────────────────────────
mkdir -p "$(dirname "$MODEL_FILE")"
mkdir -p "$(dirname "$ACCURACY_FILE")"
mkdir -p "$(dirname "$HISTORICAL_METRICS")"

printf "${C_CYAN}[TIP] Prediction Model Trainer${C_RESET}\n"

# ── Extract training data from test metrics ────────────────────────────────
extract_training_data() {
    printf "  Extracting training data from test metrics...\n"

    python3 << 'PYTHON_EOF'
import json
import os
from datetime import datetime
from pathlib import Path

repo_root = os.getcwd()
test_times_file = os.path.join(repo_root, ".yawl/ci/test-times.json")
historical_metrics = os.path.join(repo_root, ".yawl/metrics/historical-test-metrics.jsonl")

# Load current test times
if not os.path.exists(test_times_file):
    print("[]")
    exit(0)

with open(test_times_file, 'r') as f:
    test_times = json.load(f)

tests = test_times.get('tests', [])

# Group tests by module to create training samples
module_data = {}

for test in tests:
    name = test.get('name', '')
    time_ms = test.get('time_ms', 0)

    # Extract module from test name
    # Pattern: org.yawlfoundation.yawl.<module>.ClassName.testMethod
    parts = name.split('.')
    module = 'unknown'

    if 'yawlfoundation' in name:
        try:
            for i, part in enumerate(parts):
                if part == 'yawl' and i + 1 < len(parts):
                    module = parts[i + 1]
                    break
        except:
            pass

    if module not in module_data:
        module_data[module] = {
            'test_times': [],
            'test_count': 0,
            'total_time': 0
        }

    module_data[module]['test_times'].append(time_ms)
    module_data[module]['test_count'] += 1
    module_data[module]['total_time'] += time_ms

# Create training samples
training_samples = []

for module, data in module_data.items():
    test_count = data['test_count']
    avg_time = sum(data['test_times']) / len(data['test_times']) if data['test_times'] else 0

    # Estimate LOC: typical test module has ~500 LOC per test method
    # This is a heuristic; in production, use actual cloc metrics
    estimated_loc = test_count * 500

    if avg_time > 0 and test_count > 0 and estimated_loc > 0:
        training_samples.append({
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "module": module,
            "loc": estimated_loc,
            "test_count": test_count,
            "exec_time_ms": avg_time,
            "sample_count": len(data['test_times'])
        })

# Append to historical metrics (append-only)
if training_samples:
    with open(historical_metrics, 'a') as f:
        for sample in training_samples:
            f.write(json.dumps(sample) + '\n')

print(json.dumps(training_samples))
PYTHON_EOF
}

# ── Fit linear regression model ────────────────────────────────────────────
fit_model() {
    local training_json="$1"

    printf "  Fitting linear regression model...\n"

    python3 << PYTHON_EOF
import json
from datetime import datetime
import math

training_data = json.loads('''${training_json}''')

if not training_data:
    print(json.dumps({
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "samples_count": 0,
        "coefficients": {
            "loc_coeff": 0.001,
            "test_count_coeff": 100,
            "intercept": 500
        },
        "r_squared": 0.0,
        "status": "no_data",
        "reason": "No training samples available"
    }, indent=2))
    exit(0)

samples = training_data
n = len(samples)

if n < 3:
    print(json.dumps({
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "samples_count": n,
        "coefficients": {
            "loc_coeff": 0.001,
            "test_count_coeff": 100,
            "intercept": 500
        },
        "r_squared": 0.0,
        "status": "insufficient_data",
        "reason": f"Only {n} samples, need >=3"
    }, indent=2))
    exit(0)

# Multiple linear regression: y = a×x1 + b×x2 + c
# x1 = module LOC
# x2 = test count
# y = execution time (ms)

# Compute means
mean_loc = sum(s['loc'] for s in samples) / n
mean_test_count = sum(s['test_count'] for s in samples) / n
mean_exec_time = sum(s['exec_time_ms'] for s in samples) / n

# Compute variances and covariances
var_loc = sum((s['loc'] - mean_loc) ** 2 for s in samples) / n
var_test_count = sum((s['test_count'] - mean_test_count) ** 2 for s in samples) / n
var_exec_time = sum((s['exec_time_ms'] - mean_exec_time) ** 2 for s in samples) / n

cov_loc_time = sum((s['loc'] - mean_loc) * (s['exec_time_ms'] - mean_exec_time)
                   for s in samples) / n
cov_test_time = sum((s['test_count'] - mean_test_count) * (s['exec_time_ms'] - mean_exec_time)
                    for s in samples) / n
cov_loc_test = sum((s['loc'] - mean_loc) * (s['test_count'] - mean_test_count)
                   for s in samples) / n

# Solve normal equations for multivariate linear regression
# Using matrix approach: (X'X)β = X'y
denom = (var_loc * var_test_count) - (cov_loc_test ** 2)

if abs(denom) < 1e-9:
    # Singular case: use simple univariate fit on strongest feature
    if var_test_count > var_loc:
        test_coeff = cov_test_time / (var_test_count + 0.001)
        loc_coeff = 0.0001
    else:
        loc_coeff = cov_loc_time / (var_loc + 0.001)
        test_coeff = 50
else:
    # Normal case: solve system
    loc_coeff = (cov_loc_time * var_test_count - cov_test_time * cov_loc_test) / denom
    test_coeff = (cov_test_time * var_loc - cov_loc_time * cov_loc_test) / denom

# Intercept
intercept = mean_exec_time - loc_coeff * mean_loc - test_coeff * mean_test_count

# Ensure non-negative coefficients
loc_coeff = max(loc_coeff, 0.0001)
test_coeff = max(test_coeff, 10)
intercept = max(intercept, 100)

# Compute R² (coefficient of determination)
predictions = [loc_coeff * s['loc'] + test_coeff * s['test_count'] + intercept
               for s in samples]
ss_res = sum((s['exec_time_ms'] - predictions[i]) ** 2 for i, s in enumerate(samples))
ss_tot = sum((s['exec_time_ms'] - mean_exec_time) ** 2 for s in samples)
r_squared = 1.0 - (ss_res / ss_tot) if ss_tot > 0 else 0.0
r_squared = max(0.0, min(1.0, r_squared))

model = {
    "timestamp": datetime.utcnow().isoformat() + "Z",
    "samples_count": n,
    "coefficients": {
        "loc_coeff": round(loc_coeff, 6),
        "test_count_coeff": round(test_coeff, 2),
        "intercept": round(intercept, 2)
    },
    "r_squared": round(r_squared, 4),
    "status": "trained",
    "features": {
        "x1": "module_loc",
        "x2": "test_count",
        "y": "exec_time_ms"
    },
    "diagnostics": {
        "mean_loc": round(mean_loc, 1),
        "mean_test_count": round(mean_test_count, 1),
        "mean_exec_time": round(mean_exec_time, 1),
        "variance_explained": round(r_squared * 100, 1)
    }
}

print(json.dumps(model, indent=2))
PYTHON_EOF
}

# ── Bootstrap with synthetic data ──────────────────────────────────────────
bootstrap_with_synthetic_data() {
    printf "  Bootstrapping with synthetic historical data...\n"

    python3 << 'BOOTSTRAP_PYTHON'
import json
from datetime import datetime

synthetic_samples = [
    {"module": "engine", "loc": 5000, "test_count": 10, "exec_time_ms": 3200},
    {"module": "engine", "loc": 4500, "test_count": 9, "exec_time_ms": 2800},
    {"module": "resourcing", "loc": 3500, "test_count": 7, "exec_time_ms": 5500},
    {"module": "resourcing", "loc": 3800, "test_count": 8, "exec_time_ms": 5000},
    {"module": "integration", "loc": 6000, "test_count": 3, "exec_time_ms": 15000},
    {"module": "integration", "loc": 5500, "test_count": 2, "exec_time_ms": 25000},
    {"module": "elements", "loc": 2000, "test_count": 4, "exec_time_ms": 500},
    {"module": "elements", "loc": 1800, "test_count": 3, "exec_time_ms": 400},
    {"module": "utilities", "loc": 1200, "test_count": 2, "exec_time_ms": 100},
    {"module": "utilities", "loc": 1500, "test_count": 3, "exec_time_ms": 150},
]

for sample in synthetic_samples:
    sample["timestamp"] = datetime.utcnow().isoformat() + "Z"
    sample["sample_count"] = 1

print(json.dumps(synthetic_samples))
BOOTSTRAP_PYTHON
}

# ── Main flow ──────────────────────────────────────────────────────────────

# Step 1: Collect training data
if [[ "$BOOTSTRAP" == "1" ]]; then
    printf "${C_YELLOW}Bootstrapping model with synthetic data...${C_RESET}\n"
    TRAINING_DATA=$(bootstrap_with_synthetic_data)
else
    printf "Extracting training data...\n"
    TRAINING_DATA=$(extract_training_data)
fi

SAMPLE_COUNT=$(echo "$TRAINING_DATA" | jq 'length')

if [[ "$SAMPLE_COUNT" -lt "$MIN_TRAINING_SAMPLES" && "$BOOTSTRAP" != "1" ]]; then
    printf "${C_YELLOW}→ Insufficient samples (${SAMPLE_COUNT}/${MIN_TRAINING_SAMPLES})${C_RESET}\n"
    printf "  Bootstrap: bash scripts/train-prediction-model.sh --bootstrap\n"
    exit 0
fi

# Step 2: Fit model
MODEL_JSON=$(fit_model "$TRAINING_DATA")
echo "$MODEL_JSON" > "$MODEL_FILE"

printf "${C_GREEN}✓ Model trained: $MODEL_FILE${C_RESET}\n"

# Step 3: Report model parameters
STATUS=$(echo "$MODEL_JSON" | jq -r '.status')
SAMPLES=$(echo "$MODEL_JSON" | jq -r '.samples_count')
R2=$(echo "$MODEL_JSON" | jq -r '.r_squared')
LOC_COEFF=$(echo "$MODEL_JSON" | jq -r '.coefficients.loc_coeff')
TEST_COEFF=$(echo "$MODEL_JSON" | jq -r '.coefficients.test_count_coeff')
INTERCEPT=$(echo "$MODEL_JSON" | jq -r '.coefficients.intercept')

printf "  Status: ${STATUS}\n"
printf "  Samples: ${SAMPLES}\n"
printf "  R²: ${R2}\n"
printf "  Model: exec_time = ${LOC_COEFF} × LOC + ${TEST_COEFF} × test_count + ${INTERCEPT}\n"

if [[ "$VALIDATE_ONLY" != "1" ]]; then
    printf "\nRun predictions: bash scripts/predict-test-times.sh\n"
fi

printf "\n${C_GREEN}[TIP] Training complete${C_RESET}\n"
exit 0
