# YAWL Process Mining Bridge - API Reference

## Table of Contents
1. [Module Overview](#module-overview)
2. [Core Functions](#core-functions)
3. [XES Operations](#xes-operations)
4. [OCEL Operations](#ocel-operations)
5. [Process Discovery](#process-discovery)
6. [Petri Net Operations](#petri-net-operations)
7. [Conformance Checking](#conformance-checking)
8. [Statistics](#statistics)
9. [Memory Management](#memory-management)
10. [Error Handling](#error-handling)

## Module Overview

```erlang
-module(process_mining_bridge).
-behaviour(gen_server).
```

The `process_mining_bridge` module provides Erlang access to the Rust process_mining crate via NIF (Native Implemented Functions). It implements the Three-Domain Native Bridge Pattern:

```
Erlang (BEAM) ←→ NIF ←→ Rust (rust4pm) ←→ process_mining (RWTH Aachen)
```

### Key Features
- XES 1.0 import/export
- OCEL JSON/XML/SQLite support
- Process discovery (DFG, Alpha+++)
- Petri net operations
- Conformance checking
- Event log statistics
- Automatic resource management

## Core Functions

### Lifecycle Management

#### `start_link() -> {ok, Pid} | {error, Reason}`
Starts the gen_server process.

**Return Values:**
- `{ok, Pid}` - Process started successfully
- `{error, Reason}` - Failed to start

**Example:**
```erlang
{ok, Pid} = process_mining_bridge:start_link().
```

#### `stop() -> ok | {error, Reason}`
Stops the gen_server process gracefully.

**Return Values:**
- `ok` - Stopped successfully
- `{error, Reason}` - Failed to stop

**Example:**
```erlang
ok = process_mining_bridge:stop().
```

#### `ping() -> pong | pang | pong`
Checks if the NIF library is loaded and responsive.

**Return Values:**
- `pong` - NIF is loaded and working
- `pang` - NIF is not loaded
- Other - Error

**Example:**
```erlang
case process_mining_bridge:ping() of
    pong -> io:format("NIF is loaded~n");
    pang -> io:format("NIF not loaded~n")
end.
```

#### `get_nif_status() -> Status`
Gets the current NIF library status.

**Return Values:**
- `loaded` - NIF library is loaded
- `not_found` - NIF file not found
- `error` - Other error

**Example:**
```erlang
case process_mining_bridge:get_nif_status() of
    loaded -> ok;
    not_found -> io:format("Missing NIF file~n");
    error -> io:format("Error loading NIF~n")
end.
```

## XES Operations

### `import_xes(Path :: string()) -> {ok, Handle} | {error, Reason}`
Imports a XES event log from file path.

**Parameters:**
- `Path` - Path to XES file (string)

**Return Values:**
- `{ok, Handle}` - Success, Handle is a reference()
- `{error, Reason}` - Error

**Error Types:**
- `{error, nif_not_loaded}` - NIF library not loaded
- `{error, file_not_found}` - File doesn't exist
- `{error, invalid_format}` - Invalid XES format
- `{error, parse_error}` - XML parsing error

**Example:**
```erlang
case process_mining_bridge:import_xes("/path/to/log.xes") of
    {ok, Handle} ->
        % Use the handle
        process_mining_bridge:free_handle(Handle);
    {error, Reason} ->
        handle_error(Reason)
end.
```

### `export_xes(Handle :: reference(), Path :: string()) -> ok | {error, Reason}`
Exports a XES event log to file.

**Parameters:**
- `Handle` - Event log handle (reference())
- `Path` - Output file path (string)

**Return Values:**
- `ok` - Success
- `{error, Reason}` - Error

**Example:**
```erlang
case process_mining_bridge:export_xes(LogHandle, "/path/to/output.xes") of
    ok -> io:format("Export successful~n");
    {error, Reason} -> handle_error(Reason)
end.
```

## OCEL Operations

### `import_ocel_json(Path :: string()) -> {ok, Handle} | {error, Reason}`
Imports an OCEL JSON file.

**Parameters:**
- `Path` - Path to OCEL JSON file (string)

**Return Values:**
- `{ok, Handle}` - Success
- `{error, Reason}` - Error

**Example:**
```erlang
case process_mining_bridge:import_ocel_json("/path/to/ocel.json") of
    {ok, Handle} ->
        % Process OCEL data
        process_mining_bridge:free_handle(Handle);
    {error, Reason} ->
        handle_error(Reason)
end.
```

### `import_ocel_xml(Path :: string()) -> {ok, Handle} | {error, Reason}`
Imports an OCEL XML file.

**Note**: Currently returns `{error, "import_ocel_xml: OCEL XML import not yet implemented"}`

### `import_ocel_sqlite(Path :: string()) -> {ok, Handle} | {error, Reason}`
Imports an OCEL SQLite database.

**Note**: Currently returns `{error, "import_ocel_sqlite: OCEL SQLite import not yet implemented"}`

### `export_ocel_json(Handle :: reference(), Path :: string()) -> ok | {error, Reason}`
Exports an OCEL to JSON file.

**Parameters:**
- `Handle` - OCEL handle (reference())
- `Path` - Output file path (string)

**Return Values:**
- `ok` - Success
- `{error, Reason}` - Error

## Process Discovery

### `discover_dfg(Handle :: reference()) -> {ok, DfgJson :: binary()} | {error, Reason}`
Discovers a Directly-Follows Graph from an event log.

**Parameters:**
- `Handle` - Event log handle (reference())

**Return Values:**
- `{ok, DfgJson}` - Success, DfgJson is a JSON binary()
- `{error, Reason}` - Error

**DFG JSON Format:**
```json
{
    "nodes": [
        {
            "id": "activity_A",
            "label": "Activity A",
            "count": 42
        }
    ],
    "edges": [
        {
            "source": "activity_A",
            "target": "activity_B",
            "count": 38
        }
    ]
}
```

**Example:**
```erlang
case process_mining_bridge:discover_dfg(LogHandle) of
    {ok, DfgJson} ->
        % Parse JSON
        {struct, Nodes} = mochijson2:decode(DfgJson),
        process_dfg(Nodes);
    {error, Reason} ->
        handle_error(Reason)
end.
```

### `discover_alpha(Handle :: reference()) -> {ok, Result :: map()} | {error, Result}`
Discovers a Petri net using Alpha+++ algorithm.

**Parameters:**
- `Handle` - Event log handle (reference())

**Return Values:**
- `{ok, #{handle := NetHandle, pnml := PnmlXml}}` - Success
- `{error, Reason}` - Error

**Example:**
```erlang
case process_mining_bridge:discover_alpha(LogHandle) of
    {ok, #{handle := NetHandle, pnml := Pnml}} ->
        % Save PNML
        file:write_file("/path/to/net.pnml", Pnml),
        process_mining_bridge:free_handle(NetHandle);
    {error, Reason} ->
        handle_error(Reason)
end.
```

### `discover_oc_dfg(Handle :: reference()) -> {ok, DfgJson :: binary()} | {error, Reason}`
Discovers an Object-Centric DFG from OCEL.

**Note**: Currently returns `{error, "discover_oc_dfg: Object-Centric DFG not yet implemented"}`

## Petri Net Operations

### `import_pnml(Path :: string()) -> {ok, Handle} | {error, Reason}`
Imports a Petri net from PNML file.

**Note**: Currently returns `{error, "import_pnml: PNML import not yet implemented"}`

### `export_pnml(Handle :: reference()) -> {ok, PnmlXml :: binary()} | {error, Reason}`
Exports a Petri net to PNML format.

**Parameters:**
- `Handle` - Petri net handle (reference())

**Return Values:**
- `{ok, PnmlXml}` - Success, XML binary()
- `{error, Reason}` - Error

**Example:**
```erlang
case process_mining_bridge:export_pnml(NetHandle) of
    {ok, PnmlXml} ->
        file:write_file("/path/to/net.pnml", PnmlXml);
    {error, Reason} ->
        handle_error(Reason)
end.
```

## Conformance Checking

### `token_replay(LogHandle :: reference(), NetHandle :: reference()) -> {ok, Report :: map()} | {error, Reason}`
Runs token replay conformance checking.

**Parameters:**
- `LogHandle` - Event log handle (reference())
- `NetHandle` - Petri net handle (reference())

**Return Values:**
- `{ok, Report}` - Success, conformance report
- `{error, Reason}` - Error

**Note**: Currently returns `{error, "token_replay: Token replay not yet implemented"}`

## Statistics

### `event_log_stats(Handle :: reference()) -> {ok, Stats :: map()} | {error, Reason}`
Gets statistics about an event log.

**Parameters:**
- `Handle` - Event log handle (reference())

**Return Values:**
- `{ok, Stats}` - Success, statistics map
- `{error, Reason}` - Error

**Stats Map Format:**
```erlang
#{
    traces => 42,                     % Number of traces
    events => 1234,                   % Total events
    activities => 15,                  % Unique activities
    avg_events_per_trace => 29.38      % Average events per trace
}
```

**Example:**
```erlang
case process_mining_bridge:event_log_stats(LogHandle) of
    {ok, Stats} ->
        io:format("Traces: ~p, Events: ~p~n", 
            [maps:get(traces, Stats), maps:get(events, Stats)]);
    {error, Reason} ->
        handle_error(Reason)
end.
```

## Memory Management

### `free_handle(Handle :: reference()) -> ok`
Frees a resource handle.

**Parameters:**
- `Handle` - Resource handle to free

**Note**: This is a no-op function as ResourceArc handles automatic garbage collection. Included for compatibility.

**Example:**
```erlang
% Optional: free handle immediately
process_mining_bridge:free_handle(LogHandle).
```

## Error Handling

### Error Response Format

All functions return either:
- `{ok, Value}` - Success
- `{error, Reason}` - Error

### Common Error Types

| Error Type | Description |
|------------|-------------|
| `{error, nif_not_loaded}` | NIF library not loaded |
| `{error, file_not_found}` | File doesn't exist |
| `{error, invalid_format}` | File format invalid |
| `{error, parse_error}` | XML/JSON parsing failed |
| `{error, discovery_failed}` | Process discovery failed |
| `{error, conformance_failed}` | Conformance check failed |
| `{error, handle_invalid}` | Handle is invalid/closed |
| `{error, memory_error}` | Memory allocation failed |
| `{error, not_implemented}` | Feature not yet implemented |

### Error Handling Pattern

```erlang
handle_operation(Operation) ->
    case process_mining_bridge:import_xes("/path/to/log.xes") of
        {ok, LogHandle} ->
            try
                % Perform operations
                {ok, DfgJson} = process_mining_bridge:discover_dfg(LogHandle),
                {ok, Stats} = process_mining_bridge:event_log_stats(LogHandle),
                
                % Return results
                {ok, #{dfg => DfgJson, stats => Stats}}
            catch
                Error:Reason ->
                    {error, {Error, Reason}}
            after
                % Always clean up
                process_mining_bridge:free_handle(LogHandle)
            end;
        {error, nif_not_loaded} ->
            {error, "NIF library not loaded"};
        {error, file_not_found} ->
            {error, "File not found"};
        {error, Reason} ->
            {error, Reason}
    end.
```

## Type Specifications

### Basic Types

```erlang
% File path
-type path() :: string().

% Resource handle
-type handle() :: reference().

% JSON result
-type json_result() :: binary().

% Statistics map
-type stats() :: #{
    traces := non_neg_integer(),
    events := non_neg_integer(),
    activities := non_neg_integer(),
    avg_events_per_trace :: float()
}.

% Error reason
-type error_reason() :: term().
```

### Function Specifications

```erlang
% Import/Export
-spec import_xes(path()) -> {ok, handle()} | {error, error_reason()}.
-spec export_xes(handle(), path()) -> ok | {error, error_reason()}.

% Discovery
-spec discover_dfg(handle()) -> {ok, json_result()} | {error, error_reason()}.
-spec discover_alpha(handle()) -> {ok, #{handle := handle(), pnml :: binary()}} | {error, error_reason()}.

% Statistics
-spec event_log_stats(handle()) -> {ok, stats()} | {error, error_reason()}.
```

## Performance Considerations

### Resource Management
- Handles are reference-counted via ResourceArc
- Automatic garbage collection when no references remain
- Explicit `free_handle/1` is optional but recommended

### Memory Usage
- Large event logs consume significant memory
- Monitor memory usage with `erlang:memory()`
- Process files in batches when possible

### Concurrency
- All NIF functions are thread-safe
- Multiple operations can run concurrently
- Be mindful of resource contention

## Best Practices

1. **Always check error responses**
2. **Clean up handles when done**
3. **Use absolute file paths**
4. **Validate file formats before processing**
5. **Implement retry logic for transient errors**
6. **Monitor memory usage for large files**

## Example: Complete Workflow

```erlang
run_process_miningWorkflow(XesPath) ->
    % Step 1: Import event log
    case process_mining_bridge:import_xes(XesPath) of
        {ok, LogHandle} ->
            try
                % Step 2: Get statistics
                {ok, Stats} = process_mining_bridge:event_log_stats(LogHandle),
                
                % Step 3: Discover DFG
                {ok, DfgJson} = process_mining_bridge:discover_dfg(LogHandle),
                
                % Step 4: Discover Petri net
                {ok, #{handle := NetHandle, pnml := Pnml}} = 
                    process_mining_bridge:discover_alpha(LogHandle),
                
                % Return results
                {ok, #{
                    stats => Stats,
                    dfg => DfgJson,
                    pnml => Pnml
                }}
            catch
                Error:Reason ->
                    {error, {Error, Reason}}
            after
                % Clean up resources
                process_mining_bridge:free_handle(LogHandle),
                process_mining_bridge:free_handle(NetHandle)
            end;
        {error, Reason} ->
            {error, Reason}
    end.
```

## See Also

- `pm_example` - Example module demonstrating full workflows
- [Installation Guide](installation_guide.md)
- [Troubleshooting Guide](troubleshooting_guide.md)

## Version Information

- **Current Version**: 1.0.0
- **YAWL Compatibility**: YAWL v6.0.0
- **Rust Process Mining**: v0.5.2 (RWTH Aachen)
