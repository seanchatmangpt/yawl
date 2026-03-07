"""
DSPy Signatures for YAWL Process Model Generation.

This module defines the DSPy signatures for the 3-stage pipeline:
1. NL → ProcessSpec (Chain-of-Thought)
2. ProcessSpec → ProcessGraph (Chain-of-Thought)
3. ProcessGraph → YAWL XML (Predict)

Executed via JOR4J (Java > OTP > Python > OTP > Java) pattern.
"""

import dspy
from typing import Optional
from pydantic import BaseModel, Field
import json


# ═══════════════════════════════════════════════════════════════════════════════
# STAGE 1: Natural Language → ProcessSpec
# ═══════════════════════════════════════════════════════════════════════════════

class TaskDefModel(BaseModel):
    """Task definition model for JSON serialization."""
    id: str = Field(description="Unique task identifier (snake_case)")
    name: str = Field(description="Human-readable task name")
    type: str = Field(default="atomic", description="Task type: atomic, composite, multiple")
    input_vars: list[str] = Field(default_factory=list, description="Input variable names")
    output_vars: list[str] = Field(default_factory=list, description="Output variable names")
    resource_class: Optional[str] = Field(default=None, description="Required resource class")
    decomposes_to: Optional[str] = Field(default=None, description="Sub-process ID for composite tasks")


class DataObjectDefModel(BaseModel):
    """Data object definition model."""
    id: str = Field(description="Unique data object identifier")
    name: str = Field(description="Human-readable data object name")
    type: str = Field(default="string", description="Data type: string, integer, boolean, date, object")
    initial_value: Optional[str] = Field(default=None, description="Initial value expression")


class ConstraintDefModel(BaseModel):
    """Constraint definition model."""
    type: str = Field(description="Constraint type: sequence, parallel, choice, iteration, data_flow")
    source: str = Field(description="Source task/object ID")
    target: Optional[str] = Field(default=None, description="Target task/object ID")
    condition: Optional[str] = Field(default=None, description="Guard condition")


class CancellationRegionDefModel(BaseModel):
    """Cancellation region definition (YAWL-specific)."""
    trigger_task: str = Field(description="Task that triggers cancellation")
    cancelled_tasks: list[str] = Field(description="Tasks to be cancelled")
    condition: Optional[str] = Field(default=None, description="Optional guard condition")


class ProcessSpecSignature(dspy.Signature):
    """
    Transform natural language process description into structured specification.

    This signature extracts the semantic structure of a workflow from natural
    language, identifying tasks, data objects, constraints, and YAWL-specific
    constructs like OR-joins and cancellation regions.
    """

    nl_description: str = dspy.InputField(
        desc="Natural language process description"
    )
    domain_context: str = dspy.InputField(
        desc="Optional domain knowledge (healthcare, finance, manufacturing, etc.)"
    )

    process_name: str = dspy.OutputField(
        desc="Process name in PascalCase (e.g., PatientAdmission, OrderProcessing)"
    )
    tasks: str = dspy.OutputField(
        desc="JSON array of task definitions with id, name, type, input_vars, output_vars"
    )
    data_objects: str = dspy.OutputField(
        desc="JSON array of data object definitions with id, name, type"
    )
    constraints: str = dspy.OutputField(
        desc="JSON array of constraints (sequence, parallel, choice, iteration, data_flow)"
    )
    or_joins: str = dspy.OutputField(
        desc="JSON array of task IDs that require OR-join semantics"
    )
    cancellation_regions: str = dspy.OutputField(
        desc="JSON array of cancellation region definitions (trigger_task, cancelled_tasks)"
    )


# ═══════════════════════════════════════════════════════════════════════════════
# STAGE 2: ProcessSpec → ProcessGraph
# ═══════════════════════════════════════════════════════════════════════════════

class GraphNodeModel(BaseModel):
    """Graph node model."""
    id: str = Field(description="Unique node identifier")
    type: str = Field(description="Node type: task, condition, xor_gateway, and_gateway, or_gateway")
    name: str = Field(description="Human-readable name")
    task_ref: Optional[str] = Field(default=None, description="Reference to TaskDef ID for task nodes")


class GraphEdgeModel(BaseModel):
    """Graph edge model."""
    from_node: str = Field(alias="from", description="Source node ID")
    to: str = Field(description="Target node ID")
    condition: Optional[str] = Field(default=None, description="Guard condition")
    predicate: Optional[str] = Field(default=None, description="XPath predicate for conditional flow")

    class Config:
        populate_by_name = True


class SplitDefModel(BaseModel):
    """Split/join definition model."""
    node_id: str = Field(description="Gateway node ID")
    branches: list[str] = Field(description="Branch target node IDs")
    conditions: list[str] = Field(default_factory=list, description="Branch conditions")


