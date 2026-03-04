# YAWL Process Mining Bridge API - Complete Interface Specification

## Overview

This document defines the complete API surface for the Erlang ↔ Rust4pm bridge, which enables process mining capabilities in YAWL using Rust-native implementations from the `process_mining` crate (RWTH Aachen).

## Architecture Overview

```
┌─────────────────┐    Erlang    ┌─────────────────┐    Rust    ┌─────────────────┐
│                 │──────NIF─────▶                 │◀─NIF──────▶                 │
│                │               │                │            │                │
│  YAWL          │               │  process_mining │            │  process_mining │
│  Erlang        │               │  NIF Bridge     │            │  Engine         │
│  Process       │               │  (nif.rs)       │            │  (RWTH)         │
│  Mining Bridge │               │                │            │                │
│                │               │                │            │                │
└─────────────────┘               └─────────────────┘            └─────────────────┘
```

---

## 1. Erlang API - process_mining_bridge.erl

### 1.1 Module Interface

```erlang
-module(process_mining_bridge).
-behaviour(gen_server).
-export([
    start_link/0,
    stop/0,
    % XES Import/Export
    import_xes/1,
    export_xes/2,
    % OCEL Import
    import_ocel_json/1,
    import_ocel_xml/1,
    import_ocel_sqlite/1,
    % Process Discovery
    discover_dfg/1,
    discover_alpha/1,
    discover_oc_dfg/1,
    % Petri Net Operations
    import_pnml/1,
    export_pnml/1,
    % Conformance Checking
    token_replay/2,
    % Event Log Statistics
    event_log_stats/1,
    % Memory Management
    free_handle/1
]).
```

### 1.2 Available Functions

#### Lifecycle
- `start_link() -> {ok, Pid} | {error, Reason}`
- `stop() -> ok | {error, Reason}`

#### XES Import/Export
- `import_xes(Path :: string()) -> {ok, Handle} | {error, Reason}`
- `export_xes(Handle :: reference(), Path :: string()) -> ok | {error, Reason}`

#### OCEL Import
- `import_ocel_json(Path :: string()) -> {ok, Handle} | {error, Reason}`
- `import_ocel_xml(Path :: string()) -> {ok, Handle} | {error, Reason}`
- `import_ocel_sqlite(Path :: string()) -> {ok, Handle} | {error, Reason}`

#### Process Discovery
- `discover_dfg(LogHandle :: reference()) -> {ok, DfgJson :: string()} | {error, Reason}`
- `discover_alpha(LogHandle :: reference()) -> {ok, NetHandle :: reference()} | {error, Reason}`
- `discover_oc_dfg(LogHandle :: reference()) -> {ok, DfgJson :: string()} | {error, Reason}`

#### Petri Net Operations
- `import_pnml(Path :: string()) -> {ok, NetHandle :: reference()} | {error, Reason}`
- `export_pnml(NetHandle :: reference()) -> {ok, PnmlXml :: string()} | {error, Reason}`

#### Conformance Checking
- `token_replay(LogHandle :: reference(), NetHandle :: reference()) -> {ok, ConformanceReport :: map()} | {error, Reason}`

#### Statistics
- `event_log_stats(LogHandle :: reference()) -> {ok, Stats :: map()} | {error, Reason}`

#### Memory Management
- `free_handle(Handle :: reference()) -> ok`

---

## 2. Rust NIF API - nif.rs

### 2.1 Resource Types

```rust
// Resource wrappers for data structures
pub struct EventLogResource {
    pub log: Mutex<process_mining::EventLog>,
}

pub struct PetriNetResource {
    pub net: Mutex<process_mining::PetriNet>,
}

pub struct OcelResource {
    pub ocel: Mutex<process_mining::OCEL>,
}
```

### 2.2 Available NIF Functions

#### XES Import/Export
```rust
#[rustler::nif]
pub fn import_xes(env: Env<'_>, path: String) -> NifResult<Term<'_>>
#[rustler::nif]
pub fn export_xes(env: Env<'_>, log_resource: ResourceArc<EventLogResource>, path: String) -> NifResult<Term<'_>>
```

#### OCEL Import/Export
```rust
#[rustler::nif]
pub fn import_ocel_json(env: Env<'_>, path: String) -> NifResult<Term<'_>>
#[rustler::nif]
pub fn export_ocel_json(env: Env<'_>, ocel_resource: ResourceArc<OcelResource>, path: String) -> NifResult<Term<'_>>
```

