# SHACL-to-JUnit Mapping Specification

## Overview

This specification defines how SHACL (Shapes Constraint Language) constraints map to JUnit test generation. This enables automated test generation from ontology constraints, ensuring that YAWL engine implementations conform to their semantic definitions.

## Namespace Prefixes

```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix yawl: <http://yawlfoundation.org/yawl/ns#> .
@prefix junit: <http://yawlfoundation.org/yawl/ns/junit#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
```

## Core Mapping Rules

### 1. SHACL Shape to JUnit Test Class

| SHACL Construct | JUnit Mapping |
|-----------------|---------------|
| `sh:NodeShape` | `@TestClass` with test methods |
| `sh:PropertyShape` | Individual `@Test` method |
| `sh:targetClass` | Test class naming: `Test{ClassName}` |
| `sh:targetNode` | Test class for specific instance |

**Example Mapping:**

```turtle
# SHACL Shape
yawl:EngineShape
    a sh:NodeShape ;
    sh:targetClass yawl:YEngine ;
    sh:property [
        sh:path yawl:specificationId ;
        sh:datatype xsd:string ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
    ] .
```

```java
// Generated JUnit Test
@DisplayName("YEngine Shape Constraints")
public class TestYEngineShape {

    @Test
    @DisplayName("specificationId must be exactly one string")
    public void testSpecificationIdConstraint() {
        YEngine engine = YEngine.getInstance();
        String specId = engine.getSpecificationId();

        assertNotNull("specificationId must not be null", specId);
        assertTrue("specificationId must be string",
            specId instanceof String);
    }
}
```

### 2. SHACL Constraint to Assertion Mapping

| SHACL Constraint | JUnit Assertion |
|------------------|-----------------|
| `sh:minCount n` | `assertNotNull` + count check |
| `sh:maxCount n` | count assertion |
| `sh:minExclusive n` | `assertTrue(value > n)` |
| `sh:maxExclusive n` | `assertTrue(value < n)` |
| `sh:minInclusive n` | `assertTrue(value >= n)` |
| `sh:maxInclusive n` | `assertTrue(value <= n)` |
| `sh:pattern regex` | `assertTrue(value.matches(regex))` |
| `sh:minLength n` | `assertTrue(value.length() >= n)` |
| `sh:maxLength n` | `assertTrue(value.length() <= n)` |
| `sh:datatype type` | `assertInstanceOf(type, value)` |
| `sh:class cls` | `assertInstanceOf(cls, value)` |
| `sh:node kind` | Type validation |
| `sh:hasValue value` | `assertEquals(value, actual)` |
| `sh:in list` | `assertTrue(list.contains(value))` |
| `sh:equals path` | Equality check |
| `sh:disjoint path` | Disjoint check |
| `sh:lessThan path` | Comparison check |
| `sh:lessThanOrEquals path` | Comparison check |
| `sh:not shape` | Negation check |
| `sh:and shapes` | All must pass |
| `sh:or shapes` | At least one passes |
| `sh:xone shapes` | Exactly one passes |

### 3. Logical Constraint Mapping

#### AND Constraint

```turtle
sh:and (
    [ sh:datatype xsd:string ]
    [ sh:minLength 5 ]
)
```

```java
@Test
public void testAndConstraint() {
    Object value = getValue();
    assertTrue("Must be string AND at least 5 chars",
        value instanceof String && ((String) value).length() >= 5);
}
```

#### OR Constraint

```turtle
sh:or (
    [ sh:datatype xsd:string ]
    [ sh:datatype xsd:integer ]
)
```

```java
@Test
public void testOrConstraint() {
    Object value = getValue();
    assertTrue("Must be string OR integer",
        value instanceof String || value instanceof Integer);
}
```

#### NOT Constraint

```turtle
sh:not [
    sh:datatype xsd:string
]
```

```java
@Test
public void testNotConstraint() {
    Object value = getValue();
    assertFalse("Must NOT be string",
        value instanceof String);
}
```

#### XOR (XONE) Constraint

```turtle
sh:xone (
    [ sh:datatype xsd:string ]
    [ sh:datatype xsd:integer ]
)
```

