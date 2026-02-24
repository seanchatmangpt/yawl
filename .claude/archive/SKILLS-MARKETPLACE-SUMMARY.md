# Skills Marketplace MVP — Design Summary & Handoff

**Date**: 2026-02-21
**Status**: Design Complete, Ready for Implementation
**Effort**: 4 weeks, 2 engineers, $50K
**Success Metric**: 1,000 skills indexed, 95% search accuracy, <500ms query latency

---

## Quick Start: Three Questions

### 1. What are we building?

**Skills Marketplace MVP**: A Git-backed, semantic-search-enabled registry where organizations can publish YAWL workflows as reusable "skills", discover compatible skills, and bundle them into vertical domain packs (e.g., real-estate-acquisition, financial-settlement, hr-onboarding).

**Key Innovation**: Leverage existing YAWL infrastructure (ontology, A2A protocol, YStatelessEngine) to avoid greenfield development. Focus on the critical 20% (publish, discover, versioning, packs) that creates 80% of value.

### 2. How does it work?

**Workflow**:

```
1. PUBLISH (Operator publishes YAWL workflow as skill)
   ├─ Upload YAWL XML + metadata (YAML)
   ├─ API: POST /api/v1/skills/publish
   ├─ Backend extracts inputs/outputs from YAWL
   ├─ Commit to Git (.skills/skills/approval/v1.0/)
   ├─ Generate RDF + SHACL validation
   └─ Response: 201 Created {skillId, version, gitUrl}

2. DISCOVER (Agent searches for skills)
   ├─ API: GET /api/v1/skills/search?capability=*
   ├─ Execute SPARQL query against RDF store
   ├─ Return ranked results <500ms
   └─ Response: [{skillId, version, relevance}, ...]

3. INVOKE (Agent calls discovered skill)
   ├─ Use A2A protocol: POST /skills/{id}/v{ver}/execute
   ├─ Route to YStatelessEngine for stateless execution
   ├─ Return results
   └─ Response: {status, outputs}

4. PACK (Curator groups skills into vertical bundles)
   ├─ Define SkillPack (YAML): includes 5-10 related skills
   ├─ Resolve dependencies (topological sort)
   ├─ Validate via SHACL
   └─ Publish to .skills/packs/

5. MANAGE (Monitor skill versions)
   ├─ Semantic versioning (1.0.0, 1.1.0, 2.0.0)
   ├─ Automatic breaking change detection
   ├─ Notify orgs of incompatibilities
   └─ Support version pinning in dependencies
```

### 3. Why this design?

**80/20 Principle**: MVP focuses on core infrastructure (registry, discovery, versioning) while deferring non-essential features (UI, reputation, marketplace fees, analytics).

**Technology Reuse**:
- RDF ontology (extend existing yawl-ontology.ttl)
- SPARQL discovery (use existing Jena endpoint)
- A2A protocol (YawlA2AServer.java)
- YStatelessEngine (stateless skill execution)
- Maven + JUnit 5 (existing CI/CD)

**Result**: 25 engineer-days to GA instead of 100+ for greenfield platform.

---

## Deliverables (4 Documents)

### 1. **SKILLS-MARKETPLACE-MVP-DESIGN.md** (Main)
   - **File**: `/home/user/yawl/.claude/SKILLS-MARKETPLACE-MVP-DESIGN.md`
   - **Length**: ~800 lines
   - **Contents**:
     - Skill Registry Schema (directory structure, YAML, RDF)
     - Core MVP Scope (what's in, what's out)
     - Tech Stack & Integration
     - 4-Week Implementation Roadmap (Week 1-4 deliverables)
     - Code Architecture & Patterns
     - Testing Strategy (80%+ coverage)
     - Success Criteria & Metrics
     - Risk Assessment
   - **Use Case**: Lead architect reads this to understand full scope

