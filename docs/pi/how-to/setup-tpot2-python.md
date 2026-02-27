# How to Set Up the TPOT2 Python Environment

`Tpot2Bridge` launches a Python subprocess to run TPOT2 AutoML.
This guide covers installing the required Python packages and verifying the setup.

---

## Requirements

- Python 3.9 or later
- pip 21+
- At least 4 GB RAM for TPOT2 search (8 GB recommended for larger datasets)

---

## Installing Python dependencies

```bash
pip install tpot2 skl2onnx scikit-learn numpy pandas
```

### Recommended: use a virtual environment

```bash
python3 -m venv yawl-pi-venv
source yawl-pi-venv/bin/activate        # Linux/macOS
# yawl-pi-venv\Scripts\activate.bat     # Windows

pip install tpot2 skl2onnx scikit-learn numpy pandas
```

### Pinned versions (tested)

```
tpot2>=0.1.1
skl2onnx>=1.16.0
scikit-learn>=1.3.0
numpy>=1.24.0
pandas>=2.0.0
onnxruntime>=1.16.0   # for local verification
```

---

## Verifying the installation

### Quick check (< 10 seconds)

```bash
python3 -c "
import tpot2, skl2onnx, sklearn, numpy, pandas
print('tpot2:', tpot2.__version__)
print('skl2onnx:', skl2onnx.__version__)
print('sklearn:', sklearn.__version__)
print('numpy:', numpy.__version__)
print('pandas:', pandas.__version__)
print('All imports OK')
"
```

### Full end-to-end check

Run the `tpot2_runner.py` script directly with a minimal synthetic dataset:

```bash
# Create a minimal CSV
python3 -c "
import csv, random
rows = [['caseDurationMs','taskCount','distinctWorkItems','hadCancellations','avgTaskWaitMs','label']]
for i in range(50):
    rows.append([random.randint(1000,30000), random.randint(1,10),
                 random.randint(1,5), random.randint(0,1),
                 random.randint(100,5000), random.choice(['completed','failed'])])
with open('/tmp/test_data.csv','w',newline='') as f:
    csv.writer(f).writerows(rows)
print('test_data.csv written')
"

# Create a minimal config
python3 -c "
import json
config = {'taskType':'CASE_OUTCOME','generations':1,'populationSize':5,
          'maxTimeMins':2,'cvFolds':2,'scoringMetric':'roc_auc',
          'nJobs':1,'pythonExecutable':'python3'}
with open('/tmp/test_config.json','w') as f:
    json.dump(config, f)
print('test_config.json written')
"

# Run the bridge script (extracted by Tpot2Bridge to a temp dir, but also available in source)
python3 yawl-pi/src/main/resources/tpot2/tpot2_runner.py \
  --data /tmp/test_data.csv \
  --config /tmp/test_config.json \
  --output /tmp/test_model.onnx

echo "Exit code: $?"
ls -lh /tmp/test_model.onnx
```

Expected output:
```
{"bestScore": 0.62, "pipelineDescription": "...", "trainingTimeMs": 45321}
Exit code: 0
-rw-r--r-- 1 user user 14K /tmp/test_model.onnx
```

---

## Configuring the Python executable path

If `python3` is not on PATH (e.g., in a virtual environment or on Windows):

```java
Tpot2Config config = new Tpot2Config(
    Tpot2TaskType.CASE_OUTCOME,
    5, 50, 60, 5,
    "roc_auc",
    -1,
    "/home/user/yawl-pi-venv/bin/python3"   // ← full path
);
```

Or on Windows:
```java
"C:\\Users\\user\\yawl-pi-venv\\Scripts\\python.exe"
```

---

## Common issues

| Symptom | Cause | Fix |
|---|---|---|
| `PIException: python3 not found` | Python not on PATH | Use full path in `Tpot2Config.pythonExecutable` |
| `ImportError: No module named 'tpot2'` | TPOT2 not installed in active Python env | `pip install tpot2` in the same env |
| `ImportError: No module named 'skl2onnx'` | skl2onnx not installed | `pip install skl2onnx` |
| Training very slow | Default `generations=5, populationSize=50` | Use `Tpot2Config` with lower values for dev |
| Out of memory | TPOT2 using all CPUs | Set `nJobs=2` or `nJobs=1` in `Tpot2Config` |
| `PIException: TPOT2 exited with code 2` | Runtime error inside Python | Check stderr; usually a data format issue in the CSV |

---

## GPU acceleration (optional)

TPOT2 uses scikit-learn pipelines which are CPU-only. If your dataset is small
(< 100K rows), CPU is adequate. For larger datasets, consider limiting the
search space to tree-based estimators which parallelize well:

```java
// Use more CPU cores; no GPU required
Tpot2Config config = new Tpot2Config(
    Tpot2TaskType.CASE_OUTCOME,
    10, 100, 120, 5, "roc_auc",
    -1,       // -1 = all CPUs
    "python3"
);
```
