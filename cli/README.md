# YAWL CLI — ONE MEGA GODSPEED

Unified Python CLI (Typer-based) that orchestrates everything:
- **Maven** builds
- **Observatory** fact generation
- **GODSPEED** workflow (Ψ→Λ→H→Q→Ω)
- **ggen** XML generator
- **gregverse** workflow conversion
- **Teams** (experimental multi-agent coordination)

## Installation

```bash
# From project root
cd /home/user/yawl/cli
pip install -e .
```

## Quick Start

```bash
# Initialize CLI
yawl init

# Show version & environment
yawl version

# Run GODSPEED workflow
yawl godspeed full

# Build project
yawl build all

# Generate Observatory facts
yawl observatory generate

# Generate YAWL from Turtle RDF
yawl ggen generate workflow.ttl

# Export to BPMN
yawl gregverse export workflow.yawl --format bpmn
```

## Command Structure

```
yawl
├── build
│   ├── compile    - Fastest feedback (compile only)
│   ├── test       - Run tests
│   ├── validate   - Static analysis gates
│   ├── all        - Full build (compile → test → validate)
│   └── clean      - Clean artifacts
│
├── observatory
│   ├── generate   - Generate facts (Ψ phase)
│   ├── list       - List available facts
│   ├── show       - Display fact content
│   ├── search     - Search facts for pattern
│   └── refresh    - Incremental fact refresh
│
├── godspeed
│   ├── discover   - Ψ: Generate facts
│   ├── compile    - Λ: Compile code
│   ├── guard      - H: Check guards
│   ├── verify     - Q: Verify invariants
│   └── full       - Full circuit (Ψ→Λ→H→Q→Ω)
│
├── ggen
│   ├── init       - Initialize ggen
│   ├── generate   - Turtle → YAWL XML
│   ├── validate   - Validate Turtle spec
│   ├── export     - Export to formats
│   └── round-trip - Test conversion fidelity
│
├── gregverse
│   ├── import     - Import from XPDL/BPMN/Petri
│   ├── export     - Export to BPMN/XPDL/Petri/JSON
│   └── convert    - Convert between formats
│
└── team
    ├── create       - Create new team
    ├── list         - List teams
    ├── resume       - Resume team session
    ├── status       - Show team status
    ├── message      - Send message to agent
    └── consolidate  - Consolidate + commit
```

## Global Options

```
--verbose, -v   Enable verbose output
--quiet, -q     Suppress output
--help          Show help
```

## Examples

### Full GODSPEED workflow
```bash
yawl godspeed full
```

### Generate and export
```bash
# Generate YAWL from Turtle spec
yawl ggen generate workflow.ttl --output workflow.yawl

# Export to BPMN
yawl gregverse export workflow.yawl --format bpmn --output workflow.bpmn

# Round-trip test (Turtle → YAWL → Turtle)
yawl ggen round-trip workflow.ttl
```

### Observatory analysis
```bash
# Generate facts
yawl observatory generate

# List available facts
yawl observatory list

# Search for pattern
yawl observatory search "YNetRunner"

# Display specific fact
yawl observatory show modules
```

### Team operations
```bash
# Create team for engine + schema quantum
yawl team create deadlock-fix \
  --quantums "engine semantic,schema" \
  --agents 2

# Check status
yawl team status deadlock-fix

# Resume team session
yawl team resume deadlock-fix

# Consolidate and commit
yawl team consolidate deadlock-fix \
  --message "Fixed YNetRunner deadlock"
```

## Architecture

**Three-layer design**:

1. **Entry point** (`godspeed_cli.py`): Main Typer app, subcommand registration
2. **Subcommand modules** (`yawl_cli/*.py`): Feature-specific Typer apps
3. **Utils** (`yawl_cli/utils.py`): Shared config, shell execution, fact loading

**Dependency**:
- Typer 0.9.0+ (CLI framework)
- Pydantic 2.0+ (validation)
- Rich 13.0+ (beautiful output)

## Development

```bash
# Install with dev dependencies
pip install -e ".[dev]"

# Run tests
pytest

# Format code
black yawl_cli/

# Lint
ruff check yawl_cli/

# Type check
mypy yawl_cli/
```

## Future Phases

- **Phase 1**: ggen (Turtle → YAWL)
- **Phase 2**: Blue Ocean gaps (Q phase, observability, pipeline)
- **Phase 3**: Advanced features (autonomous agents, performance tuning)

---

**Status**: Scaffold complete, ready for Phase 1 & 2 implementation
