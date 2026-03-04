# YAWL Process Mining Examples

This directory contains example scripts and demonstrations for the YAWL Process Mining Bridge, which integrates Rust-based process mining algorithms with Erlang/OTP applications.

## Files Overview

### Core Examples
- **`pm_example.erl`** - Main example module demonstrating the complete process mining workflow
- **`test_pm_example.escript`** - Test script that runs the examples with proper initialization
- **`test_demo.escript`** - Simple demonstration script (works without build)

### Build Scripts
- **`build_nif.sh`** - Build the Rust NIF library
- **`validate_setup.sh`** - Validate the development environment
- **`Makefile`** - Build automation for the Erlang application

## Quick Start

### 1. Validate Your Setup
```bash
./validate_setup.sh
```
This script checks if all required dependencies (Rust, Cargo, Erlang, Rebar3) are installed and validates the project structure.

### 2. Build the NIF Library
```bash
./build_nif.sh
```
This builds the Rust NIF library and copies it to the Erlang `priv/` directory.

### 3. Build the Erlang Application
```bash
make build
```
This compiles the Erlang application using Rebar3.

### 4. Run the Examples
```bash
make test
```
Or run directly:
```bash
escript test_pm_example.escript
```

## Example Usage

### Quick Start Demo
```erlang
pm_example:quick_start().
```
This demonstrates the main features with a sample XES log.

### Complete XES Workflow
```erlang
pm_example:run_complete().
```
Runs the complete workflow:
1. Import XES file
2. Get event log statistics
3. Discover Directly-Follows Graph (DFG)
4. Discover Alpha+++ Petri Net model
5. Run conformance checking

### Custom XES File
```erlang
pm_example:run_complete("/path/to/your/log.xes").
```
Process your own XES event log file.

### OCEL Processing
```erlang
pm_example:run_ocel_example().
```
Demonstrates Object-Centric Event Log processing.

### Activity Discovery
```erlang
pm_example:discover_activities(LogHandle).
```
Extract unique activities from an event log.

### Process Simulation
```erlang
pm_example:simulate_process(LogHandle, 10).
```
Generate a simulated process trace of length 10.

## API Reference

### Main Functions
- `run_complete([XesPath])` - Run complete XES workflow
- `run_ocel_example()` - Run OCEL example
- `quick_start()` - Quick demonstration
- `discover_activities(LogHandle)` - Extract activities
- `simulate_process(LogHandle, N)` - Simulate process execution

### Bridge Functions (via `process_mining_bridge`)
- `import_xes(#{path => Path})` - Import XES file
- `event_log_stats(#{log_handle => Handle})` - Get log statistics
- `discover_dfg(#{log_handle => Handle})` - Discover DFG
- `discover_alpha(#{log_handle => Handle})` - Discover Alpha+++
- `token_replay(#{log_handle => LogHandle, net_handle => NetHandle})` - Conformance checking
- `import_ocel_json(#{path => Path})` - Import OCEL JSON

## Sample Output

### Complete Workflow Result
```erlang
{ok, #{
    dfg => <<DFG_JSON>>,
    pnml => <<PNML_XML>>,
    conformance => #{fitness => 0.95, precision => 0.87},
    stats => #{traces => 100, events => 1250, activities => 15}
}}
```

### Event Log Statistics
```erlang
{ok, #{
    traces => 100,
    events => 1250,
    activities => 15,
    avg_events_per_trace => 12.5
}}
```

## Dependencies

### System Requirements
- Rust 1.70+
- Cargo
- Erlang/OTP 21+
- Rebar3
- Sample XES log file

### Sample Files
- `../../rust4pm/examples/sample_log.xes` - Sample XES event log
- `../../rust4pm/examples/sample_ocel.json` - Sample OCEL JSON

## Troubleshooting

### Common Issues
1. **NIF not found**: Run `./build_nif.sh` and ensure the library is in `../priv/`
2. **Erlang app not built**: Run `make build`
3. **Missing dependencies**: Install missing tools with `./validate_setup.sh`
4. **Permission errors**: Ensure scripts are executable (`chmod +x`)

### Error Messages
- `nif_not_loaded`: NIF library not properly loaded
- `import_failed`: XES file import failed (check file format)
- `dfg_failed`: DFG discovery failed (log might be empty)
- `alpha_failed`: Alpha++ discovery failed (insufficient traces)
- `conformance_failed`: Token replay failed (net model incompatible)

## Development

### Building from Source
1. Clone the repository
2. Install Rust and Erlang
3. Run `./validate_setup.sh`
4. Run `./build_nif.sh`
5. Run `make build`
6. Run `make test`

### Adding New Examples
1. Add new functions to `pm_example.erl`
2. Update documentation in this README
3. Add tests to `test_pm_example.escript`
4. Update the Makefile if needed

## Architecture

The process mining bridge consists of:
- **Rust NIF**: Core algorithms (DFG, Alpha+++, conformance)
- **Erlang Wrapper**: Safe Elixir/Erlang interface
- **Process Mining Module**: High-level API functions
- **Examples**: Demonstration and testing code

## References

- [XES Standard](http://www.xes-standard.org/)
- [OCEL Standard](http://www.xes-standard.org/ocel.html)
- [Alpha++ Algorithm](https://doi.org/10.1016/j.dss.2018.07.003)
- [Erlang NIFs](https://erlang.org/doc/man/erl_nif.html)

## License

See the main project license for details.