package org.yawlfoundation.yawl.ggen.validation.model;

/**
 * Represents information about a field declaration extracted from Java source.
 */
public class FieldInfo {
    private final String name;
    private final String type;
    private final int lineNumber;
    private final String modifiers;
    private final String initialValue;
    private final String className;

    public FieldInfo(String name, String type, int lineNumber,
                     String modifiers, String initialValue, String className) {
        this.name = name;
        this.type = type;
        this.lineNumber = lineNumber;
        this.modifiers = modifiers;
        this.initialValue = initialValue;
        this.className = className;
    }

    // Getters
    public String getName() { return name; }
    public String getType() { return type; }
    public int getLineNumber() { return lineNumber; }
    public String getModifiers() { return modifiers; }
    public String getInitialValue() { return initialValue; }
    public String getClassName() { return className; }

    @Override
    public String toString() {
        return "FieldInfo{name='" + name + "', type='" + type + "', lineNumber=" + lineNumber + '}';
    }
}
