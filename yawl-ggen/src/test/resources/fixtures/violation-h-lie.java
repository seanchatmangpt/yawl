// Test fixture for H_LIE guard violations
// Contains code that doesn't match documentation patterns

public class DocumentedButNotImplemented {

    /**
     * @return never null, always returns a valid user object
     * @throws UserNotFoundException if user doesn't exist
     */
    public User getUser(String id) {
        return null; // Documentation says never null, but returns null - this is the violation
    }

    /**
     * @return list containing at least one element
     */
    public List<String> getItems() {
        return Collections.emptyList(); // Claims to have at least one element, but returns empty - violation
    }

    /**
     * @return positive number greater than zero
     */
    public int getCount() {
        return 0; // Claims positive but returns zero - violation
    }

    /**
     * @throws ProcessingException if processing fails
     * @throws IllegalArgumentException if input is invalid
     */
    public void processData(String input) {
        // No exception thrown, but documentation claims it throws - violation
        System.out.println("Processing: " + input);
    }

    /**
     * Initializes the service and sets up all required resources
     */
    public void initialize() {
        // Documentation claims it initializes, but method does nothing - violation
    }
}

public class ProperlyDocumented {

    /**
     * @return user object or null if not found
     */
    public User findUser(String id) {
        if (id == null) {
            return null;
        }
        return database.findUser(id);
    }

    /**
     * @throws ProcessingException if processing fails
     */
    public void process(String data) throws ProcessingException {
        if (data == null) {
            throw new ProcessingException("Data cannot be null");
        }
        // Real processing
    }

    /**
     * Initializes the service
     * @throws IllegalStateException if already initialized
     */
    public void initialize() {
        if (this.initialized) {
            throw new IllegalStateException("Service already initialized");
        }
        // Real initialization
    }
}