# YAWL SPARQL Engine Benchmark Suite

This benchmark suite compares different SPARQL engine implementations used in YAWL:
- QLever HTTP (remote)
- QLever Embedded (FFI)
- Oxigraph (Rust FFI)

## Quick Start

### Prerequisites

1. Java 25+
2. Maven 3.9+
3. One of the following SPARQL engines:
   - QLever (HTTP mode) - running on port 7001
   - Oxigraph/Yawl-Native - running on port 8083

### Running Benchmarks

```bash
# Run all engines
./scripts/benchmark-qlever.sh

# Run specific engine
./scripts/benchmark-qlever.sh qlever-http
./scripts/benchmark-qlever.sh qlever-embedded
./scripts/benchmark-qlever.sh oxigraph

# Run with custom JMH parameters
mvn -pl yawl-benchmark clean install
java -jar yawl-benchmark/target/benchmarks.jar QLeverBenchmark -wi 5 -i 10 -f 2
```

### Expected Results

Results are saved in JSON format with execution times and percentiles (p50, p95, p99).

## Benchmarks

### QLeverBenchmark

Tests four query patterns:

1. **simpleConstruct**: Basic CONSTRUCT queries with simple patterns
2. **complexJoin**: Queries with joins and multiple predicates
3. **largeResult**: Queries returning large result sets (2000+ triples)
4. **patternJoin**: Specific workflow pattern queries with case/task relationships

## Dataset Generation

Generate test RDF datasets:

```bash
# Generate small dataset (1K triples)
mvn exec:java -pl yawl-benchmark -Dexec.mainClass="org.yawlfoundation.yawl.benchmark.sparql.RdfDataGenerator"

# Or use standalone
java -cp target/classes:target/dependency/* org.yawlfoundation.yawl.benchmark.sparql.RdfDataGenerator

# Datasets are generated in the datasets/ directory:
# - datasets/dataset-1000.ttl    (1K triples)
# - datasets/dataset-10000.ttl   (10K triples)
# - datasets/dataset-100000.ttl  (100K triples)
# - datasets/dataset-1000000.ttl (1M triples)
```

## JMH Configuration

The benchmarks use the following JMH settings:
- Warmup: 3 iterations, 1 second each
- Measurement: 5 iterations, 1 second each
- Forks: 1 (reduces JVM warmup overhead)
- Time unit: milliseconds
- Mode: AverageTime

## Output

Results are saved in `benchmark-results/` directory:
- `qlever-http-results.json`
- `qlever-embedded-results.json`
- `oxigraph-results.json`

Each result file contains:
- Benchmark name and mode
- Score (mean time)
- Error (standard error)
- Percentiles (p50, p75, p95, p99, p999)
- Primary metric name

## Troubleshooting

### Engine Not Available

If engines are not running:

1. **QLever HTTP**:
   ```bash
   # Start QLever on port 7001
   docker run -p 7001:7001 qlever/qlever:latest
   ```

2. **Oxigraph**:
   ```bash
   # Start yawl-native service with Oxigraph
   ./scripts/start-yawl-native.sh
   ```

3. **QLever Embedded**:
   - Requires native libraries: `qlever_java.dylib` (macOS) or `qlever_java.dll` (Windows)
   - Build from source: `cmake -DCMAKE_BUILD_TYPE=Release && make`

### Build Issues

If benchmark fails to build:

```bash
# Clean and rebuild
mvn -pl yawl-benchmark clean install -U

# Check dependencies
mvn dependency:tree -pl yawl-benchmark
```

### Performance Tips

1. **Memory**: Use sufficient heap (4GB+)
2. **GC**: ZGC or G1GC recommended
3. **CPU**: More cores = better parallel benchmarking
4. **Data**: Pre-load datasets for consistent results

## Integration with CI

Add to your CI pipeline:

```yaml
# Example GitHub Actions
- name: Run SPARQL Benchmarks
  run: |
    ./scripts/benchmark-qlever.sh
    # Upload results
    tar -czf benchmark-results.tar.gz benchmark-results/
    artifact upload benchmark-results.tar.gz

- name: Benchmark Analysis
  run: |
    python scripts/analyze-results.py benchmark-results/*.json
```

## License

This benchmark suite is part of YAWL and licensed under the GNU Lesser General Public License v3.0.

## Contributing

1. Add new benchmark patterns
2. Support additional SPARQL engines
3. Improve dataset realism
4. Add performance analysis tools

## References

- [JMH (Java Microbenchmark Harness)](https://openjdk.org/projects/code-tools/jmh/)
- [QLever SPARQL Engine](https://qlever.cs.uni-freiburg.de/)
- [Oxigraph RDF Store](https://oxigraph.org/)
- [SPARQL 1.1 Query Language](https://www.w3.org/TR/sparql11-query/)
