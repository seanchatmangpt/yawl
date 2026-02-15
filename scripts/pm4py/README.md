# PM4Py MCP & A2A Integration

Process mining via [PM4Py](https://pm4py.fit.fraunhofer.de/) exposed as **MCP** (Model Context Protocol) tools and **A2A** (Agent-to-Agent) skills.

## Setup

```bash
cd scripts/pm4py
uv sync
```

## MCP Server (STDIO)

For MCP clients that spawn subprocesses (e.g. YAWL MCP client, Claude):

```bash
uv run mcp_server.py
```

**Tools:**
- `pm4py_discover` – Discover BPMN or DFG from XES log
- `pm4py_conformance` – Check fitness/precision against model
- `pm4py_performance` – Flow time, throughput, activity counts

**MCP config (e.g. Claude Code):**
```json
{
  "pm4py": {
    "command": "uv",
    "args": ["run", "mcp_server.py"],
    "cwd": "/path/to/yawl/scripts/pm4py"
  }
}
```

## A2A Agent (HTTP)

```bash
uv run a2a_agent.py
# Or: ./run-a2a.sh
```

**Agent card:** http://localhost:9092/.well-known/agent-card.json

**Skills:** `process_discovery`, `conformance_check`, `performance_analysis`

**Message format (JSON):**
```json
{"skill": "discover", "xes_input": "<xes xml or path>", "algorithm": "inductive"}
{"skill": "conformance", "xes_input": "<xes>", "bpmn_model_xml": "<bpmn>"}
{"skill": "performance", "xes_input": "<xes>"}
```

## YAWL Integration

- **MCP:** Add PM4Py as a secondary MCP server; YAWL MCP client can call PM4Py tools for process mining over exported XES.
- **A2A:** YAWL A2A client can discover and message the PM4Py agent at `http://localhost:9092/` for conformance/performance analysis.
