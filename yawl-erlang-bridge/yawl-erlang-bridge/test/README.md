# YAWL Process Mining Bridge Test Suite

This directory contains comprehensive tests for the Erlang bridge to rust4pm (YAWL Process Mining integration). All tests use real integration - no mocks are used.

## Test Structure

### Test Modules

1. **`test_nif_loading.erl`** - Tests NIF loading and initialization
   - NIF library loading and fallback behavior
   - Module initialization performance
   - Function availability verification

2. **`test_bridge_api.erl`** - Tests all public API functions
   - Bridge lifecycle (start/stop)
   - XES import/export operations
   - OCEL import/export operations
   - Process discovery algorithms
   - Petri net operations
   - Conformance checking
   - Event log statistics

3. **`test_error_handling.erl`** - Tests error handling paths
   - Invalid input validation
   - Missing dependency handling
   - Resource limits
   - Network and file system errors
   - Concurrency and memory handling
   - Timeout behavior

4. **`test_ocel_operations.erl`** - Tests OCEL-specific operations
   - OCEL JSON import/export
   - OCEL validation
   - Object and event operations
   - Statistics and discovery
   - Performance testing
   - Data integrity verification

5. **`test_fixtures.erl`** - Test data and utilities
   - Sample OCEL and XES data
   - Test file creation and cleanup
   - Helper functions for test data

6. **`test_suite.erl`** - Test coordinator
   - Runs all tests or specific modules
   - Reports test summaries
   - Manages application lifecycle

7. **`process_mining_bridge_SUITE.erl`** - Common Test suite
   - Existing Common Test implementation
   - Can be run with `ct_run` command

## Running Tests

### Method 1: EUnit (Recommended for development)

```bash
# Start Erlang shell with proper paths
erl -pa ebin -pa _build/default/lib/*/ebin

# In the Erlang shell:
c(test_suite).
test_suite:run_all_tests().
```

### Method 2: Individual test modules

```erlang
% Run specific test modules
test_suite:run_specific_tests([test_nif_loading, test_bridge_api]).
test_suite:run_specific_tests([test_error_handling, test_ocel_operations]).
```

### Method 3: Common Test (for CI/CD)

```bash
# Run with Common Test
ct_run -dir test -suite process_mining_bridge_SUITE -v
```

### Method 4: Escript (for automated testing)

```bash
# Run using the escript
escript test/escript_runner.escript all
```

## Test Data

### Sample Files

- `sample_log.xes` - Sample XES event log in `../../rust4pm/examples/`
- `sample_ocel.json` - Sample OCEL JSON (created dynamically)

### Test Data Generation

The test suite includes functions to generate various test scenarios:

```erlang
% Generate OCEL with specific number of events/objects
test_fixtures:sample_ocel_json(100)  % 100 events, 100 objects

% Create temporary test files
Path = test_fixtures:create_temp_file(content, "json")
test_fixtures:cleanup_temp_file(Path)
```

## Test Categories

### 1. NIF Loading Tests
- Verify NIF library loads correctly
- Test fallback behavior when NIF is unavailable
- Check performance of initialization

### 2. API Integration Tests
- Test all public functions with real data
- Verify import/export workflows
- Test statistics and discovery algorithms

### 3. Error Handling Tests
- Validate input validation
- Test resource limit handling
- Verify graceful degradation

### 4. OCEL Operations Tests
- Import/export OCEL JSON files
- Test object-centric operations
- Verify data integrity

### 5. Performance Tests
- Large file processing
- Concurrent operations
- Memory usage patterns

## Prerequisites

1. **Erlang/OTP 22+** - Required for EUnit and Common Test
2. **Rust NIF Library** - Build `yawl_process_mining.so` from rust4pm
3. **Test Dependencies** - Ensure all dependencies are compiled
4. **Sample Data** - Sample XES file in rust4pm/examples/

## Building the Test Suite

```bash
# Compile all test modules
erl -make

# Or compile individually
c(test_nif_loading).
c(test_bridge_api).
c(test_error_handling).
c(test_ocel_operations).
c(test_suite).
```

## Expected Test Results

### When NIF is available:
- All tests should pass
- Real integration with Rust backend
- Performance metrics collected

### When NIF is not available:
- NIF stub tests pass with expected {error, nif_not_loaded}
- Fallback behavior tests pass
- Unsupported operation tests throw exceptions

## Test Coverage Goals

- **NIF Loading**: 100% coverage of loading paths
- **API Functions**: 100% coverage of public APIs
- **Error Handling**: 100% coverage of error paths
- **OCEL Operations**: 100% coverage of OCEL features
- **Integration**: Full workflow testing

## Troubleshooting

### Common Issues

1. **NIF not loaded**
   - Check `priv/yawl_process_mining.so` exists
   - Verify library architecture matches Erlang
   - Check logs for load errors

2. **Test data not found**
   - Ensure sample XES file exists
   - Check file permissions
   - Verify paths in test_fixtures.erl

3. **Mnesia issues**
   - Ensure Mnesia schema is created
   - Check table permissions
   - Verify disk space

### Debug Mode

```erlang
% Enable debug logging
logger:set_level(debug).

% Run tests with verbose output
test_suite:run_specific_tests([test_bridge_api]).
```

## Continuous Integration

The test suite is designed for CI/CD integration:

```yaml
# Example GitHub Actions step
- name: Run Tests
  run: |
    erl -pa ebin -pa _build/default/lib/*/ebin \
        -eval 'c(test_suite), test_suite:run_all_tests(), halt(0)'
```

## Adding New Tests

1. **Identify test area** - Determine which module needs new tests
2. **Create test functions** - Follow naming convention `test_name/0`
3. **Use EUnit macros** - `?_test()` for simple tests, setup/teardown for complex
4. **Add to test_suite** - Register new test module if needed
5. **Test real integration** - Use actual data, no mocks

## Best Practices

1. **No mocks** - Always test with real integration
2. **Resource cleanup** - Clean up temporary files and handles
3. **Error coverage** - Test both success and failure paths
4. **Performance** - Include performance metrics for large datasets
5. **Data integrity** - Verify data isn't corrupted during processing

## License

These tests are part of the YAWL project and follow the same license terms.

## Contributing

1. Follow existing test patterns
2. Add test documentation
3. Ensure tests pass in CI environment
4. Test both NIF and fallback scenarios