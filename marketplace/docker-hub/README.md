# YAWL Docker Hub Images

YAWL (Yet Another Workflow Language) official Docker images for container-based deployment.

## Available Images

- `yawl/yawl:latest` - Latest stable YAWL application server
- `yawl/yawl:1.0.0` - Specific version releases
- `yawl/yawl-ui:latest` - YAWL Web UI (React-based)
- `yawl/yawl-engine:latest` - YAWL Workflow Engine (headless)

## Quick Start

### Using Docker Compose (Recommended)

```bash
# Clone the repository
git clone https://github.com/yawl/yawl.git
cd yawl/marketplace/docker-hub

# Set environment variables
cp .env.example .env
# Edit .env with your configuration

# Start the stack
docker-compose up -d

# Access YAWL
# UI: http://localhost:8080
# API: http://localhost:8080/api
```

### Standalone Container

```bash
docker run -d \
  --name yawl \
  -p 8080:8080 \
  -e DATABASE_HOST=your-db-host \
  -e DATABASE_PASSWORD=your-db-password \
  yawl/yawl:1.0.0
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `YAWL_ENVIRONMENT` | `production` | Deployment environment |
| `DATABASE_HOST` | `yawl-db` | Database host |
| `DATABASE_PORT` | `5432` | Database port |
| `DATABASE_NAME` | `yawldb` | Database name |
| `DATABASE_USER` | `yawladmin` | Database username |
| `DATABASE_PASSWORD` | (required) | Database password |
| `REDIS_HOST` | `yawl-cache` | Redis cache host |
| `REDIS_PORT` | `6379` | Redis cache port |
| `LOG_LEVEL` | `INFO` | Application log level |
| `ENABLE_MONITORING` | `true` | Enable Prometheus metrics |
| `ENABLE_AUTH` | `true` | Enable authentication |
| `JAVA_OPTS` | `-Xmx1024m` | JVM options |

### Database Setup

The docker-compose setup includes PostgreSQL 14. For production deployments, consider using a managed database service:

- **AWS RDS** for PostgreSQL
- **Azure Database** for PostgreSQL
- **Google Cloud SQL** for PostgreSQL

### Volumes

- `yawl-data`: Application data and configurations
- `yawl-logs`: Application logs
- `yawl-db-data`: Database data
- `yawl-cache-data`: Cache data

## Monitoring and Observability

### Enable Monitoring Stack

```bash
docker-compose --profile monitoring up -d
```

This starts:
- **Prometheus** (http://localhost:9090) - Metrics collection
- **Grafana** (http://localhost:3000) - Visualization

### Health Checks

Each service includes health checks:

```bash
docker-compose ps
```

## Security Best Practices

1. **Change Default Passwords**
   ```bash
   export DB_PASSWORD=$(openssl rand -base64 32)
   export GRAFANA_ADMIN_PASSWORD=$(openssl rand -base64 32)
   ```

2. **Use Environment Files**
   ```bash
   # Create .env file with secrets
   DB_PASSWORD=<secure-password>
   REDIS_PASSWORD=<secure-password>
   GRAFANA_ADMIN_PASSWORD=<secure-password>
   ```

3. **Enable SSL/TLS**
   - Configure reverse proxy (nginx/Traefik)
   - Use Let's Encrypt certificates

4. **Network Isolation**
   ```bash
   # Use internal network only
   docker-compose up -d --no-ports
   ```

5. **Image Security**
   - Use specific version tags (not `latest`)
   - Scan images for vulnerabilities
   - Keep images updated

## Scaling and Production Deployment

### Docker Swarm

```bash
docker swarm init
docker stack deploy -c docker-compose.yml yawl
```

### Kubernetes

Use the Helm chart from the helm-repo directory:

```bash
helm repo add yawl https://helm.yawl.org
helm install yawl yawl/yawl
```

## Troubleshooting

### Check Logs

```bash
# Application logs
docker-compose logs -f yawl

# Database logs
docker-compose logs -f yawl-db

# All services
docker-compose logs -f
```

### Common Issues

1. **Database Connection Failed**
   ```bash
   docker-compose exec yawl-db psql -U yawladmin -d yawldb -c "SELECT 1"
   ```

2. **Out of Memory**
   Increase `JAVA_OPTS`:
   ```bash
   export JAVA_OPTS="-Xmx2048m -Xms512m"
   docker-compose up -d
   ```

3. **Port Already in Use**
   ```bash
   # Use different ports
   docker-compose -f docker-compose.yml up -d -p
   ```

## Building Custom Images

```bash
# Build from source
docker build -t yawl/yawl:custom .

# Tag for registry
docker tag yawl/yawl:custom myregistry.azurecr.io/yawl:custom

# Push to registry
docker push myregistry.azurecr.io/yawl:custom
```

## Support and Documentation

- **Documentation**: https://docs.yawl.org
- **GitHub**: https://github.com/yawl/yawl
- **Community**: https://forum.yawl.org
- **Issues**: https://github.com/yawl/yawl/issues

## License

See LICENSE file in the repository for licensing information.

## Contributing

Contributions are welcome! Please see CONTRIBUTING.md for guidelines.
