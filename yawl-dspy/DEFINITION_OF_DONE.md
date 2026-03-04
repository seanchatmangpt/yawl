# YAWL DSPy Integration - Definition of Done

## Context

This document captures all work-in-progress and requirements for the DSPy Signature System integration with YAWL. The system bridges Java Signatures to Python DSPy programs using Groq API for LLM inference via GraalPy.

---

## 1. GraalPy Environment Setup

### What Was Done
- Modified `/Users/sac/yawl/.claude/hooks/session-start.sh` to install **GraalVM 25 with GraalPy** instead of Temurin 25
- Updated environment variables from `TEMURIN_25_HOME` to `GRAALVM_HOME`
- Added GraalPy Python language installation via `gu install python`
- Added GraalPy verification tests

### Definition of Done
- [ ] GraalVM 25 is installed in remote/CI environments
- [ ] GraalPy Python language is installed and verified
- [ ] `JAVA_HOME` points to GraalVM, not Temurin
- [ ] GraalPy execution test passes: `java -polyglot -eval "print('graalpy-ok')"`
- [ ] Session-start.sh runs without errors in remote environment
- [ ] Local development environment document how to install GraalVM manually

### Files Modified
- `/Users/sac/yawl/.claude/hooks/session-start.sh`

---

## 2. DSPy Signature System Tests

### Current State
Test file has **syntax error** at line 72-73:
```java
Signature signature = Signature.builder()
Signature signature = Signature.builder()  // DUPLICATE LINE - MUST FIX
```

### Chicago TDD Requirements
Per user: *"We are doing Chicago TDD, there should be no tests that pass if GraalPy isn't running because it isn't wrapping and running dspy."*

This means:
- **Unit tests** that only test code generation SHOULD work without GraalPy
- **Integration tests** that execute DSPy programs MUST require GraalPy and FAIL if not available
- Tests should NOT silently skip - they should be explicit about requirements

### Definition of Done
- [ ] Fix syntax error in `DspySignatureEndToEndTest.java` (duplicate line 72-73)
- [ ] Unit tests (code generation only) pass without GraalPy:
  - [ ] `testGeneratePythonSource()`
  - [ ] `testGeneratePythonWithExamples()`
  - [ ] `testGenerateExecutionCode()`
  - [ ] `testGenerateFullProgram()`
  - [ ] `testParseOutput()`
  - [ ] `testAnnotationBasedSignature()`
- [ ] Integration tests REQUIRE GraalPy and fail explicitly if not available:
  - [ ] `testEndToEndWithGroq()` - requires `GROQ_API_KEY` + GraalPy
  - [ ] `testFewShotWithGroq()` - requires `GROQ_API_KEY` + GraalPy
- [ ] Remove `assumeGraalPyNotAvailable()` helper - use `Assumptions.assumeTrue()` for integration tests
- [ ] All 8 tests compile and run
- [ ] Test output shows generated Python code

### Files to Fix
- `/Users/sac/yawl/yawl-dspy/src/test/java/org/yawlfoundation/yawl/dspy/signature/DspySignatureEndToEndTest.java`

---

## 3. DspySignatureBridge Implementation

### Current State
- Core implementation complete at `/Users/sac/yawl/yawl-dspy/src/main/java/org/yawlfoundation/yawl/dspy/signature/DspySignatureBridge.java`
- Generates Python DSPy code with Groq configuration
- Uses `groq/gpt-oss-20b` model by default

### Definition of Done
- [ ] `generatePythonSource()` generates valid Python DSPy code
- [ ] `generateFullProgram()` includes execution code
- [ ] `parseOutput()` correctly parses JSON to `SignatureResult`
- [ ] Groq API key is pulled from `GROQ_API_KEY` environment variable
- [ ] Generated Python code is syntactically valid
- [ ] Module compiles: `mvn compile -pl yawl-dspy`
- [ ] All tests pass: `mvn test -pl yawl-dspy`

### Files
- `/Users/sac/yawl/yawl-dspy/src/main/java/org/yawlfoundation/yawl/dspy/signature/DspySignatureBridge.java`
- `/Users/sac/yawl/yawl-dspy/src/main/java/org/yawlfoundation/yawl/dspy/signature/Signature.java`
- `/Users/sac/yawl/yawl-dspy/src/main/java/org/yawlfoundation/yawl/dspy/signature/SignatureResult.java`
- `/Users/sac/yawl/yawl-dspy/src/main/java/org/yawlfoundation/yawl/dspy/signature/Example.java`
- `/Users/sac/yawl/yawl-dspy/src/main/java/org/yawlfoundation/yawl/dspy/signature/InputField.java`
- `/Users/sac/yawl/yawl-dspy/src/main/java/org/yawlfoundation/yawl/dspy/signature/OutputField.java`

---

## 4. DSPy Modules (Predict, ChainOfThought, ReAct)

