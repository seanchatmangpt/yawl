# YAWL Installation Guide

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Docker Installation](#docker-installation)
4. [Docker Compose Installation](#docker-compose-installation)
5. [Kubernetes Installation](#kubernetes-installation)
6. [Cloud Platform Installation](#cloud-platform-installation)
7. [Post-Installation Setup](#post-installation-setup)
8. [Verification Steps](#verification-steps)
9. [Troubleshooting Installation Issues](#troubleshooting-installation-issues)

## Prerequisites

### System Requirements

**Minimum Hardware:**
- CPU: 2 cores (4+ cores recommended for production)
- Memory: 4GB RAM (8GB+ for production)
- Disk: 20GB free space (50GB+ for production with logs)
- Network: 10Mbps connection minimum

**Supported Operating Systems:**
- Linux (Ubuntu 20.04 LTS+, CentOS 8+)
- macOS 11+
- Windows 10+ (with WSL2 or Docker Desktop)

### Software Requirements

**Required:**
- Docker 20.10+ (for containerized deployments)
- Docker Compose 2.0+ (for Docker Compose setup)
- kubectl 1.24+ (for Kubernetes)
- Java 11+ (for standalone deployments)
- PostgreSQL 12+ or MySQL 8+ (for database)

**Optional:**
- Kubernetes cluster (GKE, EKS, AKS, or on-premises)
- Terraform 1.3+ (for infrastructure as code)
- Helm 3.0+ (for Kubernetes package management)
- Git 2.20+ (for version control)

### Network Requirements

- Port 8080: YAWL Engine (HTTP)
- Port 5432: PostgreSQL database
- Port 6379: Redis cache
- Port 3000: Grafana monitoring (optional)
- Port 9090: Prometheus (optional)
- Outbound HTTPS (443) for external integrations

### User Permissions

```bash
# Required Linux user permissions
- sudo access (for system package installation)
- Docker daemon access (in docker group)
- /home directory write access

# To add current user to docker group:
sudo usermod -aG docker $USER
newgrp docker
```

## Quick Start

### 30-Second Installation (Single-Node Docker)

```bash
# Clone the repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl

# Start with Docker Compose (all services)
docker-compose up -d

# Verify installation
docker-compose ps

# Access YAWL Engine
# Open browser: http://localhost:8080

# Login
# Default username: admin
# Default password: admin
```

**Expected Output:**
```
CONTAINER ID   IMAGE                              STATUS
abc123         postgres:14-alpine                 Up 2 minutes
def456         redis:7-alpine                     Up 2 minutes
ghi789         yawl:latest                        Up 1 minute
```

### First Steps After Installation

```bash
# 1. Access YAWL Console
curl http://localhost:8080/resourceService/

# Expected response:
# <html>
# <head><title>YAWL Resource Management Service</title></head>
# <body>Welcome to YAWL</body>
# </html>

# 2. Check logs
docker-compose logs yawl | head -50

# 3. Monitor performance
docker stats

# 4. Access monitoring dashboard
# Open browser: http://localhost:3000 (Grafana)
# Login: admin / admin
```

## Docker Installation

### Option 1: Build from Source

**Prerequisites:**
```bash
# Install build tools
sudo apt-get update
sudo apt-get install -y build-essential ant maven

# Verify installations
docker --version        # Docker 20.10+
docker-compose --version  # Docker Compose 2.0+
ant -version           # Apache Ant 1.10+
```

**Build Steps:**

```bash
# 1. Clone repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl

# 2. Build YAWL Docker image
docker build -f Dockerfile -t yawl:5.2.0 .

# This process takes 10-15 minutes
# Progress indicators:
# - Step 1/10: FROM maven:3.9 (downloading base)
# - Step 2/10: WORKDIR /app
# - Step 3/10: COPY build/ ./build/ (copying source)
# - Step 4/10: RUN apt-get update (building)
# - Step 8/10: FROM tomcat:9.0 (runtime base)
# - Step 10/10: CMD ["/usr/local/tomcat/bin/startup.sh"]

# 3. Verify image
docker images | grep yawl
# REPOSITORY   TAG       IMAGE ID      CREATED        SIZE
# yawl         5.2.0     abc123def456  5 minutes ago   1.2GB

# 4. Run the container
docker run -d \
  -p 8080:8080 \
  -e YAWL_DB_HOST=host.docker.internal \
  -e YAWL_DB_PORT=5432 \
  -e YAWL_DB_NAME=yawl \
  -e YAWL_DB_USER=yawl \
  -e YAWL_DB_PASSWORD=yawl-password \
  --name yawl-engine \
  yawl:5.2.0

# 5. Check container logs
docker logs -f yawl-engine
```

### Option 2: Use Pre-built Image

```bash
# Pull from Docker Hub
docker pull yawlfoundation/yawl:latest

# Or from GCP Artifact Registry
docker pull us-central1-docker.pkg.dev/yawl-project/yawl/yawl:latest

# Run the image
docker run -d \
  -p 8080:8080 \
  --name yawl-engine \
  yawlfoundation/yawl:latest
```

### Docker Network Setup

```bash
# Create custom bridge network
docker network create yawl-net

# Run PostgreSQL on network
docker run -d \
  --name yawl-postgres \
  --network yawl-net \
  -e POSTGRES_PASSWORD=yawl-password \
  -e POSTGRES_DB=yawl \
  -v postgres_data:/var/lib/postgresql/data \
  postgres:14-alpine

# Run Redis on network
docker run -d \
  --name yawl-redis \
  --network yawl-net \
  -v redis_data:/data \
  redis:7-alpine

# Run YAWL on network
docker run -d \
  --name yawl-engine \
  --network yawl-net \
  -p 8080:8080 \
  -e YAWL_DB_HOST=yawl-postgres \
  -e YAWL_DB_PORT=5432 \
  -e YAWL_DB_NAME=yawl \
  -e YAWL_DB_USER=yawl \
  -e YAWL_DB_PASSWORD=yawl-password \
  yawlfoundation/yawl:latest

# Verify connectivity
docker exec yawl-engine ping yawl-postgres
docker exec yawl-engine redis-cli -h yawl-redis ping
```

### Environment Variables Reference

```bash
# Database Configuration
YAWL_DB_HOST=cloudsql-proxy      # Database hostname
YAWL_DB_PORT=5432                # Database port
YAWL_DB_NAME=yawl                # Database name
YAWL_DB_USER=yawl                # Database username
YAWL_DB_PASSWORD=securepass      # Database password (set in secrets!)

# Java JVM Configuration
JAVA_OPTS="-Xms512m -Xmx1024m \  # Heap memory
           -XX:+UseG1GC \        # Garbage collector
           -Dlog4j.debug=true"   # Debug logging

YAWL_HEAP_SIZE=1024m             # Tomcat heap size

# Logging
LOG_LEVEL=INFO                    # Log level (DEBUG/INFO/WARN/ERROR)

# Redis Configuration
REDIS_HOST=redis                  # Redis hostname
REDIS_PORT=6379                   # Redis port
REDIS_PASSWORD=                   # Redis password (if required)

# Monitoring
METRICS_ENABLED=true              # Enable Prometheus metrics
METRICS_PORT=9090                 # Metrics endpoint port

# Application Settings
YAWL_ENVIRONMENT=production       # Environment (development/staging/production)
CASE_ARCHIVE_DAYS=365             # Archive cases older than N days
```

## Docker Compose Installation

### Complete Stack Deployment

```bash
# 1. Clone repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl

# 2. Review docker-compose.yml
cat docker-compose.yml

# 3. Create environment file (optional, for secrets)
cat > .env << EOF
POSTGRES_PASSWORD=your-secure-password
YAWL_DB_PASSWORD=your-secure-password
GF_SECURITY_ADMIN_PASSWORD=your-grafana-password
EOF

# 4. Build services (if not using pre-built images)
docker-compose build

# 5. Start services
docker-compose up -d

# 6. Monitor startup
docker-compose logs -f yawl
```

### Service Endpoints After Deployment

```
YAWL Engine:        http://localhost:8080
Grafana:            http://localhost:3000
Prometheus:         http://localhost:9090
Kibana:             http://localhost:5601
PostgreSQL:         localhost:5432
Redis:              localhost:6379
Nginx (Reverse):    http://localhost (ports 80/443)
```

### Docker Compose Lifecycle Management

```bash
# Start services
docker-compose up -d

# Stop services (keep volumes)
docker-compose stop

# Restart services
docker-compose restart

# View logs
docker-compose logs yawl           # Last 100 lines
docker-compose logs -f yawl        # Follow logs
docker-compose logs --tail 50 yawl # Last 50 lines

# Execute commands in running container
docker-compose exec yawl bash
docker-compose exec postgres psql -U yawl

# Remove everything (destructive)
docker-compose down                # Remove containers & networks
docker-compose down -v             # Remove containers, networks, volumes
docker-compose down --remove-orphans

# Scale services (if configured for horizontal scaling)
docker-compose up -d --scale yawl=3
```

### Production-Ready Modifications

```yaml
# Modify docker-compose.yml for production:

services:
  yawl:
    # Add resource limits
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '1'
          memory: 1G

    # Add restart policy
    restart_policy:
      condition: on-failure
      delay: 5s
      max_attempts: 5

    # Add health checks
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/resourceService/"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

    # Add logging configuration
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
```

## Kubernetes Installation

### Prerequisites

```bash
# 1. Kubernetes cluster access
kubectl cluster-info

# 2. Verify kubectl version
kubectl version --client

# 3. Check cluster capacity
kubectl top nodes      # Node resource usage
kubectl describe nodes # Detailed node info

# 4. Create namespace
kubectl create namespace yawl
kubectl config set-context --current --namespace=yawl
```

### Helm Chart Installation (Recommended)

```bash
# 1. Add Helm repository
helm repo add yawl https://charts.yawlfoundation.org
helm repo update

# 2. Create values override file
cat > custom-values.yaml << EOF
replicaCount: 3

image:
  repository: yawlfoundation/yawl
  tag: "5.2.0"

database:
  host: cloudsql-proxy
  port: 5432
  name: yawl
  user: yawl
  # password set via secrets

ingress:
  enabled: true
  hosts:
    - host: yawl.example.com
      paths:
        - path: /
          pathType: Prefix

resources:
  limits:
    cpu: 2
    memory: 2Gi
  requests:
    cpu: 500m
    memory: 1Gi

autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
EOF

# 3. Create database secret
kubectl create secret generic yawl-db-secret \
  --from-literal=password='secure-db-password'

# 4. Install Helm chart
helm install yawl-release yawl/yawl \
  -f custom-values.yaml \
  -n yawl

# 5. Monitor installation
helm status yawl-release -n yawl
kubectl get pods -n yawl -w
```

### Manual Kubernetes Manifests Installation

```bash
# 1. Create namespace
kubectl create namespace yawl

# 2. Create secrets
kubectl create secret generic yawl-db-secret \
  --from-literal=password='secure-db-password' \
  -n yawl

# 3. Apply configuration
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secrets.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/rbac.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml
kubectl apply -f k8s/network-policy.yaml
kubectl apply -f k8s/autoscaling.yaml

# 4. Verify deployment
kubectl get all -n yawl
kubectl logs -f deployment/yawl -n yawl
```

### Accessing Kubernetes Deployment

```bash
# Get service endpoint
kubectl get svc -n yawl
# NAME       TYPE           CLUSTER-IP    EXTERNAL-IP     PORT(S)
# yawl       LoadBalancer   10.0.0.1      35.201.134.56   80:30001/TCP

# Port forwarding (for local testing)
kubectl port-forward -n yawl svc/yawl 8080:80

# Access via external IP
curl http://35.201.134.56

# Check pod status
kubectl get pods -n yawl
kubectl describe pod yawl-xyz -n yawl

# View pod logs
kubectl logs pod/yawl-xyz -n yawl
kubectl logs pod/yawl-xyz -n yawl --previous  # Previous crash

# Execute commands in pod
kubectl exec -it pod/yawl-xyz -n yawl -- /bin/bash
```

## Cloud Platform Installation

### Google Cloud Platform (GCP)

```bash
# 1. Set environment variables
export GCP_PROJECT_ID="my-project"
export GCP_REGION="us-central1"

# 2. Create GKE cluster
gcloud container clusters create yawl-cluster \
  --region $GCP_REGION \
  --num-nodes 3 \
  --machine-type n2-standard-4 \
  --enable-autoscaling \
  --min-nodes 3 \
  --max-nodes 10

# 3. Create Cloud SQL instance
gcloud sql instances create yawl-postgres \
  --database-version POSTGRES_14 \
  --tier db-custom-2-7680 \
  --region $GCP_REGION \
  --availability-type REGIONAL

# 4. Create database and user
gcloud sql databases create yawl --instance yawl-postgres
gcloud sql users create yawl \
  --instance yawl-postgres \
  --password=secure-password

# 5. Get cluster credentials
gcloud container clusters get-credentials yawl-cluster \
  --region $GCP_REGION

# 6. Deploy using Terraform
cd terraform
terraform init
terraform plan
terraform apply
```

### Amazon Web Services (AWS)

```bash
# 1. Create EKS cluster
eksctl create cluster \
  --name yawl-cluster \
  --version 1.27 \
  --region us-east-1 \
  --nodegroup-name yawl-nodes \
  --node-type t3.xlarge \
  --nodes 3 \
  --nodes-min 3 \
  --nodes-max 10

# 2. Create RDS PostgreSQL database
aws rds create-db-instance \
  --db-instance-identifier yawl-postgres \
  --engine postgres \
  --db-instance-class db.r5.large \
  --allocated-storage 100 \
  --master-username admin \
  --master-user-password secure-password

# 3. Get cluster credentials
aws eks update-kubeconfig \
  --name yawl-cluster \
  --region us-east-1

# 4. Deploy using Helm
helm install yawl yawl/yawl \
  -f aws-values.yaml
```

### Microsoft Azure

```bash
# 1. Create AKS cluster
az aks create \
  --resource-group yawl-rg \
  --name yawl-cluster \
  --node-count 3 \
  --vm-set-type VirtualMachineScaleSets \
  --load-balancer-sku standard

# 2. Create Azure Database for PostgreSQL
az postgres server create \
  --resource-group yawl-rg \
  --name yawl-postgres \
  --admin-user yawl \
  --admin-password 'Secure$Password123' \
  --sku-name B_Gen5_2

# 3. Get cluster credentials
az aks get-credentials \
  --resource-group yawl-rg \
  --name yawl-cluster

# 4. Deploy with Helm
helm install yawl yawl/yawl \
  -f azure-values.yaml
```

## Post-Installation Setup

### Initial Configuration

```bash
# 1. Access YAWL console
# URL: http://localhost:8080/resourceService/

# 2. Change default admin password
# Login with: admin / admin
# Navigate to: Administration > Users > admin > Change Password

# 3. Configure LDAP/AD integration (optional)
# Administration > System Settings > LDAP Configuration
# - LDAP Server: ldap.example.com:389
# - Base DN: dc=example,dc=com
# - User search filter: uid={0}

# 4. Set up SSL/TLS (for production)
# - Generate certificate or use Let's Encrypt
# - Configure in Tomcat/Nginx
# - Update URLs to HTTPS

# 5. Configure email service
# Administration > System Settings > Mail Configuration
# - SMTP Server: smtp.example.com:587
# - From Address: yawl@example.com
# - Use TLS: Yes
```

### Database Initialization

```bash
# 1. Verify database connection
docker exec yawl-postgres psql -U yawl -d yawl -c "SELECT version();"

# 2. Initialize schema (if not auto-migrated)
docker exec yawl-postgres psql -U yawl -d yawl < docker/init-db.sql

# 3. Verify schema
docker exec yawl-postgres psql -U yawl -d yawl -c "\dt"
# Expected tables: yptask, ypcase, ypworkitem, ypresource, etc.

# 4. Create backup user (optional)
docker exec yawl-postgres psql -U postgres -d yawl -c \
  "CREATE USER backup_user WITH PASSWORD 'backup_pass';"
```

### Monitoring Setup

```bash
# 1. Prometheus configuration
# Edit docker/prometheus.yml
scrape_configs:
  - job_name: 'yawl'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/metrics'

# 2. Grafana dashboards
# Import dashboards from: https://grafana.com/grafana/dashboards/
# Or create custom dashboards for:
#   - Case throughput
#   - Task completion time
#   - Resource utilization
#   - Database performance

# 3. Alerting rules
# Define alert rules for:
#   - Pod restart rate > 2 restarts/hour
#   - High memory usage > 80%
#   - Database connection pool exhausted
#   - API response time > 5 seconds
```

### Log Aggregation

```bash
# 1. Configure Elasticsearch
# YAWL logs automatically forwarded via Filebeat

# 2. Access logs in Kibana
# URL: http://localhost:5601
# Create index pattern: yawl-*
# View logs in Discover tab

# 3. Create monitoring alerts
# Kibana > Alerting > Create Alert
# Rule: Error rate > 0.5%
# Action: Send email/Slack notification
```

## Verification Steps

### Post-Installation Checklist

```bash
# [ ] Container/Pod Status
docker-compose ps
# or
kubectl get pods -n yawl

# [ ] Database Connectivity
docker exec yawl-postgres psql -U yawl -d yawl -c "SELECT count(*) FROM yptask;"

# [ ] Application Logs
docker logs yawl-engine 2>&1 | grep -i "error\|exception" | head -10

# [ ] Service Health Check
curl -s http://localhost:8080/resourceService/ | grep -o "Welcome"

# [ ] Database Health
docker exec yawl-postgres pg_isready -U yawl

# [ ] Redis Health
docker exec yawl-redis redis-cli ping
# Expected output: PONG

# [ ] Memory Usage
docker stats --no-stream yawl-engine

# [ ] Disk Space
df -h | grep -E "Mounted|/"

# [ ] Ports in Use
netstat -tuln | grep LISTEN

# [ ] Network Connectivity
docker exec yawl-engine ping yawl-redis
docker exec yawl-engine ping yawl-postgres
```

### Performance Baseline Testing

```bash
# 1. Test API response time
time curl -s http://localhost:8080/resourceService/ > /dev/null
# Expected: < 500ms

# 2. Load test (using Apache Bench)
ab -n 100 -c 10 http://localhost:8080/resourceService/

# 3. Monitor during load test
watch -n 1 'docker stats --no-stream'

# 4. Database query performance
time docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT * FROM yptask LIMIT 10;"
# Expected: < 100ms
```

## Troubleshooting Installation Issues

### Issue: Container Fails to Start

**Symptom:**
```
Error: Container exits immediately with code 1
```

**Diagnosis:**
```bash
# Check container logs
docker logs yawl-engine

# Expected error message patterns:
# - "database connection refused" → DB not running
# - "port 8080 already in use" → Port conflict
# - "out of memory" → Insufficient heap
# - "permission denied" → File permission issue
```

**Solutions:**
```bash
# 1. Database connection refused
docker-compose ps      # Verify all containers running
docker logs yawl-postgres | tail -20

# 2. Port already in use
lsof -i :8080          # Find process using port
docker stop old-container  # Stop conflicting container
netstat -tuln | grep 8080

# 3. Out of memory
# Increase Docker memory limit in settings
# or set in docker-compose.yml:
# mem_limit: 4g

# 4. File permissions
docker exec yawl-engine ls -la /usr/local/tomcat/webapps/
# Fix if needed:
docker exec -u root yawl-engine chown -R tomcat:tomcat /usr/local/tomcat
```

### Issue: Database Connection Failures

**Symptom:**
```
SEVERE [main] org.apache.catalina.startup.Catalina.start Server startup in [10002] ms
ERROR jdbc.spi.SqlExceptionHelper - Connection to localhost:5432 refused
```

**Diagnosis:**
```bash
# Check PostgreSQL status
docker exec yawl-postgres pg_isready -U yawl

# Verify network connectivity
docker exec yawl-engine nc -zv yawl-postgres 5432

# Check credentials
docker exec yawl-postgres psql -U yawl -d yawl -c "SELECT 1;"
```

**Solutions:**
```bash
# 1. Restart database
docker-compose restart postgres

# 2. Rebuild database from backup
docker exec yawl-postgres psql -U yawl -d yawl -f backup.sql

# 3. Check connection pool settings
# In Tomcat context.xml:
# maxActive="20" maxIdle="10" maxWait="30000"

# 4. Recreate database and user
docker exec yawl-postgres dropdb -U postgres yawl
docker exec yawl-postgres createdb -U postgres yawl
```

### Issue: High Memory Usage

**Symptom:**
```
Java heap space error or container OOMKilled
```

**Diagnosis:**
```bash
# Check memory usage
docker stats yawl-engine

# Monitor garbage collection
docker exec yawl-engine jmap -heap $(pgrep -f tomcat)

# Review application logs for memory leaks
grep -i "outofmemory\|gc overhead" logs/catalina.out
```

**Solutions:**
```bash
# 1. Increase heap size
# In docker-compose.yml:
environment:
  JAVA_OPTS: "-Xms1024m -Xmx2048m -XX:+UseG1GC"

# 2. Enable garbage collection logging
JAVA_OPTS: "-Xms1024m -Xmx2048m -XX:+PrintGCDetails -XX:+PrintGCTimeStamps"

# 3. Archive old cases
# Administration > Maintenance > Archive Cases
# Set retention period (e.g., 90 days)

# 4. Clear cache
docker exec yawl-redis redis-cli FLUSHALL

# 5. Restart service
docker-compose restart yawl
```

### Issue: Slow Performance

**Symptom:**
```
API requests taking > 5 seconds to respond
```

**Diagnosis:**
```bash
# Check database query performance
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "EXPLAIN ANALYZE SELECT * FROM ypcase WHERE status='ACTIVE' LIMIT 100;"

# Monitor resource usage
watch -n 1 'docker stats --no-stream yawl-engine'

# Check for slow queries
grep "slow query" logs/yawl.log

# Review application logs
docker logs yawl-engine | grep -i "timeout\|slow"
```

**Solutions:**
```bash
# 1. Add database indexes
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "CREATE INDEX idx_case_status ON ypcase(status);"

# 2. Increase connection pool
# In Tomcat context.xml:
# maxActive="30" maxIdle="15"

# 3. Enable query caching
# In application configuration:
hibernate.cache.use_second_level_cache=true
hibernate.cache.region.factory_class=org.hibernate.cache.jndi.JndiRegionFactory

# 4. Scale horizontally
docker-compose up -d --scale yawl=3

# 5. Optimize JVM GC
JAVA_OPTS: "-Xms1024m -Xmx2048m -XX:+UseG1GC -XX:G1HeapRegionSize=16m"
```

---

**Installation Guide Version**: 1.0
**Last Updated**: 2026-02-14
**Maintained By**: YAWL Foundation
