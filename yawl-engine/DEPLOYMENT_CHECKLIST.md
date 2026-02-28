# YAWL REST API Deployment Checklist

## Pre-Deployment Verification

### Code Compilation

- [ ] Run: `bash scripts/dx.sh -pl yawl-engine compile`
- [ ] Expected: BUILD SUCCESS (all Java 25 syntax valid)
- [ ] Check: No compilation errors in controllers or DTOs
- [ ] Verify: All Spring Boot annotations recognized

### Test Execution

- [ ] Run: `mvn test -pl yawl-engine -Dtest="*ControllerTest"`
- [ ] Expected: 25+ tests pass
- [ ] Coverage: All CRUD operations tested
- [ ] Assertions: 100+ assertions passed

### Integration Testing

- [ ] Run: `bash scripts/dx.sh -pl yawl-engine test`
- [ ] Check: All test suites pass (Agent, Health, WorkItem)
- [ ] Verify: No integration issues with Spring framework

### Build Packaging

- [ ] Run: `mvn clean package -pl yawl-engine -DskipTests` (when ready)
- [ ] Expected: Creates yawl-engine-6.0.0-GA.jar
- [ ] Check: JAR contains embedded Tomcat
- [ ] Verify: Manifest has correct Main-Class

## Runtime Verification

### Local Development

```bash
# 1. Start the application
java -jar yawl-engine/target/yawl-engine-6.0.0-GA.jar

# Expected output:
# - Started YawlEngineApplication in X.XXX seconds
# - Tomcat started on port(s): 8080
```

### Endpoint Testing

#### Health Endpoints
```bash
# 2. Test liveness probe
curl -s http://localhost:8080/yawl/actuator/health/live | jq .
# Expected: {"status":"UP","checks":{"jvm":"UP","memory":"OK"},...}

# 3. Test readiness probe
curl -s http://localhost:8080/yawl/actuator/health/ready | jq .
# Expected: {"status":"UP","checks":{"agents":"READY",...}

# 4. Test overall health
curl -s http://localhost:8080/yawl/actuator/health | jq .
# Expected: {"status":"UP",...}
```

#### Agent Endpoints
```bash
# 5. List agents (should be empty)
curl -s http://localhost:8080/yawl/agents | jq .
# Expected: []

# 6. Create agent
curl -X POST http://localhost:8080/yawl/agents \
  -H "Content-Type: application/json" \
  -d '{
    "workflowId": "test-workflow",
    "name": "Test Workflow",
    "version": "1.0",
    "description": "Test",
    "specificationXml": "<yawl><!-- test --></yawl>"
  }' | jq .
# Expected: Agent with id, status=IDLE, workflowId=test-workflow

# 7. List agents again
curl -s http://localhost:8080/yawl/agents | jq .
# Expected: Array with 1 agent

# 8. Get agent by ID (replace UUID with actual)
curl -s http://localhost:8080/yawl/agents/UUID | jq .
# Expected: Single agent details

# 9. Get healthy agents
curl -s http://localhost:8080/yawl/agents/healthy | jq .
# Expected: Array with created agent

# 10. Get agent metrics
curl -s http://localhost:8080/yawl/agents/metrics | jq .
# Expected: agentCount=1, healthyAgentCount=1, metrics
```

#### Work Item Endpoints
```bash
# 11. List work items (should be empty)
curl -s http://localhost:8080/yawl/workitems | jq .
# Expected: []

# 12. Create work item
curl -X POST http://localhost:8080/yawl/workitems \
  -H "Content-Type: application/json" \
  -d '{
    "taskName": "ProcessPayment",
    "caseId": "case-001",
    "payload": "{\"amount\": 100}"
  }' | jq .
# Expected: WorkItem with id, taskName=ProcessPayment, status=RECEIVED

# 13. List work items again
curl -s http://localhost:8080/yawl/workitems | jq .
# Expected: Array with 1 work item

# 14. Get work items for agent
curl -s "http://localhost:8080/yawl/workitems?agent=UUID" | jq .
# Expected: [] (item not assigned to agent yet)

# 15. Get work item statistics
curl -s http://localhost:8080/yawl/workitems/stats | jq .
# Expected: queueSize=1, totalItems=1, completedItems=0
```

