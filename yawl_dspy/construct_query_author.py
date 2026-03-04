"""
DSPy CONSTRUCT Query Author for YAWL Self-Play Loop v3.0

Uses DSPy to generate valid SPARQL CONSTRUCT queries based on ontology schemas
and transformation goals.
"""

import json
import logging
import time
from pathlib import Path
from typing import Dict, List, Any, Optional, Tuple
from dataclasses import dataclass, asdict
import numpy as np
from rdflib import Graph, URIRef, Literal, Namespace
from rdflib.namespace import RDF, RDFS, XSD, OWL
import requests

logger = logging.getLogger(__name__)

try:
    import dspy
    from dspy.teleprompters import BootstrapFewShot
    from dspy.primitives.program import Program
    DSPY_AVAILABLE = True
except ImportError:
    DSPY_AVAILABLE = False
    logger.warning("DSPy not available. CONSTRUCT query generation will be limited.")

logger = logging.getLogger(__name__)

# Define namespaces
YAWL = "https://yawl.io/yawl#"
SIM = "https://yawl.io/sim#"

@dataclass
class OntologySchema:
    """Represents ontology schema for query generation."""
    classes: List[Dict[str, Any]]
    properties: List[Dict[str, Any]]
    individuals: List[Dict[str, Any]]

@dataclass
class TransformationGoal:
    """Represents transformation goal for CONSTRUCT queries."""
    target_classes: List[str]
    relationship_types: List[str]
    output_format: str = "TURTLE"
    constraints: Dict[str, Any] = None

@dataclass
class ConstructQuery:
    """Generated CONSTRUCT query with validation results."""
    query: str
    is_valid: bool
    validation_errors: List[str]
    execution_time: float = 0.0
    result_count: int = 0
    score: float = 0.0

class ConstructQueryTemplate(dspy.Module):
    """DSPy template for CONSTRUCT query generation."""

    def __init__(self):
        super().__init__()

        # Define prompts
        self.generate_query = dspy.Predict(
            "ontology_schema: {ontology_schema}\n"
            "transformation_goal: {transformation_goal}\n"
            "task: Generate a SPARQL CONSTRUCT query that transforms the source ontology\n"
            "according to the specified goal.\n\n"
            "CONSTRUCT {",

            "query_output: {query}",
            temperature=0.3
        )

        self.validate_query = dspy.Predict(
            "query: {query}\n"
            "task: Check if this is a valid SPARQL CONSTRUCT query.\n"
            "Return True if valid, False otherwise, and list any errors.",

            "is_valid: {valid}",
            "errors: {errors}",
            temperature=0.1
        )

        self.refine_query = dspy.Predict(
            "original_query: {query}\n"
            "errors: {errors}\n"
            "ontology_schema: {ontology_schema}\n"
            "task: Fix the errors in this SPARQL CONSTRUCT query.",

            "refined_query: {refined_query}",
            temperature=0.2
        )

