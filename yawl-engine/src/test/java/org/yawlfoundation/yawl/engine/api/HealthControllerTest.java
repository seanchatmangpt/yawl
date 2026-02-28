package org.yawlfoundation.yawl.engine.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for HealthController REST endpoints.
 * Tests liveness and readiness probes for Kubernetes integration.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Test: GET /actuator/health/live returns UP status
     */
    @Test
    void testLivenessProbeUp() throws Exception {
        mockMvc.perform(get("/actuator/health/live")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.checks", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Test: GET /actuator/health/live includes JVM check
     */
    @Test
    void testLivenessProbeIncludesJvmCheck() throws Exception {
        mockMvc.perform(get("/actuator/health/live")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks.jvm", is("UP")))
                .andExpect(jsonPath("$.checks.memory", is("OK")));
    }

    /**
     * Test: GET /actuator/health/ready returns readiness status
     */
    @Test
    void testReadinessProbe() throws Exception {
        mockMvc.perform(get("/actuator/health/ready")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", anyOf(is("UP"), is("DOWN"))))
                .andExpect(jsonPath("$.checks", notNullValue()))
                .andExpect(jsonPath("$.checks.agents", notNullValue()));
    }

    /**
     * Test: GET /actuator/health/ready includes database check
     */
    @Test
    void testReadinessProbeIncludesDatabaseCheck() throws Exception {
        mockMvc.perform(get("/actuator/health/ready")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks.database", notNullValue()));
    }

    /**
     * Test: GET /actuator/health returns overall health
     */
    @Test
    void testOverallHealth() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Test: GET /actuator/health/live response structure
     */
    @Test
    void testLivenessResponseStructure() throws Exception {
        mockMvc.perform(get("/actuator/health/live")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("status")))
                .andExpect(jsonPath("$", hasKey("checks")))
                .andExpect(jsonPath("$", hasKey("timestamp")))
                .andExpect(jsonPath("$.checks", hasKey("jvm")))
                .andExpect(jsonPath("$.checks", hasKey("memory")));
    }

    /**
     * Test: GET /actuator/health/ready response includes agent count
     */
    @Test
    void testReadinessResponseIncludesAgentCount() throws Exception {
        mockMvc.perform(get("/actuator/health/ready")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks.agentCount", greaterThanOrEqualTo(0)));
    }
}
