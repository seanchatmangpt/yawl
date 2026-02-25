# YAWL Developer Guide

**Version:** 5.2
**Last Updated:** 2026-02-16
**Target Audience:** Developers extending or modifying YAWL

---

## Table of Contents

1. [Development Environment Setup](#development-environment-setup)
2. [Project Structure](#project-structure)
3. [Build System](#build-system)
4. [Testing Strategy](#testing-strategy)
5. [Code Standards](#code-standards)
6. [Claude Code Integration](#claude-code-integration)
7. [Architecture Deep Dive](#architecture-deep-dive)
8. [Contributing Guidelines](#contributing-guidelines)

---

## Development Environment Setup

### Required Tools

| Tool | Version | Purpose |
|------|---------|---------|
| **Java JDK** | 25+ | Compilation and runtime |
| **Maven** | 3.9+ | Build and dependency management |
| **Git** | 2.30+ | Version control |
| **Docker** | 24+ | Containerization |
| **IDE** | Any | IntelliJ IDEA, Eclipse, or VS Code |

### Environment Setup

#### 1. Install Java 25

```bash
# Using SDKMAN (recommended)
curl -s "https://get.sdkman.io" | bash
sdk install java 25.0.0-open

# Verify
java -version
# Expected: openjdk version "25"
```

#### 2. Install Maven

```bash
# Using SDKMAN
sdk install maven 3.9.6

# Or download from:
# https://maven.apache.org/download.cgi

# Verify
mvn -version
# Expected: Apache Maven 3.9.6
```

#### 3. Configure IDE

**IntelliJ IDEA:**
```bash
# Import as Maven project
File → Open → Select pom.xml
# IntelliJ auto-imports dependencies

# Set JDK
File → Project Structure → Project SDK → Java 25
```

**Eclipse:**
```bash
# Import Maven project
File → Import → Maven → Existing Maven Projects
# Select root pom.xml

# Set compiler
Window → Preferences → Java → Compiler → 25
```

**VS Code:**
```bash
# Install extensions
code --install-extension vscjava.vscode-java-pack
code --install-extension vscjava.vscode-maven

# Open folder
code /path/to/yawl
```

#### 4. Clone and Build

```bash
# Clone repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl

# First build (downloads dependencies)
mvn clean install

# Expected: BUILD SUCCESS (~2-3 minutes)
```

---

## Project Structure

### Multi-Module Maven Layout

```
yawl/
├── pom.xml                      # Parent POM
├── yawl-utilities/              # Common utilities
├── yawl-elements/               # Workflow element definitions
├── yawl-engine/                 # Stateful workflow engine
├── yawl-stateless/              # Stateless workflow engine
├── yawl-resourcing/             # Resource management
├── yawl-worklet/                # Dynamic workflow selection
├── yawl-scheduling/             # Scheduling and timers
├── yawl-integration/            # MCP, A2A, Z.AI integration
├── yawl-monitoring/             # Process monitoring
├── yawl-control-panel/          # Desktop control panel
├── src/                         # Legacy source (being migrated)
├── test/                        # Test suites
├── schema/                      # YAWL XML schema definitions
├── docs/                        # Documentation
├── k8s/                         # Kubernetes manifests
└── .claude/                     # Claude Code configuration
```

### Module Dependencies

```
yawl-utilities
    ↓
yawl-elements
    ↓
yawl-engine ← yawl-stateless
    ↓          ↓
yawl-resourcing
    ↓
yawl-worklet
    ↓
yawl-integration (MCP/A2A)
yawl-scheduling
yawl-monitoring
yawl-control-panel
```

### Key Package Structure

```
org.yawlfoundation.yawl
├── authentication          # Security and auth
├── balancer               # Load balancing
├── controlpanel           # Desktop GUI
├── costmodel              # Cost analysis
├── elements               # Core workflow elements
├── engine                 # Stateful workflow engine
├── exceptions             # Exception hierarchy
├── integration
│   ├── a2a               # Agent-to-Agent protocol
│   ├── mcp               # Model Context Protocol
│   └── zai               # Z.AI integration
├── logging                # Logging framework
├── monitoring             # Process monitoring
├── resourcing             # Resource management
├── scheduling             # Scheduling service
├── schema                 # XML schema utilities
├── stateless              # Stateless engine
├── unmarshal              # XML unmarshalling
├── util                   # Common utilities
└── worklet                # Worklet service
```

---

## Build System

### Maven Multi-Module Build

YAWL uses Maven as the primary build system. Ant (`build/build.xml`) is deprecated and will be removed in v6.0.

#### Parent POM Configuration

All modules inherit from the parent POM (`pom.xml`):

**Key Properties:**
```xml
<properties>
    <java.version>25</java.version>
    <maven.compiler.source>25</maven.compiler.source>
    <maven.compiler.target>25</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```

**Dependency Management:**
- Spring Boot BOM 3.2.5
- Jakarta EE BOM 10.0.0
- OpenTelemetry BOM 1.40.0
- Resilience4j BOM 2.2.0
- TestContainers BOM 1.19.7

#### Build Commands

```bash
# Full build (all modules)
mvn clean install

# Compile only
mvn compile

# Run tests
mvn test

# Package WARs/JARs
mvn package

# Skip tests (faster)
mvn clean install -DskipTests

# Parallel build (4 threads)
mvn clean install -T 4

# Build specific module
cd yawl-engine
mvn clean install

# Build module + dependencies
mvn clean install -pl yawl-engine -am
```

#### Build Profiles

**Development Profiles:**

1. **java21** (default) - LTS production build
   ```bash
   mvn clean install
   ```

2. **java24** - Future compatibility testing
   ```bash
   mvn clean install -Pjava24
   ```

3. **java25** - Experimental preview features
   ```bash
   mvn clean install -Pjava25
   ```

**Production Profiles:**

1. **prod** - Production build with security scanning
   ```bash
   mvn clean install -Pprod
   ```
   - OWASP Dependency Check
   - CVSS threshold: 7.0
   - Fail on high-severity vulnerabilities

2. **security-audit** - Comprehensive security audit
   ```bash
   mvn clean verify -Psecurity-audit
   ```

**Deployment Profiles:**

1. **docker** - Build fat JARs for containers
   ```bash
   mvn clean package -Pdocker
   ```

#### Maven Plugins

**Essential Plugins:**
- `maven-compiler-plugin` - Java compilation
- `maven-surefire-plugin` - Test execution
- `maven-jar-plugin` - JAR packaging
- `jacoco-maven-plugin` - Code coverage
- `maven-shade-plugin` - Fat JAR creation
- `maven-enforcer-plugin` - Build rules enforcement
- `maven-dependency-plugin` - Dependency analysis

---

## Testing Strategy

### Chicago TDD (Detroit School)

YAWL follows **Chicago-style TDD** (not London/mockist):
- **Real integrations** over mocks
- **Real database** for persistence tests
- **Real services** for integration tests
- **Test doubles** only when external dependencies unavailable

### Test Organization

```
test/
└── org/yawlfoundation/yawl/
    ├── elements/          # Element tests
    ├── engine/            # Engine tests
    ├── exceptions/        # Exception tests
    ├── logging/           # Logging tests
    ├── resourcing/        # Resourcing tests
    ├── stateless/         # Stateless engine tests
    ├── authentication/    # Auth tests
    └── integration/       # Integration tests
```

### Test Suites

| Suite | Tests | Purpose |
|-------|-------|---------|
| **TestElements** | 25+ | Workflow element validation |
| **TestEngine** | 30+ | Engine core functionality |
| **TestStateless** | 15+ | Stateless engine |
| **TestResourcing** | 20+ | Resource management |
| **TestAuthentication** | 12+ | Security and auth |
| **Integration** | 10+ | End-to-end scenarios |

### Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=YEngineTest

# Specific test method
mvn test -Dtest=YEngineTest#testCaseLaunch

# Integration tests only
mvn test -Dtest=IntegrationTestSuite

# Generate coverage report
mvn jacoco:report
# View: target/site/jacoco/index.html
```

### Test Environment Configuration

**Environment Detection:**
```java
import org.yawlfoundation.yawl.util.EnvironmentDetector;

@Before
public void setUp() {
    if (EnvironmentDetector.isClaudeCodeRemote()) {
        // Use H2 in-memory database
        // Skip integration tests requiring services
    } else {
        // Use PostgreSQL
        // Run full test suite
    }
}
```

**Database Configuration:**
```properties
# Local environment (build.properties.local)
database.type=postgres
database.path=yawl
database.user=yawl_admin
database.password=yawl_password

# Remote/CI environment (build.properties.remote)
database.type=h2
database.path=mem:yawl;DB_CLOSE_DELAY=-1
database.user=sa
database.password=
```

### Test Coverage Goals

| Module | Target Coverage | Current |
|--------|----------------|---------|
| **yawl-engine** | 80%+ | 75% |
| **yawl-elements** | 85%+ | 82% |
| **yawl-stateless** | 75%+ | 68% |
| **yawl-resourcing** | 70%+ | 65% |
| **yawl-integration** | 60%+ | 55% |

---

## Code Standards

### HYPER_STANDARDS Compliance

YAWL enforces **Fortune 5 production standards** via automated hooks.

#### Forbidden Patterns (H Guards)

**NEVER write code containing:**
1. `TODO`, `FIXME`, `XXX`, `HACK` - Deferred work markers
2. `mock()`, `stub()`, `fake()` - Mock method names
3. `class MockService` - Mock class names
4. `boolean useMockData = true` - Mock mode flags
5. `return "";` - Empty returns (unless semantic)
6. `return null; // stub` - Null returns with stubs
7. `public void method() { }` - No-op methods
8. `DUMMY_CONFIG`, `PLACEHOLDER_VALUE` - Placeholder constants
9. `catch (e) { return mockData(); }` - Silent fallbacks
10. `if (isTestMode) return mock();` - Conditional mocks
11. `.getOrDefault(key, "test_value")` - Fake defaults
12. `if (true) return;` - Logic skipping
13. `log.warn("not implemented")` - Log instead of throw
14. `import org.mockito.*` - Mock imports in src/

**Enforcement:**
- `.claude/hooks/hyper-validate.sh` runs after every Write/Edit
- Blocks commit if violations detected
- Exit code 2 = validation failure

#### Required Patterns (Q Invariants)

**Every public method must:**
1. **Do real work** OR
2. **Throw UnsupportedOperationException** with clear message

**Example:**
```java
// GOOD: Real implementation
public String processWorkflow(String specID) {
    YSpecification spec = loadSpecification(specID);
    return spec.execute();
}

// GOOD: Explicit unsupported
public String advancedFeature() {
    throw new UnsupportedOperationException(
        "Advanced feature requires Enterprise Edition"
    );
}

// BAD: Empty placeholder
public String processWorkflow(String specID) {
    // TODO: Implement later
    return "";
}

// BAD: Mock implementation
public String processWorkflow(String specID) {
    return mockWorkflowExecution(specID);
}
```

### Java Code Style

**File Organization:**
```java
// 1. Package declaration
package org.yawlfoundation.yawl.engine;

// 2. Imports (alphabetical, grouped)
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.exceptions.YEngineException;

// 3. Class Javadoc
/**
 * Core workflow engine implementation.
 *
 * <p>YEngine manages workflow case lifecycle, task execution, and state persistence.
 * Uses Hibernate for database persistence and supports multiple workflow patterns.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YEngine {
    // 4. Static constants
    public static final String ENGINE_VERSION = "5.2.0";

    // 5. Instance fields
    private YPersistenceManager _pmgr;

    // 6. Constructors
    public YEngine() {
        _pmgr = YPersistenceManager.getInstance();
    }

    // 7. Public methods
    public String launchCase(String specID) {
        // Implementation
    }

    // 8. Private methods
    private YSpecification loadSpec(String id) {
        // Implementation
    }
}
```

**Naming Conventions:**
- Classes: `PascalCase` (e.g., `YEngine`, `YWorkItem`)
- Methods: `camelCase` (e.g., `launchCase`, `getWorkItems`)
- Constants: `UPPER_SNAKE_CASE` (e.g., `ENGINE_VERSION`)
- Variables: `camelCase` or `_camelCase` for instance fields
- Packages: `lowercase` (e.g., `org.yawlfoundation.yawl.engine`)

**Javadoc Requirements:**
```java
/**
 * Brief one-line description.
 *
 * <p>Detailed multi-paragraph description if needed.</p>
 *
 * @param paramName description of parameter
 * @return description of return value
 * @throws ExceptionType when this exception is thrown
 * @see RelatedClass
 * @since 5.2
 */
public ReturnType methodName(ParamType paramName) throws ExceptionType {
    // Implementation
}
```

---

## Claude Code Integration

### Repository Structure

```
.claude/
├── hooks/
│   ├── session-start.sh           # Environment setup
│   ├── hyper-validate.sh          # Code quality validation
│   └── validate-no-mocks.sh       # Pre-commit checks
├── skills/
│   ├── yawl-build/               # Build automation skill
│   ├── yawl-test/                # Test execution skill
│   └── yawl-validate/            # Validation skill
├── agents/
│   ├── yawl-engineer.md          # Development agent
│   ├── yawl-tester.md            # Testing agent
│   ├── yawl-reviewer.md          # Code review agent
│   └── yawl-validator.md         # Quality validation agent
└── settings.json                  # Hook configuration
```

### SessionStart Hook

**Purpose:** Automatic environment setup for Claude Code Web sessions

**What it does:**
1. Installs Apache Ant (if missing)
2. Configures H2 in-memory database for ephemeral sessions
3. Backs up local PostgreSQL configuration
4. Creates remote-specific configuration
5. Symlinks correct configuration based on environment
6. Exports environment variables

**Configuration:**
```json
{
  "hooks": {
    "SessionStart": [{
      "hooks": [{
        "type": "command",
        "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/session-start.sh",
        "statusMessage": "Setting up YAWL environment..."
      }]
    }]
  }
}
```

### PostToolUse Hook

**Purpose:** Enforce code quality standards after every file modification

**What it validates:**
- No TODO/FIXME markers
- No mock/stub/fake patterns
- No empty returns (unless semantic)
- No placeholder constants
- No silent exception handling

**Configuration:**
```json
{
  "hooks": {
    "PostToolUse": [{
      "matcher": "Write|Edit",
      "hooks": [{
        "type": "command",
        "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/hyper-validate.sh",
        "statusMessage": "Validating code quality..."
      }]
    }]
  }
}
```

### Stop Hook

**Purpose:** Final verification before session completion

**What it checks:**
- All changes committed
- No uncommitted modifications
- Clean working tree
- Branch pushed to remote

### Environment Detection

**Runtime detection class:**
```java
import org.yawlfoundation.yawl.util.EnvironmentDetector;

// Detect environment
boolean isRemote = EnvironmentDetector.isClaudeCodeRemote();

// Choose database strategy
boolean useH2 = EnvironmentDetector.useEphemeralDatabase();

// Skip integration tests
boolean skipIntegration = EnvironmentDetector.skipIntegrationTests();

// Get recommended database type
String dbType = EnvironmentDetector.getRecommendedDatabaseType();
// Returns: "h2" (remote) or "postgres" (local)
```

---

## Architecture Deep Dive

### Core Components

#### 1. YEngine (Stateful Workflow Engine)

**Location:** `yawl-engine/`

**Key Classes:**
- `YEngine` - Main engine API
- `YNetRunner` - Executes workflow nets
- `YIdentifier` - Case/instance identifier
- `YWorkItem` - Work item representation
- `YPersistenceManager` - Database persistence

**Workflow Lifecycle:**
```
1. Upload Specification (YSpecification)
2. Launch Case (YEngine.launchCase)
3. Create Net Instance (YNetRunner)
4. Enable Work Items (YWorkItem)
5. Execute Tasks
6. Complete Case
```

#### 2. YStatelessEngine (Stateless Workflow Engine)

**Location:** `yawl-stateless/`

**Key Differences from YEngine:**
- No database persistence (memory-only or XML snapshots)
- Event-driven architecture via listeners
- Full case serialization/deserialization support
- Ideal for lightweight deployments

**Entry Points:**
- `YStatelessEngine` - Main API
- `YCaseMonitor` - Idle case detection
- `YCaseImporter/Exporter` - Serialization

#### 3. YElements (Workflow Element Definitions)

**Location:** `yawl-elements/`

**Key Classes:**
- `YSpecification` - Workflow specification
- `YNet` - Workflow net definition
- `YTask` - Task element
- `YCondition` - Condition element
- `YFlow` - Flow between elements

#### 4. YResourcing (Resource Management)

**Location:** `yawl-resourcing/`

**Capabilities:**
- Participant management
- Role-based access control
- Work item allocation strategies
- Calendar and availability management

#### 5. YIntegration (MCP/A2A)

**Location:** `yawl-integration/`

**Sub-packages:**
- `mcp` - Model Context Protocol integration
- `a2a` - Agent-to-Agent protocol
- `zai` - Z.AI API integration

**MCP Integration:**
```java
YawlMcpServer server = new YawlMcpServer(3000);
server.registerWorkflowTools();
server.start();
// Exposes YAWL operations as MCP tools
```

**A2A Integration:**
```java
YawlA2AServer server = new YawlA2AServer(8080);
server.start();
// Exposes YAWL capabilities via A2A protocol
```

### Database Architecture

**Supported Databases:**
- PostgreSQL 15+ (recommended for production)
- H2 (in-memory or file-based, for development/testing)
- MySQL 8.0+ (supported)
- Oracle 19c+ (enterprise)

**ORM:** Hibernate 5.6+

**Connection Pooling:** HikariCP

**Configuration:**
```properties
# PostgreSQL (production)
database.type=postgres
database.host=localhost
database.port=5432
database.name=yawl
database.user=yawl_admin
database.password=secure_password

# H2 (development)
database.type=h2
database.path=mem:yawl;DB_CLOSE_DELAY=-1
database.user=sa
database.password=
```

---

## Contributing Guidelines

### Before You Code

1. **Read CLAUDE.md** - Understand project conventions
2. **Check HYPER_STANDARDS.md** - Know forbidden patterns
3. **Review existing code** - Follow established patterns
4. **Check GitHub issues** - Avoid duplicate work

### Development Workflow

```bash
# 1. Create feature branch
git checkout -b feature/your-feature-name

# 2. Make changes
# Edit files...

# 3. Build and test
mvn clean install

# 4. Verify code quality
# (PostToolUse hook runs automatically)

# 5. Commit changes
git add src/path/to/changed/files
git commit -m "$(cat <<'EOF'
Add feature: Brief description

Detailed description of changes:
- What was added/changed
- Why it was necessary
- How it was implemented

https://claude.ai/code/session_YOUR_SESSION_ID
EOF
)"

# 6. Push to remote
git push -u origin feature/your-feature-name

# 7. Create pull request
gh pr create --title "Your Feature" --body "Description"
```

### Commit Message Format

```
<type>: <subject>

<body>

<session-url>
```

**Types:**
- `feat` - New feature
- `fix` - Bug fix
- `refactor` - Code refactoring
- `test` - Add/update tests
- `docs` - Documentation
- `chore` - Build/tooling changes

**Example:**
```
feat: Add MCP server integration

Implements Model Context Protocol server that exposes YAWL
workflows as AI-callable tools. Includes:
- YawlMcpServer with SSE transport
- Tool definitions for workflow operations
- Resource providers for specifications

https://claude.ai/code/session_01Jy5VPT7aRfKskDEcYVSXDq
```

### Pull Request Checklist

- [ ] Code compiles without errors
- [ ] All tests pass (`mvn test`)
- [ ] Code coverage maintained or improved
- [ ] No HYPER_STANDARDS violations
- [ ] Javadoc updated for public APIs
- [ ] README/docs updated if needed
- [ ] Commit messages include session URL
- [ ] PR description explains changes clearly

---

## Additional Resources

- **CLAUDE.md** - Project overview and conventions
- **HYPER_STANDARDS.md** - Detailed coding standards
- **BEST-PRACTICES-2026.md** - Claude Code best practices
- **INTEGRATION_GUIDE.md** - MCP/A2A integration details
- **OPERATIONS_GUIDE.md** - Production operations

---

**Total Lines:** ~400
**Estimated Reading Time:** 25 minutes
**Difficulty:** Intermediate
