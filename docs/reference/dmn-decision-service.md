# DMN Decision Service API Reference

**Module**: `yawl-dmn` | **Primary class**: `org.yawlfoundation.yawl.dmn.DmnDecisionService`

`yawl-dmn` provides DMN 1.2/1.3 decision evaluation via the GraalWASM `dmn_feel_engine.wasm` engine. The Java layer adds schema-level input validation (`DataModel`), an entity-relationship schema model (`DmnTable`, `DmnColumn`, `DmnRelationship`), and post-evaluation COLLECT aggregation (`DmnCollectAggregation`).

---

## DmnDecisionService

`org.yawlfoundation.yawl.dmn.DmnDecisionService` implements `AutoCloseable`.

### Constructors

#### `DmnDecisionService(DataModel schema)`

Creates a service with schema-validated input. Before each `evaluate()` call, context variables are checked against the tables in `schema`. Type mismatches produce warnings but do not block evaluation.

```java
DataModel schema = DataModel.builder("LoanSchema")
    .table(DmnTable.builder("Applicant")
        .column(DmnColumn.of("age", "integer").build())
        .build())
    .build();

DmnDecisionService svc = new DmnDecisionService(schema);
```

**Throws**: `IllegalArgumentException` if `schema.validateIntegrity()` returns errors.

#### `DmnDecisionService()`

Creates a schema-less service. No input validation is performed before evaluation; FEEL handles its own type coercions.

```java
DmnDecisionService svc = new DmnDecisionService();
```

---

### Methods

#### `parseDmnModel(String dmnXml)`

Parses DMN 1.2 or 1.3 XML into an executable model object. Parse once; evaluate many times.

```java
DmnWasmBridge.DmnModel model = svc.parseDmnModel(dmnXml);
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `dmnXml` | `String` | DMN 1.2 or 1.3 XML; must not be null or blank |

**Returns**: `DmnWasmBridge.DmnModel` — opaque handle to the parsed decision graph.

**Throws**: `DmnException` if the XML is malformed or the DMN version is unsupported.

---

#### `evaluate(DmnWasmBridge.DmnModel model, String decisionId, DmnEvaluationContext ctx)`

Evaluates a named decision against the given context.

```java
DmnDecisionResult result = svc.evaluate(model, "EligibilityCheck", ctx);
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `model` | `DmnWasmBridge.DmnModel` | Parsed model from `parseDmnModel()` |
| `decisionId` | `String` | The `id` attribute of the `<decision>` element (not the `name`) |
| `ctx` | `DmnEvaluationContext` | Input variable bindings |

**Returns**: `DmnDecisionResult` — contains matched rules and output values.

**Throws**: `DmnException` if evaluation fails or the decision ID is not found.

**Schema validation**: If a `DataModel` was provided at construction, context variables are validated against all tables before calling the WASM engine. Warnings are logged but evaluation proceeds.

---

#### `evaluateAndAggregate(DmnWasmBridge.DmnModel model, String decisionId, DmnEvaluationContext ctx, String outputColumn, DmnCollectAggregation aggregation)`

Evaluates a COLLECT decision and applies an aggregation to a named output column in one step.

```java
OptionalDouble total = svc.evaluateAndAggregate(
    model, "RiskFactors", ctx, "riskScore", DmnCollectAggregation.SUM);
```

**Returns**: `OptionalDouble` — the aggregated value. Empty if no rules matched or the column has no numeric values.

---

#### `collectAggregate(DmnDecisionResult result, String outputColumn, DmnCollectAggregation aggregation)`

Applies a COLLECT aggregation to a previously-evaluated result. Use when you need multiple aggregation views without re-evaluating.

```java
DmnDecisionResult result = svc.evaluate(model, "RiskFactors", ctx);
OptionalDouble sum = svc.collectAggregate(result, "riskScore", DmnCollectAggregation.SUM);
OptionalDouble max = svc.collectAggregate(result, "riskScore", DmnCollectAggregation.MAX);
```

For `COUNT`, the method returns the total number of matched rows regardless of the column content.

---

#### `getSchema()`

Returns the `DataModel` schema this service validates inputs against, or `null` if schema validation is disabled.

---

#### `close()`

Closes the underlying `DmnWasmBridge` and releases WASM resources. Idempotent.

---

## DmnDecisionResult

`org.yawlfoundation.yawl.graalwasm.dmn.DmnDecisionResult`

