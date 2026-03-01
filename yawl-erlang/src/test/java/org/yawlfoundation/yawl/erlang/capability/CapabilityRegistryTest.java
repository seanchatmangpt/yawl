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
package org.yawlfoundation.yawl.erlang.capability;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CapabilityRegistry}.
 *
 * <p>Verifies that the startup gate correctly detects missing implementations
 * and test coverage, and passes when all capabilities are fully declared.</p>
 */
class CapabilityRegistryTest {

    // -----------------------------------------------------------------------
    // Fixtures — minimal annotated classes for registry population
    // -----------------------------------------------------------------------

    @MapsToCapability(value = Capability.LAUNCH_CASE, layer = "L2")
    @MapsToCapability(value = Capability.CHECK_CONFORMANCE, layer = "L2")
    @MapsToCapability(value = Capability.SUBSCRIBE_TO_EVENTS, layer = "L2")
    @MapsToCapability(value = Capability.RELOAD_MODULE, layer = "L2")
    @MapsToCapability(value = Capability.LOAD_BINARY_MODULE, layer = "L2")
    @MapsToCapability(value = Capability.ROLLBACK_MODULE, layer = "L2")
    @MapsToCapability(value = Capability.AS_RPC_CALLABLE, layer = "L2")
    static class FullL2Impl {}

    @MapsToCapability(value = Capability.LAUNCH_CASE, layer = "L3")
    @MapsToCapability(value = Capability.CHECK_CONFORMANCE, layer = "L3")
    @MapsToCapability(value = Capability.SUBSCRIBE_TO_EVENTS, layer = "L3")
    @MapsToCapability(value = Capability.RELOAD_MODULE, layer = "L3")
    @MapsToCapability(value = Capability.LOAD_BINARY_MODULE, layer = "L3")
    @MapsToCapability(value = Capability.ROLLBACK_MODULE, layer = "L3")
    @MapsToCapability(value = Capability.AS_RPC_CALLABLE, layer = "L3")
    static class FullL3Impl {}

    static class FullTestClass {
        @CapabilityTest(Capability.LAUNCH_CASE)
        void testLaunchCase() {}

        @CapabilityTest(Capability.CHECK_CONFORMANCE)
        void testCheckConformance() {}

        @CapabilityTest(Capability.SUBSCRIBE_TO_EVENTS)
        void testSubscribeToEvents() {}

        @CapabilityTest(Capability.RELOAD_MODULE)
        void testReloadModule() {}

        @CapabilityTest(Capability.LOAD_BINARY_MODULE)
        void testLoadBinaryModule() {}

        @CapabilityTest(Capability.ROLLBACK_MODULE)
        void testRollbackModule() {}

        @CapabilityTest(Capability.AS_RPC_CALLABLE)
        void testAsRpcCallable() {}
    }

    @MapsToCapability(value = Capability.LAUNCH_CASE, layer = "L2")
    // Missing all others
    static class PartialL2Impl {}

    @MapsToCapability(value = Capability.LAUNCH_CASE, layer = "L3")
    // Missing all others
    static class PartialL3Impl {}

    static class PartialTestClass {
        @CapabilityTest(Capability.LAUNCH_CASE)
        void testLaunchCase() {}
        // Missing all others
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    void validate_passesWhenAllCapabilitiesFullyCovered() {
        CapabilityRegistry registry = new CapabilityRegistry(
                List.of(FullL2Impl.class),
                List.of(FullL3Impl.class),
                List.of(FullTestClass.class));

        assertDoesNotThrow(registry::validate,
                "Registry with complete L2/L3/Test coverage must pass validation");
    }

