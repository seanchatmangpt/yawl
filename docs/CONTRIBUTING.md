# YAWL v6.0.0 Developer Contribution Guide

## Code of Conduct

Be respectful, inclusive, and professional. Contribute because you want to improve YAWL for everyone.

## Getting Started

### 1. Fork & Clone
```bash
# Fork on GitHub, then:
git clone https://github.com/YOUR_USERNAME/yawl.git
cd yawl
git remote add upstream https://github.com/yawlfoundation/yawl.git
```

### 2. Create Feature Branch
```bash
git checkout -b feature/your-feature-name
# Branch naming: feature/, bugfix/, docs/, refactor/
```

### 3. Make Changes
Follow YAWL development patterns:

```java
// DO: Implement real functionality
public void processWorkItem(YWorkItem item) {
    if (item == null) {
        throw new IllegalArgumentException("WorkItem cannot be null");
    }
    // Real implementation...
}

// DON'T: Create stubs or TODOs
public void processWorkItem(YWorkItem item) {
    // TODO: implement this   [FORBIDDEN]
    // Stub implementation    [FORBIDDEN]
}
```

### 4. Write Tests (TDD Recommended)
```bash
# Create test file with your changes
# Location: test/org/yawlfoundation/yawl/{module}/{ClassName}Test.java

# Example:
test/org/yawlfoundation/yawl/engine/YWorkItemTest.java
```

### 5. Run Full Build
```bash
# Must succeed before commit!
mvn clean compile
mvn clean test

# Or combined:
mvn clean compile test
```

### 6. Commit with Meaningful Message
```bash
git add src/... test/...
git commit -m "Add feature: description of what you did

This commit adds XYZ functionality because ABC.

Relates to: #123 (if applicable)"
```

### 7. Push & Create Pull Request
```bash
git push origin feature/your-feature-name
# Go to GitHub and create PR
```

## Code Standards

### HYPER_STANDARDS Compliance

All code MUST follow HYPER_STANDARDS:

#### DO
- Implement REAL functionality
  ```java
  public String getData(String key) {
      return dataMap.getOrDefault(key, null);
  }
  ```

- Throw clear exceptions
  ```java
  if (workItem == null) {
      throw new IllegalArgumentException("WorkItem cannot be null");
  }
  ```

- Write meaningful tests
  ```java
  @Test
  @DisplayName("should process valid workflow")
  void testWorkflowProcessing() {
      // Real test with assertions
  }
  ```

- Update documentation
- Use descriptive names

#### DON'T
- Add TODO/FIXME/XXX/HACK comments
  ```java
  // TODO: implement this    [FORBIDDEN]
  // FIXME: broken code       [FORBIDDEN]
  ```

- Create mock/stub implementations
  ```java
  public class MockWorkflow {  // [FORBIDDEN]
      // Fake implementation
  }
  
  public void process() {
      if (testMode) return;    // [FORBIDDEN]
  }
  ```

- Return empty/null placeholders
  ```java
  return "";                   // [FORBIDDEN]
  return null; // stub         // [FORBIDDEN]
  ```

- Skip error handling
  ```java
  try {
      // code
  } catch (Exception e) {
      return fake();           // [FORBIDDEN - silent fallback]
  }
  ```

- Lie about functionality
  ```java
  // Method name claims validation, but does nothing
  public boolean validateWorkflow() {
      return true;            // [FORBIDDEN - lie]
  }
  ```

### Code Style

#### Java Conventions
```java
// Class names: PascalCase
public class YWorkItem {

    // Constants: UPPER_SNAKE_CASE
    private static final int MAX_RETRIES = 3;
    
    // Variables: camelCase
    private String workItemId;
    private int executionCount;
    
    // Methods: camelCase with verbs
    public void executeTask() { }
    public YWorkItem createWorkItem(String id) { }
    public boolean isReady() { }
}
```

