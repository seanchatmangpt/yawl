# How-To: Setup Python Environment

## Problem

You need to configure Python for TPOT2 AutoML training.

## Solution

### Step 1: Install Python 3.9+

```bash
# macOS (Homebrew)
brew install python@3.11

# Ubuntu/Debian
sudo apt-get install python3.11 python3.11-venv

# Verify
python3 --version  # Should be 3.9+
```

### Step 2: Create Virtual Environment

```bash
python3 -m venv /opt/yawl-tpot2-venv
source /opt/yawl-tpot2-venv/bin/activate
```

### Step 3: Install Dependencies

```bash
pip install tpot2 skl2onnx numpy pandas scikit-learn
```

### Step 4: Verify Installation

```bash
python3 -c "
import tpot2
import skl2onnx
import numpy
import pandas
import sklearn
print('TPOT2:', tpot2.__version__)
print('skl2onnx:', skl2onnx.__version__)
print('sklearn:', sklearn.__version__)
"
```

### Step 5: Configure Tpot2Bridge

```java
// Default uses "python3" on PATH
Tpot2Config config = Tpot2Config.defaults(Tpot2TaskType.CASE_OUTCOME);

// Or specify full path
Tpot2Config config = new Tpot2Config(
    Tpot2TaskType.CASE_OUTCOME,
    5, 50, 60, 5, "roc_auc", -1,
    "/opt/yawl-tpot2-venv/bin/python3"  // Custom path
);
```

## Troubleshooting

### "No module named 'tpot2'"

**Cause**: Python environment not activated or packages not installed.

**Fix**:
```bash
source /opt/yawl-tpot2-venv/bin/activate
pip install tpot2 skl2onnx
```

### "Python executable not found"

**Cause**: `python3` not on PATH.

**Fix**: Specify full path in config:
```java
Tpot2Config config = new Tpot2Config(
    ..., "/usr/bin/python3.11"
);
```

### "ONNX export failed"

**Cause**: `skl2onnx` not installed or incompatible version.

**Fix**:
```bash
pip install --upgrade skl2onnx scikit-learn
```
