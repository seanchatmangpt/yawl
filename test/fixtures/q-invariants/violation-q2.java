/**
 * Q-Invariants Violation: Q2 Pattern - Mock class declarations
 *
 * This fixture violates Q invariant by declaring mock/stub/fake/demo classes
 * that implement real interfaces. These should be deleted or implemented properly.
 *
 * Expected: FAIL with pattern Q2 on mock class declarations
 */
package org.yawlfoundation.yawl.test.invariants;

/**
 * This is a mock service that violates invariant Q2.
 * It pretends to be a real WorkflowService but only logs or returns fake data.
 */
public class MockWorkflowService implements WorkflowService {

    @Override
    public void startWorkflow(String workflowName, java.util.Map<String, Object> vars) {
        System.out.println("MOCK: Starting workflow " + workflowName);
    }

    @Override
    public void completeTask(String taskId, java.util.Map<String, Object> results) {
        System.out.println("MOCK: Completing task " + taskId);
    }

    @Override
    public String getWorkflowStatus(String workflowId) {
        return "MOCK_STATUS";
    }
}

/**
 * Another mock class that should not exist in real code.
 */
public class FakeDataRepository implements DataRepository {

    @Override
    public Object getData(String key) {
        return "fake_data_" + key;
    }

    @Override
    public void storeData(String key, Object value) {
        System.out.println("FAKE: Storing " + key);
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
