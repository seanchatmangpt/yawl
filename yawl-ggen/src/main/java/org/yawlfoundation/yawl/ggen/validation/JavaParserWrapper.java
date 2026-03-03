package org.yawlfoundation.yawl.ggen.validation;

import org.yawlfoundation.yawl.ggen.validation.model.CommentInfo;
import org.yawlfoundation.yawl.ggen.validation.model.MethodInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wrapper for JavaParser library.
 * This is a simplified implementation that uses regex parsing.
 */
public class JavaParserWrapper {
    /**
     * Parse source code using JavaParser (simplified with regex)
     * @param source source code to parse
     * @return parsed tree
     */
    public Tree parse(String source) {
        return new Tree(source);
    }

    /**
     * Extract methods from JavaParser AST
     * @param ast parsed AST
     * @return list of methods
     */
    public List<MethodInfo> extractMethods(Tree ast) {
        List<MethodInfo> methods = new ArrayList<>();

        // Use regex to find method declarations
        Pattern methodPattern = Pattern.compile(
            "(?:(?:public|protected|private)\\s+)?(?:static\\s+)?(?:final\\s+)?" +
            "(?:[\\w<>\\[\\],\\s]+?)\\s+" +  // Return type
            "([\\w]+)\\s*" +                  // Method name
            "\\(([^)]*)\\)" +                 // Parameters
            "\\s*\\{[^}]*\\}"                 // Method body (simplified)
        );

        Matcher matcher = methodPattern.matcher(ast.getSource());
        int lineNumber = 1;

        while (matcher.find()) {
            String name = matcher.group(1);
            String body = matcher.group(0);

            int methodStart = findLineNumber(ast.getSource(), matcher.start());

            MethodInfo method = new MethodInfo(
                name,
                "void", // Default return type
                body,
                methodStart,
                methodStart + body.split("\n").length, // Rough estimate
                List.of(), // parameters
                List.of(), // modifiers
                "UnknownClass", // className
                List.of(), // annotations
                List.of() // comments
            );

            methods.add(method);
        }

        return methods;
    }

    /**
     * Extract comments from JavaParser AST
     * @param ast parsed AST
     * @return list of comments
     */
    public List<CommentInfo> extractComments(Tree ast) {
        List<CommentInfo> comments = new ArrayList<>();

        // Pattern to match both single-line (//) and multi-line (/* */) comments
        Pattern commentPattern = Pattern.compile(
            "//.*?$|" +  // Single-line comments
            "/\\*.*?\\*/"  // Multi-line comments (non-greedy)
        );

        Matcher matcher = commentPattern.matcher(ast.getSource());

        while (matcher.find()) {
            int line = findLineNumber(ast.getSource(), matcher.start());
            String text = matcher.group().trim();

            CommentInfo.CommentType type = text.startsWith("/**") ? CommentInfo.CommentType.JAVADOC :
                                          text.startsWith("//") ? CommentInfo.CommentType.LINE :
                                          CommentInfo.CommentType.BLOCK;

            CommentInfo comment = new CommentInfo(type, text, line, line, null);
            comments.add(comment);
        }

        return comments;
    }

    /**
     * Extract classes from JavaParser AST
     * @param ast parsed AST
     * @return list of classes
     */
    public List<ClassInfo> extractClasses(Tree ast) {
        // Placeholder - not implemented in this simplified version
        return new ArrayList<>();
    }

    /**
     * Find the line number for a given character position in the source
     */
    private int findLineNumber(String source, int position) {
        String substring = source.substring(0, position);
        return substring.split("\n").length;
    }
}