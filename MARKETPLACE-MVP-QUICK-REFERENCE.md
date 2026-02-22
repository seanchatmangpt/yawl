# Marketplace MVP — Quick Reference Guide

**Purpose**: One-page summary for engineers, PMs, and stakeholders
**Last Updated**: 2026-02-21

---

## Executive Summary (60 seconds)

| Aspect | Details |
|--------|---------|
| **What** | Unified RDF-backed marketplace for Skills, Integrations, Agents, Data |
| **Why** | Enable cross-marketplace discovery (e.g., "Find skills using Salesforce") |
| **When** | 4 weeks |
| **Who** | 3 engineers (RDF expert, platform lead, billing) |
| **Cost** | $75K |
| **Success** | 1000+ entities, <500ms SPARQL queries, full audit trail |

---

## The Four Marketplaces (Unified by RDF)

```
┌─────────────────────────────────────────────────────────────┐
│              UNIFIED RDF GRAPH (Apache Jena)                │
├──────────────┬──────────────┬──────────────┬────────────────┤
│   Skills     │ Integrations │    Agents    │   Data Sources │
│              │              │              │                │
│ • Workflows  │ • SAP Connector  │ • Claude      │ • Audit Logs   │
│ • Tasks      │ • Salesforce API │ • Decision   │ • Metrics      │
│ • Approval   │ • Slack/Email    │ • Vision     │ • Analytics    │
│              │                  │              │                │
│ 250 items    │ 45 items         │ 12 items     │ 8 items        │
└──────────────┴──────────────┴──────────────┴────────────────┘
                          ↓
                  ┌─────────────────┐
                  │  SPARQL Engine  │
                  │ (15-20 Queries) │
                  │   <500ms (p95)  │
                  └─────────────────┘
                          ↓
         ┌─────────────────┬─────────────────┐
         │   REST API      │  Git History    │
         │  /api/skills    │  Immutable      │
         │  /api/discover  │  Audit Trail    │
         └─────────────────┴─────────────────┘
```

---

## 15-20 Pre-Written SPARQL Queries

**Category 1: Skill Discovery**
1. Find skills by input type
2. Find skills by output type
3. Find skills by tag
4. Find highest-rated skills

**Category 2: Integration Discovery**
5. Find integrations by target system (SAP, Salesforce, etc.)
6. Find integrations by authentication type (OAuth, JWT, etc.)
7. Find skills using a specific integration

**Category 3: Agent Discovery**
8. Find agents by capability
9. Find agents by model (Claude, GPT, etc.)
10. Find agents supporting a tool

**Category 4: Data Discovery**
11. Find data sources by schema
12. Find data sources by access type (SQL, SPARQL, etc.)
13. Find data sources by cost range

**Category 5: Cross-Marketplace Links**
14. Skill → Integration → Agent chain (dependency graph)
15. Skills using Skill X (transitive closure)
16. Agents consuming Data X (reverse lookup)
17. Total cost of a skill (sum all dependencies)

**Category 6: Metadata & Stats**
18. Marketplace growth (entity counts)
19. Top-rated skills by marketplace
20. Entity versions and breaking changes

---

## Week-by-Week Breakdown

| Week | Focus | Deliverables | Success Metric |
|------|-------|--------------|----------------|
| **1** | Foundation | Ontology (550 lines), Jena TDB2, Git init | Ontology validates, 100+ triples stored |
| **2** | Discovery | SPARQL engine (15 queries), REST API | All queries <500ms, API responds |
| **3** | Governance | Git sync, version control, audit trail | 100% of changes in Git history |
| **4** | Billing | Usage tracking, pricing calculator, integration tests | 1000+ entities, full E2E test passes |

---

## Key Technologies

| Component | Technology | Why |
|-----------|-----------|-----|
| **RDF Store** | Apache Jena TDB2 | Lightweight, SPARQL native, persistent |
| **SPARQL Endpoint** | Jena Fuseki | HTTP endpoint, easy to query |
| **Git** | JGit (Java) | Immutable history, version control |
| **API** | Spring Boot REST | Familiar, fast to build |
| **Database** | PostgreSQL | Billing, usage events, audit logs |
| **Containers** | Docker Compose | Local dev, simple to set up |

