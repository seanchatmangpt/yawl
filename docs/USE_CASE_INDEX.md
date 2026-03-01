# YAWL Use Case Index — Learning Paths by Goal

**Version**: 6.0.0 | **Updated**: 2026-02-28 | **Purpose**: Find docs for your specific use case | **Format**: Goal → Learning Path

---

## How to Use This Index

1. **Find your use case** below (e.g., "Deploy a workflow engine", "Build a real-time dashboard")
2. **Follow the recommended learning path** (step-by-step docs)
3. **Estimated time** shows how long the path takes
4. **Difficulty** shows: 🟢 Beginner | 🟡 Intermediate | 🔴 Advanced

---

## Use Cases A-Z

### 1. Add Real-Time Case Monitoring to an Existing Application
**Goal**: Monitor workflow cases in real-time, show status updates on a dashboard
**Difficulty**: 🟡 Intermediate | **Estimated Time**: 4-6 hours | **Best For**: Operators, DevOps

**Prerequisites**: Running YAWL engine, basic REST API knowledge

**Learning Path**:
1. [05-call-yawl-rest-api.md](tutorials/05-call-yawl-rest-api.md) — Learn REST API (1h)
2. [how-to/subscribe-workflow-events.md](how-to/subscribe-workflow-events.md) — Event subscription (1h)
3. [yawl-mcp-a2a-getting-started.md](tutorials/yawl-mcp-a2a-getting-started.md) — MCP agent monitoring (1h)
4. [how-to/yawl-monitoring-tracing.md](how-to/yawl-monitoring-tracing.md) — Real-time tracing (1.5h)
5. [reference/api-reference.md](reference/api-reference.md) — API details for dashboard (1h)

**Key Modules**: yawl-integration, yawl-monitoring, yawl-mcp-a2a

**Next Step**: Build custom dashboard using REST API

---

### 2. Add Python Task Handlers to Workflows
**Goal**: Execute Python code as workflow tasks (data processing, ML inference, etc.)
**Difficulty**: 🟡 Intermediate | **Estimated Time**: 3-4 hours | **Best For**: Data Scientists, Python developers

**Prerequisites**: YAWL running, Python knowledge

**Learning Path**:
1. [04-write-a-yawl-specification.md](tutorials/04-write-a-yawl-specification.md) — Workflow design (1h)
2. [polyglot/tutorials/01-graalpy-getting-started.md](polyglot/tutorials/01-graalpy-getting-started.md) — GraalPy intro (1h)
3. [06-write-a-custom-work-item-handler.md](tutorials/06-write-a-custom-work-item-handler.md) — Custom handlers (1h)
4. [polyglot/how-to/](polyglot/how-to/) — Python integration patterns (1h)

**Key Modules**: yawl-polyglot, yawl-graalpy, yawl-engine

**Next Step**: Build ML model handler for case outcome prediction

---

### 3. Apply Machine Learning to Predict Case Outcomes
**Goal**: Train ML models to predict workflow outcomes and adapt execution
**Difficulty**: 🔴 Advanced | **Estimated Time**: 8-12 hours | **Best For**: Data Scientists, ML engineers

**Prerequisites**: Python/ML experience, YAWL basics, process understanding

**Learning Path**:
1. [explanation/object-centric-process-mining.md](explanation/object-centric-process-mining.md) — Process mining concepts (1h)
2. [pi/tutorials/01-first-case-prediction.md](pi/tutorials/01-first-case-prediction.md) — First prediction model (2h)
3. [pi/tutorials/02-train-automl-model.md](pi/tutorials/02-train-automl-model.md) — AutoML training (2.5h)
4. [pi/tutorials/03-realtime-adaptive.md](pi/tutorials/03-realtime-adaptive.md) — Real-time decisions (2h)
5. [pi/reference/](pi/reference/) — ML API details (1.5h)

**Key Modules**: yawl-pi, yawl-engine

**Next Step**: Deploy model to production with monitoring

---

### 4. Authenticate Users with JWT & OAuth2
**Goal**: Secure YAWL with modern authentication (JWT tokens, OAuth2 flows)
**Difficulty**: 🟡 Intermediate | **Estimated Time**: 3-4 hours | **Best For**: Security engineers, DevOps

