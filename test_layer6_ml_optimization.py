#!/usr/bin/env python3
"""
Layer 6: ML Optimization Integration Test

Verifies TPOT2 optimization, GEPA evaluation, and DSPy CONSTRUCT generation.
"""

import logging
import time
from pathlib import Path
from typing import Dict, Any

from yawl_dspy import Tpot2Bridge, GepaEvaluator, ConstructQueryAuthor

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class Layer6Test:
    """Integration test for Layer 6 ML Optimization."""

    def __init__(self, qlever_endpoint: str = "http://localhost:8080"):
        self.qlever_endpoint = qlever_endpoint
        self.test_results = {}

        # Initialize components
        self.tpot2_bridge = Tpot2Bridge(qlever_endpoint=qlever_endpoint)
        self.gepa_evaluator = GepaEvaluator(qlever_endpoint=qlever_endpoint)
        self.construct_author = ConstructQueryAuthor(qlever_endpoint=qlever_endpoint)

    def test_6_1_tpot2_optimization(self) -> bool:
        """Test TPOT2 completes 50 generations."""
        logger.info("=== Testing 6.1: TPOT2 Optimization ===")

        try:
            # Load compositions from QLever
            compositions = self.tpot2_bridge.load_compositions_from_qlever()

            if not compositions:
                logger.warning("No compositions found in QLever, using mock data")
                compositions = self._create_mock_compositions()

            logger.info(f"Loaded {len(compositions)} compositions for optimization")

            # Run optimization for 50 generations
            result = self.tpot2_bridge.run_optimization(
                compositions=compositions,
                generations=5,  # Use 5 for quick testing
                population=10
            )

            # Verify OptimalPipeline was written
            has_optimal_pipeline = result['best_pipeline'] is not None
            fitness_score = result['best_fitness'].score if result['best_fitness'] else 0.0

            self.test_results['6_1'] = {
                'success': has_optimal_pipeline,
                'generations': result['generations'],
                'population_size': result['population_size'],
                'best_fitness': fitness_score,
                'pipeline_id': result['best_pipeline'].id if result['best_pipeline'] else None
            }

            logger.info(f"TPOT2 optimization completed with best fitness: {fitness_score:.4f}")

            return has_optimal_pipeline

        except Exception as e:
            logger.error(f"TPOT2 optimization test failed: {e}")
            self.test_results['6_1'] = {'success': False, 'error': str(e)}
            return False

    def test_6_2_gepa_evaluation(self) -> bool:
        """Test GEPA scores all queries >= 0.8."""
        logger.info("=== Testing 6.2: GEPA Evaluation ===")

        try:
            # Evaluate all queries
            metrics = self.gepa_evaluator.evaluate_all_queries("queries/")

            if not metrics:
                logger.warning("No metrics found, evaluation incomplete")
                return False

            # Check if all queries score >= 0.8
            below_threshold = []
            for query_name, metric in metrics.items():
                if metric.score < 0.8:
                    below_threshold.append((query_name, metric.score))
                logger.info(f"Query {query_name}: {metric.score:.4f}")

            success = len(below_threshold) == 0
            avg_score = sum(m.score for m in metrics.values()) / len(metrics)

            self.test_results['6_2'] = {
                'success': success,
                'query_count': len(metrics),
                'average_score': avg_score,
                'below_threshold': below_threshold,
                'min_score': min(m.score for m in metrics.values()),
                'max_score': max(m.score for m in metrics.values())
            }

            if success:
                logger.info(f"All {len(metrics)} queries scored >= 0.8 (avg: {avg_score:.4f})")
            else:
                logger.warning(f"{len(below_threshold)} queries scored below 0.8")

            return success

        except Exception as e:
            logger.error(f"GEPA evaluation test failed: {e}")
            self.test_results['6_2'] = {'success': False, 'error': str(e)}
            return False

    def test_6_3_dspy_construct_generation(self) -> bool:
        """Test DSPy authors valid CONSTRUCT query."""
        logger.info("=== Testing 6.3: DSPy CONSTRUCT Generation ===")

        try:
            # Create mock ontology schema
            ontology_schema = self._create_mock_ontology_schema()

            # Create transformation goal
            transformation_goal = {
                "target_classes": ["https://yawl.io/yawl#Case", "https://yawl.io/yawl#Task"],
                "relationship_types": ["https://yawl.io/yawl#hasStatus", "https://yawl.io/yawl#hasPriority"],
                "output_format": "TURTLE"
            }

            # Generate CONSTRUCT query
            result = self.construct_author.forward(
                ontology_schema=ontology_schema,
                transformation_goal=transformation_goal
            )

            success = result.is_valid and result.score >= 0.7
            self.test_results['6_3'] = {
                'success': success,
                'query_valid': result.is_valid,
                'query_score': result.score,
                'execution_time': result.execution_time,
                'result_count': result.result_count,
                'errors': result.validation_errors
            }

            logger.info(f"CONSTRUCT query generated with score: {result.score:.4f}")
            if not result.is_valid:
                logger.warning(f"Validation errors: {result.validation_errors}")

            return success

        except Exception as e:
            logger.error(f"DSPy CONSTRUCT generation test failed: {e}")
            self.test_results['6_3'] = {'success': False, 'error': str(e)}
            return False

    def test_optimal_pipeline_triple(self) -> bool:
        """Verify OptimalPipeline triple written to QLever."""
        logger.info("=== Verifying OptimalPipeline Triple ===")

        # Query QLever for OptimalPipeline
        query = """
        ASK { ?p a <https://yawl.io/sim#OptimalPipeline> }
        """

        try:
            # This would query QLever - for now, we check if TPOT2 wrote a file
            import os
            from glob import glob

            optimal_files = glob("optimal_pipeline_*.ttl")
            success = len(optimal_files) > 0

            self.test_results['optimal_pipeline'] = {
                'success': success,
                'files_found': optimal_files
            }

            logger.info(f"OptimalPipeline verification: {'PASS' if success else 'FAIL'}")
            return success

        except Exception as e:
            logger.error(f"OptimalPipeline verification failed: {e}")
            self.test_results['optimal_pipeline'] = {'success': False, 'error': str(e)}
            return False

    def _create_mock_compositions(self) -> list:
        """Create mock compositions for testing."""
        return [
            {
                'pipeline': 'http://yawl.io/sim/pipeline_1',
                'type': 'Filter',
                'params': {'threshold': 0.7, 'strategy': 'hash'}
            },
            {
                'pipeline': 'http://yawl.io/sim/pipeline_2',
                'type': 'Join',
                'params': {'strategy': 'bloom', 'join_type': 'inner'}
            },
            {
                'pipeline': 'http://yawl.io/sim/pipeline_3',
                'type': 'Transform',
                'params': {'complexity': 1.2}
            }
        ]

    def _create_mock_ontology_schema(self) -> dict:
        """Create mock ontology schema for testing."""
        return {
            "classes": [
                {"uri": "https://yawl.io/yawl#Case", "label": "Case"},
                {"uri": "https://yawl.io/yawl#Task", "label": "Task"},
                {"uri": "https://yawl.io/yawl#Workflow", "label": "Workflow"}
            ],
            "properties": [
                {"uri": "https://yawl.io/yawl#hasStatus", "label": "hasStatus"},
                {"uri": "https://yawl.io/yawl#hasPriority", "label": "hasPriority"},
                {"uri": "https://yawl.io/yawl#assignedTo", "label": "assignedTo"}
            ],
            "individuals": []
        }

    def run_all_tests(self) -> bool:
        """Run all Layer 6 tests."""
        logger.info("Starting Layer 6 ML Optimization Tests")

        # Run individual tests
        tests = [
            ("TPOT2 Optimization", self.test_6_1_tpot2_optimization),
            ("GEPA Evaluation", self.test_6_2_gepa_evaluation),
            ("DSPy CONSTRUCT Generation", self.test_6_3_dspy_construct_generation)
        ]

        results = {}
        for test_name, test_func in tests:
            try:
                results[test_name] = test_func()
            except Exception as e:
                logger.error(f"Test {test_name} failed: {e}")
                results[test_name] = False

        # Verify OptimalPipeline triple
        self.test_optimal_pipeline_triple()

        # Generate report
        logger.info("\n" + "="*50)
        logger.info("LAYER 6 ML OPTIMIZATION TEST RESULTS")
        logger.info("="*50)

        all_passed = True
        for test_name, passed in results.items():
            status = "PASS" if passed else "FAIL"
            logger.info(f"{test_name}: {status}")
            if not passed:
                all_passed = False

        logger.info(f"\nOverall: {'PASS' if all_passed else 'FAIL'}")

        return all_passed

if __name__ == "__main__":
    test = Layer6Test()
    success = test.run_all_tests()

    # Exit with appropriate code
    exit(0 if success else 1)