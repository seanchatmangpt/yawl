#!/usr/bin/env python3
"""
Test for Python bindings
"""

import sys
import os

# Add the current directory to Python path for testing
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

def test_import():
    """Test that the Python module can be imported"""
    try:
        import yawl_process_mining
        print("✓ Successfully imported yawl_process_mining")
        return True
    except ImportError as e:
        print(f"✗ Failed to import yawl_process_mining: {e}")
        return False

def test_submodules():
    """Test that submodules can be imported"""
    try:
        import yawl_process_mining.xes as xes
        import yawl_process_mining.discovery as discovery
        import yawl_process_mining.conformance as conformance
        print("✓ Successfully imported all submodules")
        return True
    except ImportError as e:
        print(f"✗ Failed to import submodules: {e}")
        return False

def test_functions_exist():
    """Test that expected functions exist"""
    try:
        import yawl_process_mining as ypm

        # Test module level functions
        assert hasattr(ypm, 'import_xes')
        assert hasattr(ypm, 'discover_dfg')
        assert hasattr(ypm, 'discover_alpha')
        assert hasattr(ypm, 'check_conformance')

        # Test submodule functions
        assert hasattr(ypm.xes, 'import_xes')
        assert hasattr(ypm.discovery, 'discover_dfg')
        assert hasattr(ypm.discovery, 'discover_alpha')
        assert hasattr(ypm.conformance, 'check_conformance')

        print("✓ All expected functions exist")
        return True
    except Exception as e:
        print(f"✗ Functions test failed: {e}")
        return False

def main():
    """Run all tests"""
    print("Testing Python bindings...")

    tests = [
        test_import,
        test_submodules,
        test_functions_exist,
    ]

    passed = 0
    for test in tests:
        if test():
            passed += 1
        print()

    print(f"Passed: {passed}/{len(tests)} tests")

    if passed == len(tests):
        print("✓ All tests passed!")
        return 0
    else:
        print("✗ Some tests failed!")
        return 1

if __name__ == "__main__":
    sys.exit(main())