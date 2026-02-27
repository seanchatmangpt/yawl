# YAWL Build Sequences — Complete Reference

**19 modules | 7 dependency layers | FM7 poka-yoke**

This document is the canonical reference for building any subset or all of the YAWL Maven
modules in the correct order. It covers the full dependency DAG, parallel build layers,
per-module sequence diagrams, minimal build paths, and the corrected `dx.sh` ordering.

---

## §1 — Full Dependency Flowchart

```mermaid
graph TD
    subgraph L0["Layer 0 — Foundation (parallel)"]
        U[yawl-utilities]
        SEC[yawl-security]
        GPY[yawl-graalpy]
        GJS[yawl-graaljs]
    end

    subgraph L1["Layer 1 — First consumers (parallel)"]
        EL[yawl-elements]
        GG[yawl-ggen]
        GW[yawl-graalwasm]
    end

    subgraph L2["Layer 2 — Core engine"]
        EN[yawl-engine]
    end

    subgraph L3["Layer 3 — Engine extensions"]
        SL[yawl-stateless]
    end

    subgraph L4["Layer 4 — Services (parallel)"]
        AU[yawl-authentication]
        SC[yawl-scheduling]
        MO[yawl-monitoring]
        WK[yawl-worklet]
        CP[yawl-control-panel]
        IN[yawl-integration]
        WA[yawl-webapps]
    end

    subgraph L5["Layer 5 — Advanced services (parallel)"]
        PI[yawl-pi]
        RS[yawl-resourcing]
    end

    subgraph L6["Layer 6 — Top-level application"]
        MCP[yawl-mcp-a2a-app]
    end

    U --> EL
    GPY --> GG
    GJS --> GW

    EL --> EN

    U --> SL
    EL --> SL
    EN --> SL

    EN --> AU
    EN --> SC
    EN --> MO
    SL --> MO
    SL --> WK
    EN --> CP
    EN --> IN
    SL --> IN
    GG --> IN

    EN --> PI
    EL --> PI
    IN --> PI

    EN --> RS
    SL --> RS
    IN --> RS

    IN --> MCP
    PI --> MCP
    EN --> MCP
    SL --> MCP
    EL --> MCP
    U --> MCP
    GG --> MCP

    style L0 fill:#f0f4ff,stroke:#6b7db3
    style L1 fill:#f0f8ff,stroke:#5b8fa8
    style L2 fill:#fff8e1,stroke:#f5a623
    style L3 fill:#fff3e0,stroke:#e67e22
    style L4 fill:#f1f8e9,stroke:#558b2f
    style L5 fill:#fce4ec,stroke:#c62828
    style L6 fill:#ede7f6,stroke:#4527a0
```

---

## §2 — Parallel Build Layers

Modules within the same layer have no dependency on each other and can compile concurrently.
Maven's `-T 1.5C` flag exploits this automatically.

| Layer | Modules | Parallel? | Bottleneck |
|-------|---------|-----------|-----------|
| **0** | yawl-utilities, yawl-security, yawl-graalpy, yawl-graaljs | ✓ All 4 | None |
| **1** | yawl-elements, yawl-ggen, yawl-graalwasm | ✓ All 3 | Waits for layer 0 |
| **2** | yawl-engine | — (single) | Waits for yawl-elements |
| **3** | yawl-stateless | — (single) | Waits for layer 2 |
| **4** | yawl-authentication, yawl-scheduling, yawl-monitoring, yawl-worklet, yawl-control-panel, yawl-integration, yawl-webapps | ✓ All 7 | Waits for layer 3 (and yawl-ggen for integration) |
| **5** | yawl-pi, yawl-resourcing | ✓ Both | Waits for yawl-integration |
| **6** | yawl-mcp-a2a-app | — (single) | Waits for layer 5 |

**Key insight**: The critical path is:
`utilities → elements → engine → stateless → integration → pi → mcp-a2a-app` (7 hops)

---

## §3 — Sequence Diagrams (19 modules)

