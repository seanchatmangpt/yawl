# YAWL Rust4PM - Process Mining Integration

A high-performance integration of the official RWTH Aachen process_mining crate (v0.5.2) with YAWL, providing state-of-the-art process mining capabilities for OCEL2, conformance checking, and process discovery.

## Architecture Overview

### Core Library Integration

This project integrates the official `process_mining` crate from RWTH Aachen University:

- **Version**: 0.5.2
- **Paper**: "Developing a High-Performance Process Mining Library with Java and Python Bindings in Rust" (Küsters & van der Aalst, 2024)
- **GitHub**: https://github.com/aarkue/rust-bridge-process-mining
- **Features**: XES import/export, OCEL 2.0 support, Process Discovery, Conformance Checking

### Multi-Language Support

The integration provides native bindings for multiple languages:

- **Java**: JNI-based high-level API with automatic resource management
- **Python**: PyO3-based bindings for seamless integration
- **Erlang**: NIF interface for the YAWL ecosystem
- **C**: FFI-compatible interface for custom integrations

## Supported Features

### Process Mining Algorithms

#### Process Discovery
- **Alpha Miner**: Classic α+ algorithm for Petri net discovery
- **Directly Follows Graph (DFG)**: Activity sequence analysis
- **OC-DFG**: Object-centric directly follows graph for OCEL data

#### Process Models
- **Petri Nets**: Full PNML support (import/export)
- **Process Trees**: Recursive process model representation

#### Conformance Checking
- **Token Replay**: Full token-based replay for conformance checking
- **Fitness Measurement**: Track missing, remaining, and consumed tokens
- **PNML Support**: Parse PNML XML for process model definition

### Data Format Support

#### OCEL 2.0 (Object-Centric Event Logs)
- **JSON**: Standard OCEL JSON format
- **XML**: XML-based OCEL representation
- **SQLite**: SQLite database format

#### XES (IEEE Standard)
- **Import**: Read XES event logs
- **Export**: Write XES event logs

### Performance Features
- **Thread-Safe**: All operations are thread-safe
- **Memory Efficient**: Automatic resource management
- **Zero-Copy**: Optimized data structures
- **High Performance**: Rust-optimized processing

## Build Instructions

### Prerequisites
- Rust 1.70+
- Cargo (package manager)
- JDK 17+ (for Java bindings)
- Python 3.8+ (for Python bindings)
- Erlang/OTP 25+ (for NIF bindings)

### Build Commands

```bash
# Build for all targets
make all

# Build for Java JNI
make jni

# Build for Erlang NIF
make nif

# Build for Python
make python

# Build specific targets
make build          # Release build
make build-debug    # Debug build
make test          # Run tests
make bench         # Run benchmarks
make docs          # Generate documentation
```

## Usage Examples

### Java Integration

```java
// Using the high-level API
try (var bridge = new Rust4pmBridge();
     var engine = new ProcessMiningEngine(bridge)) {

    // Parse OCEL2 from JSON
    try (var log = engine.parseOcel2Json(json)) {
        // Discover process model
        DirectlyFollowsGraph dfg = engine.discoverDfg(log);

        // Check conformance against PNML
        ConformanceReport report = engine.checkConformance(log, pnmlXml);

        // Get statistics
        ActivityStats stats = engine.getActivityStats(log);
    }
}

// Using XES importer
XesImporter.importXesFile("/path/to/log.xes");
```

### Python Integration

```python
# Install the package
pip install yawl_process_mining

# Basic usage
import yawl_process_mining as ypm

# Parse OCEL2
ocel = ypm.parse_ocel_from_json(json_str)

# Discover DFG
dfg = ypm.discover_dfg(ocel)

# Check conformance
report = ypm.check_conformance(ocel, pnml_string)
print(f"Fitness: {report.fitness}")
```

### Erlang NIF Integration

```erlang
% Start the NIF
process_mining_bridge:start(),

% Load OCEL from file
{ok, Ocel} = process_mining_bridge:parse_ocel2_json("/path/to/ocel.json"),

% Discover process model
{ok, DFG} = process_mining_bridge:discover_dfg(Ocel),

% Check conformance
{ok, Result} = process_mining_bridge:check_conformance(Ocel, PNML),

% Cleanup
ok = process_mining_bridge:free_ocel(Ocel).
```

### C Interface

```c
#include "rust4pm.h"

// Parse OCEL2 from C
rust4pm_ocel_t* ocel = rust4pm_parse_ocel2_json("/path/to/ocel.json");
if (ocel) {
    // Discover DFG
    char* dfg_json = rust4pm_discover_dfg(ocel);
    printf("DFG: %s\n", dfg_json);

    // Free memory
    rust4pm_free_dfg(dfg_json);
    rust4pm_free_ocel(ocel);
}
```

