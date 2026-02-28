# How to Evaluate DMN Decisions with DmnDecisionService

## Problem

You have a DMN 1.2 or 1.3 XML decision table and need to evaluate it against runtime data — extracting typed outputs (booleans, strings, numbers) from matched rules, applying COLLECT aggregations across multi-hit results, and validating inputs against a known schema before evaluation. `DmnDecisionService` provides all of this without an external BPMN engine.

## Prerequisites

- `yawl-dmn` on your classpath (version 6.0.0-GA)
- GraalVM JDK 24.1+ at runtime (required for `dmn_feel_engine.wasm`)
- A DMN 1.2 or 1.3 XML string

## Steps

### 1. Evaluate a UNIQUE decision (schema-less)

For a quick evaluation without input validation, use the no-arg constructor:

```java
import org.yawlfoundation.yawl.dmn.DmnDecisionService;
import org.yawlfoundation.yawl.graalwasm.dmn.*;

String dmnXml = """
    <?xml version="1.0" encoding="UTF-8"?>
    <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                 id="ShippingDecision" name="Shipping"
                 namespace="http://example.com">
      <decision id="ShippingCost" name="ShippingCost">
        <decisionTable id="dt1" hitPolicy="UNIQUE">
          <input id="in1" label="country">
            <inputExpression typeRef="string"><text>country</text></inputExpression>
          </input>
          <output id="out1" label="cost" name="cost" typeRef="double"/>
          <rule id="r1">
            <inputEntry><text>"US"</text></inputEntry>
            <outputEntry><text>5.99</text></outputEntry>
          </rule>
          <rule id="r2">
            <inputEntry><text>"DE","FR","GB"</text></inputEntry>
            <outputEntry><text>12.50</text></outputEntry>
          </rule>
          <rule id="r3">
            <inputEntry><text>-</text></inputEntry>
            <outputEntry><text>29.99</text></outputEntry>
          </rule>
        </decisionTable>
      </decision>
    </definitions>
    """;

try (DmnDecisionService service = new DmnDecisionService()) {

    DmnWasmBridge.DmnModel model = service.parseDmnModel(dmnXml);

    DmnEvaluationContext ctx = DmnEvaluationContext.builder()
        .put("country", "DE")
        .build();

    DmnDecisionResult result = service.evaluate(model, "ShippingCost", ctx);

    result.getSingleResult().ifPresent(row -> {
        double cost = ((Number) row.get("cost")).doubleValue();
        System.out.printf("Shipping cost to DE: %.2f%n", cost);
    });
}
```

Expected output:

```
Shipping cost to DE: 12.50
```

---

### 2. Evaluate with a DataModel schema for input validation

Use `DmnDecisionService(DataModel schema)` to validate the evaluation context against declared column types before each call. Schema violations are logged as warnings — they never block evaluation:

```java
import org.yawlfoundation.yawl.dmn.*;

DataModel schema = DataModel.builder("ShippingSchema")
    .table(DmnTable.builder("Order")
        .column(DmnColumn.of("country", "string").build())
        .column(DmnColumn.of("weight", "double").build())
        .build())
    .build();

try (DmnDecisionService service = new DmnDecisionService(schema)) {

    DmnWasmBridge.DmnModel model = service.parseDmnModel(dmnXml);

    // Context with type-correct values
    DmnEvaluationContext ctx = DmnEvaluationContext.builder()
        .put("country", "US")
        .put("weight", 1.5)
        .build();

    DmnDecisionResult result = service.evaluate(model, "ShippingCost", ctx);
    result.getSingleResult().ifPresent(row ->
        System.out.println("Cost: " + row.get("cost")));
}
```

If `weight` is passed as a String instead of a Number, the schema validator logs a warning and evaluation still proceeds with FEEL's own type coercion.

---

### 3. Build a DataModel with relationships

`DmnRelationship` documents the ER structure for tooling. Schema validation uses column-level data, not relationships, but relationships enable documentation and graph analysis:

