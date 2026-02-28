# YAWL Learning Roadmap

This guide shows recommended learning paths based on your goals and experience level. Follow the progression to build deep understanding of YAWL without knowledge gaps.

---

## 🎯 Choose Your Goal

### Path 1: I Just Want to Try YAWL (30 minutes)
**Goal**: Run a workflow locally and understand basic concepts

```
START
  ↓
[10 min] Quick Start (Users) ← Read this first!
  ├─ Overview of YAWL
  ├─ Why use Petri nets
  └─ 3 deployment options
  ↓
[15 min] Run Your First Workflow
  ├─ Launch the engine
  ├─ Deploy a sample workflow
  ├─ Execute a case
  └─ View results in Control Panel
  ↓
[5 min] Try MCP Agent Integration (optional)
  └─ Connect an AI agent
  ↓
DONE! You can now explain YAWL to others.
```

**Next step**: Pick a more focused path below

---

### Path 2: I Want to Build Workflows (2-3 hours)
**Goal**: Design and deploy real workflows

```
FOUNDATION (30 min):
  ↓
  [10 min] Quick Start (Users)
  [20 min] Run Your First Workflow
  ↓
CORE CONCEPTS (45 min):
  ↓
  [15 min] Petri Net Foundations
           → Understand tokens, places, transitions
  [15 min] Workflow Patterns Reference
           → See what patterns YAWL supports
  [15 min] Case Lifecycle Explanation
           → How cases flow through engine
  ↓
BUILD YOUR FIRST WORKFLOW (60 min):
  ↓
  [30 min] Write a YAWL Specification
           → Design tasks, conditions, flows
           → Code along with the tutorial
  [30 min] How-To: Model a Process
           → Apply best practices
           → Avoid common mistakes
  ↓
DEPLOY & TEST (30 min):
  ↓
  [15 min] How-To: Deploy to Production
  [15 min] Testing Guide
  ↓
DONE! You can design and deploy real workflows.
```

**Advanced**: Add data modeling, exception handling, scheduling

---

### Path 3: I Want to Develop on YAWL (3-4 hours)
**Goal**: Write code that integrates with or extends YAWL

```
FOUNDATION (45 min):
  ↓
  [20 min] Build YAWL
           → Clone the repo
           → Compile & verify
  [15 min] Understand the Build
           → Maven structure
           → Shared-src strategy
  [10 min] Quick Start (Users)
  ↓
CORE CONCEPTS (45 min):
  ↓
  [15 min] YEngine Architecture
           → YEngine, YNetRunner, case creation
  [15 min] YElements Domain Model
           → YSpecification, YNet, YTask
  [15 min] Dual Engine Architecture
           → Stateful vs stateless
  ↓
PRACTICE (60 min):
  ↓
  [30 min] Custom Work Item Handler
           → Extend engine with custom logic
           → Code along with examples
  [30 min] Call the REST API
           → Integrate from external systems
           → Test your integration
  ↓
ADVANCED (30 min):
  ↓
  Choose one:
  ├─ [20 min] MCP Agent Integration → Connect AI agents
  ├─ [20 min] Polyglot (Python/JS) → Use other languages
  └─ [20 min] Code Generation (ggen) → Generate specs
  ↓
DONE! You can extend YAWL and build integrations.
```

**Next**: DevOps path or Advanced path

---

### Path 4: I Want to Deploy & Manage YAWL (3-4 hours)
**Goal**: Deploy, monitor, and scale YAWL in production

