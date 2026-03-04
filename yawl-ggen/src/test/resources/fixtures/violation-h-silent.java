// Test fixture for H_SILENT guard violations - INTENTIONAL BAD CODE FOR TESTING
// Contains log.warn("not implemented") and similar silent logging
// DO NOT apply hyper-validation to test fixtures

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SilentService {
    private static final Logger log = LoggerFactory.getLogger(SilentService.class);
    
    public void processPayment() {
        log.error("Payment processing not implemented yet"); // Silent logging - intentional violation
    }
    
    public void validateBusinessRules() {
        log.warn("Business rules validation not implemented"); // Silent logging - intentional violation
    }
    
    public void authenticateUser(String username, String password) {
        log.info("Authentication not implemented, allowing all users"); // Silent logging - intentional violation
    }
    
    public void sendEmail(String recipient, String subject, String body) {
        log.debug("Email sending not implemented"); // Silent logging - intentional violation
    }
    
    public void exportData() {
        log.error("Data export functionality not available"); // Silent logging - intentional violation
    }
}

public class RealService {
    // Real implementation that should pass
    public void processPayment() {
        throw new UnsupportedOperationException(
            "Payment processing requires payment gateway integration. " +
            "See IMPLEMENTATION_GUIDE.md"
        );
    }
}
