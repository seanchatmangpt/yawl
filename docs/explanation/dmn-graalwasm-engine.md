# The DMN GraalWASM Engine

**Quadrant**: Explanation | **Concept**: FEEL evaluation via GraalWASM, DmnDecisionService layering, COLLECT aggregation delegation

This document explains how DMN decisions are evaluated in YAWL — what the WASM engine does, why it is layered the way it is, and where the FEEL semantics live.

---

## The Problem with DMN Evaluation

DMN decision tables use the **FEEL** (Friendly Enough Expression Language) — an OMG standard language for expressing constraints, conditions, and expressions in decision tables. FEEL has:

- Interval expressions: `[18..65]` (inclusive range), `(0..100)` (exclusive)
- Unary tests: `>= 30000`, `< 18`, `"DE","FR","GB"` (string enumeration)
- Arithmetic: `a + b * c`
- Date/time arithmetic: `date("2026-01-01") + duration("P1Y")`
- Built-in functions: `sum()`, `min()`, `max()`, `count()`, `string()`, `date()`
- Boolean connectives: `a and b`, `not(a)`

A correct FEEL parser and evaluator is a non-trivial implementation — FEEL is a significant language with a full specification. Rather than implement FEEL in Java, `yawl-dmn` delegates all FEEL evaluation to `dmn_feel_engine.wasm`, a precompiled Rust implementation running inside GraalWASM.

---

## Architecture Layers

```
DmnDecisionService          ← Java: schema validation, COLLECT aggregation, lifecycle
    ↓
DmnWasmBridge               ← Java: DMN XML parsing, decision graph, FEEL calls
    ↓
WasmExecutionEngine         ← yawl-graalwasm: GraalWASM context management
    ↓
dmn_feel_engine.wasm        ← Rust: FEEL parser, evaluator, decision table matching
```

Each layer has a distinct responsibility:

**`dmn_feel_engine.wasm`** — evaluates FEEL expressions and applies hit policies (UNIQUE, FIRST, ANY, COLLECT, RULE ORDER, OUTPUT ORDER). It is the only place where FEEL semantics are implemented. All rule matching, interval checking, and output selection happen here.

**`DmnWasmBridge`** — manages the GraalWASM context, loads the WASM module, converts DMN XML to the WASM-expected format, passes evaluation contexts to WASM, and converts results back to Java `Map<String, Object>`.

**`DmnDecisionService`** — the public API for application code. It adds:
1. Schema validation (pre-evaluation input checking against `DataModel`)
2. COLLECT aggregation (post-evaluation numeric reduction)
3. `AutoCloseable` lifecycle

Application code should always use `DmnDecisionService`, not `DmnWasmBridge` directly.

---

## The WASM Boundary

At the WASM boundary, all data is represented as either primitive numeric types (`i32`, `i64`, `f64`) or as pointers into WASM linear memory. The Rust FEEL engine exports functions that:

1. Accept a pointer + length for a JSON-encoded evaluation context
2. Accept a pointer + length for the DMN XML
3. Execute FEEL evaluation and write the result to WASM memory
4. Return a pointer + length for the result JSON

`DmnWasmBridge` manages this memory protocol — allocating WASM memory for inputs, calling the export, reading the result, and freeing memory. Application code never sees any of this.

The WASM binary is loaded once at construction via `WasmModule.loadFromClasspath("wasm/dmn_feel_engine.wasm")`. WASM module instantiation is the expensive step (~30 ms). Subsequent evaluations are fast (<1 ms for typical decision tables).

---

## Why `DmnDecisionService` Adds Schema Validation

FEEL is dynamically typed. A FEEL expression like `age >= 18` will silently evaluate to `false` (instead of throwing) if `age` is passed as a string `"thirty-five"` rather than a number `35`. The WASM engine cannot distinguish these cases without type information.

`DataModel` provides that type information. When a schema is declared:

```java
DataModel schema = DataModel.builder("LoanSchema")
    .table(DmnTable.builder("Applicant")
        .column(DmnColumn.of("age", "integer").build())
        .column(DmnColumn.of("income", "double").build())
        .build())
    .build();
```

`DmnDecisionService` calls `table.validateRow(ctx.asMap())` before each evaluation. If `age` is a String, the validator logs a warning. This catches type errors at the Java boundary — earlier, cheaper, and with a clearer error message than debugging a silent FEEL mismatch.

Schema validation is **advisory** — it warns but does not block. This is intentional: strict blocking would break partial context evaluation (where not all declared fields are present in the evaluation context, which is valid in FEEL).

---

## COLLECT Aggregation: Two Implementations

