#!/bin/bash
# Script to validate the YAWL Process Mining setup

echo "=== YAWL Process Mining Setup Validation ==="
echo

# Check Rust
if command -v rustc >/dev/null 2>&1; then
    RUST_VERSION=$(rustc --version)
    echo "✓ Rust found: $RUST_VERSION"
else
    echo "✗ Rust not found. Please install Rust: https://rustup.rs/"
    exit 1
fi

# Check Cargo
if command -v cargo >/dev/null 2>&1; then
    CARGO_VERSION=$(cargo --version)
    echo "✓ Cargo found: $CARGO_VERSION"
else
    echo "✗ Cargo not found"
    exit 1
fi

# Check Rust sources
if [ -d "../rust4pm" ]; then
    echo "✓ Rust sources found at ../rust4pm"

    # Check if Cargo.toml exists
    if [ -f "../rust4pm/Cargo.toml" ]; then
        echo "✓ Cargo.toml found"
    else
        echo "⚠️  Warning: Cargo.toml not found in rust4pm"
    fi

    # Check if src directory exists
    if [ -d "../rust4pm/src" ]; then
        echo "✓ src directory found"
    else
        echo "⚠️  Warning: src directory not found in rust4pm"
    fi
else
    echo "✗ Rust sources not found at ../rust4pm"
    exit 1
fi

# Check Erlang
if command -v erl >/dev/null 2>&1; then
    ERLANG_VERSION=$(erl -version 2>&1 | head -1)
    echo "✓ Erlang found: $ERLANG_VERSION"
else
    echo "✗ Erlang not found. Please install Erlang/OTP 21+"
    exit 1
fi

# Check Rebar3
if command -v rebar3 >/dev/null 2>&1; then
    REBAR_VERSION=$(rebar3 version)
    echo "✓ Rebar3 found: $REBAR_VERSION"
else
    echo "✗ Rebar3 not found. Please install Rebar3: https://rebar3.org/"
    exit 1
fi

# Check if we're in the correct directory
if [ ! -f "pm_example.erl" ]; then
    echo "⚠️  Warning: pm_example.erl not found in current directory"
    echo "Current directory: $(pwd)"
fi

# Check Erlang application structure
if [ -d "../ebin" ]; then
    echo "✓ ebin directory found"
else
    echo "⚠️  Warning: ebin directory not found (will be created on build)"
fi

# Check NIF library
PRIV_DIR="../priv/"
if [ -f "$PRIV_DIR/libyawl_process_mining.so" ]; then
    echo "✓ Linux NIF library found at $PRIV_DIR/libyawl_process_mining.so"
elif [ -f "$PRIV_DIR/yawl_process_mining.dll" ]; then
    echo "✓ Windows NIF library found at $PRIV_DIR/yawl_process_mining.dll"
elif [ -f "$PRIV_DIR/libyawl_process_mining.dylib" ]; then
    echo "✓ macOS NIF library found at $PRIV_DIR/libyawl_process_mining.dylib"
else
    echo "✗ NIF library not found in $PRIV_DIR"
    echo "Run './build_nif.sh' to build the NIF library"
fi

# Check sample files
SAMPLE_LOG="../../rust4pm/examples/sample_log.xes"
SAMPLE_OCEL="../../rust4pm/examples/sample_ocel.json"

if [ -f "$SAMPLE_LOG" ]; then
    echo "✓ Sample XES log found: $SAMPLE_LOG"
else
    echo "⚠️  Warning: Sample XES log not found at $SAMPLE_LOG"
fi

if [ -f "$SAMPLE_OCEL" ]; then
    echo "✓ Sample OCEL JSON found: $SAMPLE_OCEL"
else
    echo "⚠️  Warning: Sample OCEL JSON not found at $SAMPLE_OCEL"
fi

# Check example files
if [ -f "pm_example.erl" ]; then
    echo "✓ Example module found: pm_example.erl"
else
    echo "✗ Example module not found: pm_example.erl"
    exit 1
fi

if [ -f "test_pm_example.escript" ]; then
    echo "✓ Test script found: test_pm_example.escript"
elif [ -f "test_pm_example.erl" ]; then
    echo "✓ Test script found: test_pm_example.erl (old format)"
else
    echo "✗ Test script not found"
    exit 1
fi

# Check build scripts
if [ -f "build_nif.sh" ]; then
    echo "✓ Build script found: build_nif.sh"
else
    echo "✗ Build script not found: build_nif.sh"
    exit 1
fi

if [ -f "Makefile" ]; then
    echo "✓ Makefile found"
else
    echo "✗ Makefile not found"
    exit 1
fi

echo
echo "=== System Configuration Check ==="

# Check memory requirements for process mining
echo "Memory available:"
free -h 2>/dev/null || echo "Free memory check not available on this system"

# Check disk space
echo "Disk space in current directory:"
df -h . | tail -1

echo
echo "=== Validation Complete ==="
echo
echo "Quick setup commands:"
echo "1. ./build_nif.sh          # Build the NIF library"
echo "2. make build              # Build Erlang application"
echo "3. make test               # Run all tests"
echo
echo "Or run all steps:"
echo "   make test"
echo
echo "Manual verification:"
echo "1. Verify NIF library exists in priv/"
echo "2. Test with: erl -noshell -s pm_example test -s init stop"
echo "3. Check logs for any error messages"