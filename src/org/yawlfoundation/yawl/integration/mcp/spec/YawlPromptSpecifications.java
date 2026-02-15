package org.yawlfoundation.yawl.integration.mcp.spec;

import io.modelcontextprotocol.spec.GetPromptResult;
import io.modelcontextprotocol.spec.Prompt;
import io.modelcontextprotocol.spec.PromptArgument;
import io.modelcontextprotocol.spec.PromptMessage;
import io.modelcontextprotocol.spec.Role;
import io.modelcontextprotocol.spec.SyncPromptSpecification;
import io.modelcontextprotocol.spec.TextContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * YAWL Prompt Specifications for MCP.
 *
 * Defines prompt templates for AI-assisted workflow interactions.
 * These prompts guide AI models in working with YAWL workflows.
 *
 * Available Prompts:
 * - yawl_start_workflow: Guide for starting a new workflow
 * - yawl_task_execution: Guide for executing workflow tasks
 * - yawl_exception_handling: Guide for handling workflow exceptions
 * - yawl_status_check: Guide for checking workflow status
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlPromptSpecifications {

    /**
     * Gets all available prompt specifications.
     *
     * @return list of all prompt specifications
     */
    public static List<SyncPromptSpecification> getAllPrompts() {
        List<SyncPromptSpecification> prompts = new ArrayList<>();

        prompts.add(createWorkflowStartPrompt());
        prompts.add(createTaskExecutionPrompt());
        prompts.add(createExceptionHandlingPrompt());
        prompts.add(createStatusCheckPrompt());

        return prompts;
    }

    /**
     * Creates the workflow start prompt.
     */
    private static SyncPromptSpecification createWorkflowStartPrompt() {
        Prompt prompt = new Prompt(
                "yawl_start_workflow",
                "Start a YAWL workflow with guidance on parameters and options",
                List.of(
                        new PromptArgument("specName", "Name of the workflow specification to start", true),
                        new PromptArgument("caseData", "Input data for the workflow case (JSON format)", false),
                        new PromptArgument("delayMs", "Optional delay before starting (milliseconds)", false)
                )
        );

        return new SyncPromptSpecification(prompt, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String specName = args != null ? (String) args.get("specName") : "UnknownWorkflow";
            String caseData = args != null ? (String) args.get("caseData") : "{}";

            StringBuilder instruction = new StringBuilder();
            instruction.append("You are starting a YAWL workflow. Follow these steps:\n\n");
            instruction.append("1. First, use the 'yawl_list_specs' tool to verify '").append(specName).append("' exists.\n");
            instruction.append("2. Review the specification to understand required input parameters.\n");
            instruction.append("3. Prepare the case data in the correct format:\n");
            instruction.append("   ").append(caseData).append("\n");
            instruction.append("4. Use 'yawl_launch_case' to start the workflow.\n");
            instruction.append("5. Record the returned case ID for future reference.\n\n");
            instruction.append("Best practices:\n");
            instruction.append("- Validate input data matches the specification schema\n");
            instruction.append("- Handle potential errors gracefully\n");
            instruction.append("- Log the case ID for tracking purposes\n");

            List<PromptMessage> messages = List.of(
                    new PromptMessage(Role.USER,
                            new TextContent("I need to start the " + specName + " workflow with data: " + caseData)),
                    new PromptMessage(Role.ASSISTANT,
                            new TextContent(instruction.toString()))
            );

            return new GetPromptResult("Start YAWL Workflow: " + specName, messages);
        });
    }

    /**
     * Creates the task execution prompt.
     */
    private static SyncPromptSpecification createTaskExecutionPrompt() {
        Prompt prompt = new Prompt(
                "yawl_task_execution",
                "Execute a workflow task with proper checkout and checkin flow",
                List.of(
                        new PromptArgument("workItemId", "ID of the work item to execute", true),
                        new PromptArgument("taskData", "Output data for the task completion (JSON)", false)
                )
        );

        return new SyncPromptSpecification(prompt, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String workItemId = args != null ? (String) args.get("workItemId") : "unknown-item";
            String taskData = args != null ? (String) args.get("taskData") : "{}";

            StringBuilder instruction = new StringBuilder();
            instruction.append("You are executing a YAWL workflow task. Follow this workflow:\n\n");
            instruction.append("1. Get work item details using the work item ID: ").append(workItemId).append("\n");
            instruction.append("2. Checkout the work item using 'yawl_checkout_workitem'.\n");
            instruction.append("3. Perform the task logic and prepare output data:\n");
            instruction.append("   ").append(taskData).append("\n");
            instruction.append("4. Checkin the work item with output data using 'yawl_checkin_workitem'.\n\n");
            instruction.append("Error handling:\n");
            instruction.append("- If checkout fails, the item may already be claimed\n");
            instruction.append("- If checkin fails, validate data format\n");
            instruction.append("- Use 'yawl_skip_workitem' if the task cannot be completed\n");
            instruction.append("- Use 'yawl_suspend_workitem' to temporarily pause\n");

            List<PromptMessage> messages = List.of(
                    new PromptMessage(Role.USER,
                            new TextContent("Execute work item " + workItemId + " with output: " + taskData)),
                    new PromptMessage(Role.ASSISTANT,
                            new TextContent(instruction.toString()))
            );

            return new GetPromptResult("Execute YAWL Task: " + workItemId, messages);
        });
    }

    /**
     * Creates the exception handling prompt.
     */
    private static SyncPromptSpecification createExceptionHandlingPrompt() {
        Prompt prompt = new Prompt(
                "yawl_exception_handling",
                "Handle workflow exceptions and errors",
                List.of(
                        new PromptArgument("caseId", "ID of the case with the exception", true),
                        new PromptArgument("errorType", "Type of error encountered", false),
                        new PromptArgument("context", "Additional context about the error", false)
                )
        );

        return new SyncPromptSpecification(prompt, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String caseId = args != null ? (String) args.get("caseId") : "unknown-case";
            String errorType = args != null ? (String) args.get("errorType") : "unknown";
            String context = args != null ? (String) args.get("context") : "No additional context";

            StringBuilder instruction = new StringBuilder();
            instruction.append("You are handling a YAWL workflow exception. Follow these steps:\n\n");
            instruction.append("Case ID: ").append(caseId).append("\n");
            instruction.append("Error Type: ").append(errorType).append("\n");
            instruction.append("Context: ").append(context).append("\n\n");
            instruction.append("Resolution options:\n");
            instruction.append("1. Get case status using 'yawl_get_case_status'\n");
            instruction.append("2. Get work items for the case using 'yawl_get_workitems_case'\n");
            instruction.append("3. Based on the error type:\n");
            instruction.append("   - Task failure: Skip or retry the work item\n");
            instruction.append("   - Timeout: Check if work item is still valid\n");
            instruction.append("   - Data error: Validate and correct input data\n");
            instruction.append("4. If unrecoverable, use 'yawl_cancel_case' to terminate\n\n");
            instruction.append("Always log the resolution for audit purposes.\n");

            List<PromptMessage> messages = List.of(
                    new PromptMessage(Role.USER,
                            new TextContent("Handle exception for case " + caseId + ": " + errorType)),
                    new PromptMessage(Role.ASSISTANT,
                            new TextContent(instruction.toString()))
            );

            return new GetPromptResult("Handle YAWL Exception: " + caseId, messages);
        });
    }

    /**
     * Creates the status check prompt.
     */
    private static SyncPromptSpecification createStatusCheckPrompt() {
        Prompt prompt = new Prompt(
                "yawl_status_check",
                "Check and report on workflow status",
                List.of(
                        new PromptArgument("caseId", "ID of the case to check (optional - omit for all)", false),
                        new PromptArgument("includeWorkItems", "Include work item details (true/false)", false)
                )
        );

        return new SyncPromptSpecification(prompt, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String caseId = args != null ? (String) args.get("caseId") : null;
            boolean includeWorkItems = args != null && Boolean.TRUE.equals(args.get("includeWorkItems"));

            StringBuilder instruction = new StringBuilder();
            instruction.append("You are checking YAWL workflow status.\n\n");

            if (caseId != null && !caseId.isEmpty()) {
                instruction.append("Specific case: ").append(caseId).append("\n\n");
                instruction.append("Steps:\n");
                instruction.append("1. Use 'yawl_get_case_status' to get current state\n");
                instruction.append("2. Use 'yawl_get_case_data' to get current data\n");
                if (includeWorkItems) {
                    instruction.append("3. Use 'yawl_get_workitems_case' to list work items\n");
                }
            } else {
                instruction.append("All cases:\n\n");
                instruction.append("Steps:\n");
                instruction.append("1. Use 'yawl_get_running_cases' to list all cases\n");
                instruction.append("2. For each case of interest, get detailed status\n");
                if (includeWorkItems) {
                    instruction.append("3. Use 'yawl_get_workitems' to see all live work items\n");
                }
            }

            instruction.append("\nFormat the report with:\n");
            instruction.append("- Case ID and specification\n");
            instruction.append("- Current state/status\n");
            instruction.append("- Active work items (if requested)\n");
            instruction.append("- Any issues or blocked items\n");

            List<PromptMessage> messages = List.of(
                    new PromptMessage(Role.USER,
                            new TextContent("Check status for " + (caseId != null ? caseId : "all cases"))),
                    new PromptMessage(Role.ASSISTANT,
                            new TextContent(instruction.toString()))
            );

            return new GetPromptResult("YAWL Status Check", messages);
        });
    }
}
