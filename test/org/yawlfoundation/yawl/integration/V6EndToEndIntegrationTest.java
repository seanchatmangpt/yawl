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

package org.yawlfoundation.yawl.integration;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpServer;

/**
 * V6 integration tests covering MCP server construction, guard conditions, and
 * YSpecificationID record behaviour. These tests do not require a live YAWL
 * engine and run cleanly in the standalone test environment.
 *
 * <p>A2A server HTTP lifecycle tests are covered by:
 * {@code org.yawlfoundation.yawl.integration.a2a.YawlA2AServerTest}
 *
 * <p>Test areas:
 * <ul>
 *   <li>MCP server construction validation (all guard conditions)</li>
 *   <li>MCP server state before start (not running, null internal server)</li>
 *   <li>MCP server logging handler initialised at construction</li>
 *   <li>MCP server stop before start is a no-op</li>
 *   <li>MCP server start fails cleanly with no engine (IOException)</li>
 *   <li>YSpecificationID construction and field access</li>
 *   <li>YSpecificationID equality (same components = equal)</li>
 *   <li>YSpecificationID inequality (different components = not equal)</li>
 *   <li>YSpecificationID version string formatting</li>
 *   <li>YSpecificationID toString is non-empty</li>
 *   <li>YSpecificationID hashCode contract (equal objects, equal hashCode)</li>
 *   <li>YSpecificationID comparison with null (equals returns false)</li>
 * </ul>
 *
 * <p>Chicago TDD: real objects, real APIs, no mocks.
 *
 * @author YAWL Engine Team - V6 integration 2026-02-17
 */
public class V6EndToEndIntegrationTest extends TestCase {

    private static final String ENGINE_URL = "http://localhost:8080/yawl";
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "YAWL";

    public V6EndToEndIntegrationTest(String name) {
        super(name);
    }

    // =========================================================================
    //  MCP Server: construction and state tests
    // =========================================================================

    /**
     * Verifies that YawlMcpServer rejects null engine URL with IllegalArgumentException.
     */
    public void testMcpServerRejectsNullEngineUrl() {
        try {
            new YawlMcpServer(null, TEST_USERNAME, TEST_PASSWORD);
            fail("YawlMcpServer must not accept null engine URL");
        } catch (IllegalArgumentException e) {
            assertNotNull("Exception must have a message", e.getMessage());
            assertTrue("Message must reference URL or engine",
                    e.getMessage().toLowerCase().contains("url") ||
                    e.getMessage().toLowerCase().contains("engine"));
        }
    }

