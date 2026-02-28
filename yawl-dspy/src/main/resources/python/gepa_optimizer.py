"""
GEPA (Gradient Estimation for Prompt Architecture) Optimizer for DSPy Programs.

This module provides configurable GEPA optimization for DSPy programs targeting
both behavioral accuracy and performance metrics for YAWL workflow generation.

The optimizer supports three optimization targets:
- behavioral: Focus on perfect behavioral footprint agreement (100% accuracy)
- performance: Focus on execution time and resource utilization
- balanced: Combine behavioral accuracy with performance considerations

Author: YAWL Foundation
Version: 6.0.0
"""

import json
import hashlib
import logging
import time
from dataclasses import dataclass, field, asdict
from typing import Any, Dict, List, Optional, Callable, Tuple
from datetime import datetime
from pathlib import Path
from enum import Enum
from functools import lru_cache

try:
    import dspy
    DSPY_AVAILABLE = True
except ImportError:
    DSPY_AVAILABLE = False

# Configure logging
logger = logging.getLogger(__name__)


class OptimizationTarget(str, Enum):
    """GEPA optimization target types."""
    BEHAVIORAL = "behavioral"
    PERFORMANCE = "performance"
    BALANCED = "balanced"


class AutoMode(str, Enum):
    """GEPA auto-optimization modes."""
    LIGHT = "light"
    MEDIUM = "medium"
    HEAVY = "heavy"
    CUSTOM = "custom"


