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

## Test Coverage

**No tests exist.** The test directory `../test/org/yawlfoundation/yawl/scheduling/` does not exist.

Coverage gaps (entire module):
- Timer firing and deadline calculation — no unit tests
- Calendar-aware duration computation (working hours, holidays) — no unit tests
- Calendar query API — no unit tests
- Engine timer integration (timer task creation / cancellation) — no tests

## Roadmap

- **Timer unit tests** — add `TestSchedulingTimer` covering deadline calculation, interval parsing, and cancellation semantics
- **Calendar service tests** — add `TestCalendarService` with synthetic business-hours calendars verifying working-time computation and holiday exclusions
- **iCal (RFC 5545) import** — implement an `ICalendarImporter` so calendar definitions can be loaded from standard `.ics` files
- **Quartz Scheduler integration** — add an optional Quartz-backed `YQuartzScheduler` for clustered, persistent timer management (currently in-JVM only)
- **REST API for calendar management** — expose calendar CRUD and schedule query over JAX-RS to allow runtime calendar updates without engine restart
- **Time zone awareness** — enforce `ZoneId`-based arithmetic throughout; replace any remaining `Date` / `Calendar` usages with `java.time` types
