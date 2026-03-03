// Test fixture for H_MOCK guard violations - INTENTIONAL BAD CODE FOR TESTING
// Contains MockDataService class and mock method patterns
// DO NOT apply hyper-validation to test fixtures

public class MockDataService implements DataService {
    public String fetchData() {
        return "mock data"; // Mock implementation - intentional violation
    }
}

public class StubPaymentService {
    public void processPayment() {
        // Stub implementation - intentional violation
    }
}

public class FakeAuthenticationProvider {
    public boolean authenticate(String username, String password) {
        return true; // Fake authentication - intentional violation
    }
}

public class DemoWorkflowEngine {
    public void executeDemo() {
        // Demo workflow - intentional violation
    }
}

public class RealDataService implements DataService {
    // Real implementation that should pass
    public String fetchData() {
        throw new UnsupportedOperationException(
            "Real DataService requires database connection. " +
            "See IMPLEMENTATION_GUIDE.md"
        );
    }
}
