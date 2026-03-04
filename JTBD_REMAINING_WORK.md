# YAWL JTBD Tests - Remaining Work

**Status**: IN PROGRESS
**Last Updated**: 2026-03-03T17:25:00Z

---

## Executive Summary

The JTBD (Jobs To Be Done) tests reveal a **critical gap**: the Rust NIF library only implements benchmark functions, not the actual process mining operations needed for the tests.

---

## Precondition Results ✅

| # | Test | Status | Output |
|---|------|--------|--------|
| 1 | BEAM Bridge Alive | ✅ PASS | `BRIDGE_ALIVE` - nop() and int_passthrough(42) work |
| 2 | Input Files Valid | ✅ PASS | All 3 JSON files valid |
| 3 | Output Directory Clean | ✅ PASS | `/tmp/jtbd/output/` empty |

---

## Critical Gap: Missing NIF Functions

### Current State of `nif.rs`

The Rust NIF at `yawl-rust4pm/rust4pm/src/nif/nif.rs` only exports:

```rust
rustler::init!("process_mining_bridge", [
    nop,                      // ✅ exists
    int_passthrough,          // ✅ exists
    atom_passthrough,         // ✅ exists
    small_list_passthrough,   // ✅ exists
    tuple_passthrough,        // ✅ exists
    echo_json,                // ✅ exists
    echo_term,                // ✅ exists
    echo_binary,              // ✅ exists
    echo_ocel_event,          // ✅ exists
    large_list_transfer,      // ✅ exists
], load = load);
```

### Missing Functions Required for JTBD

| Function | JTBD | Status | Priority |
|----------|------|--------|----------|
| `import_ocel_json_path/1` | 1,2,3,4,5 | ❌ MISSING | P0 |
| `discover_dfg/1` | 1 | ❌ MISSING | P0 |
| `discover_petri_net/1` | 2,4 | ❌ MISSING | P0 |
| `token_replay/2` | 2,4 | ❌ MISSING | P0 |
| `slim_link_ocel/1` | 3 | ❌ MISSING | P1 |
| `discover_oc_declare/1` | 3 | ❌ MISSING | P1 |

---

## JTBD Test Results

### JTBD 1: DFG Discovery
**Status**: ❌ FAIL
**Gap**: `import_ocel_json_path` returns `{error, nif_not_loaded}`
**Required Fix**: Implement NIF function

### JTBD 2: Conformance Scoring
**Status**: ❌ BLOCKED
**Dependency**: Requires JTBD 1 to pass
**Gap**: `discover_petri_net`, `token_replay` not implemented

### JTBD 3: OC-DECLARE Constraints
**Status**: ❌ FAIL
**Gap**: `discover_oc_declare` returns `{error, nif_not_loaded}`
**Required Fix**: Implement NIF function

### JTBD 4: Loop Accumulation
**Status**: ⚠️ PARTIAL
**Issue**: Uses fallback mock scores (0.5, 0.7) - NOT computed by Rust
**Required Fix**: Implement real `token_replay` with actual computation

### JTBD 5: Fault Isolation
**Status**: ❌ FAIL
**Issue**: gen_server crashes due to recursive call in `handle_call`
**Gap**: `import_ocel_json_path` missing causes crash path

---

## Implementation Tasks

### Phase 1: Core NIF Functions (P0)

#### Task 1.1: Implement `import_ocel_json_path`
**File**: `yawl-rust4pm/rust4pm/src/nif/nif.rs`

```rust
use std::collections::HashMap;
use std::sync::Mutex;
use uuid::Uuid;

lazy_static::lazy_static! {
    static ref OCEL_REGISTRY: Mutex<HashMap<String, OcelLog>> = Mutex::new(HashMap::new());
}

#[rustler::nif]
pub fn import_ocel_json_path(path: String) -> NifResult<String> {
    let json_str = std::fs::read_to_string(&path)
        .map_err(|e| rustler::Error::Term(Box::new(format!("IO Error: {}", e))))?;

    let ocel: OcelLog = serde_json::from_str(&json_str)
        .map_err(|e| rustler::Error::Term(Box::new(format!("Parse Error: {}", e))))?;

    let uuid = Uuid::new_v4().to_string();
    OCEL_REGISTRY.lock().unwrap().insert(uuid.clone(), ocel);

    Ok(uuid)
}
```

