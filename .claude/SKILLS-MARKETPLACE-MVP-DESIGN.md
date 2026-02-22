# Skills Marketplace MVP Implementation Design
## 4-Week Implementation Plan (80/20 Principle)

**Version**: 1.0
**Date**: 2026-02-21
**Status**: Ready for Implementation
**Effort**: 4 weeks, 2 engineers, $50K cost
**Success Metric**: 1,000 skills indexed, 95% search accuracy, <500ms query latency

---

## Executive Summary

Transform YAWL workflows into reusable "skills" and create a Git-backed marketplace enabling organizations to discover, publish, and bundle workflows into vertical domain packs. This MVP focuses on the critical 20% that creates 80% of value: skill publishing, versioning, discovery, and packs.

**Key Innovation**: Leverage existing YAWL ontology + RDF infrastructure to avoid greenfield development. Reuse A2A protocol for skill invocation. Build on current YStatelessEngine for stateless skill execution.

**MVP Scope**:
- Skill publish API (YAWL XML → skill metadata)
- Skill discovery via SPARQL
- Vertical packs (5-10 related skills)
- Basic semantic versioning + breaking change detection
- NO: Reputation system, UI, analytics, marketplace fees

---

## Part 1: Skill Registry Schema

### 1.1 Directory Structure (Git-backed)

```
.skills/                                    # Root registry
├─ ontology/                               # RDF ontology definitions
│  ├─ skill-ontology.ttl                   # Core skill classes
│  ├─ skill-shapes.ttl                     # SHACL validation shapes
│  └─ pack-ontology.ttl                    # Vertical pack definitions
│
├─ skills/                                 # Published skills (immutable)
│  ├─ approval/                            # Domain folder
│  │  ├─ v1.0/
│  │  │  ├─ skill.yaml                     # Metadata manifest
│  │  │  ├─ skill.ttl                      # RDF definition
│  │  │  ├─ workflow.yawl                  # YAWL workflow (reference)
│  │  │  ├─ inputs.json                    # Example input schema
│  │  │  ├─ outputs.json                   # Example output schema
│  │  │  └─ sla.json                       # SLA contract
│  │  └─ v1.1/                             # Patch: backward-compatible
│  │
│  ├─ po-creation/
│  │  ├─ v1.0/
│  │  │  ├─ skill.yaml
│  │  │  ├─ skill.ttl
│  │  │  └─ ...
│  │  └─ v1.1/
│  │
│  └─ expense-report/
│     ├─ v1.0/
│     └─ v2.0/                             # Major: breaking change
│
├─ packs/                                  # Vertical packs
│  ├─ real-estate-acquisition-v1.0.yaml
│  ├─ financial-settlement-v1.0.yaml
│  └─ hr-onboarding-v1.0.yaml
│
├─ registrations/                          # Metadata index (generated)
│  └─ skill-index.json                     # All skills + versions
│
└─ CHANGELOG.md                            # Registry version history
```

**Immutability Strategy**: Once published, skills are read-only. New versions create new folders (v1.0 → v1.1 → v2.0).

### 1.2 Skill Manifest (YAML)

**File**: `.skills/skills/approval/v1.0/skill.yaml`

```yaml
apiVersion: gregverse.org/v1
kind: Skill
metadata:
  name: "Approval Workflow"
  namespace: "gregverse.workflow"
  id: "approval"
  version: "1.0.0"
  author: "AccelOrg"
  authoremail: "ops@accel.org"
  created: "2026-02-21T14:32:00Z"
  description: "Multi-level approval chain with escalation"
  tags: ["approval", "governance", "workflow-pattern"]

spec:
  # Workflow reference
  workflow:
    yawlFile: "workflow.yawl"
    yawlHash: "sha256:abc123..."  # Immutable reference
    pattern: "multiple_instance"

  # Input specification
  inputs:
    - name: "documentId"
      type: "uuid"
      required: true
      description: "Document to approve"
    - name: "approvers"
      type: "array<string>"
      required: true
      minItems: 1
      maxItems: 10
      description: "List of approver user IDs"
    - name: "deadline"
      type: "iso8601-duration"
      required: false
      default: "PT72H"
      description: "Approval deadline (ISO 8601)"

  # Output specification
  outputs:
    - name: "approvalStatus"
      type: "enum"
      enum: ["APPROVED", "REJECTED", "ESCALATED"]
      description: "Final approval decision"
    - name: "approvalTrace"
      type: "object"
      description: "Approval history (JSON array)"
    - name: "rejectionReason"
      type: "string"
      required: false
      description: "If rejected, reason why"

  # SLA contract
  sla:
    maxResponseTime: "PT72H"      # ISO 8601: 3 days
    minSuccessRate: "99.5%"
    maxErrorRate: "0.5%"
    supportedErrorCodes:
      - "ApprovalTimeout"
      - "ApproverUnavailable"
      - "InvalidDocument"

  # Versioning
  version:
    semantic: "1.0.0"
    breakingChange: false
    compatibility:
      - "0.9.0"  # Backward compatible with
    deprecationDate: null         # When v1.0 will be unsupported
    sunsetDate: null              # When v1.0 will be deleted

  # Interface export (for federation)
  interfaces:
    - type: "A2A"                 # Agent-to-Agent protocol
      endpoint: "/skills/approval/v1.0/execute"
      authRequired: true
      authScheme: "JWT"
    - type: "REST"
      endpoint: "/api/v1/skills/approval/execute"
      authRequired: true
      authScheme: "OAuth2"
    - type: "SPARQL"
      enabled: true

  # Licensing
  license:
    spdx: "MIT"
    url: "https://opensource.org/licenses/MIT"

  # Dependencies (other skills)
  dependencies:
    - skill: "notification-email"
      version: ">=1.0.0"
    - skill: "audit-log"
      version: ">=1.0.0"

  # Keywords for discovery
  keywords:
    - "governance"
    - "approval-chain"
    - "multi-level"
    - "pattern:multiple_instance"

status:
  # Populated by CI/CD
  published: true
  publishedAt: "2026-02-21T14:32:00Z"
  shaclValidation: "PASS"
  petriNetVerified: true
  testsPassed: true
  coverage: 92.5
```