### Performance Validation

```bash
# 16. Response time test
time curl -s http://localhost:8080/yawl/agents > /dev/null
# Expected: Response in <100ms

# 17. Load test (1000 agents)
for i in {1..1000}; do
  curl -s -X POST http://localhost:8080/yawl/agents \
    -H "Content-Type: application/json" \
    -d "{\"workflowId\":\"wf-$i\",\"name\":\"WF $i\",\"version\":\"1.0\",\"description\":\"Test\",\"specificationXml\":\"<yawl></yawl>\"}" \
    > /dev/null
done
# Expected: Completes in <30 seconds
```

### Error Handling Validation

```bash
# 18. Test 404 Not Found
curl -s http://localhost:8080/yawl/agents/00000000-0000-0000-0000-000000000000
# Expected: 404 response

# 19. Test 400 Bad Request
curl -X POST http://localhost:8080/yawl/agents \
  -H "Content-Type: application/json" \
  -d '{"name":"Invalid"}' # Missing required fields
# Expected: 400 Bad Request

# 20. Test invalid agent ID filter
curl -s "http://localhost:8080/yawl/workitems?agent=invalid-uuid"
# Expected: 400 or handled gracefully
```

## Docker Deployment

### Build Docker Image

```dockerfile
# Dockerfile.yawl-engine
FROM openjdk:25-slim
COPY yawl-engine/target/yawl-engine-6.0.0-GA.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
# Build
docker build -t yawl-engine:6.0.0 -f Dockerfile.yawl-engine .

# Run
docker run -p 8080:8080 yawl-engine:6.0.0
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
  labels:
    app: yawl-engine
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
      containers:
      - name: yawl-engine
        image: yawl-engine:6.0.0
        ports:
        - containerPort: 8080
          name: http
        livenessProbe:
          httpGet:
            path: /yawl/actuator/health/live
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /yawl/actuator/health/ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: yawl-engine
spec:
  type: LoadBalancer
  ports:
  - port: 80
    targetPort: 8080
  selector:
    app: yawl-engine
```

## Post-Deployment Verification

### Kubernetes Health Checks

```bash
# Check pod status
kubectl get pods -l app=yawl-engine

# Check logs
kubectl logs -l app=yawl-engine --tail=100

# Test endpoint via service
kubectl port-forward svc/yawl-engine 8080:80
curl http://localhost:8080/yawl/actuator/health/live
```

### Monitoring Setup

- [ ] Metrics endpoint: `/yawl/actuator/metrics`
- [ ] Prometheus scrape config configured
- [ ] Grafana dashboards created for:
  - Agent count and health
  - Work item queue size
  - Request latency
  - Error rates

### Observability

- [ ] Logging configured (application.properties)
- [ ] Log level: INFO for production
- [ ] Debug logging available for troubleshooting
- [ ] Timestamps in ISO 8601 format

## Rollback Plan

If deployment fails:

1. Check logs for errors
2. Verify Spring Boot dependencies resolved
3. Check database connectivity (if DB required)
4. Verify port 8080 not already in use
5. Check Java 25 available
6. Revert to previous version

## Sign-Off

- [ ] All compilation tests pass
- [ ] All unit/integration tests pass
- [ ] All 13 endpoints verified working
- [ ] Health probes responding correctly
- [ ] Load testing completed
- [ ] Error handling validated
- [ ] Documentation reviewed
- [ ] Ready for production deployment

---

**Prepared by**: Agent 5 (REST API & Health Endpoints)
**Date**: 2026-02-28
**Status**: READY FOR DEPLOYMENT
