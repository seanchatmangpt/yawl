package org.ggen.ast;

/**
 * Represents information about a comment extracted from Java AST.
 */
public record CommentInfo(
    String id,
    String text,
    int lineNumber,
    CommentType type
) {
    /**
     * Get the cleaned text of the comment (without comment markers)
     */
    public String getCleanText() {
        return text.replaceAll("^[/\\*]+\\s*", "").replaceAll("\\s*[/\\*]+$", "").trim();
    }

    /**
     * Check if the comment contains a specific keyword
     */
    public boolean containsKeyword(String keyword) {
        return getCleanText().toLowerCase().contains(keyword.toLowerCase());
    }

    /**
     * Get the first line of the comment
     */
    public String getFirstLine() {
        return getCleanText().split("\n")[0].trim();
    }

    /**
     * Check if this is a TODO comment
     */
    public boolean isTodo() {
        return containsKeyword("TODO") || containsKeyword("FIXME");
    }

    /**
     * Check if this is a Javadoc comment
     */
    public boolean isJavadoc() {
        return type == CommentType.JAVADOC;
    }

    /**
     * Comment types
     */
    public enum CommentType {
        LINE,      // //
        BLOCK,     // /* */
        JAVADOC    // /** */
    }
}