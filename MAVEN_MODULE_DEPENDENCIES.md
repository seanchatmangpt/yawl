# YAWL Maven Module Dependencies

## Module Dependency Graph

```
┌──────────────────┐
│ yawl-utilities   │  Base utilities, authentication, logging, schema
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  yawl-elements   │  Workflow element definitions (Petri net components)
└────────┬─────────┘
         │
         ├──────────────────────────────┬──────────────────────────┐
         │                              │                          │
         ▼                              ▼                          ▼
┌──────────────────┐          ┌──────────────────┐      ┌──────────────────┐
│   yawl-engine    │          │ yawl-stateless   │      │ yawl-scheduling  │
│  (YEngine, DB)   │          │  (Event-driven)  │      │   (Timers)       │
└────────┬─────────┘          └────────┬─────────┘      └──────────────────┘
         │                              │
         ├──────────────────────────────┤
         │                              │
         ▼                              │
┌──────────────────┐                    │
│ yawl-resourcing  │                    │
│  (Work queues)   │                    │
└────────┬─────────┘                    │
         │                              │
         ▼                              │
┌──────────────────┐                    │
│  yawl-worklet    │                    │
│  (RDR rules)     │                    │
└────────┬─────────┘                    │
         │                              │
         ├──────────────────────────────┘
         │
         ▼
┌──────────────────┐
│ yawl-integration │  MCP, A2A, Spring Boot adapters
└──────────────────┘

         ┌──────────────────┐
         │ yawl-monitoring  │  JSF-based monitoring (depends on yawl-engine)
         └──────────────────┘

         ┌──────────────────┐
         │yawl-control-panel│  Swing desktop app (depends on yawl-engine)
         └──────────────────┘
```

## Dependency Matrix

| Module              | Depends On                                    | Provides                                      |
|---------------------|----------------------------------------------|-----------------------------------------------|
| **yawl-utilities**  | None (base)                                  | Authentication, logging, schema, exceptions   |
| **yawl-elements**   | yawl-utilities                               | YSpecification, YNet, YTask, YCondition       |
| **yawl-engine**     | yawl-elements                                | YEngine, YNetRunner, persistence              |
| **yawl-stateless**  | None (independent)                           | YStatelessEngine, case import/export          |
| **yawl-resourcing** | yawl-engine                                  | Resource allocation, work queues              |
| **yawl-worklet**    | yawl-engine, yawl-resourcing                 | Dynamic worklet selection, RDR rules          |
| **yawl-scheduling** | yawl-engine                                  | Timer management, calendar integration        |
| **yawl-integration**| yawl-engine, yawl-stateless                  | MCP server, A2A server, Spring Boot           |
| **yawl-monitoring** | yawl-engine                                  | JSF monitoring UI, case tracking              |
| **yawl-control-panel**| yawl-engine                                | Swing desktop control panel                   |

## Module Details

### yawl-utilities
**Purpose**: Foundation module providing common utilities
**Source**: `src/org/yawlfoundation/yawl/{authentication,logging,schema,unmarshal,util,exceptions}`
**Key Dependencies**:
- Apache Commons (Lang3, IO, Codec)
- JDOM2 (XML processing)
- Log4j2, SLF4J (Logging)
- Jakarta XML Binding

**Provides**:
- Authentication framework
- Logging infrastructure
- YAWL schema validation
- XML unmarshalling
- String utilities
- Exception hierarchy

### yawl-elements
**Purpose**: Core workflow element definitions
**Source**: `src/org/yawlfoundation/yawl/elements`
**Key Dependencies**:
- JDOM2 (XML)
- Jaxen (XPath)
- Apache Commons Collections4

**Provides**:
- `YSpecification` - Workflow specification container
- `YNet` - Workflow net (Petri net)
- `YTask` - Atomic/Composite tasks
- `YCondition` - Places in Petri net
- `YFlow` - Arcs/transitions
- Data binding and predicate evaluation

### yawl-engine
**Purpose**: Stateful workflow engine with persistence
**Source**: `src/org/yawlfoundation/yawl/engine`
**Key Dependencies**:
- yawl-elements
- Hibernate 6.5.1 (ORM)
- H2, PostgreSQL, MySQL (Database drivers)
- HikariCP (Connection pooling)

**Provides**:
- `YEngine` - Main engine facade
- `YNetRunner` - Net execution engine
- `YWorkItem` - Work item management
- Persistence layer (Hibernate mappings)
- Interface A (design-time)
- Interface B (client interaction)
- Interface E (event notifications)
- Interface X (extended operations)

### yawl-stateless
**Purpose**: Lightweight stateless workflow engine
**Source**: `src/org/yawlfoundation/yawl/stateless`
**Key Dependencies**:
- Apache Commons (Lang3, Collections4)
- JDOM2
- Jackson (JSON processing)

**Provides**:
- `YStatelessEngine` - Stateless execution engine
- `YCaseMonitor` - Case state tracking
- Case import/export (serialization)
- Event-driven execution model
- No persistence overhead

### yawl-resourcing
**Purpose**: Resource management and allocation
**Source**: `src/org/yawlfoundation/yawl/resourcing`
**Key Dependencies**:
- yawl-engine
- Hibernate (persistence)

**Provides**:
- `ResourceManager` - Central resource management
- `AbstractSelector` - Allocation strategies
- Work queue management
- Participant management
- Role-based allocation
- Organizational model

**Hibernate Mappings**:
- `ResourceMap.hbm.xml`
- `WorkQueue.hbm.xml`

