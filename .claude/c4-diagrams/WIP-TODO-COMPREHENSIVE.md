# C4 Diagrams - 100-Item Comprehensive Work List

**Branch**: `claude/c4-diagrams-wip-1tk3Y`
**Objective**: Maximum detail C4 architecture diagrams covering all YAWL v6.0.0 aspects
**Target Completion**: Production-ready documentation

---

## COMPLETED (7 items)

- [x] **C1-SYSTEM-CONTEXT.puml** — System context diagram with external actors
- [x] **C2-CONTAINER-ARCHITECTURE.puml** — 6-layer module organization
- [x] **C3-CORE-ENGINE-COMPONENTS.puml** — YNetRunner deep dive
- [x] **C4-WIP-WORK-IN-FLIGHT.puml** — Active initiatives tracking
- [x] **C4-INTEGRATION-FLOWS.puml** — MCP/A2A protocol flows
- [x] **C4-DEPENDENCY-GRAPH.puml** — Module dependency graph
- [x] **README.md** — Comprehensive documentation

---

## PHASE 1: ADDITIONAL SYSTEM DIAGRAMS (8 items)

### Deployment Architecture
- [ ] **C2-DEPLOYMENT-ARCHITECTURES.puml** — Dev, test, prod environments
  - Local development setup (H2 in-memory)
  - Staging environment (PostgreSQL)
  - Production cluster (HA, load balancing)
  - Container registry integration
  - Infrastructure-as-Code references

- [ ] **C2-NETWORK-TOPOLOGY.puml** — Network flows and load balancing
  - API gateway layer
  - Load balancer configuration
  - Service mesh (if applicable)
  - Network segmentation
  - Firewall rules

- [ ] **C2-DATA-FLOW-ARCHITECTURE.puml** — Complete data flow
  - Request flow (client → API → engine → response)
  - Background job processing
  - Event publishing (Kafka/RabbitMQ future)
  - Cache layers
  - Data consistency guarantees

### Security Architecture
- [ ] **C2-SECURITY-ARCHITECTURE.puml** — Auth, encryption, compliance
  - OAuth/LDAP integration points
  - JWT token lifecycle
  - TLS/mTLS configuration
  - Secrets management
  - Compliance boundaries (SOX 404)

- [ ] **C2-DISASTER-RECOVERY.puml** — Backup and recovery flows
  - RTO/RPO targets
  - Backup strategies
  - Failover mechanisms
  - Data replication
  - Recovery procedures

- [ ] **C2-MONITORING-ARCHITECTURE.puml** — Observability stack
  - Prometheus scrape targets
  - OpenTelemetry collector
  - Log aggregation (ELK/Splunk)
  - Alerting rules
  - Dashboard definitions

### API Architecture
- [ ] **C2-REST-API-ARCHITECTURE.puml** — REST endpoints and resources
  - REST resource hierarchy
  - CRUD operation mapping
  - HTTP verb usage
  - Status code conventions
  - Error response formats

- [ ] **C2-EVENTS-ARCHITECTURE.puml** — Event-driven architecture
  - Event types and schemas
  - Event routing
  - Subscriber patterns
  - Dead letter handling
  - Event versioning

---

## PHASE 2: LAYER-BY-LAYER DEEP DIVES (24 items)

### LAYER 0: Foundation (6 items)
- [ ] **C3-LAYER0-UTILITIES.puml** — yawl-utilities components
  - Configuration management
  - Logging framework
  - Validation utilities
  - Exception hierarchy
  - Helper classes

- [ ] **C3-LAYER0-SECURITY.puml** — yawl-security components
  - Crypto algorithms
  - TLS/SSL configuration
  - Certificate management
  - Auth token handling
  - Encryption/decryption flows

- [ ] **C3-LAYER0-ERLANG.puml** — yawl-erlang integration
  - Erlang node setup
  - Message passing
  - Distributed cache
  - Failover mechanisms
  - Monitoring integration

- [ ] **C3-LAYER0-POLYGLOT.puml** — GraalVM runtimes
  - Python (graalpy) integration
  - JavaScript (graaljs) integration
  - WASM (graalwasm) integration
  - FFI boundaries
  - Performance implications

- [ ] **C3-LAYER0-RUST-FFI.puml** — yawl-rust4pm FFI layer
  - JNI binding patterns
  - Memory safety contracts
  - Error handling across boundary
  - Performance benchmarks
  - Security considerations

