# YAWL Erlang Bridge - Process Mining Integration

This document describes the process mining integration capabilities of the YAWL Erlang Bridge, including the Mnesia registry for Rust object handle persistence.

## Overview

The YAWL Erlang Bridge provides a robust integration between YAWL workflow engine and Rust-based process mining capabilities. The bridge manages Rust object handles through a persistent Mnesia registry, ensuring that Rust objects remain valid across Erlang process restarts.

## Mnesia Registry

The Mnesia registry is responsible for managing Rust object handles for:
- OCEL (Object-Centric Event Log) objects
- Slim OCEL objects
- Petri Net objects
- Conformance checking results

### Tables Structure

The registry creates four Mnesia tables:

#### 1. `ocel_registry`
```erlang
-record(ocel_registry, {
    ocel_id :: term(),           % OCEL identifier
    uuid :: binary(),            % Unique identifier
    rust_pointer :: binary(),     % Rust object pointer
    timestamp :: integer()        % Registration timestamp
}).
```

#### 2. `slim_ocel_registry`
```erlang
-record(slim_ocel_registry, {
    slim_ocel_id :: term(),       % Slim OCEL identifier
    uuid :: binary(),            % Unique identifier
    rust_pointer :: binary(),     % Rust object pointer
    parent_ocel_id :: term(),     % Parent OCEL ID
    timestamp :: integer()        % Registration timestamp
}).
```

#### 3. `petri_net_registry`
```erlang
-record(petri_net_registry, {
    petri_net_id :: term(),       % Petri net identifier
    uuid :: binary(),            % Unique identifier
    rust_pointer :: binary(),     % Rust object pointer
    timestamp :: integer()        % Registration timestamp
}).
```

#### 4. `conformance_registry`
```erlang
-record(conformance_registry, {
    conformance_id :: term(),     % Conformance check identifier
    uuid :: binary(),            % Unique identifier
    rust_pointer :: binary(),     % Rust object pointer
    timestamp :: integer()        % Registration timestamp
}).
```

## API Reference

### OCEL Registry Functions

#### `register_ocel(OcelId, RustPointer) -> ok | {error, Reason}`
Registers an OCEL object with the given Rust pointer.

```erlang
OcelId = my_ocel_id,
RustPointer = <<"0x7f8a3c0d4e5f8">>,
case mnesia_registry:register_ocel(OcelId, RustPointer) of
    ok -> lager:info("OCEL registered successfully");
    {error, Reason} -> lager:error("Failed to register OCEL: ~p", [Reason])
end
```

#### `lookup_ocel(OcelId) -> {ok, RustPointer} | {error, not_found}`
Looks up the Rust pointer for a registered OCEL object.

```erlang
case mnesia_registry:lookup_ocel(OcelId) of
    {ok, RustPointer} ->
        %% Use the Rust pointer for operations
        rust_operations:process_pointer(RustPointer);
    {error, not_found} ->
        lager:warning("OCEL not found: ~p", [OcelId])
end
```

#### `unregister_ocel(OcelId) -> ok`
Removes an OCEL object from the registry.

```erlang
mnesia_registry:unregister_ocel(OcelId)
```

### Slim OCEL Registry Functions

#### `register_slim_ocel(SlimOcelId, RustPointer) -> ok | {error, Reason}`
Registers a Slim OCEL object.

```erlang
SilmOcelId = {parent_id, slim_id},
RustPointer = <<"0x7f8a3c0d4e5f9">>,
mnesia_registry:register_slim_ocel(SlimOcelId, RustPointer)
```

#### `lookup_slim_ocel(SlimOcelId) -> {ok, RustPointer} | {error, not_found}`
Looks up a Slim OCEL object.

#### `unregister_slim_ocel(SlimOcelId) -> ok`
Removes a Slim OCEL object.

### Petri Net Registry Functions

#### `register_petri_net(PetriNetId, RustPointer) -> ok | {error, Reason}`
Registers a Petri net object.

```erl
PetriNetId = "process_definition_1",
RustPointer = <<"0x7f8a3c0d4e5fa">>,
mnesia_registry:register_petri_net(PetriNetId, RustPointer)
```

#### `lookup_petri_net(PetriNetId) -> {ok, RustPointer} | {error, not_found}`
Looks up a Petri net object.

#### `unregister_petri_net(PetriNetId) -> ok`
Removes a Petri net object.

### Conformance Registry Functions

#### `register_conformance(ConformanceId, RustPointer) -> ok | {error, Reason}`
Registers a conformance checking result.

```erl
ConformanceId = {"case_123", "process_def"},
RustPointer = <<"0x7f8a3c0d4e5fb">>,
mnesia_registry:register_conformance(ConformanceId, RustPointer)
```

#### `lookup_conformance(ConformanceId) -> {ok, RustPointer} | {error, not_found}`
Looks up a conformance checking result.

#### `unregister_conformance(ConformanceId) -> ok`
Removes a conformance checking result.

### Utility Functions

#### `clear_stale_entries() -> ok`
Removes registry entries older than 24 hours. This should be called periodically to clean up stale entries.

```erlang
mnesia_registry:clear_stale_entries()
```

#### `get_registry_stats() -> {ok, Stats}`
Retrieves registry statistics.

```erlang
{ok, Stats} = mnesia_registry:get_registry_stats(),
lager:info("Registry stats: ~p", [Stats])
```

The Stats map contains:
- `operations`: Total number of operations
- `ocel_count`: Number of registered OCEL objects
- `slim_ocel_count`: Number of registered Slim OCEL objects
- `petri_net_count`: Number of registered Petri nets
- `conformance_count`: Number of registered conformance results
- `errors`: Number of errors encountered
- `backup_size`: Size of backup files

## Persistence and Recovery

### gen_server Restart
The Mnesia registry is designed to survive gen_server restarts. When the registry restarts:
1. Mnesia tables are automatically reloaded from disk
2. All registered Rust pointers remain valid
3. No data loss occurs during normal operation

### Supervisor Restart
On supervisor restart, the registry will clear stale entries to prevent memory leaks. This is done automatically during initialization.

### Backup and Recovery
The registry performs automatic backups every 5 minutes to `/tmp/yawl_bridge_mnesia_backup/`. These backups can be used to recover from catastrophic failures.

## Performance Considerations

- **mnesia:dirty_write/3**: Used for write operations for maximum performance
- **mnesia:dirty_read/2**: Used for read operations for maximum performance
- **Table Type**: All tables use `set` type to prevent duplicates
- **Indexing**: Timestamps are indexed for efficient cleanup operations

## Error Handling

The API functions return `ok` on success or `{error, Reason}` on failure. Common error reasons include:
- `{error, not_found}`: Object not found in registry
- `{error, Reason}`: Mnesia operation failed with Reason

## Dependencies

The Mnesia registry depends on:
- `mnesia`: Erlang's distributed database
- `uuid`: For generating unique identifiers
- `lager`: For logging

## Testing

Run the test suite with:
```bash
rebar3 eunit
```

The test suite includes:
- Supervisor start/stop tests
- Registry API function tests
- Mnesia table creation tests
- Error condition tests