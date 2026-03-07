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

import java.util.List;
import java.util.Set;

/**
 * Thrown by {@link CapabilityRegistry#validate()} when one or more declared
 * {@link Capability} values are not fully implemented and tested.
 *
 * <p>This exception extends {@link IllegalStateException} to signal that the
 * module is in an illegal operational state — it has declared capabilities it
 * cannot honour. The system should halt on this exception; the module must not
 * serve requests with missing capabilities.</p>
 *
 * <p>The {@link #getMissingL2()} , {@link #getMissingL3()}, and
 * {@link #getMissingTests()} accessors allow tooling to identify which
 * specific gaps need to be filled.</p>
 */
public final class CapabilityRegistryException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final transient List<Capability> missingL2;
    private final transient List<Capability> missingL3;
    private final transient List<Capability> missingTests;

    /**
     * Constructs a registry exception with the specified missing capabilities.
     *
     * @param missingL2    capabilities with no Layer 2 implementation
     * @param missingL3    capabilities with no Layer 3 implementation
     * @param missingTests capabilities with no {@link CapabilityTest}-annotated method
     */
    public CapabilityRegistryException(
            List<Capability> missingL2,
            List<Capability> missingL3,
            List<Capability> missingTests) {
        super(buildMessage(missingL2, missingL3, missingTests));
        this.missingL2 = List.copyOf(missingL2);
        this.missingL3 = List.copyOf(missingL3);
        this.missingTests = List.copyOf(missingTests);
    }

    /** Returns capabilities with no Layer 2 (@MapsToCapability layer="L2") implementation. */
    public List<Capability> getMissingL2() {
        return missingL2;
    }

    /** Returns capabilities with no Layer 3 (@MapsToCapability layer="L3") implementation. */
    public List<Capability> getMissingL3() {
        return missingL3;
    }

    /** Returns capabilities with no @CapabilityTest-annotated test method. */
    public List<Capability> getMissingTests() {
        return missingTests;
    }

    /** Returns true if there are any missing capabilities (always true for this exception). */
    public boolean hasMissing() {
        return !missingL2.isEmpty() || !missingL3.isEmpty() || !missingTests.isEmpty();
    }

    private static String buildMessage(
            List<Capability> missingL2,
            List<Capability> missingL3,
            List<Capability> missingTests) {
        var sb = new StringBuilder();
        sb.append("CapabilityRegistry validation failed — incomplete implementation:\n");

        if (!missingL2.isEmpty()) {
            sb.append("  Missing Layer 2 (@MapsToCapability layer=\"L2\"): ")
              .append(missingL2).append("\n");
        }
        if (!missingL3.isEmpty()) {
            sb.append("  Missing Layer 3 (@MapsToCapability layer=\"L3\"): ")
              .append(missingL3).append("\n");
        }
        if (!missingTests.isEmpty()) {
            sb.append("  Missing @CapabilityTest: ")
              .append(missingTests).append("\n");
        }
        sb.append("Fix: add @MapsToCapability and @CapabilityTest annotations for each missing capability.");
        return sb.toString();
    }
}
