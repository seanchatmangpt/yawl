// Test fixture for H_EMPTY guard violations - INTENTIONAL BAD CODE FOR TESTING
// Contains empty method bodies {} in void methods
// DO NOT apply hyper-validation to test fixtures

public class EmptyService {
    public void initialize() {
        // Empty body - intentional violation
    }
    
    public void cleanup() {
    }
    
    public void process() {
        
    }
    
    public void validate() {
    }
    
    public void reset() {
    }
    
    public void doNothing() {
        
    }
}

public class RealService {
    // Real implementation that should pass
    public void initialize() {
        throw new UnsupportedOperationException(
            "Real Service requires configuration setup. " +
            "See IMPLEMENTATION_GUIDE.md"
        );
    }
}
