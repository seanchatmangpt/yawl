# YAWL MCP-A2A Performance Benchmarking

This directory contains performance benchmark results and analysis for the YAWL MCP-A2A MVP application.

## Quick Start

```bash
# Run full benchmark suite
./scripts/performance/run-benchmarks.sh --full --report

# Run quick benchmarks (faster, fewer iterations)
./scripts/performance/run-benchmarks.sh --quick

# Run JUnit performance tests only
./scripts/performance/run-benchmarks.sh --junit

# Generate report from existing results
./scripts/performance/run-benchmarks.sh --report
```

## Benchmark Categories

### 1. Component-Level Benchmarks

| Benchmark | File | Description |
|-----------|------|-------------|
| MCP Server Response Times | `McpA2APerformanceBenchmark.java` | Tool call latency, capabilities construction |
| A2A Message Processing | `A2APerformanceTest.java` | Message parsing, JWT operations |
| Database Impact | `DatabaseImpactTest.java` | Connection pooling, query latency |
| Memory Footprint | `MemoryFootprintBenchmark.java` | Per-session memory, GC impact |

### 2. End-to-End Benchmarks

| Benchmark | File | Description |
|-----------|------|-------------|
| Complete Workflow | `EndToEndWorkflowBenchmark.java` | Launch → Checkout → Complete cycle |
| Cross-Service Communication | `EndToEndWorkflowBenchmark.java` | MCP → A2A → YAWL latency |
| Tool Call Chaining | `EndToEndWorkflowBenchmark.java` | Multiple sequential tool calls |
| Handoff Protocol | `A2APerformanceTest.java` | Agent-to-agent work item transfer |

### 3. Transport Benchmarks

| Benchmark | File | Description |
|-----------|------|-------------|
| STDIO Transport | `NetworkTransportBenchmark.java` | Local CLI performance |
| HTTP/SSE Transport | `NetworkTransportBenchmark.java` | Cloud deployment performance |
| Compression | `NetworkTransportBenchmark.java` | GZIP effectiveness analysis |

## Performance Targets

### Latency Targets (P95)

| Component | Target | Critical |
|-----------|--------|----------|
| MCP Tool Call | <50ms | <100ms |
| A2A Message Processing | <100ms | <200ms |
| Database Query | <20ms | <50ms |
| JWT Token Generation | <10ms | <20ms |
| Handoff Protocol | <200ms | <500ms |

### Throughput Targets

| Component | Target | Critical |
|-----------|--------|----------|
| MCP Logging | >10,000 ops/sec | >5,000 ops/sec |
| Concurrent Sessions | 100+ | 50+ |
| Message Processing | >1,000 msg/sec | >500 msg/sec |

### Resource Targets

| Resource | Target | Critical |
|----------|--------|----------|
| Memory per Session | <10KB | <20KB |
| GC Pause Time | <10ms | <50ms |
| Connection Pool Wait | <10ms | <50ms |

## Profiling Tools

### VisualVM

```bash
# Start with VisualVM profiling
visualvm --openjmx localhost:9010 &
java -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9010 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -jar yawl-mcp-a2a-app.jar
```

### Async Profiler

```bash
# CPU profiling (30 seconds)
./profiler.sh -d 30 -f cpu_profile.html <pid>

# Memory allocation profiling
./profiler.sh -d 30 -e alloc -f alloc_profile.html <pid>

# Lock contention profiling
./profiler.sh -d 30 -e lock -f lock_profile.html <pid>
```

### JFR (Java Flight Recorder)

```bash
# Start JFR recording
java -XX:StartFlightRecording=duration=60s,filename=recording.jfr \
     -jar yawl-mcp-a2a-app.jar

# Analyze with JDK Mission Control
jmc recording.jfr
```

## Interpreting Results

### Latency Percentiles

- **P50 (Median)**: Typical user experience
- **P90**: 90% of requests faster than this
- **P95**: SLO target (95% compliance)
- **P99**: Tail latency, critical for SLAs
- **P99.9**: Worst case outliers

### Throughput Metrics

- **Ops/sec**: Operations completed per second
- **Msg/sec**: Messages processed per second
- **RPS**: Requests per second (HTTP)

### Memory Metrics

- **Heap Used**: Current heap utilization
- **GC Pauses**: Garbage collection pause times
- **Allocation Rate**: Object creation rate

## Troubleshooting

### High Latency

1. Check GC logs for long pauses
2. Verify database connection pool sizing
3. Review network latency with `ping` and `traceroute`
4. Profile CPU hotspots with Async Profiler

### Low Throughput

1. Check thread pool sizing
2. Verify virtual threads are enabled
3. Review lock contention
4. Check for I/O bottlenecks

### Memory Issues

1. Analyze heap dump with Eclipse MAT
2. Check for memory leaks
3. Review GC configuration
4. Consider increasing heap size

## Reports

Performance reports are generated in this directory with timestamps:

- `PERFORMANCE_REPORT_YYYYMMDD_HHMMSS.md` - Main report
- `jmh-results-*.json` - Raw JMH data
- `junit-perf-*.txt` - JUnit test output
