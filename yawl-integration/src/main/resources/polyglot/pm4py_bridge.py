"""
pm4py_bridge.py — one-to-one GraalPy bridge for the pm4py Python library.

Loaded once into all GraalPy contexts at Pm4py construction time via
PythonExecutionEngine.evalScriptInAllContexts(). After loading, every
function is accessible via ctx.getBindings().getMember("function_name").

Design principles:
  - One function per pm4py public API entry point (mirror pm4py docs exactly)
  - No serialisation inside this file; return raw pm4py objects as GraalPy Values
  - Java Strings arrive via GraalPy interop; str() coercion is always explicit
  - No temp files for XES/PNML where stream-based parsers exist
  - _require_pm4py() guard on every function — clear error if pm4py is absent

See: https://pm4py.fit.fraunhofer.de/documentation
"""

import io
import json

try:
    import pm4py
    _PM4PY_AVAILABLE = True
    _PM4PY_ERROR = None
except ImportError as _e:
    _PM4PY_AVAILABLE = False
    _PM4PY_ERROR = str(_e)


def _require_pm4py():
    if not _PM4PY_AVAILABLE:
        raise RuntimeError(
            "pm4py is not installed in the GraalPy environment: " + _PM4PY_ERROR
        )


# ── Health ─────────────────────────────────────────────────────────────────────

def ping() -> str:
    """Liveness check. Returns 'ok' if pm4py is importable, raises RuntimeError otherwise."""
    _require_pm4py()
    return "ok"


# ── Reading / Writing ──────────────────────────────────────────────────────────

def read_xes(xes_xml) -> object:
    """
    Parse XES event log from a string.
    Accepts a Java String via GraalPy interop (str() coerces the interop object).
    Returns a pm4py EventLog object.

    Equivalent to: pm4py.read_xes(path)
    """
    _require_pm4py()
    content = str(xes_xml)
    # Prefer in-memory parse (no temp file, no disk I/O)
    try:
        from pm4py.objects.log.importer.xes import importer as xes_importer
        stream = io.StringIO(content)
        return xes_importer.deserialize(stream)
    except (AttributeError, TypeError):
        # Fallback for older pm4py versions that require a file path
        import tempfile, os
        with tempfile.NamedTemporaryFile(
                mode='w', suffix='.xes', delete=False, encoding='utf-8') as f:
            f.write(content)
            tmp = f.name
        try:
            return pm4py.read_xes(tmp)
        finally:
            os.unlink(tmp)


def write_xes(log, path: str) -> None:
    """
    Write pm4py EventLog to an XES file at path.
    Equivalent to: pm4py.write_xes(log, path)
    """
    _require_pm4py()
    pm4py.write_xes(log, str(path))


def read_pnml_string(pnml_xml) -> tuple:
    """
    Parse PNML XML string into a (PetriNet, initial Marking, final Marking) tuple.
    Accepts a Java String via GraalPy interop.
    No temp file: uses io.StringIO.
    """
    _require_pm4py()
    content = str(pnml_xml)
    try:
        from pm4py.objects.petri_net.importer import importer as pnml_importer
        stream = io.StringIO(content)
        return pnml_importer.deserialize(stream)
    except (AttributeError, TypeError):
        import tempfile, os
        with tempfile.NamedTemporaryFile(
                mode='w', suffix='.pnml', delete=False, encoding='utf-8') as f:
            f.write(content)
            tmp = f.name
        try:
            return pm4py.read_pnml(tmp)
        finally:
            os.unlink(tmp)


def export_petri_net_to_pnml_string(net, im, fm) -> str:
    """
    Serialize a pm4py PetriNet to PNML XML string.
    Equivalent to pm4py.write_pnml(net, im, fm, path) but returns the string.
    """
    _require_pm4py()
    try:
        from pm4py.objects.petri_net.exporter import exporter as pnml_exporter
        out = io.StringIO()
        pnml_exporter.serialize(net, im, fm, out)
        return out.getvalue()
    except (AttributeError, TypeError):
        import tempfile, os
        with tempfile.NamedTemporaryFile(
                mode='w', suffix='.pnml', delete=False, encoding='utf-8') as f:
            tmp = f.name
        try:
            pm4py.write_pnml(net, im, fm, tmp)
            with open(tmp, encoding='utf-8') as rf:
                return rf.read()
        finally:
            os.unlink(tmp)


# ── Discovery ──────────────────────────────────────────────────────────────────

def discover_petri_net_alpha(log) -> tuple:
    """
    Alpha algorithm discovery.
    Returns (PetriNet, initial Marking, final Marking).
    Equivalent to: pm4py.discover_petri_net_alpha(log)
    """
    _require_pm4py()
    return pm4py.discover_petri_net_alpha(log)


