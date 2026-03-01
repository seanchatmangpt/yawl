package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.datamodelling.api.DataModellingService;
import org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h;
import org.yawlfoundation.yawl.datamodelling.model.BusinessDomain;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.Capability.*;
import static org.yawlfoundation.yawl.datamodelling.test.DataModellingTestFixtures.*;

/**
 * Group I — DataFlow Migration Capability Tests (1 capability).
 *
 * <p>Tests cover:
 * - MIGRATE_DATAFLOW_TO_DOMAIN: Convert YAML dataflow diagram to structured BusinessDomain model
 *
 * <p>These tests use real dataflow fixtures, asserting structural properties
 * (non-null results, valid domain names, proper error handling) without mocking
 * or stubbing the native bridge.
 *
 * <p>Skips if native library is not present (UnsupportedOperationException expected).
 */
@Tag("capability")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataFlowCapabilityTest {

    DataModellingService service;

    @BeforeAll
    void setup() {
        assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
            "Native library not present — skipping DataFlow tests");
        service = DataModellingModule.create();
    }

    @AfterAll
    void teardown() throws Exception {
        if (service != null) {
            service.close();
        }
    }

    @Nested
    @DisplayName("MIGRATE_DATAFLOW_TO_DOMAIN")
    class MigrateDataflowToDomain {

        @Test
        @CapabilityTest(MIGRATE_DATAFLOW_TO_DOMAIN)
        @DisplayName("valid YAML returns non-null BusinessDomain")
        void validYaml_returnsNonNullDomain() throws Exception {
            BusinessDomain domain = service.migrateDataflowToDomain(DATAFLOW_YAML);
            assertNotNull(domain, "DataFlow migration must return non-null BusinessDomain");
        }

        @Test
        @CapabilityTest(MIGRATE_DATAFLOW_TO_DOMAIN)
        @DisplayName("migrated domain has non-null name")
        void migratedDomain_hasNonNullName() throws Exception {
            BusinessDomain domain = service.migrateDataflowToDomain(DATAFLOW_YAML);
            assertNotNull(domain.name(), "Migrated domain must have a non-null name");
        }

        @Test
        @CapabilityTest(MIGRATE_DATAFLOW_TO_DOMAIN)
        @DisplayName("invalid YAML throws DataModellingException")
        void invalidYaml_throwsDataModellingException() {
            assertThrows(DataModellingException.class,
                () -> service.migrateDataflowToDomain("garbage: not: a: dataflow:::"),
                "Invalid YAML must throw DataModellingException");
        }
    }
}
