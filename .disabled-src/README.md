# Disabled Source Directory - YAWL v6.0.0

**Date Disabled**: 2026-03-04
**Total Java Files**: 114
**Status**: Archived for future restoration

This directory contains source code that was disabled from the main YAWL codebase during the v6.0.0 cleanup. These components were removed due to missing dependencies, experimental status, or integration challenges.

---

## 📁 Directory Structure by Dependency Type

### 🔧 Runtime Engines (GraalVM-based)

#### graalpy.disabled/ (8 files)
**GraalPy Python Integration**
- **Purpose**: Native Python execution in YAWL using GraalPy
- **Files**: `PythonExecutionEngine.java`, `PythonContextPool.java`, `PythonInterfaceGenerator.java`, etc.
- **Dependencies**: GraalPy, Python 3.12+
- **Restore Requirements**:
  - Add GraalPy dependencies to pom.xml
  - Install Python 3.12+ system-wide
  - Configure Python virtual environment management
  - Enable python-maven-plugin integration

#### graaljs.disabled/ (7 files)
**GraalJS JavaScript Integration**
- **Purpose**: JavaScript execution in YAWL for scripting tasks
- **Files**: `JavaScriptExecutionEngine.java`, `JavaScriptContextPool.java`, `JsTypeMarshaller.java`, etc.
- **Dependencies**: GraalJS 24.0+
- **Restore Requirements**:
  - Add GraalJS dependencies
  - Configure JavaScript sandbox policies
  - Add security constraints for JS execution
  - Implement type marshalling layer

#### graalwasm.disabled/ (15 files)
**GraalWasm WebAssembly Integration**
- **Purpose**: DMN decision execution and Rust process mining via WebAssembly
- **Files**: `WasmExecutionEngine.java`, `DmnWasmBridge.java`, `Rust4pmWrapper.java`, etc.
- **Dependencies**: GraalWasm, Rust 1.70+
- **Restore Requirements**:
  - Add GraalWasm support
  - Build Rust WebAssembly modules
  - Configure DMN 1.1 support
  - Set up WASM binary caching

### 🏭 Process Mining Integration (41 files)

#### processmining.disabled/
**Complete Process Mining Suite**
- **Purpose**: Process discovery, conformance checking, and analysis
- **Submodules**:
  - `discovery/`: Alpha Miner, Heuristic Miner, Inductive Miner (4 files)
  - `conformance/`: Conformance analysis engine (7 files)
  - `synthesis/`: Workflow specification synthesis (5 files)
  - `ocpm/`: Object-centric process mining (5 files)
  - `responsibleai/`: Bias detection and fairness analysis (3 files)
  - `streaming/`: Event stream processing (1 file)
  - `pnml/`: PNML format support (4 files)
- **Dependencies**:
  - PM4py (Python library)
  - Rust4pm (Rust implementation)
  - ProM framework
  - OCEL 2.0 support
- **Restore Requirements**:
  - Add Python environment with PM4py
  - Build Rust process mining components
  - Configure event log database
  - Set up visualization dependencies

### 🎯 Machine Learning & AI Integration (16 files)

#### dspy.disabled/
**DSPy Integration for LLM Orchestration**
- **Purpose**: Large language model integration with YAWL
- **Files**: `PythonDspyBridge.java`, `DspyProgram.java`, cache management
- **Dependencies**: DSPy, OpenAI API, llama-cpp-python
- **Restore Requirements**:
  - Configure LLM API keys
  - Set up DSPy Python environment
  - Add model caching infrastructure
  - Implement prompt templates

#### a2a-skills.disabled/
**Autonomous Agent Skills**
- **Purpose**: RL-based skill system for autonomous workflow agents
- **Files**: 8 skill implementations (Commit, Execute, Generate, etc.)
- **Dependencies**: Reinforcement learning libraries
- **Restore Requirements**:
  - Add reinforcement learning framework
  - Configure skill registry
  - Implement skill persistence

### 🌐 MCP & A2A Integration (5 files)

#### mcp-spec.disabled/
**MCP (Model Context Protocol) Specifications**
- **Purpose**: Integration with Model Context Protocol for external AI
- **Files**: `YawlFactoryToolSpecifications.java`, `YawlConformanceToolSpecifications.java`, etc.
- **Dependencies**: MCP server implementation
- **Restore Requirements**:
  - Add MCP Java client library
  - Configure server endpoints
  - Implement tool specifications