```java
@Test
public void testXoneConstraint() {
    Object value = getValue();
    int matches = 0;
    if (value instanceof String) matches++;
    if (value instanceof Integer) matches++;
    assertEquals("Exactly one constraint must match", 1, matches);
}
```

### 4. YAWL-Specific SHACL Extensions

#### YSpecification Shape

```turtle
yawl:SpecificationShape
    a sh:NodeShape ;
    sh:targetClass yawl:YSpecification ;

    sh:property [
        sh:path yawl:uri ;
        sh:datatype xsd:string ;
        sh:pattern "^[a-zA-Z0-9_-]+\\.xml$" ;
        sh:message "URI must end with .xml" ;
    ] ;

    sh:property [
        sh:path yawl:rootNet ;
        sh:class yawl:YNet ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:message "Specification must have exactly one root net" ;
    ] ;

    sh:property [
        sh:path yawl:decomposition ;
        sh:class yawl:YDecomposition ;
        sh:minCount 1 ;
        sh:message "Specification must have at least one decomposition" ;
    ] .
```

Generated test:

```java
@DisplayName("YSpecification Shape Constraints")
public class TestYSpecificationShape {

    private YSpecification spec;

    @BeforeEach
    public void setUp() throws Exception {
        spec = loadTestSpecification("test-spec.xml");
    }

    @Test
    @DisplayName("URI must match pattern")
    public void testUriPattern() {
        String uri = spec.getURI();
        assertNotNull("URI must not be null", uri);
        assertTrue("URI must end with .xml",
            uri.matches("^[a-zA-Z0-9_-]+\\.xml$"));
    }

    @Test
    @DisplayName("Root net must exist")
    public void testRootNetExists() {
        YNet rootNet = spec.getRootNet();
        assertNotNull("Specification must have exactly one root net", rootNet);
    }

    @Test
    @DisplayName("Decompositions must exist")
    public void testDecompositionsExist() {
        Collection<YDecomposition> decomps = spec.getDecompositions();
        assertNotNull("Decompositions must not be null", decomps);
        assertTrue("Specification must have at least one decomposition",
            decomps.size() >= 1);
    }
}
```

#### YNet Shape

```turtle
yawl:NetShape
    a sh:NodeShape ;
    sh:targetClass yawl:YNet ;

    sh:property [
        sh:path yawl:inputCondition ;
        sh:class yawl:YInputCondition ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:message "Net must have exactly one input condition" ;
    ] ;

    sh:property [
        sh:path yawl:outputCondition ;
        sh:class yawl:YOutputCondition ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:message "Net must have exactly one output condition" ;
    ] ;

    sh:property [
        sh:path yawl:task ;
        sh:class yawl:YTask ;
        sh:message "Net tasks must be YTask instances" ;
    ] .
```

#### YTask Shape

```turtle
yawl:TaskShape
    a sh:NodeShape ;
    sh:targetClass yawl:YTask ;

    sh:property [
        sh:path yawl:joinType ;
        sh:in ( "xor" "and" "or" ) ;
        sh:message "Join type must be xor, and, or or" ;
    ] ;

    sh:property [
        sh:path yawl:splitType ;
        sh:in ( "xor" "and" "or" ) ;
        sh:message "Split type must be xor, and, or or" ;
    ] ;

    sh:property [
        sh:path yawl:flowsInto ;
        sh:class yawl:YFlow ;
        sh:minCount 1 ;
        sh:message "Task must have at least one outgoing flow" ;
    ] .
```

### 5. Custom Test Generation Properties

SHACL annotations can include test generation hints:

```turtle
yawl:TaskShape
    sh:property [
        sh:path yawl:joinType ;
        junit:testName "testJoinTypeValid" ;
        junit:exception YSyntaxException ;
        junit:setupMethod "createTestTask" ;
        junit:cleanupMethod "destroyTestTask" ;
        junit:timeout 30 ;
        junit:repeated 5 ;
    ] .
```

Generated test:

