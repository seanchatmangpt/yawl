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

package org.yawlfoundation.yawl.tooling.intellij.run;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YSpecificationValidator;
import org.yawlfoundation.yawl.schema.SchemaHandler;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.tooling.simulation.WorkflowSimulator;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.YVerificationHandler;
import org.yawlfoundation.yawl.util.YVerificationMessage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Platform-independent run configuration model for executing YAWL specifications
 * within IntelliJ IDEA.
 *
 * The IntelliJ plugin's {@code RunConfiguration} adapter stores a
 * {@link YawlRunConfiguration} instance as its persistent state and calls
 * {@link #execute()} to run the configured action. Run output is captured in a
 * {@link RunResult} and rendered in the IDE's Run tool window by the adapter.
 *
 * Supported run actions (configurable in the Run/Debug Configurations dialog):
 * <ul>
 *   <li>{@link Action#VALIDATE} — XSD + semantic validation, prints all errors/warnings</li>
 *   <li>{@link Action#SIMULATE} — Automated simulation via the stateless engine</li>
 *   <li>{@link Action#COMPILE}  — Full structural verification with decomposition report</li>
 * </ul>
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public final class YawlRunConfiguration {

    /**
     * The action performed by this run configuration.
     */
    public enum Action {
        VALIDATE,
        SIMULATE,
        COMPILE
    }

    private String specFilePath;
    private Action action;
    private int simulationMaxSteps;
    private boolean simulationTrace;
    private String simulationInitData;

    /**
     * Create a new run configuration with default settings.
     *
     * @param specFilePath absolute path to the .xml or .yawl specification file
     * @param action       the action to execute
     */
    public YawlRunConfiguration(String specFilePath, Action action) {
        if (specFilePath == null || specFilePath.isBlank()) {
            throw new IllegalArgumentException("specFilePath must not be blank");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        this.specFilePath       = specFilePath;
        this.action             = action;
        this.simulationMaxSteps = 1000;
        this.simulationTrace    = false;
        this.simulationInitData = null;
    }

    // ---- Accessors -----------------------------------------------------------

    public String getSpecFilePath()          { return specFilePath; }
    public Action getAction()                { return action; }
    public int getSimulationMaxSteps()       { return simulationMaxSteps; }
    public boolean isSimulationTrace()       { return simulationTrace; }
    public String getSimulationInitData()    { return simulationInitData; }

    public YawlRunConfiguration withSimulationMaxSteps(int steps) {
        if (steps <= 0) throw new IllegalArgumentException("simulationMaxSteps must be positive");
        this.simulationMaxSteps = steps;
        return this;
    }

    public YawlRunConfiguration withSimulationTrace(boolean trace) {
        this.simulationTrace = trace;
        return this;
    }

    public YawlRunConfiguration withSimulationInitData(String data) {
        this.simulationInitData = data;
        return this;
    }

    // ---- Execution -----------------------------------------------------------

    /**
     * Execute this run configuration and return the result.
     *
     * @return {@link RunResult} with stdout, exit code, and action name
     */
    public RunResult execute() {
        File specFile = new File(specFilePath);
        if (!specFile.exists() || !specFile.isFile()) {
            return RunResult.failure(action, "File not found: " + specFilePath, "");
        }

        String specXml;
        try {
            specXml = Files.readString(specFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return RunResult.failure(action, "Cannot read file: " + e.getMessage(), "");
        }

        return switch (action) {
            case VALIDATE -> executeValidate(specXml);
            case COMPILE  -> executeCompile(specXml);
            case SIMULATE -> executeSimulate(specXml);
        };
    }

    private RunResult executeValidate(String specXml) {
        StringBuilder output = new StringBuilder();
        output.append("YAWL Validate: ").append(specFilePath).append("\n\n");

        SchemaHandler handler = new SchemaHandler(YSchemaVersion.defaultVersion().getSchemaURL());
        boolean schemaValid   = handler.compileAndValidate(specXml);
        if (!schemaValid) {
            for (String err : handler.getErrorMessages()) {
                output.append("[XSD ERROR] ").append(err).append("\n");
            }
            output.append("\nResult: INVALID (XSD errors)\n");
            return RunResult.failure(action, output.toString(), "");
        }
        output.append("XSD validation: PASSED\n");

        List<YSpecification> specs;
        try {
            specs = YMarshal.unmarshalSpecifications(specXml, false);
        } catch (Exception e) {
            return RunResult.failure(action, output + "\nParse error: " + e.getMessage(), "");
        }

        boolean allValid = true;
        for (YSpecification spec : specs) {
            YSpecificationValidator v = new YSpecificationValidator(spec);
            boolean valid = v.validate();
            if (!valid) allValid = false;
            for (YSpecificationValidator.ValidationError ve : v.getErrors()) {
                output.append("[ERROR] ").append(ve.getMessage()).append("\n");
            }
            output.append("Spec '").append(spec.getURI()).append("': ")
                  .append(valid ? "VALID" : "INVALID").append("\n");
        }

        output.append("\nResult: ").append(allValid ? "VALID" : "INVALID").append("\n");
        return allValid ? RunResult.success(action, output.toString())
                        : RunResult.failure(action, output.toString(), "");
    }

    private RunResult executeCompile(String specXml) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos, true, StandardCharsets.UTF_8);

        List<YSpecification> specs;
        try {
            specs = YMarshal.unmarshalSpecifications(specXml, false);
        } catch (Exception e) {
            return RunResult.failure(action, "Parse error: " + e.getMessage(), "");
        }

        boolean allPassed = true;
        for (YSpecification spec : specs) {
            YVerificationHandler vh = new YVerificationHandler();
            spec.verify(vh);
            for (YVerificationMessage ve : vh.getErrors()) {
                ps.println("[ERROR] " + ve.getMessage());
                allPassed = false;
            }
            for (YVerificationMessage vw : vh.getWarnings()) {
                ps.println("[WARN]  " + vw.getMessage());
            }
            ps.println("Spec '" + spec.getURI() + "': " + (vh.getErrors().isEmpty() ? "OK" : "FAILED"));
        }

        ps.println("\nBuild " + (allPassed ? "SUCCESS" : "FAILED"));
        String output = bos.toString(StandardCharsets.UTF_8);
        return allPassed ? RunResult.success(action, output)
                         : RunResult.failure(action, output, "");
    }

    private RunResult executeSimulate(String specXml) {
        ByteArrayOutputStream outBos = new ByteArrayOutputStream();
        ByteArrayOutputStream errBos = new ByteArrayOutputStream();
        PrintStream outPs = new PrintStream(outBos, true, StandardCharsets.UTF_8);
        PrintStream errPs = new PrintStream(errBos, true, StandardCharsets.UTF_8);

        WorkflowSimulator sim = new WorkflowSimulator(outPs, errPs);
        WorkflowSimulator.SimulationResult result;
        try {
            result = sim.simulate(specXml, simulationMaxSteps, simulationTrace, simulationInitData);
        } catch (Exception e) {
            return RunResult.failure(action,
                    outBos.toString(StandardCharsets.UTF_8),
                    "Simulation error: " + e.getMessage());
        }

        String stdout = outBos.toString(StandardCharsets.UTF_8);
        String stderr = errBos.toString(StandardCharsets.UTF_8);

        return switch (result.outcome()) {
            case COMPLETED    -> RunResult.success(action, stdout);
            case MAX_EXCEEDED -> RunResult.failure(action, stdout, "Max steps exceeded");
            case ERROR        -> RunResult.failure(action, stdout, result.errorMessage());
        };
    }

    // ---- Result type ---------------------------------------------------------

    /**
     * Immutable result of a run configuration execution.
     */
    public record RunResult(
            Action action,
            boolean success,
            String stdout,
            String errorMessage
    ) {
        static RunResult success(Action action, String stdout) {
            return new RunResult(action, true, stdout, null);
        }

        static RunResult failure(Action action, String stdout, String errorMessage) {
            return new RunResult(action, false, stdout, errorMessage);
        }

        /** Exit code compatible with process exit conventions. */
        public int exitCode() {
            return success ? 0 : 1;
        }
    }
}
