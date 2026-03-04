# YAWL Erlang Bridge

JVM↔BEAM ei bridge for Erlang distribution protocol with Unix domain socket transport.

## Overview

This module implements a three-layer bridge between Java and Erlang:

1. **Layer 1**: jextract ei.h bindings to `org.yawlfoundation.yawl.bridge.erlang.nativ.ei`
2. **Layer 2**: Typed bridge with `ErlangNode` managing ei connections
3. **Layer 3**: Domain API `ProcessMiningClient` for process mining operations

## Architecture

### Layer 1 - Native Interface (jextract ei.h)

```toml
# jextract-ei.toml
Target: $OTP_ROOT/lib/erl_interface-5.6.3/include/ei.h

Functions:
- ei_connect_init, ei_connect, ei_rpc
- ei_x_new, ei_x_free, ei_x_encode_*, ei_decode_*
- ei_x_buff struct

Package: org.yawlfoundation.yawl.bridge.erlang.nativ.ei
```

### Layer 2 - Typed Bridge

```java
ErlTerm sealed interface:
- ErlAtom, ErlList, ErlTuple, ErlBinary, ErlLong

ErlangNode:
public class ErlangNode implements AutoCloseable {
    public ErlangNode(String nodeName, String cookie, Path socketPath);
    public ErlTerm rpc(String module, String function, ErlTerm... args);
    public void close();
}
```

### Layer 3 - Domain API

```java
ProcessMiningClient:
- importOcel(Path ocelPath)
- slimLink(Map<String, ErlTerm> parameters)
- discoverOcDeclare(DiscoveryType type, Map<String, ErlTerm> constraints)
- tokenReplay(String logId, String modelId, ReplayParameters params)
```

## Unix Domain Socket Transport

Uses `-proto_dist local` configuration:
- `ei_connect_host_port` to bypass EPMD
- Socket path: `/tmp/yawl-erlang/#{node_name}.sock`

## Dependencies

- OTP 28 with erl_interface 5.6.3
- Java 21
- Maven 3.8+

## Building

1. Generate native bindings:
```bash
jextract -t jextract-ei.toml
```

2. Build native library:
```bash
cd src/main/cpp && make
```

3. Build Java module:
```bash
mvn clean compile
```

## Usage

### Basic Connection

```java
ErlangNode node = new ErlangNode("yawl@localhost", "secret-cookie");
try {
    ErlTerm response = node.rpc("pm_ocel", "import",
        ErlAtom.of("/path/to/ocel.xes"));
} finally {
    node.close();
}
```

### Process Mining Operations

```java
ProcessMiningClient client = new ProcessMiningClientImpl("yawl@localhost", "secret-cookie");

// Import OCEL
ImportResult result = client.importOcel(Paths.get("data/ocel.xes"));

// Slim link discovery
Map<String, ErlTerm> params = Map.of(
    "min_support", ErlLong.of(5),
    "discovery_type", ErlAtom.of("heuristic")
);
DiscoveryResult discovery = client.slimLink(params);

// Token replay
ReplayParameters replay = new ReplayParameters("align", 0.8, Map.of());
ReplayResult replay = client.tokenReplay(result.getLogId(), discovery.getModelId(), replay);
```

## Testing

Tests are written with JUnit 5 and follow Chicago TDD principles.

```bash
# Unit tests
mvn test

# Integration tests (requires real Erlang node)
mvn test -Dintegration-tests=true
```

## Files

```
yawl-erlang-bridge/
├── yawl-erlang-bridge/
│   ├── pom.xml
│   ├── jextract-ei.toml
│   └── src/main/
│       ├── java/org/yawlfoundation/yawl/bridge/erlang/
│       │   ├── ErlTerm.java
│       │   ├── ErlangException.java
│       │   ├── ErlAtom.java
│       │   ├── ErlList.java
│       │   ├── ErlTuple.java
│       │   ├── ErlBinary.java
│       │   ├── ErlLong.java
│       │   ├── EiBuffer.java
│       │   └── ErlangNode.java
│       ├── java/org/yawlfoundation/yawl/bridge/erlang/nativ/
│       │   └── EiNativeLibrary.java
│       ├── java/org/yawlfoundation/yawl/bridge/erlang/domain/
│       │   ├── ProcessMiningClient.java
│       │   └── ProcessMiningClientImpl.java
│       └── java/org/yawlfoundation/yawl/bridge/erlang/test/
│           ├── ErlangNodeTest.java
│           ├── ErlTermCodecTest.java
│           └── ProcessMiningClientTest.java
└── README.md
```

## Standards

This implementation follows HYPER_STANDARDS:
- No TODO/FIXME markers (real implementation or UnsupportedOperationException)
- No mock/stub implementations (real connections or throw)
- No silent fallbacks
- No lies (code matches documentation)
- Chicago TDD with 80%+ coverage

## License

YAWL v6.0.0 SPR - A = μ(O) | drift(A) → 0