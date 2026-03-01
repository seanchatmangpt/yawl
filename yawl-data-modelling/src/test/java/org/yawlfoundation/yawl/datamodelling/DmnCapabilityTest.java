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
 * Group G — DMN Capability Tests (2 capabilities).
 *
 * <p>Tests cover:
 * - IMPORT_DMN_MODEL: Parse DMN 1.3 XML into WorkspaceModel
 * - EXPORT_DMN_MODEL: Export WorkspaceModel back to DMN 1.3 XML
 *
 * <p>These tests use real DMN fixtures (orders decision), asserting structural
 * properties (non-null results, valid content) without mocking or stubbing
 * the native bridge.
 *
 * <p>Skips if native library is not present (UnsupportedOperationException expected).
 */
@Tag("capability")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DmnCapabilityTest {

    DataModellingService service;

    @BeforeAll
    void setup() {
        assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
            "Native library not present — skipping DMN tests");
        service = DataModellingModule.create();
    }

    @AfterAll
    void teardown() throws Exception {
        if (service != null) {
            service.close();
        }
    }

    @Nested
    @DisplayName("IMPORT_DMN_MODEL")
    class ImportDmnModel {

        @Test
        @CapabilityTest(IMPORT_DMN_MODEL)
        @DisplayName("valid DMN XML returns non-null WorkspaceModel")
        void validDmnXml_returnsNonNullWorkspace() throws Exception {
            WorkspaceModel ws = service.importDmnModel(DMN_XML);
            assertNotNull(ws, "DMN import must return non-null WorkspaceModel");
        }

        @Test
        @CapabilityTest(IMPORT_DMN_MODEL)
        @DisplayName("invalid XML throws DataModellingException")
        void invalidXml_throwsDataModellingException() {
            assertThrows(DataModellingException.class,
                () -> service.importDmnModel("<not-dmn/>"),
                "Invalid XML must throw DataModellingException");
        }

        @Test
        @CapabilityTest(IMPORT_DMN_MODEL)
        @DisplayName("empty XML throws exception")
        void emptyXml_throwsException() {
            assertThrows(Exception.class,
                () -> service.importDmnModel(""),
                "Empty XML must throw exception");
        }
    }

    @Nested
    @DisplayName("EXPORT_DMN_MODEL")
    class ExportDmnModel {

        @Test
        @CapabilityTest(EXPORT_DMN_MODEL)
        @DisplayName("exported output from imported model is non-blank")
        void exportedOutput_isNonBlank() throws Exception {
            WorkspaceModel ws = service.importDmnModel(DMN_XML);
            String dmn = service.exportDmnModel(ws);

            assertNotNull(dmn, "DMN export must not be null");
            assertFalse(dmn.isBlank(), "DMN export must not be blank");
        }

        @Test
        @CapabilityTest(EXPORT_DMN_MODEL)
        @DisplayName("exported output contains decision or definitions marker")
        void exportedOutput_containsDmnMarker() throws Exception {
            WorkspaceModel ws = service.importDmnModel(DMN_XML);
            String dmn = service.exportDmnModel(ws);

            assertTrue(
                dmn.toLowerCase().contains("decision")
                    || dmn.toLowerCase().contains("dmn")
                    || dmn.toLowerCase().contains("definitions"),
                "DMN export must contain decision, dmn, or definitions marker");
        }

        @Test
        @CapabilityTest(EXPORT_DMN_MODEL)
        @DisplayName("roundtrip preserves model properties")
        void roundtrip_preservesModelProperties() throws Exception {
            WorkspaceModel original = service.importDmnModel(DMN_XML);
            String exported = service.exportDmnModel(original);
            WorkspaceModel roundtrip = service.importDmnModel(exported);

            assertNotNull(roundtrip, "Roundtrip must produce non-null WorkspaceModel");
            assertEquals(original.tables().size(), roundtrip.tables().size(),
                "Roundtrip must preserve table count");
        }
    }
}
