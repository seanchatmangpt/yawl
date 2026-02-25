# yawl-utilities

**Artifact:** `org.yawlfoundation:yawl-utilities:6.0.0-Beta` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

Foundation library shared by every other YAWL module. Provides:

- **`org.yawlfoundation.yawl.util`** — general-purpose helpers (string, XML, IO, reflection)
- **`org.yawlfoundation.yawl.exceptions`** — custom exception hierarchy (`YConnectivityException`, `YSyntaxException`, etc.)
- **`org.yawlfoundation.yawl.schema`** — XSD schema handling and YAWL schema version management
- **`org.yawlfoundation.yawl.unmarshal`** — YAWL specification file unmarshalling
- **`org.yawlfoundation.yawl.authentication`** (base only) — credential helpers without engine dependency
- **`org.yawlfoundation.yawl.logging`** (partial) — logging infrastructure excluding engine-coupled classes

## Internal Dependencies

None — this is the base module with no YAWL dependencies.

## Key Third-Party Dependencies

| Artifact | Purpose |
|----------|---------|
| `commons-lang3` | String and object utilities |
| `commons-io` | File and stream utilities |
| `commons-codec` | Base64 / hex encoding |
| `jdom2` | XML DOM manipulation |
| `jakarta.xml.bind-api` + `jaxb-impl` | JAXB marshalling / unmarshalling |
| `log4j-api` + `log4j-core` | Logging implementation |
| `slf4j-api` | Logging facade |

## Build Configuration Notes

- **Source directory:** `../src` (shared monorepo layout; all modules point to the same `src/`)
- The compiler `<includes>` filter restricts compilation to exactly six packages:
  `authentication`, `logging` (partial), `schema`, `unmarshal`, `util`, `exceptions`
- **Six logging classes excluded** (compiled in `yawl-engine` instead because they depend on `YEngine`):
  `YEventLogger`, `YEventKeyCache`, `YLogPredicate`, `YLogPredicateWorkItemParser`, `YLogServer`, `YXESBuilder`
- Annotation processing disabled (`-proc:none`) to prevent stale JMH annotation-processor files
- Schema XSD files are copied to `schema/` on the classpath
- Unmarshal XSD files are preserved at their package path for `getClass().getResource()` resolution
- Test scope is limited to four self-contained tests (`TestYConnectivityException`, `TestYSyntaxException`,
  `TestYAWLExceptionEnhancements`, `TestDynamicValueLogging`)

## Quick Build

```bash
mvn -pl yawl-utilities clean package
```

## Test Coverage

| Test Class | Package | Tests | Notes |
|------------|---------|-------|-------|
| `TestYConnectivityException` | `exceptions` | 2 | Connectivity error message and cause chain |
| `TestYSyntaxException` | `exceptions` | 1 | Syntax error construction |
| `TestYAWLExceptionEnhancements` | `exceptions` | 1 | Exception hierarchy and wrapping |
| `TestDynamicValueLogging` | `util` | ~4 | Dynamic log-level value capture |

**Total active tests: ~8** (scoped to the four classes that have no transitive engine dependency)

Tests run with: `mvn -pl yawl-utilities test`

Coverage gaps:
- `util` package helpers (string, XML, IO utilities) — untested at module scope
- `unmarshal` package — tested via `yawl-elements`
- `schema` package — tested via `yawl-elements`
- Partial `authentication` and `logging` classes compiled here — covered in `yawl-authentication` and `yawl-engine`

## Roadmap

- **JSpecify null-safety sweep** — annotate all public APIs with `@Nullable` / `@NonNull`; enforce via NullAway in CI
- **Virtual-thread-safe utilities** — audit and document thread-safety guarantees on shared utility classes for virtual-thread environments
- **Jakarta JAXB 4.0 migration** — replace `com.sun.xml.bind:jaxb-impl` with the standard `org.glassfish.jaxb:jaxb-runtime` once all modules are on Jakarta EE 10
- **Expand exception tests** — cover all 12 exception types in `YException` hierarchy with JUnit 5 parameterised tests
- **Commons IO / Codec upgrade** — migrate from legacy `commons-io` groupId to `org.apache.commons` groupId throughout the monorepo
- **`util` unit test suite** — add `TestYXMLHelper`, `TestYStringHelper`, `TestYFileUtil` to reach >80% line coverage on the util package