**Prerequisites**: Security fundamentals, token concepts

**Learning Path**:
1. [yawl-security-getting-started.md](tutorials/yawl-security-getting-started.md) — Security intro (1h)
2. [yawl-authentication-getting-started.md](tutorials/yawl-authentication-getting-started.md) — Auth intro (1h)
3. [how-to/yawl-authentication-setup.md](how-to/yawl-authentication-setup.md) — JWT config (1h)
4. [reference/authentication/](reference/authentication/) — Auth APIs (1h)

**Key Modules**: yawl-security, yawl-authentication

**Next Step**: Integrate with company identity provider (Active Directory, Okta)

---

### 5. Build a Workflow Specification from Scratch
**Goal**: Design & implement a complete YAWL workflow for a business process
**Difficulty**: 🟡 Intermediate | **Estimated Time**: 6-8 hours | **Best For**: Process designers, architects

**Prerequisites**: Process modeling knowledge, basic XML

**Learning Path**:
1. [04-write-a-yawl-specification.md](tutorials/04-write-a-yawl-specification.md) — Spec design (2h)
2. [how-to/yawl-elements-schema-design.md](how-to/yawl-elements-schema-design.md) — Schema design (1.5h)
3. [reference/workflow-patterns.md](reference/workflow-patterns.md) — Pattern library (1h)
4. [reference/patterns/](reference/patterns/) — Pattern examples (1.5h)
5. [how-to/validate-spec.md](how-to/validate-spec.md) — Validation (1h)

**Key Modules**: yawl-elements, yawl-utilities

**Next Step**: Deploy spec & run your first case

---

### 6. Build Real-Time Dashboard for Case Monitoring
**Goal**: Create a web dashboard showing live workflow execution with case updates
**Difficulty**: 🟡 Intermediate | **Estimated Time**: 8-10 hours | **Best For**: Frontend developers, DevOps

**Prerequisites**: Web development, REST API, JavaScript

**Learning Path**:
1. [05-call-yawl-rest-api.md](tutorials/05-call-yawl-rest-api.md) — REST API basics (1h)
2. [reference/api-reference.md](reference/api-reference.md) — Complete API spec (1.5h)
3. [reference/api/](reference/api/) — API implementations (SDKs) (1h)
4. [how-to/subscribe-workflow-events.md](how-to/subscribe-workflow-events.md) — WebSocket events (1h)
5. [yawl-monitoring-getting-started.md](tutorials/yawl-monitoring-getting-started.md) — Real-time metrics (1.5h)
6. [how-to/yawl-monitoring-tracing.md](how-to/yawl-monitoring-tracing.md) — Tracing (1.5h)

**Key Modules**: yawl-integration, yawl-monitoring, yawl-engine

**Next Step**: Add alerts & notifications

---

### 7. Clone & Build YAWL from Source
**Goal**: Get the YAWL codebase, build it locally, understand the module structure
**Difficulty**: 🟢 Beginner | **Estimated Time**: 1-2 hours | **Best For**: Developers, contributors

**Prerequisites**: Git, Maven, Java 25

**Learning Path**:
1. [01-build-yawl.md](tutorials/01-build-yawl.md) — Clone & build (45 min)
2. [02-understand-the-build.md](tutorials/02-understand-the-build.md) — Build structure (45 min)
3. [how-to/developer-guide.md](how-to/developer-guide.md) — Dev environment (30 min)

**Key Modules**: Build system

**Next Step**: Explore modules, write your first commit

---

### 8. Configure Multi-Tenant Deployment
**Goal**: Set up YAWL to isolate workflows & data for multiple organizations
**Difficulty**: 🔴 Advanced | **Estimated Time**: 8-12 hours | **Best For**: DevOps, architects

**Prerequisites**: YAWL basics, deployment experience, database knowledge

