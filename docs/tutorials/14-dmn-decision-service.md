# Tutorial: DMN Decision Evaluation with DmnDecisionService

By the end of this tutorial you will have built a working loan eligibility service that uses `DmnDecisionService` to evaluate a DMN 1.3 decision table inside the JVM, with a typed `DataModel` schema validating inputs before every evaluation and COLLECT aggregation computing risk scores across multiple matched rules — all without any external decision engine, REST calls, or BPM platform.

---

## Prerequisites

- YAWL built locally: `bash scripts/dx.sh -pl yawl-dmn` succeeds
- GraalVM JDK 24.1+ at runtime (required for WebAssembly support)
- Basic familiarity with DMN decision tables (hit policies, inputs, outputs)

---

## Step 0: Add the Maven dependency

Add `yawl-dmn` to your `pom.xml`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-dmn</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

---

## Step 1: Write a DMN decision table

Create a string containing a DMN 1.3 XML decision table. For this tutorial, the table determines loan eligibility based on applicant age and annual income:

```java
String dmnXml = """
    <?xml version="1.0" encoding="UTF-8"?>
    <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                 id="LoanEligibility" name="Loan Eligibility"
                 namespace="http://example.com/loan">
      <decision id="EligibilityCheck" name="EligibilityCheck">
        <decisionTable id="dt1" hitPolicy="UNIQUE">
          <input id="in1" label="age">
            <inputExpression typeRef="integer">
              <text>age</text>
            </inputExpression>
          </input>
          <input id="in2" label="income">
            <inputExpression typeRef="double">
              <text>income</text>
            </inputExpression>
          </input>
          <output id="out1" label="eligible" name="eligible" typeRef="boolean"/>
          <output id="out2" label="reason" name="reason" typeRef="string"/>
          <rule id="r1">
            <inputEntry><text>&lt; 18</text></inputEntry>
            <inputEntry><text>-</text></inputEntry>
            <outputEntry><text>false</text></outputEntry>
            <outputEntry><text>"Too young"</text></outputEntry>
          </rule>
          <rule id="r2">
            <inputEntry><text>[18..65]</text></inputEntry>
            <inputEntry><text>&gt;= 30000</text></inputEntry>
            <outputEntry><text>true</text></outputEntry>
            <outputEntry><text>"Approved"</text></outputEntry>
          </rule>
          <rule id="r3">
            <inputEntry><text>[18..65]</text></inputEntry>
            <inputEntry><text>&lt; 30000</text></inputEntry>
            <outputEntry><text>false</text></outputEntry>
            <outputEntry><text>"Insufficient income"</text></outputEntry>
          </rule>
          <rule id="r4">
            <inputEntry><text>&gt; 65</text></inputEntry>
            <inputEntry><text>-</text></inputEntry>
            <outputEntry><text>false</text></outputEntry>
            <outputEntry><text>"Above age limit"</text></outputEntry>
          </rule>
        </decisionTable>
      </decision>
    </definitions>
    """;
```

This table has UNIQUE hit policy: at most one rule fires per evaluation. The inputs are `age` (integer) and `income` (double). Outputs are `eligible` (boolean) and `reason` (string).

---

## Step 2: Build a DataModel schema

A `DataModel` describes the shape of data flowing into the DMN decision. It validates input contexts before evaluation and documents the expected schema for tooling and humans:

```java
import org.yawlfoundation.yawl.dmn.DataModel;
import org.yawlfoundation.yawl.dmn.DmnTable;
import org.yawlfoundation.yawl.dmn.DmnColumn;

DataModel schema = DataModel.builder("LoanSchema")
    .description("Input schema for loan eligibility decisions")
    .table(DmnTable.builder("Applicant")
        .column(DmnColumn.of("age", "integer").build())
        .column(DmnColumn.of("income", "double").build())
        .build())
    .build();

System.out.println("Schema: " + schema);
```

Expected output:

```
Schema: DataModel{name='LoanSchema', tables=1, relationships=0}
```

The schema has one table (`Applicant`) with two typed columns. When you later call `evaluate()`, the service checks that the context contains these fields and that their types match.

---

## Step 3: Create the service and parse the DMN model

Create a `DmnDecisionService` with the schema, then parse the DMN XML into an executable model:

```java
import org.yawlfoundation.yawl.dmn.DmnDecisionService;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnWasmBridge;

try (DmnDecisionService service = new DmnDecisionService(schema)) {

    DmnWasmBridge.DmnModel model = service.parseDmnModel(dmnXml);
    System.out.println("DMN model parsed successfully");

    // All subsequent steps go inside this try block
}
```

The `DmnDecisionService` implements `AutoCloseable`. The `parseDmnModel()` call validates the XML and returns a reusable model object. Parse once, evaluate many times.

Expected output:

