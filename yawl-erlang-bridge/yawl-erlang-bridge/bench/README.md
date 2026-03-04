# YAWL Erlang Bridge Benchmarks

This directory contains a comprehensive benchmark suite for the Erlang ↔ Rust NIF bridge used in YAWL process mining.

## Quick Start

1. **Compile the Rust NIF** (if not already done):
```bash
cd ../../
cargo build --release
cp target/release/libyawl_process_mining.so ebin/
```

2. **Run all benchmarks**:
```bash
./run_benchmarks.sh
```

3. **Run individual benchmarks**:
```bash
# NIF call overhead
erl -pa ebin -s benchmark_nif_overhead start -s init stop

# Data marshalling
erl -pa ebin -s benchmark_data_marshalling start -s init stop

# Process mining operations
erl -pa ebin -s benchmark_pm_operations start -s init stop

# Concurrency testing
erl -pa ebin -s benchmark_concurrency start -s init stop
```

## What We Measure

### 1. NIF Call Overhead
- Pure Erlang ↔ Rust crossing cost
- Small data transfer costs
- Empty call latency baseline

### 2. Data Marshalling  
- JSON serialization/deserialization
- Erlang term transfer costs
- Binary data bandwidth
- Memory usage patterns

### 3. Process Mining Operations
- XES Import/Export performance
- DFG discovery throughput
- Alpha miner execution time
- Event log statistics

### 4. Concurrency Performance
- Scaling with multiple workers
- Latency under load
- Memory growth patterns
- Resource sharing effects

## Performance Targets

Based on YAWL performance requirements:

- **Engine startup**: < 60s (covered by other benchmarks)
- **Case creation**: < 500ms p95
- **Work item checkout**: < 200ms p95
- **NIF call overhead**: < 50μs p99
- **Data marshalling**: < 1ms for 1KB p99
- **Process mining**: 1K-10K events/sec depending on operation
- **Concurrent scaling**: Linear up to 100 workers

## Expected Output

Each benchmark produces:
- Summary statistics (mean, median, percentiles)
- Throughput measurements (operations/sec)
- Memory usage analysis
- Error rates and statistics
- Optimization recommendations

## Integration with YAWL

These benchmarks help validate that the Erlang ↔ Rust bridge meets YAWL's performance targets:

1. **YNetRunner latency**: Ensure NIF calls don't slow down workflow execution
2. **YWorkItem throughput**: Verify process mining operations scale appropriately
3. **Memory patterns**: Monitor GC impact and memory usage
4. **Stability**: Ensure bridge doesn't cause crashes or deadlocks

## Files

- `BENCHMARK_DESIGN.md` - Complete design specification
- `benchmark_nif_overhead.erl` - Pure call overhead measurements
- `benchmark_data_marshalling.erl` - Data transfer performance
- `benchmark_pm_operations.erl` - Process mining operations
- `benchmark_concurrency.erl` - Concurrent performance testing
- `run_benchmarks.sh` - Main runner script
- `README.md` - This file

## Next Steps

1. Add the missing NIF functions to `nif.rs`
2. Run baseline benchmarks
3. Implement optimizations
4. Re-run benchmarks to measure improvement
5. Integrate with YAWL performance monitoring
