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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import org.junit.Assume;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Integration test: Validates MCP server class availability and public API contract.
 *
 * <p>Checks that the Model Context Protocol server infrastructure is compiled and
 * accessible without starting a live server or connecting to a YAWL engine.
 * All tests use reflection-based classpath validation.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>YawlMcpServer is on the integration module classpath</li>
 *   <li>YawlMcpServer is a public, concrete, non-abstract class</li>
 *   <li>Three-argument constructor (engineUrl, username, password) is accessible</li>
 *   <li>isRunning() returns false before start() is called</li>
 *   <li>getMcpServer() returns null before start() is called</li>
 *   <li>getLoggingHandler() returns non-null immediately after construction</li>
 *   <li>stop() before start() does not throw</li>
 *   <li>YawlMcpServer rejects null engine URL</li>
 *   <li>YawlMcpServer rejects empty engine URL</li>
 *   <li>YawlMcpServer rejects null username</li>
 *   <li>YawlMcpServer rejects null password</li>
 *   <li>Two independently-constructed server instances are distinct objects</li>
 *   <li>Required public lifecycle methods are present: start, stop, isRunning</li>
 *   <li>Integration test is in the correct package</li>
 *   <li>Java 21+ runtime requirement is met</li>
 * </ul>
 *
 * <p>Chicago TDD: real objects, real APIs, no mocks.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("integration")
public class YMcpServerAvailabilityIT {

    private static final String ENGINE_URL = "http://localhost:8080/yawl";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "YAWL";

    /**
     * Loads YawlMcpServer and skips all tests in this class if it is not compiled.
     * This allows the test suite to pass even in build configurations that exclude
     * the MCP bridge (e.g. when MCP SDK is not available).
     */
    private Class<?> loadMcpServerClass() {
        try {
            return Class.forName("org.yawlfoundation.yawl.integration.mcp.YawlMcpServer");
        } catch (ClassNotFoundException e) {
            Assume.assumeTrue(
                    "YawlMcpServer not compiled in this build configuration — skipping MCP tests",
                    false);
            return null; // unreachable, satisfies compiler
        }
    }

    private Object newMcpServer(Class<?> mcpClass, String url, String user, String pass)
            throws Exception {
        return mcpClass
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance(url, user, pass);
    }

    // =========================================================================
    // Class loading and structure
    // =========================================================================

    @Test
    public void mcpServer_classIsOnClasspath() {
        Class<?> mcpClass = loadMcpServerClass();
        assertNotNull("YawlMcpServer class must be loadable from integration module", mcpClass);
        assertEquals("org.yawlfoundation.yawl.integration.mcp.YawlMcpServer",
                mcpClass.getName());
    }

    @Test
    public void mcpServer_isPublicConcreteClass() {
        Class<?> mcpClass = loadMcpServerClass();
        int mods = mcpClass.getModifiers();
        assertTrue("YawlMcpServer must be public", Modifier.isPublic(mods));
        assertFalse("YawlMcpServer must not be abstract", Modifier.isAbstract(mods));
        assertFalse("YawlMcpServer must not be an interface", mcpClass.isInterface());
    }

    @Test
    public void mcpServer_hasThreeArgConstructor() throws Exception {
        Class<?> mcpClass = loadMcpServerClass();
        // Must not throw NoSuchMethodException
        java.lang.reflect.Constructor<?> ctor = mcpClass
                .getDeclaredConstructor(String.class, String.class, String.class);
        assertNotNull("Three-arg constructor (engineUrl, username, password) must exist", ctor);
        assertTrue("Constructor must be public", Modifier.isPublic(ctor.getModifiers()));
    }

