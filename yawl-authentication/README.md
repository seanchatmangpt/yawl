# yawl-authentication

**Artifact:** `org.yawlfoundation:yawl-authentication:6.0.0-Alpha` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

Session management and security infrastructure for the YAWL engine:

- JWT token issuance and validation
- CSRF protection
- Client credential management and registration
- Hibernate-backed session store (H2 embedded by default)

## Internal Dependencies

| Module | Reason |
|--------|--------|
| `yawl-elements` | domain model (transitively pulls `yawl-utilities`) |

## Key Third-Party Dependencies

| Artifact | Purpose |
|----------|---------|
| `jakarta.persistence-api` | JPA annotations on session entities |
| `jakarta.servlet-api` | Servlet API (provided by container) |
| `hibernate-core` | ORM for session persistence |
| `h2` | Embedded relational database (default session store) |
| `commons-lang3` | Utility helpers |
| `log4j-api` | Logging |

## Build Configuration Notes

- **Source directory:** `../src/org/yawlfoundation/yawl/authentication` (scoped to a single package,
  not the entire `src/` monorepo tree)
- Hibernate mapping files (`*.hbm.xml`) and properties files are included as classpath resources
- No test sources declared — authentication logic is integration-tested via `yawl-engine-webapp`

## Quick Build

```bash
mvn -pl yawl-utilities,yawl-elements,yawl-authentication clean package
```
