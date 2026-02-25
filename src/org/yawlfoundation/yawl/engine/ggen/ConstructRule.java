/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.engine.ggen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A single SPARQL CONSTRUCT rule in the ggen.toml pipeline.
 *
 * In the construct execution model (A = μ(O)), each rule represents one
 * graph rewriting step: it matches patterns in the ontology (WHERE) and
 * produces derived triples (CONSTRUCT). Unlike SELECT-based rules that
 * extract rows for template iteration, CONSTRUCT rules enrich the ontology
 * graph — the output is itself RDF, queryable and composable.
 *
 * Rules are declared in ggen.toml under {@code [[inference.rules]]}:
 * <pre>
 *   [[inference.rules]]
 *   name = "derive-task-handlers"
 *   description = "Enrich graph with task handler metadata"
 *   construct = """
 *     PREFIX yawl: <http://yawlfoundation.org/yawl#>
 *     CONSTRUCT {
 *       ?task yawl-gen:handlerClass ?handlerClass .
 *     }
 *     WHERE {
 *       ?task a ?taskType ; yawl:taskId ?taskId .
 *       BIND(CONCAT("Y", ?taskId, "Handler") AS ?handlerClass)
 *     }
 *   """
 *   yawl_transition = "enrich_task_handlers"
 * </pre>
 *
 * Alternatively, {@code construct} may reference a file path:
 * <pre>
 *   construct = "queries/migrate-concurrency.sparql"
 * </pre>
 *
 * YAWL integration: each rule may optionally bind to a YAWL workflow
 * transition. When {@code yawlTransition} is set, the rule only fires
 * when that transition is enabled in the workflow net. This allows
 * CONSTRUCT rules to execute in formally correct dependency order,
 * guaranteed by Petri net soundness.
 *
 * @see ConstructPhase
 * @see ConstructReceipt
 */
public class ConstructRule {

    private static final Pattern CONSTRUCT_KEYWORD =
        Pattern.compile("(?i)\\bCONSTRUCT\\b");
    private static final Pattern WHERE_KEYWORD =
        Pattern.compile("(?i)\\bWHERE\\b");
    private static final Pattern FILE_REFERENCE =
        Pattern.compile("^[\\w./\\-]+\\.sparql$");

    private final String name;
    private final String description;
    private final String construct;        // Inline SPARQL or file path
    private final String template;         // Tera template path (optional)
    private final String outputPath;       // Output path template (optional)
    private final String yawlTransition;  // YAWL transition ID (optional)

    /**
     * Create a minimal CONSTRUCT rule with name and query only.
     *
     * @param name      unique rule name (non-empty)
     * @param construct inline SPARQL CONSTRUCT query or .sparql file path
     * @throws IllegalArgumentException if name or construct is invalid
     */
    public ConstructRule(String name, String construct) {
        this(name, "", construct, null, null, null);
    }

