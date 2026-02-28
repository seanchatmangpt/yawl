# YAWL Templates & Ready-to-Use Samples

Complete, copy-paste templates for workflows, deployments, code, and configurations.

## Quick Start

1. **Pick a template** from the categories below
2. **Copy to your project** (or customize inline)
3. **Update placeholder values** (marked with `changeme`, `TODO`, variable names)
4. **Test** with `scripts/dx.sh` or deployment commands
5. **Reference docs** linked in each template header

## Directory Structure

```
templates/
├── workflows/          # Complete YAWL workflow specs (XML)
├── deployments/        # Docker, Kubernetes, CI/CD configs
│   ├── docker/        # Docker Compose files
│   ├── kubernetes/     # Kubernetes manifests
│   └── ci-cd/         # GitHub Actions, GitLab CI pipelines
├── code/              # Copy-paste Java code examples
│   ├── handlers/      # Custom work item handlers
│   ├── api-clients/   # REST API clients
│   └── event-subscribers/ # Event listening patterns
└── config/            # Configuration templates
    ├── auth/          # Authentication & security
    ├── monitoring/    # Prometheus, metrics setup
    └── scheduling/    # Calendar, SLA, recurring workflows
```

---

## Workflow Templates (XML)

### 1. Basic Request/Approval Workflow
**File**: `workflows/01-request-approval-workflow.xml`

Simple 2-step workflow: submit request → manager approves/rejects.

**When to use**:
- Leave requests, equipment requisitions, budget approvals

**Key features**:
- User submission form
- Manager approval with comment
- Conditional branching (approved/rejected)
- Email notifications

---

### 2. Multi-Step Data Validation Workflow
**File**: `workflows/02-multi-step-validation-workflow.xml`

Sequential validation stages with error handling and retry logic.

**When to use**:
- Data import pipelines, form submissions, document processing

**Key features**:
- 3-stage validation (format, business rules, external)
- Auto-retry with exponential backoff
- Error collection and reporting
- Audit trail

---

### 3. Parallel Approval Workflow
**File**: `workflows/03-parallel-approval-workflow.xml`

Multiple approvers in parallel; requires all approvals to proceed.

**When to use**:
- High-value purchases, policy changes, complex hiring

**Key features**:
- AND-split for parallel branches
- AND-join for synchronization
- Escalation for non-responsive approvers
- Aggregate decision logic

---

### 4. Exception Handling Workflow
**File**: `workflows/04-exception-handling-workflow.xml`

Sophisticated error handling, retries, compensation, and escalation.

**When to use**:
- Payment processing, integrations, critical business processes

**Key features**:
- Exception detection and classification
- Automatic retry with exponential backoff
- Worklet service integration
- Transaction compensation (rollback)
- Manual escalation for fatal errors

---

## Deployment Templates

### Docker Compose
**File**: `deployments/docker/docker-compose-stateless-engine.yml`

Production-ready stack with YAWL Engine, PostgreSQL, Redis, Prometheus, Grafana, Jaeger.

**Quick start**:
```bash
docker-compose -f deployments/docker/docker-compose-stateless-engine.yml up -d
curl http://localhost:8080/health
```

**Services**:
- YAWL Engine (8080)
- PostgreSQL (5432)
- Redis (6379)
- Prometheus (9090)
- Grafana (3000) - admin:admin
- Jaeger (16686)

---

### Kubernetes Deployment
**File**: `deployments/kubernetes/yawl-engine-deployment.yaml`

Enterprise-grade K8s deployment with auto-scaling, HA, networking.

**Deploy**:
```bash
kubectl apply -f deployments/kubernetes/yawl-engine-deployment.yaml
kubectl get pods -n yawl-production
```

**Features**:
- 3-replicas YAWL Engine (HA)
- StatefulSet PostgreSQL
- HorizontalPodAutoscaler (auto-scale 3-10 replicas)
- Ingress for external access
- NetworkPolicy for security

