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

package org.yawlfoundation.yawl.qlever;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Registry for SPARQL capability mappings and testing coverage.
 * Provides validation that all capabilities are mapped in the engine
 * and tested across the test suite.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
public final class SparqlCapabilityRegistry {

    private SparqlCapabilityRegistry() {
        throw new UnsupportedOperationException("Instantiation not allowed");
    }

    /**
     * Checks that every SparqlCapability is covered by a @MapsToSparqlCapability
     * annotation on at least one method of the engine class.
     * Called from QLeverEmbeddedSparqlEngine static initializer.
     *
     * @param engineClass the engine class to check
     * @throws SparqlCapabilityRegistryException if capabilities are unmapped
     *         or if the enum count does not match TOTAL
     */
    public static void checkMappings(Class<?> engineClass) {
        if (SparqlCapability.values().length != SparqlCapability.TOTAL) {
            throw new SparqlCapabilityRegistryException(
                "SparqlCapability enum has " + SparqlCapability.values().length
                + " values but TOTAL=" + SparqlCapability.TOTAL
            );
        }

        Set<SparqlCapability> mapped = EnumSet.noneOf(SparqlCapability.class);
        for (Method m : engineClass.getDeclaredMethods()) {
            MapsToSparqlCapability ann = m.getAnnotation(MapsToSparqlCapability.class);
            if (ann != null) {
                Collections.addAll(mapped, ann.value());
            }
        }

        Set<SparqlCapability> unmapped = EnumSet.allOf(SparqlCapability.class);
        unmapped.removeAll(mapped);

        if (!unmapped.isEmpty()) {
            throw new SparqlCapabilityRegistryException(
                unmapped.size() + " capabilities unmapped in " + engineClass.getSimpleName()
                + ": " + unmapped
            );
        }
    }

    /**
     * Checks that every SparqlCapability has at least one @SparqlCapabilityTest
     * annotation across the provided test classes.
     * Called from SparqlTestFixtures @BeforeAll.
     *
     * @param testClasses the test classes to check
     * @throws SparqlCapabilityRegistryException if capabilities are untested
     */
    public static void assertAllTested(Class<?>... testClasses) {
        Set<SparqlCapability> tested = EnumSet.noneOf(SparqlCapability.class);

        for (Class<?> testClass : testClasses) {
            for (Method m : testClass.getDeclaredMethods()) {
                SparqlCapabilityTest single = m.getAnnotation(SparqlCapabilityTest.class);
                if (single != null) {
                    tested.add(single.value());
                }

                SparqlCapabilityTests container = m.getAnnotation(SparqlCapabilityTests.class);
                if (container != null) {
                    for (SparqlCapabilityTest t : container.value()) {
                        tested.add(t.value());
                    }
                }
            }
        }

        Set<SparqlCapability> untested = EnumSet.allOf(SparqlCapability.class);
        untested.removeAll(tested);

        if (!untested.isEmpty()) {
            throw new SparqlCapabilityRegistryException(
                untested.size() + " capabilities have no @SparqlCapabilityTest: "
                + untested
            );
        }
    }
}