    @Test
    public void mcpServer_hasRequiredLifecycleMethods() {
        Class<?> mcpClass = loadMcpServerClass();
        Set<String> methodNames = Arrays.stream(mcpClass.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue("YawlMcpServer must have start()",     methodNames.contains("start"));
        assertTrue("YawlMcpServer must have stop()",      methodNames.contains("stop"));
        assertTrue("YawlMcpServer must have isRunning()", methodNames.contains("isRunning"));
    }

    // =========================================================================
    // Construction and initial state
    // =========================================================================

    @Test
    public void mcpServer_constructsWithValidParameters() throws Exception {
        Class<?> mcpClass = loadMcpServerClass();
        Object server = newMcpServer(mcpClass, ENGINE_URL, USERNAME, PASSWORD);
        assertNotNull("YawlMcpServer must be constructable with valid parameters", server);
    }

    @Test
    public void mcpServer_isNotRunningBeforeStart() throws Exception {
        Class<?> mcpClass = loadMcpServerClass();
        Object server = newMcpServer(mcpClass, ENGINE_URL, USERNAME, PASSWORD);
        Method isRunning = mcpClass.getMethod("isRunning");
        assertFalse("MCP server must not be running before start() is called",
                (Boolean) isRunning.invoke(server));
    }

    @Test
    public void mcpServer_internalServerIsNullBeforeStart() throws Exception {
        Class<?> mcpClass = loadMcpServerClass();
        Object server = newMcpServer(mcpClass, ENGINE_URL, USERNAME, PASSWORD);
        Method getMcpServer = mcpClass.getMethod("getMcpServer");
        assertNull("Internal McpServer reference must be null before start()",
                getMcpServer.invoke(server));
    }

    @Test
    public void mcpServer_loggingHandlerInitializedAtConstruction() throws Exception {
        Class<?> mcpClass = loadMcpServerClass();
        Object server = newMcpServer(mcpClass, ENGINE_URL, USERNAME, PASSWORD);
        Method getLoggingHandler = mcpClass.getMethod("getLoggingHandler");
        assertNotNull("Logging handler must be initialized at construction",
                getLoggingHandler.invoke(server));
    }

    // =========================================================================
    // Guard conditions — invalid constructor arguments
    // =========================================================================

    @Test
    public void mcpServer_rejectsNullEngineUrl() throws Exception {
        Class<?> mcpClass = loadMcpServerClass();
        try {
            newMcpServer(mcpClass, null, USERNAME, PASSWORD);
            fail("YawlMcpServer must not accept null engine URL");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            assertTrue("Cause must be IllegalArgumentException, was: " + ite.getCause(),
                    ite.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void mcpServer_rejectsEmptyEngineUrl() throws Exception {
        Class<?> mcpClass = loadMcpServerClass();
        try {
            newMcpServer(mcpClass, "", USERNAME, PASSWORD);
            fail("YawlMcpServer must not accept empty engine URL");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            assertTrue("Cause must be IllegalArgumentException, was: " + ite.getCause(),
                    ite.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void mcpServer_rejectsNullUsername() throws Exception {
        Class<?> mcpClass = loadMcpServerClass();
        try {
            newMcpServer(mcpClass, ENGINE_URL, null, PASSWORD);
            fail("YawlMcpServer must not accept null username");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            assertTrue("Cause must be IllegalArgumentException, was: " + ite.getCause(),
                    ite.getCause() instanceof IllegalArgumentException);
        }
    }

    @Test
    public void mcpServer_rejectsNullPassword() throws Exception {
        Class<?> mcpClass = loadMcpServerClass();
        try {
            newMcpServer(mcpClass, ENGINE_URL, USERNAME, null);
            fail("YawlMcpServer must not accept null password");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            assertTrue("Cause must be IllegalArgumentException, was: " + ite.getCause(),
                    ite.getCause() instanceof IllegalArgumentException);
        }
    }

    // =========================================================================
    // Lifecycle edge cases
    // =========================================================================

    @Test
    public void mcpServer_stopBeforeStartIsNoOp() throws Exception {
        Class<?> mcpClass = loadMcpServerClass();
        Object server = newMcpServer(mcpClass, ENGINE_URL, USERNAME, PASSWORD);
        Method stop = mcpClass.getMethod("stop");
        Method isRunning = mcpClass.getMethod("isRunning");

        try {
            stop.invoke(server);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            fail("stop() before start() must not throw, but threw: " + ite.getCause());
        }
        assertFalse("Server must not be running after stop-before-start",
                (Boolean) isRunning.invoke(server));
    }

    @Test
    public void mcpServer_twoInstancesAreDistinctObjects() throws Exception {
        Class<?> mcpClass = loadMcpServerClass();
        Object server1 = newMcpServer(mcpClass, ENGINE_URL, "user1", "pass1");
        Object server2 = newMcpServer(mcpClass, ENGINE_URL, "user2", "pass2");

        assertNotNull("Server 1 must be non-null", server1);
        assertNotNull("Server 2 must be non-null", server2);
        assertNotSame("Two MCP server instances must be distinct objects", server1, server2);
    }

    @Test
    public void mcpServer_startFailsGracefullyWithNoEngine() throws Exception {
        Class<?> mcpClass = loadMcpServerClass();
        // Use a port that is very unlikely to be in use
        Object server = newMcpServer(mcpClass, "http://localhost:19997/yawl", USERNAME, PASSWORD);
        Method start = mcpClass.getMethod("start");
        Method isRunning = mcpClass.getMethod("isRunning");

        try {
            start.invoke(server);
            // If start somehow succeeds, stop it and fail the test
            Method stop = mcpClass.getMethod("stop");
            stop.invoke(server);
            fail("start() must fail when no YAWL engine is reachable at the given URL");
        } catch (java.lang.reflect.InvocationTargetException ite) {
            // Expected: IOException (connection refused) or similar transport exception
            assertNotNull("Exception cause must be non-null", ite.getCause());
            assertFalse("Server must not be running after a failed start",
                    (Boolean) isRunning.invoke(server));
        }
    }

    // =========================================================================
    // Integration module package structure and Java version
    // =========================================================================

    @Test
    public void integrationTest_isInCorrectPackage() {
        assertEquals("This IT must live in org.yawlfoundation.yawl.integration",
                "org.yawlfoundation.yawl.integration",
                this.getClass().getPackageName());
    }

    @Test
    public void javaRuntime_meetsMinimumRequirementForYawlV6() {
        int version = Runtime.version().feature();
        assertTrue("YAWL v6 requires Java 21+, running on Java " + version, version >= 21);
    }
}
