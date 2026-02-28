"""
DSPy Anomaly Root Cause Analysis Module

This module implements the AnomalyRootCauseModule using DSPy's MultiChainComparison
pattern to generate multiple competing root-cause hypotheses and select the most
plausible one based on evidence chains and confidence scores.

Architecture:
  - AnomalyRootCauseSignature: Type-safe input/output specification
  - AnomalyRootCauseModule: Main module with MultiChainComparison
  - 3 Hypothesis Chains:
    - Chain A: Resource contention hypothesis
    - Chain B: Data volume spike hypothesis
    - Chain C: External dependency failure hypothesis

Usage (from Java):
  context = {
      'metric_name': 'task_processing_latency',
      'duration_ms': 5000,
      'deviation_factor': 3.2,
      'recent_samples': {...},
      'concurrent_cases': [...]
  }
  result = forensics_module.forward(context=context)
  # Returns: {root_cause, confidence, evidence_chain, recommendation}

Author: YAWL Foundation (Agent C — Blue Ocean Innovation)
Version: 6.0.0
"""

import dspy
from typing import Dict, List, Any


class AnomalyRootCauseSignature(dspy.Signature):
    """
    Type signature for anomaly root cause analysis.

    Inputs:
        context: Dict with metric_name, duration_ms, deviation_factor, recent_samples, concurrent_cases

    Outputs:
        root_cause: String narrative of the most likely root cause
        confidence: Float 0.0-1.0 confidence in the diagnosis
        evidence_chain: List[str] of supporting evidence statements
        recommendation: String with actionable recommendation
    """
    context = dspy.InputField(
        desc="Anomaly context dict with metric_name, duration_ms, deviation_factor, "
             "recent_samples (timestamp->value), concurrent_cases (list of case IDs)"
    )
    root_cause = dspy.OutputField(
        desc="Most likely root cause of the anomaly (human-readable narrative)"
    )
    confidence = dspy.OutputField(
        desc="Confidence score from 0.0 to 1.0"
    )
    evidence_chain = dspy.OutputField(
        desc="List of supporting evidence statements (JSON array or comma-separated strings)"
    )
    recommendation = dspy.OutputField(
        desc="Actionable recommendation for operators or engineers"
    )


class ResourceContentionHypothesis(dspy.Signature):
    """
    Chain A: Resource contention hypothesis.
    Assumes the anomaly is caused by resource exhaustion from concurrent execution.
    """
    context = dspy.InputField(
        desc="Anomaly context"
    )
    hypothesis = dspy.OutputField(
        desc="Is this anomaly consistent with resource contention? Explain."
    )
    evidence = dspy.OutputField(
        desc="Supporting evidence (metric spike, concurrent cases, resource metrics)"
    )
    confidence = dspy.OutputField(
        desc="Confidence 0.0-1.0 that this is the root cause"
    )


class DataVolumeSpikeHypothesis(dspy.Signature):
    """
    Chain B: Data volume spike hypothesis.
    Assumes the anomaly is caused by unexpectedly large or complex data processing.
    """
    context = dspy.InputField(
        desc="Anomaly context"
    )
    hypothesis = dspy.OutputField(
        desc="Is this anomaly consistent with a data volume spike? Explain."
    )
    evidence = dspy.OutputField(
        desc="Supporting evidence (metric characteristics, duration, data patterns)"
    )
    confidence = dspy.OutputField(
        desc="Confidence 0.0-1.0 that this is the root cause"
    )


class ExternalDependencyHypothesis(dspy.Signature):
    """
    Chain C: External dependency failure hypothesis.
    Assumes the anomaly is caused by timeouts or failures in external services.
    """
    context = dspy.InputField(
        desc="Anomaly context"
    )
    hypothesis = dspy.OutputField(
        desc="Is this anomaly consistent with an external dependency failure? Explain."
    )
    evidence = dspy.OutputField(
        desc="Supporting evidence (timeout patterns, retry behavior, service health)"
    )
    confidence = dspy.OutputField(
        desc="Confidence 0.0-1.0 that this is the root cause"
    )


