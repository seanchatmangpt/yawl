/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ggen.ast;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.ggen.ast.model.ClassInfo;
import org.yawlfoundation.yawl.ggen.ast.model.CommentInfo;
import org.yawlfoundation.yawl.ggen.ast.model.MethodInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Java AST parser that extracts structural information from Java source files.
 *
 * <p>This parser uses JavaParser to create an abstract syntax tree (AST) from
 * Java source files and extracts information about classes, methods, and comments.
 * It provides a clean interface using the model classes for better type safety.
 *
 * <p>Key features:
 * <ul>
 *   <li>Uses JavaParser library for accurate AST parsing</li>
 *   <li>Extracts method names, bodies, return types, and modifiers</li>
 *   <li>Extracts comment text and locations with type information</li>
 *   <li>Handles complex Java syntax including generics and annotations</li>
 *   <li>Thread-safe for concurrent file processing</li>
 *   <li>Graceful error handling with ParseException</li>
 * </ul>
 *
 * @since 1.0
 */
public class JavaAstParser {

    private final JavaParser javaParser;

    /**
     * Creates a new JavaAstParser with default settings.
     */
    public JavaAstParser() {
        this.javaParser = new JavaParser();
    }

    /**
     * Creates a new JavaAstParser with custom settings.
     *
     * @param javaParser the JavaParser instance to use
     */
    public JavaAstParser(JavaParser javaParser) {
        this.javaParser = javaParser;
    }

  
    /**
     * Parses a Java source file and extracts all structural information.
     *
     * @param javaSource the path to the Java source file
     * @return list of ClassInfo objects (typically one top-level class per file)
     * @throws IOException if the file cannot be read
     * @throws ParseException if the file cannot be parsed
     */
    public List<ClassInfo> parseFile(Path javaSource) throws IOException, ParseException {
        Objects.requireNonNull(javaSource, "Java source path cannot be null");

        if (!Files.exists(javaSource)) {
            throw new IOException("File does not exist: " + javaSource);
        }

        if (!Files.isRegularFile(javaSource)) {
            throw new IOException("Path is not a file: " + javaSource);
        }

        if (!javaSource.toString().endsWith(".java")) {
            throw new IOException("File must have .java extension: " + javaSource);
        }

        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(javaSource);

            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit compilationUnit = parseResult.getResult().get();
                return extractClasses(compilationUnit);
            } else {
                String error = parseResult.getProblems()
                    .stream()
                    .map(p -> p.getMessage())
                    .collect(Collectors.joining("; "));
                throw new ParseException("Failed to parse Java file: " + javaSource +
                                        " - Errors: " + error);
            }
        } catch (Exception e) {
            throw new ParseException("Error parsing Java file: " + javaSource, e);
        }
    }

    /**
     * Extracts all class information from a CompilationUnit.
     *
     * @param compilationUnit the parsed CompilationUnit
     * @return list of ClassInfo objects
     */
    public List<ClassInfo> extractClasses(CompilationUnit compilationUnit) {
        Objects.requireNonNull(compilationUnit, "CompilationUnit cannot be null");

        List<ClassInfo> classes = new ArrayList<>();
        ClassExtractorVisitor visitor = new ClassExtractorVisitor(classes);
        visitor.visit(compilationUnit, null);

        return Collections.unmodifiableList(classes);
    }

    /**
     * Extracts all method information from a CompilationUnit.
     *
     * @param compilationUnit the parsed CompilationUnit
     * @return list of MethodInfo objects
     */
    public List<MethodInfo> extractMethods(CompilationUnit compilationUnit) {
        Objects.requireNonNull(compilationUnit, "CompilationUnit cannot be null");

        List<MethodInfo> methods = new ArrayList<>();
        MethodExtractorVisitor visitor = new MethodExtractorVisitor(methods);
        visitor.visit(compilationUnit, null);

        return Collections.unmodifiableList(methods);
    }

    /**
     * Extracts all comments from a CompilationUnit.
     *
     * @param compilationUnit the parsed CompilationUnit
     * @return list of CommentInfo objects
     */
    public List<CommentInfo> extractComments(CompilationUnit compilationUnit) {
        Objects.requireNonNull(compilationUnit, "CompilationUnit cannot be null");

        List<CommentInfo> comments = new ArrayList<>();
        CommentExtractorVisitor visitor = new CommentExtractorVisitor(comments);
        visitor.visit(compilationUnit, null);

        return Collections.unmodifiableList(comments);
    }

    /**
     * Exception thrown when parsing fails.
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
     * Visitor to extract class information from AST.
     */
    private static class ClassExtractorVisitor extends VoidVisitorAdapter<Void> {
        private final List<ClassInfo> classes;

        public ClassExtractorVisitor(List<ClassInfo> classes) {
            this.classes = new ArrayList<>(classes);
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
            super.visit(classDecl, arg);

            // Extract class information
            String packageName = classDecl.findCompilationUnit()
                .map(cu -> cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse(""))
                .orElse("");

            List<String> modifiers = classDecl.getModifiers()
                .stream()
                .map(m -> m.getKeyword().asString())
                .toList();

            List<MethodInfo> methods = classDecl.getMethods()
                .stream()
                .map(this::extractMethodInfo)
                .toList();

            List<ClassInfo> innerClasses = classDecl.getMembers()
                .stream()
                .filter(m -> m instanceof ClassOrInterfaceDeclaration)
                .map(m -> (ClassOrInterfaceDeclaration) m)
                .map(this::extractClassInfo)
                .toList();

            List<CommentInfo> classComments = extractCommentsForNode(classDecl);

            ClassInfo classInfo = new ClassInfo(
                classDecl.getNameAsString(),
                packageName,
                modifiers,
                classDecl.getRange().map(r -> r.begin.line).orElse(0),
                methods,
                innerClasses,
                classComments
            );

            classes.add(classInfo);
        }

        private ClassInfo extractClassInfo(ClassOrInterfaceDeclaration classDecl) {
            String packageName = classDecl.findCompilationUnit()
                .map(cu -> cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse(""))
                .orElse("");

            List<String> modifiers = classDecl.getModifiers()
                .stream()
                .map(m -> m.getKeyword().asString())
                .toList();

            List<MethodInfo> methods = classDecl.getMethods()
                .stream()
                .map(this::extractMethodInfo)
                .toList();

            return new ClassInfo(
                classDecl.getNameAsString(),
                packageName,
                modifiers,
                classDecl.getRange().map(r -> r.begin.line).orElse(0),
                methods,
                Collections.emptyList(), // Nested inner classes not handled recursively here
                Collections.emptyList()
            );
        }

        private MethodInfo extractMethodInfo(MethodDeclaration methodDecl) {
            String name = methodDecl.getNameAsString();
            String returnType = methodDecl.getTypeAsString();
            List<String> parameterTypes = methodDecl.getParameters()
                .stream()
                .map(p -> p.getTypeAsString())
                .toList();
            List<String> parameterNames = methodDecl.getParameters()
                .stream()
                .map(p -> p.getNameAsString())
                .toList();
            List<String> modifiers = methodDecl.getModifiers()
                .stream()
                .map(m -> m.getKeyword().asString())
                .toList();
            int lineNumber = methodDecl.getRange().map(r -> r.begin.line).orElse(0);
            String body = methodDecl.getBody()
                .map(b -> b.toString())
                .orElse("");

            List<CommentInfo> methodComments = extractCommentsForNode(methodDecl);

            return new MethodInfo(
                name,
                returnType,
                parameterTypes,
                parameterNames,
                modifiers,
                lineNumber,
                body,
                methodComments
            );
        }

        private List<CommentInfo> extractCommentsForNode(com.github.javaparser.ast.Node node) {
            return node.getComments()
                .stream()
                .map(this::convertComment)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        }

        private Optional<CommentInfo> convertComment(Comment comment) {
            try {
                int lineNumber = comment.getRange().map(r -> r.begin.line).orElse(0);
                String rawContent = comment.toString();

                if (comment instanceof com.github.javaparser.ast.comments.LineComment) {
                    com.github.javaparser.ast.comments.LineComment lineComment =
                        (com.github.javaparser.ast.comments.LineComment) comment;
                    String content = lineComment.getContent().trim();
                    return Optional.of(CommentInfo.lineComment(lineNumber, content, rawContent));
                } else if (comment instanceof com.github.javaparser.ast.comments.BlockComment) {
                    com.github.javaparser.ast.comments.BlockComment blockComment =
                        (com.github.javaparser.ast.comments.BlockComment) comment;
                    String content = blockComment.getContent().trim();
                    return Optional.of(CommentInfo.blockComment(lineNumber, content, rawContent));
                }
            } catch (Exception e) {
                // Skip comments that can't be converted
            }
            return Optional.empty();
        }
    }

    /**
     * Visitor to extract method information from AST.
     */
    private static class MethodExtractorVisitor extends VoidVisitorAdapter<Void> {
        private final List<MethodInfo> methods;

        public MethodExtractorVisitor(List<MethodInfo> methods) {
            this.methods = new ArrayList<>(methods);
        }

        @Override
        public void visit(MethodDeclaration methodDecl, Void arg) {
            super.visit(methodDecl, arg);

            ClassExtractorVisitor classVisitor = new ClassExtractorVisitor(null);
            MethodInfo methodInfo = classVisitor.extractMethodInfo(methodDecl);
            methods.add(methodInfo);
        }
    }

    /**
     * Visitor to extract comment information from AST.
     */
    private static class CommentExtractorVisitor extends VoidVisitorAdapter<Void> {
        private final List<CommentInfo> comments;

        public CommentExtractorVisitor(List<CommentInfo> comments) {
            this.comments = new ArrayList<>(comments);
        }

        @Override
        public void visit(Comment comment, Void arg) {
            super.visit(comment, arg);

            ClassExtractorVisitor classVisitor = new ClassExtractorVisitor(null);
            classVisitor.convertComment(comment).ifPresent(comments::add);
        }
    }
}