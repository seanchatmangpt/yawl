#!/usr/bin/env python3
"""
PM4Py MCP Server - Process mining tools via Model Context Protocol.

Exposes process discovery, conformance checking, and performance analysis
as MCP tools. Runs on STDIO transport for subprocess invocation by MCP clients.

Usage:
  uv run mcp_server.py
  # Or: python mcp_server.py (with mcp, pm4py installed)

MCP clients (e.g. YAWL, Claude) can spawn this server and call:
  - pm4py_discover: Discover process model from XES log
  - pm4py_conformance: Check log conformance against model
  - pm4py_performance: Analyze performance metrics
"""

from __future__ import annotations

from mcp.server.fastmcp import FastMCP

from pm4py_backend import (
    analyze_performance,
    check_conformance,
    discover_process,
    to_json,
)

mcp = FastMCP(
    "PM4Py Process Mining",
    instructions="Process mining tools for XES event logs: discovery, conformance, performance.",
)


@mcp.tool()
def pm4py_discover(xes_input: str, algorithm: str = "inductive") -> str:
    """
    Discover a process model from an XES event log.

    Args:
        xes_input: XES XML string or path to .xes file
        algorithm: "inductive" (BPMN) or "dfg" (directly-follows graph)

    Returns:
        JSON with model, num_traces, num_events, num_activities
    """
    result = discover_process(xes_input, algorithm=algorithm)
    return to_json(result)


@mcp.tool()
def pm4py_conformance(
    xes_input: str,
    petri_net_path: str | None = None,
    bpmn_model_xml: str | None = None,
) -> str:
    """
    Check conformance of an XES log against a process model.

    Args:
        xes_input: XES XML string or path
        petri_net_path: Path to Petri net .pnml file (optional)
        bpmn_model_xml: BPMN model XML string (optional). If neither provided,
            discovers model from log (fitness will be 1.0).

    Returns:
        JSON with fitness, precision, num_traces, fitting_traces
    """
    result = check_conformance(
        xes_input,
        petri_net_path=petri_net_path or None,
        bpmn_model_xml=bpmn_model_xml or None,
    )
    return to_json(result)


@mcp.tool()
def pm4py_performance(xes_input: str) -> str:
    """
    Analyze performance metrics of an XES event log.

    Args:
        xes_input: XES XML string or path

    Returns:
        JSON with avg_flow_time_seconds, throughput_traces_per_day, activity_counts
    """
    result = analyze_performance(xes_input)
    return to_json(result)


if __name__ == "__main__":
    mcp.run(transport="stdio")
