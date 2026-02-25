package org.yawlfoundation.yawl.integration.mcp.spec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * YAWL Prompt Specifications for MCP SDK v1 (0.18.0+).
 *
 * Creates prompt templates that guide AI models through YAWL workflow operations.
 * Each prompt fetches real data from the YAWL engine to populate context.
 *
 * Implements MCP 2025-11-25 specification with exchange-based handlers.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlPromptSpecifications {

    private YawlPromptSpecifications() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Create all prompt specifications.
     *
     * @param client the YAWL InterfaceB client
     * @param sessionSupplier supplies the current session handle
     * @return list of prompt specifications
     */
    public static List<McpServerFeatures.SyncPromptSpecification> createAll(
            InterfaceB_EnvironmentBasedClient client,
            Supplier<String> sessionSupplier) {

        List<McpServerFeatures.SyncPromptSpecification> prompts = new ArrayList<>();
        prompts.add(createWorkflowAnalysis(client, sessionSupplier));
        prompts.add(createTaskCompletionGuide(client, sessionSupplier));
        prompts.add(createCaseTroubleshooting(client, sessionSupplier));
        prompts.add(createWorkflowDesignReview(client, sessionSupplier));
        return prompts;
    }

    private static McpServerFeatures.SyncPromptSpecification createWorkflowAnalysis(
            InterfaceB_EnvironmentBasedClient client, Supplier<String> sessionSupplier) {

        McpSchema.Prompt prompt = new McpSchema.Prompt(
            "workflow_analysis",
            "Analyze a YAWL workflow specification and provide recommendations",
            List.of(
                new McpSchema.PromptArgument("spec_identifier",
                    "The specification identifier to analyze", true),
                new McpSchema.PromptArgument("analysis_type",
                    "Focus: performance, correctness, optimization, or general", false)
            ));

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String specId = (String) args.get("spec_identifier");
            if (specId == null) {
                throw new RuntimeException("Required argument missing: spec_identifier");
            }
            String analysisType = args.containsKey("analysis_type")
                ? (String) args.get("analysis_type") : "general";

            String specInfo = fetchSpecInfo(client, sessionSupplier.get(), specId);

            String text = String.format(
                "Analyze the following YAWL workflow specification (focus: %s):\n\n%s\n\n" +
                "Provide:\n1. Summary of the workflow's purpose and structure\n" +
                "2. Key observations about task decomposition and flow\n" +
                "3. Specific %s recommendations\n" +
                "4. Potential issues, bottlenecks, or improvements\n" +
                "5. Best practices alignment assessment",
                analysisType, specInfo, analysisType);

            return new McpSchema.GetPromptResult(
                "Workflow analysis for: " + specId,
                List.of(new McpSchema.PromptMessage(
                    McpSchema.Role.USER, new McpSchema.TextContent(text))));
        });
    }

    private static McpServerFeatures.SyncPromptSpecification createTaskCompletionGuide(
            InterfaceB_EnvironmentBasedClient client, Supplier<String> sessionSupplier) {

        McpSchema.Prompt prompt = new McpSchema.Prompt(
            "task_completion_guide",
            "Generate step-by-step guidance for completing a specific work item",
            List.of(
                new McpSchema.PromptArgument("work_item_id",
                    "The work item ID to get guidance for", true),
                new McpSchema.PromptArgument("context",
                    "Additional context about the task", false)
            ));

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String workItemId = (String) args.get("work_item_id");
            if (workItemId == null) {
                throw new RuntimeException("Required argument missing: work_item_id");
            }
            String context = args.containsKey("context") ? (String) args.get("context") : "";

            String taskInfo;
            try {
                taskInfo = client.getWorkItem(workItemId, sessionSupplier.get());
                if (taskInfo == null) {
                    taskInfo = "Work item " + workItemId + " not found.";
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch work item: " + e.getMessage(), e);
            }

            String text = String.format(
                "Provide step-by-step guidance for completing this YAWL work item:\n\n" +
                "Work Item ID: %s\n%s\nWork Item Data:\n%s\n\n" +
                "Include:\n1. What this task requires\n" +
                "2. Expected input/output data format (XML)\n" +
                "3. Steps using yawl_checkout_work_item and yawl_checkin_work_item tools\n" +
                "4. Data validation requirements\n5. Common issues and resolutions",
                workItemId,
                context.isEmpty() ? "" : "Context: " + context + "\n",
                taskInfo);

            return new McpSchema.GetPromptResult(
                "Task completion guide for: " + workItemId,
                List.of(new McpSchema.PromptMessage(
                    McpSchema.Role.USER, new McpSchema.TextContent(text))));
        });
    }

    private static McpServerFeatures.SyncPromptSpecification createCaseTroubleshooting(
            InterfaceB_EnvironmentBasedClient client, Supplier<String> sessionSupplier) {

        McpSchema.Prompt prompt = new McpSchema.Prompt(
            "case_troubleshooting",
            "Diagnose issues with a workflow case and suggest resolution steps",
            List.of(
                new McpSchema.PromptArgument("case_id",
                    "The case ID to troubleshoot", true),
                new McpSchema.PromptArgument("symptom",
                    "Description of the observed issue", false)
            ));

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String caseId = (String) args.get("case_id");
            if (caseId == null) {
                throw new RuntimeException("Required argument missing: case_id");
            }
            String symptom = args.containsKey("symptom")
                ? (String) args.get("symptom") : "Case appears stuck or not progressing";

            String caseInfo;
            try {
                String session = sessionSupplier.get();
                String caseState = client.getCaseState(caseId, session);
                List<WorkItemRecord> items = client.getWorkItemsForCase(caseId, session);
                StringBuilder sb = new StringBuilder();
                sb.append("Case State: ").append(caseState != null ? caseState : "unknown").append("\n");
                sb.append("Work Items: ").append(items != null ? items.size() : 0).append("\n");
                if (items != null) {
                    for (WorkItemRecord wir : items) {
                        sb.append("  - ").append(wir.getID())
                          .append(" [").append(wir.getTaskID()).append("]")
                          .append(" status=").append(wir.getStatus()).append("\n");
                    }
                }
                caseInfo = sb.toString();
            } catch (IOException e) {
                throw new RuntimeException("Failed to fetch case data: " + e.getMessage(), e);
            }

            String text = String.format(
                "Troubleshoot the following YAWL workflow case:\n\n" +
                "Case ID: %s\nReported Symptom: %s\n\nCurrent State:\n%s\n\n" +
                "Diagnostic steps:\n1. Analyze case state and work item statuses\n" +
                "2. Identify blocked or stuck work items\n" +
                "3. Check for missing resources or data\n" +
                "4. Suggest specific resolution steps\n5. Recommend preventive measures",
                caseId, symptom, caseInfo);

            return new McpSchema.GetPromptResult(
                "Case troubleshooting for: " + caseId,
                List.of(new McpSchema.PromptMessage(
                    McpSchema.Role.USER, new McpSchema.TextContent(text))));
        });
    }

    private static McpServerFeatures.SyncPromptSpecification createWorkflowDesignReview(
            InterfaceB_EnvironmentBasedClient client, Supplier<String> sessionSupplier) {

        McpSchema.Prompt prompt = new McpSchema.Prompt(
            "workflow_design_review",
            "Review a workflow specification for YAWL best practices compliance",
            List.of(
                new McpSchema.PromptArgument("spec_identifier",
                    "The specification identifier to review", true)
            ));

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String specId = (String) args.get("spec_identifier");
            if (specId == null) {
                throw new RuntimeException("Required argument missing: spec_identifier");
            }

            String specInfo = fetchSpecInfo(client, sessionSupplier.get(), specId);

            String text = String.format(
                "Perform a design review of this YAWL workflow specification:\n\n%s\n\n" +
                "Review criteria:\n" +
                "1. YAWL pattern usage (choice, parallel split, synchronization)\n" +
                "2. Resource allocation strategy (direct, role-based, deferred)\n" +
                "3. Exception handling completeness (timeout, constraint violations)\n" +
                "4. Data flow correctness (variable mappings, XPath expressions)\n" +
                "5. Cancellation region design\n" +
                "6. Timer and deadline configurations\n" +
                "7. Documentation completeness\n" +
                "8. Overall structure and maintainability",
                specInfo);

            return new McpSchema.GetPromptResult(
                "Design review for: " + specId,
                List.of(new McpSchema.PromptMessage(
                    McpSchema.Role.USER, new McpSchema.TextContent(text))));
        });
    }

    /**
     * Create a prompt that runs live process mining analysis and formats the
     * results as a natural-language performance report for the given specification.
     *
     * @param facade          process mining facade connected to the YAWL engine
     * @param client          InterfaceB client for spec lookup
     * @param sessionSupplier supplies the current session handle
     * @return prompt specification for {@code workflow_performance_report}
     */
    public static McpServerFeatures.SyncPromptSpecification createWorkflowPerformanceReport(
            ProcessMiningFacade facade,
            InterfaceB_EnvironmentBasedClient client,
            Supplier<String> sessionSupplier) {

        McpSchema.Prompt prompt = new McpSchema.Prompt(
            "workflow_performance_report",
            "Generate a natural-language performance and conformance report for a workflow specification",
            List.of(
                new McpSchema.PromptArgument("spec_identifier",
                    "The specification identifier to analyze", true)
            ));

        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            String specIdentifier = (String) args.get("spec_identifier");
            if (specIdentifier == null) {
                throw new RuntimeException("Required argument missing: spec_identifier");
            }

            YSpecificationID specId = findSpecificationId(client, sessionSupplier.get(), specIdentifier);

            ProcessMiningFacade.ProcessMiningReport report;
            try {
                report = facade.analyzePerformance(specId, false);
            } catch (IOException e) {
                throw new RuntimeException(
                    "Failed to run performance analysis for '" + specIdentifier + "': "
                    + e.getMessage(), e);
            }

            String text = formatPerformanceReport(specIdentifier, report);

            return new McpSchema.GetPromptResult(
                "Workflow performance report for: " + specIdentifier,
                List.of(new McpSchema.PromptMessage(
                    McpSchema.Role.USER, new McpSchema.TextContent(text))));
        });
    }

    private static YSpecificationID findSpecificationId(
            InterfaceB_EnvironmentBasedClient client, String session, String specIdentifier) {
        try {
            List<SpecificationData> specs = client.getSpecificationList(session);
            if (specs != null) {
                for (SpecificationData spec : specs) {
                    YSpecificationID specId = spec.getID();
                    if (specIdentifier.equals(specId.getIdentifier())
                            || specIdentifier.equals(specId.getUri())) {
                        return specId;
                    }
                }
            }
            throw new RuntimeException(
                "Specification '" + specIdentifier + "' not found in loaded specifications.");
        } catch (RuntimeException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to fetch specification list: " + e.getMessage(), e);
        }
    }

    private static String formatPerformanceReport(
            String specIdentifier, ProcessMiningFacade.ProcessMiningReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a natural-language workflow performance analysis report for '")
          .append(specIdentifier).append("'.\n\n");
        sb.append("## Analysis Data\n\n");
        sb.append("**Cases Analyzed**: ").append(report.traceCount).append("\n");
        sb.append("**Analysis Timestamp**: ").append(report.analysisTime).append("\n\n");

        sb.append("### Performance Metrics\n");
        if (report.performance != null) {
            sb.append("- Average flow time: ")
              .append(String.format("%.1f", report.performance.avgFlowTimeMs)).append(" ms\n");
            sb.append("- Throughput: ")
              .append(String.format("%.2f", report.performance.throughputPerHour))
              .append(" cases/hour\n");
            if (report.performance.activityCounts != null
                    && !report.performance.activityCounts.isEmpty()) {
                sb.append("- Activity execution counts:\n");
                report.performance.activityCounts.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(10)
                    .forEach(e -> sb.append("  - ").append(e.getKey())
                                   .append(": ").append(e.getValue()).append("\n"));
            }
        } else {
            sb.append("- Performance metrics not available\n");
        }

        sb.append("\n### Conformance\n");
        if (report.conformance != null) {
            sb.append("- Fitness score: ")
              .append(String.format("%.3f", report.conformance.computeFitness())).append("\n");
        } else {
            sb.append("- Conformance checking not performed (no net model provided)\n");
        }

        sb.append("\n### Process Variants\n");
        sb.append("- Distinct variants: ").append(report.variantCount).append("\n");
        if (!report.variantFrequencies.isEmpty()) {
            sb.append("- Top variants (by frequency):\n");
            int count = 0;
            for (Map.Entry<String, Long> entry : report.variantFrequencies.entrySet()) {
                if (count++ >= 5) break;
                sb.append("  - ").append(entry.getKey())
                  .append(": ").append(entry.getValue()).append(" case(s)\n");
            }
        }

        sb.append("\n## Instructions\n");
        sb.append("Based on the analysis data above, provide:\n");
        sb.append("1. A natural-language summary of workflow performance and throughput\n");
        sb.append("2. Key bottlenecks or inefficiencies identified in activity durations\n");
        sb.append("3. Variant analysis — insights from common vs. rare execution paths\n");
        sb.append("4. Actionable optimization recommendations with concrete next steps\n");
        sb.append("5. Process mining insights for workflow redesign or monitoring");
        return sb.toString();
    }

    private static String fetchSpecInfo(
            InterfaceB_EnvironmentBasedClient client, String session, String specIdentifier) {
        try {
            List<SpecificationData> specs = client.getSpecificationList(session);
            if (specs != null) {
                for (SpecificationData spec : specs) {
                    YSpecificationID specId = spec.getID();
                    if (specIdentifier.equals(specId.getIdentifier())
                            || specIdentifier.equals(specId.getUri())) {
                        return String.format(
                            "Specification: %s\nVersion: %s\nURI: %s\nStatus: %s\n" +
                            "Name: %s\nDocumentation: %s\nRoot Net: %s",
                            specId.getIdentifier(), specId.getVersionAsString(),
                            specId.getUri(), spec.getStatus(), spec.getName(),
                            spec.getDocumentation(), spec.getRootNetID());
                    }
                }
            }
            return "Specification '" + specIdentifier + "' not found in loaded specifications.";
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch specification info: " + e.getMessage(), e);
        }
    }
}
