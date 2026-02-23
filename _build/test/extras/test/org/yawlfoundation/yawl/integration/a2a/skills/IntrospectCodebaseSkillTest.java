/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.a2a.skills;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Tests for IntrospectCodebaseSkill using Chicago School TDD.
 *
 * <p>Tests the Observatory-based codebase introspection skill that provides
 * 100x context compression by querying pre-computed facts instead of
 * exploring the full codebase.
 *
 * <p><b>Coverage Targets:</b>
 * <ul>
 *   <li>Skill metadata (ID, name, description, permissions)</li>
 *   <li>Query validation and error handling</li>
 *   <li>Individual query types (modules, reactor, gates, etc.)</li>
 *   <li>Aggregate queries (all)</li>
 *   <li>Missing fact file handling</li>
 *   <li>Observatory availability checks</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class IntrospectCodebaseSkillTest extends TestCase {

    private Path tempObservatory;
    private Path factsDir;
    private IntrospectCodebaseSkill skill;

    public IntrospectCodebaseSkillTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tempObservatory = Files.createTempDirectory("observatory-test");
        factsDir = tempObservatory.resolve("facts");
        Files.createDirectories(factsDir);
        skill = new IntrospectCodebaseSkill(tempObservatory);
    }

    @Override
    protected void tearDown() throws Exception {
        if (tempObservatory != null) {
            deleteRecursively(tempObservatory);
        }
        super.tearDown();
    }

    // =========================================================================
    // Skill Metadata Tests
    // =========================================================================

    public void testGetIdReturnsCorrectIdentifier() {
        assertEquals("introspect_codebase", skill.getId());
    }

    public void testGetNameReturnsHumanReadableName() {
        assertEquals("Introspect Codebase", skill.getName());
    }

    public void testGetDescriptionContainsCompressionRatio() {
        String description = skill.getDescription();
        assertNotNull("Description should not be null", description);
        assertTrue("Description should mention 100x compression",
            description.contains("100x"));
        assertTrue("Description should mention context compression",
            description.contains("compression"));
    }

    public void testGetRequiredPermissionsIncludesCodeRead() {
        Set<String> permissions = skill.getRequiredPermissions();
        assertTrue("Should require code:read permission",
            permissions.contains("code:read"));
        assertEquals("Should only require one permission", 1, permissions.size());
    }

    // =========================================================================
    // Query Validation Tests
    // =========================================================================

    public void testExecuteWithNullQueryDefaultsToAll() throws Exception {
        writeFactFile("modules.json", "{\"modules\": []}");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should succeed with default query", result.isSuccess());
    }

    public void testExecuteWithEmptyQueryDefaultsToAll() throws Exception {
        writeFactFile("modules.json", "{\"modules\": []}");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should succeed with empty query defaulting to all", result.isSuccess());
    }

    public void testExecuteWithUnknownQueryReturnsError() {
        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "unknown_query_type")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Should fail with unknown query", result.isSuccess());
        assertTrue("Error should mention unknown query",
            result.getError().contains("Unknown query"));
        assertTrue("Error should list supported queries",
            result.getError().contains("Supported"));
    }

    // =========================================================================
    // Individual Query Type Tests
    // =========================================================================

    public void testExecuteModulesQueryReturnsModuleData() throws Exception {
        writeFactFile("modules.json", "{\"modules\": [\"yawl-engine\", \"yawl-elements\"]}");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "modules")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should succeed for modules query", result.isSuccess());
        assertNotNull("Should have data", result.getData());

        @SuppressWarnings("unchecked")
        java.util.List<String> modules = (java.util.List<String>) result.getData().get("modules");
        assertNotNull("Should have modules list", modules);
        assertTrue("Should contain yawl-engine", modules.contains("yawl-engine"));
    }

    public void testExecuteReactorQueryReturnsBuildOrder() throws Exception {
        writeFactFile("reactor.json", "{\"build_order\": [\"engine\", \"elements\", \"integration\"]}");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "reactor")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should succeed for reactor query", result.isSuccess());
        assertNotNull("Should have data", result.getData());
    }

    public void testExecuteGatesQueryReturnsQualityGates() throws Exception {
        writeFactFile("gates.json", "{\"gates\": {\"coverage\": 80, \"lint\": \"strict\"}}");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "gates")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should succeed for gates query", result.isSuccess());
        assertNotNull("Should have data", result.getData());
    }

    public void testExecuteIntegrationQueryReturnsIntegrationStatus() throws Exception {
        writeFactFile("integration.json", "{\"mcp\": true, \"a2a\": true, \"zai\": true}");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "integration")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should succeed for integration query", result.isSuccess());
        assertNotNull("Should have data", result.getData());
    }

    public void testExecuteStaticAnalysisQueryReturnsAnalysisData() throws Exception {
        writeFactFile("static-analysis.json", "{\"bugs\": 0, \"violations\": 0}");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "static-analysis")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should succeed for static-analysis query", result.isSuccess());
    }

    public void testExecuteSpotbugsQueryReturnsFindings() throws Exception {
        writeFactFile("spotbugs-findings.json", "{\"findings\": []}");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "spotbugs")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should succeed for spotbugs query", result.isSuccess());
    }

    public void testExecutePmdQueryReturnsViolations() throws Exception {
        writeFactFile("pmd-violations.json", "{\"violations\": []}");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "pmd")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should succeed for pmd query", result.isSuccess());
    }

    public void testExecuteCheckstyleQueryReturnsWarnings() throws Exception {
        writeFactFile("checkstyle-warnings.json", "{\"warnings\": []}");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "checkstyle")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should succeed for checkstyle query", result.isSuccess());
    }

    // =========================================================================
    // Aggregate Query Tests
    // =========================================================================

    public void testExecuteAllQueryAggregatesAllFacts() throws Exception {
        writeFactFile("modules.json", "{\"modules\": []}");
        writeFactFile("reactor.json", "{\"build_order\": []}");
        writeFactFile("gates.json", "{}");
        writeFactFile("integration.json", "{}");
        writeFactFile("static-analysis.json", "{}");
        writeFactFile("spotbugs-findings.json", "{}");
        writeFactFile("pmd-violations.json", "{}");
        writeFactFile("checkstyle-warnings.json", "{}");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "all")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should succeed for all query", result.isSuccess());
        assertNotNull("Should have data", result.getData());
        assertNotNull("Should have timestamp", result.getData().get("timestamp"));
        assertNotNull("Should have modules", result.getData().get("modules"));
        assertNotNull("Should have reactor", result.getData().get("reactor"));
        assertNotNull("Should have gates", result.getData().get("gates"));
        assertNotNull("Should have integration", result.getData().get("integration"));
        assertNotNull("Should have static_analysis", result.getData().get("static_analysis"));
    }

    public void testExecuteAllQueryIncludesStaticAnalysisSummary() throws Exception {
        writeFactFile("modules.json", "{\"modules\": []}");
        writeFactFile("reactor.json", "{}");
        writeFactFile("gates.json", "{}");
        writeFactFile("integration.json", "{}");
        writeFactFile("static-analysis.json", "{\"summary\": \"clean\"}");
        writeFactFile("spotbugs-findings.json", "{}");
        writeFactFile("pmd-violations.json", "{}");
        writeFactFile("checkstyle-warnings.json", "{}");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "all")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue("Should succeed", result.isSuccess());

        @SuppressWarnings("unchecked")
        Map<String, Object> staticAnalysis = (Map<String, Object>) result.getData().get("static_analysis");
        assertNotNull("Should have static_analysis object", staticAnalysis);
        assertNotNull("Should have summary in static_analysis", staticAnalysis.get("summary"));
    }

    // =========================================================================
    // Missing Fact File Handling Tests
    // =========================================================================

    public void testExecuteQueryWithMissingFactFileReturnsHelpfulError() throws Exception {
        // Do not create any fact files
        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "modules")
            .build();
        SkillResult result = skill.execute(request);

        // Should still succeed but return error data
        assertTrue("Should succeed even with missing file", result.isSuccess());
        assertNotNull("Should have data", result.getData());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = result.getData();
        assertTrue("Should indicate file not found",
            data.toString().contains("not found") || data.toString().contains("error"));
    }

    public void testExecuteQueryWithMissingFactFileSuggestsRunningObservatory() throws Exception {
        // Do not create any fact files
        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "modules")
            .build();
        SkillResult result = skill.execute(request);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = result.getData();
        assertTrue("Should suggest running observatory script",
            data.toString().contains("observatory.sh") || data.toString().contains("hint"));
    }

    public void testExecuteQueryWithMalformedJsonReturnsError() throws Exception {
        writeFactFile("modules.json", "this is not valid json {{{");

        SkillRequest request = SkillRequest.builder("introspect_codebase")
            .parameter("query", "modules")
            .build();
        SkillResult result = skill.execute(request);

        assertFalse("Should fail with malformed JSON", result.isSuccess());
        assertNotNull("Should have error message", result.getError());
    }

    // =========================================================================
    // Observatory Availability Tests
    // =========================================================================

    public void testIsObservatoryAvailableReturnsTrueWhenFactsExist() throws Exception {
        writeFactFile("modules.json", "{}");

        assertTrue("Should report available when modules.json exists",
            skill.isObservatoryAvailable());
    }

    public void testIsObservatoryAvailableReturnsFalseWhenNoFacts() {
        assertFalse("Should report unavailable when no facts exist",
            skill.isObservatoryAvailable());
    }

    public void testIsObservatoryAvailableReturnsFalseWhenNoModulesJson() throws Exception {
        // Create other files but not modules.json
        writeFactFile("reactor.json", "{}");

        assertFalse("Should report unavailable when modules.json missing",
            skill.isObservatoryAvailable());
    }

    // =========================================================================
    // Available Facts Tests
    // =========================================================================

    public void testGetAvailableFactsReturnsAllSupportedQueries() {
        Set<String> facts = skill.getAvailableFacts();

        assertNotNull("Should return non-null set", facts);
        assertTrue("Should include modules", facts.contains("modules"));
        assertTrue("Should include reactor", facts.contains("reactor"));
        assertTrue("Should include gates", facts.contains("gates"));
        assertTrue("Should include integration", facts.contains("integration"));
        assertTrue("Should include static-analysis", facts.contains("static-analysis"));
        assertTrue("Should include all", facts.contains("all"));
    }

    // =========================================================================
    // Permission Checking Tests (via A2ASkill interface)
    // =========================================================================

    public void testCanExecuteReturnsTrueWhenAllPermissionsGranted() {
        Set<String> granted = Set.of("code:read", "code:write");

        assertTrue("Should allow execution with required permissions",
            skill.canExecute(granted));
    }

    public void testCanExecuteReturnsTrueWithWildcardPermission() {
        Set<String> granted = Set.of("*");

        assertTrue("Should allow execution with wildcard permission",
            skill.canExecute(granted));
    }

    public void testCanExecuteReturnsFalseWhenPermissionMissing() {
        Set<String> granted = Set.of("code:write");

        assertFalse("Should deny execution without required permission",
            skill.canExecute(granted));
    }

    public void testCanExecuteReturnsFalseWithEmptyPermissions() {
        Set<String> granted = Set.of();

        assertFalse("Should deny execution with no permissions",
            skill.canExecute(granted));
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void writeFactFile(String name, String content) throws IOException {
        Files.writeString(factsDir.resolve(name), content);
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walk(path)
                .sorted((a, b) -> b.compareTo(a)) // Delete children first
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        // Ignore deletion errors in teardown
                    }
                });
        } else {
            Files.delete(path);
        }
    }
}
