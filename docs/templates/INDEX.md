# YAWL Templates Index

Complete reference to all ready-to-use templates organized by use case.

## Quick Navigation

### I want to... [Find the right template]

#### Workflows (XML Specs)
- **Submit a form and get approval?** → [01-request-approval-workflow.xml](./workflows/01-request-approval-workflow.xml)
- **Validate data through multiple steps?** → [02-multi-step-validation-workflow.xml](./workflows/02-multi-step-validation-workflow.xml)
- **Get approval from multiple people at once?** → [03-parallel-approval-workflow.xml](./workflows/03-parallel-approval-workflow.xml)
- **Handle errors and retry automatically?** → [04-exception-handling-workflow.xml](./workflows/04-exception-handling-workflow.xml)

#### Deployments
- **Run YAWL locally with Docker?** → [docker-compose-stateless-engine.yml](./deployments/docker/docker-compose-stateless-engine.yml)
- **Deploy to Kubernetes (production)?** → [yawl-engine-deployment.yaml](./deployments/kubernetes/yawl-engine-deployment.yaml)
- **Set up CI/CD pipeline?** → [github-actions-workflow.yml](./deployments/ci-cd/github-actions-workflow.yml)

#### Code (Java)
- **Run custom logic when a task executes?** → [CustomWorkItemHandler.java](./code/handlers/CustomWorkItemHandler.java)
- **Call YAWL APIs from my application?** → [YawlRestApiClient.java](./code/api-clients/YawlRestApiClient.java)
- **Listen to workflow events in real-time?** → [WorkflowEventSubscriber.java](./code/event-subscribers/WorkflowEventSubscriber.java)

#### Configuration
- **Set up authentication (JWT, OAuth, API keys)?** → [jwt-configuration.yaml](./config/auth/jwt-configuration.yaml)
- **Monitor with Prometheus and Grafana?** → [prometheus-config.yaml](./config/monitoring/prometheus-config.yaml)
- **Define business hours and SLAs?** → [calendar-scheduling.yaml](./config/scheduling/calendar-scheduling.yaml)

---

## Templates by Category

### Workflow Templates (4 files)

| File | Purpose | Use Case | Complexity |
|------|---------|----------|-----------|
| `01-request-approval-workflow.xml` | Simple 2-step request/approval | Leave requests, equipment, budget | Easy |
| `02-multi-step-validation-workflow.xml` | Sequential data validation | Form validation, data import, EDI | Medium |
| `03-parallel-approval-workflow.xml` | Multi-way parallel approvals | High-value purchases, policy changes | Medium |
| `04-exception-handling-workflow.xml` | Complex error handling + retries | Payment processing, integrations | Hard |

**Features across all workflow templates**:
- Complete, valid YAWL 4.0 XML schemas
- Embedded documentation
- Data variable definitions
- Task decomposition references
- Inline comments for customization

**To use**:
1. Copy XML file to your workflow editor
2. Update task names, documentation, roles
3. Connect to your services/handlers
4. Test with YAWL Engine

### Deployment Templates (3 files)

| File | Platform | Services | When to Use |
|------|----------|----------|-----------|
| `docker-compose-stateless-engine.yml` | Docker | YAWL, PG, Redis, Prometheus, Grafana, Jaeger | Local development, testing |
| `yawl-engine-deployment.yaml` | Kubernetes | Stateless YAWL (3x), PG, Redis, ingress, autoscaling | Production |
| `github-actions-workflow.yml` | GitHub Actions | Compile, test, scan, build, deploy | CI/CD automation |

**Features**:
- Production-ready configurations
- Health checks and liveness probes
- Resource limits and requests
- Persistent storage configuration
- Monitoring and logging setup
- Security best practices

**To use**:
1. Copy config file to your environment
2. Update credentials/secrets
3. Deploy with docker-compose or kubectl
4. Verify with health checks

### Code Templates (3 files, ~2000 LOC)

| File | Language | Purpose | When to Use |
|------|----------|---------|-----------|
| `CustomWorkItemHandler.java` | Java | Execute custom logic for tasks | Integration, business logic |
| `YawlRestApiClient.java` | Java | Call YAWL APIs from Java apps | App integration, automation |
| `WorkflowEventSubscriber.java` | Java | Listen to workflow events | Real-time monitoring, notifications |

**Features**:
- Complete, working code (not stubs)
- Inline documentation and examples
- Error handling and retries
- Integration patterns
- Extensible class hierarchies

**To use**:
1. Copy Java class to your project
2. Extend or customize methods
3. Implement application-specific logic
4. Register with YAWL Engine

### Configuration Templates (3 files)

| File | Format | Purpose | Settings |
|------|--------|---------|----------|
| `jwt-configuration.yaml` | YAML | Authentication & security | JWT, OAuth, CORS, rate limiting, TLS |
| `prometheus-config.yaml` | YAML | Metrics collection | Scrape configs, alert rules, storage |
| `calendar-scheduling.yaml` | YAML | SLAs & deadlines | Business hours, holidays, SLA tiers |

**Features**:
- All configuration options documented
- Sensible defaults provided
- Multiple examples (standard, 24/7, shifts)
- Production security hardening
- Alert rule definitions

**To use**:
1. Copy config file
2. Update placeholders (passwords, URLs, domains)
3. Deploy to config management
4. Reload/restart application

---

## File Statistics