### 1.3 RDF Definition (Turtle)

**File**: `.skills/skills/approval/v1.0/skill.ttl`

```turtle
@prefix : <http://gregverse.org/skill/approval/v1.0#> .
@prefix skill: <http://gregverse.org/skill#> .
@prefix proc: <http://gregverse.org/process#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix shacl: <http://www.w3.org/ns/shacl#> .

# ===== SKILL DEFINITION =====

:ApprovalSkill a skill:Skill ;
    dcterms:title "Approval Workflow" ;
    dcterms:description "Multi-level approval chain with escalation" ;
    dcterms:creator :AccelOrg ;
    dcterms:issued "2026-02-21"^^xsd:date ;
    dcterms:language "en" ;

    # Semantic version
    skill:versionNumber "1.0.0" ;
    skill:breakingChange false ;
    skill:previousVersion :ApprovalSkill_v0_9_0 ;

    # Skill type (Petri net pattern)
    skill:pattern <http://gregverse.org/patterns#multiple_instance> ;

    # Input schema
    skill:inputSchema [
        a shacl:NodeShape ;
        shacl:targetClass :ApprovalInput ;
        shacl:property [
            shacl:path :documentId ;
            shacl:datatype xsd:string ;
            shacl:minCount 1 ;
            shacl:maxCount 1 ;
        ] ;
        shacl:property [
            shacl:path :approvers ;
            shacl:minCount 1 ;
            shacl:maxCount 10 ;
        ] ;
        shacl:property [
            shacl:path :deadline ;
            shacl:datatype xsd:duration ;
            shacl:minCount 0 ;
        ]
    ] ;

    # Output schema
    skill:outputSchema [
        a shacl:NodeShape ;
        shacl:targetClass :ApprovalOutput ;
        shacl:property [
            shacl:path :approvalStatus ;
            shacl:in ( :APPROVED :REJECTED :ESCALATED ) ;
            shacl:minCount 1 ;
        ] ;
        shacl:property [
            shacl:path :approvalTrace ;
            shacl:minCount 0 ;
        ]
    ] ;

    # SLA
    skill:hasSLA [
        skill:maxResponseTime "PT72H" ;
        skill:minSuccessRate "99.5%" ;
        skill:supportedErrorCode "ApprovalTimeout" ;
        skill:supportedErrorCode "ApproverUnavailable" ;
    ] ;

    # Invocation interface
    skill:invocableVia :A2AInterface ;
    skill:invocableVia :RESTInterface ;

    # Ontology reference
    skill:implementedBy :ApprovalWorkflow ;

    # Keywords
    skill:keyword "governance" ;
    skill:keyword "approval-chain" ;
    skill:keyword "multiple_instance_pattern" .

# ===== WORKFLOW REFERENCE =====

:ApprovalWorkflow a skill:WorkflowImplementation ;
    dcterms:title "Approval Workflow Implementation" ;
    skill:yawlFile "workflow.yawl" ;
    skill:yawlContentHash "sha256:abc123def456..." ;
    skill:yawlValidated true ;
    skill:petriNetSound true ;
    skill:deadlockFree true .

# ===== INTERFACE: A2A =====

:A2AInterface a skill:Interface ;
    dcterms:title "A2A Protocol Interface" ;
    skill:protocol "A2A" ;
    skill:endpoint "/skills/approval/v1.0/execute" ;
    skill:authRequired true ;
    skill:authScheme "JWT" ;
    skill:authScopes ( "skill:execute" ) ;
    skill:rateLimit "1000 req/minute" ;
    skill:timeout "PT5M" .

# ===== INTERFACE: REST =====

:RESTInterface a skill:Interface ;
    dcterms:title "REST API Interface" ;
    skill:protocol "REST" ;
    skill:endpoint "/api/v1/skills/approval/execute" ;
    skill:httpMethod "POST" ;
    skill:contentType "application/json" ;
    skill:authRequired true ;
    skill:authScheme "OAuth2" ;
    skill:rateLimit "500 req/minute" .

# ===== DISCOVERABILITY =====

:ApprovalSkill skill:discoverableVia <http://gregverse-registry.org/sparql> ;
    skill:indexedIn :SkillRegistry_v1 .
```

### 1.4 Pack Definition (YAML)

**File**: `.skills/packs/real-estate-acquisition-v1.0.yaml`

