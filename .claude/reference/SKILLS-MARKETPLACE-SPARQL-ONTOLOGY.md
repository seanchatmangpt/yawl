# Skills Marketplace SPARQL Queries & RDF Ontology

**Week 3 Deliverable** — Discovery engine reference

---

## Part 1: RDF Ontology (skill-ontology.ttl)

### 1.1 Core Classes

```turtle
@prefix : <http://gregverse.org/skill#> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
@prefix owl: <http://www.w3.org/2002/07/owl#> .

# ===== CLASS HIERARCHY =====

:Skill a owl:Class ;
    rdfs:label "YAWL Skill (Reusable Workflow)" ;
    rdfs:comment "A publishable, versioned YAWL workflow available in the marketplace" ;
    owl:disjointWith :SkillPack ;
    owl:disjointWith :Dependency .

:SkillVersion a owl:Class ;
    rdfs:label "Specific version of a skill" ;
    rdfs:comment "Points to semantic version (1.0.0)" .

:SkillPack a owl:Class ;
    rdfs:label "Bundle of related skills" ;
    rdfs:comment "Vertical pack (e.g., real-estate-acquisition)" .

:Interface a owl:Class ;
    rdfs:label "Invocation interface (A2A, REST, SPARQL)" ;
    rdfs:comment "How to call a skill" .

:InputSchema a owl:Class ;
    rdfs:label "Input specification" ;
    rdfs:subClassOf :DataSchema .

:OutputSchema a owl:Class ;
    rdfs:label "Output specification" ;
    rdfs:subClassOf :DataSchema .

:DataSchema a owl:Class ;
    rdfs:label "Data structure definition" .

:SLAContract a owl:Class ;
    rdfs:label "Service-level agreement" ;
    rdfs:comment "Availability, latency, success rate" .

:WorkflowImplementation a owl:Class ;
    rdfs:label "Reference to YAWL implementation" .

:Dependency a owl:Class ;
    rdfs:label "Skill dependency" ;
    rdfs:comment "References another skill with version constraint" .

:Domain a owl:Class ;
    rdfs:label "Industry/business domain" ;
    rdfs:comment "e.g., real-estate, finance, healthcare" .

:WorkflowPattern a owl:Class ;
    rdfs:label "Petri net pattern" ;
    rdfs:comment "e.g., multiple_instance, parallel, exclusive_choice" .

# ===== PROPERTY: OBJECT (IRI) =====

:hasVersion a owl:ObjectProperty ;
    rdfs:label "points to specific version" ;
    rdfs:domain :Skill ;
    rdfs:range :SkillVersion .

:inputSchema a owl:ObjectProperty ;
    rdfs:label "input data structure" ;
    rdfs:domain :Skill ;
    rdfs:range :InputSchema .

:outputSchema a owl:ObjectProperty ;
    rdfs:label "output data structure" ;
    rdfs:domain :Skill ;
    rdfs:range :OutputSchema .

:hasSLA a owl:ObjectProperty ;
    rdfs:label "SLA contract" ;
    rdfs:domain :Skill ;
    rdfs:range :SLAContract .

:hasField a owl:ObjectProperty ;
    rdfs:label "contains data field" ;
    rdfs:domain :DataSchema ;
    rdfs:range :DataField .

:implementedBy a owl:ObjectProperty ;
    rdfs:label "YAWL workflow" ;
    rdfs:domain :Skill ;
    rdfs:range :WorkflowImplementation .

:invocableVia a owl:ObjectProperty ;
    rdfs:label "invocation interface" ;
    rdfs:domain :Skill ;
    rdfs:range :Interface .

:dependsOn a owl:ObjectProperty ;
    rdfs:label "depends on another skill" ;
    rdfs:domain :Skill ;
    rdfs:range :Dependency .

:targetSkill a owl:ObjectProperty ;
    rdfs:label "skill reference in dependency" ;
    rdfs:domain :Dependency ;
    rdfs:range :Skill .

:inDomain a owl:ObjectProperty ;
    rdfs:label "classified under domain" ;
    rdfs:domain :Skill ;
    rdfs:range :Domain .

:hasPattern a owl:ObjectProperty ;
    rdfs:label "Petri net pattern" ;
    rdfs:domain :Skill ;
    rdfs:range :WorkflowPattern .

:previousVersion a owl:ObjectProperty ;
    rdfs:label "version before this one" ;
    rdfs:domain :SkillVersion ;
    rdfs:range :SkillVersion .

:includes a owl:ObjectProperty ;
    rdfs:label "pack includes skill" ;
    rdfs:domain :SkillPack ;
    rdfs:range :Skill .

# ===== PROPERTY: DATATYPE =====

:versionNumber a owl:DatatypeProperty ;
    rdfs:label "semantic version" ;
    rdfs:domain :SkillVersion ;
    rdfs:range xsd:string ;
    rdfs:comment "Format: MAJOR.MINOR.PATCH" .

:fieldName a owl:DatatypeProperty ;
    rdfs:label "field identifier" ;
    rdfs:domain :DataField ;
    rdfs:range xsd:string .

:fieldType a owl:DatatypeProperty ;
    rdfs:label "data type" ;
    rdfs:domain :DataField ;
    rdfs:range xsd:string ;
    rdfs:comment "e.g., uuid, string, integer, array<string>" .

:required a owl:DatatypeProperty ;
    rdfs:label "is field required" ;
    rdfs:domain :DataField ;
    rdfs:range xsd:boolean .

:minOccurs a owl:DatatypeProperty ;
    rdfs:label "minimum occurrences" ;
    rdfs:domain :DataField ;
    rdfs:range xsd:nonNegativeInteger .

:maxOccurs a owl:DatatypeProperty ;
    rdfs:label "maximum occurrences" ;
    rdfs:domain :DataField ;
    rdfs:range xsd:nonNegativeInteger .

:maxResponseTime a owl:DatatypeProperty ;
    rdfs:label "SLA: max response time" ;
    rdfs:domain :SLAContract ;
    rdfs:range xsd:duration ;
    rdfs:comment "ISO 8601 format: PT4H" .

:minSuccessRate a owl:DatatypeProperty ;
    rdfs:label "SLA: minimum success rate" ;
    rdfs:domain :SLAContract ;
    rdfs:range xsd:string ;
    rdfs:comment "e.g., 99.5%" .

:supportedErrorCode a owl:DatatypeProperty ;
    rdfs:label "SLA: supported error code" ;
    rdfs:domain :SLAContract ;
    rdfs:range xsd:string ;
    rdfs:comment "e.g., TimeoutException, InvalidInput" .

:versionConstraint a owl:DatatypeProperty ;
    rdfs:label "semantic version constraint" ;
    rdfs:domain :Dependency ;
    rdfs:range xsd:string ;
    rdfs:comment "e.g., >=1.0.0, ~1.2.3, ^1.0.0" .

:breakingChange a owl:DatatypeProperty ;
    rdfs:label "is major version change" ;
    rdfs:domain :SkillVersion ;
    rdfs:range xsd:boolean .

:keyword a owl:DatatypeProperty ;
    rdfs:label "searchable keyword" ;
    rdfs:domain :Skill ;
    rdfs:range xsd:string .

:yawlFile a owl:DatatypeProperty ;
    rdfs:label "YAWL file path" ;
    rdfs:domain :WorkflowImplementation ;
    rdfs:range xsd:string .

:yawlContentHash a owl:DatatypeProperty ;
    rdfs:label "SHA256 hash of YAWL content" ;
    rdfs:domain :WorkflowImplementation ;
    rdfs:range xsd:string ;
    rdfs:comment "Immutable reference to exact YAWL version" .

:petriNetSound a owl:DatatypeProperty ;
    rdfs:label "Petri net soundness verified" ;
    rdfs:domain :WorkflowImplementation ;
    rdfs:range xsd:boolean .

# ===== DUBLIN CORE =====

dcterms:title rdfs:domain :Skill .
dcterms:description rdfs:domain :Skill .
dcterms:creator rdfs:domain :Skill ; rdfs:range xsd:string .
dcterms:issued rdfs:domain :Skill ; rdfs:range xsd:dateTime .
dcterms:modified rdfs:domain :Skill ; rdfs:range xsd:dateTime .
dcterms:license rdfs:domain :Skill .
dcterms:language rdfs:domain :Skill ; rdfs:range xsd:string .
dcterms:accessControl rdfs:domain :Skill ; rdfs:range xsd:string .
    # Values: PUBLIC, PRIVATE, RESTRICTED
```

