# MCP Workflow Tools - Code Examples & Templates
## Practical Guide to Implementing Workflow-as-Tools for LLM Integration

**Document Date**: 2026-02-21
**Companion to**: MCP_LLM_TOOL_DESIGN.md

---

## Section 1: Complete Tool Implementation Template

This section provides copy-paste templates for adding workflow tools to YawlMcpServer.

### 1.1 Basic Tool Template

```java
/**
 * Tool: {workflow_name}
 *
 * Description: {business_process_description}
 *
 * Input:
 *   - {param1}: {type} — {description}
 *   - {param2}: {type} — {description}
 *
 * Output:
 *   - case_id: Case ID for tracking
 *   - status: COMPLETED | FAILED | TIMEOUT
 *   - result: Final case data/decision
 *
 * SLA: Completes within {X} hours
 */
private static McpServerFeatures.SyncToolSpecification create{WorkflowName}Tool(
        InterfaceB_EnvironmentBasedClient interfaceBClient,
        String sessionHandle) {

    // ===== SCHEMA DEFINITION =====
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("param1", Map.of(
        "type", "string",
        "description", "Description of param1",
        "minLength", 1,
        "maxLength", 100
    ));
    props.put("param2", Map.of(
        "type", "number",
        "description", "Description of param2",
        "minimum", 0.01,
        "maximum", 1000000
    ));
    props.put("param3", Map.of(
        "type", "integer",
        "description", "Optional parameter with default",
        "minimum", 1,
        "maximum", 72,
        "default", 24
    ));

    List<String> required = List.of("param1", "param2");
    McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
        "object", props, required, false, null, null);

    // ===== TOOL SPECIFICATION =====
    return new McpServerFeatures.SyncToolSpecification(
        McpSchema.Tool.builder()
            .name("workflow_name_snake_case")
            .description("Route {business_process} through automated workflow. " +
                "Returns case ID and final decision.")
            .inputSchema(schema)
            .build(),

        // ===== TOOL HANDLER =====
        (exchange, args) -> {
            long startTime = System.currentTimeMillis();
            String caseId = null;

            try {
                // 1. VALIDATE INPUTS
                String param1 = validateStringArg(args, "param1", 1, 100);
                double param2 = validateNumberArg(args, "param2", 0.01, 1_000_000);
                int param3 = validateIntArg(args, "param3", 1, 72, 24);

                // 2. BUILD CASE DATA (XML)
                String caseData = String.format(
                    "<data>" +
                    "<param1>%s</param1>" +
                    "<param2>%.2f</param2>" +
                    "<param3>%d</param3>" +
                    "</data>",
                    escapeXml(param1), param2, param3);

                // 3. LAUNCH CASE
                YSpecificationID specId = new YSpecificationID(
                    "{WorkflowName}", "1.0", "http://yawl/workflow");

                caseId = interfaceBClient.launchCase(
                    specId, caseData, null, sessionHandle);

                if (caseId == null || caseId.contains("<failure>")) {
                    return new McpSchema.CallToolResult(
                        "Failed to launch case: " + caseId, true);
                }

                // 4. POLL FOR COMPLETION
                String result = pollCaseCompletion(
                    interfaceBClient, caseId, sessionHandle,
                    param3 * 60 * 60 * 1000L);  // Convert hours to ms

                if (result == null) {
                    return new McpSchema.CallToolResult(
                        "Case timeout after " + param3 + " hours. Case: " + caseId, true);
                }

                long durationMs = System.currentTimeMillis() - startTime;

                // 5. LOG AND RETURN
                logToolInvocation("workflow_name", args, caseId, durationMs, false);
                return new McpSchema.CallToolResult(
                    "Workflow completed successfully.\n" +
                    "Case: " + caseId + "\n" +
                    "Result:\n" + result, false);

            } catch (IllegalArgumentException e) {
                logToolInvocation("workflow_name", args, caseId,
                    System.currentTimeMillis() - startTime, true);
                return new McpSchema.CallToolResult(
                    "Validation error: " + e.getMessage(), true);
            } catch (Exception e) {
                logToolInvocation("workflow_name", args, caseId,
                    System.currentTimeMillis() - startTime, true);
                return new McpSchema.CallToolResult(
                    "Error: " + e.getMessage(), true);
            }
        }
    );
}
```

