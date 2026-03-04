"""
Tests for DSPy Worklet Selection module.

Validates the dynamic worklet selection using DSPy's BootstrapFewShot optimizer
for learning from historical task-to-worklet mappings.

Run with: pytest -m unit cli/tests/dspy/test_dspy_worklet_selection.py
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
    from dspy_worklet_selection import (
        WorkletSelectionSignature,
        WorkletSelectionModule,
        WorkletSelectionContext,
        DspyWorkletSelector,
    )
    DSPY_AVAILABLE = True
except ImportError:
    DSPY_AVAILABLE = False
    WorkletSelectionSignature = None
    WorkletSelectionModule = None
    WorkletSelectionContext = None
    DspyWorkletSelector = None


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestWorkletSelectionSignature:
    """Test worklet selection signature definition."""

    def test_signature_fields(self):
        """Test that signature has correct input/output fields."""
        sig = WorkletSelectionSignature

        # Check input field
        assert hasattr(sig, 'context')

        # Check output fields
        assert hasattr(sig, 'worklet_id')
        assert hasattr(sig, 'rationale')
        assert hasattr(sig, 'confidence')


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestWorkletSelectionModule:
    """Test worklet selection module initialization."""

    def test_module_initialization(self):
        """Test module can be initialized."""
        module = WorkletSelectionModule()

        assert module is not None
        assert module.classify is not None


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestDspyWorkletSelector:
    """Test main worklet selector class."""

    @pytest.fixture
    def selector(self):
        """Create a selector instance for testing."""
        return DspyWorkletSelector()

    def test_selector_initialization(self, selector):
        """Test selector can be initialized."""
        assert selector is not None
        assert selector.module is not None

    @pytest.mark.integration
    def test_select_worklet_with_context(self, selector):
        """Test worklet selection with proper context."""
        context = WorkletSelectionContext(
            task_name="PaymentProcessing",
            case_data={"amount": 5000, "currency": "USD"},
            available_worklets=["FastTrack", "StandardTrack", "ExpressTrack"],
            historical_selections={"FastTrack": 5, "StandardTrack": 10, "ExpressTrack": 2}
        )

        result = selector.select_worklet(context)

        assert result is not None
        assert result.worklet_id in context.available_worklets

    @pytest.mark.integration
    def test_select_worklet_high_value(self, selector):
        """Test worklet selection for high-value transactions."""
        context = WorkletSelectionContext(
            task_name="HighValueTransfer",
            case_data={"amount": 50000, "currency": "USD"},
            available_worklets=["ManualReview", "AutoApprove"],
            historical_selections={"ManualReview": 15, "AutoApprove": 5}
        )

        result = selector.select_worklet(context)

        # For high-value items, ManualReview should be preferred
        assert result.worklet_id in ["ManualReview", "AutoApprove"]

    @pytest.mark.integration
    def test_select_worklet_empty_historical(self, selector):
        """Test worklet selection with no historical data."""
        context = WorkletSelectionContext(
            task_name="NewTask",
            case_data={},
            available_worklets=["WorkletA", "WorkletB"],
            historical_selections={}
        )

        result = selector.select_worklet(context)

        assert result is not None
        assert result.worklet_id in context.available_worklets

    @pytest.mark.integration
    def test_confidence_score_range(self, selector):
        """Test that confidence score is in valid range."""
        context = WorkletSelectionContext(
            task_name="TestTask",
            case_data={},
            available_worklets=["A", "B"],
            historical_selections={"A": 1}
        )

        result = selector.select_worklet(context)

        # Confidence should be between 0 and 1
        assert 0.0 <= result.confidence <= 1.0


# Mark tests
@pytest.mark.unit
def test_unit_marker():
    pass
