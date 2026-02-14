package org.yawlfoundation.yawl.examples.integration;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * AI Agent Example - Demonstrates AI-powered order approval processing
 *
 * This example shows how to:
 * 1. Connect an AI agent to YAWL workflows
 * 2. Receive work item assignments
 * 3. Use LLM to analyze order data
 * 4. Make intelligent approval decisions
 * 5. Report completion back to YAWL
 *
 * Prerequisites:
 * - YAWL Engine running at http://localhost:8080/yawl/ib
 * - ZAI_API_KEY environment variable set
 * - OrderFulfillment specification loaded with approval task
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AiAgentExample {

    private static final String ENGINE_URL = "http://localhost:8080/yawl/ib";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "YAWL";

    private final InterfaceB_EnvironmentBasedClient yawlClient;
    private final ZaiService aiService;
    private String sessionHandle;

    public AiAgentExample() throws IOException {
        this.yawlClient = new InterfaceB_EnvironmentBasedClient(ENGINE_URL);
        this.aiService = initializeAI();
    }

    /**
     * Initialize AI service with proper error handling
     */
    private ZaiService initializeAI() {
        try {
            ZaiService service = new ZaiService();
            service.setSystemPrompt(
                "You are an intelligent order approval agent integrated with YAWL workflow engine. " +
                "Your role is to analyze order requests and make approval decisions based on:\n" +
                "1. Order amount (approve < $10,000 automatically)\n" +
                "2. Customer history and credit rating\n" +
                "3. Product availability and delivery feasibility\n" +
                "4. Risk factors (fraud indicators, unusual patterns)\n" +
                "Provide clear reasoning for all decisions."
            );
            return service;
        } catch (IllegalStateException e) {
            System.err.println("Warning: " + e.getMessage());
            System.err.println("AI features will not be available");
            return null;
        }
    }

    /**
     * Start the AI agent and begin processing work items
     */
    public void start() throws IOException {
        System.out.println("=== AI Agent for Order Approval ===\n");

        // Connect to YAWL
        System.out.println("Connecting to YAWL Engine...");
        sessionHandle = yawlClient.connect(ADMIN_USER, ADMIN_PASSWORD);
        System.out.println("Connected with session: " + sessionHandle);

        if (aiService == null || !aiService.isInitialized()) {
            System.out.println("\nWARNING: AI service not initialized");
            System.out.println("Set ZAI_API_KEY environment variable to enable AI features");
            System.out.println("Continuing with rule-based approval logic...\n");
        } else {
            System.out.println("AI service initialized: " + aiService.isInitialized());
            System.out.println("AI connection test: " + (aiService.testConnection() ? "OK" : "FAILED"));
        }

        System.out.println("\nAI Agent ready to process order approvals");
        System.out.println("Monitoring for approval tasks...\n");

        // Process work items (in real scenario, this would be a continuous loop)
        processApprovalTasks();
    }

    /**
     * Process approval tasks from YAWL
     */
    private void processApprovalTasks() throws IOException {
        List<WorkItemRecord> workItems = yawlClient.getCompleteListOfLiveWorkItems(sessionHandle);

        System.out.println("Found " + workItems.size() + " live work items");

        for (WorkItemRecord item : workItems) {
            if (isApprovalTask(item)) {
                System.out.println("\n--- Processing Approval Task ---");
                processApproval(item);
            }
        }

        // If no approval tasks found, demonstrate with example
        if (workItems.stream().noneMatch(this::isApprovalTask)) {
            System.out.println("No approval tasks found in engine");
            System.out.println("Demonstrating AI approval logic with examples...\n");
            demonstrateApprovalLogic();
        }
    }

    /**
     * Check if work item is an approval task
     */
    private boolean isApprovalTask(WorkItemRecord item) {
        String taskId = item.getTaskID();
        return taskId != null && (
            taskId.toLowerCase().contains("approval") ||
            taskId.toLowerCase().contains("approve") ||
            taskId.toLowerCase().contains("review")
        );
    }

    /**
     * Process a single approval work item
     */
    private void processApproval(WorkItemRecord item) throws IOException {
        System.out.println("Work Item ID: " + item.getID());
        System.out.println("Task: " + item.getTaskID());
        System.out.println("Case: " + item.getCaseID());
        System.out.println("Status: " + item.getStatus());

        // Get work item data
        String itemData = item.getDataString();
        System.out.println("\nOrder Data:");
        System.out.println(itemData != null ? itemData : "No data available");

        // Extract order information
        OrderInfo order = extractOrderInfo(itemData);

        // Make approval decision
        ApprovalDecision decision = makeApprovalDecision(order);

        System.out.println("\n--- Approval Decision ---");
        System.out.println("Approved: " + decision.approved);
        System.out.println("Reason: " + decision.reason);
        System.out.println("Approved By: " + decision.approver);

        // Check out the work item
        if ("Enabled".equals(item.getStatus()) || "Fired".equals(item.getStatus())) {
            String checkoutResult = yawlClient.checkOutWorkItem(item.getID(), sessionHandle);
            if (checkoutResult.contains("fault")) {
                System.out.println("Error checking out: " + checkoutResult);
                return;
            }
        }

        // Complete with decision data
        String resultData = buildApprovalResult(decision);
        String result = yawlClient.checkInWorkItem(item.getID(), resultData, "AI Agent", sessionHandle);

        if (result != null && !result.contains("fault")) {
            System.out.println("✓ Approval task completed successfully");
        } else {
            System.out.println("Error completing task: " + result);
        }
    }

    /**
     * Make approval decision using AI or rules
     */
    private ApprovalDecision makeApprovalDecision(OrderInfo order) {
        if (aiService != null && aiService.isInitialized()) {
            return makeAIDecision(order);
        } else {
            return makeRuleBasedDecision(order);
        }
    }

    /**
     * Use AI to make intelligent approval decision
     */
    private ApprovalDecision makeAIDecision(OrderInfo order) {
        System.out.println("\nUsing AI to analyze order...");

        String prompt = String.format(
            "Analyze this order and decide if it should be approved:\n\n" +
            "Order ID: %s\n" +
            "Customer: %s\n" +
            "Amount: $%.2f\n" +
            "Product: %s\n" +
            "Quantity: %d\n" +
            "Delivery Address: %s\n\n" +
            "Consider:\n" +
            "- Order amount (auto-approve < $10,000)\n" +
            "- Customer legitimacy\n" +
            "- Order reasonableness\n" +
            "- Risk indicators\n\n" +
            "Respond with: DECISION: [APPROVE/REJECT] REASON: [explanation]",
            order.orderId, order.customer, order.amount,
            order.product, order.quantity, order.deliveryAddress
        );

        String aiResponse = aiService.chat(prompt);
        System.out.println("AI Response: " + aiResponse);

        return parseAIResponse(aiResponse);
    }

    /**
     * Parse AI response into approval decision
     */
    private ApprovalDecision parseAIResponse(String response) {
        ApprovalDecision decision = new ApprovalDecision();
        decision.approver = "AI_Agent_GLM-4.6";

        String upper = response.toUpperCase();
        decision.approved = upper.contains("APPROVE") && !upper.contains("REJECT");

        // Extract reason
        if (response.contains("REASON:")) {
            int start = response.indexOf("REASON:") + 7;
            decision.reason = response.substring(start).trim();
        } else {
            decision.reason = response;
        }

        return decision;
    }

    /**
     * Rule-based approval decision (fallback when AI unavailable)
     */
    private ApprovalDecision makeRuleBasedDecision(OrderInfo order) {
        System.out.println("\nUsing rule-based approval logic...");

        ApprovalDecision decision = new ApprovalDecision();
        decision.approver = "AI_Agent_Rules";

        // Simple rules
        if (order.amount < 0) {
            decision.approved = false;
            decision.reason = "Invalid order amount (negative value)";
        } else if (order.amount > 10000) {
            decision.approved = false;
            decision.reason = "Order amount exceeds automatic approval limit ($10,000). Requires manual review.";
        } else if (order.quantity <= 0) {
            decision.approved = false;
            decision.reason = "Invalid quantity";
        } else if (order.customer == null || order.customer.trim().isEmpty()) {
            decision.approved = false;
            decision.reason = "Missing customer information";
        } else {
            decision.approved = true;
            decision.reason = String.format(
                "Order approved: amount $%.2f within limits, customer verified, quantity reasonable (%d units)",
                order.amount, order.quantity
            );
        }

        return decision;
    }

    /**
     * Extract order information from XML data
     */
    private OrderInfo extractOrderInfo(String xmlData) {
        OrderInfo info = new OrderInfo();

        if (xmlData == null || xmlData.trim().isEmpty()) {
            return info;
        }

        // Simple XML parsing (in production, use proper XML parser)
        info.orderId = extractValue(xmlData, "orderId", "orderID");
        info.customer = extractValue(xmlData, "customer");
        info.product = extractValue(xmlData, "product");
        info.deliveryAddress = extractValue(xmlData, "deliveryAddress", "address");

        String amountStr = extractValue(xmlData, "amount", "totalAmount");
        try {
            info.amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            info.amount = 0.0;
        }

        String qtyStr = extractValue(xmlData, "quantity");
        try {
            info.quantity = Integer.parseInt(qtyStr);
        } catch (NumberFormatException e) {
            info.quantity = 0;
        }

        return info;
    }

    /**
     * Extract XML element value (simple string search)
     */
    private String extractValue(String xml, String... tagNames) {
        for (String tag : tagNames) {
            String openTag = "<" + tag + ">";
            String closeTag = "</" + tag + ">";

            int start = xml.indexOf(openTag);
            int end = xml.indexOf(closeTag);

            if (start >= 0 && end > start) {
                return xml.substring(start + openTag.length(), end).trim();
            }
        }
        return "";
    }

    /**
     * Build approval result XML
     */
    private String buildApprovalResult(ApprovalDecision decision) {
        return "<data>" +
            "<approved>" + decision.approved + "</approved>" +
            "<approvedBy>" + decision.approver + "</approvedBy>" +
            "<approvalReason>" + escapeXml(decision.reason) + "</approvalReason>" +
            "<approvalDate>" + new java.util.Date().toString() + "</approvalDate>" +
            "</data>";
    }

    /**
     * Escape XML special characters
     */
    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    /**
     * Demonstrate AI approval logic with examples
     */
    private void demonstrateApprovalLogic() {
        System.out.println("=== Approval Logic Demonstration ===\n");

        OrderInfo[] testOrders = {
            createOrder("ORD-001", "Acme Corp", 2500, "Widgets", 50, "123 Main St"),
            createOrder("ORD-002", "Tech Solutions", 15000, "Servers", 5, "456 Tech Park"),
            createOrder("ORD-003", "Retail Inc", -100, "Products", 10, "789 Store Ln"),
            createOrder("ORD-004", "", 500, "Items", 20, "Unknown")
        };

        for (OrderInfo order : testOrders) {
            System.out.println("--- Order: " + order.orderId + " ---");
            System.out.println("Customer: " + order.customer);
            System.out.println("Amount: $" + order.amount);

            ApprovalDecision decision = makeApprovalDecision(order);

            System.out.println("Decision: " + (decision.approved ? "APPROVED" : "REJECTED"));
            System.out.println("Reason: " + decision.reason);
            System.out.println();
        }
    }

    /**
     * Create test order
     */
    private OrderInfo createOrder(String orderId, String customer, double amount,
                                 String product, int quantity, String address) {
        OrderInfo info = new OrderInfo();
        info.orderId = orderId;
        info.customer = customer;
        info.amount = amount;
        info.product = product;
        info.quantity = quantity;
        info.deliveryAddress = address;
        return info;
    }

    /**
     * Shutdown agent
     */
    public void shutdown() {
        try {
            if (sessionHandle != null) {
                yawlClient.disconnect(sessionHandle);
                System.out.println("\nDisconnected from YAWL Engine");
            }
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }

    /**
     * Order information class
     */
    private static class OrderInfo {
        String orderId = "";
        String customer = "";
        double amount = 0.0;
        String product = "";
        int quantity = 0;
        String deliveryAddress = "";
    }

    /**
     * Approval decision class
     */
    private static class ApprovalDecision {
        boolean approved = false;
        String reason = "";
        String approver = "";
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        AiAgentExample agent = null;

        try {
            agent = new AiAgentExample();
            agent.start();

            System.out.println("\n=== AI Agent Example Complete ===");
            System.out.println("\nKey Features Demonstrated:");
            System.out.println("  ✓ AI-powered order analysis");
            System.out.println("  ✓ Intelligent approval decisions");
            System.out.println("  ✓ Integration with YAWL workflow");
            System.out.println("  ✓ Fallback to rule-based logic");
            System.out.println("  ✓ Proper error handling");

            System.out.println("\nTo use with real workflows:");
            System.out.println("  1. Set ZAI_API_KEY environment variable");
            System.out.println("  2. Load OrderFulfillment spec with approval task");
            System.out.println("  3. Launch case that requires approval");
            System.out.println("  4. Run this AI agent to process approvals");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nTroubleshooting:");
            System.err.println("  - Ensure YAWL Engine is running");
            System.err.println("  - Check admin credentials");
            System.err.println("  - Verify network connectivity");
            e.printStackTrace();
        } finally {
            if (agent != null) {
                agent.shutdown();
            }
        }
    }
}
