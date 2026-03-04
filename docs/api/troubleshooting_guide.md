# YAWL Process Mining Bridge - Troubleshooting Guide

This guide provides solutions for common issues encountered when using the YAWL Process Mining Bridge.

## Quick Reference

| Symptom | Likely Cause | Solution |
|---------|-------------|----------|
| `{error, nif_not_loaded}` | NIF library missing | Rebuild and check permissions |
| `{error, file_not_found}` | Invalid file path | Use absolute paths |
| `{error, invalid_format}` | Corrupted XES file | Validate XML structure |
| `{error, out_of_memory}` | Large file processing | Increase VM memory, use batches |
| Compilation errors | Missing dependencies | Install build tools |

## Common Issues and Solutions

### 1. NIF Library Issues

#### Problem: NIF Library Not Loading
```erlang
% Symptom
{error, nif_not_loaded}
```

#### Diagnostics
```erlang
% Check library status
process_mining_bridge:get_nif_status().

% Check file existence
filelib:is_file("priv/yawl_process_mining.so").
```

#### Solutions

**Option 1: Rebuild the Library**
```bash
# Clean and rebuild
cd rust4pm
make clean
make nif

# Verify files exist
ls -la priv/yawl_process_mining.*
```

**Option 2: Check File Permissions**
```bash
# Fix permissions
chmod +x priv/yawl_process_mining.so

# Verify ownership
ls -la priv/
```

**Option 3: Verify Correct File Name**
```erlang
% Check for correct filename
case os:type() of
    {unix, darwin} -> "libyawl_process_mining.dylib";
    {unix, _} -> "yawl_process_mining.so";
    {win32, _} -> "yawl_process_mining.dll"
end.
```

#### System-Specific Solutions

**Linux**
```bash
# Install missing dependencies
sudo apt-get install build-essential libssl-dev

# Check library path
ldd priv/yawl_process_mining.so

# Fix library path (if needed)
export LD_LIBRARY_PATH=/path/to/libs:$LD_LIBRARY_PATH
```

**macOS**
```bash
# Install dependencies
brew install rust openssl

# Check for correct architecture
file priv/libyawl_process_mining.dylib

# For Apple Silicon
make nif TARGET=aarch64-apple-darwin
```

**Windows**
```powershell
# Check Visual Studio installation
vswhere -latest -property installationPath

# Rebuild with MSVC
make nif

# Check in Windows directory
ls -la priv/yawl_process_mining.dll
```

### 2. File Handling Issues

#### Problem: File Not Found Error
```erlang
{error, "Import failed: file not found"}
```

#### Solutions

**Use Absolute Paths**
```erlang
% Instead of
import_xes("my_log.xes").

% Use
import_xes("/absolute/path/to/my_log.xes").
```

**Verify File Exists**
```bash
# Check file exists
ls -la /path/to/log.xes

# Check permissions
ls -la /path/to/

# Check file size
wc -c /path/to/log.xes
```

**Handle Non-existent Files Gracefully**
```erlang
handle_file_import(Path) ->
    case filelib:is_file(Path) of
        true ->
            process_mining_bridge:import_xes(Path);
        false ->
            {error, {file_not_found, Path}}
    end.
```

#### Problem: Invalid Format Error
```erlang
{error, "XES parse error: invalid XML"}
```

#### Solutions

**Validate XES File**
```bash
# Check XML syntax
xmllint --noout /path/to/log.xes

# Check XES namespace
xmllint --schema schema/XES.xes /path/to/log.xes

# Check file encoding
file /path/to/log.xes
```

**Sample Validation Script**
```bash
#!/bin/bash
# validate_xes.sh

XES_FILE="$1"

if [ ! -f "$XES_FILE" ]; then
    echo "File not found: $XES_FILE"
    exit 1
fi

# Check XML syntax
if ! xmllint --noout "$XES_FILE" 2>/dev/null; then
    echo "Invalid XML syntax in $XES_FILE"
    exit 1
fi

# Check XES structure
if ! grep -q "xes:version" "$XES_FILE"; then
    echo "Not a valid XES file (missing xes:version)"
    exit 1
fi

echo "Valid XES file: $XES_FILE"
```

### 3. Memory Issues

#### Problem: Out of Memory Errors
```erlang
{error, "out_of_memory"}
```

#### Solutions