```java
import org.yawlfoundation.yawl.dmn.*;

DataModel schema = DataModel.builder("EcommerceSchema")
    .table(DmnTable.builder("Customer")
        .column(DmnColumn.of("customerId", "string").build())
        .column(DmnColumn.of("tier", "string").build())    // "gold", "silver", "basic"
        .build())
    .table(DmnTable.builder("Order")
        .column(DmnColumn.of("orderId", "string").build())
        .column(DmnColumn.of("totalAmount", "double").build())
        .build())
    .relationship(DmnRelationship.builder("customer-orders")
        .fromTable("Customer")
        .toTable("Order")
        .sourceCardinality(EndpointCardinality.ONE_ONE)
        .targetCardinality(EndpointCardinality.ZERO_MANY)
        .build())
    .build();

// Validate integrity before use
List<String> errors = schema.validateIntegrity();
if (!errors.isEmpty()) {
    throw new IllegalStateException("Schema errors: " + errors);
}
```

---

### 4. Handle COLLECT decisions and apply aggregation

For decision tables with hit policy COLLECT, multiple rules may fire. Use `evaluateAndAggregate()` to compute SUM, MIN, MAX, or COUNT across all matched output values:

```java
String collectDmn = """
    <?xml version="1.0" encoding="UTF-8"?>
    <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                 id="RiskScoring" name="Risk Scoring"
                 namespace="http://example.com">
      <decision id="RiskFactors" name="RiskFactors">
        <decisionTable id="dt1" hitPolicy="COLLECT">
          <input id="in1" label="tier">
            <inputExpression typeRef="string"><text>tier</text></inputExpression>
          </input>
          <input id="in2" label="totalAmount">
            <inputExpression typeRef="double"><text>totalAmount</text></inputExpression>
          </input>
          <output id="out1" label="riskPoints" name="riskPoints" typeRef="double"/>
          <rule id="r1">
            <inputEntry><text>"basic"</text></inputEntry>
            <inputEntry><text>-</text></inputEntry>
            <outputEntry><text>20.0</text></outputEntry>
          </rule>
          <rule id="r2">
            <inputEntry><text>-</text></inputEntry>
            <inputEntry><text>&gt; 10000</text></inputEntry>
            <outputEntry><text>15.0</text></outputEntry>
          </rule>
          <rule id="r3">
            <inputEntry><text>-</text></inputEntry>
            <inputEntry><text>&gt; 50000</text></inputEntry>
            <outputEntry><text>25.0</text></outputEntry>
          </rule>
        </decisionTable>
      </decision>
    </definitions>
    """;

try (DmnDecisionService service = new DmnDecisionService()) {

    DmnWasmBridge.DmnModel model = service.parseDmnModel(collectDmn);

    DmnEvaluationContext ctx = DmnEvaluationContext.builder()
        .put("tier", "basic")
        .put("totalAmount", 75000.0)
        .build();

    // SUM: all three rules fire → 20.0 + 15.0 + 25.0
    java.util.OptionalDouble totalRisk = service.evaluateAndAggregate(
        model, "RiskFactors", ctx, "riskPoints", DmnCollectAggregation.SUM);
    System.out.println("Total risk: " + totalRisk.orElse(0));

    // MAX: highest single risk factor
    java.util.OptionalDouble maxRisk = service.evaluateAndAggregate(
        model, "RiskFactors", ctx, "riskPoints", DmnCollectAggregation.MAX);
    System.out.println("Max risk factor: " + maxRisk.orElse(0));

    // COUNT: how many rules fired
    java.util.OptionalDouble count = service.evaluateAndAggregate(
        model, "RiskFactors", ctx, "riskPoints", DmnCollectAggregation.COUNT);
    System.out.println("Rules fired: " + (int) count.orElse(0));
}
```

Expected output:

```
Total risk: 60.0
Max risk factor: 25.0
Rules fired: 3
```

---

