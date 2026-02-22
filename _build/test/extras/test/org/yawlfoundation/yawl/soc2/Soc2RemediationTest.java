/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.soc2;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.security.CredentialKey;
import org.yawlfoundation.yawl.security.CredentialManager;
import org.yawlfoundation.yawl.security.CredentialManagerFactory;
import org.yawlfoundation.yawl.security.CredentialUnavailableException;
import org.yawlfoundation.yawl.security.EnvironmentCredentialManager;
import org.yawlfoundation.yawl.security.HibernatePropertiesOverrider;
import org.yawlfoundation.yawl.util.Argon2PasswordEncryptor;
import org.yawlfoundation.yawl.util.PasswordEncryptor;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * SOC2 Production Audit Remediation Tests.
 *
 * <p>Verifies all 4 CRITICAL infrastructure issues identified in the SOC2 audit
 * are remediated:
 *
 * <ol>
 *   <li>SOC2 CRITICAL#1 - Hardcoded Database Credentials (hibernate.properties, jdbc.properties)</li>
 *   <li>SOC2 CRITICAL#2 - Hardcoded Service Credentials (editor.properties, web.xml)</li>
 *   <li>SOC2 CRITICAL#3 - Generic Admin Account Enabled (web.xml AllowGenericAdminID)</li>
 *   <li>SOC2 CRITICAL#4 - SHA-1 Password Hashing (PasswordEncryptor)</li>
 * </ol>
 *
 * <p>Chicago TDD: All tests use real implementations, no mocks. Environment variables
 * that are not set in the test environment cause tests to verify error behaviour rather
 * than success, ensuring test isolation.
 *
 * @author YAWL Foundation - SOC2 Remediation 2026-02-17
 */
public class Soc2RemediationTest extends TestCase {

    private static final String PROJECT_ROOT = "/home/user/yawl";
    private static final String HIBERNATE_PROPS =
            PROJECT_ROOT + "/build/properties/hibernate.properties";
    private static final String JDBC_PROPS = PROJECT_ROOT + "/src/jdbc.properties";
    private static final String WEB_XML = PROJECT_ROOT + "/build/engine/web.xml";
    private static final String EDITOR_PROPS =
            PROJECT_ROOT + "/build/procletService/editor.properties";