```yaml
apiVersion: gregverse.org/v1
kind: SkillPack
metadata:
  name: "Real Estate Acquisition"
  version: "1.0.0"
  author: "RealEstateOrg"
  created: "2026-02-21T14:32:00Z"
  description: "Complete workflow for residential property acquisition"

spec:
  # Skills bundled in this pack
  includes:
    - skill: "property-search"
      version: ">=1.0.0"
      description: "Find properties matching criteria"
      order: 1

    - skill: "property-valuation"
      version: ">=1.0.0"
      description: "Professional property appraisal"
      order: 2

    - skill: "financing-approval"
      version: ">=1.0.0"
      description: "Mortgage pre-qualification"
      order: 3

    - skill: "inspection-scheduling"
      version: ">=1.0.0"
      description: "Home inspection booking"
      order: 4

    - skill: "title-search"
      version: ">=1.0.0"
      description: "Title research and clearance"
      order: 5

    - skill: "offer-submission"
      version: ">=1.0.0"
      description: "Submit and negotiate offers"
      order: 6

    - skill: "closing-coordination"
      version: ">=2.0.0"
      description: "Coordinate closing day"
      order: 7

  # Domain classification
  domain: "real-estate"
  industry: "property-management"
  use_case: "acquisition"

  # Typical execution time
  estimatedDuration: "PT60D"  # ~60 days to complete

  # Documentation
  documentation:
    quickstart: "https://docs.gregverse.org/packs/real-estate-acquisition"
    examples: "https://github.com/gregverse/examples/tree/main/real-estate"

  # Version history
  versions:
    - version: "1.0.0"
      releaseDate: "2026-02-21"
      breaking: false
    - version: "0.9.0"
      releaseDate: "2026-01-15"
      breaking: false

  # Licensing
  license: "MIT"
```

---

## Part 2: Core MVP Scope (80/20 Analysis)

### 2.1 What's IN (Critical 20%)

| Feature | Why 80% Value | Effort | Priority |
|---------|---|---|---|
| **Skill publish API** | Enable workflow → skill conversion | 3 days | P0 |
| **Git-backed storage** | Immutability + audit trail | 2 days | P0 |
| **YAML manifest format** | Human-readable skill metadata | 2 days | P0 |
| **SPARQL discovery** | Query skills by capability | 4 days | P0 |
| **Basic versioning** | Track skill versions (semantic) | 2 days | P0 |
| **Breaking change detection** | Notify orgs on incompatibility | 3 days | P0 |
| **Vertical packs** | Bundle 5-10 related skills | 2 days | P0 |
| **Sample skills (10)** | Real-world examples (approval, PO, expense) | 3 days | P0 |
| **Integration tests** | 80% coverage (critical paths) | 3 days | P0 |

**Total**: 25 engineer-days (12.5 days/engineer × 2 engineers) = fits 4-week sprint

### 2.2 What's OUT (Non-MVP)

These create 20% value but require 80% effort:

| Feature | Why Deferred | Reason |
|---------|---|---|
| **Reputation system** | Requires aggregation pipeline | Can add in v1.1 |
| **Marketplace UI** | Requires frontend + design | Can use GitHub for now |
| **Advanced analytics** | Requires BI pipeline | Can use git history |
| **Payment/licensing** | Enterprise feature | Post-MVP |
| **Autonomous recommendation** | Requires ML model | Future enhancement |
| **Custom search facets** | Nice-to-have filtering | SPARQL queries sufficient |
| **API key management** | Can use GitHub tokens | Future enhancement |

---

## Part 3: Tech Stack & Integration

### 3.1 Tech Stack (80% Reuse from Existing YAWL)

| Layer | Technology | YAWL Integration | Effort |
|-------|-----------|---|---|
| **Registry** | Git + RDF (existing) | Use `.specify/` pattern | Reuse |
| **Ontology** | RDF/OWL 2 DL (Turtle) | Extend existing yawl-ontology.ttl | 1 day |
| **Validation** | SHACL 2024 (Java: TopBraid API) | Extend existing yawl-shapes.ttl | 1 day |
| **Discovery** | SPARQL 1.1 + Apache Jena | Leverage existing SPARQL endpoint | Reuse |
| **Skill format** | YAML manifest | New (lightweight) | 2 days |
| **API** | Spring Boot REST | Integrate with existing YawlA2AServer | 3 days |
| **Skill execution** | A2A protocol + YStatelessEngine | Direct reuse | Reuse |
| **Build/test** | Maven + JUnit 5 | Existing CI/CD (scripts/dx.sh) | Reuse |

**Rationale**: Leverage existing YAWL infrastructure to minimize greenfield development.

### 3.2 Code Integration Points

```
src/org/yawlfoundation/yawl/
├─ integration/a2a/skills/        ← Existing! Extend here
│  ├─ A2ASkill.java               (existing)
│  ├─ SkillRequest.java           (existing)
│  ├─ SkillResult.java            (existing)
│  └─ SkillRegistry.java          ← NEW: This MVP
│
├─ stateless/
│  └─ YStatelessEngine.java        (existing, reuse for skill execution)
│
└─ tools/
   └─ SkillPublishTool.java        ← NEW: CLI tool for publishing
```

---

## Part 4: 4-Week Implementation Roadmap

### Week 1: Skill Schema & Metadata Model

**Deliverables**:
- SkillMetadata model (Java POJO)
- YAML deserialization (SnakeYAML)
- RDF ontology extension (skill-ontology.ttl)
- SHACL validation shapes (skill-shapes.ttl)
- UML class diagram

**Files to Create**:

