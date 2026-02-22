# Skills Marketplace — UML Class & Interaction Diagrams

**Week 1 Deliverable** — Detailed architecture documentation

---

## 1. Core Model Class Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      SkillMetadata                              │
├─────────────────────────────────────────────────────────────────┤
│ - id: String                                                    │
│ - name: String                                                  │
│ - version: SemanticVersion                                      │
│ - author: String                                                │
│ - authorEmail: String                                           │
│ - created: Instant                                              │
│ - description: String                                           │
│ - pattern: String (e.g., "multiple_instance")                 │
│ - inputs: List<SkillInput>                                      │
│ - outputs: List<SkillOutput>                                    │
│ - sla: SkillSLA                                                 │
│ - dependencies: List<SkillDependency>                           │
│ - license: String                                               │
│ - keywords: List<String>                                        │
│ - workflowRef: WorkflowReference                               │
├─────────────────────────────────────────────────────────────────┤
│ + toYaml(): String                                              │
│ + toTurtle(): String                                            │
│ + toJson(): String                                              │
│ + validate(): ValidationResult                                  │
│ + getFullId(): String  // "approval/v1.0"                      │
│ + isBackwardCompatibleWith(SkillMetadata): boolean             │
│ + getBreakingChanges(SkillMetadata): List<BreakingChange>      │
└─────────────────────────────────────────────────────────────────┘
         △
         │ composition
         │
         ├─────────────────────────────────────────────────────────┐
         │                                                         │
    ┌────┴──────┐   ┌──────────────┐   ┌─────────────┐           │
    │            │   │              │   │             │           │
┌───┴──────────┐ │ ┌─┴────────────┐ │ ┌─┴──────────┐ │           │
│              │ │ │              │ │ │            │ │           │
│ SkillInput   │ │ │ SkillOutput  │ │ │ SkillSLA   │ │           │
├──────────────┤ │ ├──────────────┤ │ ├────────────┤ │           │
│ name: String │ │ │ name: String │ │ │ maxTime    │ │           │
│ type: String │ │ │ type: String │ │ │ minRate    │ │           │
│ required: bool  │ │ required: bool  │ │ errorCodes │ │           │
│ minVal/maxVal  │ │ description  │ │ │ timeout    │ │           │
├──────────────┤ │ ├──────────────┤ │ ├────────────┤ │           │
│ validate()   │ │ │ validate()   │ │ │ validate() │ │           │
└──────────────┘ │ └──────────────┘ │ └────────────┘ │           │
                 │                   │                │           │
                 └───────────────────┴────────────────┘           │
                                                                  │

         ┌────────────────────────────────────────────────────────┘
         │
    ┌────┴──────────────────┐
    │                       │
┌───┴────────────────────┐  │  ┌──────────────────────┐
│                        │  │  │                      │
│ SemanticVersion        │  │  │ WorkflowReference    │
├────────────────────────┤  │  ├──────────────────────┤
│ major: int             │  │  │ yawlFile: String     │
│ minor: int             │  │  │ yawlHash: String     │
│ patch: int             │  │  │ pattern: String      │
├────────────────────────┤  │  │ yawlValidated: bool  │
│ + compare(v2): int     │  │  │ petriNetSound: bool  │
│ + isCompatible(v2)     │  │  ├──────────────────────┤
│ + toString(): String   │  │  │ validate(): Result   │
└────────────────────────┘  │  └──────────────────────┘
                            │
         ┌──────────────────┴────────┐
         │                           │
    ┌────┴──────────────┐  ┌────────┴─────┐
    │                   │  │              │
│ SkillDependency      │  │ SkillPack    │
├───────────────────┤  ├──────────────┤
│ skillId: String   │  │ name: String │
│ version: String   │  │ version: String │
│ optional: bool    │  │ domain: String  │
├───────────────────┤  │ includes: List<SkillRef> │
│ matches(version)  │  ├──────────────┤
└───────────────────┘  │ validate()   │
                       │ resolveDeps()│
                       └──────────────┘
