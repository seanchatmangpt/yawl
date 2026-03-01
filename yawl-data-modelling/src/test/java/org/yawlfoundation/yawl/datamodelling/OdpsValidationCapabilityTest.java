package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.datamodelling.api.DataModellingService;
import org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.Capability.*;
import static org.yawlfoundation.yawl.datamodelling.test.DataModellingTestFixtures.*;

/**
 * Group E — ODPS Validation Capability Tests (1 capability).
 *
 * <p>Tests cover:
 * - VALIDATE_ODPS: Validate ODPS v2 YAML structure against spec requirements
 *
 * <p>These tests use real ODPS fixtures, asserting validation passes for valid
 * schemas and properly rejects invalid/incomplete ODPS definitions without mocking
 * or stubbing the native bridge.
 *
 * <p>Skips if native library is not present (UnsupportedOperationException expected).
 */
@Tag("capability")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OdpsValidationCapabilityTest {

    DataModellingService service;

    @BeforeAll
    void setup() {
        assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
            "Native library not present — skipping ODPS validation tests");
        service = DataModellingModule.create();
    }

    @AfterAll
    void teardown() throws Exception {
        if (service != null) {
            service.close();
        }
    }

    @Nested
    @DisplayName("VALIDATE_ODPS")
    class ValidateOdps {

        @Test
        @CapabilityTest(VALIDATE_ODPS)
        @DisplayName("valid ODPS YAML does not throw")
        void validOdpsYaml_doesNotThrow() {
            assertDoesNotThrow(() -> service.validateOdps(ODPS_YAML),
                "Valid ODPS YAML must not throw exception");
        }

        @Test
        @CapabilityTest(VALIDATE_ODPS)
        @DisplayName("ODPS missing spec section throws DataModellingException")
        void missingSpecSection_throwsDataModellingException() {
            String invalidOdps = "apiVersion: v2\nkind: DataProduct\nmetadata:\n  name: test\n";
            assertThrows(DataModellingException.class,
                () -> service.validateOdps(invalidOdps),
                "ODPS missing spec must throw DataModellingException");
        }

        @Test
        @CapabilityTest(VALIDATE_ODPS)
        @DisplayName("ODPS missing apiVersion throws exception")
        void missingApiVersion_throwsDataModellingException() {
            String invalidOdps = "kind: DataProduct\nmetadata:\n  name: test\nspec:\n  tables: []\n";
            assertThrows(Exception.class,
                () -> service.validateOdps(invalidOdps),
                "ODPS missing apiVersion must throw exception");
        }
    }
}
