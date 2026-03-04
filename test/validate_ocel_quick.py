#!/usr/bin/env python3
"""
Quick validation script for OCEL test files
"""

import json
import sys
import os

def validate_ocel_file(filepath):
    """Quick validation of an OCEL file"""
    try:
        with open(filepath, 'r') as f:
            data = json.load(f)

        # Basic structure checks
        required_keys = ['events', 'objects']
        for key in required_keys:
            if key not in data:
                print(f"✗ Missing required key: {key}")
                return False

        # Event checks
        events = data['events']
        if not isinstance(events, list):
            print("✗ Events must be a list")
            return False

        for i, event in enumerate(events[:3]):  # Check first 3 events
            if not isinstance(event, dict):
                print(f"✗ Event {i} is not a dictionary")
                return False

            if 'id' not in event:
                print(f"✗ Event {i} missing id")
                return False

            if 'type' not in event:
                print(f"✗ Event {i} missing type")
                return False

            if 'timestamp' not in event:
                print(f"✗ Event {i} missing timestamp")
                return False

        # Object checks
        objects = data['objects']
        if not isinstance(objects, dict):
            print("✗ Objects must be a dictionary")
            return False

        print(f"✓ {os.path.basename(filepath)}: {len(events)} events, {len(objects)} objects")
        return True

    except json.JSONDecodeError as e:
        print(f"✗ {filepath}: Invalid JSON - {e}")
        return False
    except Exception as e:
        print(f"✗ {filepath}: {e}")
        return False

def main():
    """Main function"""
    base_path = '/Users/sac/yawl/test'
    files = [
        'ocel-normal-flow.json',
        'ocel-edge-cases.json',
        'ocel-stress-test.json'
    ]

    print("Quick OCEL Validation")
    print("=" * 30)

    all_valid = True
    for filename in files:
        filepath = os.path.join(base_path, filename)
        if not os.path.exists(filepath):
            print(f"✗ {filename}: File not found")
            all_valid = False
        else:
            if not validate_ocel_file(filepath):
                all_valid = False

    if all_valid:
        print("\n✓ All files are valid OCEL format")
        return 0
    else:
        print("\n✗ Some files failed validation")
        return 1

if __name__ == "__main__":
    sys.exit(main())