# YAWL Engine Dockerfile (Java 25 Optimized)

This Dockerfile configures the YAWL workflow engine with Java 25 optimizations for production deployment.

## Features

- **Java 25 with virtual threads** for concurrent case execution
- **Shenandoah GC with compact heuristics** for low-latency garbage collection
- **CDS (Class Data Sharing)** and **AOT (Ahead-of-Time)** cache support
- **Compact object headers** for reduced memory overhead
- **TLS 1.3+ enforcement** (CNSA compliant)
- **Non-root user** security

## Build Instructions

```bash
# Build the production image
docker build -t yawl-engine:6.0.0 -f docker/production/Dockerfile.engine .

# Build with specific version
docker build \
  --build-arg VERSION=6.0.0 \
  --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
  -t yawl-engine:6.0.0 \
  -f docker/production/Dockerfile.engine .
```

## Java 25 Optimizations

### GC Configuration
- **Shenandoah GC**: Concurrent garbage collection with compact heuristics
- **String deduplication**: Reduces memory footprint for repeated strings
- **Max GC pause**: 50ms for predictable performance

### Memory Management
- **Heap size**: 4GB initial, 8GB maximum
- **Container awareness**: Respects container memory limits
- **Compact object headers**: Reduces memory overhead per object

### CDS and AOT
- **CDS archive**: `/opt/yawl/classes.jsa` (optional)
- **AOT cache**: `/opt/yawl/aot.cache` (optional)
  - These are copied from the build stage if available

### Virtual Threads
- **Thread parallelism**: 200 worker threads
- **Thread pool**: 256 max threads
- **Thread pinning tracking**: Enabled for debugging

## Security Features

- **Non-root user**: Runs as `dev` user (UID 1000)
- **TLS 1.3+ enforcement**: Disables weak protocols and ciphers
- **Health checks**: Monitors application availability
- **Isolated directories**: `/app` and `/opt/yawl` with proper ownership

## Runtime Configuration

### Environment Variables

```bash
# Database configuration
DB_TYPE=h2

# Timezone
TZ=UTC

# Spring profiles
SPRING_PROFILES_ACTIVE=production
```

### Port Mapping

```bash
# HTTP port
docker run -p 8080:8080 yawl-engine:6.0.0

# HTTP and HTTPS ports
docker run -p 8080:8080 -p 8443:8443 yawl-engine:6.0.0
```

## Health Checks

The container includes a health check that:
- Runs every 30 seconds
- Times out after 10 seconds
- Uses HTTP GET requests to check endpoints:
  - `/health` (YAWL native health check)
  - `/actuator/health/liveness` (Spring Boot health check)

## Performance Tuning

### For High Memory Systems
```bash
docker run -e JAVA_OPTS="-Xms8g -Xmx16g" yawl-engine:6.0.0
```

### For Low Latency Requirements
```bash
docker run -e JAVA_OPTS="-XX:MaxGCPauseMillis=20" yawl-engine:6.0.0
```

## Troubleshooting

### Common Issues

1. **Out of Memory**: Check heap dumps in `/app/logs/heap-dump.hprof`
2. **Health check failures**: Verify port 8080 is accessible
3. **Permission issues**: Files owned by `dev` user (UID 1000)

### Monitoring

```bash
# Check logs
docker logs yawl-engine:6.0.0

# Monitor memory usage
docker stats yawl-engine:6.0.0

# Access health endpoint
curl http://localhost:8080/health
```

## File Structure

```
/app/
├── yawl-engine.jar          # Main application JAR
├── logs/                    # Application logs
├── data/                    # Application data
├── temp/                    # Temporary files
├── config/                  # Configuration files
└── specifications/          # Workflow specifications

/opt/yawl/
├── yawl-engine.jar          # Optimized JVM working directory
├── lib/                     # Additional JAR dependencies
├── classes.jsa              # CDS archive (optional)
└── aot.cache                # AOT cache (optional)
```

## Best Practices

1. **Memory limits**: Always set container memory limits
2. **Volume mounting**: Mount `/app/data` and `/app/logs` for persistence
3. **Network isolation**: Use proper network configurations
4. **Health checks**: Configure orchestration to restart unhealthy containers
5. **Logging**: Centralize logs for monitoring

## References

- [Java 25 Features](https://openjdk.org/projects/jdk/25/)
- [Shenandoah GC Guide](https://openjdk.org/projects/amber/ShenandoahGC/)
- [Docker Best Practices](https://docs.docker.com/develop/)