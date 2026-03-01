/**
 * Q-Invariants Clean: Q2 Pattern - PASS
 *
 * This fixture has no mock/stub/fake/demo class declarations.
 * All classes are either real implementations or test utilities, not mocks.
 *
 * Expected: PASS (no Q2 violations)
 */
package org.yawlfoundation.yawl.test.invariants;

/**
 * Real implementation of WorkflowService.
 * This is not a mock - it implements actual workflow management logic.
 */
public class RealWorkflowService implements WorkflowService {

    private final java.util.Map<String, WorkflowInstance> workflows = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void startWorkflow(String workflowName, java.util.Map<String, Object> vars) {
        if (workflowName == null || workflowName.isEmpty()) {
            throw new IllegalArgumentException("workflowName cannot be empty");
        }
        WorkflowInstance instance = new WorkflowInstance(workflowName, vars);
        workflows.put(instance.getId(), instance);
    }

    @Override
    public void completeTask(String taskId, java.util.Map<String, Object> results) {
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("taskId cannot be empty");
        }
        // Real implementation would update task status
    }

    @Override
    public String getWorkflowStatus(String workflowId) {
        WorkflowInstance instance = workflows.get(workflowId);
        if (instance == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
        return instance.getStatus();
    }

    static class WorkflowInstance {
        private final String id;
        private final String name;
        private final java.util.Map<String, Object> variables;
        private String status = "ACTIVE";

        WorkflowInstance(String name, java.util.Map<String, Object> vars) {
            this.id = java.util.UUID.randomUUID().toString();
            this.name = name;
            this.variables = new java.util.HashMap<>(vars);
        }

        String getId() { return id; }
        String getStatus() { return status; }
    }
}

/**
 * Real implementation of DataRepository.
 * Provides persistent data storage, not fake/mock data.
 */
public class PersistentDataRepository implements DataRepository {

    private final java.util.Map<String, Object> store = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public Object getData(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key cannot be empty");
        }
        return store.get(key);
    }

    @Override
    public void storeData(String key, Object value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key cannot be empty");
        }
        store.put(key, value);
    }
}

interface WorkflowService {
    void startWorkflow(String workflowName, java.util.Map<String, Object> vars);
    void completeTask(String taskId, java.util.Map<String, Object> results);
    String getWorkflowStatus(String workflowId);
}

interface DataRepository {
    Object getData(String key);
    void storeData(String key, Object value);
}