```
DMN model parsed successfully
```

---

## Step 4: Build an evaluation context and evaluate

Construct a context with the applicant's values and evaluate the `EligibilityCheck` decision:

```java
import org.yawlfoundation.yawl.graalwasm.dmn.DmnEvaluationContext;
import org.yawlfoundation.yawl.graalwasm.dmn.DmnDecisionResult;

DmnEvaluationContext ctx = DmnEvaluationContext.builder()
    .put("age", 35)
    .put("income", 55000.0)
    .build();

DmnDecisionResult result = service.evaluate(model, "EligibilityCheck", ctx);

result.getSingleResult().ifPresent(row -> {
    System.out.println("Eligible: " + row.get("eligible"));
    System.out.println("Reason:   " + row.get("reason"));
});
```

Expected output:

```
Eligible: true
Reason:   Approved
```

The service validated the context against the `LoanSchema` before calling the WASM engine. The FEEL expressions `[18..65]` and `>= 30000` were evaluated by `dmn_feel_engine.wasm`.

---

## Step 5: Test boundary cases

Evaluate with a rejected applicant:

```java
DmnEvaluationContext youngCtx = DmnEvaluationContext.builder()
    .put("age", 17)
    .put("income", 60000.0)
    .build();

DmnDecisionResult youngResult = service.evaluate(model, "EligibilityCheck", youngCtx);
youngResult.getSingleResult().ifPresent(row -> {
    System.out.println("Eligible: " + row.get("eligible"));
    System.out.println("Reason:   " + row.get("reason"));
});
```

Expected output:

```
Eligible: false
Reason:   Too young
```

---

## Step 6: Add a COLLECT decision and use aggregation

Extend the DMN XML to include a second decision that uses COLLECT hit policy to compute multiple risk scores, then add a second step that aggregates them.

First, add a COLLECT decision to the DMN definitions:

```java
String dmnXmlWithCollect = """
    <?xml version="1.0" encoding="UTF-8"?>
    <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                 id="LoanRisk" name="Loan Risk"
                 namespace="http://example.com/loan">
      <decision id="RiskFactors" name="RiskFactors">
        <decisionTable id="dt2" hitPolicy="COLLECT">
          <input id="in1" label="age">
            <inputExpression typeRef="integer">
              <text>age</text>
            </inputExpression>
          </input>
          <input id="in2" label="income">
            <inputExpression typeRef="double">
              <text>income</text>
            </inputExpression>
          </input>
          <output id="out1" label="riskScore" name="riskScore" typeRef="double"/>
          <rule id="rf1">
            <inputEntry><text>&lt; 25</text></inputEntry>
            <inputEntry><text>-</text></inputEntry>
            <outputEntry><text>15.0</text></outputEntry>
          </rule>
          <rule id="rf2">
            <inputEntry><text>-</text></inputEntry>
            <inputEntry><text>&lt; 25000</text></inputEntry>
            <outputEntry><text>25.0</text></outputEntry>
          </rule>
          <rule id="rf3">
            <inputEntry><text>-</text></inputEntry>
            <inputEntry><text>[25000..50000)</text></inputEntry>
            <outputEntry><text>10.0</text></outputEntry>
          </rule>
        </decisionTable>
      </decision>
    </definitions>
    """;
```

Now evaluate and aggregate:

```java
import org.yawlfoundation.yawl.dmn.DmnCollectAggregation;

try (DmnDecisionService riskService = new DmnDecisionService(schema)) {

    DmnWasmBridge.DmnModel riskModel = riskService.parseDmnModel(dmnXmlWithCollect);

    // Applicant: age 22, income 30000 → risk factors: age (<25) fires, income (25k-50k) fires
    DmnEvaluationContext riskCtx = DmnEvaluationContext.builder()
        .put("age", 22)
        .put("income", 30000.0)
        .build();

    // Sum all risk scores from matched rules
    java.util.OptionalDouble totalRisk = riskService.evaluateAndAggregate(
        riskModel, "RiskFactors", riskCtx, "riskScore", DmnCollectAggregation.SUM);

    totalRisk.ifPresent(score ->
        System.out.println("Total risk score: " + score));

    // Max risk score
    java.util.OptionalDouble maxRisk = riskService.evaluateAndAggregate(
        riskModel, "RiskFactors", riskCtx, "riskScore", DmnCollectAggregation.MAX);

    maxRisk.ifPresent(score ->
        System.out.println("Worst risk factor: " + score));
}
```

Expected output:

```
Total risk score: 25.0
Worst risk factor: 15.0
```

Both the age rule (`riskScore=15.0`) and the income bracket rule (`riskScore=10.0`) fired. SUM = 25.0. MAX = 15.0 (computed by `feel_max` in `dmn_feel_engine.wasm`).

---

## Complete program

