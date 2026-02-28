# YAWL Module Selector

**Time to Complete**: 8-10 minutes
**Goal**: Identify exactly which modules you need for your use case, with explanations

---

## How to Use This Guide

Below are **6 decision questions** about what you want to accomplish. For each question, mark the box that best describes your needs. At the bottom, you'll find your personalized module list with setup instructions.

**Confidence Levels**:
- 🟢 **High Confidence** — Module is definitely necessary
- 🟡 **Medium Confidence** — Likely needed, but depends on requirements
- 🔴 **Optional** — Nice to have, but not required for core functionality

---

## SECTION 1: YOUR PRIMARY GOAL

### Question 1: What is Your Main Use Case?

Choose the **primary goal** of your YAWL deployment:

- [ ] **A** — Execute business process workflows (cases, tasks, approvals)
- [ ] **B** — Build a real-time AI agent that monitors cases and suggests actions
- [ ] **C** — Generate YAWL specifications programmatically (infrastructure-as-code)
- [ ] **D** — Deploy workflows to cloud with auto-scaling (serverless/containers)
- [ ] **E** — Integrate YAWL with external systems (APIs, databases, message queues)
- [ ] **F** — Schedule workflows to run at specific times or intervals
- [ ] **G** — Manage resource allocation (task assignment, work queue management)

**💡 Help**: You can have multiple goals, but pick the **most important one** here. Secondary goals are addressed in later questions.

---

## SECTION 2: WORKFLOW REQUIREMENTS

### Question 2: Who Will Design the Workflows?

How will your workflows be created?

- [ ] **VISUAL_DESIGNER** — Non-technical business analysts using a web UI
- [ ] **XML_EDITOR** — Technical users writing YAWL XML specifications by hand
- [ ] **CODE_GENERATION** — Workflows generated from code/templates/infrastructure-as-code
- [ ] **MIX** — Some visual, some code-generated

**💡 Help**: "Visual" requires a web-based control panel. "Code" requires code generation tools.

---

### Question 3: Do You Need Custom Business Logic in Tasks?

How will task logic be implemented?

- [ ] **NO** — Pure YAWL workflows with simple data transformations
- [ ] **JAVA** — Custom logic in Java (JAR plugins, external services)
- [ ] **POLYGLOT** — Python, JavaScript, or other languages embedded in workflows
- [ ] **EXTERNAL** — Task handlers run in separate processes (microservices)

**💡 Help**: Most workflows use JAVA or EXTERNAL. Polyglot lets you use Python/JS without custom Java.

---

## SECTION 3: INFRASTRUCTURE & DEPLOYMENT

### Question 4: Where Will You Deploy?

Choose your primary deployment environment:

- [ ] **CLOUD_SERVERLESS** — AWS Lambda, Google Cloud Functions (stateless only)
- [ ] **CLOUD_CONTAINER** — Kubernetes, ECS, Docker Swarm (any deployment)
- [ ] **CLOUD_VM** — EC2, Azure VMs, Compute Engine (traditional server)
- [ ] **ON_PREMISE** — Your data center or private cloud
- [ ] **MARKETPLACE** — Managed YAWL on cloud marketplace (AWS, Azure, GCP)

**💡 Help**: Serverless = only stateless engine. Traditional = can use both. Marketplace = managed, no setup.

---

### Question 5: What Integrations Do You Need?

Which external systems must connect to YAWL?

- [ ] **NONE** — Standalone YAWL, no external integrations
- [ ] **REST_ONLY** — Only HTTP REST API integration
- [ ] **DATABASES** — Store workflow data in custom databases
- [ ] **IDENTITY_PROVIDER** — LDAP, OAuth2, SAML for authentication
- [ ] **MESSAGE_QUEUES** — Kafka, RabbitMQ, Azure Service Bus
- [ ] **EXTERNAL_SERVICES** — Salesforce, SAP, custom legacy systems
- [ ] **MULTIPLE** — 3+ of the above

