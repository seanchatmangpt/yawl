# Docker Deployment Guide for Autonomous Agents

## Overview

This guide explains how to deploy YAWL autonomous agents using Docker and Docker Compose.

## Architecture

The YAWL system consists of:

1. **YAWL Engine**: Core workflow execution engine
2. **PostgreSQL Database**: Persistent storage
3. **Autonomous Agents**: Self-organizing workflow participants
4. **Optional Services**: Resource Service, Worklet Service, Monitor Service

## Docker Compose Files

### `docker-compose.yml`

**Purpose:** Production deployment of YAWL Engine and services

**Profiles:**
- `production`: YAWL Engine + Database + Services

**Usage:**
```bash
docker compose --profile production up -d
```

### `docker-compose.simulation.yml`

**Purpose:** Autonomous agent simulation environment

**Profiles:**
- `simulation`: Autonomous party agents for order fulfillment

**Usage:**
```bash
# With engine from docker-compose.yml
docker compose -f docker-compose.yml -f docker-compose.simulation.yml \
  --profile production --profile simulation up -d

# With engine on host (e.g., running in IDE)
YAWL_ENGINE_URL=http://host.docker.internal:8888/yawl \
docker compose -f docker-compose.simulation.yml --profile simulation up
```

## Quick Start

### 1. Start YAWL Engine

```bash
cd /home/user/yawl

# Start engine and database
docker compose --profile production up -d

# Wait for engine to be ready
docker compose logs -f engine
# Look for: "Server startup in [XXX] milliseconds"
```

**Verify:**
```bash
curl http://localhost:8888/yawl/ib
# Should return Interface B WSDL
```

### 2. Set Z.AI API Key

Autonomous agents use Z.AI for reasoning:

```bash
export ZAI_API_KEY="your-zhipu-api-key-here"
```

Or create `.env` file:
```
ZAI_API_KEY=your-zhipu-api-key-here
```

### 3. Start Autonomous Agents

```bash
docker compose -f docker-compose.yml -f docker-compose.simulation.yml \
  --profile production --profile simulation up -d
```

This starts 5 agents:
- **ordering-agent** (port 8091): Procurement, purchase orders
- **carrier-agent** (port 8092): Transportation, shipping
- **freight-agent** (port 8093): In-transit tracking
- **payment-agent** (port 8094): Invoicing, payments
- **delivered-agent** (port 8095): Claims, returns

### 4. Launch Workflow

```bash
# Upload and launch orderfulfillment specification
docker compose exec engine curl -X POST \
  "http://localhost:8080/yawl/ib?action=upload" \
  -F "specFile=@/workspace/exampleSpecs/orderfulfillment.yawl"

# Or use GenericWorkflowLauncher from host
```

### 5. Monitor Progress

**Check agent logs:**
```bash
docker compose logs -f ordering-agent
docker compose logs -f carrier-agent
```

**Check work items:**
```bash
curl "http://localhost:8888/yawl/ib?action=getWorkItems&sessionHandle=..."
```

**Check A2A discovery:**
```bash
curl http://localhost:8091/.well-known/agent.json
```

## Agent Configuration

Agents are configured via environment variables in `docker-compose.simulation.yml`:

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `AGENT_CAPABILITY` | Agent capability description | `"Ordering: procurement, purchase orders"` |
| `AGENT_PORT` | HTTP server port | `8091` |
| `YAWL_ENGINE_URL` | YAWL Engine Interface B URL | `http://engine:8080/yawl` |
| `YAWL_USERNAME` | Interface B username | `admin` |
| `YAWL_PASSWORD` | Interface B password | `YAWL` |
| `ZAI_API_KEY` | Z.AI API key (required) | `your-api-key` |

### Customizing Agents

#### Option 1: Modify `docker-compose.simulation.yml`

Edit agent capability:
```yaml
services:
  custom-agent:
    environment:
      - "AGENT_CAPABILITY=CustomDomain: skill1, skill2, skill3"
      - AGENT_PORT=8096
```

