# Van Der Aalst Pattern Demo Setup and Execution

## Overview

The Van Der Aalst Pattern Demo for YAWL v6.0 is a comprehensive demonstration of workflow patterns based on the seminal work by Wil van der Aalst and colleagues. This demo showcases the implementation of 43+ workflow patterns in YAWL, ranging from basic control flow to advanced AI/ML integration patterns.

## Pattern Categories

### 1. Basic Control Flow Patterns (WCP 1-5)
- **WCP-1**: Sequence - Tasks executed in sequential order
- **WCP-2**: Parallel Split - Split execution into parallel branches
- **WCP-3**: Synchronization - Synchronize parallel branches
- **WCP-4**: Exclusive Choice - Choose one branch based on condition
- **WCP-5**: Simple Merge - Merge two alternative branches without sync

### 2. Advanced Branching Patterns (WCP 6-11)
- **WCP-6**: Multi-Choice - Choose multiple branches based on conditions
- **WCP-7**: Structured Synchronizing Merge - Synchronize multiple activated branches
- **WCP-8**: Multi-Merge - Merge without synchronization
- **WCP-9**: Structured Discriminator - Discard all but first completion
- **WCP-10**: Structured Loop - Loop with explicit structure
- **WCP-11**: Implicit Termination - Case terminates when no active work

### 3. Multi-Instance Patterns (WCP 12-17)
- **WCP-12**: MI Without Synchronization - Multiple instances without sync
- **WCP-13**: MI With A Priori Design Time Knowledge - Fixed number of instances
- **WCP-14**: MI With A Priori Runtime Knowledge - Dynamic instance count at runtime
- **WCP-15**: MI Without A Priori Runtime Knowledge - Instance count determined during execution
- **WCP-16**: MI Without A Priori Knowledge - Instance count unknown at design and runtime
- **WCP-17**: Interleaved Parallel Routing - Parallel tasks executed one at a time

### 4. State-Based Patterns (WCP 18-21)
- **WCP-18**: Deferred Choice - Choice deferred until one branch executes
- **WCP-19**: Milestone - Task enabled only when milestone reached
- **WCP-20**: Cancel Activity - Cancel a specific activity
- **WCP-21**: Cancel Case - Cancel entire case

### 5. Extended Patterns (WCP 44)
- **WCP-44**: Saga Pattern - Long-running transaction with compensation

### 6. Distributed Patterns (WCP 45-50)
- **WCP-45**: Saga Choreography - Saga pattern with choreography
- **WCP-46**: Two-Phase Commit - Distributed transaction with prepare/commit
- **WCP-47**: Circuit Breaker - Protect against cascading failures
- **WCP-48**: Retry - Retry failed operations
- **WCP-49**: Bulkhead - Isolate failures to prevent cascade
- **WCP-50**: Timeout - Timeout pattern for long operations

### 7. Event-Driven Patterns (WCP 51-59)
- **WCP-51**: Event Gateway - Event-based workflow gateway
- **WCP-52**: Outbox - Reliable event publishing with outbox pattern
- **WCP-53**: Scatter-Gather - Distribute work and collect results
- **WCP-54**: Event Router - Route events to appropriate handlers
- **WCP-55**: Event Stream - Process events from event stream
- **WCP-56**: CQRS - Command Query Responsibility Segregation
- **WCP-57**: Event Sourcing - Store state as sequence of events
- **WCP-58**: Compensating Transaction - Undo operations on failure
- **WCP-59**: Side-by-Side - Run new version alongside old for validation

### 8. AI/ML Integration Patterns (WCP 60-68)
- **WCP-60**: Rules Engine - Rule-based decision engine
- **WCP-61**: ML Model - Machine learning model inference
- **WCP-62**: Human-AI Handoff - Seamless transition between AI and human
- **WCP-63**: Model Fallback - Fallback to alternative model on failure
- **WCP-64**: Confidence Threshold - Route based on model confidence
- **WCP-65**: Feature Store - Centralized feature management
- **WCP-66**: Pipeline - ML pipeline orchestration
- **WCP-67**: Drift Detection - Detect model drift over time
- **WCP-68**: Auto-Retrain - Automatic model retraining trigger