### 1.2 Predefined Domains & Patterns

```turtle
# ===== DOMAINS =====

:Domain_RealEstate a :Domain ;
    rdfs:label "Real Estate" ;
    dcterms:description "Property acquisition, sales, leasing" .

:Domain_Finance a :Domain ;
    rdfs:label "Finance" ;
    dcterms:description "Settlement, payments, treasury" .

:Domain_Healthcare a :Domain ;
    rdfs:label "Healthcare" ;
    dcterms:description "Claims processing, insurance" .

:Domain_HumanResources a :Domain ;
    rdfs:label "Human Resources" ;
    dcterms:description "Onboarding, payroll, benefits" .

:Domain_Supply_Chain a :Domain ;
    rdfs:label "Supply Chain" ;
    dcterms:description "Procurement, logistics, fulfillment" .

# ===== PETRI NET PATTERNS =====

:Pattern_MultipleInstance a :WorkflowPattern ;
    rdfs:label "Multiple Instance Pattern" ;
    dcterms:description "Execute task N times in parallel" ;
    dcterms:reference "van der Aalst, 43 workflow patterns" .

:Pattern_ExclusiveChoice a :WorkflowPattern ;
    rdfs:label "Exclusive Choice" ;
    dcterms:description "Choose one of multiple paths" .

:Pattern_ParallelSplit a :WorkflowPattern ;
    rdfs:label "Parallel Split" ;
    dcterms:description "Execute multiple tasks in parallel" .

:Pattern_Synchronization a :WorkflowPattern ;
    rdfs:label "Synchronization (Parallel Join)" ;
    dcterms:description "Wait for all parallel tasks to complete" .

:Pattern_ArbitraryDeferredChoice a :WorkflowPattern ;
    rdfs:label "Arbitrary Deferred Choice" ;
    dcterms:description "Interleaved execution paths" .

:Pattern_EventBasedGateway a :WorkflowPattern ;
    rdfs:label "Event-Based Gateway" ;
    dcterms:description "Choose based on external event" .

:Pattern_Milestone a :WorkflowPattern ;
    rdfs:label "Milestone Pattern" ;
    dcterms:description "Reachable conditions" .

:Pattern_CancellationRegion a :WorkflowPattern ;
    rdfs:label "Cancellation Region" ;
    dcterms:description "Compensating transactions" .
```

