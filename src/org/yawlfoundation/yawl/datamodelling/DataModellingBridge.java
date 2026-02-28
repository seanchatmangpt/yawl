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

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionContext;
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;
import org.yawlfoundation.yawl.graaljs.JavaScriptSandboxConfig;
import org.yawlfoundation.yawl.datamodelling.converters.JsonObjectMapper;
import org.yawlfoundation.yawl.datamodelling.converters.WorkspaceConverter;
import org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;

/**
 * Thin Java facade over the data-modelling-sdk WebAssembly module (v2.3.0).
 *
 * <p>Exposes 70+ schema operations as typed Java methods. All computation runs inside
 * {@code data_modelling_wasm_bg.wasm} via GraalJS+WASM polyglot — no logic is
 * reimplemented in Java. Methods return raw JSON strings that callers can parse
 * or pass back to subsequent operations.</p>
 *
 * <h2>Architecture</h2>
 * <pre>
 * DataModellingBridge
 *   └─ JavaScriptExecutionEngine (yawl-graaljs, JS+WASM polyglot)
 *        └─ JavaScriptExecutionContext (Context.newBuilder("js","wasm"))
 *             └─ data_modelling_wasm.js  (ES module, wasm-bindgen glue)
 *                  └─ data_modelling_wasm_bg.wasm  (Rust compiled SDK)
 * </pre>
 *
 * <h2>Supported operations</h2>
 * <ul>
 *   <li>Schema import: ODCS YAML, SQL (PostgreSQL/MySQL/SQLite/Databricks),
 *       Avro, JSON Schema, Protobuf, CADS, ODPS, BPMN, DMN, OpenAPI</li>
 *   <li>Schema export: ODCS YAML, SQL, Avro, JSON Schema, Protobuf, CADS, ODPS,
 *       BPMN, DMN, OpenAPI, Markdown, PDF</li>
 *   <li>Format conversion: universal ODCS converter, OpenAPI-to-ODCS</li>
 *   <li>Workspace operations: create, domain management, relationship management</li>
 *   <li>Decision records (MADR): create, parse, export YAML/Markdown</li>
 *   <li>Knowledge base (KB): create articles, search, export YAML/Markdown</li>
 *   <li>Sketches: create, parse, export, search</li>
 *   <li>Validation: ODPS, table/column names, data types, circular dependencies</li>
 * </ul>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * try (DataModellingBridge bridge = new DataModellingBridge()) {
 *
 *     // Parse ODCS YAML into workspace JSON
 *     String workspaceJson = bridge.parseOdcsYaml("""
 *         apiVersion: v3.1.0
 *         kind: DataContract
 *         name: customers
 *         schema:
 *           fields:
 *             - name: id
 *               type: bigint
 *         """);
 *
 *     // Convert back to YAML
 *     String yaml = bridge.exportOdcsYamlV2(workspaceJson);
 *
 *     // Import from SQL
 *     String fromSql = bridge.importFromSql("CREATE TABLE orders(id INT, total DECIMAL)", "postgres");
 * }
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>Thread-safe via the underlying {@code JavaScriptContextPool} (pool size = 1 by default).</p>
 *
 * <h2>WASM binary source</h2>
 * <p>data-modelling-sdk v2.3.0 — MIT licence — github.com/OffeneDatenmodellierung/data-modelling-sdk</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DataModellingException
 */