---

## File Structure (Maven Module)

```
yawl-marketplace/
├── pom.xml                           # Dependencies
├── docker-compose.yml                # Local dev environment
├── src/main/java/org/yawlfoundation/yawl/marketplace/
│   ├── RDFGraphRepository.java       # Jena TDB2 access layer
│   ├── SPARQLQueryEngine.java        # 15 pre-written queries
│   ├── GitSyncService.java           # Bidirectional sync
│   ├── BillingCalculator.java        # Pricing & charges
│   ├── rest/
│   │   ├── SkillController.java
│   │   ├── IntegrationController.java
│   │   ├── AgentController.java
│   │   └── DataSourceController.java
│   ├── governance/
│   │   ├── ChangeNotificationService.java
│   │   └── BreakingChangeDetector.java
│   └── audit/
│       └── AuditService.java
├── src/main/resources/
│   ├── ontology/
│   │   ├── yawl-marketplace-ontology.ttl  # Core schema (550 lines)
│   │   └── example-graph.ttl              # Sample data
│   └── application.properties
├── src/test/java/.../
│   ├── SPARQLPerformanceTest.java
│   ├── GitSyncIntegrationTest.java
│   └── EndToEndIntegrationTest.java
└── docs/
    ├── API.md                        # REST + GraphQL endpoints
    ├── ONTOLOGY.md                   # Class & property reference
    ├── QUERIES.md                    # Example SPARQL queries
    └── DEPLOYMENT.md                 # Docker + Kubernetes setup
```

---

## Billing Model (Simplified)

```
┌─────────────────────────────────────────────────────────┐
│ PRICING TIERS                                           │
├─────────────┬──────────┬───────────┬────────────────────┤
│ Tier        │ Cost     │ Skills    │ Integrations       │
├─────────────┼──────────┼───────────┼────────────────────┤
│ Free        │ $0       │ 100 calls │ 10 calls/day       │
│ Pro         │ $99/mo   │ Unlimited │ 10K calls/day      │
│ Business    │ $999/mo  │ Unlimited │ Unlimited          │
│ Enterprise  │ Custom   │ All       │ All (custom SLA)   │
└─────────────┴──────────┴───────────┴────────────────────┘

Per-Usage Cost (on top of tier):
├─ Skill call: $0.001
├─ Integration call: $0.001
├─ Agent call: $0.05
└─ Data (per GB): $0.10
```

---

## Getting Started (Developer)

### 1. Clone & Setup
```bash
cd /home/user/yawl
mvn clean install -pl yawl-marketplace -DskipTests
cd yawl-marketplace
```

### 2. Start Services (Docker)
```bash
docker-compose up -d
# Jena Fuseki: http://localhost:3030
# PostgreSQL: localhost:5432
# API: http://localhost:8080
```

### 3. Load Sample Data
```bash
curl -X POST http://localhost:8080/api/marketplace/load-sample-data

# Verify RDF graph populated
curl http://localhost:3030/federation/sparql?query=SELECT%20(COUNT(*) AS %3Fcount)%20WHERE%20%7B%3Fs%20?p%20?o%7D
```

### 4. Run Discovery Query
```bash
curl http://localhost:8080/api/skills?q=approval
# Returns: [SkillDTO, SkillDTO, ...]
```

### 5. Run Tests
```bash
mvn test -pl yawl-marketplace
mvn verify -pl yawl-marketplace  # Full integration tests
```

---

## CI/CD Pipeline

**File**: `.github/workflows/marketplace-mvp.yml`

```yaml
name: Marketplace MVP

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'

      - name: Build
        run: mvn clean install -pl yawl-marketplace

      - name: Unit Tests
        run: mvn test -pl yawl-marketplace

      - name: Integration Tests
        run: mvn verify -pl yawl-marketplace

      - name: SPARQL Performance Check
        run: mvn -pl yawl-marketplace \
             -Dtest=SPARQLPerformanceTest test

      - name: CodeQuality (SpotBugs)
        run: mvn spotbugs:check -pl yawl-marketplace

      - name: Upload Coverage
        uses: codecov/codecov-action@v3
```

---

## Integration Checkpoints

