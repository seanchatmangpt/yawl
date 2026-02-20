package org.yawlfoundation.yawl.gregverse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for Gregverse application.
 *
 * <p>Verifies that the main application class is properly structured
 * and can be instantiated.</p>
 */
class GregverseApplicationContextTest {

    @Test
    @DisplayName("GregverseApplication class exists and is instantiable")
    void gregverseApplicationClassExists() {
        GregverseApplication app = new GregverseApplication();
        assertNotNull(app, "GregverseApplication instance should not be null");
    }

    @Test
    @DisplayName("GregverseApplication has SpringBoot annotation")
    void hasSpringBootApplicationAnnotation() {
        assertNotNull(GregverseApplication.class.getAnnotation(
            org.springframework.boot.autoconfigure.SpringBootApplication.class),
            "GregverseApplication should have @SpringBootApplication annotation");
    }
}
