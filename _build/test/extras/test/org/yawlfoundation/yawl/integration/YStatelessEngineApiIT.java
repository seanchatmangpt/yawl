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

import org.junit.Test;
import org.junit.Before;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Integration test: Validates YStatelessEngine public API contract.
 *
 * <p>Verifies the engine can be instantiated and its core methods are accessible
 * without requiring a live database (stateless mode). All checks use real
 * class loading and reflection against the compiled yawl-stateless module.
 *
 * <p>Test coverage:
 * <ul>
 *   <li>Class loading: YStatelessEngine is on the integration module classpath</li>
 *   <li>Default constructor: zero-arg construction succeeds</li>
 *   <li>Timed constructor: long-arg construction succeeds</li>
 *   <li>Case monitoring API: isCaseMonitoringEnabled, setCaseMonitoringEnabled</li>
 *   <li>Engine number accessor: getEngineNbr returns non-negative int</li>
 *   <li>Announcement control: enableMultiThreadedAnnouncements, isMultiThreadedAnnouncementsEnabled</li>
 *   <li>Listener registration API: add* and remove* methods present on public surface</li>
 *   <li>Core workflow methods: unmarshalSpecification, launchCase, startWorkItem,
 *       completeWorkItem, cancelCase, unloadCase, marshalCase, restoreCase</li>
 *   <li>Multi-threaded announcements off by default</li>
 *   <li>Case monitoring off by default (no-arg constructor)</li>
 *   <li>Independent engine instances have distinct engine numbers</li>
 * </ul>
 *
 * <p>Chicago TDD: real objects, real APIs, no mocks.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("integration")
public class YStatelessEngineApiIT {

    private Class<?> engineClass;

    @Before
    public void setUp() throws Exception {
        engineClass = Class.forName("org.yawlfoundation.yawl.stateless.YStatelessEngine");
    }

    // =========================================================================
    // Class loading
    // =========================================================================

    @Test
    public void statelessEngine_classIsLoadableFromIntegrationModule() {
        assertNotNull("YStatelessEngine class must be loadable on integration classpath", engineClass);
        assertEquals("org.yawlfoundation.yawl.stateless.YStatelessEngine", engineClass.getName());
    }

    @Test
    public void statelessEngine_isConcretePublicClass() {
        int mods = engineClass.getModifiers();
        assertTrue("YStatelessEngine must be public", Modifier.isPublic(mods));
        assertFalse("YStatelessEngine must not be abstract", Modifier.isAbstract(mods));
        assertFalse("YStatelessEngine must not be an interface", engineClass.isInterface());
    }

    // =========================================================================
    // Constructor validation
    // =========================================================================

    @Test
    public void statelessEngine_noArgConstructorInstantiatesEngine() throws Exception {
        Object engine = engineClass.getDeclaredConstructor().newInstance();
        assertNotNull("No-arg YStatelessEngine constructor must produce non-null instance", engine);
    }

    @Test
    public void statelessEngine_longArgConstructorInstantiatesEngine() throws Exception {
        Object engine = engineClass.getDeclaredConstructor(long.class).newInstance(60000L);
        assertNotNull("Long-arg YStatelessEngine constructor must produce non-null instance", engine);
    }

    @Test
    public void statelessEngine_negativeIdleTimeoutDisablesMonitoring() throws Exception {
        // A negative value disables case idle monitoring per the YStatelessEngine contract
        Object engine = engineClass.getDeclaredConstructor(long.class).newInstance(-1L);
        assertNotNull("YStatelessEngine(-1) must still construct successfully", engine);
        Method isCaseMonitoringEnabled = engineClass.getMethod("isCaseMonitoringEnabled");
        Boolean monitoring = (Boolean) isCaseMonitoringEnabled.invoke(engine);
        assertFalse("Case monitoring must be disabled when idle timeout is negative", monitoring);
    }

    // =========================================================================
    // Case monitoring API
    // =========================================================================

    @Test
    public void statelessEngine_caseMonitoringOffByDefaultWithNoArgConstructor() throws Exception {
        Object engine = engineClass.getDeclaredConstructor().newInstance();
        Method isCaseMonitoringEnabled = engineClass.getMethod("isCaseMonitoringEnabled");
        Boolean monitoring = (Boolean) isCaseMonitoringEnabled.invoke(engine);
        assertFalse("Case monitoring must be off by default with no-arg constructor", monitoring);
    }

    @Test
    public void statelessEngine_caseMonitoringCanBeEnabled() throws Exception {
        Object engine = engineClass.getDeclaredConstructor().newInstance();
        Method setCaseMonitoringEnabled = engineClass.getMethod("setCaseMonitoringEnabled", boolean.class);
        Method isCaseMonitoringEnabled = engineClass.getMethod("isCaseMonitoringEnabled");

        setCaseMonitoringEnabled.invoke(engine, true);
        Boolean monitoring = (Boolean) isCaseMonitoringEnabled.invoke(engine);
        assertTrue("Case monitoring must be enabled after setCaseMonitoringEnabled(true)", monitoring);
    }

