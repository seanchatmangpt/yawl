# Q Phase Implementation Design — Java Classes & Integration

**Objective**: Design and document Java classes for invariants validation via SHACL, to be implemented after Hook proof-of-concept.

---

## 1. Class Hierarchy

```
InvariantValidator (interface)
  ├─ SHACLValidator (concrete implementation)
  │  └─ Uses: CodeToRDF, ShaclValidator (Topbraid)
  └─ ReceiptBuilder (generates JSON receipt)

CodeToRDF (utility)
  └─ JavaASTVisitor (ANTLR4)
  └─ RDFEmitter

InvariantReceipt (model)
  └─ InvariantViolation (nested)

InvariantViolationException (checked)

InvariantReceiptGenerator (CLI)
```

---

## 2. InvariantValidator Interface

**Location**: `/home/user/yawl/src/org/yawlfoundation/yawl/codegen/validation/InvariantValidator.java`

```java
package org.yawlfoundation.yawl.codegen.validation;

import java.nio.file.Path;
import java.util.List;

/**
 * Validates generated code against core invariants:
 * Q1: real_impl ∨ throw
 * Q2: ¬mock
 * Q3: ¬silent_fallback
 * Q4: ¬lie (code matches docs)
 */
public interface InvariantValidator {
    
    /**
     * Validate all Java files in directory.
     *
     * @param codeDir Directory containing generated .java files
     * @return InvariantReceipt with detailed findings
     * @throws InvariantValidationException if validation cannot proceed
     */
    InvariantReceipt validate(Path codeDir) throws InvariantValidationException;
    
    /**
     * Validate single Java file.
     *
     * @param javaFile Path to .java file
     * @return List of violations (empty if green)
     */
    List<InvariantViolation> validateFile(Path javaFile);
    
    /**
     * Get SHACL validation report (RDF).
     *
     * @return Turtle-formatted SHACL report
     */
    String getShaclReport();
    
    /**
     * Get count of methods checked.
     */
    int getMethodsChecked();
}
```

---

## 3. SHACLValidator Implementation

**Location**: `/home/user/yawl/src/org/yawlfoundation/yawl/codegen/validation/SHACLValidator.java`

