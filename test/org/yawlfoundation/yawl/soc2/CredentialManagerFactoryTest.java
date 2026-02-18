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

import java.lang.reflect.Field;

/**
 * SOC2 CC6.1 / CC6.9 - Credential Manager Tests.
 *
 * <p>Tests map to SOC 2 Trust Service Criteria:
 * CC6.1 - Logical access controls - credentials must be explicitly provisioned.
 * CC6.9 - The entity protects credentials (keys, tokens) from unauthorized disclosure.
 *
 * <p>Covers:
 * <ul>
 *   <li>getInstance() without registration throws UnsupportedOperationException (fail-fast)</li>
 *   <li>setInstance() with null throws IllegalArgumentException</li>
 *   <li>setInstance() registers and getInstance() returns it</li>
 *   <li>CredentialKey enum covers all expected keys</li>
 *   <li>Factory pattern enforces explicit configuration</li>
 * </ul>
 *
 * <p>Chicago TDD: real CredentialManagerFactory and real CredentialKey enum,
 * with a real in-process CredentialManager implementation for testing.
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
public class CredentialManagerFactoryTest extends TestCase {

    public CredentialManagerFactoryTest(String name) {
        super(name);
    }

    /**
     * Resets the CredentialManagerFactory singleton to null between tests using reflection,
     * ensuring each test starts from a clean state without interference.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        resetFactory();
    }

    @Override
    protected void tearDown() throws Exception {
        resetFactory();
        super.tearDown();
    }

    private static void resetFactory() throws Exception {
        Field instanceField = CredentialManagerFactory.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    // =========================================================================
    // CC6.1 - Fail-fast when not configured
    // =========================================================================

    /**
     * SOC2 CC6.1: getInstance() without prior setInstance() must throw
     * UnsupportedOperationException. This enforces explicit configuration -
     * no credential access without proper vault setup.
     */
    public void testGetInstanceWithoutRegistrationThrows() {
        try {
            CredentialManagerFactory.getInstance();
            fail("Expected UnsupportedOperationException when no CredentialManager registered");
        } catch (UnsupportedOperationException e) {
            assertNotNull("Exception must have a message", e.getMessage());
            assertTrue("Error must guide operator to configure vault",
                    e.getMessage().toLowerCase().contains("credential") ||
                    e.getMessage().toLowerCase().contains("vault") ||
                    e.getMessage().toLowerCase().contains("register"));
        }
    }

    // =========================================================================
    // CC6.9 - setInstance validation
    // =========================================================================

    /**
     * SOC2 CC6.9: setInstance(null) must throw - prevent clearing the security boundary.
     */
    public void testSetInstanceNullThrows() {
        try {
            CredentialManagerFactory.setInstance(null);
            fail("Expected IllegalArgumentException for null CredentialManager");
        } catch (IllegalArgumentException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    // =========================================================================
    // CC6.1 - Registration and retrieval
    // =========================================================================

    /**
     * SOC2 CC6.1: After setInstance(), getInstance() must return the registered manager.
     */
    public void testSetAndGetInstanceReturnsRegistered() throws Exception {
        TestCredentialManager testManager = new TestCredentialManager();
        CredentialManagerFactory.setInstance(testManager);

        CredentialManager retrieved = CredentialManagerFactory.getInstance();
        assertNotNull("getInstance() must not return null after registration", retrieved);
        assertSame("getInstance() must return the exact registered instance",
                testManager, retrieved);
    }

    /**
     * SOC2 CC6.1: The registered manager can retrieve credentials by key.
     */
    public void testRegisteredManagerCanRetrieveCredentials() throws Exception {
        TestCredentialManager testManager = new TestCredentialManager();
        CredentialManagerFactory.setInstance(testManager);

        CredentialManager cm = CredentialManagerFactory.getInstance();
        String password = cm.getCredential(CredentialKey.YAWL_ADMIN_PASSWORD);
        assertNotNull("getCredential must not return null", password);
        assertFalse("getCredential must not return empty string", password.isEmpty());
    }

    // =========================================================================
    // CC6.9 - CredentialKey enum completeness
    // =========================================================================

    /**
     * SOC2 CC6.9: All expected credential types must have a CredentialKey entry.
     * This test prevents credential types being added without audit trail.
     */
    public void testCredentialKeyEnumHasRequiredEntries() {
        CredentialKey[] keys = CredentialKey.values();
        assertTrue("Must have at least 5 credential keys defined", keys.length >= 5);

        // Verify each expected key exists
        assertKeyExists(CredentialKey.YAWL_ADMIN_PASSWORD, keys);
        assertKeyExists(CredentialKey.YAWL_ENGINE_SERVICE_PASSWORD, keys);
        assertKeyExists(CredentialKey.ZAI_API_KEY, keys);
        assertKeyExists(CredentialKey.ZHIPU_API_KEY, keys);
        assertKeyExists(CredentialKey.PROCLET_SERVICE_PASSWORD, keys);
    }

    private void assertKeyExists(CredentialKey expected, CredentialKey[] allKeys) {
        for (CredentialKey k : allKeys) {
            if (k == expected) return;
        }
        fail("CredentialKey." + expected.name() + " must exist in the enum");
    }

    /**
     * SOC2 CC6.9: CredentialKey values must be uniquely named (no duplicate credentials).
     */
    public void testCredentialKeyNamesAreUnique() {
        CredentialKey[] keys = CredentialKey.values();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (CredentialKey k : keys) {
            assertTrue("Duplicate CredentialKey name: " + k.name(), names.add(k.name()));
        }
    }

    // =========================================================================
    // CC6.1 - No re-registration in production (test only)
    // =========================================================================

    /**
     * SOC2 CC6.1: Verifies that setInstance can be called a second time
     * (test teardown requires this), but this behaviour is explicitly documented
     * as test-only. Documents the security expectation.
     */
    public void testSecondSetInstanceReplacesFirst() throws Exception {
        TestCredentialManager manager1 = new TestCredentialManager();
        TestCredentialManager manager2 = new TestCredentialManager();

        CredentialManagerFactory.setInstance(manager1);
        assertSame(manager1, CredentialManagerFactory.getInstance());

        // Replacement is permitted by the API (documented as test-only)
        CredentialManagerFactory.setInstance(manager2);
        assertSame("Second setInstance replaces first (test-only scenario)",
                manager2, CredentialManagerFactory.getInstance());
    }

    // =========================================================================
    // Test helper: minimal CredentialManager for testing
    // =========================================================================

    /**
     * Minimal real CredentialManager for testing. Returns fixed values
     * keyed by CredentialKey name. Does NOT use mocks.
     */
    private static final class TestCredentialManager implements CredentialManager {

        private static final java.util.Map<CredentialKey, String> STORE =
                java.util.Map.of(
                    CredentialKey.YAWL_ADMIN_PASSWORD,           "test-admin-pass-soc2",
                    CredentialKey.YAWL_ENGINE_SERVICE_PASSWORD,  "test-engine-svc-pass-soc2",
                    CredentialKey.ZAI_API_KEY,                   "test-zai-key-soc2",
                    CredentialKey.ZHIPU_API_KEY,                 "test-zhipu-key-soc2",
                    CredentialKey.PROCLET_SERVICE_PASSWORD,      "test-proclet-pass-soc2"
                );

        @Override
        public String getCredential(CredentialKey key) {
            if (key == null) {
                throw new IllegalArgumentException("key must not be null");
            }
            String value = STORE.get(key);
            if (value == null) {
                throw new CredentialUnavailableException(
                        key, "No test credential configured for this key");
            }
            return value;
        }

        @Override
        public void rotateCredential(CredentialKey key, String newValue) {
            if (key == null) {
                throw new IllegalArgumentException("key must not be null");
            }
            if (newValue == null || newValue.isEmpty()) {
                throw new IllegalArgumentException("newValue must not be null or empty");
            }
            // In the test implementation, rotation is accepted (no durable state)
        }
    }

    // =========================================================================
    // Test suite
    // =========================================================================

    public static Test suite() {
        return new TestSuite(CredentialManagerFactoryTest.class);
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