---

## Part 2: SHACL Validation Shapes (skill-shapes.ttl)

```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix skill: <http://gregverse.org/skill#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

# ===== SKILL VALIDATION SHAPE =====

:SkillShape a sh:NodeShape ;
    sh:targetClass skill:Skill ;
    sh:name "Skill Validation" ;
    sh:description "Validates skill metadata structure and content" ;

    # Required properties
    sh:property [
        sh:path dcterms:title ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
        sh:minLength 3 ;
        sh:maxLength 200 ;
    ] ;

    sh:property [
        sh:path dcterms:description ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
        sh:minLength 10 ;
        sh:maxLength 2000 ;
    ] ;

    sh:property [
        sh:path dcterms:creator ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
    ] ;

    sh:property [
        sh:path skill:versionNumber ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
        sh:pattern "^\\d+\\.\\d+\\.\\d+$" ;
        sh:message "Version must be in format MAJOR.MINOR.PATCH" ;
    ] ;

    sh:property [
        sh:path skill:inputSchema ;
        sh:minCount 0 ;
        sh:maxCount 1 ;
        sh:node :InputSchemaShape ;
    ] ;

    sh:property [
        sh:path skill:outputSchema ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:node :OutputSchemaShape ;
    ] ;

    sh:property [
        sh:path skill:hasSLA ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:node :SLAShape ;
    ] ;

    sh:property [
        sh:path skill:implementedBy ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:node :WorkflowImplementationShape ;
    ] ;

    # Custom SPARQL constraint: version uniqueness
    sh:sparqlConstraint [
        sh:message "Skill version must be unique within organization" ;
        sh:sparql """
            PREFIX skill: <http://gregverse.org/skill#>
            SELECT $this
            WHERE {
                $this skill:versionNumber ?version ;
                    dcterms:creator ?creator .

                ?other skill:versionNumber ?version ;
                    dcterms:creator ?creator ;
                    dcterms:title ?otherTitle .

                FILTER($this != ?other)
            }
        """ ;
    ] .

# ===== INPUT SCHEMA SHAPE =====

:InputSchemaShape a sh:NodeShape ;
    sh:targetClass skill:InputSchema ;
    sh:name "Input Schema Validation" ;

    sh:property [
        sh:path skill:hasField ;
        sh:minCount 1 ;
        sh:node :DataFieldShape ;
        sh:message "Input schema must have at least 1 field" ;
    ] .

# ===== OUTPUT SCHEMA SHAPE =====

:OutputSchemaShape a sh:NodeShape ;
    sh:targetClass skill:OutputSchema ;
    sh:name "Output Schema Validation" ;

    sh:property [
        sh:path skill:hasField ;
        sh:minCount 1 ;
        sh:node :DataFieldShape ;
        sh:message "Output schema must have at least 1 field" ;
    ] .

# ===== DATA FIELD SHAPE =====

:DataFieldShape a sh:NodeShape ;
    sh:targetClass skill:DataField ;
    sh:name "Data Field Validation" ;

    sh:property [
        sh:path skill:fieldName ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
        sh:minLength 1 ;
        sh:maxLength 100 ;
    ] ;

    sh:property [
        sh:path skill:fieldType ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
        sh:in ( "uuid" "string" "integer" "boolean" "decimal"
                "array<string>" "array<integer>" "object" "xml" ) ;
    ] ;

    sh:property [
        sh:path skill:required ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:boolean ;
    ] .

# ===== SLA SHAPE =====

:SLAShape a sh:NodeShape ;
    sh:targetClass skill:SLAContract ;
    sh:name "SLA Validation" ;

    sh:property [
        sh:path skill:maxResponseTime ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:duration ;
        sh:pattern "^PT([0-9]+H|[0-9]+M|[0-9]+S)$" ;
        sh:message "Max response time must be ISO 8601 duration (e.g., PT4H)" ;
    ] ;

    sh:property [
        sh:path skill:minSuccessRate ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
        sh:pattern "^(100(\\.0+)?|[0-9]{1,2}(\\.[0-9]+)?)%$" ;
        sh:message "Success rate must be percentage: 0-100" ;
    ] ;

    sh:property [
        sh:path skill:supportedErrorCode ;
        sh:minCount 1 ;
        sh:datatype xsd:string ;
    ] .

# ===== WORKFLOW IMPLEMENTATION SHAPE =====

:WorkflowImplementationShape a sh:NodeShape ;
    sh:targetClass skill:WorkflowImplementation ;
    sh:name "Workflow Implementation Validation" ;

    sh:property [
        sh:path skill:yawlFile ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
        sh:pattern "\\.yawl$" ;
    ] ;

    sh:property [
        sh:path skill:yawlContentHash ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
        sh:pattern "^sha256:[a-f0-9]{64}$" ;
    ] ;

    sh:property [
        sh:path skill:petriNetSound ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:boolean ;
        sh:message "Petri net must be verified for soundness" ;
    ] .

# ===== SKILL PACK SHAPE =====

:SkillPackShape a sh:NodeShape ;
    sh:targetClass skill:SkillPack ;
    sh:name "Skill Pack Validation" ;

    sh:property [
        sh:path dcterms:title ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
    ] ;

    sh:property [
        sh:path skill:includes ;
        sh:minCount 2 ;
        sh:message "Pack must include at least 2 skills" ;
    ] ;

    sh:property [
        sh:path skill:inDomain ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:node :DomainShape ;
    ] ;

    # Custom constraint: no circular dependencies
    sh:sparqlConstraint [
        sh:message "Pack must not have circular dependencies" ;
        sh:sparql """
            PREFIX skill: <http://gregverse.org/skill#>
            SELECT $this
            WHERE {
                # If skill A depends on B, and B depends on A → cycle
                ?skillA skill:dependsOn / skill:targetSkill ?skillB .
                ?skillB skill:dependsOn / skill:targetSkill ?skillA .
            }
        """ ;
    ] .

# ===== DOMAIN SHAPE =====

:DomainShape a sh:NodeShape ;
    sh:targetClass skill:Domain ;
    sh:name "Domain Validation" ;

    sh:property [
        sh:path rdfs:label ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
    ] .
```