**Increase Erlang VM Memory**
```bash
# Basic increase
erl +pc unicode +hms 1024M +hmbs 1024M

# Large file handling
erl +pc unicode +hms 4096M +hmbs 4096M

# With SMP support
erl +smp +pc unicode +hms 4096M +hmbs 4096M
```

**Monitor Memory Usage**
```erlang
% Check current memory usage
erlang:memory().

% Check garbage collection
erlang:statistics(garbage_collection).

% Enable memory monitoring
observer:start().
```

**Process Large Files in Batches**
```erlang
process_large_file(FilePath) ->
    case process_mining_bridge:import_xes(FilePath) of
        {ok, Handle} ->
            % Process in smaller chunks
            process_in_batches(Handle, 500),
            process_mining_bridge:free_handle(Handle),
            ok;
        {error, Reason} ->
            {error, Reason}
    end.

process_in_batches(Handle, BatchSize) ->
    % Implement batch processing logic
    ok.
```

#### Problem: Memory Leaks

**Detect Memory Leaks**
```erlang
% Check memory before and after operation
Before = erlang:memory(total).

% Run operations
... your code ...

After = erlang:memory(total),
io:format("Memory increase: ~p bytes~n", [After - Before]).
```

**Fix Resource Management**
```erlang
% Always clean up handles
with_handle(Fun, Path) ->
    case process_mining_bridge:import_xes(Path) of
        {ok, Handle} ->
            try
                Fun(Handle)
            after
                process_mining_bridge:free_handle(Handle)
            end;
        {error, Reason} ->
            {error, Reason}
    end.
```

### 4. Performance Issues

#### Problem: Slow Processing

**Optimize for Large Files**
```bash
# Use SSD storage for faster I/O
# Monitor disk I/O
iostat -x 1
```

**Use Parallel Processing**
```erlang
process_files_parallel(Files) ->
    Self = self(),
    Pids = lists:map(fun(File) ->
        spawn_link(fun() ->
            Result = process_mining_bridge:import_xes(File),
            Self ! {self(), Result}
        end)
    end, Files),
    
    collect_results(Pids, []).

collect_results([], Results) -> Results;
collect_results(Pids, Results) ->
    receive
        {Pid, Result} ->
            NewPids = Pids -- [Pid],
            collect_results(NewPids, [Result | Results])
    after 30000 ->
        timeout
    end.
```

**Cache Results**
```erlang
-define(CACHE_TTL, 3600000). % 1 hour

get_cached_result(Path) ->
    Key = {import_xes, Path},
    case get(Key) of
        undefined ->
            case process_mining_bridge:import_xes(Path) of
                {ok, Handle} ->
                    put(Key, {Handle, erlang:system_time(millisecond)}),
                    {ok, Handle};
                Error ->
                    Error
            end;
        {Handle, Timestamp} ->
            case erlang:system_time(millisecond) - Timestamp > ?CACHE_TTL of
                true ->
                    process_mining_bridge:free_handle(Handle),
                    put(Key, none),
                    get_cached_result(Path);
                false ->
                    {ok, Handle}
            end
    end.
```

### 5. Error Handling Improvements

#### Standardized Error Handling
```erlang
-define(DEFAULT_TIMEOUT, 30000).

safe_import(Path) ->
    try
        case process_mining_bridge:import_xes(Path) of
            {ok, Handle} ->
                {ok, Handle};
            {error, Reason} ->
                log_error(import, Path, Reason),
                {error, Reason}
        catch
            Error:Reason ->
                log_error(import_exception, Path, {Error, Reason}),
                {error, {internal_error, Reason}}
        after
            % Optional cleanup
            ok
        end
    after
        % Timeout handling
        ok
    end.

log_error(Operation, Path, Error) ->
    Timestamp = erlang:universaltime(),
    io:format("[~p] ~p failed for ~p: ~p~n", [Timestamp, Operation, Path, Error]).
```

#### Retry Logic
```erlang
retry_import(Path, Retries) when Retries > 0 ->
    case process_mining_bridge:import_xes(Path) of
        {ok, Handle} -> {ok, Handle};
        {error, Reason} when Retries > 0 ->
            timer:sleep(1000),
            retry_import(Path, Retries - 1);
        {error, Reason} -> {error, Reason}
    end.

retry_import(Path) ->
    retry_import(Path, 3). % 3 retries
```

### 6. Platform-Specific Issues

#### Windows-Specific Issues