@dataclass
class BehavioralFootprint:
    """
    Behavioral footprint for workflow validation.

    Represents the execution semantics of a workflow as a set of relations:
    - Direct succession: A directly follows B
    - Concurrency: A and B can execute in parallel
    - Exclusivity: A and B are mutually exclusive (XOR)
    """
    direct_succession: Dict[Tuple[str, str], bool] = field(default_factory=dict)
    concurrency: Dict[Tuple[str, str], bool] = field(default_factory=dict)
    exclusivity: Dict[Tuple[str, str], bool] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        """Convert to JSON-serializable dict."""
        return {
            "direct_succession": [list(k) + [v] for k, v in self.direct_succession.items()],
            "concurrency": [list(k) + [v] for k, v in self.concurrency.items()],
            "exclusivity": [list(k) + [v] for k, v in self.exclusivity.items()]
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'BehavioralFootprint':
        """Create from dict representation."""
        footprint = cls()
        for item in data.get("direct_succession", []):
            if len(item) >= 3:
                footprint.direct_succession[(item[0], item[1])] = item[2]
        for item in data.get("concurrency", []):
            if len(item) >= 3:
                footprint.concurrency[(item[0], item[1])] = item[2]
        for item in data.get("exclusivity", []):
            if len(item) >= 3:
                footprint.exclusivity[(item[0], item[1])] = item[2]
        return footprint


@dataclass
class PerformanceMetrics:
    """Performance metrics for workflow execution."""
    avg_execution_time_ms: float = 0.0
    p99_execution_time_ms: float = 0.0
    resource_utilization: float = 0.0
    throughput_tasks_per_sec: float = 0.0
    memory_peak_mb: float = 0.0

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class GepaOptimizationResult:
    """Result of GEPA optimization process."""
    target: str
    score: float
    behavioral_footprint: Optional[BehavioralFootprint] = None
    performance_metrics: Optional[PerformanceMetrics] = None
    footprint_agreement: float = 0.0
    optimization_history: List[Dict[str, Any]] = field(default_factory=list)
    timestamp: str = field(default_factory=lambda: datetime.utcnow().isoformat() + "Z")

    def to_dict(self) -> Dict[str, Any]:
        result = {
            "target": self.target,
            "score": self.score,
            "footprint_agreement": self.footprint_agreement,
            "timestamp": self.timestamp,
            "optimization_history": self.optimization_history
        }
        if self.behavioral_footprint:
            result["behavioral_footprint"] = self.behavioral_footprint.to_dict()
        if self.performance_metrics:
            result["performance_metrics"] = self.performance_metrics.to_dict()
        return result


@dataclass
class GepaConfig:
    """Configuration for GEPA optimizer."""
    target: OptimizationTarget = OptimizationTarget.BALANCED
    auto_mode: AutoMode = AutoMode.MEDIUM
    max_rounds: int = 3
    max_bootstrapped_demos: int = 4
    max_labeled_demos: int = 16
    num_candidate_programs: int = 10
    num_threads: int = 4

    # Target-specific weights
    weight_behavioral: float = 0.7
    weight_performance: float = 0.3

    # Thresholds
    footprint_agreement_threshold: float = 1.0
    performance_improvement_threshold: float = 0.1

    @classmethod
    def from_toml(cls, config_path: Path) -> 'GepaConfig':
        """Load configuration from TOML file."""
        try:
            import tomllib
            with open(config_path, 'rb') as f:
                data = tomllib.load(f)

            opt_config = data.get("optimization", {})
            return cls(
                target=OptimizationTarget(opt_config.get("target", "balanced")),
                auto_mode=AutoMode(opt_config.get("auto_mode", "medium")),
                max_rounds=opt_config.get("max_rounds", 3),
                max_bootstrapped_demos=opt_config.get("max_bootstrapped_demos", 4),
                max_labeled_demos=opt_config.get("max_labeled_demos", 16),
                num_candidate_programs=opt_config.get("num_candidate_programs", 10),
                num_threads=opt_config.get("num_threads", 4),
                weight_behavioral=opt_config.get("weight_behavioral", 0.7),
                weight_performance=opt_config.get("weight_performance", 0.3),
                footprint_agreement_threshold=opt_config.get("footprint_agreement_threshold", 1.0),
                performance_improvement_threshold=opt_config.get("performance_improvement_threshold", 0.1)
            )
        except Exception as e:
            logger.warning(f"Failed to load GEPA config from {config_path}: {e}. Using defaults.")
            return cls()


class FootprintScorer:
    """
    Scores behavioral footprint agreement between reference and generated workflows.

    A perfect score of 1.0 means the generated workflow has identical behavioral
    semantics to the reference workflow.
    """

    def score_footprint(
        self,
        reference: BehavioralFootprint,
        generated: BehavioralFootprint
    ) -> float:
        """
        Calculate footprint agreement score.

        Uses Jaccard similarity for each relation type, then averages.

        Args:
            reference: The reference (expected) footprint
            generated: The generated (actual) footprint

        Returns:
            Agreement score between 0.0 and 1.0
        """
        if not reference.direct_succession and not generated.direct_succession:
            ds_score = 1.0
        else:
            ds_score = self._jaccard_similarity(
                set(reference.direct_succession.keys()),
                set(generated.direct_succession.keys())
            )

        if not reference.concurrency and not generated.concurrency:
            conc_score = 1.0
        else:
            conc_score = self._jaccard_similarity(
                set(reference.concurrency.keys()),
                set(generated.concurrency.keys())
            )

        if not reference.exclusivity and not generated.exclusivity:
            excl_score = 1.0
        else:
            excl_score = self._jaccard_similarity(
                set(reference.exclusivity.keys()),
                set(generated.exclusivity.keys())
            )

        # Weighted average (equal weights for now)
        return (ds_score + conc_score + excl_score) / 3.0

    def extract_footprint(self, workflow_data: Dict[str, Any]) -> BehavioralFootprint:
        """
        Extract behavioral footprint from workflow data.

        Args:
            workflow_data: Workflow specification in POWL/YAWL format

        Returns:
            BehavioralFootprint with extracted relations
        """
        footprint = BehavioralFootprint()

        # Extract from net structure
        net = workflow_data.get("net", {})
        flows = net.get("flows", [])
        transitions = net.get("transitions", [])

        # Build transition lookup
        transition_map = {t.get("id"): t.get("name") for t in transitions}

        # Extract direct succession from flows
        transition_flows = []
        for flow in flows:
            source = flow.get("source", "")
            target = flow.get("target", "")
            # Only consider transition-to-transition flows
            if source.startswith("t") and target.startswith("t"):
                source_name = transition_map.get(source, source)
                target_name = transition_map.get(target, target)
                footprint.direct_succession[(source_name, target_name)] = True
                transition_flows.append((source, target))

        # Detect concurrency (AND splits) and exclusivity (XOR splits)
        splits: Dict[str, List[str]] = {}
        for source, target in transition_flows:
            if source not in splits:
                splits[source] = []
            splits[source].append(target)

        for source, targets in splits.items():
            if len(targets) >= 2:
                source_trans = transition_map.get(source, source)
                # Check split type from transition metadata
                source_info = next(
                    (t for t in transitions if t.get("id") == source),
                    {}
                )
                split_type = source_info.get("splits", "and")

                for i, t1 in enumerate(targets):
                    for t2 in targets[i+1:]:
                        name1 = transition_map.get(t1, t1)
                        name2 = transition_map.get(t2, t2)

                        if split_type == "and":
                            # Parallel: concurrent execution
                            footprint.concurrency[(name1, name2)] = True
                            footprint.concurrency[(name2, name1)] = True
                        elif split_type == "xor":
                            # Exclusive: only one path
                            footprint.exclusivity[(name1, name2)] = True
                            footprint.exclusivity[(name2, name1)] = True

        return footprint

    @staticmethod
    def _jaccard_similarity(set1: set, set2: set) -> float:
        """Calculate Jaccard similarity between two sets."""
        if not set1 and not set2:
            return 1.0
        if not set1 or not set2:
            return 0.0
        intersection = len(set1 & set2)
        union = len(set1 | set2)
        return intersection / union if union > 0 else 0.0


class GepaOptimizer:
    """
    Configurable GEPA optimization for DSPy programs.

    This optimizer wraps DSPy's optimization capabilities with YAWL-specific
    metrics and validation for perfect process generation.

    Usage:
        optimizer = GepaOptimizer(target="behavioral")
        optimized_program = optimizer.optimize_workflow_generation(
            program, training_data, validation_metrics
        )
    """

    def __init__(
        self,
        target: str = "balanced",
        auto_mode: str = "medium",
        config: Optional[GepaConfig] = None,
        config_path: Optional[Path] = None
    ):
        """
        Initialize GEPA optimizer.

        Args:
            target: Optimization target ("behavioral", "performance", "balanced")
            auto_mode: Auto-optimization mode ("light", "medium", "heavy")
            config: Pre-built configuration (overrides other params)
            config_path: Path to TOML configuration file
        """
        if config:
            self.config = config
        elif config_path:
            self.config = GepaConfig.from_toml(config_path)
        else:
            self.config = GepaConfig(
                target=OptimizationTarget(target),
                auto_mode=AutoMode(auto_mode)
            )

        self.footprint_scorer = FootprintScorer()
        self.optimization_history: List[Dict[str, Any]] = []

        if not DSPY_AVAILABLE:
            logger.warning("DSPy not available. GEPA optimization will use fallback mode.")

    def optimize_workflow_generation(
        self,
        program: 'dspy.Module',
        training_data: List[Dict[str, Any]],
        validation_metrics: Optional[Dict[str, Any]] = None
    ) -> Tuple['dspy.Module', GepaOptimizationResult]:
        """
        Optimize DSPy program for perfect process generation.

        Args:
            program: The DSPy program to optimize
            training_data: List of training examples with 'input' and 'output' keys
            validation_metrics: Optional validation metrics to include

        Returns:
            Tuple of (optimized_program, optimization_result)
        """
        start_time = time.time()

        logger.info(
            f"Starting GEPA optimization: target={self.config.target.value}, "
            f"auto={self.config.auto_mode.value}, examples={len(training_data)}"
        )

        result = GepaOptimizationResult(
            target=self.config.target.value,
            score=0.0
        )

        try:
            # Step 1: Convert training data to DSPy Examples
            dspy_examples = self._convert_training_data(training_data)

            if not dspy_examples:
                raise ValueError("No valid training examples after conversion")

            # Step 2: Create optimization metric based on target
            metric = self._create_target_metric()

            # Step 3: Run GEPA optimization
            optimized_program = self._run_gepa_optimization(
                program, dspy_examples, metric
            )

            # Step 4: Calculate final scores
            if validation_metrics:
                result.behavioral_footprint = BehavioralFootprint(
                    **validation_metrics.get("behavioral_footprint", {})
                ) if isinstance(validation_metrics.get("behavioral_footprint"), dict) else None
                result.performance_metrics = PerformanceMetrics(
                    **validation_metrics.get("performance_metrics", {})
                ) if isinstance(validation_metrics.get("performance_metrics"), dict) else None

            # Calculate composite score based on target
            result.score = self._calculate_composite_score(
                result.footprint_agreement,
                result.performance_metrics
            )

            result.optimization_history = self.optimization_history.copy()

            elapsed_ms = (time.time() - start_time) * 1000
            logger.info(
                f"GEPA optimization completed: score={result.score:.3f}, "
                f"footprint_agreement={result.footprint_agreement:.3f}, "
                f"time={elapsed_ms:.1f}ms"
            )

            return optimized_program, result

        except Exception as e:
            logger.error(f"GEPA optimization failed: {e}", exc_info=True)
            # Return original program with failure result
            result.score = 0.0
            result.optimization_history = [{
                "error": str(e),
                "timestamp": datetime.utcnow().isoformat() + "Z"
            }]
            return program, result

    def _convert_training_data(
        self,
        training_data: List[Dict[str, Any]]
    ) -> List['dspy.Example']:
        """Convert dict training data to DSPy Example objects."""
        examples = []

        for i, item in enumerate(training_data):
            try:
                if isinstance(item, dspy.Example):
                    examples.append(item)
                elif isinstance(item, dict):
                    input_data = item.get("input", item.get("workflow_description", ""))
                    output_data = item.get("output", item.get("expected_output", {}))

                    example = dspy.Example(
                        input=str(input_data) if not isinstance(input_data, str) else input_data,
                        output=output_data
                    ).with_inputs("input")
                    examples.append(example)
            except Exception as e:
                logger.warning(f"Failed to convert training example {i}: {e}")

        logger.info(f"Converted {len(examples)} training examples")
        return examples

    def _create_target_metric(self) -> Callable:
        """Create optimization metric based on target type."""

        def behavioral_metric(example, pred, trace=None):
            """Metric for behavioral accuracy (footprint agreement)."""
            try:
                # Extract footprints and compare
                if hasattr(pred, 'powl_json'):
                    generated = json.loads(pred.powl_json)
                    expected = example.output if isinstance(example.output, dict) else {}

                    ref_footprint = self.footprint_scorer.extract_footprint(expected)
                    gen_footprint = self.footprint_scorer.extract_footprint(generated)

                    return self.footprint_scorer.score_footprint(ref_footprint, gen_footprint)
                return 0.0
            except Exception:
                return 0.0

        def performance_metric(example, pred, trace=None):
            """Metric for performance optimization."""
            try:
                # Check execution time and resource utilization
                if hasattr(pred, 'metrics'):
                    metrics = pred.metrics
                    time_score = max(0, 1 - (metrics.get("inference_time_ms", 500) / 1000))
                    return time_score
                return 0.5
            except Exception:
                return 0.5

        def balanced_metric(example, pred, trace=None):
            """Balanced metric combining behavioral and performance."""
            beh_score = behavioral_metric(example, pred, trace)
            perf_score = performance_metric(example, pred, trace)
            return (
                self.config.weight_behavioral * beh_score +
                self.config.weight_performance * perf_score
            )

        metrics = {
            OptimizationTarget.BEHAVIORAL: behavioral_metric,
            OptimizationTarget.PERFORMANCE: performance_metric,
            OptimizationTarget.BALANCED: balanced_metric
        }

        return metrics.get(self.config.target, balanced_metric)

    def _run_gepa_optimization(
        self,
        program: 'dspy.Module',
        examples: List['dspy.Example'],
        metric: Callable
    ) -> 'dspy.Module':
        """Run GEPA optimization on the program."""

        if not DSPY_AVAILABLE:
            logger.warning("DSPy not available, returning original program")
            return program

        try:
            # Try to use GEPA if available (DSPy 2.5+)
            try:
                from dspy import GEPA

                auto_settings = {
                    AutoMode.LIGHT: "light",
                    AutoMode.MEDIUM: "medium",
                    AutoMode.HEAVY: "heavy"
                }

                optimizer = GEPA(
                    metric=metric,
                    auto=auto_settings.get(self.config.auto_mode, "medium"),
                    num_threads=self.config.num_threads
                )

                optimized = optimizer.compile(
                    program,
                    trainset=examples[:self.config.max_labeled_demos]
                )

                self.optimization_history.append({
                    "optimizer": "GEPA",
                    "auto_mode": self.config.auto_mode.value,
                    "examples_used": min(len(examples), self.config.max_labeled_demos),
                    "timestamp": datetime.utcnow().isoformat() + "Z"
                })

                return optimized

            except ImportError:
                # Fallback to BootstrapFewShot for older DSPy versions
                logger.info("GEPA not available, using BootstrapFewShot fallback")

                from dspy import BootstrapFewShot

                optimizer = BootstrapFewShot(
                    metric=metric,
                    max_bootstrapped_demos=self.config.max_bootstrapped_demos,
                    max_rounds=self.config.max_rounds
                )

                optimized = optimizer.compile(
                    program,
                    trainset=examples[:self.config.max_labeled_demos]
                )

                self.optimization_history.append({
                    "optimizer": "BootstrapFewShot",
                    "max_rounds": self.config.max_rounds,
                    "examples_used": min(len(examples), self.config.max_labeled_demos),
                    "timestamp": datetime.utcnow().isoformat() + "Z"
                })

                return optimized

        except Exception as e:
            logger.error(f"Optimization failed: {e}")
            self.optimization_history.append({
                "error": str(e),
                "fallback": "original_program",
                "timestamp": datetime.utcnow().isoformat() + "Z"
            })
            return program

    def _calculate_composite_score(
        self,
        footprint_agreement: float,
        performance_metrics: Optional[PerformanceMetrics]
    ) -> float:
        """Calculate composite optimization score."""

        if self.config.target == OptimizationTarget.BEHAVIORAL:
            return footprint_agreement

        if self.config.target == OptimizationTarget.PERFORMANCE:
            if performance_metrics:
                # Normalize performance metrics to 0-1 scale
                time_score = max(0, 1 - (performance_metrics.avg_execution_time_ms / 1000))
                util_score = performance_metrics.resource_utilization
                return (time_score + util_score) / 2
            return 0.5

        # Balanced
        if performance_metrics:
            time_score = max(0, 1 - (performance_metrics.avg_execution_time_ms / 1000))
            return (
                self.config.weight_behavioral * footprint_agreement +
                self.config.weight_performance * time_score
            )
        return footprint_agreement * self.config.weight_behavioral

    def validate_perfect_generation(
        self,
        generated_workflow: Dict[str, Any],
        reference_workflow: Dict[str, Any]
    ) -> Tuple[bool, Dict[str, Any]]:
        """
        Validate that generated workflow matches reference perfectly.

        Args:
            generated_workflow: The generated workflow specification
            reference_workflow: The reference (expected) workflow specification

        Returns:
            Tuple of (is_perfect, validation_details)
        """
        ref_footprint = self.footprint_scorer.extract_footprint(reference_workflow)
        gen_footprint = self.footprint_scorer.extract_footprint(generated_workflow)

        agreement = self.footprint_scorer.score_footprint(ref_footprint, gen_footprint)

        is_perfect = agreement >= self.config.footprint_agreement_threshold

        details = {
            "footprint_agreement": agreement,
            "is_perfect": is_perfect,
            "threshold": self.config.footprint_agreement_threshold,
            "reference_relations": {
                "direct_succession": len(ref_footprint.direct_succession),
                "concurrency": len(ref_footprint.concurrency),
                "exclusivity": len(ref_footprint.exclusivity)
            },
            "generated_relations": {
                "direct_succession": len(gen_footprint.direct_succession),
                "concurrency": len(gen_footprint.concurrency),
                "exclusivity": len(gen_footprint.exclusivity)
            }
        }

        return is_perfect, details


# Convenience functions for external use

def create_gepa_optimizer(
    target: str = "balanced",
    auto_mode: str = "medium"
) -> GepaOptimizer:
    """Create a GEPA optimizer with specified configuration."""
    return GepaOptimizer(target=target, auto_mode=auto_mode)


def optimize_dspy_program(
    program: 'dspy.Module',
    training_data: List[Dict[str, Any]],
    target: str = "balanced"
) -> Tuple['dspy.Module', GepaOptimizationResult]:
    """
    Convenience function to optimize a DSPy program.

    Args:
        program: The DSPy program to optimize
        training_data: Training examples
        target: Optimization target ("behavioral", "performance", "balanced")

    Returns:
        Tuple of (optimized_program, result)
    """
    optimizer = GepaOptimizer(target=target)
    return optimizer.optimize_workflow_generation(program, training_data)


def score_footprint_agreement(
    reference: Dict[str, Any],
    generated: Dict[str, Any]
) -> float:
    """
    Score footprint agreement between two workflows.

    Args:
        reference: Reference workflow specification
        generated: Generated workflow specification

    Returns:
        Agreement score between 0.0 and 1.0
    """
    scorer = FootprintScorer()
    ref_fp = scorer.extract_footprint(reference)
    gen_fp = scorer.extract_footprint(generated)
    return scorer.score_footprint(ref_fp, gen_fp)
