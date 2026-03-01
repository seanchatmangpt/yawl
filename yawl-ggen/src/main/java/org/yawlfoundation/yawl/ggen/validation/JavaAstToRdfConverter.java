/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.validation;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Java source code to RDF facts for SPARQL querying.
 * Performs lightweight AST extraction by parsing Java source with regex patterns
 * and converting to RDF triples for semantic analysis.
 *
 * Extracts:
 * - Method declarations (name, line, return type)
 * - Method bodies (content)
 * - Comments (content, attached to methods)
 * - Catch blocks
 * - Return statements
 */
public final class JavaAstToRdfConverter {
    private static final String CODE_NS = "http://yawlfoundation.org/ggen/code#";

    private static final Pattern METHOD_PATTERN =
        Pattern.compile(
            "(?:public|private|protected)?\\s+(?:static)?\\s+(\\w+)\\s+(\\w+)\\s*\\(([^)]*)\\)",
            Pattern.MULTILINE
        );

    private static final Pattern CLASS_PATTERN =
        Pattern.compile(
            "(?:public)?\\s*class\\s+(\\w+)",
            Pattern.MULTILINE
        );

    private static final Pattern COMMENT_PATTERN =
        Pattern.compile("//\\s*(.*)$", Pattern.MULTILINE);

    private static final Pattern CATCH_PATTERN =
        Pattern.compile(
            "catch\\s*\\(\\s*([^)]+)\\s*\\)\\s*\\{([^}]*)\\}",
            Pattern.MULTILINE
        );

    private static final Pattern RETURN_PATTERN =
        Pattern.compile("return\\s+([^;]+);", Pattern.MULTILINE);

    private JavaAstToRdfConverter() {
        // Utility class - no instantiation
    }

    /**
     * Convert a Java source file to an RDF model.
     *
     * @param javaSource the path to the Java source file
     * @return an RDF Model with extracted code facts
     * @throws IOException if the file cannot be read
     */
    public static Model convertFile(Path javaSource) throws IOException {
        Objects.requireNonNull(javaSource, "javaSource must not be null");

        String sourceCode = Files.readString(javaSource);
        return convertSource(sourceCode, javaSource.getFileName().toString());
    }

    /**
     * Convert Java source code string to an RDF model.
     *
     * @param sourceCode the Java source code
     * @param sourceId identifier for this source (typically filename)
     * @return an RDF Model with extracted code facts
     */
    public static Model convertSource(String sourceCode, String sourceId) {
        Objects.requireNonNull(sourceCode, "sourceCode must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");

        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("code", CODE_NS);

        // Create custom properties
        Property methodName = model.createProperty(CODE_NS + "methodName");
        Property returnType = model.createProperty(CODE_NS + "returnType");
        Property methodBody = model.createProperty(CODE_NS + "methodBody");
        Property lineNumber = model.createProperty(CODE_NS + "lineNumber");
        Property commentText = model.createProperty(CODE_NS + "commentText");
        Property hasComment = model.createProperty(CODE_NS + "hasComment");
        Property className = model.createProperty(CODE_NS + "className");
        Property catchContent = model.createProperty(CODE_NS + "catchContent");
        Property exceptionType = model.createProperty(CODE_NS + "exceptionType");
        Property returnStatement = model.createProperty(CODE_NS + "returnStatement");

        // Extract and add classes
        String[] lines = sourceCode.split("\n");
        extractClasses(sourceCode, model, className);

        // Extract and add methods
        extractMethods(sourceCode, lines, model, methodName, returnType, methodBody,
                       lineNumber, hasComment);

        // Extract and add comments
        extractComments(sourceCode, lines, model, commentText);

        // Extract and add catch blocks
        extractCatchBlocks(sourceCode, lines, model, catchContent, exceptionType);

        // Extract and add return statements
        extractReturnStatements(sourceCode, lines, model, returnStatement);

        return model;
    }

    private static void extractClasses(String sourceCode, Model model, Property className) {
        Matcher matcher = CLASS_PATTERN.matcher(sourceCode);
        while (matcher.find()) {
            String classNameValue = matcher.group(1);
            Resource classRes = model.createResource(
                CODE_NS + "Class_" + UUID.randomUUID()
            );
            classRes.addProperty(RDF.type, model.createResource(CODE_NS + "Class"));
            classRes.addProperty(className, classNameValue);
            classRes.addProperty(
                model.createProperty(CODE_NS + "sourceId"),
                model.createLiteral("(unknown)")
            );
        }
    }

