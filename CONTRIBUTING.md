# YAWL Contributing Guide

## Table of Contents

1. [Getting Started](#getting-started)
2. [Development Setup](#development-setup)
3. [Code Organization](#code-organization)
4. [Development Workflow](#development-workflow)
5. [Coding Standards](#coding-standards)
6. [Testing](#testing)
7. [Documentation](#documentation)
8. [Build and Release](#build-and-release)
9. [Contributing](#contributing)
10. [Community Guidelines](#community-guidelines)

## Getting Started

### Contributing to YAWL

We welcome contributions from the community! YAWL is an open-source project maintained by the YAWL Foundation. This guide will help you get started with development and contributing back to the project.

### Prerequisites

- Java 11 or higher
- Maven 3.6+
- Git 2.20+
- Docker and Docker Compose (for local testing)
- PostgreSQL 12+ (for database testing)
- Ant 1.10+ (for build)

### License

YAWL is distributed under the **GNU LGPL 3.0 License**. By contributing, you agree to license your contributions under the same license.

## Development Setup

### 1. Clone Repository

```bash
# Clone YAWL repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl

# Add upstream remote
git remote add upstream https://github.com/yawlfoundation/yawl.git

# Fetch upstream changes
git fetch upstream
```

### 2. Create Development Branch

```bash
# Create feature branch from develop
git checkout upstream/develop
git checkout -b feature/my-feature-name

# Branch naming conventions:
# - feature/description-of-feature
# - bugfix/description-of-bug
# - hotfix/critical-issue
# - docs/documentation-update
# - test/test-improvements

# Example: feature/enhanced-error-handling
```

### 3. Set Up Development Environment

```bash
# Build project
cd build
ant -f build.xml clean build

# This generates:
# - build/output/*.war files (deployable artifacts)
# - build/lib/ (dependencies)

# Set up Docker environment
docker-compose up -d
docker-compose logs -f yawl

# Verify setup
curl http://localhost:8080/resourceService/
```

### 4. IDE Configuration

#### Eclipse Setup

```bash
# 1. Import project into Eclipse
# File > Import > General > Existing Projects into Workspace

# 2. Configure JDK
# Project > Properties > Java Compiler > Compiler Compliance Level
# Set to Java 11

# 3. Set up code formatting
# Window > Preferences > Java > Code Style > Formatter
# Import: build/eclipse-formatter.xml

# 4. Enable build automatically
# Project > Build Automatically
```

#### IntelliJ IDEA Setup

```bash
# 1. Open project
# File > Open > select yawl directory

# 2. Configure SDK
# File > Project Structure > Project > SDK
# Set to Java 11

# 3. Mark directories
# Mark /src as Sources Root
# Mark /test as Test Sources Root

# 4. Configure code style
# File > Settings > Editor > Code Style > Java
# Set to YAWL code style (import from build/)
```

### 5. Database Setup

```bash
# Using Docker Compose
docker-compose up -d postgres

# Or manual PostgreSQL setup
createdb -U postgres yawl_dev
psql -U postgres -d yawl_dev -c \
  "CREATE USER yawl_dev WITH PASSWORD 'dev_password';"
psql -U postgres -d yawl_dev \
  "GRANT ALL PRIVILEGES ON DATABASE yawl_dev TO yawl_dev;"

# Initialize schema
psql -U yawl_dev -d yawl_dev < docker/init-db.sql
```

## Code Organization

### Directory Structure

```
yawl/
├── src/
│   └── org/yawlfoundation/yawl/
│       ├── engine/              # Core engine
│       ├── resourcing/          # Resource management
│       ├── monitoring/          # Monitoring service
│       ├── worklet/             # Worklet service
│       ├── util/                # Utilities
│       └── integration/         # External integrations
├── build/
│   ├── build.xml               # Build configuration
│   ├── ivy.xml                 # Dependency management
│   ├── engine/                 # Engine service build
│   ├── resourceService/        # Resource service build
│   ├── monitorService/         # Monitor service build
│   └── ...
├── test/
│   └── org/yawlfoundation/yawl/
│       ├── engine/             # Engine tests
│       ├── resourcing/         # Resourcing tests
│       └── integration/        # Integration tests
├── docker/                     # Docker configuration
├── helm/                       # Helm charts
├── k8s/                        # Kubernetes manifests
├── terraform/                  # Infrastructure as code
└── schema/                     # XML schemas
```

### Module Overview

```
┌─────────────────────────────────┐
│   YAWL Core Modules             │
├─────────────────────────────────┤
│                                 │
│  Engine Module (yawl-engine)   │
│  ├─ Process Definition Parser  │
│  ├─ Net Executor               │
│  ├─ Case Manager               │
│  └─ Exception Handler          │
│                                 │
│  Resource Module               │
│  ├─ User Management            │
│  ├─ Role & Org Unit Mgmt       │
│  ├─ Allocation Engine          │
│  └─ Calendar Service           │
│                                 │
│  Monitoring Module             │
│  ├─ Event Collector            │
│  ├─ Analytics Engine           │
│  └─ Report Generator           │
│                                 │
│  Supporting Modules            │
│  ├─ Document Store             │
│  ├─ Mail Service               │
│  ├─ Cost Service               │
│  └─ Integration Adapters       │
│                                 │
└─────────────────────────────────┘
```

## Development Workflow

### 1. Feature Development

```bash
# 1. Create feature branch
git checkout -b feature/implement-new-feature

# 2. Make changes
# Edit source files in src/

# 3. Build locally
cd build
ant clean build

# 4. Test changes (see Testing section)
cd ../test
ant test

# 5. Commit with descriptive message
git add src/
git commit -m "feature: implement new feature

- Add new functionality X
- Improve performance of Y
- Update related documentation

Closes #123"

# 6. Push to your fork
git push origin feature/implement-new-feature
```

### 2. Bug Fix Workflow

```bash
# 1. Create bug fix branch
git checkout -b bugfix/fix-critical-issue

# 2. Reproduce bug
# Write failing test first
# tests/org/yawlfoundation/yawl/engine/BugFixTest.java

# 3. Fix the bug
# Make minimal changes to fix the issue

# 4. Verify fix
ant -f build/build.xml clean build
ant -f build/build.xml test

# 5. Commit
git commit -m "fix: resolve critical issue #456

Problem: Description of the bug
Solution: How it was fixed
Test: Added test case to prevent regression"

# 6. Push and create pull request
git push origin bugfix/fix-critical-issue
```

### 3. Commit Messages

#### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

#### Types

- `feature`: New functionality
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style (formatting, naming)
- `refactor`: Code restructuring
- `perf`: Performance improvements
- `test`: Test additions/modifications
- `chore`: Build, dependencies, tooling

#### Examples

```bash
# Feature
feature(engine): implement dynamic workflow configuration

# Bug fix
fix(resource): resolve race condition in allocation

# Documentation
docs(api): update REST API documentation for v5.2

# Performance
perf(database): add indexes to improve query performance

# Test
test(engine): add unit tests for exception handling

# Refactor
refactor(core): restructure case manager for better maintainability
```

## Coding Standards

### Java Code Style

```java
// 1. Naming Conventions
public class WorkItemManager {  // PascalCase for classes
    private List<WorkItem> workItems;  // camelCase for variables
    private static final int MAX_RETRIES = 3;  // UPPER_SNAKE_CASE for constants

    public void processWorkItem(WorkItem item) {  // camelCase for methods
        // Implementation
    }
}

// 2. Formatting
public class ExampleClass {
    // 4-space indentation
    private String field1;
    private int field2;

    // Constructor
    public ExampleClass(String field1, int field2) {
        this.field1 = field1;
        this.field2 = field2;
    }

    // Method
    public String getField1() {
        return field1;
    }

    // Braces on same line
    if (condition) {
        doSomething();
    } else {
        doSomethingElse();
    }
}

// 3. Documentation
/**
 * Processes a workflow case through the engine.
 *
 * @param case the case to process
 * @throws InvalidCaseException if case is invalid
 * @throws DatabaseException if database operation fails
 * @return the processed case
 */
public Case processCase(Case case) throws InvalidCaseException, DatabaseException {
    // Implementation
}

// 4. Error Handling
try {
    // Operation that might throw checked exception
    database.save(case);
} catch (SQLException e) {
    logger.error("Failed to save case: " + case.getId(), e);
    throw new DatabaseException("Save operation failed", e);
}

// 5. Logging
logger.debug("Processing case: {}", case.getId());
logger.info("Case completed successfully: {}", case.getId());
logger.warn("Task delayed for case: {}", case.getId());
logger.error("Failed to execute task: {}", task.getId(), exception);
```

### Code Review Checklist

```
Before submitting PR, verify:

☐ Code follows style guide
☐ No hardcoded values (use constants/configuration)
☐ Error handling for all exceptions
☐ Logging at appropriate levels
☐ No dead code or commented-out code
☐ Null checks where applicable
☐ Performance considered (O(n) algorithms avoided)
☐ Security implications reviewed (no SQL injection, etc.)
☐ Documentation updated
☐ Unit tests added/updated
☐ Integration tests pass
☐ No breaking changes to public APIs
☐ Backward compatibility maintained
```

## Testing

### Unit Tests

```bash
# Run all unit tests
cd build
ant test

# Run specific test class
ant test -Dtest.class=org.yawlfoundation.yawl.engine.CaseManagerTest

# Run tests with coverage
ant test-with-coverage
```

### Test Structure

```java
// Location: test/org/yawlfoundation/yawl/engine/CaseManagerTest.java

public class CaseManagerTest {
    private CaseManager caseManager;
    private MockDatabase mockDatabase;

    @Before
    public void setUp() throws Exception {
        mockDatabase = new MockDatabase();
        caseManager = new CaseManager(mockDatabase);
    }

    @After
    public void tearDown() throws Exception {
        caseManager.close();
        mockDatabase.close();
    }

    @Test
    public void testCreateCase() throws Exception {
        // Arrange
        Workflow workflow = createTestWorkflow();
        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");

        // Act
        Case result = caseManager.createCase(workflow, data);

        // Assert
        assertNotNull(result);
        assertEquals(workflow.getId(), result.getWorkflowId());
        assertEquals("ACTIVE", result.getStatus());
    }

    @Test(expected = InvalidCaseException.class)
    public void testCreateCaseWithInvalidWorkflow() throws Exception {
        caseManager.createCase(null, new HashMap<>());
    }

    @Test
    public void testCompleteCase() throws Exception {
        // Given
        Case testCase = createTestCase();
        caseManager.addCase(testCase);

        // When
        caseManager.completeCase(testCase.getId());

        // Then
        Case completed = caseManager.getCase(testCase.getId());
        assertEquals("COMPLETED", completed.getStatus());
    }
}
```

### Integration Tests

```bash
# Run integration tests (requires database)
docker-compose up -d postgres
ant test-integration

# Run specific integration test
ant test-integration -Dtest.class=org.yawlfoundation.yawl.integration.WorkflowExecutionTest
```

### Test Coverage

```bash
# Generate coverage report
ant test-coverage

# View report
open build/coverage/index.html  # macOS
xdg-open build/coverage/index.html  # Linux

# Minimum coverage requirement: 70%
# Priority areas: Engine, Resource, Database layers
```

## Documentation

### Updating Documentation

```bash
# Documentation files
docs/
├── API.md                 # REST API documentation
├── ARCHITECTURE.md        # System architecture
├── INSTALLATION.md        # Installation guide
├── OPERATIONS.md          # Operations guide
├── TROUBLESHOOTING.md     # Troubleshooting guide
└── CONTRIBUTING.md        # This file

# Update existing documentation
vim docs/API.md

# Add new documentation
touch docs/FEATURE_NEW.md

# Build documentation (HTML)
sphinx-build -b html docs/ docs/_build/html/
```

### Documentation Standards

```markdown
# Heading 1 (Document Title)

## Heading 2 (Major Section)

### Heading 3 (Subsection)

Paragraphs should be clear and concise.

#### Code Examples

```bash
# Always include language identifier
command --option value
```

#### Tables

| Column 1 | Column 2 |
|----------|----------|
| Value 1  | Value 2  |

#### Lists

- Bullet point 1
- Bullet point 2
  - Nested point 2.1
  - Nested point 2.2

1. Numbered item 1
2. Numbered item 2
```

## Build and Release

### Building YAWL

```bash
# Clean build
cd build
ant clean build

# This generates:
# - build/output/engine.war
# - build/output/resourceService.war
# - build/output/monitorService.war
# - etc.

# Build Docker image
docker build -f Dockerfile -t yawl:5.2.0 .

# Verify build
ls -lh build/output/*.war
docker images | grep yawl
```

### Version Management

```bash
# Version format: major.minor.patch
# Current: 5.2.0

# In build.xml:
# <property name="version" value="5.2.0"/>

# In Dockerfile:
# ARG VERSION=5.2.0

# In helm/Chart.yaml:
# version: 5.2.0
# appVersion: "5.2"

# Semantic versioning:
# - MAJOR: Breaking changes
# - MINOR: New features (backward compatible)
# - PATCH: Bug fixes
```

### Release Process

```bash
# 1. Create release branch
git checkout -b release/5.2.0

# 2. Update version numbers
sed -i 's/5.1.0/5.2.0/g' build/build.xml
sed -i 's/5.1.0/5.2.0/g' helm/Chart.yaml
sed -i 's/5.1.0/5.2.0/g' Dockerfile

# 3. Commit version bump
git commit -m "chore(release): bump version to 5.2.0"

# 4. Create tag
git tag -a v5.2.0 -m "Release version 5.2.0"

# 5. Push to upstream
git push upstream release/5.2.0
git push upstream --tags

# 6. Create pull request
# GitHub: Create PR from release/5.2.0 to main

# 7. After merge, create GitHub release
# GitHub: Releases > Create New Release
# - Tag: v5.2.0
# - Title: YAWL 5.2.0
# - Changelog and artifacts
```

## Contributing

### Pull Request Process

```bash
# 1. Fork repository
# GitHub: Click "Fork" button

# 2. Create feature branch
git checkout -b feature/my-feature

# 3. Make changes and commit
git commit -m "feature: description"

# 4. Push to fork
git push origin feature/my-feature

# 5. Create pull request
# GitHub: Compare & pull request
# - Title: Brief description
# - Description: What changed, why, how to test
# - Link issue: "Closes #123"

# 6. Address review comments
git add .
git commit -m "Address review comments"
git push origin feature/my-feature

# 7. Squash commits before merge (if requested)
git rebase -i upstream/develop
git push -f origin feature/my-feature
```

### PR Template

```markdown
## Description
Brief description of what this PR does.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Related Issue
Closes #123

## Testing
- [ ] Unit tests added
- [ ] Integration tests pass
- [ ] Manual testing completed

## Checklist
- [ ] Code follows style guide
- [ ] Documentation updated
- [ ] No new warnings generated
- [ ] Tests pass locally

## Screenshots (if applicable)
Add screenshots for UI changes.

## Additional Context
Any additional information needed for review.
```

## Community Guidelines

### Code of Conduct

We are committed to providing a welcoming and inclusive environment for all contributors.

**Expected Behavior:**
- Be respectful and professional
- Welcome diverse perspectives
- Focus on constructive feedback
- Take responsibility for impact

**Unacceptable Behavior:**
- Harassment or discrimination
- Offensive language or imagery
- Personal attacks
- Intimidation or threats

### Reporting Issues

```bash
# Bug Report
# GitHub: New Issue > Bug Report
# - Describe the bug clearly
# - Steps to reproduce
# - Expected vs actual behavior
# - Environment (version, OS, etc.)

# Feature Request
# GitHub: New Issue > Feature Request
# - Describe the feature
# - Use case/motivation
# - Proposed implementation (optional)

# Discussion
# GitHub Discussions > New Discussion
# - Questions
# - Ideas
# - Implementation advice
```

### Getting Help

- **Documentation**: https://docs.yawlfoundation.org
- **Forum**: https://forum.yawlfoundation.org
- **GitHub Issues**: https://github.com/yawlfoundation/yawl/issues
- **Email**: support@yawlfoundation.org

### Communication Channels

```
GitHub Issues      - Bug reports, feature requests
Pull Requests      - Code review, discussion
GitHub Discussions - Questions, ideas, general discussion
Forum             - Community support, best practices
Email             - Official communication, formal matters
```

## Development Tips

### Useful Commands

```bash
# View current branch status
git status
git log --oneline -n 10

# Compare with upstream
git diff upstream/develop..HEAD

# Sync with upstream
git fetch upstream
git rebase upstream/develop

# Undo last commit (keeps changes)
git reset --soft HEAD~1

# View file history
git log -p src/MyFile.java

# Create patch
git format-patch upstream/develop -o patches/

# Apply patch
git apply patches/0001-fix-issue.patch
```

### Debugging

```bash
# Enable debug logging
export LOG_LEVEL=DEBUG

# Run with debugger
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 ...

# Attach debugger: IntelliJ/Eclipse -> Debug Configuration -> Remote

# View database queries
docker logs -f yawl-postgres | grep query

# Monitor system resources
docker stats
watch 'docker ps'
```

---

**Contributing Guide Version**: 1.0
**Last Updated**: 2026-02-14
**Maintained By**: YAWL Foundation
