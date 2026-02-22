# YAWL Skills Marketplace — MVP Implementation Design

**Date**: 2026-02-21
**Status**: Design Complete, Ready for Implementation
**Version**: 1.0.0

---

## Overview

This directory contains the complete design specification for the YAWL Skills Marketplace MVP — a Git-backed, semantic-search-enabled registry where organizations can publish YAWL workflows as reusable "skills", discover compatible skills, and bundle them into vertical domain packs.

**Key Metrics**:
- **Timeline**: 4 weeks
- **Team**: 2 engineers
- **Cost**: $50K
- **Success Criteria**: 1,000 skills indexed, 95% search accuracy, <500ms query latency

---

## Quick Navigation

### For Everyone
1. Start with: **[.claude/SKILLS-MARKETPLACE-SUMMARY.md](.claude/SKILLS-MARKETPLACE-SUMMARY.md)** (15 min read)
   - Three questions: What? How? Why?
   - Week-by-week implementation roadmap
   - Integration points with existing YAWL infrastructure

### For Project Leads
1. Read: **[.claude/SKILLS-MARKETPLACE-INDEX.md](.claude/SKILLS-MARKETPLACE-INDEX.md)** (document map)
2. Then: **[.claude/SKILLS-MARKETPLACE-MVP-DESIGN.md](.claude/SKILLS-MARKETPLACE-MVP-DESIGN.md)** Part 2 (scope + timeline)

### For Architects
1. Read: **[.claude/SKILLS-MARKETPLACE-MVP-DESIGN.md](.claude/SKILLS-MARKETPLACE-MVP-DESIGN.md)** (complete specification)
2. Study: **[.claude/SKILLS-MARKETPLACE-UML-DIAGRAMS.md](.claude/SKILLS-MARKETPLACE-UML-DIAGRAMS.md)** (architecture diagrams)

### For Week 1 Engineer
1. Read: **[.claude/SKILLS-MARKETPLACE-UML-DIAGRAMS.md](.claude/SKILLS-MARKETPLACE-UML-DIAGRAMS.md)** Part 1 (class diagram)
2. Implement: SkillMetadata model, RDF ontology, SHACL shapes

### For Week 3 Engineer (Discovery)
1. Read: **[.claude/SKILLS-MARKETPLACE-SPARQL-ONTOLOGY.md](.claude/SKILLS-MARKETPLACE-SPARQL-ONTOLOGY.md)** (complete)
2. Implement: SPARQL queries, SkillDiscoveryEngine, performance tuning

---

## The Five Design Documents

| # | Document | Purpose | Audience |
|---|----------|---------|----------|
| 1 | **SKILLS-MARKETPLACE-INDEX.md** | Document map + quick reference | Everyone |
| 2 | **SKILLS-MARKETPLACE-SUMMARY.md** | Executive summary | Leads, stakeholders |
| 3 | **SKILLS-MARKETPLACE-MVP-DESIGN.md** | Complete implementation spec | Architects, engineers |
| 4 | **SKILLS-MARKETPLACE-UML-DIAGRAMS.md** | Visual architecture | Developers |
| 5 | **SKILLS-MARKETPLACE-SPARQL-ONTOLOGY.md** | Discovery engine + RDF | Backend engineer (Week 3) |

**Total**: 4,000+ lines, ~145 KB documentation

---

## What's Being Built?

### Publish Phase
```
Organization publishes YAWL workflow as skill
├─ POST /api/v1/skills/publish {yawl, metadata}
├─ Extract inputs/outputs from YAWL
├─ Commit to Git (.skills/skills/approval/v1.0/)
└─ Response: {skillId, version, gitUrl}
```

### Discover Phase
```
Agent discovers compatible skills
├─ GET /api/v1/skills/search?capability=*
├─ Execute SPARQL against RDF store
└─ Response: [{skillId, version, relevance}, ...]
```

### Invoke Phase
```
Agent invokes discovered skill
├─ POST /skills/{id}/v{version}/execute
├─ Route to YStatelessEngine
└─ Response: {status, outputs}
```