- [ ] **C3-LAYER0-DEPENDENCIES.puml** — Layer 0 internal dependencies
  - Inter-module dependencies
  - Import structure
  - Circular dependency prevention
  - Versioning strategy

### LAYER 1: First Consumers (5 items)
- [ ] **C3-LAYER1-ELEMENTS.puml** — yawl-elements domain model
  - YNet structure
  - Task types (manual, automatic, etc.)
  - Gateway types (AND, OR, XOR)
  - Data elements
  - Resource references

- [ ] **C3-LAYER1-GGEN.puml** — yawl-ggen code generation
  - Generation phases (H, Q, Ψ, Λ, Ω)
  - Template engine
  - Validation framework
  - Output artifacts
  - Configuration system

- [ ] **C3-LAYER1-DMN.puml** — yawl-dmn integration
  - DMN elements mapping
  - Decision table structure
  - Expression language support
  - Rule evaluation
  - Cache strategy

- [ ] **C3-LAYER1-DATA-MODELLING.puml** — yawl-data-modelling
  - Schema definition
  - Type system
  - Serialization formats
  - Validation rules
  - Evolution strategy

- [ ] **C3-LAYER1-WASM.puml** — yawl-graalwasm runtime
  - WASM module loading
  - Memory layout
  - Function export
  - Performance characteristics
  - Security model

### LAYER 2: Core Engine (4 items)
- [ ] **C3-LAYER2-YNETRUNNER.puml** — YNetRunner internals (expanded)
  - Net lifecycle states
  - Transition evaluation
  - Execution queue
  - Error handling
  - Performance optimization

- [ ] **C3-LAYER2-TASK-LIFECYCLE.puml** — Task state machine
  - State transitions
  - Guard conditions
  - Timeout handling
  - Cancellation paths
  - Completion variants

- [ ] **C3-LAYER2-VIRTUAL-THREADS.puml** — Java 25 virtual thread integration
  - VirtualThreadPerTaskExecutor
  - Scoped values
  - Carrier thread management
  - Parking/unparking strategies
  - Performance tuning

- [ ] **C3-LAYER2-PERSISTENCE.puml** — Database layer (expanded)
  - ORM mapping
  - Query optimization
  - Transaction management
  - Checkpoint strategy
  - Recovery procedures

### LAYER 3: Engine Extension (3 items)
- [ ] **C3-LAYER3-STATELESS.puml** — yawl-stateless architecture
  - Stateless execution model
  - Comparison with stateful
  - Use cases
  - State externalization
  - Performance characteristics

- [ ] **C3-LAYER3-MULTI-TENANT.puml** — Multi-tenancy support
  - Tenant isolation
  - Data separation
  - Quota management
  - Performance isolation
  - Shared resources

- [ ] **C3-LAYER3-COMPATIBILITY.puml** — Version compatibility
  - YAWL 5.x migration
  - API compatibility
  - Workflow format evolution
  - Deprecation timeline
  - Migration tools

### LAYER 4: Services (6 items)
- [ ] **C3-LAYER4-AUTHENTICATION.puml** — yawl-authentication service
  - OAuth flow
  - LDAP integration
  - JWT token management
  - Session handling
  - Token refresh strategy

- [ ] **C3-LAYER4-SCHEDULING.puml** — yawl-scheduling service
  - Task scheduling algorithm
  - Timer management
  - Cron expression parsing
  - Delay handling
  - SLA tracking

- [ ] **C3-LAYER4-MONITORING.puml** — yawl-monitoring service (expanded)
  - Metric collection
  - Trace sampling
  - Aggregation strategy
  - Alert rules
  - Dashboard definitions

- [ ] **C3-LAYER4-WORKLET.puml** — yawl-worklet service
  - Dynamic task invocation
  - Exception handler mapping
  - Sub-workflow execution
  - State synchronization
  - Timeout handling

- [ ] **C3-LAYER4-INTEGRATION-DETAIL.puml** — yawl-integration deep dive
  - MCP server details
  - A2A server details
  - Protocol codec
  - Error mapping
  - Performance optimization

- [ ] **C3-LAYER4-CONTROL-PANEL.puml** — yawl-control-panel UI architecture
  - Frontend component structure
  - REST API usage
  - State management
  - WebSocket integration
  - Real-time updates

