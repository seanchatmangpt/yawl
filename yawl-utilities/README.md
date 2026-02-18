# yawl-utilities

**Artifact:** `org.yawlfoundation:yawl-utilities:6.0.0-Alpha` | `packaging: jar`
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
