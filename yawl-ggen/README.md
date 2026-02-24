# yawl-ggen — Code Generation Engine

Deterministic code generation for YAWL specifications via RDF + SPARQL + template rendering. Implements the GODSPEED methodology: generate → validate (H guards) → validate (Q invariants) → emit.

## Purpose

Convert external process representations (BPMN, PNML, XES event logs) into YAWL specifications and Java artefacts. Cloud mining clients pull process models from Signavio, Celonis, and UiPath; the local pipeline synthesises valid YAWL XML from the inferred Petri-net structure.

## Key Packages

| Package | Contents |
|---------|----------|
| `ggen.api` | `ProcessConversionServlet` — REST endpoint; `InMemoryJobQueue` — async job tracking |
| `ggen.mining.cloud` | `CelonicsMiningClient`, `SignavioClient`, `UiPathAutomationClient` — cloud connectors |
| `ggen.mining.generators` | `BpelExporter`, `CamundaBpmnExporter`, `TerraformGenerator` — output format writers |
| `ggen.mining.model` | `PetriNet`, `Place`, `Transition`, `Arc` — internal Petri-net representation |
| `ggen.mining.parser` | `BpmnParser`, `PnmlParser`, `XesParser` — input format parsers |
| `ggen.mining.rdf` | `RdfAstConverter` — AST → RDF triples for SPARQL-based validation |

## Build

```bash
# Compile only
bash scripts/dx.sh -pl yawl-ggen

# Full module build with tests
bash scripts/dx.sh -pl yawl-ggen all
```

## Test

```bash
mvn test -pl yawl-ggen
```

Tests: `InMemoryJobQueueTest`, `CloudMiningClientFactoryTest`, `GeneratorIntegrationTest`, `BpmnParserTest`.

## Dependencies

- **Apache Jena** (jena-core, jena-arq) — RDF model + SPARQL query engine
- **yawl-utilities** — shared XML/IO utilities
- **yawl-elements** — `YSpecification` target model

## Interfaces

- **Input**: BPMN 2.0 XML, PNML, XES 2.0 event logs, cloud mining API responses
- **Output**: YAWL specification XML, BPEL, Camunda BPMN, Terraform HCL
- **REST**: `POST /ggen/convert` — triggers async conversion job; `GET /ggen/jobs/{id}` — polls status
