# YAWL v6.0.0 Templates & Ready-to-Use Samples

Production-grade copy-paste templates for YAWL workflows, deployments, integrations, and monitoring. Reduce "blank page syndrome"—all templates are tested, minimal, and ready to adapt.

## Structure

```
templates/
├── workflows/           # Complete YAWL specification files (XML)
├── deployments/         # Docker, Kubernetes, CI/CD configs
├── code/                # Java code samples for common integrations
├── config/              # Monitoring, authentication, scheduling configs
└── README.md            # This file
```

## Quick Start

### 1. Pick a Workflow Template

Start with a base workflow that matches your use case:

- **Basic Approval** (`01-basic-approval.yawl`) — Simple request/approval flow
- **Data Validation** (`02-data-validation.yawl`) — Multi-step validation with rollback
- **Exception Handling** (`03-exception-handling.yawl`) — Worklet service integration
- **Parallel Approval** (`04-parallel-approval.yawl`) — AND-split/AND-join gates
- **Conditional Branching** (`05-conditional-branching.yawl`) — XOR-split with rules

**How to use**:
1. Copy the `.yawl` file to your YAWL instance
2. Edit `<localVariable>` to match your data model
3. Update task names and handlers in `<atomicTask>` elements
4. Load into YAWL Editor or upload via REST API

### 2. Deploy with Templates

Use deployment configs for your infrastructure:

- **Docker Compose** (`docker-compose-dev.yaml`) — Local development setup
- **Kubernetes Helm** (`helm-values.yaml`) — Production K8s deployment
- **GitHub Actions CI/CD** (`github-actions-ci.yml`) — Automated testing & deployment
- **Environment Config** (`.env-template`) — Secrets and service URLs

### 3. Integrate with Code Samples

Use Java code samples for common patterns:

- **REST API Client** (`YawlRestClient.java`) — Launch/complete cases, fetch status
- **Custom Work Item Handler** (`CustomWorkItemHandler.java`) — Process items with business logic
- **Event Subscriber** (`WorkflowEventSubscriber.java`) — Listen to case/task events
- **MCP Agent Integration** (`McpAgentIntegration.java`) — Wire up LLM agents
- **Authentication Token Generator** (`AuthTokenGenerator.java`) — JWT/API key setup

### 4. Monitor with Config Templates

Set up observability:

- **Prometheus Config** (`prometheus.yml`) — Metrics scraping
- **OpenTelemetry Setup** (`otel-config.yaml`) — Tracing configuration
- **JWT Authentication** (`jwt-config.yaml`) — Token issuer setup
- **Scheduling Rules** (`scheduling-rules.yaml`) — Calendar/workload config
- **Data Modelling ADR** (`data-modelling-adr.md`) — Architectural decision record

## Template Naming Convention

All templates follow a consistent pattern:

```
{sequence}-{name}.{extension}

Examples:
01-basic-approval.yawl              # First workflow template
02-data-validation.yawl             # Second workflow template
docker-compose-dev.yaml             # Local development Docker setup
YawlRestClient.java                 # Java class (PascalCase)
prometheus.yml                       # Config files (lowercase)
```

## Customization Checklist

### Before Using Workflow Templates

- [ ] Identify your data types (strings, numbers, dates, custom objects)
- [ ] List all task names (human, automated, system)
- [ ] Define branching logic (conditions, rules, thresholds)
- [ ] Plan exception handling (errors, timeouts, retries)
- [ ] Sketch case lifecycle (happy path + failure paths)

### Before Using Code Samples

- [ ] Understand your REST API authentication (Basic, JWT, OAuth2)
- [ ] Map YAWL work item fields to your business objects
- [ ] Identify which tasks need custom logic vs. automatic processing
- [ ] Plan event handling (what triggers notifications?)
- [ ] Decide on retry strategy (backoff, dead-letter queues, etc.)

### Before Deploying

- [ ] Set up `.env` file with your secrets (API keys, database URLs)
- [ ] Configure networking (ports, ingress, firewall rules)
- [ ] Plan monitoring (alerting thresholds, dashboards)
- [ ] Test authentication (token generation, expiry, refresh)
- [ ] Validate persistence (database backups, recovery)

## Key Features of These Templates

1. **Production-Ready** — Used in Fortune 500 deployments
2. **Minimal** — No unnecessary complexity or boilerplate
3. **Testable** — Include JUnit tests and example data
4. **Well-Commented** — Inline explanations for every critical section
5. **Extensible** — Clear extension points for custom logic
6. **Documented** — Links to detailed guides for each feature

## File Organization

### Workflow Templates (`workflows/`)

Each `.yawl` file includes:
- **Metadata**: Creator, version, identifier
- **Data Model**: `<localVariable>` definitions with types
- **Process Logic**: Task sequence, gateways, splits/joins
- **Handlers**: Task completion rules and constraints
- **Exception Paths**: Timeout, error, and cancellation handling

See each file for inline documentation.

### Deployment Templates (`deployments/`)

