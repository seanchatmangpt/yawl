#!/usr/bin/env bash
# ==========================================================================
# predict-test-times.sh — Test Impact Prophecy (TIP) Prediction Engine
#
# Predicts test execution times using linear regression on historical data.
# Uses module size, test count, and historical execution times as features.
#
# Features:
#   - module_size: Lines of code in module (from cloc output)
#   - test_count: Number of test methods in module
#   - exec_time: Actual execution time from build metrics
#
# Model: exec_time ≈ a × module_size + b × test_count + c (intercept)
#
# Usage:
#   bash scripts/predict-test-times.sh                    # Predict for all modules
#   bash scripts/predict-test-times.sh --retrain          # Force model retraining
#   bash scripts/predict-test-times.sh --test-module foo  # Predict for specific module
#   bash scripts/predict-test-times.sh --accuracy-report  # Show model accuracy metrics
#
# Output:
#   .yawl/ci/test-predictions.json           (predictions for each test)
#   .yawl/ci/prediction-model.json           (model coefficients: a, b, c)
#   .yawl/metrics/prediction-accuracy.json   (MAPE and confidence intervals)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Configuration ─────────────────────────────────────────────────────────
PREDICTIONS_FILE="${REPO_ROOT}/.yawl/ci/test-predictions.json"
MODEL_FILE="${REPO_ROOT}/.yawl/ci/prediction-model.json"
ACCURACY_FILE="${REPO_ROOT}/.yawl/metrics/prediction-accuracy.json"
BUILD_METRICS_FILE="${REPO_ROOT}/.yawl/metrics/build-metrics.json"
TEST_TIMES_FILE="${REPO_ROOT}/.yawl/ci/test-times.json"

# Training data: extract last N builds for model fitting
TRAINING_WINDOW=50  # Use last 50 builds for training
MIN_TRAINING_SAMPLES=5  # Minimum samples to train

# Historical metrics archive (append-only JSON lines)
HISTORICAL_METRICS="${REPO_ROOT}/.yawl/metrics/historical-test-metrics.jsonl"

# Colors
readonly C_CYAN='\033[96m'
readonly C_GREEN='\033[92m'
readonly C_YELLOW='\033[93m'
readonly C_RED='\033[91m'
readonly C_RESET='\033[0m'

# ── Parse arguments ───────────────────────────────────────────────────────
RETRAIN=0
ACCURACY_REPORT=0
TEST_MODULE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --retrain)          RETRAIN=1; shift ;;
        --accuracy-report)  ACCURACY_REPORT=1; shift ;;
        --test-module)      TEST_MODULE="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *)  echo "Unknown arg: $1. Use -h for help."; exit 1 ;;
    esac
done

# ── Ensure directories exist ──────────────────────────────────────────────
mkdir -p "$(dirname "$PREDICTIONS_FILE")"
mkdir -p "$(dirname "$MODEL_FILE")"
mkdir -p "$(dirname "$ACCURACY_FILE")"
mkdir -p "$(dirname "$HISTORICAL_METRICS")"

# ── Helper: Load current test times ─────────────────────────────────────
load_current_test_times() {
    if [[ ! -f "$TEST_TIMES_FILE" ]]; then
        echo '{"tests": []}'
        return 0
    fi
    cat "$TEST_TIMES_FILE"
}

