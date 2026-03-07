package org.yawlfoundation.yawl.integration.autonomous.marketplace;

/**
 * Exception thrown when a SPARQL engine operation fails.
 */
public class SparqlEngineException extends Exception {

    private static final long serialVersionUID = 1L;

    public SparqlEngineException(String message) {
        super(message);
    }

    public SparqlEngineException(String message, Throwable cause) {
        super(message, cause);
    }
}
