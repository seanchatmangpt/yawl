# ggen Use Cases Summary for YAWL

## Overview

Based on analysis of 30+ ggen example projects, this document catalogs all discovered code generation use cases applicable to YAWL.

---

## ggen Architecture Pattern

```
RDF Ontology (.ttl)
       │
       ├── SPARQL SELECT queries
       │         │
       │         ▼
       │   Tera Templates (.tera)
       │         │
       │         ▼
       │   Generated Artifacts
       │
       └── SHACL Validation Shapes
                 │
                 ▼
           Constraint Checking
```

---

## 1. Workflow & Business Process Generation

### Existing: `yawl-workflow-platform`
**Ontology**: `ontology/platform-workflows.ttl`

| Generated Artifact | Template | Purpose |
|-------------------|----------|---------|
| YAWL XML Specifications | `yawl-spec.tera` | Workflow definitions |
| ESM Task Definitions | `task-definitions.tera` | JavaScript task modules |
| Flow Definitions | `flow-definitions.tera` | Control flow with conditions |
| Servlet Handlers | `servlet-handler.tera` | HTTP endpoint handlers |
| Worklet Handlers | `worklet-handler.tera` | Exception handling rules |
| Workflow Engine | `workflow-engine.tera` | Van der Aalst patterns |
| REST API Routes | `rest-api.tera` | Express-style routes |
| Vitest Tests | `vitest-tests.tera` | Integration tests |

### Opportunity: Extend for Java YAWL
```toml
[[generation.rules]]
name = "java-engine-classes"
query = """
PREFIX yawl: <http://unrdf.org/yawl#>
SELECT ?className ?methods ?extends
WHERE {
  ?class a yawl:EngineClass ;
         yawl:className ?className ;
         yawl:hasMethod ?methods .
  OPTIONAL { ?class yawl:extends ?extends }
}
"""
template = { file = "templates/java-class.tera" }
output_file = "src/org/yawlfoundation/yawl/engine/{{ className }}.java"
```

---

## 2. API Contract Generation

### Existing: `openapi`, `nextjs-openapi-sqlite-shadcn-vitest`

| Output | Description |
|--------|-------------|
| OpenAPI 3.0 YAML | Complete API specification |
| TypeScript Interfaces | Type-safe client types |
| Zod Schemas | Runtime validation |
| Type Guards | Runtime type checking |
| API Routes | Next.js/Express handlers |

### YAWL Application: Generate Interface B Client SDKs

```toml
[[generation.rules]]
name = "python-client"
query = """
PREFIX yawl-api: <http://unrdf.org/yawl/api#>
SELECT ?method ?path ?requestType ?responseType
WHERE {
  ?endpoint a yawl-api:Endpoint ;
            yawl-api:method ?method ;
            yawl-api:path ?path .
}
"""
template = { file = "templates/python-client.tera" }
output_file = "clients/python/yawl_client.py"
```

**Targets**: Python, TypeScript, Go, Rust, Java clients

---

## 3. DDD/CQRS Architecture Generation

### Existing: `factory-paas`

| Generated Artifact | Description |
|-------------------|-------------|
| Rust Entity Structs | Domain model |
| Command Enums | CQRS commands |
| Event Enums | Domain events |
| Aggregate Logic | DDD aggregates |
| Command Handlers | Business logic |
| Projections | Read models |
| HTTP Routes (Axum) | API endpoints |

### YAWL Application: Event-Sourced Case Store

```toml
[[generation.rules]]
name = "case-events"
query = """
PREFIX yawl-event: <http://unrdf.org/yawl/event#>
SELECT ?eventType ?payloadFields
WHERE {
  ?event a yawl-event:CaseEvent ;
         yawl-event:type ?eventType ;
         yawl-event:payload ?payloadFields .
}
"""
template = { file = "templates/case-event.tera" }
output_file = "events/{{ eventType }}.java"
```

---

## 4. Thesis & Academic Paper Generation

### Existing: `thesis-gen`

| Output | Description |
|--------|-------------|
| `thesis.tex` | Main LaTeX document |
| `chapters/*.tex` | Chapter files |
| `theorems.tex` | Theorems/proofs |
| `equations.tex` | Mathematical equations |
| `algorithms.tex` | Pseudocode |
| `figures.tex` | Figure environments |
| `tables.tex` | Table definitions |
| `references.bib` | BibTeX bibliography |

### YAWL Application: Pattern Catalog Documentation

```toml
[[generation.rules]]
name = "pattern-paper"
query = """
PREFIX yawl-pattern: <http://unrdf.org/yawl/pattern#>
SELECT ?patternId ?name ?description ?formalism
WHERE {
  ?pattern a yawl-pattern:ControlFlowPattern ;
           yawl-pattern:id ?patternId ;
           yawl-pattern:name ?name .
}
"""
template = { file = "templates/pattern-paper.tera" }
output_file = "docs/patterns/pattern-{{ patternId }}.tex"
```

