"""
tpot2_runner.py — TPOT2 AutoML subprocess runner for YAWL process mining.

Invoked by Tpot2Bridge.fit() via ProcessBuilder:
  python3 tpot2_runner.py --data <csv_path> --config <json_path> --output <onnx_path>

Writes JSON metrics to stdout as the last line:
  {"bestScore": 0.92, "pipelineDescription": "GradientBoosting(...)", "trainingTimeMs": 14325}

Exit codes:
  0  success
  1  configuration or library error (ImportError, bad taskType, missing Python deps)
  2  training failure (TPOT2 found no pipeline, ONNX export failed)

Supported taskType values (from Tpot2TaskType enum):
  CASE_OUTCOME      — binary classification: "completed" vs "failed"
  NEXT_ACTIVITY     — multiclass classification: activity name labels
  ANOMALY_DETECTION — binary classification: "normal" vs "anomaly"
  REMAINING_TIME    — regression: numeric remaining_time_ms column

Requirements:
  pip install tpot2 skl2onnx numpy pandas scikit-learn
"""
import sys
import json
import time
import argparse


def parse_args():
    parser = argparse.ArgumentParser(description="TPOT2 AutoML runner for YAWL process mining")
    parser.add_argument("--data", required=True, help="Path to training CSV file")
    parser.add_argument("--config", required=True, help="Path to JSON config file")
    parser.add_argument("--output", required=True, help="Path to write serialised ONNX model")
    return parser.parse_args()


def check_imports():
    """Verify required libraries are available with actionable error messages."""
    try:
        import numpy as np  # noqa: F401
        import pandas as pd  # noqa: F401
    except ImportError as exc:
        print(f"ERROR: numpy/pandas not installed — run: pip install numpy pandas\n{exc}",
              file=sys.stderr)
        sys.exit(1)

    try:
        from tpot import TPOTClassifier, TPOTRegressor  # noqa: F401
    except ImportError as exc:
        try:
            import tpot2 as tpot_pkg  # noqa: F401
            # tpot2 uses a different import path in some versions
            from tpot2 import TPOTClassifier, TPOTRegressor  # noqa: F401
        except ImportError:
            print(
                f"ERROR: tpot2 not installed — run: pip install tpot2\n{exc}",
                file=sys.stderr,
            )
            sys.exit(1)

    try:
        import skl2onnx  # noqa: F401
        from skl2onnx.common.data_types import FloatTensorType  # noqa: F401
    except ImportError as exc:
        print(
            f"ERROR: skl2onnx not installed — run: pip install skl2onnx\n{exc}",
            file=sys.stderr,
        )
        sys.exit(1)


def load_tpot():
    """Import TPOT estimators, trying both tpot and tpot2 import paths."""
    try:
        from tpot import TPOTClassifier, TPOTRegressor
        return TPOTClassifier, TPOTRegressor
    except ImportError:
        from tpot2 import TPOTClassifier, TPOTRegressor
        return TPOTClassifier, TPOTRegressor


def load_config(config_path: str) -> dict:
    with open(config_path, "r") as f:
        return json.load(f)


def load_data(data_path: str):
    import numpy as np
    import pandas as pd

    df = pd.read_csv(data_path)
    label_col = "label"
    if label_col not in df.columns:
        print(f"ERROR: CSV has no 'label' column. Columns: {list(df.columns)}", file=sys.stderr)
        sys.exit(2)

    feature_cols = [c for c in df.columns if c != label_col]
    X = df[feature_cols].to_numpy(dtype=np.float32)
    y = df[label_col].to_numpy()
    return X, y, feature_cols


def build_tpot_estimator(cfg: dict, TPOTClassifier, TPOTRegressor):
    task_type = cfg["taskType"]
    common = {
        "generations": cfg.get("generations", 5),
        "population_size": cfg.get("populationSize", 50),
        "max_time_mins": cfg.get("maxTimeMins", 60),
        "cv": cfg.get("cvFolds", 5),
        "n_jobs": cfg.get("nJobs", -1),
        "verbosity": 2,
        "random_state": 42,
    }
    scoring = cfg.get("scoringMetric")
    if scoring:
        common["scoring"] = scoring

    if task_type == "REMAINING_TIME":
        return TPOTRegressor(**common)
    elif task_type in ("CASE_OUTCOME", "NEXT_ACTIVITY", "ANOMALY_DETECTION"):
        return TPOTClassifier(**common)
    else:
        print(f"ERROR: Unknown taskType '{task_type}'. "
              f"Must be one of: CASE_OUTCOME, REMAINING_TIME, NEXT_ACTIVITY, ANOMALY_DETECTION",
              file=sys.stderr)
        sys.exit(1)


def export_to_onnx(fitted_pipeline, X, output_path: str):
    """Convert a fitted sklearn pipeline to ONNX and write to disk."""
    from skl2onnx import convert_sklearn
    from skl2onnx.common.data_types import FloatTensorType

    n_features = X.shape[1]
    initial_type = [("float_input", FloatTensorType([None, n_features]))]

    onnx_model = convert_sklearn(
        fitted_pipeline,
        initial_types=initial_type,
        target_opset=17,
    )
    with open(output_path, "wb") as f:
        f.write(onnx_model.SerializeToString())


def main():
    args = parse_args()
    check_imports()

    TPOTClassifier, TPOTRegressor = load_tpot()
    cfg = load_config(args.config)
    X, y, feature_cols = load_data(args.data)

    task_type = cfg["taskType"]
    print(f"[tpot2_runner] task={task_type}, samples={len(X)}, features={len(feature_cols)}",
          file=sys.stderr)

    estimator = build_tpot_estimator(cfg, TPOTClassifier, TPOTRegressor)

    start_ms = int(time.time() * 1000)
    try:
        estimator.fit(X, y)
    except Exception as exc:
        print(f"ERROR: TPOT2 fitting failed: {exc}", file=sys.stderr)
        sys.exit(2)
    training_time_ms = int(time.time() * 1000) - start_ms

    fitted = getattr(estimator, "fitted_pipeline_", None)
    if fitted is None:
        print("ERROR: TPOT2 found no valid pipeline within the time/generation budget",
              file=sys.stderr)
        sys.exit(2)

    try:
        export_to_onnx(fitted, X, args.output)
    except Exception as exc:
        print(f"ERROR: ONNX export failed: {exc}", file=sys.stderr)
        sys.exit(2)

    # best score stored by TPOT2 after fitting
    best_score = float(getattr(estimator, "optimized_metric_", 0.0))
    pipeline_str = str(fitted)[:2000]  # cap to avoid bloated JSON

    metrics = {
        "bestScore": best_score,
        "pipelineDescription": pipeline_str,
        "trainingTimeMs": training_time_ms,
    }
    # This JSON line MUST be the last stdout output — Tpot2Bridge scans backwards for it
    print(json.dumps(metrics), flush=True)


if __name__ == "__main__":
    main()