    /**
     * Verifies that YawlMcpServer rejects empty engine URL.
     */
    public void testMcpServerRejectsEmptyEngineUrl() {
        try {
            new YawlMcpServer("", TEST_USERNAME, TEST_PASSWORD);
            fail("YawlMcpServer must not accept empty engine URL");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    /**
     * Verifies that YawlMcpServer rejects null username.
     */
    public void testMcpServerRejectsNullUsername() {
        try {
            new YawlMcpServer(ENGINE_URL, null, TEST_PASSWORD);
            fail("YawlMcpServer must not accept null username");
        } catch (IllegalArgumentException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    /**
     * Verifies that YawlMcpServer rejects empty username.
     */
    public void testMcpServerRejectsEmptyUsername() {
        try {
            new YawlMcpServer(ENGINE_URL, "", TEST_PASSWORD);
            fail("YawlMcpServer must not accept empty username");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    /**
     * Verifies that YawlMcpServer rejects null password.
     */
    public void testMcpServerRejectsNullPassword() {
        try {
            new YawlMcpServer(ENGINE_URL, TEST_USERNAME, null);
            fail("YawlMcpServer must not accept null password");
        } catch (IllegalArgumentException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    /**
     * Verifies that YawlMcpServer rejects empty password.
     */
    public void testMcpServerRejectsEmptyPassword() {
        try {
            new YawlMcpServer(ENGINE_URL, TEST_USERNAME, "");
            fail("YawlMcpServer must not accept empty password");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    /**
     * Verifies that YawlMcpServer can be constructed with valid parameters
     * and is in the not-running state before start().
     */
    public void testMcpServerConstructionAndInitialState() {
        YawlMcpServer server = new YawlMcpServer(ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);
        assertNotNull("YawlMcpServer must be constructable with valid params", server);
        assertFalse("MCP server must not be running before start()", server.isRunning());
    }

    /**
     * Verifies that the MCP server's internal McpServer reference is null before
     * start() is called.
     */
    public void testMcpServerInternalStateBeforeStart() {
        YawlMcpServer server = new YawlMcpServer(ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);
        assertNull("Internal McpServer must be null before start()", server.getMcpServer());
    }

    /**
     * Verifies that MCP server's logging handler is initialized at construction.
     */
    public void testMcpServerLoggingHandlerInitializedAtConstruction() {
        YawlMcpServer server = new YawlMcpServer(ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);
        assertNotNull("Logging handler must be initialized in constructor",
                server.getLoggingHandler());
    }

    /**
     * Verifies that stop() before start() on MCP server does not throw.
     */
    public void testMcpServerStopBeforeStartIsNoOp() {
        YawlMcpServer server = new YawlMcpServer(ENGINE_URL, TEST_USERNAME, TEST_PASSWORD);
        try {
            server.stop();
        } catch (Exception e) {
            fail("MCP server stop() before start() must not throw: " + e.getMessage());
        }
        assertFalse("MCP server must not be running after stop-before-start", server.isRunning());
    }

    /**
     * Verifies that starting MCP server with no live YAWL engine fails and leaves
     * the server in a not-running state. The failure may be IOException (connection
     * refused) or IllegalArgumentException (restricted HTTP header in test env).
     */
    public void testMcpServerStartFailsWithNoEngine() {
        YawlMcpServer server = new YawlMcpServer(
                "http://localhost:19998/yawl", TEST_USERNAME, TEST_PASSWORD);
        try {
            server.start();
            // If start succeeds, stop the server and fail
            server.stop();
            fail("MCP server start() must fail when engine is not reachable");
        } catch (java.io.IOException e) {
            // Expected in production: connection refused to non-existent engine
            assertNotNull("IOException must have a message", e.getMessage());
            assertFalse("Server must not be running after failed start", server.isRunning());
        } catch (Exception e) {
            // Also acceptable: other exceptions when engine is not reachable
            // (e.g. IllegalArgumentException from restricted HTTP headers in test env)
            assertFalse("Server must not be running after failed start", server.isRunning());
        }
    }

    /**
     * Verifies that multiple independent MCP server instances can be constructed
     * with different parameters, all in the not-running state.
     */
    public void testMultipleMcpServerInstancesAreIndependent() {
        YawlMcpServer server1 = new YawlMcpServer(ENGINE_URL, "admin1", "pass1");
        YawlMcpServer server2 = new YawlMcpServer(ENGINE_URL, "admin2", "pass2");

        assertNotNull("Server 1 must be constructed", server1);
        assertNotNull("Server 2 must be constructed", server2);
        assertFalse("Server 1 must not be running", server1.isRunning());
        assertFalse("Server 2 must not be running", server2.isRunning());

        // Different instances must be distinct objects
        assertNotSame("Server instances must be distinct objects", server1, server2);
    }

    // =========================================================================
    //  YSpecificationID: record/value type tests
    // =========================================================================

    /**
     * Verifies that YSpecificationID can be constructed with the three required
     * components (identifier, version, uri) and they are accessible.
     */
    public void testYSpecificationIdConstruction() {
        YSpecificationID specId = new YSpecificationID("OrderProcessing", "1.0",
                "OrderProcessing.yawl");
        assertNotNull("YSpecificationID must not be null", specId);
        assertEquals("Identifier must match", "OrderProcessing", specId.getIdentifier());
        assertEquals("URI must match", "OrderProcessing.yawl", specId.getUri());
    }

    /**
     * Verifies that two YSpecificationID instances with the same identifier,
     * version, and URI are equal (value type equality).
     */
    public void testYSpecificationIdEquality() {
        YSpecificationID id1 = new YSpecificationID("Spec-A", "2.0", "Spec-A.yawl");
        YSpecificationID id2 = new YSpecificationID("Spec-A", "2.0", "Spec-A.yawl");
        assertEquals("YSpecificationID instances with same components must be equal",
                id1, id2);
    }

    /**
     * Verifies that equal YSpecificationIDs have the same hashCode (hashCode contract).
     */
    public void testYSpecificationIdEqualObjectsHaveSameHashCode() {
        YSpecificationID id1 = new YSpecificationID("Spec-B", "1.0", "Spec-B.yawl");
        YSpecificationID id2 = new YSpecificationID("Spec-B", "1.0", "Spec-B.yawl");
        assertEquals("Equal YSpecificationIDs must have same hashCode",
                id1.hashCode(), id2.hashCode());
    }

    /**
     * Verifies that two YSpecificationID instances with different identifiers are
     * not equal.
     */
    public void testYSpecificationIdInequality() {
        YSpecificationID id1 = new YSpecificationID("Spec-A", "1.0", "Spec-A.yawl");
        YSpecificationID id2 = new YSpecificationID("Spec-B", "1.0", "Spec-B.yawl");
        assertFalse("YSpecificationIDs with different identifiers must not be equal",
                id1.equals(id2));
    }

    /**
     * Verifies that YSpecificationID with different versions are not equal.
     */
    public void testYSpecificationIdVersionDifferenceProducesInequality() {
        YSpecificationID id1 = new YSpecificationID("Spec-C", "1.0", "Spec-C.yawl");
        YSpecificationID id2 = new YSpecificationID("Spec-C", "2.0", "Spec-C.yawl");
        assertFalse("YSpecificationIDs with different versions must not be equal",
                id1.equals(id2));
    }

    /**
     * Verifies that getVersionAsString() returns a non-null, non-empty version string
     * matching the version provided at construction.
     */
    public void testYSpecificationIdVersionString() {
        YSpecificationID specId = new YSpecificationID("TestSpec", "3.0", "TestSpec.yawl");
        String versionStr = specId.getVersionAsString();
        assertNotNull("getVersionAsString() must return non-null", versionStr);
        assertFalse("getVersionAsString() must return non-empty string", versionStr.isEmpty());
        assertEquals("Version string must match '3.0'", "3.0", versionStr);
    }

    /**
     * Verifies that toString() on YSpecificationID returns a non-empty string.
     */
    public void testYSpecificationIdToStringIsNonEmpty() {
        YSpecificationID specId = new YSpecificationID("InvoiceApproval", "1.5",
                "InvoiceApproval.yawl");
        String str = specId.toString();
        assertNotNull("toString() must not return null", str);
        assertFalse("toString() must not return empty string", str.isEmpty());
    }

    /**
     * Verifies that equals() returns false when compared to null.
     */
    public void testYSpecificationIdEqualsNullReturnsFalse() {
        YSpecificationID specId = new YSpecificationID("NullTest", "1.0", "NullTest.yawl");
        assertFalse("YSpecificationID.equals(null) must return false",
                specId.equals(null));
    }

    /**
     * Verifies that equals() is reflexive: an object equals itself.
     */
    public void testYSpecificationIdEqualsIsReflexive() {
        YSpecificationID specId = new YSpecificationID("Reflexive", "1.0", "Reflexive.yawl");
        assertEquals("YSpecificationID must equal itself", specId, specId);
    }

    // =========================================================================
    //  Test suite
    // =========================================================================

    public static Test suite() {
        TestSuite suite = new TestSuite("V6 Integration Tests");
        suite.addTestSuite(V6EndToEndIntegrationTest.class);
        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
