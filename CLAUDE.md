# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

# YAWL - Yet Another Workflow Language

Java-based BPM/Workflow engine with formal foundations. Version 5.2.

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
```

## Test Commands

```bash
# Run all test suites (JUnit)
java -cp classes:build/3rdParty/lib/* junit.textui.TestRunner org.yawlfoundation.yawl.TestAllYAWLSuites
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
