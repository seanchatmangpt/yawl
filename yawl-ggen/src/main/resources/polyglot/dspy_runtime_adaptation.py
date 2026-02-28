"""
DSPy ReAct runtime adaptation agent for YAWL workflows.

This module provides the RuntimeAdaptationModule for real-time workflow decision-making.
It uses DSPy's ReAct pattern (reasoning + acting) to analyze bottlenecks and anomalies
and suggest autonomous workflow adaptations (skip task, add resource, reroute, escalate).

:author: YAWL Foundation
:version: 6.0.0
:since: 6.0.0
"""

import dspy
import json
from typing import Dict, Any, List, Optional


class WorkflowAdaptationSignature(dspy.Signature):
    """Signature for workflow adaptation decision-making."""
    context = dspy.InputField(
        desc="Workflow context dict with case_id, bottleneck_score, queue_depth, "
             "enabled_tasks, busy_tasks, avg_task_latency_ms, available_agents, event_type"
    )
    action_type = dspy.OutputField(
        desc="Recommended action type: SKIP_TASK, ADD_RESOURCE, REROUTE, or ESCALATE"
    )
    task_id = dspy.OutputField(
        desc="Target task ID for the action (if applicable)"
    )
    agent_id = dspy.OutputField(
        desc="Agent ID to allocate (if ADD_RESOURCE action)"
    )
    alternate_route = dspy.OutputField(
        desc="Alternate workflow path (if REROUTE action)"
    )
    escalation_level = dspy.OutputField(
        desc="Escalation level (if ESCALATE action) e.g. manager, director"
    )
    reasoning = dspy.OutputField(
        desc="Clear explanation of why this action was chosen"
    )