**💡 Help**: REST is standard. Message queues = async events. External services = adapters/connectors.

---

## SECTION 4: ADVANCED FEATURES

### Question 6: Do You Need These Advanced Features?

Check all that apply to your deployment:

- [ ] **MONITORING** — Real-time dashboards, alerts, SLA tracking
- [ ] **AUDIT_TRAIL** — Compliance-grade audit logs (immutable, timestamped)
- [ ] **RESOURCE_MANAGEMENT** — Task assignment, work queue load balancing
- [ ] **PROCESS_INTELLIGENCE** — Analytics, mining, bottleneck detection
- [ ] **DYNAMIC_WORKFLOWS** — Modify workflows while cases are running
- [ ] **HIGH_AVAILABILITY** — Zero-downtime deployments, failover
- [ ] **ZERO_TRUST_SECURITY** — mTLS, SPIFFE/SPIRE, certificate-based auth
- [ ] **MULTI_REGION** — Deployments across multiple geographic regions

**💡 Help**: Monitoring and HA are common. Audit trail is required for compliance (healthcare, finance). Resource management is needed for human task-heavy workflows.

---

---

## YOUR PERSONALIZED MODULE LIST

**Find your scenario below** based on your answers above. Your list shows:

1. **Core modules** (required)
2. **Feature modules** (highly recommended)
3. **Optional modules** (for advanced use cases)
4. **Total estimated setup time**

---

## 🟢 SCENARIO 1: Simple Workflow Execution (Non-Technical)

**Matches if you answered:**
- Q1: A (execute workflows)
- Q2: Visual designer
- Q3: No custom logic
- Q4: Any deployment
- Q5: None
- Q6: (empty or Monitoring only)

### 📦 Modules to Download

**CORE** (must-have):
1. `yawl-elements` — Workflow specification model
   - **What it does**: Defines what tasks, conditions, and flows are
   - **Setup time**: 10 minutes (just add to Maven)
   - **Learn more**: [yawl-elements Tutorial](../tutorials/yawl-elements-getting-started.md)

2. `yawl-engine` — Petri net execution engine
   - **What it does**: Runs your workflows, manages case state
   - **Setup time**: 15 minutes (configure database)
   - **Learn more**: [yawl-engine Tutorial](../tutorials/yawl-engine-getting-started.md)

3. `yawl-webapps` — Web UI for design and execution
   - **What it does**: Visual workflow designer + control panel
   - **Setup time**: 20 minutes (deploy WAR file)
   - **Learn more**: [Web UI Getting Started](../tutorials/yawl-webapps-getting-started.md)

**FEATURES** (recommended):
- `yawl-monitoring` — Real-time dashboards and metrics
  - 🟡 **Confidence**: Medium (useful for visibility)
  - **Setup time**: 15 minutes
  - **Why**: See what's happening in your workflows

**Total Setup Time**: ~1 hour (including database setup)

### ✅ Next Steps

1. [Download YAWL](../tutorials/01-build-yawl.md)
2. Deploy `yawl-webapps` to Tomcat or Docker
3. [Create your first workflow](../tutorials/03-run-your-first-workflow.md)
4. [Learn YAWL patterns](../explanation/workflow-patterns.md)

---

## 🔵 SCENARIO 2: Workflow Development with Java Integration

**Matches if you answered:**
- Q1: A or E (execute + integrate)
- Q2: XML editor or code generation
- Q3: Java custom logic
- Q4: Cloud container or on-premise
- Q5: REST or Databases

### 📦 Modules to Download

**CORE** (must-have):
1. `yawl-elements` — Specification model
2. `yawl-engine` — Execution engine
3. `yawl-utilities` — Shared utilities (XML, exceptions, logging)
   - **What it does**: Common code used by all modules
   - **Setup time**: 5 minutes (transitive dependency)