**DLL Loading Issues**
```powershell
# Check DLL dependencies
dumpbin /dependents priv/yawl_process_mining.dll

# Fix PATH if needed
$env:PATH += ";C:\path\to\vc\redist\x64\Microsoft.VC140.CRT"
```

**Path Separators**
```erlang
% Use forward slashes in Erlang
import_xes("C:/path/to/log.xes").

% Or use filename module
filename:join(["C:", "path", "to", "log.xes"]).
```

#### macOS-Specific Issues

**Library Path Issues**
```bash
# Check library dependencies
otool -L priv/libyawl_process_mining.dylib

# Set DYLD_LIBRARY_PATH if needed
export DYLD_LIBRARY_PATH=/usr/local/lib:$DYLD_LIBRARY_PATH
```

**Homebrew OpenSSL**
```bash
# Install Homebrew OpenSSL
brew install openssl

# Link OpenSSL
brew link --force openssl
```

#### Linux-Specific Issues

**Shared Library Issues**
```bash
# Check library dependencies
ldd priv/yawl_process_mining.so

# Install missing dependencies
sudo apt-get install libc6-dev
```

**SELinux/AppArmor**
```bash
# Check SELinux status
sestatus

# Temporarily disable for testing
setenforce 0

# Or add appropriate rules
sudo semanage fcontext -a -t textrel_shlib_t "priv/yawl_process_mining.so"
```

## Advanced Troubleshooting

### Debug Mode

**Build with Debug Symbols**
```bash
make build-debug
```

**Run with Debug Output**
```erlang
% Enable logging
logger:set_application_level(process_mining_bridge, debug).

% Run with verbose output
erl -pa ebin -eval "process_mining_bridge:start_link(), halt()." +pc unicode
```

**Rust Debug Symbols**
```bash
# Build debug version
cargo build --features nif

# Run with debugger
rust-gdb target/release/yawl_process_mining
```

### Performance Profiling

**Erlang Profiling**
```erlang
% Enable profiling
system_profile:start().

% Run your code
... operations ...

% Get profile results
system_profile:stop().
system_profile:analysis().
```

**Rust Profiling**
```bash
# Build with profiling
cargo build --features nif --profile=release

# Run with perf
perf record -g ./your_program

# Analyze results
perf report
```

### Memory Analysis

**Erlang Memory Analysis**
```erlang
% Get memory breakdown
erts_debug:memory(allocated_types).

% Check garbage collection
erlang:statistics(garbage_collection).

# Analyze process memory
[{Pid, erlang:memory(process, Pid)} || Pid <- processes()].
```

**Rust Memory Analysis**
```bash
# Run with valgrind
valgrind --leak-check=full ./your_program

# Check memory usage
valgrind --tool=massif ./your_program
```

## FAQ

### Q: What is the maximum file size supported?
A: While there's no hard limit, files larger than 100MB may require:
- Increased Erlang VM memory (`+hms 4096M`)
- Batch processing
- SSD storage for better I/O performance

### Q: Can I run multiple instances of the bridge?
A: Yes, but be mindful of:
- Memory usage (each instance needs separate memory)
- Resource contention
- Handle cleanup to prevent memory leaks

### Q: How do I handle very large event logs?
A: Consider:
- Processing in batches
- Using streaming when available
- Increasing VM memory
- Using SSD storage

### Q: What causes "Function not implemented" errors?
A: Some features may be:
- Not yet implemented in the Rust NIF
- Planned for future releases
- Available through alternative methods

### Q: How do I contribute fixes?
A:
1. Fork the repository
2. Create a feature branch
3. Implement the fix
4. Add tests
5. Submit a pull request

## Contact Support

### Resources
- **Documentation**: `/docs/api/` directory
- **Examples**: `/examples/` directory
- **Tests**: `/test/` directory
- **Source Code**: `/src/` directory

### Community Support
1. **GitHub Issues**: Report bugs and request features
2. **Discussions**: Ask questions and share knowledge
3. **Email**: yawl-dev@lists.sourceforge.net

### Before Reporting Issues
- Check this troubleshooting guide
- Verify the issue is not already reported
- Provide minimal reproduction steps
- Include system information (OS, versions)
- Include error messages and stack traces

## Version Information

- **Current Version**: 1.0.0
- **YAWL Compatibility**: YAWL v6.0.0
- **Rust Process Mining**: v0.5.2 (RWTH Aachen)
- **Erlang/OTP**: 25.0+

---

For additional information, please refer to the full API documentation in `/docs/api/process_mining_bridge_api.md`.