```java
package org.yawlfoundation.yawl.codegen.validation;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.topbraid.shacl.validation.ValidationReport;
import org.topbraid.shacl.validation.ValidationResult;
import org.topbraid.shacl.engine.ShapesGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * SHACL-based invariant validator using Topbraid SHACL engine.
 */
public class SHACLValidator implements InvariantValidator {
    
    private final Path shapesFile;
    private Model shapesModel;
    private String lastShaclReport;
    private int methodsChecked;
    
    public SHACLValidator(Path shapesFile) throws IOException {
        this.shapesFile = shapesFile;
        this.shapesModel = loadShapes();
    }
    
    private Model loadShapes() throws IOException {
        // Load .specify/invariants.ttl
        return Rio.parse(
            Files.newInputStream(shapesFile),
            "http://yawlfoundation.org/shapes/",
            RDFFormat.TURTLE
        );
    }
    
    @Override
    public InvariantReceipt validate(Path codeDir) throws InvariantValidationException {
        try {
            // Step 1: Convert Java code to RDF
            CodeToRDF converter = new CodeToRDF();
            String turtleOutput = converter.convertDirectoryToTurtle(codeDir);
            this.methodsChecked = converter.getMethodsChecked();
            
            // Step 2: Parse RDF model
            Model dataModel = Rio.parse(
                new java.io.StringReader(turtleOutput),
                "http://yawlfoundation.org/code/",
                RDFFormat.TURTLE
            );
            
            // Step 3: Run SHACL validation
            ShapesGraph shapesGraph = new ShapesGraph(shapesModel);
            ValidationReport report = shapesGraph.validateData(dataModel);
            
            // Store report for later retrieval
            this.lastShaclReport = serializeReport(report);
            
            // Step 4: Convert to InvariantReceipt
            return buildReceipt(report, codeDir);
            
        } catch (Exception e) {
            throw new InvariantValidationException(
                "Failed to validate invariants: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<InvariantViolation> validateFile(Path javaFile) {
        // Single file validation (for incremental checks)
        List<InvariantViolation> violations = new ArrayList<>();
        // TODO: Implement incremental file validation
        return violations;
    }
    
    private InvariantReceipt buildReceipt(ValidationReport report, Path codeDir) {
        InvariantReceipt receipt = new InvariantReceipt();
        receipt.setPhase("invariants");
        receipt.setTimestamp(java.time.Instant.now().toString());
        receipt.setCodeDirectory(codeDir.toString());
        receipt.setMethodsChecked(methodsChecked);
        
        // Extract violations from SHACL report
        List<InvariantViolation> violations = new ArrayList<>();
        for (ValidationResult result : report.getResults()) {
            violations.add(convertToViolation(result));
        }
        
        receipt.setViolations(violations);
        receipt.setStatus(report.conforms() ? "GREEN" : "RED");
        receipt.setPassingRate(calculatePassingRate(violations));
        
        return receipt;
    }
    
    private InvariantViolation convertToViolation(ValidationResult result) {
        InvariantViolation v = new InvariantViolation();
        v.setInvariant(extractInvariantType(result));
        v.setMethod(result.getSubject().stringValue());
        v.setIssue(result.getMessage());
        v.setSeverity("FAIL");
        v.setRemediation(suggestRemediation(v.getInvariant()));
        return v;
    }
    
    private String extractInvariantType(ValidationResult result) {
        String message = result.getMessage();
        if (message.contains("real")) return "Q1_real_impl_or_throw";
        if (message.contains("mock")) return "Q2_no_mock_objects";
        if (message.contains("fallback")) return "Q3_no_silent_fallback";
        if (message.contains("match")) return "Q4_no_lie";
        return "UNKNOWN";
    }
    
    private String suggestRemediation(String invariantType) {
        return switch(invariantType) {
            case "Q1_real_impl_or_throw" ->
                "Implement real logic or throw UnsupportedOperationException";
            case "Q2_no_mock_objects" ->
                "Rename to real class name or remove from generated code";
            case "Q3_no_silent_fallback" ->
                "Re-throw exception or log + provide cached/real alternative";
            case "Q4_no_lie" ->
                "Update code to match documentation or revise javadoc";
            default -> "Review invariant definition and fix violation";
        };
    }
    
    private double calculatePassingRate(List<InvariantViolation> violations) {
        if (methodsChecked == 0) return 1.0;
        return 1.0 - (violations.size() / (double) methodsChecked);
    }
    
    @Override
    public String getShaclReport() {
        return lastShaclReport != null ? lastShaclReport : "(report not generated)";
    }
    
    @Override
    public int getMethodsChecked() {
        return methodsChecked;
    }
    
    private String serializeReport(ValidationReport report) {
        // Serialize SHACL validation report to Turtle
        return report.toString(); // Simplified; use RDF4J serialization in practice
    }
}
```

---

## 4. CodeToRDF Converter

**Location**: `/home/user/yawl/src/org/yawlfoundation/yawl/codegen/ast/CodeToRDF.java`