---

## 5. C4 Architecture Diagrams

### Existing: `gcp-erlang-autonomics`, `factory-paas`

| Level | Diagram | Purpose |
|-------|---------|---------|
| Level 1 | Context | System context, actors |
| Level 2 | Containers | Microservices, databases |
| Level 3 | Components | Internal components |
| Level 4 | Deployment | Infrastructure |

### YAWL Application

```toml
[[generation.rules]]
name = "c4-level2"
query = """
PREFIX c4: <http://ggen.io/c4#>
PREFIX yawl: <http://unrdf.org/yawl#>
SELECT ?container ?type ?connections
WHERE {
  ?container a c4:Container ;
             c4:partOf yawl:Engine ;
             c4:type ?type .
}
"""
template = { file = "templates/c4-containers.tera" }
output_file = "docs/architecture/c2-containers.mmd"
```

---

## 6. Infrastructure as Code

### Existing: `factory-paas`, `gcp-erlang-autonomics`

| Output | Description |
|--------|-------------|
| `main.tf` | Terraform resources |
| `variables.tf` | Input variables |
| `outputs.tf` | Output values |
| `deployment.yaml` | Kubernetes manifests |
| `service.yaml` | K8s services |
| `Dockerfile` | Container images |

### YAWL Application

```toml
[[generation.rules]]
name = "terraform-yawl"
query = """
PREFIX infra: <http://ggen.io/infra#>
PREFIX yawl: <http://unrdf.org/yawl#>
SELECT ?resource ?type ?config
WHERE {
  ?resource a infra:Resource ;
            infra:supports yawl:Engine ;
            infra:type ?type .
}
"""
template = { file = "templates/terraform-resource.tera" }
output_file = "infra/{{ resource }}.tf"
```

---

## 7. Cloud Marketplace Artifacts

### New Use Case (Designed for YAWL)

| Marketplace | Artifacts |
|-------------|-----------|
| AWS | CloudFormation, ECS Task Defs, AMI |
| Azure | ARM Templates, Container Apps |
| GCP | Deployment Manager, Cloud Run |
| Kubernetes | Helm Charts, CRDs, Operators |

### Configuration

```toml
[[generation.rules]]
name = "aws-cloudformation"
query = """
PREFIX cloud: <http://ggen.io/cloud#>
PREFIX yawl: <http://unrdf.org/yawl#>
SELECT ?resourceType ?properties ?dependencies
WHERE {
  ?resource a cloud:AWSResource ;
            cloud:component yawl:Engine ;
            cloud:type ?resourceType .
}
"""
template = { file = "templates/cloudformation.tera" }
output_file = "deploy/aws/yawl-stack.yaml"
```

---

## 8. Compliance & Security Artifacts

### New Use Case (Designed for YAWL)

| Artifact | Framework |
|----------|-----------|
| SBOM (SPDX/CycloneDX) | All |
| SOC 2 Control Matrix | SOC 2 |
| GDPR ROPA | GDPR |
| HIPAA Audit Schema | HIPAA |
| FedRAMP Controls | FedRAMP |
| ISO 27001 ISMS | ISO 27001 |

### Configuration

```toml
[[generation.rules]]
name = "soc2-controls"
query = """
PREFIX compliance: <http://ggen.io/compliance#>
PREFIX yawl: <http://unrdf.org/yawl#>
SELECT ?control ?description ?evidence
WHERE {
  ?control a compliance:SOC2Control ;
           compliance:mapsTo yawl:Pattern ;
           compliance:description ?description .
}
"""
template = { file = "templates/soc2-control.tera" }
output_file = "compliance/soc2/{{ control }}.md"
```

---

## 9. Full-Stack Application Generation

### Existing: `nextjs-openapi-sqlite-shadcn-vitest`

| Layer | Generated |
|-------|-----------|
| Database | Drizzle ORM Schema |
| API | OpenAPI Spec + Routes |
| Types | TypeScript + Zod |
| UI | shadcn/ui Components |
| Tests | Vitest Unit + Integration |

### YAWL Application: Admin Dashboard

```toml
[[generation.rules]]
name = "admin-dashboard"
query = """
PREFIX yawl-ui: <http://unrdf.org/yawl/ui#>
SELECT ?page ?components ?dataBinding
WHERE {
  ?page a yawl-ui:AdminPage ;
        yawl-ui:component ?components ;
        yawl-ui:bindsTo ?dataBinding .
}
"""
template = { file = "templates/admin-page.tera" }
output_file = "admin/{{ page }}.tsx"
```

---

## 10. Framework Upgrade Automation

### New Use Case (Designed for YAWL)

