package org.yawlfoundation.yawl.ggen.validation;

import org.yawlfoundation.yawl.ggen.validation.model.MethodInfo;
import org.yawlfoundation.yawl.ggen.validation.model.CommentInfo;

/**
 * Wrapper for tree-sitter-java parser.
 * This is a placeholder implementation - in a real implementation,
 * this would interface with the actual tree-sitter-java library.
 */
public class TreeSitterJavaWrapper {
    /**
     * Check if tree-sitter-java is available
     * @return true if available (always false for this placeholder)
     */
    public boolean isAvailable() {
        return false; // Placeholder - tree-sitter-java not implemented yet
    }

    /**
     * Parse source code using tree-sitter
     * @param source source code to parse
     * @return parsed tree (placeholder)
     */
    public Tree parse(String source) {
        throw new UnsupportedOperationException("Tree-sitter-java not implemented yet");
    }

    /**
     * Extract methods from tree-sitter AST
     * @param ast parsed AST
     * @return list of methods
     */
    public java.util.List<MethodInfo> extractMethods(Tree ast) {
        return java.util.Collections.emptyList(); // Placeholder
    }

    /**
     * Extract comments from tree-sitter AST
     * @param ast parsed AST
     * @return list of comments
     */
    public java.util.List<CommentInfo> extractComments(Tree ast) {
        return java.util.Collections.emptyList(); // Placeholder
    }

    /**
     * Extract classes from tree-sitter AST
     * @param ast parsed AST
     * @return list of classes
     */
    public java.util.List<ClassInfo> extractClasses(Tree ast) {
        return java.util.Collections.emptyList(); // Placeholder
    }
}