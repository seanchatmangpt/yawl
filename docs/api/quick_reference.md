# YAWL Process Mining Bridge - Quick Reference Card

## Architecture
```
Erlang (BEAM) ←→ NIF ←→ Rust ←→ process_mining (RWTH Aachen)
```

## Quick Start

### 1. Check Installation
```erlang
% Check NIF status
process_mining_bridge:check_nif_loaded().
process_mining_bridge:ping().
```

### 2. Basic Workflow
```erlang
% Import XES
{ok, LogHandle} = process_mining_bridge:import_xes("log.xes").

% Get stats
{ok, Stats} = process_mining_bridge:event_log_stats(LogHandle).

% Discover DFG
{ok, DfgJson} = process_mining_bridge:discover_dfg(LogHandle).

% Clean up
process_mining_bridge:free_handle(LogHandle).
```

## Core Functions

### Lifecycle
- `start_link()` - Start bridge
- `stop()` - Stop bridge
- `ping()` - Check NIF status
- `get_nif_status()` - Get NIF status

### XES Operations
- `import_xes(Path)` - Import XES file
- `export_xes(Handle, Path)` - Export XES file

### Process Discovery
- `discover_dfg(Handle)` - Discover DFG (JSON)
- `discover_alpha(Handle)` - Discover Alpha++ net

### Statistics
- `event_log_stats(Handle)` - Get log statistics

### Memory
- `free_handle(Handle)` - Free resource (optional)

## Error Handling Pattern

```erlang
case process_mining_bridge:import_xes("file.xes") of
    {ok, Handle} ->
        % Success case
        case process_mining_bridge:discover_dfg(Handle) of
            {ok, Dfg} -> process_dfg(Dfg);
            {error, Reason} -> handle_error(Reason)
        end;
    {error, Reason} ->
        % Error case
        handle_error(Reason)
end.
```

## Data Structures

### Statistics Map
```erlang
#{
    traces => 42,          % Number of traces
    events => 1234,        % Total events
    activities => 15,      % Unique activities
    avg_events_per_trace => 29.38
}
```

### DFG JSON
```json
{
    "nodes": [{"id": "A", "label": "Activity A", "count": 10}],
    "edges": [{"source": "A", "target": "B", "count": 8}]
}
```

## Common Errors

| Error | Meaning |
|-------|---------|
| `nif_not_loaded` | NIF library missing |
| `file_not_found` | File doesn't exist |
| `invalid_format` | Invalid file format |
| `not_implemented` | Feature not available |

## File Locations

- Erlang module: `yawl-erlang-bridge/src/process_mining_bridge.erl`
- Rust NIF: `yawl-rust4pm/rust4pm/src/nif/nif.rs`
- Examples: `yawl-erlang-bridge/examples/pm_example.erl`
- Sample data: `yawl-rust4pm/rust4pm/examples/sample_log.xes`

## Build Commands

```bash
# Build Rust NIF
cd rust4pm && make nif

# Build Erlang app
cd yawl-erlang-bridge && make

# Run tests
make test
```

## Example Usage

### Complete Workflow
```erlang
run_workflow() ->
    case process_mining_bridge:import_xes("sample.xes") of
        {ok, Handle} ->
            {ok, Stats} = process_mining_bridge:event_log_stats(Handle),
            {ok, Dfg} = process_mining_bridge:discover_dfg(Handle),
            {ok, Net} = process_mining_bridge:discover_alpha(Handle),
            {ok, #{stats => Stats, dfg => Dfg, net => Net}};
        {error, Error} ->
            {error, Error}
    end.
```

## Command Line Examples

```bash
# Run example
erl -pa examples -noshell -eval "pm_example:run_complete()."

# Check NIF
erl -pa ebin -noshell -eval "io:format('~p~n', [process_mining_bridge:ping()])."
```

## Version

- Current: 1.0.0
- YAWL: v6.0.0
- Rust Process Mining: v0.5.2