```

---

## 2. Registry & Repository Pattern

```
┌────────────────────────────────────────────────────────────────┐
│                    SkillRegistry (Interface)                   │
├────────────────────────────────────────────────────────────────┤
│ + publish(metadata): SkillMetadata                             │
│ + get(skillId, version): SkillMetadata                         │
│ + listVersions(skillId): List<String>                          │
│ + discover(query): List<SkillMetadata>                         │
│ + validate(metadata): ValidationResult                         │
│ + listAll(): List<SkillMetadata>                               │
│ + delete(skillId, version): void  (soft delete)               │
└────────────────────────────────────────────────────────────────┘
         △
         │ implements (1:1)
         │
    ┌────┴──────────────────────────────────────────┬────────────┐
    │                                               │            │
┌───┴──────────────────────┐        ┌──────────────┴──────┐     │
│                          │        │                     │     │
│ GitSkillRepository       │        │ InMemorySkillReg    │     │
├──────────────────────────┤        ├─────────────────────┤     │
│ - gitUrl: String         │        │ - skillMap: Map     │     │
│ - localPath: Path        │        │ - rdfStore: RDF     │     │
│ - git: Git               │        │ - cache: ConcMap    │     │
├──────────────────────────┤        ├─────────────────────┤     │
│ + commit(metadata)       │        │ + get(): fast       │     │
│ + push(): void           │        │ + list(): instant   │     │
│ + pull(): void           │        │ + validate(): SHACL │     │
│ + readFile(path): bytes  │        └─────────────────────┘     │
│ + readYaml(id, v): Meta  │                                     │
│ + readTurtle(id, v): RDF │        ┌──────────────────────┐     │
├──────────────────────────┤        │                      │     │
│ + publish(): GitCommit   │        │ CachedSkillRegistry │     │
│ + getCommitHash(): String       │ (with TTL)           │     │
└──────────────────────────┘        ├──────────────────────┤     │
         △                          │ - delegate: Registry │     │
         │ uses (1:N)               │ - cache: TTLMap      │     │
         │                          │ - ttl: Duration      │     │
    ┌────┴──────────────────┐       ├──────────────────────┤     │
    │                       │       │ + get(): cached      │     │
┌───┴────────────────┐      │       │ + invalidate(id)     │     │
│                    │      │       └──────────────────────┘     │
│ SkillRepository    │      │                                     │
│ (abstract adapter) │      │       ┌──────────────────────┐     │
├────────────────────┤      │       │                      │     │
│ - registry: Reg    │      └──────→│ RDFSkillStore        │     │
├────────────────────┤              ├──────────────────────┤     │
│ + commit()         │              │ - store: JenaStore   │     │
│ + pull()           │              │ - sparql: SparqlEnd  │     │
│ + validate()       │              ├──────────────────────┤     │
└────────────────────┘              │ + storeTriples()     │     │
                                    │ + querySparql()      │     │
                                    │ + validate(shacl)    │     │
                                    └──────────────────────┘     │
```

---

## 3. Publish Workflow Sequence Diagram

```
Actor (Operator)          SkillPublishController        GitSkillRepository
     │                            │                            │
     │ POST /api/v1/skills       │                            │
     │──────publish              │                            │
     │    {yawl, metadata}        │                            │
     │                            │                            │
     │                    validate(yawl)                       │
     │                    validate(metadata)                   │
     │                            │                            │
     │                    extract inputs/outputs              │
     │                    from YAWL spec                      │
     │                            │                            │
     │                    check version conflict              │
     │                            ├─ listVersions             │
     │                            │ ◄─ ["1.0.0", "1.1.0"]     │
     │                            │                            │
     │                    ✓ Version 2.0.0 available            │
     │                            │                            │
     │                    publish                             │
     │                    ├─ write skill.yaml                 │
     │                    ├─ write skill.ttl                  │
     │                    ├─ write workflow.yawl              │
     │                    │                                   │
     │                    │                      git add      │
     │                    │                      ◄────────────┤
     │                    │                      git commit   │
     │                    │                      ◄────────────┤
     │                    │                      git push     │
     │                    │                      ◄────────────┤
     │                    │                                   │
     │                    ◄────── GitCommit                   │
     │                                                         │
     │◄────── 201 Created                                      │
     │    {skillId, version, gitUrl}                          │
     │
