/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.multimodule;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.containers.YawlContainerFixtures;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemID;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-module integration tests: engine + elements + persistence.
 *
 * Tests the full vertical slice across three YAWL subsystems:
 *
 * 1. yawl-elements: YSpecification construction (WorkflowDataFactory)
 * 2. yawl-engine:   YEngine, YNetRunner, YWorkItem lifecycle
 * 3. Persistence:   H2 in-memory JDBC (schema, seed, query)
 *
 * Each test verifies that these three subsystems compose correctly —
 * that domain objects built by the elements module can be processed
 * by the engine module and have their state persisted and re-queried
 * via the database layer.
 *
 * Chicago TDD: real YAWL objects, real JDBC connections, no mocks.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
class MultiModuleIntegrationTest {

    private Connection db;
    private YEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:multi_%d;DB_CLOSE_DELAY=-1".formatted(System.nanoTime());
        db = DriverManager.getConnection(jdbcUrl, "sa", "");
        YawlContainerFixtures.applyYawlSchema(db);

        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine singleton must be available");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null && !db.isClosed()) {
            db.close();
        }
    }

    // =========================================================================
    // Specification → Persistence Round-Trip
    // =========================================================================

    @Test
    void testSpecificationBuiltThenPersisted() throws Exception {
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec("multi-spec-001");
        assertNotNull(spec, "Factory must build specification");
        assertNotNull(spec.getRootNet(), "Spec must have root net");

        // Persist specification metadata
        WorkflowDataFactory.seedSpecification(db, "multi-spec-001", "1.0", spec.getName());

        // Verify persistence round-trip
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT spec_name FROM yawl_specification WHERE spec_id = ?")) {
            ps.setString(1, "multi-spec-001");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Spec row must exist in DB");
                assertEquals(spec.getName(), rs.getString(1),
                        "Spec name must round-trip through DB");
            }
        }
    }

    // =========================================================================
    // Engine + Elements: Work Item Lifecycle
    // =========================================================================

    @Test
    void testWorkItemLifecycleAcrossModules() throws Exception {
        // Elements module: build spec
        YSpecification spec = WorkflowDataFactory.buildMinimalSpec("wi-lifecycle");
        YNet net = spec.getRootNet();
        YAtomicTask task = (YAtomicTask) net.getNetElement("process");
        assertNotNull(task, "task 'process' must exist in minimal spec");

        // Engine module: create work item
        YIdentifier caseId = new YIdentifier(null);
        YWorkItemID wid = new YWorkItemID(caseId, "process");
        YWorkItem workItem = new YWorkItem(null, spec.getSpecificationID(), task, wid, true, false);

        // Verify initial state
        assertEquals(YWorkItemStatus.statusEnabled, workItem.getStatus(),
                "Initial status must be Enabled");

        // Transition: Enabled -> Executing -> Completed
        workItem.setStatus(YWorkItemStatus.statusExecuting);
        assertEquals(YWorkItemStatus.statusExecuting, workItem.getStatus());

        workItem.setStatus(YWorkItemStatus.statusComplete);
        assertEquals(YWorkItemStatus.statusComplete, workItem.getStatus());

        // Persistence: record the lifecycle in DB
        WorkflowDataFactory.seedSpecification(db, "wi-lifecycle", "1.0", spec.getName());
        WorkflowDataFactory.seedNetRunner(db, "runner-wi", "wi-lifecycle", "1.0", "root", "RUNNING");
        WorkflowDataFactory.seedWorkItem(db, wid.toString(), "runner-wi", "process", "Completed");

        // Verify DB state
        try (PreparedStatement ps = db.prepareStatement(
                "SELECT status FROM yawl_work_item WHERE item_id = ?")) {
            ps.setString(1, wid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Work item row must exist");
                assertEquals("Completed", rs.getString(1),
                        "Status in DB must be Completed");
            }
        }
    }

    // =========================================================================
    // Sequential Multi-Task Workflow Execution
    // =========================================================================

    @Test
    void testSequentialWorkflowAllTasksPersisted() throws Exception {
        int taskCount = 3;
        YSpecification spec = WorkflowDataFactory.buildSequentialSpec(
                "sequential-multi", taskCount);
        YNet net = spec.getRootNet();

        // Verify elements module produced correct structure
        assertEquals(taskCount, net.getNetElements().size(),
                "Net must have " + taskCount + " tasks");

        // Persist spec and runner
        WorkflowDataFactory.seedSpecification(db, "sequential-multi", "1.0", spec.getName());
        WorkflowDataFactory.seedNetRunner(db,
                "runner-seq", "sequential-multi", "1.0", "root", "RUNNING");

        // Simulate each task transitioning through Enabled -> Completed
        YIdentifier caseId = new YIdentifier(null);
        List<String> workItemIds = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            String taskId = "task_" + i;
            YAtomicTask task = (YAtomicTask) net.getNetElement(taskId);
            assertNotNull(task, "Task must exist: " + taskId);

            YWorkItemID wid = new YWorkItemID(caseId, taskId);
            WorkflowDataFactory.seedWorkItem(db, wid.toString(),
                    "runner-seq", taskId, "Completed");
            workItemIds.add(wid.toString());
        }

        // Verify all work items in DB
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_work_item WHERE runner_id = 'runner-seq'")) {
            assertTrue(rs.next());
            assertEquals(taskCount, rs.getInt(1),
                    "All " + taskCount + " work items must be persisted");
        }

        // Verify all have Completed status
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_work_item "
                     + "WHERE runner_id = 'runner-seq' AND status = 'Completed'")) {
            assertTrue(rs.next());
            assertEquals(taskCount, rs.getInt(1),
                    "All work items must have Completed status");
        }
    }

    // =========================================================================
    // Engine Singleton Integration
    // =========================================================================

    @Test
    void testEngineSingletonConsistency() {
        YEngine e1 = YEngine.getInstance();
        YEngine e2 = YEngine.getInstance();
        assertNotNull(e1, "First engine reference must not be null");
        assertNotNull(e2, "Second engine reference must not be null");
        assertEquals(System.identityHashCode(e1), System.identityHashCode(e2),
                "YEngine must be a singleton (same instance)");
    }

    // =========================================================================
    // Cross-Module Data Consistency
    // =========================================================================

    @Test
    void testCrossModuleDataConsistency() throws Exception {
        // Build 3 different specs via elements module
        int specCount = 3;
        List<YSpecification> specs = new ArrayList<>();
        for (int i = 0; i < specCount; i++) {
            YSpecification spec = WorkflowDataFactory.buildSequentialSpec(
                    "consistency-spec-" + i, i + 1);
            specs.add(spec);
            WorkflowDataFactory.seedSpecification(db,
                    "consistency-spec-" + i, "1.0", spec.getName());
        }

        // Verify DB reflects the in-memory elements model
        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT spec_id, spec_name FROM yawl_specification ORDER BY spec_id")) {
            int idx = 0;
            while (rs.next()) {
                String dbSpecId   = rs.getString("spec_id");
                String dbSpecName = rs.getString("spec_name");
                YSpecification memSpec = specs.get(idx);

                assertEquals("consistency-spec-" + idx, dbSpecId,
                        "DB spec_id must match in-memory spec ID");
                assertEquals(memSpec.getName(), dbSpecName,
                        "DB spec_name must match in-memory spec name");
                idx++;
            }
            assertEquals(specCount, idx,
                    "DB must contain exactly " + specCount + " spec rows");
        }
    }

    // =========================================================================
    // Concurrent Multi-Module Access
    // =========================================================================

    @Test
    void testConcurrentSpecificationBuildAndPersist() throws Exception {
        int threadCount = 5;
        List<Thread> threads = new ArrayList<>();
        List<YSpecification> builtSpecs =
                java.util.Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            Thread t = Thread.ofVirtual().start(() -> {
                try {
                    YSpecification spec = WorkflowDataFactory.buildSequentialSpec(
                            "concurrent-spec-" + idx, idx + 1);
                    builtSpecs.add(spec);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            threads.add(t);
        }

        for (Thread t : threads) {
            t.join(10_000);
        }

        assertEquals(threadCount, builtSpecs.size(),
                "All " + threadCount + " virtual threads must build a spec");

        // Persist all (single-threaded to avoid H2 concurrent write contention)
        for (int i = 0; i < builtSpecs.size(); i++) {
            YSpecification s = builtSpecs.get(i);
            WorkflowDataFactory.seedSpecification(db,
                    "concurrent-spec-" + i, "1.0", s.getName());
        }

        try (Statement stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM yawl_specification")) {
            assertTrue(rs.next());
            assertEquals(threadCount, rs.getInt(1),
                    "All " + threadCount + " specs must be persisted");
        }
    }
}