#### Process Discovery
```rust
#[rustler::nif]
pub fn discover_dfg(env: Env<'_>, log_resource: ResourceArc<EventLogResource>) -> NifResult<Term<'_>>
#[rustler::nif]
pub fn discover_alpha(env: Env<'_>, log_resource: ResourceArc<EventLogResource>) -> NifResult<Term<'_>>
```

#### Petri Net Operations
```rust
#[rustler::nif]
pub fn export_pnml(env: Env<'_>, net_resource: ResourceArc<PetriNetResource>) -> NifResult<Term<'_>>
```

#### Statistics
```rust
#[rustler::nif]
pub fn event_log_stats(env: Env<'_>, log_resource: ResourceArc<EventLogResource>) -> NifResult<Term<'_>>
```

#### Resource Management
```rust
#[rustler::nif]
pub fn free_event_log(env: Env<'_>, resource: ResourceArc<EventLogResource>) -> NifResult<Term<'_>>
#[rustler::nif]
pub fn free_petri_net(env: Env<'_>, resource: ResourceArc<PetriNetResource>) -> NifResult<Term<'_>>
#[rustler::nif]
pub fn free_ocel(env: Env<'_>, resource: ResourceArc<OcelResource>) -> NifResult<Term<'_>>
```

---

## 3. Type Mappings (Erlang ↔ Rust)

### 3.1 Basic Types

| Erlang | Rust | Notes |
|--------|------|-------|
| `string()` | `String` | UTF-8 encoded |
| `integer()` | `i64` | 64-bit integers |
| `float()` | `f64` | 64-bit floats |
| `binary()` | `Vec<u8>` | Binary data |
| `reference()` | `ResourceArc<T>` | Resource handle |
| `map()` | `HashMap<String, Value>` | Key-value pairs |
| `list()` | `Vec<Value>` | Array/list |
| `boolean()` | `bool` | True/False |

### 3.2 Process Mining Specific Types

#### Event Log Handle
```erlang
Erlang: reference()  % Opaque handle from import operations
Rust: ResourceArc<EventLogResource>  % Managed resource
```

#### DFG (Directly-Follows Graph)
```erlang
Erlang: binary()  % JSON string representation
Rust: String  % JSON serialized DFG
```

#### Petri Net Handle
```erlang
Erlang: reference()  % Opaque handle from discovery/import
Rust: ResourceArc<PetriNetResource>  % Managed resource
```

#### Statistics Response
```erlang
Erlang: map()  % Proplist-style tuple
Rust: HashMap<String, Value>  % Statistics dictionary
```

### 3.3 JSON Schema for Complex Types

#### DFG JSON Structure
```json
{
    "nodes": [
        {
            "id": "string",
            "label": "string",
            "count": 123
        }
    ],
    "edges": [
        {
            "source": "string",
            "target": "string",
            "count": 123
        }
    ]
}
```

#### Event Log Statistics
```json
{
    "traces": 42,
    "events": 1234,
    "activities": 15,
    "avg_events_per_trace": 29.38
}
```

---

## 4. Error Handling Conventions

### 4.1 Error Response Format

```erlang
% Success response
{ok, Value}

% Error response
{error, Reason}
```

### 4.2 Error Types

| Error Type | Description | Example |
|------------|-------------|---------|
| `{error, nif_not_loaded}` | NIF library not loaded | `{error, nif_not_loaded}` |
| `{error, file_not_found}` | File path doesn't exist | `{error, "file_not_found"}` |
| `{error, invalid_format}` | File format is invalid | `{error, "invalid_xes_format"}` |
| `{error, parse_error}` | JSON parsing failed | `{error, "JSON parse error"}` |
| `{error, discovery_failed}` | Process discovery failed | `{error, "alpha_discovery_failed"}` |
| `{error, conformance_failed}` | Conformance check failed | `{error, "token_replay_failed"}` |
| `{error, handle_invalid}` | Handle is invalid/closed | `{error, "handle_invalid"}` |
| `{error, memory_error}` | Memory allocation failed | `{error, "out_of_memory"}` |

### 4.3 Error Handling Pattern

```erlang
case process_mining_bridge:import_xes("/path/to/log.xes") of
    {ok, LogHandle} ->
        % Success - use the handle
        case process_mining_bridge:discover_dfg(LogHandle) of
            {ok, DfgJson} ->
                % Process DFG
                handle_dfg(DfgJson);
            {error, Reason} ->
                % Handle discovery error
                handle_error(Reason)
        end;
    {error, Reason} ->
        % Handle import error
        handle_error(Reason)
end.
```

---