#### Formatting
- **Indentation**: 4 spaces (not tabs)
- **Line length**: Max 120 characters (soft limit)
- **Braces**: Java style (opening on same line)

```java
public void method() {
    if (condition) {
        // code
    }
}
```

#### Imports
```java
// Organize imports:
// 1. java.*
// 2. javax.*
// 3. jakarta.*
// 4. org.*
// 5. com.*

import java.util.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.*;
```

### JavaDoc Standards

```java
/**
 * Processes a workflow work item through its lifecycle.
 *
 * <p>This method handles the transition from one work item state to another,
 * ensuring all necessary validations are performed before proceeding.
 *
 * @param item the work item to process (must not be null)
 * @return the updated work item with new state
 * @throws IllegalArgumentException if item is null
 * @throws WorkflowException if processing fails
 *
 * @see YWorkItem
 * @see WorkflowExecutor
 * @since 6.0.0
 */
public YWorkItem processWorkItem(YWorkItem item) 
    throws WorkflowException {
    if (item == null) {
        throw new IllegalArgumentException("WorkItem cannot be null");
    }
    // Implementation...
}
```

### Package Documentation

Each package has a `package-info.java`:

```java
/**
 * Workflow execution engine for YAWL.
 *
 * <p>This package provides the core engine implementation for executing
 * YAWL workflow specifications. It includes:
 *
 * <ul>
 * <li>{@link YEngine} - Main workflow execution engine
 * <li>{@link YWorkItem} - Work item lifecycle management
 * <li>{@link YNet} - Petri net model representation
 * </ul>
 *
 * @see org.yawlfoundation.yawl.elements
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.engine;

import org.yawlfoundation.yawl.elements.*;
```

## Testing Requirements

### Minimum Test Coverage
- **New code**: 80%+ coverage
- **Bug fixes**: Add regression test
- **Refactoring**: Maintain existing coverage

### Test Template
```java
package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@DisplayName("YWorkItem Tests")
public class YWorkItemTest {

    private YWorkItem workItem;

    @BeforeEach
    void setUp() {
        workItem = new YWorkItem("test-id");
    }

    @Test
    @DisplayName("should process valid work item")
    void testProcessing() {
        // Arrange
        workItem.setData("status", "ready");
        
        // Act
        workItem.execute();
        
        // Assert
        assertThat(workItem.isExecuted(), is(true));
    }

    @Test
    @DisplayName("should reject null input")
    void testNullHandling() {
        assertThat(workItem.process(null), is(false));
    }
}
```

### Test Execution
```bash
# Before every commit
mvn clean test

# Check specific test
mvn test -Dtest=YWorkItemTest

# Run all tests in module
mvn -pl yawl-engine clean test
```

## Commit Message Standards

### Format
```
[TYPE] Brief description (50 chars max)

Detailed explanation of the change:
- What was changed
- Why it was changed
- How it affects the system

Relates to: #123
Fixes: #456
```

### Types
- **feat**: New feature
- **fix**: Bug fix
- **refactor**: Code restructuring
- **test**: Test additions/updates
- **docs**: Documentation
- **perf**: Performance improvement
- **security**: Security fix

### Examples

Good:
```
feat: Add workflow validation before execution

Validates workflow specification against schema before
attempting execution. This prevents invalid workflows
from causing runtime errors.

Relates to: #234
```

Bad:
```
fixed stuff
updated code
TODO: finish this later
```

## Pull Request Process

### Before Submitting PR

1. **Sync with upstream**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

2. **Run full test suite**
   ```bash
   mvn clean compile test
   ```

3. **Check for HYPER_STANDARDS violations**
   ```bash
   grep -r "TODO\|FIXME\|XXX\|HACK" src/
   ```

4. **Verify code style**
   - Consistent indentation
   - Clear variable names
   - Proper JavaDoc

### PR Description Template