```
FOUNDATION (30 min):
  ↓
  [10 min] Quick Start (Users)
  [20 min] Docker Dev Environment
           → Container basics
           → Local deployment
  ↓
DEPLOYMENT BASICS (60 min):
  ↓
  [20 min] Stateless Engine Getting Started
           → Event-driven architecture
  [20 min] Web Applications Getting Started
           → REST API server
  [20 min] Production Deployment Guide
           → Security, monitoring, scaling
  ↓
OPERATIONS (45 min):
  ↓
  [15 min] Monitoring Getting Started
           → OpenTelemetry tracing
           → Prometheus metrics
  [15 min] Scheduling Getting Started
           → Calendar-aware execution
  [15 min] Authentication Setup
           → JWT, CSRF, certificates
  ↓
ADVANCED OPERATIONS (30 min):
  ↓
  Choose one:
  ├─ [20 min] Scale to 1M Cases → Performance tuning
  ├─ [20 min] Disaster Recovery → Backup & restore
  └─ [20 min] CI/CD Setup → Automated deployment
  ↓
DONE! You can deploy and manage YAWL at scale.
```

**Next**: Add monitoring, Add AI features

---

### Path 5: I Want to Use AI with YAWL (2-3 hours)
**Goal**: Add machine learning predictions and AI agents to workflows

```
FOUNDATION (20 min):
  ↓
  [10 min] Quick Start (Users)
  [10 min] Run Your First Workflow
  ↓
AI CONCEPTS (30 min):
  ↓
  [15 min] Process Intelligence Architecture
           → AutoML, predictions, adaptation
  [15 min] Case Lifecycle Explanation
           → Where predictions fit
  ↓
PRACTICE (60 min):
  ↓
  [20 min] First Case Prediction
           → Build your first ML model
           → Understand the workflow
  [20 min] Train AutoML Model
           → Use TPOT2 for optimization
  [20 min] Realtime Adaptive Workflows
           → Deploy predictions to engine
  ↓
AGENTS (30 min):
  ↓
  Choose one:
  ├─ [20 min] MCP Agent Integration → Connect AI agents
  └─ [20 min] Natural Language QA → Query in plain English
  ↓
DONE! You can add AI predictions and agents to workflows.
```

**Next**: Add polyglot languages, Add advanced scheduling

---

## 📚 Organized by Level

### Level 1️⃣: Beginner (0-2 hours)

**Goal**: Understand YAWL and run a workflow

**Suggested path**:
1. [Quick Start (Users)](./tutorials/quick-start-users.md)
2. [Run Your First Workflow](./tutorials/03-run-your-first-workflow.md)
3. [Petri Net Foundations](./explanation/petri-net-foundations.md)

**Estimated time**: 45 minutes

**You'll be able to**:
- Explain YAWL and Petri nets
- Deploy and execute a workflow
- View cases in the control panel

---

### Level 2️⃣: Intermediate (2-5 hours)

**Goal**: Build real workflows or extend YAWL

**Choose your specialization**:
- **Workflow Design**: [Write a YAWL Specification](./tutorials/04-write-a-yawl-specification.md) → [Workflow Patterns](./reference/workflow-patterns.md)
- **Development**: [Custom Work Item Handler](./tutorials/06-write-a-custom-work-item-handler.md) → [REST API](./tutorials/05-call-yawl-rest-api.md)
- **Deployment**: [Stateless Engine](./tutorials/yawl-stateless-getting-started.md) → [Production Guide](./how-to/deployment/stateless-deployment.md)

**Estimated time**: 3-4 hours per specialization

**You'll be able to**:
- Design and deploy complex workflows
- Write code that integrates with YAWL
- Deploy to production

---

### Level 3️⃣: Advanced (5-10+ hours)

**Goal**: Master YAWL and optimize for your use case

**Choose your specialization**:
- **Architecture**: [Engine Architecture](./explanation/yawl-engine-architecture.md) → [ADRs](./explanation/decisions/)
- **Performance**: [Scale to 1M Cases](./tutorials/11-scale-to-million-cases.md) → [Performance Tuning](./how-to/performance-optimization.md)
- **AI Integration**: [Process Intelligence](./pi/tutorials/01-first-case-prediction.md) → [Adaptive Workflows](./pi/tutorials/03-realtime-adaptive.md)
- **Polyglot**: [Python](./polyglot/tutorials/01-graalpy-getting-started.md) → [Code Generation](./tutorials/polyglot-ggen-getting-started.md)