### 5. Apply COLLECT aggregation to an existing result

When you need multiple aggregation views of the same evaluation, evaluate once and aggregate separately:

```java
DmnDecisionResult result = service.evaluate(model, "RiskFactors", ctx);

java.util.OptionalDouble sum  = service.collectAggregate(result, "riskPoints", DmnCollectAggregation.SUM);
java.util.OptionalDouble min  = service.collectAggregate(result, "riskPoints", DmnCollectAggregation.MIN);
java.util.OptionalDouble max  = service.collectAggregate(result, "riskPoints", DmnCollectAggregation.MAX);
java.util.OptionalDouble cnt  = service.collectAggregate(result, "riskPoints", DmnCollectAggregation.COUNT);

System.out.printf("SUM=%.1f MIN=%.1f MAX=%.1f COUNT=%d%n",
    sum.orElse(0), min.orElse(0), max.orElse(0), (int) cnt.orElse(0));
```

---

### 6. Look up a table in the DataModel schema

Use `DataModel.getTable()` to introspect the schema at runtime:

```java
DataModel schema = DataModel.builder("LoanSchema")
    .table(DmnTable.builder("Applicant")
        .column(DmnColumn.of("age", "integer").build())
        .column(DmnColumn.of("income", "double").build())
        .build())
    .build();

schema.getTable("Applicant").ifPresent(table -> {
    System.out.println("Table: " + table.getName());
    System.out.println("Columns: " + table.getColumnNames());
});

System.out.println("Has Applicant: " + schema.hasTable("Applicant"));
System.out.println("Table count:   " + schema.tableCount());
```

---

### 7. Resolve COLLECT aggregation from a DMN symbol or name

Use `DmnCollectAggregation.fromValue()` when the aggregation operator comes from configuration:

```java
// Accepts DMN symbols: "C+", "C<", "C>", "C#"
DmnCollectAggregation agg = DmnCollectAggregation.fromValue("C+");  // → SUM

// Accepts names: "SUM", "MIN", "MAX", "COUNT"
DmnCollectAggregation agg2 = DmnCollectAggregation.fromValue("MAX"); // → MAX

// From YAML or database config
String configuredAgg = loadFromConfig("risk.aggregation");          // e.g. "C>"
DmnCollectAggregation operator = DmnCollectAggregation.fromValue(configuredAgg);
```

---

## Verification

Run a known-correct decision and assert the output:

```java
try (DmnDecisionService service = new DmnDecisionService()) {
    DmnWasmBridge.DmnModel model = service.parseDmnModel(dmnXml);
    DmnEvaluationContext ctx = DmnEvaluationContext.builder()
        .put("country", "US").build();
    DmnDecisionResult result = service.evaluate(model, "ShippingCost", ctx);

    var row = result.getSingleResult().orElseThrow();
    assert ((Number) row.get("cost")).doubleValue() == 5.99 :
        "Expected cost 5.99 for US";
}
```

---

## Troubleshooting

### `DmnException: Decision not found: MyDecision`

The `decisionId` must match the `id` attribute of the `<decision>` element in the DMN XML, not the `name` attribute. Check the XML: `<decision id="ShippingCost" name="Shipping Cost">` → use `"ShippingCost"`.

### `result.getSingleResult()` is empty for a COLLECT decision

COLLECT hit policy returns matched rules via `result.getMatchedRules()`, not `getSingleResult()`. Switch to `collectAggregate()` or `evaluateAndAggregate()`.

### Schema validation warnings appear for correct types

The `DataModel` schema warns when a context value's Java type doesn't exactly match the declared column type. `put("income", 50000)` passes an `Integer` but the column declares `double`. Use `put("income", 50000.0)` to pass a `Double`.

### `IllegalArgumentException: DataModel '...' has integrity violations`

A relationship in the `DataModel` references a table name that is not declared in the same `DataModel`. Call `schema.validateIntegrity()` before passing the schema to `DmnDecisionService` to get the specific error message.