```java
import org.yawlfoundation.yawl.dmn.*;
import org.yawlfoundation.yawl.graalwasm.dmn.*;

public class LoanEligibilityDemo {

    private static final String ELIGIBILITY_DMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                     id="LoanEligibility" name="Loan Eligibility"
                     namespace="http://example.com/loan">
          <decision id="EligibilityCheck" name="EligibilityCheck">
            <decisionTable id="dt1" hitPolicy="UNIQUE">
              <input id="in1" label="age">
                <inputExpression typeRef="integer"><text>age</text></inputExpression>
              </input>
              <input id="in2" label="income">
                <inputExpression typeRef="double"><text>income</text></inputExpression>
              </input>
              <output id="out1" label="eligible" name="eligible" typeRef="boolean"/>
              <output id="out2" label="reason" name="reason" typeRef="string"/>
              <rule id="r1">
                <inputEntry><text>&lt; 18</text></inputEntry>
                <inputEntry><text>-</text></inputEntry>
                <outputEntry><text>false</text></outputEntry>
                <outputEntry><text>"Too young"</text></outputEntry>
              </rule>
              <rule id="r2">
                <inputEntry><text>[18..65]</text></inputEntry>
                <inputEntry><text>&gt;= 30000</text></inputEntry>
                <outputEntry><text>true</text></outputEntry>
                <outputEntry><text>"Approved"</text></outputEntry>
              </rule>
              <rule id="r3">
                <inputEntry><text>[18..65]</text></inputEntry>
                <inputEntry><text>&lt; 30000</text></inputEntry>
                <outputEntry><text>false</text></outputEntry>
                <outputEntry><text>"Insufficient income"</text></outputEntry>
              </rule>
              <rule id="r4">
                <inputEntry><text>&gt; 65</text></inputEntry>
                <inputEntry><text>-</text></inputEntry>
                <outputEntry><text>false</text></outputEntry>
                <outputEntry><text>"Above age limit"</text></outputEntry>
              </rule>
            </decisionTable>
          </decision>
        </definitions>
        """;

    public static void main(String[] args) {
        DataModel schema = DataModel.builder("LoanSchema")
            .table(DmnTable.builder("Applicant")
                .column(DmnColumn.of("age", "integer").build())
                .column(DmnColumn.of("income", "double").build())
                .build())
            .build();

        try (DmnDecisionService service = new DmnDecisionService(schema)) {

            DmnWasmBridge.DmnModel model = service.parseDmnModel(ELIGIBILITY_DMN);

            // Approved applicant
            check(service, model, 35, 55000.0);

            // Under-age
            check(service, model, 17, 60000.0);

            // Low income
            check(service, model, 30, 20000.0);

            // Over age limit
            check(service, model, 70, 80000.0);
        }
    }

    private static void check(DmnDecisionService svc,
                               DmnWasmBridge.DmnModel model,
                               int age, double income) {
        DmnEvaluationContext ctx = DmnEvaluationContext.builder()
            .put("age", age)
            .put("income", income)
            .build();

        DmnDecisionResult result = svc.evaluate(model, "EligibilityCheck", ctx);
        result.getSingleResult().ifPresent(row -> System.out.printf(
            "age=%-3d income=%8.0f → eligible=%-5s reason=%s%n",
            age, income, row.get("eligible"), row.get("reason")));
    }
}
```

Expected output:

```
age=35  income=  55000 → eligible=true  reason=Approved
age=17  income=  60000 → eligible=false reason=Too young
age=30  income=  20000 → eligible=false reason=Insufficient income
age=70  income=  80000 → eligible=false reason=Above age limit
```

---

## You have now

- Declared a typed `DataModel` schema with `DmnTable` and `DmnColumn`
- Written DMN 1.3 XML with UNIQUE and COLLECT hit policies
- Parsed a DMN model into an executable form once and reused it
- Evaluated a UNIQUE decision and read typed outputs from `getSingleResult()`
- Applied COLLECT hit policy aggregation using `DmnCollectAggregation.SUM` and `DmnCollectAggregation.MAX`
- Confirmed that the schema validation layer warns on type mismatches before any WASM call
- Run all FEEL expression evaluation **entirely inside the JVM** via `dmn_feel_engine.wasm`

---

## What next

- **Multi-table schemas**: Add more `DmnTable` entities and `DmnRelationship` edges to model joins
- **Runtime schema-less evaluation**: Use `new DmnDecisionService()` (no-arg) when you want FEEL to handle its own type coercions without Java-side pre-validation
- **Chained decisions**: Call `evaluate()` multiple times for decision chains, passing outputs as inputs to downstream decisions
- **Explanation Guide** (`docs/explanation/dmn-graalwasm-engine.md`) — understand how FEEL expressions are evaluated inside GraalWASM and why