### 9. Enterprise Patterns (ENT-1-8)
- **ENT-1**: Sequential Approval - Multi-level sequential approval workflow
- **ENT-2**: Parallel Approval - Multi-level parallel approval workflow
- **ENT-3**: Escalation - Escalation on timeout
- **ENT-4**: Compensation - Compensation pattern for rollback
- **ENT-5**: SLA Monitoring - Service level agreement monitoring
- **ENT-6**: Delegation - Task delegation pattern
- **ENT-7**: Four Eyes - Two-person approval requirement
- **ENT-8**: Nomination - Nomination-based task assignment

### 10. Agent Patterns (AGT-1-5)
- **AGT-1**: Agent Assisted - Human works with AI agent assistance
- **AGT-2**: LLM Decision - Decision made by LLM-based agent
- **AGT-3**: Human-Agent Handoff - Handoff between human and agent
- **AGT-4**: Agent-to-Agent Handoff - Transfer work between specialized agents
- **AGT-5**: Multi-Agent Orchestration - Coordinate multiple agents

### Additional Patterns
The demo also includes numerous advanced patterns in categories like:
- Cancellation Patterns (Cancel Task, Cancel Case, Cancel Region)
- Iteration Patterns (Structured Loop with cancellation)
- Discriminator Variants
- Trigger Patterns (Local, Global, Reset)

## CLI Options

### Command Syntax
```bash
java -jar yawl-pattern-demo.jar [options]
```

### Available Options

| Option | Short Form | Description | Default |
|--------|------------|-------------|---------|
| `--format` | `-f` | Output format (console, json, markdown, html) | console |
| `--output` | `-o` | Output file path (default: report) | report |
| `--timeout` | `-t` | Execution timeout per pattern in seconds | 300 |
| `--no-tracing` | | Disable execution tracing | enabled |
| `--no-metrics` | | Disable metrics collection | enabled |
| `--no-auto-complete` | | Disable auto-completion of work items | enabled |
| `--no-parallel` | | Disable parallel pattern execution | enabled |
| `--no-token-analysis` | | Disable token savings analysis | enabled |
| `--commentary` | | Include detailed commentary | disabled |
| `--patterns` | `-p` | Comma-separated pattern IDs to run | all |
| `--categories` | `-c` | Comma-separated categories to run | all |
| `--help` | `-h` | Show this help message | |

### Category Filters
Available categories include:
- `BASIC` - Basic Control Flow patterns
- `BRANCHING` - Branching and Synchronization patterns
- `MULTI_INSTANCE` - Multi-Instance patterns
- `STATE_BASED` - State-Based patterns
- `DISTRIBUTED` - Distributed workflow patterns
- `EVENT_DRIVEN` - Event-Driven patterns
- `AI_ML` - AI/ML Integration patterns
- `ENTERPRISE` - Enterprise patterns
- `AGENT` - Agent patterns
- `GREGVERSE_SCENARIO` - GregVerse Scenario patterns
- And more...

## Build Instructions

### Prerequisites
- Java 21 or later
- Maven 3.8 or later

### Build Steps

```bash
# Navigate to the project root
cd /Users/sac/cre/vendors/yawl

# Build the entire project
mvn clean compile

# Build the specific demo module
mvn clean package -pl yawl-mcp-a2a-app -DskipTests=false

# Or build with all dependencies
mvn clean package -DskipTests=false
```

**Note**: The demo requires Java 21+ with preview features enabled:
```bash
# Run with Java 25 and preview features
java --enable-preview -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --help
```

### Run the Demo

#### 1. Run All Patterns
```bash
# Run all patterns with console output
java -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar

# Or with Maven
mvn exec:java -pl yawl-mcp-a2a-app -Dexec.mainClass="org.yawlfoundation.yawl.mcp.a2a.demo.PatternDemoRunner"
```

#### 2. Run Specific Patterns
```bash
# Run specific pattern IDs
java -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --patterns WCP-1,WCP-2,WCP-3

# Run with JSON output
java -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --patterns WCP-1 --format json
```

