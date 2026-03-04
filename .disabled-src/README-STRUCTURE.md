# Disabled Source Directory Structure

```
.disabled-src/
├── README.md                    # Main documentation (this file)
├── README-STRUCTURE.md          # File organization details
├── graalpy.disabled/            # 8 files - GraalPy Python integration
├── graaljs.disabled/            # 7 files - GraalJS JavaScript integration  
├── graalwasm.disabled/          # 15 files - WebAssembly components
├── processmining.disabled/      # 41 files - Process mining suite
├── mcp-spec.disabled/           # 5 files - MCP specifications
├── dspy.disabled/               # 8 files - DSPy AI integration
├── mcp-a2a.disabled/            # 3 files - A2A communication
├── benchmark.disabled/          # 2 files - Performance benchmarks
├── evolution.disabled/          # 1 file - Workflow evolution
├── governance.disabled/         # 1 file - Governance framework
├── erlang.disabled/             # Empty directory - Erlang integration
├── a2a-skills.disabled/        # 8 files - RL-based skills
├── yawl-benchmark/              # 7 files - QLever integration
├── Symlinks for quick access:
    ├── GraalPy-Engine -> graalpy.disabled/
    ├── JavaScript-Engine -> graaljs.disabled/
    ├── WebAssembly-Engine -> graalwasm.disabled/
    ├── ProcessMining-Suite -> processmining.disabled/
    ├── DSPy-AI -> dspy.disabled/
    └── MCP-Specifications -> mcp-spec.disabled/
```

## File Count by Category

| Category | File Count | Main Dependencies |
|----------|------------|-------------------|
| Process Mining | 41 | PM4py, Rust4pm, ProM |
| WebAssembly | 15 | GraalWasm, Rust 1.70+ |
| GraalPy | 8 | Python 3.12+, GraalPy |
| GraalJS | 7 | JavaScript runtime |
| DSPy AI | 8 | LLM APIs, DSPy framework |
| MCP Specifications | 5 | MCP protocol library |
| A2A Skills | 8 | Reinforcement learning |
| Benchmarking | 9 | JMH, QLever |
| Experimental | 2 | Evolution algorithms |
| | | |
| **Total** | **114 Java files** | |

## Quick Navigation

```bash
# Access specific disabled components
cd .disabled-src/GraalPy-Engine
cd .disabled-src/ProcessMining-Suite
cd .disabled-src/DSPy-AI

# List all disabled files
find .disabled-src -name "*.java" | sort
```
