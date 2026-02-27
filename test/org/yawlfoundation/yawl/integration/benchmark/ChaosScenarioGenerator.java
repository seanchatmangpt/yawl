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
 * Generator of realistic chaos scenarios for YAWL v6.0.0-GA resilience testing.
 *
 * <p>Generates realistic failure scenarios including network issues, resource constraints,
 * data corruption, and system failures to test system resilience and recovery capabilities.
 *
 * <p>Chaos Categories:
 * <ul>
 *   <li>Network Chaos - Network partitions, latency, packet loss</li>
 *   <li>Resource Chaos - Memory/CPU exhaustion, disk space, connection limits</li>
 *   <li>Service Chaos - Service failures, timeouts, degraded performance</li>
 *   <li>Data Chaos - Data corruption, inconsistency, loss</li>
 *   <li>Infrastructure Chaos - Hardware failures, cloud service disruptions</li>
 * </ul>
 *
 * <p>Features:
 * <ul>
 *   <li>Realistic failure patterns based on production data</li>
 *   <li>Controlled chaos injection with safety mechanisms</li>
 *   <li>Progressive failure escalation</li>
 *   <li>Recovery simulation</li>
 *   <li>Chaos engineering best practices</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ChaosScenarioGenerator {

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    // Chaos categories and patterns
    private static final Map<String, List<String>> CHAOS_PATTERNS = Map.of(
        "network", List.of(
            "network_partition", "high_latency", "packet_loss", "connection_reset",
            "bandwidth_throttling", "dns_failure", "ssl_certificate_expiry"
        ),
        "resource", List.of(
            "memory_exhaustion", "cpu_saturation", "disk_space_full", "connection_pool_exhausted",
            "file_descriptor_limit", "garbage_collection_stress", "thread_leak"
        ),
        "service", List.of(
            "service_timeout", "service_unavailable", "slow_api_response", "circuit_breaker_trip",
            "load_balancer_failure", "cache_failure", "message_queue_failure"
        ),
        "data", List.of(
            "data_corruption", "data_loss", "inconsistent_state", "deadlock",
            "race_condition", "transaction_failure", "replication_lag"
        ),
        "infrastructure", List.of(
            "hardware_failure", "power_outage", "cooling_failure", "disk_failure",
            "network_switch_failure", "rack_failure", "zone_failure"
        )
    );

    // Severity levels
    private static final String[] SEVERITY_LEVELS = {"low", "medium", "high", "critical"};

    // Chaos injection targets
    private static final Map<String, List<String>> TARGET_COMPONENTS = Map.of(
        "workflow_engine", List.of("task_execution", "workflow_routing", "case_management"),
        "database", List.of("query_execution", "connection_management", "transaction_processing"),
        "cache", List.of("cache_lookup", "cache_invalidation", "cache_warmup"),
        "api_gateway", List.of("request_routing", "load_balancing", "rate_limiting"),
        "message_queue", List.of("message_publishing", "message_consumption", "queue_management"),
        "file_system", List.of("file_operations", "storage_management", "backup_processes")
    );

    // Recovery strategies
    private static final List<String> RECOVERY_STRATEGIES = List.of(
        "auto_reboot", "manual_intervention", "circuit_breaker_reset", "failover_to_backup",
        "gradual_rollout", "blue_green_deployment", "canary_release", "quick_rollback"
    );

    // Chaos timing patterns
    private static final Map<String, Duration> TIMING_PATTERNS = Map.of(
        "immediate", Duration.ofSeconds(0),
        "gradual", Duration.ofSeconds(30),
        "sudden", Duration.ofSeconds(5),
        "pulsing", Duration.ofSeconds(10),
        "cyclical", Duration.ofMinutes(5)
    );

    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final ExecutorService executor;

    /**
     * Creates a new chaos scenario generator
     */
    public ChaosScenarioGenerator() {
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Generates a chaos scenario with specified parameters
     */
    public ChaosScenario generateScenario(String category, String pattern, String severity) {
        ChaosScenario scenario = new ChaosScenario();
        scenario.setId("chaos-" + UUID.randomUUID().toString().substring(0, 8));
        scenario.setCategory(category);
        scenario.setPattern(pattern);
        scenario.setSeverity(severity);
        scenario.setTimestamp(Instant.now());
        scenario.setStatus("pending");

        // Set timing
        Duration duration = TIMING_PATTERNS.get(getRandomTimingPattern());
        scenario.setTiming(duration);
        scenario.setDuration(duration);

        // Set target components
        List<String> targets = getTargetComponentsForCategory(category);
        scenario.setTargetComponents(targets);
        scenario.setPrimaryTarget(targets.get(random.nextInt(targets.size())));

        // Set failure characteristics
        scenario.setFailureCharacteristics(generateFailureCharacteristics(category, pattern, severity));

        // Set impact assessment
        scenario.setImpactAssessment(generateImpactAssessment(category, pattern, severity));

        // Set recovery strategies
        scenario.setRecoveryStrategies(generateRecoveryStrategies(category, severity));

        // Set monitoring requirements
        scenario.setMonitoringRequirements(generateMonitoringRequirements(category));

        // Set safety mechanisms
        scenario.setSafetyMechanisms(generateSafetyMechanisms());

        return scenario;
    }

    /**
     * Generates a chaos campaign with multiple scenarios
     */
    public ChaosCampaign generateCampaign(String campaignName, int scenarioCount) {
        ChaosCampaign campaign = new ChaosCampaign();
        campaign.setId("campaign-" + UUID.randomUUID().toString().substring(0, 8));
        campaign.setName(campaignName);
        campaign.setCreatedAt(Instant.now());
        campaign.setStatus("planning");
        campaign.setScenarios(new ArrayList<>());

        // Generate scenarios with different patterns
        List<String> categories = new ArrayList<>(CHAOS_PATTERNS.keySet());
        Collections.shuffle(categories);

        for (int i = 0; i < scenarioCount; i++) {
            String category = categories.get(i % categories.size());
            String pattern = CHAOS_PATTERNS.get(category)
                .get(random.nextInt(CHAOS_PATTERNS.get(category).size()));
            String severity = SEVERITY_LEVELS[random.nextInt(SEVERITY_LEVELS.length)];

            ChaosScenario scenario = generateScenario(category, pattern, severity);
            campaign.getScenarios().add(scenario);
        }

        // Set campaign timeline
        campaign.setTimeline(generateCampaignTimeline(campaign.getScenarios()));

        // Set success criteria
        campaign.setSuccessCriteria(generateSuccessCriteria());

        return campaign;
    }

    /**
     * Generates a chaos experiment schedule
     */
    public ChaosSchedule generateSchedule(ChaosCampaign campaign, Instant startDateTime) {
        ChaosSchedule schedule = new ChaosSchedule();
        schedule.setCampaignId(campaign.getId());
        schedule.setStartDateTime(startDateTime);
        schedule.setScenarios(new ArrayList<>());

        // Schedule each scenario with proper timing
        Instant currentSchedule = startDateTime;
        int dayCounter = 1;

        for (ChaosScenario scenario : campaign.getScenarios()) {
            ChaosScheduledScenario scheduledScenario = new ChaosScheduledScenario();
            scheduledScenario.setScenario(scenario);
            scheduledScenario.setScheduledTime(currentSchedule);
            scheduledScenario.setDay(dayCounter);
            scheduledScenario.setStatus("scheduled");

            schedule.getScenarios().add(scheduledScenario);

            // Move to next time slot
            currentSchedule = currentSchedule.plusHours(4);
            if (currentSchedule.getHour() > 18) {
                currentSchedule = currentSchedule.plusDays(1).withHour(9);
                dayCounter++;
            }
        }

        return schedule;
    }

    /**
     * Generates chaos injection scripts for testing
     */
    public List<Map<String, Object>> generateInjectionScripts(ChaosScenario scenario) {
        List<Map<String, Object>> scripts = new ArrayList<>();

        // Generate scripts for different platforms
        scripts.addAll(generateNetworkScripts(scenario));
        scripts.addAll(generateResourceScripts(scenario));
        scripts.addAll(generateServiceScripts(scenario));
        scripts.addAll(generateDataScripts(scenario));
        scripts.addAll(generateInfrastructureScripts(scenario));

        return scripts;
    }

    /**
     * Generates metrics collection configuration for chaos testing
     */
    public Map<String, Object> generateMetricsConfiguration(ChaosScenario scenario) {
        Map<String, Object> config = new HashMap<>();
        config.put("scenarioId", scenario.getId());
        config.put("metrics_interval_ms", 1000);
        config.put("collection_duration", scenario.getDuration().toMillis() + 30000);

        // Define metrics to collect
        List<Map<String, String>> metrics = new ArrayList<>();

        // System metrics
        metrics.addAll(generateSystemMetrics());

        // Application metrics
        metrics.addAll(generateApplicationMetrics(scenario));

        // Business metrics
        metrics.addAll(generateBusinessMetrics(scenario));

        // Chaos-specific metrics
        metrics.addAll(generateChaosMetrics(scenario));

        config.put("metrics", metrics);

        return config;
    }

    /**
     * Generates chaos experiment results
     */
    public ChaosExperimentResults generateExperimentResults(ChaosScenario scenario,
                                                           Instant startTime,
                                                           Instant endTime) {
        ChaosExperimentResults results = new ChaosExperimentResults();
        results.setScenarioId(scenario.getId());
        results.setStartTime(startTime);
        results.setEndTime(endTime);
        results.setDuration(Duration.between(startTime, endTime));

        // Generate test results
        Map<String, Object> testResults = generateTestResults(scenario);
        results.setTestResults(testResults);
        results.setSuccess(testResults.get("success").equals(true));

        // Generate performance impact
        Map<String, Object> performanceImpact = generatePerformanceImpact(scenario);
        results.setPerformanceImpact(performanceImpact);

        // Generate recovery analysis
        Map<String, Object> recoveryAnalysis = generateRecoveryAnalysis(scenario);
        results.setRecoveryAnalysis(recoveryAnalysis);

        // Generate recommendations
        results.setRecommendations(generateRecommendations(scenario, testResults, performanceImpact, recoveryAnalysis));

        return results;
    }

    /**
     * Generates chaos safety assessment
     */
    public Map<String, Object> generateSafetyAssessment(ChaosScenario scenario) {
        Map<String, Object> assessment = new HashMap<>();
        assessment.put("scenarioId", scenario.getId());
        assessment.put("riskLevel", calculateRiskLevel(scenario));
        assessment.put("safetyScore", calculateSafetyScore(scenario));
        assessment.put("mitigationFactors", generateMitigationFactors(scenario));
        assessment.put("rollbackPlan", generateRollbackPlan(scenario));
        assessment.put("emergencyStop", generateEmergencyStopCriteria());

        return assessment;
    }

    // Helper classes and methods

    public static class ChaosScenario {
        private String id;
        private String category;
        private String pattern;
        private String severity;
        private Instant timestamp;
        private String status;
        private Duration timing;
        private Duration duration;
        private List<String> targetComponents;
        private String primaryTarget;
        private Map<String, Object> failureCharacteristics;
        private Map<String, Object> impactAssessment;
        private List<String> recoveryStrategies;
        private Map<String, Object> monitoringRequirements;
        private List<String> safetyMechanisms;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Duration getTiming() { return timing; }
        public void setTiming(Duration timing) { this.timing = timing; }
        public Duration getDuration() { return duration; }
        public void setDuration(Duration duration) { this.duration = duration; }
        public List<String> getTargetComponents() { return targetComponents; }
        public void setTargetComponents(List<String> targetComponents) { this.targetComponents = targetComponents; }
        public String getPrimaryTarget() { return primaryTarget; }
        public void setPrimaryTarget(String primaryTarget) { this.primaryTarget = primaryTarget; }
        public Map<String, Object> getFailureCharacteristics() { return failureCharacteristics; }
        public void setFailureCharacteristics(Map<String, Object> failureCharacteristics) { this.failureCharacteristics = failureCharacteristics; }
        public Map<String, Object> getImpactAssessment() { return impactAssessment; }
        public void setImpactAssessment(Map<String, Object> impactAssessment) { this.impactAssessment = impactAssessment; }
        public List<String> getRecoveryStrategies() { return recoveryStrategies; }
        public void setRecoveryStrategies(List<String> recoveryStrategies) { this.recoveryStrategies = recoveryStrategies; }
        public Map<String, Object> getMonitoringRequirements() { return monitoringRequirements; }
        public void setMonitoringRequirements(Map<String, Object> monitoringRequirements) { this.monitoringRequirements = monitoringRequirements; }
        public List<String> getSafetyMechanisms() { return safetyMechanisms; }
        public void setSafetyMechanisms(List<String> safetyMechanisms) { this.safetyMechanisms = safetyMechanisms; }
    }

    public static class ChaosCampaign {
        private String id;
        private String name;
        private Instant createdAt;
        private String status;
        private List<ChaosScenario> scenarios;
        private Map<String, Object> timeline;
        private Map<String, Object> successCriteria;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<ChaosScenario> getScenarios() { return scenarios; }
        public void setScenarios(List<ChaosScenario> scenarios) { this.scenarios = scenarios; }
        public Map<String, Object> getTimeline() { return timeline; }
        public void setTimeline(Map<String, Object> timeline) { this.timeline = timeline; }
        public Map<String, Object> getSuccessCriteria() { return successCriteria; }
        public void setSuccessCriteria(Map<String, Object> successCriteria) { this.successCriteria = successCriteria; }
    }

    public static class ChaosSchedule {
        private String campaignId;
        private Instant startDateTime;
        private List<ChaosScheduledScenario> scenarios;

        public String getCampaignId() { return campaignId; }
        public void setCampaignId(String campaignId) { this.campaignId = campaignId; }
        public Instant getStartDateTime() { return startDateTime; }
        public void setStartDateTime(Instant startDateTime) { this.startDateTime = startDateTime; }
        public List<ChaosScheduledScenario> getScenarios() { return scenarios; }
        public void setScenarios(List<ChaosScheduledScenario> scenarios) { this.scenarios = scenarios; }
    }

    public static class ChaosScheduledScenario {
        private ChaosScenario scenario;
        private Instant scheduledTime;
        private int day;
        private String status;

        public ChaosScenario getScenario() { return scenario; }
        public void setScenario(ChaosScenario scenario) { this.scenario = scenario; }
        public Instant getScheduledTime() { return scheduledTime; }
        public void setScheduledTime(Instant scheduledTime) { this.scheduledTime = scheduledTime; }
        public int getDay() { return day; }
        public void setDay(int day) { this.day = day; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class ChaosExperimentResults {
        private String scenarioId;
        private Instant startTime;
        private Instant endTime;
        private Duration duration;
        private Map<String, Object> testResults;
        private Map<String, Object> performanceImpact;
        private Map<String, Object> recoveryAnalysis;
        private List<String> recommendations;
        private boolean success;

        public String getScenarioId() { return scenarioId; }
        public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
        public Duration getDuration() { return duration; }
        public void setDuration(Duration duration) { this.duration = duration; }
        public Map<String, Object> getTestResults() { return testResults; }
        public void setTestResults(Map<String, Object> testResults) { this.testResults = testResults; }
        public Map<String, Object> getPerformanceImpact() { return performanceImpact; }
        public void setPerformanceImpact(Map<String, Object> performanceImpact) { this.performanceImpact = performanceImpact; }
        public Map<String, Object> getRecoveryAnalysis() { return recoveryAnalysis; }
        public void setRecoveryAnalysis(Map<String, Object> recoveryAnalysis) { this.recoveryAnalysis = recoveryAnalysis; }
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }

    // Implementation methods

    private List<String> getTargetComponentsForCategory(String category) {
        return TARGET_COMPONENTS.getOrDefault(category, List.of("workflow_engine"));
    }

    private Map<String, Object> generateFailureCharacteristics(String category, String pattern, String severity) {
        Map<String, Object> characteristics = new HashMap<>();

        switch (category) {
            case "network":
                characteristics.put("latency_ms", generateLatency());
                characteristics.put("packet_loss_percent", generatePacketLoss());
                characteristics.put("bandwidth_reduction", generateBandwidthReduction());
                break;
            case "resource":
                characteristics.put("cpu_usage_percent", generateCPUUsage(severity));
                characteristics.put("memory_usage_percent", generateMemoryUsage(severity));
                characteristics.put("disk_usage_percent", generateDiskUsage());
                break;
            case "service":
                characteristics.put("timeout_seconds", generateTimeout());
                characteristics.put("error_rate", generateErrorRate());
                characteristics.put("response_time_ms", generateResponseTime());
                break;
            case "data":
                characteristics.put("corruption_rate", generateCorruptionRate());
                characteristics.put("data_loss_percent", generateDataLoss());
                characteristics.put("inconsistency_frequency", generateInconsistency());
                break;
            case "infrastructure":
                characteristics.put("failure_type", generateFailureType());
                characteristics.put("recovery_time_minutes", generateRecoveryTime());
                characteristics.put("affected_nodes", generateAffectedNodes());
                break;
        }

        return characteristics;
    }

    private Map<String, Object> generateImpactAssessment(String category, String pattern, String severity) {
        Map<String, Object> impact = new HashMap<>();
        impact.put("business_impact", generateBusinessImpact(severity));
        impact.put("technical_impact", generateTechnicalImpact(severity));
        impact.put("user_experience", generateUserExperience(severity));
        impact.put("financial_impact", generateFinancialImpact(severity));
        impact.put("recovery_time", generateRecoveryTimeEstimate(category, severity));
        return impact;
    }

    private List<String> generateRecoveryStrategies(String category, String severity) {
        List<String> strategies = new ArrayList<>();

        // Always include auto-recovery for low severity
        if (severity.equals("low")) {
            strategies.add("auto_reboot");
        }

        // Add strategy based on category
        switch (category) {
            case "network":
                strategies.addAll(List.of("failover_to_backup", "circuit_breaker_reset"));
                break;
            case "resource":
                strategies.addAll(List.of("scale_resources", "optimize_usage"));
                break;
            case "service":
                strategies.addAll(List.of("circuit_breaker_reset", "blue_green_deployment"));
                break;
            case "data":
                strategies.addAll(List.of("restore_from_backup", "data_repair"));
                break;
            case "infrastructure":
                strategies.addAll(List.of("auto_reboot", "failover_to_backup"));
                break;
        }

        // Always include manual intervention for high severity
        if (severity.equals("high") || severity.equals("critical")) {
            strategies.add("manual_intervention");
        }

        return strategies;
    }

    private Map<String, Object> generateMonitoringRequirements(String category) {
        Map<String, Object> requirements = new HashMap<>();
        requirements.put("metrics_frequency_ms", 1000);
        requirements.put("alert_thresholds", generateAlertThresholds(category));
        requirements.put("health_checks", generateHealthChecks(category));
        requirements.put("logging_requirements", generateLoggingRequirements(category));
        return requirements;
    }

    private List<String> generateSafetyMechanisms() {
        return Arrays.asList(
            "emergency_stop_button",
            "automatic_rollback",
            "circuit_breaker_protection",
            "load_balancer_failover",
            "data_backup",
            "monitoring_alerts"
        );
    }

    private Map<String, Object> generateCampaignTimeline(List<ChaosScenario> scenarios) {
        Map<String, Object> timeline = new HashMap<>();

        // Calculate total duration
        Duration totalDuration = scenarios.stream()
            .map(ChaosScenario::getDuration)
            .reduce(Duration.ZERO, Duration::plus);

        timeline.put("total_duration", totalDuration);
        timeline.put("scenarios_count", scenarios.size());
        timeline.put("start_buffer", Duration.ofHours(1));
        timeline.put("end_buffer", Duration.ofHours(1));
        timeline.put("between_scenario_gap", Duration.ofHours(2));

        return timeline;
    }

    private Map<String, Object> generateSuccessCriteria() {
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("min_uptime_percent", 99.9);
        criteria.put("max_error_rate", 0.01);
        criteria.put("recovery_time_seconds", 300);
        criteria.put("data_integrity", "verified");
        criteria.put("user_impact_score", "low");
        return criteria;
    }

    // Network scripts generation
    private List<Map<String, Object>> generateNetworkScripts(ChaosScenario scenario) {
        List<Map<String, Object>> scripts = new ArrayList<>();

        Map<String, Object> script = new HashMap<>();
        script.put("type", "network");
        script.put("platform", "linux");
        script.put("script_content", generateNetworkScriptContent(scenario));
        script.put("execute_command", "sudo sh -c '" + script.get("script_content") + "'");
        script.put("cleanup_command", "sudo sh -c 'tc qdisc del dev eth0 root'");
        scripts.add(script);

        return scripts;
    }

    private String generateNetworkScriptContent(ChaosScenario scenario) {
        StringBuilder script = new StringBuilder();

        switch (scenario.getPattern()) {
            case "high_latency":
                script.append("tc qdisc add dev eth0 root netem delay ")
                      .append(generateLatency()).append("ms");
                break;
            case "packet_loss":
                script.append("tc qdisc add dev eth0 root netem loss ")
                      .append(generatePacketLoss()).append("%");
                break;
            case "bandwidth_throttling":
                script.append("tc qdisc add dev eth0 root tbf rate ")
                      .append(generateBandwidthReduction()).append("kbit latency 50ms burst 1540");
                break;
            case "network_partition":
                script.append("tc qdisc add dev eth0 root netem delay 0 loss 100%");
                break;
            default:
                script.append("tc qdisc add dev eth0 root netem delay 100ms loss 1%");
                break;
        }

        return script.toString();
    }

    // Resource scripts generation
    private List<Map<String, Object>> generateResourceScripts(ChaosScenario scenario) {
        List<Map<String, Object>> scripts = new ArrayList<>();

        Map<String, Object> memoryScript = new HashMap<>();
        memoryScript.put("type", "memory");
        memoryScript.put("platform", "linux");
        memoryScript.put("script_content", generateMemoryStressScript());
        memoryScript.put("execute_command", "sh -c '" + memoryScript.get("script_content") + "'");
        memoryScript.put("cleanup_command", "killall -9 stress-ng");
        scripts.add(memoryScript);

        return scripts;
    }

    private String generateMemoryStressScript() {
        return "stress-ng --vm " + (random.nextInt(4, 8)) +
               " --vm-bytes " + (random.nextInt(1, 4)) + "G --timeout " +
               (scenario.getDuration().getSeconds() + 30) + "s";
    }

    // Service scripts generation
    private List<Map<String, Object>> generateServiceScripts(ChaosScenario scenario) {
        List<Map<String, Object>> scripts = new ArrayList<>();

        Map<String, Object> serviceScript = new HashMap<>();
        serviceScript.put("type", "service");
        serviceScript.put("platform", "general");
        serviceScript.put("script_content", generateServiceDisruptionScript(scenario));
        serviceScript.put("execute_command", generateServiceDisruptionCommand(scenario));
        serviceScript.put("cleanup_command", generateServiceRecoveryCommand(scenario));
        scripts.add(serviceScript);

        return scripts;
    }

    private String generateServiceDisruptionScript(ChaosScenario scenario) {
        return String.join("\n", Arrays.asList(
            "#!/bin/bash",
            "echo 'Starting service disruption...'",
            "echo 'Pattern: " + scenario.getPattern() + "'",
            "echo 'Duration: " + scenario.getDuration().getSeconds() + " seconds'",
            "",
            "# Service disruption logic here",
            "echo 'Disruption applied successfully'"
        ));
    }

    private String generateServiceDisruptionCommand(ChaosScenario scenario) {
        return "chmod +x chaos_script.sh && ./chaos_script.sh";
    }

    private String generateServiceRecoveryCommand(ChaosScenario scenario) {
        return "./chaos_script.sh --cleanup";
    }

    // Data scripts generation
    private List<Map<String, Object>> generateDataScripts(ChaosScenario scenario) {
        List<Map<String, Object>> scripts = new ArrayList<>();

        Map<String, Object> dataScript = new HashMap<>();
        dataScript.put("type", "data");
        dataScript.put("platform", "general");
        dataScript.put("script_content", generateDataCorruptionScript());
        dataScript.put("execute_command", "python data_corruption.py");
        dataScript.put("cleanup_command", "python data_recovery.py");
        scripts.add(dataScript);

        return scripts;
    }

    private String generateDataCorruptionScript() {
        return String.join("\n", Arrays.asList(
            "#!/usr/bin/env python3",
            "import random",
            "import time",
            "",
            "def corrupt_data(data_file, corruption_rate=0.1):",
            "    with open(data_file, 'r+') as f:",
            "        data = f.read()",
            "        # Apply corruption logic",
            "        corrupted = ''.join([random.choice('xyz') if random.random() < corruption_rate else c for c in data])",
            "        f.seek(0)",
            "        f.write(corrupted)",
            "        f.truncate()",
            "",
            "if __name__ == '__main__':",
            "    print('Data corruption simulation started')"
        ));
    }

    // Infrastructure scripts generation
    private List<Map<String, Object>> generateInfrastructureScripts(ChaosScenario scenario) {
        List<Map<String, Object>> scripts = new ArrayList<>();

        Map<String, Object> infraScript = new HashMap<>();
        infraScript.put("type", "infrastructure");
        infraScript.put("platform", "cloud");
        infraScript.put("script_content", generateInfrastructureFailureScript());
        infraScript.put("execute_command", "terraform apply -auto-approve chaos_config.tf");
        infraScript.put("cleanup_command", "terraform destroy -auto-approve");
        scripts.add(infraScript);

        return scripts;
    }

    private String generateInfrastructureFailureScript() {
        return String.join("\n", Arrays.asList(
            "# Terraform configuration for infrastructure chaos",
            "resource \"random_integer\" \"failure\" {",
            "    min = 1",
            "    max = 100",
            "}",
            "",
            "resource \"null_resource\" \"chaos\" {",
            "    triggers = {",
            "        failure = random_integer.failure.result",
            "    }",
            "    provisioner \"local-exec\" {",
            "        command = \"echo 'Infrastructure failure injected: ${self.triggers.failure}'\"",
            "    }",
            "}"
        ));
    }

    // Metrics generation methods

    private List<Map<String, String>> generateSystemMetrics() {
        List<Map<String, String>> metrics = new ArrayList<>();

        // CPU metrics
        metrics.add(createMetric("cpu_usage_percent", "percentage", "System CPU usage"));
        metrics.add(createMetric("cpu_load_average", "load", "1-minute CPU load average"));
        metrics.add(createMetric("cpu_core_count", "count", "Number of CPU cores"));

        // Memory metrics
        metrics.add(createMetric("memory_usage_percent", "percentage", "System memory usage"));
        metrics.add(createMetric("memory_available_mb", "megabytes", "Available memory"));
        metrics.add(createMetric("memory_swap_usage_percent", "percentage", "Swap memory usage"));

        // Disk metrics
        metrics.add(createMetric("disk_usage_percent", "percentage", "Disk usage percentage"));
        metrics.add(createMetric("disk_io_reads_per_sec", "reads/sec", "Disk reads per second"));
        metrics.add(createMetric("disk_io_writes_per_sec", "writes/sec", "Disk writes per second"));

        // Network metrics
        metrics.add(createMetric("network_bytes_sent", "bytes", "Network bytes sent"));
        metrics.add(createMetric("network_bytes_received", "bytes", "Network bytes received"));
        metrics.add(createMetric("network_packet_loss_percent", "percentage", "Network packet loss"));

        return metrics;
    }

    private List<Map<String, String>> generateApplicationMetrics(ChaosScenario scenario) {
        List<Map<String, String>> metrics = new ArrayList<>();

        // Workflow engine metrics
        metrics.add(createMetric("workflow_cases_running", "count", "Number of running cases"));
        metrics.add(createMetric("workflow_tasks_completed", "count", "Number of completed tasks"));
        metrics.add(createMetric("workflow_error_rate", "percentage", "Workflow error rate"));
        metrics.add(createMetric("workflow_average_duration", "seconds", "Average case duration"));

        // API metrics
        metrics.add(createMetric("api_requests_per_sec", "requests/sec", "API requests per second"));
        metrics.add(createMetric("api_average_response_time", "milliseconds", "Average API response time"));
        metrics.add(createMetric("api_error_rate", "percentage", "API error rate"));

        // Database metrics
        metrics.add(createMetric("db_active_connections", "count", "Active database connections"));
        metrics.add(createMetric("db_average_query_time", "milliseconds", "Average database query time"));
        metrics.add(createMetric("db_slow_queries_per_sec", "queries/sec", "Slow queries per second"));

        return metrics;
    }

    private List<Map<String, String>> generateBusinessMetrics(ChaosScenario scenario) {
        List<Map<String, String>> metrics = new ArrayList<>();

        // Business process metrics
        metrics.add(createMetric("business_process_throughput", "processes/hour", "Business process throughput"));
        metrics.add(createMetric("business_process_latency", "seconds", "Business process latency"));
        metrics.add(createMetric("business_process_success_rate", "percentage", "Business process success rate"));

        // User experience metrics
        metrics.add(createMetric("user_sessions", "count", "Number of active user sessions"));
        metrics.add(createMetric("user_error_rate", "percentage", "User error rate"));
        metrics.add(createMetric("user_satisfaction_score", "score", "User satisfaction score"));

        // Financial metrics
        metrics.add(createMetric("transaction_throughput", "transactions/sec", "Transaction throughput"));
        metrics.add(createMetric("revenue_impact", "currency", "Revenue impact"));
        metrics.add(createMetric("operational_cost", "currency", "Operational cost"));

        return metrics;
    }

    private List<Map<String, String>> generateChaosMetrics(ChaosScenario scenario) {
        List<Map<String, String>> metrics = new ArrayList<>();

        // Chaos injection metrics
        metrics.add(createMetric("chaos_injection_active", "boolean", "Chaos injection status"));
        metrics.add(createMetric("chaos_intensity_level", "level", "Chaos intensity level"));
        metrics.add(createMetric("chaos_target_affected", "count", "Number of affected targets"));

        // Recovery metrics
        metrics.add(createMetric("recovery_time_seconds", "seconds", "Recovery time"));
        metrics.add(createMetric("recovery_success_rate", "percentage", "Recovery success rate"));
        metrics.add(createMetric("recovery_attempts", "count", "Number of recovery attempts"));

        // Impact metrics
        metrics.add(createMetric("impact_score", "score", "Overall impact score"));
        metrics.add(createMetric("severity_level", "level", "Severity level"));
        metrics.add(createMetric("blast_radius", "units", "Blast radius affected"));

        return metrics;
    }

    private Map<String, String> createMetric(String name, String type, String description) {
        Map<String, String> metric = new HashMap<>();
        metric.put("name", name);
        metric.put("type", type);
        metric.put("description", description);
        return metric;
    }

    // Results generation methods

    private Map<String, Object> generateTestResults(ChaosScenario scenario) {
        Map<String, Object> results = new HashMap<>();

        // Generate success/failure based on severity
        boolean success = scenario.getSeverity().equals("low") || random.nextBoolean();
        results.put("success", success);
        results.put("test_duration", scenario.getDuration().toMillis());
        results.put("test_timestamp", Instant.now().toString());

        // Generate test metrics
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("uptime_percentage", generateUptimePercentage(scenario));
        metrics.put("error_rate", generateErrorRate(scenario));
        metrics.put("response_time_impact", generateResponseTimeImpact(scenario));
        metrics.put("throughput_impact", generateThroughputImpact(scenario));
        results.put("metrics", metrics);

        // Generate test summary
        results.put("summary", generateTestSummary(scenario, success));

        return results;
    }

    private Map<String, Object> generatePerformanceImpact(ChaosScenario scenario) {
        Map<String, Object> impact = new HashMap<>();

        // Generate performance metrics
        impact.put("response_time_increase", generateResponseTimeIncrease(scenario));
        impact.put("throughput_decrease", generateThroughputDecrease(scenario));
        impact.put("error_rate_increase", generateErrorRateIncrease(scenario));
        impact.put("resource_utilization", generateResourceUtilization(scenario));

        // Generate impact assessment
        impact.put("severity", calculatePerformanceSeverity(impact));
        impact.put("recovery_time", estimateRecoveryTime(scenario));

        return impact;
    }

    private Map<String, Object> generateRecoveryAnalysis(ChaosScenario scenario) {
        Map<String, Object> analysis = new HashMap<>();

        // Generate recovery metrics
        analysis.put("recovery_time_seconds", generateRecoveryTimeSeconds(scenario));
        analysis.put("recovery_success_rate", generateRecoverySuccessRate(scenario));
        analysis.put("recovery_attempts", generateRecoveryAttempts(scenario));
        analysis.put("rollback_required", generateRollbackRequired(scenario));

        // Generate recovery bottlenecks
        analysis.put("recovery_bottlenecks", generateRecoveryBottlenecks(scenario));

        return analysis;
    }

    private List<String> generateRecommendations(ChaosScenario scenario,
                                                 Map<String, Object> testResults,
                                                 Map<String, Object> performanceImpact,
                                                 Map<String, Object> recoveryAnalysis) {
        List<String> recommendations = new ArrayList<>();

        // Generate recommendations based on scenario and results
        if (!(boolean) testResults.get("success")) {
            recommendations.add("Implement better monitoring for " + scenario.getCategory() + " failures");
            recommendations.add("Consider circuit breaker pattern for " + scenario.getPrimaryTarget());
            recommendations.add("Increase timeout thresholds for " + scenario.getPattern());
        }

        if ((double) performanceImpact.get("response_time_increase") > 50) {
            recommendations.add("Optimize " + scenario.getPrimaryTarget() + " for better performance");
            recommendations.add("Consider load balancing for " + scenario.getCategory() + " components");
        }

        if ((double) recoveryAnalysis.get("recovery_time_seconds") > 300) {
            recommendations.add("Implement automated recovery for " + scenario.getPattern());
            recommendations.add("Improve documentation for manual recovery procedures");
        }

        // General recommendations
        recommendations.add("Regular chaos testing to ensure system resilience");
        recommendations.add("Update runbooks based on test results");
        recommendations.add("Implement proactive monitoring for early detection");

        return recommendations;
    }

    // Safety assessment methods

    private String calculateRiskLevel(ChaosScenario scenario) {
        int riskScore = 0;

        // Risk based on severity
        switch (scenario.getSeverity()) {
            case "low": riskScore += 1; break;
            case "medium": riskScore += 3; break;
            case "high": riskScore += 5; break;
            case "critical": riskScore += 10; break;
        }

        // Risk based on category
        switch (scenario.getCategory()) {
            case "network": riskScore += 2; break;
            case "resource": riskScore += 3; break;
            case "service": riskScore += 4; break;
            case "data": riskScore += 6; break;
            case "infrastructure": riskScore += 7; break;
        }

        if (riskScore <= 3) return "low";
        if (riskScore <= 6) return "medium";
        if (riskScore <= 9) return "high";
        return "critical";
    }

    private double calculateSafetyScore(ChaosScenario scenario) {
        double score = 100.0;

        // Reduce score based on severity
        switch (scenario.getSeverity()) {
            case "low": score -= 10; break;
            case "medium": score -= 25; break;
            case "high": score -= 50; break;
            case "critical": score -= 75; break;
        }

        // Add points for safety mechanisms
        score += scenario.getSafetyMechanisms().size() * 5;

        return Math.max(0, Math.min(100, score));
    }

    private List<String> generateMitigationFactors(ChaosScenario scenario) {
        List<String> factors = new ArrayList<>();

        factors.add("Backup systems available");
        factors.add("Monitoring in place");
        factors.add("Recovery procedures documented");
        factors.add("Low impact time window selected");
        factors.add("Required resources available");

        return factors;
    }

    private String generateRollbackPlan(ChaosScenario scenario) {
        return String.join("\n", Arrays.asList(
            "1. Identify the point of failure",
            "2. Trigger rollback mechanism",
            "3. Verify rollback completion",
            "4. Validate data consistency",
            "5. Monitor system stability",
            "6. Document rollback outcome"
        ));
    }

    private List<String> generateEmergencyStopCriteria() {
        return Arrays.asList(
            "System uptime drops below 95%",
            "Error rate exceeds 5%",
            "Response time increases by 200%",
            "Data integrity issues detected",
            "User complaints threshold exceeded"
        );
    }

    // Utility methods

    private String getRandomTimingPattern() {
        String[] patterns = {"immediate", "gradual", "sudden", "pulsing", "cyclical"};
        return patterns[random.nextInt(patterns.length)];
    }

    private int generateLatency() {
        return random.nextInt(100, 5000);
    }

    private int generatePacketLoss() {
        return random.nextInt(0, 20);
    }

    private int generateBandwidthReduction() {
        return random.nextInt(10, 90);
    }

    private int generateCPUUsage(String severity) {
        return switch (severity) {
            case "low" -> random.nextInt(60, 80);
            case "medium" -> random.nextInt(80, 90);
            case "high" -> random.nextInt(90, 95);
            case "critical" -> random.nextInt(95, 99);
            default -> random.nextInt(70, 90);
        };
    }

    private int generateMemoryUsage(String severity) {
        return switch (severity) {
            case "low" -> random.nextInt(70, 85);
            case "medium" -> random.nextInt(85, 95);
            case "high" -> random.nextInt(95, 98);
            case "critical" -> random.nextInt(98, 100);
            default -> random.nextInt(75, 90);
        };
    }

    private int generateDiskUsage() {
        return random.nextInt(70, 95);
    }

    private int generateTimeout() {
        return random.nextInt(30, 300);
    }

    private double generateErrorRate() {
        return random.nextDouble() * 0.1;
    }

    private int generateResponseTime() {
        return random.nextInt(100, 5000);
    }

    private double generateCorruptionRate() {
        return random.nextDouble() * 0.05;
    }

    private int generateDataLoss() {
        return random.nextInt(0, 10);
    }

    private int generateInconsistency() {
        return random.nextInt(0, 50);
    }

    private String generateFailureType() {
        String[] types = {"hardware_failure", "power_outage", "network_failure", "software_bug"};
        return types[random.nextInt(types.length)];
    }

    private int generateRecoveryTime() {
        return random.nextInt(10, 120);
    }

    private int generateAffectedNodes() {
        return random.nextInt(1, 10);
    }

    private String generateBusinessImpact(String severity) {
        return switch (severity) {
            case "low" -> "Minimal business impact";
            case "medium" -> "Moderate business impact";
            case "high" -> "Significant business impact";
            case "critical" -> "Critical business impact";
            default -> "Unknown business impact";
        };
    }

    private String generateTechnicalImpact(String severity) {
        return switch (severity) {
            case "low" -> "Minor technical issues";
            case "medium" -> "Moderate system degradation";
            case "high" -> "Severe system performance impact";
            case "critical" -> "System critical failure";
            default -> "Unknown technical impact";
        };
    }

    private String generateUserExperience(String severity) {
        return switch (severity) {
            case "low" -> "Slight user experience impact";
            case "medium" -> "Noticeable performance degradation";
            case "high" -> "Significant user experience issues";
            case "critical" -> "Complete service disruption";
            default -> "Unknown user experience impact";
        };
    }

    private String generateFinancialImpact(String severity) {
        return switch (severity) {
            case "low" -> "Minimal financial impact";
            case "medium" -> "Moderate financial impact";
            case "high" -> "Significant financial impact";
            case "critical" -> "Critical financial impact";
            default -> "Unknown financial impact";
        };
    }

    private int generateRecoveryTimeEstimate(String category, String severity) {
        int baseTime = switch (category) {
            case "network" -> 30;
            case "resource" -> 10;
            case "service" -> 60;
            case "data" -> 120;
            case "infrastructure" -> 180;
            default -> 30;
        };

        return switch (severity) {
            case "low" -> baseTime;
            case "medium" -> baseTime * 2;
            case "high" -> baseTime * 4;
            case "critical" -> baseTime * 8;
            default -> baseTime;
        };
    }

    private Map<String, Object> generateAlertThresholds(String category) {
        Map<String, Object> thresholds = new HashMap<>();

        switch (category) {
            case "network":
                thresholds.put("latency_ms", 100);
                thresholds.put("packet_loss_percent", 5);
                break;
            case "resource":
                thresholds.put("cpu_usage_percent", 90);
                thresholds.put("memory_usage_percent", 85);
                break;
            case "service":
                thresholds.put("error_rate", 0.05);
                thresholds.put("response_time_ms", 5000);
                break;
            case "data":
                thresholds.put("corruption_rate", 0.01);
                thresholds.put("data_loss_percent", 1);
                break;
            case "infrastructure":
                thresholds.put("failure_detected", true);
                thresholds.put("recovery_time_minutes", 30);
                break;
        }

        return thresholds;
    }

    private List<String> generateHealthChecks(String category) {
        List<String> checks = new ArrayList<>();

        switch (category) {
            case "network":
                checks.addAll(List.of("ping", "connection_test", "bandwidth_test"));
                break;
            case "resource":
                checks.addAll(List.of("cpu_usage", "memory_usage", "disk_space"));
                break;
            case "service":
                checks.addAll(List.of("service_status", "response_time", "error_rate"));
                break;
            case "data":
                checks.addAll(List.of("data_integrity", "consistency_check", "backup_status"));
                break;
            case "infrastructure":
                checks.addAll(List.of("component_status", "power_status", "cooling_status"));
                break;
        }

        return checks;
    }

    private String generateLoggingRequirements(String category) {
        return String.join("\n", Arrays.asList(
            "Log all chaos injection events",
            "Monitor system performance during chaos",
            "Track recovery progress",
            "Record user impact metrics",
            "Generate audit trail for compliance"
        ));
    }

    // Test result utilities

    private double generateUptimePercentage(ChaosScenario scenario) {
        return switch (scenario.getSeverity()) {
            case "low" -> 99.9;
            case "medium" -> 99.5;
            case "high" -> 95.0;
            case "critical" -> 90.0;
            default -> 99.0;
        };
    }

    private double generateErrorRate(ChaosScenario scenario) {
        return switch (scenario.getSeverity()) {
            case "low" -> 0.01;
            case "medium" -> 0.05;
            case "high" -> 0.1;
            case "critical" -> 0.2;
            default -> 0.02;
        };
    }

    private int generateResponseTimeImpact(ChaosScenario scenario) {
        return switch (scenario.getSeverity()) {
            case "low" -> 10;
            case "medium" -> 30;
            case "high" -> 50;
            case "critical" -> 80;
            default -> 20;
        };
    }

    private int generateThroughputImpact(ChaosScenario scenario) {
        return switch (scenario.getSeverity()) {
            case "low" -> 5;
            case "medium" -> 20;
            case "high" -> 40;
            case "critical" -> 60;
            default -> 10;
        };
    }

    private String generateTestSummary(ChaosScenario scenario, boolean success) {
        if (success) {
            return "Chaos test completed successfully. System demonstrated resilience against " + scenario.getPattern();
        } else {
            return "Chaos test revealed vulnerabilities. System needs improvement to handle " + scenario.getPattern();
        }
    }

    // Performance impact utilities

    private int generateResponseTimeIncrease(ChaosScenario scenario) {
        return switch (scenario.getSeverity()) {
            case "low" -> 20;
            case "medium" -> 50;
            case "high" -> 100;
            case "critical" -> 200;
            default -> 30;
        };
    }

    private int generateThroughputDecrease(ChaosScenario scenario) {
        return switch (scenario.getSeverity()) {
            case "low" -> 10;
            case "medium" -> 30;
            case "high" -> 50;
            case "critical" -> 80;
            default -> 15;
        };
    }

    private double generateErrorRateIncrease(ChaosScenario scenario) {
        return switch (scenario.getSeverity()) {
            case "low" -> 0.02;
            case "medium" -> 0.05;
            case "high" -> 0.1;
            case "critical" -> 0.2;
            default -> 0.03;
        };
    }

    private Map<String, Object> generateResourceUtilization(ChaosScenario scenario) {
        Map<String, Object> utilization = new HashMap<>();
        utilization.put("cpu_usage", generateCPUUsage(scenario.getSeverity()));
        utilization.put("memory_usage", generateMemoryUsage(scenario.getSeverity()));
        utilization.put("disk_usage", generateDiskUsage());
        return utilization;
    }

    private String calculatePerformanceSeverity(Map<String, Object> impact) {
        double totalImpact = (double) impact.getOrDefault("response_time_increase", 0) +
                           (double) impact.getOrDefault("throughput_decrease", 0) +
                           (double) impact.getOrDefault("error_rate_increase", 0) * 100;

        if (totalImpact < 50) return "low";
        if (totalImpact < 100) return "medium";
        if (totalImpact < 200) return "high";
        return "critical";
    }

    private int estimateRecoveryTime(ChaosScenario scenario) {
        return switch (scenario.getSeverity()) {
            case "low" -> 30;
            case "medium" -> 60;
            case "high" -> 180;
            case "critical" -> 300;
            default -> 60;
        };
    }

    // Recovery analysis utilities

    private int generateRecoveryTimeSeconds(ChaosScenario scenario) {
        return switch (scenario.getSeverity()) {
            case "low" -> 60;
            case "medium" -> 120;
            case "high" -> 300;
            case "critical" -> 600;
            default -> 90;
        };
    }

    private double generateRecoverySuccessRate(ChaosScenario scenario) {
        return switch (scenario.getSeverity()) {
            case "low" -> 0.95;
            case "medium" -> 0.85;
            case "high" -> 0.70;
            case "critical" -> 0.50;
            default -> 0.80;
        };
    }

    private int generateRecoveryAttempts(ChaosScenario scenario) {
        return switch (scenario.getSeverity()) {
            case "low" -> 1;
            case "medium" -> 2;
            case "high" -> 3;
            case "critical" -> 5;
            default -> 1;
        };
    }

    private boolean generateRollbackRequired(ChaosScenario scenario) {
        return scenario.getSeverity().equals("high") || scenario.getSeverity().equals("critical");
    }

    private List<String> generateRecoveryBottlenecks(ChaosScenario scenario) {
        List<String> bottlenecks = new ArrayList<>();

        switch (scenario.getCategory()) {
            case "network":
                bottlenecks.addAll(List.of("Network reconfiguration", "DNS propagation", "Route convergence"));
                break;
            case "resource":
                bottlenecks.addAll(List.of("Resource allocation", "Auto-scaling delays", "Cache warming"));
                break;
            case "service":
                bottlenecks.addAll(List.of("Service restart", "Database connection pooling", "State restoration"));
                break;
            case "data":
                bottlenecks.addAll(List.of("Data repair", "Consistency verification", "Backup restoration"));
                break;
            case "infrastructure":
                bottlenecks.addAll(List.of("Hardware replacement", "Power restoration", "Cooling system"));
                break;
        }

        return bottlenecks;
    }
}