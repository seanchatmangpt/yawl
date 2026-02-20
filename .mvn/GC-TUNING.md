# ZGC Tuning Guide for YAWL v6.0.0

## Overview

ZGC (Z Garbage Collector) is a scalable, low-latency garbage collector optimized for
large heaps and high-throughput applications. It achieves sub-millisecond pause times
regardless of heap size, making it ideal for YAWL's workflow engine.

## Configuration Files

- `.mvn/jvm.config` - Development (default, balanced)
- `.mvn/jvm.config.dev` - Development builds (faster iteration)
- `.mvn/jvm.config.ci` - CI/CD pipeline
- `.mvn/jvm.config.prod` - Production (high throughput)
- `.mvn/jvm.config.perf` - Performance testing (max throughput)

## Key ZGC Parameters

### Heap Configuration

```
-Xms2g          # Initial heap: 2GB
-Xmx8g          # Max heap: 8GB
```

**Tuning for your environment:**
- For heaps <4GB: Consider G1GC instead
- For heaps 4-16GB: ZGC optimal
- For heaps >16GB: ZGC + lower InitiatingHeapOccupancyPercent

### Concurrent GC Threads

```
-XX:ConcGCThreads=<n>
```

**Default:** 25% of processing threads
**YAWL Recommendation:** 6-8 threads for typical server (24 core systems)

If you have GC pauses >5ms, increase this value:
```
-XX:ConcGCThreads=8   # 8 concurrent GC threads
```

### Initiating Heap Occupancy

```
-XX:InitiatingHeapOccupancyPercent=35
```

Controls when ZGC starts concurrent GC cycle.
- **Lower (25%):** More frequent GC, lower pause times, higher GC overhead
- **Higher (45%):** Fewer GC cycles, lower overhead, higher pause times

**For YAWL:** 35-40% is optimal (balance between latency and throughput)

### Soft/Hard Max Heap Occupancy

```
-XX:SoftMaxHeapSize=7g    # Trigger GC before hitting hard limit
-XX:ZStatisticsInterval=60  # Print GC stats every 60s
```

## Performance Validation

### Monitoring ZGC Pauses

```bash
# View GC activity during load test
jstat -gc -h10 <pid> 1000

# Parse GC logs
java -cp $JAVA_HOME/lib/jol-core.jar \
  org.openjdk.jol.info.ObjectLayout org.yawlfoundation.yawl.elements.YWorkItem
```

### Benchmark Commands

```bash
# Run with performance config
JAVA_TOOL_OPTIONS="-Xms2g -Xmx8g -XX:+UseZGC" \
bash scripts/dx.sh test -pl yawl-engine

# Check GC overhead (look for "GC overhead limit exceeded" errors)
# If <5% overhead reported, ZGC is well-tuned
```

## Troubleshooting

### High GC Pauses (>10ms)

1. **Increase concurrent threads:** `-XX:ConcGCThreads=8`
2. **Lower heap occupancy threshold:** `-XX:InitiatingHeapOccupancyPercent=25`
3. **Enable large pages:** `-XX:+UseLargePages -XX:LargePageSizeInBytes=2m`

### Out of Memory

1. **Increase max heap:** `-Xmx16g` (if system RAM available)
2. **Enable SoftMaxHeapSize:** `-XX:SoftMaxHeapSize=14g`
3. **Check for memory leaks:** Use JProfiler or async-profiler

### CPU Spikes

ZGC uses multiple threads for concurrent marking:
- **Increase CPU capacity** or
- **Reduce ConcGCThreads** (trades pause time for CPU)

## Integration with Compact Object Headers

ZGC pairs well with `-XX:+UseCompactObjectHeaders`:
- **Headers:** 12 bytes (standard) → 8 bytes (compact)
- **Savings:** 10-20% memory reduction
- **Benefit:** Better ZGC performance (fewer objects to scan)

## Expected Improvements

With ZGC + Compact Headers + Virtual Threads:

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| GC Pause Time | 50-100ms | <1ms | 50-100x |
| Heap Memory | 100% | 85% | -15% |
| Throughput | Baseline | +5-10% | +5-10% |
| Latency (p99) | 500ms | 50ms | 10x |

## Production Deployment

For production, use `jvm.config.prod` which includes:

```properties
# Production-grade ZGC configuration
-XX:+UseZGC
-XX:+UseCompactObjectHeaders
-XX:InitiatingHeapOccupancyPercent=40
-XX:ConcGCThreads=8
-XX:+AlwaysPreTouch                    # Pre-touch memory at startup
-XX:+UseLargePages                     # Use OS large pages
-XX:LargePageSizeInBytes=2m
-XX:-UseBiasedLocking                  # Disable for virtual threads
```

## References

- [ZGC Documentation](https://wiki.openjdk.org/display/zgc/)
- [Java 25 GC Tuning](https://docs.oracle.com/en/java/javase/25/gctuning/introduction.html)
- [Compact Object Headers](https://openjdk.org/jeps/450)