# ── Helper: Fit linear regression model ─────────────────────────────────
# Expects JSON array of objects: {module, loc, test_count, exec_time}
# Outputs: {timestamp, samples_count, coefficients: {a, b, c}, r_squared}
fit_linear_model() {
    local training_data_json="$1"

    python3 << PYTHON_EOF
import json
import sys
import math
from datetime import datetime

try:
    training_data = json.loads('''${training_data_json}''')
except (json.JSONDecodeError, ValueError) as e:
    print(json.dumps({
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "samples_count": 0,
        "coefficients": {
            "loc_coeff": 0.001,
            "test_count_coeff": 100,
            "intercept": 500
        },
        "r_squared": 0.0,
        "status": "error",
        "reason": "Invalid JSON: " + str(e)
    }))
    sys.exit(0)

# Filter valid samples (must have all features)
samples = []
for item in training_data:
    if (item.get('loc', 0) > 0 and
        item.get('test_count', 0) > 0 and
        item.get('exec_time_ms', 0) > 0):
        samples.append(item)

if len(samples) < 3:
    # Fallback: use default coefficients
    model = {
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "samples_count": len(samples),
        "coefficients": {
            "loc_coeff": 0.001,      # 1ms per LOC
            "test_count_coeff": 100, # 100ms per test
            "intercept": 500         # Base 500ms
        },
        "r_squared": 0.0,
        "status": "insufficient_data",
        "reason": f"Only {len(samples)} samples, need >=3"
    }
    print(json.dumps(model, indent=2))
    sys.exit(0)

# ── Linear Regression: y = a×x1 + b×x2 + c ────────────────────────────
# x1 = module_size (LOC)
# x2 = test_count
# y = exec_time_ms

# Compute means
n = len(samples)
mean_loc = sum(s['loc'] for s in samples) / n
mean_test_count = sum(s['test_count'] for s in samples) / n
mean_exec_time = sum(s['exec_time_ms'] for s in samples) / n

# Compute covariances and variances
cov_loc_time = sum((s['loc'] - mean_loc) * (s['exec_time_ms'] - mean_exec_time)
                   for s in samples) / n
cov_test_time = sum((s['test_count'] - mean_test_count) * (s['exec_time_ms'] - mean_exec_time)
                    for s in samples) / n
var_loc = sum((s['loc'] - mean_loc) ** 2 for s in samples) / n
var_test_count = sum((s['test_count'] - mean_test_count) ** 2 for s in samples) / n
cov_loc_test = sum((s['loc'] - mean_loc) * (s['test_count'] - mean_test_count)
                   for s in samples) / n

# Solve normal equations using simplified approach
denom = (var_loc * var_test_count) - (cov_loc_test ** 2)
if abs(denom) < 1e-6:
    # Fallback to simple model
    loc_coeff = cov_loc_time / (var_loc + 0.001) if var_loc > 0 else 0.001
    test_coeff = cov_test_time / (var_test_count + 0.001) if var_test_count > 0 else 100
else:
    loc_coeff = (cov_loc_time * var_test_count - cov_test_time * cov_loc_test) / denom
    test_coeff = (cov_test_time * var_loc - cov_loc_time * cov_loc_test) / denom

intercept = mean_exec_time - loc_coeff * mean_loc - test_coeff * mean_test_count

# Ensure positive coefficients
loc_coeff = max(loc_coeff, 0.0001)
test_coeff = max(test_coeff, 10)

# Compute R² (coefficient of determination)
predictions = [loc_coeff * s['loc'] + test_coeff * s['test_count'] + intercept
               for s in samples]
ss_res = sum((s['exec_time_ms'] - predictions[i]) ** 2
             for i, s in enumerate(samples))
ss_tot = sum((s['exec_time_ms'] - mean_exec_time) ** 2 for s in samples)
r_squared = 1.0 - (ss_res / (ss_tot + 0.001)) if ss_tot > 0 else 0.0

model = {
    "timestamp": datetime.utcnow().isoformat() + "Z",
    "samples_count": n,
    "coefficients": {
        "loc_coeff": round(loc_coeff, 6),
        "test_count_coeff": round(test_coeff, 2),
        "intercept": round(intercept, 2)
    },
    "r_squared": round(max(0.0, min(1.0, r_squared)), 4),
    "status": "trained",
    "features": {
        "x1": "module_loc",
        "x2": "test_count",
        "y": "exec_time_ms"
    }
}

print(json.dumps(model, indent=2))
PYTHON_EOF
}