Each diagram shows the exact build chain required before a module can compile.

### yawl-utilities (Layer 0)

```mermaid
sequenceDiagram
    Note over yawl-utilities: No dependencies — builds immediately
    yawl-utilities->>yawl-utilities: compile ✓
```

### yawl-security (Layer 0)

```mermaid
sequenceDiagram
    Note over yawl-security: No dependencies — builds immediately
    yawl-security->>yawl-security: compile ✓
```

### yawl-graalpy (Layer 0)

```mermaid
sequenceDiagram
    Note over yawl-graalpy: No dependencies — builds immediately
    yawl-graalpy->>yawl-graalpy: compile ✓
```

### yawl-graaljs (Layer 0) ← NEW

```mermaid
sequenceDiagram
    Note over yawl-graaljs: No YAWL dependencies — GraalVM Polyglot + GraalJS only
    yawl-graaljs->>yawl-graaljs: compile ✓
```

### yawl-elements (Layer 1)

```mermaid
sequenceDiagram
    participant U as yawl-utilities
    participant E as yawl-elements

    U->>U: compile ✓
    U->>E: provides utilities
    E->>E: compile ✓
```

### yawl-ggen (Layer 1)

```mermaid
sequenceDiagram
    participant GPY as yawl-graalpy
    participant GG as yawl-ggen

    GPY->>GPY: compile ✓
    GPY->>GG: provides GraalPy runtime
    GG->>GG: compile ✓ (RDF+SPARQL+Tera code generation)
```

### yawl-graalwasm (Layer 1) ← NEW

```mermaid
sequenceDiagram
    participant GJS as yawl-graaljs
    participant GW as yawl-graalwasm

    GJS->>GJS: compile ✓ (GraalJS context pool)
    GJS->>GW: provides JS+WASM polyglot context for Rust4pmBridge
    GW->>GW: compile ✓ (WasmExecutionEngine, Rust4pmBridge, OCEL2)
```

### yawl-engine (Layer 2)

```mermaid
sequenceDiagram
    participant U as yawl-utilities
    participant E as yawl-elements
    participant EN as yawl-engine

    U->>U: compile ✓
    U->>E: provides utilities
    E->>E: compile ✓
    E->>EN: provides YNet, YTask, YSpec domain model
    EN->>EN: compile ✓ (YEngine, YNetRunner, persistence)
```

### yawl-stateless (Layer 3)

```mermaid
sequenceDiagram
    participant U as yawl-utilities
    participant E as yawl-elements
    participant EN as yawl-engine
    participant SL as yawl-stateless

    U->>U: compile ✓
    U->>E: provides utilities
    E->>E: compile ✓
    E->>EN: provides elements
    EN->>EN: compile ✓
    U->>SL: provides utilities (direct dep)
    E->>SL: provides elements (direct dep)
    EN->>SL: provides engine (direct dep)
    SL->>SL: compile ✓ (event-driven, no persistence)
```

### yawl-authentication (Layer 4)

```mermaid
sequenceDiagram
    participant U as yawl-utilities
    participant E as yawl-elements
    participant EN as yawl-engine
    participant AU as yawl-authentication

    U->>E: compile chain
    E->>EN: compile chain
    EN->>EN: compile ✓
    EN->>AU: provides engine (JWT, CSRF, session management)
    AU->>AU: compile ✓
```

### yawl-scheduling (Layer 4)

```mermaid
sequenceDiagram
    participant U as yawl-utilities
    participant E as yawl-elements
    participant EN as yawl-engine
    participant SC as yawl-scheduling

    U->>E: compile chain
    E->>EN: compile chain
    EN->>EN: compile ✓
    EN->>SC: provides engine (timer management)
    SC->>SC: compile ✓
```

### yawl-monitoring (Layer 4)

