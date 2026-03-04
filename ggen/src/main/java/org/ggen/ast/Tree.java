package org.ggen.ast;

import com.github.javaparser.ast.CompilationUnit;

import java.util.Optional;

/**
 * Represents a parsed AST tree.
 * Can be either from JavaParser or tree-sitter-java.
 */
public class Tree {
    private final String source;
    private final CompilationUnit compilationUnit;
    private final boolean isTreeSitter;

    /**
     * Create a Tree from JavaParser
     */
    public Tree(CompilationUnit compilationUnit, String source, boolean isTreeSitter) {
        this.compilationUnit = compilationUnit;
        this.source = source;
        this.isTreeSitter = isTreeSitter;
    }

    /**
     * Create a Tree from source only (tree-sitter case)
     */
    public Tree(String source, boolean isTreeSitter) {
        this(null, source, isTreeSitter);
    }

    /**
     * Get the source code
     */
    public String getSource() {
        return source;
    }

    /**
     * Get the CompilationUnit (only for JavaParser trees)
     */
    public Optional<CompilationUnit> getCompilationUnit() {
        return Optional.ofNullable(compilationUnit);
    }

    /**
     * Check if this is a tree-sitter tree
     */
    public boolean isTreeSitter() {
        return isTreeSitter;
    }
}