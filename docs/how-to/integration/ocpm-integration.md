# How to Enable Object-Centric Process Mining in YAWL

**Quadrant**: How-To | **Audience**: Operators, Developers

> **Background**: Read [Object-Centric Process Mining](../../explanation/object-centric-process-mining.md)
> and [Process Intelligence](../../explanation/process-intelligence.md) for the
> conceptual foundation before following these steps.

---

## Prerequisites

| Requirement | Version |
|-------------|---------|
| YAWL engine | v6.0+ |
| Java | 25+ |
| Python | 3.10+ |
| Rust toolchain (optional, for building from source) | 1.75+ |
| Docker (optional, for containerised deployment) | 24+ |

Interface E must be enabled on the YAWL engine (it is on by default in v6).

---

## Part A — Deploy Rust4PM Server

Rust4PM (`process_mining` crate on crates.io) provides a REST HTTP server that
performs process discovery and OCEL 2.0 analysis 10-40× faster than pure Python
implementations.

### Option 1 — Docker (recommended)

```bash
docker run -d \
  --name rust4pm \
  -p 8000:8000 \
  rust4pm/server:latest
```

Verify the server is running:

```bash
curl http://localhost:8000/api/v1/health
# Expected: {"status":"ok"}
```

### Option 2 — Build from source

```bash
cargo install process_mining --features=http-server
rust4pm-server --port 8000
```

### Option 3 — Kubernetes

```yaml
# k8s/agents/rust4pm-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: rust4pm
spec:
  replicas: 1
  selector:
    matchLabels:
      app: rust4pm
  template:
    spec:
      containers:
        - name: rust4pm
          image: rust4pm/server:latest
          ports:
            - containerPort: 8000
          env:
            - name: RUST4PM_MAX_HEAP_MB
              value: "2048"
```

### Configure YAWL to use Rust4PM

Set the environment variable before starting YAWL:

```bash
export RUST4PM_SERVER_URL=http://rust4pm:8000
# or for local development:
export RUST4PM_SERVER_URL=http://localhost:8000
```

YAWL's `Rust4PmClient.fromEnvironment()` reads this variable automatically.

Verify the connection from Java:

```java
var client = Rust4PmClient.fromEnvironment();
boolean healthy = client.isHealthy();  // throws IOException if unreachable
System.out.println("Rust4PM healthy: " + healthy);
```

---

## Part B — Start pm4py Python Bridge

The pm4py bridge exposes process discovery, conformance, and performance analysis
via both MCP (subprocess) and A2A (HTTP) transports.

### Install dependencies

```bash
cd /home/user/yawl/scripts/pm4py
pip install -r requirements.txt
```

`requirements.txt` includes:
```
pm4py>=2.7.0
mcp[cli]>=1.0.0
a2a-sdk[http-server]>=0.3.0
uvicorn[standard]>=0.30.0
```

### Start MCP server (subprocess / STDIO transport)

The MCP server is invoked as a subprocess by the YAWL MCP host:

```bash
python scripts/pm4py/mcp_server.py
```

This starts a FastMCP server on STDIO exposing three tools:
- `pm4py_discover` — OC-DFG or OC-Petri Net discovery
- `pm4py_conformance` — fitness and precision
- `pm4py_performance` — flow time, throughput, activity counts

### Start A2A agent (HTTP transport)

The A2A agent runs as a standalone HTTP service:

```bash
export PM4PY_A2A_PORT=9092   # optional, default is 9092
python scripts/pm4py/a2a_agent.py
```

Verify the agent card:

```bash
curl http://localhost:9092/.well-known/agent.json
```

### Docker deployment

```bash
# MCP server (subprocess)
docker build -f scripts/pm4py/Dockerfile.mcp -t yawl-pm4py-mcp scripts/pm4py/
docker run --name pm4py-mcp yawl-pm4py-mcp

# A2A agent (HTTP)
docker build -f scripts/pm4py/Dockerfile.a2a -t yawl-pm4py-a2a scripts/pm4py/
docker run -d -p 9092:9092 --name pm4py-a2a yawl-pm4py-a2a
```