```mermaid
sequenceDiagram
    participant U as yawl-utilities
    participant E as yawl-elements
    participant EN as yawl-engine
    participant SL as yawl-stateless
    participant MO as yawl-monitoring

    U->>E: compile chain
    E->>EN: compile chain
    EN->>SL: compile chain
    EN->>MO: provides engine (OpenTelemetry, Prometheus)
    SL->>MO: provides stateless engine
    MO->>MO: compile ✓
```

### yawl-worklet (Layer 4)

```mermaid
sequenceDiagram
    participant U as yawl-utilities
    participant E as yawl-elements
    participant EN as yawl-engine
    participant SL as yawl-stateless
    participant WK as yawl-worklet

    U->>E: compile chain
    E->>EN: compile chain
    EN->>SL: compile chain
    SL->>WK: provides stateless (RDR routing)
    WK->>WK: compile ✓ (uses shared ../src tree)
```

### yawl-control-panel (Layer 4)

```mermaid
sequenceDiagram
    participant U as yawl-utilities
    participant E as yawl-elements
    participant EN as yawl-engine
    participant CP as yawl-control-panel

    U->>E: compile chain
    E->>EN: compile chain
    EN->>EN: compile ✓
    EN->>CP: provides engine (Swing desktop UI)
    CP->>CP: compile ✓
```

### yawl-integration (Layer 4)

```mermaid
sequenceDiagram
    participant GPY as yawl-graalpy
    participant GG as yawl-ggen
    participant U as yawl-utilities
    participant E as yawl-elements
    participant EN as yawl-engine
    participant SL as yawl-stateless
    participant IN as yawl-integration

    Note over GPY,GG: Parallel with main engine chain ↓
    GPY->>GG: provides GraalPy
    GG->>GG: compile ✓

    U->>E: compile chain
    E->>EN: compile chain
    EN->>SL: compile chain

    EN->>IN: provides engine
    SL->>IN: provides stateless
    GG->>IN: provides code generation (RDF+SPARQL)
    IN->>IN: compile ✓ (MCP, A2A, external connectors)
```

### yawl-webapps (Layer 4 — aggregator)

```mermaid
sequenceDiagram
    Note over yawl-webapps: Aggregator POM — no module dependencies
    Note over yawl-webapps: Contains yawl-engine-webapp WAR submodule
    yawl-webapps->>yawl-webapps: compile ✓ (can build at any point)
```

### yawl-pi (Layer 5)

```mermaid
sequenceDiagram
    participant GPY as yawl-graalpy
    participant GG as yawl-ggen
    participant U as yawl-utilities
    participant E as yawl-elements
    participant EN as yawl-engine
    participant SL as yawl-stateless
    participant IN as yawl-integration
    participant PI as yawl-pi

    GPY->>GG: provides GraalPy
    U->>E: compile chain
    E->>EN: compile chain
    EN->>SL: compile chain
    EN->>IN: compile chain
    SL->>IN: compile chain
    GG->>IN: compile chain
    IN->>IN: compile ✓

    EN->>PI: provides engine
    E->>PI: provides elements (Petri net model)
    IN->>PI: provides integration (MCP connectors)
    PI->>PI: compile ✓ (Predictive AI, ONNX Runtime, OCEL2)

    Note over PI: EXCLUDED when CLAUDE_CODE_REMOTE=true (89MB onnxruntime)
```

### yawl-resourcing (Layer 5)

```mermaid
sequenceDiagram
    participant GPY as yawl-graalpy
    participant GG as yawl-ggen
    participant U as yawl-utilities
    participant E as yawl-elements
    participant EN as yawl-engine
    participant SL as yawl-stateless
    participant IN as yawl-integration
    participant RS as yawl-resourcing

    GPY->>GG: provides GraalPy
    U->>E: compile chain
    E->>EN: compile chain
    EN->>SL: compile chain
    EN->>IN: compile chain
    SL->>IN: compile chain
    GG->>IN: compile chain
    IN->>IN: compile ✓

    EN->>RS: provides engine
    SL->>RS: provides stateless
    IN->>RS: provides integration
    RS->>RS: compile ✓ (allocators, work queues, participants)
```

