/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.tooling.docgen;

import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCompositeTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.data.YVariable;
import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.elements.data.YVariable;
import org.yawlfoundation.yawl.tooling.cli.YawlCliCommand;
import org.yawlfoundation.yawl.unmarshal.YMarshal;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * Generates workflow documentation (Markdown or HTML) for a YAWL specification.
 *
 * For each specification the output includes:
 * <ul>
 *   <li>Specification metadata (URI, name, version, documentation)</li>
 *   <li>Per-decomposition sections describing tasks, conditions, and flows</li>
 *   <li>Input/output parameter tables for each decomposition</li>
 *   <li>Complexity metrics (element count, flow count, join/split distribution)</li>
 * </ul>
 *
 * Usage (programmatic):
 * <pre>
 *   WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
 *   String markdown = gen.generateMarkdown();
 *   String html     = gen.generateHtml();
 * </pre>
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public class WorkflowDocumentationGenerator {

    private final YSpecification spec;

    /**
     * Construct a generator for the given specification.
     * @param spec the loaded YAWL specification
     */
    public WorkflowDocumentationGenerator(YSpecification spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Specification cannot be null");
        }
        this.spec = spec;
    }

    // ---- Markdown generation --------------------------------------------------

    /**
     * Generate Markdown documentation for the specification.
     * @return Markdown string
     */
    public String generateMarkdown() {
        StringBuilder sb = new StringBuilder();

        // Title and metadata
        sb.append("# ").append(mdEscape(specDisplayName())).append("\n\n");
        sb.append("> **URI:** `").append(spec.getURI()).append("`  \n");
        sb.append("> **Schema version:** ").append(spec.getSchemaVersion()).append("  \n");
        if (spec.getMetaData() != null) {
            sb.append("> **Spec version:** ").append(spec.getMetaData().getVersion()).append("  \n");
        }
        sb.append("> **Generated:** ").append(LocalDate.now()).append("\n\n");

        if (spec.getDocumentation() != null && !spec.getDocumentation().isBlank()) {
            sb.append(spec.getDocumentation().trim()).append("\n\n");
        }

        // Complexity metrics
        sb.append("## Workflow Metrics\n\n");
        appendMarkdownMetrics(sb);

        // Root net
        if (spec.getRootNet() != null) {
            appendMarkdownNet(sb, spec.getRootNet(), true);
        }

        // Sub-nets
        for (YDecomposition decomp : spec.getDecompositions()) {
            if (decomp instanceof YNet net && net != spec.getRootNet()) {
                appendMarkdownNet(sb, net, false);
            }
        }

        // Service gateways
        for (YDecomposition decomp : spec.getDecompositions()) {
            if (!(decomp instanceof YNet)) {
                sb.append("## Service Gateway: ").append(mdEscape(decomp.getID())).append("\n\n");
                if (decomp.getDocumentation() != null) {
                    sb.append(decomp.getDocumentation().trim()).append("\n\n");
                }
                appendMarkdownParameters(sb, decomp);
            }
        }

        return sb.toString();
    }

    private void appendMarkdownMetrics(StringBuilder sb) {
        WorkflowMetrics metrics = computeMetrics();
        sb.append("| Metric | Value |\n");
        sb.append("|--------|-------|\n");
        sb.append("| Decompositions | ").append(metrics.decompositionCount()).append(" |\n");
        sb.append("| Net elements (total) | ").append(metrics.totalElements()).append(" |\n");
        sb.append("| Atomic tasks | ").append(metrics.atomicTaskCount()).append(" |\n");
        sb.append("| Composite tasks | ").append(metrics.compositeTaskCount()).append(" |\n");
        sb.append("| Conditions | ").append(metrics.conditionCount()).append(" |\n");
        sb.append("| Flows (total) | ").append(metrics.flowCount()).append(" |\n");
        sb.append("| AND-splits | ").append(metrics.andSplitCount()).append(" |\n");
        sb.append("| XOR-splits | ").append(metrics.xorSplitCount()).append(" |\n");
        sb.append("| OR-splits | ").append(metrics.orSplitCount()).append(" |\n");
        sb.append("| AND-joins | ").append(metrics.andJoinCount()).append(" |\n");
        sb.append("| XOR-joins | ").append(metrics.xorJoinCount()).append(" |\n");
        sb.append("| OR-joins | ").append(metrics.orJoinCount()).append(" |\n");
        sb.append("| Cyclomatic complexity | ").append(metrics.cyclomaticComplexity()).append(" |\n");
        sb.append("\n");
    }

    private void appendMarkdownNet(StringBuilder sb, YNet net, boolean isRoot) {
        String heading = isRoot ? "## Root Net: " : "## Sub-Net: ";
        String netName = net.getName() != null ? net.getName() : net.getID();
        sb.append(heading).append(mdEscape(netName))
          .append(" (`").append(net.getID()).append("`)\n\n");

        if (net.getDocumentation() != null && !net.getDocumentation().isBlank()) {
            sb.append(net.getDocumentation().trim()).append("\n\n");
        }

        appendMarkdownParameters(sb, net);

        // Local variables
        Collection<YVariable> localVars = net.getLocalVariables().values();
        if (!localVars.isEmpty()) {
            sb.append("### Local Variables\n\n");
            sb.append("| Name | Type | Initial Value |\n");
            sb.append("|------|------|---------------|\n");
            for (YVariable v : localVars) {
                sb.append("| `").append(v.getName()).append("` | ")
                  .append(v.getDataTypeNameUnprefixed()).append(" | ")
                  .append(v.getInitialValue() != null ? "`" + mdEscape(v.getInitialValue()) + "`" : "-")
                  .append(" |\n");
            }
            sb.append("\n");
        }

        // Elements table
        Collection<YExternalNetElement> elements = net.getNetElements().values();
        if (!elements.isEmpty()) {
            sb.append("### Net Elements\n\n");
            sb.append("| ID | Type | Name | Join | Split |\n");
            sb.append("|----|------|------|------|-------|\n");
            for (YExternalNetElement el : elements) {
                String type  = elementTypeName(el);
                String name  = el.getName() != null ? mdEscape(el.getName()) : "-";
                String join  = (el instanceof YTask t) ? taskTypeToString(t.getJoinType()) : "-";
                String split = (el instanceof YTask t) ? taskTypeToString(t.getSplitType()) : "-";
                sb.append("| `").append(el.getID()).append("` | ").append(type)
                  .append(" | ").append(name)
                  .append(" | ").append(join)
                  .append(" | ").append(split)
                  .append(" |\n");
            }
            sb.append("\n");
        }

        // Flows
        sb.append("### Control Flows\n\n");
        sb.append("| From | To | Predicate | Default |\n");
        sb.append("|------|----|-----------|--------|\n");
        for (YExternalNetElement el : elements) {
            for (YFlow flow : el.getPostsetFlows()) {
                String predicate = flow.getXpathPredicate() != null
                        ? "`" + mdEscape(flow.getXpathPredicate()) + "`" : "-";
                String isDefault = flow.isDefaultFlow() ? "yes" : "no";
                sb.append("| `").append(el.getID()).append("` | `")
                  .append(flow.getNextElement().getID()).append("` | ")
                  .append(predicate).append(" | ").append(isDefault).append(" |\n");
            }
        }
        sb.append("\n");
    }

    private void appendMarkdownParameters(StringBuilder sb, YDecomposition decomp) {
        List<YParameter> inputParams = decomp.getInputParameters().values().stream()
                .sorted().toList();
        List<YParameter> outputParams = decomp.getOutputParameters().values().stream()
                .sorted().toList();

        if (!inputParams.isEmpty()) {
            sb.append("### Input Parameters\n\n");
            sb.append("| Name | Type | Documentation |\n");
            sb.append("|------|------|---------------|\n");
            for (YParameter p : inputParams) {
                sb.append("| `").append(p.getName()).append("` | ")
                  .append(p.getDataTypeNameUnprefixed()).append(" | ")
                  .append(p.getDocumentation() != null ? mdEscape(p.getDocumentation()) : "-")
                  .append(" |\n");
            }
            sb.append("\n");
        }

        if (!outputParams.isEmpty()) {
            sb.append("### Output Parameters\n\n");
            sb.append("| Name | Type | Documentation |\n");
            sb.append("|------|------|---------------|\n");
            for (YParameter p : outputParams) {
                sb.append("| `").append(p.getName()).append("` | ")
                  .append(p.getDataTypeNameUnprefixed()).append(" | ")
                  .append(p.getDocumentation() != null ? mdEscape(p.getDocumentation()) : "-")
                  .append(" |\n");
            }
            sb.append("\n");
        }
    }

    // ---- HTML generation -----------------------------------------------------

    /**
     * Generate HTML documentation for the specification.
     * @return complete HTML document string
     */
    public String generateHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>").append(htmlEscape(specDisplayName())).append("</title>\n");
        sb.append(htmlStyles());
        sb.append("</head>\n<body>\n");

        sb.append("<h1>").append(htmlEscape(specDisplayName())).append("</h1>\n");
        sb.append("<dl>\n");
        sb.append("  <dt>URI</dt><dd><code>").append(htmlEscape(spec.getURI())).append("</code></dd>\n");
        sb.append("  <dt>Schema version</dt><dd>").append(spec.getSchemaVersion()).append("</dd>\n");
        if (spec.getMetaData() != null) {
            sb.append("  <dt>Spec version</dt><dd>").append(spec.getMetaData().getVersion()).append("</dd>\n");
        }
        sb.append("  <dt>Generated</dt><dd>").append(LocalDate.now()).append("</dd>\n");
        sb.append("</dl>\n");

        if (spec.getDocumentation() != null && !spec.getDocumentation().isBlank()) {
            sb.append("<p>").append(htmlEscape(spec.getDocumentation().trim())).append("</p>\n");
        }

        // Metrics
        sb.append("<h2>Workflow Metrics</h2>\n");
        WorkflowMetrics metrics = computeMetrics();
        sb.append("<table><tr><th>Metric</th><th>Value</th></tr>\n");
        sb.append(htmlMetricRow("Decompositions",         metrics.decompositionCount()));
        sb.append(htmlMetricRow("Net elements (total)",   metrics.totalElements()));
        sb.append(htmlMetricRow("Atomic tasks",            metrics.atomicTaskCount()));
        sb.append(htmlMetricRow("Composite tasks",         metrics.compositeTaskCount()));
        sb.append(htmlMetricRow("Conditions",              metrics.conditionCount()));
        sb.append(htmlMetricRow("Flows (total)",           metrics.flowCount()));
        sb.append(htmlMetricRow("AND-splits",              metrics.andSplitCount()));
        sb.append(htmlMetricRow("XOR-splits",              metrics.xorSplitCount()));
        sb.append(htmlMetricRow("OR-splits",               metrics.orSplitCount()));
        sb.append(htmlMetricRow("Cyclomatic complexity",   metrics.cyclomaticComplexity()));
        sb.append("</table>\n");

        // Nets
        if (spec.getRootNet() != null) {
            appendHtmlNet(sb, spec.getRootNet(), true);
        }
        for (YDecomposition decomp : spec.getDecompositions()) {
            if (decomp instanceof YNet net && net != spec.getRootNet()) {
                appendHtmlNet(sb, net, false);
            }
        }

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private void appendHtmlNet(StringBuilder sb, YNet net, boolean isRoot) {
        String netName = net.getName() != null ? net.getName() : net.getID();
        String heading = isRoot ? "Root Net" : "Sub-Net";
        sb.append("<h2>").append(heading).append(": ").append(htmlEscape(netName))
          .append(" (<code>").append(htmlEscape(net.getID())).append("</code>)</h2>\n");

        if (net.getDocumentation() != null && !net.getDocumentation().isBlank()) {
            sb.append("<p>").append(htmlEscape(net.getDocumentation().trim())).append("</p>\n");
        }

        sb.append("<h3>Net Elements</h3>\n");
        sb.append("<table><tr><th>ID</th><th>Type</th><th>Name</th><th>Join</th><th>Split</th></tr>\n");
        for (YExternalNetElement el : net.getNetElements().values()) {
            String type  = elementTypeName(el);
            String name  = el.getName() != null ? htmlEscape(el.getName()) : "-";
            String join  = (el instanceof YTask t) ? taskTypeToString(t.getJoinType()) : "-";
            String split = (el instanceof YTask t) ? taskTypeToString(t.getSplitType()) : "-";
            sb.append("<tr><td><code>").append(htmlEscape(el.getID())).append("</code></td>")
              .append("<td>").append(type).append("</td>")
              .append("<td>").append(name).append("</td>")
              .append("<td>").append(join).append("</td>")
              .append("<td>").append(split).append("</td></tr>\n");
        }
        sb.append("</table>\n");

        sb.append("<h3>Control Flows</h3>\n");
        sb.append("<table><tr><th>From</th><th>To</th><th>Predicate</th><th>Default</th></tr>\n");
        for (YExternalNetElement el : net.getNetElements().values()) {
            for (YFlow flow : el.getPostsetFlows()) {
                String predicate = flow.getXpathPredicate() != null
                        ? "<code>" + htmlEscape(flow.getXpathPredicate()) + "</code>" : "-";
                sb.append("<tr><td><code>").append(htmlEscape(el.getID())).append("</code></td>")
                  .append("<td><code>").append(htmlEscape(flow.getNextElement().getID())).append("</code></td>")
                  .append("<td>").append(predicate).append("</td>")
                  .append("<td>").append(flow.isDefaultFlow() ? "yes" : "no").append("</td></tr>\n");
            }
        }
        sb.append("</table>\n");
    }

    private String htmlMetricRow(String metric, int value) {
        return "<tr><td>" + htmlEscape(metric) + "</td><td>" + value + "</td></tr>\n";
    }

    private String htmlStyles() {
        return """
                <style>
                  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; max-width: 1100px; margin: 0 auto; padding: 2rem; color: #333; }
                  h1 { color: #1a3a5c; border-bottom: 2px solid #1a3a5c; padding-bottom: .4rem; }
                  h2 { color: #2563a8; margin-top: 2rem; }
                  h3 { color: #375a7f; }
                  table { border-collapse: collapse; width: 100%; margin-bottom: 1.5rem; }
                  th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
                  th { background: #f0f4f8; font-weight: 600; }
                  tr:nth-child(even) { background: #f9fafb; }
                  code { background: #f0f4f8; padding: 1px 4px; border-radius: 3px; font-size: 0.9em; }
                  dl dt { font-weight: 600; margin-top: .5rem; }
                  dl dd { margin-left: 1rem; }
                </style>
                """;
    }

    // ---- Metrics computation -------------------------------------------------

    /**
     * Compute workflow complexity metrics across all nets in the specification.
     * @return {@link WorkflowMetrics} record
     */
    public WorkflowMetrics computeMetrics() {
        int decompositionCount = spec.getDecompositions().size();
        int totalElements      = 0;
        int atomicTaskCount    = 0;
        int compositeTaskCount = 0;
        int conditionCount     = 0;
        int flowCount          = 0;
        int andSplitCount = 0, xorSplitCount = 0, orSplitCount = 0;
        int andJoinCount  = 0, xorJoinCount  = 0, orJoinCount  = 0;

        for (YDecomposition decomp : spec.getDecompositions()) {
            if (!(decomp instanceof YNet net)) continue;

            for (YExternalNetElement el : net.getNetElements().values()) {
                totalElements++;
                flowCount += el.getPostsetFlows().size();

                if (el instanceof YAtomicTask t) {
                    atomicTaskCount++;
                    int splitType = t.getSplitType();
                    if (splitType == YTask._AND)       andSplitCount++;
                    else if (splitType == YTask._XOR)  xorSplitCount++;
                    else if (splitType == YTask._OR)   orSplitCount++;

                    int joinType = t.getJoinType();
                    if (joinType == YTask._AND)        andJoinCount++;
                    else if (joinType == YTask._XOR)   xorJoinCount++;
                    else if (joinType == YTask._OR)    orJoinCount++;
                } else if (el instanceof YCompositeTask) {
                    compositeTaskCount++;
                } else if (el instanceof YCondition) {
                    conditionCount++;
                }
            }
        }

        // Cyclomatic complexity: E - N + 2P (simplified for Petri nets)
        int nodeCount   = totalElements;
        int edgeCount   = flowCount;
        int components  = Math.max(1, decompositionCount);
        int cyclomatic  = edgeCount - nodeCount + (2 * components);

        return new WorkflowMetrics(
                decompositionCount, totalElements,
                atomicTaskCount, compositeTaskCount, conditionCount,
                flowCount,
                andSplitCount, xorSplitCount, orSplitCount,
                andJoinCount,  xorJoinCount,  orJoinCount,
                Math.max(1, cyclomatic)
        );
    }

    // ---- Utility methods ------------------------------------------------------

    private String specDisplayName() {
        return (spec.getName() != null && !spec.getName().isBlank()) ? spec.getName() : spec.getURI();
    }

    /**
     * Convert a YTask join/split type int constant to its lowercase string name.
     * Mirrors the private {@code decoratorTypeToString} logic in YTask.
     *
     * @param type  the int type constant (YTask._AND, YTask._OR, YTask._XOR)
     * @return lowercase string: "and", "or", "xor", or "and" as the default
     */
    private static String taskTypeToString(int type) {
        if (type == YTask._AND) return "and";
        if (type == YTask._OR)  return "or";
        if (type == YTask._XOR) return "xor";
        return "and";
    }

    private static String elementTypeName(YExternalNetElement el) {
        return switch (el) {
            case YInputCondition  ic -> "InputCondition";
            case YOutputCondition oc -> "OutputCondition";
            case YAtomicTask      at -> "AtomicTask";
            case YCompositeTask   ct -> "CompositeTask";
            case YCondition        c -> "Condition";
            default                  -> el.getClass().getSimpleName();
        };
    }

    private static String mdEscape(String s) {
        return s.replace("|", "\\|").replace("[", "\\[").replace("]", "\\]");
    }

    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    // ---- Metrics record -------------------------------------------------------

    /**
     * Immutable record capturing workflow complexity metrics.
     */
    public record WorkflowMetrics(
            int decompositionCount,
            int totalElements,
            int atomicTaskCount,
            int compositeTaskCount,
            int conditionCount,
            int flowCount,
            int andSplitCount,
            int xorSplitCount,
            int orSplitCount,
            int andJoinCount,
            int xorJoinCount,
            int orJoinCount,
            int cyclomaticComplexity
    ) { }

    // ---- Nested CLI command --------------------------------------------------

    /**
     * CLI subcommand: {@code yawl docs <spec-file> [options]}
     *
     * Generates workflow documentation in Markdown or HTML format.
     *
     * Options:
     *   --format md|html   Output format (default: md)
     *   --output <file>    Write to file instead of stdout
     *   --metrics-only     Print only the metrics table
     */
    public static final class DocsCommand extends YawlCliCommand {

        public DocsCommand(PrintStream out, PrintStream err) {
            super(out, err);
        }

        @Override
        public String name() { return "docs"; }

        @Override
        public String synopsis() { return "Generate workflow documentation (Markdown or HTML) for a specification"; }

        @Override
        public int execute(String[] args) {
            if (isHelpRequest(args)) {
                printHelp();
                return 0;
            }
            if (args.length == 0) {
                return fail("No specification file provided.");
            }

            String filePath    = null;
            String format      = "md";
            String outputPath  = null;
            boolean metricsOnly = false;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--format" -> format      = requireArg(args, ++i, "--format");
                    case "--output" -> outputPath   = requireArg(args, ++i, "--output");
                    case "--metrics-only" -> metricsOnly = true;
                    default -> {
                        if (args[i].startsWith("--")) {
                            return fail("Unknown option: " + args[i]);
                        }
                        if (filePath != null) {
                            return fail("Only one specification file may be provided.");
                        }
                        filePath = args[i];
                    }
                }
            }

            if (filePath == null) {
                return fail("No specification file provided.");
            }

            File specFile = new File(filePath);
            if (!specFile.exists() || !specFile.isFile()) {
                return fail("File not found: " + filePath);
            }

            String specXml;
            try {
                specXml = Files.readString(specFile.toPath(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return fail("Cannot read file: " + e.getMessage());
            }

            List<YSpecification> specs;
            try {
                specs = YMarshal.unmarshalSpecifications(specXml, false);
            } catch (Exception e) {
                return fail("Parse error: " + e.getMessage());
            }

            if (specs.isEmpty()) {
                return fail("No specifications found in file.");
            }

            StringBuilder combined = new StringBuilder();
            for (YSpecification spec : specs) {
                WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);

                if (metricsOnly) {
                    WorkflowMetrics m = gen.computeMetrics();
                    combined.append("Specification: ").append(spec.getURI()).append("\n");
                    combined.append("  Decompositions     : ").append(m.decompositionCount()).append("\n");
                    combined.append("  Total elements     : ").append(m.totalElements()).append("\n");
                    combined.append("  Atomic tasks       : ").append(m.atomicTaskCount()).append("\n");
                    combined.append("  Composite tasks    : ").append(m.compositeTaskCount()).append("\n");
                    combined.append("  Conditions         : ").append(m.conditionCount()).append("\n");
                    combined.append("  Flows              : ").append(m.flowCount()).append("\n");
                    combined.append("  Cyclomatic complex.: ").append(m.cyclomaticComplexity()).append("\n");
                } else {
                    combined.append(
                            "html".equalsIgnoreCase(format) ? gen.generateHtml() : gen.generateMarkdown()
                    );
                }
            }

            String content = combined.toString();

            if (outputPath != null) {
                try {
                    Files.writeString(new File(outputPath).toPath(), content, StandardCharsets.UTF_8);
                    out.println("[docs] Written to: " + outputPath);
                } catch (IOException e) {
                    return fail("Cannot write output file: " + e.getMessage());
                }
            } else {
                out.println(content);
            }

            return 0;
        }

        private String requireArg(String[] args, int index, String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException(flag + " requires a value.");
            }
            return args[index];
        }

        @Override
        protected void printHelp() {
            out.println("Usage: yawl docs <spec-file> [options]");
            out.println();
            out.println("Generate workflow documentation for a YAWL specification.");
            out.println();
            out.println("Arguments:");
            out.println("  <spec-file>          Path to the .xml or .yawl specification file");
            out.println();
            out.println("Options:");
            out.println("  --format md|html     Output format (default: md)");
            out.println("  --output <file>      Write to file instead of stdout");
            out.println("  --metrics-only       Print only the complexity metrics table");
            out.println("  -h, --help           Show this help message");
        }
    }
}
