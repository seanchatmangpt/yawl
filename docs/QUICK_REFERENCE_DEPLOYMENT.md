# YAWL Deployment Quick Reference — Choose Your Architecture

**TL;DR**: Answer 3 questions to find your deployment model in 2 minutes.

---

## 1-Minute Decision Tree

```
┌────────────────────────────────────────────────────────┐
│  Do you need horizontal scaling across servers?        │
│  (Can you afford to replicate the engine?)             │
└─────────────────────┬─────────────────────────────────┘
                      │
        ┌─────────────┴─────────────┐
        ▼ YES (scale to millions)   ▼ NO (single or few servers)
        │                           │
        │                           ▼
        │              ┌──────────────────────────────────┐
        │              │ Can your workflow handle         │
        │              │ stateless re-execution?          │
        │              │ (No session persistence needed)  │
        │              └─────────┬──────────────────────┘
        │                        │
        │         ┌──────────────┴──────────────┐
        │         ▼ NO                          ▼ YES
        │         │                             │
        ▼         ▼                             ▼
   ┌─────────┐  ┌─────────────────┐      ┌──────────────┐
   │STATELESS│  │  PERSISTENT     │      │STATELESS     │
   │ENGINE   │  │  SINGLE INSTANCE│      │LIGHTWEIGHT   │
   │(Kafka)  │  │  (PostgreSQL)   │      │(Kubernetes)  │
   └─────────┘  └─────────────────┘      └──────────────┘
        │              │                        │
        │ Scales to    │ Scales to             │ Scales to
        │ 1M+/day      │ 100K/day              │ 1M+/day
        │ (events)     │ (cases)               │ (read replicas)
```

---

## Quick Reference Matrix