| Upgrade | Pattern Detection | Transformation |
|---------|------------------|----------------|
| Java 8 → 17 | Anonymous classes | Lambda expressions |
| Java 8 → 17 | `new Integer()` | `Integer.valueOf()` |
| Java 8 → 17 | `Vector/Hashtable` | `ArrayList/HashMap` |
| Java 8 → 17 | Try-catch | Try-with-resources |
| javax → jakarta | `javax.servlet` | `jakarta.servlet` |
| Java 8 → 17 | POJO classes | `record` types |
| Java 8 → 17 | Switch statements | Switch expressions |

### Configuration

```toml
[[upgrade.rules]]
name = "lambda-conversion"
detect = "new Comparator<.*>\\(\\) \\{"
transform = "lambda-expression"
severity = "info"

[[upgrade.rules]]
name = "jakarta-migration"
detect = "import javax\\.(servlet|persistence)"
transform = "import jakarta.$1"
severity = "warning"
```

---

## 11. Refactoring Pattern Automation

### New Use Case (Designed for YAWL)

| Pattern | Automation Level |
|---------|-----------------|
| Extract Interface | High |
| Immutable Value Object | High |
| Builder Pattern | High |
| Strategy Pattern | High |
| Factory Method | High |
| Null Object | Medium |
| State Pattern | Medium |

### Configuration

```toml
[[refactoring.rules]]
name = "extract-interface"
target = "YEngine"
interfaces = ["InterfaceADesign", "InterfaceAManagement", "InterfaceBClient"]
output = "src/org/yawlfoundation/yawl/engine/YWorkflowEngine.java"
```

---

## 12. Test Generation

### Existing: Multiple examples

| Test Type | Output |
|-----------|--------|
| Unit Tests | JUnit/Vitest |
| Integration Tests | Testcontainers |
| Property Tests | QuickCheck/Proptest |
| Concurrency Tests | Thread-based stress tests |
| Mutation Tests | Pitest/Cargo-mutants |

### YAWL Configuration

```toml
[[generation.rules]]
name = "junit-from-shacl"
query = """
PREFIX sh: <http://www.w3.org/ns/shacl#>
PREFIX yawl: <http://unrdf.org/yawl#>
SELECT ?shape ?constraint ?message
WHERE {
  ?shape a sh:NodeShape ;
         sh:targetClass yawl:Specification ;
         sh:property ?constraint .
  ?constraint sh:message ?message .
}
"""
template = { file = "templates/junit-shacl.tera" }
output_file = "test/org/yawlfoundation/yawl/Test{{ shape }}.java"
```

---

## Summary: ggen Capabilities Matrix

| Category | Existing Examples | YAWL Applicability |
|----------|-------------------|-------------------|
| **Workflow Generation** | yawl-workflow-platform | ✅ Direct use |
| **API Contracts** | openapi, nextjs-* | ✅ Interface B SDKs |
| **DDD/CQRS** | factory-paas | ✅ Event-sourced engine |
| **Academic Papers** | thesis-gen | ✅ Pattern documentation |
| **C4 Diagrams** | gcp-erlang-autonomics | ✅ Architecture docs |
| **Infrastructure** | factory-paas | ✅ Terraform/K8s |
| **Cloud Marketplace** | (New) | ✅ AWS/Azure/GCP |
| **Compliance** | (New) | ✅ SOC2/GDPR/HIPAA |
| **Full-Stack Apps** | nextjs-* | ✅ Admin dashboard |
| **Framework Upgrades** | (New) | ✅ Java 8→17 migration |
| **Refactoring** | (New) | ✅ Design pattern application |
| **Test Generation** | Multiple | ✅ SHACL→JUnit |

---

## Recommended ggen.toml for YAWL

```toml
# =============================================================================
# YAWL v5.2 ggen Manifest - Comprehensive Code Generation
# =============================================================================

[project]
name = "yawl-codegen"
version = "5.2.0"
description = "Ontology-driven code generation for YAWL workflow engine"

[ontology]
source = ".specify/yawl-ontology.ttl"
schema = ".specify/yawl-shapes.ttl"
imports = [".specify/patterns/*.ttl"]

[ontology.prefixes]
yawl = "http://yawlfoundation.org/yawl#"
yawl-api = "http://yawlfoundation.org/yawl/api#"
yawl-pattern = "http://yawlfoundation.org/yawl/pattern#"

# Include all generation rules from categories above...
```

---

## Next Steps

1. **Create YAWL ggen.toml** - Unified manifest for all generation rules
2. **Implement Java Templates** - Tera templates for Java code generation
3. **Create SPARQL Queries** - Queries for each generation rule
4. **Set up CI Integration** - Run `ggen sync` as part of build
5. **Document Patterns** - Use thesis-gen for pattern catalog

---

**Generated**: 2026-02-16
**Sources**: ~/ggen/examples/* (30+ projects analyzed)
