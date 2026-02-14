package org.yawlfoundation.yawl.integration.mcp;

import org.jdom2.Element;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;

import java.util.*;

/**
 * MCP Prompt Provider for YAWL Workflow Assistance
 *
 * Provides context-aware prompts for AI assistants working with YAWL workflows.
 * Each prompt integrates with the real YAWL engine to provide accurate, state-aware guidance.
 *
 * Prompt Categories:
 * 1. workflow-design - Help design YAWL workflow specifications
 * 2. case-debugging - Debug issues with running cases
 * 3. data-mapping - Map data between tasks
 * 4. exception-handling - Handle workflow exceptions
 * 5. resource-allocation - Suggest resource allocation strategies
 * 6. process-optimization - Suggest workflow optimizations
 * 7. task-completion - Guide users through completing work items
 *
 * Fortune 5 Standards:
 * - All prompts query real YAWL engine state
 * - No mock data or placeholder responses
 * - Fail fast if engine or dependencies unavailable
 * - Provide actionable, implementation-ready guidance
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlMcpPromptProvider {

    private final YEngine engine;
    private final Map<String, PromptDefinition> prompts;

    /**
     * Initialize with default YAWL engine instance
     */
    public YawlMcpPromptProvider() {
        try {
            this.engine = YEngine.getInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                "YAWL Engine initialization failed. Ensure engine is configured:\n" +
                "  1. Database connection in build/build.properties\n" +
                "  2. Persistence layer initialized\n" +
                "  3. Engine status = RUNNING\n" +
                "Error: " + e.getMessage(),
                e
            );
        }
        this.prompts = new HashMap<>();
        registerAllPrompts();
    }

    /**
     * Initialize with specific YAWL engine instance
     */
    public YawlMcpPromptProvider(YEngine engine) {
        if (engine == null) {
            throw new IllegalArgumentException("YAWL Engine cannot be null");
        }
        this.engine = engine;
        this.prompts = new HashMap<>();
        registerAllPrompts();
    }

    /**
     * Register all available prompts
     */
    private void registerAllPrompts() {
        // 1. Workflow Design Assistance
        prompts.put("workflow-design", new PromptDefinition(
            "workflow-design",
            "Help design a YAWL workflow specification",
            "Provides expert guidance on designing YAWL workflows with proper task decomposition, " +
            "data flow, and control flow patterns. Analyzes existing specifications if provided.",
            new String[]{"domain", "requirements", "existingSpec"},
            this::generateWorkflowDesignPrompt
        ));

        // 2. Case Debugging
        prompts.put("case-debugging", new PromptDefinition(
            "case-debugging",
            "Debug issues with a running case",
            "Analyzes running workflow case state, identifies stuck tasks, examines data flow, " +
            "and suggests debugging steps. Requires active case ID.",
            new String[]{"caseId", "issueDescription"},
            this::generateCaseDebuggingPrompt
        ));

        // 3. Data Mapping
        prompts.put("data-mapping", new PromptDefinition(
            "data-mapping",
            "Help map data between tasks",
            "Assists with XPath/XQuery expressions for mapping data between task inputs and outputs. " +
            "Analyzes task schemas and suggests transformations.",
            new String[]{"sourceTask", "targetTask", "specId"},
            this::generateDataMappingPrompt
        ));

        // 4. Exception Handling
        prompts.put("exception-handling", new PromptDefinition(
            "exception-handling",
            "Handle workflow exceptions",
            "Provides guidance on handling workflow exceptions, timeouts, and compensation flows. " +
            "Analyzes current exception state and suggests resolution strategies.",
            new String[]{"caseId", "exceptionType", "workItemId"},
            this::generateExceptionHandlingPrompt
        ));

        // 5. Resource Allocation
        prompts.put("resource-allocation", new PromptDefinition(
            "resource-allocation",
            "Suggest resource allocation strategies",
            "Analyzes workflow resource requirements and suggests optimal allocation strategies. " +
            "Considers task complexity, priorities, and available resources.",
            new String[]{"specId", "resourceConstraints"},
            this::generateResourceAllocationPrompt
        ));

        // 6. Process Optimization
        prompts.put("process-optimization", new PromptDefinition(
            "process-optimization",
            "Suggest workflow optimizations",
            "Reviews workflow specifications and running cases to identify bottlenecks, " +
            "redundant tasks, and optimization opportunities.",
            new String[]{"specId", "performanceMetrics"},
            this::generateProcessOptimizationPrompt
        ));

        // 7. Task Completion
        prompts.put("task-completion", new PromptDefinition(
            "task-completion",
            "Guide user through completing a work item",
            "Provides step-by-step guidance for completing work items, including required data, " +
            "validation rules, and next steps in the workflow.",
            new String[]{"workItemId"},
            this::generateTaskCompletionPrompt
        ));
    }

    /**
     * Get all available prompt names
     */
    public Set<String> getAvailablePrompts() {
        return new HashSet<>(prompts.keySet());
    }

    /**
     * Get prompt definition by name
     */
    public PromptDefinition getPrompt(String name) {
        PromptDefinition prompt = prompts.get(name);
        if (prompt == null) {
            throw new IllegalArgumentException(
                "Unknown prompt: " + name + "\n" +
                "Available prompts: " + String.join(", ", prompts.keySet())
            );
        }
        return prompt;
    }

    /**
     * Generate prompt content with arguments
     */
    public String generatePrompt(String promptName, Map<String, String> arguments) {
        PromptDefinition definition = getPrompt(promptName);
        return definition.generator.generate(arguments);
    }

    // PROMPT GENERATORS //

    /**
     * Generate workflow design assistance prompt
     */
    private String generateWorkflowDesignPrompt(Map<String, String> args) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# YAWL Workflow Design Assistant\n\n");

        String domain = args.get("domain");
        String requirements = args.get("requirements");
        String existingSpec = args.get("existingSpec");

        prompt.append("You are an expert YAWL workflow designer. Help the user design a workflow ");
        prompt.append("that follows YAWL best practices and formal workflow patterns.\n\n");

        if (domain != null && !domain.isEmpty()) {
            prompt.append("## Domain Context\n");
            prompt.append(domain).append("\n\n");
        }

        if (requirements != null && !requirements.isEmpty()) {
            prompt.append("## Requirements\n");
            prompt.append(requirements).append("\n\n");
        }

        // Get available workflow patterns from engine
        try {
            Set<YSpecificationID> specs = engine.getLoadedSpecificationIDs();
            if (!specs.isEmpty()) {
                prompt.append("## Available Specifications in Engine\n");
                for (YSpecificationID specId : specs) {
                    prompt.append("- ").append(specId.getUri()).append(" (v")
                          .append(specId.getVersionAsString()).append(")\n");
                }
                prompt.append("\n");
            }
        } catch (Exception e) {
            prompt.append("Note: Unable to retrieve loaded specifications: ")
                  .append(e.getMessage()).append("\n\n");
        }

        if (existingSpec != null && !existingSpec.isEmpty()) {
            prompt.append("## Existing Specification to Analyze\n");
            prompt.append("```xml\n");
            prompt.append(existingSpec);
            prompt.append("\n```\n\n");
        }

        prompt.append("## Guidelines\n");
        prompt.append("1. Use YAWL workflow patterns (sequence, parallel split, synchronization, etc.)\n");
        prompt.append("2. Define clear task decompositions with proper inputs/outputs\n");
        prompt.append("3. Specify data mappings using XPath/XQuery\n");
        prompt.append("4. Include exception handling and cancellation regions\n");
        prompt.append("5. Document task purposes and business rules\n");
        prompt.append("6. Validate against YAWL_Schema4.0.xsd\n\n");

        prompt.append("Provide a complete YAWL specification with:\n");
        prompt.append("- Task definitions with proper split/join types\n");
        prompt.append("- Data variable declarations and mappings\n");
        prompt.append("- Flow conditions and predicates\n");
        prompt.append("- Resource allocation hints\n");

        return prompt.toString();
    }

    /**
     * Generate case debugging prompt
     */
    private String generateCaseDebuggingPrompt(Map<String, String> args) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# YAWL Case Debugging Assistant\n\n");

        String caseId = args.get("caseId");
        String issueDescription = args.get("issueDescription");

        if (caseId == null || caseId.isEmpty()) {
            throw new IllegalArgumentException(
                "caseId is required for case-debugging prompt.\n" +
                "Provide the case identifier to analyze."
            );
        }

        prompt.append("You are debugging YAWL workflow case: **").append(caseId).append("**\n\n");

        if (issueDescription != null && !issueDescription.isEmpty()) {
            prompt.append("## Reported Issue\n");
            prompt.append(issueDescription).append("\n\n");
        }

        // Query real engine state for this case
        try {
            YIdentifier caseIdentifier = new YIdentifier(caseId);

            // Get case specification
            YSpecification spec = engine.getSpecificationForCase(caseIdentifier);
            if (spec != null) {
                prompt.append("## Case Specification\n");
                prompt.append("- URI: ").append(spec.getURI()).append("\n");
                prompt.append("- Version: ").append(spec.getSchemaVersion()).append("\n");
                prompt.append("- Root Net: ").append(spec.getRootNet().getID()).append("\n\n");
            }

            // Get all running work items and filter by case
            Set<YWorkItem> allWorkItems = engine.getAllWorkItems();
            Set<YWorkItem> caseWorkItems = new HashSet<>();

            if (allWorkItems != null) {
                for (YWorkItem item : allWorkItems) {
                    if (item.getCaseID().equals(caseIdentifier)) {
                        caseWorkItems.add(item);
                    }
                }
            }

            if (!caseWorkItems.isEmpty()) {
                prompt.append("## Active Work Items\n");
                for (YWorkItem item : caseWorkItems) {
                    prompt.append("- ").append(item.getTaskID())
                          .append(" [").append(item.getStatus()).append("]");
                    if (item.getEnablementTime() != null) {
                        prompt.append(" (enabled: ").append(item.getEnablementTime()).append(")");
                    }
                    prompt.append("\n");
                }
                prompt.append("\n");
            } else {
                prompt.append("## Active Work Items\n");
                prompt.append("No active work items found for this case.\n\n");
            }

        } catch (Exception e) {
            prompt.append("## ⚠️ Case State Retrieval Error\n");
            prompt.append("Unable to retrieve complete case state: ")
                  .append(e.getMessage()).append("\n\n");
        }

        prompt.append("## Debugging Checklist\n");
        prompt.append("1. Check for deadlocked work items (OR-join conditions)\n");
        prompt.append("2. Verify data flow between tasks (missing mappings)\n");
        prompt.append("3. Review flow conditions and predicates\n");
        prompt.append("4. Check timer expirations and timeouts\n");
        prompt.append("5. Examine exception logs and error states\n");
        prompt.append("6. Verify resource availability for tasks\n\n");

        prompt.append("Analyze the case state and provide:\n");
        prompt.append("- Root cause analysis\n");
        prompt.append("- Specific remediation steps\n");
        prompt.append("- Prevention strategies for future cases\n");

        return prompt.toString();
    }

    /**
     * Generate data mapping assistance prompt
     */
    private String generateDataMappingPrompt(Map<String, String> args) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# YAWL Data Mapping Assistant\n\n");

        String sourceTask = args.get("sourceTask");
        String targetTask = args.get("targetTask");
        String specId = args.get("specId");

        prompt.append("You are helping map data between YAWL tasks using XPath/XQuery expressions.\n\n");

        if (specId != null && !specId.isEmpty()) {
            try {
                YSpecificationID ySpecId = new YSpecificationID(specId);
                YSpecification spec = engine.getSpecification(ySpecId);

                if (spec != null) {
                    prompt.append("## Specification: ").append(spec.getURI()).append("\n\n");

                    YNet rootNet = spec.getRootNet();

                    if (sourceTask != null && !sourceTask.isEmpty()) {
                        YTask source = (YTask) rootNet.getNetElement(sourceTask);
                        if (source != null) {
                            prompt.append("### Source Task: ").append(sourceTask).append("\n");
                            prompt.append("Output Parameters:\n");
                            appendTaskParameters(prompt, source, false);
                            prompt.append("\n");
                        }
                    }

                    if (targetTask != null && !targetTask.isEmpty()) {
                        YTask target = (YTask) rootNet.getNetElement(targetTask);
                        if (target != null) {
                            prompt.append("### Target Task: ").append(targetTask).append("\n");
                            prompt.append("Input Parameters:\n");
                            appendTaskParameters(prompt, target, true);
                            prompt.append("\n");
                        }
                    }
                }
            } catch (Exception e) {
                prompt.append("Note: Unable to retrieve specification details: ")
                      .append(e.getMessage()).append("\n\n");
            }
        }

        prompt.append("## YAWL Data Mapping Syntax\n");
        prompt.append("```xml\n");
        prompt.append("<mapping>\n");
        prompt.append("  <expression query=\"/source/field\" />\n");
        prompt.append("  <expression query=\"concat(/source/first, ' ', /source/last)\" />\n");
        prompt.append("  <expression query=\"/source/items/item[position() &lt; 5]\" />\n");
        prompt.append("</mapping>\n");
        prompt.append("```\n\n");

        prompt.append("## Guidelines\n");
        prompt.append("1. Use XPath 2.0 syntax for queries\n");
        prompt.append("2. Ensure output schema matches target input schema\n");
        prompt.append("3. Handle missing/optional data with default values\n");
        prompt.append("4. Validate complex types and nested structures\n");
        prompt.append("5. Test mappings with sample data\n\n");

        prompt.append("Provide:\n");
        prompt.append("- Complete mapping expressions\n");
        prompt.append("- Data transformation logic\n");
        prompt.append("- Validation rules\n");
        prompt.append("- Sample input/output examples\n");

        return prompt.toString();
    }

    /**
     * Generate exception handling guidance prompt
     */
    private String generateExceptionHandlingPrompt(Map<String, String> args) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# YAWL Exception Handling Assistant\n\n");

        String caseId = args.get("caseId");
        String exceptionType = args.get("exceptionType");
        String workItemId = args.get("workItemId");

        prompt.append("You are assisting with YAWL workflow exception handling.\n\n");

        if (caseId != null && !caseId.isEmpty()) {
            prompt.append("## Case: ").append(caseId).append("\n");

            if (workItemId != null && !workItemId.isEmpty()) {
                prompt.append("## Work Item: ").append(workItemId).append("\n");

                try {
                    YWorkItem item = engine.getWorkItem(workItemId);
                    if (item != null) {
                        prompt.append("- Status: ").append(item.getStatus()).append("\n");
                        prompt.append("- Task: ").append(item.getTaskID()).append("\n");
                        if (item.getEnablementTime() != null) {
                            prompt.append("- Enabled: ").append(item.getEnablementTime()).append("\n");
                        }
                    }
                } catch (Exception e) {
                    prompt.append("Note: Unable to retrieve work item details\n");
                }
            }
            prompt.append("\n");
        }

        if (exceptionType != null && !exceptionType.isEmpty()) {
            prompt.append("## Exception Type: ").append(exceptionType).append("\n\n");
        }

        prompt.append("## YAWL Exception Handling Strategies\n\n");
        prompt.append("### 1. Compensation Flow\n");
        prompt.append("- Define cancellation regions for atomic operations\n");
        prompt.append("- Implement rollback tasks for compensable actions\n");
        prompt.append("- Use cancellation sets to clean up partial work\n\n");

        prompt.append("### 2. Timeout Handling\n");
        prompt.append("- Configure timer parameters on tasks\n");
        prompt.append("- Define timeout exception handlers\n");
        prompt.append("- Implement escalation paths for overdue items\n\n");

        prompt.append("### 3. Resource Unavailability\n");
        prompt.append("- Configure resource allocation fallbacks\n");
        prompt.append("- Use dynamic resource assignment\n");
        prompt.append("- Implement queue management strategies\n\n");

        prompt.append("### 4. Data Validation Failures\n");
        prompt.append("- Define schema validation rules\n");
        prompt.append("- Implement error recovery tasks\n");
        prompt.append("- Provide user-friendly error messages\n\n");

        prompt.append("### 5. External Service Failures\n");
        prompt.append("- Implement retry logic with exponential backoff\n");
        prompt.append("- Define alternative service paths\n");
        prompt.append("- Use circuit breaker patterns\n\n");

        prompt.append("Provide specific exception handling strategy including:\n");
        prompt.append("- YAWL specification changes needed\n");
        prompt.append("- Compensation/rollback procedures\n");
        prompt.append("- Monitoring and alerting setup\n");
        prompt.append("- Testing approach for exception paths\n");

        return prompt.toString();
    }

    /**
     * Generate resource allocation guidance prompt
     */
    private String generateResourceAllocationPrompt(Map<String, String> args) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# YAWL Resource Allocation Assistant\n\n");

        String specId = args.get("specId");
        String resourceConstraints = args.get("resourceConstraints");

        prompt.append("You are optimizing resource allocation for YAWL workflows.\n\n");

        if (specId != null && !specId.isEmpty()) {
            try {
                YSpecificationID ySpecId = new YSpecificationID(specId);
                YSpecification spec = engine.getSpecification(ySpecId);

                if (spec != null) {
                    prompt.append("## Specification: ").append(spec.getURI()).append("\n");

                    YNet rootNet = spec.getRootNet();
                    List<YTask> tasks = rootNet.getNetTasks();

                    prompt.append("## Tasks Requiring Resources\n");
                    for (YTask task : tasks) {
                        if (task.getDecompositionPrototype() != null) {
                            prompt.append("- ").append(task.getID());
                            if (task.getSplitType() > 0) {
                                prompt.append(" [Split: ").append(task.getSplitType()).append("]");
                            }
                            if (task.getJoinType() > 0) {
                                prompt.append(" [Join: ").append(task.getJoinType()).append("]");
                            }
                            prompt.append("\n");
                        }
                    }
                    prompt.append("\n");
                }
            } catch (Exception e) {
                prompt.append("Note: Unable to analyze specification: ")
                      .append(e.getMessage()).append("\n\n");
            }
        }

        if (resourceConstraints != null && !resourceConstraints.isEmpty()) {
            prompt.append("## Resource Constraints\n");
            prompt.append(resourceConstraints).append("\n\n");
        }

        prompt.append("## Resource Allocation Strategies\n\n");
        prompt.append("### 1. Organizational Model\n");
        prompt.append("- Define roles, positions, and capabilities\n");
        prompt.append("- Map tasks to required competencies\n");
        prompt.append("- Use YAWL resource service for allocation\n\n");

        prompt.append("### 2. Work Distribution Strategies\n");
        prompt.append("- Round-robin for balanced workload\n");
        prompt.append("- Shortest queue for minimizing wait times\n");
        prompt.append("- Capability-based for skill matching\n");
        prompt.append("- Random selection for simple cases\n\n");

        prompt.append("### 3. Dynamic Allocation\n");
        prompt.append("- Runtime role resolution based on case data\n");
        prompt.append("- Workload-aware distribution\n");
        prompt.append("- Priority-based task assignment\n\n");

        prompt.append("### 4. Constraint Handling\n");
        prompt.append("- Separation of duties (different users for specific tasks)\n");
        prompt.append("- Binding constraints (same user for related tasks)\n");
        prompt.append("- Delegation and escalation policies\n\n");

        prompt.append("Provide resource allocation plan with:\n");
        prompt.append("- Organizational structure definition\n");
        prompt.append("- Task-to-role mappings\n");
        prompt.append("- Distribution strategy per task type\n");
        prompt.append("- Constraint specifications\n");
        prompt.append("- Monitoring metrics for allocation efficiency\n");

        return prompt.toString();
    }

    /**
     * Generate process optimization guidance prompt
     */
    private String generateProcessOptimizationPrompt(Map<String, String> args) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# YAWL Process Optimization Assistant\n\n");

        String specId = args.get("specId");
        String performanceMetrics = args.get("performanceMetrics");

        prompt.append("You are analyzing YAWL workflows for optimization opportunities.\n\n");

        if (specId != null && !specId.isEmpty()) {
            try {
                YSpecificationID ySpecId = new YSpecificationID(specId);
                YSpecification spec = engine.getSpecification(ySpecId);

                if (spec != null) {
                    prompt.append("## Specification Analysis: ").append(spec.getURI()).append("\n\n");

                    YNet rootNet = spec.getRootNet();

                    prompt.append("### Workflow Structure\n");
                    List<YTask> tasks = rootNet.getNetTasks();
                    prompt.append("- Total Tasks: ").append(tasks.size()).append("\n");
                    Map<String, YExternalNetElement> elements = rootNet.getNetElements();
                    int conditionCount = 0;
                    for (YExternalNetElement element : elements.values()) {
                        if (!(element instanceof YTask)) {
                            conditionCount++;
                        }
                    }
                    prompt.append("- Conditions: ").append(conditionCount).append("\n");
                    prompt.append("- Variables: ").append(rootNet.getLocalVariables().size()).append("\n");

                    // Analyze task complexity
                    int atomicTasks = 0;
                    int compositeTasks = 0;
                    int multiInstanceTasks = 0;

                    for (YTask task : rootNet.getNetTasks()) {
                        if (task.isMultiInstance()) {
                            multiInstanceTasks++;
                        } else if (task.getDecompositionPrototype() instanceof YNet) {
                            compositeTasks++;
                        } else {
                            atomicTasks++;
                        }
                    }

                    prompt.append("- Atomic Tasks: ").append(atomicTasks).append("\n");
                    prompt.append("- Composite Tasks: ").append(compositeTasks).append("\n");
                    prompt.append("- Multi-Instance Tasks: ").append(multiInstanceTasks).append("\n\n");
                }
            } catch (Exception e) {
                prompt.append("Note: Unable to analyze specification: ")
                      .append(e.getMessage()).append("\n\n");
            }
        }

        if (performanceMetrics != null && !performanceMetrics.isEmpty()) {
            prompt.append("## Performance Metrics\n");
            prompt.append(performanceMetrics).append("\n\n");
        }

        prompt.append("## Optimization Analysis Checklist\n\n");
        prompt.append("### 1. Bottleneck Identification\n");
        prompt.append("- Long-running tasks with high wait times\n");
        prompt.append("- Resource contention points\n");
        prompt.append("- Synchronization points causing delays\n\n");

        prompt.append("### 2. Workflow Patterns\n");
        prompt.append("- Unnecessary sequential constraints (can tasks run in parallel?)\n");
        prompt.append("- Complex OR-joins that may deadlock\n");
        prompt.append("- Redundant data transformations\n");
        prompt.append("- Excessive task granularity (too many small tasks)\n\n");

        prompt.append("### 3. Data Flow Optimization\n");
        prompt.append("- Minimize data transformations between tasks\n");
        prompt.append("- Reduce XML document size for faster processing\n");
        prompt.append("- Cache frequently accessed reference data\n");
        prompt.append("- Use efficient XQuery expressions\n\n");

        prompt.append("### 4. Resource Optimization\n");
        prompt.append("- Balance workload across available resources\n");
        prompt.append("- Reduce context switching (task batching)\n");
        prompt.append("- Optimize task allocation strategies\n");
        prompt.append("- Pre-allocate resources for critical paths\n\n");

        prompt.append("### 5. Exception Path Efficiency\n");
        prompt.append("- Minimize overhead of error handling flows\n");
        prompt.append("- Use efficient compensation mechanisms\n");
        prompt.append("- Avoid over-engineering for rare exceptions\n\n");

        prompt.append("Provide optimization recommendations including:\n");
        prompt.append("- Specific workflow restructuring suggestions\n");
        prompt.append("- Parallelization opportunities\n");
        prompt.append("- Data flow improvements\n");
        prompt.append("- Resource allocation changes\n");
        prompt.append("- Expected performance impact (quantified if possible)\n");

        return prompt.toString();
    }

    /**
     * Generate task completion guidance prompt
     */
    private String generateTaskCompletionPrompt(Map<String, String> args) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# YAWL Task Completion Assistant\n\n");

        String workItemId = args.get("workItemId");

        if (workItemId == null || workItemId.isEmpty()) {
            throw new IllegalArgumentException(
                "workItemId is required for task-completion prompt.\n" +
                "Provide the work item ID to get completion guidance."
            );
        }

        prompt.append("You are helping a user complete work item: **").append(workItemId).append("**\n\n");

        try {
            YWorkItem item = engine.getWorkItem(workItemId);

            if (item == null) {
                throw new IllegalArgumentException(
                    "Work item not found: " + workItemId + "\n" +
                    "Verify the work item ID is correct and still active."
                );
            }

            prompt.append("## Work Item Details\n");
            prompt.append("- Task: ").append(item.getTaskID()).append("\n");
            prompt.append("- Status: ").append(item.getStatus()).append("\n");
            prompt.append("- Case: ").append(item.getCaseID()).append("\n");

            if (item.getEnablementTime() != null) {
                prompt.append("- Enabled: ").append(item.getEnablementTime()).append("\n");
            }
            if (item.getStartTime() != null) {
                prompt.append("- Started: ").append(item.getStartTime()).append("\n");
            }

            prompt.append("\n");

            // Get task documentation if available
            if (item.getDocumentation() != null && !item.getDocumentation().isEmpty()) {
                prompt.append("## Task Documentation\n");
                prompt.append(item.getDocumentation()).append("\n\n");
            }

            // Get input data
            Element dataElement = item.getDataElement();
            if (dataElement != null) {
                prompt.append("## Input Data\n");
                prompt.append("```xml\n");
                prompt.append(dataElement.toString()).append("\n");
                prompt.append("```\n\n");
            }

            // Get task parameters from specification
            YSpecificationID specId = item.getSpecificationID();
            YSpecification spec = engine.getSpecification(specId);

            if (spec != null) {
                YNet net = spec.getRootNet();
                YTask task = (YTask) net.getNetElement(item.getTaskID());

                if (task != null) {
                    prompt.append("## Required Output Parameters\n");
                    appendTaskParameters(prompt, task, false);
                    prompt.append("\n");
                }
            }

        } catch (Exception e) {
            prompt.append("## ⚠️ Work Item Retrieval Error\n");
            prompt.append("Unable to retrieve work item details: ")
                  .append(e.getMessage()).append("\n\n");
        }

        prompt.append("## Completion Steps\n");
        prompt.append("1. Review input data and task documentation\n");
        prompt.append("2. Gather/generate required output data\n");
        prompt.append("3. Validate output against schema requirements\n");
        prompt.append("4. Submit completion data to workflow engine\n");
        prompt.append("5. Verify task transitions to completed state\n\n");

        prompt.append("## YAWL Interface B Completion\n");
        prompt.append("```java\n");
        prompt.append("// Checkout work item (if not already started)\n");
        prompt.append("client.checkOutWorkItem(workItemId, sessionHandle);\n\n");
        prompt.append("// Prepare output data\n");
        prompt.append("String outputData = \"<data>...</data>\";\n\n");
        prompt.append("// Complete work item\n");
        prompt.append("client.checkInWorkItem(workItemId, outputData, null, sessionHandle);\n");
        prompt.append("```\n\n");

        prompt.append("Guide the user through:\n");
        prompt.append("- Understanding required outputs\n");
        prompt.append("- Generating valid output data\n");
        prompt.append("- Handling any validation errors\n");
        prompt.append("- Confirming successful completion\n");
        prompt.append("- Understanding next workflow steps\n");

        return prompt.toString();
    }

    // HELPER METHODS //

    /**
     * Append task parameter information to prompt
     */
    private void appendTaskParameters(StringBuilder prompt, YTask task, boolean inputParams) {
        if (task.getDecompositionPrototype() != null) {
            Map<String, YParameter> params = inputParams ?
                task.getDecompositionPrototype().getInputParameters() :
                task.getDecompositionPrototype().getOutputParameters();

            if (params != null && !params.isEmpty()) {
                for (YParameter param : params.values()) {
                    prompt.append("- ").append(param.getName())
                          .append(" (").append(param.getDataTypeName()).append(")");
                    if (param.isMandatory()) {
                        prompt.append(" *required*");
                    }
                    prompt.append("\n");
                }
            } else {
                prompt.append("No ").append(inputParams ? "input" : "output")
                      .append(" parameters defined.\n");
            }
        }
    }

    /**
     * Prompt definition with metadata and generator
     */
    public static class PromptDefinition {
        private final String name;
        private final String description;
        private final String longDescription;
        private final String[] arguments;
        private final PromptGenerator generator;

        public PromptDefinition(String name, String description, String longDescription,
                              String[] arguments, PromptGenerator generator) {
            this.name = name;
            this.description = description;
            this.longDescription = longDescription;
            this.arguments = arguments;
            this.generator = generator;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getLongDescription() { return longDescription; }
        public String[] getArguments() { return arguments; }

        @Override
        public String toString() {
            return String.format("Prompt: %s\n  Description: %s\n  Arguments: %s",
                name, description, String.join(", ", arguments));
        }
    }

    /**
     * Functional interface for prompt generators
     */
    @FunctionalInterface
    public interface PromptGenerator {
        String generate(Map<String, String> arguments);
    }

    /**
     * Main method for testing prompts
     */
    public static void main(String[] args) {
        try {
            YawlMcpPromptProvider provider = new YawlMcpPromptProvider();

            System.out.println("=== Available YAWL MCP Prompts ===\n");
            for (String promptName : provider.getAvailablePrompts()) {
                PromptDefinition def = provider.getPrompt(promptName);
                System.out.println(def);
                System.out.println();
            }

            // Test workflow design prompt
            System.out.println("\n=== Testing workflow-design Prompt ===\n");
            Map<String, String> designArgs = new HashMap<>();
            designArgs.put("domain", "Purchase Order Processing");
            designArgs.put("requirements", "Automated PO approval with multi-level authorization");
            String designPrompt = provider.generatePrompt("workflow-design", designArgs);
            System.out.println(designPrompt);

            // Test case debugging prompt (if cases available)
            System.out.println("\n=== Testing case-debugging Prompt ===\n");
            Map<String, String> debugArgs = new HashMap<>();
            debugArgs.put("caseId", "1.1");
            debugArgs.put("issueDescription", "Case appears stuck at approval task");
            try {
                String debugPrompt = provider.generatePrompt("case-debugging", debugArgs);
                System.out.println(debugPrompt);
            } catch (Exception e) {
                System.out.println("Note: " + e.getMessage());
            }

        } catch (Exception e) {
            System.err.println("Error testing prompts: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
