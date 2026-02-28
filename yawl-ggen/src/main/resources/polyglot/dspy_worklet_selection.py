"""
DSPy-based worklet selection module for YAWL workflow engine.

This module implements a machine-learning approach to worklet selection using
DSPy's BootstrapFewShot classifier. It learns from historical worklet selections
and improves accuracy over time as more cases complete.

Author: YAWL Foundation
Version: 6.0.0
"""

import dspy
from typing import Dict, Any, List


class WorkletSelectionSignature(dspy.Signature):
    """Signature for worklet selection prediction.

    Defines the input-output contract for the worklet selection classifier.
    The classifier takes a selection context (task, case data, candidates) and
    produces a selected worklet ID with confidence and rationale.
    """
    context: str = dspy.InputField(
        desc="Selection context: task name, case data, available worklets, historical patterns"
    )
    worklet_id: str = dspy.OutputField(
        desc="Selected worklet ID from available candidates (e.g., 'FastTrack', 'StandardTrack')"
    )
    rationale: str = dspy.OutputField(
        desc="Reasoning for the selection: historical pattern, case attributes, or urgency"
    )
    confidence: str = dspy.OutputField(
        desc="Confidence score as string '0.0' to '1.0' (e.g., '0.87')"
    )


class WorkletSelectionModule(dspy.Module):
    """DSPy module for learning worklet selection patterns.

    This module uses dspy.ChainOfThought by default for reasoning about
    worklet selection. After bootstrap training on historical examples,
    it can be optimized via BootstrapFewShot to improve accuracy.

    Attributes:
        classify: ChainOfThought predictor for worklet selection
    """

    def __init__(self):
        """Initialize the worklet selection module."""
        super().__init__()
        # Start with ChainOfThought; will be optimized by BootstrapFewShot
        self.classify = dspy.ChainOfThought(WorkletSelectionSignature)

    def forward(self, context: str) -> Dict[str, Any]:
        """Execute worklet selection inference.

        Args:
            context: String representation of WorkletSelectionContext containing:
                     - task_name: Workflow task identifier
                     - case_data: Case attributes relevant to selection
                     - available_worklets: List of candidate worklet IDs
                     - historical_selections: Count of past selections per worklet

        Returns:
            Dictionary with keys:
            - worklet_id: Selected worklet ID
            - rationale: Explanation of selection
            - confidence: Confidence score (0.0 to 1.0)
        """
        # Execute the classifier with context
        result = self.classify(context=context)

        # Extract values from dspy Prediction object
        worklet_id = getattr(result, 'worklet_id', 'StandardTrack')
        rationale = getattr(result, 'rationale', 'No explicit rationale provided')
        confidence_str = getattr(result, 'confidence', '0.5')

        # Parse confidence as float, handle various string formats
        try:
            # Extract numeric part if confidence is a sentence
            confidence_value = str(confidence_str).split()[0]
            confidence = float(confidence_value)
            # Clamp to valid range [0.0, 1.0]
            confidence = max(0.0, min(1.0, confidence))
        except (ValueError, IndexError):
            confidence = 0.5  # Default fallback

        return {
            'worklet_id': str(worklet_id).strip(),
            'rationale': str(rationale).strip(),
            'confidence': confidence
        }


def bootstrap_from_rdr_history(rdr_traces: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Extract training examples from RDR rule logs for future bootstrap.

    This function processes historical RDR evaluation traces and extracts
    (context, worklet_id, rationale) tuples suitable for BootstrapFewShot
    training. It's called after cases complete to collect new examples.

    Args:
        rdr_traces: List of RDR evaluation traces from historical logs.
                   Each trace should contain:
                   - task: Task name
                   - case_data: Case attributes
                   - selected_worklet: Selected worklet ID
                   - rule_applied: RDR rule that matched (for rationale)

    Returns:
        List of training examples ready for BootstrapFewShot.compile().
        Each example is a dict with keys:
        - context: String representation of task context
        - worklet_id: Selected worklet
        - rationale: Explanation from applied rule
        - confidence: High confidence (historical selection is truth)
    """
    examples = []
    for trace in rdr_traces:
        try:
            # Build context string from trace
            task_name = trace.get('task', 'Unknown')
            case_data = trace.get('case_data', {})
            selected_worklet = trace.get('selected_worklet', 'StandardTrack')
            rule_applied = trace.get('rule_applied', 'Default rule')

            # Create a searchlight example for BootstrapFewShot
            example = {
                'context': f"Task: {task_name}, Case: {case_data}",
                'worklet_id': selected_worklet,
                'rationale': f"Historical pattern matched: {rule_applied}",
                'confidence': '0.95'  # Historical selections are trusted
            }
            examples.append(example)
        except Exception as e:
            # Skip malformed traces; continue with others
            continue

    return examples


# Module instantiation for use by Java bridge
worklet_selection_module = WorkletSelectionModule()
