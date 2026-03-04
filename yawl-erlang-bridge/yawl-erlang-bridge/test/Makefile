# Makefile for YAWL Process Mining Bridge Test Suite

.PHONY: all test clean compile escript jtbd help

# Default target
all: compile test

# Compile all Erlang files
compile:
	@echo "Compiling test modules..."
	@erl -pa ebin -pa _build/default/lib/*/ebin \
		-eval 'c(test_fixtures), c(jtbd_runner), c(jtbd_3_constraints), c(test_nif_loading), c(test_bridge_api), c(test_error_handling), c(test_ocel_operations), c(test_suite), c(property_tests/ocel_property_tests), c(run_all_property_tests), halt(0)'
	@echo "Compilation complete."

# Run all tests
test:
	@echo "Running all tests..."
	@erl -pa ebin -pa _build/default/lib/*/ebin \
		-eval 'c(test_suite), test_suite:run_all_tests(), halt(0)'

# Run property tests
property-test:
	@echo "Running property-based tests..."
	@erl -pa ebin -pa _build/default/lib/*/ebin \
		-eval 'c(property_tests/ocel_property_tests), ocel_property_tests:run_property_tests(), halt(0)'

# Run comprehensive property tests
property-test-all:
	@echo "Running comprehensive property tests..."
	@erl -pa ebin -pa _build/default/lib/*/ebin \
		-eval 'c(run_all_property_tests), run_all_property_tests:run_all_tests(), halt(0)'

# Run specific property test
property-test/%:
	@echo "Running property test: $*..."
	@erl -pa ebin -pa _build/default/lib/*/ebin \
		-eval 'c(property_tests/ocel_property_tests), proper:quickcheck(property_tests/ocel_property_tests:$*(), [{numtests, 100}, {verbose, false}]), halt(0)'

# Run specific test module
test/%:
	@echo "Running test: $*..."
	@erl -pa ebin -pa _build/default/lib/*/ebin \
		-eval 'c(test_suite), test_suite:run_specific_tests([$*]), halt(0)'

# Run tests using escript
escript:
	@echo "Running tests with escript..."
	./escript_runner.escript all

# Run Common Test suite
common-test:
	@echo "Running Common Test suite..."
	ct_run -dir test -suite process_mining_bridge_SUITE -v

# Run JTBD tests
jtbd:
	@echo "Running JTBD tests..."
	@erl -pa ebin -pa _build/default/lib/process_mining_bridge/ebin \
		-eval 'c(jtbd_runner), jtbd_runner:run_all(), halt(0)'

# Clean compiled files
clean:
	@echo "Cleaning up..."
	@find . -name "*.beam" -delete
	@find test -name "*.tmp" -delete
	@find test -name "*_export.*" -delete
	@echo "Cleanup complete."

# Generate test report
report:
	@echo "Generating test report..."
	@erl -pa ebin -pa _build/default/lib/*/ebin \
		-eval 'c(test_suite), test_suite:run_all_tests(), halt(0)' > test_report.txt
	@echo "Report saved to test_report.txt"

# Show help
help:
	@echo "YAWL Process Mining Bridge Test Suite"
	@echo "======================================"
	@echo ""
	@echo "Available targets:"
	@echo "  all         - Compile and run all tests"
	@echo "  compile     - Compile all test modules"
	@echo "  test        - Run all EUnit tests"
	@echo "  test/%      - Run specific test module (e.g., test/test_bridge_api)"
	@echo "  jtbd        - Run JTBD tests"
	@echo "  escript     - Run tests using escript runner"
	@echo "  common-test - Run Common Test suite"
	@echo "  clean       - Clean compiled files and temporary files"
	@echo "  report      - Generate test report"
	@echo "  help        - Show this help message"
	@echo ""
	@echo "Property Test Targets:"
	@echo "  property-test        - Run basic property tests"
	@echo "  property-test-all    - Run comprehensive property tests"
	@echo "  property-test/%      - Run specific property test"
	@echo ""
	@echo "Available test modules:"
	@echo "  - test_nif_loading"
	@echo "  - test_bridge_api"
	@echo "  - test_error_handling"
	@echo "  - test_ocel_operations"
	@echo "  - test_suite"
	@echo "  - jtbd_1_dfg_discovery"
	@echo "  - jtbd_3_constraints"
	@echo ""
	@echo "Example usage:"
	@echo "  make test             # Run all tests"
	@echo "  make test/test_bridge_api  # Run API tests only"
	@echo "  make jtbd             # Run JTBD tests"
	@echo "  make escript          # Run with escript runner"
	@echo "  make property-test    # Run property tests"
	@echo "  make property-test-all # Run comprehensive property tests"
	@echo ""
	@echo "Environment:"
	@echo "  Ensure Erlang/OTP is installed"
	@echo "  NIF library should be in priv/ directory"
	@echo "  Test data in test/test_data/ directory"