### 2. **SKILLS-MARKETPLACE-UML-DIAGRAMS.md** (Architecture)
   - **File**: `/home/user/yawl/.claude/SKILLS-MARKETPLACE-UML-DIAGRAMS.md`
   - **Length**: ~600 lines
   - **Contents**:
     - Core Model Class Diagram (SkillMetadata, Registry, Pack, etc.)
     - Registry & Repository Pattern
     - Publish Workflow Sequence Diagram
     - Discovery Workflow (SPARQL)
     - Pack Creation & Dependency Resolution
     - Search & Ranking Sequence
     - Semantic Versioning & Breaking Changes
     - A2A Integration Pattern
     - Pack Dependency Graph
     - Test Architecture
   - **Use Case**: Developers reference these diagrams during implementation

### 3. **SKILLS-MARKETPLACE-SPARQL-ONTOLOGY.md** (Discovery)
   - **File**: `/home/user/yawl/.claude/SKILLS-MARKETPLACE-SPARQL-ONTOLOGY.md`
   - **Length**: ~700 lines
   - **Contents**:
     - RDF Ontology (skill-ontology.ttl: classes, properties, Dublin Core)
     - SHACL Validation Shapes (skill-shapes.ttl: constraints)
     - 5 Core SPARQL Queries (capability, domain, incompatibility, circular deps, version resolution)
     - Jena Fuseki Configuration
     - Query Performance Tuning
     - Testing SPARQL Queries (fixtures, JUnit)
   - **Use Case**: Week 3 implementer builds discovery engine from this

### 4. **This Summary** (Handoff)
   - **File**: `/home/user/yawl/.claude/SKILLS-MARKETPLACE-SUMMARY.md`
   - **Contents**: High-level overview + references

---

## Week-by-Week Breakdown

### Week 1: Skill Schema & Metadata Model

**Goal**: Define data structures + RDF ontology

**Files to Create**:
```
src/org/yawlfoundation/yawl/integration/a2a/skills/
├─ model/
│  ├─ SkillMetadata.java
│  ├─ SkillInput.java
│  ├─ SkillOutput.java
│  ├─ SkillSLA.java
│  ├─ SkillVersion.java
│  └─ SkillPack.java
└─ manifest/
   └─ SkillManifestParser.java (YAML deserialization)

.skills/ontology/
├─ skill-ontology.ttl
├─ skill-shapes.ttl
└─ pack-ontology.ttl
```

**Testing**: Unit tests for YAML parsing, model validation (10+ test cases)

**Deliverable**: UML class diagram (see SKILLS-MARKETPLACE-UML-DIAGRAMS.md Part 1)

**Success Criteria**:
- All models serialize/deserialize correctly
- RDF ontology validates via SHACL
- UML diagram complete

---

### Week 2: Publish API & Git Backend

**Goal**: Enable skill publishing to Git-backed registry

**Files to Create**:
```
src/org/yawlfoundation/yawl/integration/a2a/skills/
├─ api/
│  └─ SkillPublishController.java
├─ repository/
│  ├─ SkillRepository.java (interface)
│  └─ GitSkillRepository.java (implementation)
├─ versioning/
│  ├─ SemanticVersioning.java
│  └─ BreakingChangeDetector.java
└─ service/
   └─ SkillPublishService.java

test/
├─ SkillPublishIntegrationTest.java
├─ SemanticVersioningTest.java
└─ GitRepositoryTest.java
```

**API Endpoint**:
```
POST /api/v1/skills/publish
{
  "yawlXml": "<yawl>...</yawl>",
  "metadata": {
    "name": "Approval Workflow",
    "version": "1.0.0",
    ...
  }
}

Response 201: {skillId, version, gitUrl}
```

**Testing**: Parse YAWL → extract I/O, commit to Git, detect breaking changes

**Success Criteria**:
- Publish API accepts YAWL + metadata
- Git commits are immutable
- Breaking change detection works on 5 test cases

---

### Week 3: Discovery Engine (SPARQL)

**Goal**: Implement semantic search via SPARQL

**Files to Create**:
```
src/org/yawlfoundation/yawl/integration/a2a/skills/
├─ discovery/
│  ├─ SkillDiscoveryEngine.java
│  ├─ SkillSearchQuery.java
│  └─ SkillSearchResult.java
├─ api/
│  └─ SkillDiscoveryController.java
└─ sparql/
   ├─ queries/
   │  ├─ SkillsByCapability.sparql
   │  ├─ SkillsByDomain.sparql
   │  ├─ IncompatibleVersions.sparql
   │  ├─ CircularDependencies.sparql
   │  └─ ResolveVersionConstraint.sparql
   └─ SkillSparqlEndpoint.java

test/
├─ SkillDiscoveryTest.java
├─ SkillSearchPerformanceTest.java
└─ SparqlQueryTest.java
```