---

## Part C — Wire MCP Tools (Unblock PM_UNAVAILABLE)

By default, YAWL's 5 MCP process mining tools return:
> "Process mining requires the pm4py Python bridge which is not available in this
> deployment."

To wire real implementations, pass a configured `ProcessMiningFacade` to
`YawlProcessMiningToolSpecifications`. This wiring happens in `YawlMcpServer`
configuration:

```java
// Construct facade (connects to YAWL engine and Rust4PM)
var facade = new ProcessMiningFacade(
    System.getenv("YAWL_ENGINE_URL"),
    System.getenv("YAWL_ADMIN_USER"),
    System.getenv("YAWL_ADMIN_PASS")
);

// Pass facade when registering MCP tools
var pmTools = new YawlProcessMiningToolSpecifications(
    System.getenv("YAWL_ENGINE_URL"),
    System.getenv("YAWL_ADMIN_USER"),
    System.getenv("YAWL_ADMIN_PASS")
);
// Register pmTools.createAll() with your McpServer instance
```

Once wired, invoke a tool via any MCP client:

```bash
# Using Claude Code's MCP client
mcp call yawl_pm_performance '{"specIdentifier": "OrderProcess"}'
```

---

## Part D — Export OCEL 2.0 from YAWL

The `ProcessMiningFacade` runs the full pipeline in one call:

```java
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;

// Initialize
var facade = new ProcessMiningFacade(engineUrl, username, password);
var specId  = new YSpecificationID("OrderProcess", "1.0", "OrderProcess");

// Run full pipeline: XES export → OCEL 2.0 → performance → conformance → variants
var report  = facade.analyze(specId, net, /* withData= */ true);

// Access results (all fields on the immutable Java 25 record)
String xesXml    = report.xesXml();    // XES for ProM, pm4py, legacy tools
String ocelJson  = report.ocelJson();  // OCEL 2.0 JSON for OCPM tools
int    cases     = report.traceCount();
double fitness   = report.conformance().fitness();
double precision = report.conformance().precision();
double avgFlowMs = report.performance().avgFlowTimeMs();
```

### Performance-only (no conformance, faster)

```java
var report = facade.analyzePerformance(specId, /* withData= */ false);
```

---

## Part E — Analyse with pm4py (Python)

Once you have an OCEL 2.0 JSON export, standard pm4py calls apply:

```python
import pm4py
import tempfile

# Option A: from file saved by YAWL
ocel = pm4py.read_ocel2("yawl-export.jsonocel")

# Option B: from JSON string returned by facade
with tempfile.NamedTemporaryFile(suffix=".jsonocel", mode="w", delete=False) as f:
    f.write(ocel_json_str)
    ocel = pm4py.read_ocel2(f.name)

# Object types available in YAWL's OCEL 2.0
print(pm4py.ocel.ocel_get_object_types(ocel))
# → ['case', 'task', 'specification', 'resource']

# Discover Object-Centric DFG
ocdfg = pm4py.discover_ocdfg(ocel)
pm4py.view_ocdfg(ocdfg, annotation="frequency")

# Discover Object-Centric Petri Net
ocpn = pm4py.discover_oc_petri_net(ocel)
pm4py.view_ocpn(ocpn)

# Filter to specific object types
filtered = pm4py.filter_ocel_object_attribute(
    ocel, "ocel:type", ["case", "task"], positive=True
)
ocdfg_filtered = pm4py.discover_ocdfg(filtered)
```

---

## Part F — High-Performance Import with rustxes