**FEATURES** (highly recommended):
1. `yawl-integration` — REST API and service plugins
   - 🟢 **Confidence**: High (needed for REST API)
   - **What it does**: HTTP API, external service integration, custom handlers
   - **Setup time**: 20 minutes
   - **Learn more**: [Integration Tutorial](../tutorials/yawl-integration-getting-started.md)

2. `yawl-data-model` — Type system and data transformation
   - 🟡 **Confidence**: Medium (if you use complex data types)
   - **What it does**: Define workflow data types, validation, transformation
   - **Setup time**: 15 minutes
   - **Learn more**: [Data Modeling Guide](../explanation/data-modelling-sdk-facade.md)

3. `yawl-webapps` — Web UI (if you want visual editor)
   - 🟡 **Confidence**: Medium (optional if using XML editor)

**OPTIONAL**:
- `yawl-benchmark` — Performance testing
  - Test your custom code with JMH benchmarks
  - **Setup time**: 10 minutes

**Total Setup Time**: ~1.5-2 hours

### ✅ Next Steps

1. [Build YAWL with your modules](../tutorials/01-build-yawl.md)
2. [Write your YAWL specification](../tutorials/02-write-your-first-yawl-spec.md)
3. [Call the REST API](../tutorials/05-call-yawl-rest-api.md)
4. [Implement a custom task handler](../tutorials/06-write-a-custom-work-item-handler.md)
5. [Deploy to production](../how-to/deployment/production.md)

---

## 🟣 SCENARIO 3: Polyglot Workflows (Python/JavaScript)

**Matches if you answered:**
- Q1: A or E
- Q2: Code generation or XML
- Q3: Polyglot (Python/JS)
- Q4: Any deployment
- Q5: Any

### 📦 Modules to Download

**CORE**:
1. `yawl-elements`
2. `yawl-engine`
3. `yawl-polyglot` — Multi-language support (Python, JavaScript, WASM)
   - 🟢 **Confidence**: High (required for non-Java languages)
   - **What it does**: Execute Python, JavaScript, or WebAssembly in workflow tasks
   - **Setup time**: 15 minutes
   - **Learn more**: [Polyglot Getting Started](../tutorials/yawl-polyglot-getting-started.md)

**FEATURES**:
1. `yawl-integration` — REST API and plugin system
   - 🟢 **Confidence**: High (standard)

2. `yawl-tera-templates` — Template engine for code generation
   - 🟡 **Confidence**: Medium (if generating workflows)
   - **What it does**: Generate YAWL specs from templates using Tera syntax
   - **Setup time**: 10 minutes
   - **Learn more**: [Tera Templates Guide](../how-to/ggen-tera-templates.md)

**OPTIONAL**:
- `yawl-ggen` — RDF-based code generation (advanced)
  - For infrastructure-as-code workflows
  - **Setup time**: 30 minutes (complex)

**Total Setup Time**: ~1.5 hours

### ✅ Next Steps

1. [Build YAWL](../tutorials/01-build-yawl.md)
2. [Try GraalPy](../polyglot/tutorials/01-graalpy-getting-started.md) or [GraalJS](../polyglot/tutorials/02-graaljs-getting-started.md)
3. [Write a Python task handler](../tutorials/polyglot-ggen-getting-started.md)
4. [Deploy with Polyglot support](../how-to/deployment/polyglot-deployment.md)

---

## 🟠 SCENARIO 4: AI Agent Integration & Real-Time Monitoring

**Matches if you answered:**
- Q1: B (AI agents) or E (integrations)
- Q2: Code generation or XML
- Q3: Polyglot or Java
- Q4: Cloud container (Kubernetes recommended)
- Q5: Multiple integrations
- Q6: Monitoring, Zero-trust security

### 📦 Modules to Download

**CORE**:
1. `yawl-elements`
2. `yawl-engine`
3. `yawl-integration` — REST API and service plugins