def discover_petri_net_alpha_plus(log) -> tuple:
    """
    Alpha+ algorithm discovery.
    Returns (PetriNet, initial Marking, final Marking).
    Equivalent to: pm4py.discover_petri_net_alpha_plus(log)
    """
    _require_pm4py()
    return pm4py.discover_petri_net_alpha_plus(log)


def discover_petri_net_alpha_plus_plus(log) -> tuple:
    """
    Alpha+++ algorithm discovery.
    Returns (PetriNet, initial Marking, final Marking).
    Equivalent to: pm4py.discover_petri_net_alpha_plus_plus(log)
    """
    _require_pm4py()
    return pm4py.discover_petri_net_alpha_plus_plus(log)


def discover_petri_net_inductive(log) -> tuple:
    """
    Inductive miner discovery.
    Returns (PetriNet, initial Marking, final Marking).
    Equivalent to: pm4py.discover_petri_net_inductive(log)
    """
    _require_pm4py()
    return pm4py.discover_petri_net_inductive(log)


def discover_petri_net_heuristics(log) -> tuple:
    """
    Heuristics miner discovery.
    Returns (PetriNet, initial Marking, final Marking).
    Equivalent to: pm4py.discover_petri_net_heuristics(log)
    """
    _require_pm4py()
    return pm4py.discover_petri_net_heuristics(log)


def discover_process_tree_inductive(log) -> object:
    """
    Discover a process tree using the inductive miner.
    Returns a pm4py ProcessTree object.
    Equivalent to: pm4py.discover_process_tree_inductive(log)
    """
    _require_pm4py()
    return pm4py.discover_process_tree_inductive(log)


def discover_bpmn_inductive(log) -> object:
    """
    Discover a BPMN model using the inductive miner.
    Returns a pm4py BPMN object.
    Equivalent to: pm4py.discover_bpmn_inductive(log)
    """
    _require_pm4py()
    return pm4py.discover_bpmn_inductive(log)


def discover_powl(log) -> object:
    """
    Discover a POWL model.
    Returns a pm4py POWL object.
    Equivalent to: pm4py.discover_powl(log)
    """
    _require_pm4py()
    return pm4py.discover_powl(log)


def discover_dfg(log) -> tuple:
    """
    Discover a Directly-Follows Graph.
    Returns (dfg_dict, start_activities_dict, end_activities_dict).
    Note: dfg_dict has tuple keys (str, str) — not directly marshallable to Java.
    Use discover_dfg_as_json for Java interop.
    Equivalent to: pm4py.discover_dfg(log)
    """
    _require_pm4py()
    return pm4py.discover_dfg(log)


def discover_dfg_as_json(log) -> str:
    """
    Discover a DFG and return it as a JSON string.
    Converts tuple-key dfg dict to edge list for Java interop compatibility.
    Returns JSON: {"edges": [{"source": str, "target": str, "count": int}],
                   "start_activities": {str: int},
                   "end_activities": {str: int}}
    """
    _require_pm4py()
    dfg, start, end = pm4py.discover_dfg(log)
    return json.dumps({
        "edges": [
            {"source": a, "target": b, "count": c}
            for (a, b), c in dfg.items()
        ],
        "start_activities": dict(start),
        "end_activities": dict(end)
    })


# ── Conformance ────────────────────────────────────────────────────────────────

def conformance_diagnostics_token_based_replay(log, net, im, fm) -> list:
    """
    Token-based replay conformance diagnostics.
    Returns list of per-trace replay results (each is a dict).
    Equivalent to: pm4py.conformance_diagnostics_token_based_replay(log, net, im, fm)
    """
    _require_pm4py()
    return pm4py.conformance_diagnostics_token_based_replay(log, net, im, fm)


def conformance_diagnostics_alignments(log, net, im, fm) -> list:
    """
    Alignment-based conformance diagnostics.
    Returns list of per-trace alignment results (each is a dict).
    Equivalent to: pm4py.conformance_diagnostics_alignments(log, net, im, fm)
    """
    _require_pm4py()
    return pm4py.conformance_diagnostics_alignments(log, net, im, fm)


def fitness_token_based_replay(log, net, im, fm) -> dict:
    """
    Compute fitness via token-based replay.
    Returns dict with keys: log_fitness, average_trace_fitness, perc_fit_traces, etc.
    Equivalent to: pm4py.fitness_token_based_replay(log, net, im, fm)
    """
    _require_pm4py()
    return pm4py.fitness_token_based_replay(log, net, im, fm)