```
Total Templates: 15 files
├── Workflows (XML):       4 files (1,500 lines)
├── Deployments:           3 files (900 lines)
├── Code (Java):           3 files (2,100 lines)
└── Config (YAML):         3 files (800 lines)

Total Lines of Code: ~5,300 lines
Estimated time to adapt: 2-4 hours per template
Learning curve: Low (well-documented, commented)
Production-ready: Yes
```

---

## Getting Started (5 Minutes)

### Step 1: Choose Your Template
Find a template from the "Quick Navigation" section above that matches your need.

### Step 2: Copy Template
```bash
# Example: Docker deployment
cp docs/templates/deployments/docker/docker-compose-stateless-engine.yml .
```

### Step 3: Customize
Edit the file and update:
- Placeholder values (search for `changeme`, `example.com`, `TODO`)
- Port numbers, hostnames
- Secret values, credentials
- Service names, roles

### Step 4: Test
```bash
# Workflows
curl -X POST http://localhost:8080/yawl/gateway/uploadSpecification \
  -F "specification=@template.xml"

# Docker
docker-compose up -d

# Kubernetes
kubectl apply -f manifest.yaml

# Java
mvn test
```

### Step 5: Deploy
Follow deployment instructions in the template header.

---

## Template Structure & Conventions

### Every Template Includes

**Header Block**:
```xml/yaml/java
# TEMPLATE: [Name]
# PURPOSE: [What it's for]
# CUSTOMIZATION: [What to change]
# LINK: [Detailed documentation reference]
```

**Documentation**:
- Purpose and use cases
- Key features
- Customization checklist
- Testing examples
- Integration patterns

**Inline Comments**:
- Explain key sections
- Mark variables to update
- Show examples
- Reference related templates

### Customization Patterns

**Marked for Change**:
- `changeme` - Replace with your value
- `TODO:` - Implement specific logic
- `example.com` - Replace with your domain
- `PT5S` - Duration in ISO 8601 format
- Variable names like `${variable}` - Template substitution

**Common Customizations**:
```
# Search and replace
- Ports (8080, 5432, 9090)
- Domains (localhost, example.com)
- Credentials (passwords, API keys, secrets)
- Names (workflow names, task names)
- Thresholds (timeouts, SLA hours, retry counts)
```

---

## Integration Paths

### Simple Integration (30 min)
1. Pick workflow template
2. Upload to YAWL Engine
3. Test case execution
4. Connect to your service endpoints

### Standard Integration (2-4 hours)
1. Deploy with Docker Compose
2. Create custom work item handler
3. Set up basic monitoring
4. Configure authentication

### Full Production Setup (1-2 days)
1. Deploy to Kubernetes
2. Implement event subscribers
3. Set up CI/CD pipeline
4. Configure monitoring, logging, alerting
5. Load testing and optimization

---

## Common Issues & Solutions

| Issue | Check | Solution |
|-------|-------|----------|
| Workflow XML invalid | XML syntax, schema | Use xmllint, validate schema |
| Docker won't start | Docker running, ports free | `docker-compose ps`, check logs |
| API calls fail | Auth token, endpoint URL | Verify JWT secret, check CORS |
| Metrics missing | Prometheus config, scrape path | Check `__meta_kubernetes` labels |
| Task never completes | Handler implementation | Add debug logging, check output |
| SLA not triggering | Calendar config, working hours | Verify timezone, check dates |

---

## Documentation Cross-Reference

For more details on each area, see:

### Workflows & Patterns
- `docs/architecture/WORKFLOW-PATTERNS.md` - Design patterns for workflows
- `docs/architecture/INTERFACES.md` - Task decomposition types
- `docs/QUICK-START.md` - Getting started with YAWL

### Deployments
- `docs/deployments/DEPLOYMENT-GUIDE.md` - Full deployment reference
- `docs/deployments/DOCKER-DEPLOYMENT.md` - Docker-specific guide
- `docker/README.md` - Docker infrastructure

### Integration & APIs
- `docs/api/API-GUIDE.md` - Complete REST API reference
- `docs/integration/INTEGRATION-GUIDE.md` - Integration patterns
- `docs/api/sdks/java/YawlClient.java` - Full Java SDK

### Security & Monitoring
- `docs/security/SECURITY-GUIDE.md` - Security best practices
- `docs/observability/MONITORING-GUIDE.md` - Monitoring setup
- `docs/observability/ALERT-RULES.md` - Alert rule examples

---

## Contributing New Templates

Want to add your own template?

1. **Create file** in appropriate directory
2. **Follow conventions**:
   - Add header block with PURPOSE, CUSTOMIZATION, LINK
   - Include inline documentation
   - Mark customization points clearly
   - Add usage examples
3. **Update INDEX.md** with entry
4. **Validate** (XML schema, syntax, completeness)
5. **Submit PR** to YAWL project

---

## Version & Support

- **YAWL Version**: 6.0.0+
- **Java**: 25+
- **Docker**: 24.0+
- **Kubernetes**: 1.28+
- **Browsers**: Chrome, Firefox, Safari (latest)

For version-specific templates, see releases:
- YAWL 5.x: Use legacy branch
- YAWL 6.x: Use current templates

---

## Quick Links

- **YAWL Project**: https://www.yawlfoundation.org/
- **GitHub**: https://github.com/yawl/yawl
- **Docker Hub**: https://hub.docker.com/u/yawl
- **Documentation**: https://www.yawlfoundation.org/documentation
- **Forum**: https://www.yawlfoundation.org/forum

---

Last Updated: 2026-02-28
For latest templates and updates, visit the YAWL project repository.
