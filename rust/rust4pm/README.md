# rust4pm - BEAM↔Rust Process Mining NIF Library

A Rust NIF library for Erlang/BEAM that provides process mining capabilities through a high-performance Rust implementation.

## Features

- **OCEL2 JSON parsing** - Import OCEL 2.0 event logs from JSON
- **Process mining algorithms** - Directly accessible from Erlang
- **Memory safety** - All Rust objects live on Rust heap with opaque handles
- **Cross-platform** - Supports Linux, macOS, and Windows
- **Zero-copy interface** - Efficient binary transfer between BEAM and Rust

## NIF Functions

### parse_ocel2_json
Parse OCEL2 JSON from Erlang binary and create OCEL log object.

**Signature:**
```erlang
parse_ocel2_json(JsonBinary) -> {ok, OcelId} | {error, Reason}
```

### slim_link_ocel
Create a slim version of the OCEL log with reduced size while preserving semantics.

**Signature:**
```erlang
slim_link_ocel(OcelId) -> {ok, SlimOcelId} | {error, Reason}
```

### discover_oc_declare
Discover OC-DECLARE constraints in the OCEL log.

**Signature:**
```erlang
discover_oc_declare(OcelId) -> {ok, Constraints} | {error, Reason}
```
Where `Constraints` is a list of `{Name, Parameters}` tuples.

### token_replay_ocel
Perform token replay on a Petri net with conformance scoring.

**Signature:**
```erlang
token_replay_ocel(OcelId, PnmlBinary) -> {ok, {Fitness, Precision}} | {error, Reason}
```

## Build Requirements

- Rust 1.70+
- Erlang OTP 25+ (for development)
- Cargo
- Cbindgen (for header generation)

## Building

```bash
# Build release version
make build

# Build debug version
make debug

# Build and generate C header
make build-with-header

# Run tests
make test

# Clean build artifacts
make clean
```

## Integration with Erlang

1. Generate C header:
```bash
make header
```

2. Include the header in your Erlang NIF module:
```c
#include "rust4pm_nif.h"
```

3. Load the library in Erlang:
```erlang
% Load the NIF library
case erlang:load_nif("rust4pm", 0) of
    ok -> ok;
    {error, {load_failure, _}} = Error ->
        %% Handle load error
        Error
end
```

4. Implement the NIF stubs in Erlang:
```erlang
-module(rust4pm).
-export([parse_ocel2_json/1, slim_link_ocel/1, discover_oc_declare/1, token_replay_ocel/2]).

parse_ocel2_json(JsonBinary) ->
    erlang:nif_error("NIF library not loaded").

slim_link_ocel(OcelId) ->
    erlang:nif_error("NIF library not loaded").

discover_oc_declare(OcelId) ->
    erlang:nif_error("NIF library not loaded").

token_replay_ocel(OcelId, PnmlBinary) ->
    erlang:nif_error("NIF library not loaded").
```

## Memory Safety

- All Rust objects live on the Rust heap
- Opaque UUID handles are passed to BEAM
- No cross-heap pointers are created
- All memory is properly freed through the NIF interface

## Architecture

```
┌─────────────────┐      NIF Functions      ┌─────────────────┐
│   Erlang/BEAM   │  <──→  rust4pm.so  <──→│    Rust Core    │
│                 │                      │                 │
│  - parse_ocel2_json  │  │  - pm4rs_core    │
│  - slim_link_ocel   │  │  - UUID handles  │
│  - discover_oc_declare│ │  - JSON parsing  │
│  - token_replay_ocel │  │  - Petri nets    │
└─────────────────┘                      └─────────────────┘
```

## Testing

Run the test suite:
```bash
make test
```

Run specific tests:
```bash
cargo test parse_ocel2_json
cargo test integration
```

## Dependencies

- `erl_nif` - Erlang NIF interface
- `serde` / `serde_json` - JSON serialization
- `uuid` - UUID generation for OcelId
- `petgraph` - Graph algorithms
- `chrono` - Time parsing
- `regex` - Pattern matching

## Future Work

- [ ] Replace placeholder implementations with real pm4rs-core
- [ ] Add more process mining algorithms
- [ ] Performance optimization
- [ ] Add conformance metrics
- [ ] Support for OCEL1
- [ ] Streaming API for large logs

## License

This project is part of the YAWL workflow engine and follows the same license terms.