# Optimization Test Data Fixtures

This directory contains test fixtures used by the regression testing framework.

## Structure

### impact-graph-data/
Test cases for impact graph validation.
- `source-change.java`: File with semantic change
- `dependency-chain.json`: Test dependency graph

### cache-data/
Test results for cache validation.
- `fresh-results.json`: Fresh test run results
- `cached-results.json`: Results from cache hit

### semantic-test-files/
Java files for semantic hashing validation.
- `formatted-original.java`: Original code
- `formatted-whitespace.java`: Same code, reformatted
- `formatted-semantic-change.java`: Code with semantic change

### cluster-data/
Test time data for clustering algorithm.
- `test-times.json`: Sample test execution times
- `clusters.json`: Expected cluster assignments

### test-times/
Historical test execution times for TIP training.
- `training-data.json`: Time series of test durations
- `actual-times.json`: Ground truth times

### predictions/
TIP prediction validation data.
- `predictions.json`: Model predictions
- `actuals.json`: Actual test times

## Usage

These fixtures are automatically loaded by test-optimizations.sh.
Modify only if changing test scenarios or algorithms.