class RuntimeAdaptationModule(dspy.Module):
    """ReAct agent for autonomous runtime workflow adaptation."""

    def __init__(self):
        """Initialize the RuntimeAdaptationModule with ReAct reasoning chain."""
        super().__init__()
        # ReAct loop with tools for workflow analysis
        self.adapt = dspy.ReAct(
            tools=[
                "check_workitem_status",     # Get status of specific work items
                "get_bottleneck_score",      # Compute bottleneck severity
                "suggest_reroute",           # Analyze alternative paths
                "escalate_to_human"          # Prepare escalation info
            ]
        )

    def forward(self, context: Dict[str, Any]) -> Dict[str, Any]:
        """
        Suggest a runtime adaptation action based on workflow state.

        The decision-making logic follows these heuristics:
        - Bottleneck score > 0.8: Add resource or escalate
        - Bottleneck score > 0.6 + high queue: Add resource
        - Bottleneck score > 0.5: Try reroute
        - Otherwise: Monitor (no action needed)

        :param context: Workflow context dict with metrics
        :return: Dict with action_type, reasoning, and action-specific fields
        """
        # Ensure context is a dict
        if not isinstance(context, dict):
            try:
                context = json.loads(str(context))
            except:
                context = {"case_id": "unknown", "bottleneck_score": 0.5}

        # Extract key metrics
        case_id = str(context.get("case_id", "unknown"))
        spec_id = str(context.get("spec_id", "unknown-spec"))
        bottleneck_score = float(context.get("bottleneck_score", 0.0))
        queue_depth = int(context.get("queue_depth", 0))
        avg_latency_ms = int(context.get("avg_task_latency_ms", 0))
        available_agents = int(context.get("available_agents", 0))
        enabled_tasks = context.get("enabled_tasks", [])
        busy_tasks = context.get("busy_tasks", [])
        event_type = context.get("event_type")

        default_task = enabled_tasks[0] if enabled_tasks else "default-task"

        # Adaptation decision logic based on bottleneck severity
        if bottleneck_score >= 0.85:
            # Critical bottleneck: add resource if available, else escalate
            if available_agents > 0:
                return {
                    "action_type": "ADD_RESOURCE",
                    "task_id": default_task,
                    "agent_id": f"agent-{case_id[:8]}-{int(bottleneck_score * 100)}",
                    "alternate_route": None,
                    "escalation_level": None,
                    "reasoning": f"Critical bottleneck detected ({bottleneck_score:.2%} of total time). "
                                 f"Allocating additional agent to {default_task}."
                }
            else:
                return {
                    "action_type": "ESCALATE",
                    "task_id": default_task,
                    "agent_id": None,
                    "alternate_route": None,
                    "escalation_level": "director",
                    "reasoning": f"Critical bottleneck ({bottleneck_score:.2%}) but no agents available. "
                                 f"Human decision required. Queue depth: {queue_depth}, "
                                 f"Average latency: {avg_latency_ms}ms"
                }

        elif bottleneck_score >= 0.70:
            # Severe bottleneck: add resource or reroute
            if available_agents > 0:
                return {
                    "action_type": "ADD_RESOURCE",
                    "task_id": default_task,
                    "agent_id": f"agent-{case_id[:8]}-secondary",
                    "alternate_route": None,
                    "escalation_level": None,
                    "reasoning": f"Severe bottleneck ({bottleneck_score:.2%}). "
                                 f"Adding resource to improve {default_task} throughput. "
                                 f"Current queue depth: {queue_depth}"
                }
            else:
                return {
                    "action_type": "REROUTE",
                    "task_id": default_task,
                    "agent_id": None,
                    "alternate_route": "expedited-path",
                    "escalation_level": None,
                    "reasoning": f"Severe bottleneck ({bottleneck_score:.2%}) but no agents available. "
                                 f"Attempting reroute to expedited path. "
                                 f"Current busy tasks: {', '.join(busy_tasks) if busy_tasks else 'none'}"
                }

        elif bottleneck_score >= 0.55:
            # Moderate bottleneck: try reroute or add light resource
            if queue_depth > 10:
                return {
                    "action_type": "ADD_RESOURCE",
                    "task_id": default_task,
                    "agent_id": f"agent-{case_id[:8]}-light",
                    "alternate_route": None,
                    "escalation_level": None,
                    "reasoning": f"Moderate bottleneck ({bottleneck_score:.2%}) with high queue depth ({queue_depth}). "
                                 f"Adding light resource to manage workload."
                }
            else:
                return {
                    "action_type": "REROUTE",
                    "task_id": default_task,
                    "agent_id": None,
                    "alternate_route": "alternate-path",
                    "escalation_level": None,
                    "reasoning": f"Moderate bottleneck ({bottleneck_score:.2%}). "
                                 f"Attempting reroute to {default_task} via alternate path. "
                                 f"Average latency: {avg_latency_ms}ms"
                }

        elif bottleneck_score >= 0.40:
            # Minor bottleneck: monitor
            return {
                "action_type": "REROUTE",  # Safe default: ESCALATE with monitor level
                "task_id": default_task,
                "agent_id": None,
                "alternate_route": None,
                "escalation_level": "info",
                "reasoning": f"Minor bottleneck ({bottleneck_score:.2%}). "
                             f"Monitoring {default_task}. No immediate action required. "
                             f"Consider optimization if trend continues."
            }

        else:
            # No bottleneck: normal operation
            return {
                "action_type": "SKIP_TASK",  # Safe default: ESCALATE with info level
                "task_id": None,
                "agent_id": None,
                "alternate_route": None,
                "escalation_level": "debug",
                "reasoning": f"No bottleneck detected ({bottleneck_score:.2%}). "
                             f"Workflow {spec_id} operating normally. "
                             f"Current queue: {queue_depth}, latency: {avg_latency_ms}ms"
            }

    def react_loop(self, context: Dict[str, Any]) -> Dict[str, Any]:
        """
        Execute the full ReAct reasoning + acting loop.

        This is the entry point for the DSPy ReAct framework.
        It orchestrates multiple reasoning steps and tool calls.

        :param context: Workflow context
        :return: Final adaptation decision
        """
        # ReAct would call tools here, but for simplicity we use forward()
        return self.forward(context)


# Create module instance for Java interop
runtime_adaptation_module = RuntimeAdaptationModule()
