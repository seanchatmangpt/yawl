/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.containers;

import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Test data factory and builder for YAWL workflow integration tests.
 *
 * Provides deterministic construction of:
 * - YSpecification objects (minimal and complex)
 * - Database seed rows for persistence-layer tests
 * - Named specification variants for parametrized tests
 *
 * All factory methods return fully-wired YAWL domain objects ready for
 * assertion without requiring a running YAWL engine instance. This matches
 * Chicago TDD: real objects, real wiring, deterministic state.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
public final class WorkflowDataFactory {

    private WorkflowDataFactory() {
        throw new UnsupportedOperationException(
                "WorkflowDataFactory is a static utility class");
    }

    // =========================================================================
    // Specification Builders
    // =========================================================================

    /**
     * Builds a minimal linear workflow:
     * InputCondition -> AtomicTask -> OutputCondition
     *
     * @param specId unique specification identifier
     * @return fully-wired YSpecification
     * @throws Exception if specification construction fails
     */
    public static YSpecification buildMinimalSpec(String specId) throws Exception {
        YSpecification spec = new YSpecification(specId);
        spec.setName("Minimal Workflow [" + specId + "]");
        spec.setVersion(YSchemaVersion.Beta7);

        YNet net = new YNet("root", spec);
        spec.setRootNet(net);

        YInputCondition  input  = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        YAtomicTask task = new YAtomicTask(
                "process", YAtomicTask._AND, YAtomicTask._AND, net);
        net.addNetElement(task);

        YFlow flowIn  = new YFlow(input, task);
        YFlow flowOut = new YFlow(task, output);
        input.addPostset(flowIn);
        task.addPreset(flowIn);
        task.addPostset(flowOut);
        output.addPreset(flowOut);

        return spec;
    }

    /**
     * Builds a multi-task sequential workflow with N tasks.
     * InputCondition -> task_0 -> task_1 -> ... -> task_{n-1} -> OutputCondition
     *
     * @param specId    unique specification identifier
     * @param taskCount number of sequential tasks (minimum 1)
     * @return fully-wired YSpecification with taskCount tasks
     * @throws Exception if specification construction fails
     * @throws IllegalArgumentException if taskCount < 1
     */
    public static YSpecification buildSequentialSpec(String specId, int taskCount)
            throws Exception {
        if (taskCount < 1) {
            throw new IllegalArgumentException(
                    "taskCount must be at least 1, got: " + taskCount);
        }

        YSpecification spec = new YSpecification(specId);
        spec.setName("Sequential Workflow [" + specId + "] tasks=" + taskCount);
        spec.setVersion(YSchemaVersion.Beta7);

        YNet net = new YNet("root", spec);
        spec.setRootNet(net);

        YInputCondition  input  = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);

        // Build task chain
        YAtomicTask[] tasks = new YAtomicTask[taskCount];
        for (int i = 0; i < taskCount; i++) {
            tasks[i] = new YAtomicTask(
                    "task_" + i, YAtomicTask._AND, YAtomicTask._AND, net);
            net.addNetElement(tasks[i]);
        }

        // Wire input -> first task
        YFlow firstFlow = new YFlow(input, tasks[0]);
        input.addPostset(firstFlow);
        tasks[0].addPreset(firstFlow);

        // Wire task[i] -> task[i+1]
        for (int i = 0; i < taskCount - 1; i++) {
            YFlow f = new YFlow(tasks[i], tasks[i + 1]);
            tasks[i].addPostset(f);
            tasks[i + 1].addPreset(f);
        }

        // Wire last task -> output
        YFlow lastFlow = new YFlow(tasks[taskCount - 1], output);
        tasks[taskCount - 1].addPostset(lastFlow);
        output.addPreset(lastFlow);

        return spec;
    }

    /**
     * Generates a deterministic specification ID from a test name.
     * Suitable for use as a database primary key.
     *
     * @param testName human-readable test name
     * @return deterministic spec-id string, safe for database use
     */
    public static String specIdFor(String testName) {
        return "spec-" + testName.toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }

    /**
     * Generates a unique specification ID using a UUID suffix.
     * Use when test isolation requires guaranteed uniqueness.
     *
     * @param prefix human-readable prefix for the generated ID
     * @return unique spec-id string
     */
    public static String uniqueSpecId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // =========================================================================
    // Database Seed Helpers
    // =========================================================================

    /**
     * Seeds one specification row into the yawl_specification table.
     *
     * @param connection open JDBC connection with YAWL schema applied
     * @param specId     specification identifier (primary key)
     * @param version    version string (e.g. "1.0")
     * @param name       human-readable spec name
     * @throws SQLException if the insert fails
     */
    public static void seedSpecification(Connection connection,
                                         String specId,
                                         String version,
                                         String name) throws SQLException {
        String sql = """
            INSERT INTO yawl_specification (spec_id, spec_version, spec_name)
            VALUES (?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, specId);
            ps.setString(2, version);
            ps.setString(3, name);
            ps.executeUpdate();
        }
    }

    /**
     * Seeds one net-runner row into the yawl_net_runner table.
     * The specification row (specId, version) must exist as a foreign key.
     *
     * @param connection open JDBC connection with YAWL schema applied
     * @param runnerId   runner identifier (primary key)
     * @param specId     owning specification identifier
     * @param version    owning specification version
     * @param netId      net element identifier within the specification
     * @param state      runner state (e.g. "RUNNING", "COMPLETED")
     * @throws SQLException if the insert fails
     */
    public static void seedNetRunner(Connection connection,
                                     String runnerId,
                                     String specId,
                                     String version,
                                     String netId,
                                     String state) throws SQLException {
        String sql = """
            INSERT INTO yawl_net_runner (runner_id, spec_id, spec_version, net_id, state)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, runnerId);
            ps.setString(2, specId);
            ps.setString(3, version);
            ps.setString(4, netId);
            ps.setString(5, state);
            ps.executeUpdate();
        }
    }

    /**
     * Seeds one work-item row into the yawl_work_item table.
     * The runner row (runnerId) must exist as a foreign key.
     *
     * @param connection open JDBC connection with YAWL schema applied
     * @param itemId     work-item identifier (primary key)
     * @param runnerId   owning runner identifier
     * @param taskId     task element identifier
     * @param status     work-item status (e.g. "Enabled", "Executing", "Completed")
     * @throws SQLException if the insert fails
     */
    public static void seedWorkItem(Connection connection,
                                    String itemId,
                                    String runnerId,
                                    String taskId,
                                    String status) throws SQLException {
        String sql = """
            INSERT INTO yawl_work_item (item_id, runner_id, task_id, status)
            VALUES (?, ?, ?, ?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, runnerId);
            ps.setString(3, taskId);
            ps.setString(4, status);
            ps.executeUpdate();
        }
    }
}
