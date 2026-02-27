/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.mcp.spec;

import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for {@link YawlGraalPyWorkflowToolSpecifications}.
 *
 * <p>Uses real objects — no mocks. Tests verify tool metadata (names, schemas)
 * without invoking the GraalPy runtime.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class YawlGraalPyWorkflowToolSpecificationsTest {

    private YawlGraalPyWorkflowToolSpecifications specs;

    @BeforeEach
    public void setUp() {
        specs = new YawlGraalPyWorkflowToolSpecifications();
    }

    @AfterEach
    public void tearDown() {
        specs.close();
    }

    // =========================================================================
    // createAll() — tool count and names
    // =========================================================================

    @Test
    public void testCreateAllReturnsTwoTools() {
        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        assertNotNull(tools);
        assertEquals(2, tools.size(), "Expected exactly 2 GraalPy tool specs");
    }

    @Test
    public void testToolNamesArePresentAndDistinct() {
        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        List<String> names = tools.stream()
            .map(t -> t.tool().name())
            .toList();

        assertTrue(names.contains("yawl_synthesize_graalpy"),
            "Expected yawl_synthesize_graalpy in " + names);
        assertTrue(names.contains("yawl_mine_workflow"),
            "Expected yawl_mine_workflow in " + names);
    }

    @Test
    public void testSynthesizeGraalPyToolHasDescription() {
        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        var synthesizeTool = tools.stream()
            .filter(t -> "yawl_synthesize_graalpy".equals(t.tool().name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("yawl_synthesize_graalpy not found"));

        assertNotNull(synthesizeTool.tool().description());
        assertFalse(synthesizeTool.tool().description().isBlank());
        assertTrue(synthesizeTool.tool().description().contains("GraalPy")
            || synthesizeTool.tool().description().contains("pm4py"),
            "Description should mention GraalPy or pm4py: "
                + synthesizeTool.tool().description());
    }

    @Test
    public void testMineWorkflowToolHasDescription() {
        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        var mineTool = tools.stream()
            .filter(t -> "yawl_mine_workflow".equals(t.tool().name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("yawl_mine_workflow not found"));

        assertNotNull(mineTool.tool().description());
        assertFalse(mineTool.tool().description().isBlank());
    }

    @Test
    public void testSynthesizeToolRequiresDescriptionParam() {
        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        var synthesizeTool = tools.stream()
            .filter(t -> "yawl_synthesize_graalpy".equals(t.tool().name()))
            .findFirst()
            .orElseThrow();

        var schema = synthesizeTool.tool().inputSchema();
        assertNotNull(schema);
        assertNotNull(schema.required());
        assertTrue(schema.required().contains("description"),
            "yawl_synthesize_graalpy must require 'description': " + schema.required());
    }

    @Test
    public void testMineWorkflowToolRequiresXesContentParam() {
        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        var mineTool = tools.stream()
            .filter(t -> "yawl_mine_workflow".equals(t.tool().name()))
            .findFirst()
            .orElseThrow();

        var schema = mineTool.tool().inputSchema();
        assertNotNull(schema);
        assertNotNull(schema.required());
        assertTrue(schema.required().contains("xesContent"),
            "yawl_mine_workflow must require 'xesContent': " + schema.required());
    }

    // =========================================================================
    // createAll() is safe to call multiple times
    // =========================================================================

    @Test
    public void testCreateAllIsIdempotent() {
        List<McpServerFeatures.SyncToolSpecification> first = specs.createAll();
        List<McpServerFeatures.SyncToolSpecification> second = specs.createAll();
        assertEquals(first.size(), second.size());
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Test
    public void testCloseDoesNotThrow() {
        assertDoesNotThrow(() -> specs.close());
    }

    @Test
    public void testImplementsAutoCloseable() {
        assertTrue(specs instanceof AutoCloseable,
            "YawlGraalPyWorkflowToolSpecifications must implement AutoCloseable");
    }
}