### LAYER 5: Advanced Services (3 items)
- [ ] **C3-LAYER5-PI.puml** — yawl-pi portfolio management
  - Portfolio hierarchy
  - Theme allocation
  - Capacity planning
  - Risk assessment
  - Reporting structure

- [ ] **C3-LAYER5-RESOURCING.puml** — yawl-resourcing allocation engine
  - Resource pool model
  - Skill matching
  - Capacity optimization (Rust FFI)
  - Load balancing
  - Conflict resolution

- [ ] **C3-LAYER5-QLEVER.puml** — yawl-qlever query engine
  - Query DSL
  - Execution planning
  - Optimization techniques
  - RDF integration
  - Performance benchmarks

### LAYER 6: Application (1 item)
- [ ] **C3-LAYER6-MCP-A2A-APP.puml** — yawl-mcp-a2a-app orchestrator
  - Main entry point
  - Configuration loading
  - Service initialization
  - Shutdown procedures
  - Health checks

---

## PHASE 3: WORKFLOW EXECUTION PATTERNS (15 items)

### Basic Patterns
- [ ] **C4-PATTERN-SIMPLE-TASK.puml** — Simple task execution
  - Task creation
  - Assignment
  - Execution
  - Completion

- [ ] **C4-PATTERN-PARALLEL-TASKS.puml** — Parallel task execution
  - AND-split
  - Synchronization
  - Deadlock prevention
  - Performance

- [ ] **C4-PATTERN-CONDITIONAL.puml** — Conditional execution
  - XOR-split
  - Condition evaluation
  - Predicate logic
  - Edge cases

- [ ] **C4-PATTERN-LOOPS.puml** — Loop patterns
  - For-each loops
  - While loops
  - Do-while
  - Termination conditions

- [ ] **C4-PATTERN-EXCEPTION-HANDLING.puml** — Exception patterns
  - Exception routing
  - Recovery paths
  - Retry logic
  - Escalation

### Advanced Patterns
- [ ] **C4-PATTERN-WORKLET.puml** — Worklet invocation
  - Dynamic task assignment
  - Sub-workflow execution
  - State propagation
  - Error handling

- [ ] **C4-PATTERN-SUB-NETS.puml** — Sub-net composition
  - Sub-net structure
  - Parameter mapping
  - Scope management
  - Completion tracking

- [ ] **C4-PATTERN-MULTI-INSTANCE.puml** — Multi-instance patterns
  - MI task structure
  - Instance creation
  - Synchronization
  - Completion criteria

- [ ] **C4-PATTERN-RESOURCE-ALLOCATION.puml** — Resource-based assignment
  - Participant selection
  - Skill matching
  - Load balancing
  - Fallback strategies

- [ ] **C4-PATTERN-HUMAN-INTERACTION.puml** — Human task interaction
  - Form rendering
  - Data validation
  - Approval workflow
  - Escalation

### Integration Patterns
- [ ] **C4-PATTERN-SERVICE-INVOCATION.puml** — External service calls
  - Web service invocation
  - REST API calls
  - Error handling
  - Timeout management

- [ ] **C4-PATTERN-EVENT-CORRELATION.puml** — Event-based execution
  - Event waiting
  - Correlation
  - Timeout handling
  - Multiple event handling

- [ ] **C4-PATTERN-AGENT-INVOCATION.puml** — Agent execution
  - MCP agent calls
  - A2A agent calls
  - Result collection
  - Failure handling

- [ ] **C4-PATTERN-SAGA.puml** — Saga/compensation pattern
  - Transaction steps
  - Compensation steps
  - Failure scenarios
  - Idempotency

- [ ] **C4-PATTERN-MESSAGE-QUEUE.puml** — Message-driven workflow
  - Message listening
  - Queue integration
  - Priority handling
  - Dead letter queues

---

## PHASE 4: CROSS-CUTTING CONCERNS (15 items)

### Performance & Optimization
- [ ] **C4-CONCERN-PERFORMANCE.puml** — Performance optimization
  - Virtual thread strategy
  - Cache layers
  - Query optimization
  - Batch processing

- [ ] **C4-CONCERN-CACHING.puml** — Caching strategy
  - Cache levels (L1, L2, L3)
  - Invalidation strategy
  - TTL management
  - Cache warming