#### Option 2: Use YAML Configuration Files

Create `build/autonomous-config/custom-agent.yaml`:
```yaml
agent:
  id: "custom-agent-001"
  name: "Custom Agent"
  port: 8096
  capability:
    domain: "custom-domain"
    skills:
      - "skill1"
      - "skill2"

engine:
  url: "${YAWL_ENGINE_URL}"
  username: "${YAWL_USERNAME}"
  password: "${YAWL_PASSWORD}"

strategies:
  discovery: "a2a"
  eligibility: "zai"
  decision: "zai"
  output: "zai"

zai:
  apiKey: "${ZAI_API_KEY}"
  model: "glm-4-flash"
```

Mount configuration in Docker Compose:
```yaml
services:
  custom-agent:
    volumes:
      - ./build/autonomous-config/custom-agent.yaml:/config/agent.yaml
    command: bash -c "ant compile -q && java -cp ... GenericPartyAgent /config/agent.yaml"
```

## Network Configuration

### Default Network: `yawl-network`

All services run on a Docker bridge network for inter-container communication.

### Port Mappings

| Service | Internal Port | External Port | Purpose |
|---------|---------------|---------------|---------|
| YAWL Engine | 8080 | 8888 | Workflow engine (Interface A/B) |
| Resource Service | 8080 | 8081 | Resource allocation |
| Worklet Service | 8080 | 8082 | Dynamic adaptation |
| Monitor Service | 8080 | 8083 | Process monitoring |
| Ordering Agent | 8091 | 8091 | A2A discovery + HTTP |
| Carrier Agent | 8092 | 8092 | A2A discovery + HTTP |
| Freight Agent | 8093 | 8093 | A2A discovery + HTTP |
| Payment Agent | 8094 | 8094 | A2A discovery + HTTP |
| Delivered Agent | 8095 | 8095 | A2A discovery + HTTP |
| PM4Py Agent | 9092 | 9092 | Process mining (Python) |

### Accessing from Host

**YAWL Engine:**
```bash
curl http://localhost:8888/yawl/ia  # Interface A
curl http://localhost:8888/yawl/ib  # Interface B
```

**Agent Discovery:**
```bash
curl http://localhost:8091/.well-known/agent.json
```

### Accessing from Container

**Engine URL from agents:**
```
http://engine:8080/yawl
```

**Agent URL from other agents:**
```
http://ordering-agent:8091
```

## Database Configuration

### PostgreSQL

**Default Credentials:**
- Host: `postgres` (internal), `localhost:5432` (external)
- Database: `yawl`
- Username: `yawl`
- Password: `yawl123`

**Persistent Storage:**
- Volume: `postgres_data`
- Location: Docker volume (preserved across restarts)

**Migrations:**
- Mounted from: `./database/migrations`
- Runs on first startup

**Health Check:**
```bash
docker compose exec postgres pg_isready -U yawl
```

## Production Deployment

### 1. Update Credentials

**DO NOT use default passwords in production!**

Create `.env.production`:
```
POSTGRES_PASSWORD=strong-random-password
YAWL_PASSWORD=strong-random-password
ZAI_API_KEY=your-api-key
DB_PASSWORD=strong-random-password
```

Update `docker-compose.yml`:
```yaml
environment:
  - DB_PASSWORD=${DB_PASSWORD}
```

### 2. Configure Resource Limits

```yaml
services:
  engine:
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G
```

### 3. Enable TLS/SSL

Use reverse proxy (nginx, Traefik):

```yaml
services:
  nginx:
    image: nginx:alpine
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
      - ./certs:/etc/nginx/certs
    ports:
      - "443:443"
    depends_on:
      - engine
```

### 4. Configure Logging

```yaml
services:
  engine:
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

### 5. Health Monitoring

All services have health checks configured:

```bash
docker compose ps
# Check "Health" column
```

## Troubleshooting

### Issue: Agents can't connect to engine

**Check:**
```bash
docker compose logs engine
# Ensure engine is started and healthy

