# YAWL Process Mining Python Bindings

High-performance Python bindings for YAWL Process Mining, built with PyO3 and Rust.

## Features

- **XES Import/Export**: Read and write XES event log files
- **Directly Follows Graph (DFG)**: Discover process models from event logs
- **Alpha Miner**: Discover Petri nets using the Alpha algorithm
- **Conformance Checking**: Check event logs against process models
- **Token-based Replay**: Calculate fitness using token replay algorithms
- **High Performance**: Rust-optimized for fast processing

## Installation

### From PyPI (when published)

```bash
pip install yawl-process-mining
```

### From Source

```bash
# Clone the repository
git clone https://github.com/yawlfoundation/yawl-rust4pm.git
cd yawl-rust4pm

# Install Python dependencies
pip install maturin

# Build and install the Python package
maturin develop
```

### Prerequisites

- Python 3.8+
- Rust 1.70+
- Cargo

## Usage

### Import XES Event Log

```python
import yawl_process_mining as ypm

# Import XES event log
event_log = ypm.import_xes("path/to/your/log.xes")

# Access event data
print(f"Events: {len(event_log['events'])}")
print(f"Traces: {len(event_log['traces'])}")
```

### Discover DFG

```python
# Discover Directly Follows Graph
dfg = ypm.discover_dfg(event_log)

# Access DFG data
print(f"Nodes: {dfg['nodes']}")
print(f"Edges: {dfg['edges']}")
print(f"Start activities: {dfg['start_activities']}")
print(f"End activities: {dfg['end_activities']}")
```

### Discover Alpha Miner

```python
# Discover Petri Net using Alpha Miner
pnml = ypm.discover_alpha(event_log)

# PNML is returned as XML string
print(f"PNML length: {len(pnml)} characters")
```

### Conformance Checking

```python
# Check conformance against a process model
conformance = ypm.check_conformance(event_log, pnml)

# Access conformance metrics
print(f"Fitness: {conformance['fitness']}")
print(f"Missing: {conformance['missing']}")
print(f"Remaining: {conformance['remaining']}")
print(f"Consumed: {conformance['consumed']}")
```

### Using Submodules

For more granular control, you can use the submodules:

```python
import yawl_process_mining.xes as xes
import yawl_process_mining.discovery as discovery
import yawl_process_mining.conformance as conformance

# Import XES
event_log = xes.import_xes("log.xes")

# Discover DFG
dfg = discovery.discover_dfg(event_log)

# Discover Alpha miner
pnml = discovery.discover_alpha(event_log)

# Check conformance
conformance = conformance.check_conformance(event_log, pnml)
```

## API Reference

### ypm.xes

- `import_xes(path: str) -> dict`: Import XES event log from file path
- `export_xes(event_log: dict, path: str) -> None`: Export event log to XES file

### ypm.discovery

- `discover_dfg(event_log: dict) -> dict`: Discover Directly Follows Graph
- `discover_alpha(event_log: dict, frequency_threshold: int = 1) -> str`: Discover Petri Net using Alpha miner
- `discover_heuristics(event_log: dict, dependency_threshold: float = 0.5) -> dict`: Discover Heuristics Net

### ypm.conformance

- `check_conformance(event_log: dict, pnml: str) -> dict`: Check conformance of event log against PNML model
- `token_replay_fitness(event_log: dict, pnml: str) -> dict`: Calculate fitness using token-based replay
- `calculate_precision(event_log: dict, pnml: str) -> float`: Calculate precision of process model
- `calculate_generalization(event_log: dict, pnml: str) -> float`: Calculate generalization of process model

## Performance

The Python bindings provide high-performance processing by leveraging Rust:

- **Fast Import/Export**: Optimized XES parsing and serialization
- **Efficient Discovery**: Fast DFG and Alpha Miner algorithms
- **Concurrent Processing**: Thread-safe operations for parallel processing
- **Memory Efficient**: Efficient data structures minimize memory usage

## Examples

See the `examples/` directory for more usage examples:

```bash
# Run examples
python examples/basic_usage.py
python/examples/dfg_discovery.py
python/examples/alpha_miner.py
python/examples/conformance_checking.py
```

## Testing

```bash
# Run tests
pytest tests/

# Run tests with coverage
pytest tests/ --cov=yawl_process_mining

# Run specific test
pytest tests/test_xes_import.py
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Run the test suite
6. Submit a pull request

## License

This project is part of the YAWL Foundation and is distributed under the GNU Lesser General Public License v3.0 or later. See the LICENSE file for details.

## References

- [YAWL Foundation](https://www.yawlfoundation.org/)
- [Process Mining with YAWL](https://www.yawlfoundation.org/wiki/ProcessMining)
- [RWTH Aachen Process Mining](https://processmining.de/)
- [XES Standard](https://www.xes-standard.org/)
- [PyO3 Documentation](https://pyo3.rs/)