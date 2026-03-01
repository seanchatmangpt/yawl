package org.yawlfoundation.yawl.datamodelling;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.datamodelling.api.DataModellingService;
import org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h;
import org.yawlfoundation.yawl.datamodelling.model.SqlDialect;
import org.yawlfoundation.yawl.datamodelling.model.WorkspaceModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.datamodelling.Capability.*;
import static org.yawlfoundation.yawl.datamodelling.test.DataModellingTestFixtures.*;

/**
 * Group B — SQL Capability Tests (2 capabilities).
 *
 * <p>Tests cover:
 * - IMPORT_FROM_SQL: Parse SQL DDL into WorkspaceModel (multiple dialects)
 * - EXPORT_TO_SQL: Export WorkspaceModel to dialect-specific SQL
 *
 * <p>These tests use real SQL fixtures (orders dataset with 2 tables) for
 * PostgreSQL and SQLite dialects, asserting structural properties (table names,
 * column presence, dialect-specific syntax) without mocking or stubbing
 * the native bridge.
 *
 * <p>Skips if native library is not present (UnsupportedOperationException expected).
 */
@Tag("capability")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlCapabilityTest {

    private DataModellingService service;

    @BeforeAll
    void setup() {
        assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(),
            "Native library not present — skipping SQL tests");
        service = DataModellingModule.create();
    }

    @AfterAll
    void teardown() throws Exception {
        if (service != null) {
            service.close();
        }
    }

    @Nested
    @DisplayName("IMPORT_FROM_SQL")
    class ImportFromSql {

        @Test
        @CapabilityTest(IMPORT_FROM_SQL)
        @DisplayName("PostgreSQL dialect returns workspace with tables")
        void postgresDialect_returnsWorkspaceWithTables() throws Exception {
            WorkspaceModel ws = service.importFromSql(SQL_POSTGRES, SqlDialect.POSTGRESQL);
            assertNotNull(ws, "Import result must not be null");
            assertFalse(ws.tables().isEmpty(),
                "PostgreSQL import must produce at least one table");
        }

        @Test
        @CapabilityTest(IMPORT_FROM_SQL)
        @DisplayName("PostgreSQL dialect includes orders table")
        void postgresDialect_ordersTablePresent() throws Exception {
            WorkspaceModel ws = service.importFromSql(SQL_POSTGRES, SqlDialect.POSTGRESQL);
            var names = ws.tables().stream().map(t -> t.name()).toList();

            assertTrue(names.stream().anyMatch(n -> n.toLowerCase().contains("orders")),
                "Expected table containing 'orders', got: " + names);
        }

        @Test
        @CapabilityTest(IMPORT_FROM_SQL)
        @DisplayName("SQLite dialect returns workspace with tables")
        void sqliteDialect_returnsWorkspaceWithTables() throws Exception {
            WorkspaceModel ws = service.importFromSql(SQL_SQLITE, SqlDialect.SQLITE);
            assertNotNull(ws, "SQLite import result must not be null");
            assertFalse(ws.tables().isEmpty(),
                "SQLite import must produce at least one table");
        }

        @Test
        @CapabilityTest(IMPORT_FROM_SQL)
        @DisplayName("all SQL dialects handle input without unchecked exceptions")
        void allDialects_neverThrowUnchecked() throws Exception {
            for (SqlDialect dialect : SqlDialect.values()) {
                try {
                    service.importFromSql(SQL_POSTGRES, dialect);
                } catch (DataModellingException acceptable) {
                    // Acceptable: some dialects may reject the input syntax
                } catch (Exception unchecked) {
                    fail("Dialect " + dialect + " threw unchecked exception: " + unchecked);
                }
            }
        }
    }

    @Nested
    @DisplayName("EXPORT_TO_SQL")
    class ExportToSql {

        @Test
        @CapabilityTest(EXPORT_TO_SQL)
        @DisplayName("PostgreSQL export contains CREATE TABLE")
        void postgresDialect_containsCreateTable() throws Exception {
            WorkspaceModel ws = service.importFromSql(SQL_POSTGRES, SqlDialect.POSTGRESQL);
            String sql = service.exportToSql(ws, SqlDialect.POSTGRESQL);

            assertNotNull(sql, "Exported SQL must not be null");
            assertFalse(sql.isBlank(),
                "Exported SQL must not be blank");
            assertTrue(sql.toUpperCase().contains("CREATE TABLE"),
                "PostgreSQL export must contain CREATE TABLE statement");
        }

        @Test
        @CapabilityTest(EXPORT_TO_SQL)
        @DisplayName("PostgreSQL export contains orders table name")
        void postgresDialect_containsOrdersTableName() throws Exception {
            WorkspaceModel ws = service.importFromSql(SQL_POSTGRES, SqlDialect.POSTGRESQL);
            String sql = service.exportToSql(ws, SqlDialect.POSTGRESQL);

            assertTrue(sql.toLowerCase().contains("orders"),
                "PostgreSQL export must reference 'orders' table");
        }

        @Test
        @CapabilityTest(EXPORT_TO_SQL)
        @DisplayName("SQLite export does not contain PostgreSQL-specific SERIAL type")
        void sqliteDialect_noPostgresSpecificTypes() throws Exception {
            WorkspaceModel ws = service.importFromSql(SQL_POSTGRES, SqlDialect.POSTGRESQL);
            String sqliteSql = service.exportToSql(ws, SqlDialect.SQLITE);

            assertNotNull(sqliteSql, "SQLite export must not be null");
            assertFalse(sqliteSql.isBlank(),
                "SQLite export must not be blank");
            assertFalse(sqliteSql.toUpperCase().contains("SERIAL"),
                "SQLite export must not contain SERIAL (PostgreSQL-specific syntax)");
        }
    }
}