**Learning Path**:
1. [how-to/deployment/overview.md](how-to/deployment/overview.md) — Deployment options (1h)
2. [how-to/configure-multi-tenancy.md](how-to/configure-multi-tenancy.md) — Multi-tenancy setup (2h)
3. [how-to/configure-resource-service.md](how-to/configure-resource-service.md) — Resource isolation (1.5h)
4. [how-to/setup-org-model.md](how-to/setup-org-model.md) — Org per tenant (1.5h)
5. [how-to/deployment/production.md](how-to/deployment/production.md) — Production hardening (2h)
6. [v6/DEFINITION-OF-DONE.md](v6/DEFINITION-OF-DONE.md) — Quality gates (1.5h)

**Key Modules**: yawl-resourcing, yawl-webapps, yawl-integration

**Next Step**: Deploy to production with monitoring

---

### 9. Connect an AI Agent to Monitor & Automate Workflows
**Goal**: Integrate autonomous AI agents for real-time case monitoring & automation
**Difficulty**: 🟡 Intermediate | **Estimated Time**: 5-7 hours | **Best For**: AI/ML engineers, automation architects

**Prerequisites**: AI/agent concepts, MCP understanding, LLM experience

**Learning Path**:
1. [08-mcp-agent-integration.md](tutorials/08-mcp-agent-integration.md) — MCP agent intro (1.5h)
2. [yawl-mcp-a2a-getting-started.md](tutorials/yawl-mcp-a2a-getting-started.md) — MCP/A2A setup (1.5h)
3. [explanation/autonomous-agents.md](explanation/autonomous-agents.md) — Agent architecture (1h)
4. [reference/mcp-tools.md](reference/mcp-tools.md) — Tool reference (1h)
5. [explanation/mcp-llm-design.md](explanation/mcp-llm-design.md) — LLM design (1h)

**Key Modules**: yawl-mcp-a2a, yawl-integration, yawl-engine

**Next Step**: Build custom agent tools for your domain

---

### 10. Deploy YAWL on Kubernetes
**Goal**: Deploy YAWL engine to production Kubernetes cluster
**Difficulty**: 🟡 Intermediate | **Estimated Time**: 4-6 hours | **Best For**: DevOps, cloud engineers

**Prerequisites**: Kubernetes basics, Docker, YAWL knowledge

**Learning Path**:
1. [how-to/deployment/overview.md](how-to/deployment/overview.md) — Deployment strategy (1h)
2. [07-docker-dev-environment.md](tutorials/07-docker-dev-environment.md) — Docker intro (1h)
3. [how-to/deployment/docker.md](how-to/deployment/docker.md) — Docker deployment (1h)
4. [how-to/deployment/docker-full.md](how-to/deployment/docker-full.md) — Full setup (1h)
5. [how-to/deployment/production.md](how-to/deployment/production.md) — Production checklist (1.5h)

**Key Modules**: yawl-webapps, deployment

**Next Step**: Set up monitoring & autoscaling

---

### 11. Develop a Custom Work Item Handler
**Goal**: Write Java code to handle custom task execution logic
**Difficulty**: 🟡 Intermediate | **Estimated Time**: 3-4 hours | **Best For**: Java developers

**Prerequisites**: Java knowledge, YAWL basics

**Learning Path**:
1. [yawl-engine-getting-started.md](tutorials/yawl-engine-getting-started.md) — Engine intro (1h)
2. [06-write-a-custom-work-item-handler.md](tutorials/06-write-a-custom-work-item-handler.md) — Custom handler (1h)
3. [reference/interfaces.md](reference/interfaces.md) — Interface spec (1h)
4. [how-to/yawl-engine-case-execution.md](how-to/yawl-engine-case-execution.md) — Case execution (1h)

**Key Modules**: yawl-engine, yawl-elements

**Next Step**: Integrate external system (database, API, service)

---

### 12. Enable Real-Time Adaptive Workflows with Predictions
**Goal**: Use ML predictions to dynamically change workflow path at runtime
**Difficulty**: 🔴 Advanced | **Estimated Time**: 10-14 hours | **Best For**: Data scientists, architects

**Prerequisites**: ML knowledge, YAWL basics, Python

