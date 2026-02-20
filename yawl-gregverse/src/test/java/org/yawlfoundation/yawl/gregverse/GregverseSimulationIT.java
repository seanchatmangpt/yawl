package org.yawlfoundation.yawl.gregverse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Greg-Verse simulation.
 *
 * <p>Tests the full Greg-Verse workflow including agent discovery,
 * skill marketplace, and business workflows.</p>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GregverseSimulationIT {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testHealthEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/actuator/health", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("UP"));
    }

    @Test
    void testAgentDiscovery() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/agents/list", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Implementation will check for 8 registered agents
    }

    @Test
    void testSkillsMarketplace() {
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/workflows/skills-marketplace/start",
            "{\"query\": \"Need SEO advice for new startup\"}",
            String.class);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        // Implementation will verify workflow execution
    }

    @Test
    void testNewsletterResearch() {
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/workflows/newsletter-research/start",
            "{\"topic\": \"AI in business\"}",
            String.class);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    void testMetricsEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/actuator/metrics", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("gregverse"));
    }
}