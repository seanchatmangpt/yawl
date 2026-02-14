# YAWL MCP Tools - Quick Reference

## Tool Summary

| Tool Name | Purpose | Required Parameters | Optional Parameters |
|-----------|---------|---------------------|---------------------|
| `launch_case` | Launch new workflow instance | `spec_id` | `spec_version`, `spec_uri`, `case_data` |
| `get_case_status` | Get case execution state | `case_id` | - |
| `get_enabled_work_items` | List all live work items | - | - |
| `checkout_work_item` | Start work item execution | `work_item_id` | - |
| `checkin_work_item` | Complete work item | `work_item_id`, `data` | - |
| `get_work_item_data` | Get work item details | `work_item_id` | - |
| `cancel_case` | Terminate running case | `case_id` | - |
| `get_specification_list` | List loaded workflows | - | - |
| `upload_specification` | Deploy new workflow | `spec_xml` | - |
| `get_case_data` | Get all case data | `case_id` | - |

## Implementation Details

### Real YAWL Engine Integration

```java
// Interface B Client - Runtime Operations
InterfaceB_EnvironmentBasedClient interfaceBClient =
    new InterfaceB_EnvironmentBasedClient("http://localhost:8080/yawl/ib");

String session = interfaceBClient.connect("admin", "YAWL");

// Launch case
YSpecificationID specId = new YSpecificationID("orderfulfillment", "0.1", "orderfulfillment");
String caseId = interfaceBClient.launchCase(specId, caseData, session);

// Checkout work item
String result = interfaceBClient.checkOutWorkItem(workItemId, session);

// Checkin work item
String result = interfaceBClient.checkInWorkItem(workItemId, data, session);

// Get work items
List<WorkItemRecord> items = interfaceBClient.getCompleteListOfLiveWorkItems(session);

// Get case status
String state = interfaceBClient.getCaseState(caseId, session);

// Cancel case
String result = interfaceBClient.cancelCase(caseId, session);

// Get case data
String data = interfaceBClient.getCaseData(caseId, session);

// Get work item data
String data = interfaceBClient.getWorkItem(workItemId, session);
```

```java
// Interface A Client - Design Operations
InterfaceA_EnvironmentBasedClient interfaceAClient =
    new InterfaceA_EnvironmentBasedClient("http://localhost:8080/yawl/ia");

String session = interfaceAClient.connect("admin", "YAWL");

// Upload specification
String result = interfaceAClient.uploadSpecification(specXml, session);

// List specifications
List<SpecificationData> specs = interfaceBClient.getSpecificationList(session);
```

## MCP Protocol Mapping

### JSON-RPC 2.0 Request Format

```json
{
  "jsonrpc": "2.0",
  "id": <number>,
  "method": "tools/call",
  "params": {
    "name": "<tool_name>",
    "arguments": {
      "<param1>": "<value1>",
      "<param2>": "<value2>"
    }
  }
}
```

### JSON-RPC 2.0 Response Format

```json
{
  "jsonrpc": "2.0",
  "id": <number>,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "<result_text>"
      }
    ]
  }
}
```

## Tool Definitions (MCP Schema)

### 1. launch_case

```json
{
  "name": "launch_case",
  "description": "Launch a new YAWL workflow case",
  "inputSchema": {
    "type": "object",
    "properties": {
      "spec_id": {
        "type": "string",
        "description": "Specification identifier",
        "required": true
      },
      "spec_version": {
        "type": "string",
        "description": "Specification version (default: 0.1)",
        "required": false
      },
      "spec_uri": {
        "type": "string",
        "description": "Specification URI (default: same as spec_id)",
        "required": false
      },
      "case_data": {
        "type": "string",
        "description": "XML case input data",
        "required": false
      }
    },
    "required": ["spec_id"]
  }
}
```

### 2. get_case_status

```json
{
  "name": "get_case_status",
  "description": "Get the current status and state of a workflow case",
  "inputSchema": {
    "type": "object",
    "properties": {
      "case_id": {
        "type": "string",
        "description": "Case identifier",
        "required": true
      }
    },
    "required": ["case_id"]
  }
}
```

### 3. get_enabled_work_items

```json
{
  "name": "get_enabled_work_items",
  "description": "Get list of all enabled work items across all cases",
  "inputSchema": {
    "type": "object",
    "properties": {},
    "required": []
  }
}
```

### 4. checkout_work_item

```json
{
  "name": "checkout_work_item",
  "description": "Checkout a work item to begin execution",
  "inputSchema": {
    "type": "object",
    "properties": {
      "work_item_id": {
        "type": "string",
        "description": "Work item identifier",
        "required": true
      }
    },
    "required": ["work_item_id"]
  }
}
```

### 5. checkin_work_item

```json
{
  "name": "checkin_work_item",
  "description": "Complete a work item with output data",
  "inputSchema": {
    "type": "object",
    "properties": {
      "work_item_id": {
        "type": "string",
        "description": "Work item identifier",
        "required": true
      },
      "data": {
        "type": "string",
        "description": "XML output data",
        "required": true
      }
    },
    "required": ["work_item_id", "data"]
  }
}
```

### 6. get_work_item_data

```json
{
  "name": "get_work_item_data",
  "description": "Get input/output data for a specific work item",
  "inputSchema": {
    "type": "object",
    "properties": {
      "work_item_id": {
        "type": "string",
        "description": "Work item identifier",
        "required": true
      }
    },
    "required": ["work_item_id"]
  }
}
```

### 7. cancel_case

