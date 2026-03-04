#!/usr/bin/env python3
"""
Script to verify OCEL data structure and content
"""

import json
import os
from datetime import datetime

def verify_ocel_structure(data, filename):
    """Verify the basic OCEL structure"""
    print(f"\n=== {filename} ===")

    # Check globalInfo
    if 'globalInfo' in data:
        global_info = data['globalInfo']
        print(f"✓ Global Info: {global_info.get('exporter', 'N/A')} v{global_info.get('exporterVersion', 'N/A')}")
    else:
        print("⚠ No globalInfo found")

    # Check events
    if 'events' in data:
        events = data['events']
        print(f"✓ {len(events)} events")

        # Check event types
        event_types = {}
        for event in events:
            event_type = event.get('type', 'unknown')
            event_types[event_type] = event_types.get(event_type, 0) + 1

        print("  Event types:", ", ".join([f"{k}({v})" for k, v in event_types.items()]))

        # Check timestamps
        timestamps = [datetime.fromisoformat(e['timestamp'].replace('Z', '+00:00')) for e in events]
        if timestamps:
            duration = (timestamps[-1] - timestamps[0]).total_seconds()
            print(f"  Time span: {duration/3600:.1f} hours")

        # Check lifecycle states
        lifecycles = {}
        for event in events:
            lifecycle = event.get('lifecycle', 'unknown')
            lifecycles[lifecycle] = lifecycles.get(lifecycle, 0) + 1
        print("  Lifecycle states:", ", ".join([f"{k}({v})" for k, v in lifecycles.items()]))

    # Check objects
    if 'objects' in data:
        objects = data['objects']
        print(f"✓ {len(objects)} objects")

        # Check object types
        object_types = {}
        for obj_id, obj in objects.items():
            obj_type = obj.get('type', 'unknown')
            object_types[obj_type] = object_types.get(obj_type, 0) + 1

        print("  Object types:", ", ".join([f"{k}({v})" for k, v in object_types.items()]))

    # Check for specific patterns
    check_patterns(data, filename)

def check_patterns(data, filename):
    """Check for specific patterns in the data"""
    events = data.get('events', [])
    objects = data.get('objects', {})

    # Check for failures
    failures = [e for e in events if e.get('type') == 'TaskFail']
    if failures:
        print(f"✓ {len(failures)} failures found")
        for fail in failures[:3]:  # Show first 3
            print(f"  - {fail.get('attributes', {}).get('concept:name', 'Unknown')}: {fail.get('attributes', {}).get('failureReason', 'Unknown reason')}")

    # Check for conflicts
    conflicts = [e for e in events if e.get('type') == 'ResourceConflict']
    if conflicts:
        print(f"✓ {len(conflicts)} resource conflicts found")

    # Check for retries
    retries = [e for e in events if 'retry' in str(e.get('id', '')).lower() or e.get('attributes', {}).get('retryNumber', 0) > 0]
    if retries:
        print(f"✓ {len(retries)} retry attempts found")

    # Check reassignments
    reassigns = [e for e in events if e.get('type') == 'TaskReassign']
    if reassigns:
        print(f"✓ {len(reassigns)} reassignments found")

    # Check blocked tasks
    blocked = [o for o in objects.values() if o.get('attributes', {}).get('pi:status') == 'blocked']
    if blocked:
        print(f"✓ {len(blocked)} blocked tasks")

    # Check failed tasks
    failed = [o for o in objects.values() if o.get('attributes', {}).get('pi:status') == 'failed']
    if failed:
        print(f"✓ {len(failed)} failed tasks")

def main():
    """Main function to verify all OCEL files"""
    base_path = '/Users/sac/yawl/test'
    ocel_files = [
        'ocel-normal-flow.json',
        'ocel-edge-cases.json',
        'ocel-stress-test.json'
    ]

    print("YAWL OCEL Data Verification Report")
    print("=" * 50)

    total_events = 0
    total_objects = 0

    for filename in ocel_files:
        filepath = os.path.join(base_path, filename)
        if os.path.exists(filepath):
            try:
                with open(filepath, 'r') as f:
                    data = json.load(f)

                verify_ocel_structure(data, filename)

                total_events += len(data.get('events', []))
                total_objects += len(data.get('objects', {}))

            except json.JSONDecodeError as e:
                print(f"✗ {filename}: Invalid JSON - {e}")
        else:
            print(f"✗ {filename}: File not found")

    print(f"\n=== Summary ===")
    print(f"Total events across all files: {total_events}")
    print(f"Total objects across all files: {total_objects}")

    # Sample content extraction
    print(f"\n=== Sample Content ===")
    for filename in ocel_files[:2]:  # Show sample from first two files
        filepath = os.path.join(base_path, filename)
        if os.path.exists(filepath):
            with open(filepath, 'r') as f:
                data = json.load(f)

            # Show first event
            if data.get('events'):
                first_event = data['events'][0]
                print(f"\nFirst event in {filename}:")
                print(f"  ID: {first_event.get('id')}")
                print(f"  Type: {first_event.get('type')}")
                print(f"  Timestamp: {first_event.get('timestamp')}")
                print(f"  Lifecycle: {first_event.get('lifecycle')}")
                if first_event.get('attributes'):
                    print(f"  Attributes: {list(first_event['attributes'].keys())}")

    print(f"\n=== Test Data Characteristics ===")
    print("1. ocel-normal-flow.json: Typical workflow with no failures")
    print("2. ocel-edge-cases.json: Includes failures, retries, conflicts, and reassignments")
    print("3. ocel-stress-test.json: High concurrency with resource contention and parallel work")

if __name__ == "__main__":
    main()