```

---

## 4. Discovery Workflow (SPARQL)

```
Agent (Searcher)      SkillDiscoveryEngine        RDFStore (Jena)
      │                      │                          │
      │ GET /api/v1/skills  │                          │
      │ /search?capability  │                          │
      │                     │                          │
      │              build SPARQL query                 │
      │              SELECT ?skill WHERE               │
      │                ?skill a skill:Skill ;          │
      │                skill:inputSchema /             │
      │                  skill:hasField                │
      │                  [skill:fieldType "UUID"]      │
      │                                                 │
      │              executeSparql()                    │
      │              ├─────────────── query          │
      │              │                 ──────────────→ │
      │              │                                 │
      │              │                 ◄─ binding 1   │
      │              │                    binding 2   │
      │              │                    binding 3   │
      │              │                                 │
      │              mapBindingsToSkills()             │
      │              sortByRelevance()                 │
      │                                                 │
      │◄─────────────[List<SkillMetadata>]            │
      │                                                 │
      │ HTTP 200 OK                                    │
      │ [{                                             │
      │   "id": "approval",                            │
      │   "version": "1.0.0",                          │
      │   "relevance": 0.95                            │
      │ }, ...]                                        │
      │
```

---

## 5. Pack Creation & Dependency Resolution

```
┌──────────────────────────────────────────────────────────┐
│              SkillPackService                            │
├──────────────────────────────────────────────────────────┤
│ - registry: SkillRegistry                               │
│ - validator: SkillPackValidator                         │
├──────────────────────────────────────────────────────────┤
│ + createPack(packDef): SkillPack                         │
│   ├─ validate(packDef): ValidationResult                │
│   ├─ resolveDependencies(skills): Map                   │
│   │  ├─ topologicalSort(skills)                         │
│   │  ├─ checkCycles(): boolean                          │
│   │  └─ resolvePins(version constraints)                │
│   ├─ validateCompatibility(): boolean                   │
│   └─ persistToDisk(packYaml)                            │
│                                                         │
│ + validatePack(packDef): ValidationResult               │
│   ├─ ensureAllSkillsExist()                             │
│   ├─ ensureVersionsCompatible()                         │
│   ├─ ensureNoCircularDeps()                             │
│   ├─ ensureDomainConsistent()                           │
│   └─ applySHACL(shapes)                                 │
│                                                         │
│ + resolveDependencies(skills): Map<SkillId, Metadata>  │
│   ├─ for each skill in pack:                            │
│   │  ├─ fetch from registry                             │
│   │  ├─ resolve version (semantic pins)                 │
│   │  ├─ traverse dependencies recursively               │
│   │  └─ collect all transitive deps                     │
│   ├─ topologicalSort(allSkills)                         │
│   ├─ detectCycles(skillGraph)                           │
│   └─ return {skillId → SkillMetadata}                   │
│                                                         │
│ + checkDependencyVersion(dep, version): boolean         │
│   ├─ parse semantic version constraint                  │
│   │  (>=1.0.0, ~1.2.3, ^1.0.0, etc.)                    │
│   └─ match against resolved version                     │
└──────────────────────────────────────────────────────────┘
         │
         │ uses
         ▼
