# Erlang ↔ Rust NIF Bridge Benchmark Design

## Executive Summary

This benchmark suite provides comprehensive performance testing for the YAWL process mining bridge between Erlang and Rust NIFs. The benchmarks focus on three critical areas:

1. **NIF Call Overhead** - Pure crossing cost
2. **Data Marshalling** - Serialization/deserialization costs  
3. **Process Mining Operations** - End-to-end performance

## Benchmark Architecture

### Core Components

```
bench/
├── BENCHMARK_DESIGN.md         # This design document
├── run_benchmarks.sh           # Main runner script
├── benchmark_nif_overhead.erl  # Pure NIF call benchmark
├── benchmark_data_marshalling.erl # Data transfer benchmark
├── benchmark_pm_operations.erl  # Process mining operations
└── benchmark_concurrency.erl   # Concurrency & load testing
```

### Required Rust NIF Extensions

Add these functions to `nif.rs` for benchmarking:

```rust
// Benchmark utilities
#[rustler::nif] pub fn nop() -> Term<'_>
#[rustler::nif] pub fn int_passthrough(n: i64) -> Term<'_>
#[rustler::nif] pub fn atom_passthrough(atom: Atom) -> Term<'_>
#[rustler::nif] pub fn small_list_passthrough(list: Vec<Atom>) -> Term<'_>
#[rustler::nif] pub fn tuple_passthrough(tuple: (Atom, Atom, Atom)) -> Term<'_>

// Data marshalling tests
#[rustler::nif] pub fn echo_json(json: String) -> Term<'_>
#[rustler::nif] pub fn echo_term(term: Term<'_>) -> Term<'_>
#[rustler::nif] pub fn echo_binary(binary: Vec<u8>) -> Term<'_>
#[rustler::nif] pub fn echo_ocel_event(event: String) -> Term<'_>
#[rustler::nif] pub fn large_list_transfer(list: Vec<i64>) -> Term<'_>
```

## Detailed Benchmark Scopes

### 1. NIF Call Overhead Benchmark

**Purpose**: Measure the pure cost of Erlang → Rust → Erlang calls

**Metrics Tracked**:
- Empty call latency (μs)
- Small data transfer overhead
- P50, P90, P99 percentiles

**Test Cases**:
- Empty call (no data transfer)
- Integer passthrough (42)
- Atom passthrough (test_atom)
- Small list passthrough ([a,b,c,d,e])
- Tuple passthrough ({a,b,c,d,e})

**Expected Results**:
- Empty call: < 50μs p99
- Integer transfer: < 100μs p99

### 2. Data Marshalling Benchmark

**Purpose**: Measure serialization/deserialization costs for different data types

**Metrics Tracked**:
- Transfer time per operation (μs)
- Bandwidth (MB/s)
- Memory usage during transfer

**Test Cases**:
- JSON event marshalling (OCEL events)
- Erlang term marshalling
- Binary data transfer (1KB)
- Large list transfer (1000 elements)

**Expected Results**:
- JSON transfer: < 1ms for 1KB p99
- Binary bandwidth: > 100 MB/s

### 3. Process Mining Operations Benchmark

**Purpose**: Measure end-to-end performance of real PM operations

**Metrics Tracked**:
- Total operation time (ms)
- Throughput (events/sec)
- Memory usage during operation

**Operations Tested**:
- XES Import
- XES Export  
- DFG Discovery
- Alpha Miner
- Event Log Statistics

**Expected Results**:
- XES import: 1K events/sec
- DFG discovery: 10K events/sec
- Alpha miner: 5K events/sec

### 4. Concurrency Benchmark

**Purpose**: Measure performance under concurrent load

**Metrics Tracked**:
- Throughput with N concurrent workers
- Latency distribution under load
- Memory growth patterns
- Resource contention effects

**Test Scenarios**:
- Simple concurrent NIF calls
- Concurrent XES imports
- Concurrent DFG discovery with sharing
- Load testing with varying concurrency levels

**Expected Results**:
- Linear throughput scaling up to 100 workers
- P99 latency < 100ms under load
- Memory growth within 50MB per 100 workers

## Test Data

### Synthetic Event Logs
The benchmarks use programmatically generated XES logs with:
- Configurable number of traces (cases)
- Configurable events per trace
- Realistic activity patterns
- Proper XES structure

### OCEL Events
JSON format with:
- Event and case IDs
- Activity names
- Timestamps
- Object identifiers
- Numeric and string attributes

## Performance Targets

### Latency Targets
- NIF call overhead: < 50μs p99
- JSON marshalling: < 1ms for 1KB
- DFG discovery: < 100ms for 10K events

### Throughput Targets
- Simple operations: > 10K ops/sec
- XES processing: > 1K events/sec
- Concurrency: Linear scaling to 100 workers

### Memory Targets
- Steady state: ± 10MB per concurrent worker
- Peak usage: < 50MB per large operation
- No memory leaks detected

## Integration with YAWL

The benchmark suite integrates with YAWL by:
1. Using real YAWL data structures where possible
2. Testing with typical YAWL workloads
3. Validating compatibility with YNetRunner
4. Measuring impact on workflow throughput

## Analysis Tools

The benchmarks include:
- Percentile analysis (P50, P90, P99)
- Linear regression for scaling analysis
- Memory monitoring
- Statistical summaries

## Running the Benchmarks

```bash
# Compile the bridge first
cd ../
cargo build --release
cp target/release/libyawl_process_mining.so ebin/

# Run all benchmarks
./run_benchmarks.sh

# Individual benchmarks
erl -pa ebin -s benchmark_nif_overhead start -s init stop
erl -pa ebin -s benchmark_data_marshalling start -s init stop
```

## Optimization Opportunities

Based on benchmark results, identify opportunities for:

1. **Batch Operations**: Group multiple events in single NIF calls
2. **Binary Protocols**: Replace JSON with MessagePack for large transfers
3. **Resource Pooling**: Reuse log handles instead of recreating
4. **Streaming**: Process logs in chunks for large datasets
5. **Parallel Processing**: Utilize Rust threads for CPU-intensive ops

## Output Format

All benchmarks produce structured output including:
- Summary statistics (mean, median, percentiles)
- Throughput measurements
- Memory usage statistics
- Error rates
- Recommended optimizations
