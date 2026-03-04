package org.yawlfoundation.yawl.ggen.validation.model;

/**
 * Represents information about an annotation in the Java AST.
 */
public class AnnotationInfo {
    private final String name;
    private final String value;
    private final int lineNumber;

    public AnnotationInfo(String name, String value, int lineNumber) {
        this.name = name;
        this.value = value;
        this.lineNumber = lineNumber;
    }

    // Getters
    public String getName() { return name; }
    public String getValue() { return value; }
    public int getLineNumber() { return lineNumber; }

    @Override
    public String toString() {
        return "AnnotationInfo{name='" + name + "', value='" + value + "', lineNumber=" + lineNumber + "}";
    }
}