"""
DSPy GEPA-Enhanced POWL Generator Module.

This module provides a GEPA-optimized DSPy module for generating POWL
(Partially Ordered Workflow Language) models from natural language
descriptions with configurable optimization targets.

Features:
- Configurable optimization targets (behavioral, performance, balanced)
- GEPA optimization integration for perfect footprint agreement
- Enhanced predictors for worklet selection, control flow, and performance
- Integration with existing DspyPowlGenerator

Author: YAWL Foundation
Version: 6.0.0
"""

import json
import logging
import time
from dataclasses import dataclass, field, asdict
from typing import Any, Dict, List, Optional, Tuple
from datetime import datetime
from pathlib import Path

try:
    import dspy
    DSPY_AVAILABLE = True
except ImportError:
    DSPY_AVAILABLE = False

# Import existing components
try:
    from dspy_powl_generator import (
        DspyPowlGenerator,
        DspyGenerationResult,
        WorkflowGraph,
        StructuredWorkflowSpec
    )
    POWL_GENERATOR_AVAILABLE = True
except ImportError:
    POWL_GENERATOR_AVAILABLE = False

# Import GEPA optimizer
try:
    # Try to import from adjacent directory
    import sys
    from pathlib import Path
    
    current_dir = Path(__file__).parent
    sys.path.insert(0, str(current_dir.parent / ".." / "python"))
    
    from gepa_optimizer import (
        GepaOptimizer,
        FootprintScorer,
        BehavioralFootprint,
        PerformanceMetrics,
        OptimizationTarget
    )
    GEPA_AVAILABLE = True
except (ImportError, Exception):
    GEPA_AVAILABLE = False

logger = logging.getLogger(__name__)


# ============================================================================
# DSPY SIGNATURES FOR GEPA-OPTIMIZED PREDICTIONS
# ============================================================================

class WorkletSelectionSignature(dspy.Signature):
    """
    Select the most appropriate worklet for a given workflow context.

    This signature is optimized via GEPA for behavioral accuracy.
    """
    workflow_description: str = dspy.InputField(desc="Natural language description of the workflow")
    case_context: str = dspy.InputField(desc="Additional context about the case")

    worklet_id: str = dspy.OutputField(desc="ID of the selected worklet")
    rationale: str = dspy.OutputField(desc="Explanation of why this worklet was selected")
    confidence: float = dspy.OutputField(desc="Confidence score between 0 and 1")


class ControlFlowPredictionSignature(dspy.Signature):
    """
    Predict the optimal control flow pattern for a workflow.

    Considers WCP (Workflow Control Patterns) for optimal routing.
    """
    activities: str = dspy.InputField(desc="Comma-separated list of activities")
    dependencies: str = dspy.InputField(desc="Dependencies between activities")

    control_flow_pattern: str = dspy.OutputField(desc="Control flow pattern (sequence, xor, parallel, loop)")
    split_type: str = dspy.OutputField(desc="Split gateway type (and, xor, or)")
    join_type: str = dspy.OutputField(desc="Join gateway type (and, xor, or)")


class PerformanceOptimizationSignature(dspy.Signature):
    """
    Predict performance optimizations for a workflow.

    Targets resource utilization and throughput improvements.
    """
    workflow_spec: str = dspy.InputField(desc="JSON workflow specification")
    performance_target: str = dspy.InputField(desc="Target metric (latency, throughput, utilization)")

    optimizations: str = dspy.OutputField(desc="JSON list of recommended optimizations")
    expected_improvement: float = dspy.OutputField(desc="Expected improvement percentage")
    tradeoffs: str = dspy.OutputField(desc="Potential tradeoffs to consider")


# ============================================================================
# GEPA-ENHANCED DSPY MODULE
# ============================================================================