    @Test
    public void statelessEngine_caseMonitoringCanBeDisabled() throws Exception {
        Object engine = engineClass.getDeclaredConstructor(long.class).newInstance(30000L);
        Method setCaseMonitoringEnabled = engineClass.getMethod("setCaseMonitoringEnabled", boolean.class);
        Method isCaseMonitoringEnabled = engineClass.getMethod("isCaseMonitoringEnabled");

        // Monitoring starts enabled (positive idle timeout)
        assertTrue("Case monitoring must be on after timed constructor",
                (Boolean) isCaseMonitoringEnabled.invoke(engine));

        setCaseMonitoringEnabled.invoke(engine, false);
        assertFalse("Case monitoring must be off after setCaseMonitoringEnabled(false)",
                (Boolean) isCaseMonitoringEnabled.invoke(engine));
    }

    // =========================================================================
    // Engine number
    // =========================================================================

    @Test
    public void statelessEngine_getEngineNbrReturnsNonNegativeInt() throws Exception {
        Object engine = engineClass.getDeclaredConstructor().newInstance();
        Method getEngineNbr = engineClass.getMethod("getEngineNbr");
        Integer nbr = (Integer) getEngineNbr.invoke(engine);
        assertNotNull("getEngineNbr() must return non-null", nbr);
        assertTrue("getEngineNbr() must return non-negative value, got: " + nbr, nbr >= 0);
    }

    @Test
    public void statelessEngine_twoInstancesHaveDistinctEngineNumbers() throws Exception {
        Object engine1 = engineClass.getDeclaredConstructor().newInstance();
        Object engine2 = engineClass.getDeclaredConstructor().newInstance();
        Method getEngineNbr = engineClass.getMethod("getEngineNbr");
        int nbr1 = (Integer) getEngineNbr.invoke(engine1);
        int nbr2 = (Integer) getEngineNbr.invoke(engine2);
        assertNotEquals("Two YStatelessEngine instances must have distinct engine numbers", nbr1, nbr2);
    }

    // =========================================================================
    // Multi-threaded announcement API
    // =========================================================================

    @Test
    public void statelessEngine_multiThreadedAnnouncementsOffByDefault() throws Exception {
        Object engine = engineClass.getDeclaredConstructor().newInstance();
        Method isEnabled = engineClass.getMethod("isMultiThreadedAnnouncementsEnabled");
        Boolean enabled = (Boolean) isEnabled.invoke(engine);
        assertFalse("Multi-threaded announcements must be off by default", enabled);
    }

    @Test
    public void statelessEngine_multiThreadedAnnouncementsCanBeEnabled() throws Exception {
        Object engine = engineClass.getDeclaredConstructor().newInstance();
        Method enable = engineClass.getMethod("enableMultiThreadedAnnouncements", boolean.class);
        Method isEnabled = engineClass.getMethod("isMultiThreadedAnnouncementsEnabled");

        enable.invoke(engine, true);
        assertTrue("Multi-threaded announcements must be on after enable(true)",
                (Boolean) isEnabled.invoke(engine));

        enable.invoke(engine, false);
        assertFalse("Multi-threaded announcements must be off after enable(false)",
                (Boolean) isEnabled.invoke(engine));
    }

    // =========================================================================
    // Required public API surface (listener registration + core workflow)
    // =========================================================================

    @Test
    public void statelessEngine_hasAllRequiredListenerRegistrationMethods() {
        Set<String> methodNames = Arrays.stream(engineClass.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        String[] requiredListenerMethods = {
            "addCaseEventListener",
            "addWorkItemEventListener",
            "addExceptionEventListener",
            "addLogEventListener",
            "addTimerEventListener",
            "removeCaseEventListener",
            "removeWorkItemEventListener",
            "removeExceptionEventListener",
            "removeLogEventListener",
            "removeTimerEventListener"
        };

        for (String method : requiredListenerMethods) {
            assertTrue("YStatelessEngine must have public method: " + method,
                    methodNames.contains(method));
        }
    }

    @Test
    public void statelessEngine_hasCoreWorkflowMethods() {
        Set<String> methodNames = Arrays.stream(engineClass.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        String[] requiredCoreMethods = {
            "unmarshalSpecification",
            "launchCase",
            "startWorkItem",
            "completeWorkItem",
            "cancelCase",
            "unloadCase",
            "marshalCase",
            "restoreCase",
            "suspendCase",
            "resumeCase"
        };

        for (String method : requiredCoreMethods) {
            assertTrue("YStatelessEngine must have core workflow method: " + method,
                    methodNames.contains(method));
        }
    }

    @Test
    public void statelessEngine_hasCaseStateManagementMethods() {
        Set<String> methodNames = Arrays.stream(engineClass.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue("YStatelessEngine must have suspendWorkItem", methodNames.contains("suspendWorkItem"));
        assertTrue("YStatelessEngine must have unsuspendWorkItem", methodNames.contains("unsuspendWorkItem"));
        assertTrue("YStatelessEngine must have rollbackWorkItem", methodNames.contains("rollbackWorkItem"));
        assertTrue("YStatelessEngine must have skipWorkItem", methodNames.contains("skipWorkItem"));
        assertTrue("YStatelessEngine must have createNewInstance", methodNames.contains("createNewInstance"));
        assertTrue("YStatelessEngine must have checkEligibilityToAddInstances",
                methodNames.contains("checkEligibilityToAddInstances"));
    }

    // =========================================================================
    // Java version requirement
    // =========================================================================

    @Test
    public void javaRuntime_meetsYawlV6Requirement() {
        int version = Runtime.version().feature();
        assertTrue("YAWL v6 requires Java 21+, found Java " + version, version >= 21);
    }
}