**AI & AGENTS**:
1. `yawl-mcp-a2a` — MCP server and A2A integration
   - 🟢 **Confidence**: High (required for AI agents)
   - **What it does**: Model Context Protocol (MCP) support, AI agent coordination
   - **Setup time**: 20 minutes
   - **Learn more**: [MCP/A2A Integration](../explanation/autonomous-agents.md)

**MONITORING & OPERATIONS**:
1. `yawl-monitoring` — Metrics, tracing, dashboards
   - 🟢 **Confidence**: High (critical for production)
   - **What it does**: OpenTelemetry, Prometheus, Grafana integration
   - **Setup time**: 20 minutes
   - **Learn more**: [Monitoring Getting Started](../tutorials/yawl-monitoring-getting-started.md)

2. `yawl-auth` — Authentication and authorization
   - 🟡 **Confidence**: Medium (if using enterprise auth)
   - **What it does**: LDAP, OAuth2, SAML, JWT, certificate auth
   - **Setup time**: 15-30 minutes
   - **Learn more**: [Authentication Guide](../tutorials/yawl-authentication-getting-started.md)

**OPTIONAL**:
- `yawl-polyglot` — If using Python/JS in tasks
- `yawl-security` — X.509 certs, digital signatures, TLS
  - For zero-trust deployments

**Total Setup Time**: ~2-3 hours

### ✅ Next Steps

1. [Build YAWL](../tutorials/01-build-yawl.md)
2. [Set up MCP integration](../explanation/autonomous-agents.md)
3. [Connect an AI agent](../tutorials/integrating-ai-agents.md)
4. [Configure monitoring](../tutorials/yawl-monitoring-getting-started.md)
5. [Deploy to Kubernetes](../how-to/deployment/kubernetes.md)

---

## 🟡 SCENARIO 5: Serverless Cloud Deployment (Auto-Scaling)

**Matches if you answered:**
- Q1: D (cloud deployment)
- Q4: Cloud serverless OR cloud container
- Q5: REST only
- Q6: Monitoring, High availability

### 📦 Modules to Download

**CORE**:
1. `yawl-elements`
2. `yawl-stateless-engine` — Stateless execution (required for serverless)
   - 🟢 **Confidence**: High (mandatory for serverless)
   - **What it does**: Executes cases without persistent state between invocations
   - **Setup time**: 15 minutes
   - **Learn more**: [Stateless Engine Getting Started](../tutorials/yawl-stateless-getting-started.md)

3. `yawl-integration` — REST API

**FEATURES**:
1. `yawl-monitoring` — Metrics and observability
   - 🟢 **Confidence**: High (essential for cloud ops)

2. `yawl-auth` — Cloud identity integration (IAM, OAuth2)
   - 🟡 **Confidence**: Medium (if using cloud auth)

**OPTIONAL**:
- `yawl-polyglot` — For polyglot task logic
- `yawl-security` — If using certificate-based auth

**Total Setup Time**: ~1.5 hours (deployment platform setup adds ~2-3 hours)

### ✅ Next Steps

1. [Choose your cloud platform](../explanation/cloud-marketplace-ggen.md)
2. [Deploy stateless engine](../tutorials/yawl-stateless-getting-started.md)
3. [Set up serverless architecture](../how-to/deployment/serverless-deployment.md)
4. [Configure auto-scaling](../tutorials/11-scale-to-million-cases.md)
5. [Monitor performance](../tutorials/yawl-monitoring-getting-started.md)

---

## 🟢 SCENARIO 6: Enterprise Workflow Management (Human-Centric)

**Matches if you answered:**
- Q1: A or G (workflows + resource management)
- Q2: Visual designer (with optional code generation)
- Q3: Java or external
- Q4: Cloud container or on-premise
- Q5: Multiple integrations
- Q6: Monitoring, Audit trail, Resource management

### 📦 Modules to Download

**CORE**:
1. `yawl-elements`
2. `yawl-engine`
3. `yawl-webapps` — Control panel + designer
   - 🟢 **Confidence**: High

4. `yawl-integration` — REST API and plugins
   - 🟢 **Confidence**: High