**REST API**:
```
GET /api/v1/skills/search?capability=approval&version=>=1.0.0
GET /api/v1/skills/search?domain=real-estate
GET /api/v1/skills/{id}/versions
```

**Testing**: 50 sample skills, verify accuracy + latency (<500ms)

**Success Criteria**:
- Search finds 95%+ relevant skills
- Query latency <500ms
- Results ranked by relevance

---

### Week 4: Pack Grouping & Integration Tests

**Goal**: Create vertical packs + full integration tests

**Files to Create**:
```
src/org/yawlfoundation/yawl/integration/a2a/skills/
├─ pack/
│  ├─ SkillPackService.java
│  └─ SkillPackValidator.java
└─ api/
   └─ SkillPackController.java

test/
├─ SkillPackIntegrationTest.java
└─ SkillPublishFullCycleTest.java

.skills/skills/                # 10 sample skills
├─ approval/v1.0/
├─ po-creation/v1.0/
├─ expense-report/v1.0/
├─ property-search/v1.0/
├─ property-valuation/v1.0/
├─ financing-approval/v1.0/
├─ inspection-scheduling/v1.0/
├─ title-search/v1.0/
├─ offer-submission/v1.0/
└─ closing-coordination/v1.0/

.skills/packs/                 # 3 vertical packs
├─ real-estate-acquisition-v1.0.yaml
├─ financial-settlement-v1.0.yaml
└─ hr-onboarding-v1.0.yaml

docs/
└─ SKILLS_MARKETPLACE_USER_GUIDE.md
```

**REST API**:
```
GET /api/v1/packs
GET /api/v1/packs/{id}/skills
POST /api/v1/packs
```

**Testing**:
- Publish 10 sample skills
- Create 3 packs + resolve dependencies
- 80%+ code coverage
- End-to-end integration test

**Success Criteria**:
- 10 sample skills published
- 3 vertical packs created
- Search finds all skills + packs
- 80%+ code coverage
- Full integration test suite passes

---

## Code Footprint

**Total New Code**: ~6,000 lines

```
src/main/java/
├─ skills/model/        900 lines (POJO classes)
├─ skills/manifest/     300 lines (YAML parsing)
├─ skills/repository/   500 lines (Git backend)
├─ skills/api/          600 lines (Spring REST)
├─ skills/discovery/    700 lines (SPARQL engine)
├─ skills/pack/         400 lines (pack grouping)
└─ skills/util/         200 lines
Total: ~3,600 lines

src/test/java/
├─ model/               400 lines
├─ *IntegrationTest.java   1,500 lines
├─ *PerformanceTest.java   400 lines
Total: ~2,300 lines

.skills/
├─ ontology/            300 lines (TTL)
├─ skills/ × 10         2,000 lines (YAML + TTL)
├─ packs/ × 3           450 lines (YAML)
Total: ~2,750 lines

docs/
├─ README.md
├─ API_REFERENCE.md
└─ USER_GUIDE.md
```

---

## Integration Points (80% Reuse)

| Component | File | Status |
|-----------|------|--------|
| **RDF Ontology** | `/yawl/.specify/yawl-ontology.ttl` | Extend (1 day) |
| **SHACL Shapes** | `/yawl/.specify/yawl-shapes.ttl` | Extend (1 day) |
| **SPARQL Endpoint** | Existing Apache Jena | Reuse (0 days) |
| **A2A Protocol** | `YawlA2AServer.java` | Reuse (0 days) |
| **YStatelessEngine** | `YStatelessEngine.java` | Reuse (0 days) |
| **Build System** | Maven + JUnit 5 | Reuse (0 days) |
| **CI/CD** | `scripts/dx.sh` | Reuse (0 days) |

**Result**: Minimal new infrastructure, maximum leverage of existing YAWL ecosystem.

---

## Success Criteria (Week 4 Validation)

### Functional

