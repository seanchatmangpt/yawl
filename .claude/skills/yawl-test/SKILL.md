---
name: yawl-test
description: Run YAWL unit tests
disable-model-invocation: true
user-invocable: true
allowed-tools: Bash(ant unitTest)
---

# YAWL Test Skill

Run the full YAWL unit test suite using JUnit.

## Usage

```
/yawl-test
```

## What It Does

1. Compiles test classes
2. Runs all JUnit tests in test/
3. Reports failures with details
4. Generates test reports in output/test-results/

## Execution

```bash
cd "$CLAUDE_PROJECT_DIR"
ant unitTest
```

## Success Criteria

- All tests pass (100% success rate)
- No test failures or errors
- Test report generated

## Before Committing

This MUST pass before any git commit. The workflow is:

```bash
ant compile && ant unitTest
# If both succeed, then git commit
```
