"""
GEPA Evaluator for YAWL Self-Play Loop v3.0

Genetic Evolutionary Pipeline Assessment (GEPA) scores SPARQL queries based on:
- Result completeness
- Execution efficiency
- Query complexity
- Semantic validity
"""

import json
import logging
import time
from pathlib import Path
from typing import Dict, List, Any, Optional, Tuple
from dataclasses import dataclass
import numpy as np
import requests
from rdflib import Graph, URIRef, Literal
from rdflib.namespace import RDF, RDFS, XSD

logger = logging.getLogger(__name__)

@dataclass
class QueryMetrics:
    """Metrics for evaluating query quality."""
    result_count: int
    execution_time: float
    complexity_score: float
    semantic_validity: float
    completeness: float
    score: float = 0.0

    def calculate_score(self):
        """Calculate overall score (0.0 to 1.0)."""
        # Weights for different metrics
        weights = {
            'result_count': 0.3,
            'execution_time': 0.2,
            'complexity_score': 0.2,
            'semantic_validity': 0.2,
            'completeness': 0.1
        }

        # Normalize metrics
        normalized_result = min(1.0, self.result_count / 1000.0)  # Cap at 1000 results
        normalized_time = max(0.0, 1.0 - (self.execution_time / 10.0))  # Cap at 10 seconds
        normalized_complexity = max(0.0, 1.0 - self.complexity_score)

        # Calculate weighted score
        self.score = (
            weights['result_count'] * normalized_result +
            weights['execution_time'] * normalized_time +
            weights['complexity_score'] * normalized_complexity +
            weights['semantic_validity'] * self.semantic_validity +
            weights['completeness'] * self.completeness
        )

        return self.score