    @Test
    void validate_throwsWhenL2ImplementationMissing() {
        CapabilityRegistry registry = new CapabilityRegistry(
                List.of(PartialL2Impl.class),   // missing 6 capabilities
                List.of(FullL3Impl.class),
                List.of(FullTestClass.class));

        CapabilityRegistryException ex = assertThrows(CapabilityRegistryException.class,
                registry::validate,
                "Registry with missing L2 implementations must throw");

        assertFalse(ex.getMissingL2().isEmpty(), "missingL2 must be non-empty");
        assertTrue(ex.getMissingL2().contains(Capability.CHECK_CONFORMANCE));
        assertTrue(ex.getMissingL2().contains(Capability.SUBSCRIBE_TO_EVENTS));
        assertTrue(ex.getMissingL2().contains(Capability.RELOAD_MODULE));
        assertTrue(ex.getMissingL2().contains(Capability.LOAD_BINARY_MODULE));
        assertTrue(ex.getMissingL2().contains(Capability.ROLLBACK_MODULE));
        assertTrue(ex.getMissingL2().contains(Capability.AS_RPC_CALLABLE));
    }

    @Test
    void validate_throwsWhenL3ImplementationMissing() {
        CapabilityRegistry registry = new CapabilityRegistry(
                List.of(FullL2Impl.class),
                List.of(PartialL3Impl.class),   // missing 6 capabilities
                List.of(FullTestClass.class));

        CapabilityRegistryException ex = assertThrows(CapabilityRegistryException.class,
                registry::validate);

        assertFalse(ex.getMissingL3().isEmpty());
        assertTrue(ex.getMissingL2().isEmpty(), "L2 must be fully covered");
    }

    @Test
    void validate_throwsWhenTestCoverageMissing() {
        CapabilityRegistry registry = new CapabilityRegistry(
                List.of(FullL2Impl.class),
                List.of(FullL3Impl.class),
                List.of(PartialTestClass.class));  // only LAUNCH_CASE tested

        CapabilityRegistryException ex = assertThrows(CapabilityRegistryException.class,
                registry::validate);

        assertFalse(ex.getMissingTests().isEmpty());
        assertTrue(ex.getMissingTests().contains(Capability.CHECK_CONFORMANCE));
        assertFalse(ex.getMissingTests().contains(Capability.LAUNCH_CASE),
                "LAUNCH_CASE is covered by the partial test class");
    }

    @Test
    void validate_exceptionMessageContainsMissingCapabilities() {
        CapabilityRegistry registry = new CapabilityRegistry(
                List.of(),
                List.of(FullL3Impl.class),
                List.of(FullTestClass.class));

        CapabilityRegistryException ex = assertThrows(CapabilityRegistryException.class,
                registry::validate);

        String msg = ex.getMessage();
        assertTrue(msg.contains("Layer 2"), "Message must mention Layer 2");
        assertTrue(msg.contains("LAUNCH_CASE"), "Message must name missing capability");
    }

    @Test
    void validate_hasMissingReturnsTrueOnFailure() {
        CapabilityRegistry registry = new CapabilityRegistry(
                List.of(), List.of(), List.of());

        CapabilityRegistryException ex = assertThrows(CapabilityRegistryException.class,
                registry::validate);

        assertTrue(ex.hasMissing());
    }

    @Test
    void constructor_rejectsNullLists() {
        assertThrows(IllegalArgumentException.class,
                () -> new CapabilityRegistry(null, List.of(), List.of()),
                "null layer2Classes must throw");
        assertThrows(IllegalArgumentException.class,
                () -> new CapabilityRegistry(List.of(), null, List.of()),
                "null layer3Classes must throw");
        assertThrows(IllegalArgumentException.class,
                () -> new CapabilityRegistry(List.of(), List.of(), null),
                "null testClasses must throw");
    }

    @Test
    void capability_totalSentinelIsExcludedFromValidation() {
        // TOTAL must not appear in any missing list — it is excluded from registry checks
        CapabilityRegistry registry = new CapabilityRegistry(
                List.of(FullL2Impl.class),
                List.of(FullL3Impl.class),
                List.of(FullTestClass.class));

        // Must not throw — TOTAL is sentinel and should not be required
        assertDoesNotThrow(registry::validate);
    }
}
