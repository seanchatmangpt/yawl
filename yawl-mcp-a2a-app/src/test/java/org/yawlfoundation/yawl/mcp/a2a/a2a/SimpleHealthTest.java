package org.yawlfoundation.yawl.mcp.a2a.a2a;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Simple test to verify the application starts without configuration errors.
 */
@SpringBootTest
@ActiveProfiles("test")
class SimpleHealthTest {

    @Test
    void applicationContextLoads() {
        // This test will pass if the Spring Boot application can start
        // without errors related to configuration, bean creation, or dependency injection
    }
}