```java
package org.yawlfoundation.yawl.codegen.ast;

import java.nio.file.*;
import java.util.*;

/**
 * Converts Java AST to RDF (Turtle) representation.
 * Uses ANTLR4 parser for Java syntax.
 */
public class CodeToRDF {
    
    private final JavaParser parser;
    private int methodsChecked;
    
    public CodeToRDF() {
        this.parser = new JavaParser();
    }
    
    /**
     * Parse single Java file and emit Turtle triples.
     */
    public String convertToTurtle(Path javaFile) throws IOException {
        String source = Files.readString(javaFile);
        JavaParser.CompilationUnitContext ctx = parser.parse(source);
        JavaASTVisitor visitor = new JavaASTVisitor();
        return visitor.visit(ctx).emit();
    }
    
    /**
     * Parse entire directory recursively.
     */
    public String convertDirectoryToTurtle(Path codeDir) throws IOException {
        StringBuilder turtle = new StringBuilder();
        turtle.append("@prefix code: <http://yawlfoundation.org/code#> .\n");
        turtle.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n\n");
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(codeDir, "**/*.java")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    turtle.append(convertToTurtle(path)).append("\n");
                }
            }
        }
        
        return turtle.toString();
    }
    
    /**
     * Get RDF4J Model representation.
     */
    public org.eclipse.rdf4j.model.Model getModel() {
        // TODO: Load Turtle into RDF4J Model
        return null;
    }
    
    public int getMethodsChecked() {
        return methodsChecked;
    }
}

/**
 * ANTLR4 visitor for Java AST → RDF conversion.
 */
class JavaASTVisitor extends JavaParserBaseVisitor<RDFGraph> {
    
    private RDFGraph graph = new RDFGraph();
    
    @Override
    public RDFGraph visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
        // Extract method metadata
        String methodName = ctx.IDENTIFIER().getText();
        String returnType = ctx.typeTypeOrVoid().getText();
        String body = ctx.methodBody().getText();
        
        // Emit RDF triples
        String methodUri = "code:" + methodName;
        graph.addTriple(methodUri, "a", "code:Method");
        graph.addTriple(methodUri, "code:name", "\"" + methodName + "\"");
        graph.addTriple(methodUri, "code:returnType", "code:" + returnType);
        graph.addTriple(methodUri, "code:body", "\"" + escape(body) + "\"");
        graph.addTriple(methodUri, "code:lineCount", String.valueOf(body.split("\n").length));
        
        // Detect real logic
        if (hasRealLogic(body)) {
            graph.addTriple(methodUri, "code:hasRealLogic", "true");
        }
        
        // Detect throws
        if (body.contains("throw new UnsupportedOperationException")) {
            graph.addTriple(methodUri, "code:throwsException", "code:UnsupportedOperationException");
        }
        
        return graph;
    }
    
    private boolean hasRealLogic(String body) {
        // Heuristic: body length + contains assignments/method calls
        return body.length() > 50 && (body.contains("=") || body.contains("("));
    }
    
    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

/**
 * RDF graph builder (simplified).
 */
class RDFGraph {
    private List<String> triples = new ArrayList<>();
    
    void addTriple(String subject, String predicate, String object) {
        triples.add(subject + " " + predicate + " " + object + " .");
    }
    
    String emit() {
        return String.join("\n", triples);
    }
}
```

---

## 5. InvariantReceipt Model

**Location**: `/home/user/yawl/src/org/yawlfoundation/yawl/codegen/validation/InvariantReceipt.java`

```java
package org.yawlfoundation.yawl.codegen.validation;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Receipt for invariants validation phase.
 * JSON serializable for audit trail.
 */
public class InvariantReceipt {
    
    @JsonProperty("phase")
    private String phase = "invariants";
    
    @JsonProperty("timestamp")
    private String timestamp;
    
    @JsonProperty("code_directory")
    private String codeDirectory;
    
    @JsonProperty("methods_checked")
    private int methodsChecked;
    
    @JsonProperty("classes_checked")
    private int classesChecked;
    
    @JsonProperty("violations")
    private List<InvariantViolation> violations;
    
    @JsonProperty("status")
    private String status;  // "GREEN" or "RED"
    
    @JsonProperty("passing_rate")
    private double passingRate;
    
    @JsonProperty("shacl_report_hash")
    private String shaclReportHash;
    
    @JsonProperty("files_processed")
    private List<String> filesProcessed;
    
    // Getters/Setters...
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public int getMethodsChecked() { return methodsChecked; }
    public void setMethodsChecked(int count) { this.methodsChecked = count; }
    
    public List<InvariantViolation> getViolations() { return violations; }
    public void setViolations(List<InvariantViolation> violations) { this.violations = violations; }
    
    // Nested class
    public static class InvariantViolation {
        @JsonProperty("invariant")
        private String invariant;  // "Q1_real_impl_or_throw", etc.
        
        @JsonProperty("class")
        private String className;
        
        @JsonProperty("method")
        private String method;
        
        @JsonProperty("line")
        private int line;
        
        @JsonProperty("issue")
        private String issue;
        
        @JsonProperty("severity")
        private String severity;  // "FAIL"
        
        @JsonProperty("remediation")
        private String remediation;
        
        // Getters/Setters...
        public String getInvariant() { return invariant; }
        public void setInvariant(String inv) { this.invariant = inv; }
        
        public String getMethod() { return method; }
        public void setMethod(String m) { this.method = m; }
        
        public String getIssue() { return issue; }
        public void setIssue(String i) { this.issue = i; }
        
        public String getRemediation() { return remediation; }
        public void setRemediation(String r) { this.remediation = r; }
    }
}
```

---

## 6. Integration with ggen CLI

**File**: `ggen.toml` (additions)