    /**
     * Create a full CONSTRUCT rule with all optional fields.
     *
     * @param name           unique rule name (non-empty)
     * @param description    human-readable description (may be empty)
     * @param construct      inline SPARQL CONSTRUCT query or .sparql file path
     * @param template       Tera template path for artifact rendering (may be null)
     * @param outputPath     output path template with {@code {{ var }}} placeholders (may be null)
     * @param yawlTransition YAWL transition ID that enables this rule (may be null)
     * @throws IllegalArgumentException if name or construct is invalid
     */
    public ConstructRule(String name,
                         String description,
                         String construct,
                         String template,
                         String outputPath,
                         String yawlTransition) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Rule name must not be empty");
        }
        if (construct == null || construct.trim().isEmpty()) {
            throw new IllegalArgumentException("Construct query must not be empty");
        }
        validateConstructQuery(name, construct);

        this.name = name.trim();
        this.description = description != null ? description.trim() : "";
        this.construct = construct.trim();
        this.template = template != null ? template.trim() : null;
        this.outputPath = outputPath != null ? outputPath.trim() : null;
        this.yawlTransition = yawlTransition != null ? yawlTransition.trim() : null;
    }

    /**
     * Get the rule name.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the rule description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the SPARQL CONSTRUCT query (inline text or file path).
     */
    public String getConstruct() {
        return construct;
    }

    /**
     * Get the Tera template path, or null if no rendering step.
     */
    public String getTemplate() {
        return template;
    }

    /**
     * Get the output path template, or null if no artifact generation.
     */
    public String getOutputPath() {
        return outputPath;
    }

    /**
     * Get the YAWL transition ID that enables this rule, or null if
     * the rule fires unconditionally in the pipeline.
     */
    public String getYawlTransition() {
        return yawlTransition;
    }

    /**
     * Returns true if this rule's construct field is a file reference
     * (ends with .sparql) rather than an inline query.
     *
     * @return true if construct is a .sparql file path
     */
    public boolean isFileReference() {
        return FILE_REFERENCE.matcher(construct).matches();
    }

    /**
     * Returns true if this rule includes a template rendering step.
     */
    public boolean hasTemplate() {
        return template != null && !template.isEmpty();
    }

    /**
     * Returns true if this rule produces a file artifact (has both template and output path).
     */
    public boolean producesArtifact() {
        return hasTemplate() && outputPath != null && !outputPath.isEmpty();
    }

    /**
     * Returns true if this rule is bound to a specific YAWL workflow transition.
     */
    public boolean hasYawlTransition() {
        return yawlTransition != null && !yawlTransition.isEmpty();
    }

    /**
     * Resolve the actual SPARQL query text.
     *
     * If {@link #isFileReference()} is true, reads the query from the given
     * base directory. Otherwise returns the inline construct string directly.
     *
     * @param baseDir directory to resolve relative .sparql file paths against
     * @return SPARQL CONSTRUCT query text
     * @throws java.io.IOException if the file reference cannot be read
     */
    public String resolveQuery(Path baseDir) throws java.io.IOException {
        if (isFileReference()) {
            Path queryFile = baseDir.resolve(construct);
            if (!Files.exists(queryFile)) {
                throw new java.io.FileNotFoundException(
                    "SPARQL query file not found for rule '" + name + "': " + queryFile);
            }
            return Files.readString(queryFile);
        }
        return construct;
    }

    /**
     * Validate this rule's CONSTRUCT query is syntactically plausible.
     *
     * Checks that the query contains both CONSTRUCT and WHERE keywords
     * and has balanced braces in the CONSTRUCT clause.
     *
     * @param ruleContext description of rule for error messages
     * @return list of validation error messages (empty if valid)
     */
    public java.util.List<String> validateSyntax(String ruleContext) {
        java.util.List<String> errors = new java.util.ArrayList<>();
        String query = isFileReference() ? construct : construct;

        if (isFileReference()) {
            // File references are validated when resolved
            return errors;
        }

        if (!CONSTRUCT_KEYWORD.matcher(query).find()) {
            errors.add(ruleContext + ": CONSTRUCT query missing CONSTRUCT keyword");
        }
        if (!WHERE_KEYWORD.matcher(query).find()) {
            errors.add(ruleContext + ": CONSTRUCT query missing WHERE keyword");
        }

        // Check brace balance in the construct body
        int depth = 0;
        int constructStart = -1;
        for (int i = 0; i < query.length(); i++) {
            char ch = query.charAt(i);
            if (ch == '{') {
                if (constructStart < 0) constructStart = i;
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth < 0) {
                    errors.add(ruleContext + ": Unbalanced braces in CONSTRUCT query");
                    break;
                }
            }
        }
        if (depth != 0) {
            errors.add(ruleContext + ": Unclosed braces in CONSTRUCT query (depth=" + depth + ")");
        }

        return errors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConstructRule)) return false;
        ConstructRule that = (ConstructRule) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return String.format("ConstructRule{name='%s', fileRef=%s, yawlTransition=%s}",
            name, isFileReference(), yawlTransition);
    }

    /**
     * Validate that the construct field is a valid CONSTRUCT query or file reference.
     * Throws immediately at construction time so rules are always internally consistent.
     */
    private static void validateConstructQuery(String name, String construct) {
        String trimmed = construct.trim();

        // File references (.sparql) are always accepted — validated on resolution
        if (FILE_REFERENCE.matcher(trimmed).matches()) {
            return;
        }

        // Inline query must contain CONSTRUCT keyword
        if (!CONSTRUCT_KEYWORD.matcher(trimmed).find()) {
            throw new IllegalArgumentException(
                "Rule '" + name + "': inline construct must be a SPARQL CONSTRUCT query " +
                "(missing CONSTRUCT keyword) or a .sparql file path. Got: " +
                trimmed.substring(0, Math.min(50, trimmed.length())));
        }
    }
}
