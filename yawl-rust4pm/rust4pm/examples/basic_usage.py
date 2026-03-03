#!/usr/bin/env python3
"""
Basic usage example for YAWL Process Mining Python bindings
"""

import sys
import os
import json

# Add the parent directory to Python path to import yawl_process_mining
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

def create_sample_xes_data():
    """Create a sample XES event log data for testing"""
    return {
        "events": [
            {
                "activity": "Start",
                "timestamp": "2024-01-01T10:00:00Z",
                "case_id": "case_1",
                "resource": "resource_1",
                "cost": "100"
            },
            {
                "activity": "Task_A",
                "timestamp": "2024-01-01T10:30:00Z",
                "case_id": "case_1",
                "resource": "resource_2",
                "cost": "200"
            },
            {
                "activity": "Task_B",
                "timestamp": "2024-01-01T11:00:00Z",
                "case_id": "case_1",
                "resource": "resource_3",
                "cost": "150"
            },
            {
                "activity": "Complete",
                "timestamp": "2024-01-01T11:30:00Z",
                "case_id": "case_1",
                "resource": "resource_1",
                "cost": "50"
            },
            {
                "activity": "Start",
                "timestamp": "2024-01-01T12:00:00Z",
                "case_id": "case_2",
                "resource": "resource_2",
                "cost": "100"
            },
            {
                "activity": "Task_A",
                "timestamp": "2024-01-01T12:30:00Z",
                "case_id": "case_2",
                "resource": "resource_1",
                "cost": "180"
            },
            {
                "activity": "Complete",
                "timestamp": "2024-01-01T13:00:00Z",
                "case_id": "case_2",
                "resource": "resource_3",
                "cost": "70"
            }
        ],
        "traces": [
            {
                "case_id": "case_1",
                "attributes": {
                    "total_cost": "450",
                    "duration": "PT1H30M"
                }
            },
            {
                "case_id": "case_2",
                "attributes": {
                    "total_cost": "350",
                    "duration": "PT1H30M"
                }
            }
        ],
        "attributes": {
            "concept:name": "Sample Process",
            "concept:version": "1.0",
            "xes:globalTraceAttributes": "resource,cost"
        }
    }

def main():
    """Basic usage example"""
    print("YAWL Process Mining - Basic Usage Example")
    print("=" * 50)

    try:
        # Import the module
        import yawl_process_mining as ypm
        print("✓ Successfully imported yawl_process_mining")

        # Test with XES file import
        print("\n--- XES File Import ---")
        try:
            # Import from XES file
            event_log = ypm.import_xes("examples/sample_log.xes")
            print(f"✓ Successfully imported XES file with {len(event_log['events'])} events")
            print(f"  Traces: {len(event_log['traces'])}")
            print(f"  Attributes: {list(event_log['attributes'].keys())}")
        except Exception as e:
            print(f"XES file import failed: {e}")
            print("Using sample data instead...")
            # Fallback to sample data
            event_log = create_sample_xes_data()
            print(f"✓ Using sample data with {len(event_log['events'])} events")

        # Test DFG discovery
        print("\n--- DFG Discovery ---")
        dfg = ypm.discover_dfg(event_log)
        print(f"Discovered DFG with {len(dfg['nodes'])} nodes and {len(dfg['edges'])} edges")
        print(f"Nodes: {dfg['nodes']}")
        print(f"Start activities: {dfg['start_activities']}")
        print(f"End activities: {dfg['end_activities']}")
        if dfg['edges']:
            print(f"Sample edges: {dfg['edges'][:3]}...")  # Show first 3 edges

        # Test Alpha miner
        print("\n--- Alpha Miner ---")
        pnml = ypm.discover_alpha(event_log)
        print(f"Discovered Alpha Miner with {len(pnml)} characters")
        print("PNML preview:")
        print(pnml[:200] + "...")  # Show first 200 characters

        # Test conformance checking
        print("\n--- Conformance Checking ---")
        conformance = ypm.check_conformance(event_log, pnml)
        print(f"Conformance metrics:")
        print(f"  Fitness: {conformance['fitness']}")
        print(f"  Missing: {conformance['missing']}")
        print(f"  Remaining: {conformance['remaining']}")
        print(f"  Consumed: {conformance['consumed']}")

        # Test submodule usage
        print("\n--- Submodule Usage ---")

        # XES submodule
        import yawl_process_mining.xes as xes
        print("✓ Imported xes submodule")

        # Discovery submodule
        import yawl_process_mining.discovery as discovery
        print("✓ Imported discovery submodule")

        # Conformance submodule
        import yawl_process_mining.conformance as conformance
        print("✓ Imported conformance submodule")

        print("\n✓ All basic usage tests passed!")

    except ImportError as e:
        print(f"✗ Import error: {e}")
        print("Make sure the Python bindings are built and installed:")
        print("  maturin develop")
        return 1
    except Exception as e:
        print(f"✗ Error: {e}")
        import traceback
        traceback.print_exc()
        return 1

    return 0

if __name__ == "__main__":
    sys.exit(main())