#!/usr/bin/env python3
"""
Verification script for Python bindings setup
"""

import os
import sys

def check_file_exists(path, description):
    """Check if a file exists and print status"""
    if os.path.exists(path):
        print(f"✓ {description}: {path}")
        return True
    else:
        print(f"✗ {description}: {path} - MISSING")
        return False

def check_directory_exists(path, description):
    """Check if a directory exists and print status"""
    if os.path.exists(path) and os.path.isdir(path):
        print(f"✓ {description}: {path}")
        return True
    else:
        print(f"✗ {description}: {path} - MISSING")
        return False

def main():
    """Verify all Python bindings files are in place"""
    print("Verifying Python bindings setup...")
    print("=" * 50)

    base_dir = os.path.dirname(os.path.abspath(__file__))
    passed = 0
    total = 0

    # Check source files
    print("\nSource Files:")
    source_files = [
        ("src/python/mod.rs", "Python module declaration"),
        ("src/python/xes.rs", "XES import/export module"),
        ("src/python/discovery.rs", "Process discovery module"),
        ("src/python/conformance.rs", "Conformance checking module"),
        ("src/python/lib.rs", "Python entry point"),
        ("src/lib.rs", "Main library file"),
    ]

    for file_path, description in source_files:
        total += 1
        full_path = os.path.join(base_dir, file_path)
        if check_file_exists(full_path, description):
            passed += 1

    # Check configuration files
    print("\nConfiguration Files:")
    config_files = [
        ("pyproject.toml", "Python package configuration"),
        ("requirements.txt", "Python dependencies"),
        ("Cargo.toml", "Rust project configuration"),
        ("Makefile", "Build system"),
    ]

    for file_path, description in config_files:
        total += 1
        full_path = os.path.join(base_dir, file_path)
        if check_file_exists(full_path, description):
            passed += 1

    # Check scripts
    print("\nBuild Scripts:")
    script_files = [
        ("build_python.sh", "Python build script"),
        ("setup_python_dev.sh", "Development setup script"),
    ]

    for file_path, description in script_files:
        total += 1
        full_path = os.path.join(base_dir, file_path)
        if check_file_exists(full_path, description):
            passed += 1
            # Check if script is executable
            if os.access(full_path, os.X_OK):
                print(f"  ✓ {description} is executable")
            else:
                print(f"  - {description} is not executable")

    # Check examples and tests
    print("\nExamples and Tests:")
    example_files = [
        ("examples/basic_usage.py", "Basic usage example"),
        ("examples/sample_log.xes", "Sample XES log file"),
        ("tests/test_python_bindings.py", "Python bindings test"),
    ]

    for file_path, description in example_files:
        total += 1
        full_path = os.path.join(base_dir, file_path)
        if check_file_exists(full_path, description):
            passed += 1

    # Check documentation
    print("\nDocumentation:")
    doc_files = [
        ("README_PYTHON.md", "Python documentation"),
        ("PYTHON_BINDINGS_SUMMARY.md", "Python bindings summary"),
    ]

    for file_path, description in doc_files:
        total += 1
        full_path = os.path.join(base_dir, file_path)
        if check_file_exists(full_path, description):
            passed += 1

    print("\n" + "=" * 50)
    print(f"Summary: {passed}/{total} files verified")

    if passed == total:
        print("✓ All files are in place!")
        print("\nNext steps:")
        print("1. Run: ./setup_python_dev.sh")
        print("2. Run: maturin develop")
        print("3. Run: python examples/basic_usage.py")
        return 0
    else:
        print("✗ Some files are missing!")
        return 1

if __name__ == "__main__":
    sys.exit(main())