# Actor Validation Deployment Guide

This guide covers the deployment of the Actor Validation System in YAWL v6.0.0.

## Prerequisites

- Java 25 or later
- Maven 4.0 or later
- Docker (for containerized deployment)
- Kubernetes (optional, for production)

## Deployment Methods

### 1. Local Development

#### Quick Start

```bash
# Clone and setup
git clone https://github.com/yawlfoundation/yawl.git
cd yawl

# Build with actor validation
./scripts/build-with-actor-validation.sh

# Run locally
mvn spring-boot:run -P actor-validation
```

#### Configuration

Create local configuration file:

```yaml
# config/local/actor-validation.yml
validation:
  enabled: true
  interval_seconds: 30
  memory_threshold_mb: 100

mcp:
  enabled: true
  port: 8080
  host: "localhost"

observability:
  enabled: true
  metrics_port: 9090
  tracing_enabled: true
```

### 2. Production Deployment

#### Docker Deployment

```bash
# Build Docker image
docker build -t yawl-actor-validation -f docker/Dockerfile.actor-validation .

# Run container
docker run -d \
  --name yawl-actor-validation \
  -p 8080:8080 \
  -p 9090:9090 \
  -e ACTOR_VALIDATION_ENABLED=true \
  -e ACTOR_MEMORY_THRESHOLD=100 \
  yawl-actor-validation
```

#### Kubernetes Deployment

```yaml
# k8s/actor-validation-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-actor-validation
spec:
  replicas: 3
  selector:
    matchLabels:
      app: yawl-actor-validation
  template:
    metadata:
      labels:
        app: yawl-actor-validation
    spec:
      containers:
      - name: actor-validation
        image: yawl-actor-validation:latest
        ports:
        - containerPort: 8080
          name: mcp
        - containerPort: 9090
          name: metrics
        env:
        - name: ACTOR_VALIDATION_ENABLED
          value: "true"
        - name: ACTOR_MEMORY_THRESHOLD
          value: "100"
        resources:
          requests:
            memory: "2Gi"
            cpu: "1"
          limits:
            memory: "4Gi"
            cpu: "2"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: yawl-actor-validation-service
spec:
  selector:
    app: yawl-actor-validation
  ports:
  - name: mcp
    port: 8080
    targetPort: 8080
  - name: metrics
    port: 9090
    targetPort: 9090
  type: ClusterIP
```

Apply the deployment:

```bash
kubectl apply -f k8s/actor-validation-deployment.yaml
```

### 3. Cloud Deployment

#### AWS ECS

```json
// ecs/task-definition.json
{
  "family": "yawl-actor-validation",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::123456789012:role/ecsTaskExecutionRole",
  "containerDefinitions": [
    {
      "name": "actor-validation",
      "image": "123456789012.dkr.ecr.us-east-1.amazonaws.com/yawl-actor-validation:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        },
        {
          "containerPort": 9090,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "ACTOR_VALIDATION_ENABLED",
          "value": "true"
        },
        {
          "name": "ACTOR_MEMORY_THRESHOLD",
          "value": "100"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/yawl-actor-validation",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

#### Azure Kubernetes Service (AKS)

```bash
# Create namespace
kubectl create namespace yawl-actor-validation

# Apply Helm chart
helm install yawl-actor-validation charts/yawl-actor-validation \
  --namespace yawl-actor-validation \
  --set image.tag=latest \
  --set replicaCount=3
```

## Configuration Management

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `ACTOR_VALIDATION_ENABLED` | Enable actor validation | `true` | No |
| `ACTOR_MEMORY_THRESHOLD` | Memory leak detection threshold (MB) | `100` | No |
| `ACTOR_VALIDATION_INTERVAL` | Validation interval (seconds) | `30` | No |
| `ACTOR_VALIDATION_VERBOSE` | Enable verbose logging | `false` | No |
| `MCP_PORT` | MCP server port | `8080` | No |
| `MCP_HOST` | MCP server host | `localhost` | No |
| `METRICS_PORT` | Metrics server port | `9090` | No |
| `OTEL_ENABLED` | Enable OpenTelemetry | `true` | No |

### Configuration Files

#### Production Config

```yaml
# config/production/actor-validation.yml
validation:
  enabled: true
  interval_seconds: 60
  memory_threshold_mb: 150
  performance_threshold_ms: 5000