┌──────────────────────────────────────────────────────────┐
│         SkillPackValidator (SHACL-based)                │
├──────────────────────────────────────────────────────────┤
│ - shaclShapes: URL                                      │
│ - rdfStore: JenaRDFStore                               │
├──────────────────────────────────────────────────────────┤
│ + validate(packRdf): ValidationResult                   │
│   ├─ loadShapes()                                       │
│   ├─ applySHACL(packRdf)                                │
│   ├─ collectViolations()                                │
│   └─ return ValidationResult                            │
│                                                         │
│ + validateDependencyGraph(skills): boolean              │
│   ├─ buildGraph()                                       │
│   ├─ detectCycles() → throw if found                    │
│   └─ validateVersions() → each satisfies constraints    │
└──────────────────────────────────────────────────────────┘

Example Flow:
═════════════

Input: SkillPackDefinition
  ├─ name: "Real Estate Acquisition"
  ├─ includes: [property-search@>=1.0.0,
  │              property-valuation@>=1.0.0,
  │              financing-approval@>=2.0.0]
  └─ domain: "real-estate"

Process:
  1. validate(packDef) → checks structure
  2. resolveDependencies([...])
     └─ property-search v1.5.0
        ├─ depends on: notification-email@>=1.0.0
        │  └─ resolve to v1.2.0 ✓
        │  └─ depends on: audit-log@>=1.0.0
        │     └─ resolve to v2.0.0 ✓
        ├─ property-valuation v1.0.0
        │  ├─ depends on: property-search@>=1.0.0
        │  │  └─ Already resolved ✓
        │  └─ depends on: audit-log@>=1.0.0
        │     └─ Already resolved ✓
        └─ financing-approval v2.0.0
           ├─ depends on: property-valuation@>=1.0.0
           │  └─ Already resolved ✓
           └─ depends on: audit-log@>=2.0.0
              └─ Version conflict! ✗ FAILED
                 (v1.5 needed v2.0 but v1.2 resolved)

Output: ValidationError or
        SkillPack {
          includedSkills: Map<SkillId, SkillMetadata>
          resolvedDeps: Map<SkillId, SkillMetadata>
          executionOrder: [id1, id2, id3, ...]
        }
```

---

## 6. Search & Ranking Sequence

```
                    SkillDiscoveryEngine
                            │
                  searchByCapability(
                    inputs: ["uuid", "amount"]
                    outputs: ["status", "trace"]
                  )
                            │
                ┌───────────┴───────────┐
                │                       │
        buildSparqlQuery()      executeQuery()
        ├─ SELECT ?skill       ├─ for each binding:
        ├─ WHERE {              │  ├─ fetch SkillMetadata
        │  ?skill a skill:Skill │  ├─ calc relevance:
        │  skill:inputSchema /  │  │   * exact match inputs: +0.5
        │  skill:hasField       │  │   * exact match outputs: +0.3
        │  [sk:fieldType "uuid"]  │  │   * partial match: +0.1
        │  skill:outputSchema / │  │   * newer version: +0.05
        │  skill:hasField       │  │   * higher rating: +0.05
        │  [sk:fieldType        │  │   * → Score 0.0-1.0
        │  "status"]            │  │
        │ }                     │  └─ add to results
        │ ORDER BY score DESC   │
        └                       └─ sortByScore(results)
                                   retainTopN(N=20)

