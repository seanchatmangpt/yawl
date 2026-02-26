# YAWL Getting Started Guide

**Version:** 6.0.0-GA
**Last Updated:** 2026-02-25

---

## 1. Introduction

Welcome to YAWL (Yet Another Workflow Language), a powerful BPM/Workflow engine for enterprise process automation. This guide will help you get started quickly.

### 1.1 What is YAWL?

YAWL is a workflow management system based on Petri Nets, offering:
- Formal workflow specification language
- Support for 43+ workflow patterns
- Dynamic workflow adaptation via Worklets
- Human and automated task management
- Integration with web services and external systems
- **v6.0.0-GA Features**: GRPO/RL (Generate, Run, Optimize/Reinforce Learning), MCP/A2A server for autonomous agents, OpenSage integration, and Java 25 virtual thread optimization

### 1.2 Key Concepts

| Concept | Description |
|---------|-------------|
| **Specification** | Definition of a workflow process |
| **Net** | A workflow diagram with tasks and conditions |
| **Task** | A unit of work in a workflow |
| **Condition** | A decision point in a workflow |
| **Case** | An instance of a running specification |
| **Work Item** | A task instance assigned to a resource |

---

## 2. Quick Start

### 2.1 Access YAWL

After deployment, access YAWL at:

```
https://yawl.yourdomain.com
```

> **v6.0.0-GA Build Instructions**:
> For new installations, ensure Java 25+ is installed and use:
> ```bash
> mvn clean install -T 1.5C -P ga-release
> ```

Default credentials (change immediately):
- **Username**: admin
- **Password**: AdminPassword123

### 2.2 First Login

1. Navigate to the YAWL URL
2. Enter your credentials
3. You'll be prompted to change the password
4. Complete the setup wizard

### 2.3 Dashboard Overview

```
+----------------------------------------------------------+
|  YAWL Dashboard                                          |
+----------------------------------------------------------+
|  [Specifications] [Cases] [Work Items] [Admin]          |
+----------------------------------------------------------+
|                                                          |
|  Active Cases: 45        Pending Work Items: 23          |
|  Completed Today: 12     Overdue Items: 3               |
|                                                          |
+----------------------------------------------------------+
```

---

## 3. Creating Your First Workflow

### 3.1 Using the Process Editor

1. Navigate to **Specifications** > **Create New**
2. Use the visual editor to design your workflow:
   - Drag tasks and conditions onto the canvas
   - Connect elements with flows
   - Configure task properties

### 3.2 Simple Workflow Example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specification xmlns="http://www.yawlfoundation.org/yawlschema">
  <metaData>
    <identifier>SimpleApproval</identifier>
    <version>1.0</version>
    <description>Simple approval workflow</description>
  </metaData>

  <decomposition id="SimpleApproval" isRootNet="true">
    <processControlElements>
      <inputCondition id="start" name="Start">
        <flowsInto>
          <nextElementRef id="submit" />
        </flowsInto>
      </inputCondition>

      <task id="submit" name="Submit Request">
        <flowsInto>
          <nextElementRef id="approve" />
        </flowsInto>
      </task>

      <task id="approve" name="Approve Request">
        <flowsInto>
          <nextElementRef id="end" />
        </flowsInto>
      </task>

      <outputCondition id="end" name="End" />
    </processControlElements>
  </decomposition>
</specification>
```

### 3.3 Upload Specification

1. Go to **Specifications** > **Upload**
2. Select your XML file
3. Click **Upload**
4. Verify the specification loads without errors

---

## 4. Launching a Case

### 4.1 Start a Case

1. Navigate to **Specifications**
2. Find your specification
3. Click **Launch Case**
4. Provide any required input data
5. Click **Start**

### 4.2 Monitor Case Progress

```bash
# Via API
curl -X GET "https://yawl.yourdomain.com/ib/api/cases/{caseId}" \
  -H "Authorization: Bearer $TOKEN"
```

### 4.3 Case States

| State | Description |
|-------|-------------|
| **Running** | Case is actively executing |
| **Suspended** | Case is paused |
| **Completed** | Case finished successfully |
| **Cancelled** | Case was terminated |
| **Failed** | Case encountered an error |

---

## 5. Working with Work Items

### 5.1 View Work Items

1. Navigate to **Work Items**
2. Filter by status, priority, or assignment
3. Click on a work item to view details

### 5.2 Complete a Work Item

1. Select a work item
2. Review task description and data
3. Enter required output data
4. Click **Complete**

### 5.3 Work Item States

| State | Description |
|-------|-------------|
| **Enabled** | Ready to be started |
| **Executing** | Currently being worked on |
| **Complete** | Finished successfully |
| **Failed** | Execution failed |
| **Cancelled** | Cancelled by system or user |

---

## 6. Resource Management

### 6.1 Participants

YAWL supports human and non-human resources:

```xml
<resources>
  <participant id="manager1">
    <name>John Manager</name>
    <description>Department Manager</description>
  </participant>

  <participant id="finance_service">
    <name>Finance Service</name>
    <type>System</type>
  </participant>
</resources>
```

### 6.2 Roles

```xml
<role id="approvers">
  <name>Approver Role</name>
  <offers>
    <participant>manager1</participant>
    <participant>manager2</participant>
  </offers>
</role>
```

### 6.3 Task Allocation

```xml
<task id="approve">
  <resourcing>
    <offer>
      <role>approvers</role>
    </offer>
    <allocate>
      <fifo/>  <!-- First in, first out -->
    </allocate>
  </resourcing>
