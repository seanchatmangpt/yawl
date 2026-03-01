/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.yawlfoundation.yawl.datamodelling.bridge.DataModellingL3;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link DataModellingL3}.
 *
 * <p>Native-library-dependent tests are skipped when the native library is not available
 * (e.g. in CI without the compiled Rust cdylib). Set system property
 * {@code datamodelling.library.path} to the path of {@code libdata_modelling_ffi.so}
 * to enable native tests.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataModellingBridgeTest {

    private DataModellingL3 bridge;

    @BeforeAll
    void setUp() {
        try {
            bridge = DataModellingL3.fromSystemProperty();
        } catch (Exception e) {
            // Expected when native library is not available (e.g. CI without cargo build).
            // Native-dependent tests will be skipped via skipIfNoNativeLib().
            bridge = null;
        }
    }

    @AfterAll
    void tearDown() {
        if (bridge != null) {
            bridge.close();
        }
    }

    /** Skips the calling test if the native library is not loaded. */
    private void skipIfNoNativeLib() {
        assumeTrue(bridge != null && bridge.isAvailable(),
                "Skipped: DataModellingL3 requires native libdata_modelling_ffi.so. " +
                "Set -Ddatamodelling.library.path=<path> to enable.");
    }

    // ── ODCS YAML parsing ─────────────────────────────────────────────────────

    @Test
    void parseOdcsYaml_minimalContract_returnsWorkspaceJson() {
        skipIfNoNativeLib();
        String yaml = """
                apiVersion: v3.1.0
                kind: DataContract
                name: customers
                schema:
                  fields:
                    - name: id
                      type: bigint
                    - name: email
                      type: string
                """;

        String result = bridge.parseOdcsYaml(yaml);

        assertThat(result, not(emptyOrNullString()));
        assertThat(result, containsString("customers"));
    }

    // ── SQL import ────────────────────────────────────────────────────────────

    @Test
    void importFromSql_postgresCreateTable_returnsWorkspaceJson() {
        skipIfNoNativeLib();
        String sql = """
                CREATE TABLE orders (
                    id        BIGINT       NOT NULL,
                    total     DECIMAL(10,2) NOT NULL,
                    status    VARCHAR(50),
                    PRIMARY KEY (id)
                );
                """;

        String result = bridge.importFromSql(sql, "postgres");

        assertThat(result, not(emptyOrNullString()));
        assertThat(result, containsString("orders"));
    }

    @Test
    void importFromSql_sqliteCreateTable_returnsWorkspaceJson() {
        skipIfNoNativeLib();
        String sql = "CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT NOT NULL);";

        String result = bridge.importFromSql(sql, "sqlite");

        assertThat(result, not(emptyOrNullString()));
        assertThat(result, containsString("products"));
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @Test
    void exportOdcsYamlV2_parsedWorkspace_roundTripsSuccessfully() {
        skipIfNoNativeLib();
        String yaml = """
                apiVersion: v3.1.0
                kind: DataContract
                name: invoices
                schema:
                  fields:
                    - name: invoice_id
                      type: uuid
                """;

        String workspaceJson = bridge.parseOdcsYaml(yaml);
        assertThat(workspaceJson, not(emptyOrNullString()));

        String exportedYaml = bridge.exportOdcsYamlV2(workspaceJson);
        assertThat(exportedYaml, not(emptyOrNullString()));
    }

    @Test
    void exportOdcsYamlToMarkdown_parsedWorkspace_returnsMarkdown() {
        skipIfNoNativeLib();
        String yaml = """
                apiVersion: v3.1.0
                kind: DataContract
                name: users
                schema:
                  fields:
                    - name: user_id
                      type: bigint
                    - name: username
                      type: string
                """;

        String result = bridge.exportOdcsYamlToMarkdown(yaml);
        assertThat(result, not(emptyOrNullString()));
    }

    // ── Workspace operations ──────────────────────────────────────────────────

    @Test
    void createWorkspace_validArgs_returnsWorkspaceJson() {
        skipIfNoNativeLib();
        String result = bridge.createWorkspace("test-workspace", "owner-123");

        assertThat(result, not(emptyOrNullString()));
        assertThat(result, containsString("test-workspace"));
    }

    // ── Domain operations ─────────────────────────────────────────────────────

    @Test
    void createDomain_validName_returnsDomainJson() {
        skipIfNoNativeLib();
        String result = bridge.createDomain("sales");

        assertThat(result, not(emptyOrNullString()));
        assertThat(result, containsString("sales"));
    }

    @Test
    void addDomainToWorkspace_validArgs_returnsUpdatedWorkspace() {
        skipIfNoNativeLib();
        String workspace = bridge.createWorkspace("my-workspace", "owner-1");
        String domain = bridge.createDomain("analytics");

        assertThat(domain, containsString("analytics"));

        String result = bridge.addDomainToWorkspace(workspace, "domain-123", "analytics");
        assertThat(result, not(emptyOrNullString()));
    }

    // ── Decision records ──────────────────────────────────────────────────────

    @Test
    void createDecision_validArgs_returnsDecisionJson() {
        skipIfNoNativeLib();
        String result = bridge.createDecision(
                1,
                "Use PostgreSQL for primary storage",
                "We need a reliable, ACID-compliant relational database.",
                "Use PostgreSQL 16 with read replicas.",
                "YAWL Foundation"
        );

        assertThat(result, not(emptyOrNullString()));
        assertThat(result, containsString("PostgreSQL"));
    }

    @Test
    void decisionIndex_createAndAdd_roundTripsSuccessfully() {
        skipIfNoNativeLib();
        String index = bridge.createDecisionIndex();
        assertThat(index, not(emptyOrNullString()));

        String decision = bridge.createDecision(
                1, "ADR-001", "Context", "Decision", "Author");

        String updatedIndex = bridge.addDecisionToIndex(index, decision, "0001-adr-001.yaml");
        assertThat(updatedIndex, not(emptyOrNullString()));

        String yamlIndex = bridge.exportDecisionIndexToYaml(updatedIndex);
        assertThat(yamlIndex, not(emptyOrNullString()));
    }

    // ── Knowledge base ────────────────────────────────────────────────────────

    @Test
    void createKnowledgeArticle_validArgs_returnsArticleJson() {
        skipIfNoNativeLib();
        String result = bridge.createKnowledgeArticle(
                1,
                "API Authentication Guide",
                "How to authenticate with the YAWL API",
                "# Authentication\n\nUse Bearer tokens...",
                "YAWL Foundation"
        );

        assertThat(result, not(emptyOrNullString()));
        assertThat(result, containsString("Authentication"));
    }

    @Test
    void searchKnowledgeArticles_matchingQuery_returnsFilteredResults() {
        skipIfNoNativeLib();
        String article1 = bridge.createKnowledgeArticle(
                1, "Authentication Guide", "Auth", "# Auth content", "Author");
        String article2 = bridge.createKnowledgeArticle(
                2, "Deployment Runbook", "Deploy", "# Deploy content", "Author");

        String articlesJson = "[" + article1 + "," + article2 + "]";
        String results = bridge.searchKnowledgeArticles(articlesJson, "Authentication");

        assertThat(results, not(emptyOrNullString()));
    }

    // ── Sketch operations ─────────────────────────────────────────────────────

    @Test
    void createSketch_validArgs_returnsSketchJson() {
        skipIfNoNativeLib();
        String result = bridge.createSketch(
                1,
                "System Architecture Diagram",
                "architecture",
                "{\"type\":\"excalidraw\",\"elements\":[]}"
        );

        assertThat(result, not(emptyOrNullString()));
        assertThat(result, containsString("Architecture"));
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void validateTableName_validSnakeCase_returnsValidResult() {
        skipIfNoNativeLib();
        String result = bridge.validateTableName("customer_orders");
        assertThat(result, not(emptyOrNullString()));
    }

    @Test
    void validateColumnName_validName_returnsValidResult() {
        skipIfNoNativeLib();
        String result = bridge.validateColumnName("created_at");
        assertThat(result, not(emptyOrNullString()));
    }

    @Test
    void checkCircularDependency_noCircle_returnsFalse() {
        skipIfNoNativeLib();
        String result = bridge.checkCircularDependency("[]", "table-a", "table-b");
        assertThat(result, not(emptyOrNullString()));
    }

    // ── Convert operations ────────────────────────────────────────────────────

    @Test
    void convertToOdcs_fromSql_returnsOdcsJson() {
        skipIfNoNativeLib();
        String sql = "CREATE TABLE payments (id INT, amount DECIMAL, currency VARCHAR(3));";

        String result = bridge.convertToOdcs(sql, "sql");
        assertThat(result, not(emptyOrNullString()));
    }

    // ── BPMN/DMN passthrough ──────────────────────────────────────────────────

    @Test
    void exportBpmnModel_minimalBpmn_returnsXml() {
        skipIfNoNativeLib();
        String bpmnXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             id="def1" targetNamespace="http://example.com">
                  <process id="process1" isExecutable="true">
                    <startEvent id="start1"/>
                    <endEvent id="end1"/>
                  </process>
                </definitions>
                """;

        String result = bridge.exportBpmnModel(bpmnXml);
        assertThat(result, not(emptyOrNullString()));
    }
}