#### 3. Run by Category
```bash
# Run all basic patterns
java -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --categories BASIC

# Run multiple categories
java -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --categories BASIC,BRANCHING
```

#### 4. Advanced Options
```bash
# HTML report with commentary
java -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --format html --commentary --output patterns-report

# Fast execution (60s timeout, sequential)
java -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --timeout 60 --no-parallel

# Include token analysis
java -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --token-report
```

## Example Output

### Console Output
```
======================================================================
            YAWL Pattern Demo v6.0.0
            Yet Another Workflow Language
======================================================================

Running 3 patterns with virtual threads...

[1/3] WCP-1... OK (45ms)
[2/3] WCP-2... OK (67ms)
[3/3] WCP-3... OK (23ms)

======================================================================
Complete. 3/3 patterns successful.
Token Analysis:
  YAML tokens: 1,234
  XML tokens: 2,456
  Savings:     49.8%

Total duration: 135ms
======================================================================
```

### Live Demo Commands

```bash
# Run a quick demo with basic patterns
java --enable-preview -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --patterns WCP-1,WCP-2,WCP-3 --timeout 10

# Generate HTML report for all basic patterns
java --enable-preview -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --categories BASIC --format html --output basic-patterns-report

# Run with commentary and detailed output
java --enable-preview -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --patterns WCP-1 --commentary --with-tracing
```

### JSON Output
```json
{
  "version": "6.0.0",
  "execution": {
    "startTime": "2026-02-22T14:30:00Z",
    "duration": "PT0.135S",
    "patternsExecuted": 3,
    "successfulPatterns": 3,
    "failedPatterns": 0
  },
  "patterns": [
    {
      "id": "WCP-1",
      "name": "Sequence",
      "category": "Basic Control Flow",
      "success": true,
      "duration": "PT0.045S",
      "metrics": {
        "workItems": 3,
        "events": 2,
        "tokenCount": {
          "yaml": 156,
          "xml": 234
        }
      }
    },
    {
      "id": "WCP-2",
      "name": "Parallel Split",
      "category": "Basic Control Flow",
      "success": true,
      "duration": "PT0.067S",
      "metrics": {
        "workItems": 4,
        "events": 3,
        "tokenCount": {
          "yaml": 189,
          "xml": 312
        }
      }
    }
  ]
}
```

### JSON Output
```json
{
  "version": "6.0.0",
  "execution": {
    "startTime": "2026-02-22T14:30:00Z",
    "duration": "PT0.344S",
    "patternsExecuted": 5,
    "successfulPatterns": 4,
    "failedPatterns": 1
  },
  "patterns": [
    {
      "id": "WCP-1",
      "name": "Sequence",
      "category": "Basic Control Flow",
      "success": true,
      "duration": "PT0.045S",
      "metrics": {
        "workItems": 3,
        "events": 2,
        "tokenCount": {
          "yaml": 156,
          "xml": 234
        }
      }
    },
    {
      "id": "WCP-5",
      "name": "Simple Merge",
      "category": "Basic Control Flow",
      "success": false,
      "error": "Timed out after 300s",
      "duration": "PT0.120S"
    }
  ]
}
```

### HTML Report
The HTML report includes:
- Executive summary with success/failure statistics
- Interactive pattern table with sortable columns
- Pattern details with workflow diagrams
- Performance metrics and token analysis
- Execution traces and events

## Pattern File Structure

Each pattern is defined as a YAML file with the following structure:

```yaml
name: PatternName
uri: pattern-uri.xml
first: FirstTask

variables:
  - name: varName
    type: xs:string
    default: "defaultValue"

tasks:
  - id: FirstTask
    flows: [NextTask]
    split: xor
    join: and
    description: "Task description"
```

### Example Pattern Files

