package org.ggen.ast;

/**
 * Represents parameter information extracted from method declarations.
 */
public record ParameterInfo(
    String name,
    String type,
    boolean varArgs
) {
    /**
     * Create a parameter without varargs
     */
    public ParameterInfo(String name, String type) {
        this(name, type, false);
    }

    /**
     * Get the parameter type with array notation if varargs
     */
    public String getTypeWithNotation() {
        return varArgs ? type + "..." : type;
    }

    /**
     * Check if the parameter is a primitive type
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

    /**
     * Create a parameter from a string representation (fallback)
     */
    public static ParameterInfo fromString(String paramStr) {
        // Simple parsing - in production, use proper JavaParser
        boolean varArgs = paramStr.endsWith("...");
        String type = varArgs ? paramStr.substring(0, paramStr.length() - 3).trim() : paramStr;

        // Extract type and name (simplified)
        int lastSpace = type.lastIndexOf(' ');
        if (lastSpace == -1) {
            return new ParameterInfo("param", type, varArgs);
        }

        String typeName = type.substring(0, lastSpace).trim();
        String paramName = type.substring(lastSpace).trim();

        return new ParameterInfo(paramName, typeName, varArgs);
    }
}