- [ ] **C4-CONCERN-BATCHING.puml** — Batch processing
  - Batch size strategy
  - Timeout handling
  - Error recovery
  - Performance tuning

### Reliability & Resilience
- [ ] **C4-CONCERN-RESILIENCE.puml** — Fault tolerance
  - Circuit breakers
  - Retry strategies
  - Bulkheads
  - Graceful degradation

- [ ] **C4-CONCERN-IDEMPOTENCY.puml** — Idempotent operations
  - Request deduplication
  - Idempotency keys
  - State management
  - Recovery

- [ ] **C4-CONCERN-DISTRIBUTED-CONSISTENCY.puml** — Consistency models
  - ACID guarantees
  - Eventual consistency
  - Distributed transactions
  - CAP theorem

### Scalability
- [ ] **C4-CONCERN-HORIZONTAL-SCALING.puml** — Multi-node deployment
  - State replication
  - Load distribution
  - Message coordination
  - Failover handling

- [ ] **C4-CONCERN-VERTICAL-SCALING.puml** — Single-node scaling
  - Resource utilization
  - Thread pool tuning
  - Memory management
  - CPU optimization

- [ ] **C4-CONCERN-QUEUE-BACKPRESSURE.puml** — Queue management
  - Queue depth monitoring
  - Backpressure handling
  - Consumer speed matching
  - Flow control

### Security
- [ ] **C4-CONCERN-AUTHENTICATION.puml** — User authentication
  - Auth flow
  - Token lifecycle
  - Session management
  - MFA integration

- [ ] **C4-CONCERN-AUTHORIZATION.puml** — Access control
  - Role-based access (RBAC)
  - Attribute-based access (ABAC)
  - Permission model
  - Audit logging

- [ ] **C4-CONCERN-ENCRYPTION.puml** — Data protection
  - Data at rest encryption
  - Data in transit encryption
  - Key management
  - Cryptographic algorithms

### Compliance & Governance
- [ ] **C4-CONCERN-COMPLIANCE.puml** — Regulatory compliance
  - SOX 404 requirements
  - GDPR obligations
  - Audit trails
  - Retention policies

- [ ] **C4-CONCERN-AUDIT-LOGGING.puml** — Audit and forensics
  - Audit trail structure
  - Event logging
  - Non-repudiation
  - Data retention

---

## PHASE 5: QUALITY & VALIDATION (12 items)

### Testing Architecture
- [ ] **C4-TEST-UNIT-ARCHITECTURE.puml** — Unit test structure
  - Test organization
  - Fixture management
  - Mock/stub usage
  - Assertion patterns

- [ ] **C4-TEST-INTEGRATION-ARCHITECTURE.puml** — Integration test structure
  - Test containers
  - Database setup
  - Service mocking
  - Test data

- [ ] **C4-TEST-E2E-ARCHITECTURE.puml** — End-to-end test structure
  - Test environment setup
  - Workflow execution
  - Assertion validation
  - Cleanup procedures

- [ ] **C4-TEST-FORTUNE5-ARCHITECTURE.puml** — Fortune 5 scale tests
  - Test orchestrator
  - Data factory
  - SLA enforcement
  - Performance metrics

### Code Quality
- [ ] **C4-QUALITY-GUARDS.puml** — H-Guards validation
  - Pattern detection (7 guards)
  - Violation reporting
  - Receipt generation
  - Gating mechanism

- [ ] **C4-QUALITY-INVARIANTS.puml** — Q-Invariants validation
  - Real implementation requirement
  - Exception throwing
  - Mock prevention
  - Assertion patterns

- [ ] **C4-QUALITY-STATIC-ANALYSIS.puml** — Static analysis
  - SpotBugs checks
  - PMD rules
  - Code coverage
  - Complexity metrics

- [ ] **C4-QUALITY-CODE-REVIEW.puml** — Code review process
  - Review criteria
  - Approval workflow
  - Checklist items
  - Conflict resolution

### Documentation
- [ ] **C4-DOCUMENTATION-ARCHITECTURE.puml** — Documentation strategy
  - Documentation types
  - Audience segmentation
  - Update procedures
  - Version control

- [ ] **C4-DOCUMENTATION-API.puml** — API documentation
  - OpenAPI/Swagger
  - Endpoint documentation
  - Example requests/responses
  - Error documentation

---