### Pack Phase
```
Curator groups skills into vertical bundles
├─ Define SkillPack (YAML): includes 5-10 skills
├─ Resolve dependencies + validate
└─ Publish to .skills/packs/
```

---

## 4-Week Implementation Plan

### Week 1: Metadata Model + Ontology
- Create SkillMetadata POJO classes (~900 lines)
- Create RDF ontology (skill-ontology.ttl)
- Create SHACL validation shapes (skill-shapes.ttl)
- Unit tests (10+ test cases)
- **Commit**: "feat: add skill metadata model + RDF ontology"

### Week 2: Publish API + Git Backend
- SkillPublishController (REST API)
- GitSkillRepository (Git storage)
- SemanticVersioning logic
- BreakingChangeDetector (SPARQL-based)
- Integration tests
- **Commit**: "feat: add skill publish API + git backend"

### Week 3: Discovery Engine
- SkillDiscoveryEngine (SPARQL executor)
- 5 core SPARQL queries
- SkillDiscoveryController (REST API)
- Performance tests (<500ms latency)
- Load test: 1,000 skills
- **Commit**: "feat: add skill discovery engine"

### Week 4: Packs + Integration Tests
- SkillPackService (pack grouping)
- 10 sample skills
- 3 vertical packs (Real Estate, Finance, HR)
- Full end-to-end integration tests (80% coverage)
- User guide + API reference
- **Commit**: "feat: add skill packs + integration tests"

---

## Success Criteria

### Functional
- Publish API accepts YAWL + metadata ✓
- Discovery finds skills by capability ✓
- Semantic versioning (1.0.0, 1.1.0, 2.0.0) ✓
- Breaking change detection ✓
- Pack creation + dependency resolution ✓
- 10 sample skills + 3 vertical packs ✓

### Performance
- Search latency: <500ms per query ✓
- Search accuracy: 95%+ relevant ✓
- Skill scalability: 1,000 skills indexed ✓
- Publish latency: <2 seconds ✓
- Throughput: >100 searches/sec ✓

### Quality
- Test coverage: 80%+ on core modules ✓
- Static analysis: 0 critical violations ✓
- Documentation: 100% public APIs ✓

---

## Technology Stack (80% Reuse)

| Layer | Technology | YAWL Integration |
|-------|-----------|---|
| **Registry** | Git + RDF | Leverage existing `.specify/` pattern |
| **Ontology** | RDF/OWL 2 DL | Extend yawl-ontology.ttl |
| **Validation** | SHACL 2024 | Extend yawl-shapes.ttl |
| **Discovery** | SPARQL 1.1 + Jena | Use existing endpoint |
| **Skill format** | YAML manifest | New (lightweight) |
| **API** | Spring Boot REST | Integrate with YawlA2AServer |
| **Execution** | A2A protocol | YStatelessEngine |
| **Build** | Maven + JUnit 5 | Existing CI/CD |

---

## File Structure (After Implementation)

```
src/org/yawlfoundation/yawl/integration/a2a/skills/
├─ model/                      (SkillMetadata, Input, Output, SLA, Pack)
├─ manifest/                   (YAML parser)
├─ api/                        (REST controllers)
├─ repository/                 (Git backend)
├─ discovery/                  (SPARQL engine)
├─ pack/                       (pack grouping + validation)
├─ service/                    (business logic)
└─ util/                       (helpers)

.skills/
├─ ontology/                   (RDF definitions)
│  ├─ skill-ontology.ttl
│  ├─ skill-shapes.ttl
│  └─ pack-ontology.ttl
│
├─ skills/                     (published skills, immutable)
│  ├─ approval/v1.0/
│  │  ├─ skill.yaml
│  │  ├─ skill.ttl
│  │  ├─ workflow.yawl
│  │  └─ ...
│  ├─ po-creation/v1.0/
│  ├─ expense-report/v1.0/
│  └─ ... (10 total)
│
└─ packs/                      (vertical bundles)
   ├─ real-estate-acquisition-v1.0.yaml
   ├─ financial-settlement-v1.0.yaml
   └─ hr-onboarding-v1.0.yaml

test/org/yawlfoundation/yawl/integration/a2a/skills/
├─ model/
├─ *IntegrationTest.java
├─ *PerformanceTest.java
└─ ... (20+ test cases)
```

