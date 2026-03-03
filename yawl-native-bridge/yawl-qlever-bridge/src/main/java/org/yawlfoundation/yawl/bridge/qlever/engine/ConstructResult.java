/**
 * ConstructResult - Result of a CONSTRUCT query
 *
 * Represents the RDF graph result of a SPARQL CONSTRUCT query.
 */
public final record ConstructResult(
    String jsonResult,
    String xmlResult,
    String query,
    String turtleResult,
    String ntriplesResult,
    boolean isSuccessful,
    String errorMessage
) implements QueryResult {

    /**
     * Creates a successful CONSTRUCT result
     */
    public ConstructResult(String jsonResult, String xmlResult, String query, String turtleResult, String ntriplesResult) {
        this(jsonResult, xmlResult, query, turtleResult, ntriplesResult, true, null);
    }

    /**
     * Creates a failed CONSTRUCT result
     */
    public ConstructResult(String query, String errorMessage) {
        this(null, null, query, null, null, false, errorMessage);
    }

    @Override
    public String getJsonResult() {
        return jsonResult;
    }

    @Override
    public String getXmlResult() {
        return xmlResult;
    }

    @Override
    public String getQuery() {
        return query;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Gets the result as Turtle format
     */
    public String getTurtleResult() {
        return turtleResult;
    }

    /**
     * Gets the result as N-Triples format
     */
    public String getNtriplesResult() {
        return ntriplesResult;
    }

    /**
     * Checks if the result has content
     */
    public boolean hasContent() {
        return turtleResult != null && !turtleResult.trim().isEmpty();
    }

    /**
     * Gets the result format for display
     */
    public String getFormattedResult() {
        if (turtleResult != null && !turtleResult.trim().isEmpty()) {
            return "Turtle:\n" + turtleResult;
        } else if (ntriplesResult != null && !ntriplesResult.trim().isEmpty()) {
            return "N-Triples:\n" + ntriplesResult;
        } else {
            return "No content";
        }
    }

    @Override
    public String toString() {
        if (isSuccessful) {
            String content = hasContent() ? "(" + turtleResult.length() + " chars)" : "(empty)";
            return "ConstructResult[query=" + query + ", " + content + "]";
        } else {
            return "ConstructResult[query=" + query + ", error=" + errorMessage + "]";
        }
    }
}