mcp:
  enabled: true
  port: 8080
  host: "0.0.0.0"
  ssl_enabled: true
  ssl_key_store: "/path/to/keystore.jks"
  ssl_key_store_password: "${SSL_KEYSTORE_PASSWORD}"

observability:
  enabled: true
  metrics_port: 9090
  tracing_enabled: true
  jaeger_endpoint: "http://jaeger:14268/api/traces"

monitoring:
  health_check_interval: 30
  alert_rules:
    - name: "High Memory Usage"
      condition: "memory_usage > 90%"
      severity: "critical"
    - name: "Slow Processing"
      condition: "processing_time > 5000"
      severity: "warning"
    - name: "Deadlock Risk"
      condition: "deadlock_count > 0"
      severity: "critical"
```

## Monitoring and Observability

### Prometheus Metrics

```yaml
# prometheus/prometheus.yml
scrape_configs:
  - job_name: 'yawl-actor-validation'
    static_configs:
      - targets: ['yawl-actor-validation:9090']
    metrics_path: /metrics
    scrape_interval: 30s
```

### Grafana Dashboard

Key metrics to monitor:
- Actor count
- Memory usage
- Processing time
- Violation count
- Health status

Alert rules:
- Memory usage > 90%
- Processing time > 5s
- Deadlock count > 0
- Error rate > 5%

### Logging

Configure structured logging:

```yaml
# logging-config.yml
logging:
  level:
    org.yawlfoundation.yawl.integration.actor: INFO
  loggers:
    actor-validation:
      appender-ref:
        - ref: FILE
        - ref: CONSOLE
```

## Deployment Checklist

### Pre-Deployment

- [ ] Configure all required environment variables
- [ ] Set up monitoring and alerting
- [ ] Prepare configuration files
- [ ] Test deployment script
- [ ] Verify resource requirements
- [ ] Set up SSL/TLS certificates
- [ ] Configure backup and recovery

### Deployment

- [ ] Deploy to staging environment
- [ ] Run integration tests
- [ ] Validate metrics and logs
- [ ] Check performance
- [ ] Verify monitoring and alerting
- [ ] Run load testing
- [ ] Check for resource leaks

### Post-Deployment

- [ ] Monitor for 24 hours
- [ ] Check for violations
- [ ] Verify MCP tools are working
- [ ] Test recovery procedures
- [ ] Update documentation
- [ ] Train operations team
- [ ] Perform security scan

## Rollback Plan

### Automated Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/yawl-actor-validation

# Check rollout status
kubectl rollout status deployment/yawl-actor-validation
```

### Manual Rollback

1. Identify the problematic version
2. Create backup of current configuration
3. Rollback to previous stable version
4. Verify functionality
5. Monitor for issues

## Performance Tuning

### Resource Allocation

- **Memory**: Start with 2GB, scale based on actor count
- **CPU**: Start with 1 core, scale based on load
- **Storage**: SSD for better performance

### Optimization Strategies

1. **Virtual Threads**: Enable for better scalability
2. **Caching**: Cache validation results
3. **Batch Processing**: Process validations in batches
4. **Lazy Loading**: Load resources on demand

## Security Considerations

### Network Security

- Use HTTPS for MCP connections
- Implement authentication and authorization
- Use network policies in Kubernetes
- Monitor for suspicious activity

### Data Security

- Encrypt sensitive configuration
- Use secure storage for credentials
- Implement audit logging
- Regular security scanning

## Maintenance

### Regular Tasks

- [ ] Update dependencies
- [ ] Review configuration
- [ ] Monitor metrics
- [ ] Check for new vulnerabilities
- [ ] Update documentation
- [ ] Train team members

### Backup and Recovery

- Configuration backup
- Metrics backup
- Log retention
- Disaster recovery procedures

## Troubleshooting

### Common Issues

1. **High Memory Usage**
   - Check for memory leaks
   - Adjust memory thresholds
   - Increase heap size

2. **Slow Performance**
   - Check thread pools
   - Optimize validation logic
   - Increase resources

3. **MCP Connection Issues**
   - Check network connectivity
   - Verify configuration
   - Check server status

### Debug Commands

```bash
# Check logs
kubectl logs deployment/yawl-actor-validation -f

# Check metrics
curl http://yawl-actor-validation:9090/metrics

# Check MCP health
curl http://yawl-actor-validation:8080/health

# Run diagnostic script
./scripts/actor-validation-diagnostics.sh
```

## Support

- GitHub Issues: `actor-validation` label
- Documentation: `/docs/actor-validation.md`
- Contact: yawl-foundation@example.com