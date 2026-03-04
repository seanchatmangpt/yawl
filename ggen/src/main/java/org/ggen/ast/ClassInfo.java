package org.ggen.ast;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents class information extracted from AST analysis.
 */
public record ClassInfo(
    String name,
    int lineNumber,
    List<MethodInfo> methods,
    List<FieldInfo> fields,
    String packageName
) {
    /**
     * Get all methods with a specific name
     */
    public List<MethodInfo> getMethodsByName(String methodName) {
        return methods.stream()
                .filter(m -> m.name().equals(methodName))
                .collect(Collectors.toList());
    }

    /**
     * Get all public methods
     */
    public List<MethodInfo> getPublicMethods() {
        return methods.stream()
                .filter(m -> m.annotations().contains("Override") || !m.name().startsWith("_"))
                .collect(Collectors.toList());
    }

    /**
     * Get all fields
     */
    public List<FieldInfo> getAllFields() {
        return fields;
    }

    /**
     * Get the full qualified name of the class
     */
    public String getQualifiedName() {
        if (packageName.isEmpty()) {
            return name;
        }
        return packageName + "." + name;
    }

    /**
     * Check if the class has any methods
     */
    public boolean hasMethods() {
        return !methods.isEmpty();
    }

    /**
     * Check if the class has any fields
     */
    public boolean hasFields() {
        return !fields.isEmpty();
    }

    /**
     * Get the number of methods
     */
    public int getMethodCount() {
        return methods.size();
    }

    /**
     * Get the number of fields
     */
    public int getFieldCount() {
        return fields.size();
    }
}