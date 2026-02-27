/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WorkletService}: RDR evaluation, A2A routing, and worklet selection.
 *
 * <p>Chicago TDD: Uses real {@link RdrSet}, {@link RdrTree}, and {@link RdrNode} objects.
 * {@link YWorkItem} construction requires full engine context, so {@link TestableWorkletService}
 * overrides {@link WorkletService#buildContext} to inject a fixed test context.
 */
@Tag("unit")
class WorkletServiceTest {

    @TempDir
    Path rdrDir;

    private RdrSetRepository repository;
    private RdrSet rdrSet;

    @BeforeEach
    void setUp() {
        repository = new RdrSetRepository(rdrDir);

        // Build an RdrSet with two trees in memory for evaluate() tests
        rdrSet = new RdrSet("OrderProcessing");

        // Tree 1: taskId = ApprovalTask → FinanceApprovalWorklet
        RdrTree approvalTree = new RdrTree("ApprovalTask");
        approvalTree.setRoot(new RdrNode(1, "taskId = ApprovalTask", "FinanceApprovalWorklet"));
        rdrSet.addTree(approvalTree);

        // Tree 2: taskId = RiskTask → a2a:http://agent:8090/risk_assessment
        RdrTree riskTree = new RdrTree("RiskTask");
        riskTree.setRoot(new RdrNode(2, "taskId = RiskTask",
                "a2a:http://agent:8090/risk_assessment"));
        rdrSet.addTree(riskTree);
    }

    /**
     * Scenario 1: RDR conclusion matching taskId returns SubCaseSelection.
     */
    @Test
    void evaluate_matchingSubCaseRule_returnsSubCaseSelection() {
        Map<String, String> context = Map.of("taskId", "ApprovalTask", "caseId", "1.1",
                "specId", "OrderProcessing");
        TestableWorkletService service = new TestableWorkletService(repository, context);

        WorkletSelection result = service.evaluate(null, rdrSet);

        assertInstanceOf(WorkletSelection.SubCaseSelection.class, result,
                "Should return SubCaseSelection for matching sub-case rule");
        WorkletSelection.SubCaseSelection scs = (WorkletSelection.SubCaseSelection) result;
        assertEquals("FinanceApprovalWorklet", scs.workletName());
        assertEquals(1, scs.rdrNodeId());
    }

    /**
     * Scenario 2: A2A conclusion (starts with "a2a:") returns A2AAgentSelection.
     */
    @Test
    void evaluate_a2aConclusion_returnsA2AAgentSelection() {
        Map<String, String> context = Map.of("taskId", "RiskTask", "caseId", "1.2",
                "specId", "OrderProcessing");
        TestableWorkletService service = new TestableWorkletService(repository, context);

        WorkletSelection result = service.evaluate(null, rdrSet);

        assertInstanceOf(WorkletSelection.A2AAgentSelection.class, result,
                "Should return A2AAgentSelection for a2a: conclusion");
        WorkletSelection.A2AAgentSelection a2a = (WorkletSelection.A2AAgentSelection) result;
        assertEquals("http://agent:8090", a2a.agentEndpoint());
        assertEquals("risk_assessment", a2a.skill());
        assertEquals(2, a2a.rdrNodeId());
    }

    /**
     * Scenario 3: No matching rule returns NoSelection.
     */
    @Test
    void evaluate_noMatchingRule_returnsNoSelection() {
        Map<String, String> context = Map.of("taskId", "UnknownTask");
        TestableWorkletService service = new TestableWorkletService(repository, context);

        WorkletSelection result = service.evaluate(null, rdrSet);

        assertInstanceOf(WorkletSelection.NoSelection.class, result,
                "Should return NoSelection when no rule matches");
    }

    /**
     * Scenario 4: Empty RdrSet returns NoSelection.
     */
    @Test
    void evaluate_emptyRdrSet_returnsNoSelection() {
        Map<String, String> context = Map.of("taskId", "ApprovalTask");
        TestableWorkletService service = new TestableWorkletService(repository, context);
        RdrSet emptySet = new RdrSet("EmptySpec");

        WorkletSelection result = service.evaluate(null, emptySet);

        assertInstanceOf(WorkletSelection.NoSelection.class, result,
                "Empty RdrSet should always return NoSelection");
    }

    /**
     * Scenario 5: WorkletRecord A2A constructor sets isA2aDelegated correctly.
     */
    @Test
    void workletRecord_a2aConstructor_setsA2AFields() {
        WorkletRecord record = new WorkletRecord(
                "http://agent:8090", "risk_assessment", "case-1", "task-1");

        assertTrue(record.isA2aDelegated(), "Record created with A2A constructor should be delegated");
        assertEquals("http://agent:8090", record.getA2aEndpoint());
        assertEquals("risk_assessment", record.getA2aSkill());
        assertEquals("case-1", record.getHostCaseId());
        assertEquals("task-1", record.getHostTaskId());
    }

    /**
     * Scenario 6: Standard WorkletRecord constructor sets isA2aDelegated to false.
     */
    @Test
    void workletRecord_standardConstructor_notA2aDelegated() {
        WorkletRecord record = new WorkletRecord("FinanceApprovalWorklet", "case-1", "task-1");

        assertFalse(record.isA2aDelegated(), "Standard worklet record should not be A2A delegated");
        assertNull(record.getA2aEndpoint());
        assertNull(record.getA2aSkill());
    }

    /**
     * Scenario 7: RdrSetRepository returns empty RdrSet for spec with no rule file.
     */
    @Test
    void rdrSetRepository_noRuleFile_returnsEmptyRdrSet() {
        RdrSet result = repository.load("UnknownSpec");

        assertNotNull(result, "Should return non-null RdrSet even when no file exists");
        assertTrue(result.isEmpty(), "Should return empty RdrSet when no rule file exists");
        assertEquals("UnknownSpec", result.getSpecificationId());
    }

    /**
     * Scenario 8: RdrSetRepository caches the loaded RdrSet (same instance on second call).
     */
    @Test
    void rdrSetRepository_repeatedLoad_returnsCachedInstance() {
        RdrSet first = repository.load("SomeSpec");
        RdrSet second = repository.load("SomeSpec");

        assertSame(first, second, "Repeated load should return the cached instance");
        assertEquals(1, repository.cacheSize(), "Cache should have exactly one entry");
    }

    /**
     * Scenario 9: RdrSetRepository evict forces reload on next load call.
     */
    @Test
    void rdrSetRepository_evict_forcesReload() {
        RdrSet first = repository.load("SomeSpec");
        repository.evict("SomeSpec");

        assertEquals(0, repository.cacheSize(), "Cache should be empty after evict");

        RdrSet second = repository.load("SomeSpec");
        assertNotNull(second, "Should return a new empty RdrSet after evict");
    }

    /**
     * Scenario 10: RdrSetRepository loads rule file from disk and parses it.
     */
    @Test
    void rdrSetRepository_ruleFileExists_parsesRdrSet() throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rdrSet specId="PurchaseOrder">
                  <tree taskName="CreditCheck">
                    <node id="1" parentId="-1" branch="none"
                          condition="taskId = CreditCheck"
                          conclusion="CreditReviewWorklet"/>
                  </tree>
                </rdrSet>
                """;
        Files.writeString(rdrDir.resolve("PurchaseOrder.rdr.xml"), xml);

        RdrSet loaded = repository.load("PurchaseOrder");

        assertFalse(loaded.isEmpty(), "Loaded RdrSet should not be empty");
        assertTrue(loaded.hasTree("CreditCheck"),
                "Loaded RdrSet should have a tree for CreditCheck");
    }

    // -----------------------------------------------------------------------
    // Inner test helper — concrete subclass injecting fixed context
    // -----------------------------------------------------------------------

    /**
     * Subclass of WorkletService that returns a fixed context map from buildContext().
     * Allows testing evaluate() without constructing a real YWorkItem.
     */
    static class TestableWorkletService extends WorkletService {

        private final Map<String, String> fixedContext;

        TestableWorkletService(RdrSetRepository repository, Map<String, String> context) {
            super(repository);
            this.fixedContext = context;
        }

        @Override
        Map<String, String> buildContext(YWorkItem ignored) {
            return fixedContext;
        }
    }
}
