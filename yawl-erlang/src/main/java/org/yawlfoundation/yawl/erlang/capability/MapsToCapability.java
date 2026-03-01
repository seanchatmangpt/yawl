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
 * Declares that a class or method implements a specific {@link Capability} at
 * a defined bridge layer.
 *
 * <p>The {@link CapabilityRegistry} scans all registered classes for this
 * annotation at startup. For each declared {@link Capability} (except
 * {@link Capability#TOTAL}), there must be at least one annotated element with
 * {@code layer = "L2"} and at least one with {@code layer = "L3"}. Missing
 * either causes the registry to throw {@link CapabilityRegistryException}.</p>
 *
 * <p>Usage on a method (fine-grained, preferred for Layer 3 public API):
 * <pre>
 *   &#64;MapsToCapability(value = Capability.LAUNCH_CASE, layer = "L3")
 *   public String launchCase(String specId) { ... }
 * </pre>
 *
 * <p>Usage on a class (coarse-grained, suitable for Layer 2 where one class
 * handles many capabilities):
 * <pre>
 *   &#64;MapsToCapability(value = Capability.LAUNCH_CASE, layer = "L2")
 *   &#64;MapsToCapability(value = Capability.RELOAD_MODULE, layer = "L2")
 *   public class ErlangNode { ... }
 * </pre>
 *
 * @see CapabilityRegistry
 * @see Capability
 */
@Documented
@Repeatable(MapsToCapability.List.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MapsToCapability {

    /** The capability this element implements. */
    Capability value();

    /**
     * The bridge layer this implementation belongs to.
     *
     * <p>Must be exactly {@code "L2"} (typed bridge, Panama FFM types allowed)
     * or {@code "L3"} (domain API, no Panama FFM types in public signatures).</p>
     */
    String layer();

    /** Container for repeated {@link MapsToCapability} annotations. */
    @Documented
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        MapsToCapability[] value();
    }
}