#### Task 1.2: Implement `discover_dfg`
**File**: `yawl-rust4pm/rust4pm/src/nif/nif.rs`

```rust
#[derive(Serialize)]
struct DfgNode {
    id: String,
}

#[derive(Serialize)]
struct DfgEdge {
    source: String,
    target: String,
    frequency: usize,
}

#[derive(Serialize)]
struct DfgResult {
    nodes: Vec<DfgNode>,
    edges: Vec<DfgEdge>,
}

#[rustler::nif]
pub fn discover_dfg(ocel_id: String) -> NifResult<String> {
    let registry = OCEL_REGISTRY.lock().unwrap();
    let ocel = registry.get(&ocel_id)
        .ok_or_else(|| rustler::Error::Term(Box::new("OCEL not found")))?;

    // Compute DFG: count directly-follows relationships
    let mut dfg: HashMap<(String, String), usize> = HashMap::new();
    let mut activities: HashSet<String> = HashSet::new();

    // Group events by object and sort by timestamp
    let mut traces: HashMap<String, Vec<_>> = HashMap::new();
    for event in &ocel.events {
        activities.insert(event.activity.clone());
        for obj in &event.objects {
            traces.entry(obj.id.clone()).or_default().push(event);
        }
    }

    // Sort each trace and count DFG edges
    for (_, mut events) in traces {
        events.sort_by_key(|e| e.timestamp);
        for window in events.windows(2) {
            let src = &window[0].activity;
            let tgt = &window[1].activity;
            *dfg.entry((src.clone(), tgt.clone())).or_insert(0) += 1;
        }
    }

    let result = DfgResult {
        nodes: activities.into_iter().map(|a| DfgNode { id: a }).collect(),
        edges: dfg.into_iter().map(|((src, tgt), freq)| DfgEdge {
            source: src,
            target: tgt,
            frequency: freq,
        }).collect(),
    };

    serde_json::to_string(&result)
        .map_err(|e| rustler::Error::Term(Box::new(format!("JSON Error: {}", e))))
}
```

#### Task 1.3: Implement `discover_petri_net`
**File**: `yawl-rust4pm/rust4pm/src/nif/nif.rs`

```rust
lazy_static::lazy_static! {
    static ref PETRI_NET_REGISTRY: Mutex<HashMap<String, PetriNet>> = Mutex::new(HashMap::new());
}

#[rustler::nif]
pub fn discover_petri_net(ocel_id: String) -> NifResult<String> {
    let ocel_registry = OCEL_REGISTRY.lock().unwrap();
    let ocel = ocel_registry.get(&ocel_id)
        .ok_or_else(|| rustler::Error::Term(Box::new("OCEL not found")))?;

    // Alpha miner algorithm (simplified)
    let pn = alpha_miner::discover(ocel)?;

    let uuid = Uuid::new_v4().to_string();
    PETRI_NET_REGISTRY.lock().unwrap().insert(uuid.clone(), pn);

    Ok(uuid)
}
```

#### Task 1.4: Implement `token_replay`
**File**: `yawl-rust4pm/rust4pm/src/nif/nif.rs`

```rust
#[rustler::nif]
pub fn token_replay<'a>(env: Env<'a>, ocel_id: String, petri_net_id: String) -> NifResult<Term<'a>> {
    let ocel_registry = OCEL_REGISTRY.lock().unwrap();
    let ocel = ocel_registry.get(&ocel_id)
        .ok_or_else(|| rustler::Error::Term(Box::new("OCEL not found")))?;

    let pn_registry = PETRI_NET_REGISTRY.lock().unwrap();
    let pn = pn_registry.get(&petri_net_id)
        .ok_or_else(|| rustler::Error::Term(Box::new("Petri net not found")))?;

    // Real token replay algorithm
    let (produced, consumed, missing, remaining) = replay_all_traces(ocel, pn);

    // Compute conformance score
    let fitness = if produced > 0 && consumed > 0 {
        0.5 * (consumed as f64 / produced as f64) +
        0.5 * (1.0 - missing as f64 / (consumed + missing) as f64)
    } else {
        0.0
    };

    // Return as Erlang map
    Ok(rustler::Term::map_from_pairs(env, &[
        (atoms::conformance_score(), fitness.encode(env)),
        (atoms::fitness(), fitness.encode(env)),
        (atoms::produced_tokens(), produced.encode(env)),
        (atoms::consumed_tokens(), consumed.encode(env)),
        (atoms::missing_tokens(), missing.encode(env)),
    ])?)
}
```

