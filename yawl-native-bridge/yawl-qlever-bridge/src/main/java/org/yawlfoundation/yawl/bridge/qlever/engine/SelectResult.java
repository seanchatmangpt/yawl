import java.util.List;
import java.util.Collections;

/**
 * SelectResult - Result of a SELECT query
 *
 * Represents the tabular result of a SPARQL SELECT query.
 */
public final record SelectResult(
    String jsonResult,
    String xmlResult,
    String query,
    List<String> variables,
    List<List<String>> rows,
    boolean isSuccessful,
    String errorMessage
) implements QueryResult {

    /**
     * Creates a successful SELECT result
     */
    public SelectResult(String jsonResult, String xmlResult, String query, List<String> variables, List<List<String>> rows) {
        this(jsonResult, xmlResult, query, variables, rows, true, null);
    }

    /**
     * Creates a failed SELECT result
     */
    public SelectResult(String query, String errorMessage) {
        this(null, null, query, Collections.emptyList(), Collections.emptyList(), false, errorMessage);
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
     * Gets the column names (variable names) from the SELECT query
     */
    public List<String> getVariables() {
        return Collections.unmodifiableList(variables);
    }

    /**
     * Gets the result rows
     */
    public List<List<String>> getRows() {
        return Collections.unmodifiableList(rows);
    }

    /**
     * Gets the number of columns in the result
     */
    public int getColumnCount() {
        return variables.size();
    }

    /**
     * Gets the number of rows in the result
     */
    public int getRowCount() {
        return rows.size();
    }

    /**
     * Gets a specific cell value
     *
     * @param rowIndex Row index (0-based)
     * @param columnIndex Column index (0-based)
     * @return The cell value, or null if out of bounds
     */
    public String getValue(int rowIndex, int columnIndex) {
        if (rowIndex >= 0 && rowIndex < rows.size() &&
            columnIndex >= 0 && columnIndex < variables.size()) {
            return rows.get(rowIndex).get(columnIndex);
        }
        return null;
    }

    /**
     * Gets the value for a specific variable in a row
     *
     * @param rowIndex Row index (0-based)
     * @param variable Variable name
     * @return The cell value, or null if not found
     */
    public String getValue(int rowIndex, String variable) {
        int columnIndex = variables.indexOf(variable);
        return getValue(rowIndex, columnIndex);
    }

    /**
     * Checks if the result has any rows
     */
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /**
     * Gets the first row of results
     */
    public List<String> getFirstRow() {
        return rows.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(rows.get(0));
    }

    @Override
    public String toString() {
        if (isSuccessful) {
            return String.format("SelectResult[query=%s, columns=%d, rows=%d]", query, variables.size(), rows.size());
        } else {
            return "SelectResult[query=" + query + ", error=" + errorMessage + "]";
        }
    }
}