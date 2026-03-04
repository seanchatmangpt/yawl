# Disabled Components Index

**Generated**: 2026-03-04
**Total Java Files**: 130
**Active Files**: 3 (moved back to main source)

## Runtime Engines
- **[GraalPy](./graalpy.disabled/)** - Python execution engine (8 files)
  - Dependencies: Python 3.12+, GraalPy
  - Purpose: Native Python execution in JVM
- **[GraalJS](./graaljs.disabled/)** - JavaScript execution engine (7 files)
  - Dependencies: GraalJS 24.0+
  - Purpose: JavaScript scripting support
- **[WebAssembly](./graalwasm.disabled/)** - DMN & Rust integration (15 files)
  - Dependencies: GraalWasm, Rust 1.70+
  - Purpose: DMN decisions and Rust process mining

## AI/ML Integration
- **[DSPy](./dspy.disabled/)** - LLM orchestration framework (16 files)
  - Dependencies: DSPy, OpenAI API, llama-cpp-python
  - Purpose: Large language model integration
- **[A2A Skills](./a2a-skills.disabled/)** - Reinforcement learning skills (8 files)
  - Dependencies: RL libraries
  - Purpose: Autonomous agent capabilities

## Process Mining
- **[Process Mining Suite](./processmining.disabled/)** - Complete analysis toolkit (41 files)
  - Dependencies: PM4py, Rust4pm, ProM
  - Submodules:
    - discovery/ (4 files): Alpha, Heuristic, Inductive miners
    - conformance/ (7 files): Conformance analysis
    - synthesis/ (5 files): Workflow synthesis
    - ocpm/ (5 files): Object-centric mining
    - responsibleai/ (3 files): Bias detection
    - streaming/ (1 file): Event streams
    - pnml/ (4 files): PNML support

## External Integrations
- **[MCP Specifications](./mcp-spec.disabled/)** - Model Context Protocol specs (5 files)
  - Dependencies: MCP server implementation
  - Purpose: External AI model integration
- **[A2A Communication](./mcp-a2a.disabled/)** - Inter-agent messaging (5 files)
  - Dependencies: Message broker, WebSocket
  - Purpose: Agent coordination

## Performance & Monitoring
- **[Benchmarks](./benchmark.disabled/)** - Performance testing (7 files)
  - Dependencies: JMH (Java Microbenchmark Harness)
  - Purpose: System performance metrics
- **[QLever Integration](./yawl-benchmark/)** - SPARQL query engine (4 files)
  - Dependencies: QLever database
  - Purpose: SPARQL query processing

## Experimental Features
- **[Governance](./governance.disabled/)** - Process governance framework (1 file)
  - Dependencies: Business rules engine
  - Purpose: Process governance and validation
- **[Evolution](./evolution.disabled/)** - Workflow evolution system (1 file)
  - Dependencies: Genetic algorithms
  - Purpose: Dynamic workflow adaptation

## Individual Files (Top-Level)
- `DataModellingBridge.java` - **ACTIVE** (moved to main source)
- `WorkflowDNAOracle.java` - **ACTIVE** (moved to main source)
- `PatternBasedSynthesizer.java` - **ACTIVE** (moved to main source)
- `BuriedEngine.java.disabled` - Legacy workflow engine
- `ConversationalWorkflowFactory.java.disabled` - Chat interface
- `PythonDspyBridge.java.disabled` - ML integration
- `WorkflowDNAOracle.java.disabled` - Metadata service
- `XesExportLauncher.java` - Export utility
- `YawlPromptSpecifications.java.disabled` - LLM prompts
- `YawlSimulator.java.disabled` - Simulation engine

## Quick Stats
- **Total Java Files**: 130 (127 disabled, 3 active)
- **Disabled Date**: 2026-03-04
- **Main Dependencies**: Python 3.12+, Rust 1.70+, GraalVM, PM4py, DSPy
- **Estimated Restore Effort**: 2-3 person-weeks
- **Critical Dependencies**: System-wide Python, Rust toolchain, GraalVM

## Restoration Priority
1. **High**: GraalPy, Process Mining, DSPy
2. **Medium**: GraalJS, MCP, Benchmarks
3. **Low**: WebAssembly, A2A Skills, Governance