#### WCP-1: Sequence Pattern (`controlflow/wcp-1-sequence.yaml`)
```yaml
# WCP-1: Sequence Pattern
# Basic sequence of tasks that must be executed in order

name: SequencePattern
uri: sequence.xml
first: TaskA

variables:
  - name: orderId
    type: xs:string
  - name: status
    type: xs:string
    default: "started"

tasks:
  - id: TaskA
    flows: [TaskB]
    split: xor
    join: and
    description: "Initialize order"

  - id: TaskB
    flows: [TaskC]
    split: xor
    join: and
    description: "Process payment"

  - id: TaskC
    flows: [end]
    split: xor
    join: and
    description: "Complete order"
```

#### WCP-2: Parallel Split Pattern (`controlflow/wcp-2-parallel-split.yaml`)
```yaml
# WCP-2: Parallel Split Pattern
# Split execution into parallel branches

name: ParallelSplitPattern
uri: parallel-split.xml
first: TaskA

variables:
  - name: orderId
    type: xs:string

tasks:
  - id: TaskA
    flows: [TaskB, TaskC]
    split: and
    join: and
    description: "Start order processing"

  - id: TaskB
    flows: [TaskD]
    split: xor
    join: and
    description: "Process payment"

  - id: TaskC
    flows: [TaskD]
    split: xor
    join: and
    description: "Verify inventory"

  - id: TaskD
    flows: [end]
    split: xor
    join: and
    description: "Complete order"
```

#### WCP-44: Saga Pattern (Extended) (`extended/wcp-44-saga.yaml`)
```yaml
# WCP-44: Saga Pattern
# Long-running transaction with compensation

name: SagaPattern
uri: saga.xml
first: BeginTransaction

variables:
  - name: transactionId
    type: xs:string
  - name: status
    type: xs:string
    default: "started"
  - name: compensationEvents
    type: xs:string
    default: ""

tasks:
  - id: BeginTransaction
    flows: [TaskA]
    split: xor
    join: and
    description: "Start saga transaction"

  - id: TaskA
    flows: [TaskB]
    split: xor
    join: and
    description: "Execute service A"
    # Compensation handler registered
    compensation: compensateA

  - id: TaskB
    flows: [TaskC]
    split: xor
    join: and
    description: "Execute service B"
    compensation: compensateB

  - id: TaskC
    flows: [CompleteSaga]
    split: xor
    join: and
    description: "Execute service C"
    compensation: compensateC

  - id: CompleteSaga
    flows: [end]
    split: xor
    join: and
    description: "Complete saga successfully"

  - id: compensateA
    flows: []
    split: xor
    join: and
    description: "Compensate service A"
    isCompensation: true

  - id: compensateB
    flows: []
    split: xor
    join: and
    description: "Compensate service B"
    isCompensation: true

  - id: compensateC
    flows: []
    split: xor
    join: and
    description: "Compensate service C"
    isCompensation: true
```

## Performance Features

### Parallel Execution
- Uses Java 21 virtual threads for concurrent execution
- Efficient handling of 100+ patterns simultaneously
- Resource-efficient with minimal overhead

### Metrics Collection
- Execution timing per pattern
- Work item and event counts
- Memory usage tracking
- Token analysis (YAML vs XML size comparison)

### Tracing
- Detailed execution trace for each pattern
- Event logging with timestamps
- State transition tracking

## Troubleshooting

### Common Issues

1. **Out of Memory**
   ```bash
   # Increase JVM memory
   java -Xmx2g -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar
   ```

2. **Pattern Not Found**
   ```bash
   # Get help with pattern IDs
   java -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --help

   # Check available patterns
   java -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar --categories ALL
   ```

3. **Build Issues**
   ```bash
   # Clean and rebuild
   mvn clean compile
   mvn dependency:resolve
   ```

### Debug Mode
```bash
# Enable verbose logging
java -Dlogging.level=DEBUG -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Beta.jar
```

## References

- van der Aalst, W.M.P., ter Hofstede, A.H.M., Kiepuszewski, B., & Barros, A.P. (2003). Workflow Patterns. Distributed and Parallel Databases, 14(1), 5-51.
- Wil van der Aalst's Workflow Patterns Initiative: https://www.workflowpatterns.com
- YAWL Foundation: https://www.yawlfoundation.org

---

**Document Version**: 1.0
**Last Updated**: 2026-02-22
**YAWL Version**: 6.0.0-Beta