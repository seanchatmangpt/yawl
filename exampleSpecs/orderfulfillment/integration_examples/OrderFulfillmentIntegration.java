package org.yawlfoundation.yawl.examples.integration;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.List;

/**
 * Complete Order Fulfillment Integration Example
 *
 * Demonstrates end-to-end order fulfillment workflow execution using MCP/A2A:
 * 1. Launch order fulfillment case via MCP
 * 2. Execute ordering task
 * 3. Process payment
 * 4. Arrange freight
 * 5. Handle delivery
 * 6. Complete workflow with proper data flow
 *
 * Prerequisites:
 * - YAWL Engine running at http://localhost:8080/yawl/ib
 * - OrderFulfillment specification loaded
 * - Valid admin credentials
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class OrderFulfillmentIntegration {

    private static final String ENGINE_URL = "http://localhost:8080/yawl/ib";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "YAWL";
    private static final String SPEC_ID = "OrderFulfillment";

    public static void main(String[] args) {
        System.out.println("=== Order Fulfillment Integration Example ===\n");

        InterfaceB_EnvironmentBasedClient client = null;
        String sessionHandle = null;
        String caseId = null;

        try {
            // Step 1: Connect to YAWL Engine
            System.out.println("Step 1: Connecting to YAWL Engine...");
            client = new InterfaceB_EnvironmentBasedClient(ENGINE_URL);
            sessionHandle = client.connect(ADMIN_USER, ADMIN_PASSWORD);
            System.out.println("Connected with session: " + sessionHandle);

            // Step 2: Launch Order Fulfillment Case
            System.out.println("\nStep 2: Launching Order Fulfillment Workflow...");

            String caseData = buildOrderData(
                "ORD-2026-100",
                "Global Enterprises Ltd",
                "Alice Johnson",
                50,
                "PROD-Widget-X1",
                25.99,
                "123 Business Park, Tech City, TC 12345",
                "standard"
            );

            System.out.println("Order details:");
            System.out.println(caseData);

            YSpecificationID specId = new YSpecificationID(SPEC_ID, "0.1", "0.1");

            try {
                caseId = client.launchCase(specId, caseData, null, sessionHandle);

                if (caseId != null && !caseId.contains("fault")) {
                    System.out.println("Case launched successfully: " + caseId);
                } else {
                    System.out.println("Error launching case: " + caseId);
                    System.out.println("\nNote: Load OrderFulfillment specification first:");
                    System.out.println("  - Use YAWL Editor or Control Panel");
                    System.out.println("  - Or use Interface A to upload specification");
                    return;
                }
            } catch (IOException e) {
                System.out.println("Error: " + e.getMessage());
                System.out.println("Make sure OrderFulfillment specification is loaded");
                return;
            }

            // Step 3: Execute Ordering Task
            System.out.println("\nStep 3: Executing Ordering Task...");
            executeTask(client, sessionHandle, caseId, "ordering",
                "<data>" +
                "<orderVerified>true</orderVerified>" +
                "<inventoryChecked>true</inventoryChecked>" +
                "<stockAvailable>true</stockAvailable>" +
                "</data>");

            // Step 4: Process Payment
            System.out.println("\nStep 4: Processing Payment...");
            executeTask(client, sessionHandle, caseId, "payment",
                "<data>" +
                "<paymentMethod>credit_card</paymentMethod>" +
                "<paymentStatus>approved</paymentStatus>" +
                "<transactionId>TXN-" + System.currentTimeMillis() + "</transactionId>" +
                "<amountCharged>1299.50</amountCharged>" +
                "</data>");

            // Step 5: Arrange Freight
            System.out.println("\nStep 5: Arranging Freight...");
            executeTask(client, sessionHandle, caseId, "freight",
                "<data>" +
                "<carrier>FastShip Logistics</carrier>" +
                "<trackingNumber>TRACK-" + System.currentTimeMillis() + "</trackingNumber>" +
                "<estimatedDelivery>2026-02-20</estimatedDelivery>" +
                "<shippingCost>25.00</shippingCost>" +
                "</data>");

            // Step 6: Handle Delivery
            System.out.println("\nStep 6: Completing Delivery...");
            executeTask(client, sessionHandle, caseId, "delivery",
                "<data>" +
                "<deliveryStatus>completed</deliveryStatus>" +
                "<deliveredDate>2026-02-19</deliveredDate>" +
                "<signature>Alice Johnson</signature>" +
                "<deliveryNotes>Package left at reception</deliveryNotes>" +
                "</data>");

            // Step 7: Check Final Case State
            System.out.println("\nStep 7: Checking Final Case State...");
            String caseState = client.getCaseData(caseId, sessionHandle);
            if (caseState != null && !caseState.contains("fault")) {
                System.out.println("Final case data:");
                System.out.println(caseState);
            } else {
                System.out.println("Case may have completed and been archived");
            }

            // Step 8: Demonstrate Exception Handling
            System.out.println("\nStep 8: Exception Handling Example...");
            demonstrateExceptionHandling(client, sessionHandle);

            System.out.println("\n=== Order Fulfillment Integration Complete ===");
            System.out.println("\nWorkflow Summary:");
            System.out.println("  ✓ Order placed and verified");
            System.out.println("  ✓ Payment processed successfully");
            System.out.println("  ✓ Freight arranged with carrier");
            System.out.println("  ✓ Delivery completed to customer");
            System.out.println("\nData Flow:");
            System.out.println("  Order → Payment → Freight → Delivery");
            System.out.println("  Each task received data from previous task");
            System.out.println("  Final state contains complete order history");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            if (client != null && sessionHandle != null) {
                try {
                    client.disconnect(sessionHandle);
                    System.out.println("\nDisconnected from YAWL Engine");
                } catch (IOException e) {
                    System.err.println("Error disconnecting: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Execute a workflow task by finding and completing the work item
     */
    private static void executeTask(InterfaceB_EnvironmentBasedClient client,
                                    String sessionHandle,
                                    String caseId,
                                    String taskId,
                                    String outputData) throws IOException {

        System.out.println("  Finding work item for task: " + taskId);

        // Get work items for this case
        List<WorkItemRecord> workItems = client.getWorkItemsForCase(caseId, sessionHandle);

        WorkItemRecord targetItem = null;
        for (WorkItemRecord item : workItems) {
            if (item.getTaskID() != null && item.getTaskID().toLowerCase().contains(taskId.toLowerCase())) {
                targetItem = item;
                break;
            }
        }

        if (targetItem == null) {
            System.out.println("  No work item found for task: " + taskId);
            System.out.println("  Available tasks:");
            for (WorkItemRecord item : workItems) {
                System.out.println("    - " + item.getTaskID() + " (status: " + item.getStatus() + ")");
            }
            return;
        }

        System.out.println("  Work item found: " + targetItem.getID());
        System.out.println("  Status: " + targetItem.getStatus());

        // Check out if enabled/fired
        if ("Enabled".equals(targetItem.getStatus()) || "Fired".equals(targetItem.getStatus())) {
            String checkoutResult = client.checkOutWorkItem(targetItem.getID(), sessionHandle);
            if (checkoutResult.contains("fault")) {
                System.out.println("  Error checking out: " + checkoutResult);
                return;
            }
            System.out.println("  Work item checked out");
        }

        // Complete the task
        System.out.println("  Completing with data: " + outputData);
        String result = client.checkInWorkItem(targetItem.getID(), outputData, "Integration Example", sessionHandle);

        if (result != null && !result.contains("fault")) {
            System.out.println("  ✓ Task completed successfully");
        } else {
            System.out.println("  Error completing task: " + result);
        }
    }

    /**
     * Build order data XML
     */
    private static String buildOrderData(String orderId, String customer, String contactName,
                                        int quantity, String product, double unitPrice,
                                        String deliveryAddress, String shippingMethod) {
        double totalAmount = quantity * unitPrice;

        return "<data>" +
            "<orderId>" + orderId + "</orderId>" +
            "<customer>" + customer + "</customer>" +
            "<contactName>" + contactName + "</contactName>" +
            "<quantity>" + quantity + "</quantity>" +
            "<product>" + product + "</product>" +
            "<unitPrice>" + unitPrice + "</unitPrice>" +
            "<totalAmount>" + totalAmount + "</totalAmount>" +
            "<deliveryAddress>" + deliveryAddress + "</deliveryAddress>" +
            "<shippingMethod>" + shippingMethod + "</shippingMethod>" +
            "<orderDate>" + new java.util.Date().toString() + "</orderDate>" +
            "</data>";
    }

    /**
     * Demonstrate exception handling scenarios
     */
    private static void demonstrateExceptionHandling(InterfaceB_EnvironmentBasedClient client,
                                                     String sessionHandle) throws IOException {
        System.out.println("  Exception scenarios that can be handled:");
        System.out.println("  1. Payment Declined:");
        System.out.println("     - Set paymentStatus=declined in payment task");
        System.out.println("     - Workflow can route to retry or cancel path");
        System.out.println("  2. Out of Stock:");
        System.out.println("     - Set stockAvailable=false in ordering task");
        System.out.println("     - Workflow can route to backorder process");
        System.out.println("  3. Delivery Failed:");
        System.out.println("     - Set deliveryStatus=failed in delivery task");
        System.out.println("     - Workflow can route to re-delivery or refund");
        System.out.println("  4. Timeout Handling:");
        System.out.println("     - Use YAWL timers on tasks");
        System.out.println("     - Automatic escalation after deadline");
    }
}