---

## Starting Points

### If you're the lead architect:
1. Read SUMMARY.md (15 min)
2. Read MVP-DESIGN.md (1 hour)
3. Review UML-DIAGRAMS.md Part 1-3 (30 min)
4. Make technology decisions + assign engineers

### If you're engineer #1 (Week 1):
1. Read SUMMARY.md (15 min)
2. Study UML-DIAGRAMS.md Part 1 (class diagram)
3. Implement SkillMetadata + model classes
4. Create RDF ontology + SHACL shapes
5. Write unit tests

### If you're engineer #2 (Week 2):
1. Read SUMMARY.md (15 min)
2. Study UML-DIAGRAMS.md Part 2-3 (publish workflow)
3. Implement SkillPublishController
4. Create GitSkillRepository
5. Implement SemanticVersioning + BreakingChangeDetector

### If you're engineer #1 (Week 3 - Discovery):
1. Read SPARQL-ONTOLOGY.md (1 hour)
2. Study UML-DIAGRAMS.md Part 4 (discovery workflow)
3. Implement SkillDiscoveryEngine
4. Create 5 SPARQL queries
5. Performance test + optimize

### If you're engineer #2 (Week 4 - Packs):
1. Study UML-DIAGRAMS.md Part 5-6 (pack creation)
2. Implement SkillPackService + SkillPackValidator
3. Create 10 sample skills
4. Create 3 vertical packs
5. End-to-end integration tests

---

## Key Metrics & Monitoring

### Build Quality
```bash
bash scripts/dx.sh compile               # Fastest (changed only)
bash scripts/dx.sh -pl yawl-engine       # Single module
bash scripts/dx.sh all                   # Full build (pre-commit)
mvn clean verify -P analysis             # Static analysis
```

### Test Coverage
```bash
# Target: 80%+ on core modules
# Tools: JaCoCo (Java), SpotBugs, CheckStyle
```

### Performance Validation
```
Search latency:   <500ms (p99)
Throughput:       >100 queries/sec
Skill scale:      1000+ in RDF store
Memory:           <1GB for 10K skills
```

---

## References

All documentation located in: `/home/user/yawl/.claude/`

1. **SKILLS-MARKETPLACE-INDEX.md** — Start here (document map)
2. **SKILLS-MARKETPLACE-SUMMARY.md** — Quick overview (15 min)
3. **SKILLS-MARKETPLACE-MVP-DESIGN.md** — Full specification (1 hour)
4. **SKILLS-MARKETPLACE-UML-DIAGRAMS.md** — Architecture diagrams (30 min)
5. **SKILLS-MARKETPLACE-SPARQL-ONTOLOGY.md** — Discovery engine (1 hour)

---

## Timeline

```
Week 1 (Feb 24 - Mar 2)     Metadata model + RDF ontology
Week 2 (Mar 3 - Mar 9)      Publish API + Git backend
Week 3 (Mar 10 - Mar 16)    Discovery engine + SPARQL
Week 4 (Mar 17 - Mar 23)    Packs + integration tests
GA (Mar 25)                 v1.0.0 release
```

---

## Next Steps

1. **Review** this README + SKILLS-MARKETPLACE-SUMMARY.md (30 min)
2. **Approve** design + assign engineers
3. **Kickoff** Week 1 with full team
4. **Execute** 4-week sprint using detailed implementation guides
5. **Deploy** to production (GA) with full test coverage

---

**Status**: READY FOR IMPLEMENTATION
**Questions?**: See SKILLS-MARKETPLACE-INDEX.md for detailed Q&A section

---

**Author**: Architecture Specialist
**Session**: Claude Code + Haiku 4.5
**YAWL Target**: v6.0.0