</task>
```

---

## 7. Data Handling

### 7.1 Input Parameters

```xml
<task id="submit">
  <inputParam name="requestType">
    <type>string</type>
    <required>true</required>
  </inputParam>
  <inputParam name="amount">
    <type>decimal</type>
    <required>true</required>
  </inputParam>
</task>
```

### 7.2 Output Parameters

```xml
<task id="approve">
  <outputParam name="approved">
    <type>boolean</type>
  </outputParam>
  <outputParam name="comments">
    <type>string</type>
  </outputParam>
</task>
```

### 7.3 Data Transformations

```xml
<task id="transform">
  <mapping>
    <expression language="XQuery">
      {{
        let $total := //item/price/sum(.)
        return
          <totalAmount>{$total}</totalAmount>
      }}
    </expression>
  </mapping>
</task>
```

---

## 8. Integration

### 8.1 Web Service Tasks

```xml
<task id="checkCredit">
  <externalInteraction>
    <webService>
      <operation>checkCreditScore</operation>
      <wsdl>https://api.example.com/credit?wsdl</wsdl>
    </webService>
  </externalInteraction>
</task>
```

### 8.2 REST API Integration

```xml
<task id="callAPI">
  <externalInteraction>
    <restService>
      <method>POST</method>
      <url>https://api.example.com/orders</url>
      <headers>
        <header name="Authorization">Bearer ${apiToken}</header>
      </headers>
    </restService>
  </externalInteraction>
</task>
```

---

## 9. Monitoring

### 9.1 Process Monitoring

Access the monitoring dashboard at:

```
https://yawl.yourdomain.com/monitor
```

### 9.2 Key Metrics

| Metric | Description |
|--------|-------------|
| Cases Started | Total cases launched |
| Cases Completed | Successfully finished cases |
| Average Duration | Mean case completion time |
| Work Items Pending | Items awaiting action |
| Service Level | % meeting SLA |

### 9.3 Logs

```bash
# Access logs via API
curl -X GET "https://yawl.yourdomain.com/logGateway/cases/{caseId}/events" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 10. API Reference

### 10.1 Authentication

```bash
# Get access token
curl -X POST "https://yawl.yourdomain.com/ib/api/authenticate" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'
```

### 10.2 Autonomous Agent Integration (v6.0.0-GA)

YAWL v6.0.0-GA includes MCP/A2A server capabilities for autonomous agent integration:

```bash
# Start MCP server for autonomous agents
java -cp yawl-integration.jar -Dmcp.server.port=8001 org.yawlfoundation.yawl.integration.mcp.MCPServer

# Connect OpenSage agent
agent connect --type yawl --endpoint http://localhost:8001 --case-id {caseId}
```

### 10.3 GRPO/RL Quick Example

```java
// Example using yawl-ggen for workflow generation and optimization
import org.yawlfoundation.yawl.ggen.*;

public class GRPORLExample {
    public void generateAndOptimize() {
        // Generate workflow pattern
        WorkflowPatternGenerator generator = new WorkflowPatternGenerator();
        YAWLNet workflow = generator.generatePattern("cancellation_with_retry");

        // Run simulation
        WorkflowSimulator simulator = new WorkflowSimulator(workflow);
        PerformanceMetrics metrics = simulator.runSimulation();

        // Apply reinforcement learning optimization
        WorkflowOptimizer optimizer = new WorkflowOptimizer();
        YAWLNet optimized = optimizer.optimize(workflow, metrics);
    }
}
```

### 10.2 Common Operations

```bash
# Launch a case
curl -X POST "https://yawl.yourdomain.com/ib/api/cases" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "specIdentifier": "SimpleApproval",
    "specVersion": "1.0",
    "caseParams": {
      "requestType": "expense",
      "amount": 500.00
    }
  }'

# Get work items
curl -X GET "https://yawl.yourdomain.com/ib/api/workitems" \
  -H "Authorization: Bearer $TOKEN"

# Complete work item
curl -X PUT "https://yawl.yourdomain.com/ib/api/workitems/{itemId}/complete" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "output": {
      "approved": true,
      "comments": "Approved for processing"
    }
  }'
```

---

## 11. Troubleshooting

### 11.1 Common Issues

| Issue | Cause | Resolution |
|-------|-------|------------|
| Login failed | Invalid credentials | Reset password |
| Specification error | Invalid XML | Validate schema |
| Case stuck | Deadlock or timeout | Cancel and restart |
| Work item not appearing | Resource allocation | Check role assignment |

### 11.2 Getting Help

- **Documentation**: https://yawlfoundation.github.io
- **Community**: yawl@list.unsw.edu.au
- **GitHub**: https://github.com/yawlfoundation/yawl

---

## 12. Next Steps

1. Explore the workflow patterns library
2. Create complex workflows with conditions and loops
3. Implement Worklets for dynamic adaptation
4. Integrate with external systems
5. Set up monitoring and alerting
6. **v6.0.0-GA Features**: Experiment with GRPO/RL in `yawl-ggen`, configure MCP/A2A for autonomous agents, and leverage Java 25 virtual threads for high-performance workflow execution

---

## 13. Additional Resources

- [YAWL Documentation](https://yawlfoundation.github.io)
- [Workflow Patterns](https://www.workflowpatterns.com)
- [API Reference](https://docs.yawl.io/api)
- [Examples Repository](https://github.com/yawlfoundation/yawl-examples)
