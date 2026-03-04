/*
 * Test fixture for H_MOCK pattern detection
 * This file contains mock implementations that should be flagged.
 *
 * This is a test file intentionally containing violations to validate
 * that the H_MOCK pattern detection works correctly.
 */
public class MockViolationExample {

    // Method with mock in name pattern (should be detected by H_MOCK pattern)
    public String mockFetchData() {
        // This method has "mock" in its name
        return "data";
    }

    // Class with mock in name pattern (should be detected by H_MOCK pattern)
    public class MockUserService {
        // This class has "Mock" in its name
        public String getUserInfo() {
            return "user info";
        }
    }

    // Method with fake in name pattern (should be detected by H_MOCK pattern)
    public String fakeProcessRequest() {
        // This method has "fake" in its name
        return "processed";
    }

    // Method with demo in name pattern (should be detected by H_MOCK pattern)
    public String demoDataGenerator() {
        // This method has "demo" in its name
        return "generated";
    }
}