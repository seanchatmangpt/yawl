/**
 * AskResult - Result of an ASK query
 *
 * Represents the boolean result of a SPARQL ASK query.
 */
public final record AskResult(
    String jsonResult,
    String xmlResult,
    String query,
    boolean answer,
    boolean isSuccessful,
    String errorMessage
) implements QueryResult {

    /**
     * Creates a successful ASK result
     */
    public AskResult(String jsonResult, String xmlResult, String query, boolean answer) {
        this(jsonResult, xmlResult, query, answer, true, null);
    }

    /**
     * Creates a failed ASK result
     */
    public AskResult(String query, String errorMessage) {
        this(null, null, query, false, false, errorMessage);
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
     * Gets the boolean answer of the ASK query
     */
    public boolean getAnswer() {
        return answer;
    }

    /**
     * Returns the string representation of the answer ("true" or "false")
     */
    public String getAnswerString() {
        return Boolean.toString(answer);
    }

    /**
     * Checks if the query answered true
     */
    public boolean isTrue() {
        return answer;
    }

    /**
     * Checks if the query answered false
     */
    public boolean isFalse() {
        return !answer;
    }

    @Override
    public String toString() {
        if (isSuccessful) {
            return "AskResult[query=" + query + ", answer=" + answer + "]";
        } else {
            return "AskResult[query=" + query + ", error=" + errorMessage + "]";
        }
    }
}