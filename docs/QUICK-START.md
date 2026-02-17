# YAWL v6.0.0 Quick Start Guide
**5-Minute Setup for New Developers**

## Prerequisites

Ensure you have:
- **Java 21+** (OpenJDK recommended)
- **Maven 3.9.11+**
- **Git**
- **4GB+ RAM** (8GB recommended)
- **2GB disk space** for dependencies

## Setup (5 minutes)

### 1. Clone Repository
```bash
git clone https://github.com/yawlfoundation/yawl.git
cd yawl
```

### 2. Verify Environment
```bash
# Check Java
java -version          # Should show OpenJDK 21+

# Check Maven
mvn -version           # Should show 3.9.11+
```

### 3. Build Project
```bash
# Full build with tests (first time: ~5-10 minutes with network)
mvn clean install

# Just compile (if you don't need tests)
mvn clean compile

# Run tests only
mvn clean test
```

### 4. Verify Installation
```bash
# Check for successful build
ls target/yawl-*.jar   # Should exist

# Run control panel (requires X11 or headless mode)
java -jar yawl-control-panel/target/*.jar
```

## IDE Setup (2 minutes)

### IntelliJ IDEA
1. **File** > **Open** > Select `pom.xml`
2. **Maven** > **Reload Projects** (or press Ctrl+Shift+O)
3. Let indexing complete (~2 min)
4. Ready to develop!

### Eclipse/STS
1. **File** > **Import** > **Existing Maven Projects**
2. Browse to project root, click **Finish**
3. Let Maven dependencies resolve (~2 min)
4. Ready to develop!

### VS Code
1. Install **Extension Pack for Java**
2. Open folder
3. Maven should auto-detect `pom.xml`
4. Wait for dependency resolution
5. Ready to develop!

## First Commit Workflow

### 1. Create Feature Branch
```bash
git checkout -b feature/my-feature
```

### 2. Make Changes
Edit files in `src/` or `test/`

### 3. Build & Test
```bash
# Must pass before commit!
mvn clean compile && mvn clean test
```

### 4. Commit
```bash
git add src/path/to/MyFile.java test/path/to/MyFileTest.java
git commit -m "Add feature: description of what you did"
```

### 5. Push
```bash
git push -u origin feature/my-feature
```

## Project Structure

```
yawl/
├── pom.xml                    # Root Maven configuration
├── yawl-*/                    # 12 core modules
│   ├── src/                   # Production code
│   ├── test/                  # Unit tests
│   └── pom.xml               # Module config
├── docs/                      # Documentation
├── test/                      # Integration tests
└── schema/                    # XML schemas
```

## Key Modules

| Module | Purpose |
|--------|---------|
| **yawl-elements** | Core workflow element definitions |
| **yawl-engine** | Stateful workflow execution engine |
| **yawl-stateless** | Stateless engine for cloud deployments |
| **yawl-integration** | MCP and A2A server integrations |
| **yawl-authentication** | Authentication and authorization |
| **yawl-resourcing** | Resource allocation and scheduling |

## Common Commands

```bash
# Compile only
mvn clean compile

# Run tests
mvn clean test

# Full build with all tests
mvn clean install

# Run specific test
mvn test -Dtest=YWorkItemTest

# Run tests in parallel (faster)
mvn test -T 2

# Build without tests
mvn clean install -DskipTests

# Generate project documentation
mvn clean javadoc:javadoc

# Check for unused dependencies
mvn dependency:analyze
```

## Troubleshooting

### "Cannot resolve dependencies"
**Solution**: Ensure network is available for first build
```bash
mvn clean install   # Downloads all dependencies
```

### "Java version mismatch"
**Solution**: Verify Java 21 is default
```bash
java -version      # Should show 21.x.x
javac -version     # Should show 21.x.x
```

### "Tests timeout"
**Solution**: Increase memory
```bash
export MAVEN_OPTS="-Xmx4g"
mvn clean test
```

### "Port already in use"
**Solution**: Kill existing process
```bash
lsof -i :8080        # Find process on port 8080
kill -9 <PID>        # Kill it
```

## HYPER_STANDARDS Compliance

All commits must follow HYPER_STANDARDS:

✓ **DO**:
- Implement real functionality or throw `UnsupportedOperationException`
- Write meaningful test cases
- Update documentation
- Use descriptive commit messages

✗ **DON'T**:
- Add TODO/FIXME/XXX/HACK comments
- Create mock/stub/fake implementations
- Return empty strings or null values as placeholders
- Skip error handling
- Lie about what your code does

## Getting Help

- **Documentation**: See `/docs` directory
- **Tests**: Look at `test/` for examples
- **Issues**: Check GitHub issues for known problems
- **Community**: Visit yawlfoundation.org

## Next Steps

1. Read **BUILD.md** for deep Maven understanding
2. Read **TESTING.md** for testing strategy
3. Read **CONTRIBUTING.md** for contribution guidelines
4. Check **TROUBLESHOOTING.md** for common issues

Happy coding!