DMN's COLLECT hit policy (`C`) fires multiple rules. The specification defines four post-evaluation reductions:

| DMN symbol | Name | Description |
|------------|------|-------------|
| `C+` | SUM | Sum of output values |
| `C<` | MIN | Minimum output value |
| `C>` | MAX | Maximum output value |
| `C#` | COUNT | Count of matched rows |

**SUM and COUNT** are implemented purely in Java (`DmnCollectAggregation.SUM`, `DmnCollectAggregation.COUNT`). They are trivial reductions that don't benefit from WASM delegation.

**MIN and MAX** delegate to the WASM engine:

```java
// In DmnCollectAggregation.MIN:
private static double wasmReduce(String wasmFn, List<? extends Number> values) {
    try (WasmExecutionEngine engine = WasmExecutionEngine.builder()...build();
         WasmModule mod = engine.loadModuleFromClasspath("wasm/dmn_feel_engine.wasm")) {
        double acc = values.get(0).doubleValue();
        for (int i = 1; i < values.size(); i++) {
            Value result = mod.execute(wasmFn, acc, values.get(i).doubleValue());
            acc = result.asDouble();
        }
        return acc;
    }
}
```

`feel_min(a, b)` and `feel_max(a, b)` are WASM exports that perform pairwise f64 comparison according to FEEL semantics. Using the WASM engine for this ensures that any FEEL-specific edge cases in numeric comparison (NaN handling, positive/negative infinity) match the decision table's own rules.

For single-element collections, the WASM round-trip is skipped and the value is returned directly.

---

## Decision Model Parsing

`DmnWasmBridge.parseDmnModel(dmnXml)` returns an opaque `DmnModel` handle. This parse step:

1. Validates the XML is well-formed DMN 1.2 or 1.3
2. Builds the decision dependency graph (decisions can refer to other decisions via `informationRequirement`)
3. Pre-compiles FEEL input expressions for fast repeated evaluation
4. Returns a handle that can be evaluated multiple times without re-parsing

Parse once at application startup. Evaluate many times per request. Re-parsing is expensive (~5 ms); evaluation is cheap (<1 ms).

---

## Why Not Camunda DMN or Drools?

The alternatives to `yawl-dmn` for in-JVM DMN evaluation are:

- **Camunda DMN Engine**: Requires Camunda dependencies, non-trivial setup, and is tightly coupled to the Camunda process engine ecosystem.
- **Drools DMN**: Large dependency tree (>50 JARs), slow startup, separate rule compilation step.
- **Custom FEEL parser**: Months of implementation work for a language with an 80-page specification.

`yawl-dmn` reaches the JVM via a precompiled Rust WASM binary with zero Camunda/Drools dependency. The dependency graph is:

```
yawl-dmn
└── yawl-graalwasm
    └── org.graalvm.polyglot:wasm (GraalWASM runtime)
```

This is three Maven dependencies for a complete, spec-compliant DMN evaluator.

---

## What the Java Layer Is NOT

`DmnDecisionService` does not:

- Implement FEEL parsing or expression evaluation
- Apply hit policies (that is done inside the WASM binary)
- Parse DMN XML (that is done inside the WASM binary)
- Cache decision results
- Validate DMN semantic constraints (e.g., that UNIQUE produces at most one result — the WASM engine enforces this)

If a FEEL expression produces the wrong result, the fix is in the WASM binary (or in the DMN XML itself), not in `DmnDecisionService`.

---

## Failure Modes

| Failure | Likely cause |
|---------|-------------|
| `DmnException: Failed to parse DMN model` | Invalid DMN XML — check namespace declarations and element structure |
| `DmnException: Decision not found: X` | `decisionId` does not match the `id` attribute (not `name`) of the `<decision>` element |
| `result.getSingleResult()` is empty for UNIQUE | No rule matched the input context — this is correct FEEL behaviour, not a bug |
| Schema validation warning logged | Context variable type mismatch — use `.put("age", 35)` not `.put("age", "35")` |
| Bridge construction fails on CI | CI uses Temurin JDK, not GraalVM JDK — use `assumeTrue(bridge != null)` in tests |
| SUM returns 0.0 | COLLECT decision returned no rules — check input values match at least one rule |

---

## Thread Safety

`DmnDecisionService` creates one `DmnWasmBridge` instance, which in turn uses one `WasmExecutionEngine`. GraalVM WASM contexts are not thread-safe. If concurrent DMN evaluation is needed, create one `DmnDecisionService` per thread or use a thread-local pool pattern.

The schema (`DataModel`) is immutable and safe to share across threads.