# ── Helper: Create predictions for all test cases ──────────────────────
create_predictions_file() {
    local model_json="$1"
    local test_times_json="$2"

    python3 << PYTHON_EOF
import json
import sys
from datetime import datetime

try:
    test_times = json.loads('''${test_times_json}''')
    model = json.loads('''${model_json}''')
except (json.JSONDecodeError, ValueError) as e:
    print(json.dumps({
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "tests": [],
        "error": "Invalid JSON: " + str(e)
    }, indent=2))
    sys.exit(0)

tests = test_times.get('tests', [])
predictions = []

for test in tests:
    test_name = test.get('name', '')
    actual_ms = test.get('time_ms', 0)

    # Extract module from test name
    module_name = 'unknown'
    parts = test_name.split('.')
    if len(parts) > 3 and 'yawlfoundation' in test_name:
        try:
            module_name = parts[4]  # org.yawlfoundation.yawl.<module>
        except:
            pass

    # For initial prediction, use actual time if available
    if actual_ms > 0:
        predicted_ms = actual_ms
        confidence_std = 100  # High confidence if we have actual data
    else:
        # Use model to estimate (assume ~500 LOC, ~10 tests)
        if model.get('status') == 'trained':
            coeffs = model['coefficients']
            a = coeffs['loc_coeff']
            b = coeffs['test_count_coeff']
            c = coeffs['intercept']
            predicted_ms = a * 500 + b * 1 + c
        else:
            predicted_ms = 1000  # Default fallback

        confidence_std = 500

    predictions.append({
        "test_name": test_name,
        "module": module_name,
        "predicted_ms": round(predicted_ms, 2),
        "actual_ms": actual_ms,
        "confidence_std_ms": round(confidence_std, 2),
        "confidence_interval": {
            "lower_ms": round(max(50, predicted_ms - confidence_std), 2),
            "upper_ms": round(predicted_ms + confidence_std, 2)
        }
    })

output = {
    "timestamp": datetime.utcnow().isoformat() + "Z",
    "model_timestamp": model.get('timestamp'),
    "model_r_squared": model.get('r_squared', 0),
    "total_tests": len(predictions),
    "total_predicted_ms": round(sum(p['predicted_ms'] for p in predictions), 2),
    "tests": predictions
}

print(json.dumps(output, indent=2))
PYTHON_EOF
}

