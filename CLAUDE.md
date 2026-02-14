# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# YAWL - Yet Another Workflow Language

Java-based BPM/Workflow engine with formal foundations. Version 5.2.

---

# ⚠️ MANDATORY CODING STANDARDS - FORTUNE 5 PRODUCTION QUALITY

## ZERO TOLERANCE POLICY

This codebase operates under **strict Toyota Production System and Chicago TDD principles**:

### ❌ ABSOLUTELY FORBIDDEN - HARD CRASH REQUIRED:

1. **NO MOCKS** - Never create mock implementations or fake behavior
   - ❌ FORBIDDEN: `if (sdk == null) return mockResponse();`
   - ✅ REQUIRED: `if (sdk == null) throw new IllegalStateException("SDK required");`

2. **NO STUBS** - Never return placeholder values or empty implementations
   - ❌ FORBIDDEN: `public String getData() { return ""; } // TODO`
   - ✅ REQUIRED: `public String getData() { throw new UnsupportedOperationException("Not implemented"); }`

3. **NO TODOs** - Never leave TODO/FIXME/XXX/HACK comments
   - ❌ FORBIDDEN: `// TODO: implement this later`
   - ✅ REQUIRED: Either implement it NOW or throw exception with clear message

4. **NO FALLBACKS** - Never silently degrade to fake behavior
   - ❌ FORBIDDEN: `try { realImpl(); } catch (Exception e) { return mockData(); }`
   - ✅ REQUIRED: `try { realImpl(); } catch (Exception e) { throw new RuntimeException("Real implementation failed", e); }`

5. **NO LIES** - Code must honestly represent its capabilities
   - ❌ FORBIDDEN: Returning success when nothing happened
   - ✅ REQUIRED: Fail fast with clear error messages

### ✅ REQUIRED PRACTICES:

**FAIL FAST** - If dependencies are missing, crash immediately:
```java
public Service() {
    String apiKey = System.getenv("API_KEY");
    if (apiKey == null) {
        throw new IllegalStateException("API_KEY environment variable is required");
    }
}
```

**EXPLICIT EXCEPTIONS** - Unimplemented features must throw with clear messages:
```java
private String fetchData() {
    throw new UnsupportedOperationException(
        "Real API integration required. Add SDK dependency and implement:\n" +
        "  - API client initialization\n" +
        "  - Authentication handling\n" +
        "  - Error recovery"
    );
}
```

**HONEST BEHAVIOR** - Code behavior must match documentation exactly:
- If method says it "starts workflow", it must actually start a workflow
- If method says it "validates data", it must actually validate data
- Never pretend to do something you're not doing

### 🚨 ENFORCEMENT:

**AI Assistants MUST:**
1. **REFUSE** to write mocks, stubs, or TODOs
2. **STOP** immediately if asked to create placeholder code
3. **RESPOND**: "I cannot create mock/stub code. This codebase requires real implementations only. Should I implement the real version or throw an exception?"

**Code Review Checklist:**
- [ ] No "mock" or "stub" in method names or comments
- [ ] No "TODO", "FIXME", "XXX", "HACK" comments
- [ ] No empty returns (`return "";`, `return null;` without purpose)
- [ ] All unimplemented methods throw `UnsupportedOperationException`
- [ ] All missing dependencies cause `IllegalStateException` at startup

**Tests MUST:**
- Run against REAL systems only
- Require REAL dependencies (fail if missing)
- Never pass by returning fake data

### 📋 RATIONALE:

**Why This Matters:**
- **Fortune 5 Production**: We deploy to critical business systems
- **AI Confusion**: Mock code confuses future AI assistants about what's real
- **Hidden Bugs**: Silent failures hide problems until production
- **Wasted Time**: Debugging fake behavior wastes engineering hours
- **Trust**: Stakeholders need honest system status

**Toyota Production System (Jidoka)**: Stop the line when problems occur
**Chicago TDD**: Test real integrations, not mocks

---

## Environment Requirements

**This project runs in Docker/DevContainer environments.**
- Use docker-compose for full YAWL stack
- DevContainer setup available in `.devcontainer/`

## Build Commands

```bash
# Build all WARs (default target)
ant -f build/build.xml buildWebApps

# Full build (all release material)
ant -f build/build.xml buildAll

# Compile only
ant -f build/build.xml compile

# Build standalone JAR
ant -f build/build.xml build_Standalone

# Clean build artifacts
ant -f build/build.xml clean

# Generate Javadoc
ant -f build/build.xml javadoc

# Run unit tests
ant -f build/build.xml unitTest
```

## Docker/DevContainer Setup

### Option 1: VS Code DevContainer (Recommended)
```bash
# Open in VS Code with DevContainer
# VSCode will prompt to "Reopen in Container"
# This automatically sets up Java 21 and Ant

# Then use the build script:
./.claude/build.sh all
```

