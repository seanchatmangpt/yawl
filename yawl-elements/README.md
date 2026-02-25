# yawl-elements

**Artifact:** `org.yawlfoundation:yawl-elements:6.0.0-Beta` | `packaging: jar`
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

## Test Coverage

| Test Class | Package | Tests | Focus |
|------------|---------|-------|-------|
| `TestYAtomicTask` | `elements` | 3 | Atomic task split/join semantics |
| `TestYCompositeTask` | `elements` | 2 | Composite task decomposition |
| `TestYExternalCondition` | `elements` | 2 | Condition marking and flow |
| `TestYExternalNetElement` | `elements` | 1 | Net element base behaviour |
| `TestYExternalTask` | `elements` | 11 | External task lifecycle |
| `TestYFlowsInto` | `elements` | 5 | Flow arc predicates and ordering |
| `TestYInputCondition` | `elements` | 1 | Input condition token handling |
| `TestYMultiInstanceAttributes` | `elements` | 2 | MI task threshold attributes |
| `TestYNet` | `elements` | 7 | Net reset, marking, token sets |
| `TestYNetElement` | `elements` | 1 | Abstract element properties |
| `TestYOutputCondition` | `elements` | 1 | Output condition semantics |
| `TestYSpecification` | `elements` | 6 | Spec loading and validation |
| `TestYTimerParametersParsing` | `elements` | 19 | ISO 8601 duration / date parsing |
| `YSpecVersionTest` | `elements` | 39 | Specification version comparison and ordering |
| `TestDataParsing` | `elements` | 1 | Data element XML round-trip |
| `TestYIdentifier` | `elements.state` | 5 | Case/net identifier uniqueness |
| `TestYMarking` | `elements.state` | 8 | Petri net marking equality and operations |
| `TestYSetOfMarkings` | `elements.state` | 1 | Marking set membership |
| `TestSchemaHandler` | `schema` | 5 | XSD schema loading |
| `TestSchemaHandlerValidation` | `schema` | 7 | Schema validation against spec XML |

**Total: ~115 tests across 20 test classes**

Run with: `mvn -pl yawl-utilities,yawl-elements test`

## Roadmap

- **Sealed `YNetElement` hierarchy** — convert `YNetElement` / `YTask` / `YCondition` to sealed classes with exhaustive pattern matching in task routing logic
- **Record-based value types** — migrate `YFlow`, `YTimerParameters`, and `YSpecificationID` to Java records for immutability and structural equality
- **YAWL 4.0 schema support** — add parser support for any forthcoming schema extensions while maintaining backward compatibility with 2.x / 3.0 / 4.0 specs
- **`YNet` Petri net analyser** — expose reachability graph and soundness check results via a public API (currently buried in verification code)
- **Increase schema validation coverage** — add property-based tests for `TestYTimerParametersParsing` using parameterised JUnit 5 `@MethodSource`
- **`YSpecVersionTest` — migrate to JUnit 5** — currently JUnit 4 `@RunWith`; migrate to `@ExtendWith` and add version range boundary tests