class ConstructQueryAuthor:
    """DSPy module for authoring valid CONARIES queries."""

    def __init__(self, qlever_endpoint: str = "http://localhost:8080"):
        self.qlever_endpoint = qlever_endpoint
        self.query_template = ConstructQueryTemplate() if DSPY_AVAILABLE else None
        self.generated_queries: List[ConstructQuery] = []

    def _parse_ontology_schema(self, schema_data: Dict[str, Any]) -> OntologySchema:
        """Parse ontology schema data into structured format."""
        return OntologySchema(
            classes=schema_data.get("classes", []),
            properties=schema_data.get("properties", []),
            individuals=schema_data.get("individuals", [])
        )

    def _validate_construct_syntax(self, query: str) -> Tuple[bool, List[str]]:
        """Basic syntax validation for CONSTRUCT queries."""
        errors = []

        # Check for required CONSTRUCT clause
        if not query.upper().startswith("CONSTRUCT"):
            errors.append("Query must start with CONSTRUCT clause")

        # Check for balanced braces
        open_braces = query.count("{")
        close_braces = query.count("}")
        if open_braces != close_braces:
            errors.append(f"Unbalanced braces: {open_braces} open, {close_braces} close")

        # Check for WHERE clause (required for most CONSTRUCT queries)
        if "WHERE" not in query.upper():
            errors.append("CONSTRUCT queries should include a WHERE clause")

        # Check for proper graph pattern syntax
        if "CONSTRUCT {" in query and "WHERE {" not in query:
            errors.append("CONSTRUCT should be followed by WHERE clause with graph patterns")

        return len(errors) == 0, errors

    def _execute_query(self, query: str) -> Tuple[float, int]:
        """Execute query and measure performance."""
        try:
            start_time = time.time()
            response = requests.post(
                self.qlever_endpoint,
                data={"query": query},
                timeout=30
            )
            execution_time = time.time() - start_time

            if response.status_code != 200:
                raise Exception(f"QLever error: {response.status_code}")

            result = response.json()
            result_count = len(result.get("results", {}).get("bindings", []))

            return execution_time, result_count

        except Exception as e:
            logger.error(f"Query execution failed: {e}")
            return 30.0, 0  # Max timeout, no results

    def _generate_initial_query(self, ontology_schema: OntologySchema,
                               transformation_goal: TransformationGoal) -> str:
        """Generate initial CONSTRUCT query based on schema and goals."""
        # Extract information from schema
        class_uris = [cls.get("uri", "") for cls in ontology_schema.classes]
        property_uris = [prop.get("uri", "") for prop in ontology_schema.properties]

        # Generate basic CONSTRUCT template
        construct_patterns = []

        # Add triples for target classes
        for class_uri in transformation_goal.target_classes:
            if class_uri in class_uris:
                construct_patterns.append(f"?subject a <{class_uri}> .")

        # Add property relationships
        for rel_type in transformation_goal.relationship_types:
            if rel_type in property_uris:
                construct_patterns.append(f"?subject <{rel_type}> ?object .")

        # Build query
        construct_section = "\n    ".join(construct_patterns) if construct_patterns else "?subject ?predicate ?object ."

        query = f"""
CONSTRUCT {{
    {construct_section}
}}
WHERE {{
    # Source patterns based on ontology classes
    {' UNION '.join([f"?subject a <{cls['uri']}>" for cls in ontology_schema.classes[:3]] if ontology_schema.classes else "")}

    # Add property patterns
    {' UNION '.join([f"?subject <{prop['uri']}> ?object" for prop in ontology_schema.properties[:3]] if ontology_schema.properties else "")}

    # Basic optional constraints
    FILTER EXISTS {{ ?subject a ?type }}
}}
LIMIT 100
        """.strip()

        return query

    def forward(self, ontology_schema: Dict[str, Any],
                transformation_goal: Dict[str, Any],
                type_constraints: Optional[List[str]] = None) -> ConstructQuery:
        """Generate valid CONSTRUCT query using DSPy."""

        # Parse inputs
        schema = self._parse_ontology_schema(ontology_schema)
        goal = TransformationGoal(**transformation_goal)

        logger.info(f"Generating CONSTRUCT query for {len(goal.target_classes)} target classes")

        # Step 1: Generate initial query
        initial_query = self._generate_initial_query(schema, goal)

        # Step 2: Validate query
        is_valid, errors = self._validate_construct_syntax(initial_query)

        refined_query = initial_query

        if not is_valid:
            # Try to refine query
            refined_query = self._refine_query(initial_query, errors, schema, goal)
            is_valid, errors = self._validate_construct_syntax(refined_query)

            if not is_valid:
                # Final fallback - generate minimal valid query
                refined_query = self._generate_fallback_query()
                is_valid, errors = self._validate_construct_syntax(refined_query)
                logger.warning(f"Fallback query used with {len(errors)} errors")

        # Step 3: Execute query
        execution_time, result_count = self._execute_query(refined_query)

        # Step 4: Calculate quality score
        score = self._calculate_query_score(
            refined_query, is_valid, errors, execution_time, result_count
        )

        # Create result
        result = ConstructQuery(
            query=refined_query,
            is_valid=is_valid,
            validation_errors=errors,
            execution_time=execution_time,
            result_count=result_count,
            score=score
        )

        self.generated_queries.append(result)
        logger.info(f"Generated CONSTRUCT query with score: {score:.4f}")

        return result

    def _refine_query(self, query: str, errors: List[str],
                     schema: OntologySchema, goal: TransformationGoal) -> str:
        """Refine query to fix validation errors."""
        refined = query

        # Simple fixes for common errors
        if "CONSTRUCT" not in refined.upper():
            refined = "CONSTRUCT {\n    ?subject ?predicate ?object .\n}\n" + refined

        if "WHERE" not in refined.upper():
            refined = refined + "\nWHERE {\n    ?subject a ?type .\n}"

        # Add basic patterns if missing
        if "FILTER" not in refined:
            refined = refined.replace("WHERE {", "WHERE {\n    FILTER EXISTS { ?subject a ?type }")

        return refined

    def _generate_fallback_query(self) -> str:
        """Generate a minimal valid CONSTRUCT query."""
        return """
CONSTRUCT {
    ?subject ?predicate ?object .
}
WHERE {
    ?subject a ?type .
    FILTER EXISTS { ?subject ?predicate ?object }
}
LIMIT 100
        """.strip()

    def _calculate_query_score(self, query: str, is_valid: bool,
                            errors: List[str], execution_time: float,
                            result_count: int) -> float:
        """Calculate overall quality score for the query."""

        # Base score from validity
        validity_score = 1.0 if is_valid else 0.3

        # Penalty for errors
        error_penalty = len(errors) * 0.1
        error_score = max(0.0, 1.0 - error_penalty)

        # Performance score (faster is better)
        performance_score = max(0.0, 1.0 - (execution_time / 10.0))

        # Result completeness score
        result_score = min(1.0, result_count / 100.0)

        # Combined score
        score = (
            0.4 * validity_score +
            0.2 * error_score +
            0.2 * performance_score +
            0.2 * result_score
        )

        return score

    def generate_multiple_queries(self, ontology_schema: Dict[str, Any],
                                transformation_goals: List[Dict[str, Any]],
                                num_queries: int = 3) -> List[ConstructQuery]:
        """Generate multiple CONSTRUCT queries with different strategies."""
        results = []

        for i, goal_data in enumerate(transformation_goals[:num_queries]):
            logger.info(f"Generating query {i+1}/{num_queries}")

            # Add variation to generation strategy
            variation = i / num_queries
            goal_data["variation"] = variation

            result = self.forward(ontology_schema, goal_data)
            results.append(result)

        # Sort by score
        results.sort(key=lambda x: x.score, reverse=True)

        return results

    def get_best_query(self) -> Optional[ConstructQuery]:
        """Get the highest-scoring query generated so far."""
        if not self.generated_queries:
            return None

        return max(self.generated_queries, key=lambda x: x.score)

    def export_queries(self, output_file: str = "construct_queries.json"):
        """Export generated queries to JSON file."""
        export_data = {
            "total_queries": len(self.generated_queries),
            "queries": [
                {
                    "query": q.query,
                    "is_valid": q.is_valid,
                    "validation_errors": q.validation_errors,
                    "execution_time": q.execution_time,
                    "result_count": q.result_count,
                    "score": q.score
                } for q in self.generated_queries
            ]
        }

        with open(output_file, 'w') as f:
            json.dump(export_data, f, indent=2)

        logger.info(f"Generated {len(self.generated_queries)} CONSTRUCT queries exported to {output_file}")
        return output_file