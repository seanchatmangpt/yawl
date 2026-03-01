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

/**
 * Enumeration of 42 native capabilities exposed by libdata_modelling_ffi.so.
 *
 * <p>Each capability maps to a typed Java method in {@link DataModellingBridge}
 * and a corresponding C function via Panama FFI.</p>
 *
 * <p>Capabilities are organized into logical groups:</p>
 * <ul>
 *   <li><strong>ODCS Core:</strong> PARSE_ODCS_YAML, EXPORT_TO_ODCS_YAML, CONVERT_TO_ODCS</li>
 *   <li><strong>SQL:</strong> IMPORT_FROM_SQL, EXPORT_TO_SQL</li>
 *   <li><strong>Schema Format Import:</strong> IMPORT_FROM_AVRO, IMPORT_FROM_JSON_SCHEMA, IMPORT_FROM_PROTOBUF, IMPORT_FROM_CADS, IMPORT_FROM_ODPS</li>
 *   <li><strong>Schema Format Export:</strong> EXPORT_TO_AVRO, EXPORT_TO_JSON_SCHEMA, EXPORT_TO_PROTOBUF, EXPORT_TO_CADS, EXPORT_TO_ODPS</li>
 *   <li><strong>ODPS Validation:</strong> VALIDATE_ODPS</li>
 *   <li><strong>BPMN:</strong> IMPORT_BPMN_MODEL, EXPORT_BPMN_MODEL</li>
 *   <li><strong>DMN:</strong> IMPORT_DMN_MODEL, EXPORT_DMN_MODEL</li>
 *   <li><strong>OpenAPI:</strong> IMPORT_OPENAPI_SPEC, EXPORT_OPENAPI_SPEC, CONVERT_OPENAPI_TO_ODCS, ANALYZE_OPENAPI_CONVERSION</li>
 *   <li><strong>DataFlow:</strong> MIGRATE_DATAFLOW_TO_DOMAIN</li>
 *   <li><strong>Sketch:</strong> PARSE_SKETCH_YAML, PARSE_SKETCH_INDEX_YAML, EXPORT_SKETCH_TO_YAML, EXPORT_SKETCH_INDEX_TO_YAML, CREATE_SKETCH, CREATE_SKETCH_INDEX, ADD_SKETCH_TO_INDEX, SEARCH_SKETCHES</li>
 *   <li><strong>Domain:</strong> CREATE_DOMAIN, ADD_SYSTEM_TO_DOMAIN, ADD_CADS_NODE_TO_DOMAIN, ADD_ODCS_NODE_TO_DOMAIN</li>
 *   <li><strong>Filter:</strong> FILTER_NODES_BY_OWNER, FILTER_RELATIONSHIPS_BY_OWNER, FILTER_NODES_BY_INFRASTRUCTURE_TYPE, FILTER_RELATIONSHIPS_BY_INFRASTRUCTURE_TYPE, FILTER_BY_TAGS</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public enum Capability {
    // ── Group A: ODCS Core (3) ───────────────────────────────────────────────
    /** Parse ODCS (Open Data Catalog Specification) YAML into normalized JSON. */
    PARSE_ODCS_YAML,
    /** Export normalized JSON to ODCS YAML format. */
    EXPORT_TO_ODCS_YAML,
    /** Convert between schema formats with ODCS as target. */
    CONVERT_TO_ODCS,

    // ── Group B: SQL (2) ─────────────────────────────────────────────────────
    /** Import SQL DDL schema into ODCS JSON representation. */
    IMPORT_FROM_SQL,
    /** Export ODCS JSON to SQL DDL for target dialect. */
    EXPORT_TO_SQL,

    // ── Group C: Schema Format Import (5) ────────────────────────────────────
    /** Import Apache Avro schema into ODCS JSON. */
    IMPORT_FROM_AVRO,
    /** Import JSON Schema into ODCS JSON. */
    IMPORT_FROM_JSON_SCHEMA,
    /** Import Protocol Buffers schema into ODCS JSON. */
    IMPORT_FROM_PROTOBUF,
    /** Import CADS (Catalog Asset Description Schema) into ODCS JSON. */
    IMPORT_FROM_CADS,
    /** Import ODPS (Open Data Policy Specification) YAML into ODCS JSON. */
    IMPORT_FROM_ODPS,

    // ── Group D: Schema Format Export (5) ────────────────────────────────────
    /** Export ODCS JSON to Apache Avro schema. */
    EXPORT_TO_AVRO,
    /** Export ODCS JSON to JSON Schema. */
    EXPORT_TO_JSON_SCHEMA,
    /** Export ODCS JSON to Protocol Buffers. */
    EXPORT_TO_PROTOBUF,
    /** Export ODCS JSON to CADS (Catalog Asset Description Schema). */
    EXPORT_TO_CADS,
    /** Export ODCS JSON to ODPS YAML. */
    EXPORT_TO_ODPS,

    // ── Group E: ODPS Validation (1) ────────────────────────────────────────
    /** Validate ODPS YAML against specification constraints. Throws on validation error. */
    VALIDATE_ODPS,

    // ── Group F: BPMN (2) ───────────────────────────────────────────────────
    /** Import BPMN XML model into ODCS JSON representation. */
    IMPORT_BPMN_MODEL,
    /** Export ODCS JSON to BPMN XML format. */
    EXPORT_BPMN_MODEL,

    // ── Group G: DMN (2) ────────────────────────────────────────────────────
    /** Import DMN (Decision Model and Notation) XML into ODCS JSON. */
    IMPORT_DMN_MODEL,
    /** Export ODCS JSON to DMN XML format. */
    EXPORT_DMN_MODEL,

    // ── Group H: OpenAPI (4) ────────────────────────────────────────────────
    /** Import OpenAPI specification (YAML or JSON) into ODCS JSON. */
    IMPORT_OPENAPI_SPEC,
    /** Export ODCS JSON to OpenAPI specification format. */
    EXPORT_OPENAPI_SPEC,
    /** Convert OpenAPI specification to ODCS format. */
    CONVERT_OPENAPI_TO_ODCS,
    /** Analyze OpenAPI spec for conversion feasibility and potential issues. */
    ANALYZE_OPENAPI_CONVERSION,

    // ── Group I: DataFlow (1) ────────────────────────────────────────────────
    /** Migrate dataflow definition into domain organization model. */
    MIGRATE_DATAFLOW_TO_DOMAIN,

    // ── Group J: Sketch (8) ─────────────────────────────────────────────────
    /** Parse sketch definition from YAML into JSON. */
    PARSE_SKETCH_YAML,
    /** Parse sketch index definition from YAML into JSON. */
    PARSE_SKETCH_INDEX_YAML,
    /** Export sketch definition to YAML format. */
    EXPORT_SKETCH_TO_YAML,
    /** Export sketch index definition to YAML format. */
    EXPORT_SKETCH_INDEX_TO_YAML,
    /** Create a new sketch with specified name, type, and optional description. */
    CREATE_SKETCH,
    /** Create a new sketch index. */
    CREATE_SKETCH_INDEX,
    /** Add a sketch to an existing sketch index. */
    ADD_SKETCH_TO_INDEX,
    /** Search sketches within an index using query string. */
    SEARCH_SKETCHES,

    // ── Group K: Domain (4) ─────────────────────────────────────────────────
    /** Create a new domain with name and optional description. */
    CREATE_DOMAIN,
    /** Add a system to an existing domain. */
    ADD_SYSTEM_TO_DOMAIN,
    /** Add a CADS node to a domain. */
    ADD_CADS_NODE_TO_DOMAIN,
    /** Add an ODCS node to a domain. */
    ADD_ODCS_NODE_TO_DOMAIN,

    // ── Group L: Filter (5) ─────────────────────────────────────────────────
    /** Filter domain/system nodes by owner identifier. */
    FILTER_NODES_BY_OWNER,
    /** Filter domain/system relationships by owner identifier. */
    FILTER_RELATIONSHIPS_BY_OWNER,
    /** Filter domain/system nodes by infrastructure type. */
    FILTER_NODES_BY_INFRASTRUCTURE_TYPE,
    /** Filter domain/system relationships by infrastructure type. */
    FILTER_RELATIONSHIPS_BY_INFRASTRUCTURE_TYPE,
    /** Filter domain/system entities by tags. */
    FILTER_BY_TAGS
}
