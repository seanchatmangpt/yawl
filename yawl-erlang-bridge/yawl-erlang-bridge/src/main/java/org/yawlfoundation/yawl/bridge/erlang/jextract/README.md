# YAWL Erlang Bridge - Layer 1: jextract Bindings

This package provides Layer 1 bindings for the Erlang ei.h interface using Java's Foreign Function & Memory API (jextract).

## Overview

The Layer 1 bindings provide direct C-to-Java interoperability for Erlang's distributed protocol without any business logic abstraction. This follows the Three-Domain Native Bridge Pattern:

```
JVM (Java) ↔ BEAM (Erlang)
     ↑
  Layer 1: Raw jextract bindings (this package)
```

## Package Structure

### 1. EiNative.java
**Purpose**: Direct jextract bindings to ei.h C functions

**Key Functions**:
- `erlConnectInit()` - Initialize Erlang connection parameters
- `erlConnect()` - Connect to Erlang node via socket
- `erlRpc()` - Execute remote procedure calls
- `eiXNew()/eiXFree()` - Buffer management
- `eiXEncode*()` - Encode Java objects to Erlang binary format
- `eiDecode*()` - Decode Erlang binary format to Java objects

**Thread Safety**: Methods are thread-safe - native library is loaded once and reused

**Error Handling**: All methods throw `UnsupportedOperationException` if native library is unavailable

### 2. EiConstants.java
**Purpose**: Erlang protocol constants and magic numbers

**Key Constants**:
- `ERLANG_MAGIC_NUMBER = 131` - Magic number for all Erlang messages
- External term format tags (ATOM_EXT, TUPLE_EXT, LIST_EXT, etc.)
- Error codes (ERL_ERROR, ERL_CONNECT_FAIL, etc.)
- Length limits (MAXATOMLEN = 255)
- Timeout values

### 3. EiLayout.java
**Purpose**: MemoryLayout definitions for C structs

**Layouts Defined**:
- `EI_X_BUFF_LAYOUT` - ei_x_buff struct (buff, buffsize, index)
- `ERL_CONNECT_LAYOUT` - ErlConnect struct (nodename, creation, addr, fd)
- Term layouts (ERL_TUPLE_LAYOUT, ERL_LIST_LAYOUT, etc.)

**Helper Methods**:
- `createSegment()` - Allocate memory segments from layouts
- `validateSegment()` - Verify segment size compatibility

## Usage Pattern

```java
// 1. Initialize connection
byte[] cookie = "secret".getBytes();
int creation = 0;
MemorySegment process = EiNative.erlConnectInit(cookie, creation, null);

// 2. Create buffers for RPC
MemorySegment x = EiNative.eiXNew(EiConstants.DEFAULT_XBUFF_SIZE);

// 3. Encode arguments
EiNative.eiXEncodeAtom(x, "module".getBytes());
EiNative.eiXEncodeTupleHeader(x, 2);

// 4. Execute RPC
MemorySegment reply = EiNative.eiXNew(1024);
int result = EiNative.erlRpc(process,
                            "node@host".getBytes(),
                            "module".getBytes(),
                            "function".getBytes(),
                            2, x, reply, 5000);

// 5. Decode response
MemorySegment index = arena.allocate(ValueLayout.JAVA_INT);
MemorySegment arity = arena.allocate(ValueLayout.JAVA_INT);
EiNative.eiDecodeTupleHeader(reply, index, arity);

// 6. Cleanup
EiNative.eiXFree(x);
EiNative.eiXFree(reply);
```

## Native Library Requirements

The bindings require libei.so from Erlang/OTP:

### On Ubuntu/Debian:
```bash
sudo apt-get install erlang-dev
export LD_LIBRARY_PATH=/usr/lib/erlang/lib/erl_interface-5.x/erts-14.x/lib
```

### On macOS (Homebrew):
```bash
brew install erlang
export DYLD_LIBRARY_PATH=$(brew --prefix erlang)/lib/erlang/lib/erl_interface-*/lib
```

### On RHEL/CentOS:
```bash
sudo yum install erlang-devel
export LD_LIBRARY_PATH=/usr/lib64/erlang/lib/erl_interface-*/lib
```

## Building from Source

If libei.so is not available, you can build it:

```bash
# Install Erlang development headers
sudo apt-get install erlang-dev  # Debian/Ubuntu
sudo yum install erlang-devel    # RHEL/CentOS

# Build libei.so
cd /usr/lib/erlang/lib/erl_interface-*/lib
make
```

## Error Handling

All Layer 1 methods follow this pattern:

```java
try {
    EiNative.erlConnectInit(cookie, creation, null);
} catch (UnsupportedOperationException e) {
    // Native library not available
    log.error("Erlang ei library not found: {}", e.getMessage());
    throw new BridgeUnavailableException("Erlang integration disabled", e);
}
```

## Performance Considerations

1. **Buffer Reuse**: Allocate buffers once and reuse them
2. **Arena Management**: Use confined arenas for temporary allocations
3. **Zero-Copy**: Pass memory segments directly between JVM and native code
4. **Thread Safety**: All methods are thread-safe with native library caching

## Integration with Layer 2

This Layer 1 package is consumed by Layer 2 (`org.yawlfoundation.yawl.bridge.erlang.ErlangNode`) which provides:

- High-level node management
- Connection pooling
- Automatic reconnection
- Business logic integration

## Protocol References

- [Erlang External Term Format](https://erlang.org/doc/apps/erts/erl_dist_protocol.html)
- [ei.h C Interface](https://www.erlang.org/doc/apps/erts/erl_interface)
- [Foreign Function & Memory API (JEP 444)](https://openjdk.org/jeps/444)