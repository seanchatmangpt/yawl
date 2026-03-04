"""
Tests for DSPy Resource Routing module.

Validates the intelligent agent resource allocation using DSPy's ChainOfThought
for predicting the best agent for tasks based on capabilities and historical performance.

Run with: pytest -m unit cli/tests/dspy/test_dspy_resource_routing.py
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
    from dspy_resource_routing import (
        ResourceRoutingSignature,
        ResourceRoutingModule,
        ResourcePredictionContext,
        bootstrap_from_marketplace_history,
    )
    DSPY_AVAILABLE = True
except ImportError:
    DSPY_AVAILABLE = False
    ResourceRoutingSignature = None
    ResourceRoutingModule = None
    ResourcePredictionContext = None
    bootstrap_from_marketplace_history = None


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestResourceRoutingSignature:
    """Test resource routing signature definition."""

    def test_signature_fields(self):
        """Test that signature has correct fields."""
        sig = ResourceRoutingSignature

        # Check input
        assert hasattr(sig, 'context')

        # Check outputs
        assert hasattr(sig, 'best_agent_id')
        assert hasattr(sig, 'reasoning')
        assert hasattr(sig, 'confidence')


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestResourceRoutingModule:
    """Test resource routing module initialization."""

    def test_module_initialization(self):
        """Test module can be initialized."""
        module = ResourceRoutingModule()

        assert module is not None
        assert module.predict is not None


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestBootstrapFromMarketplaceHistory:
    """Test training example extraction."""

    def test_empty_history(self):
        """Test extraction from empty history."""
        result = bootstrap_from_marketplace_history([])
        assert result == []

    def test_valid_history(self):
        """Test extraction from valid performance history."""
        history = [
            {"task_type": "data_processing", "agent_id": "agent-1", "success_rate": 0.95, "queue_depth": 2, "latency_ms": 100},
            {"task_type": "data_processing", "agent_id": "agent-2", "success_rate": 0.80, "queue_depth": 5, "latency_ms": 200},
            {"task_type": "nlp_classification", "agent_id": "agent-3", "success_rate": 0.70, "queue_depth": 1, "latency_ms": 300}
        ]

        result = bootstrap_from_marketplace_history(history)

        assert len(result) == 3
        assert all("data_processing" in str(ex.get("context", {})) or "nlp_classification" in str(ex.get("context", {})) for ex in result)

    def test_malformed_history(self):
        """Test handling of malformed records."""
        history = [
            {"task_type": "data_processing", "agent_id": "agent-1"},  # Missing success_rate
            {"task_type": "nlp_classification"},  # Incomplete record
        ]

        result = bootstrap_from_marketplace_history(history)

        # Should handle gracefully
        assert isinstance(result, list)


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestResourcePredictionContext:
    """Test context data structure."""

    def test_context_creation(self):
        """Test creating prediction context."""
        context = ResourcePredictionContext(
            task_type="data_processing",
            required_capabilities={"cpu": 4, "memory": 8},
            agent_historical_scores={"agent-1": 0.9, "agent-2": 0.85},
            current_queue_depth=3,
            available_agents=["agent-1", "agent-2", "agent-3"]
        )

        assert context.task_type == "data_processing"
        assert len(context.available_agents) == 3


# Mark tests
@pytest.mark.unit
def test_unit_marker():
    pass
