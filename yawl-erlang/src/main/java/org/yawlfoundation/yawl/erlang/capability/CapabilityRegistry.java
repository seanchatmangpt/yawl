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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Startup gate that verifies the YAWL Erlang/OTP bridge module is completely
 * implemented and tested before the system enters service.
 *
 * <p>The registry operates by scanning registered classes for
 * {@link MapsToCapability} and {@link CapabilityTest} annotations, then
 * verifying that every declared {@link Capability} (except {@link Capability#TOTAL})
 * has at least one Layer 2 implementation, at least one Layer 3 implementation,
 * and at least one test method.</p>
 *
 * <p>If any capability is missing any of the three requirements, {@link #validate()}
 * throws {@link CapabilityRegistryException}, causing the deployment to fail
 * immediately rather than operating with missing capabilities.</p>
 *
 * <p>Usage:
 * <pre>
 *   CapabilityRegistry registry = new CapabilityRegistry(
 *       List.of(ErlangNode.class),          // Layer 2 classes
 *       List.of(ErlangBridge.class,         // Layer 3 classes
 *               HotReloadServiceImpl.class,
 *               YawlServiceSupervisor.class,
 *               SchemaContractRegistry.class,
 *               SchemaValidationInterceptor.class),
 *       List.of(ErlangBridgeCapabilityTest.class));  // Test classes
 *
 *   registry.validate();  // throws CapabilityRegistryException if incomplete
 * </pre>
 *
 * <p>The registry is intentionally simple: no classpath scanning, no dynamic
 * proxy generation, no reflection beyond annotation reading. The caller provides
 * the explicit class lists, making the completeness contract auditable from the
 * call site.</p>
 *
 * @see Capability
 * @see MapsToCapability
 * @see CapabilityTest
 * @see CapabilityRegistryException
 */
public final class CapabilityRegistry {

    private final List<Class<?>> layer2Classes;
    private final List<Class<?>> layer3Classes;
    private final List<Class<?>> testClasses;

    /**
     * Creates a registry with the specified class lists.
     *
     * @param layer2Classes classes implementing capabilities at Layer 2
     *                      (may include Panama FFM types internally)
     * @param layer3Classes classes implementing capabilities at Layer 3
     *                      (pure Java domain types only in public signatures)
     * @param testClasses   classes containing {@link CapabilityTest}-annotated methods
     * @throws IllegalArgumentException if any list is null
     */
    public CapabilityRegistry(
            List<Class<?>> layer2Classes,
            List<Class<?>> layer3Classes,
            List<Class<?>> testClasses) {
        if (layer2Classes == null) throw new IllegalArgumentException("layer2Classes must not be null");
        if (layer3Classes == null) throw new IllegalArgumentException("layer3Classes must not be null");
        if (testClasses == null) throw new IllegalArgumentException("testClasses must not be null");

        this.layer2Classes = List.copyOf(layer2Classes);
        this.layer3Classes = List.copyOf(layer3Classes);
        this.testClasses = List.copyOf(testClasses);
    }

    /**
     * Validates that every declared capability is fully implemented and tested.
     *
     * <p>For each {@link Capability} value (except {@link Capability#TOTAL}):
     * <ul>
     *   <li>At least one class or method in {@code layer2Classes} must have
     *       {@code @MapsToCapability(value=CAPABILITY, layer="L2")}</li>
     *   <li>At least one class or method in {@code layer3Classes} must have
     *       {@code @MapsToCapability(value=CAPABILITY, layer="L3")}</li>
     *   <li>At least one method in {@code testClasses} must have
     *       {@code @CapabilityTest(CAPABILITY)}</li>
     * </ul>
     *
     * @throws CapabilityRegistryException if any capability fails any of the three checks
     */
    public void validate() {
        Set<Capability> implementedL2 = scanMapsToCapability(layer2Classes, "L2");
        Set<Capability> implementedL3 = scanMapsToCapability(layer3Classes, "L3");
        Set<Capability> tested = scanCapabilityTests(testClasses);

        List<Capability> missingL2 = new ArrayList<>();
        List<Capability> missingL3 = new ArrayList<>();
        List<Capability> missingTests = new ArrayList<>();

        for (Capability cap : Capability.values()) {
            if (cap == Capability.TOTAL) continue;

            if (!implementedL2.contains(cap)) missingL2.add(cap);
            if (!implementedL3.contains(cap)) missingL3.add(cap);
            if (!tested.contains(cap)) missingTests.add(cap);
        }

        if (!missingL2.isEmpty() || !missingL3.isEmpty() || !missingTests.isEmpty()) {
            throw new CapabilityRegistryException(missingL2, missingL3, missingTests);
        }
    }

    /**
     * Returns the set of capabilities found as {@link MapsToCapability} annotations
     * on any class or method in the given class list, filtered by layer.
     */
    private Set<Capability> scanMapsToCapability(List<Class<?>> classes, String layer) {
        Set<Capability> found = EnumSet.noneOf(Capability.class);
        for (Class<?> cls : classes) {
            collectFromElement(cls, layer, found);
            for (Method method : cls.getDeclaredMethods()) {
                collectFromElement(method, layer, found);
            }
        }
        return found;
    }

    private void collectFromElement(AnnotatedElement element, String layer, Set<Capability> target) {
        MapsToCapability single = element.getAnnotation(MapsToCapability.class);
        if (single != null && layer.equals(single.layer())) {
            target.add(single.value());
        }
        MapsToCapability.List container = element.getAnnotation(MapsToCapability.List.class);
        if (container != null) {
            for (MapsToCapability annotation : container.value()) {
                if (layer.equals(annotation.layer())) {
                    target.add(annotation.value());
                }
            }
        }
    }

    /**
     * Returns the set of capabilities covered by {@link CapabilityTest} annotations
     * on any method in the given test class list.
     */
    private Set<Capability> scanCapabilityTests(List<Class<?>> testClasses) {
        Set<Capability> found = EnumSet.noneOf(Capability.class);
        for (Class<?> cls : testClasses) {
            for (Method method : cls.getDeclaredMethods()) {
                CapabilityTest single = method.getAnnotation(CapabilityTest.class);
                if (single != null) {
                    found.add(single.value());
                }
                CapabilityTest.List container = method.getAnnotation(CapabilityTest.List.class);
                if (container != null) {
                    for (CapabilityTest annotation : container.value()) {
                        found.add(annotation.value());
                    }
                }
            }
        }
        return found;
    }
}