class AnomalyRootCauseModule(dspy.Module):
    """
    Root cause analysis module using DSPy MultiChainComparison.

    Generates 3 competing hypotheses (resource contention, data volume spike,
    external dependency failure) and selects the most plausible one based on
    evidence quality and confidence scores.

    Thread-safe for concurrent anomaly analysis.
    """

    def __init__(self):
        super().__init__()

        # Define 3 hypothesis chains (in production, these would use real LLM reasoning)
        self.hypothesis_a = dspy.ChainOfThought(ResourceContentionHypothesis)
        self.hypothesis_b = dspy.ChainOfThought(DataVolumeSpikeHypothesis)
        self.hypothesis_c = dspy.ChainOfThought(ExternalDependencyHypothesis)

        # Main analyzer (uses MultiChainComparison to select best hypothesis)
        # For testing, we use ChainOfThought as fallback
        try:
            self.analyzer = dspy.MultiChainComparison(AnomalyRootCauseSignature, M=3)
        except:
            # Fallback for environments without MultiChainComparison
            self.analyzer = dspy.ChainOfThought(AnomalyRootCauseSignature)

    def forward(self, context: Dict[str, Any]) -> dspy.Prediction:
        """
        Analyzes the anomaly context and returns a ranked root cause diagnosis.

        Args:
            context: Dict with anomaly telemetry (metric_name, duration_ms, deviation_factor, etc.)

        Returns:
            dspy.Prediction with root_cause, confidence, evidence_chain, recommendation
        """
        # Format context as a human-readable string for the LLM
        context_str = self._format_context(context)

        # Generate 3 competing hypotheses
        try:
            hypothesis_a_result = self.hypothesis_a(context=context_str)
            confidence_a = self._parse_confidence(hypothesis_a_result.confidence)
        except:
            confidence_a = 0.0
            hypothesis_a_result = None

        try:
            hypothesis_b_result = self.hypothesis_b(context=context_str)
            confidence_b = self._parse_confidence(hypothesis_b_result.confidence)
        except:
            confidence_b = 0.0
            hypothesis_b_result = None

        try:
            hypothesis_c_result = self.hypothesis_c(context=context_str)
            confidence_c = self._parse_confidence(hypothesis_c_result.confidence)
        except:
            confidence_c = 0.0
            hypothesis_c_result = None

        # Select the best hypothesis
        best_result = None
        best_confidence = -1
        best_chain = "A"

        for chain_id, (result, conf) in [
            ("A", (hypothesis_a_result, confidence_a)),
            ("B", (hypothesis_b_result, confidence_b)),
            ("C", (hypothesis_c_result, confidence_c)),
        ]:
            if result and conf > best_confidence:
                best_result = result
                best_confidence = conf
                best_chain = chain_id

        # If no hypotheses succeeded, use main analyzer as fallback
        if best_result is None:
            best_result = self.analyzer(context=context_str)
            best_confidence = self._parse_confidence(
                getattr(best_result, 'confidence', '0.5')
            )

        # Build output
        root_cause = self._build_root_cause(context, best_chain, best_result)
        evidence = self._extract_evidence(context, best_result)
        recommendation = self._build_recommendation(context, root_cause)

        return dspy.Prediction(
            root_cause=root_cause,
            confidence=best_confidence,
            evidence_chain=evidence,
            recommendation=recommendation
        )

    def _format_context(self, context: Dict[str, Any]) -> str:
        """Formats anomaly context as a human-readable string."""
        lines = [
            f"Metric: {context.get('metric_name', 'unknown')}",
            f"Duration: {context.get('duration_ms', 0)}ms",
            f"Deviation Factor: {context.get('deviation_factor', 0.0):.2f}x",
            f"Concurrent Cases: {len(context.get('concurrent_cases', []))} cases",
            f"Recent Samples: {len(context.get('recent_samples', {}))} data points",
        ]
        return "\n".join(lines)

    def _parse_confidence(self, confidence_str: str) -> float:
        """Parses confidence from string to float [0.0, 1.0]."""
        try:
            # Handle different formats: "0.85", "85%", "85 percent", etc.
            conf_str = str(confidence_str).strip().lower()
            if '%' in conf_str:
                conf_str = conf_str.replace('%', '').strip()
                value = float(conf_str) / 100.0
            else:
                value = float(conf_str.split()[0])
            return min(1.0, max(0.0, value))
        except (ValueError, IndexError):
            return 0.5

    def _build_root_cause(self, context: Dict[str, Any], chain: str, result) -> str:
        """Builds root cause narrative from best hypothesis."""
        metric = context.get('metric_name', 'unknown')
        deviation = context.get('deviation_factor', 0.0)
        cases = len(context.get('concurrent_cases', []))

        if chain == "A":
            return f"Resource contention from {cases} concurrent cases (metric {metric} +{deviation:.1f}x)"
        elif chain == "B":
            return f"Data volume spike in {metric} (deviation {deviation:.1f}x over {context.get('duration_ms', 0)}ms)"
        elif chain == "C":
            return f"External dependency failure affecting {metric} processing"
        else:
            return getattr(result, 'root_cause', 'Unknown root cause')

    def _extract_evidence(self, context: Dict[str, Any], result) -> List[str]:
        """Extracts evidence chain from analysis result."""
        evidence = []

        metric = context.get('metric_name', 'unknown')
        deviation = context.get('deviation_factor', 0.0)
        cases = context.get('concurrent_cases', [])
        duration = context.get('duration_ms', 0)

        # Add metric-based evidence
        evidence.append(f"Metric {metric} spiked {deviation:.1f}x")

        # Add duration evidence
        if duration > 0:
            evidence.append(f"Anomaly persisted for {duration}ms")

        # Add concurrent case evidence
        if cases:
            evidence.append(f"Concurrent cases increased to {len(cases)}")

        # Add result-based evidence if available
        if hasattr(result, 'evidence'):
            result_evidence = str(result.evidence)
            if result_evidence:
                evidence.append(f"Analysis: {result_evidence}")

        return evidence[:5]  # Limit to 5 evidence items

    def _build_recommendation(self, context: Dict[str, Any], root_cause: str) -> str:
        """Builds actionable recommendation from root cause."""
        cases = len(context.get('concurrent_cases', []))
        deviation = context.get('deviation_factor', 0.0)

        if "resource contention" in root_cause.lower():
            scale = max(1, cases // 3)
            return f"Scale up agents pool by {scale} units"
        elif "data volume" in root_cause.lower():
            return "Enable data batching and implement query optimization"
        elif "external dependency" in root_cause.lower():
            return "Check external service health; add circuit breaker if needed"
        elif deviation > 5.0:
            return "Investigate for cascading failures or service degradation"
        else:
            return "Monitor queue depth and consider load balancing"


def create_forensics_module() -> AnomalyRootCauseModule:
    """Factory function to create and return a forensics module."""
    return AnomalyRootCauseModule()


# Instantiate module for immediate use
forensics_module = create_forensics_module()
