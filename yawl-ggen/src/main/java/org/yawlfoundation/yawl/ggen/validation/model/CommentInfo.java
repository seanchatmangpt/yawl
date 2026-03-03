package org.yawlfoundation.yawl.ggen.validation.model;

/**
 * Represents information about a comment extracted from Java source.
 */
public class CommentInfo {
    public enum CommentType {
        LINE,       // Single-line comment: //
        BLOCK,      // Multi-line comment: /* */
        JAVADOC     // Javadoc comment: /** */
    }

    private final CommentType type;
    private final String text;
    private final int lineNumber;
    private final int endLineNumber;
    private final String associatedElement; // Method or class name if associated

    public CommentInfo(CommentType type, String text, int lineNumber,
                       int endLineNumber, String associatedElement) {
        this.type = type;
        this.text = text;
        this.lineNumber = lineNumber;
        this.endLineNumber = endLineNumber;
        this.associatedElement = associatedElement;
    }

    // Getters
    public CommentType getType() { return type; }
    public String getText() { return text; }
    public int getLineNumber() { return lineNumber; }
    public int getEndLineNumber() { return endLineNumber; }
    public String getAssociatedElement() { return associatedElement; }

    public boolean isJavadoc() { return type == CommentType.JAVADOC; }
    public boolean isLineComment() { return type == CommentType.LINE; }
    public boolean isBlockComment() { return type == CommentType.BLOCK; }

    @Override
    public String toString() {
        return "CommentInfo{type=" + type + ", lineNumber=" + lineNumber + '}';
    }
}