## PHASE 6: OPERATIONS & RUNBOOKS (12 items)

### Deployment & Operations
- [ ] **C4-OPS-DEPLOYMENT.puml** — Deployment procedures
  - Build pipeline
  - Testing gates
  - Staging validation
  - Production rollout

- [ ] **C4-OPS-CONFIGURATION.puml** — Configuration management
  - Environment variables
  - Configuration files
  - Secrets management
  - Hot reloading

- [ ] **C4-OPS-LOGGING.puml** — Logging architecture
  - Log levels
  - Log aggregation
  - Log analysis
  - Retention policies

- [ ] **C4-OPS-MONITORING.puml** — Monitoring procedures
  - Health checks
  - Alerting
  - Escalation
  - On-call procedures

### Troubleshooting
- [ ] **C4-TROUBLESHOOTING-PERFORMANCE.puml** — Performance debugging
  - Profiling tools
  - Bottleneck identification
  - Optimization techniques
  - Validation

- [ ] **C4-TROUBLESHOOTING-DEADLOCKS.puml** — Deadlock diagnosis
  - Detection methods
  - Root cause analysis
  - Prevention strategies
  - Recovery procedures

- [ ] **C4-TROUBLESHOOTING-MEMORY.puml** — Memory leak investigation
  - Heap dump analysis
  - GC behavior
  - Memory pressure
  - Remediation

- [ ] **C4-TROUBLESHOOTING-INTEGRATION.puml** — Integration debugging
  - Protocol debugging
  - Message tracing
  - Error investigation
  - Validation

### Capacity Planning
- [ ] **C4-CAPACITY-RESOURCE-PLANNING.puml** — Resource capacity
  - CPU requirements
  - Memory requirements
  - Disk requirements
  - Network bandwidth

- [ ] **C4-CAPACITY-SCALING-STRATEGY.puml** — Scaling decisions
  - Metrics for scaling
  - Timing of scaling
  - Scaling direction
  - Cost optimization

- [ ] **C4-CAPACITY-LOAD-TESTING.puml** — Load testing strategy
  - Test scenarios
  - Load profiles
  - Duration
  - Validation criteria

- [ ] **C4-CAPACITY-FORECASTING.puml** — Capacity forecasting
  - Growth projections
  - Resource trends
  - Budget planning
  - Upgrade timing

---

## PHASE 7: STRATEGIC & FUTURE (7 items)

### Product Roadmap
- [ ] **C4-ROADMAP-TIMELINE.puml** — Feature roadmap timeline
  - Current features
  - Planned features
  - Timeline
  - Dependencies

- [ ] **C4-ROADMAP-MIGRATION-PATH.puml** — Technology migration path
  - Current state
  - Target state
  - Transition phases
  - Risk mitigation

### Ecosystem Integration
- [ ] **C4-ECOSYSTEM-PARTNERS.puml** — Partner integrations
  - Strategic partners
  - Integration points
  - Data flows
  - Governance

- [ ] **C4-ECOSYSTEM-MARKETPLACE.puml** — Skills and agents marketplace
  - Marketplace architecture
  - Listing management
  - Discovery mechanism
  - Trust/rating system

- [ ] **C4-ECOSYSTEM-PLUGINS.puml** — Plugin architecture
  - Plugin types
  - Extension points
  - Lifecycle management
  - Isolation mechanisms

### Future Technologies
- [ ] **C4-FUTURE-AI-INTEGRATION.puml** — AI/ML integration vision
  - ML pipeline
  - Model serving
  - Feedback loops
  - Governance

- [ ] **C4-FUTURE-BLOCKCHAIN.puml** — Blockchain for audit trail
  - Blockchain usage
  - Smart contracts
  - Consensus mechanism
  - Privacy preservation

- [ ] **C4-FUTURE-QUANTUM.puml** — Quantum computing readiness
  - Quantum-safe cryptography
  - Hybrid classical-quantum
  - Timeline
  - Risk mitigation

---

## PHASE 8: INTEGRATION & INDEXING (5 items)

### Cross-References
- [ ] **INDEX-DIAGRAMS.md** — Master index of all diagrams
  - Diagram listing
  - Purpose of each
  - Relationships
  - Search/navigation

- [ ] **PATTERN-CATALOG.md** — Workflow pattern documentation
  - Pattern descriptions
  - Use cases
  - Anti-patterns
  - Examples

