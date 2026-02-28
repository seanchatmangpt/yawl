/**
 * H-Guards Clean: H_MOCK Pattern - PASS
 *
 * This fixture has real class/method names (no mock/stub/fake/demo prefixes).
 * Expected: PASS (no H_MOCK violations)
 */
package org.yawlfoundation.yawl.test.guards;

public class DataService implements DataRepository {

    public String fetchData() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
            "fetchData requires real database or API integration"
        );
    }

    public void processWorkflow() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
            "processWorkflow requires YNetRunner integration"
        );
    }

    public String getEngineResponse() throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
            "getEngineResponse requires real YAWL engine connection"
        );
    }

    @Override
    public void persistData(String data) throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
            "persistData requires database implementation"
        );
    }
}

interface DataRepository {
    void persistData(String data);
}