## 5. Usage Examples

### 5.1 Basic Workflow: Import → Discover → Export

```erlang
%% Step 1: Import XES event log
case process_mining_bridge:import_xes("/path/to/event_log.xes") of
    {ok, LogHandle} ->
        %% Step 2: Discover DFG
        case process_mining_bridge:discover_dfg(LogHandle) of
            {ok, DfgJson} ->
                %% Step 3: Export to file
                case process_mining_bridge:export_xes(LogHandle, "/path/to/output.xes") of
                    ok ->
                        io:format("Process mining completed successfully~n"),
                        cleanup_handles([LogHandle]);
                    {error, ExportError} ->
                        handle_error(ExportError)
                end;
            {error, DiscoveryError} ->
                handle_error(DiscoveryError)
        end;
    {error, ImportError} ->
        handle_error(ImportError)
end.

%% Clean up handles
cleanup_handles(Handles) ->
    lists:foreach(fun process_mining_bridge:free_handle/1, Handles).
```

### 5.2 Alpha Miner Workflow

```erlang
%% Import event log
case process_mining_bridge:import_xes("/path/to/log.xes") of
    {ok, LogHandle} ->
        %% Discover Petri Net using Alpha+++
        case process_mining_bridge:discover_alpha(LogHandle) of
            {ok, NetHandle} ->
                %% Export to PNML
                case process_mining_bridge:export_pnml(NetHandle) of
                    {ok, PnmlXml} ->
                        %% Save PNML file
                        file:write_file("/path/to/net.pnml", PnmlXml),
                        cleanup_handles([LogHandle, NetHandle]);
                    {error, ExportError} ->
                        handle_error(ExportError)
                end;
            {error, AlphaError} ->
                handle_error(AlphaError)
        end;
    {error, ImportError} ->
        handle_error(ImportError)
end.
```

### 5.3 OCEL Processing

```erlang
%% Import OCEL from JSON
case process_mining_bridge:import_ocel_json("/path/to/ocel.json") of
    {ok, OcelHandle} ->
        %% Get statistics
        case process_mining_bridge:event_log_stats(OcelHandle) of
            {ok, Stats} ->
                io:format("Traces: ~p~n", [maps:get(traces, Stats)]),
                io:format("Events: ~p~n", [maps:get(events, Stats)]),
                cleanup_handles([OcelHandle]);
            {error, StatsError} ->
                handle_error(StatsError)
        end;
    {error, ImportError} ->
        handle_error(ImportError)
end.
```

### 5.4 Complete Example (from pm_example.erl)

```erlang
%% Run complete process mining workflow
run_complete() ->
    XesPath = "/path/to/log.xes",

    %% Import XES
    case process_mining_bridge:import_xes(XesPath) of
        {ok, #{handle := LogHandle}} ->
            %% Get statistics
            case process_mining_bridge:event_log_stats(LogHandle) of
                {ok, Stats} ->
                    %% Discover DFG
                    case process_mining_bridge:discover_dfg(LogHandle) of
                        {ok, DfgJson} ->
                            %% Discover Alpha++ Petri Net
                            case process_mining_bridge:discover_alpha(LogHandle) of
                                {ok, #{pnml := Pnml, handle := NetHandle}} ->
                                    %% Conformance check
                                    case process_mining_bridge:token_replay(LogHandle, NetHandle) of
                                        {ok, Conformance} ->
                                            %% Return all results
                                            {ok, #{
                                                dfg => DfgJson,
                                                pnml => Pnml,
                                                conformance => Conformance,
                                                stats => Stats
                                            }};
                                        {error, ConformanceError} ->
                                            {error, {conformance_failed, ConformanceError}}
                                    end;
                                {ok, #{pnml := Pnml}} ->
                                    {ok, #{
                                        dfg => DfgJson,
                                        pnml => Pnml,
                                        stats => Stats
                                    }};
                                {error, AlphaError} ->
                                    {error, {alpha_failed, AlphaError}}
                            end;
                        {error, DfgError} ->
                            {error, {dfg_failed, DfgError}}
                    end;
                {error, StatsError} ->
                    {error, {stats_failed, StatsError}}
            end;
        {error, ImportError} ->
            {error, {import_failed, ImportError}}
    end.
```

---

## 6. Performance Considerations

### 6.1 Resource Management

- **Handles are reference-counted**: Automatic cleanup when no longer referenced
- **Explicit cleanup recommended**: Call `free_handle/1` when done to free memory immediately
- **Virtual thread support**: NIF operations don't block Erlang VM threads

