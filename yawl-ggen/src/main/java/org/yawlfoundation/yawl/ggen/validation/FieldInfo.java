package org.yawlfoundation.yawl.ggen.validation;

/**
 * Represents field information extracted from AST analysis.
 */
public class FieldInfo {
    private final String name;
    private final String type;
    private final int lineNumber;

    public FieldInfo(String name, String type, int lineNumber) {
        this.name = name;
        this.type = type;
        this.lineNumber = lineNumber;
    }

    // Getters
    public String getName() { return name; }
    public String getType() { return type; }
    public int getLineNumber() { return lineNumber; }
}