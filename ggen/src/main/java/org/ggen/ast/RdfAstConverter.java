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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

import java.util.List;
import java.util.UUID;

/**
 * Converts Java AST information to RDF model for SPARQL querying.
 *
 * <p>This class transforms Java method and comment information into an RDF model
 * that can be queried using SPARQL for complex guard pattern detection.
 * It creates a semantic representation of the code structure.
 *
 * <p>Key features:
 * <ul>
 *   <li>Converts Java methods to RDF resources</li>
 *   <li>Links comments to their containing methods</li>
 *   <li>Creates typed relationships between code elements</li>
 *   <li>Supports SPARQL queries for semantic analysis</li>
 * </ul>
 *
 * @since 1.0
 */
public class RdfAstConverter {

    /**
     * Namespace for our code-related RDF properties.
     */
    public static final String CODE_NS = "http://ggen.io/code#";

    /**
     * RDF properties for code elements.
     */
    public static final Property HAS_NAME = ModelFactory.createDefaultModel()
            .createProperty(CODE_NS, "name");
    public static final Property HAS_BODY = ModelFactory.createDefaultModel()
            .createProperty(CODE_NS, "body");
    public static final Property HAS_LINE_NUMBER = ModelFactory.createDefaultModel()
            .createProperty(CODE_NS, "lineNumber");
    public static final Property HAS_RETURN_TYPE = ModelFactory.createDefaultModel()
            .createProperty(CODE_NS, "returnType");
    public static final Property HAS_COMMENT = ModelFactory.createDefaultModel()
            .createProperty(CODE_NS, "hasComment");
    public static final Property HAS_JAVADOC = ModelFactory.createDefaultModel()
            .createProperty(CODE_NS, "javadoc");
    public static final Property HAS_TEXT = ModelFactory.createDefaultModel()
            .createProperty(CODE_NS, "text");
    public static final Property THROWS = ModelFactory.createDefaultModel()
            .createProperty(CODE_NS, "throws");

    /**
     * RDF types for code elements.
     */
    public static final Resource CLASS = ModelFactory.createDefaultModel()
            .createResource(CODE_NS + "Class");
    public static final Resource METHOD = ModelFactory.createDefaultModel()
            .createResource(CODE_NS + "Method");
    public static final Resource COMMENT = ModelFactory.createDefaultModel()
            .createResource(CODE_NS + "Comment");

    /**
     * Converts Java method information into an RDF model.
     *
     * @param methods list of Java method information objects
     * @return RDF model containing the converted method information
     */
    public Model convertMethodsToRdf(List<JavaAstParser.MethodInfo> methods) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("code", CODE_NS);

        for (JavaAstParser.MethodInfo method : methods) {
            String methodId = "Method_" + UUID.randomUUID().toString();
            Resource methodRes = model.createResource(CODE_NS + methodId);
            methodRes.addProperty(RDF.type, METHOD);
            methodRes.addProperty(HAS_NAME, method.getName());
            methodRes.addProperty(HAS_BODY, method.getBody());
            methodRes.addProperty(HAS_LINE_NUMBER, model.createTypedLiteral(method.getLineNumber()));
            methodRes.addProperty(HAS_RETURN_TYPE, method.getReturnType());

            // Add comments as linked resources
            for (String commentText : method.getComments()) {
                String commentId = "Comment_" + UUID.randomUUID().toString();
                Resource commentRes = model.createResource(CODE_NS + commentId);
                commentRes.addProperty(RDF.type, COMMENT);
                commentRes.addProperty(HAS_TEXT, commentText);
                commentRes.addProperty(HAS_LINE_NUMBER, model.createTypedLiteral(method.getLineNumber()));
                methodRes.addProperty(HAS_COMMENT, commentRes);
            }
        }

        return model;
    }

    /**
     * Converts Java comments into an RDF model.
     *
     * @param comments list of Java comment information objects
     * @return RDF model containing the converted comment information
     */
    public Model convertCommentsToRdf(List<JavaAstParser.CommentInfo> comments) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("code", CODE_NS);

        for (JavaAstParser.CommentInfo comment : comments) {
            String commentId = "Comment_" + UUID.randomUUID().toString();
            Resource commentRes = model.createResource(CODE_NS + commentId);
            commentRes.addProperty(RDF.type, COMMENT);
            commentRes.addProperty(HAS_TEXT, comment.getText());
            commentRes.addProperty(HAS_LINE_NUMBER, model.createTypedLiteral(comment.getLineNumber()));
        }

        return model;
    }

    /**
     * Converts both methods and comments into a single RDF model.
     *
     * @param methods list of Java method information objects
     * @param comments list of Java comment information objects
     * @return combined RDF model
     */
    public Model convertAstToRdf(List<JavaAstParser.MethodInfo> methods,
                                  List<JavaAstParser.CommentInfo> comments) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("code", CODE_NS);

        // Add methods and their comments
        for (JavaAstParser.MethodInfo method : methods) {
            String methodId = "Method_" + UUID.randomUUID().toString();
            Resource methodRes = model.createResource(CODE_NS + methodId);
            methodRes.addProperty(RDF.type, METHOD);
            methodRes.addProperty(HAS_NAME, method.getName());
            methodRes.addProperty(HAS_BODY, method.getBody());
            methodRes.addProperty(HAS_LINE_NUMBER, model.createTypedLiteral(method.getLineNumber()));
            methodRes.addProperty(HAS_RETURN_TYPE, method.getReturnType());

            // Link comments to this method
            for (String commentText : method.getComments()) {
                String commentId = "Comment_" + UUID.randomUUID().toString();
                Resource commentRes = model.createResource(CODE_NS + commentId);
                commentRes.addProperty(RDF.type, COMMENT);
                commentRes.addProperty(HAS_TEXT, commentText);
                commentRes.addProperty(HAS_LINE_NUMBER, model.createTypedLiteral(method.getLineNumber()));
                methodRes.addProperty(HAS_COMMENT, commentRes);
            }
        }

        // Add standalone comments
        for (JavaAstParser.CommentInfo comment : comments) {
            String commentId = "Comment_" + UUID.randomUUID().toString();
            Resource commentRes = model.createResource(CODE_NS + commentId);
            commentRes.addProperty(RDF.type, COMMENT);
            commentRes.addProperty(HAS_TEXT, comment.getText());
            commentRes.addProperty(HAS_LINE_NUMBER, model.createTypedLiteral(comment.getLineNumber()));
        }

        return model;
    }
}