| Method | Return type | Description |
|--------|-------------|-------------|
| `getSingleResult()` | `Optional<Map<String, Object>>` | First matched rule's output values. For UNIQUE/FIRST hit policies. |
| `getMatchedRules()` | `List<Map<String, Object>>` | All matched rules' output values. For COLLECT/RULE ORDER/OUTPUT ORDER. |
| `isEmpty()` | `boolean` | True if no rules matched |

Output map values are typed Java objects: `Boolean`, `String`, `Integer`, `Double`, etc. depending on the FEEL expression result.

---

## DmnEvaluationContext

`org.yawlfoundation.yawl.graalwasm.dmn.DmnEvaluationContext`

Immutable map of input variable name → value bindings.

```java
DmnEvaluationContext ctx = DmnEvaluationContext.builder()
    .put("age",    35)
    .put("income", 55000.0)
    .put("tier",   "gold")
    .build();

Map<String, Object> asMap = ctx.asMap();
Set<String> keys = ctx.keySet();
```

**Accepted value types**: `Boolean`, `String`, `Integer`, `Long`, `Float`, `Double`, `BigDecimal`. Null values are allowed and propagate as FEEL `null`.

---

## DataModel

`org.yawlfoundation.yawl.dmn.DataModel`

Describes the entity-relationship schema for DMN inputs. Immutable after construction.

### Builder

```java
DataModel model = DataModel.builder("ModelName")
    .description("Optional description")
    .table(table1)
    .table(table2)
    .relationship(rel1)
    .build();
```

**Throws** (from `builder`): `IllegalArgumentException` if name is null or blank.

**Throws** (from `table()`): `IllegalArgumentException` on duplicate table name.

**Throws** (from `relationship()`): `IllegalArgumentException` on duplicate relationship name.

### Methods

| Method | Return type | Description |
|--------|-------------|-------------|
| `getName()` | `String` | Model identifier |
| `getDescription()` | `String?` | Optional description |
| `getTables()` | `List<DmnTable>` | All tables in declaration order (unmodifiable) |
| `getRelationships()` | `List<DmnRelationship>` | All relationships in declaration order (unmodifiable) |
| `getTable(String name)` | `Optional<DmnTable>` | Table by name |
| `hasTable(String name)` | `boolean` | Whether a table with this name exists |
| `tableCount()` | `int` | Number of tables |
| `relationshipCount()` | `int` | Number of relationships |
| `getRelationshipsFrom(String tableName)` | `List<DmnRelationship>` | Outbound relationships from a table |
| `getRelationshipsTo(String tableName)` | `List<DmnRelationship>` | Inbound relationships to a table |
| `getRelationship(String name)` | `Optional<DmnRelationship>` | Relationship by name |
| `validateIntegrity()` | `List<String>` | Referential integrity errors (empty = valid) |

---

## DmnTable

`org.yawlfoundation.yawl.dmn.DmnTable`

A named entity with typed columns. Corresponds to a DMN input data entity.

### Builder

```java
DmnTable table = DmnTable.builder("Applicant")
    .description("Loan applicant data")
    .column(DmnColumn.of("age", "integer").build())
    .column(DmnColumn.of("income", "double").nullable(true).build())
    .build();
```

### Methods

| Method | Return type | Description |
|--------|-------------|-------------|
| `getName()` | `String` | Table name |
| `getDescription()` | `String?` | Optional description |
| `getColumns()` | `List<DmnColumn>` | Columns in declaration order |
| `getColumnNames()` | `List<String>` | Column name list |
| `getColumn(String name)` | `Optional<DmnColumn>` | Column by name |
| `hasColumn(String name)` | `boolean` | Whether a column with this name exists |
| `columnCount()` | `int` | Number of columns |
| `validateRow(Map<String, Object> row)` | `List<String>` | Validate a context row against column types. Returns error messages; empty = valid. |

---

## DmnColumn

`org.yawlfoundation.yawl.dmn.DmnColumn`

A named, typed column in a `DmnTable`. Immutable after construction.

### Factory method

```java
DmnColumn col = DmnColumn.of("age", "integer")
    .nullable(false)
    .primaryKey(false)
    .description("Applicant age in years")
    .build();
```

### Supported type strings

| Type string | FEEL type | Java type |
|-------------|-----------|-----------|
| `"string"` | string | `String` |
| `"integer"`, `"int"` | number (integer) | `Integer`, `Long` |
| `"double"`, `"number"`, `"decimal"` | number | `Double`, `BigDecimal` |
| `"boolean"` | boolean | `Boolean` |
| `"date"` | date | `String` (ISO-8601) |
| `"datetime"` | date and time | `String` (ISO-8601) |
| `"time"` | time | `String` (ISO-8601) |
| `"duration"` | days and time duration | `String` (ISO-8601) |

