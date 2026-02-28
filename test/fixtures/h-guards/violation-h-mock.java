/**
 * H-Guards Violation: H_MOCK Pattern
 *
 * This fixture contains mock/stub/fake class and method names.
 * Expected: FAIL with pattern H_MOCK or H_MOCK_CLASS
 */
package org.yawlfoundation.yawl.test.guards;

public class MockDataService implements DataService {

    public String fetchData() {
        return "mock data";
    }

    public void mockProcessing() {
        System.out.println("Mock processing");
    }

    public String getFakeResponse() {
        return "fake response";
    }

    public void demoExecution() {
        System.out.println("Demo mode");
    }

    public String getStubResult() {
        return "stub";
    }

    @Override
    public void processData(String data) {
        // Mock implementation
    }
}

interface FakeRepository {
    void save();
    void delete();
}