```
src/org/yawlfoundation/yawl/integration/a2a/skills/
├─ model/
│  ├─ SkillMetadata.java           (core model)
│  ├─ SkillInput.java              (input spec)
│  ├─ SkillOutput.java             (output spec)
│  ├─ SkillSLA.java                (SLA contract)
│  ├─ SkillVersion.java            (versioning info)
│  └─ SkillPack.java               (pack definition)
│
├─ manifest/
│  └─ SkillManifestParser.java     (YAML → SkillMetadata)
│
└─ registry/
   └─ SkillRegistry.java           (in-memory registry)

.skills/
├─ ontology/
│  ├─ skill-ontology.ttl           (new)
│  ├─ skill-shapes.ttl             (new)
│  └─ pack-ontology.ttl            (new)
```

**Testing**: Unit tests for YAML parsing, model validation

**Success Criteria**:
- All models serialize/deserialize correctly
- RDF ontology validates via SHACL
- UML diagram shows clear data structures

### Week 2: Publish API & Git Backend

**Deliverables**:
- SkillPublishController (Spring REST)
- GitSkillRepository (Git read/write)
- Semantic versioning logic
- Breaking change detection via SPARQL

**Files to Create**:

```
src/org/yawlfoundation/yawl/integration/a2a/skills/
├─ api/
│  └─ SkillPublishController.java  (POST /api/v1/skills/publish)
│
├─ repository/
│  ├─ SkillRepository.java         (interface)
│  └─ GitSkillRepository.java      (implementation)
│
└─ versioning/
   ├─ SemanticVersioning.java      (MAJOR.MINOR.PATCH logic)
   └─ BreakingChangeDetector.java  (SPARQL query for changes)

test/org/yawlfoundation/yawl/integration/a2a/skills/
├─ SkillPublishIntegrationTest.java
├─ SemanticVersioningTest.java
└─ GitRepositoryTest.java
```

**HTTP API**:

```
POST /api/v1/skills/publish
Content-Type: application/json

{
  "yawlXml": "<yawl>...</yawl>",
  "metadata": {
    "name": "Approval Workflow",
    "version": "1.0.0",
    "author": "AccelOrg",
    ...
  }
}

Response 201 Created:
{
  "skillId": "approval",
  "version": "1.0.0",
  "published": "2026-02-21T14:32:00Z",
  "gitUrl": "https://github.com/gregverse/registry.git/.skills/skills/approval/v1.0"
}
```

**Testing**:
- Parse YAWL → extract inputs/outputs
- Commit to Git + verify immutability
- Detect breaking changes

**Success Criteria**:
- Publish API accepts YAWL + metadata
- Git commits are immutable
- Breaking change detection works on 5 test cases

### Week 3: Discovery Engine (SPARQL)

**Deliverables**:
- SPARQL query library (5 core queries)
- SkillDiscoveryController (REST endpoints)
- Search indexes (RDF triples)
- Performance benchmarks

**Files to Create**:

```
src/org/yawlfoundation/yawl/integration/a2a/skills/
├─ discovery/
│  ├─ SkillDiscoveryEngine.java    (SPARQL query executor)
│  ├─ SkillSearchQuery.java        (query builder)
│  └─ SkillSearchResult.java       (result model)
│
├─ api/
│  └─ SkillDiscoveryController.java (GET /api/v1/skills/search)
│
└─ sparql/
   ├─ queries/
   │  ├─ SkillsByCapability.sparql
   │  ├─ SkillsByDomain.sparql
   │  ├─ SkillsByVersion.sparql
   │  ├─ IncompatibleVersions.sparql
   │  └─ DependencyGraph.sparql
   │
   └─ SkillSparqlEndpoint.java     (SPARQL HTTP endpoint)

test/org/yawlfoundation/yawl/integration/a2a/skills/
├─ SkillDiscoveryTest.java
├─ SkillSearchPerformanceTest.java
└─ SparqlQueryTest.java
```

**REST API**:

```
GET /api/v1/skills/search?capability=approval&version=>=1.0.0
GET /api/v1/skills/search?domain=real-estate
GET /api/v1/skills/search?keyword=multiple_instance
GET /api/v1/skills/{id}/versions
GET /api/v1/skills/{id}/v1.0/dependencies
```

**SPARQL Queries** (5 core):

1. **SkillsByCapability**: Find skills matching input/output types
2. **SkillsByDomain**: Find skills in domain (finance, HR, etc.)
3. **SkillsByVersion**: Find versions compatible with SLA
4. **IncompatibleVersions**: Detect breaking changes
5. **DependencyGraph**: Find dependent skills

**Testing**:
- Query 50 sample skills, verify accuracy
- Measure latency (target: <500ms per query)
- Test on 1000 skills (scalability)

**Success Criteria**:
- Search finds 95%+ relevant skills
- Query latency <500ms per query
- Results ranked by relevance

### Week 4: Pack Grouping & Integration Tests

**Deliverables**:
- SkillPackController (REST endpoints)
- SkillPackValidator (SHACL-based)
- 10 sample skills + 3 vertical packs
- Full integration tests (80% coverage)
- Documentation + README

**Files to Create**:

```
src/org/yawlfoundation/yawl/integration/a2a/skills/
├─ pack/
│  ├─ SkillPack.java              (already defined)
│  ├─ SkillPackService.java       (business logic)
│  └─ SkillPackValidator.java     (SHACL validation)
│
├─ api/
│  └─ SkillPackController.java    (GET/POST /api/v1/packs)

test/org/yawlfoundation/yawl/integration/a2a/skills/
├─ SkillPackIntegrationTest.java
├─ SkillPublishFullCycleTest.java
└─ SkillRegistryPerformanceTest.java

.skills/skills/                    # Sample skills
├─ approval/v1.0/
│  ├─ skill.yaml
│  ├─ skill.ttl
│  ├─ workflow.yawl
│  ├─ inputs.json
│  ├─ outputs.json
│  └─ sla.json
├─ po-creation/v1.0/
├─ expense-report/v1.0/
├─ property-search/v1.0/
├─ property-valuation/v1.0/
├─ financing-approval/v1.0/
├─ inspection-scheduling/v1.0/
├─ title-search/v1.0/
├─ offer-submission/v1.0/
└─ closing-coordination/v1.0/

.skills/packs/
├─ real-estate-acquisition-v1.0.yaml
├─ financial-settlement-v1.0.yaml
└─ hr-onboarding-v1.0.yaml

docs/
└─ SKILLS_MARKETPLACE_USER_GUIDE.md
```

**Sample Skills** (10 real-world examples):

1. **approval** — Multi-level approval chain
2. **po-creation** — Purchase order generation
3. **expense-report** — Expense submission + approval
4. **property-search** — Real estate MLS search
5. **property-valuation** — Appraisal workflow
6. **financing-approval** — Mortgage qualification
7. **inspection-scheduling** — Home inspection booking
8. **title-search** — Title research + clearance
9. **offer-submission** — Offer negotiation
10. **closing-coordination** — Closing day orchestration

**Vertical Packs** (3 industry domains):

1. **Real Estate Acquisition** (skills 4-10)
2. **Financial Settlement** (approval + PO + audit-log)
3. **HR Onboarding** (skills TBD—approval, notification, audit)

**REST API**:

```
GET /api/v1/packs                          # List all packs
GET /api/v1/packs/{id}                    # Get pack details
POST /api/v1/packs                        # Create new pack
GET /api/v1/packs/{id}/skills             # List skills in pack
```

**Testing**:
- Full end-to-end: publish skill → discover → create pack
- All 10 sample skills pass SHACL validation
- All 3 packs verify dependencies resolve correctly
- Performance test: 1000 skills, <500ms search

**Success Criteria**:
- 10 sample skills published
- 3 vertical packs created
- Search finds all skills + packs
- 80%+ code coverage on core modules
- Full integration test suite passes

---

## Part 5: Code Architecture & Patterns

### 5.1 UML Class Diagram (Week 1 Deliverable)

```
┌─────────────────────────────────────────────────────────┐
│                    SkillRegistry                        │
├─────────────────────────────────────────────────────────┤
│ - skillMap: Map<String, SkillMetadata>                 │
│ - packMap: Map<String, SkillPack>                      │
│ - rdfStore: JenaRDFStore                               │
├─────────────────────────────────────────────────────────┤
│ + publish(yawl, metadata): SkillMetadata               │
│ + discover(query): List<SkillMetadata>                 │
│ + getVersion(id, version): SkillMetadata               │
│ + createPack(skills): SkillPack                        │
└─────────────────────────────────────────────────────────┘
         △
         │ implements
         │
┌─────────────────────────────────────────────────────────┐
│              GitSkillRepository                         │
├─────────────────────────────────────────────────────────┤
│ - gitUrl: String                                        │
│ - localPath: File                                       │
├─────────────────────────────────────────────────────────┤
│ + commit(skillYaml, skillTtl, workflow): GitCommit     │
│ + readSkill(skillId, version): SkillMetadata           │
│ + listVersions(skillId): List<String>                  │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                 SkillMetadata                           │
├─────────────────────────────────────────────────────────┤
│ - name: String                                          │
│ - id: String                                            │
│ - version: SemanticVersion                             │
│ - author: String                                        │
│ - inputs: List<SkillInput>                             │
│ - outputs: List<SkillOutput>                           │
│ - sla: SkillSLA                                        │
│ - dependencies: List<SkillDependency>                  │
├─────────────────────────────────────────────────────────┤
│ + toYaml(): String                                      │
│ + toTurtle(): String                                    │
│ + validate(): ValidationResult                         │
└─────────────────────────────────────────────────────────┘
         △ uses
         │
    ┌────┴──────┬────────────┐
    │            │            │
┌───┴──┐  ┌─────┴──┐  ┌──────┴──┐
│Input │  │ Output │  │SkillSLA │
└──────┘  └────────┘  └─────────┘

┌─────────────────────────────────────────────────────────┐
│              SkillDiscoveryEngine                       │
├─────────────────────────────────────────────────────────┤
│ - sparqlEndpoint: String                               │
│ - rdfStore: JenaRDFStore                               │
├─────────────────────────────────────────────────────────┤
│ + search(query): List<SkillMetadata>                   │
│ + searchByCapability(inputs, outputs): List            │
│ + searchByDomain(domain): List                         │
│ + getIncompatibilities(skillId, v1, v2): List         │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                  SkillPack                              │
├─────────────────────────────────────────────────────────┤
│ - name: String                                          │
│ - version: String                                       │
│ - includes: List<SkillReference>                       │
│ - domain: String                                        │
├─────────────────────────────────────────────────────────┤
│ + validate(): ValidationResult                         │
│ + resolveDependencies(): Map<String, SkillMetadata>   │
└─────────────────────────────────────────────────────────┘
```