### Phase 2: Advanced Functions (P1)

#### Task 2.1: Implement `slim_link_ocel`
Create optimized OCEL representation for constraint discovery.

#### Task 2.2: Implement `discover_oc_declare`
Discover Declare-style constraints from OCEL.

### Phase 3: Bug Fixes

#### Task 3.1: Fix Recursive Call in `handle_call`
**File**: `yawl-erlang-bridge/yawl-erlang-bridge/src/process_mining_bridge.erl`

The `handle_call` clauses call the same function recursively:

```erlang
%% BUG: This calls itself recursively
handle_call({import_ocel_json, Path}, _From, State) ->
    case import_ocel_json(Path) of  %% <-- calls itself!
        ...
```

**Fix**: Call the NIF stub directly or restructure to avoid recursion.

---

## Build Commands

```bash
# Build Rust NIF
cd /Users/sac/yawl/yawl-rust4pm/rust4pm
cargo build --release

# Copy to priv
cp target/release/libprocess_mining_bridge.dylib \
   /Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/priv/

# Compile Erlang
cd /Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge
erlc -o ebin src/*.erl

# Test
erl -pa ebin -eval "
  {ok, OcelId} = process_mining_bridge:import_ocel_json_path(<<\"/tmp/jtbd/input/pi-sprint-ocel.json\">>),
  io:format(\"OCEL_ID: ~s~n\", [OcelId]),
  halt(0)." -noshell
```

---

## Success Criteria

The JTBD tests pass when:

1. **JTBD 1**: `OCEL_ID` is a real UUID, `DFG_BYTES > 0`, nodes match input activities
2. **JTBD 2**: `CONFORMANCE_SCORE` is a float in (0.0, 1.0), not 0.0 or 1.0
3. **JTBD 3**: `CONSTRAINT_COUNT >= 1`, activities from input file
4. **JTBD 4**: Two different scores computed from different inputs
5. **JTBD 5**: PIDs identical before/after error

**NO HARDCODING**: All numbers must be computed by Rust, not chosen by developers.

---

## File Locations

| Component | Path |
|-----------|------|
| Rust NIF source | `/Users/sac/yawl/yawl-rust4pm/rust4pm/src/nif/nif.rs` |
| Erlang bridge | `/Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/src/process_mining_bridge.erl` |
| NIF library | `/Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge/priv/` |
| Test inputs | `/tmp/jtbd/input/` |
| Test outputs | `/tmp/jtbd/output/` |

---

## Agent Assignments

| Agent ID | Task | Status |
|----------|------|--------|
| af2f2d1336d93eaf1 | Implement import_ocel_json_path | Running |
| a9f25e5e31737b4a2 | Implement discover_dfg | Running |
| aa75e77740ea91f0b | Implement token_replay | Running |
| a6996aa0d87fb7541 | Run JTBD 1 test | Running |
| a18b68c89d0f83664 | Run JTBD 2 test | Running |
| acc9e5d56682c9cde | Run JTBD 3 test | Running |
| a9f9eedf6914ed6b9 | Run JTBD 4 test | Running |
| af6e0def48e8a6719 | Run JTBD 5 test | Running |
| ab57a5ca929d36197 | Verify no hardcoding | Running |
| a7e1af87f9580818d | Generate final report | Running |

---

## Next Actions

1. Wait for implementation agents to complete
2. Rebuild Rust NIF with new functions
3. Re-run all JTBD tests
4. Verify scores are computed (not hardcoded)
5. Generate final passing report

---

**GODSPEED.** ✈️