- [ ] **COMPONENT-CATALOG.md** — Component reference
  - Component listings
  - Responsibilities
  - Dependencies
  - Configuration

### Documentation Updates
- [ ] **UPDATE-ARCHITECTURE-PATTERNS.md** — Update architecture patterns doc
  - Link new diagrams
  - Add references
  - Update examples
  - Cross-links

- [ ] **UPDATE-IMPLEMENTATION-GUIDE.md** — Update implementation guide
  - Diagram references
  - Visual guides
  - Step-by-step procedures
  - Troubleshooting

---

## VERIFICATION CHECKLIST (5 items)

### Completeness
- [ ] All 100 diagrams created and documented
- [ ] Each diagram has clear purpose and use cases
- [ ] All diagrams cross-reference related diagrams
- [ ] README.md updated with master index
- [ ] All PlantUML syntax validated

### Quality
- [ ] Consistent naming conventions
- [ ] Consistent styling/colors
- [ ] Clear component boundaries
- [ ] Accurate relationships/dependencies
- [ ] Readable at all zoom levels

### Documentation
- [ ] Each diagram has context explanation
- [ ] Use cases documented
- [ ] Related diagrams linked
- [ ] Example scenarios provided
- [ ] Maintenance guidelines documented

### Integration
- [ ] Linked from main .claude/INDEX.md
- [ ] Linked from relevant rule files
- [ ] Referenced in architecture docs
- [ ] Available in multiple formats (PlantUML, PNG, SVG)
- [ ] Version controlled with timestamps

### Testing
- [ ] All PlantUML files compile without errors
- [ ] Diagrams render correctly
- [ ] Links are functional
- [ ] Naming is consistent across diagrams
- [ ] No orphaned diagrams

---

## PRIORITY RANKING

### CRITICAL (must complete)
1. Phase 1: Additional system diagrams (8)
2. Phase 2: Layer-by-layer deep dives (24)
3. Phase 3: Workflow execution patterns (15)
4. Phase 8: Integration & indexing (5)

### HIGH (should complete)
5. Phase 4: Cross-cutting concerns (15)
6. Phase 5: Quality & validation (12)
7. Phase 6: Operations & runbooks (12)

### MEDIUM (nice to have)
8. Phase 7: Strategic & future (7)

---

## EXECUTION STRATEGY

### Branch Management
- Branch: `claude/c4-diagrams-wip-1tk3Y`
- Commit strategy: Group logical diagrams per commit
- Examples:
  - Commit 1: Phase 1 system diagrams (C2-DEPLOYMENT, C2-NETWORK, C2-DATA-FLOW, etc.)
  - Commit 2: LAYER 0 deep dives (C3-LAYER0-*)
  - Commit 3: LAYER 1-6 deep dives (C3-LAYER1-* through C3-LAYER6-*)
  - Commit 4: Workflow patterns (C4-PATTERN-*)
  - Commit 5: Cross-cutting concerns (C4-CONCERN-*)
  - Commit 6: Quality & validation (C4-TEST-*, C4-QUALITY-*)
  - Commit 7: Operations (C4-OPS-*, C4-TROUBLESHOOTING-*, C4-CAPACITY-*)
  - Commit 8: Future & roadmap (C4-ROADMAP-*, C4-ECOSYSTEM-*, C4-FUTURE-*)
  - Commit 9: Integration & index (INDEX-*, PATTERN-*, COMPONENT-*)

### Quality Gates
- [ ] All PlantUML files validate without syntax errors
- [ ] All diagrams follow consistent naming convention
- [ ] All components have meaningful descriptions
- [ ] All relationships are labeled
- [ ] README updated for each new diagram set

### Documentation Standards
- [ ] Every diagram has: Title, purpose, use cases, related diagrams
- [ ] Component descriptions are 1-2 sentences max
- [ ] Relationships are clearly labeled with interaction type
- [ ] Color coding used consistently (if applicable)

---

## NOTES

- Total estimated items: 100
- Completed: 7 (7%)
- Remaining: 93 (93%)
- Focus: Maximum detail, comprehensive coverage, production-ready documentation
- Target audience: Architects, engineers, operators, stakeholders
- Update frequency: As architecture evolves

---

**Last Updated**: 2026-03-04
**Created By**: Claude Code
**Status**: IN PROGRESS
