# YAWL Actor System Performance Benchmarks

This comprehensive benchmark suite evaluates the performance characteristics of YAWL's actor-based workflow engine across multiple dimensions.

## Overview

The benchmark suite consists of 14 individual tests organized into 5 main categories:

1. **Message Throughput** - Measures actor message processing capabilities
2. **Memory Usage** - Evaluates memory consumption patterns
3. **Scalability** - Tests system performance under increased load
4. **Latency** - Measures message delivery and processing delays
5. **Recovery** - Evaluates system resilience after failures

## Quick Start

### Running the Benchmarks

```bash
# Generate performance report and visualizations
cd yawl-benchmark
python3 src/main/resources/visualization/generate_report.py

# Export data for external analysis
python3 src/main/resources/visualization/export_benchmark_data.py
```

### Viewing Results

The generated reports include:

- `actor_performance_report.md` - Main performance analysis
- `comprehensive_analysis.md` - Detailed optimization recommendations
- `throughput_analysis.png` - Message throughput visualization
- `performance_radar.png` - Overall performance profile
- `scalability_vs_throughput.png` - Performance quadrant analysis
- `exports/` - Data files for external tools

## Benchmark Categories

### 1. Message Throughput Benchmarks

Tests the system's ability to process messages efficiently:

- `SingleThreadedThroughput` - Single-threaded message processing
- `MultiThreadedThroughput` - Multi-threaded message processing
- `BatchThroughput` - Batch processing capabilities

**Target**: >10M messages/sec across 1M actors

### 2. Memory Usage Benchmarks

Evaluates memory consumption patterns:

- `MemoryGrowth` - Memory growth under load
- `MemoryLeakDetection` - Memory leak detection
- `GCPressure` - Garbage collector pressure
- `ActorOverhead` - Memory overhead per actor

**Target**: <10% memory growth under sustained load

### 3. Scalability Benchmarks

Tests system performance with increasing actor counts:

- `LinearScaling` - Linear scaling efficiency
- `ThreadScaling` - Thread-based scaling
- `LoadBalancing` - Load balancing efficiency

**Target**: Linear scaling to 1M actors

### 4. Latency Benchmarks

Measures message delivery and processing times:

- `MessageLatency` - Individual message latency
- `EndToEndLatency` - End-to-end workflow latency

**Target**: p95 < 1ms, p99 < 5ms

### 5. Recovery Benchmarks

Evaluates system resilience:

- `FailureRecovery` - Failure recovery time
- `CrashRecovery` - Crash recovery time

**Target**: <100ms recovery time

## Performance Metrics

### Key Indicators

- **Throughput**: Messages processed per second
- **Memory Growth**: Percentage increase in memory usage
- **Scalability Efficiency**: Percentage of theoretical maximum performance
- **Latency**: Message processing time in milliseconds
- **Recovery Time**: Time to recover from failures in milliseconds

### Performance Tiers

- **Excellent** (✓): Meets or exceeds targets
- **Good** (△): Near targets, minor optimizations needed
- **Needs Improvement** (✗): Significant optimization required

## System Requirements

### Hardware Requirements

- **CPU**: 4+ cores recommended
- **Memory**: 8GB+ RAM
- **Storage**: SSD for optimal performance
- **Network**: 10Gbps recommended

### Software Requirements

- Java 25+ with ZGC
- Python 3.8+ with matplotlib, pandas, numpy
- Maven 3.6+ for building

## Configuration

### JVM Configuration

```bash
# Recommended JVM settings for benchmarking
java -Xms4g -Xmx8g \
     -XX:+UseZGC \
     -XX:+UseCompactObjectHeaders \
     -jar benchmark-runner.jar
```

### Benchmark Parameters

The benchmarks support several configuration parameters:

- `--no-graphs`: Skip visualization generation
- `--no-recommendations`: Skip optimization recommendations
- `--no-raw-data`: Skip raw data export

## Integration

### With CI/CD Pipeline

```yaml
# Example GitHub Actions workflow
- name: Run Actor Benchmarks
  run: |
    cd yawl-benchmark
    mvn test -Dtest=ActorSystemBenchmarks
    python3 src/main/resources/visualization/generate_report.py
    
- name: Upload Results
  uses: actions/upload-artifact@v2
  with:
    name: benchmark-results
    path: actor-benchmark-reports/
```

### With Monitoring Systems

The exported data can be integrated with:

- **Prometheus**: Using the JSON export
- **Grafana**: For dashboards and visualizations
- **ELK Stack**: For log analysis and trends
- **ML Platforms**: Using the ML-formatted data

## Troubleshooting

### Common Issues

1. **Memory Issues**
   - Increase JVM heap size
   - Monitor garbage collection behavior
   - Check for memory leaks

2. **Performance Degradation**
   - Check thread pool configuration
   - Monitor lock contention
   - Verify network latency

3. **Benchmark Failures**
   - Verify dependencies are installed
   - Check disk space
   - Review JVM configuration

### Debug Mode

```bash
# Run with debug output
python3 src/main/resources/visualization/generate_report.py --verbose
```

## Contributing

### Adding New Benchmarks

1. Create a new benchmark class extending the base pattern
2. Implement the benchmark logic
3. Add to appropriate benchmark suite
4. Update documentation

### Performance Testing Guidelines

- Always run in a controlled environment
- Use consistent JVM settings
- Monitor system resources during execution
- Document any deviations from expected behavior

## License

This benchmark suite is part of the YAWL project and is distributed under the GNU Lesser General Public License.

## Support

For issues and questions:
- GitHub Issues: [YAWL Repository](https://github.com/yawlfoundation/yawl)
- Documentation: [YAWL Wiki](https://github.com/yawlfoundation/yawl/wiki)
- Performance Analysis: [Performance Guide](comprehensive_analysis.md)

---
*Last Updated: 2026-02-28*
*Version: 6.0.0*
