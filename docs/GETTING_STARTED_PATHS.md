# YAWL Documentation — Choose Your Path

Welcome to YAWL! This guide helps you find the right documentation for your role and experience level.

---

## 👤 Choose Your Role

### 👨‍💼 **Business Analyst / Process Owner**
*I want to understand and design workflows*

**Start here:**
1. [Quick Start (Users)](./diataxis/INDEX.md?user=business) - 10 min overview
2. [Write a YAWL Specification](./tutorials/04-write-a-yawl-specification.md) - Design a workflow
3. [How-To: Model a Process](./how-to/workflow-modelling.md) - Step-by-step design
4. [Workflow Patterns Reference](./reference/workflow-patterns.md) - Learn best practices

**Quick wins:**
- Run [Run Your First Workflow](./tutorials/03-run-your-first-workflow.md) (20 min)
- Check [Process Intelligence](./diataxis/INDEX.md?section=PI) for AI-powered insights

---

### 👨‍💻 **Developers / Integrators**
*I want to build, integrate, and extend YAWL*

**Start here:**
1. [Build YAWL](./tutorials/01-build-yawl.md) - Get the source running
2. [Understand the Build](./tutorials/02-understand-the-build.md) - Maven structure
3. [Call the YAWL REST API](./tutorials/05-call-yawl-rest-api.md) - API basics
4. [Write a Custom Work Item Handler](./tutorials/06-write-a-custom-work-item-handler.md) - Extend the engine

**Next level:**
- [MCP Agent Integration](./tutorials/08-mcp-agent-integration.md) - Connect AI agents
- [Core Engine Architecture](./explanation/yawl-engine-architecture.md) - Deep dive

**For polyglot devs:**
- [GraalPy Getting Started](./polyglot/tutorials/01-graalpy-getting-started.md) - Python workflows
- [GraalJS Getting Started](./polyglot/tutorials/02-graaljs-getting-started.md) - JavaScript workflows

---

### 🚀 **DevOps / Platform Engineers**
*I want to deploy, monitor, and scale YAWL*

**Start here:**
1. [Docker Dev Environment](./tutorials/07-docker-dev-environment.md) - Container setup
2. [YAWL Stateless Engine Getting Started](./tutorials/yawl-stateless-getting-started.md) - Event-driven deployment
3. [How-To: Deploy to Production](./how-to/deployment/stateless-deployment.md) - Production setup
4. [Monitoring & Observability](./tutorials/yawl-monitoring-getting-started.md) - Metrics & tracing

**Operations:**
- [Scheduling](./tutorials/yawl-scheduling-getting-started.md) - Case scheduling
- [Authentication Setup](./how-to/yawl-authentication-setup.md) - JWT, CSRF, security
- [Scale to 1M Cases](./tutorials/11-scale-to-million-cases.md) - High-volume deployment

**For cloud platforms:**
- [Marketplace Quick Start](./tutorials/09-marketplace-quick-start.md) - Cloud deployment
- [CI/CD Build Setup](./how-to/cicd/build.md) - Pipeline configuration

---

### 📊 **Data Scientists / ML Engineers**
*I want to use AI to optimize workflows*

**Start here:**
1. [First Case Prediction](./pi/tutorials/01-first-case-prediction.md) - ML basics
2. [Train AutoML Model](./pi/tutorials/02-train-automl-model.md) - TPOT2 integration
3. [Realtime Adaptive Workflows](./pi/tutorials/03-realtime-adaptive.md) - Deploy predictions
4. [Natural Language QA](./pi/tutorials/04-natural-language-qa.md) - Query workflows in plain English

