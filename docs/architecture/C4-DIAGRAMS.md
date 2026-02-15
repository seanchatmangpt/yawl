# C4 Architecture Diagrams - YAWL AGI Orchestration System

**Project**: YAWL Modernization for AGI Swarm Coordination
**Version**: 5.2
**Date**: February 2026
**Authors**: Based on van der Aalst et al. workflow foundations

---

## Overview

This document provides comprehensive C4 model diagrams for the YAWL system modernized as an AGI orchestration platform. The diagrams follow the C4 model hierarchy:

1. **System Context** - How YAWL fits in the AGI ecosystem
2. **Container** - Runtime containers and their interactions
3. **Component** - Internal structure of key containers
4. **Code** - Critical classes and their relationships

Additionally includes:
- Deployment architecture
- AGI coordination patterns
- Integration flows

---

## Diagram Rendering

These diagrams use:
- **PlantUML** with C4 extension (`.puml` files)
- **Mermaid** for inline rendering (`.mmd` files)
- **Structurizr DSL** for complete C4 workspace (`.dsl` files)

Render with:
```bash
# PlantUML
plantuml -tsvg docs/architecture/*.puml

# Mermaid CLI
mmdc -i docs/architecture/*.mmd -o docs/architecture/rendered/

# Structurizr Lite (Docker)
docker run -it --rm -p 8080:8080 -v $(pwd)/docs/architecture:/usr/local/structurizr structurizr/lite
```

---

## Table of Contents

1. [C4 Level 1 - System Context](#c4-level-1---system-context)
2. [C4 Level 2 - Container Diagram](#c4-level-2---container-diagram)
3. [C4 Level 3 - Component Diagrams](#c4-level-3---component-diagrams)
   - 3.1 [YAWL Engine Components](#31-yawl-engine-components)
   - 3.2 [MCP Server Components](#32-mcp-server-components)
   - 3.3 [A2A Coordination Components](#33-a2a-coordination-components)
4. [C4 Level 4 - Code Diagrams](#c4-level-4---code-diagrams)
5. [Deployment Architecture](#deployment-architecture)
6. [AGI Orchestration Patterns](#agi-orchestration-patterns)

---

## Quick Reference

| Diagram | Purpose | File |
|---------|---------|------|
| System Context | YAWL in AGI ecosystem | `c4-level1-context.puml` |
| Container | Runtime architecture | `c4-level2-containers.puml` |
| Engine Components | Core engine internals | `c4-level3-engine.puml` |
| MCP Components | AI agent integration | `c4-level3-mcp.puml` |
| A2A Components | Agent coordination | `c4-level3-a2a.puml` |
| Code Structure | Key classes | `c4-level4-code.puml` |
| Deployment | Docker Compose setup | `deployment-docker.puml` |
| AGI Patterns | Swarm orchestration | `agi-patterns.puml` |

---

## Next Sections

Individual diagram files are located in:
- `docs/architecture/diagrams/plantuml/` - PlantUML sources
- `docs/architecture/diagrams/mermaid/` - Mermaid sources
- `docs/architecture/diagrams/rendered/` - Rendered SVG/PNG outputs
- `docs/architecture/workspace.dsl` - Structurizr complete workspace

See README in each directory for rendering instructions.

---

**Note**: All diagrams follow Fortune 5 production standards:
- No "mock" or "stub" components shown
- All integrations are real implementations
- Clear error handling paths
- Security boundaries marked
- Scalability patterns indicated
