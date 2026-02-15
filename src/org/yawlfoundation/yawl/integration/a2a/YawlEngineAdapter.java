package org.yawlfoundation.yawl.integration.a2a;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YAWL Engine Adapter
 *
 * Wrapper for YAWL InterfaceB operations with connection management.
 * Provides a simplified API for A2A-to-YAWL integration.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlEngineAdapter {

    private static final int DEFAULT_RECONNECT_ATTEMPTS = 3;
    private static final long DEFAULT_RECONNECT_DELAY_MS = 1000;

    private final String engineUrl;
    private final String username;
    private final String password;
    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final InterfaceA_EnvironmentBasedClient interfaceAClient;
    private String sessionHandleB;
    private String sessionHandleA;
    private volatile boolean connected;

    /**
     * Create YAWL Engine adapter
     *
     * @param engineUrl the YAWL engine URL (e.g., "http://localhost:8080/yawl")
     * @param username the YAWL username
     * @param password the YAWL password
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    public YawlEngineAdapter(String engineUrl, String username, String password) {
        if (engineUrl == null || engineUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("YAWL engine URL is required");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("YAWL username is required");
        }
        if (password == null) {
            throw new IllegalArgumentException("YAWL password is required");
        }

        this.engineUrl = engineUrl;
        this.username = username;
        this.password = password;

        String interfaceAUrl = engineUrl + "/ia";
        String interfaceBUrl = engineUrl + "/ib";

        this.interfaceAClient = new InterfaceA_EnvironmentBasedClient(interfaceAUrl);
        this.interfaceBClient = new InterfaceB_EnvironmentBasedClient(interfaceBUrl);
        this.connected = false;
    }

    /**
     * Create adapter from environment variables
     *
     * Required environment variables:
     * - YAWL_ENGINE_URL: Engine URL (e.g., http://localhost:8080/yawl)
     * - YAWL_USERNAME: Username for authentication
     * - YAWL_PASSWORD: Password for authentication
     *
     * @return configured adapter
     * @throws IllegalStateException if required environment variables are missing
     */
    public static YawlEngineAdapter fromEnvironment() {
        String engineUrl = System.getenv("YAWL_ENGINE_URL");
        String username = System.getenv("YAWL_USERNAME");
        String password = System.getenv("YAWL_PASSWORD");

        if (engineUrl == null || engineUrl.isEmpty()) {
            throw new IllegalStateException(
                "YAWL_ENGINE_URL environment variable not set.\n" +
                "Set it with: export YAWL_ENGINE_URL=http://localhost:8080/yawl"
            );
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalStateException(
                "YAWL_USERNAME environment variable not set.\n" +
                "Set it with: export YAWL_USERNAME=admin"
            );
        }
        if (password == null) {
            throw new IllegalStateException(
                "YAWL_PASSWORD environment variable not set.\n" +
                "Set it with: export YAWL_PASSWORD=YAWL"
            );
        }

        return new YawlEngineAdapter(engineUrl, username, password);
    }

    /**
     * Connect to the YAWL engine
     *
     * @throws A2AException if connection fails
     */
    public synchronized void connect() throws A2AException {
        if (connected) {
            return;
        }

        int attempts = 0;
        IOException lastException = null;

        while (attempts < DEFAULT_RECONNECT_ATTEMPTS) {
            attempts++;
            try {
                sessionHandleB = interfaceBClient.connect(username, password);

                if (sessionHandleB == null || sessionHandleB.contains("failure") || sessionHandleB.contains("error")) {
                    throw new IOException("Connection rejected: " + sessionHandleB);
                }

                connected = true;
                return;

            } catch (IOException e) {
                lastException = e;
                if (attempts < DEFAULT_RECONNECT_ATTEMPTS) {
                    try {
                        Thread.sleep(DEFAULT_RECONNECT_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new A2AException(
            A2AException.ErrorCode.CONNECTION_FAILED,
            "Failed to connect to YAWL engine at " + engineUrl,
            "Verify the YAWL engine is running and accessible.\n" +
            "Check that the username and password are correct.\n" +
            "Ensure the URL includes the context path (e.g., /yawl)",
            lastException
        );
    }

    /**
     * Disconnect from the YAWL engine
     */
    public synchronized void disconnect() {
        if (!connected) {
            return;
        }

        try {
            if (sessionHandleB != null) {
                interfaceBClient.disconnect(sessionHandleB);
            }
            if (sessionHandleA != null) {
                interfaceAClient.disconnect(sessionHandleA);
            }
        } catch (IOException e) {
            // Ignore disconnect errors
        } finally {
            sessionHandleB = null;
            sessionHandleA = null;
            connected = false;
        }
    }

    /**
     * Ensure connection is active
     *
     * @throws A2AException if not connected
     */
    public void ensureConnected() throws A2AException {
        if (!connected || sessionHandleB == null) {
            connect();
        }
    }

    /**
     * Check if connected
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected && sessionHandleB != null;
    }

    /**
     * Get engine URL
     *
     * @return the engine URL
     */
    public String getEngineUrl() {
        return engineUrl;
    }

    // ==================== Workflow Operations ====================

    /**
     * Launch a workflow case
     *
     * @param specId the specification ID (format: "identifier:version:uri" or just "identifier")
     * @param caseData optional case data in XML format
     * @return the launched case ID
     * @throws A2AException if launch fails
     */
    public String launchCase(String specId, String caseData) throws A2AException {
        ensureConnected();

        YSpecificationID ySpecId = parseSpecificationID(specId);
        String data = caseData != null ? wrapDataInXML(caseData) : null;

        try {
            String caseId = interfaceBClient.launchCase(ySpecId, data, sessionHandleB);

            if (caseId == null || caseId.contains("failure") || caseId.contains("error")) {
                throw new A2AException(
                    A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                    "Failed to launch case for specification " + specId + ": " + caseId,
                    "Verify the specification is loaded in the engine.\n" +
                    "Check that the specification ID format is correct."
                );
            }

            return caseId;

        } catch (IOException e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to communicate with YAWL engine while launching case",
                null,
                e
            );
        }
    }

    /**
     * Get all live work items
     *
     * @return list of work item records
     * @throws A2AException if retrieval fails
     */
    public List<WorkItemRecord> getWorkItems() throws A2AException {
        ensureConnected();

        try {
            List<WorkItemRecord> items = interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandleB);
            return items != null ? items : new ArrayList<>();

        } catch (IOException e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to retrieve work items from YAWL engine",
                null,
                e
            );
        }
    }

    /**
     * Get work items for a specific case
     *
     * @param caseId the case ID
     * @return list of work item records for the case
     * @throws A2AException if retrieval fails
     */
    public List<WorkItemRecord> getWorkItemsForCase(String caseId) throws A2AException {
        ensureConnected();

        try {
            List<WorkItemRecord> items = interfaceBClient.getWorkItemsForCase(caseId, sessionHandleB);
            return items != null ? items : new ArrayList<>();

        } catch (IOException e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to retrieve work items for case " + caseId,
                null,
                e
            );
        }
    }

    /**
     * Get a specific work item
     *
     * @param workItemId the work item ID
     * @return the work item XML, or null if not found
     * @throws A2AException if retrieval fails
     */
    public String getWorkItem(String workItemId) throws A2AException {
        ensureConnected();

        try {
            return interfaceBClient.getWorkItem(workItemId, sessionHandleB);

        } catch (IOException e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to retrieve work item " + workItemId,
                null,
                e
            );
        }
    }

    /**
     * Check out a work item
     *
     * @param workItemId the work item ID
     * @return the checked out work item data
     * @throws A2AException if checkout fails
     */
    public String checkOutWorkItem(String workItemId) throws A2AException {
        ensureConnected();

        try {
            String result = interfaceBClient.checkOutWorkItem(workItemId, sessionHandleB);

            if (result == null || result.contains("failure") || result.contains("error")) {
                throw new A2AException(
                    A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                    "Failed to check out work item " + workItemId + ": " + result,
                    "The work item may already be checked out or no longer available."
                );
            }

            return result;

        } catch (IOException e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to check out work item " + workItemId,
                null,
                e
            );
        }
    }

    /**
     * Check in a work item
     *
     * @param workItemId the work item ID
     * @param outputData optional output data in XML format
     * @throws A2AException if checkin fails
     */
    public void checkInWorkItem(String workItemId, String outputData) throws A2AException {
        ensureConnected();

        String data = outputData != null ? wrapDataInXML(outputData) : null;

        try {
            String result = interfaceBClient.checkInWorkItem(workItemId, data, sessionHandleB);

            if (result == null || !result.contains("success")) {
                throw new A2AException(
                    A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                    "Failed to check in work item " + workItemId + ": " + result,
                    "Verify the output data format matches the task's output parameters."
                );
            }

        } catch (IOException e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to check in work item " + workItemId,
                null,
                e
            );
        }
    }

    /**
     * Complete a task (checkout + checkin)
     *
     * @param caseId the case ID
     * @param taskId the task ID
     * @param outputData optional output data
     * @return completion status
     * @throws A2AException if completion fails
     */
    public Map<String, Object> completeTask(String caseId, String taskId, String outputData) throws A2AException {
        ensureConnected();

        // Find the work item
        List<WorkItemRecord> workItems = getWorkItemsForCase(caseId);
        WorkItemRecord targetItem = null;

        for (WorkItemRecord item : workItems) {
            if (item.getTaskID().equals(taskId)) {
                targetItem = item;
                break;
            }
        }

        if (targetItem == null) {
            throw new A2AException(
                A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                "Task " + taskId + " not found in case " + caseId,
                "Verify the task ID is correct and the case is still active."
            );
        }

        String workItemId = targetItem.getID();

        // Check out
        checkOutWorkItem(workItemId);

        // Check in with data
        String data = outputData != null ? outputData : targetItem.getDataList();
        checkInWorkItem(workItemId, data);

        Map<String, Object> result = new HashMap<>();
        result.put("caseId", caseId);
        result.put("taskId", taskId);
        result.put("workItemId", workItemId);
        result.put("status", "completed");

        return result;
    }

    /**
     * Get case data
     *
     * @param caseId the case ID
     * @return the case data as XML
     * @throws A2AException if retrieval fails
     */
    public String getCaseData(String caseId) throws A2AException {
        ensureConnected();

        try {
            return interfaceBClient.getCaseData(caseId, sessionHandleB);

        } catch (IOException e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to get case data for " + caseId,
                null,
                e
            );
        }
    }

    /**
     * Cancel a case
     *
     * @param caseId the case ID to cancel
     * @throws A2AException if cancellation fails
     */
    public void cancelCase(String caseId) throws A2AException {
        ensureConnected();

        try {
            String result = interfaceBClient.cancelCase(caseId, sessionHandleB);

            if (result == null || result.contains("failure") || result.contains("error")) {
                throw new A2AException(
                    A2AException.ErrorCode.TASK_EXECUTION_FAILED,
                    "Failed to cancel case " + caseId + ": " + result,
                    "The case may already be completed or canceled."
                );
            }

        } catch (IOException e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to cancel case " + caseId,
                null,
                e
            );
        }
    }

    /**
     * Get list of loaded specifications
     *
     * @return list of specification names
     * @throws A2AException if retrieval fails
     */
    public List<String> getSpecifications() throws A2AException {
        ensureInterfaceA();

        try {
            String specsXML = interfaceAClient.getLoadedSpecificationData(sessionHandleA);
            return extractSpecificationNames(specsXML);

        } catch (IOException e) {
            throw new A2AException(
                A2AException.ErrorCode.CONNECTION_FAILED,
                "Failed to retrieve specifications from YAWL engine",
                null,
                e
            );
        }
    }

    /**
     * Get engine session handle
     *
     * @return the session handle
     */
    public String getSessionHandle() {
        return sessionHandleB;
    }

    // ==================== Private Methods ====================

    private synchronized void ensureInterfaceA() throws A2AException {
        if (sessionHandleA == null) {
            try {
                sessionHandleA = interfaceAClient.connect(username, password);

                if (sessionHandleA == null || sessionHandleA.contains("failure") || sessionHandleA.contains("error")) {
                    throw new IOException("Interface A connection failed: " + sessionHandleA);
                }

            } catch (IOException e) {
                throw new A2AException(
                    A2AException.ErrorCode.CONNECTION_FAILED,
                    "Failed to connect to YAWL Interface A",
                    null,
                    e
                );
            }
        }
    }

    private YSpecificationID parseSpecificationID(String specId) {
        String[] parts = specId.split(":");
        if (parts.length == 3) {
            return new YSpecificationID(parts[0], parts[1], parts[2]);
        } else if (parts.length == 1) {
            return new YSpecificationID(parts[0], "0.1", "0.1");
        } else {
            throw new IllegalArgumentException(
                "Invalid specification ID format. Use 'identifier:version:uri' or just 'identifier'.\n" +
                "Example: 'OrderProcessing:1.0:http://example.com/orders' or just 'OrderProcessing'"
            );
        }
    }

    private String wrapDataInXML(String data) {
        if (data == null) {
            return null;
        }
        String trimmed = data.trim();
        if (trimmed.startsWith("<")) {
            return trimmed;
        }
        return "<data>" + trimmed + "</data>";
    }

    private List<String> extractSpecificationNames(String specsXML) {
        List<String> names = new ArrayList<>();

        if (specsXML == null || specsXML.isEmpty()) {
            return names;
        }

        int pos = 0;
        while (true) {
            int specStart = specsXML.indexOf("<specIdentifier>", pos);
            if (specStart == -1) break;

            int specEnd = specsXML.indexOf("</specIdentifier>", specStart);
            if (specEnd == -1) break;

            String specContent = specsXML.substring(specStart + 16, specEnd);
            names.add(specContent.trim());

            pos = specEnd;
        }

        return names;
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        System.out.println("YAWL Engine Adapter Test");
        System.out.println("========================");

        YawlEngineAdapter adapter;
        try {
            adapter = YawlEngineAdapter.fromEnvironment();
        } catch (IllegalStateException e) {
            System.err.println("Configuration error: " + e.getMessage());
            System.err.println("\nSet the required environment variables:");
            System.err.println("  export YAWL_ENGINE_URL=http://localhost:8080/yawl");
            System.err.println("  export YAWL_USERNAME=admin");
            System.err.println("  export YAWL_PASSWORD=YAWL");
            return;
        }

        System.out.println("Engine URL: " + adapter.getEngineUrl());
        System.out.println();

        // Test connection
        System.out.println("Testing connection...");
        try {
            adapter.connect();
            System.out.println("Connection: SUCCESS");

            // Get specifications
            System.out.println("\nFetching specifications...");
            List<String> specs = adapter.getSpecifications();
            System.out.println("Loaded specifications (" + specs.size() + "):");
            for (String spec : specs) {
                System.out.println("  - " + spec);
            }

            // Get work items
            System.out.println("\nFetching work items...");
            List<WorkItemRecord> items = adapter.getWorkItems();
            System.out.println("Active work items: " + items.size());

            adapter.disconnect();
            System.out.println("\nDisconnected.");

        } catch (A2AException e) {
            System.err.println("Error: " + e.getFullReport());
        }
    }
}
