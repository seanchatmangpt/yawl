package org.yawlfoundation.yawl.ggen.validation;

import org.yawlfoundation.yawl.ggen.validation.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Java AST parser that extracts code structure for guard validation.
 * Uses JavaParser as primary with optional tree-sitter-java fallback.
 */
public class JavaAstParser {
    private static final Logger logger = LoggerFactory.getLogger(JavaAstParser.class);

    private final boolean useTreeSitter;
    private TreeSitterJavaWrapper treeSitterWrapper;
    private JavaParserWrapper javaParserWrapper;

    /**
     * Creates a new JavaAstParser
     * @param useTreeSitter true to attempt tree-sitter-java, false to use JavaParser only
     */
    public JavaAstParser(boolean useTreeSitter) {
        this.useTreeSitter = useTreeSitter;
        this.treeSitterWrapper = new TreeSitterJavaWrapper();
        this.javaParserWrapper = new JavaParserWrapper();
    }

    /**
     * Creates a JavaAstParser with tree-sitter enabled
     */
    public JavaAstParser() {
        this(true);
    }

    /**
     * Parses a Java source file into an AST
     * @param javaSource path to the Java source file
     * @return parsed AST
     * @throws IOException if the file cannot be read
     * @throws ParseException if the file cannot be parsed
     */
    public Tree parseFile(Path javaSource) throws IOException, ParseException {
        if (!Files.exists(javaSource)) {
            throw new IOException("File not found: " + javaSource);
        }

        if (!javaSource.toString().endsWith(".java")) {
            throw new IOException("File must be a Java source file (.java): " + javaSource);
        }

        try {
            String source = Files.readString(javaSource);

            if (useTreeSitter && treeSitterWrapper.isAvailable()) {
                logger.debug("Using tree-sitter-java for parsing: {}", javaSource);
                return treeSitterWrapper.parse(source);
            } else {
                logger.debug("Using JavaParser for parsing: {}", javaSource);
                return javaParserWrapper.parse(source);
            }
        } catch (Exception e) {
            logger.error("Failed to parse file: {}", javaSource, e);
            throw new ParseException("Failed to parse Java file: " + javaSource, e);
        }
    }

    /**
     * Extracts method declarations from the AST
     * @param ast the parsed AST
     * @return list of method information
     */
    public List<MethodInfo> extractMethods(Tree ast) {
        if (ast == null) {
            return Collections.emptyList();
        }

        try {
            if (ast.isTreeSitter()) {
                return treeSitterWrapper.extractMethods(ast);
            } else {
                return javaParserWrapper.extractMethods(ast);
            }
        } catch (Exception e) {
            logger.error("Failed to extract methods from AST", e);
            return Collections.emptyList();
        }
    }

    /**
     * Extracts comments from the AST
     * @param ast the parsed AST
     * @return list of comment information
     */
    public List<CommentInfo> extractComments(Tree ast) {
        if (ast == null) {
            return Collections.emptyList();
        }

        try {
            if (ast.isTreeSitter()) {
                return treeSitterWrapper.extractComments(ast);
            } else {
                return javaParserWrapper.extractComments(ast);
            }
        } catch (Exception e) {
            logger.error("Failed to extract comments from AST", e);
            return Collections.emptyList();
        }
    }

    /**
     * Extracts class declarations from the AST
     * @param ast the parsed AST
     * @return list of class information
     */
    public List<ClassInfo> extractClasses(Tree ast) {
        if (ast == null) {
            return Collections.emptyList();
        }

        try {
            if (ast.isTreeSitter()) {
                return treeSitterWrapper.extractClasses(ast);
            } else {
                return javaParserWrapper.extractClasses(ast);
            }
        } catch (Exception e) {
            logger.error("Failed to extract classes from AST", e);
            return Collections.emptyList();
        }
    }

    /**
     * Parse a file and extract all information at once
     * @param javaSource path to the Java source file
     * @return ParseResult containing all extracted information
     * @throws IOException if the file cannot be read
     * @throws ParseException if the file cannot be parsed
     */
    public ParseResult parseAndExtract(Path javaSource) throws IOException, ParseException {
        Tree ast = parseFile(javaSource);
        List<MethodInfo> methods = extractMethods(ast);
        List<CommentInfo> comments = extractComments(ast);
        List<ClassInfo> classes = extractClasses(ast);

        return new ParseResult(javaSource, ast, methods, comments, classes);
    }

    /**
     * Check if tree-sitter-java is available
     * @return true if tree-sitter is available and enabled
     */
    public boolean isTreeSitterAvailable() {
        return useTreeSitter && treeSitterWrapper.isAvailable();
    }

    /**
     * Exception thrown when parsing fails
     */
    public static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Result of parsing and extracting information from a Java file
     */
    public static class ParseResult {
        private final Path filePath;
        private final Tree ast;
        private final List<MethodInfo> methods;
        private final List<CommentInfo> comments;
        private final List<ClassInfo> classes;

        public ParseResult(Path filePath, Tree ast, List<MethodInfo> methods,
                          List<CommentInfo> comments, List<ClassInfo> classes) {
            this.filePath = filePath;
            this.ast = ast;
            this.methods = Collections.unmodifiableList(new ArrayList<>(methods));
            this.comments = Collections.unmodifiableList(new ArrayList<>(comments));
            this.classes = Collections.unmodifiableList(new ArrayList<>(classes));
        }

        // Getters
        public Path getFilePath() { return filePath; }
        public Tree getAst() { return ast; }
        public List<MethodInfo> getMethods() { return methods; }
        public List<CommentInfo> getComments() { return comments; }
        public List<ClassInfo> getClasses() { return classes; }
    }
}