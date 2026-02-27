# YAWL AOT Cache Generator

This script generates Ahead-of-Time (AOT) caches for YAWL to improve JVM startup performance.

## Features

- Generates AOT cache for frequently used YAWL classes
- Supports GraalVM and JDK 25+ with AOT capabilities
- Configurable cache location (`/opt/yawl/aot.cache`)
- Optimized class filtering for minimal cache size
- Comprehensive logging and error handling
- Color-coded output for better visibility

## Prerequisites

- Java 25+ with AOT support enabled
- YAWL v6.0.0 compiled (`mvn clean compile` required)
- Write permissions to `/opt/yawl/` and `/var/log/yawl/`

## Usage

```bash
# Basic execution
./scripts/aot/generate-aot.sh

# The script will:
# 1. Check for AOT support in JVM
# 2. Collect commonly used YAWL classes
# 3. Generate AOT cache
# 4. Log results to /var/log/yawl/aot-generation.log
```

## Output

### Console Output
- Color-coded messages (green for info, yellow for warnings, red for errors)
- Progress updates during cache generation
- Final cache size information

### Log File
- Location: `/var/log/yawl/aot-generation.log`
- Timestamped entries for all operations
- Complete error details if generation fails

## Using the Generated Cache

To run YAWL with the generated AOT cache:

```bash
java -XX:AOTCache=/opt/yawl/aot.cache \
     --enable-preview \
     -jar yawl-engine.jar
```

## Configuration

### Cache Location
- Default: `/opt/yawl/aot.cache`
- Can be modified by changing the `AOT_OUTPUT` variable in the script

### Class Selection
The script includes these frequently used classes by default:
- YEngine - Main workflow engine
- YNetRunner - Network runner
- YWorkItem - Work item management
- YNet/YTask/YCondition - Workflow elements
- YStatelessEngine - Stateless engine
- WorkQueue - Resource management
- User - Authentication

### JVM Options Used
- `--enable-preview` - Enable Java language features
- `-XX:+UseCompactObjectHeaders` - Memory optimization
- `-XX:+UseShenandoahGC` - Low-pause garbage collector

## Troubleshooting

### Common Issues

1. **"AOT cache not supported"**
   - Ensure you're using Java 25+
   - Verify AOT flags are enabled in your JVM

2. **Permission denied for /opt/yawl/ or /var/log/yawl/**
   - Run with appropriate permissions:
   ```bash
   sudo ./scripts/aot/generate-aot.sh
   ```

3. **Cache file not created**
   - Check JVM logs for errors
   - Verify class files exist in target/classes

### Debug Mode

For detailed debugging, modify the script to set `set -x` at the top to enable trace mode.

## Performance Benefits

Expected improvements:
- Faster JVM startup (30-50% reduction in warmup time)
- Lower memory footprint during initialization
- Reduced CPU usage during startup

## Integration

This script is designed to work with the YAWL build system and can be integrated into:
- CI/CD pipelines
- Docker build processes
- Deployment scripts
- Development workflows

For production use, consider:
- Running as part of the deployment process
- Monitoring cache file size
- Regenerating when YAWL libraries are updated