package org.yawlfoundation.yawl.datamodelling.test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shared test fixture loader for all data-modelling capability tests.
 *
 * <p>Resources are loaded from the classpath relative to this class's package:
 * {@code org/yawlfoundation/yawl/datamodelling/test/fixtures/}.
 */
public final class DataModellingTestFixtures {

    private DataModellingTestFixtures() {}

    /** Load a fixture file from the fixtures/ directory relative to this class. */
    public static String load(String filename) {
        try (InputStream in = DataModellingTestFixtures.class
                .getResourceAsStream("fixtures/" + filename)) {
            if (in == null) {
                throw new RuntimeException("Missing test fixture: fixtures/" + filename);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Cannot load fixture: " + filename, e);
        }
    }

    // Group A — ODCS
    public static final String ODCS_YAML        = load("odcs-orders.yaml");

    // Group B — SQL
    public static final String SQL_POSTGRES      = load("orders-postgres.sql");
    public static final String SQL_SQLITE        = load("orders-sqlite.sql");

    // Group C — Schema Format Import
    public static final String AVRO_SCHEMA       = load("orders.avsc");
    public static final String JSON_SCHEMA       = load("orders.json-schema.json");
    public static final String PROTOBUF          = load("orders.proto");
    public static final String CADS_YAML         = load("orders-cads.yaml");
    public static final String ODPS_YAML         = load("orders-odps.yaml");

    // Group F/G — Process models
    public static final String BPMN_XML          = load("orders-process.bpmn");
    public static final String DMN_XML           = load("orders-decision.dmn");

    // Group H — OpenAPI
    public static final String OPENAPI_YAML      = load("orders-api.yaml");

    // Group I — DataFlow
    public static final String DATAFLOW_YAML     = load("orders-dataflow.yaml");

    // Group J — Sketch
    public static final String SKETCH_YAML       = load("sketch-001.yaml");
    public static final String SKETCH_INDEX_YAML = load("sketch-index.yaml");
    public static final String EXCALIDRAW_JSON   = load("excalidraw-minimal.json");
}
