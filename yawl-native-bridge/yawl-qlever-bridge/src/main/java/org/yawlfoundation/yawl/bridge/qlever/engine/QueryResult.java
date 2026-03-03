/**
 * QueryResult - Base Interface for Query Results
 *
 * Represents the result of a QLever query execution.
 */
public sealed interface QueryResult
    permits AskResult, SelectResult, ConstructResult {

    /**
     * Checks if the query executed successfully
     */
    boolean isSuccessful();

    /**
     * Converts this result to a string representation
     */
    String toString();
}