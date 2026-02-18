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

## Test Coverage

| Test Class | Tests | Focus |
|------------|-------|-------|
| `TestConnections` | 4 | Client registration, credential validation, session lookup |
| `TestJwtManager` | 19 | Token issuance, expiry, signature verification, refresh |
| `V6SecurityTest` | 0 | Suite placeholder — no assertions yet |

**Total active tests: 23**

Run with: `mvn -pl yawl-utilities,yawl-elements,yawl-authentication test`

Coverage gaps:
- CSRF filter logic — not unit-tested; covered only through webapp integration tests
- H2 session store persistence round-trip — partially covered by `TestConnections`
- Token revocation and blacklisting — no test coverage

## Roadmap

- **OAuth 2.0 / OIDC integration** — add an `OIDCAuthenticationFilter` supporting standard `Authorization: Bearer` flows; enable delegation to Keycloak, Okta, or Azure AD
- **Azure AD / Microsoft Graph** — activate the `microsoft-graph` and `azure-identity` libraries already in the parent BOM for SSO in enterprise deployments
- **JWT refresh-token rotation** — implement sliding-window refresh with single-use refresh tokens and revocation list (Redis or DB-backed)
- **CSRF test suite** — write `TestCSRFFilter` covering double-submit cookie, header validation, and pre-flight bypass for CORS requests
- **Hibernate second-level cache** — enable JCache / Ehcache on the session store for multi-node engine deployments (currently single-node H2 only)
- **Session audit trail** — persist login/logout events to the XES workflow log for compliance and forensic analysis
