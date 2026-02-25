# YAWL Quick Start Guide

**Version:** 6.0.0
**Last Updated:** 2026-02-16
**Target Audience:** New developers and operators

---

## Overview

YAWL (Yet Another Workflow Language) is an enterprise BPM/Workflow system based on rigorous Petri net semantics. This guide gets you running in under 15 minutes.

---

## Prerequisites

### Required
- **Java 25** (OpenJDK or Oracle JDK)
- **Maven 3.9+** (primary build system)
- **Git** (for source control)

### Optional
- **Docker** (for containerized deployment)
- **PostgreSQL 15+** (for persistent storage)
- **Redis 7+** (for caching and session management)

### Verify Prerequisites

```bash
# Check Java version (must be 25+)
java -version

# Check Maven version (must be 3.9+)
mvn -version

# Check Git
git --version

# Optional: Check Docker
docker --version
```

---

## Installation

### 1. Clone Repository

```bash
# Clone YAWL repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl
```

### 2. Build with Maven

```bash
# Full build (compile + test + package)
mvn clean install

# Expected output:
# [INFO] BUILD SUCCESS
# [INFO] Total time: ~2-3 minutes
```

### 3. Quick Build Verification

```bash
# Compile only (faster - ~18 seconds)
mvn compile

# Run tests
mvn test

# Package WARs
mvn package
```

---

## Running YAWL

### Option 1: Docker Compose (Recommended for First-Time Users)

```bash
# Start all services
docker-compose --profile production up -d

# Verify services are running
docker-compose ps

# Check health
curl http://localhost:8888/health
```

**Services Started:**
- **Engine** - Port 8080 (Core workflow engine)
- **Resource Service** - Port 9095 (Resource management)
- **Worklet Service** - Port 9092 (Dynamic selection)
- **PostgreSQL** - Port 5432 (Database)
- **Redis** - Port 6379 (Cache)

### Option 2: Standalone JAR

```bash
# Build standalone JAR
mvn clean package -Pdocker

# Run engine
java -jar yawl-engine/target/yawl-engine-5.2.0-shaded.jar

# Access at http://localhost:8080
```

### Option 3: Kubernetes (Production)

```bash
# Apply base manifests
kubectl apply -f k8s/base/

# Check status
kubectl get pods -n yawl
kubectl get svc -n yawl

# Access via ingress
kubectl get ingress -n yawl
```

---

## First Access

### Web Interface

1. Open browser to `http://localhost:8080/resourceService`
2. **Default Credentials:**
   - Username: `admin`
   - Password: `YAWL` (change immediately)
3. Complete initial setup wizard

### API Access

```bash
# Authenticate
curl -X POST "http://localhost:8080/ib/api/authenticate" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"YAWL"}'

# Returns sessionHandle
{"sessionHandle":"abc123def456..."}

# Check health
curl http://localhost:8080/ib/api/health
```

---

## Common Operations

### Launch a Workflow Case

```bash
# Upload specification (first time)
curl -X POST "http://localhost:8080/ia/api/upload" \
  -H "sessionHandle: YOUR_SESSION_HANDLE" \
  -F "specFile=@examples/OrderProcessing.yawl"

# Launch case
curl -X POST "http://localhost:8080/ib/api/launchCase" \
  -H "sessionHandle: YOUR_SESSION_HANDLE" \
  -H "Content-Type: application/json" \
  -d '{
    "specID": "OrderProcessing",
    "specVersion": "1.0",
    "caseParams": {
      "orderId": "12345",
      "customer": "Acme Corp"
    }
  }'
```

### Get Available Work Items

```bash
curl -X GET "http://localhost:8080/ib/api/workItemsForParticipant" \
  -H "sessionHandle: YOUR_SESSION_HANDLE" \
  -H "participantID: admin"
```

### Complete Work Item

```bash
curl -X POST "http://localhost:8080/ib/api/checkOutWorkItem" \
  -H "sessionHandle: YOUR_SESSION_HANDLE" \
  -d "workItemID=YOUR_WORK_ITEM_ID"

curl -X POST "http://localhost:8080/ib/api/checkInWorkItem" \
  -H "sessionHandle: YOUR_SESSION_HANDLE" \
  -d "workItemID=YOUR_WORK_ITEM_ID&data=<output>...</output>"
```

---

## Common Issues and Solutions

### Issue 1: Build Fails with "Java version not supported"

**Problem:** Maven requires Java 25
**Solution:**
```bash
# Install Java 25
sdk install java 25.0.0-open  # Using SDKMAN

# Or download from:
# https://jdk.java.net/25/

# Verify
java -version
# Expected: java version "25"
```

### Issue 2: Tests Fail with Database Connection Error