---

### GitHub Actions CI/CD
**File**: `deployments/ci-cd/github-actions-workflow.yml`

Automated build, test, security scan, deploy to staging/production.

**Install**:
```bash
mkdir -p .github/workflows
cp deployments/ci-cd/github-actions-workflow.yml \
   .github/workflows/yawl-ci-cd.yml
```

**Pipeline stages**:
1. Compile & lint
2. Unit & integration tests
3. Code quality (SonarQube)
4. Security scanning (OWASP, Trivy)
5. Docker image build
6. Deploy to staging
7. Deploy to production
8. Notifications (Slack)

---

## Code Templates

### Custom Work Item Handler
**File**: `code/handlers/CustomWorkItemHandler.java`

Template for implementing custom logic when tasks execute.

**Usage pattern**:
```java
public class PaymentHandler extends CustomWorkItemHandler {
    public void handleWorkItem(YWorkItem workItem, String taskId, String caseId) {
        // 1. Extract input data
        String customerId = workItem.getDataVariable("customerId");

        // 2. Validate
        validateCustomer(customerId);

        // 3. Call external service
        String transactionId = processPayment(customerId);

        // 4. Output result
        workItem.setDataVariable("transactionId", transactionId);

        // 5. Complete task
        engine.completeWorkItem(caseId, taskId, true);
    }
}
```

**Features**:
- Input/output data handling
- Validation with error messages
- External service calls with retry
- Error handling (validation, transient, fatal)
- Audit logging

---

### REST API Client
**File**: `code/api-clients/YawlRestApiClient.java`

Java client library for YAWL REST APIs.

**Usage**:
```java
YawlRestApiClient client = new YawlRestApiClient(
    "http://localhost:8080",
    "username",
    "password"
);

// Launch case
String caseId = client.launchCase("OrderWorkflow", "{...}");

// Get work items
List<WorkItem> items = client.getEnabledWorkItems(caseId);

// Complete task
client.completeWorkItem(caseId, taskId, outputData);
```

---

### Event Subscriber
**File**: `code/event-subscribers/WorkflowEventSubscriber.java`

Listen to YAWL workflow events in real-time.

**Events**:
- `case.launched`, `case.completed`, `case.cancelled`
- `workitem.enabled`, `workitem.started`, `workitem.completed`
- `workflow.error`

**Usage**:
```java
WorkflowEventSubscriber subscriber =
    new WorkflowEventSubscriber("localhost:9092");
subscriber.subscribe("yawl-workflow-events");
subscriber.start();

// Automatically calls:
// - handleCaseLaunched(event)
// - handleWorkItemCompleted(event)
// - handleWorkflowError(event)
// etc.
```

---

## Configuration Templates

### Authentication & Security
**File**: `config/auth/jwt-configuration.yaml`

JWT tokens, OAuth/OIDC, API keys, CORS, rate limiting, TLS/SSL.

**Key settings**:
```yaml
jwt:
  secret: "changeme"  # Generate: openssl rand -base64 32
  expiration: 86400
  algorithm: HS256

cors:
  allowed-origins: ["https://app.example.com"]

rate-limit:
  default-limit: 100
  default-window-seconds: 60
```

---

### Monitoring & Metrics
**File**: `config/monitoring/prometheus-config.yaml`

Prometheus scrape config for YAWL Engine, PostgreSQL, Redis, Kubernetes.

**Metrics**:
- `yawl_case_duration_seconds` - case execution time
- `yawl_workitem_processing_duration_seconds` - task duration
- `yawl_engine_error_total` - error count

**Setup**:
```bash
cp config/monitoring/prometheus-config.yaml /etc/prometheus/
curl -X POST http://localhost:9090/-/reload
```

---

### Scheduling & SLAs
**File**: `config/scheduling/calendar-scheduling.yaml`

Business calendars, working hours, SLA tiers, auto-escalation.