```java
@RepeatedTest(5)
@Timeout(value = 30, unit = TimeUnit.SECONDS)
@Test
@DisplayName("Join type must be valid")
public void testJoinTypeValid() {
    YTask task = createTestTask();
    try {
        task.setJoinType("invalid");
        fail("Should throw YSyntaxException");
    } catch (YSyntaxException e) {
        assertTrue("Exception message should mention join type",
            e.getMessage().contains("join"));
    } finally {
        destroyTestTask(task);
    }
}
```

### 6. Message Mapping

SHACL `sh:message` becomes assertion message:

```turtle
sh:property [
    sh:path yawl:caseId ;
    sh:minCount 1 ;
    sh:message "Case ID is required for case tracking" ;
]
```

```java
assertNotNull("Case ID is required for case tracking", case.getCaseId());
```

### 7. Severity to Test Category

| SHACL Severity | JUnit Tag |
|----------------|-----------|
| `sh:Info` | `@Tag("info")` |
| `sh:Warning` | `@Tag("warning")` |
| `sh:Violation` | `@Tag("violation")` (default) |

```turtle
sh:property [
    sh:path yawl:deprecatedField ;
    sh:severity sh:Warning ;
    sh:message "This field is deprecated" ;
]
```

```java
@Tag("warning")
@Test
@DisplayName("Deprecated field warning")
public void testDeprecatedField() {
    // Warning-level test
}
```

### 8. Deactivated Shapes

Shapes with `sh:deactivated true` generate `@Disabled` tests:

```turtle
yawl:FutureFeatureShape
    sh:deactivated true ;
    sh:property [ ... ]
```

```java
@Disabled("Shape deactivated: future feature")
@Test
public void testFutureFeature() {
    // Test not executed
}
```

## Complete Example: YEngine SHACL Shape

```turtle
@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix yawl: <http://yawlfoundation.org/yawl/ns#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

yawl:EngineShape
    a sh:NodeShape ;
    sh:targetClass yawl:YEngine ;

    # Engine must be a singleton
    sh:property [
        sh:path yawl:instance ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:message "YEngine must be a singleton" ;
        junit:testName "testSingletonPattern" ;
    ] ;

    # Specifications collection
    sh:property [
        sh:path yawl:loadedSpecification ;
        sh:class yawl:YSpecification ;
        sh:message "All loaded items must be YSpecification instances" ;
        junit:testName "testLoadedSpecifications" ;
    ] ;

    # Work item repository
    sh:property [
        sh:path yawl:workItemRepository ;
        sh:class yawl:YWorkItemRepository ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:message "Engine must have exactly one work item repository" ;
        junit:testName "testWorkItemRepository" ;
    ] ;

    # Case ID format
    sh:property [
        sh:path yawl:caseIdFormat ;
        sh:pattern "^[0-9]+:[a-zA-Z0-9_-]+$" ;
        sh:message "Case IDs must follow format: id:taskname" ;
        junit:testName "testCaseIdFormat" ;
    ] .

# Generate test class
# TestYEngineShape.java
```

## Test Generation Pipeline

```
1. Parse SHACL shapes from TTL/JSON-LD
2. Extract target classes and constraints
3. For each NodeShape:
   a. Create test class Test{ClassName}Shape
   b. For each PropertyShape:
      i. Generate @Test method
      ii. Map constraint to assertion
      iii. Add setup/teardown from annotations
   c. Add class-level annotations
4. Generate test suite aggregating all shape tests
5. Output JUnit 4/5 compatible Java files
```

## Configuration

Test generation can be configured via JSON:

```json
{
  "shaclInput": "shapes/yawl-shapes.ttl",
  "outputDir": "test/org/yawlfoundation/yawl/generated/",
  "junitVersion": "5",
  "package": "org.yawlfoundation.yawl.generated",
  "generateSuite": true,
  "suiteName": "ShaclValidationSuite",
  "imports": [
    "org.yawlfoundation.yawl.engine.YEngine",
    "org.yawlfoundation.yawl.elements.*"
  ],
  "customAssertions": {
    "yawl:validSpecification": "assertSpecificationValid"
  }
}
```

## Metadata

- **Version**: 1.0.0
- **Author**: YAWL Foundation
- **License**: LGPL 3.0
- **Compatibility**: SHACL 1.0, JUnit 4.13+, JUnit 5.8+
