# YAWL v7.0.0 — C4 Architecture Diagrams

## Maven Module Dependency Map with QLever Knowledge Graph Integration

---

## Level 1: System Context Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                        EXTERNAL ACTORS                               │
│                                                                      │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────────┐   │
│  │ Process    │  │ AI Agent   │  │ Process    │  │ External     │   │
│  │ Operator   │  │ (Claude/   │  │ Mining     │  │ Services     │   │
│  │            │  │  MCP/A2A)  │  │ Tools      │  │ (LDAP, DB)   │   │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └──────┬───────┘   │
│        │               │               │                │            │
└────────┼───────────────┼───────────────┼────────────────┼────────────┘
         │               │               │                │
         ▼               ▼               ▼                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│                    YAWL v7.0.0 SYSTEM                                 │
│                                                                      │
│   Workflow Engine + Process Intelligence + Knowledge Graph            │
│                                                                      │
│   25 Active Maven Modules · Java 25 · Virtual Threads                │
│   QLever SPARQL · ONNX Inference · OCEL2 · MCP/A2A                   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
         │               │               │                │
         ▼               ▼               ▼                ▼
┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────────┐
│ Database   │  │ Python     │  │ Erlang/OTP │  │ Native       │
│ (H2/MySQL/ │  │ (TPOT2     │  │ Runtime    │  │ QLever       │
│  Postgres) │  │  AutoML)   │  │            │  │ (libqlever)  │
└────────────┘  └────────────┘  └────────────┘  └──────────────┘
```

---

## Level 2: Container Diagram — Module Layers

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           YAWL v7.0.0 REACTOR                               │
│                                                                             │
│  ═══════════════════════════════════════════════════════════════════════     │
│  LAYER 0: FOUNDATION (no YAWL deps, parallel build)                        │
│  ═══════════════════════════════════════════════════════════════════════     │
│                                                                             │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐       │
│  │ yawl-        │ │ yawl-        │ │ yawl-        │ │ yawl-        │       │
│  │ utilities    │ │ exceptions   │ │ security     │ │ erlang       │       │
│  │              │ │              │ │              │ │              │       │
│  │ XNode, XML   │ │ YAWLException│ │ Crypto, TLS  │ │ OTP bridge   │       │
│  │ StringUtil   │ │ hierarchy    │ │ Auth tokens  │ │ JInterface   │       │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘       │
│         │                │                │                │               │
│  ┌──────────────┐                                                          │
│  │ yawl-        │                                                          │
│  │ graalpy      │                                                          │
│  │              │                                                          │
│  │ Python       │                                                          │
│  │ interop      │                                                          │
│  └──────┬───────┘                                                          │
│         │                                                                   │
│  ═══════╪═══════════════════════════════════════════════════════════════     │
│  LAYER 1: FIRST CONSUMERS (parallel build)                                 │
│  ═══════╪═══════════════════════════════════════════════════════════════     │
│         ▼                                                                   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐       │
│  │ yawl-        │ │ yawl-        │ │ yawl-        │ │ yawl-        │       │
│  │ elements     │ │ ggen         │ │ tpot2        │ │ dspy         │       │
│  │              │ │              │ │              │ │              │       │
│  │ YNet, YTask  │ │ Code gen     │ │ AutoML       │ │ LLM          │       │
│  │ YWorkItem    │ │ RDF, SPARQL  │ │ ONNX bridge  │ │ reasoning    │       │
│  │ YCondition   │ │ AST analysis │ │ Python subpr │ │ DSPy module  │       │
│  └──────┬───────┘ └──────────────┘ └──────────────┘ └──────────────┘       │
│         │                                                                   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐                        │
│  │ yawl-        │ │ yawl-        │ │ yawl-data-   │                        │
│  │ ml-bridge    │ │ dmn          │ │ modelling    │                        │
│  │              │ │              │ │              │                        │
│  │ ML pipeline  │ │ Decision     │ │ Data model   │                        │
│  │ abstraction  │ │ Model        │ │ generation   │                        │
│  └──────────────┘ │ Notation     │ └──────────────┘                        │
│                   └──────────────┘                                          │
│         │                                                                   │
│  ═══════╪═══════════════════════════════════════════════════════════════     │
│  LAYER 2: CORE ENGINE                                                      │
│  ═══════╪═══════════════════════════════════════════════════════════════     │
│         ▼                                                                   │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                      yawl-engine                                │        │
│  │                                                                 │        │
│  │  YEngine (stateful) · YNetRunner · YSpecification              │        │
│  │  GlobalCaseRegistry · InstanceCache · YPersistenceManager      │        │
│  │  ObserverGateway · WorkflowEventStore · InterfaceA/B/E/X       │        │
│  │  Virtual Thread per case · Structured Concurrency               │        │
│  │                                                                 │        │
│  └─────────────────────────────┬───────────────────────────────────┘        │
│                                │                                            │
│  ═══════════════════════════════╪════════════════════════════════════════    │
│  LAYER 3: ENGINE EXTENSION                                                 │
│  ═══════════════════════════════╪════════════════════════════════════════    │
│                                ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                     yawl-stateless                              │        │
│  │                                                                 │        │
│  │  YStatelessEngine · YCaseMonitor · Cloud-native execution      │        │
│  │  Event-driven · Horizontal scaling · Container-ready           │        │
│  │                                                                 │        │
│  └─────────────────────────────┬───────────────────────────────────┘        │
│                                │                                            │
│  ═══════════════════════════════╪════════════════════════════════════════    │
│  LAYER 4: SERVICES (parallel build after stateless)                        │
│  ═══════════════════════════════╪════════════════════════════════════════    │
│                                ▼                                            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐       │
│  │ yawl-        │ │ yawl-        │ │ yawl-        │ │ yawl-        │       │
│  │ authentication│ │ scheduling   │ │ monitoring   │ │ worklet      │       │
│  │              │ │              │ │              │ │              │       │
│  │ LDAP, OAuth  │ │ Cron, timer  │ │ Metrics,     │ │ Dynamic      │       │
│  │ JWT tokens   │ │ scheduling   │ │ health check │ │ sub-process  │       │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘       │
│                                                                             │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐       │
│  │ yawl-        │ │ yawl-        │ │ yawl-        │ │ yawl-        │       │
│  │ control-panel│ │ integration  │ │ benchmark    │ │ webapps      │       │
│  │              │ │              │ │              │ │              │       │
│  │ Admin UI     │ │ MCP, A2A     │ │ JMH perf     │ │ Web UI       │       │
│  │ Dashboard    │ │ Z.AI client  │ │ testing      │ │ servlets     │       │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘       │
│                                                                             │
│  ═══════════════════════════════════════════════════════════════════════     │
│  LAYER 5: ADVANCED SERVICES (parallel build)                               │
│  ═══════════════════════════════════════════════════════════════════════     │
│                                                                             │
│  ┌──────────────┐ ┌──────────────────────┐ ┌──────────────────────┐        │
│  │ yawl-        │ │ yawl-qlever          │ │ yawl-native-bridge   │        │
│  │ resourcing   │ │                      │ │                      │        │
│  │              │ │ QLeverEmbedded       │ │ yawl-qlever-bridge   │        │
│  │ Resource     │ │ SparqlEngine         │ │ (Panama FFM bindings)│        │
│  │ allocation   │ │ QLeverFfiBindings    │ │                      │        │
│  │ Work lists   │ │ Process Knowledge    │ │ jextract-generated   │        │
│  │              │ │ Graph queries        │ │ qlever_ffi.h wrapper │        │
│  └──────────────┘ └──────────────────────┘ └──────────────────────┘        │
│                                                                             │
│  ═══════════════════════════════════════════════════════════════════════     │
│  DISABLED MODULES (awaiting dependency resolution)                         │
│  ═══════════════════════════════════════════════════════════════════════     │
│                                                                             │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐       │
│  │ yawl-pi      │ │ yawl-graaljs │ │ yawl-graalwasm│ │ yawl-rust4pm │       │
│  │ [disabled]   │ │ [disabled]   │ │ [disabled]   │ │ [disabled]   │       │
│  │              │ │              │ │              │ │              │       │
│  │ 7 PI         │ │ GraalJS      │ │ WASM exec    │ │ Rust process │       │
│  │ connections  │ │ sandbox      │ │ Rust4pmBridge│ │ mining JNI   │       │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘       │
│                                                                             │
│  ┌──────────────┐                                                          │
│  │ yawl-mcp-    │                                                          │
│  │ a2a-app      │                                                          │
│  │ [disabled]   │                                                          │
│  │              │                                                          │
│  │ Top-level    │                                                          │
│  │ application  │                                                          │
│  └──────────────┘                                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Level 3: Component Diagram — QLever Knowledge Graph Integration

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     QLEVER KNOWLEDGE GRAPH INTEGRATION                       │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        yawl-engine (Layer 2)                        │    │
│  │                                                                     │    │
│  │  ┌───────────────┐    ┌──────────────────┐    ┌────────────────┐   │    │
│  │  │  YNetRunner    │───▶│ WorkflowEvent    │    │ ObserverGateway│   │    │
│  │  │  (execution)   │    │ Store            │    │ (pub/sub)      │   │    │
│  │  └───────────────┘    └────────┬─────────┘    └───────┬────────┘   │    │
│  │                                │                      │             │    │
│  └────────────────────────────────┼──────────────────────┼─────────────┘    │
│                                   │                      │                  │
│                    ┌──────────────┼──────────────────────┼──────────┐       │
│                    │              ▼                      ▼          │       │
│                    │  ┌──────────────────┐  ┌───────────────────┐   │       │
│                    │  │ ProcessKnowledge │  │ PredictiveProcess │   │       │
│                    │  │ GraphBuilder     │  │ Observer          │   │       │
│                    │  │                  │  │ (adaptive pkg)    │   │       │
│                    │  │ Events→RDF       │  │                   │   │       │
│                    │  │ triples          │  │ ONNX inference    │   │       │
│                    │  └────────┬─────────┘  └────────┬──────────┘   │       │
│                    │           │                      │              │       │
│                    │           ▼                      ▼              │       │
│                    │  ┌──────────────────┐  ┌───────────────────┐   │       │
│                    │  │ QLeverProcess    │  │ PredictiveModel   │   │       │
│                    │  │ QueryEngine      │  │ Registry          │   │       │
│                    │  │                  │  │ (ONNX sessions)   │   │       │
│                    │  │ SPARQL queries:  │  │                   │   │       │
│                    │  │ • bottlenecks    │  │ Connection 1:     │   │       │
│                    │  │ • SLA corr.      │  │ Predictive        │   │       │
│                    │  │ • variants       │  │                   │   │       │
│                    │  │ • resources      │  └───────────────────┘   │       │
│                    │  └────────┬─────────┘                          │       │
│                    │           │                                     │       │
│                    │  yawl-pi (disabled, future Connection 7)       │       │
│                    └───────────┼─────────────────────────────────────┘       │
│                               │                                             │
│                               ▼                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        yawl-qlever (Layer 5)                        │    │
│  │                                                                     │    │
│  │  ┌───────────────────────────────────────────────────────────────┐  │    │
│  │  │              QLeverEmbeddedSparqlEngine                       │  │    │
│  │  │                                                               │  │    │
│  │  │  initialize()      — Start native engine                      │  │    │
│  │  │  loadRdfData()     — Load RDF triples (TURTLE/JSON/XML/CSV)  │  │    │
│  │  │  executeSparqlQuery() — Run SPARQL 1.1 query                 │  │    │
│  │  │  shutdown()        — Clean shutdown + resource cleanup         │  │    │
│  │  │                                                               │  │    │
│  │  │  @ThreadSafe · @GuardedBy("this")                            │  │    │
│  │  │  ScheduledExecutorService for query timeouts                  │  │    │
│  │  └────────────────────────────┬──────────────────────────────────┘  │    │
│  │                               │                                     │    │
│  │  ┌────────────────────────────▼──────────────────────────────────┐  │    │
│  │  │              QLeverFfiBindings                                │  │    │
│  │  │                                                               │  │    │
│  │  │  initializeEngine()  — Panama FFM downcall                   │  │    │
│  │  │  shutdownEngine()    — Panama FFM downcall                   │  │    │
│  │  │  loadRdfData()       — Panama FFM downcall                   │  │    │
│  │  │  executeSparqlQuery() — Panama FFM downcall                  │  │    │
│  │  │  getEngineStatus()   — Panama FFM downcall                   │  │    │
│  │  └────────────────────────────┬──────────────────────────────────┘  │    │
│  │                               │                                     │    │
│  │  ┌────────────┐ ┌────────────┐│┌────────────┐ ┌────────────────┐   │    │
│  │  │ QLeverResult│ │QLeverStatus│││QLeverMedia │ │QLeverFfi       │   │    │
│  │  │            │ │            │││ Type       │ │ Exception      │   │    │
│  │  │ status     │ │ UNINIT     │││ TURTLE     │ │ errorCode      │   │    │
│  │  │ data       │ │ READY      │││ JSON_LD    │ │ nativeMessage  │   │    │
│  │  │ metadata   │ │ LOADING    │││ RDFXML     │ │ queryContext   │   │    │
│  │  │ queryTime  │ │ ERROR      │││ CSV        │ │                │   │    │
│  │  └────────────┘ └────────────┘│└────────────┘ └────────────────┘   │    │
│  │                               │                                     │    │
│  └───────────────────────────────┼─────────────────────────────────────┘    │
│                                  │                                          │
│  ════════════════════════════════╪══════════════ Panama FFM boundary ════   │
│                                  │                                          │
│  ┌───────────────────────────────▼─────────────────────────────────────┐    │
│  │                  yawl-native-bridge/yawl-qlever-bridge               │    │
│  │                                                                     │    │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌────────────────┐    │    │
│  │  │ QleverNative     │  │ QleverEngine     │  │ QleverFfi      │    │    │
│  │  │ Bridge           │  │ (interface)       │  │ (jextract)     │    │    │
│  │  │                  │  │                  │  │                │    │    │
│  │  │ Arena lifecycle  │  │ QLeverEngineImpl │  │ Auto-generated │    │    │
│  │  │ MemorySegment    │  │ (impl)           │  │ from header    │    │    │
│  │  └──────────────────┘  └──────────────────┘  └───────┬────────┘    │    │
│  │                                                      │             │    │
│  │  ┌──────────────────────────────────────────────────┐│             │    │
│  │  │ qlever_ffi.h (C header)                          ││             │    │
│  │  │                                                  ││             │    │
│  │  │ qlever_init() · qlever_shutdown()                ││             │    │
│  │  │ qlever_load_data() · qlever_execute_query()      ││             │    │
│  │  │ qlever_get_status() · qlever_free_result()       ││             │    │
│  │  │                                                  ││             │    │
│  │  │ Lippincott pattern: C++ exceptions → error codes ││             │    │
│  │  └──────────────────────────────────────────────────┘│             │    │
│  └──────────────────────────────────────────────────────┼─────────────┘    │
│                                                         │                  │
│  ═══════════════════════════════════════════════════════╪════ Native ═══   │
│                                                         ▼                  │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                        libqlever.so (Native)                        │    │
│  │                                                                     │    │
│  │  QLever SPARQL Engine (University of Freiburg)                      │    │
│  │  • Compressed 6-permutation RDF index (SPO, SOP, PSO, POS, OSP, OPS)│   │
│  │  • Memory-mapped I/O (zero JVM heap pressure)                       │    │
│  │  • Full-text search + SPARQL integration                            │    │
│  │  • <5ms query latency on 100K+ triple graphs                       │    │
│  │                                                                     │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Level 3: Component Diagram — Process Intelligence (7 Connections)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                PROCESS INTELLIGENCE FACADE (7 Connections)                    │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    ProcessIntelligenceFacade                         │    │
│  │                                                                     │    │
│  │  predictOutcome()    recommendActions()    optimiseAssignment()      │    │
│  │  ask()               prepareOcel2Export()  autoTrainCaseOutcome()    │    │
│  │  querySparql()                                                      │    │
│  │                                                                     │    │
│  └──┬──────┬──────┬──────┬──────┬──────┬──────┬────────────────────────┘    │
│     │      │      │      │      │      │      │                             │
│     ▼      ▼      ▼      ▼      ▼      ▼      ▼                             │
│  ┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────────────────┐      │
│  │ C1   ││ C2   ││ C3   ││ C4   ││ C5   ││ C6   ││ C7               │      │
│  │Pred- ││Presc-││Optim-││ RAG  ││Data  ││AutoML││Knowledge Graph   │      │
│  │ictive││ript- ││isa-  ││      ││Prep  ││      ││                  │      │
│  │      ││ive   ││tion  ││      ││      ││      ││QLeverEmbedded    │      │
│  │      ││      ││      ││      ││      ││      ││SparqlEngine      │      │
│  │ONNX  ││Jena  ││Hung- ││ZAI   ││OCEL2 ││TPOT2 ││                  │      │
│  │Model ││RDF   ││arian ││LLM   ││Bridge││Python││ProcessKnowledge  │      │
│  │Regist││Constr││Algo  ││NL    ││Schema││Subpr ││GraphBuilder      │      │
│  │ry    ││Model ││      ││Query ││Infer ││      ││                  │      │
│  └──┬───┘└──┬───┘└──┬───┘└──┬───┘└──┬───┘└──┬───┘└──────┬───────────┘      │
│     │       │       │       │       │       │           │                    │
│     ▼       ▼       ▼       ▼       ▼       ▼           ▼                    │
│  ┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────────────────┐      │
│  │ONNX  ││Jena  ││Math  ││Claude││Rust4 ││TPOT2 ││QLever Native     │      │
│  │Runtim││ARQ   ││Optim ││API   ││pm    ││(Py)  ││(libqlever.so)    │      │
│  │(JVM) ││(JVM) ││(JVM) ││(HTTP)││(WASM)││(subp)││(Panama FFM)      │      │
│  └──────┘└──────┘└──────┘└──────┘└──────┘└──────┘└──────────────────┘      │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    Independent Degradation Property                  │    │
│  │                                                                     │    │
│  │  Each connection can fail independently without affecting others.    │    │
│  │  PIException carries connection ID: "predictive", "prescriptive",   │    │
│  │  "optimisation", "rag", "data-prep", "automl", "knowledge-graph"    │    │
│  │                                                                     │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Level 3: Component Diagram — Module Dependency Graph

```
                            yawl-utilities
                           ┌──────┬──────┐
                           │      │      │
                           ▼      ▼      ▼
                    yawl-exceptions  yawl-security
                           │              │
                           ▼              │
                      yawl-elements ◄─────┘
                      ┌────┤
                      │    │
          ┌───────────┤    ├──────────────────┐
          │           │    │                  │
          ▼           ▼    ▼                  ▼
    yawl-ggen    yawl-dmn  yawl-data-    yawl-ml-bridge
                           modelling          │
          │                                   │
          │                                   ▼
          │                              yawl-tpot2
          │                                   │
          ▼                                   │
    yawl-engine ◄─────────────────────────────┘
          │
          ▼
    yawl-stateless
          │
          ├─────────────┬──────────────┬──────────────┬──────────────┐
          │             │              │              │              │
          ▼             ▼              ▼              ▼              ▼
    yawl-         yawl-          yawl-          yawl-          yawl-
    authentication scheduling    monitoring     worklet        control-panel
                                                                    │
          ┌────────────────────────────────────────┐                │
          │                                        │                │
          ▼                                        ▼                ▼
    yawl-integration                         yawl-webapps    yawl-benchmark
          │
          ▼
    yawl-resourcing

    ─── Independent modules (Layer 5) ───

    yawl-qlever ◄──── yawl-native-bridge/yawl-qlever-bridge
         │                     │
         │                     ▼
         │              qlever_ffi.h (native header)
         │                     │
         │                     ▼
         └──────────── libqlever.so (native SPARQL engine)

    ─── Polyglot modules (Layer 0) ───

    yawl-erlang ──── Erlang/OTP 28 (JInterface)
    yawl-graalpy ─── GraalPy (Python interop)