class GepaEvaluator:
    """Genetic Evolutionary Pipeline Assessment evaluator."""

    def __init__(self, qlever_endpoint: str = "http://localhost:8080",
                 max_results: int = 1000, timeout: int = 30):
        self.qlever_endpoint = qlever_endpoint
        self.max_results = max_results
        self.timeout = timeout
        self.query_scores: Dict[str, QueryMetrics] = {}

    def _execute_query(self, query: str) -> Dict[str, Any]:
        """Execute SPARQL query against QLever."""
        try:
            start_time = time.time()
            response = requests.post(
                self.qlever_endpoint,
                data={"query": query},
                timeout=self.timeout
            )
            execution_time = time.time() - start_time

            if response.status_code != 200:
                raise Exception(f"QLever error: {response.status_code}")

            result = response.json()
            return {
                "success": True,
                "results": result.get("results", {}).get("bindings", []),
                "execution_time": execution_time
            }
        except Exception as e:
            logger.error(f"Query execution failed: {e}")
            return {
                "success": False,
                "results": [],
                "execution_time": self.timeout,
                "error": str(e)
            }

    def _calculate_complexity(self, query: str) -> float:
        """Calculate query complexity score (0.0 to 1.0, higher = more complex)."""
        # Count query components
        clauses = query.upper().split()

        # Count different SPARQL constructs
        construct_count = query.count("CONSTRUCT")
        select_count = query.count("SELECT")
        where_count = query.count("WHERE")
        optional_count = query.count("OPTIONAL")
        union_count = query.count("UNION")
        filter_count = query.count("FILTER")
        subquery_count = query.count("SUBQUERY")
        triple_count = query.count(" . ")

        # Calculate complexity score
        complexity = (
            construct_count * 0.3 +
            select_count * 0.2 +
            where_count * 0.1 +
            optional_count * 0.15 +
            union_count * 0.15 +
            filter_count * 0.05 +
            subquery_count * 0.1 +
            triple_count * 0.001  # Normalize triple count
        )

        return min(1.0, complexity / 10.0)  # Normalize to 0-1

    def _check_semantic_validity(self, query: str) -> float:
        """Check semantic validity of SPARQL query (0.0 to 1.0)."""
        # Basic syntax checks
        if not query.strip():
            return 0.0

        if query.upper().count("SELECT") + query.upper().count("CONSTRUCT") == 0:
            return 0.0

        if query.count("WHERE") == 0 and "CONSTRUCT" not in query.upper():
            return 0.5  # CONSTRUCT doesn't always require WHERE

        # Check for balanced brackets
        open_brackets = query.count("{")
        close_brackets = query.count("}")
        if open_brackets != close_brackets:
            return 0.3

        # Check for common patterns that indicate valid queries
        valid_patterns = [
            "SELECT",
            "CONSTRUCT",
            "WHERE",
            "rdfs:",
            "rdf:",
            "a"
        ]

        pattern_matches = sum(1 for pattern in valid_patterns if pattern in query.upper())
        validity = min(1.0, pattern_matches / len(valid_patterns))

        return validity

    def _evaluate_completeness(self, results: List[Dict], query: str) -> float:
        """Evaluate query completeness based on results and query intent."""
        if not results:
            return 0.0

        # Check for expected result patterns
        expected_patterns = [
            ("element", "resource"),
            ("type", "class"),
            ("label", "literal"),
            ("property", "uri")
        ]

        result_variations = set()
        for result in results:
            for var in result.keys():
                result_variations.add(var.lower())

        completeness = 0.0
        for pattern, _ in expected_patterns:
            if any(pattern in var for var in result_variations):
                completeness += 0.25

        return min(1.0, completeness)

    def evaluate_query(self, query_file: str, query_content: Optional[str] = None) -> QueryMetrics:
        """Evaluate a single SPARQL query and return metrics."""
        # Read query content from file if not provided
        if query_content is None:
            with open(query_file, 'r') as f:
                query_content = f.read()

        query_name = Path(query_file).stem
        logger.info(f"Evaluating query: {query_name}")

        # Execute query
        execution_result = self._execute_query(query_content)

        if not execution_result["success"]:
            logger.warning(f"Query {query_name} failed to execute")
            return QueryMetrics(
                result_count=0,
                execution_time=self.timeout,
                complexity_score=0.0,
                semantic_validity=0.0,
                completeness=0.0
            )

        # Calculate metrics
        result_count = len(execution_result["results"])
        execution_time = execution_result["execution_time"]
        complexity_score = self._calculate_complexity(query_content)
        semantic_validity = self._check_semantic_validity(query_content)
        completeness = self._evaluate_completeness(execution_result["results"], query_content)

        # Create metrics object
        metrics = QueryMetrics(
            result_count=result_count,
            execution_time=execution_time,
            complexity_score=complexity_score,
            semantic_validity=semantic_validity,
            completeness=completeness
        )

        # Calculate final score
        score = metrics.calculate_score()

        # Store results
        self.query_scores[query_name] = metrics

        logger.info(f"Query {query_name} score: {score:.4f} "
                   f"(Results: {result_count}, Time: {execution_time:.2f}s, "
                   f"Complexity: {complexity_score:.2f})")

        return metrics

    def evaluate_all_queries(self, queries_dir: str = "queries/") -> Dict[str, QueryMetrics]:
        """Evaluate all SPARQL queries in the specified directory."""
        queries_path = Path(queries_dir)
        query_files = list(queries_path.glob("*.sparql"))

        if not query_files:
            logger.warning(f"No SPARQL files found in {queries_dir}")
            return {}

        logger.info(f"Evaluating {len(query_files)} queries from {queries_dir}")

        results = {}
        for query_file in query_files:
            metrics = self.evaluate_query(str(query_file))
            results[query_file.stem] = metrics

        return results

    def get_low_scoring_queries(self, threshold: float = 0.8) -> List[Tuple[str, QueryMetrics]]:
        """Get queries that score below the threshold."""
        low_scoring = []

        for query_name, metrics in self.query_scores.items():
            if metrics.score < threshold:
                low_scoring.append((query_name, metrics))

        return sorted(low_scoring, key=lambda x: x[1].score)

    def get_average_score(self) -> float:
        """Get average score across all evaluated queries."""
        if not self.query_scores:
            return 0.0

        total_score = sum(metrics.score for metrics in self.query_scores.values())
        return total_score / len(self.query_scores)

    def generate_evaluation_report(self) -> Dict[str, Any]:
        """Generate a comprehensive evaluation report."""
        if not self.query_scores:
            return {"error": "No queries evaluated yet"}

        # Calculate statistics
        scores = [m.score for m in self.query_scores.values()]
        avg_score = np.mean(scores)
        min_score = np.min(scores)
        max_score = np.max(scores)

        # Get distribution
        below_0_6 = sum(1 for s in scores if s < 0.6)
        between_0_6_0_8 = sum(1 for s in scores if 0.6 <= s < 0.8)
        between_0_8_0_9 = sum(1 for s in scores if 0.8 <= s < 0.9)
        above_0_9 = sum(1 for s in scores if s >= 0.9)

        # Get low-scoring queries
        low_scoring = self.get_low_scoring_queries(0.8)

        return {
            "summary": {
                "total_queries": len(self.query_scores),
                "average_score": float(avg_score),
                "min_score": float(min_score),
                "max_score": float(max_score),
                "queries_below_0_6": below_0_6,
                "queries_0_6_to_0_8": between_0_6_0_8,
                "queries_0_8_to_0_9": between_0_8_0_9,
                "queries_above_0_9": above_0_9,
                "queries_below_threshold_0_8": len(low_scoring)
            },
            "query_scores": {
                name: {
                    "score": metrics.score,
                    "result_count": metrics.result_count,
                    "execution_time": metrics.execution_time,
                    "complexity_score": metrics.complexity_score,
                    "semantic_validity": metrics.semantic_validity,
                    "completeness": metrics.completeness
                } for name, metrics in self.query_scores.items()
            },
            "low_scoring_queries": [
                {
                    "name": name,
                    "score": metrics.score,
                    "fix_needed": True
                } for name, metrics in low_scoring
            ]
        }

    def export_scores(self, output_file: str = "gepa_scores.json"):
        """Export evaluation results to JSON file."""
        report = self.generate_evaluation_report()

        with open(output_file, 'w') as f:
            json.dump(report, f, indent=2)

        logger.info(f"GEPA evaluation results exported to {output_file}")
        return output_file