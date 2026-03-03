"""
Pytest configuration for DSPy tests.

Provides fixtures, configuration, and shared test utilities.
"""

import pytest
import os
import sys
from pathlib import Path
from typing import Any, Dict, Generator
from unittest.mock import MagicMock


# Add paths for imports
sys.path.insert(0, str(Path(__file__).parent.parent / "yawl-ggen" / "src" / "main" / "resources" / "polyglot"))


@pytest.fixture
def mock_dspy_settings():
    """Mock DSPy settings for testing without LLM."""
    import dspy

    # Configure DSPy with a mock LM
    mock_lm = MagicMock()
    mock_lm.__class__.__name__ = "MockLM"
    dspy.settings.configure(lm=mock_lm)
    return mock_lm


@pytest.fixture
def sample_workflow_description():
    """Sample workflow description for testing."""
    return """
    A customer submits an order through the web portal.
    The system validates the order details.
    Payment is processed through the payment gateway.
    If payment succeeds, the order is confirmed.
    If payment fails, the order is cancelled.
    A confirmation email is sent to the customer.
    """


@pytest.fixture
def sample_worklet_context():
    """Sample worklet selection context for testing."""
    return {
        "task_name": "PaymentProcessing",
        "case_data": {
            "amount": 1000,
            "currency": "USD",
            "priority": "normal"
        },
        "available_worklets": [
            "FastTrack",
            "StandardTrack",
            "ManualReview"
        ],
        "historical_selections": {
            "FastTrack": 50,
            "StandardTrack": 30,
            "ManualReview": 10
        }
    }


@pytest.fixture
def sample_anomaly_context():
    """Sample anomaly context for testing."""
    return {
        "metric_name": "task_latency",
        "duration_ms": 5000,
        "deviation_factor": 2.5,
        "recent_samples": 100,
        "concurrent_cases": 5,
        "event_payload": {
            "task_id": "payment_task",
            "case_id": "case-123"
        }
    }


@pytest.fixture
def sample_resource_context():
    """Sample resource routing context for testing."""
    return {
        "task_type": "data_processing",
        "required_capabilities": {
            "cpu": 4,
            "memory": 8,
            "storage": 100
        },
        "agent_historical_scores": {
            "agent-1": 0.95,
            "agent-2": 0.88,
            "agent-3": 0.92
        },
        "available_agents": ["agent-1", "agent-2", "agent-3"],
        "queue_depth": 3
    }


@pytest.fixture
def sample_adaptation_context():
    """Sample runtime adaptation context for testing."""
    return {
        "case_id": "case-456",
        "spec_id": "spec-789",
        "bottleneck_score": 0.85,
        "enabled_tasks": ["task1", "task2", "task3"],
        "busy_tasks": ["task1", "task2"],
        "queue_depth": 15,
        "avg_task_latency_ms": 800.0,
        "available_agents": ["agent-1", "agent-2"]
    }


# Custom markers for DSPy tests
# Custom markers for# (Note: pytest_configure is called automatically by pytest)
# Markers are already defined in pyproject.toml



# Skip tests if DSPy or Ollama not available
def pytest_collection_modifyitems(items, config):
    """Skip integration tests if Ollama is not available."""
    if "integration" in [m for m in items]:
        # Check if Ollama is accessible
        import subprocess
        try:
            result = subprocess.run(
                ["curl", "-s", "--connect-timeout", "5", "http://localhost:11434/api/version"],
                capture_output=True,
                timeout=10
            )
            if result.returncode != 0:
                skip_tests = True
        except:
            skip_tests = False

        if skip_tests:
            skip_reason = "Ollama not available - skipping integration tests"
            return items[:]


# Test utilities
def assert_valid_powl(powl_json: str):
    """Assert that POWL JSON has valid structure."""
    import json

    data = json.loads(powl_json)

    assert "version" in data
    assert "net" in data
    assert "id" in data["net"]
    assert "name" in data["net"]


def assert_valid_confidence(confidence: float):
    """Assert confidence score is in valid range."""
    assert 0.0 <= confidence <= 1.0, \
        f"Confidence {confidence} not in range [0.0, 1.0]"


def assert_valid_worklet_id(worklet_id: str, available: list):
    """Assert worklet ID is in available list."""
    assert worklet_id in available, \
        f"Worklet ID {worklet_id} not in available: {available}"
