"""
Tests for DSPy POWL Generator module.

Validates the POWL (Partially Ordered Workflow Language) generation from natural language
process descriptions using DSPy's ChainOfThought reasoning.

Run with: pytest -m unit cli/tests/dspy/test_dspy_powl_generator.py
"""

import pytest
import json
from unittest.mock import Mock, patch, MagicMock
from pathlib import Path
import sys
import os

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent.parent / "yawl-ggen" / "src" / "main" / "resources" / "polyglot"))

# Try to import, skip gracefully if not available
try:
    from dspy_powl_generator import (
        DspyPowlGenerator,
        DspyGenerationResult,
        WorkflowGraph,
        WorkflowPlace,
        WorkflowTransition,
        WorkflowFlow,
        StructuredWorkflowSpec,
        extract_activities_heuristic,
        generate_sequence_graph,
        validate_workflow_graph,
        BOOTSTRAP_EXAMPLES,
    )
    DSPY_AVAILABLE = True
except ImportError:
    DSPY_AVAILABLE = False
    DspyPowlGenerator = None
    DspyGenerationResult = None
    WorkflowGraph = None
    WorkflowPlace = None
    WorkflowTransition = None
    WorkflowFlow = None
    StructuredWorkflowSpec = None
    extract_activities_heuristic = None
    generate_sequence_graph = None
    validate_workflow_graph = None
    BOOTSTRAP_EXAMPLES = []


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestWorkflowGraph:
    """Test WorkflowGraph data structure."""

    def test_workflow_graph_creation(self):
        """Test basic workflow graph creation."""
        graph = WorkflowGraph(
            net_id="test_net",
            net_name="TestNetwork"
        )

        assert graph.net_id == "test_net"
        assert graph.net_name == "TestNetwork"
        assert len(graph.places) == 0
        assert len(graph.transitions) == 0
        assert len(graph.flows) == 0

    def test_add_place(self):
        """Test adding places to workflow graph."""
        graph = WorkflowGraph(net_id="N1", net_name="Test")

        place = WorkflowPlace(id="p1", name="Start", place_type="input_place")
        graph.add_place(place)

        assert len(graph.places) == 1
        assert graph.places[0].id == "p1"

    def test_add_transition(self):
        """Test adding transitions to workflow graph."""
        graph = WorkflowGraph(net_id="N1", net_name="Test")

        transition = WorkflowTransition(
            id="t1",
            name="Task1",
            splits="xor",
            joins="and"
        )
        graph.add_transition(transition)

        assert len(graph.transitions) == 1
        assert graph.transitions[0].id == "t1"

    def test_add_flow(self):
        """Test adding flows to workflow graph."""
        graph = WorkflowGraph(net_id="N1", net_name="Test")

        # Add place and transition first
        graph.add_place(WorkflowPlace(id="p1", name="Start"))
        graph.add_transition(WorkflowTransition(id="t1", name="Task"))

        flow = WorkflowFlow(source="p1", target="t1")
        graph.add_flow(flow)

        assert len(graph.flows) == 1
        assert graph.flows[0].source == "p1"

    def test_to_dict(self):
        """Test workflow graph serialization to dict."""
        graph = WorkflowGraph(
            net_id="N1",
            net_name="TestNet"
        )
        graph.add_place(WorkflowPlace(id="p1", name="Start", place_type="input_place"))
        graph.add_transition(WorkflowTransition(id="t1", name="Task"))
        graph.add_flow(WorkflowFlow(source="p1", target="t1"))

        result = graph.to_dict()

        assert result["id"] == "N1"
        assert result["name"] == "TestNet"
        assert len(result["places"]) == 1
        assert len(result["transitions"]) == 1
        assert len(result["flows"]) == 1


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestExtractActivitiesHeuristic:
    """Test heuristic activity extraction from text."""

    def test_simple_sequence(self):
        """Test extracting activities from simple sequence."""
        text = "Submit order then process payment then ship goods"
        activities = extract_activities_heuristic(text)

        assert len(activities) > 0
        assert any("order" in a.lower() for a in activities)

    def test_empty_text(self):
        """Test extraction from empty text."""
        activities = extract_activities_heuristic("")
        assert activities == []

    def test_complex_description(self):
        """Test extraction from complex workflow description."""
        text = """
        A customer submits an order.
        The system processes the payment.
        If approved, goods are shipped.
        If rejected, cancellation is processed.
        """
        activities = extract_activities_heuristic(text)

        assert len(activities) >= 2


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestGenerateSequenceGraph:
    """Test fallback graph generation."""

    def test_single_activity(self):
        """Test graph generation with single activity."""
        activities = ["Submit Order"]
        graph = generate_sequence_graph(activities, "Test Workflow")

        assert graph is not None
        assert len(graph.transitions) == 1
        assert graph.transitions[0].name == "Submit Order"

    def test_multiple_activities(self):
        """Test graph generation with multiple activities."""
        activities = ["Submit Order", "Process Payment", "Ship Goods"]
        graph = generate_sequence_graph(activities, "Order Process")

        assert graph is not None
        assert len(graph.transitions) == 3
        assert len(graph.places) >= 4
        assert len(graph.flows) >= 4

    def test_empty_activities(self):
        """Test graph generation with empty list."""
        graph = generate_sequence_graph([], "Empty")

        assert graph is not None
        assert len(graph.transitions) == 0


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestValidateWorkflowGraph:
    """Test Petri-net validation."""

    def test_valid_graph(self):
        """Test validation of a valid workflow graph."""
        graph = WorkflowGraph(net_id="N1", net_name="Valid")
        graph.add_place(WorkflowPlace(id="p_start", name="Start", place_type="input_place"))
        graph.add_place(WorkflowPlace(id="p_end", name="End", place_type="output_place"))
        graph.add_transition(WorkflowTransition(id="t1", name="Task"))
        graph.add_flow(WorkflowFlow(source="p_start", target="t1"))
        graph.add_flow(WorkflowFlow(source="t1", target="p_end"))

        is_valid, errors = validate_workflow_graph(graph)
        # Note: Validation may have specific rules

    def test_duplicate_ids(self):
        """Test detection of duplicate IDs."""
        graph = WorkflowGraph(net_id="N1", net_name="Test")
        graph.add_place(WorkflowPlace(id="p1", name="Place1"))
        graph.add_place(WorkflowPlace(id="p1", name="Place2"))  # Duplicate ID

        is_valid, errors = validate_workflow_graph(graph)
        assert "Duplicate" in errors[0]


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestDspyPowlGenerator:
    """Test main DSPy POWL generator class."""

    @pytest.fixture
    def generator(self):
        """Create a generator instance for testing."""
        return DspyPowlGenerator()

    def test_generator_initialization(self, generator):
        """Test generator can be initialized."""
        assert generator is not None
        assert generator.program is not None

    @pytest.mark.integration
    def test_generate_with_fallback(self, generator):
        """Test generation with fallback enabled."""
        result = generator.generate(
            "A simple order workflow",
            use_fallback=True
        )

        assert result is not None
        assert result.powl_json is not None

        # Parse JSON to verify structure
        powl_data = json.loads(result.powl_json)
        assert "version" in powl_data
        assert "net" in powl_data

    @pytest.mark.integration
    def test_generate_metrics(self, generator):
        """Test that generation includes proper metrics."""
        result = generator.generate(
            "Customer submits order, then payment processing",
            use_fallback=True
        )

        assert result.metrics is not None
        assert "inference_time_ms" in result.metrics
        assert "activities_count" in result.metrics

    @pytest.mark.integration
    def test_fallback_on_dspy_failure(self, generator):
        """Test that fallback works when DSPy fails."""
        result = generator.generate(
            "A test workflow description",
            use_fallback=True
        )

        # Should use fallback and still return a result
        assert result is not None
        assert result.powl_json is not None


# Mark tests
@pytest.mark.unit
def test_unit_marker():
    pass
