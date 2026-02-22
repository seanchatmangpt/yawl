# YAWL Docker Configuration

This directory contains Docker configurations for YAWL v6.0.0-Alpha.

## Quick Start

### Development (with H2 database)
```bash
# Start development environment
docker compose -f docker-compose.dev.yml up -d

# Check logs
docker compose -f docker-compose.dev.yml logs -f yawl-engine

# Stop development environment
docker compose -f docker-compose.dev.yml down
```

### Full Stack with PostgreSQL
```bash
# Start with PostgreSQL and observability
docker compose --profile production --profile observability up -d

# Check all services
docker compose ps

# View logs
docker compose logs -f
```

### Basic Production
```bash
# Start with PostgreSQL only
docker compose --profile production up -d

# Access services
# Engine API: http://localhost:8080
# Actuator: http://localhost:9090/actuator/health
```

## Available Profiles

| Profile | Description | Services |
|---------|-------------|----------|
| `default` | Development with H2 database | yawl-engine, h2-console |
| `production` | Production with PostgreSQL | yawl-engine-prod, postgres |
| `observability` | Monitoring stack | otel-collector, prometheus, grafana, jaeger |

## Ports

| Service | Port | Description |
|---------|------|-------------|
| YAWL Engine API | 8080 | Main YAWL workflow engine API |
| Actuator/Metrics | 9090 | Spring Boot actuator and metrics |
| Resource Service | 8081 | YAWL resource service |
| H2 Console | 8082 | Web interface for H2 database |
| H2 TCP | 9092 | TCP connection for H2 database |
| Prometheus | 9091 | Prometheus metrics UI |
| Grafana | 3000 | Grafana dashboard UI |
| Jaeger | 16686 | Jaeger tracing UI |
| OpenTelemetry | 4317, 4318 | OTLP endpoints |

## Health Checks

The containers implement a three-tier health check strategy:

1. **Spring Boot Actuator** (primary): Checks `/actuator/health/liveness`
2. **TCP Port Check**: Verifies the application is accepting connections
3. **JVM Process Check**: Validates the JVM process exists

Health check script: `docker/scripts/healthcheck.sh`

## Environment Variables

### Application Configuration
- `SPRING_PROFILES_ACTIVE`: Development or production profile
- `SPRING_APPLICATION_NAME`: Application name

### Database Configuration
- `DB_TYPE`: Database type (h2, postgres)
- `DB_HOST`: Database host
- `DB_PORT`: Database port
- `DB_NAME`: Database name
- `DB_USER`: Database username
- `DB_PASSWORD`: Database password

### JVM Options
- `JAVA_OPTS`: JVM options including garbage collection and memory settings

### Logging
- `LOGGING_LEVEL_ROOT`: Root log level
- `LOGGING_LEVEL_ORG_YAWLFOUNDATION`: YAWL package log level
- `TZ`: Timezone

## Environment File

Create `.env` file from `.env.template` to customize environment variables:

```bash
cp .env.template .env
# Edit .env with your settings
```

## Volume Mounts

| Volume | Purpose | Location |
|--------|---------|----------|
| yawl_specifications | Workflow specifications | /app/specifications |
| yawl_logs | Application logs | /app/logs |
| yawl_data | Persistent data (H2 database) | /app/data |
| yawl_temp | Temporary files | /app/temp |
| yawl_config | Configuration files | /app/config |

## Development Tips

### Mount Specifications
Mount local specifications for development:
```yaml
volumes:
  - ./specifications:/app/specifications/import:ro
```

### Debug Mode
Add debug JVM options:
```bash
export JAVA_OPTS="$JAVA_OPTS -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

### Live Reload
For development with hot reload, use volume mounts for the application JAR.

## Production Considerations

### Security
- Use strong passwords
- Enable TLS for external connections
- Use private container registry
- Configure network policies

### Monitoring
- Use Prometheus metrics for monitoring
- Configure alerts for critical thresholds
- Set up log aggregation

### Scaling
- Use external database (PostgreSQL)
- Configure connection pooling
- Set up load balancing for multiple instances

## Troubleshooting

### Common Issues

1. **Health Check Failing**
   ```bash
   # Check health status
   docker compose ps

   # View health check logs
   docker logs yawl-engine
   ```

2. **Port Conflicts**
   ```bash
   # Change port mappings in docker-compose.yml
   # ports:
   #   - "8081:8081"  # Change host port
   ```

3. **Database Connection Issues**
   ```bash
   # Check database logs
   docker logs yawl-postgres

   # Verify database connectivity
   docker exec -it yawl-engine nc -z postgres 5432
   ```

### Useful Commands

```bash
# Clean up unused containers
docker system prune -f

# Remove volumes
docker volume rm yawl_logs yawl_data

# View resource usage
docker stats

# Inspect container
docker inspect yawl-engine

# Access container shell
docker exec -it yawl-engine sh
```

## Building Custom Images

```bash
# Build the engine image
docker build -f docker/production/Dockerfile.engine -t yawl-engine:6.0.0-alpha .

# Build multi-arch images
docker buildx bake --load
```

## References

- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Docker Compose](https://docs.docker.com/compose/)
- [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/)