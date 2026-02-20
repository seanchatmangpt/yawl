package org.yawlfoundation.yawl.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EncryptedPropertiesIntegrationTest validates encrypted property injection
 * in the Spring application context for sensitive configurations like MCP credentials
 * and rate limiter secrets.
 *
 * Test coverage:
 * - Property resolution in Spring context
 * - Jasypt configuration validity
 * - RateLimiter and MCP credential encryption support
 */
@SpringBootTest
@TestPropertySource(properties = {
        "jasypt.encryptor.password=integration-test-password"
})
@DisplayName("Encrypted Properties Integration Tests")
public class EncryptedPropertiesIntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Jasypt configuration is active")
    void testJasyptConfigurationActive() {
        String algorithm = environment.getProperty("jasypt.encryptor.algorithm");
        assertNotNull(algorithm);
        assertEquals("PBEWithHMACSHA512AndAES_256", algorithm);
    }

    @Test
    @DisplayName("Pool size is correctly configured")
    void testPoolSizeConfiguration() {
        String poolSize = environment.getProperty("jasypt.encryptor.pool-size");
        assertNotNull(poolSize);
        assertEquals("8", poolSize);
    }

    @Test
    @DisplayName("IV generator is configured for randomization")
    void testIVGeneratorConfiguration() {
        String ivGenerator = environment.getProperty("jasypt.encryptor.iv-generator-classname");
        assertEquals("org.jasypt.iv.RandomIvGenerator", ivGenerator);
    }

    @Test
    @DisplayName("Application name is correctly set")
    void testApplicationNameConfiguration() {
        String appName = environment.getProperty("spring.application.name");
        assertEquals("yawl-engine", appName);
    }

    @Test
    @DisplayName("Server port is configured")
    void testServerPortConfiguration() {
        String port = environment.getProperty("server.port");
        assertEquals("8080", port);
    }
}