### yawl-mcp-a2a-app (Layer 6)

```mermaid
sequenceDiagram
    participant GPY as yawl-graalpy
    participant GG as yawl-ggen
    participant U as yawl-utilities
    participant E as yawl-elements
    participant EN as yawl-engine
    participant SL as yawl-stateless
    participant IN as yawl-integration
    participant PI as yawl-pi
    participant MCP as yawl-mcp-a2a-app

    Note over GPY,GG: Graal chain (parallel with engine chain)
    GPY->>GG: compile chain ✓

    Note over U,SL: Engine chain
    U->>E: compile chain
    E->>EN: compile chain
    EN->>SL: compile chain

    Note over IN: Integration (needs engine+stateless+ggen)
    EN->>IN: engine dep
    SL->>IN: stateless dep
    GG->>IN: ggen dep
    IN->>IN: compile ✓

    Note over PI: PI (needs engine+integration+elements)
    EN->>PI: engine dep
    E->>PI: elements dep
    IN->>PI: integration dep
    PI->>PI: compile ✓

    IN->>MCP: integration dep
    PI->>MCP: pi dep
    EN->>MCP: engine dep
    SL->>MCP: stateless dep
    E->>MCP: elements dep
    U->>MCP: utilities dep
    GG->>MCP: ggen dep
    MCP->>MCP: compile ✓ (Spring Boot MCP+A2A server)

    Note over MCP: Most connected module — 7 direct YAWL deps
    Note over MCP: EXCLUDED when CLAUDE_CODE_REMOTE=true (via yawl-pi)
```

---

## §4 — Canonical Topological Sort

The definitive 19-module build order where every dependency is satisfied before its consumer.

| # | Module | Layer | Depends On (YAWL modules only) |
|---|--------|-------|--------------------------------|
| 1 | **yawl-utilities** | 0 | — |
| 2 | **yawl-security** | 0 | — |
| 3 | **yawl-graalpy** | 0 | — |
| 4 | **yawl-graaljs** | 0 | — ← NEW |
| 5 | **yawl-elements** | 1 | yawl-utilities |
| 6 | **yawl-ggen** | 1 | yawl-graalpy |
| 7 | **yawl-graalwasm** | 1 | yawl-graaljs ← NEW |
| 8 | **yawl-engine** | 2 | yawl-elements |
| 9 | **yawl-stateless** | 3 | yawl-utilities, yawl-elements, yawl-engine |
| 10 | **yawl-authentication** | 4 | yawl-engine |
| 11 | **yawl-scheduling** | 4 | yawl-engine |
| 12 | **yawl-monitoring** | 4 | yawl-engine, yawl-stateless |
| 13 | **yawl-worklet** | 4 | yawl-stateless |
| 14 | **yawl-control-panel** | 4 | yawl-engine |
| 15 | **yawl-integration** | 4 | yawl-engine, yawl-stateless, yawl-ggen |
| 16 | **yawl-webapps** | 4* | — (aggregator, independent) |
| 17 | **yawl-pi** | 5 | yawl-engine, yawl-integration, yawl-elements |
| 18 | **yawl-resourcing** | 5 | yawl-engine, yawl-stateless, yawl-integration |
| 19 | **yawl-mcp-a2a-app** | 6 | yawl-integration, yawl-pi, yawl-engine, yawl-stateless, yawl-elements, yawl-utilities, yawl-ggen |

> `yawl-webapps` is an aggregator POM (no module deps) and can compile at any layer ≥ 0.

---

## §5 — Minimal Build Paths (for `-pl` targeting)

Use these when running `mvn -pl <modules> -am compile` or `bash scripts/dx.sh -pl <modules>`.
The `-am` (also-make) flag tells Maven to also build upstream dependencies automatically,
so the minimal path shown is the **transitive closure** for that module.

