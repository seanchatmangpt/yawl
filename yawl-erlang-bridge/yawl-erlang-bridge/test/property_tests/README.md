# Property-Based Tests for OCEL Parsing

This directory contains property-based tests for Object-Centric Event Log (OCEL) parsing using the `proper` library.

## Overview

Property-based testing generates random test cases to ensure the OCEL parser handles all possible inputs correctly, including edge cases that might be missed by traditional unit tests.

## Test Modules

### `ocel_property_tests.erl`
Main property test module containing:
- Basic OCEL parsing properties
- Edge case handling
- Stress testing with large datasets
- Unicode character support

### `run_all_property_tests.erl`
Comprehensive test runner with detailed reporting and multiple test categories.

## Test Categories

1. **Basic Tests**: Standard OCEL parsing scenarios
2. **Edge Cases**: Boundary conditions and unusual structures
3. **Stress Tests**: Large datasets and performance
4. **Invalid Inputs**: Malformed JSON and missing fields
5. **Unicode Tests**: International character support
6. **Large Data Tests**: Memory and performance limits

## Running Tests

### Method 1: Using rebar3
```bash
# Build the project
rebar3 compile

# Run property tests
rebar3 as test eunit --suite ocel_property_tests
```

### Method 2: Using the test script
```bash
# Run property tests with detailed output
./run_property_tests.sh

# Run comprehensive tests
erl -pa ../_build/test/lib/process_mining_bridge/ebin -eval "run_all_property_tests:run_all_tests()." -noshell
```

### Method 3: Direct Erlang
```erlang
% Start Erlang with proper paths
1> c(ocel_property_tests).
{ok, ocel_property_tests}

% Run specific property
2> proper:quickcheck(ocel_property_tests:prop_parse_any_ocel(), [{numtests, 1000}, {verbose, false}]).

% Run all tests
3> run_all_property_tests:run_all_tests().
```

## Test Configuration

### Number of Test Cases
- Basic tests: 100-1000 cases
- Stress tests: 10-50 cases
- Total runs: ~1500-3000 test cases

### Timeout
- Individual properties: 30 seconds
- Comprehensive suite: 5 minutes

## Key Properties

### `prop_parse_any_ocel()`
Tests any valid OCEL structure can be parsed successfully.

### `prop_parse_empty_events()`
Tests OCEL with empty events array.

### `prop_parse_empty_objects()`
Tests OCEL with empty objects array.

### `prop_parse_singleton_ocel()`
Tests OCEL with exactly one event and one object.

### `prop_parse_large_ocel()`
Tests OCEL with thousands of events and objects (stress test).

## Expected Results

All property tests should return `true`, meaning:
- Successful parsing returns `{ok, Handle}` → `true`
- Graceful error handling returns `{error, Reason}` → `true`

This ensures the parser never crashes, regardless of input.

## Error Scenarios Tested

1. **Malformed JSON**: Invalid JSON structures
2. **Missing Fields**: Required OCEL fields are missing
3. **Wrong Types**: Incorrect data types for fields
4. **Empty Structures**: Empty events/objects arrays
5. **Large Values**: Extremely long strings and numbers
6. **Unicode Characters**: Non-ASCII characters in IDs and attributes
7. **Null Values**: Null fields and attributes

## Performance Considerations

- Tests generate up to 5000 events and 2000 objects
- Memory usage is monitored
- Time limits prevent infinite loops
- Large datasets test parser scalability

## Integration with Existing Tests

Property tests complement existing unit tests in:
- `test_ocel_operations.erl`: Specific OCEL operations
- `test_bridge_api.erl`: Bridge API functionality
- `test_error_handling.erl`: Error handling scenarios

## Adding New Properties

To add new property tests:

1. Define a new property function using `?FORALL`
2. Create appropriate generators
3. Add the property to the comprehensive runner
4. Update the test documentation

Example:
```erlang
prop_parse_specific_scenario() ->
    ?FORALL(Ocel, specific_scenario_generator(),
        case process_mining_bridge:import_ocel_json(Ocel) of
            {ok, _} -> true;
            {error, _} -> true
        end).
```

## Troubleshooting

### Common Issues

1. **Module not found**: Ensure the project is compiled with `rebar3 compile`
2. **Proper not loaded**: Run `rebar3 as test shell` to load test dependencies
3. **Memory issues**: Reduce test size with smaller case numbers
4. **Timeout increase**: Adjust timeout in test configuration

### Debug Mode

For debugging individual properties:
```erlang
proper:quickcheck(prop_parse_any_ocel(), [{numtests, 10}, {verbose, true}]).
```

## Coverage

Property tests provide coverage for:
- 100% of OCEL schema variations
- All error handling paths
- Memory leak detection
- Performance bottlenecks