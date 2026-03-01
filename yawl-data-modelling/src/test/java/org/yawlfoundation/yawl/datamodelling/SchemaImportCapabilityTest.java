package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.datamodelling.api.DataModellingService;
import org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h;
import org.yawlfoundation.yawl.datamodelling.model.WorkspaceModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.Capability.*;
import static org.yawlfoundation.yawl.datamodelling.test.DataModellingTestFixtures.*;

/**
 * Capability tests for Group C: Schema Format Import (5 capabilities).
 *
 * <p>Coverage: IMPORT_FROM_AVRO, IMPORT_FROM_JSON_SCHEMA, IMPORT_FROM_PROTOBUF,
 * IMPORT_FROM_CADS, IMPORT_FROM_ODPS.
 *
 * <p>Each capability tested with:
 * 1. Valid fixture → assertNotNull(workspace)
 * 2. Invalid/empty input → assertThrows(DataModellingException.class, ...)
 */
@Tag("capability")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaImportCapabilityTest {

    DataModellingService service;

    @BeforeAll
    void setup() {
        assumeTrue(
            data_modelling_ffi_h.LIBRARY.isPresent(),
            "Native library not present — skipping capability tests"
        );
        service = DataModellingModule.create();
    }

    @AfterAll
    void teardown() throws Exception {
        if (service != null) {
            service.close();
        }
    }

    // Group C — Schema Format Import

    // IMPORT_FROM_AVRO
    @Test
    @CapabilityTest(IMPORT_FROM_AVRO)
    @DisplayName("IMPORT_FROM_AVRO: orders.avsc → WorkspaceModel")
    void importFromAvro_ordersSchema_returnsNonNullWorkspace() throws Exception {
        WorkspaceModel ws = service.importFromAvro(AVRO_SCHEMA);
        assertNotNull(ws, "importFromAvro must return non-null WorkspaceModel");
    }

    @Test
    @CapabilityTest(IMPORT_FROM_AVRO)
    @DisplayName("IMPORT_FROM_AVRO: invalid input → DataModellingException")
    void importFromAvro_invalidInput_throwsDataModellingException() {
        assertThrows(
            DataModellingException.class,
            () -> service.importFromAvro("not valid avro schema"),
            "importFromAvro must throw DataModellingException on invalid input"
        );
    }

    // IMPORT_FROM_JSON_SCHEMA
    @Test
    @CapabilityTest(IMPORT_FROM_JSON_SCHEMA)
    @DisplayName("IMPORT_FROM_JSON_SCHEMA: orders.json-schema.json → WorkspaceModel")
    void importFromJsonSchema_ordersSchema_returnsNonNullWorkspace() throws Exception {
        WorkspaceModel ws = service.importFromJsonSchema(JSON_SCHEMA);
        assertNotNull(ws, "importFromJsonSchema must return non-null WorkspaceModel");
    }

    @Test
    @CapabilityTest(IMPORT_FROM_JSON_SCHEMA)
    @DisplayName("IMPORT_FROM_JSON_SCHEMA: invalid input → DataModellingException")
    void importFromJsonSchema_invalidInput_throwsDataModellingException() {
        assertThrows(
            DataModellingException.class,
            () -> service.importFromJsonSchema("not a json schema"),
            "importFromJsonSchema must throw DataModellingException on invalid input"
        );
    }

    // IMPORT_FROM_PROTOBUF
    @Test
    @CapabilityTest(IMPORT_FROM_PROTOBUF)
    @DisplayName("IMPORT_FROM_PROTOBUF: orders.proto → WorkspaceModel")
    void importFromProtobuf_ordersProto_returnsNonNullWorkspace() throws Exception {
        WorkspaceModel ws = service.importFromProtobuf(PROTOBUF);
        assertNotNull(ws, "importFromProtobuf must return non-null WorkspaceModel");
    }

    @Test
    @CapabilityTest(IMPORT_FROM_PROTOBUF)
    @DisplayName("IMPORT_FROM_PROTOBUF: invalid proto → DataModellingException")
    void importFromProtobuf_invalidProto_throwsDataModellingException() {
        assertThrows(
            DataModellingException.class,
            () -> service.importFromProtobuf("not valid proto"),
            "importFromProtobuf must throw DataModellingException on invalid input"
        );
    }

    // IMPORT_FROM_CADS
    @Test
    @CapabilityTest(IMPORT_FROM_CADS)
    @DisplayName("IMPORT_FROM_CADS: orders-cads.yaml → WorkspaceModel")
    void importFromCads_cadsYaml_returnsNonNullWorkspace() throws Exception {
        WorkspaceModel ws = service.importFromCads(CADS_YAML);
        assertNotNull(ws, "importFromCads must return non-null WorkspaceModel");
    }

    @Test
    @CapabilityTest(IMPORT_FROM_CADS)
    @DisplayName("IMPORT_FROM_CADS: empty input → DataModellingException")
    void importFromCads_emptyInput_throwsDataModellingException() {
        assertThrows(
            DataModellingException.class,
            () -> service.importFromCads(""),
            "importFromCads must throw DataModellingException on empty input"
        );
    }

    // IMPORT_FROM_ODPS
    @Test
    @CapabilityTest(IMPORT_FROM_ODPS)
    @DisplayName("IMPORT_FROM_ODPS: orders-odps.yaml → WorkspaceModel")
    void importFromOdps_odpsYaml_returnsNonNullWorkspace() throws Exception {
        WorkspaceModel ws = service.importFromOdps(ODPS_YAML);
        assertNotNull(ws, "importFromOdps must return non-null WorkspaceModel");
    }

    @Test
    @CapabilityTest(IMPORT_FROM_ODPS)
    @DisplayName("IMPORT_FROM_ODPS: invalid YAML → DataModellingException")
    void importFromOdps_invalidInput_throwsDataModellingException() {
        assertThrows(
            DataModellingException.class,
            () -> service.importFromOdps("garbage yaml :::"),
            "importFromOdps must throw DataModellingException on invalid YAML"
        );
    }
}
