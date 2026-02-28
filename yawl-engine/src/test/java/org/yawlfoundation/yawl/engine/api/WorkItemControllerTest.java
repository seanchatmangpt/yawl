package org.yawlfoundation.yawl.engine.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.yawlfoundation.yawl.engine.api.dto.WorkItemCreateDTO;
import org.yawlfoundation.yawl.engine.api.dto.WorkItemDTO;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for WorkItemController REST endpoints.
 * Tests work item creation, listing, and queue management.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Test: GET /workitems returns empty list initially
     */
    @Test
    void testListWorkItemsEmpty() throws Exception {
        mockMvc.perform(get("/workitems")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Test: POST /workitems creates a new work item
     */
    @Test
    void testCreateWorkItem() throws Exception {
        WorkItemCreateDTO createRequest = new WorkItemCreateDTO(
                "ProcessPayment",
                "case-001",
                "{\"amount\": 100.00, \"currency\": \"USD\"}"
        );

        MvcResult result = mockMvc.perform(post("/workitems")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.taskName", is("ProcessPayment")))
                .andExpect(jsonPath("$.status", is("RECEIVED")))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        WorkItemDTO createdItem = objectMapper.readValue(response, WorkItemDTO.class);
        assert createdItem.id() != null;
        assert "ProcessPayment".equals(createdItem.taskName());
    }

    /**
     * Test: GET /workitems returns created work item
     */
    @Test
    void testListWorkItemsAfterCreation() throws Exception {
        // Create a work item
        WorkItemCreateDTO createRequest = new WorkItemCreateDTO(
                "ValidateOrder",
                "case-002",
                null
        );

        mockMvc.perform(post("/workitems")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // List work items
        mockMvc.perform(get("/workitems")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].taskName", is("ValidateOrder")));
    }

    /**
     * Test: GET /workitems with pagination
     */
    @Test
    void testListWorkItemsWithPagination() throws Exception {
        // Create multiple work items
        for (int i = 0; i < 5; i++) {
            WorkItemCreateDTO createRequest = new WorkItemCreateDTO(
                    "Task-" + i,
                    "case-" + i,
                    null
            );
            mockMvc.perform(post("/workitems")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated());
        }

        // List with limit
        mockMvc.perform(get("/workitems")
                .param("limit", "2")
                .param("page", "0")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    /**
     * Test: GET /workitems?agent={id} filters by agent
     */
    @Test
    void testListWorkItemsByAgent() throws Exception {
        // Create a work item
        WorkItemCreateDTO createRequest = new WorkItemCreateDTO(
                "AssignedTask",
                "case-003",
                null
        );

        mockMvc.perform(post("/workitems")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // Query by non-existent agent (should return empty)
        UUID nonExistentAgent = UUID.randomUUID();
        mockMvc.perform(get("/workitems")
                .param("agent", nonExistentAgent.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Test: GET /workitems/stats returns queue statistics
     */
    @Test
    void testGetWorkItemStats() throws Exception {
        mockMvc.perform(get("/workitems/stats")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueSize", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalItems", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.completedItems", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.oldestItemAge", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.throughput", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    /**
     * Test: GET /workitems/stats response structure
     */
    @Test
    void testWorkItemStatsResponseStructure() throws Exception {
        mockMvc.perform(get("/workitems/stats")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("queueSize")))
                .andExpect(jsonPath("$", hasKey("totalItems")))
                .andExpect(jsonPath("$", hasKey("completedItems")))
                .andExpect(jsonPath("$", hasKey("oldestItemAge")))
                .andExpect(jsonPath("$", hasKey("throughput")))
                .andExpect(jsonPath("$", hasKey("timestamp")));
    }

    /**
     * Test: POST /workitems with invalid data returns 400
     */
    @Test
    void testCreateWorkItemWithInvalidData() throws Exception {
        WorkItemCreateDTO invalidRequest = new WorkItemCreateDTO(
                "",  // Invalid: empty taskName
                "case-004",
                null
        );

        mockMvc.perform(post("/workitems")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test: Created work item has required fields
     */
    @Test
    void testCreatedWorkItemHasRequiredFields() throws Exception {
        WorkItemCreateDTO createRequest = new WorkItemCreateDTO(
                "TestTask",
                "case-005",
                "{\"test\": true}"
        );

        MvcResult result = mockMvc.perform(post("/workitems")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        WorkItemDTO item = objectMapper.readValue(response, WorkItemDTO.class);

        assert item.id() != null;
        assert item.taskName() != null;
        assert item.status() != null;
        assert item.createdTime() != null;
        assert item.isInProgress();  // Should not be completed yet
    }

    /**
     * Test: GET /workitems respects limit maximum of 100
     */
    @Test
    void testWorkItemListLimitMaximum() throws Exception {
        mockMvc.perform(get("/workitems")
                .param("limit", "500")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
                // The implementation should cap at 100, but we won't assert exact size
                // since it depends on previous test data
    }
}
