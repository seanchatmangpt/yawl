# YAWL Edge Case Tests

This directory contains comprehensive edge case tests for the YAWL conformance validation system. These tests verify that the system properly handles various failure scenarios and edge cases that could occur in production environments.

## Test Coverage

### 1. Failed Task Handling (`edge_case_failed_task.erl`)
**Purpose**: Test handling of tasks that return error states
- Malformed task data in OCEL
- Expected error handling and meaningful error messages
- Proper cleanup of resources

### 2. Timeout Handling (`edge_case_timeout.erl`)
**Purpose**: Test timeout scenarios for long-running tasks
- Large OCEL datasets that cause processing delays
- Process timeout detection and cleanup
- 30-second timeout enforcement

### 3. Resource Conflicts (`edge_case_resource_conflict.erl`)
**Purpose**: Test detection of double-booked resources
- Same resource assigned to multiple tasks simultaneously
- Overlapping time intervals for resources
- Conflict detection in DFG generation

### 4. Malformed OCEL Data (`edge_case_malformed_ocel.erl`)
**Purpose**: Test various types of malformed OCEL data
- Missing required fields (events, objects)
- Invalid timestamp formats
- Missing required attributes
- Invalid JSON syntax
- Null values where strings are expected

### 5. Empty Event Logs (`edge_case_empty_events.erl`)
**Purpose**: Test handling of empty or minimal event data
- Completely empty OCEL files
- Empty events arrays
- No activities defined
- Metadata-only files

### 6. Circular Dependencies (`edge_case_circular_deps.erl`)
**Purpose**: Test detection of circular workflow dependencies
- Simple cycles (A → B → A)
- Complex cycles (A → B → C → A)
- Self-loops (A → A)
- Parallel cycles

### 7. Invalid State Transitions (`edge_case_invalid_states.erl`)
**Purpose**: Test detection of invalid workflow state transitions
- Missing start/end states
- Out-of-order sequences
- Duplicate states
- Unreachable states
- Malformed transition logic

### 8. Property-Based Testing (`property_based_tests.erl`)
**Purpose**: Generate random edge cases for robustness testing
- Random malformed OCEL generation
- Large dataset generation
- Random resource conflicts
- Timestamp anomaly generation

## Running the Tests

### Quick Test Run
```bash
cd /Users/sac/yawl/test/edge-cases
./run_edge_cases.sh
```

### Manual Test Execution
```erlang
% From the YAWL root directory
erl -pa test/edge-cases -eval "edge_case_failed_task:run(), halt()." -noshell

% For specific tests
erl -pa test/edge-cases -eval "edge_case_timeout:run(), halt()." -noshell
```

### Property-Based Tests
```bash
erl -pa test/edge-cases -eval "property_based_tests:run_all_property_tests(), halt()." -noshell
```

## Test Output

### Receipt Files
Each test generates a JSON receipt file with detailed results:
- `receipt_<module>.json`: Individual test results
- `edge_case_summary_<timestamp>.json`: Comprehensive test summary

### Receipt Structure
```json
{
  "test": "test_name",
  "status": "status",
  "results": {
    "expected_failures": X,
    "unexpected_successes": Y,
    "conflicts_detected": Z
  },
  "timestamp": "2026-03-03T12:00:00Z"
}
```

### Exit Codes
- `0`: All tests passed
- `1`: Some tests failed

## Test Data

### Generated Test Files
Tests create temporary files in `/tmp/yawl-test/edge-cases/`:
- Input OCEL files for each test case
- Output DFG results
- Receipt files

### Sample Test Data
```json
{
  "events": [
    {
      "id": "event1",
      "activity": "task1",
      "timestamp": "2023-01-01T00:00:00",
      "attributes": {
        "case_id": "case1",
        "resource": "alice"
      }
    }
  ],
  "objects": [],
  "object_types": []
}
```

## Error Handling

### Expected Behaviors
1. **Graceful Degradation**: System should continue processing after detecting errors
2. **Meaningful Messages**: Error messages should be helpful for debugging
3. **Resource Cleanup**: All resources should be properly released
4. **No Silent Failures**: All errors should be detected and reported

### Common Error Types
- `{import_failed, Reason}`: OCEL import errors
- `{dfg_failed, Reason}`: DFG generation errors
- `{conformance_failed, Reason}`: Conformance checking errors
- `{verification_failed, Reason}`: Validation errors

## Integration with CI/CD

### Recommended Pipeline
```yaml
# In your CI pipeline
- name: Run Edge Case Tests
  run: |
    mkdir -p test-results/edge-cases
    cd test/edge-cases
    ./run_edge_cases.sh
    cp receipts/* ../../test-results/edge-cases/

- name: Upload Test Results
  uses: actions/upload-artifact@v2
  with:
    name: edge-case-results
    path: test-results/edge-cases/
```

## Test Metrics

### Quality Gates
- **Coverage**: All edge cases must be tested
- **Error Detection**: 100% of expected errors must be caught
- **No Silent Failures**: 0% undetected errors
- **Performance**: Tests should complete within 5 minutes total

### Success Criteria
- All test cases pass
- Meaningful error messages
- No resource leaks
- Proper cleanup
- Detailed logging

## Future Enhancements

1. **More Edge Cases**: Additional scenarios for:
   - Network failures
   - Database connection issues
   - Memory constraints
   - Concurrent processing conflicts

2. **Performance Testing**: Load testing with large datasets
3. **Stress Testing**: Resource exhaustion scenarios
4. **Integration Testing**: End-to-end workflow validation

## Troubleshooting

### Common Issues
1. **Compilation Errors**: Ensure proper Erlang paths
2. **Missing Dependencies**: Check `jsx` and `process_mining_bridge` availability
3. **Permission Issues**: Ensure write permissions for `/tmp/yawl-test/`

### Debug Mode
Run with verbose output:
```bash
bash -x ./run_edge_cases.sh
```

## Contributing

When adding new edge case tests:

1. Follow the existing pattern in `edge_case_<name>.erl`
2. Include comprehensive documentation
3. Add meaningful error cases
4. Ensure proper cleanup
5. Update this README

### Test Template
```erl
-module(edge_case_new_test).
-export([run/0, run_test/0]).

run() ->
    io:format("Running edge case: New Test~n", []),
    run_test().

run_test() ->
    %% Test implementation here
    {ok, #{test => "new_test", status => "completed"}}
.
```