**Problem:** PostgreSQL not running or wrong credentials
**Solution:**
```bash
# Start PostgreSQL via Docker
docker run -d \
  --name yawl-postgres \
  -e POSTGRES_DB=yawl \
  -e POSTGRES_USER=yawl_admin \
  -e POSTGRES_PASSWORD=yawl_password \
  -p 5432:5432 \
  postgres:15

# Update connection properties
# Edit: build/build.properties
database.type=postgres
database.path=yawl
database.user=yawl_admin
database.password=yawl_password
```

### Issue 3: Port 8080 Already in Use

**Problem:** Another application using port 8080
**Solution:**
```bash
# Find process using port
lsof -i :8080

# Kill process
kill -9 <PID>

# Or change YAWL port
# Edit docker-compose.yml or application.properties
server.port=8888
```

### Issue 4: Maven Build Slow

**Problem:** First build downloads all dependencies
**Solution:**
```bash
# Use parallel builds (4 threads)
mvn clean install -T 4

# Or use one thread per CPU core
mvn clean install -T 1C

# Skip tests for faster build
mvn clean install -DskipTests
```

### Issue 5: Docker Compose Fails to Start

**Problem:** Missing environment variables
**Solution:**
```bash
# Copy example environment file
cp .env.example .env

# Edit .env with your values
nano .env

# Required variables:
# DATABASE_PASSWORD=your_password
# YAWL_JDBC_PASSWORD=your_password
# REDIS_PASSWORD=your_redis_password

# Restart services
docker-compose down
docker-compose --profile production up -d
```

---

## Health Checks

### Verify Engine is Running

```bash
# Overall health
curl http://localhost:8080/health

# Expected: {"status":"UP"}

# Readiness (Kubernetes)
curl http://localhost:8080/health/ready

# Liveness (Kubernetes)
curl http://localhost:8080/health/live

# Metrics (Prometheus format)
curl http://localhost:8080/metrics
```

### Verify Database Connection

```bash
# Via psql
psql -h localhost -p 5432 -U yawl_admin -d yawl -c "SELECT 1;"

# Via Docker
docker exec -it yawl-postgres psql -U yawl_admin -d yawl -c "SELECT 1;"
```

### Verify Redis Connection

```bash
# Via redis-cli
redis-cli -h localhost -p 6379 ping

# Via Docker
docker exec -it yawl-redis redis-cli ping
# Expected: PONG
```

---

## Quick Build Reference

| Task | Command | Time |
|------|---------|------|
| **Full build** | `mvn clean install` | 2-3 min |
| **Compile only** | `mvn compile` | 18 sec |
| **Run tests** | `mvn test` | 30 sec |
| **Package WARs** | `mvn package` | 1 min |
| **Skip tests** | `mvn clean install -DskipTests` | 1 min |
| **Parallel build** | `mvn clean install -T 4` | 45 sec |

---

## Next Steps

After completing this quick start:

1. **Read Developer Guide** - `DEVELOPER_GUIDE.md` for in-depth development
2. **Explore Integration** - `INTEGRATION_GUIDE.md` for MCP/A2A integration
3. **Production Deployment** - `OPERATIONS_GUIDE.md` for production setup
4. **YAWL Specification** - Create your first workflow (see examples/)
5. **API Documentation** - Explore Interface B API at /ib/api/docs

---

## Essential Commands Cheat Sheet

```bash
# Build Commands
mvn clean install              # Full build
mvn compile                    # Compile only
mvn test                       # Run tests
mvn package                    # Create WARs

# Docker Commands
docker-compose up -d           # Start services
docker-compose ps              # Check status
docker-compose logs -f engine  # View logs
docker-compose down            # Stop services

# Kubernetes Commands
kubectl apply -f k8s/base/     # Deploy
kubectl get pods -n yawl       # Check pods
kubectl logs -f deploy/yawl-engine -n yawl  # Logs
kubectl delete -f k8s/base/    # Remove

# Development Commands
mvn clean install -DskipTests  # Fast build
mvn test -Dtest=YEngineTest    # Run specific test
mvn jacoco:report              # Coverage report
mvn dependency:tree            # View dependencies
```

---

## Support Resources

- **Full Documentation:** `README.md`
- **Developer Guide:** `DEVELOPER_GUIDE.md`
- **Integration Guide:** `INTEGRATION_GUIDE.md`
- **Operations Guide:** `OPERATIONS_GUIDE.md`
- **GitHub:** https://github.com/yawlfoundation/yawl
- **Website:** https://yawlfoundation.github.io

---

**Estimated Time to Complete:** 10-15 minutes
**Difficulty:** Beginner
**Prerequisites:** Java 25, Maven 3.9+
