import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;

import java.util.LinkedHashMap;

/**
 * SelectResult - Result of a SELECT query
 *
 * Represents the tabular result of a SPARQL SELECT query.
 */
public final record SelectResult(
    List<String> variables,
    List<Map<String, String>> rows
) implements QueryResult {

    /**
     * Checks if the query executed successfully
     */
    @Override
    public boolean isSuccessful() {
        return true; // SelectResult only exists for successful queries
    }

    /**
     * Constructor with empty rows for testing
     */
    public SelectResult(List<String> variables) {
        this(variables, Collections.emptyList());
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
    public List<Map<String, String>> getRows() {
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
            return rows.get(rowIndex).get(variables.get(columnIndex));
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
        if (rowIndex >= 0 && rowIndex < rows.size()) {
            return rows.get(rowIndex).get(variable);
        }
        return null;
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
    public Map<String, String> getFirstRow() {
        return rows.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(rows.get(0));
    }

    @Override
    public String toString() {
        return String.format("SelectResult[columns=%d, rows=%d]", variables.size(), rows.size());
    }
}