### Current State
Files exist and were read into context:
- `/Users/sac/yawl/yawl-dspy/src/main/java/org/yawlfoundation/yawl/dspy/module/Predict.java`
- `/Users/sac/yawl/yawl-dspy/src/main/java/org/yawlfoundation/yawl/dspy/module/ChainOfThought.java`
- `/Users/sac/yawl/yawl-dspy/src/main/java/org/yawlfoundation/yawl/dspy/module/ReAct.java`

### Definition of Done
- [ ] `Predict.java` compiles and implements `Module<T>` interface
- [ ] `ChainOfThought.java` compiles and extends Predict with reasoning
- [ ] `ReAct.java` compiles with tool use support
- [ ] Each module has corresponding test file
- [ ] Modules integrate with `LlmClient` interface
- [ ] GraalPy execution works through `PythonDspyBridge`

---

## 5. PythonDspyBridge Integration

### Current State
- `/Users/sac/yawl/yawl-dspy/src/main/java/org/yawlfoundation/yawl/dspy/PythonDspyBridge.java` exists
- Depends on `yawl-graalpy` module for `PythonExecutionEngine`

### Definition of Done
- [ ] `yawl-graalpy` module compiles: `mvn compile -pl yawl-graalpy`
- [ ] `PythonExecutionEngine` is available from `yawl-graalpy`
- [ ] `PythonDspyBridge` can execute Python DSPy programs
- [ ] Context pooling works for concurrent execution
- [ ] Error handling wraps Python exceptions properly

---

## 6. Build Verification

### Commands to Run
```bash
# 1. Compile yawl-graalpy (dependency)
mvn compile -pl yawl-graalpy -DskipTests

# 2. Compile yawl-dspy
mvn compile -pl yawl-dspy -DskipTests

# 3. Run tests (requires GraalPy for integration tests)
mvn test -pl yawl-dspy

# 4. Full validation
bash scripts/dx.sh -pl yawl-dspy
```

### Definition of Done
- [ ] `mvn compile -pl yawl-dspy` succeeds
- [ ] `mvn test-compile -pl yawl-dspy` succeeds
- [ ] 6 unit tests pass
- [ ] 2 integration tests pass (when GraalPy + GROQ_API_KEY available)
- [ ] No test skips unless explicitly required by environment

---

## 7. Documentation

### Definition of Done
- [ ] README.md explains GraalPy requirement
- [ ] Usage examples show how to define signatures
- [ ] Groq API key setup documented
- [ ] Local development setup for GraalVM documented

---

## 8. Outstanding Plan: TPOT2 Extraction

### From Plan File
A plan exists at `/Users/sac/.claude/plans/lexical-mapping-pretzel.md` for extracting TPOT2 into standalone module.

### Definition of Done (Separate Task)
- [ ] Create `yawl-tpot2` module directory structure
- [ ] Move Java files from `yawl-pi/src/main/java/org/yawlfoundation/yawl/pi/automl/`
- [ ] Update package declarations
- [ ] Move Python resources
- [ ] Add module to parent pom.xml
- [ ] Update yawl-pi dependencies
- [ ] Verify build succeeds

---

## Quick Reference: Key File Locations

| Component | Path |
|-----------|------|
| DspySignatureBridge | `/Users/sac/yawl/yawl-dspy/src/main/java/org/yawlfoundation/yawl/dspy/signature/DspySignatureBridge.java` |
| End-to-End Tests | `/Users/sac/yawl/yawl-dspy/src/test/java/org/yawlfoundation/yawl/dspy/signature/DspySignatureEndToEndTest.java` |
| Session Start Hook | `/Users/sac/yawl/.claude/hooks/session-start.sh` |
| Python Execution Engine | `/Users/sac/yawl/src/org/yawlfoundation/yawl/graalpy/PythonExecutionEngine.java` |
| Parent POM | `/Users/sac/yawl/pom.xml` |
| GraalPy Module POM | `/Users/sac/yawl/yawl-graalpy/pom.xml` |
| DSPy Module POM | `/Users/sac/yawl/yawl-dspy/pom.xml` |

---

## Immediate Action Items

1. **FIX SYNTAX ERROR** in `DspySignatureEndToEndTest.java` line 72-73
2. **VERIFY** GraalVM installation in session-start.sh works
3. **RUN** `mvn compile -pl yawl-dspy` to verify compilation
4. **RUN** `mvn test -pl yawl-dspy` to verify tests
5. **UPDATE** tests to follow Chicago TDD (fail explicitly, don't skip silently)

---

## Environment Requirements

| Requirement | Local Dev | Remote/CI |
|-------------|-----------|-----------|
| GraalVM 25 | Manual install | session-start.sh |
| GraalPy Python | `gu install python` | session-start.sh |
| GROQ_API_KEY | .env file | Environment variable |
| dspy-ai Python package | `pip install dspy-ai` | Pre-installed or pip |

---

*Last updated: 2026-03-03*
