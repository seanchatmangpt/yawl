# YAWL Documentation Navigation Guide

**Version:** 5.2
**Last Updated:** 2026-02-16
**Purpose:** Central navigation for all YAWL documentation

---

## New Consolidated Documentation (Recommended)

As of v5.2, YAWL documentation has been consolidated into **4 primary guides** to reduce fragmentation and improve clarity.

### 1. Quick Start Guide
**File:** `/home/user/yawl/QUICK_START.md`
**Length:** ~150 lines (10-15 minute read)
**Audience:** New users, first-time installers
**Topics:**
- Prerequisites verification
- Installation (clone, build, run)
- First access and authentication
- Common operations (launch workflow, get work items)
- Troubleshooting common issues
- Health checks and verification
- Essential commands cheat sheet

**When to use:**
- First time setting up YAWL
- Quick reference for common commands
- Resolving installation issues

---

### 2. Developer Guide
**File:** `/home/user/yawl/DEVELOPER_GUIDE.md`
**Length:** ~400 lines (25 minute read)
**Audience:** Developers extending or modifying YAWL
**Topics:**
- Development environment setup
- Project structure and module dependencies
- Build system (Maven multi-module)
- Testing strategy (Chicago TDD)
- Code standards (HYPER_STANDARDS compliance)
- Claude Code integration (hooks, skills, agents)
- Architecture deep dive
- Contributing guidelines

**When to use:**
- Developing new YAWL features
- Understanding codebase architecture
- Contributing to YAWL project
- Setting up development environment
- Writing tests

---

### 3. Integration Guide (Consolidated)
**File:** `/home/user/yawl/docs/INTEGRATION_GUIDE_CONSOLIDATED.md`
**Length:** ~300 lines (20 minute read)
**Audience:** Integration developers, AI engineers
**Topics:**
- MCP server integration (expose YAWL to AI models)
- A2A protocol integration (agent-to-agent communication)
- Interface B client integration (REST API access)
- Z.AI API integration (cloud AI services)
- Authentication and security
- Integration patterns
- Troubleshooting

**When to use:**
- Integrating YAWL with AI systems
- Building agent-based workflows
- Connecting external systems to YAWL
- Using YAWL REST API programmatically

---

### 4. Operations Guide
**File:** `/home/user/yawl/OPERATIONS_GUIDE.md`
**Length:** ~250 lines (30 minute read)
**Audience:** DevOps, SRE, production operations teams
**Topics:**
- Production deployment (Docker, Kubernetes, Helm)
- Configuration management
- Monitoring and logging (Prometheus, Grafana, ELK)
- Performance tuning (JVM, database, Redis)
- Troubleshooting production issues
- Backup and recovery
- Security operations
- Scaling and high availability

**When to use:**
- Deploying YAWL to production
- Monitoring production systems
- Performance optimization
- Disaster recovery planning
- Scaling YAWL infrastructure

---

## Quick Navigation by Task

### I want to...

**Get started with YAWL**
→ Read `QUICK_START.md` (15 minutes)

**Develop YAWL features**
→ Read `DEVELOPER_GUIDE.md` (25 minutes)

**Integrate YAWL with AI/agents**
→ Read `INTEGRATION_GUIDE_CONSOLIDATED.md` (20 minutes)

**Deploy YAWL to production**
→ Read `OPERATIONS_GUIDE.md` (30 minutes)

**Understand the codebase**
→ Read `DEVELOPER_GUIDE.md` → Architecture section

**Use YAWL with Claude Code**
→ Read `DEVELOPER_GUIDE.md` → Claude Code Integration section
→ See `.claude/CAPABILITIES.md` for detailed capabilities

**Integrate with MCP/A2A**
→ Read `INTEGRATION_GUIDE_CONSOLIDATED.md` → MCP/A2A sections

**Deploy to Kubernetes**
→ Read `OPERATIONS_GUIDE.md` → Production Deployment → Kubernetes

**Troubleshoot production issues**
→ Read `OPERATIONS_GUIDE.md` → Troubleshooting section

**Set up monitoring**
→ Read `OPERATIONS_GUIDE.md` → Monitoring and Logging section

---

## Legacy Documentation (Deprecated)

The following documents are being phased out in favor of the consolidated guides above:

### To Be Removed (Duplicate Content)
- `INTEGRATION_README.md` → Merged into `INTEGRATION_GUIDE_CONSOLIDATED.md`
- `INTEGRATION_GUIDE.md` (old) → Replaced by `INTEGRATION_GUIDE_CONSOLIDATED.md`
- Multiple build-related docs → Consolidated into `DEVELOPER_GUIDE.md`