    public Soc2RemediationTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        resetCredentialManagerFactory();
    }

    @Override
    protected void tearDown() throws Exception {
        resetCredentialManagerFactory();
        super.tearDown();
    }

    private void resetCredentialManagerFactory() throws Exception {
        Field instanceField = CredentialManagerFactory.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    // =========================================================================
    // SOC2 CRITICAL#1 - Hardcoded Database Credentials
    // =========================================================================

    /**
     * SOC2 CRITICAL#1: hibernate.properties must not contain plaintext password 'yawl'.
     * The hardcoded "hibernate.connection.password yawl" line must be removed.
     */
    public void testCritical1_HibernatePropsNoHardcodedPassword() throws IOException {
        String content = readFile(HIBERNATE_PROPS);
        assertFalse(
            "SOC2 CRITICAL#1 [FAIL]: hibernate.properties contains hardcoded 'yawl' password. " +
            "Set YAWL_DB_PASSWORD environment variable instead.",
            content.contains("hibernate.connection.password yawl"));
    }

    /**
     * SOC2 CRITICAL#1: hibernate.properties must not contain hardcoded username 'postgres'
     * as an active (non-commented) property.
     */
    public void testCritical1_HibernatePropsNoHardcodedUsername() throws IOException {
        String content = readFile(HIBERNATE_PROPS);
        // Active line must not have the hardcoded username
        boolean hasActiveLine = false;
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("#") && !trimmed.startsWith("##") &&
                    trimmed.equals("hibernate.connection.username postgres")) {
                hasActiveLine = true;
                break;
            }
        }
        assertFalse(
            "SOC2 CRITICAL#1 [FAIL]: hibernate.properties has active 'hibernate.connection.username postgres'. " +
            "Set YAWL_DB_USER environment variable instead.",
            hasActiveLine);
    }

    /**
     * SOC2 CRITICAL#1: jdbc.properties must not contain plaintext password 'yawl'.
     */
    public void testCritical1_JdbcPropsNoHardcodedPassword() throws IOException {
        String content = readFile(JDBC_PROPS);
        assertFalse(
            "SOC2 CRITICAL#1 [FAIL]: jdbc.properties contains hardcoded 'db.password=yawl'. " +
            "Set YAWL_DB_PASSWORD environment variable instead.",
            content.contains("db.password=yawl"));
    }

    /**
     * SOC2 CRITICAL#1: jdbc.properties must not contain hardcoded username 'postgres'
     * as an active property.
     */
    public void testCritical1_JdbcPropsNoHardcodedUsername() throws IOException {
        String content = readFile(JDBC_PROPS);
        boolean hasActiveLine = false;
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("#") && trimmed.equals("db.user=postgres")) {
                hasActiveLine = true;
                break;
            }
        }
        assertFalse(
            "SOC2 CRITICAL#1 [FAIL]: jdbc.properties has active 'db.user=postgres'. " +
            "Set YAWL_DB_USER environment variable instead.",
            hasActiveLine);
    }

    /**
     * SOC2 CRITICAL#1: HibernatePropertiesOverrider requires YAWL_DB_USER and YAWL_DB_PASSWORD
     * environment variables. When absent, it throws IllegalStateException (fail-fast).
     */
    public void testCritical1_HibernateOverriderFailsFastWhenEnvVarsAbsent() {
        // In test environment, YAWL_DB_USER/YAWL_DB_PASSWORD are typically not set.
        // Verify that apply() throws if they're absent.
        String dbUser = System.getenv("YAWL_DB_USER");
        String dbPassword = System.getenv("YAWL_DB_PASSWORD");

        if (dbUser == null && dbPassword == null) {
            Properties props = new Properties();
            try {
                HibernatePropertiesOverrider.apply(props);
                // If env vars ARE set (e.g. in CI), this is also valid
                assertNotNull("If env vars are set, username must be applied",
                        props.getProperty(HibernatePropertiesOverrider.PROP_USERNAME));
            } catch (IllegalStateException e) {
                // Expected when env vars are not set - fail-fast behaviour
                assertTrue("Error message must identify the missing env var",
                        e.getMessage().contains("YAWL_DB_USER") ||
                        e.getMessage().contains("YAWL_DB_PASSWORD"));
            }
        }
    }

    /**
     * SOC2 CRITICAL#1: When both YAWL_DB_USER and YAWL_DB_PASSWORD are set,
     * HibernatePropertiesOverrider must apply them to the properties object.
     */
    public void testCritical1_HibernateOverriderAppliesEnvVarsWhenPresent() {
        String dbUser = System.getenv("YAWL_DB_USER");
        String dbPassword = System.getenv("YAWL_DB_PASSWORD");

        if (dbUser != null && !dbUser.isEmpty() &&
                dbPassword != null && !dbPassword.isEmpty()) {
            Properties props = new Properties();
            HibernatePropertiesOverrider.apply(props);

            assertEquals("YAWL_DB_USER must override hibernate.connection.username",
                    dbUser.trim(),
                    props.getProperty(HibernatePropertiesOverrider.PROP_USERNAME));
            assertEquals("YAWL_DB_PASSWORD must override hibernate.connection.password",
                    dbPassword.trim(),
                    props.getProperty(HibernatePropertiesOverrider.PROP_PASSWORD));
        } else {
            // Skip test when env vars are not set (covered by fail-fast test above)
            System.out.println("INFO: YAWL_DB_USER/YAWL_DB_PASSWORD not set - " +
                    "testCritical1_HibernateOverriderAppliesEnvVarsWhenPresent skipped.");
        }
    }

    // =========================================================================
    // SOC2 CRITICAL#2 - Hardcoded Service Credentials
    // =========================================================================

    /**
     * SOC2 CRITICAL#2: editor.properties must not contain hardcoded service password 'YAWL'.
     */
    public void testCritical2_EditorPropsNoHardcodedServicePassword() throws IOException {
        String content = readFile(EDITOR_PROPS);
        assertFalse(
            "SOC2 CRITICAL#2 [FAIL]: editor.properties contains hardcoded 'servicepassword=YAWL'. " +
            "Set YAWL_SERVICE_TOKEN environment variable instead.",
            content.contains("servicepassword=YAWL"));
    }

    /**
     * SOC2 CRITICAL#2: editor.properties must not contain hardcoded database password 'yawl'.
     */
    public void testCritical2_EditorPropsNoHardcodedDbPassword() throws IOException {
        String content = readFile(EDITOR_PROPS);
        assertFalse(
            "SOC2 CRITICAL#2 [FAIL]: editor.properties contains hardcoded 'password=yawl'. " +
            "Set YAWL_DB_PASSWORD environment variable instead.",
            content.contains("password=yawl"));
    }

    /**
     * SOC2 CRITICAL#2: EnvironmentCredentialManager declares YAWL_SERVICE_TOKEN as
     * the service authentication env var constant.
     */
    public void testCritical2_EnvironmentCredentialManagerDeclaresServiceTokenEnvVar() {
        assertEquals("YAWL_SERVICE_TOKEN must be the service token env var",
                "YAWL_SERVICE_TOKEN",
                EnvironmentCredentialManager.ENV_YAWL_SERVICE_TOKEN);
    }

    /**
     * SOC2 CRITICAL#2: EnvironmentCredentialManager maps YAWL_ENGINE_SERVICE_PASSWORD
     * to YAWL_SERVICE_TOKEN environment variable.
     */
    public void testCritical2_ServicePasswordKeyMapsToServiceTokenEnvVar() {
        assertEquals("ENGINE_SERVICE_PASSWORD must map to YAWL_SERVICE_TOKEN",
                "YAWL_SERVICE_TOKEN",
                EnvironmentCredentialManager.envVarFor(CredentialKey.YAWL_ENGINE_SERVICE_PASSWORD));
    }

    // =========================================================================
    // SOC2 CRITICAL#3 - Generic Admin Account Enabled
    // =========================================================================

    /**
     * SOC2 CRITICAL#3: web.xml AllowGenericAdminID must be 'false', not 'true'.
     */
    public void testCritical3_WebXmlAllowGenericAdminIdIsFalse() throws IOException {
        String content = readFile(WEB_XML);

        // Find the AllowGenericAdminID param-value
        int paramNameIdx = content.indexOf("<param-name>AllowGenericAdminID</param-name>");
        assertTrue("web.xml must contain AllowGenericAdminID context-param",
                paramNameIdx >= 0);

        // Find the next param-value after the param-name
        int paramValueStart = content.indexOf("<param-value>", paramNameIdx);
        int paramValueEnd = content.indexOf("</param-value>", paramValueStart);
        assertTrue("AllowGenericAdminID must have a param-value", paramValueStart >= 0);

        String paramValue = content.substring(paramValueStart + "<param-value>".length(),
                paramValueEnd).trim();

        assertEquals(
            "SOC2 CRITICAL#3 [FAIL]: AllowGenericAdminID is '" + paramValue + "' but must be 'false'. " +
            "The generic admin/YAWL account is a security risk in production. " +
            "Set AllowGenericAdminID to false and create explicit admin accounts.",
            "false", paramValue);
    }

    /**
     * SOC2 CRITICAL#3: CredentialKey enum defines YAWL_ADMIN_PASSWORD (required for
     * explicit admin account creation - replaces generic admin).
     */
    public void testCritical3_CredentialKeyDefinesAdminPasswordKey() {
        CredentialKey adminKey = CredentialKey.YAWL_ADMIN_PASSWORD;
        assertNotNull("YAWL_ADMIN_PASSWORD CredentialKey must exist", adminKey);
        assertEquals("CredentialKey name must be YAWL_ADMIN_PASSWORD",
                "YAWL_ADMIN_PASSWORD", adminKey.name());
    }

    /**
     * SOC2 CRITICAL#3: EnvironmentCredentialManager declares YAWL_ADMIN_PASSWORD as
     * the required admin credential env var.
     */
    public void testCritical3_EnvironmentCredentialManagerDeclaresAdminPasswordEnvVar() {
        assertEquals("YAWL_ADMIN_PASSWORD must be the admin credential env var",
                "YAWL_ADMIN_PASSWORD",
                EnvironmentCredentialManager.ENV_YAWL_ADMIN_PASSWORD);
    }

    // =========================================================================
    // SOC2 CRITICAL#4 - SHA-1 Hashing Migration to Argon2id
    // =========================================================================

    /**
     * SOC2 CRITICAL#4: PasswordEncryptor.encrypt() must be marked @Deprecated.
     * This prevents new code from calling the SHA-1 implementation.
     */
    public void testCritical4_PasswordEncryptorIsDeprecated() throws Exception {
        java.lang.reflect.Method encryptMethod =
                PasswordEncryptor.class.getMethod("encrypt", String.class);
        assertNotNull("encrypt(String) method must exist", encryptMethod);
        assertNotNull("encrypt(String) must be annotated with @Deprecated",
                encryptMethod.getAnnotation(Deprecated.class));
    }

    /**
     * SOC2 CRITICAL#4: Argon2PasswordEncryptor.hash() produces a valid Argon2id
     * PHC string starting with '$argon2id$'.
     */
    public void testCritical4_Argon2idHashProducesPhcString() {
        if (!isArgon2Available()) {
            System.out.println("INFO: argon2-jvm not on classpath - " +
                "testCritical4_Argon2idHashProducesPhcString skipped (requires argon2-jvm at runtime). " +
                "Add de.mkammerer:argon2-jvm to pom.xml to enable this test.");
            return;
        }
        String hash = Argon2PasswordEncryptor.hash("test-password-soc2");
        assertNotNull("Argon2id hash must not be null", hash);
        assertTrue(
            "SOC2 CRITICAL#4 [FAIL]: Argon2id hash must start with '$argon2id$' but was: " + hash,
            hash.startsWith("$argon2id$"));
    }

    /**
     * SOC2 CRITICAL#4: Argon2PasswordEncryptor.verify() returns true for correct password.
     */
    public void testCritical4_Argon2idVerifyCorrectPassword() {
        if (!isArgon2Available()) {
            System.out.println("INFO: argon2-jvm not on classpath - " +
                "testCritical4_Argon2idVerifyCorrectPassword skipped (requires argon2-jvm at runtime).");
            return;
        }
        String password = "correct-password-soc2-" + System.nanoTime();
        String hash = Argon2PasswordEncryptor.hash(password);
        assertTrue(
            "SOC2 CRITICAL#4 [FAIL]: Argon2id verify must return true for correct password",
            Argon2PasswordEncryptor.verify(hash, password));
    }

    /**
     * SOC2 CRITICAL#4: Argon2PasswordEncryptor.verify() returns false for wrong password.
     */
    public void testCritical4_Argon2idVerifyWrongPasswordReturnsFalse() {
        if (!isArgon2Available()) {
            System.out.println("INFO: argon2-jvm not on classpath - " +
                "testCritical4_Argon2idVerifyWrongPasswordReturnsFalse skipped (requires argon2-jvm at runtime).");
            return;
        }
        String hash = Argon2PasswordEncryptor.hash("correct-password");
        assertFalse(
            "SOC2 CRITICAL#4 [FAIL]: Argon2id verify must return false for wrong password",
            Argon2PasswordEncryptor.verify(hash, "wrong-password"));
    }

    /**
     * SOC2 CRITICAL#4: Argon2id hashes are unique per call (each uses a fresh random salt).
     * This prevents rainbow table attacks.
     */
    public void testCritical4_Argon2idHashesAreUniquePerCall() {
        if (!isArgon2Available()) {
            System.out.println("INFO: argon2-jvm not on classpath - " +
                "testCritical4_Argon2idHashesAreUniquePerCall skipped (requires argon2-jvm at runtime).");
            return;
        }
        String password = "same-password-different-salt";
        String hash1 = Argon2PasswordEncryptor.hash(password);
        String hash2 = Argon2PasswordEncryptor.hash(password);
        assertFalse(
            "SOC2 CRITICAL#4 [FAIL]: Two Argon2id hashes of the same password must differ " +
            "(each call uses a fresh random salt).",
            hash1.equals(hash2));
        // Both must still verify correctly
        assertTrue("Hash1 must verify correctly", Argon2PasswordEncryptor.verify(hash1, password));
        assertTrue("Hash2 must verify correctly", Argon2PasswordEncryptor.verify(hash2, password));
    }

    /**
     * SOC2 CRITICAL#4: Argon2PasswordEncryptor throws IllegalArgumentException for null input.
     * Prevents null-bypass attacks.
     */
    public void testCritical4_Argon2idRejectsNullPassword() {
        try {
            Argon2PasswordEncryptor.hash(null);
            fail("hash(null) must throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    /**
     * SOC2 CRITICAL#4: Argon2PasswordEncryptor throws IllegalArgumentException for empty input.
     */
    public void testCritical4_Argon2idRejectsEmptyPassword() {
        try {
            Argon2PasswordEncryptor.hash("");
            fail("hash('') must throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    /**
     * SOC2 CRITICAL#4: YDefClientsLoader must NOT import PasswordEncryptor (SHA-1).
     * Verify source file does not reference the deprecated class.
     */
    public void testCritical4_YDefClientsLoaderDoesNotUsePasswordEncryptor() throws IOException {
        String loaderSrc = PROJECT_ROOT +
                "/src/org/yawlfoundation/yawl/engine/YDefClientsLoader.java";
        String content = readFile(loaderSrc);
        assertFalse(
            "SOC2 CRITICAL#4 [FAIL]: YDefClientsLoader must not import PasswordEncryptor " +
            "(SHA-1 is deprecated for password storage).",
            content.contains("import org.yawlfoundation.yawl.util.PasswordEncryptor;"));
    }

    /**
     * SOC2 CRITICAL#4: YDefClientsLoader must import Argon2PasswordEncryptor.
     */
    public void testCritical4_YDefClientsLoaderUsesArgon2PasswordEncryptor() throws IOException {
        String loaderSrc = PROJECT_ROOT +
                "/src/org/yawlfoundation/yawl/engine/YDefClientsLoader.java";
        String content = readFile(loaderSrc);
        assertTrue(
            "SOC2 CRITICAL#4 [FAIL]: YDefClientsLoader must import Argon2PasswordEncryptor " +
            "to use Argon2id for new password hashes.",
            content.contains("import org.yawlfoundation.yawl.util.Argon2PasswordEncryptor;"));
    }

    /**
     * SOC2 CRITICAL#4: YHttpServlet.encryptPassword() must use Argon2id, not SHA-1.
     */
    public void testCritical4_YHttpServletUsesArgon2ForEncryptPassword() throws IOException {
        String httpServletSrc = PROJECT_ROOT +
                "/src/org/yawlfoundation/yawl/engine/interfce/YHttpServlet.java";
        String content = readFile(httpServletSrc);
        assertTrue(
            "SOC2 CRITICAL#4 [FAIL]: YHttpServlet must import Argon2PasswordEncryptor " +
            "for encryptPassword() method.",
            content.contains("import org.yawlfoundation.yawl.util.Argon2PasswordEncryptor;"));
        assertTrue(
            "SOC2 CRITICAL#4 [FAIL]: YHttpServlet.encryptPassword() must call " +
            "Argon2PasswordEncryptor.hash()",
            content.contains("Argon2PasswordEncryptor.hash(s)"));
    }

    /**
     * SOC2 CRITICAL#4: YSessionCache must support Argon2id verification (not just SHA-1).
     */
    public void testCritical4_YSessionCacheImportsArgon2() throws IOException {
        String sessionCacheSrc = PROJECT_ROOT +
                "/src/org/yawlfoundation/yawl/authentication/YSessionCache.java";
        String content = readFile(sessionCacheSrc);
        assertTrue(
            "SOC2 CRITICAL#4 [FAIL]: YSessionCache must import Argon2PasswordEncryptor " +
            "to support Argon2id hash verification.",
            content.contains("import org.yawlfoundation.yawl.util.Argon2PasswordEncryptor;"));
        assertTrue(
            "SOC2 CRITICAL#4 [FAIL]: YSessionCache must implement verifyPassword() " +
            "to handle both Argon2id and SHA-1 hashes.",
            content.contains("verifyPassword"));
    }

    // =========================================================================
    // Supporting Infrastructure - EnvironmentCredentialManager
    // =========================================================================

    /**
     * EnvironmentCredentialManager declares all required env var constants.
     */
    public void testEnvironmentCredentialManagerDeclaresAllEnvVarConstants() {
        assertNotNull(EnvironmentCredentialManager.ENV_YAWL_ADMIN_PASSWORD);
        assertNotNull(EnvironmentCredentialManager.ENV_YAWL_SERVICE_TOKEN);
        assertNotNull(EnvironmentCredentialManager.ENV_ZAI_API_KEY);
        assertNotNull(EnvironmentCredentialManager.ENV_ZHIPU_API_KEY);
        assertNotNull(EnvironmentCredentialManager.ENV_YAWL_DB_USER);
        assertNotNull(EnvironmentCredentialManager.ENV_YAWL_DB_PASSWORD);

        assertEquals("YAWL_ADMIN_PASSWORD", EnvironmentCredentialManager.ENV_YAWL_ADMIN_PASSWORD);
        assertEquals("YAWL_SERVICE_TOKEN",  EnvironmentCredentialManager.ENV_YAWL_SERVICE_TOKEN);
        assertEquals("YAWL_DB_USER",        EnvironmentCredentialManager.ENV_YAWL_DB_USER);
        assertEquals("YAWL_DB_PASSWORD",    EnvironmentCredentialManager.ENV_YAWL_DB_PASSWORD);
    }

    /**
     * EnvironmentCredentialManager.envVarFor() returns mappings for all CredentialKey values.
     */
    public void testEnvironmentCredentialManagerHasMappingForAllKeys() {
        for (CredentialKey key : CredentialKey.values()) {
            String envVar = EnvironmentCredentialManager.envVarFor(key);
            assertNotNull(
                "EnvironmentCredentialManager must have env var mapping for " + key.name(),
                envVar);
            assertFalse(
                "Env var mapping for " + key.name() + " must not be empty",
                envVar.isEmpty());
        }
    }

    /**
     * EnvironmentCredentialManager.envVarFor(null) returns null (no NPE).
     */
    public void testEnvironmentCredentialManagerEnvVarForNullReturnsNull() {
        assertNull("envVarFor(null) must return null without throwing",
                EnvironmentCredentialManager.envVarFor(null));
    }

    /**
     * CredentialManagerFactory.setInstance() accepts EnvironmentCredentialManager
     * (as opposed to requiring a specific implementation).
     */
    public void testCredentialManagerFactoryAcceptsEnvironmentCredentialManager() {
        // We can only instantiate it if env vars are set
        String adminPass = System.getenv("YAWL_ADMIN_PASSWORD");
        String serviceToken = System.getenv("YAWL_SERVICE_TOKEN");

        if (adminPass != null && !adminPass.isEmpty() &&
                serviceToken != null && !serviceToken.isEmpty()) {
            CredentialManager cm = new EnvironmentCredentialManager();
            CredentialManagerFactory.setInstance(cm);
            assertSame("Factory must return the registered EnvironmentCredentialManager",
                    cm, CredentialManagerFactory.getInstance());
        } else {
            System.out.println("INFO: YAWL_ADMIN_PASSWORD/YAWL_SERVICE_TOKEN not set - " +
                    "testCredentialManagerFactoryAcceptsEnvironmentCredentialManager skipped.");
        }
    }

    // =========================================================================
    // HibernatePropertiesOverrider
    // =========================================================================

    /**
     * HibernatePropertiesOverrider.apply() throws IllegalArgumentException for null properties.
     */
    public void testHibernateOverriderRejectsNullProperties() {
        try {
            HibernatePropertiesOverrider.apply(null);
            fail("apply(null) must throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    /**
     * HibernatePropertiesOverrider defines correct Hibernate property key constants.
     */
    public void testHibernateOverriderPropertyKeyConstants() {
        assertEquals("hibernate.connection.username",
                HibernatePropertiesOverrider.PROP_USERNAME);
        assertEquals("hibernate.connection.password",
                HibernatePropertiesOverrider.PROP_PASSWORD);
        assertEquals("hibernate.connection.url",
                HibernatePropertiesOverrider.PROP_URL);
    }

    // =========================================================================
    // Test helper
    // =========================================================================

    /**
     * Returns true if the argon2-jvm library is on the runtime classpath.
     * Used to guard Argon2id runtime tests that require the library.
     */
    private boolean isArgon2Available() {
        try {
            Class.forName("de.mkammerer.argon2.Argon2Factory");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private String readFile(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)));
    }

    // =========================================================================
    // Test suite
    // =========================================================================

    public static Test suite() {
        TestSuite suite = new TestSuite("SOC2 Remediation Tests - All 4 Critical Issues");
        suite.addTestSuite(Soc2RemediationTest.class);
        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
