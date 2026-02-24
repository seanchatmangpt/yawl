# Deploying YAWL v6.0.0 with Docker

**Target Platforms**: Docker 20.10+, Docker Compose 2.x, Kubernetes 1.24+
**Base Images**: eclipse-temurin:25-jdk (for WAR services), postgres:15-alpine (database)
**Estimated Time**: 30-45 minutes

## Overview

This guide covers deploying YAWL v6.0.0 using Docker containers for isolated, portable, and scalable deployments.

## Prerequisites

### System Requirements
- Docker 20.10+ or Docker Desktop
- Docker Compose 2.x (for multi-container setup)
- 8GB RAM minimum
- 10GB disk space
- Linux, macOS, or Windows with WSL2

### Verify Prerequisites

```bash
# Check Docker
docker --version  # Should be 20.10+
docker run hello-world

# Check Docker Compose
docker-compose --version  # Should be 2.x

# Check disk space
df -h /var/lib/docker  # At least 10GB available
```

## Quick Start (Development)

### Option 1: Docker Compose (Recommended)

**Create `docker-compose.yml`**:

```yaml
version: '3.9'

services:
  # PostgreSQL Database
  postgres:
    image: postgres:15-alpine
    container_name: yawl-db
    environment:
      POSTGRES_DB: yawl
      POSTGRES_USER: yawluser
      POSTGRES_PASSWORD: yawlpass
      POSTGRES_INITDB_ARGS: "-c timezone=UTC"
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./scripts/init-db.sql:/docker-entrypoint-initdb.d/01-init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U yawluser -d yawl"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - yawl-network

  # YAWL Engine (Tomcat)
  yawl-engine:
    build:
      context: .
      dockerfile: containerization/Dockerfile.engine
    container_name: yawl-engine
    environment:
      CATALINA_OPTS: >-
        -Xmx2048m
        -Xms1024m
        -Djava.awt.headless=true
        -Dyawl.db.driver=org.postgresql.Driver
        -Dyawl.db.url=jdbc:postgresql://postgres:5432/yawl
        -Dyawl.db.username=yawluser
        -Dyawl.db.password=yawlpass
        -Dyawl.jwt.secret=your-secret-key-here
    ports:
      - "8080:8080"
      - "8443:8443"
    volumes:
      - ./output/yawl.war:/usr/local/tomcat/webapps/yawl.war
      - tomcat_logs:/usr/local/tomcat/logs
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/yawl/api/ib/workitems"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - yawl-network
    restart: unless-stopped

  # Resource Service
  resource-service:
    build:
      context: .
      dockerfile: containerization/Dockerfile.resourceService
    container_name: yawl-resource-service
    environment:
      CATALINA_OPTS: >-
        -Xmx1024m
        -Xms512m
        -Dyawl.db.url=jdbc:postgresql://postgres:5432/yawl
    ports:
      - "8081:8080"
    volumes:
      - ./output/resourceService.war:/usr/local/tomcat/webapps/resourceService.war
    depends_on:
      postgres:
        condition: service_healthy
      yawl-engine:
        condition: service_healthy
    networks:
      - yawl-network
    restart: unless-stopped

  # Worklet Service
  worklet-service:
    build:
      context: .
      dockerfile: containerization/Dockerfile.workletService
    container_name: yawl-worklet-service
    environment:
      CATALINA_OPTS: >-
        -Xmx1024m
        -Xms512m
        -Dyawl.db.url=jdbc:postgresql://postgres:5432/yawl
    ports:
      - "8082:8080"
    volumes:
      - ./output/workletService.war:/usr/local/tomcat/webapps/workletService.war
    depends_on:
      postgres:
        condition: service_healthy
      yawl-engine:
        condition: service_healthy
    networks:
      - yawl-network
    restart: unless-stopped

  # Nginx Reverse Proxy (Optional)
  nginx:
    image: nginx:alpine
    container_name: yawl-proxy
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./ssl:/etc/nginx/ssl:ro
    depends_on:
      - yawl-engine
    networks:
      - yawl-network
    restart: unless-stopped

volumes:
  postgres_data:
  tomcat_logs:

networks:
  yawl-network:
    driver: bridge
```

**Start Services**:

```bash
# Build and start all services
docker-compose up --build

# OR start in background
docker-compose up -d

# View logs
docker-compose logs -f yawl-engine

# Stop services
docker-compose down
```

### Option 2: Individual Docker Commands

```bash
# Build YAWL WAR files
cd /home/user/yawl
ant clean
ant buildAll

# Build Docker image
docker build -f containerization/Dockerfile.engine \
  -t yawl:5.2-engine .

# Start PostgreSQL
docker run -d \
  --name yawl-postgres \
  -e POSTGRES_DB=yawl \
  -e POSTGRES_USER=yawluser \
  -e POSTGRES_PASSWORD=yawlpass \
  -p 5432:5432 \
  -v yawl_db:/var/lib/postgresql/data \
  postgres:15-alpine

# Wait for database
sleep 10

# Start YAWL Engine
docker run -d \
  --name yawl-engine \
  -e CATALINA_OPTS="-Xmx2048m -Xms1024m -Dyawl.db.url=jdbc:postgresql://yawl-postgres:5432/yawl" \
  -p 8080:8080 \
  --link yawl-postgres:postgres \
  yawl:5.2-engine

# Check status
docker logs yawl-engine
docker ps
```