| Deployment | Stateful? | Scale | DB Required | Setup Time | Cost | Best For | Learn More |
|---|---|---|---|---|---|---|---|
| **Stateless + Kafka** | No | 1M+/day | Optional | 45 min | High | AI agents, event-driven, 24/7 uptime | [Stateless Guide](#stateless-engine-deployment) |
| **Stateless + RabbitMQ** | No | 500K/day | Optional | 30 min | Medium | Event streaming, cloud-native | [Event Store Guide](#choose-event-store) |
| **Persistent Single (WAR)** | Yes | 100K/day | Required | 15 min | Low | SMB, internal, proof-of-concept | [WAR Deployment](#persistent-single-instance) |
| **Persistent Cluster** | Yes | 200K/day | Required (HA) | 60 min | High | Enterprise, high availability | [Cluster Guide](#persistent-ha-cluster) |
| **Cloud Marketplace** | No | 500K/day | Managed | 5 min | Pay-as-you-go | Fastest start, managed service | [Marketplace](#cloud-marketplace) |
| **Docker Dev Env** | No | Testing | Optional | 10 min | Free | Development, testing, learning | [Docker Guide](#docker-dev) |

---

## Stateless Engine Deployment

**Use if**: You need to scale to millions of cases/day, prefer cloud-native, need 24/7 uptime

### Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│  Load Balancer (or Ingress Controller)              │
└────────────────┬────────────────────────────────────┘
                 │
        ┌────────┼────────┬─────────────┐
        ▼        ▼        ▼             ▼
   ┌────────┐┌────────┐┌────────┐┌────────────┐
   │YEngine-1││YEngine-2││YEngine-3│...YEngine-N│  (Stateless pods)
   │(Pod)    ││(Pod)    ││(Pod)    │(Auto-scale)│
   └────┬───┘└───┬────┘└───┬────┘└────┬───────┘
        │        │        │         │
        └────────┼────────┼─────────┘
                 │
        ┌────────▼────────┐
        │  Event Store    │
        │  (Kafka/RMQ)    │  (Source of truth)
        └────────┬────────┘
                 │
        ┌────────▼────────┐
        │  Optional: DB   │
        │  (PostgreSQL)   │  (For reporting only)
        └─────────────────┘
```

### Deployment Steps

**1. Build & Push Image**
```bash
# From yawl-stateless module
mvn clean package -P docker
docker tag yawl-stateless:latest myregistry/yawl-stateless:v1.0
docker push myregistry/yawl-stateless:v1.0
```

**2. Deploy with Docker Compose**
```bash
# Quick local testing with Kafka
docker-compose -f docs/deployment/stateless-kafka-compose.yml up -d

# Verify engine health
curl http://localhost:8080/actuator/health/readiness
```

**3. Deploy with Kubernetes**
```bash
# Create namespace
kubectl create namespace yawl

# Apply manifests
kubectl apply -f docs/deployment/k8s/yawl-stateless-deployment.yaml -n yawl
kubectl apply -f docs/deployment/k8s/kafka-statefulset.yaml -n yawl
kubectl apply -f docs/deployment/k8s/service.yaml -n yawl

# Watch rollout
kubectl rollout status deployment/yawl-engine -n yawl

# Scale up
kubectl scale deployment yawl-engine --replicas=5 -n yawl
```

### Configuration Checklist

- [ ] Event store chosen (Kafka, RabbitMQ, S3)
- [ ] Event store credentials configured
- [ ] Database connection string (if using for reporting)
- [ ] Authentication method (JWT, OAuth)
- [ ] Monitoring enabled (OpenTelemetry, Prometheus)
- [ ] Resource limits set (CPU, memory)
- [ ] Health check endpoints configured
- [ ] Logging aggregation setup (ELK, CloudWatch)

### Scaling Considerations

| Load | Pod Count | Event Store | DB | Response Time |
|---|---|---|---|---|
| Development | 1 | Single broker | SQLite | <500ms |
| Small (10K/day) | 2 | Single broker | PostgreSQL | <200ms |
| Medium (100K/day) | 5 | 3-broker cluster | PostgreSQL HA | <200ms |
| Large (1M/day) | 20 | 5-broker cluster | PostgreSQL HA + caching | <100ms |

---

## Choose Event Store

**Which event store for stateless?**

| Event Store | Throughput | Latency | Cost | Ops Overhead | Best For |
|---|---|---|---|---|---|
| **Kafka** | 1M+/sec | 10-100ms | High | Medium | High-volume, audit trail required |
| **RabbitMQ** | 50K-100K/sec | 1-10ms | Medium | Low | Reliable, moderate volume |
| **AWS SQS** | 300K/sec | 100-200ms | Low (pay-as-go) | None | AWS-native, serverless |
| **Azure Service Bus** | 1M/sec | 100-200ms | Low (pay-as-go) | None | Azure-native, serverless |
| **S3 + SQS** | 100K/sec | 500ms-1s | Low | Low | Archive, low-frequency workflows |

### Quick Setup

**Kafka (Production)**
```bash
docker run -d --name kafka \
  -e KAFKA_CFG_ZOOKEEPER_CONNECT=zookeeper:2181 \
  bitnami/kafka:latest

# Then configure yawl-stateless:
export YAWL_EVENT_STORE=kafka://kafka:9092
export YAWL_EVENT_TOPIC=yawl-events
```

**RabbitMQ (Simpler)**
```bash
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3.12-management

# Then configure yawl-stateless:
export YAWL_EVENT_STORE=rabbitmq://guest:guest@rabbitmq:5672
```

---

## Persistent Single Instance

**Use if**: You need ≤100K cases/day, prefer simplicity, can tolerate downtime windows

### Architecture

```
┌──────────────────────────────┐
│  Client / REST API Consumer  │
└─────────────┬────────────────┘
              │
        ┌─────▼─────┐
        │  YAWL WAR │  (Servlet container)
        │  (Jetty)  │  Single instance,
        │           │  no replication
        └─────┬─────┘
              │
        ┌─────▼──────────┐
        │  PostgreSQL /  │
        │  H2 (local)    │  Persistent state
        └────────────────┘
```

### Deployment Steps

**1. Build WAR**
```bash
mvn clean package -P webapps -DskipTests
# Output: yawl-webapps/target/yawl-webapps.war
```

**2. Run Locally (Testing)**
```bash
java -jar yawl-webapps.war \
  --server.port=8080 \
  --spring.datasource.url=jdbc:h2:./yawl-data
```

**3. Deploy to Tomcat (Production)**
```bash
# Copy WAR to Tomcat
cp yawl-webapps.war $CATALINA_HOME/webapps/

# Configure database in context.xml
# Restart Tomcat
$CATALINA_HOME/bin/startup.sh

# Access at: http://localhost:8080/yawl-webapps
```

### Configuration Checklist

- [ ] Database backend chosen (PostgreSQL recommended)
- [ ] Database connection pooling configured (HikariCP)
- [ ] Max connection pool size set (20-50 based on load)
- [ ] Server port set (8080, 8443 for SSL)
- [ ] TLS certificate configured (if using HTTPS)
- [ ] Session timeout configured (default 30 min)
- [ ] Log location configured (logs/)
- [ ] Backup schedule configured (daily)

### Performance Tuning

```properties
# application.properties
# Database pooling
spring.datasource.hikari.maximum-pool-size=40
spring.datasource.hikari.minimum-idle=10

# Session management
server.servlet.session.timeout=30m

# Thread pool
server.tomcat.threads.max=200
server.tomcat.threads.min-spare=10

# Caching
spring.cache.type=caffeine
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=10m
```

---

## Persistent HA Cluster

**Use if**: You need high availability, can tolerate minimal downtime, enterprise SLAs required

### Architecture

```
┌─────────────────────────────────────┐
│  HAProxy / Load Balancer            │
└──────────────┬──────────────────────┘
               │
      ┌────────┼────────┐
      ▼        ▼        ▼
  ┌────────┐┌────────┐┌────────┐
  │ YAWL-1 ││ YAWL-2 ││ YAWL-3 │  (Active-Active)
  │ Jetty  ││ Jetty  ││ Jetty  │
  └────┬───┘└───┬────┘└───┬────┘
       │        │        │
       └────────┼────────┘
                │
      ┌─────────▼──────────┐
      │  PostgreSQL HA     │
      │  (Primary + 2      │
      │   hot standby)     │
      └────────────────────┘
```

### Deployment Steps

**1. Set up PostgreSQL HA** (using streaming replication)
```bash
# Primary node
initdb -D /var/lib/postgresql/primary
pg_basebackup -D /var/lib/postgresql/standby1 -R

# Standby nodes configure recovery
echo "standby_mode = 'on'" >> standby/recovery.conf
echo "primary_conninfo = 'host=primary_host port=5432'" >> standby/recovery.conf

# Start all instances
pg_ctl start -D /var/lib/postgresql/primary
pg_ctl start -D /var/lib/postgresql/standby1
```

**2. Deploy YAWL instances**
```bash
# Instance 1
java -jar yawl-webapps.war --server.port=8001 \
  --spring.datasource.url=jdbc:postgresql://primary,standby1,standby2/yawl

# Instance 2
java -jar yawl-webapps.war --server.port=8002 \
  --spring.datasource.url=jdbc:postgresql://primary,standby1,standby2/yawl

# Instance 3
java -jar yawl-webapps.war --server.port=8003 \
  --spring.datasource.url=jdbc:postgresql://primary,standby1,standby2/yawl
```

**3. Set up HAProxy**
```haproxy
# /etc/haproxy/haproxy.cfg
backend yawl_cluster
    mode http
    server yawl1 localhost:8001 check
    server yawl2 localhost:8002 check
    server yawl3 localhost:8003 check
    balance roundrobin

frontend yawl_lb
    bind *:8080
    default_backend yawl_cluster
```

### Configuration Checklist

- [ ] PostgreSQL primary/standby streaming configured
- [ ] Connection pooling on each instance (HikariCP)
- [ ] HAProxy or load balancer configured
- [ ] Session replication configured (sticky sessions or distributed)
- [ ] Monitoring alerts set up (primary failover)
- [ ] Backup strategy configured (WAL archiving to S3)
- [ ] Disaster recovery runbooks documented
- [ ] Failover time SLA defined and tested

---

## Cloud Marketplace

**Use if**: You want zero infrastructure, pay-as-you-go, fastest time-to-value

### Quick Start

**AWS Marketplace** (5 minutes)
```bash
# Search for "YAWL Workflow Engine"
# Click "Subscribe" → "Launch"
# Configure:
#   - VPC & Subnet
#   - Instance type (t3.medium recommended)
#   - Storage (20 GB minimum)
# Launch → Wait 5 min for startup
# Access at: https://your-instance-dns/yawl
```

**Azure Marketplace** (5 minutes)
```bash
# Azure Portal → Marketplace → Search "YAWL"
# Create → Fill in resource group, instance name, region
# Billing setup → Deploy
# Access at: https://your-instance.cloudapp.azure.com/yawl
```

**GCP Marketplace** (5 minutes)
```bash
# GCP Console → Marketplace → Search "YAWL"
# Setup → Select project, zone, machine type
# Accept → Deploy
# Access via GCP Cloud Console SSH or web terminal
```

### Managed Service Features

- Auto-scaling based on load
- Daily backups to cloud storage
- Managed SSL/TLS certificates
- Built-in monitoring dashboards
- Managed database (cloud SQL)
- Automatic security patches

---

## Docker Dev Environment

**Use if**: You're developing, learning, or testing locally

### Quick Start

```bash
# Clone and build
git clone https://github.com/yawlfoundation/yawl.git
cd yawl

# Option 1: Docker Compose (easiest)
docker-compose -f docker-compose.dev.yml up -d

# Wait for startup
docker-compose logs -f yawl-engine
# Look for: "Application started in X seconds"

# Option 2: Local JAR (for IDE debugging)
mvn clean package -P webapps -DskipTests
java -jar yawl-webapps/target/yawl-webapps.jar

# Access at: http://localhost:8080/yawl
```

### Health Checks

```bash
# Check all services
docker-compose ps

# Check engine health
curl http://localhost:8080/actuator/health/readiness

# View logs
docker-compose logs yawl-engine  # Last 100 lines
docker-compose logs -f yawl-engine  # Stream real-time
```

---

## Decision Checklist

### Before choosing, ask yourself:

1. **Scale requirements**
   - [ ] <1K cases/day? → Persistent Single
   - [ ] 1K-100K/day? → Persistent Single or HA
   - [ ] 100K+/day? → Stateless + Event Store
   - [ ] 1M+/day? → Stateless + Kafka + Cloud

2. **Downtime tolerance**
   - [ ] No downtime allowed? → HA Cluster or Stateless
   - [ ] Maintenance windows OK? → Persistent Single
   - [ ] Any failover acceptable? → Stateless (auto-replicate)

3. **Budget constraints**
   - [ ] Minimal infrastructure? → Docker Dev or Marketplace
   - [ ] On-premise only? → Persistent or Stateless on-prem
   - [ ] Cloud budget available? → Marketplace (easiest) or Kubernetes (cheapest at scale)

4. **Operational complexity**
   - [ ] No ops team? → Marketplace
   - [ ] Small team? → Persistent Single or Docker
   - [ ] Mature DevOps? → Stateless + Kubernetes

5. **Compliance needs**
   - [ ] Audit trail required? → Stateless (event sourcing)
   - [ ] Data residency? → On-premise Stateless
   - [ ] Certification needed? → Enterprise HA with audit logs

---

## Common Configurations

### Startup to Production in 1 Hour

```bash
# 1. Local testing (10 min)
docker-compose -f docker-compose.dev.yml up -d
# Deploy test spec, verify it works

# 2. Build production image (10 min)
docker build -f Dockerfile.stateless -t myregistry/yawl:v1.0 .
docker push myregistry/yawl:v1.0

# 3. Deploy to Kubernetes (20 min)
kubectl apply -f k8s/yawl-ns.yaml
kubectl set image deployment/yawl-engine \
  yawl=myregistry/yawl:v1.0 -n yawl

# 4. Verify and monitor (10 min)
kubectl logs -f deployment/yawl-engine -n yawl
curl http://your-ingress/actuator/health/readiness
```

### Scaling from 10K to 1M Cases/Day

```
10K → 100K: Add caching + optimize DB queries
  └─ Upgrade to Persistent HA or Stateless with RabbitMQ

100K → 500K: Stateless + Kafka, add read replicas
  └─ Scale YAWL pods to 5-10, Kafka to 3-5 brokers

500K → 1M+: Full Kubernetes, event-driven, CQRS pattern
  └─ Scale to 20+ pods, Kafka to 10+ brokers, add event processing
```

---

## Find Your Deployment

| Your Situation | Recommendation |
|---|---|
| "I want to learn YAWL" | Docker Dev Environment |
| "I need to demo to management" | Cloud Marketplace |
| "I'm building an SMB app" | Persistent Single Instance |
| "We need 24/7 uptime" | Stateless + Kubernetes |
| "We process 1M+ cases/day" | Stateless + Kafka + Kubernetes |
| "We need high availability" | Persistent HA Cluster |
| "We're moving to the cloud" | Cloud Marketplace or Stateless + Managed Service |
| "We have on-premise data residency" | Stateless on-premise + S3 (or managed store) |

---

## Next Steps

1. **Choose your model** from the matrix above
2. **Follow the deployment steps** for your choice
3. **Configure** using the checklists
4. **Monitor** with Prometheus and OpenTelemetry
5. **Optimize** based on your metrics (see Performance Tuning section)

**Need help?** See:
- [How-To: Deploy to Production](./how-to/deployment/)
- [YAWL Stateless Getting Started](./tutorials/yawl-stateless-getting-started.md)
- [YAWL Webapps Getting Started](./tutorials/yawl-webapps-getting-started.md)
- [Troubleshooting Guide](./TROUBLESHOOTING_FLOWCHART.md)
