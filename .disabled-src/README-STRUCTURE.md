# Disabled Source Directory Structure

## Top-Level Directory Structure

```
.disabled-src/
├── README.md                    # Main documentation
├── README-STRUCTURE.md          # File organization details
├── INDEX.md                     # Quick index of disabled components
├── graalpy.disabled/            # 8 files - GraalPy Python integration
├── graaljs.disabled/            # 7 files - GraalJS JavaScript integration
├── graalwasm.disabled/          # 15 files - WebAssembly components
├── processmining.disabled/      # 41 files - Process mining suite
├── mcp-spec.disabled/           # 5 files - MCP specifications
├── dspy.disabled/               # 16 files - DSPy AI integration
├── mcp-a2a.disabled/            # 5 files - A2A communication
├── benchmark.disabled/          # 7 files - Performance benchmarks
├── evolution.disabled/          # 1 file - Workflow evolution
├── governance.disabled/         # 1 file - Governance framework
├── erlang.disabled/             # Empty directory - Erlang integration
├── a2a-skills.disabled/        # 8 files - RL-based skills
├── yawl-benchmark/              # 4 files - QLever integration
├── Individual Java files        # Top-level disabled files
└── Symlinks for quick access:
    ├── DSPy-AI -> dspy.disabled/
    ├── GraalPy-Engine -> graalpy.disabled/
    ├── JavaScript-Engine -> graaljs.disabled/
    ├── WebAssembly-Engine -> graalwasm.disabled/
    ├── ProcessMining-Suite -> processmining.disabled/
    └── MCP-Specifications -> mcp-spec.disabled/
```

## File Count by Category (130 total Java files)

| Category | File Count | Main Dependencies | Status |
|----------|------------|-------------------|---------|
| Process Mining | 41 | PM4py, Rust4pm, ProM | Disabled |
| WebAssembly | 15 | GraalWasm, Rust 1.70+ | Disabled |
| GraalPy | 8 | Python 3.12+, GraalPy | Disabled |
| GraalJS | 7 | JavaScript runtime | Disabled |
| DSPy AI | 16 | LLM APIs, DSPy framework | Disabled |
| MCP Specifications | 5 | MCP protocol library | Disabled |
| A2A Skills | 8 | Reinforcement learning | Disabled |
| Benchmarking | 11 | JMH, QLever | 4 files active |
| Experimental | 2 | Evolution algorithms | Disabled |
| Individual Files | 27 | Various | Mixed status |
| | | | |
| **Total** | **130 Java files** | | |

## Active Files (Outside .disabled-src)

These files have been moved back to the main source:
- `DataModellingBridge.java` - Data integration (64KB)
- `WorkflowDNAOracle.java` - Metadata service (20KB)
- `PatternBasedSynthesizer.java` - Workflow synthesis (25KB)

## Quick Navigation Commands

```bash
# Access specific disabled components
cd .disabled-src/GraalPy-Engine
cd .disabled-src/ProcessMining-Suite
cd .disabled-src/DSPy-AI

# Count files by category
find .disabled-src/processmining.disabled -name "*.java" | wc -l
find .disabled-src/graal*.disabled -name "*.java" | wc -l
find .disabled-src -name "*.java" | wc -l

# List all disabled files sorted by directory
find .disabled-src -name "*.java" | sort

# Check symbolic links
ls -la .disabled-src/ | grep "^l"
```

## Key Directories with Detailed Substructures

### processmining.disabled/
- discovery/ (4 files): Alpha Miner, Heuristic Miner, Inductive Miner
- conformance/ (7 files): Conformance analysis and monitoring
- synthesis/ (5 files): Workflow specification synthesis
- ocpm/ (5 files): Object-centric process mining
- responsibleai/ (3 files): Bias detection and fairness
- streaming/ (1 file): Event stream processing
- pnml/ (4 files): PNML format support

### dspy.disabled/
- dspy/ (2 files): Core DSPy integration
- tests/ (2 files): End-to-end tests
- Individual files: Additional DSPy components

### mcp-spec.disabled/
- YawlFactoryToolSpecifications.java
- YawlConformanceToolSpecifications.java
- YawlPatternSynthesisToolSpecifications.java
- YawlRlGenerationToolSpecifications.java
