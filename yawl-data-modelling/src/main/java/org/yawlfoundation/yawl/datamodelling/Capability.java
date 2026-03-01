package org.yawlfoundation.yawl.datamodelling;

/**
 * Enumeration of all 42 data-modelling capabilities.
 * {@link CapabilityRegistry#assertComplete()} verifies at startup that every
 * capability has exactly one {@link MapsToCapability} annotation in each of
 * {@code DataModellingBridge} and {@code DataModellingServiceImpl}.
 */
public enum Capability {
    // Group A — ODCS Core (3)
    PARSE_ODCS_YAML,
    EXPORT_TO_ODCS_YAML,
    CONVERT_TO_ODCS,

    // Group B — SQL (2)
    IMPORT_FROM_SQL,
    EXPORT_TO_SQL,

    // Group C — Schema Format Import (5)
    IMPORT_FROM_AVRO,
    IMPORT_FROM_JSON_SCHEMA,
    IMPORT_FROM_PROTOBUF,
    IMPORT_FROM_CADS,
    IMPORT_FROM_ODPS,

    // Group D — Schema Format Export (5)
    EXPORT_TO_AVRO,
    EXPORT_TO_JSON_SCHEMA,
    EXPORT_TO_PROTOBUF,
    EXPORT_TO_CADS,
    EXPORT_TO_ODPS,

    // Group E — ODPS Validation (1)
    VALIDATE_ODPS,

    // Group F — BPMN (2)
    IMPORT_BPMN_MODEL,
    EXPORT_BPMN_MODEL,

    // Group G — DMN (2)
    IMPORT_DMN_MODEL,
    EXPORT_DMN_MODEL,

    // Group H — OpenAPI (4)
    IMPORT_OPENAPI_SPEC,
    EXPORT_OPENAPI_SPEC,
    CONVERT_OPENAPI_TO_ODCS,
    ANALYZE_OPENAPI_CONVERSION,

    // Group I — DataFlow Migration (1)
    MIGRATE_DATAFLOW_TO_DOMAIN,

    // Group J — Sketch Operations (8)
    PARSE_SKETCH_YAML,
    PARSE_SKETCH_INDEX_YAML,
    EXPORT_SKETCH_TO_YAML,
    EXPORT_SKETCH_INDEX_TO_YAML,
    CREATE_SKETCH,
    CREATE_SKETCH_INDEX,
    ADD_SKETCH_TO_INDEX,
    SEARCH_SKETCHES,

    // Group K — Domain Operations (4)
    CREATE_DOMAIN,
    ADD_SYSTEM_TO_DOMAIN,
    ADD_CADS_NODE_TO_DOMAIN,
    ADD_ODCS_NODE_TO_DOMAIN,

    // Group L — Filter Operations (5)
    FILTER_NODES_BY_OWNER,
    FILTER_RELATIONSHIPS_BY_OWNER,
    FILTER_NODES_BY_INFRASTRUCTURE_TYPE,
    FILTER_RELATIONSHIPS_BY_INFRASTRUCTURE_TYPE,
    FILTER_BY_TAGS;

    /** Total number of capabilities. Used by {@link CapabilityRegistry} to detect enum drift. */
    public static final int TOTAL = 42;
}