class ProcessGraphSignature(dspy.Signature):
    """
    Transform structured specification into process graph with control flow.

    This signature converts the semantic ProcessSpec into an explicit graph
    representation with nodes (tasks, gateways, conditions) and edges
    (control flow with conditions).

    YAWL-specific constructs:
    - OR gateways for OR-join/OR-split semantics
    - Cancellation regions attached to tasks
    """

    process_spec: str = dspy.InputField(
        desc="ProcessSpec from Stage 1 as JSON string"
    )

    nodes: str = dspy.OutputField(
        desc="JSON array of graph nodes (tasks, gateways, events)"
    )
    edges: str = dspy.OutputField(
        desc="JSON array of graph edges with from, to, condition"
    )
    input_condition: str = dspy.OutputField(
        desc="Input condition node ID"
    )
    output_condition: str = dspy.OutputField(
        desc="Output condition node ID"
    )
    xor_splits: str = dspy.OutputField(
        desc="JSON array of XOR split definitions (node_id, branches, conditions)"
    )
    and_splits: str = dspy.OutputField(
        desc="JSON array of AND split definitions (node_id, branches)"
    )
    or_splits: str = dspy.OutputField(
        desc="JSON array of OR split definitions (YAWL-specific)"
    )


# ═══════════════════════════════════════════════════════════════════════════════
# STAGE 3: ProcessGraph → YAWL XML
# ═══════════════════════════════════════════════════════════════════════════════

class YawlXmlSignature(dspy.Signature):
    """
    Transform process graph into valid YAWL XML specification.

    This signature generates complete YAWL XML that:
    - Conforms to YAWL XSD schema
    - Includes all decompositions (root net and sub-nets)
    - Properly encodes OR-join/OR-split constructs
    - Includes cancellation region definitions
    - Defines input/output parameters for each task
    """

    process_graph: str = dspy.InputField(
        desc="ProcessGraph from Stage 2 as JSON string"
    )
    spec_id: str = dspy.InputField(
        desc="YAWL specification ID"
    )
    spec_version: str = dspy.InputField(
        desc="Specification version (e.g., 0.1, 1.0)"
    )

    yawl_xml: str = dspy.OutputField(
        desc="Complete YAWL XML specification conforming to YAWL XSD"
    )
    decomposition_id: str = dspy.OutputField(
        desc="Root decomposition ID"
    )
    metadata: str = dspy.OutputField(
        desc="JSON object with generation metadata (task_count, or_join_count, etc.)"
    )


# ═══════════════════════════════════════════════════════════════════════════════
# MODULE WRAPPERS
# ═══════════════════════════════════════════════════════════════════════════════

class Stage1SpecGenerator(dspy.Module):
    """Stage 1: NL → ProcessSpec using Chain-of-Thought."""

    def __init__(self):
        super().__init__()
        self.generate = dspy.ChainOfThought(ProcessSpecSignature)

    def forward(self, nl_description: str, domain_context: str = ""):
        return self.generate(nl_description=nl_description, domain_context=domain_context)


class Stage2GraphBuilder(dspy.Module):
    """Stage 2: ProcessSpec → ProcessGraph using Chain-of-Thought."""

    def __init__(self):
        super().__init__()
        self.build = dspy.ChainOfThought(ProcessGraphSignature)

    def forward(self, process_spec: str):
        return self.build(process_spec=process_spec)


class Stage3YawlRenderer(dspy.Module):
    """Stage 3: ProcessGraph → YAWL XML using Predict."""

    def __init__(self):
        super().__init__()
        self.render = dspy.Predict(YawlXmlSignature)

    def forward(self, process_graph: str, spec_id: str, spec_version: str):
        return self.render(
            process_graph=process_graph,
            spec_id=spec_id,
            spec_version=spec_version
        )


# ═══════════════════════════════════════════════════════════════════════════════
# END-TO-END PIPELINE
# ═══════════════════════════════════════════════════════════════════════════════

