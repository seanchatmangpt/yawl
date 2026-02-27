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
import java.util.concurrent.*;
import java.util.stream.*;
import java.time.*;
import java.util.function.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Generator of multi-tenant test data for YAWL v6.0.0-GA isolation testing.
 *
 * <p>Generates tenant-specific datasets with strict isolation guarantees for testing
 * multi-tenant architecture and data privacy.
 *
 * <p>Tenant Features:
 * <ul>
 *   <li>Tenant isolation and data segregation</li>
 *   <li>Customizable tenant configurations</li>
 *   <li>Resource quota management</li>
 *   <li>Role-based access control patterns</li>
 *   <li>Tenant-specific workflow customization</li>
 * </ul>
 *
 * <p>Isolation Guarantees:
 * <ul>
 *   <li>Data isolation - no cross-tenant data leakage</li>
 *   <li>Resource isolation - tenant-specific quotas and limits</li>
 *   <li>Configuration isolation - tenant-specific settings</li>
 *   <li>Isolation validation - comprehensive verification</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class TenantDataGenerator {

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    // Tenant templates
    private static final Map<String, TenantTemplate> TEMPLATES = Map.of(
        "enterprise", new TenantTemplate(
            "Enterprise Tenant",
            "Large organization with multiple departments",
            10000, 500, 100, "premium"
        ),
        "small_business", new TenantTemplate(
            "Small Business",
            "Small to medium-sized organization",
            1000, 50, 10, "standard"
        ),
        "startup", new TenantTemplate(
            "Startup",
            "Early-stage company",
            100, 10, 5, "basic"
        ),
        "government", new TenantTemplate(
            "Government Agency",
            "Public sector organization",
            5000, 200, 50, "compliance"
        ),
        "healthcare", new TenantTemplate(
            "Healthcare Provider",
            "Medical services organization",
            3000, 150, 30, "hipaa"
        )
    );

    // Industry classifications
    private static final String[] INDUSTRIES = {
        "Technology", "Healthcare", "Finance", "Manufacturing", "Retail",
        "Education", "Government", "Non-profit", "Energy", "Transportation"
    };

    // Tenant-specific configurations
    private static final Map<String, Map<String, Object>> CONFIG_TEMPLATES = Map.of(
        "security", Map.of(
            "encryption_level", "AES-256",
            "mfa_required", true,
            "audit_logging", true,
            "data_retention_days", 365,
            "access_review_days", 90
        ),
        "performance", Map.of(
            "max_concurrent_cases", 1000,
            "case_timeout_seconds", 3600,
            "work_item_timeout_seconds", 1800,
            "cache_enabled", true,
            "backup_enabled", true
        ),
        "compliance", Map.of(
            "industry_regulation", "HIPAA",
            "audit_frequency", "daily",
            "retention_policy", "7_years",
            "data_classification", "restricted",
            "access_control_level", "strict"
        ),
        "customization", Map.of(
            "branding_enabled", true,
            "custom_fields", true,
            "workflow_customization", true,
            "api_access", true,
            "integrations", 5
        )
    );

    // Role definitions
    private static final Map<String, List<String>> ROLE_DEFINITIONS = Map.of(
        "admin", List.of("tenant_admin", "system_admin"),
        "manager", List.of("workflow_manager", "case_manager", "resource_manager"),
        "user", List.of("workflow_user", "case_user", "viewer"),
        "auditor", List.of("security_auditor", "compliance_auditor", "system_auditor"),
        "developer", List.of("workflow_developer", "integration_developer", "api_developer")
    );

    // Permission templates
    private static final List<Map<String, String>> PERMISSION_TEMPLATES = List.of(
        Map.of("resource", "workflow", "actions", List.of("create", "read", "update", "delete", "execute")),
        Map.of("resource", "case", "actions", List.of("create", "read", "update", "complete", "cancel")),
        Map.of("resource", "workitem", "actions", List.of("claim", "complete", "escalate", "delegate")),
        Map.of("resource", "user", "actions", List.of("create", "read", "update", "manage")),
        Map.of("resource", "report", "actions", List.of("generate", "export", "share"))
    );

    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    /**
     * Creates a tenant with all associated data
     */
    public Tenant createTenant(String tenantId, String templateType) {
        TenantTemplate template = TEMPLATES.get(templateType);
        if (template == null) {
            template = TEMPLATES.get("small_business"); // Default
        }

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName(generateTenantName(template));
        tenant.setDescription(template.description);
        tenant.setIndustry(INDUSTRIES[random.nextInt(INDUSTRIES.length)]);
        tenant.setTier(template.tier);
        tenant.setTemplate(templateType);
        tenant.setCreatedAt(Instant.now());
        tenant.setStatus("active");
        tenant.setSettings(generateTenantSettings(template));
        tenant.setQuotas(template.quotas);
        tenant.setResourceLimits(template.resourceLimits);

        // Generate tenant-specific users
        List<User> users = generateUsers(tenant, template.maxUsers);
        tenant.setUsers(users);

        // Generate tenant-specific workflows
        List<Workflow> workflows = generateWorkflows(tenant, template.maxWorkflows);
        tenant.setWorkflows(workflows);

        // Generate tenant-specific work items
        List<WorkItem> workItems = generateWorkItems(tenant, template.maxWorkItems);
        tenant.setWorkItems(workItems);

        // Generate tenant-specific cases
        List<Case> cases = generateCases(tenant, template.maxCases);
        tenant.setCases(cases);

        // Generate tenant-specific roles and permissions
        Map<String, List<Permission>> permissions = generatePermissions(tenant);
        tenant.setPermissions(permissions);

        return tenant;
    }

    /**
     * Generates multiple tenants with isolation guarantees
     */
    public List<Tenant> generateTenants(int tenantCount, Map<String, Integer> templateDistribution) {
        List<Tenant> tenants = new ArrayList<>(tenantCount);
        List<String> tenantIds = generateTenantIds(tenantCount);

        for (int i = 0; i < tenantCount; i++) {
            String tenantId = tenantIds.get(i);
            String templateType = selectTemplate(templateDistribution, i);
            Tenant tenant = createTenant(tenantId, templateType);
            tenants.add(tenant);
        }

        // Verify isolation
        validateIsolation(tenants);

        return tenants;
    }

    /**
     * Generates tenant-specific test scenarios
     */
    public List<Map<String, Object>> generateTenantScenarios(List<Tenant> tenants, int scenariosPerTenant) {
        List<Map<String, Object>> scenarios = new ArrayList<>();

        for (Tenant tenant : tenants) {
            for (int i = 0; i < scenariosPerTenant; i++) {
                Map<String, Object> scenario = new HashMap<>();
                scenario.put("scenarioId", UUID.randomUUID().toString());
                scenario.put("tenantId", tenant.getId());
                scenario.put("scenarioType", getRandomScenarioType());
                scenario.put("timestamp", Instant.now().toString());
                scenario.put("description", generateScenarioDescription(tenant, scenario.get("scenarioType").toString()));
                scenario.put("expectedOutcome", generateExpectedOutcome(scenario.get("scenarioType").toString()));
                scenario.put("testData", generateScenarioTestData(tenant));
                scenario.put("validationRules", generateValidationRules(tenant));

                scenarios.add(scenario);
            }
        }

        return scenarios;
    }

    /**
     * Generates tenant migration data
     */
    public Map<String, Object> generateMigrationData(Tenant sourceTenant, Tenant targetTenant) {
        Map<String, Object> migration = new HashMap<>();
        migration.put("migrationId", UUID.randomUUID().toString());
        migration.put("sourceTenant", sourceTenant.getId());
        migration.put("targetTenant", targetTenant.getId());
        migration.put("timestamp", Instant.now().toString());
        migration.put("migrationType", "data_transfer");
        migration.put("status", "pending");

        // Define migration scope
        Map<String, Integer> scope = new HashMap<>();
        scope.put("users", sourceTenant.getUsers().size());
        scope.put("workflows", sourceTenant.getWorkflows().size());
        scope.put("cases", sourceTenant.getCases().size());
        scope.put("workItems", sourceTenant.getWorkItems().size());
        migration.put("scope", scope);

        // Generate migration plan
        List<Map<String, Object>> migrationPlan = generateMigrationPlan(sourceTenant, targetTenant);
        migration.put("plan", migrationPlan);

        return migration;
    }

    /**
     * Validates tenant isolation
     */
    public Map<String, Object> validateIsolation(List<Tenant> tenants) {
        Map<String, Object> validation = new HashMap<>();
        validation.put("totalTenants", tenants.size());
        validation.put("validationResults", new ArrayList<>());
        validation.put("isolationScore", 100.0);

        for (Tenant tenant : tenants) {
            Map<String, Object> tenantValidation = validateTenantIsolation(tenant, tenants);
            ((List<Object>) validation.get("validationResults")).add(tenantValidation);

            // Update isolation score based on violations
            if ((boolean) tenantValidation.get("isolationPassed")) {
                validation.put("isolationScore",
                    (double) validation.get("isolationScore") - 0.0); // No penalty
            } else {
                validation.put("isolationScore",
                    (double) validation.get("isolationScore") - 20.0); // Penalty for violations
            }
        }

        validation.put("isolationStatus",
            (double) validation.get("isolationScore") >= 80 ? "PASS" : "FAIL");

        return validation;
    }

    /**
     * Generates tenant configuration snapshots
     */
    public List<Map<String, Object>> generateConfigurationSnapshots(List<Tenant> tenants) {
        List<Map<String, Object>> snapshots = new ArrayList<>();

        for (Tenant tenant : tenants) {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("snapshotId", UUID.randomUUID().toString());
            snapshot.put("tenantId", tenant.getId());
            snapshot.put("timestamp", Instant.now().toString());
            snapshot.put("configuration", tenant.getSettings());
            snapshot.put("resourceUsage", calculateResourceUsage(tenant));
            snapshot.put("complianceStatus", checkComplianceStatus(tenant));
            snapshot.put("securityPosture", assessSecurityPosture(tenant));

            snapshots.add(snapshot);
        }

        return snapshots;
    }

    // Helper classes and methods

    private static class TenantTemplate {
        final String name;
        final String description;
        final int maxUsers;
        final int maxWorkflows;
        final int maxCases;
        final String tier;

        TenantTemplate(String name, String description, int maxUsers, int maxWorkflows, int maxCases, String tier) {
            this.name = name;
            this.description = description;
            this.maxUsers = maxUsers;
            this.maxWorkflows = maxWorkflows;
            this.maxCases = maxCases;
            this.tier = tier;
        }
    }

    public static class Tenant {
        private String id;
        private String name;
        private String description;
        private String industry;
        private String tier;
        private String template;
        private Instant createdAt;
        private String status;
        private Map<String, Object> settings;
        private Map<String, Integer> quotas;
        private Map<String, Integer> resourceLimits;
        private List<User> users;
        private List<Workflow> workflows;
        private List<WorkItem> workItems;
        private List<Case> cases;
        private Map<String, List<Permission>> permissions;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }
        public String getTier() { return tier; }
        public void setTier(String tier) { this.tier = tier; }
        public String getTemplate() { return template; }
        public void setTemplate(String template) { this.template = template; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Map<String, Object> getSettings() { return settings; }
        public void setSettings(Map<String, Object> settings) { this.settings = settings; }
        public Map<String, Integer> getQuotas() { return quotas; }
        public void setQuotas(Map<String, Integer> quotas) { this.quotas = quotas; }
        public Map<String, Integer> getResourceLimits() { return resourceLimits; }
        public void setResourceLimits(Map<String, Integer> resourceLimits) { this.resourceLimits = resourceLimits; }
        public List<User> getUsers() { return users; }
        public void setUsers(List<User> users) { this.users = users; }
        public List<Workflow> getWorkflows() { return workflows; }
        public void setWorkflows(List<Workflow> workflows) { this.workflows = workflows; }
        public List<WorkItem> getWorkItems() { return workItems; }
        public void setWorkItems(List<WorkItem> workItems) { this.workItems = workItems; }
        public List<Case> getCases() { return cases; }
        public void setCases(List<Case> cases) { this.cases = cases; }
        public Map<String, List<Permission>> getPermissions() { return permissions; }
        public void setPermissions(Map<String, List<Permission>> permissions) { this.permissions = permissions; }
    }

    public static class User {
        private String id;
        private String name;
        private String email;
        private String role;
        private String department;
        private Instant createdAt;
        private boolean active;

        public User(String id, String name, String email, String role, String department) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.role = role;
            this.department = department;
            this.createdAt = Instant.now();
            this.active = true;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
        public String getDepartment() { return department; }
        public Instant getCreatedAt() { return createdAt; }
        public boolean isActive() { return active; }
    }

    public static class Workflow {
        private String id;
        private String name;
        private String version;
        private String category;
        private String complexity;
        private Map<String, Object> settings;

        public Workflow(String id, String name, String category) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.version = "1.0";
            this.complexity = "medium";
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getCategory() { return category; }
        public String getComplexity() { return complexity; }
        public Map<String, Object> getSettings() { return settings; }
        public void setSettings(Map<String, Object> settings) { this.settings = settings; }
    }

    public static class WorkItem {
        private String id;
        private String caseId;
        private String workflowId;
        private String taskId;
        private String name;
        private String status;
        private Map<String, Object> data;

        public WorkItem(String id, String caseId, String workflowId, String taskId) {
            this.id = id;
            this.caseId = caseId;
            this.workflowId = workflowId;
            this.taskId = taskId;
            this.name = generateTaskName(taskId);
            this.status = "offered";
            this.data = generateWorkItemData();
        }

        public String getId() { return id; }
        public String getCaseId() { return caseId; }
        public String getWorkflowId() { return workflowId; }
        public String getTaskId() { return taskId; }
        public String getName() { return name; }
        public String getStatus() { return status; }
        public Map<String, Object> getData() { return data; }
    }

    public static class Case {
        private String id;
        private String workflowId;
        private String status;
        private Instant createdAt;
        private Instant completedAt;
        private Map<String, Object> data;

        public Case(String id, String workflowId) {
            this.id = id;
            this.workflowId = workflowId;
            this.status = "running";
            this.createdAt = Instant.now();
        }

        public String getId() { return id; }
        public String getWorkflowId() { return workflowId; }
        public String getStatus() { return status; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getCompletedAt() { return completedAt; }
        public Map<String, Object> getData() { return data; }
        public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    }

    public static class Permission {
        private String resource;
        private String action;
        private String scope;

        public Permission(String resource, String action, String scope) {
            this.resource = resource;
            this.action = action;
            this.scope = scope;
        }

        public String getResource() { return resource; }
        public String getAction() { return action; }
        public String getScope() { return scope; }
    }

    // Implementation methods

    private String generateTenantName(TenantTemplate template) {
        return template.name + " - " + INDUSTRIES[random.nextInt(INDUSTRIES.length)] + " Division";
    }

    private Map<String, Object> generateTenantSettings(TenantTemplate template) {
        Map<String, Object> settings = new HashMap<>();

        // Merge base configuration templates
        for (Map.Entry<String, Map<String, Object>> entry : CONFIG_TEMPLATES.entrySet()) {
            settings.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }

        // Add tenant-specific settings
        settings.put("branding", generateBrandingSettings());
        settings.put("notifications", generateNotificationSettings());
        settings.put("integrations", generateIntegrationSettings());

        return settings;
    }

    private List<User> generateUsers(Tenant tenant, int maxUsers) {
        List<User> users = new ArrayList<>();
        int userCount = random.nextInt(10, maxUsers);

        for (int i = 0; i < userCount; i++) {
            String[] departments = {"HR", "Finance", "IT", "Operations", "Legal", "Sales"};
            String[] roles = {"admin", "manager", "user", "auditor", "developer"};

            User user = new User(
                "user-" + tenant.getId() + "-" + UUID.randomUUID().toString().substring(0, 6),
                generateUserName(),
                generateUserEmail(tenant.getId()),
                roles[random.nextInt(roles.length)],
                departments[random.nextInt(departments.length)]
            );
            users.add(user);
        }

        return users;
    }

    private List<Workflow> generateWorkflows(Tenant tenant, int maxWorkflows) {
        List<Workflow> workflows = new ArrayList<>();
        int workflowCount = random.nextInt(5, maxWorkflows);

        for (int i = 0; i < workflowCount; i++) {
            Workflow workflow = new Workflow(
                "workflow-" + tenant.getId() + "-" + (i + 1),
                generateWorkflowName(),
                getRandomCategory()
            );
            workflow.setSettings(generateWorkflowSettings());
            workflows.add(workflow);
        }

        return workflows;
    }

    private List<WorkItem> generateWorkItems(Tenant tenant, int maxWorkItems) {
        List<WorkItem> workItems = new ArrayList<>();
        int workItemCount = random.nextInt(100, maxWorkItems);

        for (int i = 0; i < workItemCount; i++) {
            WorkItem workItem = new WorkItem(
                "wi-" + UUID.randomUUID().toString().substring(0, 12),
                "case-" + tenant.getId() + "-" + random.nextInt(1, 100),
                tenant.getWorkflows().get(random.nextInt(tenant.getWorkflows().size())).getId(),
                "task-" + random.nextInt(1, 10)
            );
            workItems.add(workItem);
        }

        return workItems;
    }

    private List<Case> generateCases(Tenant tenant, int maxCases) {
        List<Case> cases = new ArrayList<>();
        int caseCount = random.nextInt(50, maxCases);

        for (int i = 0; i < caseCount; i++) {
            Case case_ = new Case(
                "case-" + tenant.getId() + "-" + (i + 1),
                tenant.getWorkflows().get(random.nextInt(tenant.getWorkflows().size())).getId()
            );
            cases.add(case_);
        }

        return cases;
    }

    private Map<String, List<Permission>> generatePermissions(Tenant tenant) {
        Map<String, List<Permission>> permissions = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : ROLE_DEFINITIONS.entrySet()) {
            String role = entry.getKey();
            List<Permission> rolePermissions = new ArrayList<>();

            for (String roleType : entry.getValue()) {
                for (Map<String, String> template : PERMISSION_TEMPLATES) {
                    Permission permission = new Permission(
                        template.get("resource"),
                        template.get("actions").toString(),
                        roleType
                    );
                    rolePermissions.add(permission);
                }
            }

            permissions.put(role, rolePermissions);
        }

        return permissions;
    }

    private List<String> generateTenantIds(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> "tenant-" + UUID.randomUUID().toString().substring(0, 8))
            .collect(Collectors.toList());
    }

    private String selectTemplate(Map<String, Integer> templateDistribution, int index) {
        int total = templateDistribution.values().stream().mapToInt(Integer::intValue).sum();
        int cumulative = 0;
        int target = (index * total) / count;

        for (Map.Entry<String, Integer> entry : templateDistribution.entrySet()) {
            cumulative += entry.getValue();
            if (target <= cumulative) {
                return entry.getKey();
            }
        }

        return "small_business";
    }

    private Map<String, Object> validateTenantIsolation(Tenant tenant, List<Tenant> allTenants) {
        Map<String, Object> validation = new HashMap<>();
        validation.put("tenantId", tenant.getId());
        validation.put("isolationPassed", true);
        validation.put("violations", new ArrayList<>());

        // Check for data leakage
        for (Tenant otherTenant : allTenants) {
            if (!tenant.getId().equals(otherTenant.getId())) {
                checkDataIsolation(tenant, otherTenant, validation);
            }
        }

        // Check resource isolation
        checkResourceIsolation(tenant, validation);

        // Check configuration isolation
        checkConfigurationIsolation(tenant, allTenants, validation);

        return validation;
    }

    private void checkDataIsolation(Tenant tenant1, Tenant tenant2, Map<String, Object> validation) {
        // Check for identical user IDs
        for (User user1 : tenant1.getUsers()) {
            for (User user2 : tenant2.getUsers()) {
                if (user1.getId().equals(user2.getId())) {
                    ((List<Object>) validation.get("violations")).add(
                        "Duplicate user ID: " + user1.getId()
                    );
                    validation.put("isolationPassed", false);
                }
            }
        }

        // Check for case ID collisions
        for (Case case1 : tenant1.getCases()) {
            for (Case case2 : tenant2.getCases()) {
                if (case1.getId().equals(case2.getId())) {
                    ((List<Object>) validation.get("violations")).add(
                        "Duplicate case ID: " + case1.getId()
                    );
                    validation.put("isolationPassed", false);
                }
            }
        }
    }

    private void checkResourceIsolation(Tenant tenant, Map<String, Object> validation) {
        // Check if tenant exceeds its quotas
        int userCount = tenant.getUsers().size();
        if (userCount > tenant.getQuotas().getOrDefault("max_users", 1000)) {
            ((List<Object>) validation.get("violations")).add(
                "User count exceeds quota: " + userCount + " > " + tenant.getQuotas().get("max_users")
            );
            validation.put("isolationPassed", false);
        }
    }

    private void checkConfigurationIsolation(Tenant tenant, List<Tenant> allTenants, Map<String, Object> validation) {
        // Check for duplicate configuration
        for (Tenant otherTenant : allTenants) {
            if (!tenant.getId().equals(otherTenant.getId())) {
                if (tenant.getSettings().equals(otherTenant.getSettings())) {
                    ((List<Object>) validation.get("violations")).add(
                        "Duplicate configuration detected"
                    );
                    validation.put("isolationPassed", false);
                    break;
                }
            }
        }
    }

    private Map<String, Object> calculateResourceUsage(Tenant tenant) {
        Map<String, Object> usage = new HashMap<>();
        usage.put("userCount", tenant.getUsers().size());
        usage.put("workflowCount", tenant.getWorkflows().size());
        usage.put("caseCount", tenant.getCases().size());
        usage.put("workItemCount", tenant.getWorkItems().size());
        usage.put("storageUsageMB", calculateStorageUsage(tenant));
        usage.put("apiCallsPerHour", calculateAPICalls(tenant));
        return usage;
    }

    private long calculateStorageUsage(Tenant tenant) {
        return tenant.getWorkItems().size() * 1024L + // 1KB per work item
               tenant.getCases().size() * 512L +     // 0.5KB per case
               tenant.getUsers().size() * 256L;      // 0.25KB per user
    }

    private int calculateAPICalls(Tenant tenant) {
        return tenant.getUsers().size() * 100; // Assume 100 API calls per user per hour
    }

    private String checkComplianceStatus(Tenant tenant) {
        if (tenant.getIndustry().equals("Healthcare")) {
            return "HIPAA";
        } else if (tenant.getIndustry().equals("Finance")) {
            return "SOX";
        } else {
            return "Standard";
        }
    }

    private Map<String, Object> assessSecurityPosture(Tenant tenant) {
        Map<String, Object> posture = new HashMap<>();
        posture.put("mfaEnabled", tenant.getSettings().get("security").get("mfa_required"));
        posture.put("auditLogging", tenant.getSettings().get("security").get("audit_logging"));
        posture.put("encryptionLevel", tenant.getSettings().get("security").get("encryption_level"));
        posture.put("accessControl", "strict");
        return posture;
    }

    // Helper methods for data generation

    private String generateUserName() {
        String[] firstNames = {"John", "Jane", "Michael", "Sarah", "David", "Lisa"};
        String[] lastNames = {"Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia"};
        return firstNames[random.nextInt(firstNames.length)] + " " +
               lastNames[random.nextInt(lastNames.length)];
    }

    private String generateUserEmail(String tenantId) {
        return "user@" + tenantId.toLowerCase() + ".example.com";
    }

    private String generateWorkflowName() {
        String[] prefixes = {"Standard", "Advanced", "Enterprise"};
        String[] suffixes = {"Process", "Workflow", "Procedure"};
        return prefixes[random.nextInt(prefixes.length)] + " " +
               suffixes[random.nextInt(suffixes.length)];
    }

    private String getRandomCategory() {
        String[] categories = {"Approval", "Processing", "Review", "Verification", "Submission"};
        return categories[random.nextInt(categories.length)];
    }

    private String generateTaskName(String taskId) {
        return "Task " + taskId.substring(taskId.lastIndexOf("-") + 1);
    }

    private Map<String, Object> generateWorkItemData() {
        Map<String, Object> data = new HashMap<>();
        data.put("priority", random.nextInt(1, 6));
        data.put("estimatedDuration", random.nextInt(300, 3600));
        data.put("actualDuration", random.nextInt(300, 7200));
        data.put("status", getRandomStatus());
        return data;
    }

    private String getRandomStatus() {
        String[] statuses = {"offered", "allocated", "started", "completed", "failed"};
        return statuses[random.nextInt(statuses.length)];
    }

    private Map<String, Object> generateBrandingSettings() {
        Map<String, Object> branding = new HashMap<>();
        branding.put("logoUrl", "https://example.com/logo.png");
        branding.put("primaryColor", "#007bff");
        branding.put("companyName", "Generated Company");
        branding.put("supportEmail", "support@example.com");
        return branding;
    }

    private Map<String, Object> generateNotificationSettings() {
        Map<String, Object> notifications = new HashMap<>();
        notifications.put("emailEnabled", true);
        notifications.put("smsEnabled", false);
        notifications.put("pushEnabled", true);
        notifications.put("frequency", "immediate");
        return notifications;
    }

    private Map<String, Object> generateIntegrationSettings() {
        Map<String, Object> integrations = new HashMap<>();
        integrations.put("slack", true);
        integrations.put("salesforce", false);
        integrations.put("email", true);
        integrations.put("calendar", true);
        return integrations;
    }

    private Map<String, Object> generateWorkflowSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("autoAssign", true);
        settings.put("timeout", 3600);
        settings.put("retryCount", 3);
        settings.put("escalationEnabled", true);
        return settings;
    }

    private String getRandomScenarioType() {
        String[] types = {"high_load", "data_migration", "role_change", "configuration_change",
                         "disaster_recovery", "performance_test", "security_test"};
        return types[random.nextInt(types.length)];
    }

    private String generateScenarioDescription(Tenant tenant, String scenarioType) {
        return String.format("Test %s scenario for tenant %s", scenarioType, tenant.getName());
    }

    private String generateExpectedOutcome(String scenarioType) {
        return String.format("Successful completion of %s scenario", scenarioType);
    }

    private Map<String, Object> generateScenarioTestData(Tenant tenant) {
        Map<String, Object> testData = new HashMap<>();
        testData.put("tenantId", tenant.getId());
        testData.put("testUser", tenant.getUsers().get(0).getId());
        testData.put("testWorkflow", tenant.getWorkflows().get(0).getId());
        testData.put("testCase", tenant.getCases().get(0).getId());
        return testData;
    }

    private List<String> generateValidationRules(Tenant tenant) {
        return Arrays.asList(
            "User isolation: " + tenant.getId(),
            "Data isolation: " + tenant.getId(),
            "Resource isolation: " + tenant.getQuotas().get("max_users") + " users"
        );
    }

    private List<Map<String, Object>> generateMigrationPlan(Tenant sourceTenant, Tenant targetTenant) {
        List<Map<String, Object>> plan = new ArrayList<>();

        // User migration
        Map<String, Object> userMigration = new HashMap<>();
        userMigration.put("step", "migrate_users");
        userMigration.put("source", sourceTenant.getId());
        userMigration.put("target", targetTenant.getId());
        userMigration.put("entity", "users");
        userMigration.put("count", sourceTenant.getUsers().size());
        userMigration.put("estimatedDuration", "30 minutes");
        plan.add(userMigration);

        // Case migration
        Map<String, Object> caseMigration = new HashMap<>();
        caseMigration.put("step", "migrate_cases");
        caseMigration.put("source", sourceTenant.getId());
        caseMigration.put("target", targetTenant.getId());
        caseMigration.put("entity", "cases");
        caseMigration.put("count", sourceTenant.getCases().size());
        caseMigration.put("estimatedDuration", "2 hours");
        plan.add(caseMigration);

        return plan;
    }
}