package org.yawlfoundation.yawl.ggen.validation;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.yawlfoundation.yawl.ggen.validation.model.CommentInfo;
import org.yawlfoundation.yawl.ggen.validation.model.MethodInfo;

import java.util.List;

/**
 * Converts Java AST information to RDF Model for SPARQL querying.
 */
public class RdfAstConverter {

    private static final String CODE_NS = "http://ggen.io/code#";
    private static final String JAVADOC_NS = "http://ggen.io/javadoc#";

    /**
     * Converts AST information to RDF Model
     * @param methods List of method information from AST
     * @param comments List of comment information from AST
     * @return RDF Model containing the converted data
     */
    public Model convertAstToRdf(List<MethodInfo> methods, List<CommentInfo> comments) {
        Model model = ModelFactory.createDefaultModel();

        // Set up namespaces
        model.setNsPrefix("code", CODE_NS);
        model.setNsPrefix("javadoc", JAVADOC_NS);

        // Convert methods to RDF
        for (MethodInfo method : methods) {
            Resource methodRes = createMethodResource(model, method);
            model.add(methodRes, RDF.type, model.createResource(CODE_NS + "Method"));

            // Add method properties
            if (method.getName() != null) {
                model.add(methodRes, model.createProperty(CODE_NS + "name"),
                         model.createLiteral(method.getName()));
            }
            if (method.getBody() != null) {
                model.add(methodRes, model.createProperty(CODE_NS + "body"),
                         model.createLiteral(method.getBody()));
            }
            if (method.getReturnType() != null) {
                model.add(methodRes, model.createProperty(CODE_NS + "returnType"),
                         model.createLiteral(method.getReturnType()));
            }
            if (method.getLineNumber() > 0) {
                model.add(methodRes, model.createProperty(CODE_NS + "lineNumber"),
                         model.createTypedLiteral(method.getLineNumber()));
            }

            // Add annotations/javadoc if present
            if (method.getAnnotations() != null && !method.getAnnotations().isEmpty()) {
                for (String annotation : method.getAnnotations()) {
                    model.add(methodRes, model.createProperty(CODE_NS + "hasAnnotation"),
                             model.createLiteral(annotation));
                }
            }

            // Link comments to this method
            for (CommentInfo comment : comments) {
                if (isCommentInMethod(comment, method)) {
                    Resource commentRes = createCommentResource(model, comment);
                    model.add(methodRes, model.createProperty(CODE_NS + "hasComment"), commentRes);
                }
            }
        }

        // Convert standalone comments (not in methods)
        for (CommentInfo comment : comments) {
            if (comment.getLineNumber() > 0 && !isCommentInMethod(comment, null)) {
                createCommentResource(model, comment);
            }
        }

        return model;
    }

    /**
     * Creates an RDF resource for a method
     */
    private Resource createMethodResource(Model model, MethodInfo method) {
        String methodId = "Method_" + method.getName() + "_" + method.getLineNumber();
        return model.createResource(CODE_NS + methodId);
    }

    /**
     * Creates an RDF resource for a comment
     */
    private Resource createCommentResource(Model model, CommentInfo comment) {
        String commentId = "Comment_" + comment.getLineNumber() + "_" + comment.getText().hashCode();
        Resource commentRes = model.createResource(CODE_NS + commentId);

        model.add(commentRes, RDF.type, model.createResource(CODE_NS + "Comment"));
        if (comment.getText() != null) {
            model.add(commentRes, model.createProperty(CODE_NS + "text"),
                     model.createLiteral(comment.getText()));
        }
        if (comment.getLineNumber() > 0) {
            model.add(commentRes, model.createProperty(CODE_NS + "lineNumber"),
                     model.createTypedLiteral(comment.getLineNumber()));
        }

        return commentRes;
    }

    /**
     * Checks if a comment belongs to a method (heuristic-based)
     */
    private boolean isCommentInMethod(CommentInfo comment, MethodInfo method) {
        if (method == null) return false;

        // Simple heuristic: if comment is before method and within reasonable range
        if (comment.getLineNumber() < method.getLineNumber()) {
            // Check if comment is within 10 lines before method (likely javadoc)
            return (method.getLineNumber() - comment.getLineNumber()) <= 10;
        }

        // Comment could be inside method body
        return comment.getLineNumber() > method.getLineNumber() &&
               comment.getLineNumber() < method.getLineNumber() + 50; // Max method length
    }
}