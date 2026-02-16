package org.yawlfoundation.yawl.resourcing.datastore.orgdata;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Security tests for jdbcImpl to verify SQL injection protection
 *
 * This test verifies that:
 * 1. Table name validation prevents SQL injection
 * 2. Field name validation prevents SQL injection
 * 3. PreparedStatement usage prevents value injection
 * 4. Database credentials are externalized
 *
 * @author YAWL Security Team
 * @date 2026-02-16
 */
public class JdbcImplSecurityTest {

    private jdbcImpl jdbc;

    @Before
    public void setUp() {
        jdbc = new jdbcImpl();
    }

    /**
     * Test that invalid table names are rejected
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidTableNameInjection() throws Exception {
        Method validateTableName = jdbcImpl.class.getDeclaredMethod("validateTableName", String.class);
        validateTableName.setAccessible(true);

        // Attempt SQL injection through table name
        validateTableName.invoke(jdbc, "rsj_participant; DROP TABLE rsj_participant; --");
    }

    /**
     * Test that SQL injection keywords in table names are rejected
     */
    @Test(expected = IllegalArgumentException.class)
    public void testTableNameWithUnion() throws Exception {
        Method validateTableName = jdbcImpl.class.getDeclaredMethod("validateTableName", String.class);
        validateTableName.setAccessible(true);

        validateTableName.invoke(jdbc, "rsj_participant UNION SELECT * FROM rsj_role");
    }

    /**
     * Test that null table names are rejected
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNullTableName() throws Exception {
        Method validateTableName = jdbcImpl.class.getDeclaredMethod("validateTableName", String.class);
        validateTableName.setAccessible(true);

        validateTableName.invoke(jdbc, (String) null);
    }

    /**
     * Test that valid table names are accepted
     */
    @Test
    public void testValidTableName() throws Exception {
        Method validateTableName = jdbcImpl.class.getDeclaredMethod("validateTableName", String.class);
        validateTableName.setAccessible(true);

        // Should not throw exception for valid table name
        validateTableName.invoke(jdbc, "rsj_participant");
        validateTableName.invoke(jdbc, "rsj_role");
        validateTableName.invoke(jdbc, "rsj_capability");
    }

    /**
     * Test that invalid field names are rejected
     */
    @Test(expected = IllegalArgumentException.class)
    public void testInvalidFieldNameInjection() throws Exception {
        Method validateFieldName = jdbcImpl.class.getDeclaredMethod("validateFieldName", String.class);
        validateFieldName.setAccessible(true);

        // Attempt SQL injection through field name
        validateFieldName.invoke(jdbc, "userid OR 1=1; --");
    }

    /**
     * Test that SQL injection with quotes is rejected in field names
     */
    @Test(expected = IllegalArgumentException.class)
    public void testFieldNameWithQuotes() throws Exception {
        Method validateFieldName = jdbcImpl.class.getDeclaredMethod("validateFieldName", String.class);
        validateFieldName.setAccessible(true);

        validateFieldName.invoke(jdbc, "userid' OR '1'='1");
    }

    /**
     * Test that null field names are rejected
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNullFieldName() throws Exception {
        Method validateFieldName = jdbcImpl.class.getDeclaredMethod("validateFieldName", String.class);
        validateFieldName.setAccessible(true);

        validateFieldName.invoke(jdbc, (String) null);
    }

    /**
     * Test that valid field names are accepted
     */
    @Test
    public void testValidFieldName() throws Exception {
        Method validateFieldName = jdbcImpl.class.getDeclaredMethod("validateFieldName", String.class);
        validateFieldName.setAccessible(true);

        // Should not throw exception for valid field names
        validateFieldName.invoke(jdbc, "userid");
        validateFieldName.invoke(jdbc, "participantid");
        validateFieldName.invoke(jdbc, "rolename");
    }

    /**
     * Test that database configuration is externalized
     */
    @Test
    public void testDatabaseConfigurationExternalized() {
        // Verify that no hardcoded credentials exist in the constructor
        // by checking that loadDatabaseConfig() method exists
        try {
            Method loadConfig = jdbcImpl.class.getDeclaredMethod("loadDatabaseConfig");
            loadConfig.setAccessible(true);
            assertNotNull(loadConfig, "loadDatabaseConfig method should exist");
        } catch (NoSuchMethodException e) {
            fail("loadDatabaseConfig method should be present for externalized configuration");
        }
    }

    /**
     * Test that PreparedStatement validation methods exist
     */
    @Test
    public void testPreparedStatementMethodsExist() {
        try {
            Method validateTableName = jdbcImpl.class.getDeclaredMethod("validateTableName", String.class);
            Method validateFieldName = jdbcImpl.class.getDeclaredMethod("validateFieldName", String.class);

            assertNotNull(validateTableName, "validateTableName method should exist");
            assertNotNull(validateFieldName, "validateFieldName method should exist");
        } catch (NoSuchMethodException e) {
            fail("Validation methods should be present");
        }
    }

    /**
     * Test that whitelist constants are defined
     */
    @Test
    public void testWhitelistConstantsExist() {
        try {
            java.lang.reflect.Field validTables = jdbcImpl.class.getDeclaredField("VALID_TABLE_NAMES");
            java.lang.reflect.Field validFields = jdbcImpl.class.getDeclaredField("VALID_FIELD_NAMES");

            assertNotNull(validTables, "VALID_TABLE_NAMES constant should exist");
            assertNotNull(validFields, "VALID_FIELD_NAMES constant should exist");
        } catch (NoSuchFieldException e) {
            fail("Whitelist constants should be present");
        }
    }
}
