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
 * Capability tests for Group D: Schema Format Export (5 capabilities).
 *
 * <p>Coverage: EXPORT_TO_AVRO, EXPORT_TO_JSON_SCHEMA, EXPORT_TO_PROTOBUF,
 * EXPORT_TO_CADS, EXPORT_TO_ODPS.
 *
 * <p>Each capability tested with:
 * 1. Valid workspace → assertNotNull(output) and format-specific marker assertion
 * 2. Roundtrip: export → import back → assertNotNull (validates stability)
 */
@Tag("capability")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaExportCapabilityTest {

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

    // Group D — Schema Format Export

    // EXPORT_TO_AVRO
    @Test
    @CapabilityTest(EXPORT_TO_AVRO)
    @DisplayName("EXPORT_TO_AVRO: WorkspaceModel → non-blank output")
    void exportToAvro_fromOdcsWorkspace_outputNotBlank() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String out = service.exportToAvro(ws);
        assertNotNull(out, "exportToAvro must return non-null output");
        assertFalse(out.isBlank(), "Avro export must not be blank");
    }

    @Test
    @CapabilityTest(EXPORT_TO_AVRO)
    @DisplayName("EXPORT_TO_AVRO: output contains Avro 'record' marker")
    void exportToAvro_output_containsAvroRecordMarker() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String out = service.exportToAvro(ws);
        assertTrue(
            out.toLowerCase().contains("record"),
            "Avro output must contain 'record' type marker (Avro schemas are JSON with type:record)"
        );
    }

    @Test
    @CapabilityTest(EXPORT_TO_AVRO)
    @DisplayName("EXPORT_TO_AVRO: roundtrip export → import succeeds")
    void exportToAvro_roundtrip_reimportSucceeds() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String avro = service.exportToAvro(ws);
        WorkspaceModel reimport = service.importFromAvro(avro);
        assertNotNull(reimport, "Roundtrip import must succeed after export");
    }

    // EXPORT_TO_JSON_SCHEMA
    @Test
    @CapabilityTest(EXPORT_TO_JSON_SCHEMA)
    @DisplayName("EXPORT_TO_JSON_SCHEMA: WorkspaceModel → non-blank output")
    void exportToJsonSchema_fromOdcsWorkspace_outputNotBlank() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String out = service.exportToJsonSchema(ws);
        assertNotNull(out, "exportToJsonSchema must return non-null output");
        assertFalse(out.isBlank(), "JSON Schema export must not be blank");
    }

    @Test
    @CapabilityTest(EXPORT_TO_JSON_SCHEMA)
    @DisplayName("EXPORT_TO_JSON_SCHEMA: output contains 'properties' or '$schema' marker")
    void exportToJsonSchema_output_containsSchemaMarker() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String out = service.exportToJsonSchema(ws);
        assertTrue(
            out.contains("properties") || out.contains("$schema"),
            "JSON Schema output must contain 'properties' or '$schema' marker"
        );
    }

    @Test
    @CapabilityTest(EXPORT_TO_JSON_SCHEMA)
    @DisplayName("EXPORT_TO_JSON_SCHEMA: roundtrip export → import succeeds")
    void exportToJsonSchema_roundtrip_reimportSucceeds() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String schema = service.exportToJsonSchema(ws);
        WorkspaceModel reimport = service.importFromJsonSchema(schema);
        assertNotNull(reimport, "Roundtrip import must succeed after export");
    }

    // EXPORT_TO_PROTOBUF
    @Test
    @CapabilityTest(EXPORT_TO_PROTOBUF)
    @DisplayName("EXPORT_TO_PROTOBUF: WorkspaceModel → non-blank output")
    void exportToProtobuf_fromOdcsWorkspace_outputNotBlank() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String out = service.exportToProtobuf(ws);
        assertNotNull(out, "exportToProtobuf must return non-null output");
        assertFalse(out.isBlank(), "Protobuf export must not be blank");
    }

    @Test
    @CapabilityTest(EXPORT_TO_PROTOBUF)
    @DisplayName("EXPORT_TO_PROTOBUF: output contains 'message' or 'syntax' marker")
    void exportToProtobuf_output_containsProtoMarker() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String out = service.exportToProtobuf(ws);
        assertTrue(
            out.contains("message") || out.contains("syntax"),
            "Protobuf output must contain 'message' or 'syntax' keyword"
        );
    }

    @Test
    @CapabilityTest(EXPORT_TO_PROTOBUF)
    @DisplayName("EXPORT_TO_PROTOBUF: roundtrip export → import succeeds")
    void exportToProtobuf_roundtrip_reimportSucceeds() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String proto = service.exportToProtobuf(ws);
        WorkspaceModel reimport = service.importFromProtobuf(proto);
        assertNotNull(reimport, "Roundtrip import must succeed after export");
    }

    // EXPORT_TO_CADS
    @Test
    @CapabilityTest(EXPORT_TO_CADS)
    @DisplayName("EXPORT_TO_CADS: WorkspaceModel → non-blank output")
    void exportToCads_fromOdcsWorkspace_outputNotBlank() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String out = service.exportToCads(ws);
        assertNotNull(out, "exportToCads must return non-null output");
        assertFalse(out.isBlank(), "CADS export must not be blank");
    }

    @Test
    @CapabilityTest(EXPORT_TO_CADS)
    @DisplayName("EXPORT_TO_CADS: output contains YAML ':' key separator")
    void exportToCads_output_containsYamlKeyMarker() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String out = service.exportToCads(ws);
        assertTrue(
            out.contains(":"),
            "CADS output (YAML) must contain ':' key separator"
        );
    }

    // EXPORT_TO_ODPS
    @Test
    @CapabilityTest(EXPORT_TO_ODPS)
    @DisplayName("EXPORT_TO_ODPS: WorkspaceModel → non-blank output")
    void exportToOdps_fromOdcsWorkspace_outputNotBlank() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String out = service.exportToOdps(ws);
        assertNotNull(out, "exportToOdps must return non-null output");
        assertFalse(out.isBlank(), "ODPS export must not be blank");
    }

    @Test
    @CapabilityTest(EXPORT_TO_ODPS)
    @DisplayName("EXPORT_TO_ODPS: output contains 'apiVersion' or 'kind' marker")
    void exportToOdps_output_containsOdpsMarker() throws Exception {
        WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
        String out = service.exportToOdps(ws);
        assertTrue(
            out.contains("apiVersion") || out.contains("kind"),
            "ODPS output must contain 'apiVersion' or 'kind' marker"
        );
    }
}
