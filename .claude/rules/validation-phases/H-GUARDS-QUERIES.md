# H-Guards Phase Queries — SPARQL Reference & Patterns

**Status**: READY FOR IMPLEMENTATION  
**Scope**: Complete SPARQL query reference for all guard patterns

---

## 1. SPARQL Query Pattern Mapping

| Pattern | Query File | Detection Method | Complexity |
|---------|------------|------------------|------------|
| H_TODO | guards-h-todo.sparql | Regex on comments | Low |
| H_MOCK | guards-h-mock.sparql | Regex on identifiers + classes | Medium |
| H_STUB | guards-h-stub.sparql | SPARQL on return statements | High |
| H_EMPTY | guards-h-empty.sparql | SPARQL on method bodies | Medium |
| H_FALLBACK | guards-h-fallback.sparql | SPARQL on catch blocks | High |
| H_LIE | guards-h-lie.sparql | Semantic comparison | Very High |
| H_SILENT | guards-h-silent.sparql | Regex on log statements | Low |

---

## 2. Complete SPARQL Query Reference

### Query 1: H_TODO Detection

```sparql
PREFIX code: <http://ggen.io/code#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>

SELECT ?violation ?line ?pattern
WHERE {
  ?method a code:Method ;
          code:hasComment ?comment ;
          code:lineNumber ?line .

  ?comment code:text ?text .

  FILTER(REGEX(?text, "//\\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)"))

  BIND("H_TODO" AS ?pattern)
  BIND(CONCAT("Deferred work marker at line ", STR(?line), ": ", ?text)
       AS ?violation)
}
```

**Pattern**: `//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)`  
**Example**: `// TODO: Add deadlock detection`  
**Fix Guidance**: Implement real logic or throw UnsupportedOperationException

### Query 2: H_MOCK Detection

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?violation ?line ?pattern
WHERE {
  {
    # Mock method names: mockFetch(), getMockData()
    ?method a code:Method ;
            code:name ?name ;
            code:lineNumber ?line .
    FILTER(REGEX(?name, "(mock|stub|fake|demo)[A-Z]"))
    BIND("H_MOCK" AS ?pattern)
    BIND(CONCAT("Mock method name at line ", STR(?line), ": ", ?name)
         AS ?violation)
  } UNION {
    # Mock class declarations: class MockService
    ?class a code:Class ;
           code:name ?className ;
           code:lineNumber ?line .
    FILTER(REGEX(?className, "^(Mock|Stub|Fake|Demo)"))
    BIND("H_MOCK" AS ?pattern)
    BIND(CONCAT("Mock class name at line ", STR(?line), ": ", ?className)
         AS ?violation)
  }
}
```

**Pattern**: `(mock|stub|fake|demo)[A-Z]` on identifiers or `^(Mock|Stub|Fake|Demo)` on classes  
**Example**: `public class MockDataService implements DataService`  
**Fix Guidance**: Delete mock class or implement real service

### Query 3: H_STUB Detection

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?violation ?line ?pattern
WHERE {
  ?method a code:Method ;
          code:body ?body ;
          code:lineNumber ?line ;
          code:returnType ?retType .

  FILTER(
    (REGEX(?body, 'return\\s+"";') ||
     REGEX(?body, 'return\\s+0;') ||
     REGEX(?body, 'return\\s+null;.*//.*stub') ||
     REGEX(?body, 'return\\s+(Collections\\.empty|new\\s+(HashMap|ArrayList)\\(\\));\\s*$'))
    &&
    ?retType != "void"
  )

  BIND("H_STUB" AS ?pattern)
  BIND(CONCAT("Stub return at line ", STR(?line), ": ", ?body)
       AS ?violation)
}
```

**Pattern**: Empty string, zero, null return, or empty collection from non-void methods  
**Example**: `public String getData() { return ""; }`  
**Fix Guidance**: Implement real method or throw exception

### Query 4: H_EMPTY Detection

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?violation ?line ?pattern
WHERE {
  ?method a code:Method ;
          code:body ?body ;
          code:lineNumber ?line ;
          code:returnType "void" .

  FILTER(REGEX(?body, '^\\s*\\{\\s*\\}\\s*$'))

  BIND("H_EMPTY" AS ?pattern)
  BIND(CONCAT("Empty method body at line ", STR(?line))
       AS ?violation)
}
```

**Pattern**: Empty braces in void methods  
**Example**: `public void initialize() { }`  
**Fix Guidance**: Implement real logic or throw exception

### Query 5: H_FALLBACK Detection

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?violation ?line ?pattern
WHERE {
  ?method a code:Method ;
          code:body ?body ;
          code:lineNumber ?line .

  FILTER(REGEX(?body, 'catch\\s*\\([^)]+\\)\\s*\\{[^}]*return[^}]*fake[^}]*\\}'))

  BIND("H_FALLBACK" AS ?pattern)
  BIND(CONCAT("Silent fallback at line ", STR(?line))
       AS ?violation)
}
```

**Pattern**: Catch blocks that return fake data instead of propagating exceptions  
**Example**: `catch (Exception e) { return Collections.emptyList(); }`  
**Fix Guidance**: Propagate exception instead of faking data

### Query 6: H_LIE Detection (Semantic)

