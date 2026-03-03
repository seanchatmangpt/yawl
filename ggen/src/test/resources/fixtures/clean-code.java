/*
 * Test fixture for clean code that should pass all guard checks.
 * This file contains no violations and should be processed cleanly.
 */
public class CleanCodeExample {

    public void properMethod() {
        // This method has real implementation
        if (isValid()) {
            processValidRequest();
        } else {
            throw new IllegalArgumentException("Invalid input");
        }
    }

    public String getData() {
        // Return real data or throw exception
        if (dataAvailable()) {
            return retrieveData();
        }
        throw new UnsupportedOperationException("Data not available");
    }

    public void initialize() {
        // Real initialization logic
        loadConfiguration();
        setupConnections();
        validateState();
    }

    // Helper methods
    private boolean isValid() {
        return true;
    }

    private void processValidRequest() {
        // Real processing
    }

    private boolean dataAvailable() {
        return true;
    }

    private String retrieveData() {
        return "real data";
    }

    private void loadConfiguration() {
        // Load config
    }

    private void setupConnections() {
        // Setup connections
    }

    private void validateState() {
        // Validate state
    }
}