### yawl-worklet
**Purpose**: Dynamic worklet selection and exception handling
**Source**: `src/org/yawlfoundation/yawl/worklet`
**Key Dependencies**:
- yawl-engine
- yawl-resourcing
- Hibernate

**Provides**:
- Worklet selection service
- Ripple-Down Rules (RDR) engine
- Exception handling framework
- Runtime workflow adaptation
- Rule-based substitution

### yawl-scheduling
**Purpose**: Workflow scheduling and timing
**Source**: `src/org/yawlfoundation/yawl/scheduling`
**Key Dependencies**:
- yawl-engine

**Provides**:
- Timer management
- Calendar integration
- Scheduled task execution
- Time-based workflow triggers

### yawl-integration
**Purpose**: External system integration
**Source**: `src/org/yawlfoundation/yawl/integration`
**Key Dependencies**:
- yawl-engine
- yawl-stateless
- MCP SDK 0.17.2 (Model Context Protocol)
- A2A SDK 1.0.0.Alpha2 (Agent-to-Agent)
- Spring Boot 3.2.5
- OkHttp 4.12.0
- JSpecify 1.0.0

**Provides**:
- `YawlMcpServer` - MCP integration server
- `YawlA2AServer` - A2A communication server
- Spring Boot REST endpoints
- Spring Actuator health checks
- Integration adapters:
  - Order fulfillment
  - Process mining
  - Autonomous agents

### yawl-monitoring
**Purpose**: Process monitoring and visualization
**Source**: `src/org/yawlfoundation/yawl/monitor`
**Key Dependencies**:
- yawl-engine
- Jakarta Faces 3.0 (JSF)
- Glassfish JSF implementation

**Provides**:
- JSF-based monitoring UI
- Case instance tracking
- Real-time process visualization
- Instance inspection
- Performance metrics

**Scope Notes**:
- Jakarta Servlet API: `provided` (container-supplied)

### yawl-control-panel
**Purpose**: Desktop control panel application
**Source**: `src/org/yawlfoundation/yawl/controlpanel`
**Key Dependencies**:
- yawl-engine
- Apache Commons IO

**Provides**:
- Swing-based desktop UI
- YAWL server management
- Process deployment
- Configuration management
- Update management
- Preferences editor

**Main Class**: `org.yawlfoundation.yawl.controlpanel.YControlPanel`

**Resources**:
- Icons and images (`*.png`, `*.gif`, `*.jpg`)
- UI properties

## Build Order

Maven reactor build order (automatically determined):

1. **yawl-utilities** (no dependencies)
2. **yawl-elements** (depends on utilities)
3. **yawl-stateless** (independent)
4. **yawl-engine** (depends on elements)
5. **yawl-scheduling** (depends on engine)
6. **yawl-resourcing** (depends on engine)
7. **yawl-worklet** (depends on engine, resourcing)
8. **yawl-integration** (depends on engine, stateless)
9. **yawl-monitoring** (depends on engine)
10. **yawl-control-panel** (depends on engine)

## Testing Dependencies

All modules inherit test dependencies from parent:
- JUnit 4.13.2
- Hamcrest 1.3
- XMLUnit 1.3 (for modules with XML processing)

Additional test dependencies:
- **yawl-integration**: Spring Boot Test, TestContainers

## Key Technology Stack

### Persistence Layer
- **ORM**: Hibernate 6.5.1.Final
- **Databases**: H2, PostgreSQL, MySQL, Derby, HSQLDB
- **Connection Pooling**: HikariCP 5.1.0

### Web Layer
- **Servlet**: Jakarta Servlet 6.0.0
- **JSF**: Jakarta Faces 3.0.0 (Glassfish impl)
- **REST**: Spring Boot 3.2.5

### Integration
- **MCP**: Model Context Protocol 0.17.2
- **A2A**: Agent-to-Agent 1.0.0.Alpha2
- **HTTP**: OkHttp 4.12.0

### Utilities
- **XML**: JDOM2 2.0.6.1, Jakarta XML Binding 3.0.1
- **JSON**: Jackson 2.18.2, Gson 2.11.0
- **Commons**: Lang3 3.14.0, IO 2.15.1, Collections4 4.4

### Logging
- **API**: SLF4J 2.0.9
- **Implementation**: Log4j2 2.23.1

## Profiles and Build Variants

### Development Profiles
- **java21** (default): LTS production build
- **java24**: Future compatibility testing
- **java25**: Preview features (virtual threads, structured concurrency)

### Production Profiles
- **prod**: OWASP security scanning (CVSS threshold 7.0)
- **security-audit**: Comprehensive security audit

### Deployment Profiles
- **docker**: Fat JAR builds for containerization

## Module Activation

To build only specific modules:

```bash
# Build engine and all its dependencies
mvn clean install -pl yawl-engine -am

# Build integration without running tests
mvn clean install -pl yawl-integration -DskipTests

# Build all except control panel
mvn clean install -pl '!yawl-control-panel'
```

## Circular Dependencies

**None detected**. The module structure is acyclic:
- yawl-utilities → yawl-elements → yawl-engine → services
- yawl-stateless is independent
- Integration modules depend on engine but not vice versa

## Future Modules (Planned)

Based on source code structure, potential future modules:

1. **yawl-cost-service** - Cost evaluation service
2. **yawl-digital-signature** - Document signing
3. **yawl-document-store** - Document management
4. **yawl-reporter** - Reporting service
5. **yawl-simulation** - Process simulation
6. **yawl-proclet** - Proclet service
7. **yawl-balancer** - Load balancing

These are currently in the monolithic structure and may be extracted as modules in future releases.