Results with scores:
┌────────────────────┬──────────┬──────────┐
│ SkillId            │ Version  │ Score    │
├────────────────────┼──────────┼──────────┤
│ approval           │ 1.2.0    │ 0.95 ✓   │
│ po-creation        │ 1.0.0    │ 0.80     │
│ finance-workflow   │ 2.1.0    │ 0.65     │
│ generic-approval   │ 0.9.0    │ 0.50     │
└────────────────────┴──────────┴──────────┘
```

---

## 7. Semantic Versioning & Breaking Change Detection

```
┌─────────────────────────────────────────────────────┐
│           SemanticVersion                           │
├─────────────────────────────────────────────────────┤
│ - major: int                                        │
│ - minor: int                                        │
│ - patch: int                                        │
├─────────────────────────────────────────────────────┤
│ + compare(v: SemanticVersion): int                 │
│   return (major, minor, patch) spaceship operator  │
│                                                     │
│ + isMajorChange(other): boolean                    │
│   return this.major != other.major                 │
│                                                     │
│ + isMinorChange(other): boolean                    │
│   return this.major == other.major AND             │
│          this.minor != other.minor                 │
│                                                     │
│ + isPatchChange(other): boolean                    │
│   return this.major == other.major AND             │
│          this.minor == other.minor AND             │
│          this.patch != other.patch                 │
│                                                     │
│ + isCompatible(otherVersion): boolean              │
│   return otherVersion.major == this.major          │
│                                                     │
│ + satisfies(constraint: String): boolean           │
│   ├─ parse constraint: ">=1.0.0", "~1.2.3", etc   │
│   └─ return match                                  │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│      BreakingChangeDetector (SPARQL-based)         │
├─────────────────────────────────────────────────────┤
│ - sparqlEndpoint: String                            │
├─────────────────────────────────────────────────────┤
│ + detectChanges(skillId, vOld, vNew):              │
│    List<BreakingChange>                            │
│   ├─ queryOldSchema(skillId, vOld)                │
│   ├─ queryNewSchema(skillId, vNew)                │
│   ├─ compareSchemas():                             │
│   │  ├─ removedFields() → BREAKING                │
│   │  ├─ changedFieldTypes() → BREAKING            │
│   │  ├─ strictenedSLA() → BREAKING                │
│   │  ├─ addedRequiredFields() → BREAKING          │
│   │  ├─ newOptionalFields() → NOT breaking        │
│   │  ├─ relaxedSLA() → NOT breaking               │
│   │  └─ newErrorCodes() → NOT breaking            │
│   └─ return List<BreakingChange>                  │
│                                                     │
│ + categorize(change): enum                         │
│   MAJOR_REMOVAL,                                   │
│   MAJOR_TYPE_CHANGE,                               │
│   MAJOR_SLA_TIGHTEN,                               │
│   MINOR_FIELD_ADD,                                 │
│   MINOR_SLA_RELAX,                                 │
│   PATCH_DOCSTRING                                  │
└─────────────────────────────────────────────────────┘

SPARQL Query (Detect Breaking Changes):
══════════════════════════════════════════

PREFIX skill: <http://gregverse.org/skill#>
SELECT ?fieldName ?changeType
WHERE {
    # OLD version
    ?skillOld skill:versionNumber "1.0.0" ;
      skill:outputSchema / skill:hasField [
        skill:fieldName ?fieldName ;
        skill:required true ;
      ] .

    # NEW version (same skill ID)
    ?skillNew skill:versionNumber "2.0.0" ;
      skill:outputSchema ?newSchema .

    # Check if field is gone in new version
    MINUS {
        ?newSchema skill:hasField [
            skill:fieldName ?fieldName ;
        ] .
    }

    BIND("FIELD_REMOVED" AS ?changeType)
}

Example Results:
┌──────────────┬────────────────────┐
│ fieldName    │ changeType         │
├──────────────┼────────────────────┤
│ trackingId   │ FIELD_REMOVED      │ ✗ Breaking!
│ reason       │ FIELD_REMOVED      │ ✗ Breaking!
└──────────────┴────────────────────┘
```

---

## 8. A2A Integration Pattern

```
┌──────────────────────────────────────────────────────┐
│         YawlA2AServer (existing)                    │
├──────────────────────────────────────────────────────┤
│                                                      │
│ GET /.well-known/agent.json                        │
│   ├─ returns AgentCard with skills                 │
│   └─ includes SkillAsCapability[] for each skill    │
│                                                      │
│ POST / (authenticated)                              │
│   ├─ Input: A2ARequest {skillId, version, inputs}  │
│   └─ Output: A2AResponse {status, outputs}          │
│                                                      │
└──────────────────────────────────────────────────────┘
         △
         │ uses
         │
