# H-Guards SPARQL Queries

This directory contains the SPARQL queries for the H-Guards validation phase that enforces Fortune 5 production standards by detecting and blocking 7 forbidden patterns in generated code.

## Query Files

1. **guards-h-todo.sparql** - Detects TODO/FIXME/deferred work markers
   - Pattern: `//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)`
   - Severity: FAIL

2. **guards-h-mock.sparql** - Detects mock class/method names
   - Pattern: `(mock|stub|fake|demo)[A-Z]` on identifiers or `^(Mock|Stub|Fake|Demo)` on classes
   - Severity: FAIL

3. **guards-h-stub.sparql** - Detects stub returns (empty string, 0, null with stub comment)
   - Pattern: Empty returns from non-void methods
   - Severity: FAIL

4. **guards-h-empty.sparql** - Detects empty method bodies in void methods
   - Pattern: Empty braces `{}`
   - Severity: FAIL

5. **guards-h-fallback.sparql** - Detects silent catch-and-fake patterns
   - Pattern: Catch blocks that return fake data instead of propagating exceptions
   - Severity: FAIL

6. **guards-h-lie.sparql** - Detects code/documentation mismatches
   - Pattern: Method documentation doesn't match implementation
   - Severity: FAIL

7. **guards-h-silent.sparql** - Detects log-and-continue instead of throw
   - Pattern: `log.warn/error` about unimplemented features instead of throwing
   - Severity: FAIL

## Usage

These SPARQL queries are used by the `HyperStandardsValidator` to:
- Parse generated Java code into RDF models
- Execute each query to detect violations
- Generate guard-receipt.json with violations
- Exit with code 2 (RED) if violations found, 0 (GREEN) if clean

## Integration

The queries are executed in the H-Guards validation phase:
```
ggen generate → dx.sh compile → ggen validate --phase guards → ggen validate --phase invariants
```

For detailed implementation, see `.claude/rules/validation-phases/H-GUARDS-IMPLEMENTATION.md`