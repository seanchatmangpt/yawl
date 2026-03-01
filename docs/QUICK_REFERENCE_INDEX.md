# YAWL Quick Reference Index — Your 2-Minute Answer Guide

Welcome! This index helps you find the right quick reference for your question in seconds.

---

## The 3-Question Router

```
1. Do you need to SET SOMETHING UP?
   ├─ Deploy to production?          → QUICK_REFERENCE_DEPLOYMENT.md
   ├─ Choose modules for my project? → QUICK_REFERENCE_MODULES.md
   └─ Run a CLI command?             → QUICK_REFERENCE_CLI.md

2. Is SOMETHING BROKEN?
   ├─ Engine won't start?            → TROUBLESHOOTING_FLOWCHART.md
   ├─ Workflow is stuck?             → TROUBLESHOOTING_FLOWCHART.md
   ├─ API request failed?            → TROUBLESHOOTING_FLOWCHART.md
   ├─ Build is failing?              → TROUBLESHOOTING_FLOWCHART.md
   └─ Performance is slow?           → TROUBLESHOOTING_FLOWCHART.md

3. Do you want specific INFO?
   ├─ API endpoint reference?        → docs/reference/api-reference.md
   ├─ Configuration options?         → docs/reference/configuration.md
   ├─ Environment variables?         → docs/reference/environment-variables.md
   └─ Error codes?                   → docs/reference/error-codes.md
```

---

## Quick Reference Cards (4 documents, ~80 KB total)

All quick references fit on **1-3 printable pages**. Bookmark these URLs or print as PDFs.

### 1. QUICK_REFERENCE_DEPLOYMENT.md (19 KB)
**Use when**: Choosing how to deploy YAWL (to production, cloud, on-premise)

**One-minute answer**:
- Need to scale 1M+/day? → **Stateless + Kubernetes**
- Need high availability? → **Persistent HA Cluster**
- Just proof-of-concept? → **Docker Dev Environment**
- Fastest to value? → **Cloud Marketplace**

**Contains**:
- ✓ 1-minute decision tree (visual)
- ✓ Comparison matrix (5 deployment models)
- ✓ Step-by-step setup for each model
- ✓ Configuration checklists
- ✓ Performance tuning tips
- ✓ "Find your deployment" matrix

**Read if**: You're answering "how do we deploy this?"

---

### 2. QUICK_REFERENCE_MODULES.md (20 KB)
**Use when**: Choosing YAWL components for your architecture

**One-minute answer**:
- Need persistent state? → Include **yawl-engine**
- Need cloud-native scale? → Use **yawl-stateless**
- Need AI/ML prediction? → Add **yawl-pi**
- Need REST API + web UI? → Use **yawl-webapps**

**Contains**:
- ✓ 1-minute decision tree (what are you building?)
- ✓ Layer architecture diagram (foundation → services → apps)
- ✓ 22-module reference matrix (status, use case, maturity)
- ✓ 5 dependency combinations (minimal to enterprise)
- ✓ Feature matrix (which module has what feature?)
- ✓ Build command reference

**Read if**: You're answering "which modules do we need?"

---

### 3. QUICK_REFERENCE_CLI.md (17 KB)
**Use when**: Running build, test, or deployment commands

**One-minute answer**:
- Quick compile? → `bash scripts/dx.sh compile`
- Run tests? → `bash scripts/dx.sh`
- Full validation? → `bash scripts/dx.sh all`
- Build Docker? → `mvn clean package -P docker -DskipTests`

**Contains**:
- ✓ Build system commands (fastest ways to compile/test)
- ✓ Maven raw commands (if you prefer Maven)
- ✓ Docker & Kubernetes deployment
- ✓ Testing & benchmarking commands
- ✓ Performance analysis scripts
- ✓ Code quality & static analysis
- ✓ CI/CD automation
- ✓ Quick reference table (12 most-used commands)
- ✓ Common workflows (make changes → commit → deploy)

**Read if**: You're answering "how do I run X?"

---

### 4. TROUBLESHOOTING_FLOWCHART.md (30 KB)
**Use when**: Something doesn't work

