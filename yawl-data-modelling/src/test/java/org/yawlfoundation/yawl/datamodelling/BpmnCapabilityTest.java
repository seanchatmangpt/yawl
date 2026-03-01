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
 * Group F — BPMN Capability Tests (2 capabilities).
 *
 * <p>Tests cover:
 * - IMPORT_BPMN_MODEL: Parse BPMN 2.0 XML into WorkspaceModel
 * - EXPORT_BPMN_MODEL: Export WorkspaceModel back to BPMN 2.0 XML
 *
 * <p>These tests use real BPMN fixtures (orders process), asserting structural
 * properties (non-null results, valid content) without mocking or stubbing
 * the native bridge.
 *
 * <p>Skips if native library is not present (UnsupportedOperationException expected).
 */
@Tag("capability")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BpmnCapabilityTest {

    DataModellingService service;

    @BeforeAll
    void setup() {
        assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
            "Native library not present — skipping BPMN tests");
        service = DataModellingModule.create();
    }

    @AfterAll
    void teardown() throws Exception {
        if (service != null) {
            service.close();
        }
    }

    @Nested
    @DisplayName("IMPORT_BPMN_MODEL")
    class ImportBpmnModel {

        @Test
        @CapabilityTest(IMPORT_BPMN_MODEL)
        @DisplayName("valid BPMN XML returns non-null WorkspaceModel")
        void validBpmnXml_returnsNonNullWorkspace() throws Exception {
            WorkspaceModel ws = service.importBpmnModel(BPMN_XML);
            assertNotNull(ws, "BPMN import must return non-null WorkspaceModel");
        }

        @Test
        @CapabilityTest(IMPORT_BPMN_MODEL)
        @DisplayName("invalid XML throws DataModellingException")
        void invalidXml_throwsDataModellingException() {
            assertThrows(DataModellingException.class,
                () -> service.importBpmnModel("<not-bpmn/>"),
                "Invalid XML must throw DataModellingException");
        }

        @Test
        @CapabilityTest(IMPORT_BPMN_MODEL)
        @DisplayName("empty XML throws exception")
        void emptyXml_throwsException() {
            assertThrows(Exception.class,
                () -> service.importBpmnModel(""),
                "Empty XML must throw exception");
        }
    }

    @Nested
    @DisplayName("EXPORT_BPMN_MODEL")
    class ExportBpmnModel {

        @Test
        @CapabilityTest(EXPORT_BPMN_MODEL)
        @DisplayName("exported output from imported model is non-blank")
        void exportedOutput_isNonBlank() throws Exception {
            WorkspaceModel ws = service.importBpmnModel(BPMN_XML);
            String bpmn = service.exportBpmnModel(ws);

            assertNotNull(bpmn, "BPMN export must not be null");
            assertFalse(bpmn.isBlank(), "BPMN export must not be blank");
        }

        @Test
        @CapabilityTest(EXPORT_BPMN_MODEL)
        @DisplayName("exported output contains process or definitions marker")
        void exportedOutput_containsBpmnMarker() throws Exception {
            WorkspaceModel ws = service.importBpmnModel(BPMN_XML);
            String bpmn = service.exportBpmnModel(ws);

            assertTrue(
                bpmn.toLowerCase().contains("process")
                    || bpmn.toLowerCase().contains("bpmn")
                    || bpmn.toLowerCase().contains("definitions"),
                "BPMN export must contain process, bpmn, or definitions marker");
        }

        @Test
        @CapabilityTest(EXPORT_BPMN_MODEL)
        @DisplayName("roundtrip preserves model properties")
        void roundtrip_preservesModelProperties() throws Exception {
            WorkspaceModel original = service.importBpmnModel(BPMN_XML);
            String exported = service.exportBpmnModel(original);
            WorkspaceModel roundtrip = service.importBpmnModel(exported);

            assertNotNull(roundtrip, "Roundtrip must produce non-null WorkspaceModel");
            assertEquals(original.tables().size(), roundtrip.tables().size(),
                "Roundtrip must preserve table count");
        }
    }
}
