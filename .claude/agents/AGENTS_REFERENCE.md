---
name: yawl-agent-definitions
description: YAWL Agent Definitions - Claude Code 2026 Best Practices
---

Specialized agents for YAWL workflow engine development. Java 25 + HYPER_STANDARDS enforced.

## Agent Selection Matrix

| Task Type | Primary Agent | Supporting Agents | Topology |
|-----------|---------------|-------------------|----------|
| Engine feature | `yawl-engineer` | `yawl-architect`, `yawl-tester` | hierarchical |
| Specification | `yawl-validator` | `yawl-engineer` | hierarchical |
| Architecture | `yawl-architect` | `yawl-engineer`, `yawl-integrator` | mesh |
| Integration | `yawl-integrator` | `yawl-engineer`, `yawl-validator` | mesh |
| Code review | `yawl-reviewer` | `yawl-validator` | hierarchical |
| Testing | `yawl-tester` | `yawl-validator`, `yawl-engineer` | hierarchical |
| Deployment | `yawl-production-validator` | `yawl-reviewer`, `yawl-performance-benchmarker` | mesh |
| Performance | `yawl-performance-benchmarker` | `yawl-engineer`, `yawl-reviewer` | mesh |

## Spawning Pattern

```bash
# Single agent
Task("Feature", "Implement X using YAWL patterns", "yawl-engineer")

# Coordinated swarm (ALL in ONE message)
Task("Integration", "Implement MCP integration", "yawl-integrator")
Task("Validation", "Validate MCP compliance", "yawl-validator")
Task("Testing", "Create integration tests", "yawl-tester")
```

## Java 25 (All Agents)

Use records for DTOs, sealed classes for hierarchies, pattern matching for switches, virtual threads for concurrency, scoped values for context, structured concurrency for parallel work.
See `.claude/rules/java25/modern-java.md` for details.

## Memory Keys

- `swarm/yawl/architecture` — Architecture decisions (permanent)
- `swarm/yawl/patterns` — Pattern implementations (permanent)
- `swarm/yawl/integration` — Integration knowledge (permanent)
- `swarm/yawl/tests` — Test patterns (30 days)
- `swarm/yawl/sessions` — Session context (7 days)