**Key features**:
```yaml
calendars:
  default:
    working_hours:
      monday: ["09:00-17:00"]
    holidays:
      - date: "2026-12-25"
        name: "Christmas"

slas:
  high_priority:
    response_time:
      hours: 1
    resolution_time:
      hours: 4
```

---

## Implementation Checklist

### Before Using a Template

- [ ] Read the template header (PURPOSE, CUSTOMIZATION)
- [ ] Understand the workflow/deployment pattern
- [ ] Identify what needs to be customized
- [ ] Review linked documentation
- [ ] Test in development first

### After Customization

- [ ] Update all placeholder values (changeme, TODO, example.com)
- [ ] Test locally (Docker Compose or single-node K8s)
- [ ] Run security checks (OWASP, SonarQube)
- [ ] Verify against your requirements
- [ ] Set up monitoring/alerting
- [ ] Document any custom modifications

### Before Production Deployment

- [ ] Code review (team, security)
- [ ] Load testing
- [ ] Disaster recovery plan
- [ ] Backup strategy
- [ ] Runbook for operations
- [ ] Rollback procedure

---

## File Locations & Usage

```
# Workflow Upload to Engine
curl -X POST http://localhost:8080/yawl/gateway/uploadSpecification \
  -F "specification=@workflows/01-request-approval-workflow.xml"

# Docker Compose: Start Stack
docker-compose -f deployments/docker/docker-compose-stateless-engine.yml up -d

# Kubernetes: Deploy Manifest
kubectl apply -f deployments/kubernetes/yawl-engine-deployment.yaml

# GitHub Actions: Install Workflow
cp deployments/ci-cd/github-actions-workflow.yml .github/workflows/

# Java Code: Copy to Project
cp code/handlers/CustomWorkItemHandler.java \
   src/main/java/com/example/handlers/

# Configuration: Apply Settings
cp config/auth/jwt-configuration.yaml config/
cp config/monitoring/prometheus-config.yaml /etc/prometheus/
cp config/scheduling/calendar-scheduling.yaml config/
```

---

## Common Customizations

### Change YAWL Engine Port
- Docker: Update `ports: - "8080:8080"` in docker-compose.yml
- Kubernetes: Update Service port in yawl-engine-deployment.yaml
- Code: Update `YawlRestApiClient("http://localhost:9999", ...)`

### Use External Database
- Docker: Remove postgres service, set `DB_URL` in environment
- Kubernetes: Update `DB_URL` in ConfigMap

### Add Custom Metrics
- Prometheus: Add scrape_configs section
- Grafana: Create dashboard with new metrics

### Extend Validation Workflow
- XML: Copy stage2Check/stage3Check blocks
- Java: Implement additional validation handler

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Docker Compose fails to start | Check Docker running, verify ports free, review logs |
| Kubernetes pod crashes | `kubectl logs -f pod/name -n yawl-production` |
| API authentication fails | Verify JWT secret, check token expiration |
| Workflow upload fails | Validate XML syntax, check schema compliance |
| Metrics not appearing | Verify Prometheus scrape_configs, check endpoints |
| SLA not triggering | Check calendar working_hours, verify SLA config |

---

## Next Steps

1. **Choose template** matching your use case
2. **Customize** with your requirements
3. **Test locally** (Docker Compose)
4. **Deploy to staging** (Kubernetes)
5. **Monitor** (Prometheus/Grafana)
6. **Production rollout** (CI/CD pipeline)

---

## References

- YAWL Workflow Patterns: `/docs/architecture/WORKFLOW-PATTERNS.md`
- Deployment Guide: `/docs/deployments/DEPLOYMENT-GUIDE.md`
- Integration Guide: `/docs/integration/INTEGRATION-GUIDE.md`
- Security Guide: `/docs/security/SECURITY-GUIDE.md`
- Monitoring Guide: `/docs/observability/MONITORING-GUIDE.md`

For issues or questions, open GitHub issue or visit YAWL Forum.