```

---

## Level 3: Component Diagram — H-Guards Validation Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    H-GUARDS VALIDATION WITH QLEVER                          │
│                                                                             │
│  Source Code (.java files)                                                   │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────────┐                                                   │
│  │ tree-sitter-java     │  AST parsing (Layer: ggen)                        │
│  │ (JavaAstParser)      │                                                   │
│  └──────────┬───────────┘                                                   │
│             │ AST nodes                                                      │
│             ▼                                                               │
│  ┌──────────────────────┐                                                   │
│  │ RdfAstConverter      │  AST → RDF triples (Layer: ggen)                  │
│  │ (code: namespace)    │                                                   │
│  └──────────┬───────────┘                                                   │
│             │ RDF triples (TURTLE)                                           │
│             ▼                                                               │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │              QLeverEmbeddedSparqlEngine (yawl-qlever)                │   │
│  │                                                                      │   │
│  │  loadRdfData(triples, "TURTLE")  — Index code structure             │   │
│  │                                                                      │   │
│  │  ┌──────────────────────────────────────────────────────────────┐    │   │
│  │  │  7 Guard Queries (executed in sequence, <10ms total)         │    │   │
│  │  │                                                              │    │   │
│  │  │  1. guards-h-todo.sparql     — // TODO, // FIXME markers    │    │   │
│  │  │  2. guards-h-mock.sparql     — mock/stub/fake identifiers   │    │   │
│  │  │  3. guards-h-stub.sparql     — return "" / return null      │    │   │
│  │  │  4. guards-h-empty.sparql    — void { } empty bodies        │    │   │
│  │  │  5. guards-h-fallback.sparql — catch { return fake }        │    │   │
│  │  │  6. guards-h-lie.sparql      — code ≠ documentation         │    │   │
│  │  │  7. guards-h-silent.sparql   — log.error instead of throw   │    │   │
│  │  └──────────────────────────────────────────────────────────────┘    │   │
│  │                                                                      │   │
│  └──────────┬───────────────────────────────────────────────────────────┘   │
│             │ List<GuardViolation>                                          │
│             ▼                                                               │
│  ┌──────────────────────┐                                                   │
│  │ HyperStandards       │                                                   │
│  │ Validator             │                                                   │
│  │ (guard-receipt.json)  │  → exit 0 (GREEN) or exit 2 (RED)               │
│  └──────────────────────┘                                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Level 3: Component Diagram — Build Pipeline (dx.sh all)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    dx.sh all — FULL VALIDATION PIPELINE                      │
│                                                                             │
│  Phase Ψ (Observatory)                                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ observatory.sh → modules.json, gates.json, reactor.json, ...       │    │
│  │ Refreshes when pom.xml changes (DX_SKIP_OBSERVE=1 bypasses in CI)  │    │
│  └─────────────────────────────────┬───────────────────────────────────┘    │
│                                    │                                        │
│  Phase Λ (Build)                   ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ mvnd clean verify -T1C                                              │    │
│  │                                                                     │    │
│  │ Compile order (reactor):                                            │    │
│  │ L0: utilities, exceptions, security, erlang, graalpy  [parallel]    │    │
│  │ L1: elements, ggen, tpot2, dspy, ml-bridge, dmn, data-modelling     │    │
│  │ L2: engine                                                          │    │
│  │ L3: stateless                                                       │    │
│  │ L4: auth, sched, monitor, worklet, ctrl, integration, bench, web    │    │
│  │ L5: resourcing, qlever, native-bridge                               │    │
│  └─────────────────────────────────┬───────────────────────────────────┘    │
│                                    │                                        │
│  Phase H (Guards)                  ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ hyper-validate.sh → guard-receipt.json                              │    │
│  │                                                                     │    │
│  │ Scans all 25 modules for 7 forbidden patterns                       │    │
│  │ Uses QLever for SPARQL-based AST analysis                           │    │
│  │ exit 0 (GREEN) or exit 2 (RED, blocks commit)                       │    │
│  └─────────────────────────────────┬───────────────────────────────────┘    │
│                                    │                                        │
│  Phase Q (Invariants)              ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ q-phase-invariants.sh                                               │    │
│  │                                                                     │    │
│  │ real_impl ∨ throw UnsupportedOperationException                     │    │
│  │ No third option. ¬mock ∧ ¬stub ∧ ¬silent_fallback ∧ ¬lie           │    │
│  └─────────────────────────────────┬───────────────────────────────────┘    │
│                                    │                                        │
│  Phase Ω (Report)                  ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ Generate receipts → guard-receipt.json, observatory.json            │    │
│  │ Ready for git commit (specific files only, never git add .)         │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Level 4: Code Diagram — QLever FFI Call Chain

```
Java Application Code
│
│  engine.executeSparqlQuery("SELECT ?s WHERE { ?s ?p ?o } LIMIT 10")
│
▼
QLeverEmbeddedSparqlEngine.java
│  synchronized checkInitialized()
│  qleverResult = ffiBindings.executeSparqlQuery(sparql)
│  scheduleTimeout(queryTimeout)
│
▼
QLeverFfiBindings.java
│  MethodHandle queryHandle = linker.downcallHandle(
│      FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
│  MemorySegment result = (MemorySegment) queryHandle.invokeExact(
│      arena, sparqlSegment);
│
▼  ─── Panama FFM boundary (Arena-scoped memory) ───
│
qlever_ffi.h / libqlever.so
│  extern "C" qlever_result_t* qlever_execute_query(
│      qlever_engine_t* engine,
│      const char* sparql_query);
│
│  // Lippincott pattern:
│  try {
│      return engine->executeQuery(sparql);
│  } catch (const std::exception& e) {
│      return error_result(e.what());
│  } catch (...) {
│      return error_result("unknown error");
│  }
│
▼
QLever C++ Engine (native)
│  Index lookup (6-permutation compressed index)
│  Join ordering (cardinality estimation)
│  Result materialisation
│
▼  ─── Return path ───
│
QLeverResult.java
│  status: QLeverStatus.OK
│  data: "?s\n<http://example.org/s1>\n..."
│  queryTimeMs: 2
│  triplesScanned: 1847
```

---

## Cross-Module Dependency Matrix

| Module | Depends On | Depended On By |
|--------|-----------|----------------|
| **yawl-utilities** | (none) | elements, engine, most modules |
| **yawl-exceptions** | utilities | elements, engine |
| **yawl-security** | utilities | authentication, engine |
| **yawl-erlang** | (none) | integration |
| **yawl-graalpy** | (none) | tpot2, dspy |
| **yawl-elements** | utilities, exceptions | engine, ggen, dmn |
| **yawl-ggen** | elements | (code generation) |
| **yawl-tpot2** | graalpy, ml-bridge | pi (disabled) |
| **yawl-dspy** | graalpy | pi (disabled) |
| **yawl-ml-bridge** | utilities | tpot2 |
| **yawl-dmn** | elements | engine |
| **yawl-data-modelling** | elements | engine |
| **yawl-engine** | elements, utilities, exceptions | stateless, all L4+ |
| **yawl-stateless** | engine | L4 services |
| **yawl-authentication** | security, engine | webapps |
| **yawl-scheduling** | engine | monitoring |
| **yawl-monitoring** | engine | control-panel |
| **yawl-worklet** | engine | integration |
| **yawl-control-panel** | engine, monitoring | webapps |
| **yawl-integration** | engine, erlang | resourcing |
| **yawl-benchmark** | engine | (test only) |
| **yawl-webapps** | engine, auth, control-panel | (deployment) |
| **yawl-resourcing** | engine, integration | (resource mgmt) |
| **yawl-qlever** | (standalone FFI) | pi (future C7), ggen (H-Guards) |
| **yawl-native-bridge** | (standalone FFI) | qlever (bridge impl) |

---

## QLever in the CHATMAN Equation

```
A = μ(O) | μ = Ω ∘ Q ∘ H ∘ Λ ∘ Ψ

QLever participates in two gates:

  H (Guards): QLever executes 7 SPARQL guard queries
              on RDF-encoded Java ASTs

              Java Source → tree-sitter → RDF → QLever SPARQL → Violations

  Ψ (Observatory): QLever can query process knowledge graphs
                    built from WorkflowEventStore events

                    Events → RDF triples → QLever index → SPARQL queries
                    → bottlenecks, SLA correlations, process variants
```

---

*Generated for YAWL v7.0.0 · 25 active modules · QLever Knowledge Graph Integration*
*Part of the Co-location Thesis: "Intelligence belongs inside the workflow engine."*
