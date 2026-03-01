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

package org.yawlfoundation.yawl.datamodelling.bridge;

import org.yawlfoundation.yawl.datamodelling.Capability;
import org.yawlfoundation.yawl.datamodelling.DataModellingException;
import org.yawlfoundation.yawl.datamodelling.MapsToCapability;
import org.yawlfoundation.yawl.datamodelling.OdpsValidationException;
import org.yawlfoundation.yawl.datamodelling.generated.DmResult_h;
import org.yawlfoundation.yawl.datamodelling.generated.DmVoidResult_h;
import org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

import static org.yawlfoundation.yawl.datamodelling.Capability.*;

/**
 * Layer 2 bridge to libdata_modelling_ffi.so.
 *
 * <p>Thread-safe: uses {@link Arena#ofShared()} for the bridge lifetime arena.
 * Multiple threads may call any method concurrently.
 *
 * <p>Usage:
 * <pre>{@code
 * try (DataModellingBridge bridge = new DataModellingBridge()) {
 *     String json = bridge.parseOdcsYaml("apiVersion: v3.1.0\n...");
 * }
 * }</pre>
 *
 * <p>If the native library is not loaded, all methods throw
 * {@link UnsupportedOperationException} with actionable guidance.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DataModellingBridge implements AutoCloseable {

    private final Arena arena = Arena.ofShared();

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Unwrap a DmResult struct, freeing both pointers and returning data or throwing.
     *
     * @param result the memory segment containing DmResult
     * @return the data string on success
     * @throws DataModellingException if error pointer is non-null
     */
    private String unwrapResult(MemorySegment result) {
        MemorySegment errorPtr = DmResult_h.error$get(result);
        if (!MemorySegment.NULL.equals(errorPtr)) {
            String msg = errorPtr.reinterpret(Long.MAX_VALUE)
                                 .getString(0, StandardCharsets.UTF_8);
            data_modelling_ffi_h.dm_string_free(errorPtr);
            throw new DataModellingException(msg, DataModellingException.ErrorKind.EXECUTION_ERROR);
        }
        MemorySegment dataPtr = DmResult_h.data$get(result);
        try {
            return dataPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
        } finally {
            data_modelling_ffi_h.dm_string_free(dataPtr);
        }
    }

    /**
     * Unwrap a DmVoidResult struct, freeing error pointer and throwing if non-null.
     *
     * @param result the memory segment containing DmVoidResult
     * @throws OdpsValidationException if error pointer is non-null
     */
    private void unwrapVoidResult(MemorySegment result) {
        MemorySegment errorPtr = DmVoidResult_h.error$get(result);
        if (!MemorySegment.NULL.equals(errorPtr)) {
            String msg = errorPtr.reinterpret(Long.MAX_VALUE)
                                 .getString(0, StandardCharsets.UTF_8);
            data_modelling_ffi_h.dm_string_free(errorPtr);
            throw new OdpsValidationException(msg);
        }
    }

    /**
     * Allocate a required C string from the call-scoped arena.
     *
     * @param call the call-scoped arena
     * @param s the string to allocate (must not be null)
     * @return memory segment containing null-terminated C string
     */
    private static MemorySegment cstrCall(Arena call, String s) {
        return call.allocateFrom(s, StandardCharsets.UTF_8);
    }

    /**
     * Allocate an optional C string from the call-scoped arena.
     *
     * @param call the call-scoped arena
     * @param s the string to allocate (null returns MemorySegment.NULL)
     * @return memory segment containing null-terminated C string, or NULL if s is null
     */
    private static MemorySegment cstrOpt(Arena call, String s) {
        return s == null ? MemorySegment.NULL : call.allocateFrom(s, StandardCharsets.UTF_8);
    }

    // ── Group A: ODCS Core (3) ───────────────────────────────────────────────

    /**
     * Parse ODCS YAML into normalized JSON representation.
     *
     * @param yaml the ODCS YAML string
     * @return normalized JSON string
     * @throws DataModellingException on parse or validation error
     */
    @MapsToCapability(PARSE_ODCS_YAML)
    public String parseOdcsYaml(String yaml) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_parse_odcs_yaml(call, cstrCall(call, yaml)));
        }
    }

    /**
     * Export normalized JSON to ODCS YAML format.
     *
     * @param json the ODCS JSON representation
     * @return YAML string
     * @throws DataModellingException on serialization error
     */
    @MapsToCapability(EXPORT_TO_ODCS_YAML)
    public String exportToOdcsYaml(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_to_odcs_yaml(call, cstrCall(call, json)));
        }
    }

    /**
     * Convert between schema formats with ODCS as target.
     *
     * @param json the source schema JSON
     * @param sourceFormat the source format name (e.g., "avro", "sql")
     * @return ODCS JSON representation
     * @throws DataModellingException on conversion error
     */
    @MapsToCapability(CONVERT_TO_ODCS)
    public String convertToOdcs(String json, String sourceFormat) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_convert_to_odcs(call, cstrCall(call, json), cstrCall(call, sourceFormat)));
        }
    }

    // ── Group B: SQL (2) ────────────────────────────────────────────────────

    /**
     * Import SQL DDL schema into ODCS JSON representation.
     *
     * @param sql the SQL DDL string
     * @param dialect the SQL dialect (e.g., "postgresql", "mysql")
     * @return ODCS JSON representation
     * @throws DataModellingException on parsing or conversion error
     */
    @MapsToCapability(Capability.IMPORT_FROM_SQL)
    public String importFromSql(String sql, String dialect) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_import_from_sql(call, cstrCall(call, sql), cstrCall(call, dialect)));
        }
    }

    /**
     * Export ODCS JSON to SQL DDL for target dialect.
     *
     * @param json the ODCS JSON representation
     * @param dialect the target SQL dialect
     * @return SQL DDL string
     * @throws DataModellingException on serialization or conversion error
     */
    @MapsToCapability(Capability.EXPORT_TO_SQL)
    public String exportToSql(String json, String dialect) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_to_sql(call, cstrCall(call, json), cstrCall(call, dialect)));
        }
    }

    // ── Group C: Schema Format Import (5) ───────────────────────────────────

    /**
     * Import Apache Avro schema into ODCS JSON.
     *
     * @param schema the Avro schema string
     * @return ODCS JSON representation
     * @throws DataModellingException on parsing or conversion error
     */
    @MapsToCapability(Capability.IMPORT_FROM_AVRO)
    public String importFromAvro(String schema) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_import_from_avro(call, cstrCall(call, schema)));
        }
    }

    /**
     * Import JSON Schema into ODCS JSON.
     *
     * @param schema the JSON Schema string
     * @return ODCS JSON representation
     * @throws DataModellingException on parsing or conversion error
     */
    @MapsToCapability(Capability.IMPORT_FROM_JSON_SCHEMA)
    public String importFromJsonSchema(String schema) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_import_from_json_schema(call, cstrCall(call, schema)));
        }
    }

    /**
     * Import Protocol Buffers schema into ODCS JSON.
     *
     * @param schema the Protobuf schema string
     * @return ODCS JSON representation
     * @throws DataModellingException on parsing or conversion error
     */
    @MapsToCapability(Capability.IMPORT_FROM_PROTOBUF)
    public String importFromProtobuf(String schema) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_import_from_protobuf(call, cstrCall(call, schema)));
        }
    }

    /**
     * Import CADS (Catalog Asset Description Schema) into ODCS JSON.
     *
     * @param json the CADS JSON representation
     * @return ODCS JSON representation
     * @throws DataModellingException on parsing or conversion error
     */
    @MapsToCapability(Capability.IMPORT_FROM_CADS)
    public String importFromCads(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_import_from_cads(call, cstrCall(call, json)));
        }
    }

    /**
     * Import ODPS (Open Data Policy Specification) YAML into ODCS JSON.
     *
     * @param yaml the ODPS YAML string
     * @return ODCS JSON representation
     * @throws DataModellingException on parsing or conversion error
     */
    @MapsToCapability(Capability.IMPORT_FROM_ODPS)
    public String importFromOdps(String yaml) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_import_from_odps(call, cstrCall(call, yaml)));
        }
    }

    // ── Group D: Schema Format Export (5) ───────────────────────────────────

    /**
     * Export ODCS JSON to Apache Avro schema.
     *
     * @param json the ODCS JSON representation
     * @return Avro schema string
     * @throws DataModellingException on serialization or conversion error
     */
    @MapsToCapability(Capability.EXPORT_TO_AVRO)
    public String exportToAvro(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_to_avro(call, cstrCall(call, json)));
        }
    }

    /**
     * Export ODCS JSON to JSON Schema.
     *
     * @param json the ODCS JSON representation
     * @return JSON Schema string
     * @throws DataModellingException on serialization or conversion error
     */
    @MapsToCapability(Capability.EXPORT_TO_JSON_SCHEMA)
    public String exportToJsonSchema(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_to_json_schema(call, cstrCall(call, json)));
        }
    }

    /**
     * Export ODCS JSON to Protocol Buffers.
     *
     * @param json the ODCS JSON representation
     * @return Protobuf schema string
     * @throws DataModellingException on serialization or conversion error
     */
    @MapsToCapability(Capability.EXPORT_TO_PROTOBUF)
    public String exportToProtobuf(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_to_protobuf(call, cstrCall(call, json)));
        }
    }

    /**
     * Export ODCS JSON to CADS (Catalog Asset Description Schema).
     *
     * @param json the ODCS JSON representation
     * @return CADS JSON representation
     * @throws DataModellingException on serialization or conversion error
     */
    @MapsToCapability(Capability.EXPORT_TO_CADS)
    public String exportToCads(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_to_cads(call, cstrCall(call, json)));
        }
    }

    /**
     * Export ODCS JSON to ODPS YAML.
     *
     * @param json the ODCS JSON representation
     * @return ODPS YAML string
     * @throws DataModellingException on serialization or conversion error
     */
    @MapsToCapability(Capability.EXPORT_TO_ODPS)
    public String exportToOdps(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_to_odps(call, cstrCall(call, json)));
        }
    }

    // ── Group E: ODPS Validation (1) ────────────────────────────────────────

    /**
     * Validate ODPS YAML against specification constraints.
     *
     * @param yaml the ODPS YAML string to validate
     * @throws OdpsValidationException if validation fails
     */
    @MapsToCapability(VALIDATE_ODPS)
    public void validateOdps(String yaml) {
        try (Arena call = Arena.ofConfined()) {
            unwrapVoidResult(data_modelling_ffi_h.dm_validate_odps(call, cstrCall(call, yaml)));
        }
    }

    // ── Group F: BPMN (2) ───────────────────────────────────────────────────

    /**
     * Import BPMN XML model into ODCS JSON representation.
     *
     * @param xml the BPMN XML string
     * @return ODCS JSON representation
     * @throws DataModellingException on parsing or conversion error
     */
    @MapsToCapability(Capability.IMPORT_BPMN_MODEL)
    public String importBpmnModel(String xml) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_import_bpmn_model(call, cstrCall(call, xml)));
        }
    }

    /**
     * Export ODCS JSON to BPMN XML format.
     *
     * @param json the ODCS JSON representation
     * @return BPMN XML string
     * @throws DataModellingException on serialization or conversion error
     */
    @MapsToCapability(Capability.EXPORT_BPMN_MODEL)
    public String exportBpmnModel(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_bpmn_model(call, cstrCall(call, json)));
        }
    }

    // ── Group G: DMN (2) ────────────────────────────────────────────────────

    /**
     * Import DMN (Decision Model and Notation) XML into ODCS JSON.
     *
     * @param xml the DMN XML string
     * @return ODCS JSON representation
     * @throws DataModellingException on parsing or conversion error
     */
    @MapsToCapability(Capability.IMPORT_DMN_MODEL)
    public String importDmnModel(String xml) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_import_dmn_model(call, cstrCall(call, xml)));
        }
    }

    /**
     * Export ODCS JSON to DMN XML format.
     *
     * @param json the ODCS JSON representation
     * @return DMN XML string
     * @throws DataModellingException on serialization or conversion error
     */
    @MapsToCapability(Capability.EXPORT_DMN_MODEL)
    public String exportDmnModel(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_dmn_model(call, cstrCall(call, json)));
        }
    }

    // ── Group H: OpenAPI (4) ────────────────────────────────────────────────

    /**
     * Import OpenAPI specification (YAML or JSON) into ODCS JSON.
     *
     * @param yamlOrJson the OpenAPI specification string (YAML or JSON)
     * @return ODCS JSON representation
     * @throws DataModellingException on parsing or conversion error
     */
    @MapsToCapability(Capability.IMPORT_OPENAPI_SPEC)
    public String importOpenapiSpec(String yamlOrJson) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_import_openapi_spec(call, cstrCall(call, yamlOrJson)));
        }
    }

    /**
     * Export ODCS JSON to OpenAPI specification format.
     *
     * @param json the ODCS JSON representation
     * @return OpenAPI specification string (JSON)
     * @throws DataModellingException on serialization or conversion error
     */
    @MapsToCapability(Capability.EXPORT_OPENAPI_SPEC)
    public String exportOpenapiSpec(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_openapi_spec(call, cstrCall(call, json)));
        }
    }

    /**
     * Convert OpenAPI specification to ODCS format.
     *
     * @param yamlOrJson the OpenAPI specification string (YAML or JSON)
     * @return ODCS JSON representation
     * @throws DataModellingException on parsing or conversion error
     */
    @MapsToCapability(Capability.CONVERT_OPENAPI_TO_ODCS)
    public String convertOpenapiToOdcs(String yamlOrJson) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_convert_openapi_to_odcs(call, cstrCall(call, yamlOrJson)));
        }
    }

    /**
     * Analyze OpenAPI spec for conversion feasibility and potential issues.
     *
     * @param yamlOrJson the OpenAPI specification string (YAML or JSON)
     * @return analysis report as JSON
     * @throws DataModellingException on analysis error
     */
    @MapsToCapability(Capability.ANALYZE_OPENAPI_CONVERSION)
    public String analyzeOpenapiConversion(String yamlOrJson) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_analyze_openapi_conversion(call, cstrCall(call, yamlOrJson)));
        }
    }

    // ── Group I: DataFlow (1) ────────────────────────────────────────────────

    /**
     * Migrate dataflow definition into domain organization model.
     *
     * @param json the dataflow definition JSON
     * @return domain model JSON
     * @throws DataModellingException on migration error
     */
    @MapsToCapability(Capability.MIGRATE_DATAFLOW_TO_DOMAIN)
    public String migrateDataflowToDomain(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_migrate_dataflow_to_domain(call, cstrCall(call, json)));
        }
    }

    // ── Group J: Sketch (8) ─────────────────────────────────────────────────

    /**
     * Parse sketch definition from YAML into JSON.
     *
     * @param yaml the sketch YAML definition
     * @return sketch JSON representation
     * @throws DataModellingException on parsing error
     */
    @MapsToCapability(Capability.PARSE_SKETCH_YAML)
    public String parseSketchYaml(String yaml) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_parse_sketch_yaml(call, cstrCall(call, yaml)));
        }
    }

    /**
     * Parse sketch index definition from YAML into JSON.
     *
     * @param yaml the sketch index YAML definition
     * @return sketch index JSON representation
     * @throws DataModellingException on parsing error
     */
    @MapsToCapability(Capability.PARSE_SKETCH_INDEX_YAML)
    public String parseSketchIndexYaml(String yaml) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_parse_sketch_index_yaml(call, cstrCall(call, yaml)));
        }
    }

    /**
     * Export sketch definition to YAML format.
     *
     * @param json the sketch JSON representation
     * @return sketch YAML definition
     * @throws DataModellingException on serialization error
     */
    @MapsToCapability(Capability.EXPORT_SKETCH_TO_YAML)
    public String exportSketchToYaml(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_sketch_to_yaml(call, cstrCall(call, json)));
        }
    }

    /**
     * Export sketch index definition to YAML format.
     *
     * @param json the sketch index JSON representation
     * @return sketch index YAML definition
     * @throws DataModellingException on serialization error
     */
    @MapsToCapability(Capability.EXPORT_SKETCH_INDEX_TO_YAML)
    public String exportSketchIndexToYaml(String json) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_export_sketch_index_to_yaml(call, cstrCall(call, json)));
        }
    }

    /**
     * Create a new sketch with specified name, type, and optional description.
     *
     * @param name the sketch name
     * @param sketchType the sketch type (e.g., "entity-relationship", "class", "use-case")
     * @param description optional description text
     * @return created sketch JSON representation
     * @throws DataModellingException on creation error
     */
    @MapsToCapability(Capability.CREATE_SKETCH)
    public String createSketch(String name, String sketchType, String description) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_create_sketch(
                call, cstrCall(call, name), cstrCall(call, sketchType), cstrOpt(call, description)));
        }
    }

    /**
     * Create a new sketch index.
     *
     * @param name the index name
     * @return created sketch index JSON representation
     * @throws DataModellingException on creation error
     */
    @MapsToCapability(Capability.CREATE_SKETCH_INDEX)
    public String createSketchIndex(String name) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_create_sketch_index(call, cstrCall(call, name)));
        }
    }

    /**
     * Add a sketch to an existing sketch index.
     *
     * @param indexJson the sketch index JSON representation
     * @param sketchJson the sketch JSON representation
     * @return updated index JSON representation
     * @throws DataModellingException on addition error
     */
    @MapsToCapability(Capability.ADD_SKETCH_TO_INDEX)
    public String addSketchToIndex(String indexJson, String sketchJson) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_add_sketch_to_index(call, cstrCall(call, indexJson), cstrCall(call, sketchJson)));
        }
    }

    /**
     * Search sketches within an index using query string.
     *
     * @param indexJson the sketch index JSON representation
     * @param query the search query string
     * @return JSON array of matching sketches
     * @throws DataModellingException on search error
     */
    @MapsToCapability(Capability.SEARCH_SKETCHES)
    public String searchSketches(String indexJson, String query) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_search_sketches(call, cstrCall(call, indexJson), cstrCall(call, query)));
        }
    }

    // ── Group K: Domain (4) ─────────────────────────────────────────────────

    /**
     * Create a new domain with name and optional description.
     *
     * @param name the domain name
     * @param description optional description text
     * @return created domain JSON representation
     * @throws DataModellingException on creation error
     */
    @MapsToCapability(Capability.CREATE_DOMAIN)
    public String createDomain(String name, String description) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_create_domain(call, cstrCall(call, name), cstrOpt(call, description)));
        }
    }

    /**
     * Add a system to an existing domain.
     *
     * @param domainJson the domain JSON representation
     * @param systemJson the system JSON representation
     * @return updated domain JSON representation
     * @throws DataModellingException on addition error
     */
    @MapsToCapability(Capability.ADD_SYSTEM_TO_DOMAIN)
    public String addSystemToDomain(String domainJson, String systemJson) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_add_system_to_domain(call, cstrCall(call, domainJson), cstrCall(call, systemJson)));
        }
    }

    /**
     * Add a CADS node to a domain.
     *
     * @param domainJson the domain JSON representation
     * @param nodeJson the CADS node JSON representation
     * @return updated domain JSON representation
     * @throws DataModellingException on addition error
     */
    @MapsToCapability(Capability.ADD_CADS_NODE_TO_DOMAIN)
    public String addCadsNodeToDomain(String domainJson, String nodeJson) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_add_cads_node_to_domain(call, cstrCall(call, domainJson), cstrCall(call, nodeJson)));
        }
    }

    /**
     * Add an ODCS node to a domain.
     *
     * @param domainJson the domain JSON representation
     * @param nodeJson the ODCS node JSON representation
     * @return updated domain JSON representation
     * @throws DataModellingException on addition error
     */
    @MapsToCapability(Capability.ADD_ODCS_NODE_TO_DOMAIN)
    public String addOdcsNodeToDomain(String domainJson, String nodeJson) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_add_odcs_node_to_domain(call, cstrCall(call, domainJson), cstrCall(call, nodeJson)));
        }
    }

    // ── Group L: Filter (5) ─────────────────────────────────────────────────

    /**
     * Filter domain/system nodes by owner identifier.
     *
     * @param json the domain or system JSON representation
     * @param owner the owner identifier to filter by
     * @return filtered nodes JSON array
     * @throws DataModellingException on filtering error
     */
    @MapsToCapability(Capability.FILTER_NODES_BY_OWNER)
    public String filterNodesByOwner(String json, String owner) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_filter_nodes_by_owner(call, cstrCall(call, json), cstrCall(call, owner)));
        }
    }

    /**
     * Filter domain/system relationships by owner identifier.
     *
     * @param json the domain or system JSON representation
     * @param owner the owner identifier to filter by
     * @return filtered relationships JSON array
     * @throws DataModellingException on filtering error
     */
    @MapsToCapability(Capability.FILTER_RELATIONSHIPS_BY_OWNER)
    public String filterRelationshipsByOwner(String json, String owner) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_filter_relationships_by_owner(call, cstrCall(call, json), cstrCall(call, owner)));
        }
    }

    /**
     * Filter domain/system nodes by infrastructure type.
     *
     * @param json the domain or system JSON representation
     * @param infraType the infrastructure type to filter by
     * @return filtered nodes JSON array
     * @throws DataModellingException on filtering error
     */
    @MapsToCapability(Capability.FILTER_NODES_BY_INFRASTRUCTURE_TYPE)
    public String filterNodesByInfrastructureType(String json, String infraType) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_filter_nodes_by_infrastructure_type(call, cstrCall(call, json), cstrCall(call, infraType)));
        }
    }

    /**
     * Filter domain/system relationships by infrastructure type.
     *
     * @param json the domain or system JSON representation
     * @param infraType the infrastructure type to filter by
     * @return filtered relationships JSON array
     * @throws DataModellingException on filtering error
     */
    @MapsToCapability(Capability.FILTER_RELATIONSHIPS_BY_INFRASTRUCTURE_TYPE)
    public String filterRelationshipsByInfrastructureType(String json, String infraType) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_filter_relationships_by_infrastructure_type(call, cstrCall(call, json), cstrCall(call, infraType)));
        }
    }

    /**
     * Filter domain/system entities by tags.
     *
     * @param json the domain or system JSON representation
     * @param tagsJson the tags filter as JSON (e.g., ["tag1", "tag2"])
     * @return filtered entities JSON array
     * @throws DataModellingException on filtering error
     */
    @MapsToCapability(Capability.FILTER_BY_TAGS)
    public String filterByTags(String json, String tagsJson) {
        try (Arena call = Arena.ofConfined()) {
            return unwrapResult(data_modelling_ffi_h.dm_filter_by_tags(call, cstrCall(call, json), cstrCall(call, tagsJson)));
        }
    }

    // ── AutoCloseable ────────────────────────────────────────────────────────

    /**
     * Close the bridge's shared arena, freeing all native resources.
     *
     * <p>After close, all method invocations will raise {@link IllegalStateException}
     * or similar error from the foreign function interface.</p>
     */
    @Override
    public void close() {
        arena.close();
    }
}
