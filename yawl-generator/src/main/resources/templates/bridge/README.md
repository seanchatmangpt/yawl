# Process Mining Bridge Templates

This directory contains ggen templates for generating process mining bridge code from pm-bridge.ttl ontology.

## Overview

The process mining bridge enables cross-language communication between Java YAWL engine and Rust process mining libraries. The templates follow the WCP-NB (Native Bridge) and WCP-PM (Process Mining Bridge) patterns.

## Templates

### 1. `process_mining_bridge.tera`

Generates Erlang gen_server handle_call clauses for process mining bridge functionality.

**Features:**
- Generates gen_server module with synchronous/asynchronous call support
- Maps pm-bridge.ttl capabilities to Erlang functions
- Implements proper error handling and validation
- Includes FFI transport layer integration
- Supports capability-based routing

**Generated Output:**
```erlang
-module(process_mining_bridge).
-behaviour(gen_server).
```

### 2. `erl_term_codec.java.tera`

Generates Java sealed interface hierarchy for cross-language data exchange between Java and Erlang.

**Features:**
- Type-safe serialization/deserialization
- Built-in support for YAWL-specific types
- Validation-by-construction pattern
- Bridge capability constraint validation
- Thread-safe implementation with concurrent maps

**Generated Output:**
```java
public final class ErlTermCodec {
    public static byte[] encode(Object object) throws ErlTermCodecException;
    public static <T> T decode(byte[] bytes) throws ErlTermCodecException;
}
```

### 3. `discover-native-calls.sparql`

SPARQL query for discovering native capabilities from pm-bridge.ttl using QLever.

**Features:**
- Selects NativeCall triples with capability mapping details
- Groups capabilities by function type
- Includes validation rules for bridge configuration
- Provides error handling patterns
- Supports parameterized queries

**Usage:**
```sparql
SELECT ?bridgeName ?capabilityName ?nativeTarget WHERE {
    ?bridge a yawl-bridge:NativeBridge ;
            yawl-bridge:bridgeName ?bridgeName .
    ?bridge yawl-bridge:hasCapability ?capability .
    ?capability a yawl-bridge:BridgeCapability ;
               yawl-bridge:capabilityName ?capabilityName .
}
```

## Integration

### ggen.toml Configuration

The templates are integrated into the main ggen configuration:

```toml
[[generation.rules]]
name = "process-mining-bridge-erlang"
description = "Generate Erlang gen_server from pm-bridge.ttl"
query = { file = "templates/bridge/discover-native-calls.sparql" }
template = { file = "templates/bridge/process_mining_bridge.tera" }
output_file = "erlang/process_mining_bridge.erl"

[[generation.rules]]
name = "erl-term-codec-java"
description = "Generate Java ErlTerm codec interface"
query = { file = "templates/bridge/discover-native-calls.sparql" }
template = { file = "templates/bridge/erl_term_codec.java.tera" }
output_file = "java/bridge/ErlTermCodec.java"
```

### pm-bridge.ttl Integration

The templates read from pm-bridge.ttl ontology which defines:
- Bridge capabilities with native targets
- Native function mappings (L2 transport layer)
- Domain operations (L3 API layer)
- Type mappings and constraints

## Generated Code Features

### H-Guards Compliance

All generated code passes H-Guards validation:
- **No TODO**: All functionality is implemented or throws `UnsupportedOperationException`
- **No mock/stub/fake**: All code is real implementation
- **No empty returns**: All methods return proper values or throw exceptions
- **No lies**: Documentation matches implementation exactly
- **No silent fallbacks**: Errors are propagated properly

### Type Safety

- Erlang-Java type mappings validated at runtime
- Capability constraints enforced
- Cross-language data exchange with validation

### Error Handling

- Comprehensive error handling with stack traces
- Proper cleanup of native resources
- Asynchronous error logging for non-blocking operations
- Validation-by-construction pattern

## Usage Example

### 1. Generate Bridge Code

```bash
ggen generate --rules process-mining-bridge-erlang
```

### 2. Validate Generated Code

```bash
dx.sh all
```

### 3. Run Integration Tests

```bash
mvn test -Dtest=ProcessMiningIntegrationTest
```

## Architecture

The generated bridge follows a three-layer architecture:

1. **L2 FFI Transport**: Thin Panama FFM wrapper for native calls
2. **Erlang gen_server**: Handles message passing and coordination
3. **L3 Domain API**: High-level Java API with zero FFI types at call site

## Dependencies

- pm-bridge.ttl ontology (must be in QLever)
- YAWL Bridge ontology
- Erlang runtime
- Java runtime with Panama FFM support

## License

Apache 2.0 - See LICENSE file for details.