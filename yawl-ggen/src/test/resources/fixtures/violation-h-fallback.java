// Test fixture for H_FALLBACK guard violations - INTENTIONAL BAD CODE FOR TESTING
// Contains catch blocks returning fake data instead of propagating exceptions
// DO NOT apply hyper-validation to test fixtures

public class FallbackService {
    public String fetchData(String id) {
        try {
            // Real implementation that might fail
            return database.fetch(id);
        } catch (Exception e) {
            return Collections.emptyList(); // Silent fallback - intentional violation
        }
    }
    
    public List<WorkItem> getWorkItems() {
        try {
            return database.queryWorkItems();
        } catch (DatabaseException e) {
            return Collections.emptyList(); // Fake data fallback - intentional violation
        }
    }
    
    public User getUser(String username) {
        try {
            return userRepository.findByUsername(username);
        } catch (Exception e) {
            return new User("guest"); // Fake user fallback - intentional violation
        }
    }
    
    public int calculateScore(List<Transaction> transactions) {
        try {
            return scoringEngine.calculate(transactions);
        } catch (ScoringException e) {
            return 0; // Fake score fallback - intentional violation
        }
    }
}

public class RealService {
    // Real implementation that should pass
    public String fetchData(String id) {
        try {
            return database.fetch(id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch data for id: " + id, e);
        }
    }
}
