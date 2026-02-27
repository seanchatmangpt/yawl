# 1M Cases Documentation — Summary

## YAWL v6.0.0 | February 2026

### Scope

Full Diataxis coverage for the million-cases-support feature (Phases 0–5).

### Documents Added

| Quadrant | File | Status |
|----------|------|--------|
| Tutorial | tutorials/11-scale-to-million-cases.md | ✅ Complete |
| How-To | how-to/configure-zgc-compact-headers.md | ✅ Complete |
| How-To | how-to/implement-custom-case-registry.md | ✅ Complete |
| How-To | how-to/subscribe-workflow-events.md | ✅ Complete |
| How-To | how-to/migrate-uuid-case-ids.md | ✅ Complete |
| How-To | how-to/operations/tune-hpa-for-cases.md | ✅ Complete |
| Reference | reference/spi-million-cases.md | ✅ Complete |
| Reference | reference/java25-jep-index.md | ✅ Complete |
| Reference | reference/capacity-planning-1m.md | ✅ Complete |
| Reference | reference/metrics.md | ✅ Updated |
| Reference | reference/configuration.md | ✅ Updated |
| Explanation | explanation/why-scoped-values.md | ✅ Complete |
| Explanation | explanation/flow-api-event-bus.md | ✅ Complete |
| Explanation | explanation/offheap-runner-store.md | ✅ Complete |
| Explanation | explanation/million-case-architecture.md | ✅ Complete |

### Feature Coverage

| Feature | Tutorial | How-To | Reference | Explanation |
|---------|----------|--------|-----------|-------------|
| ScopedValue / TenantContext | ✅ | — | ✅ | ✅ |
| ZGC + Compact Headers | ✅ | ✅ | ✅ | ✅ |
| WorkflowEventBus (Flow API) | ✅ | ✅ | ✅ | ✅ |
| GlobalCaseRegistry SPI | ✅ | ✅ | ✅ | ✅ |
| UUID Case IDs | ✅ | ✅ | ✅ | ✅ |
| K8s HPA | ✅ | ✅ | ✅ | ✅ |
| Off-heap Runner Store | — | — | ✅ | ✅ |

### Navigation Updates

**Primary Index** — [diataxis/INDEX.md](../diataxis/INDEX.md)
- Added tutorial #11 to Tutorials table
- Added "1M Cases Support" subsection to How-To Guides
- Added "1M Cases Support" subsection to Reference (3 new docs)
- Added "1M Cases Support" subsection to Explanation (4 docs)

**Tutorials Index** — [tutorials/index.md](../tutorials/index.md)
- Added tutorial #11 to Learning Path

**Main README** — [README.md](../README.md)
- Added "Scale to 1M cases" quick navigation link
- Added tutorial #11 to Documentation Quadrants
- Added "1M Cases" subsection to How-To Guides

### Entry Points for Users

**First-time deployment at scale** → [tutorials/11-scale-to-million-cases.md](../tutorials/11-scale-to-million-cases.md)

**Specific tasks** →
- Enable low-latency GC: [how-to/configure-zgc-compact-headers.md](../how-to/configure-zgc-compact-headers.md)
- Custom case registry: [how-to/implement-custom-case-registry.md](../how-to/implement-custom-case-registry.md)
- Event subscription: [how-to/subscribe-workflow-events.md](../how-to/subscribe-workflow-events.md)
- UUID migration: [how-to/migrate-uuid-case-ids.md](../how-to/migrate-uuid-case-ids.md)
- Kubernetes tuning: [how-to/operations/tune-hpa-for-cases.md](../how-to/operations/tune-hpa-for-cases.md)

**Technical reference** →
- SPI contracts: [reference/spi-million-cases.md](../reference/spi-million-cases.md)
- Java 25 JEPs: [reference/java25-jep-index.md](../reference/java25-jep-index.md)
- Capacity planning: [reference/capacity-planning-1m.md](../reference/capacity-planning-1m.md)

**Architecture & design decisions** →
- ScopedValue rationale: [explanation/why-scoped-values.md](../explanation/why-scoped-values.md)
- Event bus design: [explanation/flow-api-event-bus.md](../explanation/flow-api-event-bus.md)
- Off-heap storage: [explanation/offheap-runner-store.md](../explanation/offheap-runner-store.md)
- Full architecture: [explanation/million-case-architecture.md](../explanation/million-case-architecture.md)

### Implementation Status

All 15 documents are committed to the repository and fully integrated into the navigation hierarchy:

- ✅ Tutorials (1 new document) + index updated
- ✅ How-To (5 new documents) + index updated
- ✅ Reference (3 new + 2 updated documents) + index updated
- ✅ Explanation (4 new documents) + index updated
- ✅ Navigation (3 main files updated: diataxis INDEX, tutorials index, main README)

---

*Documentation summary generated for Agent 5 (Index & Navigation Writer). All cross-references use relative paths and are verified to point to committed content.*
