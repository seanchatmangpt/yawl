#!/usr/bin/env python3
"""
Test script for GEPA modules with DSPy integration.

This script demonstrates how the GEPA modules work with and without DSPy.
"""

import json
import logging
import sys
from pathlib import Path
from datetime import datetime

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def test_gepa_optimizer():
    """Test the GEPA optimizer module."""
    print("\n" + "=" * 60)
    print("Testing GEPA Optimizer Module")
    print("=" * 60)
    
    try:
        # Import the GEPA optimizer
        sys.path.insert(0, 'yawl-dspy/src/main/resources/python')
        from gepa_optimizer import (
            GepaOptimizer,
            FootprintScorer,
            BehavioralFootprint,
            GepaConfig,
            OptimizationTarget
        )
        
        print("✅ GEPA Optimizer imported successfully")
        
        # Test configuration loading
        config = GepaConfig(target=OptimizationTarget.BEHAVIORAL)
        print(f"✅ Configuration loaded: target={config.target}")
        
        # Test footprint scorer
        scorer = FootprintScorer()
        print("✅ Footprint scorer initialized")
        
        # Test workflow extraction (with sample data)
        sample_workflow = {
            "net": {
                "flows": [
                    {"source": "t1", "target": "t2"},
                    {"source": "t2", "target": "t3"}
                ],
                "transitions": [
                    {"id": "t1", "name": "Start", "splits": "and"},
                    {"id": "t2", "name": "Process", "splits": "xor"},
                    {"id": "t3", "name": "End", "splits": "and"}
                ]
            }
        }
        
        footprint = scorer.extract_footprint(sample_workflow)
        print(f"✅ Footprint extracted: {len(footprint.direct_succession)} succession relations")
        
        return True
        
    except Exception as e:
        print(f"❌ Error testing GEPA optimizer: {e}")
        return False

def test_dspy_gepa_generator():
    """Test the DSPy GEPA generator module."""
    print("\n" + "=" * 60)
    print("Testing DSPy GEPA Generator Module")
    print("=" * 60)
    
    try:
        # Import the generator
        sys.path.insert(0, 'yawl-ggen/src/main/resources/polyglot')
        from dspy_gepa_powl_generator import (
            DspyGepaPowlGeneratorModule,
            create_gepa_generator,
            generate_optimized_workflow
        )
        
        print("✅ DSPy GEPA Generator imported successfully")
        
        # Test generator creation
        generator = create_gepa_generator("behavioral")
        print("✅ Generator created successfully")
        
        # Test workflow generation
        sample_description = "Process loan application: Check eligibility, Approve loan, Send notification"
        
        result = generate_optimized_workflow(
            workflow_description=sample_description,
            optimization_target="behavioral"
        )
        
        print("✅ Workflow generated successfully")
        print(f"  - Worklet ID: {result['worklet_id']}")
        print(f"  - Control Flow Pattern: {result['control_flow_pattern']}")
        print(f"  - Optimization Target: {result['gepa_metadata']['target']}")
        
        return True
        
    except Exception as e:
        print(f"❌ Error testing DSPy GEPA generator: {e}")
        return False

def test_dspy_integration():
    """Test DSPy integration if available."""
    print("\n" + "=" * 60)
    print("Testing DSPy Integration")
    print("=" * 60)
    
    try:
        import dspy
        print(f"✅ DSPy version {dspy.__version__} available")
        
        # Test a simple DSPy program
        class SimpleProgram(dspy.Module):
            def __init__(self):
                super().__init__()
                self.predict = dspy.Predict("question -> answer")
            
            def forward(self, question):
                return self.predict(question=question)
        
        program = SimpleProgram()
        print("✅ Simple DSPy program created")
        
        return True
        
    except ImportError:
        print("⚠️  DSPy not available - skipping DSPy integration test")
        return False
    except Exception as e:
        print(f"❌ Error testing DSPy integration: {e}")
        return False

def test_graalpy_compatibility():
    """Test GraalPy compatibility."""
    print("\n" + "=" * 60)
    print("Testing GraalPy Compatibility")
    print("=" * 60)
    
    try:
        # Test basic Python features that work in GraalPy
        test_data = {
            "name": "GEPA Test",
            "version": "6.0.0",
            "features": [
                "DSPy integration",
                "Workflow optimization",
                "Behavioral footprint scoring"
            ]
        }
        
        # JSON serialization (works in GraalPy)
        json_data = json.dumps(test_data, indent=2)
        parsed_data = json.loads(json_data)
        
        print("✅ JSON serialization works")
        
        # Path operations
        test_path = Path(".")
        print(f"✅ Path operations work: {test_path.absolute()}")
        
        # Time operations
        timestamp = datetime.utcnow().isoformat()
        print(f"✅ Time operations work: {timestamp}")
        
        return True
        
    except Exception as e:
        print(f"❌ Error testing GraalPy compatibility: {e}")
        return False

def main():
    """Main test function."""
    print("🚀 YAWL GEPA Modules Integration Test")
    print("Testing both with and without DSPy availability")
    print("=" * 60)
    
    # Test results
    results = {
        "gepa_optimizer": test_gepa_optimizer(),
        "dspy_gepa_generator": test_dspy_gepa_generator(),
        "dspy_integration": test_dspy_integration(),
        "graalpy_compatibility": test_graalpy_compatibility()
    }
    
    # Summary
    print("\n" + "=" * 60)
    print("Test Summary")
    print("=" * 60)
    
    passed = sum(results.values())
    total = len(results)
    
    for test_name, result in results.items():
        status = "✅ PASSED" if result else "❌ FAILED"
        print(f"{status}: {test_name}")
    
    print(f"\nOverall: {passed}/{total} tests passed")
    
    # Save test report
    report = {
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "dspy_available": "dspy" in sys.modules,
        "test_results": results,
        "passed": passed,
        "total": total,
        "status": "SUCCESS" if passed == total else "PARTIAL"
    }
    
    report_path = Path("gepa_integration_test_report.json")
    with open(report_path, 'w') as f:
        json.dump(report, f, indent=2)
    
    print(f"\nTest report saved to: {report_path}")
    
    # Exit code
    sys.exit(0 if passed == total else 1)

if __name__ == "__main__":
    main()
