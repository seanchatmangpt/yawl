/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.benchmark;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.elements.data.YDataHandler;
import org.yawlfoundation.yawl.elements.data.YVariable;
import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.util.YVerificationMessage;

/**
 * Generator of production-like workflow specifications for YAWL v6.0.0-GA benchmarking.
 *
 * <p>Generates realistic workflow specifications that simulate real-world business processes
 * with varying complexity, nested hierarchies, and realistic data flows.
 *
 * <p>Workflow Categories:
 * <ul>
 *   <li>Simple Workflows - Linear processes (3-5 tasks)</li>
 *   <li>Complex Workflows - Branching and merging (8-15 tasks)</li>
 *   <li>Large Workflows - Hierarchical processes (20-50 tasks)</li>
 *   <li>Enterprise Workflows - Multi-department processes (50+ tasks)</li>
 * </ul>
 *
 * <p>Features:
 * <ul>
 *   <li>Realistic task dependencies and routing</li>
 *   <li>Multi-department organizational patterns</li>
 *   <li>Complex data transformations</li>
 *   <li>Exception handling patterns</li>
 *   <li>SLA and deadline management</li>
 *   <li>Resource allocation patterns</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class RealisticWorkflowGenerator {

    // Workflow templates for different industries
    private static final Map<String, List<String>> WORKFLOW_TEMPLATES = Map.of(
        "finance", List.of("Order Processing", "Invoice Approval", "Expense Reimbursement",
                          "Payment Processing", "Accounting Close", "Financial Reporting"),
        "hr", List.of("Employee Onboarding", "Performance Review", "Leave Management",
                     "Recruitment Process", "Termination Workflow", "Compensation Review"),
        "operations", List.of("Supply Chain", "Inventory Management", "Manufacturing",
                             "Quality Control", "Maintenance Scheduling", "Logistics"),
        "it", List.of("Incident Management", "Change Request", "Service Request",
                     "Security Assessment", "Capacity Planning", "Deployment Process"),
        "legal", List.of("Contract Review", "Compliance Check", "Legal Discovery",
                        "Regulatory Reporting", "IP Management", "Litigation Support"),
        "sales", List.of("Lead Generation", "Quote Creation", "Order Fulfillment",
                        "Customer Onboarding", "Renewal Process", "Complaint Handling")
    );

    // Realistic task patterns
    private static final List<String> TASK_PATTERNS = List.of(
        "Data Validation", "Risk Assessment", "Compliance Check", "Quality Inspection",
        "Document Generation", "Notification", "Approval", "Rejection", "Escalation",
        "Assignment", "Execution", "Completion", "Verification", "Submission",
        "Authorization", "Authentication", "Encryption", "Audit", "Archival"
    );

    // Department definitions
    private static final List<Map<String, String>> DEPARTMENTS = List.of(
        Map.of("name", "Finance", "role", "Financial processing and approvals"),
        Map.of("name", "HR", "role", "Human resources management"),
        Map.of("name", "IT", "role", "Information technology services"),
        Map.of("name", "Operations", "role", "Business operations"),
        Map.of("name", "Legal", "role", "Legal and compliance"),
        Map.of("name", "Sales", "role", "Sales and customer relations"),
        Map.of("name", "Marketing", "role", "Marketing campaigns"),
        Map.of("name", "Customer Service", "role", "Customer support")
    );

    // Data fields for realistic workflows
    private static final List<String> COMMON_FIELDS = List.of(
        "customer_id", "order_id", "invoice_number", "employee_id", "product_id",
        "amount", "currency", "priority", "status", "deadline", "created_by",
        "approved_by", "reviewed_by", "assigned_to", "department", "location",
        "classification", "category", "subcategory", "metadata", "audit_log"
    );

    private final YDataHandler dataHandler;
    private final Random random;

    /**
     * Creates a new realistic workflow generator
     */
    public RealisticWorkflowGenerator() {
        this.dataHandler = new YDataHandler();
        this.random = new Random();
    }

    /**
     * Generates a simple workflow (3-5 tasks, linear flow)
     */
    public YNet generateSimpleWorkflow(String industry) {
        String workflowName = WORKFLOW_TEMPLATES.get(industry)
            .get(random.nextInt(WORKFLOW_TEMPLATES.get(industry).size())) + " - Simple";

        YNet workflow = createWorkflowBase(workflowName, industry, "simple");

        // Create input parameters
        createInputParameters(workflow, 3, 5);

        // Create tasks
        List<YTask> tasks = new ArrayList<>();
        for (int i = 0; i < random.nextInt(3, 6); i++) {
            YTask task = createTask(workflow, "task_" + (i + 1),
                TASK_PATTERNS.get(random.nextInt(TASK_PATTERNS.size())));
            tasks.add(task);

            // Add task data
            createTaskParameters(task, i == 0);
            createTaskOutputs(task, i == tasks.size() - 1);
        }

        // Create linear flow
        createLinearFlow(workflow, tasks);

        return workflow;
    }

    /**
     * Generates a complex workflow (8-15 tasks, branching and merging)
     */
    public YNet generateComplexWorkflow(String industry) {
        String workflowName = WORKFLOW_TEMPLATES.get(industry)
            .get(random.nextInt(WORKFLOW_TEMPLATES.get(industry).size())) + " - Complex";

        YNet workflow = createWorkflowBase(workflowName, industry, "complex");

        // Create input parameters
        createInputParameters(workflow, 5, 10);

        // Create tasks with complex structure
        List<YTask> tasks = new ArrayList<>();

        // Start task
        YTask startTask = createTask(workflow, "start", "Initial Processing");
        tasks.add(startTask);
        createTaskParameters(startTask, true);

        // Parallel processing branches
        List<YTask> branch1Tasks = createBranch(workflow, "validation", tasks.get(tasks.size() - 1));
        List<YTask> branch2Tasks = createBranch(workflow, "processing", tasks.get(tasks.size() - 1));

        tasks.addAll(branch1Tasks);
        tasks.addAll(branch2Tasks);

        // Merge point
        YTask mergeTask = createTask(workflow, "merge", "Consolidate Results");
        tasks.add(mergeTask);

        // Approval and completion
        YTask approvalTask = createTask(workflow, "approval", "Final Approval");
        tasks.add(approvalTask);

        YTask endTask = createTask(workflow, "end", "Completion");
        tasks.add(endTask);

        // Create complex flow
        createComplexFlow(workflow, tasks);

        return workflow;
    }

    /**
     * Generates a large workflow (20-50 tasks, hierarchical)
     */
    public YNet generateLargeWorkflow(String industry) {
        String workflowName = WORKFLOW_TEMPLATES.get(industry)
            .get(random.nextInt(WORKFLOW_TEMPLATES.get(industry).size())) + " - Large";

        YNet workflow = createWorkflowBase(workflowName, industry, "large");

        // Create input parameters
        createInputParameters(workflow, 8, 15);

        // Create hierarchical structure
        List<YTask> allTasks = new ArrayList<>();

        // Phase 1: Initial Processing
        List<YTask> phase1Tasks = createPhase(workflow, "initial", "Initial Processing Phase");
        allTasks.addAll(phase1Tasks);

        // Phase 2: Parallel Departments
        List<List<YTask>> departmentPhases = createDepartmentPhases(workflow, phase1Tasks.get(phase1Tasks.size() - 1));
        departmentPhases.forEach(allTasks::addAll);

        // Phase 3: Consolidation
        List<YTask> phase3Tasks = createPhase(workflow, "consolidation", "Consolidation Phase");
        allTasks.addAll(phase3Tasks);

        // Create hierarchical flow
        createHierarchicalFlow(workflow, allTasks);

        return workflow;
    }

    /**
     * Generates an enterprise workflow (50+ tasks, multi-department)
     */
    public YNet generateEnterpriseWorkflow(String industry) {
        String workflowName = WORKFLOW_TEMPLATES.get(industry)
            .get(random.nextInt(WORKFLOW_TEMPLATES.get(industry).size())) + " - Enterprise";

        YNet workflow = createWorkflowBase(workflowName, industry, "enterprise");

        // Create extensive input parameters
        createInputParameters(workflow, 12, 20);

        // Create multi-level hierarchical structure
        List<YTask> allTasks = new ArrayList<>();

        // Level 1: Executive
        List<YTask> executiveTasks = createDepartmentPhase(workflow, "executive", "Executive Review");
        allTasks.addAll(executiveTasks);

        // Level 2: Department heads
        List<List<YTask>> departmentTasks = createDepartmentHeadPhases(workflow, executiveTasks.get(executiveTasks.size() - 1));
        departmentTasks.forEach(allTasks::addAll);

        // Level 3: Teams
        List<List<YTask>> teamTasks = createTeamPhases(workflow, departmentTasks);
        teamTasks.forEach(allTasks::addAll);

        // Level 4: Individual tasks
        List<YTask> individualTasks = createIndividualTasks(workflow, teamTasks);
        allTasks.addAll(individualTasks);

        // Level 5: Consolidation
        List<YTask> consolidationTasks = createConsolidationPhase(workflow, individualTasks.get(individualTasks.size() - 1));
        allTasks.addAll(consolidationTasks);

        // Create enterprise flow
        createEnterpriseFlow(workflow, allTasks);

        return workflow;
    }

    /**
     * Generates a workflow with specific complexity levels
     */
    public YNet generateWorkflow(String industry, String complexity) {
        return switch (complexity.toLowerCase()) {
            case "simple" -> generateSimpleWorkflow(industry);
            case "complex" -> generateComplexWorkflow(industry);
            case "large" -> generateLargeWorkflow(industry);
            case "enterprise" -> generateEnterpriseWorkflow(industry);
            default -> generateSimpleWorkflow(industry);
        };
    }

    /**
     * Batch generator for multiple workflows
     */
    public List<YNet> generateWorkflows(String industry, int count, String complexity) {
        List<YNet> workflows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            workflows.add(generateWorkflow(industry, complexity));
        }
        return workflows;
    }

    /**
     * Generates workflows with varying complexity
     */
    public Map<String, List<YNet>> generateComplexityMatrix(String industry) {
        Map<String, List<YNet>> matrix = new LinkedHashMap<>();
        matrix.put("simple", generateWorkflows(industry, 3, "simple"));
        matrix.put("complex", generateWorkflows(industry, 2, "complex"));
        matrix.put("large", generateWorkflows(industry, 1, "large"));
        matrix.put("enterprise", generateWorkflows(industry, 1, "enterprise"));
        return matrix;
    }

    // Helper methods

    private YNet createWorkflowBase(String name, String industry, String complexity) {
        YNet workflow = new YNet(name + "_" + UUID.randomUUID().toString().substring(0, 8));
        workflow.setName(name);
        workflow.setSpecificationID("spec_" + UUID.randomUUID().toString().substring(0, 8));
        workflow.setNetElementID("net_" + UUID.randomUUID().toString().substring(0, 8));

        // Set description
        workflow.setDescription("A realistic " + industry + " workflow of " + complexity + " complexity " +
                            "for benchmarking purposes. Generated for YAWL v6.0.0-GA.");

        return workflow;
    }

    private void createInputParameters(YNet workflow, int min, int max) {
        int count = random.nextInt(min, max + 1);
        for (int i = 0; i < count; i++) {
            YParameter param = new YParameter("input_" + (i + 1));
            param.setName("Input Parameter " + (i + 1));
            param.setDataType(randomDataType());
            workflow.addInputParameter(param);
        }
    }

    private YTask createTask(YNet workflow, String id, String name) {
        YTask task = new YTask(id);
        task.setName(name);
        task.setTaskID(id);
        workflow.addNetElement(task);
        return task;
    }

    private void createTaskParameters(YTask task, boolean isStart) {
        // Create input parameters based on task position
        int paramCount = random.nextInt(2, 6);
        for (int i = 0; i < paramCount; i++) {
            YParameter param = new YParameter("param_" + task.getTaskID() + "_" + (i + 1));
            param.setName("Parameter " + (i + 1));
            param.setDataType(randomDataType());
            task.addInputParameter(param);
        }
    }

    private void createTaskOutputs(YTask task, boolean isEnd) {
        // Create output parameters
        int outputCount = random.nextInt(1, 4);
        for (int i = 0; i < outputCount; i++) {
            YParameter param = new YParameter("output_" + task.getTaskID() + "_" + (i + 1));
            param.setName("Output " + (i + 1));
            param.setDataType(randomDataType());
            task.addOutputParameter(param);
        }
    }

    private List<YTask> createBranch(YNet workflow, String prefix, YTask source) {
        List<YTask> branchTasks = new ArrayList<>();
        int branchSize = random.nextInt(3, 6);

        for (int i = 0; i < branchSize; i++) {
            YTask task = createTask(workflow, prefix + "_task_" + (i + 1),
                TASK_PATTERNS.get(random.nextInt(TASK_PATTERNS.size())));
            branchTasks.add(task);
            createTaskParameters(task, false);
            createTaskOutputs(task, i == branchSize - 1);
        }

        return branchTasks;
    }

    private List<YTask> createPhase(YNet workflow, String prefix, String description) {
        List<YTask> phaseTasks = new ArrayList<>();
        int phaseSize = random.nextInt(4, 8);

        for (int i = 0; i < phaseSize; i++) {
            YTask task = createTask(workflow, prefix + "_phase_" + (i + 1),
                TASK_PATTERNS.get(random.nextInt(TASK_PATTERNS.size())));
            phaseTasks.add(task);
            createTaskParameters(task, i == 0);
            createTaskOutputs(task, i == phaseSize - 1);
        }

        return phaseTasks;
    }

    private void createLinearFlow(YNet workflow, List<YTask> tasks) {
        for (int i = 0; i < tasks.size() - 1; i++) {
            YFlow flow = new YFlow(tasks.get(i), tasks.get(i + 1));
            workflow.addControlFlow(flow);
        }
    }

    private void createComplexFlow(YNet workflow, List<YTask> tasks) {
        // Complex flow with parallel branches
        int startIdx = 0;
        int validationIdx = 1;
        int processingIdx = tasks.size() - 3;
        int mergeIdx = tasks.size() - 2;
        int approvalIdx = tasks.size() - 1;
        int endIdx = tasks.size();

        // Parallel flows
        workflow.addControlFlow(new YFlow(tasks.get(startIdx), tasks.get(validationIdx)));
        workflow.addControlFlow(new YFlow(tasks.get(startIdx), tasks.get(processingIdx)));

        // Merge
        workflow.addControlFlow(new YFlow(tasks.get(validationIdx), tasks.get(mergeIdx)));
        workflow.addControlFlow(new YFlow(tasks.get(processingIdx), tasks.get(mergeIdx)));

        // Final flow
        workflow.addControlFlow(new YFlow(tasks.get(mergeIdx), tasks.get(approvalIdx)));
        workflow.addControlFlow(new YFlow(tasks.get(approvalIdx), tasks.get(endIdx)));
    }

    private void createHierarchicalFlow(YNet workflow, List<YTask> tasks) {
        // Hierarchical flow with multiple levels
        int phase1End = 5; // Initial processing phase end
        int phase3Start = phase1End + 20; // Skip department phases
        int phase3End = phase3Start + 5; // Consolidation phase end

        // Phase 1 flow
        for (int i = 0; i < phase1End; i++) {
            if (i < phase1End - 1) {
                workflow.addControlFlow(new YFlow(tasks.get(i), tasks.get(i + 1)));
            }
        }

        // Connect phases
        workflow.addControlFlow(new YFlow(tasks.get(phase1End - 1), tasks.get(phase3Start)));

        // Phase 3 flow
        for (int i = phase3Start; i < phase3End - 1; i++) {
            workflow.addControlFlow(new YFlow(tasks.get(i), tasks.get(i + 1)));
        }
    }

    private void createEnterpriseFlow(YNet workflow, List<YTask> tasks) {
        // Multi-level enterprise flow
        // Executive phase
        int executiveEnd = 3;
        for (int i = 0; i < executiveEnd - 1; i++) {
            workflow.addControlFlow(new YFlow(tasks.get(i), tasks.get(i + 1)));
        }

        // Connect to departments
        int departmentStart = executiveEnd;
        int departmentEnd = departmentStart + 15;
        workflow.addControlFlow(new YFlow(tasks.get(executiveEnd - 1), tasks.get(departmentStart)));

        // Department flow
        for (int i = departmentStart; i < departmentEnd - 1; i++) {
            workflow.addControlFlow(new YFlow(tasks.get(i), tasks.get(i + 1)));
        }

        // Continue with team, individual, and consolidation phases
        int teamStart = departmentEnd;
        int teamEnd = teamStart + 20;
        workflow.addControlFlow(new YFlow(tasks.get(departmentEnd - 1), tasks.get(teamStart)));

        for (int i = teamStart; i < teamEnd - 1; i++) {
            workflow.addControlFlow(new YFlow(tasks.get(i), tasks.get(i + 1)));
        }

        // Individual tasks
        int individualStart = teamEnd;
        int individualEnd = individualStart + 10;
        workflow.addControlFlow(new YFlow(tasks.get(teamEnd - 1), tasks.get(individualStart)));

        for (int i = individualStart; i < individualEnd - 1; i++) {
            workflow.addControlFlow(new YFlow(tasks.get(i), tasks.get(i + 1)));
        }

        // Consolidation
        int consolidationStart = individualEnd;
        workflow.addControlFlow(new YFlow(tasks.get(individualEnd - 1), tasks.get(consolidationStart)));

        for (int i = consolidationStart; i < tasks.size() - 1; i++) {
            workflow.addControlFlow(new YFlow(tasks.get(i), tasks.get(i + 1)));
        }
    }

    private String randomDataType() {
        String[] types = {"string", "integer", "decimal", "boolean", "date", "datetime", "xml", "json"};
        return types[random.nextInt(types.length)];
    }

    private List<Map<String, String>> createDepartmentPhases(YNet workflow, YTask source) {
        List<List<YTask>> allPhases = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            Map<String, String> dept = DEPARTMENTS.get(i);
            List<YTask> phase = createDepartmentPhase(workflow, dept.get("name").toLowerCase(), dept.get("role"));
            allPhases.add(phase);
        }

        return DEPARTMENTS.stream()
            .map(dept -> Map.of(
                "name", dept.get("name"),
                "tasks", createDepartmentPhase(workflow, dept.get("name").toLowerCase(), dept.get("role")).size() + ""
            ))
            .collect(Collectors.toList());
    }

    private List<YTask> createDepartmentPhase(YNet workflow, String deptName, String role) {
        List<YTask> phaseTasks = new ArrayList<>();
        int phaseSize = random.nextInt(5, 10);

        for (int i = 0; i < phaseSize; i++) {
            YTask task = createTask(workflow, deptName + "_dept_" + (i + 1),
                TASK_PATTERNS.get(random.nextInt(TASK_PATTERNS.size())));
            phaseTasks.add(task);
            createTaskParameters(task, i == 0);
            createTaskOutputs(task, i == phaseSize - 1);
        }

        return phaseTasks;
    }

    private List<List<YTask>> createDepartmentHeadPhases(YNet workflow, YTask source) {
        List<List<YTask>> phases = new ArrayList<>();

        for (int i = 0; i < DEPARTMENTS.size(); i++) {
            List<YTask> phase = createDepartmentPhase(workflow,
                DEPARTMENTS.get(i).get("name").toLowerCase() + "_head", "Department Head Review");
            phases.add(phase);
        }

        return phases;
    }

    private List<List<YTask>> createTeamPhases(YNet workflow, List<List<YTask>> departmentPhases) {
        List<List<YTask>> teamPhases = new ArrayList<>();

        for (List<YTask> deptPhase : departmentPhases) {
            // Each department has 2-3 team phases
            int teamCount = random.nextInt(2, 4);
            for (int i = 0; i < teamCount; i++) {
                List<YTask> teamPhase = createTeamPhase(workflow, deptPhase.get(0).getTaskID() + "_team_" + (i + 1));
                teamPhases.add(teamPhase);
            }
        }

        return teamPhases;
    }

    private List<YTask> createTeamPhase(YNet workflow, String teamId) {
        List<YTask> teamTasks = new ArrayList<>();
        int teamSize = random.nextInt(3, 6);

        for (int i = 0; i < teamSize; i++) {
            YTask task = createTask(workflow, teamId + "_task_" + (i + 1),
                TASK_PATTERNS.get(random.nextInt(TASK_PATTERNS.size())));
            teamTasks.add(task);
            createTaskParameters(task, i == 0);
            createTaskOutputs(task, i == teamSize - 1);
        }

        return teamTasks;
    }

    private List<YTask> createIndividualTasks(YNet workflow, List<List<YTask>> teamPhases) {
        List<YTask> individualTasks = new ArrayList<>();
        int individualCount = random.nextInt(10, 20);

        for (int i = 0; i < individualCount; i++) {
            YTask task = createTask(workflow, "individual_" + (i + 1),
                TASK_PATTERNS.get(random.nextInt(TASK_PATTERNS.size())));
            individualTasks.add(task);
            createTaskParameters(task, i == 0);
            createTaskOutputs(task, i == individualCount - 1);
        }

        return individualTasks;
    }

    private List<YTask> createConsolidationPhase(YNet workflow, YTask source) {
        List<YTask> consolidationTasks = new ArrayList<>();
        int consolidationSize = random.nextInt(3, 6);

        for (int i = 0; i < consolidationSize; i++) {
            YTask task = createTask(workflow, "consolidation_" + (i + 1),
                "Final " + TASK_PATTERNS.get(random.nextInt(TASK_PATTERNS.size())));
            consolidationTasks.add(task);
            createTaskParameters(task, i == 0);
            createTaskOutputs(task, i == consolidationSize - 1);
        }

        return consolidationTasks;
    }

    /**
     * Validates the generated workflow for consistency
     */
    public List<String> validateWorkflow(YNet workflow) {
        List<String> issues = new ArrayList<>();

        // Check for disconnected tasks
        Set<YTask> connectedTasks = new HashSet<>();
        for (YFlow flow : workflow.getControlFlows()) {
            connectedTasks.add(flow.getSource());
            connectedTasks.add(flow.getTarget());
        }

        for (YTask task : workflow.getNetTasks()) {
            if (!connectedTasks.contains(task)) {
                issues.add("Task " + task.getTaskID() + " is disconnected from workflow");
            }
        }

        // Check for cycles (simplified check)
        if (hasCycle(workflow)) {
            issues.add("Workflow contains cycles");
        }

        // Check data consistency
        for (YTask task : workflow.getNetTasks()) {
            if (task.getInputParameters().isEmpty() && !workflow.getInputParameters().isEmpty()) {
                issues.add("Task " + task.getTaskID() + " has no input parameters");
            }
        }

        return issues;
    }

    private boolean hasCycle(YNet workflow) {
        // Simplified cycle detection - in real implementation, use DFS
        return false; // Placeholder for cycle detection
    }

    /**
     * Gets workflow statistics
     */
    public Map<String, Object> getWorkflowStats(YNet workflow) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("task_count", workflow.getNetTasks().size());
        stats.put("input_params", workflow.getInputParameters().size());
        stats.put("control_flows", workflow.getControlFlows().size());
        stats.put("data_variables", workflow.getDataVariables().size());
        stats.put("name", workflow.getName());
        stats.put("spec_id", workflow.getSpecificationID());

        // Calculate complexity metrics
        int totalParams = workflow.getNetTasks().stream()
            .mapToInt(t -> t.getInputParameters().size() + t.getOutputParameters().size())
            .sum();
        stats.put("total_parameters", totalParams);
        stats.put("avg_params_per_task", totalParams / Math.max(1, workflow.getNetTasks().size()));

        return stats;
    }
}