public final class DataModellingBridge implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DataModellingBridge.class);

    /** Classpath resource: wasm-bindgen compiled Rust logic. */
    static final String WASM_RESOURCE = "wasm/data_modelling_wasm_bg.wasm";

    /** Classpath resource: wasm-bindgen ES module JS glue. */
    static final String GLUE_RESOURCE = "wasm/data_modelling_wasm.js";

    private final JavaScriptExecutionEngine jsEngine;
    private final Path tempDir;

    /**
     * Constructs the bridge with a custom JS context pool size.
     *
     * @param poolSize  number of concurrent JS+WASM contexts (1 for most use cases)
     * @throws DataModellingException  MODULE_LOAD_ERROR if WASM resources are missing
     * @throws IllegalArgumentException  if poolSize &lt; 1
     */
    public DataModellingBridge(int poolSize) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be at least 1, got: " + poolSize);
        }
        this.tempDir = extractResourcesToTemp();

        JavaScriptSandboxConfig sandboxConfig = JavaScriptSandboxConfig.builder()
                .mode(JavaScriptSandboxConfig.SandboxMode.STANDARD)
                .wasmEnabled(true)
                .allowExperimentalOptions(true)
                .ecmaScriptVersion("2024")
                .allowRead(tempDir)
                .build();

        this.jsEngine = JavaScriptExecutionEngine.builder()
                .sandboxConfig(sandboxConfig)
                .contextPoolSize(poolSize)
                .build();

        Path gluePath = tempDir.resolve("data_modelling_wasm.js");
        Path wasmPath = tempDir.resolve("data_modelling_wasm_bg.wasm");
        jsEngine.getContextPool().executeVoid(ctx -> initWasmInContext(ctx, gluePath, wasmPath));

        log.info("DataModellingBridge initialised: poolSize={}", poolSize);
    }

    /**
     * Convenience constructor with poolSize=1.
     *
     * @throws DataModellingException  if resource extraction or initialisation fails
     */
    public DataModellingBridge() {
        this(1);
    }

    // ── Schema import ─────────────────────────────────────────────────────────

    /**
     * Parses ODCS YAML (v3.1.0 or v2.x) into workspace JSON.
     *
     * @param yaml  ODCS YAML content; must not be null
     * @return workspace JSON string; never null
     * @throws DataModellingException  EXECUTION_ERROR on parse failure
     */
    public String parseOdcsYaml(String yaml) {
        return call("parse_odcs_yaml", yaml);
    }

    /**
     * Parses ODCS YAML v2 (alternate parser with extended field preservation).
     *
     * @param yaml  ODCS v2 YAML content; must not be null
     * @return workspace JSON string; never null
     */
    public String parseOdcsYamlV2(String yaml) {
        return call("parse_odcs_yaml_v2", yaml);
    }

    /**
     * Imports from SQL DDL statements into workspace JSON.
     *
     * @param sql      SQL DDL content; must not be null
     * @param dialect  SQL dialect: {@code "postgres"}, {@code "mysql"}, {@code "sqlite"},
     *                 {@code "generic"}, or {@code "databricks"}; must not be null
     * @return workspace JSON string; never null
     */
    public String importFromSql(String sql, String dialect) {
        return call("import_from_sql", sql, dialect);
    }

    /**
     * Imports from an Avro schema into workspace JSON.
     *
     * @param avroContent  Avro schema JSON; must not be null
     * @return workspace JSON string; never null
     */
    public String importFromAvro(String avroContent) {
        return call("import_from_avro", avroContent);
    }

    /**
     * Imports from a JSON Schema into workspace JSON.
     *
     * @param jsonSchemaContent  JSON Schema content; must not be null
     * @return workspace JSON string; never null
     */
    public String importFromJsonSchema(String jsonSchemaContent) {
        return call("import_from_json_schema", jsonSchemaContent);
    }

    /**
     * Imports from a Protobuf schema into workspace JSON.
     *
     * @param protobufContent  Protobuf schema content; must not be null
     * @return workspace JSON string; never null
     */
    public String importFromProtobuf(String protobufContent) {
        return call("import_from_protobuf", protobufContent);
    }

    /**
     * Imports from a CADS (Compute Asset Description Specification) YAML.
     *
     * @param yamlContent  CADS YAML content; must not be null
     * @return workspace JSON string; never null
     */
    public String importFromCads(String yamlContent) {
        return call("import_from_cads", yamlContent);
    }

    /**
     * Imports from an ODPS (Open Data Product Standard) YAML.
     *
     * @param yamlContent  ODPS YAML content; must not be null
     * @return workspace JSON string; never null
     */
    public String importFromOdps(String yamlContent) {
        return call("import_from_odps", yamlContent);
    }

    /**
     * Imports a BPMN 2.0 XML process model into a domain.
     *
     * @param domainId    the target domain ID; must not be null
     * @param xmlContent  BPMN 2.0 XML content; must not be null
     * @param modelName   optional model name; may be null
     * @return domain JSON string; never null
     */
    public String importBpmnModel(String domainId, String xmlContent, @Nullable String modelName) {
        return call("import_bpmn_model", domainId, xmlContent, modelName != null ? modelName : "");
    }

    /**
     * Imports a DMN 1.3 XML decision model into a domain.
     *
     * @param domainId    the target domain ID; must not be null
     * @param xmlContent  DMN 1.3 XML content; must not be null
     * @param modelName   optional model name; may be null
     * @return domain JSON string; never null
     */
    public String importDmnModel(String domainId, String xmlContent, @Nullable String modelName) {
        return call("import_dmn_model", domainId, xmlContent, modelName != null ? modelName : "");
    }

    /**
     * Imports an OpenAPI 3.1.1 specification into a domain.
     *
     * @param domainId  the target domain ID; must not be null
     * @param content   OpenAPI specification content (YAML or JSON); must not be null
     * @param apiName   optional API name; may be null
     * @return domain JSON string; never null
     */
    public String importOpenapiSpec(String domainId, String content, @Nullable String apiName) {
        return call("import_openapi_spec", domainId, content, apiName != null ? apiName : "");
    }

    // ── Schema export ─────────────────────────────────────────────────────────

    /**
     * Exports workspace JSON to ODCS YAML v3.1.0.
     *
     * @param contractJson  workspace or table JSON string; must not be null
     * @return ODCS YAML string; never null
     */
    public String exportOdcsYamlV2(String contractJson) {
        return call("export_odcs_yaml_v2", contractJson);
    }

    /**
     * Exports workspace JSON to SQL DDL statements.
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @param dialect        SQL dialect; must not be null
     * @return SQL DDL string; never null
     */
    public String exportToSql(String workspaceJson, String dialect) {
        return call("sanitize_sql_identifier", workspaceJson, dialect);
    }

    /**
     * Exports a BPMN model XML, normalising and re-serialising it.
     *
     * @param xmlContent  BPMN 2.0 XML content; must not be null
     * @return exported BPMN XML string; never null
     */
    public String exportBpmnModel(String xmlContent) {
        return call("export_bpmn_model", xmlContent);
    }

    /**
     * Exports a DMN model XML, normalising and re-serialising it.
     *
     * @param xmlContent  DMN 1.3 XML content; must not be null
     * @return exported DMN XML string; never null
     */
    public String exportDmnModel(String xmlContent) {
        return call("export_dmn_model", xmlContent);
    }

    /**
     * Exports ODCS YAML to Markdown documentation.
     *
     * @param odcsYaml  ODCS YAML content; must not be null
     * @return Markdown string; never null
     */
    public String exportOdcsYamlToMarkdown(String odcsYaml) {
        return call("export_odcs_yaml_to_markdown", odcsYaml);
    }

    /**
     * Exports ODPS YAML to Markdown documentation.
     *
     * @param productJson  ODPS product JSON string; must not be null
     * @return Markdown string; never null
     */
    public String exportOdpsToMarkdown(String productJson) {
        return call("export_odps_to_markdown", productJson);
    }

    // ── Format conversion ─────────────────────────────────────────────────────

    /**
     * Universal converter: converts any supported format to ODCS v3.1.0.
     *
     * <p>Supported input formats: ODCS, ODCL, SQL, JSON Schema, Avro, Protobuf,
     * CADS, ODPS, BPMN, DMN, OpenAPI.</p>
     *
     * @param input   the input content; must not be null
     * @param format  hint for input format (e.g., {@code "sql"}, {@code "avro"});
     *                may be null for auto-detection
     * @return ODCS v3.1.0 workspace JSON string; never null
     */
    public String convertToOdcs(String input, @Nullable String format) {
        return call("convert_to_odcs", input, format != null ? format : "");
    }

    /**
     * Converts an OpenAPI schema component to an ODCS table definition.
     *
     * @param openapiContent  OpenAPI specification content; must not be null
     * @param componentName   the schema component name to convert; must not be null
     * @param tableName       optional target table name; may be null (defaults to componentName)
     * @return ODCS table JSON string; never null
     */
    public String convertOpenapiToOdcs(String openapiContent, String componentName,
                                        @Nullable String tableName) {
        return call("convert_openapi_to_odcs",
                openapiContent, componentName, tableName != null ? tableName : "");
    }

    /**
     * Analyses feasibility of converting an OpenAPI component to ODCS.
     *
     * @param openapiContent  OpenAPI specification content; must not be null
     * @param componentName   the component name to analyse; must not be null
     * @return analysis JSON string with field mapping feasibility; never null
     */
    public String analyzeOpenapiConversion(String openapiContent, String componentName) {
        return call("analyze_openapi_conversion", openapiContent, componentName);
    }

    /**
     * Migrates a DataFlow YAML file to the Domain schema format.
     *
     * @param dataflowYaml  DataFlow YAML content; must not be null
     * @param domainName    optional domain name; may be null
     * @return domain JSON string; never null
     */
    public String migrateDataflowToDomain(String dataflowYaml, @Nullable String domainName) {
        return call("migrate_dataflow_to_domain", dataflowYaml, domainName != null ? domainName : "");
    }

    // ── Workspace operations ──────────────────────────────────────────────────

    /**
     * Creates a new empty workspace.
     *
     * @param name     workspace name; must not be null
     * @param ownerId  owner identifier; must not be null
     * @return workspace JSON string; never null
     */
    public String createWorkspace(String name, String ownerId) {
        return call("create_workspace", name, ownerId);
    }

    /**
     * Parses workspace YAML into workspace JSON.
     *
     * @param yamlContent  workspace YAML content; must not be null
     * @return workspace JSON string; never null
     */
    public String parseWorkspaceYaml(String yamlContent) {
        return call("parse_workspace_yaml", yamlContent);
    }

    /**
     * Adds a relationship to a workspace.
     *
     * @param workspaceJson    workspace JSON string; must not be null
     * @param relationshipJson relationship JSON string; must not be null
     * @return updated workspace JSON string; never null
     */
    public String addRelationshipToWorkspace(String workspaceJson, String relationshipJson) {
        return call("add_relationship_to_workspace", workspaceJson, relationshipJson);
    }

    /**
     * Removes a relationship from a workspace by ID.
     *
     * @param workspaceJson    workspace JSON string; must not be null
     * @param relationshipId   the relationship ID to remove; must not be null
     * @return updated workspace JSON string; never null
     */
    public String removeRelationshipFromWorkspace(String workspaceJson, String relationshipId) {
        return call("remove_relationship_from_workspace", workspaceJson, relationshipId);
    }

    // ── Domain operations ─────────────────────────────────────────────────────

    /**
     * Creates a new business domain.
     *
     * @param name  the domain name; must not be null
     * @return domain JSON string; never null
     */
    public String createDomain(String name) {
        return call("create_domain", name);
    }

    /**
     * Adds a domain to a workspace.
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @param domainId       domain ID to add; must not be null
     * @param domainName     domain name; must not be null
     * @return updated workspace JSON string; never null
     */
    public String addDomainToWorkspace(String workspaceJson, String domainId, String domainName) {
        return call("add_domain_to_workspace", workspaceJson, domainId, domainName);
    }

    /**
     * Removes a domain from a workspace.
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @param domainId       domain ID to remove; must not be null
     * @return updated workspace JSON string; never null
     */
    public String removeDomainFromWorkspace(String workspaceJson, String domainId) {
        return call("remove_domain_from_workspace", workspaceJson, domainId);
    }

    /**
     * Adds a system to a domain.
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @param domainId       target domain ID; must not be null
     * @param systemJson     system JSON string; must not be null
     * @return updated workspace JSON string; never null
     */
    public String addSystemToDomain(String workspaceJson, String domainId, String systemJson) {
        return call("add_system_to_domain", workspaceJson, domainId, systemJson);
    }

    /**
     * Adds an ODCS node (table) to a domain.
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @param domainId       target domain ID; must not be null
     * @param nodeJson       ODCS node JSON string; must not be null
     * @return updated workspace JSON string; never null
     */
    public String addOdcsNodeToDomain(String workspaceJson, String domainId, String nodeJson) {
        return call("add_odcs_node_to_domain", workspaceJson, domainId, nodeJson);
    }

    /**
     * Adds a CADS node (compute asset) to a domain.
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @param domainId       target domain ID; must not be null
     * @param nodeJson       CADS node JSON string; must not be null
     * @return updated workspace JSON string; never null
     */
    public String addCadsNodeToDomain(String workspaceJson, String domainId, String nodeJson) {
        return call("add_cads_node_to_domain", workspaceJson, domainId, nodeJson);
    }

    // ── Decision records (MADR) ───────────────────────────────────────────────

    /**
     * Creates a new MADR-compliant Architecture Decision Record.
     *
     * @param number   sequential decision number; must be &gt; 0
     * @param title    decision title; must not be null
     * @param context  the problem context; must not be null
     * @param decision the chosen option; must not be null
     * @param author   the decision author; must not be null
     * @return decision JSON string; never null
     */
    public String createDecision(int number, String title, String context,
                                  String decision, String author) {
        return call("create_decision",
                String.valueOf(number), title, context, decision, author);
    }

    /**
     * Creates a new empty decision index.
     *
     * @return decision index JSON string; never null
     */
    public String createDecisionIndex() {
        return call("create_decision_index");
    }

    /**
     * Parses a decision record YAML file.
     *
     * @param yamlContent  decision YAML content; must not be null
     * @return decision JSON string; never null
     */
    public String parseDecisionYaml(String yamlContent) {
        return call("parse_decision_yaml", yamlContent);
    }

    /**
     * Exports a decision to YAML format.
     *
     * @param decisionJson  decision JSON string; must not be null
     * @return YAML string; never null
     */
    public String exportDecisionToYaml(String decisionJson) {
        return call("export_decision_to_yaml", decisionJson);
    }

    /**
     * Exports a decision to Markdown (MADR format).
     *
     * @param decisionJson  decision JSON string; must not be null
     * @return Markdown string; never null
     */
    public String exportDecisionToMarkdown(String decisionJson) {
        return call("export_decision_to_markdown", decisionJson);
    }

    /**
     * Parses a decision index YAML file.
     *
     * @param yamlContent  decision index YAML content; must not be null
     * @return decision index JSON string; never null
     */
    public String parseDecisionIndexYaml(String yamlContent) {
        return call("parse_decision_index_yaml", yamlContent);
    }

    /**
     * Exports a decision index to YAML format.
     *
     * @param indexJson  decision index JSON string; must not be null
     * @return YAML string; never null
     */
    public String exportDecisionIndexToYaml(String indexJson) {
        return call("export_decision_index_to_yaml", indexJson);
    }

    /**
     * Adds a decision record to a decision index.
     *
     * @param indexJson    decision index JSON string; must not be null
     * @param decisionJson decision JSON string; must not be null
     * @param filename     the YAML filename for the decision; must not be null
     * @return updated decision index JSON string; never null
     */
    public String addDecisionToIndex(String indexJson, String decisionJson, String filename) {
        return call("add_decision_to_index", indexJson, decisionJson, filename);
    }

    // ── Knowledge base (KB) ───────────────────────────────────────────────────

    /**
     * Creates a new knowledge base article.
     *
     * @param number   sequential article number; must be &gt; 0
     * @param title    article title; must not be null
     * @param summary  article summary; must not be null
     * @param content  article Markdown content; must not be null
     * @param author   the article author; must not be null
     * @return article JSON string; never null
     */
    public String createKnowledgeArticle(int number, String title, String summary,
                                          String content, String author) {
        return call("create_knowledge_article",
                String.valueOf(number), title, summary, content, author);
    }

    /**
     * Creates a new empty knowledge index.
     *
     * @return knowledge index JSON string; never null
     */
    public String createKnowledgeIndex() {
        return call("create_knowledge_index");
    }

    /**
     * Parses a knowledge article YAML file.
     *
     * @param yamlContent  knowledge article YAML content; must not be null
     * @return article JSON string; never null
     */
    public String parseKnowledgeYaml(String yamlContent) {
        return call("parse_knowledge_yaml", yamlContent);
    }

    /**
     * Exports a knowledge article to YAML format.
     *
     * @param articleJson  article JSON string; must not be null
     * @return YAML string; never null
     */
    public String exportKnowledgeToYaml(String articleJson) {
        return call("export_knowledge_to_yaml", articleJson);
    }

    /**
     * Exports a knowledge article to Markdown.
     *
     * @param articleJson  article JSON string; must not be null
     * @return Markdown string; never null
     */
    public String exportKnowledgeToMarkdown(String articleJson) {
        return call("export_knowledge_to_markdown", articleJson);
    }

    /**
     * Parses a knowledge index YAML file.
     *
     * @param yamlContent  knowledge index YAML content; must not be null
     * @return knowledge index JSON string; never null
     */
    public String parseKnowledgeIndexYaml(String yamlContent) {
        return call("parse_knowledge_index_yaml", yamlContent);
    }

    /**
     * Exports a knowledge index to YAML format.
     *
     * @param indexJson  knowledge index JSON string; must not be null
     * @return YAML string; never null
     */
    public String exportKnowledgeIndexToYaml(String indexJson) {
        return call("export_knowledge_index_to_yaml", indexJson);
    }

    /**
     * Adds an article to a knowledge index.
     *
     * @param indexJson   knowledge index JSON string; must not be null
     * @param articleJson article JSON string; must not be null
     * @param filename    the YAML filename for the article; must not be null
     * @return updated knowledge index JSON string; never null
     */
    public String addArticleToKnowledgeIndex(String indexJson, String articleJson, String filename) {
        return call("add_article_to_knowledge_index", indexJson, articleJson, filename);
    }

    /**
     * Searches knowledge articles by full-text query.
     *
     * @param articlesJson  JSON array of articles; must not be null
     * @param query         search query string; must not be null
     * @return JSON array of matching articles; never null
     */
    public String searchKnowledgeArticles(String articlesJson, String query) {
        return call("search_knowledge_articles", articlesJson, query);
    }

    // ── Sketch operations ─────────────────────────────────────────────────────

    /**
     * Creates a new Excalidraw sketch with metadata.
     *
     * @param number         sequential sketch number; must be &gt; 0
     * @param title          sketch title; must not be null
     * @param sketchType     sketch type (e.g., {@code "architecture"}, {@code "workflow"})
     * @param excalidrawData Excalidraw JSON data; must not be null
     * @return sketch JSON string; never null
     */
    public String createSketch(int number, String title, String sketchType, String excalidrawData) {
        return call("create_sketch",
                String.valueOf(number), title, sketchType, excalidrawData);
    }

    /**
     * Creates a new empty sketch index.
     *
     * @return sketch index JSON string; never null
     */
    public String createSketchIndex() {
        return call("create_sketch_index");
    }

    /**
     * Parses a sketch YAML file.
     *
     * @param yamlContent  sketch YAML content; must not be null
     * @return sketch JSON string; never null
     */
    public String parseSketchYaml(String yamlContent) {
        return call("parse_sketch_yaml", yamlContent);
    }

    /**
     * Exports a sketch to YAML format.
     *
     * @param sketchJson  sketch JSON string; must not be null
     * @return YAML string; never null
     */
    public String exportSketchToYaml(String sketchJson) {
        return call("export_sketch_to_yaml", sketchJson);
    }

    /**
     * Parses a sketch index YAML file.
     *
     * @param yamlContent  sketch index YAML content; must not be null
     * @return sketch index JSON string; never null
     */
    public String parseSketchIndexYaml(String yamlContent) {
        return call("parse_sketch_index_yaml", yamlContent);
    }

    /**
     * Adds a sketch to a sketch index.
     *
     * @param indexJson  sketch index JSON string; must not be null
     * @param sketchJson sketch JSON string; must not be null
     * @param filename   the YAML filename for the sketch; must not be null
     * @return updated sketch index JSON string; never null
     */
    public String addSketchToIndex(String indexJson, String sketchJson, String filename) {
        return call("add_sketch_to_index", indexJson, sketchJson, filename);
    }

    /**
     * Searches sketches by title, description, or tags.
     *
     * @param sketchesJson  JSON array of sketches; must not be null
     * @param query         search query string; must not be null
     * @return JSON array of matching sketches; never null
     */
    public String searchSketches(String sketchesJson, String query) {
        return call("search_sketches", sketchesJson, query);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Validates ODPS YAML content against the ODPS JSON Schema.
     *
     * @param yamlContent  ODPS YAML content; must not be null
     * @throws DataModellingException  EXECUTION_ERROR if validation fails
     */
    public void validateOdps(String yamlContent) {
        call("validate_odps", yamlContent);
    }

    /**
     * Validates a table name against ODCS naming conventions.
     *
     * @param name  the table name to validate; must not be null
     * @return validation result JSON string; never null
     */
    public String validateTableName(String name) {
        return call("validate_table_name", name);
    }

    /**
     * Validates a column name against ODCS naming conventions.
     *
     * @param name  the column name to validate; must not be null
     * @return validation result JSON string; never null
     */
    public String validateColumnName(String name) {
        return call("validate_column_name", name);
    }

    /**
     * Validates a data type string.
     *
     * @param dataType  the data type to validate; must not be null
     * @return validation result JSON string; never null
     */
    public String validateDataType(String dataType) {
        return call("validate_data_type", dataType);
    }

    /**
     * Checks whether adding a relationship would create a circular dependency.
     *
     * @param relationshipsJson  JSON array of existing relationships; must not be null
     * @param sourceTableId      source table ID; must not be null
     * @param targetTableId      target table ID; must not be null
     * @return {@code "true"} or {@code "false"} JSON string; never null
     */
    public String checkCircularDependency(String relationshipsJson,
                                           String sourceTableId, String targetTableId) {
        return call("check_circular_dependency", relationshipsJson, sourceTableId, targetTableId);
    }

    /**
     * Detects naming conflicts between existing and new tables.
     *
     * @param existingTablesJson  JSON array of existing tables; must not be null
     * @param newTablesJson       JSON array of new tables to check; must not be null
     * @return JSON array of conflict descriptions; never null
     */
    public String detectNamingConflicts(String existingTablesJson, String newTablesJson) {
        return call("detect_naming_conflicts", existingTablesJson, newTablesJson);
    }

    // ── Pipeline Integration (Phase 2) ───────────────────────────────────────

    /**
     * Infers schema from JSON data with automatic type detection and constraint analysis.
     *
     * <p>Analyzes JSON data structure to determine column types, primary keys, foreign keys,
     * and constraints. Returns inferred schema as JSON compatible with Phase 1 models.</p>
     *
     * @param jsonData           JSON array or object content; must not be null
     * @param config             inference configuration; must not be null
     * @return inferred schema as JSON string; never null
     * @throws DataModellingException  EXECUTION_ERROR if inference fails
     * @throws UnsupportedOperationException if WASM SDK does not expose schema inference
     */
    public String inferSchemaFromJson(String jsonData, org.yawlfoundation.yawl.datamodelling.pipeline.InferenceConfig config) {
        throw new UnsupportedOperationException(
                "Schema inference from JSON requires WASM SDK support. "
                + "The data-modelling-sdk v2.3.0 includes schema inference module, but it is not yet exposed. "
                + "Implement via WASM export when SDK adds infer_schema_from_json() function. "
                + "Expected: JSON with fields array, detected primaryKey, foreignKeys, constraints, and confidence score.");
    }

    /**
     * Maps source schema fields to target schema with fuzzy/LLM matching.
     *
     * <p>Analyzes field names, types, and semantic meaning to generate field mappings
     * from source to target schema with confidence scores.</p>
     *
     * @param sourceSchema       source schema JSON; must not be null
     * @param targetSchema       target schema JSON; must not be null
     * @param config             mapping configuration; must not be null
     * @return mapping result JSON with fieldMappings array; never null
     * @throws DataModellingException  EXECUTION_ERROR if mapping fails
     * @throws UnsupportedOperationException if WASM SDK does not expose schema mapping
     */
    public String mapSchemas(String sourceSchema, String targetSchema,
                            org.yawlfoundation.yawl.datamodelling.pipeline.MappingConfig config) {
        throw new UnsupportedOperationException(
                "Schema field mapping requires WASM SDK support. "
                + "The data-modelling-sdk v2.3.0 includes mapping module, but it is not yet exposed. "
                + "Implement via WASM export when SDK adds map_schemas() function. "
                + "Expected: JSON with fieldMappings (sourceField, targetField, confidence), "
                + "transformationScript (SQL/JQ/Python/PySpark), and mappingCompleteness score.");
    }

    /**
     * Generates transformation script from field mappings.
     *
     * <p>Synthesizes SQL, JQ, Python, or PySpark transformation code based on field mappings
     * and type conversions.</p>
     *
     * @param mappingResultJson  mapping result JSON from mapSchemas(); must not be null
     * @param format             output format: "sql", "jq", "python", "pyspark"; must not be null
     * @return transformation script as string; never null
     * @throws DataModellingException  EXECUTION_ERROR if generation fails
     * @throws UnsupportedOperationException if WASM SDK does not expose transformation generation
     */
    public String generateTransform(String mappingResultJson, String format) {
        throw new UnsupportedOperationException(
                "Transformation script generation requires WASM SDK support. "
                + "The data-modelling-sdk v2.3.0 includes transformation generation, but it is not yet exposed. "
                + "Implement via WASM export when SDK adds generate_transform() function. "
                + "Expected: String with executable " + format + " transformation script.");
    }

    // ── LLM Integration (Phase 3) ─────────────────────────────────────────────

    /**
     * Refines a schema using LLM in offline mode (llama.cpp or compatible local inference).
     *
     * <p>This method delegates to the data-modelling-sdk's offline LLM refinement logic,
     * which runs a local LLM without external API calls. Useful for privacy-sensitive
     * or air-gapped environments.</p>
     *
     * @param schema              the schema to refine (JSON or YAML); must not be null
     * @param samples             sample data records for context; may be empty
     * @param objectives          refinement objectives; may be empty
     * @param context             optional documentation context; may be null
     * @param config              LLM configuration; must not be null
     * @return refined schema JSON; never null
     * @throws DataModellingException  EXECUTION_ERROR if offline LLM unavailable or refinement fails
     */
    public String refineSchemaWithLlmOffline(String schema, String[] samples,
                                              String[] objectives, String context,
                                              org.yawlfoundation.yawl.datamodelling.llm.LlmConfig config) {
        return call("refine_schema_with_llm_offline",
                schema,
                stringArrayToJson(samples),
                stringArrayToJson(objectives),
                context != null ? context : "",
                config.getModel(),
                String.valueOf(config.getTemperature()),
                String.valueOf(config.getMaxTokens()));
    }

    /**
     * Refines a schema using LLM in online mode (Ollama HTTP API).
     *
     * <p>This method delegates to the data-modelling-sdk's online LLM refinement logic,
     * which calls the Ollama API endpoint specified in the configuration.</p>
     *
     * @param schema              the schema to refine (JSON or YAML); must not be null
     * @param samples             sample data records for context; may be empty
     * @param objectives          refinement objectives; may be empty
     * @param context             optional documentation context; may be null
     * @param config              LLM configuration; must not be null
     * @return refined schema JSON; never null
     * @throws DataModellingException  EXECUTION_ERROR if online LLM unavailable or refinement fails
     */
    public String refineSchemaWithLlmOnline(String schema, String[] samples,
                                             String[] objectives, String context,
                                             org.yawlfoundation.yawl.datamodelling.llm.LlmConfig config) {
        return call("refine_schema_with_llm_online",
                schema,
                stringArrayToJson(samples),
                stringArrayToJson(objectives),
                context != null ? context : "",
                config.getModel(),
                config.getBaseUrl(),
                String.valueOf(config.getTemperature()),
                String.valueOf(config.getMaxTokens()),
                String.valueOf(config.getTimeoutSeconds()));
    }

    /**
     * Matches fields between source and target schemas using LLM analysis.
     *
     * <p>Returns a JSON object mapping source fields to target fields with
     * confidence scores based on semantic understanding.</p>
     *
     * @param sourceSchema  the source schema JSON; must not be null
     * @param targetSchema  the target schema JSON; must not be null
     * @param config        LLM configuration; must not be null
     * @return JSON object with field mappings and scores; never null
     * @throws DataModellingException  EXECUTION_ERROR if LLM unavailable or matching fails
     */
    public String matchFieldsWithLlm(String sourceSchema, String targetSchema,
                                      org.yawlfoundation.yawl.datamodelling.llm.LlmConfig config) {
        return call("match_fields_with_llm",
                sourceSchema,
                targetSchema,
                config.getModel(),
                String.valueOf(config.getTemperature()),
                config.getBaseUrl());
    }

    /**
     * Enriches schema documentation using LLM analysis.
     *
     * <p>Generates meaningful descriptions for tables, columns, and relationships
     * based on naming patterns and data characteristics.</p>
     *
     * @param schema  the schema to enrich; must not be null
     * @param config  LLM configuration; must not be null
     * @return enriched schema with documentation; never null
     * @throws DataModellingException  EXECUTION_ERROR if enrichment fails
     */
    public String enrichDocumentationWithLlm(String schema,
                                              org.yawlfoundation.yawl.datamodelling.llm.LlmConfig config) {
        return call("enrich_documentation_with_llm",
                schema,
                config.getModel(),
                String.valueOf(config.getTemperature()),
                config.getBaseUrl());
    }

    /**
     * Detects semantic patterns in a schema using LLM.
     *
     * <p>Identifies patterns such as PII fields, temporal fields, categorical data,
     * and other domain-specific patterns that may have governance implications.</p>
     *
     * @param schema  the schema to analyze; must not be null
     * @param config  LLM configuration; must not be null
     * @return JSON object with detected patterns; never null
     * @throws DataModellingException  EXECUTION_ERROR if pattern detection fails
     */
    public String detectPatternsWithLlm(String schema,
                                         org.yawlfoundation.yawl.datamodelling.llm.LlmConfig config) {
        return call("detect_patterns_with_llm",
                schema,
                config.getModel(),
                String.valueOf(config.getTemperature()),
                config.getBaseUrl());
    }

    /**
     * Checks whether the LLM service is available and responsive.
     *
     * <p>Performs a health check against either the offline or online LLM endpoint,
     * depending on configuration.</p>
     *
     * @param config  LLM configuration; must not be null
     * @return {@code "true"} or {@code "false"} as JSON string; never null
     */
    public String checkLlmAvailability(org.yawlfoundation.yawl.datamodelling.llm.LlmConfig config) {
        try {
            String result = call("check_llm_availability",
                    config.getMode().getValue(),
                    config.getBaseUrl(),
                    String.valueOf(config.getTimeoutSeconds()));
            return result;
        } catch (Exception e) {
            log.warn("LLM availability check failed: {}", e.getMessage());
            return "false";
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Closes the bridge, releasing all JS contexts and temp directory resources.
     *
     * <p>Idempotent: subsequent calls are no-ops.</p>
     */
    @Override
    public void close() {
        jsEngine.close();
        deleteTempDir(tempDir);
        log.info("DataModellingBridge closed");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Calls a WASM-exported function by name with zero or more string arguments.
     *
     * @param functionName  the WASM export name; must not be null
     * @param args          string arguments to pass; each converted to JS string
     * @return the function's return value as a Java String; never null
     * @throws DataModellingException  EXECUTION_ERROR if the function traps
     */
    private String call(String functionName, String... args) {
        return jsEngine.getContextPool().execute(ctx -> {
            StringBuilder js = new StringBuilder(functionName).append('(');
            for (int i = 0; i < args.length; i++) {
                String paramName = "__dm_arg" + i + "__";
                ctx.getRawContext().getBindings("js").putMember(paramName, args[i]);
                if (i > 0) js.append(',');
                js.append(paramName);
            }
            js.append(')');
            try {
                Value result = ctx.getRawContext().eval("js", js.toString());
                return result.isNull() ? "" : result.asString();
            } catch (Exception e) {
                throw new DataModellingException(
                        "WASM function '" + functionName + "' failed: " + e.getMessage(),
                        DataModellingException.ErrorKind.EXECUTION_ERROR, e);
            } finally {
                for (int i = 0; i < args.length; i++) {
                    ctx.getRawContext().getBindings("js").removeMember("__dm_arg" + i + "__");
                }
            }
        });
    }

    /**
     * Initialises the WASM module in a single JavaScript context via initSync.
     */
    // Polyfills for Web APIs used by wasm-bindgen glue (not built into GraalJS by default).
    // TextDecoder/TextEncoder: pure-JS UTF-8 codec. crypto.getRandomValues: CSPRNG for UUID.
    private static final String WEB_API_POLYFILLS = """
            if (typeof TextDecoder === 'undefined') {
                globalThis.TextDecoder = class TextDecoder {
                    constructor(enc, opts) { this.encoding = enc||'utf-8'; this.fatal=(opts&&opts.fatal)||false; }
                    decode(buf) {
                        if (!buf) return '';
                        const b = (buf instanceof Uint8Array) ? buf
                            : new Uint8Array(buf.buffer||buf, buf.byteOffset||0, buf.byteLength||buf.length);
                        let s='', i=0;
                        while(i<b.length){
                            const c=b[i++];
                            if(c<0x80){s+=String.fromCharCode(c);}
                            else if((c&0xE0)===0xC0){s+=String.fromCharCode(((c&0x1F)<<6)|(b[i++]&0x3F));}
                            else if((c&0xF0)===0xE0){s+=String.fromCharCode(((c&0x0F)<<12)|((b[i++]&0x3F)<<6)|(b[i++]&0x3F));}
                            else{const d=b[i++],e=b[i++],f=b[i++];const p=((c&7)<<18)|((d&0x3F)<<12)|((e&0x3F)<<6)|(f&0x3F);s+=String.fromCharCode(0xD800+((p-0x10000)>>10),0xDC00+((p-0x10000)&0x3FF));}
                        }
                        return s;
                    }
                };
            }
            if (typeof TextEncoder === 'undefined') {
                globalThis.TextEncoder = class TextEncoder {
                    constructor(){this.encoding='utf-8';}
                    encode(s){
                        const o=[];
                        for(let i=0;i<s.length;i++){
                            const c=s.charCodeAt(i);
                            if(c<0x80)o.push(c);
                            else if(c<0x800){o.push(0xC0|(c>>6));o.push(0x80|(c&0x3F));}
                            else if(c<0xD800||c>0xDFFF){o.push(0xE0|(c>>12));o.push(0x80|((c>>6)&0x3F));o.push(0x80|(c&0x3F));}
                            else{const n=s.charCodeAt(++i),p=0x10000+(((c&0x3FF)<<10)|(n&0x3FF));o.push(0xF0|(p>>18),0x80|((p>>12)&0x3F),0x80|((p>>6)&0x3F),0x80|(p&0x3F));}
                        }
                        return new Uint8Array(o);
                    }
                    encodeInto(s,d){const e=this.encode(s),n=Math.min(e.length,d.length);d.set(e.subarray(0,n));return{read:s.length,written:n};}
                };
            }
            if (typeof globalThis.crypto === 'undefined') { globalThis.crypto = {}; }
            if (typeof globalThis.crypto.getRandomValues === 'undefined') {
                globalThis.crypto.getRandomValues = function(arr) {
                    for (let i=0;i<arr.length;i++) arr[i]=Math.floor(Math.random()*256);
                    return arr;
                };
            }
            """;

    private void initWasmInContext(JavaScriptExecutionContext ctx, Path gluePath, Path wasmPath) {
        try {
            // Inject Web API polyfills needed by wasm-bindgen glue (TextDecoder, TextEncoder, crypto)
            ctx.getRawContext().eval("js", WEB_API_POLYFILLS);

            Source glueSource = Source.newBuilder("js", gluePath.toFile())
                    .mimeType("application/javascript+module")
                    .build();
            Value moduleExports = ctx.getRawContext().eval(glueSource);

            Value initSync = moduleExports.getMember("initSync");
            if (initSync == null || initSync.isNull()) {
                throw new DataModellingException(
                        "data_modelling_wasm.js does not export 'initSync'. "
                        + "Ensure the correct wasm-bindgen ES module is present.",
                        DataModellingException.ErrorKind.MODULE_LOAD_ERROR);
            }

            byte[] wasmBytes = Files.readAllBytes(wasmPath);
            ctx.getRawContext().getBindings("js").putMember("__wasmBytes__", wasmBytes);
            ctx.getRawContext().eval("js",
                    "const __wasmMod__ = new WebAssembly.Module(__wasmBytes__); "
                    + "initSync(__wasmMod__); "
                    + "delete __wasmBytes__; delete __wasmMod__;");

            log.debug("DataModellingBridge WASM initialised in context");
        } catch (IOException e) {
            throw new DataModellingException(
                    "Failed to read WASM file from temp directory: " + e.getMessage(),
                    DataModellingException.ErrorKind.MODULE_LOAD_ERROR, e);
        }
    }

    /**
     * Extracts WASM binary and JS glue to a temp directory.
     */
    private Path extractResourcesToTemp() {
        try {
            Path dir = Files.createTempDirectory("yawl-data-modelling-");
            extractResource(WASM_RESOURCE, dir.resolve("data_modelling_wasm_bg.wasm"));
            extractResource(GLUE_RESOURCE, dir.resolve("data_modelling_wasm.js"));
            log.debug("DataModellingBridge resources extracted to: {}", dir);
            return dir;
        } catch (IOException e) {
            throw new DataModellingException(
                    "Cannot create temp directory for WASM resources: " + e.getMessage(),
                    DataModellingException.ErrorKind.MODULE_LOAD_ERROR, e);
        }
    }

    // ── Phase 5: Advanced Filtering & Querying ───────────────────────────────

    /**
     * Create a fluent query builder for the given workspace JSON.
     *
     * <p>This method parses the workspace JSON and returns a DataModellingQueryBuilder
     * instance for performing advanced filtering, relationship analysis, and lineage queries.</p>
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @return a query builder for the workspace; never null
     * @throws DataModellingException  if workspace JSON cannot be parsed
     * @see org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder
     */
    public org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder
            queryBuilder(String workspaceJson) {
        org.yawlfoundation.yawl.datamodelling.converters.WorkspaceConverter converter =
                new org.yawlfoundation.yawl.datamodelling.converters.WorkspaceConverter();
        org.yawlfoundation.yawl.datamodelling.models.DataModellingWorkspace workspace =
                converter.fromJson(workspaceJson);
        return org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder
                .forWorkspace(workspace);
    }

    /**
     * Filter tables by owner from workspace JSON.
     *
     * <p>Returns JSON array of tables matching the specified owner.</p>
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @param owner          owner name/email; must not be null
     * @return JSON array of tables; never null (empty array if no matches)
     */
    public String filterTablesByOwner(String workspaceJson, String owner) {
        org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder builder =
                queryBuilder(workspaceJson);
        java.util.List<org.yawlfoundation.yawl.datamodelling.models.DataModellingTable> tables =
                builder.filterTablesByOwner(owner).getTables();
        return org.yawlfoundation.yawl.datamodelling.converters.JsonObjectMapper
                .toJson(tables);
    }

    /**
     * Filter tables by tag from workspace JSON.
     *
     * <p>Returns JSON array of tables that have the specified tag.</p>
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @param tag            tag value; must not be null
     * @return JSON array of tables; never null (empty array if no matches)
     */
    public String filterTablesByTag(String workspaceJson, String tag) {
        org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder builder =
                queryBuilder(workspaceJson);
        java.util.List<org.yawlfoundation.yawl.datamodelling.models.DataModellingTable> tables =
                builder.filterTablesByTag(tag).getTables();
        return org.yawlfoundation.yawl.datamodelling.converters.JsonObjectMapper
                .toJson(tables);
    }

    /**
     * Filter tables by infrastructure type from workspace JSON.
     *
     * <p>Returns JSON array of tables using the specified infrastructure.</p>
     *
     * @param workspaceJson       workspace JSON string; must not be null
     * @param infrastructureType  infrastructure type (e.g. "postgresql", "warehouse"); must not be null
     * @return JSON array of tables; never null (empty array if no matches)
     */
    public String filterTablesByInfrastructure(String workspaceJson, String infrastructureType) {
        org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder builder =
                queryBuilder(workspaceJson);
        java.util.List<org.yawlfoundation.yawl.datamodelling.models.DataModellingTable> tables =
                builder.filterTablesByInfrastructureType(infrastructureType).getTables();
        return org.yawlfoundation.yawl.datamodelling.converters.JsonObjectMapper
                .toJson(tables);
    }

    /**
     * Filter tables by medallion layer from workspace JSON.
     *
     * <p>Returns JSON array of tables in the specified medallion layer.</p>
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @param layer          medallion layer (e.g. "bronze", "silver", "gold"); must not be null
     * @return JSON array of tables; never null (empty array if no matches)
     */
    public String filterTablesByMedallionLayer(String workspaceJson, String layer) {
        org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder builder =
                queryBuilder(workspaceJson);
        java.util.List<org.yawlfoundation.yawl.datamodelling.models.DataModellingTable> tables =
                builder.filterTablesByMedallionLayer(layer).getTables();
        return org.yawlfoundation.yawl.datamodelling.converters.JsonObjectMapper
                .toJson(tables);
    }

    /**
     * Get relationships for a specific table from workspace JSON.
     *
     * <p>Returns JSON array of relationships involving the specified table.</p>
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @param tableId        table ID; must not be null
     * @param relationshipType  filter by relationship type (e.g. "dataFlow", "dependency");
     *                          if null, returns all relationships
     * @return JSON array of relationships; never null (empty array if no relationships)
     */
    public String queryTableRelationships(String workspaceJson, String tableId,
                                          @Nullable String relationshipType) {
        org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder builder =
                queryBuilder(workspaceJson);
        java.util.List<org.yawlfoundation.yawl.datamodelling.models.DataModellingRelationship> rels =
                builder.getRelationshipsForTable(tableId);

        // Filter by type if specified
        if (relationshipType != null) {
            rels = rels.stream()
                    .filter(r -> relationshipType.equals(r.getRelationshipType()))
                    .toList();
        }

        return org.yawlfoundation.yawl.datamodelling.converters.JsonObjectMapper
                .toJson(rels);
    }

    /**
     * Get impact analysis for a table from workspace JSON.
     *
     * <p>Returns JSON array of tables that would be impacted if the specified table changes.</p>
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @param tableId        table ID; must not be null
     * @return JSON array of impacted tables; never null (empty array if no impacts)
     */
    public String getImpactAnalysis(String workspaceJson, String tableId) {
        org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder builder =
                queryBuilder(workspaceJson);
        java.util.List<org.yawlfoundation.yawl.datamodelling.models.DataModellingTable> impacted =
                builder.getImpactAnalysis(tableId);
        return org.yawlfoundation.yawl.datamodelling.converters.JsonObjectMapper
                .toJson(impacted);
    }

    /**
     * Get data lineage report for a table from workspace JSON.
     *
     * <p>Returns a JSON object containing upstream dependencies and downstream dependents.</p>
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @param tableId        table ID; must not be null
     * @return JSON object with lineage information; never null
     */
    public String getDataLineageReport(String workspaceJson, String tableId) {
        org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder builder =
                queryBuilder(workspaceJson);
        java.util.Map<String, Object> lineage = builder.getDataLineageReport(tableId);
        return org.yawlfoundation.yawl.datamodelling.converters.JsonObjectMapper
                .toJson(lineage);
    }

    /**
     * Check if workspace has circular dependencies.
     *
     * <p>Returns "true" or "false" JSON value.</p>
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @return "true" if cycles detected, "false" otherwise
     */
    public String hasCyclicDependencies(String workspaceJson) {
        org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder builder =
                queryBuilder(workspaceJson);
        boolean hasCycles = builder.hasCyclicDependencies();
        return String.valueOf(hasCycles);
    }

    /**
     * Detect cycle path in workspace if one exists.
     *
     * <p>Returns JSON array of table IDs forming the cycle, or empty array if no cycle.</p>
     *
     * @param workspaceJson  workspace JSON string; must not be null
     * @return JSON array of table IDs in cycle path; never null (empty if no cycle)
     */
    public String detectCyclePath(String workspaceJson) {
        org.yawlfoundation.yawl.datamodelling.queries.DataModellingQueryBuilder builder =
                queryBuilder(workspaceJson);
        java.util.List<String> cycle = builder.detectCyclePath();
        return org.yawlfoundation.yawl.datamodelling.converters.JsonObjectMapper
                .toJson(cycle);
    }

    /**
     * Extracts a single classpath resource to a target file path.
     */
    private void extractResource(String resourcePath, Path targetPath) throws IOException {
        try (InputStream is = DataModellingBridge.class.getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new DataModellingException(
                        "Required classpath resource not found: " + resourcePath
                        + ". Ensure yawl-data-modelling JAR contains wasm/ resources.",
                        DataModellingException.ErrorKind.MODULE_LOAD_ERROR);
            }
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Recursively deletes a temp directory and its contents.
     */
    private void deleteTempDir(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                            // Best effort
                        }
                    });
        } catch (IOException e) {
            log.warn("Cannot clean up WASM temp directory {}: {}", dir, e.getMessage());
        }
    }

    /**
     * Converts a string array to JSON array representation.
     *
     * <p>Escapes special characters and wraps each element as a JSON string.</p>
     *
     * @param array the array to convert; may be null
     * @return JSON array string; never null (returns "[]" if array is null or empty)
     */
    private String stringArrayToJson(String[] array) {
        if (array == null || array.length == 0) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) json.append(",");
            json.append('"')
                    .append(escapeJsonString(array[i]))
                    .append('"');
        }
        json.append("]");
        return json.toString();
    }

    /**
     * Escapes a string for use as a JSON string value.
     *
     * <p>Handles quotes, backslashes, control characters, etc.</p>
     *
     * @param value the string to escape; must not be null
     * @return escaped string; never null
     * @throws NullPointerException if value is null
     */
    private String escapeJsonString(String value) {
        Objects.requireNonNull(value, "value must not be null");
        StringBuilder escaped = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 32 || c >= 127) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