class YawlGeneratorPipeline(dspy.Module):
    """
    Complete 3-stage pipeline for YAWL process generation.

    Usage:
        pipeline = YawlGeneratorPipeline()
        result = pipeline(
            nl_description="Patient admission process with triage...",
            domain_context="healthcare",
            spec_id="PatientAdmission",
            spec_version="1.0"
        )
        yawl_xml = result.yawl_xml
    """

    def __init__(self):
        super().__init__()
        self.stage1 = Stage1SpecGenerator()
        self.stage2 = Stage2GraphBuilder()
        self.stage3 = Stage3YawlRenderer()

    def forward(self, nl_description: str, domain_context: str, spec_id: str, spec_version: str):
        # Stage 1: NL → ProcessSpec
        spec_result = self.stage1(nl_description=nl_description, domain_context=domain_context)

        # Build ProcessSpec JSON for Stage 2
        process_spec = json.dumps({
            "process_name": spec_result.process_name,
            "tasks": json.loads(spec_result.tasks),
            "data_objects": json.loads(spec_result.data_objects),
            "constraints": json.loads(spec_result.constraints),
            "or_joins": json.loads(spec_result.or_joins),
            "cancellation_regions": json.loads(spec_result.cancellation_regions)
        })

        # Stage 2: ProcessSpec → ProcessGraph
        graph_result = self.stage2(process_spec=process_spec)

        # Build ProcessGraph JSON for Stage 3
        process_graph = json.dumps({
            "nodes": json.loads(graph_result.nodes),
            "edges": json.loads(graph_result.edges),
            "input_condition": graph_result.input_condition,
            "output_condition": graph_result.output_condition,
            "xor_splits": json.loads(graph_result.xor_splits),
            "and_splits": json.loads(graph_result.and_splits),
            "or_splits": json.loads(graph_result.or_splits)
        })

        # Stage 3: ProcessGraph → YAWL XML
        yawl_result = self.stage3(
            process_graph=process_graph,
            spec_id=spec_id,
            spec_version=spec_version
        )

        return dspy.Prediction(
            process_spec=process_spec,
            process_graph=process_graph,
            yawl_xml=yawl_result.yawl_xml,
            decomposition_id=yawl_result.decomposition_id,
            metadata=yawl_result.metadata
        )


# ═══════════════════════════════════════════════════════════════════════════════
# ENTRY POINT FOR JOR4J
# ═══════════════════════════════════════════════════════════════════════════════

def generate_process_spec(nl_description: str, domain_context: str = "") -> str:
    """
    Generate ProcessSpec from natural language.

    Entry point for JOR4J bridge.

    Args:
        nl_description: Natural language process description
        domain_context: Optional domain knowledge (healthcare, finance, etc.)

    Returns:
        JSON string of ProcessSpec
    """
    generator = Stage1SpecGenerator()
    result = generator(nl_description=nl_description, domain_context=domain_context)

    return json.dumps({
        "process_name": result.process_name,
        "tasks": json.loads(result.tasks),
        "data_objects": json.loads(result.data_objects),
        "constraints": json.loads(result.constraints),
        "or_joins": json.loads(result.or_joins),
        "cancellation_regions": json.loads(result.cancellation_regions)
    })


def build_process_graph(process_spec_json: str) -> str:
    """
    Build ProcessGraph from ProcessSpec.

    Entry point for JOR4J bridge.

    Args:
        process_spec_json: ProcessSpec as JSON string

    Returns:
        JSON string of ProcessGraph
    """
    builder = Stage2GraphBuilder()
    result = builder(process_spec=process_spec_json)

    return json.dumps({
        "nodes": json.loads(result.nodes),
        "edges": json.loads(result.edges),
        "input_condition": result.input_condition,
        "output_condition": result.output_condition,
        "xor_splits": json.loads(result.xor_splits),
        "and_splits": json.loads(result.and_splits),
        "or_splits": json.loads(result.or_splits)
    })


def render_yawl_xml(process_graph_json: str, spec_id: str, spec_version: str) -> str:
    """
    Render YAWL XML from ProcessGraph.

    Entry point for JOR4J bridge.

    Args:
        process_graph_json: ProcessGraph as JSON string
        spec_id: YAWL specification ID
        spec_version: Specification version

    Returns:
        JSON string with yawl_xml, decomposition_id, metadata
    """
    renderer = Stage3YawlRenderer()
    result = renderer(
        process_graph=process_graph_json,
        spec_id=spec_id,
        spec_version=spec_version
    )

    return json.dumps({
        "yawl_xml": result.yawl_xml,
        "decomposition_id": result.decomposition_id,
        "metadata": json.loads(result.metadata)
    })


def generate_yawl_complete(nl_description: str, domain_context: str,
                           spec_id: str, spec_version: str) -> str:
    """
    Complete end-to-end generation.

    Entry point for JOR4J bridge.

    Args:
        nl_description: Natural language process description
        domain_context: Optional domain knowledge
        spec_id: YAWL specification ID
        spec_version: Specification version

    Returns:
        JSON string with yawl_xml, process_spec, process_graph, metadata
    """
    pipeline = YawlGeneratorPipeline()
    result = pipeline(
        nl_description=nl_description,
        domain_context=domain_context,
        spec_id=spec_id,
        spec_version=spec_version
    )

    return json.dumps({
        "yawl_xml": result.yawl_xml,
        "process_spec": result.process_spec,
        "process_graph": result.process_graph,
        "decomposition_id": result.decomposition_id,
        "metadata": result.metadata
    })