### 1.2 Validation Helper Functions

```java
/**
 * Validate and extract a string argument with length constraints.
 *
 * @param args the tool arguments map
 * @param name the argument name
 * @param minLength minimum length (inclusive)
 * @param maxLength maximum length (inclusive)
 * @return the validated string
 * @throws IllegalArgumentException if validation fails
 */
private static String validateStringArg(Map<String, Object> args, String name,
                                        int minLength, int maxLength) {
    Object value = args.get(name);
    if (value == null) {
        throw new IllegalArgumentException("Required argument missing: " + name);
    }
    String str = value.toString();
    if (str.length() < minLength) {
        throw new IllegalArgumentException(
            name + " must be at least " + minLength + " characters");
    }
    if (str.length() > maxLength) {
        throw new IllegalArgumentException(
            name + " must be at most " + maxLength + " characters");
    }
    return str;
}

/**
 * Validate and extract a numeric argument with range constraints.
 */
private static double validateNumberArg(Map<String, Object> args, String name,
                                        double min, double max) {
    Object value = args.get(name);
    if (value == null) {
        throw new IllegalArgumentException("Required argument missing: " + name);
    }
    if (!(value instanceof Number)) {
        throw new IllegalArgumentException(name + " must be a number");
    }
    double num = ((Number) value).doubleValue();
    if (num < min || num > max) {
        throw new IllegalArgumentException(
            name + " must be between " + min + " and " + max);
    }
    return num;
}

/**
 * Validate and extract an integer argument with optional default.
 */
private static int validateIntArg(Map<String, Object> args, String name,
                                   int min, int max, int defaultValue) {
    Object value = args.get(name);
    if (value == null) {
        return defaultValue;
    }
    if (!(value instanceof Number)) {
        throw new IllegalArgumentException(name + " must be an integer");
    }
    int num = ((Number) value).intValue();
    if (num < min || num > max) {
        throw new IllegalArgumentException(
            name + " must be between " + min + " and " + max);
    }
    return num;
}

/**
 * XML escape special characters to prevent injection.
 */
private static String escapeXml(String input) {
    if (input == null) {
        return "";
    }
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
}

/**
 * Poll a case until completion with timeout.
 *
 * @param interfaceBClient YAWL interface client
 * @param caseId the case ID to poll
 * @param sessionHandle the session handle
 * @param timeoutMs maximum milliseconds to wait
 * @return final case data, or null if timeout
 */
private static String pollCaseCompletion(
        InterfaceB_EnvironmentBasedClient interfaceBClient,
        String caseId, String sessionHandle, long timeoutMs) throws IOException {

    long startTime = System.currentTimeMillis();
    int pollIntervalMs = 5000;  // Poll every 5 seconds

    while (true) {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > timeoutMs) {
            return null;  // Timeout
        }

        String state = interfaceBClient.getCaseState(caseId, sessionHandle);

        if (state != null && !state.contains("<failure>")) {
            // Case exists and has valid state
            if (state.contains("COMPLETED") || state.contains("DONE")) {
                // Extract final case data
                return interfaceBClient.getCaseData(caseId, sessionHandle);
            }
        }

        // Not completed yet, wait before next poll
        try {
            Thread.sleep(pollIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for case completion", e);
        }
    }
}

/**
 * Log tool invocation for audit and monitoring.
 */
private static void logToolInvocation(String toolName, Map<String, Object> args,
                                       String caseId, long durationMs,
                                       boolean isError) {
    System.err.printf("[MCP_TOOL] %s | case=%s | duration=%dms | status=%s%n",
        toolName, caseId, durationMs, isError ? "ERROR" : "SUCCESS");

    // In production: Log to structured logging system (JSON format)
    // logger.info("MCP_TOOL_INVOCATION", new MapBuilder()
    //     .put("tool", toolName)
    //     .put("case_id", caseId)
    //     .put("duration_ms", durationMs)
    //     .put("status", isError ? "error" : "success")
    //     .build());
}
```

---

## Section 2: Real-World Workflow Tools

### 2.1 Purchase Approval Tool