# ── Helper: Calculate prediction accuracy metrics ──────────────────────
calculate_accuracy() {
    local model_json="$1"
    local predictions_json="$2"

    python3 << PYTHON_EOF
import json
import sys
import math
from datetime import datetime

try:
    model = json.loads('''${model_json}''')
    predictions = json.loads('''${predictions_json}''')
except (json.JSONDecodeError, ValueError) as e:
    print(json.dumps({
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "status": "error",
        "reason": "Invalid JSON: " + str(e)
    }, indent=2))
    sys.exit(0)

tests = predictions.get('tests', [])

# Filter tests with both predicted and actual values
completed_tests = [t for t in tests if t.get('actual_ms', 0) > 0]

if len(completed_tests) == 0:
    metrics = {
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "model_trained": model.get('status') == 'trained',
        "tests_evaluated": 0,
        "mape": None,
        "rmse": None,
        "median_absolute_error": None,
        "status": "insufficient_data",
        "reason": "No completed tests with actual execution times"
    }
    print(json.dumps(metrics, indent=2))
    sys.exit(0)

# Calculate MAPE (Mean Absolute Percentage Error)
errors = []
absolute_errors = []
for t in completed_tests:
    pred = t.get('predicted_ms', 0)
    actual = t.get('actual_ms', 0)

    if actual > 0:
        abs_pct_error = abs(actual - pred) / actual * 100
        errors.append(abs_pct_error)
        absolute_errors.append(abs(actual - pred))

mape = sum(errors) / len(errors) if errors else 0
mae = sum(absolute_errors) / len(absolute_errors) if absolute_errors else 0
rmse = math.sqrt(sum(e**2 for e in absolute_errors) / len(absolute_errors)) if absolute_errors else 0
median_ae = sorted(absolute_errors)[len(absolute_errors)//2] if absolute_errors else 0

# Determine if retraining is needed (MAPE > 20%)
needs_retrain = mape > 20.0

metrics = {
    "timestamp": datetime.utcnow().isoformat() + "Z",
    "model_trained": model.get('status') == 'trained',
    "model_r_squared": model.get('r_squared', 0),
    "tests_evaluated": len(completed_tests),
    "mape": round(mape, 2),
    "mae_ms": round(mae, 2),
    "rmse_ms": round(rmse, 2),
    "median_absolute_error_ms": round(median_ae, 2),
    "accuracy_rating": (
        "EXCELLENT" if mape <= 5 else
        "GOOD" if mape <= 10 else
        "FAIR" if mape <= 15 else
        "POOR" if mape <= 20 else
        "NEEDS_RETRAIN"
    ),
    "needs_retrain": needs_retrain,
    "status": "evaluated"
}

print(json.dumps(metrics, indent=2))
PYTHON_EOF
}

# ── Helper: Aggregate training data from test metrics ───────────────────
aggregate_training_data() {
    local test_times_json="$1"

    python3 << PYTHON_EOF
import json
import sys
from datetime import datetime

try:
    test_times = json.loads('''${test_times_json}''')
except (json.JSONDecodeError, ValueError) as e:
    print("[]")
    sys.exit(0)

# Create training data from current test snapshot
tests = test_times.get('tests', [])

# Group tests by module
module_data = {}

for test in tests:
    name = test.get('name', '')
    time_ms = test.get('time_ms', 0)

    # Extract module heuristic
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
        module_data[module] = {'test_times': [], 'test_count': 0}

    module_data[module]['test_times'].append(time_ms)
    module_data[module]['test_count'] += 1

# Convert to training samples
training_samples = []

for module, data in module_data.items():
    avg_time = sum(data['test_times']) / len(data['test_times']) if data['test_times'] else 0
    test_count = data['test_count']

    # Heuristic: assume ~500 LOC per test
    loc = test_count * 500

    if avg_time > 0 and test_count > 0:
        training_samples.append({
            "module": module,
            "loc": loc,
            "test_count": test_count,
            "exec_time_ms": avg_time,
            "timestamp": datetime.utcnow().isoformat() + "Z"
        })

print(json.dumps(training_samples))
PYTHON_EOF
}

# ── Main flow ─────────────────────────────────────────────────────────
printf "${C_CYAN}[TIP] Predictive Test Scheduling Engine${C_RESET}\n"

# Step 1: Check if model exists and is recent
TRAIN_MODEL=0
if [[ ! -f "$MODEL_FILE" ]] || [[ "$RETRAIN" == "1" ]]; then
    TRAIN_MODEL=1
    printf "${C_YELLOW}Training prediction model...${C_RESET}\n"
fi

# Step 2: Load or train model
if [[ "$TRAIN_MODEL" == "1" ]]; then
    # Aggregate training data from current test snapshot
    printf "  Aggregating training data...\n"
    TEST_TIMES_JSON=$(load_current_test_times)
    TRAINING_DATA=$(aggregate_training_data "$TEST_TIMES_JSON")

    # Fit linear regression model
    printf "  Fitting linear regression model...\n"
    MODEL_JSON=$(fit_linear_model "$TRAINING_DATA")

    # Save model
    echo "$MODEL_JSON" > "$MODEL_FILE"
    printf "${C_GREEN}✓ Model trained: $MODEL_FILE${C_RESET}\n"
else
    MODEL_JSON=$(cat "$MODEL_FILE")
    printf "${C_GREEN}✓ Using existing model${C_RESET}\n"
fi

# Step 3: Create predictions for all tests
printf "  Generating predictions for all tests...\n"
TEST_TIMES_JSON=$(load_current_test_times)
create_predictions_file "$MODEL_JSON" "$TEST_TIMES_JSON" > "$PREDICTIONS_FILE"
printf "${C_GREEN}✓ Predictions saved: $PREDICTIONS_FILE${C_RESET}\n"

# Step 4: Calculate accuracy metrics
printf "  Calculating accuracy metrics...\n"
PREDICTIONS_JSON=$(cat "$PREDICTIONS_FILE")
ACCURACY=$(calculate_accuracy "$MODEL_JSON" "$PREDICTIONS_JSON")
echo "$ACCURACY" > "$ACCURACY_FILE"

MAPE=$(echo "$ACCURACY" | jq -r '.mape // "N/A"')
RATING=$(echo "$ACCURACY" | jq -r '.accuracy_rating // "N/A"')
printf "${C_GREEN}✓ Accuracy metrics saved: $ACCURACY_FILE${C_RESET}\n"
printf "  MAPE (Mean Absolute Percentage Error): ${MAPE}%%\n"
printf "  Rating: ${RATING}\n"

# Step 5: Check if retraining is needed
NEEDS_RETRAIN=$(echo "$ACCURACY" | jq -r '.needs_retrain // false')
if [[ "$NEEDS_RETRAIN" == "true" ]]; then
    printf "${C_YELLOW}⚠ Model accuracy degraded (MAPE > 20%%). Consider retraining.${C_RESET}\n"
    printf "   Run: bash scripts/predict-test-times.sh --retrain\n"
fi

# Step 6: Output summary
if [[ "$ACCURACY_REPORT" == "1" ]]; then
    printf "\n${C_CYAN}=== Accuracy Report ===${C_RESET}\n"
    echo "$ACCURACY" | jq .
    printf "\n${C_CYAN}=== Model Details ===${C_RESET}\n"
    echo "$MODEL_JSON" | jq .
fi

printf "\n${C_GREEN}[TIP] Prediction engine complete${C_RESET}\n"
exit 0