```sparql
PREFIX code: <http://ggen.io/code#>
PREFIX javadoc: <http://ggen.io/javadoc#>

SELECT ?violation ?line ?pattern
WHERE {
  ?method a code:Method ;
          code:javadoc ?doc ;
          code:body ?body ;
          code:lineNumber ?line .

  ?doc javadoc:throws ?throws .
  ?doc javadoc:returns ?returns .

  # Check if method claims to return value but body doesn't
  FILTER(
    (STRSTARTS(?returns, "void") && REGEX(?body, 'return\\s+[^;};]+')) ||
    (NOT STRSTARTS(?returns, "void") && !REGEX(?body, 'return\\s+[^;};]+'))
  )

  FILTER(
    # Check if method claims to throw but doesn't
    EXISTS { ?throws javadoc:name ?throwName } &&
    !REGEX(?body, 'throw\\s+' + ?throwName)
  )

  BIND("H_LIE" AS ?pattern)
  BIND(CONCAT("Documentation mismatch at line ", STR(?line))
       AS ?violation)
}
```

**Pattern**: Method documentation doesn't match implementation  
**Example**: `/** @return never null */ public String get() { return null; }`  
**Fix Guidance**: Update code to match documentation

### Query 7: H_SILENT Detection

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?violation ?line ?pattern
WHERE {
  ?method a code:Method ;
          code:body ?body ;
          code:lineNumber ?line .

  FILTER(REGEX(?body, 'log\\.(warn|error)\\([^)]*"[^"]*not\\s+implemented[^"]*"[^)]*\\)'))

  BIND("H_SILENT" AS ?pattern)
  BIND(CONCAT("Silent logging at line ", STR(?line), ": ", ?body)
       AS ?violation)
}
```

**Pattern**: Log warnings/errors about unimplemented features instead of throwing  
**Example**: `log.error("Not implemented yet")`  
**Fix Guidance**: Throw exception instead of logging

---

## 3. SPARQL Query Best Practices

### Query Optimization

1. **Use FILTER early** to reduce result set size
2. **Avoid complex regex** - split into multiple queries if needed
3. **Use UNION sparingly** - increases query complexity
4. **Limit result sets** with `LIMIT` clause for large files

### Error Handling

1. **Query timeouts** - Set 30-second timeout per query
2. **Parse errors** - Handle malformed Java gracefully
3. **Memory limits** - Process files in batches for large codebases

### Performance Tips

1. **Index RDF properties** - Create indexes on frequently queried properties
2. **Cache models** - Reuse RDF models across queries for same file
3. **Parallel execution** - Run independent queries concurrently

---

## 4. Query Testing Strategy

### Test Data Requirements

Each query needs test fixtures covering:
- **Positive cases**: Code that should trigger the violation
- **Negative cases**: Clean code that should pass
- **Edge cases**: Borderline patterns (should they match?)

### Test Execution

```bash
# Test individual query
s-query -e guards-h-todo.sparql test/fixtures/violation-h-todo.java

# Test all queries
for query in guards-h-*.sparql; do
  echo "Testing $query..."
  s-query -e $query test/fixtures/*.java | grep -c "violation"
done

# Integration test
java -cp test-classes org.ggen.validation.HyperStandardsValidatorTest
```

---

## 5. Receipt Model Reference

### GuardReceipt JSON Structure

```json
{
  "phase": "guards",
  "timestamp": "2026-02-21T14:32:15Z",
  "files_scanned": 42,
  "violations": [
    {
      "pattern": "H_TODO",
      "severity": "FAIL",
      "file": "/home/user/yawl/generated/java/YWorkItem.java",
      "line": 427,
      "content": "// TODO: Add deadlock detection",
      "fix_guidance": "Either implement real logic or throw UnsupportedOperationException"
    },
    {
      "pattern": "H_MOCK",
      "severity": "FAIL",
      "file": "/home/user/yawl/generated/java/MockDataService.java",
      "line": 12,
      "content": "public class MockDataService implements DataService {",
      "fix_guidance": "Delete mock class or implement real service"
    }
  ],
  "status": "RED",
  "error_message": "3 guard violations found. Fix violations or throw UnsupportedOperationException.",
  "summary": {
    "h_todo_count": 5,
    "h_mock_count": 2,
    "h_stub_count": 0,
    "h_empty_count": 3,
    "h_fallback_count": 1,
    "h_lie_count": 0,
    "h_silent_count": 0,
    "total_violations": 11
  }
}
```

### Receipt Processing

```java
// Parse receipt and determine exit code
public static int processReceipt(Path receiptFile) throws IOException {
    GuardReceipt receipt = parseJson(receiptFile);
    
    if (receipt.getStatus().equals("GREEN")) {
        return 0; // Success, proceed to next phase
    } else {
        System.err.println(receipt.getErrorMessage());
        return 2; // Fatal error, fix and re-run
    }
}
```

---

## 6. Query Debugging

### Common Issues

1. **No results returned** - Check regex patterns and property names
2. **False positives** - Refine regex or add more specific conditions
3. **Performance issues** - Split complex queries or add LIMIT clauses

### Debug Commands

```bash
# Test query syntax
sparql validate guards-h-todo.sparql

# Extract sample RDF for debugging
java -cp classes org.ggen.ast.RdfAstConverter sample.java > sample.rdf

# Query with verbose output
s-query -e guards-h-todo.sparql sample.java -v
```

---

## 7. Query Evolution

### Extending Patterns

1. **Add new pattern** - Create new SPARQL file and update configuration
2. **Modify existing** - Update query and increase version number
3. **Deprecate patterns** - Mark as deprecated in configuration, remove in next version

### Version Control

```toml
# Query versioning in guard-config.toml
[sparql_versions]
h_todo = "1.0"
h_mock = "1.0"
h_stub = "1.1"  # Enhanced regex pattern
```

---

**Complete**: All 7 SPARQL queries documented with examples and best practices
