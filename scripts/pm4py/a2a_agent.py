#!/usr/bin/env python3
"""
PM4Py A2A Agent - Process mining skills via Agent-to-Agent protocol.

Exposes process discovery, conformance checking, and performance analysis
as A2A skills. Runs on HTTP for discovery by other A2A agents.

Usage:
  uv run a2a_agent.py
  # Or: python a2a_agent.py (with a2a-sdk, pm4py installed)

Agent card: http://localhost:9092/.well-known/agent-card.json
"""

from __future__ import annotations

import json
import os

import uvicorn
from a2a.server.agent_execution import AgentExecutor
from a2a.server.apps import A2AStarletteApplication
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.tasks import InMemoryTaskStore
from a2a.types import AgentCapabilities, AgentCard, AgentSkill
from a2a.utils import new_agent_text_message

from pm4py_backend import (
    analyze_performance,
    check_conformance,
    discover_process,
    to_json,
)


def _extract_text_from_message(context: object) -> str:
    """Extract user text from A2A request context."""
    try:
        msg = getattr(context, "message", None) or getattr(
            context, "request_message", None
        )
        if msg is None:
            return ""
        parts = getattr(msg, "parts", []) or []
        texts = []
        for p in parts:
            if getattr(p, "kind", None) == "text" or hasattr(p, "text"):
                texts.append(getattr(p, "text", "") or "")
            elif isinstance(p, dict) and p.get("kind") == "text":
                texts.append(p.get("text", ""))
        return "".join(texts).strip()
    except Exception:
        return ""


class Pm4PyAgentExecutor(AgentExecutor):
    """Agent executor that routes JSON commands to PM4Py backend."""

    async def execute(
        self,
        context: object,
        event_queue: object,
    ) -> None:
        """Process incoming message and invoke PM4Py skill."""
        text = _extract_text_from_message(context)
        if not text:
            await event_queue.enqueue_event(
                new_agent_text_message(
                    '{"error":"Empty message. Send JSON: {"skill":"discover|conformance|performance","xes_input":"...","algorithm":"inductive" (optional)}"}'
                )
            )
            return

        try:
            payload = json.loads(text)
        except json.JSONDecodeError:
            await event_queue.enqueue_event(
                new_agent_text_message(
                    '{"error":"Invalid JSON. Use: {\"skill\":\"discover\",\"xes_input\":\"<xes>\"}"}'
                )
            )
            return

        skill = (payload.get("skill") or "").lower()
        xes_input = payload.get("xes_input", "")

        if not xes_input:
            await event_queue.enqueue_event(
                new_agent_text_message('{"error":"xes_input is required"}')
            )
            return

        try:
            if skill == "discover":
                algorithm = payload.get("algorithm", "inductive")
                result = discover_process(xes_input, algorithm=algorithm)
                out = to_json(result)
            elif skill == "conformance":
                result = check_conformance(
                    xes_input,
                    petri_net_path=payload.get("petri_net_path"),
                    bpmn_model_xml=payload.get("bpmn_model_xml"),
                )
                out = to_json(result)
            elif skill == "performance":
                result = analyze_performance(xes_input)
                out = to_json(result)
            else:
                out = json.dumps(
                    {
                        "error": f"Unknown skill: {skill}",
                        "supported": ["discover", "conformance", "performance"],
                    }
                )
            await event_queue.enqueue_event(new_agent_text_message(out))
        except Exception as e:
            await event_queue.enqueue_event(
                new_agent_text_message(json.dumps({"error": str(e)}))
            )

    async def cancel(
        self,
        context: object,
        event_queue: object,
    ) -> None:
        """Cancel not supported for stateless process mining."""
        raise NotImplementedError("Cancel not supported")


SKILL_DISCOVER = AgentSkill(
    id="process_discovery",
    name="Process Discovery",
    description="Discover a process model (BPMN or DFG) from an XES event log",
    tags=["process mining", "discovery", "xes", "bpmn", "dfg"],
    examples=[
        "Discover process from XES log",
        "Run inductive mining on event log",
    ],
)

SKILL_CONFORMANCE = AgentSkill(
    id="conformance_check",
    name="Conformance Check",
    description="Check conformance of XES log against a process model (fitness, precision)",
    tags=["process mining", "conformance", "fitness", "xes"],
    examples=[
        "Check log conformance",
        "Compute fitness and precision",
    ],
)

SKILL_PERFORMANCE = AgentSkill(
    id="performance_analysis",
    name="Performance Analysis",
    description="Analyze performance metrics: flow time, throughput, activity counts",
    tags=["process mining", "performance", "flow time", "throughput"],
    examples=[
        "Analyze performance of event log",
        "Compute flow time and throughput",
    ],
)

AGENT_CARD = AgentCard(
    name="PM4Py Process Mining Agent",
    description="Process mining agent: discovery, conformance, performance over XES logs",
    url="http://localhost:9092/",
    version="1.0.0",
    default_input_modes=["text"],
    default_output_modes=["text"],
    capabilities=AgentCapabilities(streaming=False),
    skills=[SKILL_DISCOVER, SKILL_CONFORMANCE, SKILL_PERFORMANCE],
)


def main() -> None:
    """Start the A2A HTTP server."""
    port = int(os.environ.get("PM4PY_A2A_PORT", "9092"))
    request_handler = DefaultRequestHandler(
        agent_executor=Pm4PyAgentExecutor(),
        task_store=InMemoryTaskStore(),
    )
    server = A2AStarletteApplication(
        agent_card=AGENT_CARD.model_copy(
            update={"url": f"http://localhost:{port}/"}
        ),
        http_handler=request_handler,
    )
    print(f"PM4Py A2A Agent on http://0.0.0.0:{port}/")
    print(f"Agent card: http://localhost:{port}/.well-known/agent-card.json")
    uvicorn.run(server.build(), host="0.0.0.0", port=port)


if __name__ == "__main__":
    main()