**Estimated time**: 4-6 hours per specialization

**You'll be able to**:
- Architect YAWL solutions for complex requirements
- Optimize for scale and performance
- Integrate AI predictions and agents
- Extend YAWL with custom functionality

---

### Level 4️⃣: Expert (10+ hours)

**Goal**: Become a YAWL architect and contributor

**Study areas**:
- All [Architecture Decision Records](./explanation/decisions/)
- All module architectures ([Engine](./explanation/yawl-engine-architecture.md), [PI](./explanation/yawl-pi-architecture.md), etc.)
- [Source code](/home/user/yawl/yawl-engine/) and module tests
- [Performance analysis](./how-to/performance-optimization.md)
- [Security architecture](./explanation/yawl-security-framework.md)

**Your path**:
1. Complete Levels 1-3
2. Read all ADRs and architecture docs
3. Study the YAWL codebase
4. Implement features and contribute

**You'll be able to**:
- Make architectural decisions
- Design new YAWL features
- Contribute to the project
- Build custom YAWL variants for specialized domains

---

## 🗺️ Learning Paths by Specialization

### Workflow Designer
```
Quick Start (Users)
  ↓
Run Your First Workflow
  ↓
Petri Net Foundations + Case Lifecycle
  ↓
Write a YAWL Specification
  ↓
Workflow Patterns Reference
  ↓
How-To: Model a Process
  ↓
Data Modelling Getting Started [optional]
  ↓
Scheduling Getting Started [optional]
  ↓
Exception Handling [optional]
```

### Backend Developer
```
Build YAWL + Understand the Build
  ↓
YEngine Architecture + YElements Domain Model
  ↓
Call the REST API
  ↓
Custom Work Item Handler
  ↓
Choose advanced:
├─ Polyglot (Python/JS) + Code Generation
├─ MCP Agent Integration
├─ Process Intelligence
└─ Performance Benchmarking
```

### DevOps / Platform Engineer
```
Quick Start (Users)
  ↓
Docker Dev Environment
  ↓
Stateless Engine Getting Started
  ↓
Production Deployment Guide
  ↓
Monitoring Getting Started
  ↓
Authentication Setup
  ↓
Scale to 1M Cases [for high-volume]
  ↓
Choose operations:
├─ Disaster Recovery
├─ CI/CD Setup
└─ Performance Tuning
```

### Data Scientist / ML Engineer
```
Quick Start (Users)
  ↓
Run Your First Workflow
  ↓
Process Intelligence Architecture
  ↓
First Case Prediction
  ↓
Train AutoML Model
  ↓
Realtime Adaptive Workflows
  ↓
Choose advanced:
├─ Natural Language QA
├─ Process Mining
└─ Resource Allocation
```

### Security / Compliance Officer
```
Quick Start (Users)
  ↓
YAWL Security Getting Started
  ↓
Security Framework
  ↓
ADR-005: SPIFFE/SPIRE Zero-Trust
  ↓
Authentication Setup
  ↓
Choose deep-dives:
├─ Certificate Management
├─ Audit Logging
└─ Security Testing
```

---

## ⏱️ Time Estimates by Role

| Role | Beginner | Intermediate | Advanced | Total |
|------|----------|--------------|----------|-------|
| **Workflow Designer** | 1h | 3h | 5h | 9h |
| **Backend Developer** | 1.5h | 4h | 6h | 11.5h |
| **DevOps Engineer** | 1h | 3.5h | 5h | 9.5h |
| **Data Scientist** | 1h | 3h | 4h | 8h |
| **Security Officer** | 1h | 2.5h | 3h | 6.5h |

*Times are approximate and depend on prior experience with similar systems*

---

## 🎓 Recommended Progression

### Option A: Deep & Narrow (Specialist)
Pick one path from above and go deep:

