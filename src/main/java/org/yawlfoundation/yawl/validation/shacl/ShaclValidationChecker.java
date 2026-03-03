/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.validation.shacl;

import org.yawlfoundation.yawl.validation.GuardChecker;
import org.yawlfoundation.yawl.validation.GuardViolation;
import org.yawlfoundation.yawl.validation.GuardReceipt;

import java.nio.file.Path;
import java.util.List;

/**
 * SHACL validation checker that implements the GuardChecker interface.
 *
 * Integrates SHACL validation into the HyperStandards validation pipeline.
 */
public class ShaclValidationChecker implements GuardChecker {

    private final YAWLShaclValidator shaclValidator;

    public ShaclValidationChecker() {
        this.shaclValidator = new YAWLShaclValidator();
    }

    @Override
    public List<GuardViolation> check(Path specificationPath) {
        GuardReceipt receipt = shaclValidator.validateSpecifications(
            specificationPath.getParent() != null ? specificationPath.getParent() : specificationPath
        );

        return receipt.getViolations();
    }

    @Override
    public String patternName() {
        return "SHACL_VALIDATION";
    }

    @Override
    public String severity() {
        return "FAIL";
    }

    /**
     * Validates a specific specification file.
     *
     * @param specificationPath Path to the YAWL specification file
     * @return List of guard violations
     */
    public List<GuardViolation> validateSpecification(Path specificationPath) {
        return shaclValidator.validateSpecifications(
            specificationPath.getParent() != null ? specificationPath.getParent() : specificationPath
        ).getViolations();
    }
}