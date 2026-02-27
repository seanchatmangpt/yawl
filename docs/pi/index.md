# YAWL Process Intelligence (yawl-pi)

YAWL Process Intelligence adds an AI layer to YAWL v6.0.0.
It provides six capability connections — from live case risk prediction through TPOT2 AutoML
training — all accessible through a single unified facade.

---

## What do you want to do?

| I want to… | Go to… |
|---|---|
| Follow a step-by-step example and learn by doing | [Tutorials](#tutorials) |
| Accomplish a specific task right now | [How-to guides](#how-to-guides) |
| Look up an API, config field, or data type | [Reference](#reference) |
| Understand how the system works and why | [Explanation](#explanation) |

---

## Tutorials

Step-by-step learning guides — follow from start to finish.

| # | Title | What you build |
|---|---|---|
| 1 | [Your first case outcome prediction](tutorials/01-first-case-prediction.md) | Store events → predict outcome → read risk score |
| 2 | [Train an AutoML model with TPOT2](tutorials/02-train-automl-model.md) | Historical events → TPOT2 → ONNX model in registry |
| 3 | [Build a real-time adaptive process](tutorials/03-realtime-adaptive.md) | PredictiveProcessObserver + SLA guardian rule |
| 4 | [Natural language Q&A over process knowledge](tutorials/04-natural-language-qa.md) | Ingest report → ask() → grounded answer |

---

## How-to guides

Goal-oriented recipes for common tasks.

| Title | Task |
|---|---|
| [Configure the PI facade](how-to/configure-pi-facade.md) | Wire PIFacadeConfig dependencies |
| [Register an ONNX model](how-to/register-onnx-model.md) | Load a .onnx file for inference |
| [Ingest an event log](how-to/ingest-event-log.md) | Convert CSV / JSON / XML to OCEL2 |
| [Add a constraint rule](how-to/add-constraint-rule.md) | Forbid an action via Jena RDF |
| [Add an adaptation rule](how-to/add-adaptation-rule.md) | Create a custom real-time rule |
| [Expose PI tools via MCP](how-to/expose-mcp-tools.md) | Register PIToolProvider with MCP server |
| [Set up the TPOT2 Python environment](how-to/setup-tpot2-python.md) | Install Python deps for AutoML |
| [Optimize resource assignments](how-to/optimize-resources.md) | Solve an assignment problem |

---

## Reference

Complete, accurate technical information.

| Title | Contents |
|---|---|
| [Facade API](reference/facade-api.md) | ProcessIntelligenceFacade — all methods |
| [Configuration](reference/config.md) | PIFacadeConfig + Tpot2Config fields |
| [Process actions](reference/process-actions.md) | ProcessAction sealed interface |
| [ONNX model format](reference/onnx-model-format.md) | Feature vectors, naming conventions |
| [OCED schema](reference/oced-schema.md) | OcedSchema fields + OCEL2 structure |

---

## Explanation

Background, rationale, and conceptual overviews.

| Title | Covers |
|---|---|
| [Architecture](explanation/architecture.md) | Module map, dependency graph, thread safety |
| [Co-located AutoML](explanation/co-located-automl.md) | Why zero-ETL prediction matters |
| [The six PI connections](explanation/6-pi-connections.md) | Design rationale per connection |
| [OCEL2 standard](explanation/ocel2-standard.md) | Object-centric event logs primer |

---

## Quick start

```java
// 1. Create dependencies
DataSource ds = /* your JDBC DataSource */;
WorkflowEventStore store = new WorkflowEventStore(ds);            // auto-creates schema
PredictiveModelRegistry registry = new PredictiveModelRegistry(Path.of("models/"));
WorkflowDNAOracle oracle  = new WorkflowDNAOracle(/* spec generator */);

// 2. Build engines
CaseOutcomePredictor predictor = new CaseOutcomePredictor(store, oracle, registry);
PrescriptiveEngine   prescriptive = new PrescriptiveEngine(oracle);
ResourceOptimizer    optimizer = new ResourceOptimizer();
NaturalLanguageQueryEngine nlEngine = new NaturalLanguageQueryEngine(
    new ProcessKnowledgeBase(), null);   // zaiService optional

// 3. Create facade
ProcessIntelligenceFacade pi = new ProcessIntelligenceFacade(
    predictor, prescriptive, optimizer, nlEngine);

// 4. Use it
CaseOutcomePrediction p = pi.predictOutcome("case-001");
System.out.println("Risk: " + p.riskScore());                    // 0.0 – 1.0
```

See [Tutorial 1](tutorials/01-first-case-prediction.md) for the full end-to-end walkthrough.