```java
/**
 * Tool: approve_purchase
 *
 * Routes a purchase request through the approval workflow.
 * Automatically approves amounts under the employee's delegation limit,
 * otherwise routes to manager for approval.
 *
 * Example:
 *   Input:  {applicant_id: "emp-12345", amount: 5000, justification: "Q1 licenses"}
 *   Output: {status: "APPROVED", decision_time_ms: 900000, notes: "Auto-approved"}
 */
private static McpServerFeatures.SyncToolSpecification createApprovePurchaseTool(
        InterfaceB_EnvironmentBasedClient interfaceBClient,
        String sessionHandle) {

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("applicant_id", Map.of(
        "type", "string",
        "description", "Employee ID (format: emp-XXXXX)",
        "pattern", "^emp-\\d{5}$"
    ));
    props.put("amount", Map.of(
        "type", "number",
        "description", "Amount in USD",
        "minimum", 0.01,
        "maximum", 10000000
    ));
    props.put("justification", Map.of(
        "type", "string",
        "description", "Business reason for purchase",
        "minLength", 10,
        "maxLength", 500
    ));
    props.put("deadline_hours", Map.of(
        "type", "integer",
        "description", "Hours to wait for approval (1-72, default 24)",
        "minimum", 1,
        "maximum", 72,
        "default", 24
    ));

    List<String> required = List.of("applicant_id", "amount", "justification");
    McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
        "object", props, required, false, null, null);

    return new McpServerFeatures.SyncToolSpecification(
        McpSchema.Tool.builder()
            .name("approve_purchase")
            .description("Route purchase request through approval workflow. " +
                "Auto-approves under delegation limit, routes to manager if over limit. " +
                "Returns approval status and decision time.")
            .inputSchema(schema)
            .build(),
        (exchange, args) -> {
            long startTime = System.currentTimeMillis();
            String caseId = null;

            try {
                String applicantId = validateStringArg(args, "applicant_id", 3, 20);
                double amount = validateNumberArg(args, "amount", 0.01, 10_000_000);
                String justification = validateStringArg(args, "justification", 10, 500);
                int deadlineHours = validateIntArg(args, "deadline_hours", 1, 72, 24);

                // Look up employee's delegation limit
                double delegationLimit = getEmployeeDelegationLimit(applicantId);
                boolean autoApprove = amount <= delegationLimit;

                String caseData = String.format(
                    "<data>" +
                    "<applicant_id>%s</applicant_id>" +
                    "<amount>%.2f</amount>" +
                    "<justification>%s</justification>" +
                    "<auto_approve>%b</auto_approve>" +
                    "<deadline_hours>%d</deadline_hours>" +
                    "</data>",
                    escapeXml(applicantId), amount, escapeXml(justification),
                    autoApprove, deadlineHours);

                YSpecificationID specId = new YSpecificationID(
                    "ApprovalWorkflow", "1.0", "http://yawl/approval");

                caseId = interfaceBClient.launchCase(
                    specId, caseData, null, sessionHandle);

                if (caseId == null || caseId.contains("<failure>")) {
                    return new McpSchema.CallToolResult(
                        "Failed to launch approval case: " + caseId, true);
                }

                // Poll with deadline
                String result = pollCaseCompletion(interfaceBClient, caseId,
                    sessionHandle, deadlineHours * 60 * 60 * 1000L);

                if (result == null) {
                    return new McpSchema.CallToolResult(
                        "Approval timeout after " + deadlineHours + " hours. " +
                        "Case: " + caseId + ". Manual review required.", true);
                }

                // Parse result
                String status = extractXmlValue(result, "approved") ? "APPROVED" : "REJECTED";
                String notes = extractXmlValue(result, "notes", "No notes");
                long durationMs = System.currentTimeMillis() - startTime;

                logToolInvocation("approve_purchase", args, caseId, durationMs, false);

                return new McpSchema.CallToolResult(
                    String.format(
                        "Purchase %s\n" +
                        "Amount: $%.2f\n" +
                        "Decision time: %d seconds\n" +
                        "Notes: %s\n" +
                        "Case ID: %s",
                        status, amount, durationMs / 1000, notes, caseId),
                    false);

            } catch (IllegalArgumentException e) {
                logToolInvocation("approve_purchase", args, caseId,
                    System.currentTimeMillis() - startTime, true);
                return new McpSchema.CallToolResult(
                    "Validation error: " + e.getMessage(), true);
            } catch (Exception e) {
                return new McpSchema.CallToolResult("Error: " + e.getMessage(), true);
            }
        }
    );
}

private static double getEmployeeDelegationLimit(String employeeId) {
    // In production: Look up from HR system / database
    // For PoC: Return fixed limits by employee level
    if (employeeId.matches("emp-1\\d{4}")) return 1000;      // Level 1
    if (employeeId.matches("emp-2\\d{4}")) return 10_000;    // Manager
    if (employeeId.matches("emp-3\\d{4}")) return 100_000;   // Director
    return 500;  // Default
}

private static String extractXmlValue(String xml, String tag) {
    return extractXmlValue(xml, tag, null);
}

private static String extractXmlValue(String xml, String tag, String defaultValue) {
    String pattern = "<" + tag + ">([^<]*)</" + tag + ">";
    java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
    java.util.regex.Matcher m = p.matcher(xml);
    if (m.find()) {
        return m.group(1);
    }
    return defaultValue;
}
```

