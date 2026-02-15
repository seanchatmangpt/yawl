"""
PM4Py process mining backend for MCP and A2A integration.

Provides process discovery, conformance checking, and performance analysis
over XES event logs. Used by both the MCP server and A2A agent.
"""

from __future__ import annotations

import io
import json
import os
import tempfile
from dataclasses import dataclass
from typing import Any

import pm4py
from pm4py.objects.log.obj import EventLog


@dataclass
class DiscoveryResult:
    """Result of process discovery."""

    algorithm: str
    num_traces: int
    num_events: int
    num_activities: int
    model_type: str
    model_repr: str


@dataclass
class ConformanceResult:
    """Result of conformance checking."""

    fitness: float
    precision: float | None
    num_traces: int
    fitting_traces: int
    deviating_traces: list[str]


@dataclass
class PerformanceResult:
    """Result of performance analysis."""

    avg_flow_time_seconds: float
    throughput_traces_per_day: float
    activity_counts: dict[str, int]
    num_traces: int


def _parse_xes(xes_input: str) -> EventLog:
    """Parse XES from string (XML) or path."""
    xes_input = xes_input.strip()
    if xes_input.startswith("<?xml") or xes_input.startswith("<"):
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".xes", delete=False, encoding="utf-8"
        ) as f:
            f.write(xes_input)
            path = f.name
        try:
            return pm4py.read_xes(path)
        finally:
            os.unlink(path)
    return pm4py.read_xes(xes_input)


def discover_process(
    xes_input: str,
    algorithm: str = "inductive",
) -> DiscoveryResult:
    """
    Discover a process model from an XES event log.

    Args:
        xes_input: XES XML string or path to .xes file
        algorithm: "inductive" (BPMN) or "dfg" (directly-follows graph)

    Returns:
        DiscoveryResult with model and statistics
    """
    log = _parse_xes(xes_input)
    num_traces = len(log)
    num_events = sum(len(trace) for trace in log)
    activities = pm4py.get_event_attribute_values(log, "concept:name")
    num_activities = len(activities)

    if algorithm == "dfg":
        dfg, start_activities, end_activities = pm4py.discover_dfg(log)
        model_repr = json.dumps(
            {
                "type": "dfg",
                "start_activities": list(start_activities),
                "end_activities": list(end_activities),
                "edges": [
                    {"from": k[0], "to": k[1], "count": v}
                    for k, v in dfg.items()
                ],
            },
            indent=2,
        )
        return DiscoveryResult(
            algorithm="dfg",
            num_traces=num_traces,
            num_events=num_events,
            num_activities=num_activities,
            model_type="dfg",
            model_repr=model_repr,
        )

    bpmn_model = pm4py.discover_bpmn_inductive(log)
    try:
        from pm4py.objects.bpmn.exporter import exporter as bpmn_exporter
        model_repr = bpmn_exporter.serialize(bpmn_model).decode("utf-8")
    except Exception:
        model_repr = "<bpmn model discovered>"
    return DiscoveryResult(
        algorithm="inductive",
        num_traces=num_traces,
        num_events=num_events,
        num_activities=num_activities,
        model_type="bpmn",
        model_repr=model_repr,
    )


def check_conformance(
    xes_input: str,
    petri_net_path: str | None = None,
    bpmn_model_xml: str | None = None,
) -> ConformanceResult:
    """
    Check conformance of an XES log against a process model.

    Args:
        xes_input: XES XML string or path
        petri_net_path: Path to Petri net .pnml file (optional)
        bpmn_model_xml: BPMN model XML string (optional)

    Returns:
        ConformanceResult with fitness and precision
    """
    log = _parse_xes(xes_input)
    num_traces = len(log)

    if petri_net_path:
        net, im, fm = pm4py.read_pnml(petri_net_path)
    elif bpmn_model_xml:
        bpmn_model = pm4py.read_bpmn(io.BytesIO(bpmn_model_xml.encode("utf-8")))
        net, im, fm = pm4py.convert_to_petri_net(bpmn_model)
    else:
        net, im, fm = pm4py.discover_petri_net_inductive(log)

    fitness_result = pm4py.fitness_token_based_replay(log, net, im, fm)
    if isinstance(fitness_result, dict):
        fitness_val = float(
            fitness_result.get("log_fitness")
            or fitness_result.get("average_trace_fitness")
            or fitness_result.get("perc_fit_traces", 0)
        )
    else:
        fitness_val = float(fitness_result) if fitness_result is not None else 0.0

    precision_result = pm4py.precision_token_based_replay(log, net, im, fm)
    precision = (
        float(precision_result) if precision_result is not None else None
    )

    fitting = int(round(fitness_val * num_traces)) if num_traces > 0 else 0
    return ConformanceResult(
        fitness=fitness_val,
        precision=precision,
        num_traces=num_traces,
        fitting_traces=fitting,
        deviating_traces=[],
    )


def analyze_performance(xes_input: str) -> PerformanceResult:
    """
    Analyze performance metrics of an XES event log.

    Args:
        xes_input: XES XML string or path

    Returns:
        PerformanceResult with flow time, throughput, activity counts
    """
    log = _parse_xes(xes_input)
    num_traces = len(log)

    if num_traces == 0:
        return PerformanceResult(
            avg_flow_time_seconds=0.0,
            throughput_traces_per_day=0.0,
            activity_counts={},
            num_traces=0,
        )

    avg_flow_seconds = 0.0
    try:
        flow_times = pm4py.get_case_duration(log)
        if flow_times:
            total = sum(
                t.total_seconds() if hasattr(t, "total_seconds") else float(t)
                for t in flow_times
            )
            avg_flow_seconds = total / len(flow_times)
    except Exception:
        pass

    throughput_per_day = 0.0
    try:
        throughput = pm4py.get_case_arrival_average(log)
        throughput_per_day = float(throughput) if throughput is not None else 0.0
    except Exception:
        pass

    activity_counts = pm4py.get_event_attribute_values(log, "concept:name")
    counts = {k: int(v) for k, v in activity_counts.items()}

    return PerformanceResult(
        avg_flow_time_seconds=avg_flow_seconds,
        throughput_traces_per_day=throughput_per_day,
        activity_counts=counts,
        num_traces=num_traces,
    )


def to_json(obj: Any) -> str:
    """Serialize result to JSON string."""
    if hasattr(obj, "__dataclass_fields__"):
        return json.dumps(
            {k: getattr(obj, k) for k in obj.__dataclass_fields__},
            indent=2,
        )
    return json.dumps(obj, indent=2)
