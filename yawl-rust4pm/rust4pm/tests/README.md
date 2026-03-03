# Process Mining Integration Tests

This directory contains integration tests for the process_mining crate integration with YAWL.

## Test Structure

### 1. `xes_import_test.rs`
Tests XES import functionality:
- Importing running-example.xes and roadtraffic.xes files
- Verifying trace counts and event counts
- Testing error handling for invalid files

### 2. `discovery_test.rs`
Tests process discovery algorithms:
- DFG (Directly-Follows Graph) discovery
- Alpha miner algorithm
- Output format validation (PNML)

### 3. `conformance_test.rs`
Tests conformance checking:
- Token replay conformance
- Fitness calculation (precision, fitness, generalization, simplicity)
- Alignment analysis

### 4. `test_data_validation.rs`
Data validation tests:
- XES file existence and structure validation
- Event and trace counting
- Timestamp format validation
- XES compliance checking

## Test Data

### Sample XES Files
- `data/running-example.xes` - Training process example with 3 traces and 9 events
- `data/roadtraffic.xes` - Road traffic violation example with 4 traces and 16 events

## Running Tests

```bash
# Run all tests
cargo test

# Run specific test modules
cargo test --test xes_import_test
cargo test --test discovery_test
cargo test --test conformance_test
cargo test --test test_data_validation

# Run tests with verbose output
cargo test -- --nocapture
```

## Requirements

- Rust 1.70+
- process_mining crate v0.5.2
- tempfile crate for testing

## API Usage

The tests use the process_mining crate API:

```rust
use process_mining::core::EventLog;
use process_mining::discovery::{dfg, alpha};
use process_mining::conformance::token_replay;
```

## Integration Points

The tests verify integration with the YAWL engine through:
1. XES import/export
2. Process discovery (DFG, Alpha miner)
3. Conformance checking (token replay)
4. PNML import/export