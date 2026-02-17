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

package org.yawlfoundation.yawl.tooling.cli.command;

import org.yawlfoundation.yawl.tooling.cli.YawlCliCommand;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CLI subcommand: {@code yawl template <template-name> [--output <file>] [--uri <uri>]}
 *
 * Generates YAWL specification XML from built-in templates conforming to
 * YAWL_Schema4.0.xsd.
 *
 * Built-in templates:
 *   sequential    - Simple A → B → C linear workflow
 *   parallel      - AND-split / AND-join parallel paths
 *   choice        - XOR-split / XOR-join exclusive choice
 *   multiinstance - Multi-instance task with static creation mode
 *   subprocess    - Parent net referencing a sub-process decomposition
 *
 * Options:
 *   --output <file>   Write to file instead of stdout
 *   --uri <uri>       Override the default specification URI
 *   --name <name>     Override the default specification name
 *   --list            List available templates
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public class TemplateCommand extends YawlCliCommand {

    /** Canonical namespace from YAWL_Schema4.0.xsd */
    private static final String NS = "http://www.yawlfoundation.org/yawlschema";
    private static final String SCHEMA_VERSION = "4.0";
    private static final String SCHEMA_LOC = NS + " " + NS + "/YAWL_Schema4.0.xsd";

    /** All built-in templates keyed by name */
    private final Map<String, TemplateDefinition> templates = buildTemplateRegistry();

    public TemplateCommand(PrintStream out, PrintStream err) {
        super(out, err);
    }

    @Override
    public String name() { return "template"; }

    @Override
    public String synopsis() { return "Generate a new specification from a named template"; }

    @Override
    public int execute(String[] args) {
        if (isHelpRequest(args)) {
            printHelp();
            return 0;
        }

        String templateName = null;
        String outputPath   = null;
        String specUri      = null;
        String specName     = null;
        boolean listMode    = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--output" -> outputPath = requireArg(args, ++i, "--output");
                case "--uri"    -> specUri     = requireArg(args, ++i, "--uri");
                case "--name"   -> specName    = requireArg(args, ++i, "--name");
                case "--list"   -> listMode    = true;
                default -> {
                    if (args[i].startsWith("--")) {
                        return fail("Unknown option: " + args[i]);
                    }
                    if (templateName != null) {
                        return fail("Only one template name may be provided.");
                    }
                    templateName = args[i];
                }
            }
        }

        if (listMode) {
            out.println("Available templates:");
            for (Map.Entry<String, TemplateDefinition> e : templates.entrySet()) {
                out.printf("  %-18s  %s%n", e.getKey(), e.getValue().description());
            }
            return 0;
        }

        if (templateName == null) {
            return fail("No template name provided. Use --list to see available templates.");
        }

        TemplateDefinition tmpl = templates.get(templateName.toLowerCase());
        if (tmpl == null) {
            return fail("Unknown template '" + templateName + "'. Use --list to see available templates.");
        }

        String uri  = (specUri  != null) ? specUri  : templateName + "Workflow";
        String name = (specName != null) ? specName : tmpl.defaultName();
        String xml  = buildSpecificationXml(uri, name, tmpl);

        if (outputPath != null) {
            File outFile = new File(outputPath);
            try {
                Files.writeString(outFile.toPath(), xml, StandardCharsets.UTF_8);
                out.println("[template] Wrote specification to: " + outFile.getAbsolutePath());
            } catch (IOException e) {
                return fail("Cannot write output file: " + e.getMessage());
            }
        } else {
            out.println(xml);
        }

        return 0;
    }

    // ---- XML generation -------------------------------------------------------

    private String buildSpecificationXml(String uri, String name, TemplateDefinition tmpl) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!--
                  Generated by YAWL CLI v6.0.0 on %s
                  Template: %s
                -->
                <specificationSet version="%s"
                    xmlns="%s"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="%s">
                  <specification uri="%s">
                    <name>%s</name>
                    <documentation>%s</documentation>
                    <metaData>
                      <creator>YAWL CLI template generator</creator>
                      <version>0.1</version>
                    </metaData>
                %s
                  </specification>
                </specificationSet>
                """.formatted(
                LocalDate.now(),
                tmpl.description(),
                SCHEMA_VERSION, NS, SCHEMA_LOC,
                xmlEscape(uri),
                xmlEscape(name),
                xmlEscape(tmpl.description()),
                tmpl.decompositionXml()
        );
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // ---- Template registry ----------------------------------------------------

    private Map<String, TemplateDefinition> buildTemplateRegistry() {
        Map<String, TemplateDefinition> reg = new LinkedHashMap<>();

        reg.put("sequential", new TemplateDefinition(
                "Sequential Workflow",
                "Linear sequence of tasks: A then B then C",
                sequentialDecomposition()
        ));

        reg.put("parallel", new TemplateDefinition(
                "Parallel Workflow",
                "AND-split feeds two parallel tasks, AND-join merges them",
                parallelDecomposition()
        ));

        reg.put("choice", new TemplateDefinition(
                "Choice Workflow",
                "XOR-split selects one of two paths, XOR-join merges them",
                choiceDecomposition()
        ));

        reg.put("multiinstance", new TemplateDefinition(
                "Multi-Instance Workflow",
                "Multi-instance task with static creation mode",
                multiInstanceDecomposition()
        ));

        reg.put("subprocess", new TemplateDefinition(
                "Subprocess Workflow",
                "Parent net with a composite task referencing a sub-net",
                subprocessDecomposition()
        ));

        return reg;
    }

    // ---- Decomposition XML bodies ---------------------------------------------

    private String sequentialDecomposition() {
        return """
                    <decomposition id="SequentialNet" xsi:type="NetFactsType" isRootNet="true">
                      <name>Sequential Net</name>
                      <processControlElements>
                        <inputCondition id="start">
                          <name>Start</name>
                          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
                        </inputCondition>
                        <task id="TaskA">
                          <name>Task A</name>
                          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
                          <join code="xor"/><split code="and"/>
                        </task>
                        <task id="TaskB">
                          <name>Task B</name>
                          <flowsInto><nextElementRef id="TaskC"/></flowsInto>
                          <join code="xor"/><split code="and"/>
                        </task>
                        <task id="TaskC">
                          <name>Task C</name>
                          <flowsInto><nextElementRef id="end"/></flowsInto>
                          <join code="xor"/><split code="and"/>
                        </task>
                        <outputCondition id="end"><name>End</name></outputCondition>
                      </processControlElements>
                    </decomposition>
                """;
    }

    private String parallelDecomposition() {
        return """
                    <decomposition id="ParallelNet" xsi:type="NetFactsType" isRootNet="true">
                      <name>Parallel Net</name>
                      <processControlElements>
                        <inputCondition id="start">
                          <name>Start</name>
                          <flowsInto><nextElementRef id="Split"/></flowsInto>
                        </inputCondition>
                        <task id="Split">
                          <name>AND Split</name>
                          <flowsInto><nextElementRef id="BranchA"/></flowsInto>
                          <flowsInto><nextElementRef id="BranchB"/></flowsInto>
                          <join code="xor"/><split code="and"/>
                        </task>
                        <task id="BranchA">
                          <name>Branch A</name>
                          <flowsInto><nextElementRef id="Join"/></flowsInto>
                          <join code="and"/><split code="and"/>
                        </task>
                        <task id="BranchB">
                          <name>Branch B</name>
                          <flowsInto><nextElementRef id="Join"/></flowsInto>
                          <join code="and"/><split code="and"/>
                        </task>
                        <task id="Join">
                          <name>AND Join</name>
                          <flowsInto><nextElementRef id="end"/></flowsInto>
                          <join code="and"/><split code="and"/>
                        </task>
                        <outputCondition id="end"><name>End</name></outputCondition>
                      </processControlElements>
                    </decomposition>
                """;
    }

    private String choiceDecomposition() {
        return """
                    <decomposition id="ChoiceNet" xsi:type="NetFactsType" isRootNet="true">
                      <name>Choice Net</name>
                      <processControlElements>
                        <inputCondition id="start">
                          <name>Start</name>
                          <flowsInto><nextElementRef id="Decision"/></flowsInto>
                        </inputCondition>
                        <task id="Decision">
                          <name>XOR Decision</name>
                          <flowsInto>
                            <nextElementRef id="PathA"/>
                            <predicate ordering="0">/data/route = 'A'</predicate>
                          </flowsInto>
                          <flowsInto>
                            <nextElementRef id="PathB"/>
                            <isDefaultFlow/>
                          </flowsInto>
                          <join code="xor"/><split code="xor"/>
                        </task>
                        <task id="PathA">
                          <name>Path A</name>
                          <flowsInto><nextElementRef id="Merge"/></flowsInto>
                          <join code="xor"/><split code="and"/>
                        </task>
                        <task id="PathB">
                          <name>Path B</name>
                          <flowsInto><nextElementRef id="Merge"/></flowsInto>
                          <join code="xor"/><split code="and"/>
                        </task>
                        <task id="Merge">
                          <name>XOR Merge</name>
                          <flowsInto><nextElementRef id="end"/></flowsInto>
                          <join code="xor"/><split code="and"/>
                        </task>
                        <outputCondition id="end"><name>End</name></outputCondition>
                      </processControlElements>
                    </decomposition>
                """;
    }

    private String multiInstanceDecomposition() {
        return """
                    <decomposition id="MultiInstanceNet" xsi:type="NetFactsType" isRootNet="true">
                      <name>Multi-Instance Net</name>
                      <processControlElements>
                        <inputCondition id="start">
                          <name>Start</name>
                          <flowsInto><nextElementRef id="MITask"/></flowsInto>
                        </inputCondition>
                        <task id="MITask">
                          <name>Multi-Instance Task</name>
                          <flowsInto><nextElementRef id="end"/></flowsInto>
                          <join code="xor"/><split code="and"/>
                          <multiInstance>
                            <minimum>1</minimum>
                            <maximum>5</maximum>
                            <threshold>3</threshold>
                            <creationMode code="static"/>
                          </multiInstance>
                        </task>
                        <outputCondition id="end"><name>End</name></outputCondition>
                      </processControlElements>
                    </decomposition>
                """;
    }

    private String subprocessDecomposition() {
        return """
                    <decomposition id="ParentNet" xsi:type="NetFactsType" isRootNet="true">
                      <name>Parent Net</name>
                      <processControlElements>
                        <inputCondition id="start">
                          <name>Start</name>
                          <flowsInto><nextElementRef id="SubTask"/></flowsInto>
                        </inputCondition>
                        <task id="SubTask">
                          <name>Sub-Process</name>
                          <flowsInto><nextElementRef id="end"/></flowsInto>
                          <join code="xor"/><split code="and"/>
                          <decomposesTo id="SubNet"/>
                        </task>
                        <outputCondition id="end"><name>End</name></outputCondition>
                      </processControlElements>
                    </decomposition>
                    <decomposition id="SubNet" xsi:type="NetFactsType" isRootNet="false">
                      <name>Sub Net</name>
                      <processControlElements>
                        <inputCondition id="subStart">
                          <name>Sub Start</name>
                          <flowsInto><nextElementRef id="SubTaskA"/></flowsInto>
                        </inputCondition>
                        <task id="SubTaskA">
                          <name>Sub Task A</name>
                          <flowsInto><nextElementRef id="subEnd"/></flowsInto>
                          <join code="xor"/><split code="and"/>
                        </task>
                        <outputCondition id="subEnd"><name>Sub End</name></outputCondition>
                      </processControlElements>
                    </decomposition>
                """;
    }

    private String requireArg(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Option " + flag + " requires a value.");
        }
        return args[index];
    }

    @Override
    protected void printHelp() {
        out.println("Usage: yawl template <template-name> [options]");
        out.println();
        out.println("Generate a new YAWL specification from a built-in template.");
        out.println();
        out.println("Arguments:");
        out.println("  <template-name>   Name of the template (use --list to see all)");
        out.println();
        out.println("Options:");
        out.println("  --output <file>   Write output to file (default: stdout)");
        out.println("  --uri <uri>       Specification URI (default: <templateName>Workflow)");
        out.println("  --name <name>     Specification display name");
        out.println("  --list            List all available templates");
        out.println("  -h, --help        Show this help message");
    }

    // ---- Inner type -----------------------------------------------------------

    /**
     * Immutable definition of a built-in specification template.
     */
    private record TemplateDefinition(
            String defaultName,
            String description,
            String decompositionXml
    ) { }
}