### 5.2 Integration Points

**1. YAWL Workflow → Skill Metadata**

```java
// Week 2: SkillPublishService.java
public SkillMetadata extractFromYawL(String yawlXml, SkillManifest manifest) {
    YSpecification spec = YawlParser.parse(yawlXml);

    // Extract inputs from YAWL netdata
    List<SkillInput> inputs = spec.getInputVariables()
        .stream()
        .map(var -> new SkillInput(var.getName(), var.getDataType()))
        .collect(toList());

    // Extract outputs from YAWL netdata
    List<SkillOutput> outputs = spec.getOutputVariables()
        .stream()
        .map(var -> new SkillOutput(var.getName(), var.getDataType()))
        .collect(toList());

    // Build SkillMetadata
    return new SkillMetadata()
        .withName(manifest.getName())
        .withInputs(inputs)
        .withOutputs(outputs)
        .withSLA(manifest.getSLA())
        .withVersion(manifest.getVersion());
}
```

**2. RDF Ontology → SPARQL Discovery**

```java
// Week 3: SkillDiscoveryEngine.java
public List<SkillMetadata> searchByCapability(String inputType, String outputType) {
    String sparql = """
        PREFIX skill: <http://gregverse.org/skill#>
        SELECT ?skillId ?version
        WHERE {
            ?skill a skill:Skill ;
                dcterms:title ?skillId ;
                skill:versionNumber ?version ;
                skill:inputSchema / skill:hasField [
                    skill:fieldType ?inputType
                ] ;
                skill:outputSchema / skill:hasField [
                    skill:fieldType ?outputType
                ] .
        }
        ORDER BY DESC(?version)
        """;

    return sparqlEndpoint.query(sparql)
        .stream()
        .map(this::resultToSkillMetadata)
        .collect(toList());
}
```

**3. A2A Skill Invocation**

```java
// Week 4: SkillExecutor.java (uses existing A2A infrastructure)
public SkillResult execute(SkillMetadata skill, Map<String, Object> inputs) {
    // Use A2A protocol to invoke skill
    A2ARequest request = new A2ARequest()
        .withSkillId(skill.getId())
        .withVersion(skill.getVersion())
        .withInputs(inputs);

    // Route to YStatelessEngine via A2A
    A2AResponse response = yawlA2AServer.invoke(request);

    return new SkillResult()
        .withStatus(response.getStatus())
        .withOutputs(response.getOutputData());
}
```

**4. Git Storage → Immutable Registry**

```java
// Week 2: GitSkillRepository.java
public GitCommit publishSkill(SkillMetadata metadata, String yawlXml) {
    // Create directory structure
    Path skillDir = skillsRoot
        .resolve(metadata.getId())
        .resolve("v" + metadata.getVersion());

    // Write YAML manifest
    Files.write(
        skillDir.resolve("skill.yaml"),
        yamlMapper.writeValueAsBytes(metadata)
    );

    // Write RDF definition
    Files.write(
        skillDir.resolve("skill.ttl"),
        metadata.toTurtle().getBytes()
    );

    // Write YAWL reference
    Files.write(
        skillDir.resolve("workflow.yawl"),
        yawlXml.getBytes()
    );

    // Commit to git (immutable)
    git.add(".");
    return git.commit(
        String.format("Publish %s v%s", metadata.getId(), metadata.getVersion())
    );
}
```

### 5.3 Testing Strategy (80% Coverage)

**Critical Paths to Test**:

1. **Publish workflow**
   - Parse YAWL XML
   - Extract inputs/outputs
   - Commit to Git
   - Generate RDF
   - Validate via SHACL

2. **Discovery workflow**
   - SPARQL query by capability
   - Filter by domain/version
   - Detect incompatibilities
   - Rank by relevance

3. **Pack creation**
   - Resolve skill dependencies
   - Validate version constraints
   - Check for circular dependencies

4. **Integration workflows**
   - End-to-end: publish → discover → execute
   - Failure modes: invalid YAML, Git conflicts, SPARQL timeout

**Test File Organization**:

```
test/org/yawlfoundation/yawl/integration/a2a/skills/

Week 1:
├─ model/
│  ├─ SkillMetadataTest.java           (POJO serialization)
│  ├─ SkillVersionTest.java            (semantic versioning)
│  └─ SkillPackTest.java               (pack structure)

Week 2:
├─ SkillPublishIntegrationTest.java    (full publish flow)
├─ GitRepositoryTest.java              (Git operations)
├─ SemanticVersioningTest.java         (version logic)
└─ BreakingChangeDetectorTest.java    (SPARQL breaking changes)

Week 3:
├─ SkillDiscoveryTest.java             (search queries)
├─ SkillSearchPerformanceTest.java    (latency: <500ms)
└─ SparqlQueryTest.java               (SPARQL correctness)

Week 4:
├─ SkillPublishFullCycleTest.java     (end-to-end)
├─ SkillPackIntegrationTest.java      (pack creation)
└─ SkillRegistryPerformanceTest.java  (1000 skills benchmark)
```

---

## Part 6: Success Criteria & Metrics

### 6.1 Functional Success Criteria