For large logs (> 100K events), use `rustxes` (Python bindings to Rust4PM's parser):

```bash
pip install rustxes
```

```python
import rustxes

# OCEL 2.0 JSON — 10-40× faster than pm4py's pure-Python importer
ocel = rustxes.read_ocel2_json("yawl-export.jsonocel")

# OCEL 2.0 XML — also supported
ocel = rustxes.read_ocel2_xml("yawl-export.xmlocel")

# XES — for traditional case-centric analysis
log = rustxes.read_xes("yawl-export.xes")
```

The `ocel` object returned by `rustxes` is compatible with pm4py's OCEL API for
downstream discovery and conformance calls.

---

## Part G — Direct Rust Usage

For high-throughput pipelines or custom Rust tooling:

```toml
# Cargo.toml
[dependencies]
process_mining = { version = "0.8", features = ["ocel"] }
```

```rust
use process_mining::ocel::{import_ocel_json_file, import_ocel_json_slice};
use std::fs;

// From file
let ocel = import_ocel_json_file("yawl-export.jsonocel")?;

// Or from bytes (e.g., from HTTP response body)
let bytes: Vec<u8> = fs::read("yawl-export.jsonocel")?;
let ocel = import_ocel_json_slice(&bytes)?;

println!(
    "{} events across {} objects ({} object types)",
    ocel.events.len(),
    ocel.objects.len(),
    ocel.object_types.len()
);
```

---

## Part H — GregverseSimulator (Prescriptive Loop)

The `GregverseSimulator` runs the full prescriptive feedback loop against a live
YAWL specification:

```java
import org.yawlfoundation.yawl.integration.processmining.GregverseSimulator;

var simulator = new GregverseSimulator(engineUrl, username, password);

// Simulate 100 synthetic cases + run discovery + conformance + feedback
var result = simulator.simulate(
    "OrderProcess",          // specId
    100,                     // synthetic case count
    List.of("Submit", "Approve", "Process", "Deliver")  // activities
);

System.out.println(result.feedback());
// e.g. "Fitness 0.72 below threshold 0.80 — review Approve→Process transition"
```

The feedback is structured for consumption by an MCP-connected LLM agent.

---

## Troubleshooting

### "PM_UNAVAILABLE" from MCP tools

The MCP tools are stubbed until `ProcessMiningFacade` is wired into
`YawlProcessMiningToolSpecifications`. Ensure `YAWL_ENGINE_URL`,
`YAWL_ADMIN_USER`, and `YAWL_ADMIN_PASS` are set and the facade is passed during
MCP server initialization.

### `RUST4PM_SERVER_URL` not set

`Rust4PmClient.fromEnvironment()` throws `IllegalStateException` if the env var
is absent. Set it to the URL of your Rust4PM server (default: `http://localhost:8000`).

### pm4py A2A agent port 9092 already in use

```bash
export PM4PY_A2A_PORT=9093
python scripts/pm4py/a2a_agent.py
```

### OCEL 2.0 export returns empty objects

Ensure Interface E is enabled and at least one case has completed tasks. The
`Ocel2Exporter` only includes work items with at least one `completed` or `failed`
lifecycle event.

### XES export hangs on large log

Use `facade.analyzePerformance(specId, false)` (no data attributes) for faster
export. For streaming on very large logs, implement `YAWLEventStream` with a
`Ocel2StreamExporter` to process events in batches.

---

## Verification Checklist

- [ ] `curl http://localhost:8000/api/v1/health` returns `{"status":"ok"}`
- [ ] `python scripts/pm4py/mcp_server.py` starts without errors
- [ ] `curl http://localhost:9092/.well-known/agent.json` returns agent card
- [ ] `facade.analyze(specId, net, false).ocelJson()` returns valid JSON
- [ ] `pm4py.read_ocel2(path)` loads the exported OCEL 2.0 without errors
- [ ] `pm4py.discover_ocdfg(ocel)` produces a non-empty graph
- [ ] MCP `yawl_pm_performance` returns real metrics (not `PM_UNAVAILABLE`)

---

## Further Reading

- [Object-Centric Process Mining](../../explanation/object-centric-process-mining.md)
- [Process Intelligence](../../explanation/process-intelligence.md)
- [MCP Process Mining Tools Reference](../../reference/mcp-process-mining-tools.md)
- [Process Mining Enhancement Plan](../../explanation/process-mining.md)
- [Kubernetes Deployment](../deployment/production.md)
