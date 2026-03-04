package org.yawlfoundation.yawl.ggen.validation.model;

/**
 * Represents parameter information extracted from AST analysis.
 *
 * <p>This class encapsulates information about a parameter found during
 * AST parsing, including its name and type.</p>
 *
 * @since 1.0
 */
public final class ParameterInfo {

    private final String name;
    private final String type;
    private final int lineNumber;

    /**
     * Creates a new parameter info object.
     *
     * @param name parameter name
     * @param type parameter type
     * @param lineNumber line number where parameter is defined
     */
    public ParameterInfo(String name, String type, int lineNumber) {
        this.name = name;
        this.type = type;
        this.lineNumber = lineNumber;
    }

    // Getters
    public String getName() { return name; }
    public String getType() { return type; }
    public int getLineNumber() { return lineNumber; }
}