| Criterion | Target | Validation |
|-----------|--------|---|
| Publish API | Accepts YAWL + metadata → Git commit | POST /api/v1/skills/publish → 201 |
| Discovery | Find skills by inputs/outputs | GET /api/v1/skills/search → results |
| Versioning | Semantic versioning (1.0.0 → 1.1.0 → 2.0.0) | SemanticVersioningTest passes |
| Breaking changes | Detect removed/changed required fields | BreakingChangeDetectorTest → 100% |
| Packs | Bundle 5-10 related skills | 3 vertical packs created |
| Sample skills | 10 real-world examples | All pass SHACL validation |

### Performance

| Metric | Target | Validation |
|--------|--------|---|
| Search latency | <500ms per query | SkillSearchPerformanceTest |
| Search accuracy | 95%+ relevant results | Manual verification |
| Skill scalability | 1,000 skills indexed | Benchmark: 1000 skills in RDF |
| Publish latency | <2 seconds | GitRepository + SHACL validation |
| Discovery throughput | >100 searches/sec | Load test |

### Code Quality

| Criterion | Target | Tool |
|-----------|--------|---|
| Test coverage | 80%+ on core | JaCoCo |
| Static analysis | 0 critical violations | SpotBugs, CheckStyle |
| Documentation | 100% of public APIs | JavaDoc |
| Integration tests | 20+ test cases | JUnit 5 |

---

## Post-MVP Roadmap (v1.1, v2.0)

### v1.1 (Month 5-6)

- [ ] MCP integration (Claude LLM calls skills as tools)
- [ ] Autonomous recommendation (ML model suggests compatible skills)
- [ ] Custom search facets (advanced filtering)
- [ ] API key management (JWT + OAuth2)
- [ ] Skill rating system (user reviews)

### v2.0 (Month 7-12)

- [ ] Marketplace UI (GitHub-style interface)
- [ ] Payment system (subscription, per-invocation)
- [ ] Advanced analytics (usage stats, trending skills)
- [ ] Reputation system (org scoring)
- [ ] Multi-tenant SaaS (yawl.cloud/marketplace)

---

## Getting Started (Next Steps)

1. **Approval**: Review this design with architects + tech leads
2. **Kickoff**: Assign 2 engineers, create Week 1 sprints
3. **Week 1**: Build SkillMetadata model + RDF ontology (validate with UML diagram)
4. **Week 2**: Implement publish API + Git backend (test with 5 sample skills)
5. **Week 3**: Build discovery engine (test performance: <500ms on 100 skills)
6. **Week 4**: Create packs + integration tests (validate end-to-end)
7. **GA**: Tag v1.0.0, create Docker image, announce to community

---

## Reference Documents

| Document | Purpose | Audience |
|----------|---------|----------|
| **SKILLS-MARKETPLACE-MVP-DESIGN.md** | Complete implementation spec | Architects, lead engineers |
| **SKILLS-MARKETPLACE-UML-DIAGRAMS.md** | Class structures + workflows | Developers |
| **SKILLS-MARKETPLACE-SPARQL-ONTOLOGY.md** | RDF + discovery engine | Backend engineer (Week 3) |
| **SKILLS-MARKETPLACE-SUMMARY.md** (this) | Executive summary | Project leads, team |

---

## Questions & Contact

**Q: Why not use existing marketplaces (npm, PyPI)?**
- A: Those model are package-centric. Skills are workflow-centric (YAWL semantics). We need RDF for semantic search + A2A for cross-org invocation. Off-the-shelf doesn't apply.

**Q: What about reputation/ratings?**
- A: Post-MVP. Core MVP is just discovery + versioning. Ratings can be added in v1.1.

**Q: How do we scale to 10,000 skills?**
- A: SPARQL performance is <500ms at 1,000 skills. At 10K, we'll optimize queries + add caching + consider partitioning RDF store. Design supports scaling.

**Q: Can we run this on-prem vs SaaS?**
- A: Yes. Skills are Git repos (can be private). SPARQL endpoint is self-hosted. Only UI is SaaS (post-MVP).

---

**Status**: READY FOR IMPLEMENTATION
**Target Launch**: 4 weeks from start
**Expected GA**: Month 5 of 2026

