"""
Tests for DSPy Anomaly Forensics module.

Validates the root cause analysis for AI-powered anomaly detection using DSPy's
MultiChainComparison for generating 3 competing hypotheses
and selecting the most plausible one.

Run with: pytest -m unit cli/tests/dspy/test_dspy_anomaly_forensics.py
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
    from dspy_anomaly_forensics import (
        AnomalyRootCauseSignature,
        AnomalyRootCauseModule,
        AnomalyContext,
        ForensicsReport,
    )
    DSPY_AVAILABLE = True
except ImportError:
    DSPY_AVAILABLE = False
    AnomalyRootCauseSignature = None
    AnomalyRootCauseModule = None
    AnomalyContext = None
    ForensicsReport = None


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestAnomalyRootCauseSignature:
    """Test anomaly root cause signature definition."""

    def test_signature_fields(self):
        """Test that signature has correct fields."""
        sig = AnomalyRootCauseSignature

        # Check input fields
        assert hasattr(sig, 'context')

        # Check output fields
        assert hasattr(sig, 'root_cause')
        assert hasattr(sig, 'confidence')
        assert hasattr(sig, 'evidence_chain')
        assert hasattr(sig, 'recommendation')


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestAnomalyContext:
    """Test anomaly context data structure."""

    def test_context_creation(self):
        """Test creating anomaly context."""
        context = AnomalyContext(
            metric_name="test_metric",
            duration_ms=1000,
            deviation_factor=2.0,
            recent_samples=[1.0, 1.5, 2.0],
            concurrent_cases=["case1", "case2", "case3"],
            event_payload={"key": "value"}
        )

        assert context.metric_name == "test_metric"
        assert context.duration_ms == 1000
        assert context.deviation_factor == 2.0
        assert len(context.recent_samples) == 3
        assert len(context.concurrent_cases) == 3


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestForensicsReport:
    """Test forensics report data structure."""

    def test_report_creation(self):
        """Test creating forensics report."""
        report = ForensicsReport(
            root_cause="Memory leak in heap",
            confidence=0.95,
            evidence_chain=["Observed 95th percentile spike", "Heap dump analysis shows leak"],
            recommendation="Increase heap size"
        )

        assert report.root_cause == "Memory leak in heap"
        assert report.confidence == 0.95
        assert len(report.evidence_chain) == 2
        assert "Increase heap size" in report.recommendation

    def test_report_serialization(self):
        """Test report can be serialized."""
        report = ForensicsReport(
            root_cause="Test issue",
            confidence=0.75,
            evidence_chain=["Evidence 1", "Evidence 2"],
            recommendation="Fix the issue"
        )

        # Convert to dict
        data = {
            "root_cause": report.root_cause,
            "confidence": report.confidence,
            "evidence_chain": report.evidence_chain,
            "recommendation": report.recommendation,
        }

        assert data["root_cause"] == "Test issue"
        assert data["confidence"] == 0.75


@pytest.mark.skipif(not DSPY_AVAILABLE, reason="DSPy not installed")
class TestAnomalyRootCauseModule:
    """Test anomaly root cause module."""

    @pytest.fixture
    def module(self):
        """Create module instance for testing."""
        return AnomalyRootCauseModule()

    def test_module_initialization(self, module):
        """Test module can be initialized."""
        assert module is not None
        assert module.analyze is not None

    @pytest.mark.integration
    def test_analyze_high_deviation(self, module):
        """Test analysis with high deviation factor."""
        context = AnomalyContext(
            metric_name="response_time_p95",
            duration_ms=5000,
            deviation_factor=3.0,  # 3x above threshold
            recent_samples=[100, 150, 200, 300],
            concurrent_cases=["case1", "case2"],
            event_payload={"endpoint": "/api/users"}
        )

        report = module.run_forensics(context)

        assert report is not None
        assert report.root_cause is not None
        assert report.confidence >= 0.0

    @pytest.mark.integration
    def test_analyze_with_evidence_chain(self, module):
        """Test analysis produces evidence chain."""
        context = AnomalyContext(
            metric_name="cpu_usage",
            duration_ms=1000,
            deviation_factor=1.5,
            recent_samples=[50, 60, 70],
            concurrent_cases=["case1"],
            event_payload={"host": "server1"}
        )

        report = module.run_forensics(context)

        assert report is not None
        assert isinstance(report.evidence_chain, list)

    @pytest.mark.integration
    def test_confidence_range(self, module):
        """Test that confidence score is in valid range."""
        context = AnomalyContext(
            metric_name="test_metric",
            duration_ms=100,
            deviation_factor=1.0,
            recent_samples=[1.0],
            concurrent_cases=[],
            event_payload={}
        )

        report = module.run_forensics(context)

        # Confidence should be between 0 and 1
        assert 0.0 <= report.confidence <= 1.0


# Mark tests
@pytest.mark.unit
def test_unit_marker():
    pass