| Criterion | Target | Verification |
|-----------|--------|---|
| **Publish API** | Accepts YAWL + metadata, creates Git commit | POST /api/v1/skills/publish returns 201 |
| **Discovery** | Find skills by capability (inputs/outputs) | GET /api/v1/skills/search?capability=* returns results |
| **Versioning** | Semantic versioning (1.0.0 → 1.1.0 → 2.0.0) | SemanticVersioningTest passes all cases |
| **Breaking changes** | Detect removed/changed required fields | BreakingChangeDetectorTest finds 100% of test cases |
| **Packs** | Bundle 5-10 related skills | 3 vertical packs created + resolved |
| **Sample skills** | 10 real-world examples | All pass SHACL validation |

### 6.2 Performance Criteria

| Metric | Target | Measurement |
|--------|--------|---|
| **Search latency** | <500ms per query | SkillSearchPerformanceTest.java |
| **Search accuracy** | 95% relevant results | Manual verification on 20 test queries |
| **Skill scalability** | 1000 skills indexed | Benchmark: 1000 skills in RDF store |
| **Publish latency** | <2 seconds | GitRepository write + SHACL validation |
| **Discovery throughput** | >100 searches/sec | Load test with Apache JMeter |

### 6.3 Code Quality Criteria

| Criterion | Target | Tool |
|-----------|--------|---|
| **Test coverage** | 80%+ on core modules | JaCoCo coverage report |
| **Static analysis** | 0 critical violations | SpotBugs, CheckStyle |
| **Integration tests** | 20+ test cases | JUnit 5 test suite |
| **Documentation** | 100% of public APIs | JavaDoc |

### 6.4 Integration Criteria

| Integration | Target | Verification |
|-------------|--------|---|
| **YAWL ontology** | Extend existing yawl-ontology.ttl | SHACL validation passes |
| **A2A protocol** | Invoke skills via A2A endpoint | A2AProtocolTest passes |
| **YStatelessEngine** | Execute skills stateless | YStatelessEngineTest passes |
| **SPARQL endpoint** | Query skill registry | SPARQL endpoint responds <500ms |
| **Git** | Immutable skill commits | Git log shows all skill versions |

---

## Part 7: Risk Assessment & Mitigation

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|-----------|
| **SPARQL query complexity** | Medium | High | Pre-optimize 5 core queries; cache results |
| **RDF ontology inconsistency** | Low | High | SHACL validation gate on every publish |
| **Breaking change false negatives** | Low | Critical | Conservative SPARQL; manual review gate |
| **Git repository size** | Medium | Medium | Archive old versions; use submodules |
| **Skill dependency cycles** | Low | High | Topological sort check on packs |
| **Performance regression** | Medium | Medium | Benchmark before/after each week |
| **YAML parsing errors** | Low | Medium | Comprehensive error messages + examples |

---

## Part 8: Deliverables & Handoff

### 8.1 Code Deliverables

**Total New Code**: ~6,000 lines (Java + YAML + RDF)

```
src/org/yawlfoundation/yawl/integration/a2a/skills/
├─ model/              (900 lines, POJO classes)
├─ manifest/           (300 lines, YAML parsing)
├─ repository/         (500 lines, Git backend)
├─ api/               (600 lines, Spring REST controllers)
├─ discovery/         (700 lines, SPARQL engine)
├─ pack/              (400 lines, pack grouping)
└─ util/              (200 lines, helpers)
Total: ~3,600 lines

test/org/yawlfoundation/yawl/integration/a2a/skills/
├─ model/             (400 lines)
├─ *IntegrationTest.java files  (1,500 lines)
├─ *PerformanceTest.java files  (400 lines)
Total: ~2,300 lines

.skills/              (600 lines YAML + Turtle)
├─ ontology/          (300 lines)
├─ skills/            (200 lines × 10 samples = 2,000 lines)
├─ packs/             (150 lines × 3 packs = 450 lines)
Total: ~2,750 lines

docs/
├─ SKILLS_MARKETPLACE_USER_GUIDE.md
└─ API_REFERENCE.md
```

### 8.2 Documentation Deliverables

1. **README.md** — Quick start guide
2. **API_REFERENCE.md** — REST endpoints + examples
3. **ONTOLOGY_GUIDE.md** — RDF schema + SPARQL queries
4. **SKILLS_MARKETPLACE_USER_GUIDE.md** — For skill publishers
5. **IMPLEMENTATION_NOTES.md** — Architecture decisions

### 8.3 Artifacts for Production

```
Delivered to git repo:

1. Source code (src/, test/)
2. Ontology files (.skills/ontology/)
3. Sample skills (.skills/skills/ × 10)
4. Vertical packs (.skills/packs/ × 3)
5. Maven build (pom.xml updated)
6. CI/CD pipeline (.github/workflows/)
7. Docker image (yawl-skills-marketplace:v1.0)
```

---

## Part 9: Integration with Gregverse & A2A

### 9.1 How Skills Connect to A2A Protocol

**Current A2A Architecture** (from existing research):

```
Agent Alpha (Workflow Engine)
    ↓
A2AServer (YawlA2AServer.java)
    ↓
A2AEndpoint: /.well-known/agent.json
    ↓
Skills (advertised as A2A capabilities)
```

**MVP Integration**: Skills ARE A2A capabilities!