### 2.2 Vendor Selection Tool

```java
/**
 * Tool: select_vendor
 *
 * Routes vendor selection through RFQ (Request for Quote) workflow.
 * Solicits bids from approved vendors and returns the best option.
 */
private static McpServerFeatures.SyncToolSpecification createSelectVendorTool(
        InterfaceB_EnvironmentBasedClient interfaceBClient,
        String sessionHandle) {

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("category", Map.of(
        "type", "string",
        "description", "Product category",
        "enum", List.of("software", "hardware", "services", "consulting")
    ));
    props.put("item_description", Map.of(
        "type", "string",
        "description", "What we need to procure",
        "minLength", 10,
        "maxLength", 500
    ));
    props.put("budget_usd", Map.of(
        "type", "number",
        "description", "Budget constraint in USD",
        "minimum", 100
    ));
    props.put("timeline_days", Map.of(
        "type", "integer",
        "description", "Days until needed",
        "minimum", 1,
        "maximum", 365
    ));
    props.put("max_vendors", Map.of(
        "type", "integer",
        "description", "Max vendors to contact (default 5)",
        "minimum", 1,
        "maximum", 20,
        "default", 5
    ));

    List<String> required = List.of("category", "item_description", "budget_usd", "timeline_days");
    McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
        "object", props, required, false, null, null);

    return new McpServerFeatures.SyncToolSpecification(
        McpSchema.Tool.builder()
            .name("select_vendor")
            .description("Route vendor selection through RFQ workflow. " +
                "Contacts approved vendors, collects bids, recommends best option.")
            .inputSchema(schema)
            .build(),
        (exchange, args) -> {
            long startTime = System.currentTimeMillis();
            String caseId = null;

            try {
                String category = validateStringArg(args, "category", 3, 20);
                String itemDescription = validateStringArg(args, "item_description", 10, 500);
                double budget = validateNumberArg(args, "budget_usd", 100, 100_000_000);
                int timeline = validateIntArg(args, "timeline_days", 1, 365, null);
                int maxVendors = validateIntArg(args, "max_vendors", 1, 20, 5);

                String caseData = String.format(
                    "<data>" +
                    "<category>%s</category>" +
                    "<item_description>%s</item_description>" +
                    "<budget_usd>%.2f</budget_usd>" +
                    "<timeline_days>%d</timeline_days>" +
                    "<max_vendors>%d</max_vendors>" +
                    "</data>",
                    escapeXml(category), escapeXml(itemDescription),
                    budget, timeline, maxVendors);

                YSpecificationID specId = new YSpecificationID(
                    "VendorSelectionWorkflow", "1.0", "http://yawl/vendor");

                caseId = interfaceBClient.launchCase(
                    specId, caseData, null, sessionHandle);

                if (caseId == null || caseId.contains("<failure>")) {
                    return new McpSchema.CallToolResult(
                        "Failed to launch vendor selection case: " + caseId, true);
                }

                // Poll with extended timeout (RFQ takes longer)
                long timeoutMs = (timeline + 2) * 24 * 60 * 60 * 1000L;
                String result = pollCaseCompletion(interfaceBClient, caseId,
                    sessionHandle, timeoutMs);

                if (result == null) {
                    return new McpSchema.CallToolResult(
                        "Vendor selection timeout. Case: " + caseId, true);
                }

                String selectedVendor = extractXmlValue(result, "selected_vendor");
                String price = extractXmlValue(result, "total_price");
                String leadTime = extractXmlValue(result, "lead_time_days");

                long durationMs = System.currentTimeMillis() - startTime;
                logToolInvocation("select_vendor", args, caseId, durationMs, false);

                return new McpSchema.CallToolResult(
                    String.format(
                        "Vendor Selected: %s\n" +
                        "Price: $%s\n" +
                        "Lead Time: %s days\n" +
                        "Case ID: %s",
                        selectedVendor, price, leadTime, caseId),
                    false);

            } catch (Exception e) {
                logToolInvocation("select_vendor", args, caseId,
                    System.currentTimeMillis() - startTime, true);
                return new McpSchema.CallToolResult("Error: " + e.getMessage(), true);
            }
        }
    );
}
```