```json
{
  "name": "cancel_case",
  "description": "Cancel a running workflow case",
  "inputSchema": {
    "type": "object",
    "properties": {
      "case_id": {
        "type": "string",
        "description": "Case identifier",
        "required": true
      }
    },
    "required": ["case_id"]
  }
}
```

### 8. get_specification_list

```json
{
  "name": "get_specification_list",
  "description": "List all loaded workflow specifications",
  "inputSchema": {
    "type": "object",
    "properties": {},
    "required": []
  }
}
```

### 9. upload_specification

```json
{
  "name": "upload_specification",
  "description": "Upload a new YAWL workflow specification",
  "inputSchema": {
    "type": "object",
    "properties": {
      "spec_xml": {
        "type": "string",
        "description": "Complete YAWL specification XML",
        "required": true
      }
    },
    "required": ["spec_xml"]
  }
}
```

### 10. get_case_data

```json
{
  "name": "get_case_data",
  "description": "Get all data for a specific case",
  "inputSchema": {
    "type": "object",
    "properties": {
      "case_id": {
        "type": "string",
        "description": "Case identifier",
        "required": true
      }
    },
    "required": ["case_id"]
  }
}
```

## YAWL Data Formats

### Case Input Data (XML)

```xml
<data>
  <Company>
    <Name>Acme Corporation</Name>
    <Address>123 Main St</Address>
    <City>Sydney</City>
    <State>NSW</State>
    <PostCode>2000</PostCode>
    <Phone>+61-2-1234-5678</Phone>
    <Fax>+61-2-1234-5679</Fax>
    <BusinessNumber>12345678901</BusinessNumber>
  </Company>
  <Order>
    <OrderNumber>ORD-2026-001</OrderNumber>
    <OrderDate>2026-02-14</OrderDate>
    <Currency>AUD</Currency>
    <OrderTerms>Net 30</OrderTerms>
    <RevisionNumber>0</RevisionNumber>
    <Remarks>Rush order</Remarks>
    <OrderLines>
      <Line>
        <LineNumber>1</LineNumber>
        <UnitCode>WIDGET-A</UnitCode>
        <UnitDescription>Premium Widget</UnitDescription>
        <UnitQuantity>100</UnitQuantity>
        <Action>Added</Action>
      </Line>
    </OrderLines>
  </Order>
  <FreightCost>125.50</FreightCost>
  <DeliveryLocation>Warehouse A</DeliveryLocation>
  <InvoiceRequired>true</InvoiceRequired>
  <PrePaid>false</PrePaid>
</data>
```

### Work Item Output Data (XML)

```xml
<data>
  <OrderApproved>true</OrderApproved>
  <ApprovalDate>2026-02-14</ApprovalDate>
  <ApprovedBy>John Smith</ApprovedBy>
  <ApprovalNotes>Order approved for standard processing</ApprovalNotes>
</data>
```

## Error Detection

### YAWL Failure Pattern

```java
if (result != null && result.contains("<failure>")) {
    throw new IOException("YAWL operation failed: " + result);
}
```

### Common YAWL Error Messages

- `<failure>Specification not found</failure>` - Spec not loaded
- `<failure>Invalid session handle</failure>` - Session expired
- `<failure>Case not found</failure>` - Case doesn't exist
- `<failure>Work item not found</failure>` - Work item doesn't exist
- `<failure>Work item not enabled</failure>` - Can't checkout
- `<failure>Work item not executing</failure>` - Can't checkin

## Server Architecture

```
YawlMcpServer
├── TCP Server (Port 3000)
│   └── JSON-RPC 2.0 Protocol
├── InterfaceB_EnvironmentBasedClient
│   ├── connect(username, password) → sessionHandle
│   ├── launchCase(specId, data, session) → caseId
│   ├── checkOutWorkItem(itemId, session) → XML
│   ├── checkInWorkItem(itemId, data, session) → XML
│   ├── getCompleteListOfLiveWorkItems(session) → List<WorkItemRecord>
│   ├── getCaseState(caseId, session) → XML
│   ├── cancelCase(caseId, session) → String
│   ├── getCaseData(caseId, session) → XML
│   └── getWorkItem(itemId, session) → XML
└── InterfaceA_EnvironmentBasedClient
    ├── uploadSpecification(xml, session) → String
    └── getSpecificationList(session) → List<SpecificationData>
```

## Fortune 5 Compliance Checklist

✅ Real YAWL engine integration (InterfaceB/A clients)
✅ No mock/stub implementations
✅ Proper error handling with exception propagation
✅ Session management with connect/disconnect
✅ All 10 tools implemented with real YAWL operations
✅ JSON parsing with json-simple library
✅ TCP server with proper socket handling
✅ Graceful shutdown with cleanup
✅ Environment-based configuration
✅ Production-ready code (no TODOs, no placeholders)

## Development Notes

### Dependencies

- `json-simple-1.1.1.jar` - JSON parsing
- YAWL core libraries (engine, interfce packages)
- Java 8+ (try-with-resources, lambdas)

### Build Command

```bash
ant -f build/build.xml compile
```

### Run Command

```bash
java -cp "classes:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.integration.mcp.YawlMcpServer 3000
```

### Test Command

```bash
echo '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | nc localhost 3000
```

## Related Documentation

- `MCP_SERVER_USAGE.md` - Detailed usage guide with examples
- `CLAUDE.md` - Project coding standards
- `INTEGRATION_GUIDE.md` - General integration documentation
- YAWL User Manual - Workflow specification format