```java
// Week 4: SkillAsA2ACapability.java
class SkillAsA2ACapability {
    String skillId;        // "approval"
    String version;        // "1.0.0"
    String endpoint;       // "/skills/approval/v1.0/execute"
    Map<String, Object> inputs;   // A2A input schema
    Map<String, Object> outputs;  // A2A output schema

    // When Agent Beta discovers this skill via SPARQL:
    // 1. Parse RDF skill definition
    // 2. Register as A2A capability in local agent
    // 3. Invoke via A2A protocol: POST /skills/approval/v1.0/execute
    // 4. Receive result + map back to local schema
}
```

**Flow**:

```
Agent Alpha publishes skill
    ↓
Skill stored in .skills/approval/v1.0/
    ↓
Skill registered in RDF ontology
    ↓
Agent Beta runs SPARQL discovery
    ↓
Agent Beta finds: approval skill v1.0
    ↓
Agent Beta registers skill as local A2A capability
    ↓
Agent Beta invokes: POST /api/v1/skills/approval/v1.0/execute
    ↓
Agent Alpha executes via YStatelessEngine
    ↓
Agent Beta receives result
```

### 9.2 How Skills Connect to MCP Tools (Future)

**MCP Architecture** (from existing research):

```
Claude (LLM)
    ↓
MCP Client (claude-sdk)
    ↓
MCP Server (YawlMcpServer.java)
    ↓
Tools (advertised via list_tools())
```

**Future Integration** (post-MVP):

```java
// Post-MVP: SkillAsMcpTool.java
class SkillAsMcpTool {
    String name;           // "approval"
    String description;    // From skill.yaml
    Map<String, Object> inputSchema;   // From SkillInput
    Map<String, Object> outputSchema;  // From SkillOutput

    // Claude can call: use_mcp_tool(name: "approval", ...)
    // MCP server routes to: YStatelessEngine.executeSkill()
}
```

**Why defer to v1.1**: MCP integration requires LLM prompt engineering + tool calling logic. Core skill machinery is sufficient for MVP.

### 9.3 How Skills Export to Multiple Formats

**YAML → RDF → Export**: One source of truth (RDF), multiple views.

```java
// Post-MVP: SkillExporter.java
public class SkillExporter {

    // Export to Mermaid (workflow diagram)
    public String toMermaidDiagram(SkillMetadata skill) { ... }

    // Export to OpenAPI (REST API spec)
    public OpenAPI toOpenAPI(SkillMetadata skill) { ... }

    // Export to AsyncAPI (event-driven spec)
    public AsyncAPI toAsyncAPI(SkillMetadata skill) { ... }

    // Export to Terraform (infrastructure)
    public String toTerraform(SkillMetadata skill) { ... }
}
```

**Why in YAML first**: YAML is human-readable. RDF is queryable. Both generated from same model.

---

## Part 10: Getting Started

### 10.1 Week 1 Checklist

- [ ] Create skill-ontology.ttl (RDF definition of Skill class)
- [ ] Create skill-shapes.ttl (SHACL validation shapes)
- [ ] Create SkillMetadata.java (core POJO)
- [ ] Create SkillManifestParser.java (YAML deserialization)
- [ ] Unit tests: 10+ test cases
- [ ] UML diagram: Model classes
- [ ] **Commit**: "feat: add skill metadata model + RDF ontology"

### 10.2 Week 2 Checklist

- [ ] Create SkillPublishController.java (REST API)
- [ ] Create GitSkillRepository.java (Git backend)
- [ ] Create SemanticVersioning.java (1.0.0 logic)
- [ ] Create BreakingChangeDetector.java (SPARQL queries)
- [ ] Integration tests: publish → git commit → verify
- [ ] **Commit**: "feat: add skill publish API + git backend"

### 10.3 Week 3 Checklist

- [ ] Create SkillDiscoveryEngine.java (SPARQL executor)
- [ ] Create 5 SPARQL query files (capability, domain, version, etc.)
- [ ] Create SkillDiscoveryController.java (REST API)
- [ ] Performance tests: <500ms latency on 1000 skills
- [ ] **Commit**: "feat: add skill discovery engine"

### 10.4 Week 4 Checklist

- [ ] Create SkillPackService.java + Controller
- [ ] Create 10 sample skills (.skills/skills/)
- [ ] Create 3 vertical packs (.skills/packs/)
- [ ] Full end-to-end integration tests
- [ ] User guide + API reference
- [ ] **Commit**: "feat: add skill packs + integration tests"

---

## Conclusion

This MVP design provides a **realistic 4-week plan** to launch the Skills Marketplace by focusing on the critical 20% (publish, discover, versioning, packs) while deferring the 80% (UI, reputation, analytics, payments).

**Key Success Factors**:

1. **Reuse existing YAWL infrastructure** (ontology, A2A, YStatelessEngine)
2. **Git as source of truth** (immutability + audit trail)
3. **SPARQL for discovery** (semantic search, not keyword search)
4. **Semantic versioning** (clear upgrade paths)
5. **Sample skills** (prove the model works)

**Expected Outcome**:
- 1,000 skills indexed
- 95% search accuracy
- <500ms query latency
- 3 vertical packs (Real Estate, Finance, HR)
- Ready for GA release + initial 100 paying customers

---

**Document Version**: 1.0
**Status**: Ready for Implementation
**Next Step**: Approve design, assign 2 engineers, begin Week 1
**Target Launch**: 4 weeks from start