---

## Part 3: SPARQL Queries (5 Core)

### Query 1: Search Skills by Input/Output Capability

**File**: `.skills/sparql/queries/SkillsByCapability.sparql`

```sparql
PREFIX skill: <http://gregverse.org/skill#>
PREFIX dcterms: <http://purl.org/dc/terms/>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

# Find skills matching required input types and producing required output types
#
# Parameters:
#   ?requiredInputTypes: List of field types we need (e.g., "uuid", "string")
#   ?requiredOutputTypes: List of field types we need from result
#
# Returns: Skills ranked by relevance

SELECT ?skillId ?versionNumber ?title ?author ?relevance
WHERE {
    # Find all skills
    ?skill a skill:Skill ;
        dcterms:title ?title ;
        dcterms:creator ?author ;
        skill:versionNumber ?versionNumber .

    # Extract skill ID from RDF resource URI
    BIND(STRAFTER(STR(?skill), "/skill/") AS ?skillId)

    # Check input requirements
    ?skill skill:inputSchema / skill:hasField ?inputField ;
        skill:inputSchema / skill:hasField [
            skill:fieldType ?inputType ;
            skill:required true
        ] .

    # Check output requirements
    ?skill skill:outputSchema / skill:hasField [
        skill:fieldType ?outputType ;
        skill:required true
    ] .

    # Calculate relevance score
    # - Exact match: +0.5 (both input and output match)
    # - Partial match: +0.3 (either input or output)
    # - Version bonus: +0.05 (newer version)
    BIND(
        IF(
            (?inputType IN ("uuid", "string", "object")) AND
            (?outputType IN ("status", "trace", "object")),
            0.5,
            IF(
                ?inputType IN ("uuid", "string", "object") OR
                ?outputType IN ("status", "trace", "object"),
                0.3,
                0.0
            )
        ) AS ?relevance
    )

    FILTER(?relevance > 0.0)
}
ORDER BY DESC(?relevance) DESC(?versionNumber)
LIMIT 20
```

