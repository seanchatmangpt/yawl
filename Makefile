# YAWL SPARQL Benchmark Makefile

.PHONY: help benchmark test datasets clean-all clean-results

# Default target
help:
	@echo "YAWL SPARQL Benchmark Suite"
	@echo ""
	@echo "Available targets:"
	@echo "  benchmark    - Run all SPARQL engine benchmarks"
	@echo "  benchmark-<engine>  - Run specific engine benchmark"
	@echo "    (qlever-http, qlever-embedded, oxigraph)"
	@echo "  datasets     - Generate test RDF datasets"
	@echo "  test         - Run benchmark tests"
	@echo "  clean-all    - Clean all generated files"
	@echo "  clean-results - Clean benchmark results"
	@echo ""
	@echo "Examples:"
	@echo "  make benchmark"
	@echo "  make benchmark-qlever-http"
	@echo "  make datasets"

# Run all benchmarks
benchmark:
	./scripts/benchmark-qlever.sh

# Run individual benchmarks
benchmark-qlever-http:
	./scripts/benchmark-qlever.sh qlever-http

benchmark-qlever-embedded:
	./scripts/benchmark-qlever.sh qlever-embedded

benchmark-oxigraph:
	./scripts/benchmark-qlever.sh oxigraph

# Generate test datasets
datasets:
	./scripts/generate-test-data.sh

# Run tests
test:
	mvn test -pl yawl-benchmark

# Clean everything
clean-all: clean-results
	mvn clean -pl yawl-benchmark
	rm -rf datasets/

# Clean only results
clean-results:
	rm -rf benchmark-results/
	rm -f *.json *.log

# View results
view-results:
	@if [ -d "benchmark-results" ]; then \
		echo "Benchmark Results:"; \
		echo "=================="; \
		for file in benchmark-results/*.json; do \
			echo "$(basename $$file):"; \
			jq '.benchmarks[0].benchmark, .benchmarks[0].score' $$file 2>/dev/null || \
			echo "  Raw JSON format"; \
			echo ""; \
		done; \
	else \
		echo "No benchmark results found. Run 'make benchmark' first."; \
	fi

# Check prerequisites
check:
	@echo "Checking prerequisites..."
	@command -v mvn >/dev/null 2>&1 && echo "✓ Maven" || echo "✗ Maven not found"
	@command -v java >/dev/null 2>&1 && echo "✓ Java" || echo "✗ Java not found"
	@curl -s -f http://localhost:7001/api/ >/dev/null 2>&1 && echo "✓ QLever HTTP" || echo "✗ QLever HTTP not available"
	@curl -s -f http://localhost:8083/sparql/health >/dev/null 2>&1 && echo "✓ Oxigraph" || echo "✗ Oxigraph not available"
