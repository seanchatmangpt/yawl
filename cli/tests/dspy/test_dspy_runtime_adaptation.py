"""
Tests for DSPy Runtime Adaptation module.

Validates the ReAct-style runtime adaptation agent for suggesting
workflow modifications based on real-time metrics.

Run with: pytest -m unit cli/tests/dspy/test_dspy_runtime_adaptation.py
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
    from dspy_runtime_adaptation import (
        WorkflowAdaptationSignature,
        RuntimeAdaptationModule,
        WorkflowAdaptationContext,
        AdaptationAction,
    )
    DSPY_AVAILABLE = True
except ImportError:
    DSPY_AVAILABLE = False
    WorkflowAdaptationSignature = None
    RuntimeAdaptationModule = None
    WorkflowAdaptationContext = None
    AdaptationAction = None


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestWorkflowAdaptationContext:
    """Test adaptation context data structure."""

    def test_context_creation(self):
        """Test creating adaptation context."""
        context = WorkflowAdaptationContext(
            case_id="case-123",
            spec_id="spec-456",
            bottleneck_score=0.75,
            enabled_tasks=["task1", "task2"],
            busy_tasks=["task1"],
            queue_depth=5,
            avg_task_latency_ms=200.0,
            available_agents=["agent1", "agent2"]
        )

        assert context.case_id == "case-123"
        assert context.bottleneck_score == 0.75
        assert len(context.enabled_tasks) == 2


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestAdaptationAction:
    """Test adaptation action enum."""

    def test_action_types(self):
        """Test all action types exist."""
        assert AdaptationAction.ADD_RESOURCE is not None
        assert AdaptationAction.REROUTE is not None
        assert AdaptationAction.SKIP_TASK is not None
        assert AdaptationAction.ESCALATE is not None


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestRuntimeAdaptationModule:
    """Test runtime adaptation module."""

    @pytest.fixture
    def module(self):
        """Create module instance for testing."""
        return RuntimeAdaptationModule()

    def test_module_initialization(self, module):
        """Test module can be initialized."""
        assert module is not None
        assert module.suggest_action is not None

    @pytest.mark.integration
    def test_suggest_action_high_bottleneck(self, module):
        """Test action suggestion with high bottleneck."""
        context = WorkflowAdaptationContext(
            case_id="test-case",
            spec_id="test-spec",
            bottleneck_score=0.9,
            enabled_tasks=["task1", "task2"],
            busy_tasks=["task1"],
            queue_depth=10,
            avg_task_latency_ms=500.0,
            available_agents=["agent1"]
        )

        action = module.suggest_action(context)

        assert action is not None
        assert action.action_type in [
            AdaptationAction.ADD_RESOURCE,
            AdaptationAction.REROUTE,
            AdaptationAction.ESCALATE
        ]

    @pytest.mark.integration
    def test_suggest_action_low_bottleneck(self, module):
        """Test action suggestion with low bottleneck."""
        context = WorkflowAdaptationContext(
            case_id="test-case",
            spec_id="test-spec",
            bottleneck_score=0.3,
            enabled_tasks=["task1"],
            busy_tasks=[],
            queue_depth=1,
            avg_task_latency_ms=50.0,
            available_agents=["agent1", "agent2"]
        )

        action = module.suggest_action(context)

        assert action is not None
        # Low bottleneck might suggest skip_task or no action


# Mark tests
@pytest.mark.unit
def test_unit_marker():
    pass