### 2.3 Compliance Check Tool

```java
/**
 * Tool: compliance_check
 *
 * Validates transaction against compliance policies:
 * - OFAC/Sanctions list checking
 * - Amount thresholds
 * - Country restrictions
 * - Vendor blacklist
 */
private static McpServerFeatures.SyncToolSpecification createComplianceCheckTool(
        InterfaceB_EnvironmentBasedClient interfaceBClient,
        String sessionHandle) {

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("transaction_id", Map.of(
        "type", "string",
        "description", "Unique transaction ID (PO number, invoice ID, etc.)"
    ));
    props.put("transaction_amount", Map.of(
        "type", "number",
        "description", "Amount in USD",
        "minimum", 0
    ));
    props.put("vendor_name", Map.of(
        "type", "string",
        "description", "Name of vendor/counterparty"
    ));
    props.put("vendor_country", Map.of(
        "type", "string",
        "description", "2-letter country code (e.g., US, CN, RU)",
        "pattern", "^[A-Z]{2}$"
    ));
    props.put("check_sanctions", Map.of(
        "type", "boolean",
        "description", "Check against OFAC sanctions list (default true)",
        "default", true
    ));

    List<String> required = List.of("transaction_id", "transaction_amount",
        "vendor_name", "vendor_country");
    McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
        "object", props, required, false, null, null);

    return new McpServerFeatures.SyncToolSpecification(
        McpSchema.Tool.builder()
            .name("compliance_check")
            .description("Validate transaction against compliance policies. " +
                "Checks sanctions lists, thresholds, country restrictions. " +
                "Returns pass/fail with violation details if any.")
            .inputSchema(schema)
            .build(),
        (exchange, args) -> {
            long startTime = System.currentTimeMillis();
            String caseId = null;

            try {
                String transactionId = validateStringArg(args, "transaction_id", 1, 50);
                double amount = validateNumberArg(args, "transaction_amount", 0, 1_000_000_000);
                String vendorName = validateStringArg(args, "vendor_name", 1, 200);
                String vendorCountry = validateStringArg(args, "vendor_country", 2, 2);
                boolean checkSanctions = (boolean) args.getOrDefault("check_sanctions", true);

                String caseData = String.format(
                    "<data>" +
                    "<transaction_id>%s</transaction_id>" +
                    "<transaction_amount>%.2f</transaction_amount>" +
                    "<vendor_name>%s</vendor_name>" +
                    "<vendor_country>%s</vendor_country>" +
                    "<check_sanctions>%b</check_sanctions>" +
                    "</data>",
                    escapeXml(transactionId), amount, escapeXml(vendorName),
                    escapeXml(vendorCountry), checkSanctions);

                YSpecificationID specId = new YSpecificationID(
                    "ComplianceCheckWorkflow", "1.0", "http://yawl/compliance");

                caseId = interfaceBClient.launchCase(
                    specId, caseData, null, sessionHandle);

                if (caseId == null || caseId.contains("<failure>")) {
                    return new McpSchema.CallToolResult(
                        "Failed to launch compliance check: " + caseId, true);
                }

                // Poll with short timeout (compliance checks are fast)
                String result = pollCaseCompletion(interfaceBClient, caseId,
                    sessionHandle, 10 * 60 * 1000L);  // 10 minute timeout

                if (result == null) {
                    return new McpSchema.CallToolResult(
                        "Compliance check timeout. Case: " + caseId, true);
                }

                String status = extractXmlValue(result, "status", "UNKNOWN");
                String violationCount = extractXmlValue(result, "violation_count", "0");
                String violations = extractXmlValue(result, "violations", "");

                long durationMs = System.currentTimeMillis() - startTime;
                logToolInvocation("compliance_check", args, caseId, durationMs,
                    !status.equals("PASS"));

                if (status.equals("PASS")) {
                    return new McpSchema.CallToolResult(
                        "✓ Compliance Check PASSED\n" +
                        "Transaction: " + transactionId + "\n" +
                        "Amount: $" + String.format("%.2f", amount) + "\n" +
                        "Vendor: " + vendorName + " (" + vendorCountry + ")\n" +
                        "No violations detected.",
                        false);
                } else {
                    return new McpSchema.CallToolResult(
                        "✗ Compliance Check FAILED\n" +
                        "Transaction: " + transactionId + "\n" +
                        "Violations (" + violationCount + "):\n" + violations,
                        true);
                }

            } catch (Exception e) {
                logToolInvocation("compliance_check", args, caseId,
                    System.currentTimeMillis() - startTime, true);
                return new McpSchema.CallToolResult("Error: " + e.getMessage(), true);
            }
        }
    );
}
```