┌────────┴───────────────────────────────────────────┐
│                                                    │
│         SkillAsA2ACapability                       │
├────────────────────────────────────────────────────┤
│ - skillId: String                                  │
│ - version: String                                  │
│ - endpoint: String (/skills/approval/v1.0)       │
│ - inputSchema: Map<String, JsonSchema>             │
│ - outputSchema: Map<String, JsonSchema>            │
├────────────────────────────────────────────────────┤
│ + toAgentCardCapability(): Capability              │
│ + invoke(inputs): A2AResponse                      │
│ + validateInputs(inputs): ValidationResult         │
│ + mapInputsToWorkflow(inputs): WorkflowInput      │
│ + mapOutputsToA2A(workflowOutput): Map             │
└────────────────────────────────────────────────────┘
         △
         │ instantiated from
         │
    SkillMetadata.toA2ACapability()


Agent Discovery Flow:
═════════════════════

Agent Alpha                     Agent Beta
  ├─ publishes skill             └─ wants to discover
  │  (approval v1.0)                & invoke skills
  │  ├─ stored in
  │  │  .skills/skills/approval/v1.0/
  │  └─ announces via A2A:
  │     GET /.well-known/agent.json
  │     ├─ advertises "approval" skill
  │     │  version: 1.0.0
  │     │  endpoint: /skills/approval/v1.0/execute
  │     │  inputs: [{name: "approvers", type: "array"}]
  │     │  outputs: [{name: "status", type: "enum"}]
  │     └─ signed JWT token
  │
  │                           Agent Beta
  │                             ├─ runs SPARQL discovery
  │                             │  query: find skills with
  │                             │         array<approver> input
  │                             │  finds: approval/v1.0.0
  │                             │
  │                             ├─ registers locally as capability
  │                             │  skillId: approval
  │                             │  endpoint: alpha.accel.org/skills/approval/v1.0
  │                             │
  │                             ├─ invokes:
  │                             │  POST alpha.accel.org/skills/approval/v1.0/execute
  │                             │  body: {approvers: ["user1", "user2"]}
  │                             │  JWT: <token>
  │                             │
  │◄────────────────────────────────────────────
  │ receives request
  │ ├─ validate JWT token
  │ ├─ map A2A inputs to YAWL workflow
  │ ├─ execute via YStatelessEngine
  │ ├─ map YAWL outputs to A2A response
  │ └─ return status + outputs
  │
  │────────────────────────────────────────────→
  │ returns A2AResponse
  │  {status: "APPROVED", outputs: {
  │    approvalStatus: "APPROVED",
  │    trace: [...]
  │  }}
  │
  └─ case completed
```

---

## 9. Pack Dependency Resolution (Graph)

```
SkillPack: real-estate-acquisition

Skills:
  S1: property-search (v1.5.0)
  S2: property-valuation (v1.0.0)
  S3: financing-approval (v2.0.0)
  S4: inspection-scheduling (v1.0.0)
  S5: title-search (v1.1.0)
  S6: offer-submission (v1.2.0)
  S7: closing-coordination (v2.1.0)

Dependencies:
  S1 → notification-email (v≥1.0.0, resolves to v1.2.0)
  S1 → audit-log (v≥1.0.0, resolves to v1.5.0)

  S2 → S1 (v≥1.0.0, resolved)
  S2 → audit-log (v≥1.0.0, resolved to v1.5.0)

  S3 → S2 (v≥1.0.0, resolved)
  S3 → audit-log (v≥2.0.0, conflict! v1.5.0 too old)
                    ↑
                    └─ CYCLE DETECTED? NO
                       VERSION CONFLICT? YES → FAIL