**HUMAN WORKFLOWS**:
1. `yawl-resourcing` — Work queue and task assignment
   - 🟢 **Confidence**: High (critical for human tasks)
   - **What it does**: Work item management, resource allocation, load balancing
   - **Setup time**: 25 minutes
   - **Learn more**: [Resource Allocation Guide](../explanation/resource-allocation.md)

2. `yawl-worklet` — Sub-workflow management
   - 🟡 **Confidence**: Medium (if you use reusable sub-workflows)
   - **What it does**: Dynamic workflow fragments, worklet library management
   - **Setup time**: 15 minutes

**OPERATIONS**:
1. `yawl-monitoring` — Dashboards and alerts
   - 🟢 **Confidence**: High

2. `yawl-scheduling` — Timed task execution
   - 🟡 **Confidence**: Medium (if you need scheduled tasks)
   - **What it does**: Cron-like scheduling, time-based triggers
   - **Setup time**: 10 minutes
   - **Learn more**: [Scheduling Guide](../how-to/scheduling.md)

3. `yawl-auth` — Enterprise authentication
   - 🟡 **Confidence**: Medium (almost always needed)
   - LDAP/Active Directory integration

**OPTIONAL**:
- `yawl-pi` — Process Intelligence (analytics, mining)
  - **Setup time**: 20 minutes
- `yawl-security` — Certificate-based auth, digital signatures
- `yawl-data-model` — Complex data types and validation

**Total Setup Time**: ~3-4 hours (first-time setup)

### ✅ Next Steps

1. [Build YAWL](../tutorials/01-build-yawl.md)
2. [Deploy to production](../how-to/deployment/production.md)
3. [Set up resource management](../tutorials/yawl-resourcing-getting-started.md)
4. [Configure LDAP authentication](../tutorials/yawl-authentication-getting-started.md)
5. [Set up monitoring & dashboards](../tutorials/yawl-monitoring-getting-started.md)
6. [Enable audit trail for compliance](../how-to/audit-trail-setup.md)

---

## 🟠 SCENARIO 7: Infrastructure-as-Code (Code-Generated Workflows)

**Matches if you answered:**
- Q1: C (code generation)
- Q2: Code generation
- Q3: Polyglot or Java
- Q4: Any deployment
- Q5: Databases, external services

### 📦 Modules to Download

**CORE**:
1. `yawl-elements`
2. `yawl-engine`
3. `yawl-integration` — REST API

**CODE GENERATION**:
1. `yawl-ggen` — RDF and SPARQL-based code generation
   - 🟢 **Confidence**: High (purpose-built for IaC)
   - **What it does**: Generate YAWL specs from RDF graphs using SPARQL queries
   - **Setup time**: 30 minutes
   - **Learn more**: [ggen Code Generation](../tutorials/polyglot-ggen-getting-started.md)

2. `yawl-tera-templates` — Template engine
   - 🟡 **Confidence**: Medium (alternative to ggen for simple cases)
   - Simpler syntax, less powerful than ggen

**POLYGLOT** (if needed):
- `yawl-polyglot` — Execute generated code in multiple languages

**Total Setup Time**: ~2 hours

### ✅ Next Steps

1. [Learn ggen architecture](../explanation/cloud-marketplace-ggen.md)
2. [Write your first ggen spec](../tutorials/polyglot-ggen-getting-started.md)
3. [Deploy generated workflows](../how-to/deployment/production.md)
4. [Automate generation in CI/CD](../how-to/cicd-integration.md)

---

## 🔵 SCENARIO 8: Large-Scale Enterprise (Everything)

**Matches if you answered:**
- Q1: Multiple goals (A + E + G)
- Q2: Mix of visual and code
- Q3: Java + Polyglot + External
- Q4: Kubernetes or multi-region
- Q5: Multiple integrations
- Q6: All advanced features

### 📦 Modules to Download

