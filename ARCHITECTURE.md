# YAWL Architecture Documentation

## Table of Contents

1. [Overview](#overview)
2. [System Components](#system-components)
3. [Architecture Diagram](#architecture-diagram)
4. [Core Services](#core-services)
5. [Data Flow](#data-flow)
6. [Database Schema](#database-schema)
7. [Service Interactions](#service-interactions)
8. [Deployment Architectures](#deployment-architectures)
9. [Scalability Design](#scalability-design)
10. [Security Architecture](#security-architecture)

## Overview

YAWL (Yet Another Workflow Language) is a comprehensive Business Process Management (BPM) and workflow system built on a service-oriented architecture. The system is designed to handle complex data transformations, provide native data handling using XML Schema/XPath/XQuery, and seamlessly integrate with organizational resources and external Web Services.

The architecture is modular and extensible, allowing organizations to customize and extend YAWL to meet their specific requirements. All components communicate through well-defined interfaces, enabling independent development and deployment of services.

### Key Architectural Principles

- **Service-Oriented Architecture (SOA)**: Each major component is a standalone service with defined interfaces
- **Loose Coupling**: Services interact through standard protocols (HTTP/REST, SOAP)
- **High Cohesion**: Services encapsulate related functionality
- **Scalability**: Horizontal scaling through containerization and orchestration
- **Extensibility**: Plugin architecture allows custom service development
- **Fault Isolation**: Failures in one service don't cascade to others

## System Components

### 1. YAWL Engine (Core)

The heart of the YAWL system, handling workflow execution and process instance management.

**Responsibilities:**
- Load and parse YAWL workflow specifications
- Manage case (process instance) lifecycle
- Execute activities and manage task queues
- Coordinate data flow between tasks
- Handle control flow and XOR/OR joins/splits
- Manage cancellation and completion of cases

**Technology Stack:**
- Java 11+
- Tomcat 9.0 application server
- XML processing (DOM/SAX/StAX)

### 2. Resource Service

Manages human and non-human resources within the workflow.

**Responsibilities:**
- User authentication and authorization
- Role and organizational unit management
- Resource allocation and scheduling
- Calendar management and availability tracking
- Work item assignment and queuing
- Integration with external directory systems (LDAP, Active Directory)

### 3. Monitoring Service

Provides real-time and historical workflow monitoring capabilities.

**Responsibilities:**
- Workflow execution tracking
- Performance metrics collection
- Bottleneck identification
- Compliance monitoring
- Audit trail generation
- Real-time dashboard updates

### 4. Worklet Service

Enables dynamic workflow configuration and evolution.

**Responsibilities:**
- Worklet management and configuration
- Dynamic process modification
- Exception handling and recovery procedures
- Runtime workflow adaptation
- Process variant management

### 5. Process Mining & Analytics

Integrates with ProM for post-execution analysis.

**Responsibilities:**
- Event log export (OpenXES format)
- Performance analysis
- Conformance checking
- Bottleneck discovery
- Process discovery support

### 6. Integration Services

Provides connectivity to external systems.

**Responsibilities:**
- Web Service invocation
- Custom application integration
- API gateway functionality
- Data transformation
- Message queue integration

### 7. Document Store

Manages attachments and files within workflows.

**Responsibilities:**
- File storage and retrieval
- Version control
- Access control per workflow context
- Integration with external storage systems

### 8. Mail & SMS Services

Enables external communication from workflows.

**Responsibilities:**
- SMTP/POP3 email handling
- SMS gateway integration
- Notification delivery
- Retry mechanisms

### 9. Reporter Service

Generates workflow reports and analytics.

**Responsibilities:**
- Custom report generation
- Statistical analysis
- Data export (PDF, Excel, CSV)
- Dashboarding

### 10. Database Layer

Persistent storage for all workflow data.

**Responsibilities:**
- Case and activity instance storage
- Process definition persistence
- Resource and calendar data
- Audit logging
- Configuration storage

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        External Systems                              │
├──────────────┬──────────────────┬──────────────────┬─────────────────┤
│ LDAP/AD      │  Web Services    │  Document Repos  │  Email/SMS APIs │
│ Directory    │  (SOAP/REST)     │  (S3, GCS, etc)  │  (SendGrid, etc)│
└──────────────┼──────────────────┼──────────────────┼─────────────────┘
               │                  │                  │
┌──────────────▼──────────────────▼──────────────────▼─────────────────┐
│                    API Gateway / Nginx Reverse Proxy                  │
├────────────────────────────────────────────────────────────────────┤
│  - Request routing                                                   │
│  - Authentication/Authorization                                    │
│  - Rate limiting & throttling                                      │
│  - Request/Response transformation                                 │
└────────────────────────────────┬───────────────────────────────────┘
                                 │
        ┌────────────────────────┼────────────────────────────┐
        │                        │                            │
┌───────▼──────────┐  ┌─────────▼──────────┐  ┌────────────▼────────┐
│  YAWL Engine     │  │ Resource Service   │  │  Monitoring Service │
│  (Port 8080)     │  │  (Port 8081)       │  │  (Port 8082)        │
├──────────────────┤  ├────────────────────┤  ├─────────────────────┤
│ - Case Mgmt      │  │ - User Mgmt        │  │ - Metrics           │
│ - Activity Exec  │  │ - Role Mgmt        │  │ - Dashboards        │
│ - Data Flow      │  │ - Scheduling       │  │ - Analytics         │
│ - Verification   │  │ - Allocation       │  │ - Audit Logs        │
└───────┬──────────┘  └────────┬───────────┘  └────────┬────────────┘
        │                       │                       │
        │  ┌────────────────────┼─────────────────────┐ │
        │  │                    │                     │ │
        └──┼────────────────────┼─────────────────────┼─┘
           │                    │                     │
        ┌──▼────────────────────▼─────────────────────▼──────┐
        │   Shared Cache Layer (Redis)                        │
        │   - Session cache                                   │
        │   - Distributed locks                              │
        │   - Transient data                                 │
        └──┬───────────────────────────────────────────────────┘
           │
        ┌──▼───────────────────────────────────────────────┐
        │   Database Layer (PostgreSQL/MySQL)              │
        │   ┌──────────────────────────────────────────┐   │
        │   │ - Process Definitions                    │   │
        │   │ - Case Instances                         │   │
        │   │ - Activity Instances                     │   │
        │   │ - Work Items                             │   │
        │   │ - Resource Data (Users, Roles, Orgs)     │   │
        │   │ - Calendar & Scheduling Data             │   │
        │   │ - Audit Logs                             │   │
        │   │ - Configuration                          │   │
        │   └──────────────────────────────────────────┘   │
        └────────────────────────────────────────────────────┘
           │
        ┌──▼───────────────────────────────────────────────┐
        │   File Storage Layer (Local/Cloud)               │
        │   - Process definition files                     │
        │   - Workflow attachments                         │
        │   - Export data (reports, logs)                  │
        └────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│           Supporting Services (Optional)                         │
├──────────────────────────────────────────────────────────────────┤
│ Worklet Service │ Document Store │ Mail Service │ Reporter Service│
│ Integration Svc │ SMS Service    │ Cost Service │ Digital Sig    │
└──────────────────────────────────────────────────────────────────┘
```

## Core Services

### YAWL Engine Architecture

The YAWL Engine is the central orchestration component that manages workflow execution:

```
┌─────────────────────────────────────────────┐
│      YAWL Engine (Tomcat Servlet)           │
├─────────────────────────────────────────────┤
│                                              │
│  ┌────────────────────────────────────────┐ │
│  │  REST API Layer                        │ │
│  │  (/resourceService/*, /engine/*)      │ │
│  └────────┬─────────────────────────────┘ │
│           │                               │
│  ┌────────▼─────────────────────────────┐ │
│  │  Business Logic Layer                │ │
│  │  ┌──────────────────────────────────┤ │
│  │  │ - Case Manager                   │ │
│  │  │ - Task Scheduler                 │ │
│  │  │ - Data Manager                   │ │
│  │  │ - Exception Handler              │ │
│  │  │ - Notification System            │ │
│  │  └──────────────────────────────────┤ │
│  │  ┌──────────────────────────────────┤ │
│  │  │ - Net Executor (Control Flow)    │ │
│  │  │ - Split/Join Handler             │ │
│  │  │ - XPath Evaluator                │ │
│  │  │ - Timer Manager                  │ │
│  │  │ - Resource Allocator             │ │
│  │  └──────────────────────────────────┤ │
│  └────────┬─────────────────────────────┘ │
│           │                               │
│  ┌────────▼─────────────────────────────┐ │
│  │  Persistence Layer (Hibernate ORM)   │ │
│  │  - Object mapping                   │ │
│  │  - Query management                 │ │
│  │  - Transaction handling             │ │
│  └────────┬─────────────────────────────┘ │
│           │                               │
│  ┌────────▼─────────────────────────────┐ │
│  │  Database Abstraction                │ │
│  │  (PostgreSQL, MySQL, Oracle)         │ │
│  └────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

### Workflow Execution Flow

```
1. Process Definition Upload
   └─> YAWL Specification (XML)
       └─> Parse & Validate (XSD Schema)
           └─> Store in Database
               └─> Notify Services

2. Case Initiation
   └─> Receive StartCase Request
       └─> Create Case Instance
           └─> Initialize Data Variables
               └─> Trigger Start Task
                   └─> Create Work Items

3. Task Execution
   └─> Work Item Assigned to Resource
       └─> Resource Executes Task
           └─> Submit Work Item Data
               └─> Validate Output Data
                   └─> Update Case Variables
                       └─> Evaluate Post-Conditions

4. Flow Navigation
   └─> Evaluate Condition (XPath)
       └─> Determine Outgoing Flows
           └─> Enable Transitions
               └─> Create Next Work Items
                   └─> Update Net State

5. Case Completion
   └─> All Paths Converge
       └─> Final Task Completes
           └─> Case Archived
               └─> Update Statistics
                   └─> Generate Audit Trail
```

## Data Flow

### Case Data Management

```
External Input Data
        │
        ▼
┌──────────────────────────┐
│  Work Item Submission    │
│  (Contains Output Data)  │
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────────────────┐
│  Data Validation                     │
│  - XML Schema validation             │
│  - XPath constraint checking         │
│  - Custom business rules             │
└──────────────┬───────────────────────┘
               │
        ┌──────┴──────┐
        │             │
    ✓ Valid      ✗ Invalid
        │             │
        ▼             ▼
    Merge into    Return Error
    Case Data     (re-execute task)
        │
        ▼
    ┌─────────────────────────┐
    │ Case Variable Update    │
    │ - Merge with existing   │
    │ - XQuery transformation │
    │ - Side effects          │
    └──────────┬──────────────┘
               │
               ▼
    ┌─────────────────────────┐
    │ Persist to Database     │
    │ - Transaction commit    │
    │ - Audit trail entry     │
    └──────────┬──────────────┘
               │
               ▼
    Available to Next Tasks
```

### External Service Integration

```
YAWL Engine
    │
    ├─> Web Service Task
    │   └─> HTTP Request (GET/POST/SOAP)
    │       └─> External Service
    │           └─> Process Response
    │               └─> Extract/Transform Data (XSLT/XPath)
    │                   └─> Return to Engine
    │
    └─> Custom Service
        └─> Java Class Invocation
            └─> Domain-specific Logic
                └─> Return Results
```

## Database Schema

### Primary Tables

```
┌─────────────────────────────────────────┐
│ yptask (Process Definitions)            │
├─────────────────────────────────────────┤
│ id                    PK                │
│ specversion           (2.0, 2.2, 4.0)   │
│ documentation         Workflow docs     │
│ specxml               Full XML content  │
│ name                  Specification ID  │
│ version               Version number    │
│ status                Active/Inactive   │
│ createtime            Timestamp         │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│ ypcase (Case Instances)                 │
├─────────────────────────────────────────┤
│ id                    PK                │
│ idspec                FK: yptask        │
│ caseid                Business case ID  │
│ status                Active/Complete   │
│ data                  XML case data     │
│ createtime            Start time       │
│ completiontime        End time         │
│ casedata              JSON/XML data    │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│ ypworkitem (Work Items/Tasks)           │
├─────────────────────────────────────────┤
│ id                    PK                │
│ idcase                FK: ypcase       │
│ taskid                Task identifier  │
│ status                Allocated/etc    │
│ allocated_to          Resource ID     │
│ created_time          Creation time   │
│ start_time            Execution start │
│ completion_time       Completion      │
│ data_input            Input XML data  │
│ data_output           Output XML data │
│ enabled_time          When enabled    │
│ fired_time            When executed   │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│ ypresource (Users/Resources)            │
├─────────────────────────────────────────┤
│ id                    PK                │
│ userid                User identifier  │
│ firstname             First name       │
│ lastname              Last name        │
│ password_hash         Hashed password  │
│ email                 Email address   │
│ status                Active/Inactive  │
│ updatetime            Last modified   │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│ ypauditlog (Audit Trail)                │
├─────────────────────────────────────────┤
│ id                    PK                │
│ timestamp             Event time      │
│ idcase                FK: ypcase      │
│ action                Action type     │
│ resource_id           Who did it      │
│ details               Event details   │
│ old_value             Previous state  │
│ new_value             New state       │
│ source_system         System origin   │
└─────────────────────────────────────────┘
```

## Service Interactions

### Request/Response Pattern

```
Client
  │
  ├─> POST /resourceService/workItemService
  │   ├─ Header: User credentials
  │   └─ Body: WorkItem data (XML)
  │
  └─> Nginx (Request routing)
      │
      ├─ Log request
      ├─ Authenticate
      ├─ Rate limit check
      │
      └─> YAWL Engine (Servlet handler)
          │
          ├─ Parse XML
          ├─ Validate business rules
          ├─ Acquire locks
          │
          ├─ Update database
          │   └─ Transaction (BEGIN..COMMIT)
          │
          ├─ Publish events
          │   └─ Message Queue (optional)
          │
          ├─ Update cache
          │   └─ Redis cache invalidation
          │
          └─ Response (XML/JSON)
              │
              └─> Client (Success or Error)
```

### Inter-Service Communication

Services communicate through:

1. **Direct REST Calls**: For synchronous operations
2. **Message Queues**: For asynchronous notifications
3. **Shared Database**: For eventual consistency
4. **Event Streaming**: For real-time analytics

Example: Resource Service allocation to YAWL Engine

```
Resource Service detects resource availability change
    │
    ├─> Update resource status in DB
    ├─> Publish ResourceAvailableEvent
    │
    └─> YAWL Engine subscribes to event
        ├─> Reevaluate pending task assignments
        ├─> Allocate tasks to available resources
        └─> Notify Resource Service of new assignments
```

## Deployment Architectures

### Single-Node Deployment

```
┌──────────────────────────────────┐
│   YAWL Container (Docker)        │
├──────────────────────────────────┤
│ YAWL Engine                      │
│ Resource Service                 │
│ All Supporting Services          │
│ + Redis (cache)                  │
└──────────────┬───────────────────┘
               │
       ┌───────▼────────┐
       │  PostgreSQL    │
       │  (Local)       │
       └────────────────┘
```

### Horizontal Scaling (Docker Compose)

```
Load Balancer (Nginx)
│
├─> YAWL Pod 1  ──┐
├─> YAWL Pod 2  ──┼─> Shared PostgreSQL
└─> YAWL Pod 3  ──┘   Shared Redis
```

### Kubernetes Deployment

```
┌─────────────────────────────────────────────┐
│          GKE Cluster / Kubernetes           │
├─────────────────────────────────────────────┤
│                                              │
│  ┌────────────────────────────────────────┐ │
│  │  Ingress Controller                    │ │
│  └────────────┬───────────────────────────┘ │
│               │                             │
│  ┌────────────▼───────────────────────────┐ │
│  │  Service (LoadBalancer)                │ │
│  │  - Session affinity: ClientIP          │ │
│  └────────────┬───────────────────────────┘ │
│               │                             │
│  ┌────────────┴───────────┬────────────┐    │
│  │                        │            │    │
│  ▼                        ▼            ▼    │
│ Pod 1                   Pod 2        Pod 3 │
│ ┌────────────┐       ┌────────────┐       │
│ │YAWL Engine │       │YAWL Engine │ ...   │
│ │Cloud SQL   │       │Cloud SQL   │       │
│ │  Proxy     │       │  Proxy     │       │
│ └────────────┘       └────────────┘       │
│                                            │
│  ┌────────────────────────────────────────┐ │
│  │  Persistent Storage (Cloud SQL)        │ │
│  │  - PostgreSQL 14                       │ │
│  │  - High availability                   │ │
│  │  - Automated backups                   │ │
│  └────────────────────────────────────────┘ │
│                                              │
│  ┌────────────────────────────────────────┐ │
│  │  Cache (Cloud Memorystore Redis)       │ │
│  │  - Session data                        │ │
│  │  - Rate limiting                       │ │
│  └────────────────────────────────────────┘ │
│                                              │
└─────────────────────────────────────────────┘
```

## Scalability Design

### Horizontal Scaling Strategy

**Stateless Design:**
- YAWL Engine instances are stateless
- All state persisted to database
- Session affinity via load balancer (optional)
- Enables any pod to handle any request

**Database Scaling:**
- Read replicas for increased query throughput
- Connection pooling (typically 20-50 per instance)
- Query optimization and indexing critical
- Vertical scaling when read replicas insufficient

**Cache Distribution:**
- Shared Redis cluster for distributed cache
- Consistent hashing for cache coherency
- TTL-based eviction for auto-cleanup
- Cache invalidation via pub/sub

### Performance Bottlenecks & Solutions

```
Bottleneck: Database Connection Exhaustion
├─ Symptom: "too many connections" errors
├─ Cause: Unclosed connections or pool misconfiguration
└─ Solution:
   ├─ Increase max_connections in PostgreSQL
   ├─ Reduce connection timeout
   ├─ Implement connection pooling (HikariCP)
   └─ Add read replicas for read-heavy workloads

Bottleneck: High Memory Usage
├─ Symptom: Pod OOMKilled or slow GC
├─ Cause: Large case data or memory leaks
└─ Solution:
   ├─ Increase heap size (JVM: -Xmx)
   ├─ Enable G1GC for better garbage collection
   ├─ Archive old cases
   └─ Profile with JProfiler or YourKit

Bottleneck: Network I/O
├─ Symptom: Slow external service integrations
├─ Cause: Unoptimized SOAP/REST calls
└─ Solution:
   ├─ Implement request pooling
   ├─ Add timeouts and retries
   ├─ Use async when possible
   └─ Cache external responses

Bottleneck: Disk I/O
├─ Symptom: Slow workflow execution
├─ Cause: Document uploads/downloads
└─ Solution:
   ├─ Use cloud storage (S3, GCS)
   ├─ Implement CDN for document distribution
   ├─ Compress documents
   └─ Async document processing
```

## Security Architecture

### Authentication & Authorization

```
┌─ Authentication Layer ─────────┐
│                                │
│ 1. LDAP/AD Integration         │
│    └─ Enterprise directory    │
│       └─ SSO support          │
│                                │
│ 2. Database Users             │
│    └─ Local credentials       │
│       └─ Password hashing     │
│                                │
│ 3. OAuth 2.0 / OIDC           │
│    └─ Third-party providers   │
│       └─ Token-based auth     │
└────────────┬────────────────┘
             │
             ▼
┌─ Authorization Layer ──────────┐
│                                │
│ Role-Based Access Control      │
│ ├─ System roles                │
│ │  └─ Admin, Operator, etc     │
│ ├─ Workflow roles              │
│ │  └─ Case owner, Approver     │
│ └─ Resource allocation         │
│    └─ Task-specific access     │
│                                │
│ Attribute-Based Access         │
│ ├─ Department filtering        │
│ ├─ Cost center restrictions    │
│ └─ Workflow-level permissions  │
└────────────┬────────────────┘
             │
             ▼
   YAWL Engine Authorization
```

### Data Encryption

- **In-transit**: TLS 1.2+ for all network communication
- **At-rest**: Encrypted database volumes, encrypted backups
- **Sensitive fields**: Hashed passwords, encrypted credentials
- **PII**: Masking in logs, separate audit trail

### Audit & Compliance

```
Every workflow action logged:
├─ Actor (user/system)
├─ Action (create/read/update/delete)
├─ Timestamp
├─ Resource (case/task/document)
├─ Old value (before change)
├─ New value (after change)
└─ Source system (API call, UI, service)

Audit log:
├─ Immutable (write-once)
├─ Tamper detection
├─ Long-term retention
├─ Export to SIEM systems
└─ Compliance reporting (SOX, GDPR, etc.)
```

### Network Security

```
┌─ VPC/Private Network ──────────────────┐
│                                         │
│ ┌─ Pod-to-Pod Communication ─────────┐ │
│ │ - Network policies                 │ │
│ │ - Service mesh (Istio optional)    │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ ┌─ External Ingress ──────────────────┐ │
│ │ - TLS termination                  │ │
│ │ - WAF rules                        │ │
│ │ - DDoS protection                  │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ ┌─ Database Access ───────────────────┐ │
│ │ - Private Cloud SQL (not public)   │ │
│ │ - Connection via proxy             │ │
│ │ - SSL/TLS required                 │ │
│ └─────────────────────────────────────┘ │
│                                         │
└─────────────────────────────────────────┘
```

## Conclusion

YAWL's architecture is designed to be:
- **Modular**: Each service independently deployable
- **Scalable**: Horizontal scaling through stateless design
- **Resilient**: Fault isolation and recovery mechanisms
- **Extensible**: Plugin architecture for custom services
- **Secure**: Multi-layered security controls
- **Observable**: Comprehensive monitoring and audit trails

The SOA approach allows organizations to start with core components and gradually add services as requirements evolve, making YAWL suitable for deployments ranging from small projects to enterprise-scale implementations.

---

**Document Version**: 1.0
**Last Updated**: 2026-02-14
**Maintained By**: YAWL Foundation
