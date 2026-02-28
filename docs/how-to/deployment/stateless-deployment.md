# How-To: Deploy YAWL Stateless Engine

This guide shows how to deploy the YAWL stateless engine to Kubernetes, Docker Compose, and other cloud platforms.

---

## Overview

Stateless deployment characteristics:
- No shared database required (event-sourced state)
- Horizontal scalability via multiple pods/containers
- Stateless services compatible with serverless platforms
- Event stream is the source of truth

---

## Prerequisites

- YAWL stateless module built: `yawl-stateless-6.0.0-GA.jar`
- Docker installed (for container deployment)
- Kubernetes cluster (for K8s deployment) OR Docker Compose
- Event store configured (Kafka, RabbitMQ, or S3)

---

## Option 1: Docker Compose

### 1.1: Create docker-compose.yml

```yaml
version: '3.8'

services:
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  yawl-stateless:
    build:
      context: .
      dockerfile: Dockerfile.stateless
    environment:
      YAWL_ENGINE_MODE: stateless
      YAWL_EVENT_STORE: kafka
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      YAWL_LOG_LEVEL: INFO
      JAVA_OPTS: -Xms1g -Xmx2g -XX:+UseG1GC
    ports:
      - "8080:8080"
    depends_on:
      - kafka
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 10s
      timeout: 5s
      retries: 3

  yawl-stateless-2:
    build:
      context: .
      dockerfile: Dockerfile.stateless
    environment:
      YAWL_ENGINE_MODE: stateless
      YAWL_EVENT_STORE: kafka
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      YAWL_LOG_LEVEL: INFO
      JAVA_OPTS: -Xms1g -Xmx2g -XX:+UseG1GC
    ports:
      - "8081:8080"
    depends_on:
      - kafka

  nginx:
    image: nginx:alpine
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    ports:
      - "80:80"
    depends_on:
      - yawl-stateless
      - yawl-stateless-2
```

### 1.2: Create Dockerfile.stateless

```dockerfile
FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests -pl yawl-stateless

FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app
COPY --from=builder /build/yawl-stateless/target/*.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

ENTRYPOINT ["java", \
  "-Dyawl.engine.mode=stateless", \
  "-Dyawl.event.store=${YAWL_EVENT_STORE:-kafka}", \
  "-Dkafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}", \
  "-jar", "app.jar"]
```

### 1.3: Create nginx.conf

```nginx
upstream yawl_backend {
    server yawl-stateless:8080;
    server yawl-stateless-2:8080;
}

server {
    listen 80;
    server_name localhost;

    location / {
        proxy_pass http://yawl_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
    }

    location /health {
        access_log off;
        proxy_pass http://yawl_backend;
    }
}
```

### 1.4: Deploy

```bash
docker compose up -d

# Monitor logs
docker compose logs -f yawl-stateless

# Verify deployment
curl http://localhost/health
```

---

## Option 2: Kubernetes

### 2.1: Create Docker Image

```bash
docker build -f Dockerfile.stateless -t myregistry/yawl-stateless:6.0.0-GA .
docker push myregistry/yawl-stateless:6.0.0-GA
```

### 2.2: Create Kubernetes Manifests

**stateless-deployment.yaml**:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: yawl-config
data:
  YAWL_ENGINE_MODE: "stateless"
  YAWL_EVENT_STORE: "kafka"
  YAWL_LOG_LEVEL: "INFO"

---
apiVersion: v1
kind: Secret
metadata:
  name: kafka-credentials
type: Opaque
stringData:
  bootstrap-servers: kafka:9092
  username: yawl_user
  password: yawl_password

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-stateless
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: yawl-stateless
  template:
    metadata:
      labels:
        app: yawl-stateless
        version: v1
    spec:
      containers:
      - name: yawl-engine
        image: myregistry/yawl-stateless:6.0.0-GA
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP
        envFrom:
        - configMapRef:
            name: yawl-config
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          valueFrom:
            secretKeyRef:
              name: kafka-credentials
              key: bootstrap-servers
        - name: JAVA_OPTS
          value: "-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
        resources:
          requests:
            memory: "1.5Gi"
            cpu: "500m"
          limits:
            memory: "2.5Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 40
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /ready
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 2
        volumeMounts:
        - name: event-store
          mountPath: /data/events
      volumes:
      - name: event-store
        persistentVolumeClaim:
          claimName: yawl-events-pvc

---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: yawl-events-pvc
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: fast-ssd
  resources:
    requests:
      storage: 100Gi

---
apiVersion: v1
kind: Service
metadata:
  name: yawl-stateless
spec:
  type: LoadBalancer
  selector:
    app: yawl-stateless
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
    name: http

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-stateless-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-stateless
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### 2.3: Deploy to Cluster

