"""
TPOT2 Bridge for YAWL Self-Play Loop v3.0

Integrates TPOT2 genetic programming with QLever for query optimization.
"""

import json
import logging
from pathlib import Path
from typing import Dict, List, Any, Optional, Tuple
from dataclasses import dataclass
import numpy as np
from sklearn.metrics import accuracy_score
import requests
from rdflib import Graph, URIRef, Literal
from rdflib.namespace import RDF, RDFS, XSD
from time import time, sleep

logger = logging.getLogger(__name__)

@dataclass
class PipelineNode:
    """Represents a node in the optimization pipeline."""
    id: str
    type: str
    parameters: Dict[str, Any]
    parents: List[str] = None
    children: List[str] = None

    def __post_init__(self):
        if self.parents is None:
            self.parents = []
        if self.children is None:
            self.children = []

@dataclass
class FitnessScore:
    """Represents a fitness score for a pipeline."""
    accuracy: float
    complexity: float
    execution_time: float
    score: float

    def calculate_fitness(self):
        # Combine accuracy with complexity penalty
        complexity_penalty = self.complexity * 0.1
        self.score = self.accuracy - complexity_penalty
        return self.score

class Tpot2Bridge:
    """Bridge between TPOT2 and QLever for genetic programming optimization."""

    def __init__(self, qlever_endpoint: str = "http://localhost:8080",
                 max_generations: int = 50, population_size: int = 20):
        self.qlever_endpoint = qlever_endpoint
        self.max_generations = max_generations
        self.population_size = population_size
        self.pipeline_nodes: List[PipelineNode] = []
        self.best_pipeline: Optional[PipelineNode] = None
        self.best_fitness: Optional[FitnessScore] = None

    def _query_qlever(self, query: str) -> Dict[str, Any]:
        """Execute a SPARQL query against QLever."""
        try:
            response = requests.post(
                self.qlever_endpoint,
                data={"query": query},
                timeout=30
            )
            response.raise_for_status()
            return response.json()
        except requests.RequestException as e:
            logger.error(f"QLever query failed: {e}")
            return {"results": {"bindings": []}}
        except Exception as e:
            logger.error(f"QLever query failed: {e}")
            return {"results": {"bindings": []}}

    def _query_compositions_from_qlever(self) -> List[Dict[str, Any]]:
        """Load valid compositions from QLever."""
        query = """
        SELECT DISTINCT ?pipeline ?type ?params WHERE {
            ?pipeline a <https://yawl.io/sim#CompositionPipeline> ;
                      <https://yawl.io/sim#hasType> ?type ;
                      <https://yawl.io/sim#hasParameters> ?params .
        }
        """
        result = self._query_qlever(query)

        compositions = []
        for binding in result.get("results", {}).get("bindings", []):
            composition = {
                "pipeline": binding.get("pipeline", {}).get("value"),
                "type": binding.get("type", {}).get("value"),
                "params": json.loads(binding.get("params", {}).get("value", "{}"))
            }
            compositions.append(composition)

        logger.info(f"Loaded {len(compositions)} compositions from QLever")
        return compositions

    def _validate_pipeline(self, pipeline: PipelineNode) -> FitnessScore:
        """Evaluate pipeline fitness by executing test queries."""
        start_time = time()

        # Generate test query based on pipeline type
        test_query = self._generate_test_query(pipeline)

        # Execute query and measure performance
        result = self._query_qlever(test_query)

        execution_time = time() - start_time
        results_count = len(result.get("results", {}).get("bindings", []))

        # Calculate fitness metrics
        accuracy = min(1.0, results_count / 100.0)  # Normalize to 0-1
        complexity = len(pipeline.parameters) * 0.5  # Simulate complexity

        fitness = FitnessScore(
            accuracy=accuracy,
            complexity=complexity,
            execution_time=execution_time,
            score=0.0  # Will be calculated
        )

        fitness.calculate_fitness()

        return fitness

    def _generate_test_query(self, pipeline: PipelineNode) -> str:
        """Generate a test query for the given pipeline."""
        base_query = f"""
        SELECT DISTINCT ?element ?type WHERE {{
            ?element a <https://yawl.io/sim#{pipeline.type}> ;
                     rdfs:label ?label .
        }}
        LIMIT 100
        """
        return base_query

    def _create_offspring(self, parent1: PipelineNode, parent2: PipelineNode) -> PipelineNode:
        """Create offspring through crossover and mutation."""
        # Crossover: combine parameters from both parents
        child_params = parent1.parameters.copy()

        # Mutation: randomly modify some parameters
        for key in child_params:
            if np.random.random() < 0.1:  # 10% mutation rate
                if isinstance(child_params[key], int):
                    child_params[key] = np.random.randint(1, 10)
                elif isinstance(child_params[key], float):
                    child_params[key] = np.random.uniform(0.0, 1.0)

        return PipelineNode(
            id=f"node_{len(self.pipeline_nodes)}",
            type=parent1.type,
            parameters=child_params
        )

    def _evaluate_population(self, population: List[PipelineNode]) -> List[Tuple[PipelineNode, FitnessScore]]:
        """Evaluate all pipelines in the population."""
        evaluated = []

        for pipeline in population:
            try:
                fitness = self._validate_pipeline(pipeline)
                evaluated.append((pipeline, fitness))
                logger.debug(f"Pipeline {pipeline.id} fitness: {fitness.score:.4f}")
            except Exception as e:
                logger.error(f"Failed to evaluate pipeline {pipeline.id}: {e}")
                # Give worst possible score for failed evaluation
                fitness = FitnessScore(0.0, 100.0, 1000.0, -100.0)
                evaluated.append((pipeline, fitness))

        return evaluated

    def run_optimization(self, compositions: List[Dict[str, Any]],
                        generations: Optional[int] = None,
                        population: Optional[int] = None) -> Dict[str, Any]:
        """Run TPOT2-style genetic programming optimization."""
        generations = generations or self.max_generations
        pop_size = population or self.population_size

        logger.info(f"Starting optimization with {generations} generations, population {pop_size}")

        # Initialize population from compositions
        population_nodes = []
        for comp in compositions[:pop_size]:
            node = PipelineNode(
                id=f"comp_{comp['pipeline'].split('/')[-1]}",
                type=comp['type'],
                parameters=comp['params']
            )
            population_nodes.append(node)

        # Add random pipelines to fill population
        while len(population_nodes) < pop_size:
            node = PipelineNode(
                id=f"random_{len(self.pipeline_nodes)}",
                type=np.random.choice(['Filter', 'Join', 'Transform']),
                parameters={
                    'threshold': np.random.uniform(0.5, 1.0),
                    'strategy': np.random.choice(['hash', 'bloom', 'index'])
                }
            )
            population_nodes.append(node)

        # Main evolutionary loop
        for generation in range(generations):
            logger.info(f"Generation {generation + 1}/{generations}")

            # Evaluate current population
            evaluated = self._evaluate_population(population_nodes)
            logger.info(f"Evaluation completed with {len(evaluated)} pipelines")

            # Sort by fitness (descending)
            evaluated.sort(key=lambda x: x[1].score, reverse=True)

            # Track best pipeline
            best_pipeline, best_fitness = evaluated[0]

            if self.best_fitness is None or best_fitness.score > self.best_fitness.score:
                self.best_pipeline = best_pipeline
                self.best_fitness = best_fitness
                logger.info(f"New best pipeline found! Score: {best_fitness.score:.4f}")

            # Select parents for next generation (top 50%)
            parents = [p for p, _ in evaluated[:pop_size//2]]
            parents = [p for p, _ in evaluated[:pop_size//2]]

            # Create next generation
            next_generation = parents.copy()  # Elitism

            while len(next_generation) < pop_size:
                # Select two parents
                parent1, parent2 = np.random.choice(parents, 2, replace=False)

                # Create offspring
                child = self._create_offspring(parent1, parent2)
                next_generation.append(child)

            population = next_generation

            # Small delay to avoid overwhelming QLever
            sleep(0.1)

        # Write best pipeline to QLever as OptimalPipeline
        self._write_optimal_pipeline()

        return {
            "best_pipeline": self.best_pipeline,
            "best_fitness": self.best_fitness,
            "generations": generations,
            "population_size": population
        }

    def _write_optimal_pipeline(self):
        """Write the optimal pipeline to QLever as OptimalPipeline triple."""
        if not self.best_pipeline:
            return

        # Create RDF graph
        g = Graph()

        # Define namespaces
        SIM = "https://yawl.io/sim#"

        # Create OptimalPipeline instance
        pipeline_uri = URIRef(f"{SIM}OptimalPipeline_{int(time())}")
        g.add((pipeline_uri, RDF.type, URIRef(f"{SIM}OptimalPipeline")))

        # Add pipeline details
        g.add((pipeline_uri, URIRef(f"{SIM}hasType"), Literal(self.best_pipeline.type)))
        g.add((pipeline_uri, URIRef(f"{SIM}hasFitness"), Literal(self.best_fitness.score)))
        g.add((pipeline_uri, URIRef(f"{SIM}hasAccuracy"), Literal(self.best_fitness.accuracy)))
        g.add((pipeline_uri, URIRef(f"{SIM}hasComplexity"), Literal(self.best_fitness.complexity)))

        # Add parameters
        for param, value in self.best_pipeline.parameters.items():
            g.add((pipeline_uri, URIRef(f"{SIM}hasParameter_{param}"), Literal(value)))

        # Serialize and store
        rdf_data = g.serialize(format="turtle")

        # Update QLever's knowledge base
        self._update_qlever_kb(rdf_data)

        logger.info("OptimalPipeline written to QLever")

    def _update_qlever_kb(self, rdf_data: str):
        """Update QLever knowledge base with new RDF data."""
        # This would typically involve updating the QLever RDF store
        # For now, we'll simulate by storing to a file
        timestamp = int(time())
        output_file = f"optimal_pipeline_{timestamp}.ttl"

        with open(output_file, 'w') as f:
            f.write(rdf_data)

        logger.info(f"OptimalPipeline RDF stored to {output_file}")

    def load_compositions_from_qlever(self) -> List[Dict[str, Any]]:
        """Public method to load compositions from QLever."""
        return self._query_compositions_from_qlever()

    def get_optimal_pipeline(self) -> Optional[Dict[str, Any]]:
        """Get the optimal pipeline found during optimization."""
        if not self.best_pipeline:
            return None

        return {
            "id": self.best_pipeline.id,
            "type": self.best_pipeline.type,
            "parameters": self.best_pipeline.parameters,
            "fitness": {
                "score": self.best_fitness.score,
                "accuracy": self.best_fitness.accuracy,
                "complexity": self.best_fitness.complexity,
                "execution_time": self.best_fitness.execution_time
            }
        }