**Example Invocation** (HTTP POST to SPARQL endpoint):

```
POST /sparql
Content-Type: application/sparql-query

PREFIX skill: <http://gregverse.org/skill#>
SELECT ?skillId ?relevance
WHERE {
    ?skill skill:outputSchema / skill:hasField [
        skill:fieldType "uuid" ;
        skill:required true
    ] ;
    skill:outputSchema / skill:hasField [
        skill:fieldType "status" ;
        skill:required true
    ] .
    ...
}
ORDER BY DESC(?relevance)
```

---

### Query 2: Search Skills by Domain

**File**: `.skills/sparql/queries/SkillsByDomain.sparql`

```sparql
PREFIX skill: <http://gregverse.org/skill#>
PREFIX dcterms: <http://purl.org/dc/terms/>

# Find all skills in a given domain
#
# Parameters:
#   ?domainLabel: Domain name (e.g., "Real Estate")
#
# Returns: All skills in domain, latest version first

SELECT ?skillId ?title ?version ?author ?description
WHERE {
    # Find domain by label
    ?domain a skill:Domain ;
        rdfs:label ?domainLabel ;
        FILTER(CONTAINS(LCASE(?domainLabel), LCASE($domainInput))) .

    # Find skills in that domain
    ?skill a skill:Skill ;
        skill:inDomain ?domain ;
        dcterms:title ?title ;
        dcterms:description ?description ;
        dcterms:creator ?author ;
        skill:versionNumber ?version .

    BIND(STRAFTER(STR(?skill), "/skill/") AS ?skillId)

    # Filter: only latest version of each skill
    # (group by skillId, select max version)
}
GROUP BY ?skillId
ORDER BY DESC(?version)
```

---

### Query 3: Detect Incompatible Versions

**File**: `.skills/sparql/queries/IncompatibleVersions.sparql`

