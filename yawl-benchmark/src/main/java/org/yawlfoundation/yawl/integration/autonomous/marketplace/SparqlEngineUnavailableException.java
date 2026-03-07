package org.yawlfoundation.yawl.integration.autonomous.marketplace;

/**
 * Exception thrown when a SPARQL engine is not reachable or unavailable.
 */
public class SparqlEngineUnavailableException extends SparqlEngineException {

    private static final long serialVersionUID = 1L;

    private final String engineType;
    private final String endpoint;

    public SparqlEngineUnavailableException(String engineType, String endpoint) {
        super(engineType + " engine unavailable at: " + endpoint);
        this.engineType = engineType;
        this.endpoint = endpoint;
    }

    public SparqlEngineUnavailableException(String engineType, String endpoint, Throwable cause) {
        super(engineType + " engine unavailable at: " + endpoint, cause);
        this.engineType = engineType;
        this.endpoint = endpoint;
    }

    public String getEngineType() {
        return engineType;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