**Deep dives:**
- [Process Intelligence Architecture](./explanation/yawl-pi-architecture.md) - System design
- [Prescriptive Engine](./reference/pi-api.md#prescriptive) - Recommended actions

---

### 🔒 **Security / Compliance Officers**
*I want to ensure YAWL meets security and compliance requirements*

**Start here:**
1. [YAWL Security Getting Started](./tutorials/yawl-security-getting-started.md) - X.509, digital signatures
2. [Authentication Setup](./how-to/yawl-authentication-setup.md) - JWT, CSRF
3. [Security Framework](./explanation/yawl-security-framework.md) - Design principles
4. [ADR-005: SPIFFE/SPIRE Zero-Trust](./explanation/decisions/ADR-005-spiffe-spire-zero-trust.md) - Identity architecture

**Compliance:**
- [Encryption & TLS](./reference/yawl-security-config.md) - Certificate management
- [Audit Logging](./explanation/yawl-monitoring-architecture.md#audit) - Track user actions

---

## 🎯 Find by Task

### I want to...

| Task | Start Here |
|------|-----------|
| **Deploy YAWL locally** | [Build YAWL](./tutorials/01-build-yawl.md) → [Run Your First Workflow](./tutorials/03-run-your-first-workflow.md) |
| **Build a production cluster** | [Stateless Engine](./tutorials/yawl-stateless-getting-started.md) → [Scale to 1M Cases](./tutorials/11-scale-to-million-cases.md) |
| **Create a workflow** | [Write a Specification](./tutorials/04-write-a-yawl-specification.md) → [How-To Guides](./diataxis/INDEX.md#how-to-guides) |
| **Connect to an external system** | [REST API](./tutorials/05-call-yawl-rest-api.md) → [Integration Guide](./how-to/integration-patterns.md) |
| **Add AI to my workflows** | [AI Agent Integration](./tutorials/08-mcp-agent-integration.md) or [Case Prediction](./pi/tutorials/01-first-case-prediction.md) |
| **Monitor running cases** | [Monitoring Setup](./tutorials/yawl-monitoring-getting-started.md) → [Observability](./explanation/yawl-monitoring-architecture.md) |
| **Extend the engine** | [Custom Handler](./tutorials/06-write-a-custom-work-item-handler.md) → [Engine Architecture](./explanation/yawl-engine-architecture.md) |
| **Use Python/JavaScript** | [GraalPy](./polyglot/tutorials/01-graalpy-getting-started.md) or [GraalJS](./polyglot/tutorials/02-graaljs-getting-started.md) |
| **Understand the architecture** | [Core Concepts](./diataxis/INDEX.md#explanation) or [YAWL Engine Architecture](./explanation/yawl-engine-architecture.md) |
| **Look up an API** | [API Reference](./diataxis/INDEX.md#api-documentation) or [Interfaces](./reference/yawl-engine-api.md) |
| **Fix a common problem** | [FAQ & Troubleshooting](#faq--troubleshooting) below |
| **Optimize performance** | [Benchmark Guide](./tutorials/yawl-benchmark-getting-started.md) → [Performance Tuning](./how-to/performance-optimization.md) |

---

## 📚 The Four Quadrants

YAWL documentation uses the **Diataxis framework**. Each document fits exactly one quadrant:

| Quadrant | Purpose | Example |
|----------|---------|---------|
| **📖 Tutorials** | Learn by doing | [Build YAWL](./tutorials/01-build-yawl.md) |
| **🎯 How-To Guides** | Accomplish a task | [Deploy to Production](./how-to/deployment/stateless-deployment.md) |
| **📋 Reference** | Look up facts | [YEngine API](./reference/yawl-engine-api.md) |
| **💡 Explanation** | Understand concepts | [Engine Architecture](./explanation/yawl-engine-architecture.md) |

👉 **[Full Index](./diataxis/INDEX.md)** — Browse all documentation by quadrant

---

## ⚡ FAQ & Troubleshooting

### Getting Started

**Q: What's the fastest way to try YAWL?**
A: [Quick Start (Users)](./tutorials/quick-start-users.md) (10 min) or [Build YAWL](./tutorials/01-build-yawl.md) + [Run Your First Workflow](./tutorials/03-run-your-first-workflow.md) (30 min)

**Q: Do I need to compile YAWL myself?**
A: For development, yes. See [Build YAWL](./tutorials/01-build-yawl.md). For production, use pre-built containers in [Marketplace](./tutorials/09-marketplace-quick-start.md) or [Stateless Engine](./tutorials/yawl-stateless-getting-started.md).

**Q: What Java version do I need?**
A: Java 25 recommended. See [ADR-004: Spring Boot 3.4 & Java 25](./explanation/decisions/ADR-004-spring-boot-34-java-25.md).

---

### Development

**Q: How do I call the YAWL engine?**
A: Via REST API. See [Call the YAWL REST API](./tutorials/05-call-yawl-rest-api.md) and [YEngine API Reference](./reference/yawl-engine-api.md).

**Q: How do I extend YAWL with custom logic?**
A: Write a Work Item Handler. See [Custom Work Item Handler](./tutorials/06-write-a-custom-work-item-handler.md).

**Q: Can I use Python or JavaScript in workflows?**
A: Yes! See [GraalPy](./polyglot/tutorials/01-graalpy-getting-started.md) or [GraalJS](./polyglot/tutorials/02-graaljs-getting-started.md).

---

### Deployment

**Q: What's the difference between stateful and stateless?**
A: See [Stateless vs Persistent Architecture](./explanation/stateless-vs-persistent-architecture.md).

**Q: How do I scale to millions of cases?**
A: [Scale to 1M Cases](./tutorials/11-scale-to-million-cases.md) guide covers clustering, sharding, and optimization.

**Q: How do I monitor running workflows?**
A: [Monitoring Getting Started](./tutorials/yawl-monitoring-getting-started.md) + [OpenTelemetry Integration](./explanation/yawl-monitoring-architecture.md).

---

### Security

**Q: How do I set up authentication?**
A: [Authentication Getting Started](./tutorials/yawl-authentication-getting-started.md) covers JWT, CSRF, and certificates.

**Q: How do I ensure zero-trust security?**
A: See [ADR-005: SPIFFE/SPIRE](./explanation/decisions/ADR-005-spiffe-spire-zero-trust.md).

---

### AI & Advanced Features

**Q: How do I add AI predictions to workflows?**
A: [Case Prediction](./pi/tutorials/01-first-case-prediction.md) or [AutoML](./pi/tutorials/02-train-automl-model.md).

**Q: Can I connect an AI agent to YAWL?**
A: Yes! [MCP Agent Integration](./tutorials/08-mcp-agent-integration.md).

---

## 🔍 Browse All Docs

- **[Full Diataxis Index](./diataxis/INDEX.md)** — All 350+ docs organized by quadrant
- **[README](./README.md)** — Documentation overview
- **[CLAUDE.md](../CLAUDE.md)** — YAWL development principles & standards

---

## 🤝 Contributing to YAWL Docs

See [Documentation Contribution Guide](./how-to/contributing-documentation.md) for how to add or improve documentation.

---

*Last updated: 2026-02-28*
*YAWL v6.0.0 — Comprehensive Diataxis documentation for all 22 modules*