```toml
# Q Phase Configuration
[phases.Q]
name = "Invariants (Q)"
description = "Validate generated code against 4 core invariants"
enabled = true
after_phase = "H"  # After Guards phase
command = "./.claude/hooks/q-phase-invariants.sh"
receipt = "receipts/invariant-receipt.json"
exit_codes = { green = 0, violations = 2 }

[phases.Q.invariants]
"Q1" = "real_impl_or_throw"
"Q2" = "no_mock_objects"
"Q3" = "no_silent_fallback"
"Q4" = "no_lie"

[phases.Q.validation]
shacl_shapes = ".specify/invariants.ttl"
code_directory = "generated"
timeout_seconds = 30
```

---

## 7. Test Classes

### 7.1 Unit Tests

**File**: `/home/user/yawl/test/org/yawlfoundation/yawl/codegen/validation/InvariantValidatorTest.java`

```java
@DisplayName("Invariants Validator Tests")
class InvariantValidatorTest {
    
    private InvariantValidator validator;
    
    @BeforeEach
    void setUp() throws IOException {
        validator = new SHACLValidator(
            Paths.get(".specify/invariants.ttl")
        );
    }
    
    @Test
    @DisplayName("Q1: Empty method detected")
    void testQ1_EmptyMethod_Violation() throws IOException {
        // Create temp file with empty method
        String javaCode = """
            public class Test {
                public void emptyMethod() {
                }
            }
            """;
        InvariantReceipt receipt = validateCode(javaCode);
        assertTrue(receipt.getViolations().stream()
            .anyMatch(v -> v.getInvariant().equals("Q1_real_impl_or_throw")));
    }
    
    @Test
    @DisplayName("Q1: Method with throw passes")
    void testQ1_WithThrow_Green() throws IOException {
        String javaCode = """
            public void process() {
                throw new UnsupportedOperationException("Not implemented");
            }
            """;
        InvariantReceipt receipt = validateCode(javaCode);
        assertEquals("GREEN", receipt.getStatus());
    }
    
    @Test
    @DisplayName("Q2: Mock class name detected")
    void testQ2_MockClassName_Violation() throws IOException {
        String javaCode = """
            public class MockWorkItem {
                public void process() { }
            }
            """;
        InvariantReceipt receipt = validateCode(javaCode);
        assertTrue(receipt.getViolations().stream()
            .anyMatch(v -> v.getInvariant().equals("Q2_no_mock_objects")));
    }
    
    @Test
    @DisplayName("Q3: Silent fallback detected")
    void testQ3_SilentFallback_Violation() throws IOException {
        String javaCode = """
            public String fetch() {
                try {
                    return client.get(url);
                } catch (IOException e) {
                    return "mock-data";
                }
            }
            """;
        InvariantReceipt receipt = validateCode(javaCode);
        assertTrue(receipt.getViolations().stream()
            .anyMatch(v -> v.getInvariant().equals("Q3_no_silent_fallback")));
    }
    
    @Test
    @DisplayName("Q4: Doc/code mismatch detected")
    void testQ4_DocMismatch_Violation() throws IOException {
        String javaCode = """
            /**
             * Validates the workflow.
             * @throws ValidationException if invalid
             */
            public void validate(Spec s) {
                // doesn't actually throw
            }
            """;
        InvariantReceipt receipt = validateCode(javaCode);
        assertTrue(receipt.getViolations().stream()
            .anyMatch(v -> v.getInvariant().equals("Q4_no_lie")));
    }
}
```

---

## 8. Maven Configuration

**File**: `pom.xml` (additions)

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

## 9. Implementation Roadmap

**Phase 1 (MVP)**: Bash hook with regex detection (DONE)
**Phase 2**: Java interface + basic validation (TBD)
**Phase 3**: SHACL integration (RDF4J + Topbraid)
**Phase 4**: Full SPARQL query engine
**Phase 5**: Incremental validation (per-file)

---

## 10. Files to Create

1. `Q-INVARIANTS-PHASE.md` (architecture) ✅
2. `Q-IMPLEMENTATION-DESIGN.md` (this file) ✅
3. `invariants.ttl` (SHACL shapes) ✅
4. `q-phase-invariants.sh` (MVP hook) ✅
5. `InvariantValidator.java` (interface) — TBD
6. `SHACLValidator.java` (implementation) — TBD
7. `CodeToRDF.java` (converter) — TBD
8. `InvariantReceipt.java` (model) — TBD
9. `InvariantValidatorTest.java` (tests) — TBD