| Target Module | Minimal `-pl` module list | Count |
|---------------|--------------------------|-------|
| yawl-utilities | `yawl-utilities` | 1 |
| yawl-security | `yawl-security` | 1 |
| yawl-graalpy | `yawl-graalpy` | 1 |
| yawl-graaljs | `yawl-graaljs` | 1 |
| yawl-elements | `yawl-utilities,yawl-elements` | 2 |
| yawl-ggen | `yawl-graalpy,yawl-ggen` | 2 |
| yawl-graalwasm | `yawl-graaljs,yawl-graalwasm` | 2 |
| yawl-engine | `yawl-utilities,yawl-elements,yawl-engine` | 3 |
| yawl-stateless | `yawl-utilities,yawl-elements,yawl-engine,yawl-stateless` | 4 |
| yawl-authentication | `yawl-utilities,yawl-elements,yawl-engine,yawl-authentication` | 4 |
| yawl-scheduling | `yawl-utilities,yawl-elements,yawl-engine,yawl-scheduling` | 4 |
| yawl-control-panel | `yawl-utilities,yawl-elements,yawl-engine,yawl-control-panel` | 4 |
| yawl-worklet | `yawl-utilities,yawl-elements,yawl-engine,yawl-stateless,yawl-worklet` | 5 |
| yawl-monitoring | `yawl-utilities,yawl-elements,yawl-engine,yawl-stateless,yawl-monitoring` | 5 |
| yawl-integration | `yawl-graalpy,yawl-ggen,yawl-utilities,yawl-elements,yawl-engine,yawl-stateless,yawl-integration` | 7 |
| yawl-pi | `yawl-graalpy,yawl-ggen,yawl-utilities,yawl-elements,yawl-engine,yawl-stateless,yawl-integration,yawl-pi` | 8 |
| yawl-resourcing | `yawl-graalpy,yawl-ggen,yawl-utilities,yawl-elements,yawl-engine,yawl-stateless,yawl-integration,yawl-resourcing` | 8 |
| yawl-webapps | `yawl-webapps` | 1 (aggregator) |
| yawl-mcp-a2a-app | All 19 modules (transitively) | 19 |

**Example commands:**
```bash
# Build just the engine and its deps
bash scripts/dx.sh -pl yawl-utilities,yawl-elements,yawl-engine

# Build integration and everything it needs
mvn -pl yawl-graalpy,yawl-ggen,yawl-utilities,yawl-elements,yawl-engine,yawl-stateless,yawl-integration -T 1.5C clean compile

# Use Maven's -am flag to auto-resolve deps (simpler)
mvn -pl yawl-integration -am -T 1.5C clean compile
```

---

## §6 — dx.sh Ordering (corrected)

The `scripts/dx.sh` `ALL_MODULES` array was **incorrect** — it placed `yawl-authentication`
before `yawl-engine`, but authentication depends on engine. The new modules `yawl-graaljs`
and `yawl-graalwasm` were also missing.

```bash
# BEFORE (wrong — authentication before engine, missing graaljs/graalwasm):
ALL_MODULES=(
    yawl-utilities yawl-elements yawl-authentication yawl-engine
    yawl-stateless yawl-resourcing yawl-scheduling
    yawl-security yawl-integration yawl-monitoring yawl-ggen yawl-pi yawl-webapps
    yawl-control-panel yawl-mcp-a2a-app
)

# AFTER (correct — topological order, all 19 modules):
ALL_MODULES=(
    yawl-utilities yawl-security yawl-graalpy yawl-graaljs
    yawl-elements yawl-ggen yawl-graalwasm
    yawl-engine
    yawl-stateless
    yawl-authentication yawl-scheduling yawl-monitoring
    yawl-worklet yawl-control-panel yawl-integration yawl-webapps
    yawl-pi yawl-resourcing
    yawl-mcp-a2a-app
)
```

---

## §7 — Build Profiles by Scenario

