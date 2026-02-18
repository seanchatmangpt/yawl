# yawl-elements

**Artifact:** `org.yawlfoundation:yawl-elements:6.0.0-Alpha` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

Core domain model grounded in formal Petri net theory. Defines the structural building
blocks of every YAWL workflow specification:

- **`org.yawlfoundation.yawl.elements`** — `YNet`, `YTask`, `YCondition`, `YFlow`,
  `YSpecification`, `YDecomposition`, composite tasks, atomic tasks, input/output conditions
- **`org.yawlfoundation.yawl.schema`** — schema version resolution and XSD validation support

## Internal Dependencies

| Module | Reason |
|--------|--------|
| `yawl-utilities` | exceptions, util helpers, schema, unmarshal |

## Key Third-Party Dependencies

| Artifact | Purpose |
|----------|---------|
| `jdom2` | XML DOM (specifications are XML documents) |
| `jaxen` | XPath evaluation over JDOM trees |
| `jakarta.xml.bind-api` + `jaxb-impl` | JAXB marshalling |
| `commons-lang3` | String/object utilities |
| `commons-collections4` | Extended collection types |
| `log4j-api` | Logging |

Test dependencies: JUnit 4, JUnit 5 Jupiter, Hamcrest, XMLUnit.

## Build Configuration Notes

- **Source directory:** `../src`; the compiler filter includes `elements/**`, `schema/**`, plus
  `util`, `exceptions`, `unmarshal` (re-compiled here to support test dependencies)
- A small subset of `engine` classes is also compiled here to satisfy element test requirements:
  `YEngine`, `YPersistenceManager`, `YWorkItemStatus`, `YSpecificationID`, `YEngineID`,
  `time/**`, `core/**`, and related exception types
- Test resources include XML fixtures from `elements`, `schema`, `unmarshal`, and `engine` package trees
- Log4j-to-SLF4J bridge excluded from test classpath to prevent conflict with `log4j-slf4j2-impl`

## Quick Build

```bash
mvn -pl yawl-utilities,yawl-elements clean package
```
