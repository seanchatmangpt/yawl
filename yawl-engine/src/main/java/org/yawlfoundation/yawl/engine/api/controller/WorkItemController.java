package org.yawlfoundation.yawl.engine.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.yawlfoundation.yawl.engine.api.dto.MetricsDTO;
import org.yawlfoundation.yawl.engine.api.dto.WorkItemCreateDTO;
import org.yawlfoundation.yawl.engine.api.dto.WorkItemDTO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API controller for work item management.
 * Provides endpoints to list, create, and query work items.
 *
 * Base path: /workitems
 */
@RestController
@RequestMapping("/workitems")
public class WorkItemController {

    /**
     * In-memory work item queue.
     * In production, this would be backed by a persistent message queue.
     */
    private final Map<UUID, WorkItemDTO> workItems = new HashMap<>();

    /**
     * List all work items with pagination.
     * Results are limited to 100 items by default.
     *
     * GET /workitems?agent={agentId}&page={page}&limit={limit}
     *
     * @param agentId Optional filter by assigned agent
     * @param page Page number (0-based, default 0)
     * @param limit Items per page (default 100, max 100)
     * @return List of work items on the specified page
     */
    @GetMapping
    public ResponseEntity<List<WorkItemDTO>> listWorkItems(
            @RequestParam(required = false) UUID agentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int limit) {

        limit = Math.min(limit, 100);  // Cap at 100 items per page
        int skip = page * limit;

        List<WorkItemDTO> filtered = workItems.values()
                .stream()
                .filter(item -> agentId == null || agentId.equals(item.assignedAgent()))
                .sorted((a, b) -> b.createdTime().compareTo(a.createdTime()))  // Newest first
                .skip(skip)
                .limit(limit)
                .toList();

        return ResponseEntity.ok(filtered);
    }

    /**
     * Get work items for a specific agent.
     *
     * GET /workitems?agent={agentId}
     *
     * @param agentId Agent UUID
     * @return List of work items assigned to the agent
     */
    @GetMapping(params = "agent")
    public ResponseEntity<List<WorkItemDTO>> getAgentWorkItems(@RequestParam UUID agentId) {
        List<WorkItemDTO> items = workItems.values()
                .stream()
                .filter(item -> agentId.equals(item.assignedAgent()))
                .toList();
        return ResponseEntity.ok(items);
    }

    /**
     * Create a new work item and enqueue it.
     *
     * POST /workitems
     *
     * @param createRequest Work item creation request
     * @return Created work item with 201 status code
     */
    @PostMapping
    public ResponseEntity<WorkItemDTO> createWorkItem(@RequestBody WorkItemCreateDTO createRequest) {
        if (!createRequest.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        UUID workItemId = UUID.randomUUID();
        Instant now = Instant.now();

        WorkItemDTO newItem = WorkItemDTO.create(
                workItemId,
                createRequest.taskName(),
                "RECEIVED",
                null,  // No agent assignment initially
                now,
                null   // Not yet completed
        );

        workItems.put(workItemId, newItem);
        return ResponseEntity.status(HttpStatus.CREATED).body(newItem);
    }

    /**
     * Get statistics about the work item queue.
     *
     * GET /workitems/stats
     *
     * @return Queue statistics including size, oldest item age, and throughput
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getWorkItemStats() {
        Map<String, Object> stats = new HashMap<>();

        int queueSize = (int) workItems.values()
                .stream()
                .filter(WorkItemDTO::isInProgress)
                .count();

        long oldestAge = workItems.values()
                .stream()
                .filter(WorkItemDTO::isInProgress)
                .mapToLong(WorkItemDTO::getElapsedMillis)
                .max()
                .orElse(0);

        long completedCount = workItems.values()
                .stream()
                .filter(item -> !item.isInProgress())
                .count();

        double throughput = workItems.size() > 0
                ? (completedCount * 60.0) / Math.max(1, oldestAge / 1000.0)
                : 0.0;

        stats.put("queueSize", queueSize);
        stats.put("totalItems", workItems.size());
        stats.put("completedItems", completedCount);
        stats.put("oldestItemAge", oldestAge);
        stats.put("throughput", String.format("%.2f items/min", throughput));
        stats.put("timestamp", Instant.now());

        return ResponseEntity.ok(stats);
    }
}