#### mcp-a2a.disabled/
**Agent-to-Agent Communication**
- **Purpose**: Inter-agent messaging and coordination
- **Files**: `GitHubMcpServer.java`, demo implementations
- **Dependencies**: Message broker, WebSocket support
- **Restore Requirements**:
  - Add messaging infrastructure
  - Configure agent discovery
  - Implement protocol handlers

### 📊 Performance & Benchmarking (7 files)

#### benchmark.disabled/
**Benchmarking Infrastructure**
- **Files**: `StressTestBenchmarks.java`, `QLeverBenchmark.java`
- **Dependencies**: JMH (Java Microbenchmark Harness)
- **Restore Requirements**:
  - Add JMH annotations
  - Configure test data generators
  - Set up performance monitoring

#### yawl-benchmark/
**Performance Benchmarks**
- **Files**: QLever SPARQL engine integration, JMH annotations
- **Dependencies**: QLever database, JMH
- **Restore Requirements**:
  - Install QLever database
  - Configure SPARQL endpoint
  - Add benchmark dataset

### 🔬 Experimental Features (7 files)

#### evolution.disabled/
**Workflow Evolution**
- **Purpose**: Dynamic workflow adaptation and evolution
- **Files**: `EvolutionLoopActivator.java`
- **Dependencies**: Genetic algorithms, workflow mutation
- **Restore Requirements**:
  - Add evolutionary algorithms library
  - Configure mutation operators
  - Implement fitness evaluation

#### governance.disabled/
**Governance Framework**
- **Purpose**: Process governance and validation
- **Files**: `GovernanceValidationGates.java`
- **Dependencies**: Business rules engine, policy management
- **Restore Requirements**:
  - Add governance rules engine
  - Configure policy repositories
  - Implement validation framework

#### erlang.disabled/
**Erlang Integration**
- **Purpose**: Erlang VM integration for distributed processing
- **Status**: Directory exists but no Java files
- **Dependencies**: Erlang OTP, JInterface
- **Restore Requirements**:
  - Add Erlang dependencies
  - Configure node communication
  - Implement message passing

---

## 🚨 Critical Dependencies Summary

### System Dependencies
- **GraalPy**: Python 3.12+ system installation
- **GraalJS**: JavaScript runtime engine
- **GraalWasm**: WebAssembly support
- **Rust 1.70+**: For process mining components
- **Node.js**: For JavaScript-related features

### External Libraries
- **PM4py**: Process mining Python library
- **DSPy**: LLM orchestration framework
- **QLever**: SPARQL query engine
- **MCP Client**: Model Context Protocol implementation
- **JMH**: Java microbenchmark harness

### Configuration Requirements
- Python virtual environment setup
- WASM binary caching system
- Event log databases
- LLM API keys and endpoints
- Message broker configuration

---

## 🔄 Restoration Priority

### High Priority
1. **GraalPy Integration**: Core Python execution capability
2. **Process Mining**: Essential for process analysis features
3. **DSPy Integration**: AI/ML capabilities for workflow automation

### Medium Priority
4. **GraalJS**: JavaScript scripting support
5. **MCP Integration**: External AI model access
6. **Performance Benchmarks**: Monitoring and optimization

### Experimental Priority
7. **GraalWasm**: WebAssembly components
8. **Erlang Integration**: Distributed processing
9. **Governance Framework**: Advanced process control

---

## 📝 Notes for Future Restoration

1. **Migration Strategy**: Components should be restored incrementally
2. **Testing**: Each restored component needs comprehensive testing
3. **Documentation**: Update YAWL documentation with restored features
4. **Performance**: Monitor impact on system performance
5. **Security**: Review security implications of restored integrations

---

## 🛠️ Quick Reference Commands

```bash
# Count files
find .disabled-src -name "*.java" | wc -l

# List all disabled directories
find .disabled-src -maxdepth 1 -type d | sort

# Process mining files specifically
find .disabled-src/processmining.disabled -name "*.java" | wc -l

# GraalVM engine files
find .disabled-src/graal*.disabled -name "*.java" | wc -l
```

---

**Last Updated**: 2026-03-04
**Contact**: YAWL Development Team
**Restore Status**: Pending dependency resolution