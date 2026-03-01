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
 * Group A — ODCS Capability Tests (3 capabilities).
 *
 * <p>Tests cover:
 * - PARSE_ODCS_YAML: Parse ODCS v2 YAML into WorkspaceModel
 * - EXPORT_TO_ODCS_YAML: Export WorkspaceModel back to ODCS YAML
 * - CONVERT_TO_ODCS: Convert from other formats to ODCS WorkspaceModel
 *
 * <p>These tests use real ODCS fixtures (orders dataset with 2 tables),
 * asserting structural properties (table count, column names, etc.)
 * without mocking or stubbing the native bridge.
 *
 * <p>Skips if native library is not present (UnsupportedOperationException expected).
 */
@Tag("capability")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OdcsCapabilityTest {

    private DataModellingService service;

    @BeforeAll
    void setup() {
        assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
            "Native library not present — skipping ODCS tests");
        service = DataModellingModule.create();
    }

    @AfterAll
    void teardown() throws Exception {
        if (service != null) {
            service.close();
        }
    }

    @Nested
    @DisplayName("PARSE_ODCS_YAML")
    class ParseOdcsYaml {

        @Test
        @CapabilityTest(PARSE_ODCS_YAML)
        @DisplayName("orders fixture returns two tables")
        void ordersFixture_returnsTwoTables() throws Exception {
            WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
            assertNotNull(ws, "Parsed ODCS must return non-null WorkspaceModel");
            assertEquals(2, ws.tables().size(), "Orders fixture must have exactly 2 tables");
        }

        @Test
        @CapabilityTest(PARSE_ODCS_YAML)
        @DisplayName("orders fixture table names are correct")
        void ordersFixture_tableNamesCorrect() throws Exception {
            WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
            var names = ws.tables().stream().map(t -> t.name()).toList();
            assertTrue(names.contains("orders"),
                "Expected 'orders' table, got: " + names);
            assertTrue(names.contains("order_items"),
                "Expected 'order_items' table, got: " + names);
        }

        @Test
        @CapabilityTest(PARSE_ODCS_YAML)
        @DisplayName("orders table columns include order_id and customer_id")
        void ordersTable_columnsIncludeOrderIdAndCustomerId() throws Exception {
            WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
            var ordersTable = ws.tables().stream()
                .filter(t -> t.name().equals("orders"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("'orders' table not found"));

            assertFalse(ordersTable.columns().isEmpty(),
                "Orders table must have at least one column");
            assertTrue(ordersTable.columns().contains("order_id"),
                "Expected column 'order_id' in: " + ordersTable.columns());
            assertTrue(ordersTable.columns().contains("customer_id"),
                "Expected column 'customer_id' in: " + ordersTable.columns());
        }

        @Test
        @CapabilityTest(PARSE_ODCS_YAML)
        @DisplayName("invalid YAML throws DataModellingException")
        void invalidYaml_throwsDataModellingException() {
            assertThrows(DataModellingException.class,
                () -> service.parseOdcsYaml("not: valid: odcs: yaml:::"),
                "Parsing invalid YAML must throw DataModellingException");
        }
    }

    @Nested
    @DisplayName("EXPORT_TO_ODCS_YAML")
    class ExportToOdcsYaml {

        @Test
        @CapabilityTest(EXPORT_TO_ODCS_YAML)
        @DisplayName("exported output is non-blank")
        void output_isNonBlank() throws Exception {
            WorkspaceModel ws = service.parseOdcsYaml(ODCS_YAML);
            String yaml = service.exportToOdcsYaml(ws);
            assertNotNull(yaml, "Exported ODCS YAML must not be null");
            assertFalse(yaml.isBlank(),
                "Exported ODCS YAML must not be blank");
        }

        @Test
        @CapabilityTest(EXPORT_TO_ODCS_YAML)
        @DisplayName("roundtrip preserves table count")
        void roundtrip_preservesTableCount() throws Exception {
            WorkspaceModel original = service.parseOdcsYaml(ODCS_YAML);
            String exported = service.exportToOdcsYaml(original);
            WorkspaceModel roundtrip = service.parseOdcsYaml(exported);

            assertEquals(original.tables().size(), roundtrip.tables().size(),
                "Table count must be preserved in roundtrip");
        }
    }

    @Nested
    @DisplayName("CONVERT_TO_ODCS")
    class ConvertToOdcs {

        @Test
        @CapabilityTest(CONVERT_TO_ODCS)
        @DisplayName("from SQL returns workspace with tables")
        void fromSql_returnsWorkspaceWithTables() throws Exception {
            WorkspaceModel ws = service.convertToOdcs(SQL_POSTGRES, "sql");
            assertNotNull(ws, "Conversion result must not be null");
            assertFalse(ws.tables().isEmpty(),
                "Converted SQL must produce at least one table");
        }

        @Test
        @CapabilityTest(CONVERT_TO_ODCS)
        @DisplayName("null format auto-detects as ODCS YAML")
        void nullFormat_autoDetectsOdcsYaml() throws Exception {
            WorkspaceModel ws = service.convertToOdcs(ODCS_YAML, null);
            assertNotNull(ws, "Auto-detected ODCS conversion must return non-null result");
            assertFalse(ws.tables().isEmpty(),
                "Auto-detected conversion must produce tables");
        }
    }
}