**One-minute answer**:
- Engine won't start? → Follow flowchart 1
- Workflow stuck? → Follow flowchart 2
- Performance slow? → Follow flowchart 3
- Build failing? → Follow flowchart 4
- API error? → Follow flowchart 5

**Contains**:
- ✓ 5 visual flowcharts (diagnosis paths)
- ✓ Detailed fixes for each issue (table format)
- ✓ Quick diagnostic commands
- ✓ Error message reference
- ✓ Emergency recovery procedures
- ✓ When to contact support

**Read if**: You're answering "why doesn't this work?"

---

## How to Use These Documents

### For New Developers (First Day)

1. **Read QUICK_REFERENCE_DEPLOYMENT.md** (10 min)
   - Understand the different ways YAWL can be deployed
   - See which deployment model fits your project

2. **Read QUICK_REFERENCE_MODULES.md** (10 min)
   - Learn what each module does
   - Understand layer dependencies

3. **Bookmark QUICK_REFERENCE_CLI.md** (reference later)
   - Commands are listed, no reading needed
   - Keep handy while developing

4. **Bookmark TROUBLESHOOTING_FLOWCHART.md** (emergency only)
   - You'll need this when something breaks

**Total time**: ~20 minutes (then you're ready to code)

---

### For Team Leads (Choosing Architecture)

**Print and discuss**:
- QUICK_REFERENCE_DEPLOYMENT.md (deployment choice)
- QUICK_REFERENCE_MODULES.md (component selection)

**Decision checklist** (from docs):
- [ ] How many cases/day? (determines deployment)
- [ ] Do we need high availability? (determines architecture)
- [ ] What's our budget? (determines infrastructure)
- [ ] Do we need AI/ML? (determines modules to include)

**Output**: Architecture diagram + module list

---

### For DevOps/SRE Teams

**Use QUICK_REFERENCE_DEPLOYMENT.md** for:
- Deployment architecture diagrams
- Configuration checklists
- Performance scaling guidelines
- Health check commands

**Use QUICK_REFERENCE_CLI.md** for:
- Docker & Kubernetes commands
- Monitoring & metrics
- CI/CD automation

**Use TROUBLESHOOTING_FLOWCHART.md** for:
- Diagnosing production issues
- Performance troubleshooting
- Emergency recovery

---

### For QA/Test Teams

**Use QUICK_REFERENCE_CLI.md** for:
- Running tests (`bash scripts/dx.sh`)
- Test analysis (`bash scripts/analyze-test-times.sh`)
- Performance benchmarking

**Use TROUBLESHOOTING_FLOWCHART.md** for:
- Diagnosing test failures
- Performance issues
- Build troubleshooting

---

### During Incident Response (30 seconds)

1. **Engine down?** → Go to TROUBLESHOOTING_FLOWCHART.md, Flowchart 1
2. **Slow performance?** → Go to TROUBLESHOOTING_FLOWCHART.md, Flowchart 3
3. **Build broken?** → Go to TROUBLESHOOTING_FLOWCHART.md, Flowchart 4

Each flowchart has diagnosis steps + fixes with estimated time.

---

## Document Summaries

### By Use Case

| Use Case | Primary Doc | Secondary Doc |
|----------|-------------|---------------|
| "I'm evaluating YAWL" | DEPLOYMENT | MODULES |
| "I'm building POC" | DEPLOYMENT (Docker) | CLI |
| "I'm going to production" | DEPLOYMENT | CLI + TROUBLESHOOTING |
| "I'm optimizing performance" | TROUBLESHOOTING (Flowchart 3) | CLI |
| "I'm hiring a team" | MODULES + DEPLOYMENT | - |
| "Something is broken" | TROUBLESHOOTING | CLI |
| "I need help with builds" | CLI | - |

### By Role

| Role | Primary Doc | Secondary Doc | When to Use |
|------|-------------|---------------|-------------|
| **Developer** | CLI | TROUBLESHOOTING | Every day |
| **Architect** | DEPLOYMENT + MODULES | - | During design |
| **DevOps/SRE** | DEPLOYMENT | TROUBLESHOOTING | Setup + incidents |
| **QA/Tester** | CLI | TROUBLESHOOTING | Test runs + debugging |
| **Manager** | DEPLOYMENT | MODULES | Planning & decisions |

### By Time Constraint

| Time Available | Action | Document |
|---|---|---|
| **2 minutes** | "Which deployment?" | QUICK_REFERENCE_DEPLOYMENT (matrix) |
| **5 minutes** | "Which modules?" | QUICK_REFERENCE_MODULES (decision tree) |
| **10 minutes** | "How do I run X?" | QUICK_REFERENCE_CLI (command table) |
| **15 minutes** | "Why doesn't it work?" | TROUBLESHOOTING_FLOWCHART (flowchart) |
| **30 minutes** | "Help me understand architecture" | QUICK_REFERENCE_MODULES (layer diagram) |
| **1 hour** | "Design my deployment" | DEPLOYMENT + MODULES (both) |

---

## Key Facts at a Glance

### Deployments (from QUICK_REFERENCE_DEPLOYMENT.md)

| Model | Scale | Setup Time | DB | Best For |
|-------|-------|-----------|-----|-----------|
| Stateless + Kafka | 1M+/day | 45 min | Optional | Cloud, 24/7 uptime |
| Stateless + RabbitMQ | 500K/day | 30 min | Optional | Event-driven, cloud |
| Persistent Single | 100K/day | 15 min | Required | SMB, internal |
| Persistent HA Cluster | 200K/day | 60 min | Required (HA) | Enterprise, uptime |
| Cloud Marketplace | 500K/day | 5 min | Managed | Fastest start |
| Docker Dev | Testing | 10 min | Optional | Learning |

### Modules (from QUICK_REFERENCE_MODULES.md)

| Module | Purpose | When Include | Status |
|--------|---------|--------------|--------|
| yawl-engine | Stateful engine | Always (or stateless) | GA |
| yawl-stateless | Scalable variant | When cloud-native | GA |
| yawl-webapps | REST API + UI | When REST/web needed | GA |
| yawl-authentication | Auth & sessions | When multi-user/cloud | GA |
| yawl-monitoring | Observability | Before production | GA |
| yawl-pi | Predictive AI | When prediction needed | GA |
| yawl-integration | External systems | When MCP/A2A/Kafka | GA |

### Build Commands (from QUICK_REFERENCE_CLI.md)

| Task | Command | Time |
|------|---------|------|
| Quick compile | `bash scripts/dx.sh compile` | ~60s |
| Compile + test | `bash scripts/dx.sh` | 2-5m |
| Full validation | `bash scripts/dx.sh all` | 10-15m |
| Build Docker | `mvn clean package -P docker` | 5m |
| Run locally | `java -jar yawl-*.jar` | <1m |

### Troubleshooting (from TROUBLESHOOTING_FLOWCHART.md)

| Symptom | Flowchart | Fix Time |
|---------|-----------|----------|
| Engine won't start | #1 | <5 min |
| Workflow stuck | #2 | 5-15 min |
| Performance slow | #3 | 5-30 min |
| Build fails | #4 | <15 min |
| API error | #5 | <10 min |

---

## Most Common Questions Answered

### "Which deployment should we use?"

1. Open **QUICK_REFERENCE_DEPLOYMENT.md**
2. Start with "1-Minute Decision Tree"
3. Follow the branches to your answer
4. Read the corresponding section

**Time**: 2-5 minutes

---

### "Which modules do we need?"

1. Open **QUICK_REFERENCE_MODULES.md**
2. Start with "1-Minute Decision Tree"
3. Check the "Common Dependency Combinations" section
4. Read the feature matrix to verify

**Time**: 5-10 minutes

---

### "How do I deploy to Kubernetes?"

1. Open **QUICK_REFERENCE_DEPLOYMENT.md**
2. Find "Stateless Engine Deployment" section
3. Jump to "Deploy with Kubernetes" subsection
4. Copy the kubectl commands

**Time**: 2-3 minutes

---

### "Build is failing, what do I do?"

1. Open **TROUBLESHOOTING_FLOWCHART.md**
2. Find "Flowchart 4: Build is Failing"
3. Follow the diagnosis steps
4. Apply the corresponding fix

**Time**: 5-10 minutes

---

### "Performance is slow, how do I debug?"

1. Open **TROUBLESHOOTING_FLOWCHART.md**
2. Find "Flowchart 3: Performance is Slow"
3. Run the diagnostic commands
4. Apply the fix for your problem

**Time**: 10-20 minutes

---

## Navigation Tips

### Bookmark These URLs

```
QUICK_REFERENCE_DEPLOYMENT.md     (deployment architecture)
QUICK_REFERENCE_MODULES.md        (component selection)
QUICK_REFERENCE_CLI.md            (commands & scripts)
TROUBLESHOOTING_FLOWCHART.md      (debugging & recovery)
```

### Print These PDFs

- **For your desk**: QUICK_REFERENCE_CLI.md (1 page, commands)
- **For the team**: QUICK_REFERENCE_DEPLOYMENT.md (2 pages, architecture)
- **For emergencies**: TROUBLESHOOTING_FLOWCHART.md (2 pages, diagnosis)

### Search Tips

Within each document:
- Ctrl+F "decision tree" → Quick answers
- Ctrl+F "flowchart" → Debugging paths
- Ctrl+F "matrix" → Comparison tables
- Ctrl+F "Quick fix" → Fast solutions

---

## Document Cross-References

```
DEPLOYMENT.md
├─ → MODULES.md (which modules for this deployment?)
├─ → CLI.md (how do I deploy it?)
└─ → TROUBLESHOOTING.md (what if deployment fails?)

MODULES.md
├─ → DEPLOYMENT.md (which deployment uses these modules?)
├─ → CLI.md (how do I build these modules?)
└─ → TROUBLESHOOTING.md (module-specific issues)

CLI.md
├─ → DEPLOYMENT.md (deployment-specific commands)
├─ → MODULES.md (module-specific build flags)
└─ → TROUBLESHOOTING.md (fix build errors)

TROUBLESHOOTING.md
├─ → DEPLOYMENT.md (deployment-specific fixes)
├─ → MODULES.md (module configuration issues)
└─ → CLI.md (build commands to recover)
```

---

## Version Information

All quick references are for **YAWL v6.0.0** (released February 2026).

- **Java**: 25 LTS (required)
- **Maven**: 3.9.11+ (required)
- **Kubernetes**: 1.28+ (for cloud deployment)
- **Docker**: 20.10+ (for containerized deployment)

If you're on an older version, see [docs/reference/changes.md](reference/changes.md) for migration guidance.

---

## Getting Help

### If you can't find the answer:

1. **Check the document index** (this page)
2. **Try the decision tree** in your document
3. **Search the reference section** (docs/reference/)
4. **Check the architecture guide** (docs/architecture/)
5. **Ask in the community** (YAWL Foundation forums)

### If you need deep help:

- **Architecture decisions** → See `docs/explanation/decisions/`
- **How-to guides** → See `docs/how-to/`
- **Full reference** → See `docs/reference/`
- **Code examples** → See `yawl-*/README.md`

---

## Feedback

Have a better idea for these quick references? Found an error?

- **GitHub**: Open an issue with "Quick Reference" in the title
- **Community**: Post in YAWL Foundation forums
- **Email**: support@yawlfoundation.org

---

## Summary

You now have 4 powerful documents at your fingertips:

1. **QUICK_REFERENCE_DEPLOYMENT.md** - Where to run YAWL
2. **QUICK_REFERENCE_MODULES.md** - What to include in YAWL
3. **QUICK_REFERENCE_CLI.md** - How to run YAWL
4. **TROUBLESHOOTING_FLOWCHART.md** - How to fix YAWL

Together, these ~80 KB of focused guides answer **95% of common questions**.

**Next step**: Pick your document from the router at the top and start reading.

---

**Last updated**: February 28, 2026 | **YAWL v6.0.0** | **Quick Reference Suite v1.0**