**Learning Path**:
1. [pi/tutorials/01-first-case-prediction.md](pi/tutorials/01-first-case-prediction.md) — Prediction intro (2h)
2. [pi/tutorials/02-train-automl-model.md](pi/tutorials/02-train-automl-model.md) — AutoML training (3h)
3. [pi/tutorials/03-realtime-adaptive.md](pi/tutorials/03-realtime-adaptive.md) — Real-time adaptation (2.5h)
4. [04-write-a-yawl-specification.md](tutorials/04-write-a-yawl-specification.md) — Workflow with decisions (1.5h)
5. [06-write-a-custom-work-item-handler.md](tutorials/06-write-a-custom-work-item-handler.md) — Adaptive handler (1.5h)
6. [pi/reference/](pi/reference/) — ML API reference (1.5h)

**Key Modules**: yawl-pi, yawl-engine, yawl-polyglot

**Next Step**: Production deployment with model monitoring

---

### 13. Implement Runtime Workflow Adaptation with Worklets
**Goal**: Use Ripple Down Rules to dynamically select workflows at runtime
**Difficulty**: 🟡 Intermediate | **Estimated Time**: 4-6 hours | **Best For**: Process designers, automation engineers

**Prerequisites**: YAWL basics, RDR concept understanding

**Learning Path**:
1. [04-write-a-yawl-specification.md](tutorials/04-write-a-yawl-specification.md) — Spec design (1h)
2. [yawl-worklet-getting-started.md](tutorials/yawl-worklet-getting-started.md) — Worklet intro (1h)
3. [how-to/implement-worklet-service.md](how-to/implement-worklet-service.md) — Worklet implementation (2h)
4. [reference/patterns/](reference/patterns/) — Adaptation patterns (1.5h)

**Key Modules**: yawl-worklet, yawl-engine, yawl-elements

**Next Step**: Integrate with performance monitoring to auto-optimize

---

### 14. Migrate from YAWL v5 to v6
**Goal**: Upgrade existing v5 workflows to v6 (Jakarta EE, Java 25 features)
**Difficulty**: 🟡 Intermediate | **Estimated Time**: 6-12 hours | **Best For**: Architects, DevOps

**Prerequisites**: v5 YAWL knowledge, current deployment

**Learning Path**:
1. [how-to/migration/](how-to/migration/) — Migration guides (1.5h)
2. [architecture/Java25-Modernization-Architecture.md](architecture/Java25-Modernization-Architecture.md) — What changed (1.5h)
3. [how-to/java25-setup.md](how-to/java25-setup.md) — Java 25 setup (1h)
4. [02-understand-the-build.md](tutorials/02-understand-the-build.md) — New build structure (1h)
5. [how-to/testing.md](how-to/testing.md) — Updated testing (1h)
6. [how-to/deployment/](how-to/deployment/) — New deployment (1.5h)

**Key Modules**: All (cross-cutting)

**Next Step**: Run regression tests, validate workflows

---

### 15. Optimize Workflow Performance for 1M Cases
**Goal**: Scale YAWL to execute 1 million cases efficiently
**Difficulty**: 🔴 Advanced | **Estimated Time**: 12-16 hours | **Best For**: Performance engineers, architects

**Prerequisites**: YAWL production experience, database tuning knowledge

**Learning Path**:
1. [11-scale-to-million-cases.md](tutorials/11-scale-to-million-cases.md) — Scaling guide (2h)
2. [PERFORMANCE.md](PERFORMANCE.md) — Performance baselines (1h)
3. [yawl-benchmark-getting-started.md](tutorials/yawl-benchmark-getting-started.md) — Benchmarking intro (1.5h)
4. [how-to/yawl-benchmark-performance-optimization.md](how-to/yawl-benchmark-performance-optimization.md) — Tuning (2.5h)
5. [architecture/Java25-Modernization-Architecture.md](architecture/Java25-Modernization-Architecture.md) — Java 25 optimizations (1.5h)
6. [how-to/enable-stateless-persistence.md](how-to/enable-stateless-persistence.md) — Stateless scaling (1.5h)
7. [v6/performance/](v6/performance/) — Performance analysis (1.5h)

**Key Modules**: yawl-engine, yawl-benchmark, yawl-stateless

**Next Step**: Monitor production & apply continuous optimization

---