## Building Custom Docker Images

### Base Dockerfile (Tomcat + Java 25)

```dockerfile
# Dockerfile.engine
FROM eclipse-temurin:25-jre-slim

# Install utilities
RUN apt-get update && apt-get install -y \
    curl \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/*

# Download and install Tomcat
RUN useradd -m -s /bin/bash tomcat && \
    mkdir -p /opt/tomcat && \
    cd /opt/tomcat && \
    curl -fsSL https://archive.apache.org/dist/tomcat/tomcat-10/v10.1.24/bin/apache-tomcat-10.1.24.tar.gz \
    | tar -xzf - --strip-components=1

# Copy WAR file
COPY output/yawl.war /opt/tomcat/webapps/

# Set environment
ENV CATALINA_HOME=/opt/tomcat
ENV PATH=$CATALINA_HOME/bin:$PATH
ENV JAVA_OPTS="-Djava.awt.headless=true"

# Expose ports
EXPOSE 8080 8443

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/yawl/api/ib/workitems || exit 1

# Run Tomcat
CMD ["catalina.sh", "run"]
```

### Multi-stage Build (Optimized)

```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-25 AS builder

WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:25-jre-slim

RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /build/target/yawl.war /opt/tomcat/webapps/

# ... rest of configuration
```

## Production Deployment

### Docker Swarm

Initialize swarm and deploy:

```bash
# Initialize
docker swarm init

# Create secret for JWT
echo "your-secret-key-here" | docker secret create yawl_jwt_secret -

# Create service
docker service create \
  --name yawl-engine \
  --port 8080:8080 \
  --secret yawl_jwt_secret \
  --env-file .env.prod \
  --replicas 3 \
  --constraint node.role==worker \
  --update-delay 10s \
  --update-parallelism 1 \
  yawl:5.2-engine
```

### Kubernetes Deployment

**Create `k8s/yawl-deployment.yaml`**:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: yawl

---
# ConfigMap for environment variables
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-config
  namespace: yawl
data:
  CATALINA_OPTS: "-Xmx2048m -Xms1024m -Djava.awt.headless=true"
  YAWL_DB_DRIVER: "org.postgresql.Driver"
  YAWL_DB_URL: "jdbc:postgresql://yawl-postgres:5432/yawl"

---
# Secret for database credentials
apiVersion: v1
kind: Secret
metadata:
  name: yawl-db-secret
  namespace: yawl
type: Opaque
stringData:
  username: yawluser
  password: yawlpass

---
# PostgreSQL StatefulSet
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: yawl-postgres
  namespace: yawl
spec:
  serviceName: yawl-postgres
  replicas: 1
  selector:
    matchLabels:
      app: yawl-postgres
  template:
    metadata:
      labels:
        app: yawl-postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_DB
          value: yawl
        - name: POSTGRES_USER
          valueFrom:
            secretKeyRef:
              name: yawl-db-secret
              key: username
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: yawl-db-secret
              key: password
        volumeMounts:
        - name: postgres-storage
          mountPath: /var/lib/postgresql/data
        livenessProbe:
          exec:
            command:
            - /bin/sh
            - -c
            - pg_isready -U yawluser -d yawl
          initialDelaySeconds: 30
          periodSeconds: 10
  volumeClaimTemplates:
  - metadata:
      name: postgres-storage
    spec:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 10Gi

---
# YAWL Engine Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
  namespace: yawl
spec:
  replicas: 3
  selector:
    matchLabels:
      app: yawl-engine
  template:
    metadata:
      labels:
        app: yawl-engine
    spec:
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - yawl-engine
              topologyKey: kubernetes.io/hostname
      containers:
      - name: yawl-engine
        image: yawl:5.2-engine
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
        envFrom:
        - configMapRef:
            name: yawl-config
        env:
        - name: YAWL_DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: yawl-db-secret
              key: username
        - name: YAWL_DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: yawl-db-secret
              key: password
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /yawl/api/ib/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
        readinessProbe:
          httpGet:
            path: /yawl/api/ib/workitems
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 5

---
# YAWL Engine Service
apiVersion: v1
kind: Service
metadata:
  name: yawl-engine
  namespace: yawl
spec:
  selector:
    app: yawl-engine
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
  type: LoadBalancer

---
# PostgreSQL Service
apiVersion: v1
kind: Service
metadata:
  name: yawl-postgres
  namespace: yawl
spec:
  selector:
    app: yawl-postgres
  ports:
  - port: 5432
    targetPort: 5432
  clusterIP: None  # Headless service for StatefulSet
```

Deploy to Kubernetes:

```bash
# Deploy
kubectl apply -f k8s/yawl-deployment.yaml

# Check status
kubectl get pods -n yawl
kubectl logs -n yawl deployment/yawl-engine

# Port forward for testing
kubectl port-forward -n yawl svc/yawl-engine 8080:80