```bash
kubectl apply -f stateless-deployment.yaml

# Monitor rollout
kubectl rollout status deployment/yawl-stateless

# Verify pods
kubectl get pods -l app=yawl-stateless

# Check service
kubectl get svc yawl-stateless
```

---

## Option 3: AWS ECS

### 3.1: Create Task Definition

```json
{
  "family": "yawl-stateless",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "containerDefinitions": [
    {
      "name": "yawl-engine",
      "image": "123456789.dkr.ecr.us-east-1.amazonaws.com/yawl-stateless:6.0.0-GA",
      "portMappings": [
        {
          "containerPort": 8080,
          "hostPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "YAWL_ENGINE_MODE",
          "value": "stateless"
        },
        {
          "name": "YAWL_EVENT_STORE",
          "value": "kafka"
        },
        {
          "name": "KAFKA_BOOTSTRAP_SERVERS",
          "value": "kafka-cluster:9092"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/yawl-stateless",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/health || exit 1"],
        "interval": 30,
        "timeout": 10,
        "retries": 3,
        "startPeriod": 40
      }
    }
  ]
}
```

### 3.2: Create ECS Service

```bash
aws ecs create-service \
  --cluster yawl-cluster \
  --service-name yawl-stateless \
  --task-definition yawl-stateless:1 \
  --desired-count 3 \
  --launch-type FARGATE \
  --network-configuration awsvpcConfiguration={subnets=subnet-xxx,securityGroups=sg-xxx,assignPublicIp=ENABLED} \
  --load-balancers targetGroupArn=arn:aws:elasticloadbalancing:...,containerName=yawl-engine,containerPort=8080
```

---

## Option 4: GCP Cloud Run

### 4.1: Build and Push Image

```bash
gcloud builds submit --tag gcr.io/my-project/yawl-stateless:6.0.0-GA
```

### 4.2: Deploy Service

```bash
gcloud run deploy yawl-stateless \
  --image gcr.io/my-project/yawl-stateless:6.0.0-GA \
  --platform managed \
  --region us-central1 \
  --memory 2Gi \
  --cpu 1 \
  --set-env-vars YAWL_ENGINE_MODE=stateless,YAWL_EVENT_STORE=kafka \
  --allow-unauthenticated
```

---

## Option 5: Azure Container Instances

### 5.1: Create Container Group

```bash
az container create \
  --resource-group yawl-rg \
  --name yawl-stateless \
  --image myregistry.azurecr.io/yawl-stateless:6.0.0-GA \
  --cpu 2 \
  --memory 2 \
  --ports 8080 \
  --environment-variables \
    YAWL_ENGINE_MODE=stateless \
    YAWL_EVENT_STORE=kafka \
  --registry-login-server myregistry.azurecr.io \
  --registry-username $USERNAME \
  --registry-password $PASSWORD
```

---

## Event Store Configuration

### Kafka Setup

```properties
# kafka-config.properties
yawl.event.store=kafka
kafka.bootstrap.servers=kafka1:9092,kafka2:9092,kafka3:9092
kafka.topic.events=yawl-events
kafka.topic.snapshots=yawl-snapshots
kafka.group.id=yawl-engine
kafka.num.partitions=10
kafka.replication.factor=3
```

### RabbitMQ Setup

```properties
yawl.event.store=rabbitmq
rabbitmq.host=rabbitmq.example.com
rabbitmq.port=5672
rabbitmq.username=yawl
rabbitmq.password=secret
rabbitmq.exchange=yawl.events
rabbitmq.queue=yawl.event.queue
```

### S3 Setup (AWS)

```properties
yawl.event.store=s3
aws.s3.bucket=yawl-events
aws.s3.region=us-east-1
aws.access.key=${AWS_ACCESS_KEY}
aws.secret.key=${AWS_SECRET_KEY}
```

---

## Monitoring & Observability

### Prometheus Metrics

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'yawl-stateless'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
```

### Useful Metrics

```bash
# Event processing rate
curl http://localhost:8080/actuator/metrics/yawl.events.processed

# Case execution rate
curl http://localhost:8080/actuator/metrics/yawl.cases.executed

# Event store latency
curl http://localhost:8080/actuator/metrics/yawl.event.store.latency
```

---

## Troubleshooting

### Pod keeps restarting

Check logs:
```bash
kubectl logs -f deployment/yawl-stateless
```

Common causes:
- Event store not reachable
- Insufficient memory
- Configuration error

### High latency on event processing

Check metrics and consider:
- Increasing event store partitions
- Increasing replica count
- Upgrading CPU/memory

---

## See Also

- [Stateless Architecture](../../explanation/stateless-architecture.md)
- [Event-Sourced Patterns](../../reference/event-sourcing-patterns.md)
- [Kubernetes Best Practices](../../reference/kubernetes-deployment.md)
