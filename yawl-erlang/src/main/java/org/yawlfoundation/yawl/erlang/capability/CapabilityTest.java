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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test method as validating a specific {@link Capability}.
 *
 * <p>The {@link CapabilityRegistry} scans all registered test classes for this
 * annotation. For each declared {@link Capability} (except {@link Capability#TOTAL}),
 * there must be at least one test method annotated with
 * {@code @CapabilityTest(Capability.SOME_CAPABILITY)}. If any capability lacks a
 * test, the registry throws {@link CapabilityRegistryException}.</p>
 *
 * <p>Example:
 * <pre>
 *   &#64;Test
 *   &#64;CapabilityTest(Capability.LAUNCH_CASE)
 *   void launchCase_returnsNonNullCaseId() {
 *       // real assertion, not vacuous
 *       String caseId = bridge.launchCase("OrderProcess");
 *       assertThat(caseId).isNotBlank();
 *       assertThat(caseId).startsWith("case_");
 *   }
 * </pre>
 *
 * <p>The registry does not enforce the quality of test assertions — that is a
 * code review responsibility. However, the convention {@code grep -r "@CapabilityTest"
 * | grep -v "assertThat\|assertEquals\|assertTrue"} catches vacuous tests (tests
 * that assert nothing meaningful).</p>
 *
 * @see CapabilityRegistry
 * @see Capability
 * @see MapsToCapability
 */
@Documented
@Repeatable(CapabilityTest.List.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CapabilityTest {

    /** The capability validated by this test method. */
    Capability value();

    /** Container for repeated {@link CapabilityTest} annotations. */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        CapabilityTest[] value();
    }
}
