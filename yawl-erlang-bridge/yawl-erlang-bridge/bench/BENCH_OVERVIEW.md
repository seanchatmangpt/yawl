# Erlang ↔ Rust NIF Bridge Benchmark Design

## Overview

This benchmark suite measures performance characteristics of the Erlang ↔ Rust NIF bridge used for process mining operations. The benchmarks focus on NIF call overhead, data marshalling costs, and typical process mining operation throughput.

## Benchmark Components

### 1. NIF Call Overhead

**Objective**: Measure the pure cost of calling from Erlang to Rust and back, excluding any actual computation.

**Metrics**:
- Call latency (μs)
- Empty round-trip time
- Small data transfer overhead

**Test Cases**:
```erlang
% Empty NIF call (no data transfer)
empty_call() -> ?MODULE:nop().

% Small integer transfer
int_call(N) -> ?MODULE:int_passthrough(N).

% Small atom transfer
atom_call(Atom) -> ?MODULE:atom_passthrough(Atom).
```

### 2. Data Marshalling Costs

**Objective**: Measure the cost of transferring data between Erlang and Rust processes.

**Metrics**:
- Serialization time (bytes → microseconds)
- Deserialization time (microseconds → bytes)
- Memory usage during transfer
- Transfer bandwidth (MB/s)

**Test Cases**:
```erlang
% JSON string marshalling (typical for OCEL events)
json_event_transfer(EventJson) -> ?MODULE:echo_json(EventJson).

% Complex data structure transfer
complex_struct_transfer(ComplexTerm) -> ?MODULE:echo_complex(ComplexTerm).

% Binary data transfer
binary_transfer(BinaryData) -> ?MODULE:echo_binary(BinaryData).
```

### 3. Process Mining Operations

**Objective**: Measure end-to-end performance of typical process mining operations.

**Metrics**:
- Total operation time
- Breakdown: NIF call time vs computation time
- Memory usage during operation
- Throughput (operations/sec)

**Operations**:
1. **XES Import/Export**
2. **OCEL Import/Export**
3. **DFG Discovery**
4. **Alpha Miner**
5. **Event Log Statistics**

### 4. Concurrency Performance

**Objective**: Measure performance under concurrent load.

**Metrics**:
- Throughput with N concurrent workers
- Latency distribution under load
- Resource contention effects
- Memory growth patterns

**Test Setup**:
```erlang
concurrent_benchmark(N, Fun) ->
    Processes = lists:seq(1, N),
    lists:foreach(fun(_) -> spawn(Fun) end, Processes),
    measure_throughput().
```

### 5. Memory Patterns

**Objective**: Measure memory allocation and garbage collection impact.

**Metrics**:
- Memory per operation (peak and steady-state)
- GC frequency and duration
- Memory leak detection
- Resource cleanup efficiency

## Test Data

### Synthetic Event Logs
```erlang
% Generate synthetic XES logs
generate_xes_log(NumCases, EventsPerCase) ->
    [generate_trace(I, EventsPerCase) || I <- lists:seq(1, NumCases)].

generate_trace(TraceId, NumEvents) ->
    Events = generate_events(TraceId, NumEvents),
    #trace{id = TraceId, events = Events}.
```

### OCEL Events
```json
{
  "event_id": "evt_001",
  "case_id": "case_001",
  "activity": "Approve",
  "timestamp": "2023-01-01T10:00:00Z",
  "object": "object_001",
  "attributes": {
    "cost": 150.50,
    "approver": "john.doe"
  }
}
```

## Performance Targets

### NIF Call Overhead
- Empty call: < 50μs p99
- Integer transfer: < 100μs p99
- JSON transfer: < 1ms for 1KB p99

### Process Mining Operations
- XES import: 1K events/sec
- XES export: 2K events/sec
- DFG discovery: 10K events/sec
- Alpha miner: 5K events/sec
- OCEL import: 5K events/sec

### Memory Usage
- Steady state: ± 10MB per concurrent worker
- Peak: < 50MB per operation for large datasets

## Optimizations to Test

1. **Batch Operations**: Group multiple events in single NIF call
2. **Binary Protocols**: Replace JSON with MessagePack for large transfers
3. **Resource Pooling**: Reuse log handles instead of recreating
4. **Streaming**: Process logs in chunks for large datasets
5. **Parallel Processing**: Utilize Rust threads for CPU-intensive ops

## Integration with YAWL

The benchmark suite will integrate with the YAWL workflow engine by:
1. Measuring impact on YNetRunner latency
2. Testing with real YAWL case data
3. Validating NIF integration stability under load
4. Measuring GC impact on workflow throughput