```
Level 1 (Beginner)      1-2 hours
        ↓
Level 2 (Intermediate)  3-4 hours (focused on your specialization)
        ↓
Level 3 (Advanced)      4-6 hours (advanced techniques in your specialization)
        ↓
TOTAL: 8-12 hours to become proficient
```

**Good for**: Backend developers, data scientists, specialists

---

### Option B: Broad & Shallow (Generalist)
Sample each path at intermediate level:

```
Level 1 (Beginner)      1-2 hours
        ↓
Level 2 (Intermediate)  6-8 hours (1-2 hours each specialization)
        ↓
TOTAL: 8-10 hours to understand all areas
```

**Good for**: Architects, project managers, full-stack engineers

---

### Option C: Realistic Enterprise (Mixed)
Learn your specialty deeply, others broadly:

```
Level 1 (Beginner)         1-2 hours
        ↓
Level 2 (Intermediate)     3-4 hours (focused specialty)
                           2-3 hours (broad overview of others)
        ↓
Level 3 (Advanced)         4-6 hours (deep specialty)
        ↓
TOTAL: 10-15 hours for well-rounded expertise
```

**Good for**: Most enterprise teams

---

## 📊 Prerequisite Graph

```
Quick Start (Users)
    ↓
    ├─→ Run Your First Workflow
    │   ├─→ Write a YAWL Specification
    │   ├─→ Call the REST API
    │   └─→ Custom Work Item Handler
    │
    └─→ Build YAWL
        ├─→ Understand the Build
        └─→ Deployment Guides
            ├─→ Docker Environment
            ├─→ Stateless Engine
            └─→ Production Deployment

Petri Net Foundations ──→ YEngine Architecture
                         └─→ YElements Domain Model
                             └─→ Workflow Patterns
                                 └─→ Data Modelling

Process Intelligence Architecture
    ├─→ First Case Prediction
    ├─→ Train AutoML Model
    └─→ Realtime Adaptive Workflows

Security Getting Started
    └─→ Security Framework
        └─→ ADR-005 Zero-Trust
```

---

## ✅ Self-Assessment

### After Beginner (30-60 min):
- [ ] I can explain what YAWL is
- [ ] I can run the engine locally
- [ ] I understand Petri nets
- [ ] I can execute a workflow

### After Intermediate (3-4 hours):
- [ ] I can design a real workflow
- [ ] I can integrate YAWL with external systems
- [ ] I can deploy to production
- [ ] I understand the REST API
- [ ] (Choose one) I can write custom handlers OR design workflows OR manage deployments

### After Advanced (5+ more hours):
- [ ] I can optimize YAWL for my requirements
- [ ] I understand architectural trade-offs
- [ ] I can scale to millions of cases
- [ ] I can integrate AI features
- [ ] I can debug complex issues

---

## 🚀 Next Steps

1. **Take the self-assessment above** — Choose your level
2. **Pick your path** — See above sections for role-based roadmaps
3. **Start with Quick Start** — 10 minutes to understand the basics
4. **Choose your next module** — Follow the progression for your path
5. **Practice** — Run the tutorials, not just read them
6. **Dive deeper** — Use reference and explanation docs when needed
7. **Connect with community** — Ask questions and share your experience

---

## 📚 Additional Resources

- **[Getting Started Paths](./GETTING_STARTED_PATHS.md)** — Quick links by role
- **[Module Dependency Map](./MODULE_DEPENDENCY_MAP.md)** — How modules relate
- **[FAQ & Common Issues](./FAQ_AND_COMMON_ISSUES.md)** — Quick answers
- **[Diataxis Master Index](./diataxis/INDEX.md)** — All 350+ docs
- **[Community](https://github.com/seanchatmangpt/yawl/discussions)** — Ask questions

---

*Last updated: 2026-02-28*
*YAWL v6.0.0 with comprehensive learning paths*