**All core modules:**
- `yawl-elements`
- `yawl-engine`
- `yawl-stateless-engine` (for auto-scaling)
- `yawl-integration`
- `yawl-webapps`

**All feature modules:**
- `yawl-resourcing` — Work queue
- `yawl-scheduling` — Timed tasks
- `yawl-monitoring` — Observability
- `yawl-auth` — Enterprise auth
- `yawl-security` — Zero-trust
- `yawl-mcp-a2a` — AI agents
- `yawl-polyglot` — Multi-language support
- `yawl-ggen` — Code generation
- `yawl-tera-templates` — Template engine
- `yawl-data-model` — Complex types
- `yawl-pi` — Process intelligence

**Optional**:
- `yawl-worklet` — Sub-workflows
- `yawl-benchmark` — Performance testing

**Total Setup Time**: ~5-7 hours (but typically phased over weeks)

### ✅ Implementation Strategy

**Phase 1 (Week 1)**: Core execution
- Deploy yawl-engine + yawl-webapps + yawl-integration

**Phase 2 (Week 2)**: Human workflows
- Add yawl-resourcing + yawl-auth + yawl-scheduling

**Phase 3 (Week 3)**: Operations
- Add yawl-monitoring + yawl-security + yawl-mcp-a2a

**Phase 4 (Week 4+)**: Advanced
- Add yawl-pi + yawl-ggen + yawl-polyglot as needed

See: [Learning Roadmap](./LEARNING_ROADMAP.md)

---

## ⚠️ Common Mistakes to Avoid

### "I'll just download all modules"
❌ **Problem**: 22 modules with complex dependencies. Hard to learn. Hard to maintain.
✅ **Solution**: Start with your scenario above. Add modules only when needed.

### "I don't need monitoring"
❌ **Problem**: Can't see what's happening in production. Hard to debug.
✅ **Solution**: Add `yawl-monitoring` from day 1. Takes 20 minutes.

### "I'll use stateful engine for serverless"
❌ **Problem**: Stateful engine requires persistent database between invocations. Fails in serverless.
✅ **Solution**: Use `yawl-stateless-engine` for AWS Lambda, Google Cloud Functions, etc.

### "I'll add polyglot support later"
❌ **Problem**: Changing from Java-only to polyglot means refactoring task handlers.
✅ **Solution**: Decide up front. Use `yawl-polyglot` if you might use Python/JS.

---

## 🎯 Quick Module Dependency Reference

```
yawl-webapps
  └── yawl-integration
      └── yawl-engine
          ├── yawl-elements
          └── yawl-utilities

yawl-stateless-engine
  └── yawl-engine
      └── [same as above]

yawl-resourcing
  └── yawl-engine
      └── [same as above]

yawl-monitoring
  └── yawl-engine
      └── [same as above]

yawl-mcp-a2a
  └── yawl-integration
      └── [same as above]

yawl-ggen
  └── [standalone, no YAWL dependencies]

yawl-polyglot
  └── yawl-engine
      └── [same as above]
```

**Key insight**: All modules ultimately depend on `yawl-engine` + `yawl-elements`. Start there.

---

## 📚 Learn More

- **[Module Dependency Map](./MODULE_DEPENDENCY_MAP.md)** — Full reference of all 22 modules
- **[Learning Roadmap](./LEARNING_ROADMAP.md)** — Recommended learning paths
- **[FAQ](./FAQ_AND_COMMON_ISSUES.md)** — Common questions answered
- **[Deployment Calculator](./DEPLOYMENT_CALCULATOR.md)** — Choose your deployment architecture

---

## 🆘 Still Confused?

1. **Find your scenario** above (A-H) that best matches your needs
2. **Download those modules** — you have a specific list
3. **Follow the next steps** — each scenario has a checklist
4. **Jump to the tutorials** — links are provided for each module

**Still stuck?** Check [FAQ & Common Issues](./FAQ_AND_COMMON_ISSUES.md) or ask in the community.