docker compose exec ordering-agent curl http://engine:8080/yawl/ib
# Test connectivity from agent container
```

**Solution:**
- Wait for engine to fully start (check logs for "Server startup")
- Verify network connectivity
- Check `YAWL_ENGINE_URL` environment variable

### Issue: Z.AI API errors

**Check:**
```bash
docker compose logs ordering-agent | grep -i "zai\|api"
```

**Common Errors:**
- `API key not found`: Set `ZAI_API_KEY` environment variable
- `Connection timeout`: Check network access to `api.bigmodel.cn`
- `Rate limit exceeded`: Reduce `pollInterval` or use different API key

### Issue: Port conflicts

**Error:**
```
Error starting userland proxy: listen tcp4 0.0.0.0:8091: bind: address already in use
```

**Solution:**
```bash
# Find process using port
lsof -i :8091

# Kill process or change port mapping in docker-compose.yml
```

### Issue: Agent not finding work items

**Check:**
```bash
# Verify work items exist
curl "http://localhost:8888/yawl/ib?action=getAllWorkItems&sessionHandle=..."

# Check agent logs for eligibility reasoning
docker compose logs ordering-agent | grep eligible
```

### Issue: Database connection errors

**Check:**
```bash
docker compose logs postgres
docker compose exec postgres pg_isready -U yawl

# Test connection from engine
docker compose exec engine nc -zv postgres 5432
```

## Scaling Agents

### Horizontal Scaling

Run multiple instances of the same agent:

```yaml
services:
  ordering-agent:
    deploy:
      replicas: 3
    # Note: Each replica needs unique A2A port or disable discovery
```

**Consideration:** Multiple agents with same capability compete for work items (first-come-first-served).

### Load Balancing

Use YAWL Resource Service for sophisticated allocation:
```yaml
services:
  resource-service:
    ports:
      - "8081:8080"
```

## Backup and Recovery

### Database Backup

```bash
# Backup
docker compose exec postgres pg_dump -U yawl yawl > backup.sql

# Restore
docker compose exec -T postgres psql -U yawl yawl < backup.sql
```

### Workflow Specifications

```bash
# Backup specifications
docker compose exec engine tar czf /tmp/specs.tar.gz /opt/yawl/specs
docker compose cp engine:/tmp/specs.tar.gz ./backups/

# Restore
docker compose cp ./backups/specs.tar.gz engine:/tmp/
docker compose exec engine tar xzf /tmp/specs.tar.gz -C /opt/yawl/
```

## Performance Tuning

### JVM Heap Size

```yaml
environment:
  - JAVA_OPTS=-Xms512m -Xmx1024m -Djava.awt.headless=true
```

### Database Connections

Increase PostgreSQL max connections:
```yaml
postgres:
  command: postgres -c max_connections=200
```

### Agent Polling Interval

In agent configuration YAML:
```yaml
engine:
  pollInterval: 10000  # Increase to reduce engine load
```

## Monitoring and Observability

### Container Metrics

```bash
docker stats
```

### Application Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f ordering-agent

# With timestamps
docker compose logs -f --timestamps ordering-agent
```

### Health Endpoints

```bash
# Engine health
curl http://localhost:8888/yawl/

# Agent health (A2A discovery)
curl http://localhost:8091/.well-known/agent.json
```

## Cleanup

### Stop All Services

```bash
docker compose -f docker-compose.yml -f docker-compose.simulation.yml down
```

### Remove Volumes (WARNING: Deletes data!)

```bash
docker compose down -v
```

### Remove Images

```bash
docker compose down --rmi all
```

## Further Reading

- [Configuration Guide](configuration-guide.md): YAML configuration reference
- [API Documentation](api-documentation.md): Interface specifications
- [Migration Guide](migration-guide.md): Upgrading from legacy agents
- [Architecture Overview](README.md): System design
- [Docker Compose Documentation](https://docs.docker.com/compose/): Official Docker docs