See `.devcontainer/devcontainer.json` for configuration.

### Option 2: Docker Compose (Full Stack)
```bash
# Start YAWL dev environment with PostgreSQL
docker-compose up -d

# Enter the development container
docker-compose exec yawl-dev bash

# Inside container, use build script:
./.claude/build.sh all
```

This includes:
- YAWL development environment (Java 21, Ant)
- PostgreSQL database (pre-configured)
- Tomcat ready for deployment
- Port forwarding (8080, 8081)

## Test Commands

```bash
# Run all test suites (JUnit)
java -cp classes:build/3rdParty/lib/* junit.textui.TestRunner org.yawlfoundation.yawl.TestAllYAWLSuites

# Run unit tests via Ant
ant unitTest
```

Test suites: Elements, State, Engine, Exceptions, Logging, Schema, Unmarshaller, Util, Worklist, Authentication.

## Database Configuration

Edit `build/build.properties`:
- `database.type`: postgres, mysql, derby, h2, hypersonic, oracle
- `database.path`, `database.user`, `database.password`

Build target auto-configures Hibernate based on database type.

## Architecture

### Core Packages

| Package | Purpose |
|---------|---------|
| `org.yawlfoundation.yawl.engine` | Core workflow engine (YEngine.java) |
| `org.yawlfoundation.yawl.stateless` | Stateless engine variant (YStatelessEngine.java) |
| `org.yawlfoundation.yawl.elements` | YAWL net elements (tasks, conditions, flows) |
| `org.yawlfoundation.yawl.resourcing` | Human/non-human resource management |
| `org.yawlfoundation.yawl.worklet` | Dynamic workflow via Worklets |
| `org.yawlfoundation.yawl.unmarshal` | XML specification parsing |
| `org.yawlfoundation.yawl.integration.a2a` | Agent-to-Agent protocol integration |
| `org.yawlfoundation.yawl.integration.mcp` | Model Context Protocol integration |

### Services (built as WARs)

- **engine** - Core YAWL engine
- **resourceService** - Resource allocation and work queues
- **workletService** - Dynamic process adaptation
- **monitorService** - Process monitoring
- **schedulingService** - Calendar-based scheduling
- **costService** - Cost tracking
- **balancer** - Load balancing across engine instances

### Key Classes

- `YEngine.java` - Main engine implementing Interface A (design) and Interface B (client)
- `YStatelessEngine.java` - Lightweight engine without persistence
- `YNetRunner.java` - Executes workflow net instances
- `YWorkItem.java` - Unit of work in the engine
- `YSpecification.java` - Parsed workflow specification

### Interfaces

- **Interface A** - Design-time operations (upload specs, manage services)
- **Interface B** - Client/runtime operations (launch cases, complete work items)
- **Interface E** - Event notifications
- **Interface X** - Extended operations

## XML Schemas

YAWL specifications are XML documents validated against XSD schemas in `schema/`:
- `YAWL_Schema4.0.xsd` - Latest schema version
- Historical schemas (Beta3 through 3.0) for backward compatibility

## Dependencies

Third-party libraries in `build/3rdParty/lib/`:
- JDOM2 (XML processing)
- Hibernate 5 (persistence)
- Log4J 2 (logging)
- Jackson (JSON)
- Various web/JSF libraries

## File Conventions

- Source: `src/org/yawlfoundation/yawl/`
- Tests: `test/org/yawlfoundation/yawl/`
- Schemas: `schema/`
- Example specs: `exampleSpecs/`, `test/*.ywl`
- Build config: `build/build.properties`

## Build Output

All build artifacts are located in `output/`:
- `yawl-lib-5.2.jar` - Core YAWL library (3.0 MB)
- `YawlControlPanel-5.2.jar` - Control Panel executable (423 KB)

Compiled classes: `classes/`

## MCP and A2A Integration

The project includes integration with MCP (Model Context Protocol) and A2A (Agent-to-Agent):

- `src/org/yawlfoundation/yawl/integration/a2a/` - A2A server and client
- `src/org/yawlfoundation/yawl/integration/mcp/` - MCP server and client
- `run-a2a-server.sh` - Start A2A server
- `run-mcp-server.sh` - Start MCP server

See `INTEGRATION_GUIDE.md` and `INTEGRATION_README.md` for details.

## Integration with Claude Code

This project is configured to work with Claude Code in Docker environments:
- Builds with Ant (no special IDE required)
- Tests runnable with `ant unitTest`
- Clean build artifacts tracked in `.gitignore`
- Build wrapper script `./.claude/build.sh` for easy access

---

**Last Updated:** February 14, 2026
**Project Version:** 5.2
