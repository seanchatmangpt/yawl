package org.yawlfoundation.yawl.ggen.validation.model;

import java.util.List;

/**
 * Represents information about a method declaration extracted from Java source.
 */
public class MethodInfo {
    private final String name;
    private final String returnType;
    private final String body;
    private final int lineNumber;
    private final int endLineNumber;
    private final List<String> parameters;
    private final List<String> modifiers;
    private final String className;
    private final List<String> annotations;
    private final List<CommentInfo> comments;

    public MethodInfo(String name, String returnType, String body,
                      int lineNumber, int endLineNumber,
                      List<String> parameters, List<String> modifiers,
                      String className, List<String> annotations, List<CommentInfo> comments) {
        this.name = name;
        this.returnType = returnType;
        this.body = body;
        this.lineNumber = lineNumber;
        this.endLineNumber = endLineNumber;
        this.parameters = parameters != null ? List.copyOf(parameters) : List.of();
        this.modifiers = modifiers != null ? List.copyOf(modifiers) : List.of();
        this.className = className;
        this.annotations = annotations != null ? List.copyOf(annotations) : List.of();
        this.comments = comments != null ? List.copyOf(comments) : List.of();
    }

    // Getters
    public String getName() { return name; }
    public String getReturnType() { return returnType; }
    public String getBody() { return body; }
    public int getLineNumber() { return lineNumber; }
    public int getEndLineNumber() { return endLineNumber; }
    public List<String> getParameters() { return parameters; }
    public List<String> getModifiers() { return modifiers; }
    public String getClassName() { return className; }
    public List<String> getAnnotations() { return annotations; }
    public List<CommentInfo> getComments() { return comments; }

    public boolean isVoid() {
        return "void".equals(returnType);
    }

    public boolean hasBody() {
        return body != null && !body.isBlank();
    }

    @Override
    public String toString() {
        return "MethodInfo{name='" + name + "', returnType='" + returnType + "', lineNumber=" + lineNumber + '}';
    }
}