### Methods

| Method | Return type | Description |
|--------|-------------|-------------|
| `getName()` | `String` | Column name |
| `getType()` | `String` | Declared type string |
| `isNullable()` | `boolean` | Whether null values are accepted (default: `true`) |
| `isPrimaryKey()` | `boolean` | Whether this column is a primary key (default: `false`) |
| `getDescription()` | `String?` | Optional description |

---

## DmnRelationship

`org.yawlfoundation.yawl.dmn.DmnRelationship`

A directed edge between two tables in a `DataModel`.

### Builder

```java
DmnRelationship rel = DmnRelationship.builder("applicant-to-orders")
    .fromTable("Applicant")
    .toTable("Order")
    .sourceCardinality(EndpointCardinality.ONE_ONE)
    .targetCardinality(EndpointCardinality.ZERO_MANY)
    .description("An applicant may have zero or more orders")
    .build();
```

### Methods

| Method | Return type | Description |
|--------|-------------|-------------|
| `getName()` | `String` | Relationship name |
| `getFromTable()` | `String` | Source table name |
| `getToTable()` | `String` | Target table name |
| `getSourceCardinality()` | `EndpointCardinality` | Cardinality at the source end |
| `getTargetCardinality()` | `EndpointCardinality` | Cardinality at the target end |
| `getDescription()` | `String?` | Optional description |

---

## EndpointCardinality

`org.yawlfoundation.yawl.dmn.EndpointCardinality`

| Constant | Notation | Meaning |
|----------|----------|---------|
| `ONE_ONE` | `1..1` | Exactly one |
| `ZERO_ONE` | `0..1` | Zero or one |
| `ONE_MANY` | `1..*` | One or more |
| `ZERO_MANY` | `0..*` | Zero or more |

---

## DmnCollectAggregation

`org.yawlfoundation.yawl.dmn.DmnCollectAggregation`

COLLECT hit policy aggregation operators. `MIN` and `MAX` delegate numeric comparison to `feel_min`/`feel_max` in `dmn_feel_engine.wasm`.

| Constant | DMN symbol | Description |
|----------|------------|-------------|
| `SUM` | `C+` | Sum of all numeric values |
| `MIN` | `C<` | Minimum numeric value (WASM-accelerated) |
| `MAX` | `C>` | Maximum numeric value (WASM-accelerated) |
| `COUNT` | `C#` | Count of matched rows |

### Methods

| Method | Signature | Description |
|--------|-----------|-------------|
| `aggregate` | `double aggregate(Collection<? extends Number> values)` | Applies the aggregation to a collection |
| `aggregateDoubles` | `OptionalDouble aggregateDoubles(List<Double> values)` | Aggregates a list of doubles; returns empty for empty list |
| `getDmnSymbol` | `String getDmnSymbol()` | Returns the DMN standard symbol (`"C+"`, `"C<"`, etc.) |
| `isNumericAggregation` | `boolean isNumericAggregation()` | True for SUM, MIN, MAX; false for COUNT |
| `fromValue` (static) | `DmnCollectAggregation fromValue(String value)` | Resolves from DMN symbol or name (case-insensitive) |

```java
// By DMN symbol
DmnCollectAggregation.fromValue("C+");   // → SUM
DmnCollectAggregation.fromValue("C>");   // → MAX

// By name
DmnCollectAggregation.fromValue("COUNT"); // → COUNT
```

**Throws**: `IllegalArgumentException` for unrecognised values.

---

## DmnException

`org.yawlfoundation.yawl.graalwasm.dmn.DmnException`

Thrown by `parseDmnModel()` and `evaluate()` on failure. Extends `RuntimeException`.

| Scenario | Message pattern |
|----------|----------------|
| Invalid DMN XML | `"Failed to parse DMN model: ..."` |
| Decision not found | `"Decision not found: <id>"` |
| FEEL evaluation error | `"FEEL evaluation failed for decision <id>: ..."` |
| WASM execution error | `"WASM execution error: ..."` |

---

## Runtime Requirements

| Requirement | Minimum |
|-------------|---------|
| JDK | GraalVM JDK 24.1+ |
| GraalVM languages | `wasm` (GraalWASM) |
| WASM binary | `dmn_feel_engine.wasm` on classpath (`wasm/dmn_feel_engine.wasm`) |
| Memory | ~30 MB for WASM engine initialisation |