## Data Models

### OCEL Log Structure
```json
{
  "events": [
    {
      "activity": "Task_A",
      "timestamp": "2024-01-01T10:00:00Z",
      "case_id": "case_1",
      "object_id": "obj_1",
      "attributes": {
        "cost": 100
      }
    }
  ],
  "objects": {
    "obj_1": {
      "object_type": "Order",
      "attributes": {
        "amount": 1000
      }
    }
  },
  "global_trace": "global_trace_1"
}
```

### Conformance Result
```json
{
  "fitness": 0.95,
  "missing": 5,
  "remaining": 10,
  "consumed": 95
}
```

## Development Setup

### Starter Kit Reference

For new projects, use the official rust-bridge-template:
https://github.com/aarkue/rust-bridge-template

### Project Structure
```
rust4pm/
├── src/                    # Rust source code
├── tests/                  # Integration tests
├── benches/                # Performance benchmarks
├── examples/               # Usage examples
├── java/                   # Java bindings source
├── python/                 # Python bindings source
└── erlang/                 # Erlang NIF source
```

### Testing

```bash
# Run all tests
cargo test

# Run specific test
cargo test test_conformance_checking

# Run with verbose output
cargo test -- --test-threads=1 --nocapture

# Run integration tests
cargo test integration
```

### Benchmarking

```bash
# Run all benchmarks
cargo bench

# Run specific benchmark
cargo bench bench_dfg_discovery

# Profile memory usage
cargo bench --bench dfg_discovery -- --sample-size 100
```

## API Reference

### Core Functions

#### OCEL Operations
- `parse_ocel2_json(path)` - Parse OCEL2 from JSON file
- `load_ocel_from_json(json_str)` - Parse OCEL2 from JSON string
- `export_ocel_to_json(ocel)` - Export OCEL to JSON string
- `slim_link_ocel(ocel)` - Create optimized slim version

#### Process Discovery
- `discover_dfg(slim_ocel)` - Discover Directly Follows Graph
- `discover_alpha_miner(ocel)` - Apply Alpha Miner algorithm
- `discover_oc_dfg(ocel)` - Discover Object-Centric DFG

#### Conformance Checking
- `check_conformance(ocel, pnml)` - Token replay conformance check

#### Query Operations
- `log_event_count(ocel)` - Get event count
- `log_case_count(ocel)` - Get case count
- `get_activities(ocel)` - Get unique activities
- `get_cases(ocel)` - Get unique case IDs

### Resource Management
All resources implement automatic cleanup via RAII pattern. Manual cleanup is also available:
- `free_ocel(ocel)` - Free OCEL resource
- `free_slim_ocel(slim)` - Free slim OCEL resource

## Performance Considerations

### Memory Management
- Automatic resource cleanup prevents leaks
- Reference counting for efficient sharing
- Zero-copy where possible for large datasets

### Concurrency
- All operations are thread-safe
- Virtual thread support for Java
- No global state for parallel processing

### Optimization Tips
1. Use slim versions for read-only operations
2. Batch multiple operations when possible
3. Enable compact object headers for memory efficiency
4. Use virtual threads for concurrent processing

## Error Handling

All functions return Result types with clear error messages:

```rust
// Rust
match parse_ocel2_json(path) {
    Ok(ocel) => process_ocel(ocel),
    Err(e) => eprintln!("Failed to parse OCEL: {}", e),
}

// Java
try {
    var log = engine.parseOcel2Json(json);
} catch (ParseException e) {
    System.err.println("Parse error: " + e.getMessage());
}
```

## Troubleshooting

### Common Issues

1. **Build Failures**
   - Ensure all prerequisites are installed
   - Check Rust version with `rustc --version`
   - Run `cargo clean` and rebuild

2. **Memory Issues**
   - Enable compact object headers: `RUSTFLAGS="-C target-feature=+crt-static"`
   - Monitor memory usage with valgrind

3. **Performance Issues**
   - Use slim versions for large datasets
   - Enable release builds for performance
   - Profile with `cargo bench`

### Logging

Enable debug logging for troubleshooting:
```bash
RUST_LOG=debug cargo run --example basic_usage
```

## Contributing

1. Follow the starter kit template
2. Ensure all tests pass
3. Update documentation for new features
4. Benchmark performance changes
5. Test all language bindings

## License

This project is part of the YAWL Foundation and is distributed under the GNU Lesser General Public License. See the LICENSE file for details.

## References

- RWTH Aachen Process Mining Library: https://github.com/aarkue/rust-bridge-process-mining
- Process Mining Book: https://www.springer.com/gp/book/9783030339795
- OCEL 2.0 Standard: https://www.win.tue.nl/lnu/ocel/
- XES Standard: http://www.xes-standard.org/