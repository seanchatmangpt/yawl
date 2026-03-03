/**
 * QueryResult - Base Interface for Query Results
 *
 * Represents the result of a QLever query execution.
 */
public sealed interface QueryResult
    permits AskResult, SelectResult, ConstructResult {

    /**
     * Gets the raw JSON result from QLever
     */
    String getJsonResult();

    /**
     * Gets the SPARQL/XML result from QLever
     */
    String getXmlResult();

    /**
     * Gets the query that produced this result
     */
    String getQuery();

    /**
     * Checks if the query executed successfully
     */
    boolean isSuccessful();

    /**
     * Gets any error message if the query failed
     */
    String getErrorMessage();

    /**
     * Converts this result to a string representation
     */
    String toString();
}