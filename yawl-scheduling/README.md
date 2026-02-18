# yawl-scheduling

**Artifact:** `org.yawlfoundation:yawl-scheduling:6.0.0-Alpha` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

Workflow timer and calendar integration service:

- Deadline and duration management for tasks and cases
- Calendar-aware scheduling (working hours, public holidays, shifts)
- Integration with the engine's work item timer mechanism
- Resource utilisation scheduling and calendar queries

## Internal Dependencies

| Module | Reason |
|--------|--------|
| `yawl-engine` | engine APIs, timer hook points |

## Key Third-Party Dependencies

| Artifact | Purpose |
|----------|---------|
| `commons-lang3` | Utilities |
| `log4j-api` | Logging |

This is the lightest module in the build — no ORM, no XML parser, no JSON library
dependencies beyond what is inherited transitively from `yawl-engine`.

## Build Configuration Notes

- **Source directory:** `../src/org/yawlfoundation/yawl/scheduling` (scoped to single package)
- **Test directory:** `../test/org/yawlfoundation/yawl/scheduling`

## Quick Build

```bash
mvn -pl yawl-utilities,yawl-elements,yawl-engine,yawl-scheduling clean package
```
