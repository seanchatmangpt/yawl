# yawl-engine

**Artifact:** `org.yawlfoundation:yawl-engine:6.0.0-Alpha` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

The stateful, persistent workflow engine — the central runtime for YAWL:

- **`YEngine`** — lifecycle management, case creation, work item routing, Interface B/E/X
- **`YNetRunner`** — Petri net token propagation and firing rules
- **`YWorkItem`** — work item state machine (enabled → executing → completed / failed)
- **`YPersistenceManager`** — Hibernate-based state persistence across restarts
- **`YEventLogger`** + supporting logging classes — structured workflow event log (XES-compatible)
- **`swingWorklist`** — Swing-based worklist UI (bundled with engine JAR)
- **`engine.time`** — timer management, deadline tracking
- **`engine.core`** — case monitor, instance control

## Internal Dependencies

| Module | Reason |
|--------|--------|
| `yawl-elements` | Petri net model (transitively pulls `yawl-utilities`) |

## Key Third-Party Dependencies

| Artifact | Scope | Purpose |
|----------|-------|---------|
| `jakarta.persistence-api` | compile | JPA annotations |
| `jakarta.servlet-api` | provided | Servlet container integration |
| `hibernate-core` | compile | ORM / session factory |
| `hibernate-hikaricp` | compile | HikariCP connection pool integration |
| `h2` | compile | Embedded DB for development and tests |
| `postgresql` | runtime | Production relational database |
| `mysql-connector-j` | runtime | Alternative production database |
| `HikariCP` | compile | High-performance connection pool |
| `commons-lang3` | compile | Utilities |
| `commons-dbcp2` | compile | Apache DBCP connection pool (alternative) |
| `jdom2` | compile | XML processing |
| `jakarta.xml.bind-api` | compile | JAXB |
| `log4j-api` + `log4j-core` | compile | Logging |

Test dependencies: JUnit 4, Hamcrest.

## Build Configuration Notes

- **Source directory:** `../src`; compiler filter includes `engine/**`, `swingWorklist/**`, plus the
  six engine-coupled logging classes excluded from `yawl-utilities`:
  `YEventLogger`, `YEventKeyCache`, `YLogPredicate`, `YLogPredicateWorkItemParser`, `YLogServer`, `YXESBuilder`
- **Schema XSD files** are copied to `org/yawlfoundation/yawl/unmarshal/` on the classpath,
  as required by `YSchemaVersion` for runtime schema validation
- **Test resources** include Hibernate configuration (`hibernate.cfg.xml`) from `../build/properties`
- Tests cover `engine/**`, `swingWorklist/**`, `patternmatching/**`
- Log4j-to-SLF4J bridge excluded from test classpath

## Quick Build

```bash
mvn -pl yawl-utilities,yawl-elements,yawl-engine clean package
```