```sparql
PREFIX skill: <http://gregverse.org/skill#>
PREFIX dcterms: <http://purl.org/dc/terms/>

# Detect breaking changes between two versions
#
# Parameters:
#   ?skillId: Skill identifier (e.g., "approval")
#   ?versionOld: Old version (e.g., "1.0.0")
#   ?versionNew: New version (e.g., "2.0.0")
#
# Returns: List of breaking changes found

SELECT ?changeType ?fieldName ?oldType ?newType
WHERE {
    # Fetch old version
    ?skillOld a skill:Skill ;
        FILTER(CONTAINS(STR(?skillOld), $skillId)) ;
        skill:versionNumber $versionOld ;
        skill:outputSchema / skill:hasField ?fieldOld .

    ?fieldOld skill:fieldName ?fieldName ;
        skill:fieldType ?oldType ;
        skill:required ?oldRequired .

    # Fetch new version
    ?skillNew a skill:Skill ;
        FILTER(CONTAINS(STR(?skillNew), $skillId)) ;
        skill:versionNumber $versionNew ;
        skill:outputSchema ?newSchema .

    # Check: is field removed in new version?
    OPTIONAL {
        ?newSchema / skill:hasField [
            skill:fieldName ?fieldName ;
            skill:fieldType ?newType ;
            skill:required ?newRequired
        ] .
    }

    # Detect breaking changes
    BIND(
        IF(
            BOUND(?newType),
            IF(
                ?oldRequired AND !?newRequired,
                "FIELD_REQUIRED_REMOVED",
                IF(
                    ?oldType != ?newType,
                    "FIELD_TYPE_CHANGED",
                    "COMPATIBLE"
                )
            ),
            IF(
                ?oldRequired = true,
                "FIELD_REMOVED",
                "COMPATIBLE"
            )
        ) AS ?changeType
    )

    FILTER(?changeType != "COMPATIBLE")
}
```

---

### Query 4: Detect Circular Dependencies

**File**: `.skills/sparql/queries/CircularDependencies.sparql`

```sparql
PREFIX skill: <http://gregverse.org/skill#>
PREFIX dcterms: <http://purl.org/dc/terms/>

# Find circular skill dependencies (A→B→C→A)
#
# Returns: Skills involved in cycles

SELECT ?cycle
WHERE {
    # Skill A depends on Skill B
    ?skillA a skill:Skill ;
        skill:dependsOn [
            skill:targetSkill ?skillB
        ] .

    # Skill B depends on Skill A (creates 2-cycle)
    ?skillB a skill:Skill ;
        skill:dependsOn [
            skill:targetSkill ?skillA
        ] .

    # Build human-readable cycle string
    BIND(CONCAT(STR(?skillA), " → ", STR(?skillB), " → ", STR(?skillA)) AS ?cycle)
}
```

---

### Query 5: Resolve Version Constraint (with fallback)

**File**: `.skills/sparql/queries/ResolveVersionConstraint.sparql`

```sparql
PREFIX skill: <http://gregverse.org/skill#>
PREFIX dcterms: <http://purl.org/dc/terms/>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

# Resolve version constraint (e.g., >=1.0.0, ~1.2.3) to actual version
#
# Parameters:
#   ?skillId: Skill identifier
#   ?constraint: Version constraint (e.g., ">=1.0.0")
#
# Returns: Best matching version

SELECT ?resolvedVersion
WHERE {
    # Find all versions of skill
    ?skill a skill:Skill ;
        FILTER(CONTAINS(STR(?skill), $skillId)) ;
        skill:versionNumber ?availableVersion .

    # Parse constraint (simplified: >=X.Y.Z or =X.Y.Z or ~X.Y.Z)
    BIND(
        IF(
            CONTAINS($constraint, ">="),
            SUBSTR($constraint, 3),
            IF(
                CONTAINS($constraint, "~"),
                SUBSTR($constraint, 2),
                $constraint
            )
        ) AS ?constraintValue
    )

    # Simple semantic version comparison (string-based, works for most cases)
    # TODO: Implement proper semantic version parsing
    BIND(
        IF(
            xsd:decimal(?availableVersion) >= xsd:decimal(?constraintValue),
            ?availableVersion,
            UNDEF
        ) AS ?matchingVersion
    )

    # Return highest matching version
}
ORDER BY DESC(?resolvedVersion)
LIMIT 1
```

---

## Part 4: SPARQL Endpoint Setup

### 4.1 Jena Fuseki Configuration

