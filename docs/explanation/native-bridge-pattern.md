# Native Bridge Pattern — Redefined

## YAWL v7 / Self-Play Loop Architecture
## Three Runtime Domains, Two Boundaries, One Isolation Guarantee

---

## The Pattern In One Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    JVM DOMAIN                           │
│                                                         │
│  YAWL Engine          QLever (C++ via Panama FFM)       │
│  GraalPy              Layer 3: QLeverEngine             │
│  TPOT2Bridge          Layer 2: NativeHandle<>           │
│  V7SelfPlayOrch       Layer 1: jextract(qlever_ffi.h)   │
│                            ↕ (in-process, ~10ns)        │
│                       libqlever_ffi.so                  │
│                       [C++ Hourglass façade]            │
└─────────────────────────┬───────────────────────────────┘
                          │
              BOUNDARY A: Unix domain socket
              Erlang distribution protocol
              ei.h → jextract → Layer 2
              Latency: ~5–20µs
              Isolation: full OS process separation
              Fault model: {badrpc, {'EXIT', Reason}}
              Hot reload: transparent to JVM
                          │
┌─────────────────────────┴───────────────────────────────┐
│                   BEAM DOMAIN                           │
│                                                         │
│  process_mining_bridge (gen_server)                     │
│  data_modelling_bridge (gen_server)                     │
│  Mnesia (registry: OcelId → Rust object handle)         │
│  Supervision tree (one_for_one)                         │
│                            ↕ (NIF boundary, ~100ns)     │
│                       rust4pm NIF                       │
│                       [#[no_mangle] pub extern "C" fn]  │
└─────────────────────────────────────────────────────────┘
                   BEAM DOMAIN (cont.)
                   rust4pm runs in BEAM heap
                   segfault → gen_server crash
                   supervisor restarts in <10ms
                   JVM receives structured error term
                   JVM never sees SIGSEGV
```

---

## Why The Previous Pattern Was Wrong

The prior definition described two layers applied uniformly:

```
JVM → Panama FFM → native library
```

**What that pattern got wrong about rust4pm:**

rust4pm is not a computation library. It is a **stateful process mining runtime**.
It retains objects across calls: `OcelId`, `SlimOcelId`, `PetriNetId`, `ConformanceId`.
Those objects live in Rust heap memory, identified by opaque UUIDs. The registry
mapping UUIDs to live Rust objects must survive gen_server restarts. That requires
Mnesia. That requires BEAM.

A direct JVM → Panama FFM → rust4pm path puts the NIF in the JVM process.
A rust4pm segfault becomes a JVM segfault. YAWL stops. QLever stops. GraalPy
stops. The entire self-play loop stops. The audit trail ends mid-iteration.

**The isolation guarantee is not an enhancement. It is the reason the system
can run unattended. Unattended operation is the product.**

**What the pattern also got wrong about QLever:**

QLever is the ontology store. It is queried thousands of times per iteration
by YAWL workflow execution, by TPOT2 fitness evaluation, by DSPy query authoring.
It is a pure query engine: SPARQL string in, result set out.

QLever belongs in the JVM domain because:
- Sub-10ns call latency (in-process Panama FFM, no boundary crossing)
- No objects retained across calls
- No fault isolation needed (QLever's C++ Hourglass façade contains exceptions)
- GC pressure stays out of JVM heap (QLever manages its own column store)

---

## The Two Boundaries Defined Precisely

### Boundary A: JVM ↔ BEAM

| Property | Value |
|----------|-------|
| **Transport** | Erlang distribution protocol over Unix domain socket (`-proto_dist local`) |
| **Why not TCP** | TCP loopback adds 50–200µs. Unix domain sockets are kernel memory copy: ~5–20µs |
| **Why not in-process** | Process separation is the isolation guarantee. Separate address spaces. |
| **Wire format** | Erlang External Term Format (ETF) |
| **Call semantics** | `ei_rpc(cnode, fd, module, function, args_buf, args_len, result_buf)` |
| **Error model** | `{badrpc, {'EXIT', {Reason, Stacktrace}}}` — structured Erlang term |

Layer 2 converts error terms to checked Java exceptions. The JVM never sees a native crash signal.

### Boundary B: BEAM ↔ Rust

| Property | Value |
|----------|-------|
| **Transport** | NIF boundary (`erl_nif.h`). Rust compiled as dynamic library loaded via `erlang:load_nif/2` |
| **Why NIF not port** | Port driver adds process and pipe. NIF runs in BEAM scheduler thread — sub-microsecond overhead |
| **Fault model** | NIF segfault → BEAM scheduler crash → gen_server EXIT → supervisor restart → ready in <10ms |
| **Memory model** | Rust objects in Rust heap. BEAM holds opaque UUID handles. Mnesia maps UUID → live Rust pointer |
| **Hot reload** | BEAM code_server maintains two live module versions. In-flight calls complete against old module |

---

## The Three Runtime Domains

### JVM Domain

**Owns:**
- Workflow orchestration (YAWL)
- Ontology queries (QLever)
- ML optimization (TPOT2, DSPy, GraalPy)
- MCP server
- Loop controller (V7SelfPlayOrchestrator)

**Does NOT own:**
- Capability execution
- Process mining state
- NIF lifecycle

**GC characteristics:** Low heap pressure. QLever's column store never enters JVM heap.
BEAM registry objects are ETF-encoded binaries — copied once at boundary crossing.

**Fault tolerance:** JVM crash is the only unrecoverable event. Acceptable because YAWL,
QLever, and loop controller have no NIF exposure. **There is no path from a rust4pm fault to a JVM crash.**

### BEAM Domain

**Owns:**
- Capability execution (`process_mining_bridge`, `data_modelling_bridge`)
- Capability registry (Mnesia)
- Fault containment (OTP supervision tree)
- Hot reload (`code_server`)
- Capability scheduling (BEAM preemptive scheduler)

**Does NOT own:**
- Ontology queries
- Workflow decisions
- ML optimization

**Scheduler characteristics:** Preemptive with reduction counting. A long rust4pm token
replay call (50–500ms) does not starve other gen_server processes.

**Fault tolerance:** `one_for_one` supervision. gen_server crash → supervised restart →
Mnesia registry re-hydration → ready. Zero impact on JVM domain during restart window.

### Rust Domain

**Owns:**
- Computational execution (token replay, DFG discovery, OC-DECLARE, alpha++ miner)
- OCEL parsing
- PNML export
- Conformance scoring

**Does NOT own:**
- Its own lifecycle (owned by BEAM)
- Its own registry (owned by Mnesia)
- Its own scheduling (preempted by BEAM)

---

## The Layer Structure Per Domain

### JVM Domain — QLever Layers

```
Layer 3: QLeverEngine (pure Java 25)
    ask(String sparql) → boolean
    select(String sparql) → List<Map<String,String>>
    construct(String sparql) → List<Triple>
    update(String turtle) → void

Layer 2: NativeHandle<QLeverEngine> (typed bridge)
    Arena lifecycle, QLeverStatus → QLeverException conversion
    MemorySegment management, result deserialization

Layer 1: jextract(qlever_ffi.h) — never hand-written
    qlever_engine_create, qlever_engine_query,
    qlever_result_get_data, qlever_engine_destroy

Native: libqlever_ffi.so
    extern "C" Hourglass façade over QLever C++ engine/index libraries
    Lippincott pattern: all C++ exceptions → QLeverStatus struct
    No HTTP. No port. In-process function calls.
```

### JVM ↔ BEAM — ei Bridge Layers

```
Layer 3: ProcessMiningClient (pure Java 25)
    importOcel(Path path) → OcelId
    slimLink(OcelId id) → SlimOcelId
    discoverOcDeclare(SlimOcelId id) → List<Constraint>
    tokenReplay(OcelId ocel, PetriNetId pn) → ConformanceResult

Layer 2: ErlangNode (typed bridge)
    ei_rpc → ei_x_buff encode/decode
    ErlTerm sealed interface hierarchy
    Arena-scoped native buffers (confined)
    {badrpc, ...} → checked ErlangException

Layer 1: jextract(ei.h) — never hand-written
    ei_connect_init, ei_rpc, ei_x_new,
    ei_x_encode_*, ei_decode_*

Transport: Unix domain socket (-proto_dist local)
    Erlang distribution protocol
    ETF encoding/decoding
    No EPMD (ei_connect_host_port bypasses it)
```

### BEAM Domain — gen_server → NIF Layers

```
process_mining_bridge (gen_server)
    handle_call({import_ocel_json_path, #{path := P}}, ...) →
        {reply, {ok, OcelId}, State}
    handle_call({slim_link_ocel, #{ocel_id := Id}}, ...) →
        {reply, {ok, SlimId}, State}
    [one handle_call clause per NativeCall triple]
    [generated by ggen from pm-bridge.ttl]

NIF boundary (erl_nif.h)
    rust4pm_parse_ocel2_json(json_ptr, json_len) → ParseResult
    rust4pm_slim_link(ocel_id_ptr) → SlimLinkResult
    [#[no_mangle] pub unsafe extern "C" fn]

Mnesia table: capability_registry
    {ocel_id, UUID, RustPointer, Timestamp}
    {slim_ocel_id, UUID, RustPointer, ParentOcelId, Timestamp}
    {petri_net_id, UUID, RustPointer, Timestamp}
    [survives gen_server restart]
```

---

## Call Pattern Annotation

The `bridge:callPattern` ontology property classifies every NativeCall triple:

```turtle
yawl-bridge:import_ocel_json_path
    a bridge:NativeCall ;
    bridge:callPattern "beam" ;          ← crosses Boundary A, then B
    bridge:registryKind "OcelId" ;       ← retains Rust object, needs Mnesia
    bridge:returnRegistryKind "OcelId" .

yawl-bridge:qlever_ask
    a bridge:NativeCall ;
    bridge:callPattern "jvm" ;           ← in-process Panama FFM only
    bridge:registryKind "inline" ;       ← no retained object
    bridge:returnRegistryKind "boolean" .
```

| callPattern | Path | Latency | Fault isolation | Hot reload |
|-------------|------|---------|----------------|------------|
| `jvm` | JVM → Panama FFM → native library | ~10ns | None (in-process) | No |
| `beam` | JVM → Unix socket → BEAM → NIF | ~5–20µs | Full OS separation | Yes |
| `direct` | JVM → Panama FFM → rust4pm | ~100ns | **None (JVM crash on segfault)** | No |

**`direct` is defined but never used.** It exists as a named escape valve — a
conscious architectural choice that has been evaluated and declined. Its presence
prevents an agent from "discovering" Panama FFM to rust4pm as an optimization.

---

## The Pattern Properties That Make Duplication Hard

### Property 1: No path from the obvious direction

Every engineer who sees "SPARQL" reaches for HTTP. Every engineer who sees
"process mining library" reaches for a JAR dependency. Every engineer who sees
"BEAM" reaches for gRPC or REST.

This pattern uses none of those. The obvious path leads to a completely different
architecture with different performance, fault properties, and hot-reload behavior.

### Property 2: The combination is the moat

Unix domain socket for Erlang distribution: not novel.
Panama FFM for C++ embedding: not novel.
rust4pm as a NIF: not novel.
TPOT2 over SPARQL composition graphs: not novel.
Mnesia as a NIF registry with supervisor integration: not novel.

**The combination of all five** — in a single GraalVM process where QLever, YAWL,
GraalPy, and the ei bridge coexist, where the BEAM supervision tree is the audit
log, where ggen generates both ontology and bridge code from the same type graph —
**is novel. No subset of it is the product.**

### Property 3: The comprehension barrier

An engineer reading this will pattern-match individual components to familiar
things and assemble a simulation that uses HTTP where this uses Unix domain
sockets, REST where this uses ETF, service mesh where this uses OTP distribution.

A competitor cannot build what they cannot accurately hold in their head.

---

## The Pattern Stated Formally

**Name:** Three-Domain Native Bridge

**Intent:** Partition a heterogeneous runtime system into three isolated domains
with precisely characterized boundaries, where each domain owns the capabilities
it is structurally suited for, and no capability crosses more boundaries than
its fault tolerance requirements demand.

**Domains:**
- **JVM:** orchestration, queries, ML optimization (in-process, no fault isolation needed)
- **BEAM:** stateful capability execution (OS-separated, supervised, hot-reloadable)
- **Rust:** computation (NIF, BEAM-contained, sub-millisecond restart)

**Boundaries:**
- **A (JVM ↔ BEAM):** Erlang distribution protocol, Unix domain socket, ETF encoding
- **B (BEAM ↔ Rust):** NIF boundary, shared address space within BEAM process

**Code generation:**
- QLever layer: `jextract(qlever_ffi.h)` → Layer 1; Layer 2+3 hand-written Java 25
- BEAM gen_server: `ggen(pm-bridge.ttl, process_mining_bridge.tera)` → `.erl`
- NIF header: `cbindgen(rust4pm/src/lib.rs)` → `rust4pm.h` → `erl_nif.h` wrapper

### The Invariant The Pattern Enforces

> **A rust4pm fault cannot reach the JVM.**
>
> This is not defended by code.
> It is guaranteed by the OS process boundary between BEAM and JVM.
> The Unix domain socket is the only path between them.
> **A Rust panic or segfault cannot cross a Unix domain socket.**

---

## Module Reference

| Module | Domain | Purpose |
|--------|--------|---------|
| `yawl-qlever` | JVM | QLever embedded SPARQL engine (Panama FFM) |
| `yawl-erlang` | JVM+BEAM | Erlang bridge (ei.h → jextract), gen_servers |
| `yawl-rust4pm` | BEAM+Rust | Process mining NIF (rust4pm) |

## See Also

- [Process Mining Architecture](process-mining-architecture.md)
- [yawl-erlang Module](../../yawl-erlang/)
- [yawl-rust4pm Module](../../yawl-rust4pm/)
- [yawl-qlever Module](../../yawl-qlever/)

---

*The pattern is defined by its boundaries.*
*The boundaries are defined by what they prevent.*
*What they prevent is the system stopping when a capability fails.*
*The system stopping would mean the loop stops.*
*The loop stopping is the only failure mode that matters.*