| Scenario | Command | Approx. Time |
|----------|---------|-------------|
| Fast check (changed modules only) | `bash scripts/dx.sh` | 5–15s |
| Compile only (changed) | `bash scripts/dx.sh compile` | 3–5s |
| Test only (changed) | `bash scripts/dx.sh test` | 10–15s |
| Full build + test (all) | `bash scripts/dx.sh all` | 45–60s |
| Single module compile | `bash scripts/dx.sh -pl yawl-engine` | 5–10s |
| Unit tests only | `mvn -P quick-test clean test -T 1.5C` | ~10s |
| CI build (with coverage) | `mvn -P ci clean verify -T 1.5C` | 2–3m |
| Full static analysis | `mvn -P analysis clean verify -T 1.5C` | 5m+ |
| Security scan (SBOM) | `mvn -P security verify` | 3m+ |
| Production validation | `mvn -P prod verify` | 3m+ |
| Pre-commit gate | `bash scripts/dx.sh all` | 45–60s |

**Environment variables:**

| Variable | Values | Effect |
|----------|--------|--------|
| `CLAUDE_CODE_REMOTE=true` | true/false | Excludes yawl-pi + yawl-mcp-a2a-app (89MB onnxruntime) |
| `DX_VERBOSE=1` | 0/1 | Full Maven output |
| `DX_OFFLINE=1` | 1/0/auto | Force offline build |
| `DX_FAIL_AT=end` | fast/end | Continue or stop on first failure |
| `DX_CLEAN=1` | 0/1 | Run `clean` phase before build |

---

## §8 — Release Gate Sequence

From `scripts/validation/validate-release.sh` (PY-1, PY-2, PY-3, FM13):

```
G_compile ──→ G_test ──→ G_guard ──→ G_analysis ──→ G_security ──→ G_documentation
   │              │          │              │                │
  <90s       100% pass   0 H-patterns   0 SpotBugs      SBOM+Grype
  reactor                (14 patterns)  0 PMD          no critical CVEs
  order                  hyper-         0 Checkstyle
  correct                validate.sh    ≥75% JaCoCo
                                              │
                              ───────────────────────────────────
                              │ PY-1: gate receipts (≤24h old) │
                              │   gate-G_guard-receipt.json     │
                              │   gate-G_test-receipt.json      │
                              │   gate-G_security-receipt.json  │
                              │ PY-2: JaCoCo ≥55% instruction  │
                              │ PY-3: grype --fail-on critical  │
                              │ PY-4: 48h stability receipt     │
                              └───────────────────────────────┘
                                              │
                                         G_release ✓
```

**Run release gate validation:**
```bash
bash scripts/validation/validate-release.sh           # all gates
bash scripts/validation/validate-release.sh receipts  # PY-1 only
bash scripts/validation/validate-release.sh coverage  # PY-2 only
bash scripts/validation/validate-release.sh sbom      # PY-3 only
bash scripts/validation/validate-release.sh stability # FM13 stability receipt
```

---

## §9 — Source Strategy Reference

YAWL modules use two source strategies. This matters for `-pl` targeting — some modules
share a common `../src` tree and scope to their package subtree via `<includes>`.

| Strategy | Modules | Source Root |
|----------|---------|-------------|
| **package_scoped** (shared `../src`) | yawl-graaljs, yawl-graalwasm, yawl-graalpy, yawl-worklet, yawl-authentication, yawl-scheduling, yawl-security, yawl-monitoring, yawl-control-panel | `../src` (scoped by package filter) |
| **standard** | yawl-utilities, yawl-elements, yawl-engine, yawl-stateless, yawl-resourcing, yawl-integration, yawl-ggen, yawl-pi, yawl-mcp-a2a-app | `src/main/java` |
| **aggregator** | yawl-webapps | pom only, no Java source |

> When modifying shared source files under `src/org/yawlfoundation/yawl/`, check
> `docs/v6/diagrams/facts/modules.txt` to identify which modules include that package.

---

*Generated 2026-02-27 | YAWL v6.0.0-GA | FM7 mitigation (RPN=105)*
