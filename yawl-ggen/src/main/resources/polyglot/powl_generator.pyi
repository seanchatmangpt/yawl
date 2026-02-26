"""
Type stub for powl_generator.py — the canonical contract between Java and Python
for POWL model generation in YAWL.

Java side (PowlPythonBridge) calls these functions via PythonExecutionEngine.evalToString().
Python side (powl_generator.py) implements them, returning JSON strings.

JSON wire format:
  Activity:  {"type": "ACTIVITY",  "id": str, "label": str}
  Operator:  {"type": "SEQUENCE" | "XOR" | "PARALLEL" | "LOOP",
               "id": str, "children": list[Node]}
"""


def generate_powl_json(description: str) -> str:
    """
    Generate a POWL model from a natural language process description.

    Args:
        description: Natural language process description (non-empty).

    Returns:
        JSON string conforming to the POWL wire format above.

    Raises:
        RuntimeError: If pm4py is not installed or no activities can be extracted.
    """
    ...


def mine_from_xes(xes_content: str) -> str:
    """
    Discover a POWL model from an XES event log using pm4py's inductive miner.

    Args:
        xes_content: Complete XES XML string of the event log.

    Returns:
        JSON string conforming to the POWL wire format above.

    Raises:
        RuntimeError: If pm4py is not installed or log parsing fails.
    """
    ...
