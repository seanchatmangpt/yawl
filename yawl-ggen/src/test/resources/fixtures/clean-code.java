// Test fixture for clean code that should pass all H-Guards validation
// Shows proper implementation patterns

public class RealService {
    // Proper implementation with clear error handling
    public String fetchData(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be null or empty");
        }
        
        // Real implementation logic
        return databaseService.fetch(id);
    }
    
    // Proper implementation that throws when not ready
    public void initialize() {
        if (!configuration.isReady()) {
            throw new UnsupportedOperationException(
                "Service requires valid configuration to initialize. " +
                "See IMPLEMENTATION_GUIDE.md"
            );
        }
        
        // Real initialization logic
        this.initializeDatabase();
        this.loadCache();
        this.startMonitoring();
    }
    
    // Proper implementation with no-op for void methods when appropriate
    public void shutdown() {
        // It's acceptable to have no-op for shutdown if resources are auto-managed
        // But we should still document this behavior
        log.debug("Shutdown called - resources are auto-managed");
    }
    
    // Proper implementation with actual business logic
    public boolean isValidWorkItem(WorkItem item) {
        if (item == null) {
            return false;
        }
        
        return item.getStatus() == WorkItemStatus.READY && 
               item.getDueDate() != null &&
               !item.getDescription().trim().isEmpty();
    }
    
    // Proper implementation that uses empty collections when appropriate
    public List<String> getAvailableFeatures() {
        if (!isInitialized()) {
            return Collections.emptyList(); // Valid return of empty collection
        }
        
        return featureService.getAvailableFeatures();
    }
}

// Proper enum implementation
public enum WorkItemStatus {
    READY, IN_PROGRESS, COMPLETED, CANCELLED
}

// Proper record implementation
public record WorkItem(
    String id,
    String description,
    WorkItemStatus status,
    LocalDate dueDate
) {
    // Additional validation logic
    public WorkItem {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID cannot be null or empty");
        }
    }
}
