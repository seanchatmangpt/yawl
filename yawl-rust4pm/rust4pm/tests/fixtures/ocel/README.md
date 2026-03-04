# OCEL Test Fixtures for YAWL JTBD Testing

This directory contains real-world inspired OCEL test fixtures adapted for YAWL workflow testing. Each fixture represents a realistic business scenario with varying complexity.

## Directory Structure

```
ocel/
├── README.md                          # This file
├── jtbd1-order-processing/            # Simple order processing workflow
├── jtbd2-loan-approval/              # Complex routing with splits
├── jtbd3-resource-allocation/        # Resource conflicts and allocation
├── jtbd4-customer-support/           # Exception handling and retries
└── jtbd5-invoice-verification/       # Multi-case correlation
```

## Fixture Structure

Each JTBD fixture contains:
- `scenario.json` - Detailed scenario description
- `workflow.yml` - Corresponding YAWL workflow definition
- `events.ocel.json` - OCEL 2.0 event log
- `expected.json` - Expected execution results
- `metadata.json` - Dataset metadata and validation rules

## OCEL Format

All event logs follow OCEL 2.0 standard with:
- `events` - Array of event objects with timestamp, activity, case
- `objects` - Business objects (orders, invoices, etc.)
- `attributes` - Additional event and object attributes
- `relations` - Links between events and objects

## Usage

```java
// Load OCEL fixture
OcelReader reader = new OcelReader();
OcelLog log = reader.read(new File("test/fixtures/ocel/jtbd1-order-processing/events.ocel.json"));

// Execute with YAWL
YStatelessEngine engine = new YStatelessEngine();
YSpecification spec = readYAWLWorkflow("test/fixtures/ocel/jtbd1-order-processing/workflow.yml");
ExecutionResult result = engine.execute(spec, log);
```

## Validation

Each fixture includes automated validation in `expected.json`:
- Completion status
- Duration statistics
- Resource utilization
- Exception counts
- Performance benchmarks

## Scenarios

### JTBD 1: Order Processing (Basic)
- Simple linear workflow
- Order → Payment → Shipping → Complete
- Minimal routing logic
- Single resource per task

### JTBD 2: Loan Approval (Complex Routing)
- Multi-level approval hierarchy
- Automatic/Manual routing based on amount
- Parallel subprocesses
- Resource assignment rules

### JTBD 3: Resource Allocation (Conflicts)
- Shared resource pools
- Priority-based scheduling
- Conflict resolution policies
- Resource availability tracking

### JTBD 4: Customer Support (Exceptions)
- Exception handling and retries
- Escalation paths
- Timeout management
- Multi-channel support

### JTBD 5: Invoice Verification (Multi-Case)
- Cross-case dependencies
- Sibling case relationships
- Bulk processing
- Correlation patterns

## Real Data Sources

Based on:
- BPI Challenge 2019 (Loan Application)
- BPI Challenge 2020 (Hospital)
- OCEL Standard Examples
- Real-world business processes