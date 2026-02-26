"""
POWL Generator: produces POWL models as JSON using pm4py.
Called from PowlPythonBridge via GraalPy.
"""
import json


def generate_powl_json(description: str) -> str:
    """
    Generates a POWL model JSON from a process description.
    Uses pm4py if available, otherwise raises RuntimeError.

    Returns JSON string: {"type":"SEQUENCE","id":"root","children":[...]}
    """
    try:
        from pm4py.objects.powl.obj import OperatorPOWL, Transition, StrictPartialOrder, Operator
    except ImportError as e:
        raise RuntimeError(f"pm4py is not installed in the GraalPy environment: {e}")

    # Simple heuristic: build a SEQUENCE of activities extracted from description
    # In production, this would use an LLM or PM algorithm to infer the model
    activities = _extract_activities_from_description(description)
    if not activities:
        raise RuntimeError(f"No activities could be extracted from description: {description[:100]}")

    nodes = []
    for act in activities:
        nodes.append({"type": "ACTIVITY", "id": _safe_id(act), "label": act})

    if len(nodes) == 1:
        return json.dumps(nodes[0])

    return json.dumps({
        "type": "SEQUENCE",
        "id": "root",
        "children": nodes
    })


def mine_from_xes(xes_content: str) -> str:
    """
    Mines a POWL model from an XES event log using pm4py's inductive miner.
    Returns JSON string representation.
    """
    try:
        import pm4py
        import tempfile, os
        with tempfile.NamedTemporaryFile(mode='w', suffix='.xes', delete=False) as f:
            f.write(xes_content)
            tmp_path = f.name
        try:
            log = pm4py.read_xes(tmp_path)
            from pm4py.algo.discovery.powl import algorithm as powl_discovery
            model = powl_discovery.apply(log)
            return _serialize_powl(model)
        finally:
            os.unlink(tmp_path)
    except ImportError as e:
        raise RuntimeError(f"pm4py is not installed in the GraalPy environment: {e}")


def _extract_activities_from_description(description: str) -> list:
    """Extracts activity names from a process description using keyword heuristics."""
    import re
    # Look for verb phrases as activity candidates
    words = re.split(r'[\s,;.]+', description)
    # Filter to meaningful words (length > 3, not stopwords)
    stopwords = {'the', 'and', 'or', 'if', 'is', 'are', 'was', 'were', 'has', 'have',
                 'then', 'when', 'after', 'before', 'during', 'with', 'by', 'for',
                 'from', 'that', 'this', 'not', 'also', 'can', 'may', 'will'}
    activities = [w.strip() for w in words if len(w.strip()) > 3 and w.lower() not in stopwords]
    # Deduplicate preserving order
    seen = set()
    result = []
    for a in activities:
        if a.lower() not in seen:
            seen.add(a.lower())
            result.append(a.capitalize())
    return result[:8]  # Cap at 8 activities for reasonable model size


def _safe_id(label: str) -> str:
    """Converts a label to a safe identifier."""
    import re
    return 'act_' + re.sub(r'[^a-zA-Z0-9_]', '_', label).lower()


def _serialize_powl(model) -> str:
    """Serializes a pm4py POWL model to JSON."""
    from pm4py.objects.powl.obj import OperatorPOWL, Transition, StrictPartialOrder, Operator

    def node_to_dict(node, node_id: str) -> dict:
        if isinstance(node, Transition):
            return {"type": "ACTIVITY", "id": node_id, "label": node.label or node_id}
        elif isinstance(node, OperatorPOWL):
            op_map = {
                Operator.SEQUENCE: "SEQUENCE",
                Operator.XOR: "XOR",
                Operator.LOOP: "LOOP",
                Operator.PARALLEL: "PARALLEL",
            }
            op_type = op_map.get(node.operator, "SEQUENCE")
            children = [node_to_dict(c, f"{node_id}_{i}") for i, c in enumerate(node.children)]
            return {"type": op_type, "id": node_id, "children": children}
        elif isinstance(node, StrictPartialOrder):
            # Flatten to SEQUENCE for simplicity
            children = [node_to_dict(c, f"{node_id}_{i}") for i, c in enumerate(node.nodes)]
            if len(children) == 1:
                return children[0]
            return {"type": "SEQUENCE", "id": node_id, "children": children}
        else:
            return {"type": "ACTIVITY", "id": node_id, "label": str(node)}

    result = node_to_dict(model, "root")
    return json.dumps(result)