    private static void extractMethods(String sourceCode, String[] lines, Model model,
                                      Property methodName, Property returnType,
                                      Property methodBody, Property lineNumber,
                                      Property hasComment) {
        Matcher matcher = METHOD_PATTERN.matcher(sourceCode);
        while (matcher.find()) {
            String retType = matcher.group(1);
            String methName = matcher.group(2);
            int startPos = matcher.start();
            int lineNum = countNewlines(sourceCode, 0, startPos) + 1;

            Resource methodRes = model.createResource(
                CODE_NS + "Method_" + UUID.randomUUID()
            );
            methodRes.addProperty(RDF.type, model.createResource(CODE_NS + "Method"));
            methodRes.addProperty(methodName, methName);
            methodRes.addProperty(returnType, retType);
            methodRes.addProperty(lineNumber, model.createTypedLiteral(lineNum));
            methodRes.addProperty(methodBody, extractMethodBody(sourceCode, matcher.end()));

            // Find and attach comments
            for (int i = Math.max(0, lineNum - 3); i < Math.min(lines.length, lineNum); i++) {
                Matcher commentMatcher = COMMENT_PATTERN.matcher(lines[i]);
                if (commentMatcher.find()) {
                    Resource commentRes = model.createResource(
                        CODE_NS + "Comment_" + UUID.randomUUID()
                    );
                    commentRes.addProperty(RDF.type, model.createResource(CODE_NS + "Comment"));
                    commentRes.addProperty(
                        model.createProperty(CODE_NS + "text"),
                        commentMatcher.group(1)
                    );
                    methodRes.addProperty(hasComment, commentRes);
                }
            }
        }
    }

    private static void extractComments(String sourceCode, String[] lines, Model model,
                                       Property commentText) {
        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = COMMENT_PATTERN.matcher(lines[i]);
            if (matcher.find()) {
                Resource commentRes = model.createResource(
                    CODE_NS + "Comment_" + UUID.randomUUID()
                );
                commentRes.addProperty(RDF.type, model.createResource(CODE_NS + "Comment"));
                commentRes.addProperty(commentText, matcher.group(1));
                commentRes.addProperty(
                    model.createProperty(CODE_NS + "lineNumber"),
                    model.createTypedLiteral(i + 1)
                );
            }
        }
    }

    private static void extractCatchBlocks(String sourceCode, String[] lines, Model model,
                                          Property catchContent, Property exceptionType) {
        Matcher matcher = CATCH_PATTERN.matcher(sourceCode);
        while (matcher.find()) {
            String excType = matcher.group(1).trim();
            String content = matcher.group(2);

            Resource catchRes = model.createResource(
                CODE_NS + "CatchBlock_" + UUID.randomUUID()
            );
            catchRes.addProperty(RDF.type, model.createResource(CODE_NS + "CatchBlock"));
            catchRes.addProperty(exceptionType, excType);
            catchRes.addProperty(catchContent, content);
        }
    }

    private static void extractReturnStatements(String sourceCode, String[] lines, Model model,
                                               Property returnStatement) {
        Matcher matcher = RETURN_PATTERN.matcher(sourceCode);
        while (matcher.find()) {
            String returnValue = matcher.group(1).trim();
            int startPos = matcher.start();
            int lineNum = countNewlines(sourceCode, 0, startPos) + 1;

            Resource returnRes = model.createResource(
                CODE_NS + "ReturnStatement_" + UUID.randomUUID()
            );
            returnRes.addProperty(RDF.type, model.createResource(CODE_NS + "ReturnStatement"));
            returnRes.addProperty(returnStatement, returnValue);
            returnRes.addProperty(
                model.createProperty(CODE_NS + "lineNumber"),
                model.createTypedLiteral(lineNum)
            );
        }
    }

    private static String extractMethodBody(String source, int startPos) {
        int braceCount = 0;
        boolean inBody = false;
        int bodyStart = startPos;
        int bodyEnd = startPos;

        for (int i = startPos; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') {
                if (!inBody) {
                    bodyStart = i + 1;
                    inBody = true;
                }
                braceCount++;
            } else if (ch == '}') {
                braceCount--;
                if (braceCount == 0 && inBody) {
                    bodyEnd = i;
                    break;
                }
            }
        }

        if (inBody && bodyEnd > bodyStart) {
            return source.substring(bodyStart, bodyEnd).trim();
        }
        throw new IllegalArgumentException(
            "Failed to extract method body: no matching braces found at position " + startPos
        );
    }

    private static int countNewlines(String s, int start, int end) {
        int count = 0;
        for (int i = start; i < end && i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }
}