---

## Section 3: Integration into YawlMcpServer

### 3.1 Adding Tools to Server

Modify `YawlToolSpecifications.createAll()` to register custom tools:

```java
public static List<McpServerFeatures.SyncToolSpecification> createAll(
        InterfaceB_EnvironmentBasedClient interfaceBClient,
        InterfaceA_EnvironmentBasedClient interfaceAClient,
        String sessionHandle) {

    List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

    // Core YAWL tools (existing)
    tools.add(createLaunchCaseTool(interfaceBClient, sessionHandle));
    tools.add(createGetCaseStatusTool(interfaceBClient, sessionHandle));
    tools.add(createCancelCaseTool(interfaceBClient, sessionHandle));
    // ... other core tools ...

    // NEW: Workflow-specific tools
    tools.add(createApprovePurchaseTool(interfaceBClient, sessionHandle));
    tools.add(createSelectVendorTool(interfaceBClient, sessionHandle));
    tools.add(createComplianceCheckTool(interfaceBClient, sessionHandle));

    return tools;
}
```

### 3.2 Main Entry Point

The existing `YawlMcpServer.main()` already handles everything:

```java
public static void main(String[] args) {
    String engineUrl = System.getenv("YAWL_ENGINE_URL");
    String username = System.getenv("YAWL_USERNAME");
    String password = System.getenv("YAWL_PASSWORD");

    YawlMcpServer server = new YawlMcpServer(engineUrl, username, password);
    server.start();  // Automatically registers all tools including your custom ones
}
```

---

## Section 4: Testing Your Tools

### 4.1 Unit Test Template

```java
@Test
public void testApprovePurchaseTool_ValidInput() throws Exception {
    // Given
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("applicant_id", "emp-12345");
    args.put("amount", 5000);
    args.put("justification", "Q1 software licenses");
    args.put("deadline_hours", 24);

    // When
    McpSchema.CallToolResult result = invokeApprovePurchaseTool(args);

    // Then
    assertFalse(result.isError());
    assertTrue(result.content().contains("APPROVED"));
}

@Test
public void testApprovePurchaseTool_InvalidAmount() throws Exception {
    // Given
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("applicant_id", "emp-12345");
    args.put("amount", -100);  // Invalid: negative amount
    args.put("justification", "Q1 software licenses");

    // When
    McpSchema.CallToolResult result = invokeApprovePurchaseTool(args);

    // Then
    assertTrue(result.isError());
    assertTrue(result.content().contains("must be between"));
}

@Test
public void testApprovePurchaseTool_MissingRequired() throws Exception {
    // Given
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("applicant_id", "emp-12345");
    // Missing: amount and justification

    // When
    McpSchema.CallToolResult result = invokeApprovePurchaseTool(args);

    // Then
    assertTrue(result.isError());
    assertTrue(result.content().contains("Required argument missing"));
}

@Test
public void testApprovePurchaseTool_XSSPrevention() throws Exception {
    // Given
    Map<String, Object> args = new LinkedHashMap<>();
    args.put("applicant_id", "emp-12345");
    args.put("amount", 5000);
    args.put("justification", "<script>alert('xss')</script>");  // XSS attempt

    // When
    McpSchema.CallToolResult result = invokeApprovePurchaseTool(args);

    // Then: Should escape the malicious input
    assertFalse(result.isError());
    String caseData = extractCaseData(result);
    assertTrue(caseData.contains("&lt;script&gt;"));
    assertFalse(caseData.contains("<script>"));
}
```

