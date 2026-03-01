# NEW DIATAXIS ENTRIES FOR PI, RESOURCING, AND INTEGRATION

These entries should be added to INDEX.md in the appropriate sections:

## TUTORIALS - Process Intelligence (Add to PI section)
| # | Tutorial | What you learn |
|---|----------|----------------|
| PI-00 | [Getting Started](../tutorials/pi-getting-started.md) | Build first predictive model, deploy predictions, make recommendations |

## TUTORIALS - Resource Management  (Add new section after PI)
| # | Tutorial | What you learn |
|---|----------|----------------|
| RM-00 | [Getting Started](../tutorials/resourcing-getting-started.md) | Build org model, allocate work items, integrate with engine |

## TUTORIALS - External Integration (Add new section after RM)
| # | Tutorial | What you learn |
|---|----------|----------------|
| INT-00 | [Getting Started](../tutorials/integration-getting-started.md) | Set up MCP server, connect AI agents, implement A2A skills |

---

## HOW-TO GUIDES - Process Intelligence (Replace existing entry)
| Guide | Task |
|-------|------|
| [PI Configuration](../how-to/pi-configuration.md) | Configure PI module, enable/disable services, set thresholds |

## HOW-TO GUIDES - Resource Management (Add new section)
| Guide | Task |
|-------|------|
| [Design Org Models](../how-to/resourcing-org-model.md) | Create flat/departmental/matrix org models, sync LDAP/AD |

## HOW-TO GUIDES - External Integration (Add new section)
| Guide | Task |
|-------|------|
| [MCP/A2A Integration](../how-to/integration-getting-started.md) | Set up MCP server, create custom tools, implement A2A skills |

---

## REFERENCE - Process Intelligence (Add new section after API Documentation)
| Reference | Contents |
|-----------|----------|
| [PI API](../reference/pi-api.md) | ProcessIntelligenceFacade, predictive/prescriptive/optimization/RAG APIs |

## REFERENCE - Resource Management (Add new section)
| Reference | Contents |
|-----------|----------|
| [Resourcing API](../reference/resourcing-api.md) | AllocationStrategy, Participant, Organization, worklist operations |

## REFERENCE - Integration (Add new section)
| Reference | Contents |
|-----------|----------|
| [Integration API](../reference/integration-api.md) | MCP tools, A2A skills, SPIFFE/SVID, deduplication, observability |

---

## EXPLANATION - Process Intelligence (Add to Architecture section)
| Explanation | What it addresses |
|-------------|-------------------|
| [Process Intelligence Architecture](../explanation/pi-architecture.md) | Five AI connections, design principles, performance, caching, resilience |

## EXPLANATION - Resource Management (Add new section)
| Explanation | What it addresses |
|-------------|-------------------|
| [Resource Allocation Design](../explanation/resource-allocation.md) | Allocation strategies, org models, constraint enforcement, sync patterns |

## EXPLANATION - Integration (Add new section)
| Explanation | What it addresses |
|-------------|-------------------|
| [Integration Architecture](../explanation/integration-architecture.md) | MCP vs A2A, observability, security, idempotency, scalability patterns |