```yaml
# File: .skills/fuseki-config.ttl

@prefix : <http://example.org/> .
@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix ja: <http://jena.apache.org/2016/08/configuration#> .
@prefix fuseki: <http://jena.apache.org/fuseki/config#> .

:service_tdb2 a fuseki:Service ;
    fuseki:name "gregverse-registry" ;
    fuseki:dataset :dataset ;
    fuseki:endpoint [
        fuseki:operation fuseki:query ;
        fuseki:name "sparql"
    ] ;
    fuseki:endpoint [
        fuseki:operation fuseki:gsp_r ;
        fuseki:name "data"
    ] ;
    fuseki:endpoint [
        fuseki:operation fuseki:update ;
        fuseki:name "update"
    ] ;
    fuseki:endpoint [
        fuseki:operation fuseki:gsp_rw ;
        fuseki:name "service"
    ] .

:dataset a ja:RDFDataset ;
    ja:defaultGraph :graph .

:graph a ja:MemoryDataset ;
    ja:graph <http://gregverse.org/> .
```

### 4.2 Query Execution (Java)

```java
// SkillDiscoveryEngine.java (Week 3)
public class SkillDiscoveryEngine {
    private String sparqlEndpoint = "http://localhost:3030/gregverse-registry/sparql";

    public List<SkillMetadata> searchByCapability(String inputType, String outputType) {
        String query = """
            PREFIX skill: <http://gregverse.org/skill#>
            SELECT ?skillId ?versionNumber ?title
            WHERE {
                ?skill a skill:Skill ;
                    skill:outputSchema / skill:hasField [
                        skill:fieldType ?outputType ;
                        skill:required true
                    ] .
                BIND(STRAFTER(STR(?skill), "/skill/") AS ?skillId)
            }
            ORDER BY DESC(?versionNumber)
            LIMIT 20
            """;

        try (QueryExecution qexec = QueryExecutionFactory
                .sparqlService(sparqlEndpoint, query)) {

            List<SkillMetadata> results = new ArrayList<>();
            ResultSet rs = qexec.execSelect();

            while (rs.hasNext()) {
                QuerySolution soln = rs.nextSolution();
                String skillId = soln.getLiteral("?skillId").getString();
                String version = soln.getLiteral("?versionNumber").getString();

                SkillMetadata skill = registry.getVersion(skillId, version);
                results.add(skill);
            }

            return results;
        }
    }

    public List<BreakingChange> detectBreakingChanges(
            String skillId, String vOld, String vNew) {

        String query = """
            PREFIX skill: <http://gregverse.org/skill#>
            SELECT ?fieldName ?changeType
            WHERE {
                ?skillOld skill:versionNumber "${vOld}" ;
                    skill:outputSchema / skill:hasField ?fieldOld .
                ?fieldOld skill:fieldName ?fieldName ;
                    skill:required true .

                OPTIONAL {
                    ?skillNew skill:versionNumber "${vNew}" ;
                        skill:outputSchema / skill:hasField [
                            skill:fieldName ?fieldName
                        ] .
                }

                BIND(
                    IF(BOUND(?skillNew), "COMPATIBLE", "FIELD_REMOVED")
                    AS ?changeType
                )
            }
            """;

        try (QueryExecution qexec = QueryExecutionFactory
                .sparqlService(sparqlEndpoint, query)) {

            List<BreakingChange> changes = new ArrayList<>();
            ResultSet rs = qexec.execSelect();

            while (rs.hasNext()) {
                QuerySolution soln = rs.nextSolution();
                if (soln.getLiteral("?changeType")
                        .getString()
                        .equals("FIELD_REMOVED")) {
                    String fieldName = soln.getLiteral("?fieldName").getString();
                    changes.add(new BreakingChange(fieldName, "REMOVED"));
                }
            }

            return changes;
        }
    }
}
```

---

## Part 5: Query Performance Tuning

### 5.1 Indexing Strategy

**RDF Triple Store Indexes** (for Jena TDB2):

```
Default indexes:
  - SPO (subject, predicate, object)
  - POS (by predicate first)
  - OSP (by object first)

Custom indexes needed:
  - skill:versionNumber (frequent filter)
  - skill:fieldType (frequent in discovery)
  - skill:required (frequent in schema queries)
  - dcterms:creator (filter by author)
  - skill:inDomain (filter by domain)

Result: 6 indexes total = ~2× storage, ~1.5× faster queries
```