### 4.2 Integration Test (with Real YAWL Engine)

```java
@Test
@IntegrationTest
public void testApprovalWorkflow_EndToEnd() throws Exception {
    // Arrange
    String engineUrl = "http://localhost:8080/yawl";
    InterfaceB_EnvironmentBasedClient client =
        new InterfaceB_EnvironmentBasedClient(engineUrl + "/ib");
    String sessionHandle = client.connect("admin", "YAWL");

    // Act: Launch approval case
    YSpecificationID specId = new YSpecificationID(
        "ApprovalWorkflow", "1.0", "http://yawl/approval");
    String caseData = "<data>" +
        "<applicant_id>emp-12345</applicant_id>" +
        "<amount>5000</amount>" +
        "<justification>Software licenses</justification>" +
        "</data>";

    String caseId = client.launchCase(specId, caseData, null, sessionHandle);

    // Assert: Case launched successfully
    assertNotNull(caseId);
    assertFalse(caseId.contains("<failure>"));

    // Wait for completion
    Thread.sleep(5000);
    String state = client.getCaseState(caseId, sessionHandle);
    System.out.println("Case state: " + state);

    // Cleanup
    client.disconnect(sessionHandle);
}
```

---

## Section 5: Claude System Prompt for Tools

```
You are an autonomous procurement coordinator with access to YAWL workflow tools.

## Available Tools

### approve_purchase
Routes purchase requests through approval workflow.
- Max amount: $10,000,000
- Completes within: 24-72 hours
- Returns: approval status, decision time, notes

Example:
{
  "tool": "approve_purchase",
  "applicant_id": "emp-12345",
  "amount": 5000,
  "justification": "Q1 software licenses",
  "deadline_hours": 24
}

### select_vendor
Finds best vendor via RFQ workflow.
- Contacts: 3-20 approved vendors
- Completes within: timeline_days + 2 days
- Returns: vendor name, price, lead time

### compliance_check
Validates transactions against compliance policies.
- Checks: OFAC sanctions, amount thresholds, country restrictions
- Completes within: 10 minutes
- Returns: pass/fail with violation details

## Decision Rules

1. **Always approve first** before vendor selection
2. **Always check compliance** before finalizing
3. **Explain each step** to the user
4. **Handle errors gracefully**:
   - Timeout → escalate to manual review
   - Validation error → ask for correction
   - Rate limit → wait 60 seconds

## Response Format

```
I'll process your procurement request in 4 steps:

**Step 1: Route through approval**
Requesting approval for: [amount and justification]
...
Status: APPROVED ✓

**Step 2: Select best vendor**
Soliciting bids from approved vendors...
Selected: [vendor name] at $[price], delivery in [days]

**Step 3: Generate purchase order**
PO: [PO-NUMBER] created

**Step 4: Compliance validation**
Checking against sanctions lists, thresholds...
Status: PASS ✓

**Final Summary**
Order complete and cleared for execution.
Delivery ETA: [date]
```
```

---

## Summary

This guide provides:
1. **Template** for creating workflow tools
2. **Real examples** (approval, vendor, compliance)
3. **Validation helpers** (safe input handling)
4. **Integration** into YawlMcpServer
5. **Testing patterns** (unit + integration)
6. **Claude prompts** for effective tool usage

**Next Steps**:
1. Copy the tool template
2. Customize for your workflows
3. Add to YawlToolSpecifications
4. Test with unit tests
5. Deploy MCP server
6. Connect Claude and test end-to-end

---

**Reference Files**:
- Main guide: `/home/user/yawl/docs/MCP_LLM_TOOL_DESIGN.md`
- MCP Spec: https://spec.modelcontextprotocol.io/
- YAWL Integration: `src/org/yawlfoundation/yawl/integration/mcp/`
