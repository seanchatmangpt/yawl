package org.ggen.ast;

import java.util.List;
import java.util.Map;

/**
 * Represents information about a method extracted from Java AST.
 */
public record MethodInfo(
    String name,
    String body,
    int lineNumber,
    String returnType,
    List<ParameterInfo> parameters,
    List<String> annotations
) {
    /**
     * Create a MethodInfo with empty parameters and annotations
     */
    public MethodInfo(String name, String body, int lineNumber, String returnType) {
        this(name, body, lineNumber, returnType, List.of(), List.of());
    }

    /**
     * Get the full signature of the method
     */
    public String getSignature() {
        StringBuilder signature = new StringBuilder();
        signature.append(returnType).append(" ").append(name);
        signature.append("(");

        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                signature.append(", ");
            }
            ParameterInfo param = parameters.get(i);
            signature.append(param.varArgs() ? "..." : "")
                    .append(param.type())
                    .append(" ")
                    .append(param.name());
        }

        signature.append(")");
        return signature.toString();
    }

    /**
     * Check if the method has a specific annotation
     */
    public boolean hasAnnotation(String annotationName) {
        return annotations.contains(annotationName);
    }

    /**
     * Check if the method has any parameters
     */
    public boolean hasParameters() {
        return !parameters.isEmpty();
    }

    /**
     * Get the parameter names as a list
     */
    public List<String> getParameterNames() {
        return parameters.stream()
                .map(ParameterInfo::name)
                .toList();
    }
}