### 16. Run Your First YAWL Workflow
**Goal**: Get YAWL running, deploy a simple spec, execute a case end-to-end
**Difficulty**: 🟢 Beginner | **Estimated Time**: 1.5-2 hours | **Best For**: Newcomers, demos

**Prerequisites**: Java knowledge (basic), Git, Maven

**Learning Path**:
1. [QUICK-START.md](QUICK-START.md) — 5-minute overview (15 min)
2. [01-build-yawl.md](tutorials/01-build-yawl.md) — Build locally (30 min)
3. [03-run-your-first-workflow.md](tutorials/03-run-your-first-workflow.md) — Run example (45 min)

**Key Modules**: yawl-engine, yawl-webapps

**Next Step**: Build your own specification

---

### 17. Set Up Distributed Tracing & Observability
**Goal**: Instrument YAWL with OpenTelemetry for distributed tracing & metrics
**Difficulty**: 🟡 Intermediate | **Estimated Time**: 4-6 hours | **Best For**: DevOps, SRE

**Prerequisites**: OpenTelemetry basics, observability concepts

**Learning Path**:
1. [yawl-monitoring-getting-started.md](tutorials/yawl-monitoring-getting-started.md) — Monitoring intro (1h)
2. [how-to/yawl-monitoring-tracing.md](how-to/yawl-monitoring-tracing.md) — Tracing setup (1.5h)
3. [reference/slos/](reference/slos/) — SLO definitions (1h)
4. [how-to/deployment/production.md](how-to/deployment/production.md) — Production observability (1.5h)

**Key Modules**: yawl-monitoring, yawl-integration

**Next Step**: Deploy Jaeger, set up alerts

---

### 18. Set Up Zero-Trust Identity with SPIFFE
**Goal**: Secure inter-service communication with SPIFFE/SVID identities
**Difficulty**: 🔴 Advanced | **Estimated Time**: 6-8 hours | **Best For**: Security engineers, DevOps

**Prerequisites**: Zero-trust concepts, PKI knowledge, mTLS

**Learning Path**:
1. [yawl-security-getting-started.md](tutorials/yawl-security-getting-started.md) — Security intro (1h)
2. [how-to/configure-spiffe.md](how-to/configure-spiffe.md) — SPIFFE setup (2h)
3. [reference/crypto-and-tls.md](reference/crypto-and-tls.md) — TLS details (1.5h)
4. [how-to/yawl-security-certificate-management.md](how-to/yawl-security-certificate-management.md) — Cert management (2h)

**Key Modules**: yawl-security

**Next Step**: Integrate with Kubernetes workload identity

---

### 19. Understand YAWL Architecture & Design Decisions
**Goal**: Deep dive into YAWL architecture, design patterns, and trade-offs
**Difficulty**: 🔴 Advanced | **Estimated Time**: 8-12 hours | **Best For**: Architects, lead engineers

**Prerequisites**: YAWL user experience, system design knowledge

**Learning Path**:
1. [explanation/dual-engine-architecture.md](explanation/dual-engine-architecture.md) — Dual engine design (1h)
2. [explanation/interface-architecture.md](explanation/interface-architecture.md) — Interface design (1.5h)
3. [explanation/decisions/](explanation/decisions/) — 30+ ADRs (3h)
4. [v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md](v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md) — Full analysis (2.5h)
5. [explanation/petri-net-foundations.md](explanation/petri-net-foundations.md) — Petri net theory (1.5h)
6. [explanation/shared-src-build-strategy.md](explanation/shared-src-build-strategy.md) — Build strategy (1h)

**Key Modules**: All (conceptual)

**Next Step**: Propose architectural improvements

---

### 20. Write End-to-End Tests for Workflows
**Goal**: Set up comprehensive testing for YAWL workflows & custom handlers
**Difficulty**: 🟡 Intermediate | **Estimated Time**: 4-6 hours | **Best For**: QA engineers, developers

**Prerequisites**: JUnit knowledge, testing concepts

**Learning Path**:
1. [how-to/testing.md](how-to/testing.md) — Testing strategy (1.5h)
2. [reference/chicago-tdd.md](reference/chicago-tdd.md) — TDD principles (1h)
3. [v6/testing/](v6/testing/) — Test framework docs (1.5h)
4. [how-to/validate-spec.md](how-to/validate-spec.md) — Spec validation (1h)

