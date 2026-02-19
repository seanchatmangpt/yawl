package org.yawlfoundation.yawl.mcp.a2a.a2a;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test to verify the Spring Boot application context loads correctly.
 * This test validates:
 * - All Spring beans are properly configured
 * - All dependencies are injected correctly
 * - No circular dependencies exist
 * - All configuration properties are valid
 */
@SpringBootTest
@ActiveProfiles("test")
class YawlMcpA2aApplicationContextTest {

    @Test
    void contextLoads() {
        // This test passes if the Spring application context loads without errors
        // All configuration, beans, and dependencies are validated automatically
    }
}