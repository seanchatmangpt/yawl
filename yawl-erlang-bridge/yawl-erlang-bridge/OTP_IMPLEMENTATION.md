# OTP Supervisor Tree Implementation for Process Mining Bridges

This document describes the complete OTP (Open Telecom Platform) implementation for the YAWL Erlang Bridge, providing fault tolerance and high availability for process mining operations.

## Architecture Overview

The implementation follows a hierarchical supervision tree with the following structure:

```
process_mining_bridge_app (application)
└── yawl_bridge_sup (supervisor)
    ├── process_mining_bridge (gen_server)
    ├── data_modelling_bridge (gen_server)
    └── mnesia_registry (gen_server)
```

## Key Components

### 1. `yawl_bridge_sup` - Top-Level Supervisor

The supervisor uses a `one_for_one` restart strategy, which means if any child process crashes, it will be restarted independently without affecting other processes.

- **Strategy**: `one_for_one`
- **Intensity**: 5 restarts
- **Period**: 60 seconds
- **Child Processes**: 3 gen_servers

### 2. `process_mining_bridge` - Process Mining Interface

Manages interactions with process mining engines and handles event processing:

- **Event Processing**: Buffer and queue management
- **Metrics Tracking**: Events processed, cases completed, errors
- **Health Monitoring**: Automatic reconnection and recovery
- **Mnesia Integration**: Case and resource event storage

Key API functions:
- `import_ocel(Path)` - Import OCEL data
- `slim_link(OcelId)` - Create slim OCEL representations
- `discover_dfg(SlimOcelId)` - Discover Directly Follows Graphs
- `token_replay(OcelId, PetriNetId)` - Perform token replay analysis
- `free_ocel(OcelId)` - Free OCEL resources

### 3. `data_modelling_bridge` - Data Modelling Interface

Handles data validation, schema management, and rule enforcement:

- **Schema Caching**: In-memory storage of validation schemas
- **Queue Processing**: Asynchronous validation requests
- **Rule Enforcement**: Type checking, required fields, enum validation
- **External Sync**: Periodic schema synchronization with external systems

### 4. `mnesia_registry` - Data Persistence Manager

Manages Mnesia database operations and backup/recovery:

- **Table Management**: Capability, case, and resource registry tables
- **Backup System**: Automated backups with configurable intervals
- **Health Monitoring**: Mnesia health checks and recovery
- **Error Handling**: Transaction error handling and retry logic

### 5. `yawl_bridge_health` - Health Check Module

Provides comprehensive health monitoring:

- **Process Status**: Checks if all processes are running
- **Mnesia Status**: Verifies Mnesia is running and accessible
- **Table Information**: Returns table sizes and memory usage
- **Memory Metrics**: System and Mnesia memory consumption

### 6. `process_mining_bridge_app` - Application Entry Point

Coordinates application startup and initialization:

- **Mnesia Schema**: Creates required database schema
- **Mnesia Start**: Initializes Mnesia system
- **Supervisor Start**: Launches the supervision tree
- **Clean Shutdown**: Properly terminates Mnesia on stop

## Mnesia Schema Definition

The application creates three main tables:

### `capability_registry`
```erlang
-record(capability_registry, {
    id :: binary(),          % Unique identifier
    handle :: reference(),   % Process reference
    timestamp :: erlang:timestamp()  % Creation time
}).
```

### `case_registry`
```erlang
-record(case_registry, {
    id :: binary(),          % Unique identifier
    case_id :: binary(),    % Case identifier
    workflow_id :: binary(), % Workflow identifier
    status :: binary(),     % Case status
    data :: map()           % Case data
}).
```

### `resource_registry`
```erlang
-record(resource_registry, {
    id :: binary(),          % Resource identifier
    name :: binary(),       % Resource name
    type :: binary(),       % Resource type
    status :: binary(),     % Resource status
    load :: integer()        % Current load
}).
```

## Configuration

### Application Configuration
```erl
{env, [
    {log_level, info},
    {mnesia_dir, "mnesia"},
    {backup_interval, 300000},  % 5 minutes
    {health_check_interval, 30000},  % 30 seconds
    {bridge_config, #{
        process_mining => #{
            endpoint => "http://localhost:8080",
            timeout => 30000,
            retries => 3
        },
        data_modelling => #{
            schema_cache_ttl => 3600000,  % 1 hour
            validation_timeout => 10000
        }
    }}
]}
```

### Dependencies
- `telemetry` - Metrics collection
- `lager` - Logging framework
- `mnesia` - Database system

## Health Check API

```erlang
HealthStatus = yawl_bridge_health:check().
```

Returns a map with:
- `process_mining`: Boolean indicating if process mining bridge is running
- `data_modelling`: Boolean indicating if data modelling bridge is running
- `mnesia`: Boolean indicating if Mnesia is running
- `nodes`: List of connected database nodes
- `tables`: Table information (name, type, size, memory)
- `memory`: Memory usage statistics

## Testing

Run tests with:
```bash
cd /Users/sac/yawl/yawl-erlang-bridge/yawl-erlang-bridge
rebar3 eunit
```

The test suite covers:
- Supervisor child processes startup
- Health check functionality
- Mnesia table creation and operations
- Basic API functions

## Deployment

### Development Mode
```bash
./scripts/start_dev.sh
```

### Production Mode
```bash
rebar3 release
```

The release can be started with:
```bash
./_build/default/rel/yawl_bridge/bin/yawl_bridge start
```

## Fault Tolerance Features

1. **Automatic Restart**: Child processes are automatically restarted on crash
2. **Mnesia Recovery**: Automatic database recovery procedures
3. **Health Monitoring**: Continuous health checks with automatic recovery
4. **Backup System**: Automated backups to prevent data loss
5. **Graceful Shutdown**: Proper cleanup of resources on termination

## Performance Considerations

1. **Buffered Processing**: Events are buffered to handle high throughput
2. **Asynchronous Validation**: Validation requests are processed asynchronously
3. **Schema Caching**: Schemas are cached in memory for fast access
4. **Periodic Sync**: External schema synchronization happens in the background

## Monitoring

The application emits telemetry metrics for:
- Event processing counts
- Validation latencies
- Memory usage
- Error rates
- Process health states