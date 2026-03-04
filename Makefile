# Makefile for multi-target YAWL Process Mining Rust Library

.PHONY: all jni nif python clean build-debug test docs

# Default target
all: jni nif python

# Build for Java JNI
jni:
	@echo "Building YAWL Process Mining for Java JNI..."
	cargo build --manifest-path ./Cargo.toml --release --features jni
	@echo "Generated: target/release/libyawl_process_mining.so"

# Build for Erlang NIF
nif:
	@echo "Building YAWL Process Mining for Erlang NIF..."
	cargo build --manifest-path ./Cargo.toml --release --features nif
	@mkdir -p priv
	@echo "Determining OS for NIF naming..."
	@uname -s | grep -q Darwin && \
		cp target/release/libyawl_process_mining.dylib priv/yawl_process_mining.so && \
		echo "Generated: priv/yawl_process_mining.so (macOS dylib)" || \
		cp target/release/libyawl_process_mining.so priv/yawl_process_mining.so && \
		echo "Generated: priv/yawl_process_mining.so (Linux so)"

# Install NIF to Erlang bridge
ERLANG_BRIDGE_PRIV ?= ../yawl-erlang-bridge/yawl-erlang-bridge/priv

nif-erlang: nif
	@mkdir -p $(ERLANG_BRIDGE_PRIV)
	@cp priv/yawl_process_mining.so $(ERLANG_BRIDGE_PRIV)/
	@echo "NIF installed to $(ERLANG_BRIDGE_PRIV)/"

# Build for Python
python:
	@echo "Building YAWL Process Mining for Python..."
	cargo build --manifest-path ./Cargo.toml --release --features python
	@echo "Generated: target/release/libyawl_process_mining.so"
	@echo "Wheel file: target/wheels/yawl_process_mining-*.whl"

# Build Python wheel
python-wheel:
	@echo "Building Python wheel..."
	maturin build --manifest-path ./Cargo.toml --release
	@echo "Wheel file: target/wheels/yawl_process_mining-*.whl"

# Install Python package
python-install:
	@echo "Installing Python package..."
	maturin develop --manifest-path ./Cargo.toml

# Build in debug mode
build-debug:
	@echo "Building YAWL Process Mining in debug mode..."
	cargo build --manifest-path ./Cargo.toml --features jni,nif,python

# Run tests
test:
	@echo "Running tests..."
	cargo test --manifest-path ./Cargo.toml --lib -- --nocapture

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	cargo clean --manifest-path ./Cargo.toml
	rm -rf priv/
	rm -rf target/release/*.dylib
	rm -rf target/release/*.so

# Generate documentation
docs:
	@echo "Generating documentation..."
	cargo doc --manifest-path ./Cargo.toml --no-deps --open

# Help
help:
	@echo "Available targets:"
	@echo "  all          - Build all targets (JNI, NIF, Python)"
	@echo "  jni          - Build for Java JNI"
	@echo "  nif          - Build for Erlang NIF"
	@echo "  python       - Build for Python"
	@echo "  build-debug  - Build in debug mode"
	@echo "  test         - Run tests"
	@echo "  clean        - Clean build artifacts"
	@echo "  docs         - Generate documentation"
	@echo "  help         - Show this help"