def fitness_alignments(log, net, im, fm) -> dict:
    """
    Compute fitness via alignment-based replay.
    Equivalent to: pm4py.fitness_alignments(log, net, im, fm)
    """
    _require_pm4py()
    return pm4py.fitness_alignments(log, net, im, fm)


def precision_token_based_replay(log, net, im, fm) -> float:
    """
    Compute precision via token-based replay.
    Returns a float in [0, 1].
    Equivalent to: pm4py.precision_token_based_replay(log, net, im, fm)
    """
    _require_pm4py()
    return pm4py.precision_token_based_replay(log, net, im, fm)


# ── Statistics ─────────────────────────────────────────────────────────────────

def get_all_case_durations(log) -> list:
    """
    Compute duration (in seconds) for each case in the log.
    Returns list of floats.
    Equivalent to: pm4py.get_all_case_durations(log)
    """
    _require_pm4py()
    return pm4py.get_all_case_durations(log)


def get_variants(log) -> dict:
    """
    Extract variants (unique activity sequences) and their frequencies.
    Returns dict mapping variant string → count.
    Equivalent to: pm4py.get_variants(log)
    """
    _require_pm4py()
    # pm4py.get_variants returns {frozenset/tuple: list_of_traces}
    # Normalise to {str: int} for Java interop
    raw = pm4py.get_variants(log)
    return {str(variant): len(traces) for variant, traces in raw.items()}


def get_start_activities(log) -> dict:
    """
    Get start activities and their occurrence counts.
    Returns dict mapping activity_name → count.
    Equivalent to: pm4py.get_start_activities(log)
    """
    _require_pm4py()
    return dict(pm4py.get_start_activities(log))


def get_end_activities(log) -> dict:
    """
    Get end activities and their occurrence counts.
    Returns dict mapping activity_name → count.
    Equivalent to: pm4py.get_end_activities(log)
    """
    _require_pm4py()
    return dict(pm4py.get_end_activities(log))


def get_event_attributes(log) -> list:
    """
    Get all event attribute names in the log.
    Returns list of strings (set converted to list for GraalPy interop).
    Equivalent to: pm4py.get_event_attributes(log)
    """
    _require_pm4py()
    return list(pm4py.get_event_attributes(log))


# ── Format Conversion ──────────────────────────────────────────────────────────

def convert_to_dataframe(log) -> object:
    """
    Convert EventLog to a pandas DataFrame.
    Returns the DataFrame as a raw Python object (GraalPy Value).
    Equivalent to: pm4py.convert_to_dataframe(log)
    """
    _require_pm4py()
    return pm4py.convert_to_dataframe(log)


def convert_to_event_log(df) -> object:
    """
    Convert a pandas DataFrame to a pm4py EventLog.
    Equivalent to: pm4py.convert_to_event_log(df)
    """
    _require_pm4py()
    return pm4py.convert_to_event_log(df)


def convert_to_event_stream(log) -> object:
    """
    Convert EventLog to an EventStream.
    Equivalent to: pm4py.convert_to_event_stream(log)
    """
    _require_pm4py()
    return pm4py.convert_to_event_stream(log)


# ── OCEL ───────────────────────────────────────────────────────────────────────

def xes_to_ocel_json(xes_xml) -> str:
    """
    Convert XES event log string to OCEL 2.0 JSON string.
    Accepts a Java String via GraalPy interop.
    Returns OCEL 2.0 JSON as a Python string.
    """
    _require_pm4py()
    log = read_xes(xes_xml)
    # Convert to OCEL via pm4py's object-centric conversion
    try:
        # pm4py 2.7+: convert_to_ocel returns an OCEL object
        ocel = pm4py.convert_to_ocel(log)
        out = io.StringIO()
        from pm4py.objects.ocel.exporter.jsonocel import exporter as ocel_exporter
        ocel_exporter.apply(ocel, out)
        result = out.getvalue()
        if result:
            return result
    except (AttributeError, ImportError, TypeError):
        pass
    # Fallback: represent as a basic OCEL 2.0 JSON structure from the DataFrame
    df = pm4py.convert_to_dataframe(log)
    # Build minimal OCEL 2.0 structure
    events = []
    for _, row in df.iterrows():
        event = {
            "ocel:id": str(row.get("case:concept:name", "")) + "_" + str(row.get("concept:name", "")),
            "ocel:activity": str(row.get("concept:name", "")),
            "ocel:timestamp": str(row.get("time:timestamp", "")),
            "ocel:type:case": [str(row.get("case:concept:name", ""))]
        }
        events.append(event)
    return json.dumps({
        "ocel:global-log": {"ocel:version": "2.0"},
        "ocel:events": {e["ocel:id"]: e for e in events}
    })