```markdown
## Description
Clear explanation of what this PR does.

## Motivation
Why is this change needed?

## Changes Made
- Bullet point list of changes
- Specific methods/classes modified
- New files added

## Testing
How was this tested?
- Test cases added/modified
- Manual testing performed
- Edge cases covered

## Checklist
- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] No HYPER_STANDARDS violations
- [ ] JavaDoc added/updated
- [ ] Commit messages are clear
- [ ] Related issues referenced
```

### Review Process

1. **Automated Checks** (CI/CD)
   - Compilation
   - All tests pass
   - Code style

2. **Code Review** (Maintainers)
   - HYPER_STANDARDS compliance
   - Test coverage
   - Architecture consistency
   - Documentation quality

3. **Approval & Merge**
   - At least one reviewer approval
   - All CI checks passing
   - No conflicts with main

## Development Workflow

### Local Development
```bash
# Create feature branch
git checkout -b feature/my-feature

# Make changes and commit
# ... edit files ...
mvn clean compile test
git add ...
git commit -m "..."

# Keep up with upstream
git fetch upstream
git rebase upstream/main

# Push to your fork
git push origin feature/my-feature
```

### Continuous Integration

Every push triggers:
1. Compilation check
2. Unit test execution
3. Code style verification
4. HYPER_STANDARDS scan

**All must pass** before merge.

## Architecture Considerations

### Module Dependencies
```
yawl-utilities (base)
    ↓
yawl-elements
    ↓
yawl-engine (core)
    ↓
yawl-integration, yawl-resourcing, etc.
```

**Golden Rule**: Never add backwards dependencies!

### Backward Compatibility
- Keep public API stable
- Deprecate before removal (2+ releases)
- Document breaking changes

## Performance Guidelines

### Benchmarking
```bash
# Run performance tests
mvn test -Dbenchmark=true
```

### Optimization Rules
1. **Profile before optimizing**
   ```bash
   mvn -X clean compile  # Debug mode
   ```

2. **Measure impact**
   - Before/after metrics
   - Unit test performance

3. **Document trade-offs**
   - Why optimization was needed
   - Cost/benefit analysis

## Security Guidelines

### Dependency Management
```bash
# Check for vulnerabilities
mvn -Psecurity-audit clean install
```

### Code Security
- Validate all inputs
- Use parameterized queries (not string concatenation)
- Never hardcode secrets
- Use security libraries (JJWT, Bouncy Castle)

### Secrets
- Never commit credentials
- Use environment variables for secrets
- Document required environment variables

## Issue Management

### Reporting Issues
1. **Search existing issues** (avoid duplicates)
2. **Create descriptive title** (not "Bug" or "Help")
3. **Include reproduction steps**
4. **Show expected vs actual behavior**
5. **Provide environment details**

### Working on Issues
1. Comment on issue (claim it)
2. Create feature branch
3. Reference issue in commit messages
4. Link PR to issue

## Documentation

### When to Update Docs
- **New features**: Add to QUICK-START.md or relevant guide
- **Breaking changes**: Update CONTRIBUTING.md and CHANGELOG.md
- **Bug fixes**: Update relevant troubleshooting guides
- **Architecture changes**: Update BUILD.md

### Doc Format
- Markdown (.md)
- Clear headings (H2, H3)
- Code examples where applicable
- Links to related docs

## Code Review Checklist

When reviewing code:

- [ ] Does it compile?
- [ ] Do all tests pass?
- [ ] No HYPER_STANDARDS violations?
- [ ] Clear variable/method names?
- [ ] Proper error handling?
- [ ] JavaDoc present?
- [ ] Tests adequate?
- [ ] Commit messages clear?

## Questions or Help?

- **Documentation**: Check `/docs` directory
- **Tests**: Look at `test/` for examples
- **Architecture**: Read `.claude/BEST-PRACTICES-2026.md`
- **Community**: Visit yawlfoundation.org

## Recognition

Contributors will be recognized:
- In CHANGELOG.md
- In GitHub contributors
- Annual contributor list

Thank you for contributing to YAWL!

