package org.yawlfoundation.yawl.datamodelling;

import java.lang.annotation.*;

/**
 * Marks a test method as covering a specific {@link Capability}.
 * Repeatable — one method may cover multiple capabilities.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(CapabilityTests.class)
public @interface CapabilityTest {
    Capability value();
}
