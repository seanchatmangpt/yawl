# YAWL Process Mining Bridge - Installation Guide

## Overview

This guide provides detailed instructions for installing and setting up the YAWL Process Mining Bridge, which enables process mining capabilities in YAWL through an Erlang-Rust interface.

## Prerequisites

### System Requirements
- **Operating System**: Linux, macOS, or Windows
- **Erlang/OTP**: 25.0 or higher
- **Rust**: 1.70.0 or higher
- **Cargo**: Latest stable version
- **Make**: Required for build automation

### Erlang Installation

#### Ubuntu/Debian
```bash
sudo apt-get update
sudo apt-get install erlang erlang-dev erlang-src
```

#### macOS (Homebrew)
```bash
brew install erlang
```

#### Windows
1. Download from https://www.erlang.org/downloads
2. Run the installer
3. Add to PATH during installation

#### Verify Installation
```bash
erl -v
```

### Rust Installation

#### Install rustup
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

#### Add to PATH
```bash
source $HOME/.cargo/env
```

#### Verify Installation
```bash
rustc --version
cargo --version
```

## Installation Steps

### Step 1: Clone Repository
```bash
cd /Users/sac/yawl
git clone <repository-url>
cd yawl-erlang-bridge
```

### Step 2: Build Rust NIF
```bash
cd rust4pm
make nif
```

This command will:
1. Build the Rust NIF library
2. Copy to `priv/yawl_process_mining.so` (Linux/macOS) or `priv/yawl_process_mining.dll` (Windows)
3. Run basic tests to verify the build

### Step 3: Build Erlang Application
```bash
cd yawl-erlang-bridge
make
```

### Step 4: Verify Installation

#### Check NIF Loading in Erlang
```erlang
% In Erlang shell
1> c(process_mining_bridge).
2> process_mining_bridge:check_nif_loaded().
true
```

#### Run Tests
```bash
# Run all tests
make test

# Run specific test
make test TEST_NAME=test_bridge

# Run NIF-specific tests
make nif-test
```

## Platform-Specific Instructions

### macOS
```bash
# Install required dependencies
brew install rust openssl

# Build with native architecture (Apple Silicon)
make nif TARGET=aarch64-apple-darwin

# For Intel Macs
make nif TARGET=x86_64-apple-darwin
```

### Linux (Ubuntu)
```bash
# Install build dependencies
sudo apt-get install build-essential libssl-dev

# Build for current architecture
make nif

# Build for 32-bit compatibility (if needed)
make nif TARGET=i686-unknown-linux-gnu

# Build for ARM (Raspberry Pi)
make nif TARGET=arm-unknown-linux-gnueabihf
```

### Windows
```powershell
# Install Visual Studio Build Tools
# Install Rust: rustup-init.exe

# Build with MSVC toolchain
make nif

# The library will be created as priv/yawl_process_mining.dll
```

## Docker Installation

### Option 1: Docker Compose

Create `docker-compose.yml`:
```yaml
version: '3.8'
services:
  yawl-bridge:
    build:
      context: .
      dockerfile: Dockerfile
    volumes:
      - ./examples:/app/examples
      - ./test:/app/test
    command: /app/examples/pm_example.erl
```

Build and run:
```bash
docker-compose build
docker-compose run yawl-bridge
```

### Option 2: Manual Dockerfile

Create `Dockerfile`:
```dockerfile
FROM erlang:25.0-slim

# Install Rust
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
ENV PATH="/root/.cargo/bin:${PATH}"

# Install build dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy source and build
COPY . .
RUN cd rust4pm && make nif && cd ../yawl-erlang-bridge && make

# Set working directory
WORKDIR /app

# Copy examples
COPY examples/ examples/
COPY test/ test/

# Run example
CMD ["erl", "-pa", "examples", "-noshell", "-eval", "pm_example:run_complete()."]
```

Build and run:
```bash
docker build -t yawl-bridge .
docker run -it yawl-bridge
```

## Verification

### Basic Verification
```bash
# Check that all files are built
ls -la yawl-erlang-bridge/priv/
ls -la rust4pm/target/release/

# Test with sample data
cd rust4pm
ls examples/sample_log.xes
```

### Functional Test
```erlang
% In Erlang shell
1> c(yawl_erlang_bridge/ebin).
2> process_mining_bridge:start_link().
3> process_mining_bridge:run_complete("/path/to/sample_log.xes").
```

