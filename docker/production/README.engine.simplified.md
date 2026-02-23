# YAWL Engine Docker Image (Simplified)

This is a simplified Dockerfile for the YAWL workflow engine that uses pre-built JAR artifacts instead of building from source in the container.

## Features

- **Base Image**: Eclipse Temurin JRE 25 Alpine (small, secure)
- **Java 25**: Virtual threads, ZGC, security hardening
- **Pre-built Artifacts**: Uses existing JAR files if available
- **Non-root User**: Runs as `dev` user (UID 1000) for security
- **Health Checks**: Monitors application health
- **Simple Startup**: Clean entrypoint script

## Prerequisites

1. Docker installed and running
2. Pre-built JAR files (optional but recommended):
   ```bash
   # Build the project first
   mvn clean package -pl yawl-engine,yawl-control-panel -DskipTests
   ```

## Build

```bash
# Build the Docker image
docker build -t yawl-engine:6.0.0-alpha -f docker/production/Dockerfile.engine.simplified .

# Or run the test script
./docker/production/test-docker-build.sh
```

## Run

```bash
# Run the container
docker run -d \
  --name yawl-engine \
  -p 8080:8080 \
  -p 9090:9090 \
  yawl-engine:6.0.0-alpha

# Check logs
docker logs yawl-engine

# Stop the container
docker stop yawl-engine
```

## Configuration

The container uses these environment variables:

- `SPRING_PROFILES_ACTIVE=production` (Spring profile)
- `DB_TYPE=h2` (Database type)
- `TZ=UTC` (Timezone)

## Health Check

The container includes a health check that monitors:
- Application health endpoint at `http://localhost:8080/actuator/health/liveness`
- Checks every 30 seconds with 10 second timeout
- Retries 3 times during startup

## JAR Location Strategy

The Dockerfile looks for JAR files in this order:
1. `./yawl-mcp-a2a-app/yawl-mcp-a2a-app/target/dependency/yawl-engine-*.jar`
2. `./yawl-engine/target/yawl-engine-*.jar`

If no JAR is found, the build will fail. You'll need to build the project first.

## Differences from Original

The simplified Dockerfile:

- ✅ Uses pre-built JARs (faster build)
- ✅ Single-stage build (simpler)
- ✅ No Maven build phase (no compilation errors)
- ✅ Cleaner startup script
- ❌ No Maven build (requires pre-built artifacts)
- ❌ No build-time verification (assumes JARs are valid)

## Troubleshooting

### Build Failures

**Error: "No pre-built JAR found"**
```bash
# Solution: Build the project first
mvn clean package -pl yawl-engine,yawl-control-panel -DskipTests
```

### Runtime Issues

**Error: "Could not find or load main class"**
- Check if the JAR file exists
- Verify the JAR contains the correct classes

**Health check failures**
- The application may take time to start up
- Check the logs for startup errors
```bash
docker logs yawl-engine
```

## File Structure

```
docker/production/
├── Dockerfile.engine.simplified    # Main Dockerfile
├── test-docker-build.sh           # Test script
├── README.engine.simplified.md    # This file
└── ...                            # Other Dockerfiles
```

## Next Steps

1. Test the Docker build with the test script
2. Run the container and verify it starts
3. Check the application is accessible on port 8080
4. Monitor logs for any issues