Dependency Graph (DAG):
═══════════════════════

        notification-email (v1.2.0)
                 △
                 │
        property-search (v1.5.0) ◄─┐
                 △                  │
                 │                  │
     ┌───────────┼───────────┐     │
     │           │           │     │
  prop-val   inspec-sched  title-search
 (v1.0.0)    (v1.0.0)       (v1.1.0)
     │           │           │     │
     └─────┬─────┴─────┬─────┴─────┤
           │           │           │
        audit-log (v1.5.0 or v2.0.0?)
           │
           │
        financing-approval
        (v2.0.0) ← requires audit-log≥2.0.0
                    but only v1.5.0 resolved ✗

Result: Topological Sort Failed
  ├─ Reason: Version conflict on audit-log
  ├─ S3 requires audit-log@≥2.0.0
  ├─ But S1 dependencies resolved only to v1.5.0
  └─ Action: Manual intervention or version pin
```

---

## 10. Test Architecture (Week 4)

```
test/org/yawlfoundation/yawl/integration/a2a/skills/

Unit Tests:
  ├─ model/SkillMetadataTest.java
  │  ├─ testSerializeYaml()
  │  ├─ testSerializeTurtle()
  │  ├─ testValidate()
  │  └─ testBackwardCompatibility()
  │
  ├─ SemanticVersioningTest.java
  │  ├─ testParse()
  │  ├─ testCompare()
  │  ├─ testIsCompatible()
  │  └─ testMajorMinorPatch()
  │
  └─ BreakingChangeDetectorTest.java
     ├─ testDetectRemovedField() → breaking
     ├─ testDetectChangedType() → breaking
     ├─ testDetectAddedOptionalField() → not breaking
     ├─ testDetectRelaxedSLA() → not breaking
     └─ testDetectTightenedSLA() → breaking

Integration Tests:
  ├─ SkillPublishIntegrationTest.java
  │  ├─ testPublishSkillE2E()
  │  │  ├─ parse YAWL XML
  │  │  ├─ create SkillMetadata
  │  │  ├─ publish via API
  │  │  └─ verify Git commit
  │  │
  │  ├─ testPublishConflict()
  │  │  └─ attempt to publish same version twice → 409
  │  │
  │  └─ testVersionConstraints()
  │     └─ publish v1.0, then v1.1, then v2.0
  │
  ├─ SkillDiscoveryTest.java
  │  ├─ testSearchByCapability()
  │  ├─ testSearchByDomain()
  │  ├─ testSearchByKeyword()
  │  ├─ testSearchAccuracy() → 95%+ relevance
  │  └─ testSearchLatency() → <500ms
  │
  ├─ SkillPackIntegrationTest.java
  │  ├─ testCreatePackE2E()
  │  ├─ testResolveDependencies()
  │  ├─ testDetectCycles()
  │  ├─ testDetectVersionConflict()
  │  └─ testValidatePackSHACL()
  │
  └─ SkillPublishFullCycleTest.java
     └─ publish 10 skills → create 3 packs
        → discover all → verify relationships

Performance Tests:
  ├─ SkillSearchPerformanceTest.java
  │  ├─ testLatencyP50() < 100ms
  │  ├─ testLatencyP99() < 500ms
  │  ├─ testThroughput() > 100 qps
  │  └─ testWith1000Skills()
  │
  └─ SkillRegistryPerformanceTest.java
     ├─ testLoadTime() < 2s
     ├─ testMemoryUsage() < 500MB
     └─ testConcurrentPublish()

Test Coverage Goals:
  ├─ SkillMetadata: 90%+
  ├─ GitSkillRepository: 85%+
  ├─ SkillDiscoveryEngine: 85%+
  ├─ SkillPackService: 80%+
  └─ Overall core: 80%+
```

---

**Document Version**: 1.0
**Status**: Week 1 Deliverable
**Next**: Implementation starts Week 1, Code Review in Week 2

