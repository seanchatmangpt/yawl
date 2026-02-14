/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.mcp;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.List;

/**
 * MCP Resource Provider for YAWL workflow data.
 *
 * This class provides access to YAWL workflow resources through the Model Context Protocol (MCP).
 * It uses InterfaceB_EnvironmentBasedClient for real data fetching from the YAWL Engine.
 *
 * Supported resource URIs:
 * - specification://{spec_id} - Get specification details and schema
 * - case://{case_id} - Get case data and status
 * - workitem://{work_item_id} - Get work item data
 * - task://{spec_id}/{task_id} - Get task definition and parameters
 * - schema://{spec_id}/{task_id} - Get input/output schema for task
 * - cases://running - List all running cases
 * - cases://completed - List completed cases (requires case log access)
 * - specifications://loaded - List loaded specifications
 *
 * Environment Configuration:
 * - YAWL_ENGINE_URL: YAWL Engine Interface B endpoint (default: http://localhost:8080/yawl/ib)
 * - YAWL_USERNAME: Username for authentication (default: admin)
 * - YAWL_PASSWORD: Password for authentication (default: YAWL)
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpResourceProvider {

    private final InterfaceB_EnvironmentBasedClient client;
    private final String engineUrl;
    private final String username;
    private final String password;
    private String sessionHandle;

    /**
     * Constructor with environment-based configuration.
     * Reads configuration from environment variables:
     * - YAWL_ENGINE_URL (default: http://localhost:8080/yawl/ib)
     * - YAWL_USERNAME (default: admin)
     * - YAWL_PASSWORD (default: YAWL)
     */
    public YawlMcpResourceProvider() {
        this(
            getEnvOrDefault("YAWL_ENGINE_URL", "http://localhost:8080/yawl/ib"),
            getEnvOrDefault("YAWL_USERNAME", "admin"),
            getEnvOrDefault("YAWL_PASSWORD", "YAWL")
        );
    }

    /**
     * Constructor with explicit configuration.
     *
     * @param engineUrl the YAWL Engine Interface B endpoint URL
     * @param username the username for authentication
     * @param password the password for authentication
     */
    public YawlMcpResourceProvider(String engineUrl, String username, String password) {
        if (engineUrl == null || engineUrl.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "YAWL Engine URL is required. Set YAWL_ENGINE_URL environment variable or pass explicitly."
            );
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Username is required. Set YAWL_USERNAME environment variable or pass explicitly."
            );
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Password is required. Set YAWL_PASSWORD environment variable or pass explicitly."
            );
        }

        this.engineUrl = engineUrl;
        this.username = username;
        this.password = password;
        this.client = new InterfaceB_EnvironmentBasedClient(engineUrl);
        this.sessionHandle = null;
    }

    /**
     * Ensures an active session with the YAWL Engine.
     * Connects if not already connected or if session is invalid.
     *
     * @throws IOException if connection fails
     */
    private void ensureConnected() throws IOException {
        if (sessionHandle == null || !isSessionValid()) {
            sessionHandle = client.connect(username, password);
            if (sessionHandle == null || sessionHandle.contains("Failure") ||
                sessionHandle.contains("Exception")) {
                throw new IOException(
                    "Failed to connect to YAWL Engine at " + engineUrl + ": " + sessionHandle
                );
            }
        }
    }

    /**
     * Checks if the current session is valid.
     *
     * @return true if session is valid, false otherwise
     */
    private boolean isSessionValid() {
        if (sessionHandle == null) {
            return false;
        }
        try {
            String result = client.checkConnection(sessionHandle);
            return result != null && !result.contains("Failure") && !result.contains("Exception");
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Disconnects from the YAWL Engine.
     *
     * @throws IOException if disconnection fails
     */
    public void disconnect() throws IOException {
        if (sessionHandle != null) {
            client.disconnect(sessionHandle);
            sessionHandle = null;
        }
    }

    /**
     * Gets specification details and schema for a given specification ID.
     * Resource URI: specification://{spec_id}
     *
     * @param specId the specification identifier
     * @return XML representation of the specification
     * @throws IOException if engine connection fails or specification not found
     */
    public String getSpecification(String specId) throws IOException {
        ensureConnected();

        YSpecificationID specID = new YSpecificationID(specId);
        String specXML = client.getSpecification(specID, sessionHandle);

        if (specXML == null || specXML.contains("Failure") || specXML.contains("Exception")) {
            throw new IOException("Failed to retrieve specification: " + specId + ". Error: " + specXML);
        }

        return specXML;
    }

    /**
     * Gets specification data schema for a given specification ID.
     * This returns the user-defined data schema for the specification.
     *
     * @param specId the specification identifier
     * @return XML representation of the data schema
     * @throws IOException if engine connection fails or specification not found
     */
    public String getSpecificationSchema(String specId) throws IOException {
        ensureConnected();

        YSpecificationID specID = new YSpecificationID(specId);
        String schema = client.getSpecificationDataSchema(specID, sessionHandle);

        if (schema == null || schema.contains("Failure") || schema.contains("Exception")) {
            throw new IOException("Failed to retrieve specification schema: " + specId + ". Error: " + schema);
        }

        return schema;
    }

    /**
     * Gets case data and status for a given case ID.
     * Resource URI: case://{case_id}
     *
     * @param caseId the case identifier
     * @return XML representation of the case data and state
     * @throws IOException if engine connection fails or case not found
     */
    public String getCaseData(String caseId) throws IOException {
        ensureConnected();

        String caseData = client.getCaseData(caseId, sessionHandle);

        if (caseData == null || caseData.contains("Failure") || caseData.contains("Exception")) {
            throw new IOException("Failed to retrieve case data: " + caseId + ". Error: " + caseData);
        }

        return caseData;
    }

    /**
     * Gets case state for a given case ID.
     *
     * @param caseId the case identifier
     * @return XML representation of the case state
     * @throws IOException if engine connection fails or case not found
     */
    public String getCaseState(String caseId) throws IOException {
        ensureConnected();

        String caseState = client.getCaseState(caseId, sessionHandle);

        if (caseState == null || caseState.contains("Failure") || caseState.contains("Exception")) {
            throw new IOException("Failed to retrieve case state: " + caseId + ". Error: " + caseState);
        }

        return caseState;
    }

    /**
     * Gets work item data for a given work item ID.
     * Resource URI: workitem://{work_item_id}
     *
     * @param workItemId the work item identifier
     * @return XML representation of the work item
     * @throws IOException if engine connection fails or work item not found
     */
    public String getWorkItem(String workItemId) throws IOException {
        ensureConnected();

        String workItem = client.getWorkItem(workItemId, sessionHandle);

        if (workItem == null || workItem.contains("Failure") || workItem.contains("Exception")) {
            throw new IOException("Failed to retrieve work item: " + workItemId + ". Error: " + workItem);
        }

        return workItem;
    }

    /**
     * Gets task definition and parameters for a given specification and task ID.
     * Resource URI: task://{spec_id}/{task_id}
     *
     * @param specId the specification identifier
     * @param taskId the task identifier
     * @return XML representation of the task information
     * @throws IOException if engine connection fails or task not found
     */
    public String getTaskInformation(String specId, String taskId) throws IOException {
        ensureConnected();

        YSpecificationID specID = new YSpecificationID(specId);
        String taskInfo = client.getTaskInformationStr(specID, taskId, sessionHandle);

        if (taskInfo == null || taskInfo.contains("Failure") || taskInfo.contains("Exception")) {
            throw new IOException(
                "Failed to retrieve task information for spec: " + specId +
                ", task: " + taskId + ". Error: " + taskInfo
            );
        }

        return taskInfo;
    }

    /**
     * Gets input/output schema for a specific task.
     * Resource URI: schema://{spec_id}/{task_id}
     *
     * This extracts the task parameters from the task information.
     *
     * @param specId the specification identifier
     * @param taskId the task identifier
     * @return XML representation of the task schema
     * @throws IOException if engine connection fails or task not found
     */
    public String getTaskSchema(String specId, String taskId) throws IOException {
        ensureConnected();

        String taskInfo = getTaskInformation(specId, taskId);
        return taskInfo;
    }

    /**
     * Lists all running cases in the engine.
     * Resource URI: cases://running
     *
     * @return XML representation of all running cases
     * @throws IOException if engine connection fails
     */
    public String getAllRunningCases() throws IOException {
        ensureConnected();

        String runningCases = client.getAllRunningCases(sessionHandle);

        if (runningCases == null || runningCases.contains("Failure") ||
            runningCases.contains("Exception")) {
            throw new IOException("Failed to retrieve running cases. Error: " + runningCases);
        }

        return runningCases;
    }

    /**
     * Lists completed cases.
     * Resource URI: cases://completed
     *
     * Note: This returns an empty list as InterfaceB does not provide direct access
     * to completed cases without case log queries. To get completed cases, you would need
     * to query the case log database directly or use the logging/auditing service.
     *
     * @return empty result indicating completed cases require log access
     * @throws IOException if this operation is attempted
     */
    public String getCompletedCases() throws IOException {
        throw new UnsupportedOperationException(
            "Completed cases require access to the YAWL case log.\n" +
            "To retrieve completed cases:\n" +
            "  1. Query the YAWL database directly (table: caselog)\n" +
            "  2. Use the YAWL Monitor Service API\n" +
            "  3. Export all case states and filter by completion status\n" +
            "Alternative: Use exportAllCaseStates() to get all cases and filter by status."
        );
    }

    /**
     * Lists all loaded specifications.
     * Resource URI: specifications://loaded
     *
     * @return list of SpecificationData objects for loaded specifications
     * @throws IOException if engine connection fails
     */
    public List<SpecificationData> getLoadedSpecifications() throws IOException {
        ensureConnected();

        List<SpecificationData> specs = client.getSpecificationList(sessionHandle);

        if (specs == null) {
            throw new IOException("Failed to retrieve specification list.");
        }

        return specs;
    }

    /**
     * Lists all loaded specifications as XML.
     *
     * @return XML representation of all loaded specifications
     * @throws IOException if engine connection fails
     */
    public String getLoadedSpecificationsAsXML() throws IOException {
        List<SpecificationData> specs = getLoadedSpecifications();

        StringBuilder xml = new StringBuilder("<specifications>");
        for (SpecificationData spec : specs) {
            xml.append("<specification>");

            String id = escapeXml(spec.getID());
            if (id != null) {
                xml.append("<identifier>").append(id).append("</identifier>");
            }

            String name = escapeXml(spec.getName());
            if (name != null) {
                xml.append("<name>").append(name).append("</name>");
            }

            String version = escapeXml(spec.getSchemaVersion());
            if (version != null) {
                xml.append("<version>").append(version).append("</version>");
            }

            String uri = escapeXml(spec.getURI());
            if (uri != null) {
                xml.append("<uri>").append(uri).append("</uri>");
            }

            String status = escapeXml(spec.getStatus());
            if (status != null) {
                xml.append("<status>").append(status).append("</status>");
            }

            if (spec.getDocumentation() != null) {
                String doc = escapeXml(spec.getDocumentation());
                if (doc != null) {
                    xml.append("<documentation>").append(doc).append("</documentation>");
                }
            }

            xml.append("</specification>");
        }
        xml.append("</specifications>");

        return xml.toString();
    }

    /**
     * Gets work items for a specific case.
     *
     * @param caseId the case identifier
     * @return list of WorkItemRecord objects for the case
     * @throws IOException if engine connection fails
     */
    public List<WorkItemRecord> getWorkItemsForCase(String caseId) throws IOException {
        ensureConnected();

        List<WorkItemRecord> workItems = client.getWorkItemsForCase(caseId, sessionHandle);

        if (workItems == null) {
            throw new IOException("Failed to retrieve work items for case: " + caseId);
        }

        return workItems;
    }

    /**
     * Gets all live work items in the engine.
     *
     * @return list of all active WorkItemRecord objects
     * @throws IOException if engine connection fails
     */
    public List<WorkItemRecord> getAllLiveWorkItems() throws IOException {
        ensureConnected();

        List<WorkItemRecord> workItems = client.getCompleteListOfLiveWorkItems(sessionHandle);

        if (workItems == null) {
            throw new IOException("Failed to retrieve live work items.");
        }

        return workItems;
    }

    /**
     * Retrieves a resource by its URI.
     * This is the main entry point for MCP resource requests.
     *
     * Supported URI schemes:
     * - specification://{spec_id}
     * - case://{case_id}
     * - workitem://{work_item_id}
     * - task://{spec_id}/{task_id}
     * - schema://{spec_id}/{task_id}
     * - cases://running
     * - cases://completed
     * - specifications://loaded
     *
     * @param resourceUri the resource URI
     * @return the resource content as a string
     * @throws IOException if the resource cannot be retrieved
     */
    public String getResource(String resourceUri) throws IOException {
        if (resourceUri == null || resourceUri.trim().isEmpty()) {
            throw new IllegalArgumentException("Resource URI cannot be null or empty");
        }

        if (resourceUri.startsWith("specification://")) {
            String specId = resourceUri.substring("specification://".length());
            return getSpecification(specId);
        } else if (resourceUri.startsWith("case://")) {
            String caseId = resourceUri.substring("case://".length());
            return getCaseData(caseId);
        } else if (resourceUri.startsWith("workitem://")) {
            String workItemId = resourceUri.substring("workitem://".length());
            return getWorkItem(workItemId);
        } else if (resourceUri.startsWith("task://")) {
            String path = resourceUri.substring("task://".length());
            String[] parts = path.split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                    "Invalid task URI format. Expected: task://{spec_id}/{task_id}"
                );
            }
            return getTaskInformation(parts[0], parts[1]);
        } else if (resourceUri.startsWith("schema://")) {
            String path = resourceUri.substring("schema://".length());
            String[] parts = path.split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                    "Invalid schema URI format. Expected: schema://{spec_id}/{task_id}"
                );
            }
            return getTaskSchema(parts[0], parts[1]);
        } else if (resourceUri.equals("cases://running")) {
            return getAllRunningCases();
        } else if (resourceUri.equals("cases://completed")) {
            return getCompletedCases();
        } else if (resourceUri.equals("specifications://loaded")) {
            return getLoadedSpecificationsAsXML();
        } else {
            throw new IllegalArgumentException(
                "Unsupported resource URI: " + resourceUri + "\n" +
                "Supported URIs:\n" +
                "  - specification://{spec_id}\n" +
                "  - case://{case_id}\n" +
                "  - workitem://{work_item_id}\n" +
                "  - task://{spec_id}/{task_id}\n" +
                "  - schema://{spec_id}/{task_id}\n" +
                "  - cases://running\n" +
                "  - cases://completed\n" +
                "  - specifications://loaded"
            );
        }
    }

    /**
     * Helper method to get environment variable with default value.
     *
     * @param envVar the environment variable name
     * @param defaultValue the default value if not set
     * @return the environment variable value or default
     */
    private static String getEnvOrDefault(String envVar, String defaultValue) {
        String value = System.getenv(envVar);
        return (value != null && !value.trim().isEmpty()) ? value : defaultValue;
    }

    /**
     * Helper method to escape XML special characters.
     *
     * @param text the text to escape
     * @return XML-escaped text, or null if input is null
     */
    private String escapeXml(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
