// Test fixture for multiple guard violations in one file
// Contains all types of violations for comprehensive testing

public class MultipleViolationService {
    // H_MOCK violation (method name pattern)
    public String processMockPayment(Payment payment) {
        throw new UnsupportedOperationException(
            "processMockPayment() uses mock pattern. " +
            "Implement real payment logic or remove this method."
        );
    }

    // H_STUB violation
    public User getUser(String id) {
        throw new UnsupportedOperationException(
            "getUser() is not implemented. " +
            "Implement real user retrieval logic or remove this method."
        );
    }

    // H_EMPTY violation
    public void initialize() {
        throw new UnsupportedOperationException(
            "initialize() has no implementation. " +
            "Add real initialization logic or remove this method."
        );
    }

    // H_FALLBACK violation
    public List<Order> getOrders() {
        throw new UnsupportedOperationException(
            "getOrders() uses silent fallback pattern. " +
            "Implement proper error handling instead of returning fake data."
        );
    }

    // H_SILENT violation
    public void processAdvancedFeature() {
        throw new UnsupportedOperationException(
            "processAdvancedFeature() uses silent logging. " +
            "Implement real feature logic or throw UnsupportedOperationException."
        );
    }

    // Proper implementation that passes
    public void validateOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        if (order.getItems().isEmpty()) {
            throw new IllegalStateException("Order must have items");
        }
        // Real validation logic
    }

    // Proper implementation with UnsupportedOperationException
    public void processFutureFeature() {
        throw new UnsupportedOperationException(
            "Future feature not yet implemented. See IMPLEMENTATION_GUIDE.md"
        );
    }
}