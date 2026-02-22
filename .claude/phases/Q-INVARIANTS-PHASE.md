# Q Phase — Invariants Validation via SHACL

**Scope**: Validate generated code against 4 core invariants:
1. **real_impl ∨ throw**: Method either implements real logic OR throws UnsupportedOperationException
2. **¬mock**: No empty/mock objects in generated code
3. **¬silent_fallback**: Exceptions caught → propagated or logged, never silent
4. **¬lie**: Method behavior matches signature + documentation

**Integration**: After H (guards) passes GREEN, invoke Q phase validator.

---

## 1. Architecture Overview

### 1.1 Data Flow

Generated Code (Java AST)
    ↓
CodeToRDF Converter (AST → Turtle)
    ↓
RDF Graph (N-Triples/Turtle)
    ↓
SHACL Validator (shapes.ttl)
    ↓
Validation Report (SPARQL queries)
    ↓
InvariantReceipt (JSON)
    ↓
[Exit 0: GREEN] OR [Exit 2: VIOLATIONS]

### 1.2 Component Stack

| Component | Purpose | Implementation |
|-----------|---------|-----------------|
| **InvariantValidator** interface | Core validation contract | Java + SPARQL |
| **CodeToRDF** converter | AST → Turtle graph | ANTLR4 visitor + RDF4J |
| **SHACLValidator** | Shape validation engine | Topbraid SHACL |
| **invariants.ttl** | SHACL shape definitions | 4 shapes (one per invariant) |
| **InvariantReceipt** model | Receipt/audit trail | JSON with SHA256 hashes |
| **Q Phase Hook** | Automated invocation | Called after H gate passes |

---

## 2. Core Invariants Explained

### 2.1 Invariant 1: real_impl ∨ throw
Method must have real implementation or throw UnsupportedOperationException.

### 2.2 Invariant 2: ¬mock
No mock, stub, fake, or placeholder objects.

### 2.3 Invariant 3: ¬silent_fallback
Exceptions caught → propagated, logged, or real alternative.

### 2.4 Invariant 4: ¬lie
Method implementation matches documentation and signature.

---

## 3. Implementation Components

### 3.1 Java Classes

- InvariantValidator.java: Validation interface
- SHACLValidator.java: Topbraid SHACL engine
- CodeToRDF.java: Java AST → Turtle converter
- InvariantReceipt.java: JSON receipt model
- InvariantReceiptGenerator.java: Report → JSON

### 3.2 SHACL Definitions

- File: .specify/invariants.ttl
- 4 NodeShape definitions
- SPARQL constraint components

### 3.3 Hooks & Scripts

- .claude/hooks/q-phase-invariants.sh: Automated execution
- Integration with ggen validate --phase invariants

---

## 4. SPARQL Query Examples

### 4.1 Detect Empty Methods

```sparql
SELECT ?method ?className ?methodName
WHERE {
  ?method a code:Method ;
          code:body ?body ;
          code:lineCount ?count .
  FILTER(strlen(?body) < 10 && ?count < 2)
  FILTER NOT EXISTS { ?method code:throws code:UnsupportedOperationException }
}
```

### 4.2 Detect Mock Classes

```sparql
SELECT ?className
WHERE {
  ?class a code:Class ;
         code:name ?className .
  FILTER regex(?className, "^Mock|^Stub|^Fake|^Demo")
}
```

### 4.3 Detect Silent Fallbacks

```sparql
SELECT ?method ?catchBlock
WHERE {
  ?method a code:Method ;
          code:hasCatchBlock ?catchBlock .
  ?catchBlock code:body ?body .
  FILTER regex(?body, "return.*fake|return.*mock")
  FILTER NOT EXISTS { ?catchBlock code:rethrows ?ex }
}
```

### 4.4 Detect Documentation Mismatch

```sparql
SELECT ?method ?expectedThrow
WHERE {
  ?method rdfs:comment ?doc ;
          code:body ?body .
  BIND(REGEX(?doc, "throws\\s+([A-Za-z]+)") AS ?expectedThrow)
  FILTER BOUND(?expectedThrow)
  FILTER NOT regex(?body, ?expectedThrow)
}
```

---

## 5. JSON Receipt Format

```json
{
  "phase": "invariants",
  "timestamp": "2026-02-21T14:30:00Z",
  "methods_checked": 45,
  "classes_checked": 12,
  "violations": [
    {
      "invariant": "real_impl_or_throw",
      "class": "YWorkItem",
      "method": "validateState",
      "line": 234,
      "issue": "Empty method body without throw",
      "severity": "FAIL",
      "remediation": "Implement validation logic or throw UnsupportedOperationException"
    },
    {
      "invariant": "no_silent_fallback",
      "class": "WorkflowClient",
      "method": "fetchWorkflow",
      "line": 156,
      "issue": "Silent fallback to fake data in catch block",
      "severity": "FAIL",
      "remediation": "Re-throw exception or log and provide cached alternative"
    }
  ],
  "status": "RED",
  "passing_rate": "95.6%",
  "shaclReportHash": "sha256:abc123...",
  "files_processed": ["YWorkItem.java", "WorkflowClient.java"]
}
```

---

## 6. Maven Dependencies

```xml
<dependency>
    <groupId>org.topbraid</groupId>
    <artifactId>shacl</artifactId>
    <version>1.4.2</version>
</dependency>

<dependency>
    <groupId>org.eclipse.rdf4j</groupId>
    <artifactId>rdf4j-model</artifactId>
    <version>4.3.1</version>
</dependency>

<dependency>
    <groupId>org.eclipse.rdf4j</groupId>
    <artifactId>rdf4j-rio-turtle</artifactId>
    <version>4.3.1</version>
</dependency>

<dependency>
    <groupId>org.antlr</groupId>
    <artifactId>antlr4-runtime</artifactId>
    <version>4.13.1</version>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.16.0</version>
</dependency>
```

---

## 7. Test Coverage

Test file: src/test/org/yawlfoundation/yawl/codegen/validation/InvariantValidatorTest.java

- testRealImplVsThrow_EmptyMethod_Violation
- testRealImplVsThrow_WithThrow_Green
- testNoMockObjects_MockClassName_Violation
- testNoSilentFallback_CatchReturnFake_Violation
- testNoSilentFallback_CatchRethrow_Green
- testNoLie_DocClaimThrowsButDoesnt_Violation
- testCodeToRDF_JavaToTurtle_Success
- testSHACLValidator_ValidModel_NoViolations

---

## 8. Success Criteria

| Criterion | Target | Status |
|-----------|--------|--------|
| True Positive Rate | 100% | Detect all 4 invariant types |
| False Positive Rate | 0% | No false alarms |
| Execution Time | <30s | Full 1000+ methods |
| Receipt Completeness | 100% | All violations documented |
| Exit Code Accuracy | 100% | 0 = GREEN, 2 = violations |

