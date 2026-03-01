package org.yawlfoundation.yawl.engine.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.yawlfoundation.yawl.engine.api.dto.AgentDTO;
import org.yawlfoundation.yawl.engine.api.dto.WorkflowDefDTO;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AgentController REST endpoints.
 * Tests agent creation, listing, and management operations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Test: GET /agents returns empty list initially
     */
    @Test
    void testListAgentsEmpty() throws Exception {
        mockMvc.perform(get("/agents")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Test: POST /agents creates a new agent
     */
    @Test
    void testCreateAgent() throws Exception {
        WorkflowDefDTO workflowDef = new WorkflowDefDTO(
                "workflow-001",
                "Test Workflow",
                "1.0",
                "A test workflow for unit testing",
                "<yawl><!-- workflow XML --></yawl>"
        );

        MvcResult result = mockMvc.perform(post("/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(workflowDef)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", containsString("IDLE")))
                .andExpect(jsonPath("$.workflowId", is("workflow-001")))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        AgentDTO createdAgent = objectMapper.readValue(response, AgentDTO.class);
        assert createdAgent.id() != null;
        assert createdAgent.status().contains("IDLE");
    }

    /**
     * Test: GET /agents returns created agent
     */
    @Test
    void testListAgentsAfterCreation() throws Exception {
        // Create an agent first
        WorkflowDefDTO workflowDef = new WorkflowDefDTO(
                "workflow-002",
                "Another Test Workflow",
                "2.0",
                "Testing list after creation",
                "<yawl><!-- workflow XML --></yawl>"
        );

        mockMvc.perform(post("/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(workflowDef)))
                .andExpect(status().isCreated());

        // Now list agents
        mockMvc.perform(get("/agents")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].workflowId", is("workflow-002")));
    }

    /**
     * Test: GET /agents/{id} returns specific agent
     */
    @Test
    void testGetAgentById() throws Exception {
        // Create an agent
        WorkflowDefDTO workflowDef = new WorkflowDefDTO(
                "workflow-003",
                "Get By ID Workflow",
                "1.0",
                "Testing get by ID",
                "<yawl><!-- workflow XML --></yawl>"
        );

        MvcResult createResult = mockMvc.perform(post("/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(workflowDef)))
                .andExpect(status().isCreated())
                .andReturn();

        AgentDTO createdAgent = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                AgentDTO.class
        );

        // Get the agent by ID
        mockMvc.perform(get("/agents/" + createdAgent.id())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdAgent.id().toString())))
                .andExpect(jsonPath("$.workflowId", is("workflow-003")));
    }

    /**
     * Test: GET /agents/{id} returns 404 for non-existent agent
     */
    @Test
    void testGetAgentByIdNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(get("/agents/" + nonExistentId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    /**
     * Test: DELETE /agents/{id} removes agent
     */
    @Test
    void testDeleteAgent() throws Exception {
        // Create an agent
        WorkflowDefDTO workflowDef = new WorkflowDefDTO(
                "workflow-004",
                "Delete Test Workflow",
                "1.0",
                "Testing delete operation",
                "<yawl><!-- workflow XML --></yawl>"
        );

        MvcResult createResult = mockMvc.perform(post("/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(workflowDef)))
                .andExpect(status().isCreated())
                .andReturn();

        AgentDTO createdAgent = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                AgentDTO.class
        );

        // Delete the agent
        mockMvc.perform(delete("/agents/" + createdAgent.id())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        // Verify agent is deleted
        mockMvc.perform(get("/agents/" + createdAgent.id())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    /**
     * Test: DELETE /agents/{id} returns 404 for non-existent agent
     */
    @Test
    void testDeleteAgentNotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(delete("/agents/" + nonExistentId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    /**
     * Test: GET /agents/healthy returns only healthy agents
     */
    @Test
    void testListHealthyAgents() throws Exception {
        // Create an agent
        WorkflowDefDTO workflowDef = new WorkflowDefDTO(
                "workflow-005",
                "Healthy Test Workflow",
                "1.0",
                "Testing healthy filter",
                "<yawl><!-- workflow XML --></yawl>"
        );

        mockMvc.perform(post("/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(workflowDef)))
                .andExpect(status().isCreated());

        // List healthy agents
        mockMvc.perform(get("/agents/healthy")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(0))));
    }

    /**
     * Test: GET /agents/metrics returns agent metrics
     */
    @Test
    void testGetAgentMetrics() throws Exception {
        mockMvc.perform(get("/agents/metrics")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.healthyAgentCount", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.throughput", greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Test: POST /agents with invalid data returns 400
     */
    @Test
    void testCreateAgentWithInvalidData() throws Exception {
        WorkflowDefDTO invalidWorkflow = new WorkflowDefDTO(
                "",  // Invalid: empty workflowId
                "Test",
                "1.0",
                "Invalid workflow",
                "<yawl><!-- workflow XML --></yawl>"
        );

        mockMvc.perform(post("/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidWorkflow)))
                .andExpect(status().isBadRequest());
    }
}