## Configuration

### Environment Variables

#### NIF Library Path
```bash
# Set custom NIF path
export NIF_PATH="/custom/path/to/priv"
```

#### Erlang VM Options
```bash
# Increase memory for large files
export ERL_FLAGS="+pc unicode +hms 2048M +hmbs 2048M"

# Enable SMP support
export ERL_FLAGS="+smp +pc unicode"
```

### Application Configuration

Create `config/sys.config`:
```erlang
[
    {process_mining_bridge, [
        {nif_path, "priv"},
        {max_file_size, 104857600},  % 100MB
        {timeout, 30000},            % 30 seconds
        {log_level, info}
    ]}
].
```

## Common Issues

### NIF Library Not Loading

**Symptom**: `{error, nif_not_loaded}`

**Solutions**:
```bash
# 1. Check library exists
ls -la priv/yawl_process_mining.*

# 2. Check permissions
chmod +x priv/yawl_process_mining.so

# 3. Verify correct file name for your OS
case os:type() of
    {unix, _} -> "yawl_process_mining.so";
    {win32, _} -> "yawl_process_mining.dll"
end

# 4. Rebuild library
cd rust4pm
make clean
make nif
```

### Compilation Errors

**Symptom**: Compilation fails or warnings

**Solutions**:
```bash
# Clean and rebuild
make clean
make nif

# Check Rust installation
rustc --version
cargo --version

# Update Rust
rustup update

# Check for syntax errors
cargo check --features nif
```

### Runtime Errors

**File Not Found Error**:
```erlang
{error, "Import failed: file not found"}
```
**Solution**: Use absolute paths and verify file exists:
```bash
file:read_file("/absolute/path/to/log.xes")
```

**Memory Issues**:
```erlang
{error, "out_of_memory"}
```
**Solution**:
```bash
# Increase Erlang VM memory
erl +pc unicode +hms 2048M +hmbs 2048M

# Monitor usage
observer:start().
```

**Function Not Implemented**:
```erlang
{error, "Function not yet implemented"}
```
**Solution**: Check implementation status and use available alternatives.

## Performance Optimization

### Large File Handling
```erlang
% For files >100MB, process in batches
process_large_file(FilePath) ->
    case process_mining_bridge:import_xes(FilePath) of
        {ok, Handle} ->
            % Process in chunks
            process_in_batches(Handle, 1000),
            process_mining_bridge:free_handle(Handle)
    end.
```

### Memory Management
```bash
# Monitor memory
observer:start().

# Check GC statistics
erlang:memory({total, allocated}).

# Optimize garbage collection
erl +pc unicode +hms 2048M +hmbs 2048M +ctgb
```

### Parallel Processing
```erlang
% Use spawn for independent operations
process_parallel(Files) ->
    Pids = lists:map(fun(File) ->
        spawn(fun() -> process_file(File) end)
    end, Files),
    [receive {Pid, Result} -> Result end || Pid <- Pids].
```

## Next Steps

1. **Run Examples**: Execute `pm_example:run_complete()` to test the installation
2. **Read API Documentation**: Check `docs/api/` for detailed API reference
3. **Test with Your Data**: Process your own XES files using the bridge
4. **Explore Advanced Features**: Try OCEL processing, conformance checking

## Support

### Resources
- **Documentation**: `/docs/api/` directory
- **Examples**: `/examples/` directory
- **Tests**: `/test/` directory
- **Source Code**: `/src/` directory

### Community Support
1. **GitHub Issues**: Report bugs and request features
2. **Discussions**: Ask questions and share knowledge
3. **Email**: yawl-dev@lists.sourceforge.net

### Debug Mode
```bash
# Build with debug symbols
make build-debug

# Run with verbose logging
erl -pa ebin -eval "process_mining_bridge:start_link(), halt()." +pc unicode
```

## Troubleshooting Flowchart

```
Installation Issue
    ↓
Check Prerequisites
    ↓
Check File Permissions
    ↓
Rebuild NIF Library
    ↓
Run Tests
    ↓
Check Community Support
```

## Version Information

- **Current Version**: 1.0.0
- **YAWL Compatibility**: YAWL v6.0.0
- **Rust Process Mining**: v0.5.2 (RWTH Aachen)
- **Erlang/OTP**: 25.0+

---

For additional information, please refer to the full API documentation in `/docs/api/process_mining_bridge_api.md`.
