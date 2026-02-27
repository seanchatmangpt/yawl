#!/bin/bash

# YAWL Production Load Test Setup Validation Script
# Validates that all components are properly configured

echo "==============================================="
echo "YAWL Production Load Test Setup Validation"
echo "==============================================="
echo ""

# Check dependencies
echo "🔍 Checking dependencies..."

# Check k6
if command -v k6 &> /dev/null; then
    K6_VERSION=$(k6 version | grep -oP 'k6/\K[0-9]+\.[0-9]+\.[0-9]+')
    echo "✅ k6 v$K6_VERSION installed"
else
    echo "❌ k6 not found. Install from: https://k6.io/docs/getting-started/installation/"
    exit 1
fi

# Check Python
if command -v python3 &> /dev/null; then
    PYTHON_VERSION=$(python3 --version | cut -d' ' -f2)
    echo "✅ Python $PYTHON_VERSION installed"
else
    echo "❌ Python not found"
    exit 1
fi

# Check Python dependencies
echo ""
echo "🔍 Checking Python dependencies..."
python3 -c "import matplotlib, pandas, numpy" 2>/dev/null
if [ $? -eq 0 ]; then
    echo "✅ All required Python packages installed"
else
    echo "❌ Missing Python packages. Install with:"
    echo "   pip install matplotlib pandas numpy"
    exit 1
fi

# Check test files
echo ""
echo "🔍 Checking test files..."
FILES=(
    "production-load-test.js"
    "run-production-load-test.sh"
    "analyze-production-results.py"
    "production-test-config.json"
    "README.md"
)

for file in "${FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "✅ $file exists"
    else
        echo "❌ $file missing"
        exit 1
    fi
done

# Check file permissions
echo ""
echo "🔍 Checking file permissions..."
if [ -x "run-production-load-test.sh" ]; then
    echo "✅ Run script is executable"
else
    echo "❌ Run script not executable"
    exit 1
fi

if [ -x "analyze-production-results.py" ]; then
    echo "✅ Analysis script is executable"
else
    echo "❌ Analysis script not executable"
    exit 1
fi

# Validate JSON syntax
echo ""
echo "🔍 Validating JSON syntax..."
python3 -c "import json; json.load(open('production-test-config.json'))" 2>/dev/null
if [ $? -eq 0 ]; then
    echo "✅ Configuration JSON is valid"
else
    echo "❌ Configuration JSON is invalid"
    exit 1
fi

# Check if k6 script is valid
echo ""
echo "🔍 Validating k6 script syntax..."
if k6 version production-load-test.js &> /dev/null; then
    echo "✅ k6 script syntax is valid"
else
    echo "❌ k6 script syntax validation failed"
    exit 1
fi

echo ""
echo "==============================================="
echo "✅ All checks passed! Setup is complete."
echo "==============================================="
echo ""

echo "Next steps:"
echo "1. Start your YAWL service"
echo "2. Run: ./run-production-load-test.sh"
echo "3. Analyze results: python analyze-production-results.py"
echo ""
echo "For help: ./run-production-load-test.sh --help"
echo "For documentation: cat README.md"
