package org.yawlfoundation.yawl.integration.a2a;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * YAWL Agent Card Producer
 *
 * Creates the AgentCard that describes YAWL workflow engine capabilities.
 * Exposes YAWL operations as A2A skills.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlAgentCard {

    private static final String AGENT_NAME = "YAWL Workflow Engine";
    private static final String AGENT_DESCRIPTION =
        "Yet Another Workflow Language (YAWL) engine for business process management. " +
        "Supports workflow execution, task management, and process monitoring.";
    private static final String PROTOCOL_VERSION = A2ATypes.A2A_PROTOCOL_VERSION;

    private final String serverUrl;
    private final String version;

    /**
     * Create YAWL Agent Card producer
     *
     * @param serverUrl the base URL of this A2A server
     * @param version the YAWL version
     */
    public YawlAgentCard(String serverUrl, String version) {
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Server URL is required");
        }
        this.serverUrl = serverUrl;
        this.version = version != null ? version : "5.2";
    }

    /**
     * Create the Agent Card
     *
     * @return the complete agent card with all YAWL capabilities
     */
    public A2ATypes.AgentCard createCard() {
        A2ATypes.AgentCapabilities capabilities = new A2ATypes.AgentCapabilities(
            false,  // streaming not supported in initial version
            false   // push notifications not supported in initial version
        );

        List<A2ATypes.AgentSkill> skills = createSkills();
        A2ATypes.AuthenticationInfo auth = new A2ATypes.AuthenticationInfo(
            "http-header",
            Arrays.asList("Bearer", "Basic")
        );

        return new A2ATypes.AgentCard(
            AGENT_NAME,
            AGENT_DESCRIPTION,
            serverUrl,
            version,
            capabilities,
            skills,
            PROTOCOL_VERSION,
            auth
        );
    }

    /**
     * Create the list of YAWL skills
     *
     * @return list of available skills
     */
    private List<A2ATypes.AgentSkill> createSkills() {
        List<A2ATypes.AgentSkill> skills = new ArrayList<>();

        // Core workflow operations
        skills.add(createLaunchCaseSkill());
        skills.add(createGetWorkItemsSkill());
        skills.add(createGetCaseDataSkill());
        skills.add(createCompleteTaskSkill());
        skills.add(createCancelCaseSkill());
        skills.add(createGetSpecificationsSkill());

        // Work item operations
        skills.add(createCheckoutWorkItemSkill());
        skills.add(createCheckinWorkItemSkill());

        // Status operations
        skills.add(createGetCaseStatusSkill());

        return Collections.unmodifiableList(skills);
    }

    /**
     * Get all available skill IDs
     *
     * @return list of skill IDs
     */
    public List<String> getSkillIds() {
        return Arrays.asList(
            "launchCase",
            "getWorkItems",
            "getCaseData",
            "completeTask",
            "cancelCase",
            "getSpecifications",
            "checkOutWorkItem",
            "checkInWorkItem",
            "getCaseStatus"
        );
    }

    // ==================== Skill Definitions ====================

    private A2ATypes.AgentSkill createLaunchCaseSkill() {
        return new A2ATypes.AgentSkill(
            "launchCase",
            "Launch Workflow Case",
            "Start a new workflow case from a loaded specification. " +
            "Returns the case ID for tracking the workflow execution.",
            Arrays.asList("workflow", "start", "launch", "case"),
            Arrays.asList(
                "Launch OrderProcessing workflow",
                "Start approval workflow with data"
            ),
            null
        );
    }

    private A2ATypes.AgentSkill createGetWorkItemsSkill() {
        return new A2ATypes.AgentSkill(
            "getWorkItems",
            "Get Work Items",
            "Retrieve active work items from the YAWL engine. " +
            "Can filter by case ID to get items for a specific workflow instance.",
            Arrays.asList("workitems", "tasks", "active", "pending"),
            Arrays.asList(
                "Get all active work items",
                "Get work items for case case-12345"
            ),
            null
        );
    }

    private A2ATypes.AgentSkill createGetCaseDataSkill() {
        return new A2ATypes.AgentSkill(
            "getCaseData",
            "Get Case Data",
            "Retrieve the current data state of a workflow case. " +
            "Returns the case data in XML format.",
            Arrays.asList("casedata", "data", "state", "variables"),
            Arrays.asList(
                "Get data for case case-12345"
            ),
            null
        );
    }

    private A2ATypes.AgentSkill createCompleteTaskSkill() {
        return new A2ATypes.AgentSkill(
            "completeTask",
            "Complete Task",
            "Complete a workflow task by checking out the work item, " +
            "optionally updating data, and checking it back in.",
            Arrays.asList("complete", "finish", "task", "done"),
            Arrays.asList(
                "Complete task Approval in case case-12345",
                "Complete task with output data"
            ),
            null
        );
    }

    private A2ATypes.AgentSkill createCancelCaseSkill() {
        return new A2ATypes.AgentSkill(
            "cancelCase",
            "Cancel Case",
            "Cancel an active workflow case. " +
            "This will terminate the case and release all associated resources.",
            Arrays.asList("cancel", "terminate", "abort", "stop"),
            Arrays.asList(
                "Cancel case case-12345"
            ),
            null
        );
    }

    private A2ATypes.AgentSkill createGetSpecificationsSkill() {
        return new A2ATypes.AgentSkill(
            "getSpecifications",
            "Get Specifications",
            "Retrieve the list of loaded workflow specifications. " +
            "Use these specification names to launch new workflow cases.",
            Arrays.asList("specifications", "specs", "workflows", "available"),
            Arrays.asList(
                "List all available workflows"
            ),
            null
        );
    }

    private A2ATypes.AgentSkill createCheckoutWorkItemSkill() {
        return new A2ATypes.AgentSkill(
            "checkOutWorkItem",
            "Check Out Work Item",
            "Check out a work item for processing. " +
            "This reserves the work item and returns its current data.",
            Arrays.asList("checkout", "reserve", "claim", "lock"),
            Arrays.asList(
                "Check out work item item-67890"
            ),
            null
        );
    }

    private A2ATypes.AgentSkill createCheckinWorkItemSkill() {
        return new A2ATypes.AgentSkill(
            "checkInWorkItem",
            "Check In Work Item",
            "Check in a previously checked out work item. " +
            "This completes the work item and advances the workflow.",
            Arrays.asList("checkin", "submit", "complete", "release"),
            Arrays.asList(
                "Check in work item item-67890 with data"
            ),
            null
        );
    }

    private A2ATypes.AgentSkill createGetCaseStatusSkill() {
        return new A2ATypes.AgentSkill(
            "getCaseStatus",
            "Get Case Status",
            "Get the current status of a workflow case including " +
            "active tasks and execution state.",
            Arrays.asList("status", "state", "progress", "running"),
            Arrays.asList(
                "Get status of case case-12345"
            ),
            null
        );
    }

    /**
     * Main method for testing
     */
    public static void main(String[] args) {
        String serverUrl = args.length > 0 ? args[0] : "http://localhost:8082";
        String version = args.length > 1 ? args[1] : "5.2";

        System.out.println("YAWL Agent Card Generator");
        System.out.println("=========================");
        System.out.println();

        YawlAgentCard producer = new YawlAgentCard(serverUrl, version);
        A2ATypes.AgentCard card = producer.createCard();

        System.out.println("Agent Card JSON:");
        System.out.println(card.toJson());

        System.out.println("\n\nAgent Card Summary:");
        System.out.println("  Name: " + card.getName());
        System.out.println("  URL: " + card.getUrl());
        System.out.println("  Version: " + card.getVersion());
        System.out.println("  Protocol: " + card.getProtocolVersion());
        System.out.println("  Skills (" + card.getSkills().size() + "):");
        for (A2ATypes.AgentSkill skill : card.getSkills()) {
            System.out.println("    - " + skill.getId() + ": " + skill.getName());
        }
    }
}
