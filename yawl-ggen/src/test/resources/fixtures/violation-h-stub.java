// Test fixture for H_STUB guard violations - INTENTIONAL BAD CODE FOR TESTING
// Contains methods returning "", 0, null stub implementations
// DO NOT apply hyper-validation to test fixtures

public class StubService {
    public String getData() {
        return ""; // Stub return - intentional violation
    }
    
    public int getCount() {
        return 0; // Stub return - intentional violation
    }
    
    public Object findItem(String id) {
        return null; // Stub return - intentional violation
    }
    
    public List<String> getEmptyList() {
        return Collections.emptyList(); // Stub return - intentional violation
    }
    
    public Map<String, Object> getEmptyMap() {
        return new HashMap<>(); // Stub return - intentional violation
    }
    
    public String getStubDataWithComment() {
        return null; // This is a stub implementation
    }
}

public class RealService {
    // Real implementation that should pass
    public String getData() {
        throw new UnsupportedOperationException(
            "Real Service requires database connection. " +
            "See IMPLEMENTATION_GUIDE.md"
        );
    }
    
    public int getCount() {
        throw new UnsupportedOperationException(
            "Real Service requires external API call. " +
            "See IMPLEMENTATION_GUIDE.md"
        );
    }
}