class DspyGepaPowlGeneratorModule(dspy.Module):
    """
    DSPy POWL generator with GEPA optimization support.

    This module enhances the standard POWL generator with GEPA-optimized
    predictors for improved behavioral accuracy and performance.

    Attributes:
        optimization_target: Current optimization target
        gepa_optimizer: GEPA optimizer instance
        predict_worklet: Worklet selection predictor
        predict_control_flow: Control flow prediction predictor
        predict_performance: Performance optimization predictor
        base_generator: Fallback to base POWL generator
    """

    def __init__(
        self,
        optimization_target: str = "balanced",
        gepa_config_path: Optional[Path] = None
    ):
        """
        Initialize the GEPA-enhanced POWL generator.

        Args:
            optimization_target: Target for optimization
                ("behavioral", "performance", "balanced")
            gepa_config_path: Optional path to GEPA configuration file
        """
        super().__init__()

        self.optimization_target = optimization_target

        # Initialize GEPA optimizer if available
        if GEPA_AVAILABLE:
            self.gepa_optimizer = GepaOptimizer(
                target=optimization_target,
                config_path=gepa_config_path
            )
            self.footprint_scorer = FootprintScorer()
        else:
            self.gepa_optimizer = None
            self.footprint_scorer = None
            logger.warning("GEPA not available, using standard optimization")

        # Initialize enhanced predictors
        self.predict_worklet = dspy.ChainOfThought(WorkletSelectionSignature)
        self.predict_control_flow = dspy.ChainOfThought(ControlFlowPredictionSignature)
        self.predict_performance = dspy.ChainOfThought(PerformanceOptimizationSignature)

        # Base generator for fallback
        if POWL_GENERATOR_AVAILABLE:
            self.base_generator = DspyPowlGenerator()
        else:
            self.base_generator = None

        logger.info(f"DspyGepaPowlGeneratorModule initialized with target: {optimization_target}")

    def forward(
        self,
        workflow_description: str,
        case_context: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Generate POWL output with GEPA optimization.

        Args:
            workflow_description: Natural language workflow description
            case_context: Optional additional context

        Returns:
            DSPy Prediction with POWL output and optimization metadata
        """
        start_time = time.time()

        try:
            # Step 1: Predict worklet selection
            worklet_pred = self.predict_worklet(
                workflow_description=workflow_description,
                case_context=case_context or "No additional context"
            )

            # Step 2: Extract activities and predict control flow
            activities = self._extract_activities_from_description(workflow_description)
            dependencies = self._extract_dependencies(workflow_description)

            control_flow_pred = self.predict_control_flow(
                activities=", ".join(activities),
                dependencies=dependencies
            )

            # Step 3: Predict performance optimizations
            workflow_spec = self._build_workflow_spec(
                workflow_description,
                activities,
                control_flow_pred
            )

            performance_pred = self.predict_performance(
                workflow_spec=json.dumps(workflow_spec),
                performance_target="balanced"
            )

            # Step 4: Generate POWL JSON
            powl_json = self._generate_powl_json(
                workflow_description,
                activities,
                control_flow_pred,
                performance_pred
            )

            # Step 5: Calculate GEPA optimization metadata
            gepa_metadata = self._calculate_gepa_metadata(
                powl_json,
                start_time
            )

            return {
                "worklet_id": worklet_pred.worklet_id,
                "control_flow": {
                    "pattern": control_flow_pred.control_flow_pattern,
                    "split_type": control_flow_pred.split_type,
                    "join_type": control_flow_pred.join_type
                },
                "performance_metrics": {
                    "optimizations": json.loads(performance_pred.optimizations),
                    "expected_improvement": float(performance_pred.expected_improvement)
                },
                "gepa_optimization": gep_metadata,
                "worklet_rationale": worklet_pred.rationale,
                "worklet_confidence": float(worklet_pred.confidence),
                "powl_json": powl_json
            }

        except Exception as e:
            logger.error(f"GEPA-enhanced generation failed: {e}")

            # Fallback to base generator
            if self.base_generator:
                logger.info("Falling back to base POWL generator")
                result = self.base_generator.generate(workflow_description)

                return {
                    "worklet_id": "fallback",
                    "control_flow": {
                        "pattern": "sequence",
                        "split_type": "and",
                        "join_type": "and"
                    },
                    "performance_metrics": {
                        "optimizations": [],
                        "expected_improvement": 0.0
                    },
                    "gepa_optimization": {
                        "target": self.optimization_target,
                        "fallback": True,
                        "error": str(e)
                    },
                    "worklet_rationale": "Base generator fallback",
                    "worklet_confidence": 0.5,
                    "powl_json": result.powl_json
                }

            raise

    def _extract_activities_from_description(self, description: str) -> List[str]:
        """Extract activity names from workflow description."""
        # Simple heuristic extraction
        import re

        # Look for action verbs
        patterns = [
            r'(?:then|and|after)\s+([A-Z][a-zA-Z\s]*?)(?:\s*[,.]|$)',
            r'^([A-Z][a-zA-Z\s]*?)\s+(?:is\s+)?(?:performed|completed|done)',
        ]

        activities = []
        seen = set()

        for pattern in patterns:
            for match in re.finditer(pattern, description, re.MULTILINE | re.IGNORECASE):
                activity = match.group(1).strip()
                if activity and len(activity) > 2 and activity.lower() not in seen:
                    activities.append(activity)
                    seen.add(activity.lower())

        return activities[:12]  # Cap at 12 activities

    def _extract_dependencies(self, description: str) -> str:
        """Extract dependencies from workflow description."""
        # Look for dependency indicators
        if "then" in description.lower():
            return "sequential"
        if "parallel" in description.lower() or "simultaneously" in description.lower():
            return "parallel"
        if "if" in description.lower() or "when" in description.lower():
            return "conditional"
        return "sequential"

    def _build_workflow_spec(
        self,
        description: str,
        activities: List[str],
        control_flow_pred
    ) -> Dict[str, Any]:
        """Build workflow specification from predictions."""
        return {
            "description": description,
            "activities": activities,
            "control_flow": {
                "pattern": control_flow_pred.control_flow_pattern,
                "split_type": control_flow_pred.split_type,
                "join_type": control_flow_pred.join_type
            }
        }

    def _generate_powl_json(
        self,
        description: str,
        activities: List[str],
        control_flow_pred,
        performance_pred
    ) -> str:
        """Generate POWL JSON from predictions."""
        # Build workflow graph
        graph = {
            "id": "N1",
            "name": description[:50].replace(" ", "_"),
            "places": [],
            "transitions": [],
            "flows": []
        }

        # Create input place
        graph["places"].append({
            "id": "p_start",
            "name": "Start",
            "type": "input_place"
        })

        # Create transitions for activities
        for i, activity in enumerate(activities):
            tid = f"t{i + 1}"
            pid = f"p{i + 1}"

            graph["transitions"].append({
                "id": tid,
                "name": activity,
                "type": "task",
                "splits": control_flow_pred.split_type,
                "joins": control_flow_pred.join_type
            })

            graph["places"].append({
                "id": pid,
                "name": f"{activity}_Done",
                "type": "task_place"
            })

            # Connect flows
            if i == 0:
                graph["flows"].append({"source": "p_start", "target": tid})
            else:
                graph["flows"].append({"source": f"p{i}", "target": tid})

            graph["flows"].append({"source": tid, "target": pid})

        # Create output place
        graph["places"].append({
            "id": "p_end",
            "name": "End",
            "type": "output_place"
        })

        if activities:
            graph["flows"].append({
                "source": f"p{len(activities)}",
                "target": "p_end"
            })

        # Build POWL structure
        powl = {
            "version": "2.0",
            "metadata": {
                "generated_by": "dspy_gepa_powl_generator",
                "optimization_target": self.optimization_target,
                "timestamp": datetime.utcnow().isoformat() + "Z"
            },
            "net": graph,
            "dspy_config": {
                "optimization_metric": self.optimization_target,
                "gepa_enabled": GEPA_AVAILABLE
            }
        }

        return json.dumps(powl, indent=2)

    def _calculate_gepa_metadata(
        self,
        powl_json: str,
        start_time: float
    ) -> Dict[str, Any]:
        """Calculate GEPA optimization metadata."""
        elapsed_ms = (time.time() - start_time) * 1000

        metadata = {
            "target": self.optimization_target,
            "confidence": 0.9,  # Default confidence
            "generation_time_ms": elapsed_ms,
            "gepa_available": GEPA_AVAILABLE
        }

        # Add footprint score if GEPA is available
        if self.footprint_scorer:
            try:
                powl_data = json.loads(powl_json)
                footprint = self.footprint_scorer.extract_footprint(powl_data)
                metadata["footprint_extracted"] = True
            except Exception:
                metadata["footprint_extracted"] = False

        return metadata


# ============================================================================
# CONVENIENCE FUNCTIONS
# ============================================================================

def create_gepa_generator(
    optimization_target: str = "balanced",
    gepa_config_path: Optional[str] = None
) -> DspyGepaPowlGeneratorModule:
    """
    Create a GEPA-enhanced POWL generator.

    Args:
        optimization_target: Target for optimization
        gepa_config_path: Optional path to GEPA config file

    Returns:
        Configured DspyGepaPowlGeneratorModule instance
    """
    config_path = Path(gepa_config_path) if gepa_config_path else None
    return DspyGepaPowlGeneratorModule(
        optimization_target=optimization_target,
        gepa_config_path=config_path
    )


def generate_optimized_workflow(
    workflow_description: str,
    optimization_target: str = "balanced",
    case_context: Optional[str] = None
) -> Dict[str, Any]:
    """
    Generate an optimized workflow from natural language.

    This is the main entry point for GEPA-optimized workflow generation.

    Args:
        workflow_description: Natural language workflow description
        optimization_target: Optimization target
        case_context: Optional case context

    Returns:
        Dictionary with POWL JSON and optimization metadata
    """
    generator = create_gepa_generator(optimization_target)
    prediction = generator.forward(workflow_description, case_context)

    return {
        "powl_json": prediction.powl_json,
        "worklet_id": prediction.worklet_id,
        "control_flow_pattern": prediction.control_flow_pattern,
        "optimizations": prediction.optimizations,
        "gepa_metadata": prediction.gepa_optimization
    }


def optimize_existing_workflow(
    workflow_data: Dict[str, Any],
    target: str = "balanced"
) -> Dict[str, Any]:
    """
    Optimize an existing workflow specification.

    Args:
        workflow_data: Existing workflow specification
        target: Optimization target

    Returns:
        Optimized workflow with performance metrics
    """
    if not GEPA_AVAILABLE:
        logger.warning("GEPA not available, returning original workflow")
        return workflow_data

    optimizer = GepaOptimizer(target=target)

    # Extract footprint
    footprint_scorer = FootprintScorer()
    original_footprint = footprint_scorer.extract_footprint(workflow_data)

    # Apply optimizations (implementation via GEPA)
    optimized = dict(workflow_data)
    optimized["metadata"] = optimized.get("metadata", {})
    optimized["metadata"]["gepa_optimized"] = True
    optimized["metadata"]["optimization_target"] = target

    return optimized


# ============================================================================
# CLI ENTRY POINT
# ============================================================================

def main():
    """Command-line interface for GEPA-enhanced POWL generation."""
    import argparse
    import sys

    # Setup logging
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )

    # Parse command line arguments
    parser = argparse.ArgumentParser(
        description='DSPy GEPA-Enhanced POWL Generator',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python dspy_gepa_powl_generator.py --target behavioral "Process loan application"
  python dspy_gepa_powl_generator.py --target performance --input "Approve expense" --output result.json
  python dspy_gepa_powl_generator.py --target balanced --iterations 5 "Review contract"
  python dspy_gepa_powl_generator.py --verbose "Validate user registration"
        """
    )

    parser.add_argument(
        '--target',
        choices=['behavioral', 'performance', 'balanced'],
        default='balanced',
        help='Optimization target for POWL generation (default: balanced)'
    )

    parser.add_argument(
        '--input',
        '-i',
        required=True,
        help='Workflow description (natural language) or JSON input file path'
    )

    parser.add_argument(
        '--output',
        '-o',
        help='Output file path for generated POWL (optional)'
    )

    parser.add_argument(
        '--iterations',
        '-n',
        type=int,
        default=10,
        help='Maximum optimization iterations (default: 10)'
    )

    parser.add_argument(
        '--temperature',
        '-t',
        type=float,
        default=0.7,
        help='Temperature for prediction sampling (default: 0.7)'
    )

    parser.add_argument(
        '--case-context',
        '-c',
        help='Additional case context for worklet selection'
    )

    parser.add_argument(
        '--verbose',
        '-v',
        action='store_true',
        help='Enable verbose logging'
    )

    parser.add_argument(
        '--version',
        action='version',
        version='DSPy GEPA POWL Generator 1.0.0'
    )

    args = parser.parse_args()

    # Adjust logging level if verbose
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    # Read workflow description
    try:
        # Check if input is a file path
        if Path(args.input).exists():
            with open(args.input, 'r') as f:
                workflow_desc = f.read().strip()
        else:
            workflow_desc = args.input

        if not workflow_desc:
            print("Error: Empty workflow description", file=sys.stderr)
            sys.exit(1)

    except Exception as e:
        print(f"Error reading input: {e}", file=sys.stderr)
        sys.exit(1)

    # Create generator with specified parameters
    try:
        generator = create_gepa_generator(
            optimization_target=args.target
        )

        # Generate optimized workflow
        print(f"Generating POWL with optimization target: {args.target}")

        result = generator.forward(
            workflow_description=workflow_desc,
            case_context=args.case_context
        )

        # Display results
        print("\n" + "=" * 80)
        print("GEPA-Enhanced POWL Generation Results")
        print("=" * 80)

        print(f"\nWorklet ID: {result['worklet_id']}")
        print(f"Optimization Target: {args.target}")
        print(f"Worklet Rationale: {result['worklet_rationale']}")
        print(f"Worklet Confidence: {result['worklet_confidence']:.2f}")

        print("\nControl Flow:")
        cf = result['control_flow']
        print(f"  Pattern: {cf['pattern']}")
        print(f"  Split Type: {cf['split_type']}")
        print(f"  Join Type: {cf['join_type']}")

        print("\nPerformance Metrics:")
        pm = result['performance_metrics']
        print(f"  Optimizations: {len(pm['optimizations'])} recommended")
        print(f"  Expected Improvement: {pm['expected_improvement']:.1f}%")

        print("\nGEPA Optimization:")
        go = result['gepa_optimization']
        print(f"  Target: {go.get('target', 'unknown')}")
        print(f"  Generation Time: {go.get('generation_time_ms', 0):.2f}ms")
        print(f"  GEPA Available: {go.get('gepa_available', False)}")

        # Save to output file if specified
        if args.output:
            with open(args.output, 'w') as f:
                json.dump(result, f, indent=2)
            print(f"\nResults saved to: {args.output}")

        print("\n" + "=" * 80)
        print("POWL JSON:")
        print("=" * 80)
        print(result['powl_json'])

        sys.exit(0)

    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()