| Date | Checkpoint | Owner | Approval |
|------|-----------|-------|----------|
| **W1 EOD (Fri)** | Ontology complete, Jena working | RDF expert | Arch lead |
| **W2 EOD (Fri)** | 15 queries <500ms, API responds | Platform lead | QA |
| **W3 EOD (Fri)** | Git sync 100%, audit trail immutable | Platform lead | Compliance |
| **W4 EOD (Fri)** | Full E2E test, 1000+ entities, billing works | All | Product |

---

## Common Issues & Fixes

| Problem | Root Cause | Fix |
|---------|-----------|-----|
| **SPARQL query slow (>1s)** | Missing index on RDF triples | Run `jena admin:index-all`, restart |
| **Git sync hangs** | JGit deadlock on large files | Break Turtle into smaller files by entity type |
| **Jena out of memory** | RDF graph >2GB | Increase heap: `export JVM_ARGS="-Xmx8g"` |
| **Postgres connection pool full** | Too many concurrent requests | Increase pool size in `application.properties` |
| **Billing calculation wrong** | Usage event not recorded | Check `marketplace_usage_events` table, verify timestamp |

---

## Success Metrics (Week 4)

Run these commands to verify MVP success:

```bash
# 1. Count RDF entities
curl http://localhost:3030/federation/sparql?query=SELECT+(COUNT(*)%20AS%20%3Fcount)%20WHERE+%7B%3Fs+rdf:type+%3Fo%7D
# Expected: ~1000

# 2. Test SPARQL latency (run 100x)
for i in {1..100}; do
  time curl http://localhost:8080/api/skills?q=approval
done | grep real | sort -n | tail -5
# Expected p95: <500ms

# 3. Verify Git history
git -C yawl-marketplace log --oneline | wc -l
# Expected: ≥1000 commits (one per entity change)

# 4. Test billing
curl http://localhost:8080/api/billing/charge \
  -d '{"organization": "acme", "month": "2026-02"}'
# Expected: { "totalCharge": 42.50, "breakdown": {...} }

# 5. Run full test suite
mvn verify -pl yawl-marketplace
# Expected: All tests pass, >95% coverage
```

---

## Documentation Map

| Document | Purpose | Owner |
|----------|---------|-------|
| **MARKETPLACE-MVP-IMPLEMENTATION.md** | Full technical design (this repo) | Architect |
| **API.md** | REST + GraphQL endpoint reference | Backend eng |
| **ONTOLOGY.md** | RDF class/property definitions | RDF expert |
| **QUERIES.md** | Example SPARQL queries (copy-paste) | Data eng |
| **DEPLOYMENT.md** | Production setup (K8s, multi-cloud) | DevOps |

---

## Next Phase (After MVP)

Once MVP launches (Week 5+):

1. **Scaling** (Weeks 5-8)
   - Add 5-10 more SPARQL queries
   - GraphQL API layer
   - Full-text search (Elasticsearch)

2. **Monetization** (Weeks 9-12)
   - Stripe integration
   - Invoice + billing dashboard
   - Advanced analytics

3. **Federation** (Weeks 13-20)
   - Decentralized registry (Git federation)
   - Multi-tenant support
   - SHACL validation

---

## Key Contacts

| Role | Name | Slack | Email |
|------|------|-------|-------|
| **Architect** | TBD | @arch-lead | arch@yawlfoundation.org |
| **Platform Lead** | TBD | @platform | platform@yawlfoundation.org |
| **RDF Expert** | TBD | @rdf-eng | rdf@yawlfoundation.org |
| **Billing Eng** | TBD | @billing | billing@yawlfoundation.org |

---

## Quick Links

- [Full Implementation Design](./MARKETPLACE-MVP-IMPLEMENTATION.md)
- [GREGVERSE Architecture](./GREGVERSE-ARCHITECTURE.md)
- [Technical Specs](./GREGVERSE-TECHNICAL-SPECS.md)
- [Jena Documentation](https://jena.apache.org/documentation/)
- [SPARQL 1.1 Spec](https://www.w3.org/TR/sparql11-query/)

---

**Last Updated**: 2026-02-21
**Status**: Ready for Engineering Team
**Next Review**: 2026-02-28 (EOW1 checkpoint)
