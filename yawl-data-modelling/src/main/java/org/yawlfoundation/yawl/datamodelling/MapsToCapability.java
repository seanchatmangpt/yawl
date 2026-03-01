package org.yawlfoundation.yawl.datamodelling;

import java.lang.annotation.*;

/**
 * Marks a method as implementing a specific {@link Capability}.
 * Required on every public method in {@code DataModellingBridge} and
 * {@code DataModellingServiceImpl}. {@link CapabilityRegistry#assertComplete()}
 * fails at startup if any capability is unmapped or over-mapped.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MapsToCapability {
    Capability value();
}
