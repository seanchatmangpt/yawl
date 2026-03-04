package org.yawlfoundation.yawl.ggen.validation;

/**
 * Represents a parsed AST tree.
 * This is a simplified implementation for placeholder purposes.
 */
public class Tree {
    private final String source;
    private final boolean isTreeSitter;

    public Tree(String source) {
        this(source, false); // Default to non-tree-sitter for this placeholder
    }

    public Tree(String source, boolean isTreeSitter) {
        this.source = source;
        this.isTreeSitter = isTreeSitter;
    }

    public String getSource() {
        return source;
    }

    public boolean isTreeSitter() {
        return isTreeSitter;
    }
}