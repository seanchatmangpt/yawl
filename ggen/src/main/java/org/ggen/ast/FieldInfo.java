package org.ggen.ast;

/**
 * Represents field information extracted from AST analysis.
 */
public record FieldInfo(
    String name,
    String type,
    int lineNumber
) {
    /**
     * Get the field type with array notation preserved
     */
    public String getTypeWithArrays() {
        return type;
    }

    /**
     * Check if the field is of a specific type
     */
    public boolean isOfType(String typeName) {
        return type.equals(typeName);
    }

    /**
     * Check if the field is a primitive type
     */
    public boolean isPrimitive() {
        return type.equals("int") || type.equals("boolean") ||
               type.equals("long") || type.equals("double") ||
               type.equals("float") || type.equals("char") ||
               type.equals("short") || type.equals("byte");
    }

    /**
     * Get the simple type name (without package)
     */
    public String getSimpleTypeName() {
        int lastDot = type.lastIndexOf('.');
        return lastDot == -1 ? type : type.substring(lastDot + 1);
    }
}