### 5.2 Query Optimization

**Before** (naive):

```sparql
SELECT ?skill
WHERE {
    ?skill a skill:Skill .
    ?skill skill:outputSchema / skill:hasField [
        skill:fieldType "uuid" ;
        skill:required true
    ] .
    # Unfiltered: joins on every triple
}
```

**After** (optimized):

```sparql
SELECT ?skill
WHERE {
    # Filter early: use most selective constraint first
    ?skill a skill:Skill ;
        skill:outputSchema ?schema ;
        skill:versionNumber ?version .

    FILTER(REGEX(?version, "^[1-9]")) # Version must exist

    # Now traverse properties
    ?schema skill:hasField ?field .
    ?field skill:fieldType "uuid" ;
        skill:required true .
}
ORDER BY DESC(?version)
```

**Expected Results**:
- Before: 2-3 seconds (10K skills)
- After: <200ms (10K skills)

---

## Part 6: Testing SPARQL Queries

### 6.1 Sample Data (Test Fixture)

```turtle
# File: test/resources/skills-test-data.ttl

@prefix : <http://gregverse.org/skill#> .
@prefix skill: <http://gregverse.org/skill#> .
@prefix dcterms: <http://purl.org/dc/terms/> .

# Skill 1: Approval (v1.0.0)
:approval_v1_0 a skill:Skill ;
    dcterms:title "Approval Workflow" ;
    dcterms:creator "AccelOrg" ;
    skill:versionNumber "1.0.0" ;
    skill:outputSchema :approval_output_v1 ;
    skill:inDomain :Domain_RealEstate .

:approval_output_v1 a skill:OutputSchema ;
    skill:hasField [
        skill:fieldName "approvalStatus" ;
        skill:fieldType "string" ;
        skill:required true
    ] ;
    skill:hasField [
        skill:fieldName "approvalTrace" ;
        skill:fieldType "object" ;
        skill:required true
    ] .

# Skill 2: Approval (v2.0.0) - Breaking change: removed approvalTrace
:approval_v2_0 a skill:Skill ;
    dcterms:title "Approval Workflow v2" ;
    dcterms:creator "AccelOrg" ;
    skill:versionNumber "2.0.0" ;
    skill:breakingChange true ;
    skill:previousVersion :approval_v1_0 ;
    skill:outputSchema :approval_output_v2 ;
    skill:inDomain :Domain_RealEstate .

:approval_output_v2 a skill:OutputSchema ;
    skill:hasField [
        skill:fieldName "approvalStatus" ;
        skill:fieldType "string" ;
        skill:required true
    ] .
    # approvalTrace removed → breaking change!

# ... more test skills
```

### 6.2 JUnit Test

```java
// SkillDiscoveryTest.java (Week 3)
public class SkillDiscoveryTest {
    private SkillDiscoveryEngine engine;

    @BeforeEach
    void setup() throws Exception {
        // Load test data into RDF store
        JenaRDFStore store = new JenaRDFStore();
        store.loadTurtle("test/resources/skills-test-data.ttl");

        engine = new SkillDiscoveryEngine(store.getSparqlEndpoint());
    }

    @Test
    void testSearchByCapability_FindsApprovalSkill() {
        List<SkillMetadata> results = engine.searchByCapability("uuid", "string");

        assertThat(results)
            .extracting(s -> s.getId())
            .contains("approval");
    }

    @Test
    void testBreakingChangeDetection() {
        List<BreakingChange> changes = engine.detectBreakingChanges(
            "approval", "1.0.0", "2.0.0"
        );

        assertThat(changes)
            .extracting(c -> c.getFieldName())
            .contains("approvalTrace");

        assertThat(changes.get(0).getType()).isEqualTo("FIELD_REMOVED");
    }

    @Test
    void testSearchPerformance_1000Skills() {
        // Load 1000 sample skills
        loadNSkills(1000);

        Instant start = Instant.now();
        List<SkillMetadata> results = engine.searchByCapability("uuid", "string");
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(elapsed).isLessThan(Duration.ofMillis(500));
    }
}
```

---

**Document Version**: 1.0
**Status**: Week 3 Implementation Reference
**Next**: SPARQL endpoint deployment + optimization

