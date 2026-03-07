/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.ggen.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.erlang.processmining.ProcessMining;
import org.yawlfoundation.yawl.ggen.model.YawlSpec;
import org.yawlfoundation.yawl.ggen.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-layer YAWL specification validator.
 *
 * <p>Validates YAWL specifications through three layers:
 * <ol>
 *   <li><b>XSD Schema Validation</b>: Checks XML conformance to YAWL XSD</li>
 *   <li><b>rust4pm Soundness</b>: Detects deadlocks and lack of synchronization</li>
 *   <li><b>Virtual Execution</b>: Simulates token flow through YNetRunner</li>
 * </ol>
 *
 * <h2>Integration with JOR4J:</h2>
 * <pre>{@code
 * // Uses existing ProcessMining fluent API from DSPy thesis
 * try (ProcessMining pm = ProcessMining.connect("yawl_erl@localhost", "secret")) {
 *     YawlValidator validator = new YawlValidator(pm);
 *
 *     YawlSpec spec = generator.generate(nl);
 *     ValidationResult result = validator.validate(spec);
 *
 *     if (result.valid()) {
 *         YEngine.getInstance().loadSpecification(spec.yawlXml());
 *     } else {
 *         System.err.println(result.getSummary());
 *     }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class YawlValidator {

    private static final Logger log = LoggerFactory.getLogger(YawlValidator.class);

    private final ProcessMining processMining;
    private final XsdValidator xsdValidator;
    private final Rust4PmValidator soundnessValidator;

    /**
     * Create a new YawlValidator.
     *
     * @param processMining Connected ProcessMining instance for rust4pm validation
     */
    public YawlValidator(ProcessMining processMining) {
        this.processMining = processMining;
        this.xsdValidator = new XsdValidator();
        this.soundnessValidator = new Rust4PmValidator(processMining);
    }

    /**
     * Validate YAWL specification through all layers.
     *
     * @param spec YawlSpec to validate
     * @return ValidationResult with detailed feedback
     */
    public ValidationResult validate(YawlSpec spec) {
        log.info("Starting multi-layer validation for: {}", spec.specId());
        long startTime = System.currentTimeMillis();

        // Layer 1: XSD Schema Validation
        log.debug("Layer 1: XSD schema validation");
        XsdValidator.XsdValidationResult xsdResult = xsdValidator.validate(spec.yawlXml());
        if (!xsdResult.valid()) {
            log.warn("XSD validation failed: {} errors", xsdResult.errors().size());
            return ValidationResult.xsdFailure(xsdResult.errors());
        }
        log.debug("XSD validation passed");

        // Layer 2: rust4pm Soundness Check
        log.debug("Layer 2: rust4pm soundness validation");
        Rust4PmValidator.SoundnessResult soundnessResult = soundnessValidator.validate(spec);
        if (!soundnessResult.isSound()) {
            log.warn("Soundness check failed: {} deadlocks, {} lack of sync",
                soundnessResult.deadlocks().size(),
                soundnessResult.lackOfSync().size());
            return ValidationResult.soundnessFailure(
                soundnessResult.deadlocks(),
                soundnessResult.lackOfSync()
            );
        }
        log.debug("Soundness check passed");

        // Layer 3: Virtual Execution
        log.debug("Layer 3: Virtual execution simulation");
        VirtualExecutor.ExecutionResult execResult = VirtualExecutor.simulate(spec);
        if (!execResult.success()) {
            log.warn("Virtual execution failed: {}", execResult.errors().size());
            return ValidationResult.executionFailure(execResult.errors());
        }
        log.debug("Virtual execution passed");

        long validationTime = System.currentTimeMillis() - startTime;
        log.info("Validation complete in {}ms", validationTime);

        return ValidationResult.success();
    }

    /**
     * Quick XSD-only validation.
     */
    public boolean isValidXml(String yawlXml) {
        return xsdValidator.validate(yawlXml).valid();
    }

    /**
     * Quick soundness check using rust4pm.
     */
    public Rust4PmValidator.SoundnessResult checkSoundness(YawlSpec spec) {
        return soundnessValidator.validate(spec);
    }
}