### Still Relevant (Specialized)
- `CLAUDE.md` - Project overview (YAWL-specific conventions)
- `.claude/BEST-PRACTICES-2026.md` - Claude Code best practices
- `.claude/HYPER_STANDARDS.md` - Detailed coding standards
- `PRODUCTION_READINESS_VALIDATION_FINAL.md` - Production validation report
- Cloud-specific guides in `docs/marketplace/{aws,azure,gcp}/`

---

## Documentation Hierarchy

```
Primary (Read First)
├── QUICK_START.md                   # 15 min - Installation & basics
├── DEVELOPER_GUIDE.md               # 25 min - Development
├── INTEGRATION_GUIDE_CONSOLIDATED.md # 20 min - Integrations
└── OPERATIONS_GUIDE.md              # 30 min - Production ops

Reference (As Needed)
├── CLAUDE.md                        # Project conventions
├── .claude/
│   ├── CAPABILITIES.md              # Claude Code capabilities
│   ├── BEST-PRACTICES-2026.md       # Best practices
│   └── HYPER_STANDARDS.md           # Coding standards
├── README.md                        # Project overview
└── docs/
    ├── marketplace/                 # Cloud deployment guides
    ├── performance/                 # Performance documentation
    └── security/                    # Security documentation

Specialized (Domain-Specific)
├── MAVEN_BUILD_GUIDE.md             # Maven details
├── SECURITY_MIGRATION_GUIDE.md      # Security hardening
├── PRODUCTION_READINESS_VALIDATION_FINAL.md  # Validation report
└── database/                        # Database-specific docs
```

---

## Cross-References Between Guides

### Quick Start → Developer Guide
After completing Quick Start, read Developer Guide if you need to:
- Understand the codebase structure
- Write custom YAWL extensions
- Contribute to the project

### Developer Guide → Integration Guide
After understanding development, read Integration Guide if you need to:
- Expose YAWL via MCP/A2A
- Integrate with external systems
- Build AI-enhanced workflows

### Integration Guide → Operations Guide
After implementing integrations, read Operations Guide to:
- Deploy integrations to production
- Monitor integration performance
- Troubleshoot integration issues

### All Guides → CLAUDE.md
All guides reference `CLAUDE.md` for:
- Project-specific conventions
- Build commands reference
- Agent and skill definitions

---

## Documentation Standards

### File Organization
- **Primary guides:** Root directory (`/home/user/yawl/`)
- **Specialized docs:** `docs/` subdirectories
- **Claude Code:** `.claude/` directory
- **Examples:** `examples/` directory

### Naming Conventions
- Primary guides: `UPPERCASE_GUIDE.md`
- Specialized docs: `Sentence_Case_Topic.md`
- Cloud guides: `lowercase-with-dashes.md`

### Cross-Referencing
- Use **absolute paths** when referencing files
- Include **section anchors** for deep links
- Provide **context** for why reader should follow link

---

## Documentation Maintenance

### Update Frequency
- **Primary guides:** Updated with each minor version
- **Cloud guides:** Updated when platform changes
- **Reference docs:** Updated as features change

### Version Control
- All documentation tracked in Git
- Changes reviewed via pull requests
- Major rewrites tagged with version

### Contribution
See `DEVELOPER_GUIDE.md` → Contributing Guidelines for:
- Documentation style guide
- Review process
- Commit message format

---

## Getting Help

### Documentation Issues
If documentation is unclear or incorrect:
1. Check for updates in latest version
2. Search GitHub issues for similar questions
3. Create new issue with "docs" label

### General Support
- **GitHub Issues:** https://github.com/yawlfoundation/yawl/issues
- **Website:** https://yawlfoundation.github.io
- **Mailing List:** yawl@list.unsw.edu.au

---

## Summary

**4 Consolidated Guides = 80% of Documentation Needs**

| Guide | Lines | Time | Audience |
|-------|-------|------|----------|
| **QUICK_START.md** | 150 | 15 min | New users |
| **DEVELOPER_GUIDE.md** | 400 | 25 min | Developers |
| **INTEGRATION_GUIDE_CONSOLIDATED.md** | 300 | 20 min | Integrators |
| **OPERATIONS_GUIDE.md** | 250 | 30 min | Operators |
| **TOTAL** | ~1100 | ~90 min | All users |

**Start here:** `QUICK_START.md`
**Deep dive:** Guide matching your role
**Reference:** `CLAUDE.md` and specialized docs

---

**Documentation Consolidation Completed:** 2026-02-16
**Next Review:** v5.3 (Q2 2026)