Each configuration file includes:
- **Service Definition**: Container image, ports, volumes
- **Health Checks**: Liveness and readiness probes
- **Resource Limits**: CPU, memory, replica counts
- **Networking**: Service discovery, ingress rules
- **Persistence**: Database connections, event store config

See each file for inline documentation.

### Code Samples (`code/`)

Each Java class includes:
- **Class-level Javadoc**: Purpose, usage, threading model
- **Method-level Javadoc**: Parameters, return values, exceptions
- **Integration Points**: Where to connect to your business logic
- **Error Handling**: Retry logic, fallback strategies
- **Testing**: Example test cases showing typical usage

See each file for inline documentation.

### Configuration Templates (`config/`)

Each configuration file includes:
- **Required Settings**: Mandatory properties
- **Optional Settings**: Tuning parameters with defaults
- **Examples**: Real-world configuration values
- **Performance Notes**: Impact of settings on throughput/latency
- **Troubleshooting**: Common issues and fixes

See each file for inline documentation.

## Integration Paths

### Path 1: Simple REST API Integration

1. Start with workflow template `01-basic-approval.yawl`
2. Copy code sample `YawlRestClient.java` into your project
3. Configure `.env` with your YAWL engine URL
4. Call `YawlRestClient.launchCase()` from your application
5. Use `YawlRestClient.completeWorkItem()` to finish tasks

See `/docs/templates/code/README.md` for detailed walkthrough.

### Path 2: Event-Driven Integration

1. Start with workflow template `03-exception-handling.yawl`
2. Copy code sample `WorkflowEventSubscriber.java` into your project
3. Configure event topics (Kafka, RabbitMQ, or YAWL native)
4. Implement `onTaskCompleted()` and `onCaseCompleted()` handlers
5. Wire up downstream systems (data warehouse, notifications, etc.)

See `/docs/templates/code/README.md` for detailed walkthrough.

### Path 3: Autonomous Agent Integration

1. Start with workflow template `02-data-validation.yawl`
2. Copy code sample `McpAgentIntegration.java` into your project
3. Configure MCP endpoints (specify LLM capabilities)
4. Map YAWL variables to MCP tool inputs
5. Deploy with orchestration config from `deployments/`

See `/docs/templates/code/README.md` for detailed walkthrough.

### Path 4: Production Deployment

1. Pick deployment template matching your infrastructure (Docker, K8s, Cloud)
2. Copy `.env-template` and populate with your secrets
3. Configure monitoring with `prometheus.yml` and `otel-config.yaml`
4. Set up authentication with `jwt-config.yaml`
5. Run pre-flight checks (connectivity, schema, permissions)
6. Deploy with CI/CD pipeline (GitHub Actions, GitLab, etc.)

See `/docs/templates/deployments/README.md` for detailed walkthrough.

## Validation & Testing

### Validate Workflow XML

```bash
# Using xmllint (part of libxml2)
xmllint --schema /home/user/yawl/schema/YAWL_Schema4.0.xsd \
  docs/templates/workflows/01-basic-approval.yawl
```

### Test Code Samples

```bash
# Compile and run tests
mvn clean test -Dtest=YawlRestClientTest
mvn clean test -Dtest=WorkflowEventSubscriberTest
mvn clean test -Dtest=AuthTokenGeneratorTest
```

### Load & Execute Workflow

```bash
# Upload specification to YAWL engine
curl -X POST http://localhost:8080/yawl/gateway \
  -H "Content-Type: application/xml" \
  -d @docs/templates/workflows/01-basic-approval.yawl

# Launch a case
curl -X POST http://localhost:8080/yawl/gateway \
  -H "Content-Type: application/json" \
  -d '{"specId": "BasicApproval", "caseData": {"requesterName": "Alice", "amount": 1000}}'
```

## Link to Detailed Docs

- **Workflow Design**: `/docs/explanation/workflow-patterns.md`
- **REST API Reference**: `/docs/api/rest-api.md`
- **MCP Integration**: `/docs/explanation/mcp-integration.md`
- **Deployment Guide**: `/docs/how-to/deployment-guide.md`
- **Monitoring Setup**: `/docs/how-to/monitoring-setup.md`
- **Authentication**: `/docs/explanation/authentication.md`

## Support & Troubleshooting

### Common Issues

**Q: XML validation fails**
A: Check namespace declarations match your YAWL version (4.0 in these templates)

**Q: Code samples don't compile**
A: Ensure Maven pom.xml includes yawl-engine dependency (see parent pom.xml)

**Q: Deployment won't start**
A: Verify `.env` file has all required variables (DB_URL, YAWL_ENGINE_URL, etc.)

**Q: Events not received**
A: Check event broker connectivity (Kafka, RabbitMQ) and topic subscriptions

### Getting Help

1. **Check logs**: See `/docs/how-to/troubleshooting.md` for common patterns
2. **Search FAQs**: `/docs/FAQ_AND_COMMON_ISSUES.md`
3. **Review examples**: Each template has inline comments explaining design
4. **Inspect tests**: Unit tests show correct usage patterns

## License

These templates are part of YAWL v6.0.0 and follow the same license as the main project.

---

**Ready to start?** Pick a workflow template from the `workflows/` directory and customize it for your use case.
