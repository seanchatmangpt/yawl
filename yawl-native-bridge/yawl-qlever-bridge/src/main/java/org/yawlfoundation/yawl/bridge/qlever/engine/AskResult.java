/**
 * AskResult - Result of an ASK query
 *
 * Represents the boolean result of a SPARQL ASK query.
 */
public final record AskResult(
    boolean value
) implements QueryResult {

    /**
     * Checks if the query executed successfully
     */
    @Override
    public boolean isSuccessful() {
        return true; // AskResult only exists for successful queries
    }

    /**
     * Gets the boolean answer of the ASK query
     */
    public boolean value() {
        return value;
    }

    /**
     * Gets the boolean answer of the ASK query
     */
    public boolean getAnswer() {
        return value;
    }

    /**
     * Returns the string representation of the answer ("true" or "false")
     */
    public String getAnswerString() {
        return Boolean.toString(value);
    }

    /**
     * Checks if the query answered true
     */
    public boolean isTrue() {
        return value;
    }

    /**
     * Checks if the query answered false
     */
    public boolean isFalse() {
        return !value;
    }

    @Override
    public String toString() {
        return "AskResult[value=" + value + "]";
    }
}