# Test
curl http://localhost:8080/yawl/api/ib/workitems
```

## Docker Networking

### Network Isolation

```bash
# Create custom network
docker network create yawl-network

# Run containers on network
docker run --network yawl-network --name postgres postgres:15-alpine
docker run --network yawl-network --name yawl-engine yawl:5.2-engine
```

### Service Discovery

```bash
# Containers can communicate by name on same network
# Example: yawl-engine can reach postgres via "postgres" hostname
```

## Volumes and Persistence

### Database Persistence

```bash
# Named volume (managed by Docker)
docker volume create yawl-db

docker run -v yawl-db:/var/lib/postgresql/data postgres:15-alpine
```

### Application Logs

```bash
# Bind mount for logs
docker run -v /opt/yawl/logs:/usr/local/tomcat/logs yawl:5.2-engine
```

### Configuration Files

```bash
# Mount config as read-only
docker run -v /opt/yawl/config/application.properties:/app/config/app.properties:ro yawl:5.2-engine
```

## Environment Configuration

### Using .env File

**Create `.env`**:

```env
# Database
DB_HOST=postgres
DB_PORT=5432
DB_NAME=yawl
DB_USER=yawluser
DB_PASSWORD=yawlpass

# YAWL
YAWL_JWT_SECRET=your-secret-key-here
YAWL_LOG_LEVEL=INFO
YAWL_MAX_WORKERS=10

# Tomcat
CATALINA_OPTS=-Xmx2048m -Xms1024m
```

### Using in Docker Compose

```yaml
services:
  yawl-engine:
    environment:
      - YAWL_DB_HOST=${DB_HOST}
      - YAWL_DB_USER=${DB_USER}
      - YAWL_DB_PASSWORD=${DB_PASSWORD}
```

## SSL/TLS in Docker

### Using Self-Signed Certificate

```bash
# Generate certificate
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /opt/yawl/ssl/key.pem \
  -out /opt/yawl/ssl/cert.pem

# Configure in docker-compose.yml
volumes:
  - /opt/yawl/ssl:/opt/tomcat/conf/ssl:ro

environment:
  - TOMCAT_SSL_ENABLED=true
  - TOMCAT_SSL_KEYSTORE=/opt/tomcat/conf/ssl/keystore.jks
```

### Using Let's Encrypt with Certbot

```bash
# Get certificate
certbot certonly --standalone -d yourdomain.com

# Mount in container
volumes:
  - /etc/letsencrypt/live/yourdomain.com:/opt/tomcat/conf/ssl:ro
```

## Monitoring Docker Containers

### Docker Stats

```bash
# Real-time resource usage
docker stats yawl-engine

# In compose
docker-compose stats
```

### Logging

```bash
# View container logs
docker logs yawl-engine
docker logs -f yawl-engine  # Follow

# In compose
docker-compose logs yawl-engine
docker-compose logs -f --tail=50
```

### Health Checks

```bash
# Check container health
docker inspect --format='{{.State.Health.Status}}' yawl-engine

# View health details
docker inspect yawl-engine | grep -A 10 '"Health"'
```

## Troubleshooting

### Container Won't Start

```bash
# Check logs
docker logs yawl-engine

# Check image
docker inspect yawl:5.2-engine

# Run with interactive terminal
docker run -it --rm yawl:5.2-engine /bin/bash
```

### Database Connection Issues

```bash
# Test connectivity from engine container
docker exec yawl-engine psql -h postgres -U yawluser -d yawl -c "SELECT 1;"

# Check network
docker network inspect yawl-network
```

### OutOfMemory in Container

```bash
# Check memory limit
docker inspect yawl-engine | grep Memory

# Increase in docker-compose.yml
services:
  yawl-engine:
    mem_limit: 4g
    memswap_limit: 4g
```

## Cleanup

```bash
# Stop and remove containers
docker-compose down

# Remove volumes
docker-compose down -v

# Remove all YAWL images
docker rmi yawl:5.2-engine yawl:5.2-resourceService

# Clean up unused resources
docker system prune -a
```

## Production Checklist

- [ ] Docker 20.10+ installed
- [ ] WAR files built (ant buildAll)
- [ ] Custom Docker images built
- [ ] Docker Compose or Kubernetes deployment tested
- [ ] Database persistence configured
- [ ] SSL/TLS certificates installed
- [ ] Environment variables configured
- [ ] Health checks passing
- [ ] Resource limits configured
- [ ] Logging configured
- [ ] Monitoring/alerting setup
- [ ] Network isolation configured
- [ ] Backup strategy for database volumes

## Additional Resources

- [Docker Documentation](https://docs.docker.com/)
- [Docker Compose Reference](https://docs.docker.com/compose/compose-file/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [YAWL Docker Images](../containerization/)
- [Database Setup Guide](./DEPLOY-DATABASE.md)

## Support

For Docker issues:
- Check container logs: `docker logs <container>`
- Review Docker troubleshooting: https://docs.docker.com/config/containers/troubleshoot/

For YAWL issues:
- Check application logs within container
- Review YAWL documentation at https://yawlfoundation.org