### 6.2 Memory Usage

```erlang
% Large files may consume significant memory
% Monitor memory usage and clean up handles promptly
process_mining_bridge:free_handle(LogHandle),  % Release memory immediately
```

### 6.3 Error Recovery

```erlang
% Implement retry logic for transient errors
import_with_retry(Path, Retries) when Retries > 0 ->
    case process_mining_bridge:import_xes(Path) of
        {ok, Handle} -> {ok, Handle};
        {error, Reason} when Retries > 0 ->
            timer:sleep(1000),  % Wait 1 second
            import_with_retry(Path, Retries - 1);
        {error, Reason} -> {error, Reason}
    end.
```

---

## 7. Configuration and Deployment

### 7.1 NIF Library Loading

```erlang
% The NIF library is automatically loaded when the process_mining_bridge starts
% Ensure the library is in the priv directory:
% priv/yawl_process_mining.so (Unix/Linux)
% priv/yawl_process_mining.dll (Windows)
```

### 7.2 Application Dependencies

```erlang
% Required applications (defined in .app file)
{applications, [
    sasl,     % For system logging
    crypto,   % For cryptographic operations
    mnesia    % For handle registry
]}.
```

---

## 8. Testing and Validation

### 8.1 Unit Testing Pattern

```erlang
-ifdef(TEST).
-include_lib("eunit/include/eunit.hrl").

import_test() ->
    % Test with a sample XES file
    SampleFile = "test/data/sample.xes",
    case process_mining_bridge:import_xes(SampleFile) of
        {ok, Handle} ->
            ?assert(is_reference(Handle)),
            process_mining_bridge:free_handle(Handle),
            ok;
        {error, _} = Error ->
            Error
    end.

event_log_stats_test() ->
    % Test statistics calculation
    case process_mining_bridge:import_xes("test/data/sample.xes") of
        {ok, Handle} ->
            {ok, Stats} = process_mining_bridge:event_log_stats(Handle),
            ?assert(is_integer(maps:get(traces, Stats))),
            ?assert(is_integer(maps:get(events, Stats))),
            process_mining_bridge:free_handle(Handle),
            ok;
        {error, _} = Error ->
            Error
    end.
-endif.
```

### 8.2 Integration Testing

```erlang
% Complete workflow integration test
integration_test() ->
    XesPath = "test/integration/workflow.xes",

    % Run complete workflow
    case pm_example:run_complete(XesPath) of
        {ok, Result} ->
            ?assert(maps:is_key(dfg, Result)),
            ?assert(maps:is_key(pnml, Result)),
            ?assert(maps:is_key(conformance, Result)),
            ok;
        {error, _} = Error ->
            Error
    end.
```

---

## 9. Best Practices

### 9.1 Error Handling

- Always check `{error, Reason}` responses
- Implement meaningful error messages for end users
- Use try-catch for synchronous operations

### 9.2 Resource Management

- Clean up handles when no longer needed
- Monitor memory usage for large event logs
- Implement timeout mechanisms for long-running operations

### 9.3 Performance Optimization

- Use batch operations for multiple files
- Cache results when appropriate
- Consider parallel processing for independent operations

---

## 10. Future Enhancements

### 10.1 Planned Features

- Additional discovery algorithms (Heuristic Miner, Inductive Miner)
- Performance analytics and timing metrics
- Real-time event log processing
- Distributed processing capabilities

### 10.2 API Extensions

- Configuration parameters for discovery algorithms
- Custom conformance checking metrics
- Integration with YAWL engine for runtime process mining

---

## Appendix A: Quick Reference

### A.1 Function Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `start_link()` | `{ok, Pid}` | Start the bridge |
| `import_xes(Path)` | `{ok, Handle}` | Import XES file |
| `export_xes(Handle, Path)` | `ok` | Export XES file |
| `discover_dfg(Handle)` | `{ok, Json}` | Discover DFG |
| `discover_alpha(Handle)` | `{ok, Handle}` | Discover Alpha++ Net |
| `token_replay(Log, Net)` | `{ok, Report}` | Conformance check |
| `event_log_stats(Handle)` | `{ok, Stats}` | Get log statistics |
| `free_handle(Handle)` | `ok` | Free resource |

### A.2 Error Codes

| Code | Description |
|------|-------------|
| `nif_not_loaded` | NIF library not available |
| `file_not_found` | File path invalid |
| `invalid_format` | File format error |
| `parse_error` | JSON parsing failed |
| `discovery_failed` | Process discovery failed |
| `conformance_failed` | Conformance check failed |