**Key Modules**: Testing framework, yawl-engine

**Next Step**: Add performance tests, mutation testing

---

## Learning Path Summary

| Use Case | Difficulty | Time | Best For |
|----------|-----------|------|----------|
| Clone & build YAWL | 🟢 | 1-2h | Developers |
| Run first workflow | 🟢 | 1.5-2h | Newcomers |
| Build spec | 🟡 | 6-8h | Process designers |
| Custom handler | 🟡 | 3-4h | Java devs |
| Deploy Kubernetes | 🟡 | 4-6h | DevOps |
| Real-time monitoring | 🟡 | 4-6h | Frontend devs |
| Tracing setup | 🟡 | 4-6h | SRE/DevOps |
| Multi-tenant setup | 🔴 | 8-12h | DevOps/architects |
| ML predictions | 🔴 | 8-12h | Data scientists |
| Scale to 1M cases | 🔴 | 12-16h | Performance engineers |
| SPIFFE security | 🔴 | 6-8h | Security engineers |
| Architecture deep dive | 🔴 | 8-12h | Architects |

---

## Quick Links by Role

### For Developers
- **First Steps**: [01-build-yawl.md](tutorials/01-build-yawl.md)
- **Custom Handler**: [06-write-a-custom-work-item-handler.md](tutorials/06-write-a-custom-work-item-handler.md)
- **Testing**: [how-to/testing.md](how-to/testing.md)
- **API**: [05-call-yawl-rest-api.md](tutorials/05-call-yawl-rest-api.md)

### For DevOps/SRE
- **Deployment**: [how-to/deployment/overview.md](how-to/deployment/overview.md)
- **Docker**: [how-to/deployment/docker.md](how-to/deployment/docker.md)
- **Monitoring**: [yawl-monitoring-getting-started.md](tutorials/yawl-monitoring-getting-started.md)
- **Operations**: [how-to/operations/](how-to/operations/)

### For Data Scientists
- **ML Integration**: [pi/tutorials/01-first-case-prediction.md](pi/tutorials/01-first-case-prediction.md)
- **Python Code**: [polyglot/tutorials/01-graalpy-getting-started.md](polyglot/tutorials/01-graalpy-getting-started.md)
- **Process Mining**: [explanation/object-centric-process-mining.md](explanation/object-centric-process-mining.md)

### For Architects
- **Architecture**: [explanation/dual-engine-architecture.md](explanation/dual-engine-architecture.md)
- **Design Decisions**: [explanation/decisions/](explanation/decisions/)
- **Performance**: [PERFORMANCE.md](PERFORMANCE.md)
- **Thesis**: [v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md](v6/THESIS-YAWL-V6-ARCHITECTURE-ANALYSIS.md)

### For Business Users
- **Getting Started**: [tutorials/10-getting-started.md](tutorials/10-getting-started.md)
- **FAQ**: [FAQ_AND_COMMON_ISSUES.md](FAQ_AND_COMMON_ISSUES.md)

---

## Related Documents

- **[SEARCH_INDEX.md](SEARCH_INDEX.md)** — Full doc search index
- **[TOPIC_INDEX.md](TOPIC_INDEX.md)** — Topics → docs mapping
- **[MODULE_HEALTH_DASHBOARD.md](MODULE_HEALTH_DASHBOARD.md)** — Module maturity
- **[DOCUMENTATION_COMPLETENESS.md](DOCUMENTATION_COMPLETENESS.md)** — 4-quadrant coverage
- **[diataxis/INDEX.md](diataxis/INDEX.md)** — All docs by quadrant

**Last Updated**: 2026-02-28
**Next Review**: 2026-03-28
**Feedback**: Submit issues or suggestions to the docs team

---

## Tips for Success

1. **Start with your role**: Use "Quick Links by Role" section above
2. **Estimate time realistically**: Add 20-30% buffer for exploration
3. **Bookmark key docs**: Save API reference, config docs for quick lookup
4. **Join community**: Slack, GitHub discussions for peer help
5. **Contribute back**: Share your learnings! Update docs with your insights

Good luck! 🚀
