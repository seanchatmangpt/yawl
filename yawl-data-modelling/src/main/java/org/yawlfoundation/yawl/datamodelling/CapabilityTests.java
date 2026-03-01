package org.yawlfoundation.yawl.datamodelling;

import java.lang.annotation.*;

/**
 * Container annotation for repeatable {@link CapabilityTest}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CapabilityTests {
    CapabilityTest[] value();
}
