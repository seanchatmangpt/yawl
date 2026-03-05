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

package org.yawlfoundation.yawl.compliance.shacl;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.YNetRunner;

import java.util.List;

/**
 * SHACL Validator interface for YAWL compliance checking.
 *
 * <p>This interface provides methods to validate YAWL specifications and
 * workflow engines against compliance requirements defined in SHACL shapes.
 * It supports SOX, GDPR, and HIPAA compliance domains.</p>
 *
 * <p>Performance guarantee: Validations complete in less than 100ms per
 * specification or engine instance.</p>
 */
public interface ShaclValidator {

    /**
     * Validates a YAWL specification against compliance requirements.
     *
     * <p>Validates the specified YAWL workflow specification against the
     * compliance requirements for the given domain.</p>
     *
     * @param spec The YAWL specification to validate
     * @param complianceDomain The compliance domain to validate against
     * @return ValidationResult containing validation results
     */
    ShaclValidationResult validate(YSpecification spec, ComplianceDomain complianceDomain);

    /**
     * Validates a YAWL workflow engine against compliance requirements.
     *
     * <p>Validates the specified YAWL workflow engine instance against
     * the compliance requirements for the given domain.</p>
     *
     * @param runner The YNetRunner instance to validate
     * @param complianceDomain The compliance domain to validate against
     * @return ValidationResult containing validation results
     */
    ShaclValidationResult validate(YNetRunner runner, ComplianceDomain complianceDomain);

    /**
     * Validates a YAWL specification against multiple compliance domains.
     *
     * <p>Validates the specified YAWL workflow specification against
     * multiple compliance domains in a single operation.</p>
     *
     * @param spec The YAWL specification to validate
     * @param domains List of compliance domains to validate against
     * @return List of ValidationResult for each domain
     */
    List<ShaclValidationResult> validate(YSpecification spec, List<ComplianceDomain> domains);

    /**
     * Validates a YAWL workflow engine against multiple compliance domains.
     *
     * <p>Validates the specified YAWL workflow engine instance against
     * multiple compliance domains in a single operation.</p>
     *
     * @param runner The YNetRunner instance to validate
     * @param domains List of compliance domains to validate against
     * @return List of ValidationResult for each domain
     */
    List<ShaclValidationResult> validate(YNetRunner runner, List<ComplianceDomain> domains);

    /**
     * Validates all supported compliance domains for a specification.
     *
     * @param spec The YAWL specification to validate
     * @return List of ValidationResult for all supported domains
     */
    List<ShaclValidationResult> validateAll(YSpecification spec);

    /**
     * Validates all supported compliance domains for a workflow engine.
     *
     * @param runner The YNetRunner instance to validate
     * @return List of ValidationResult for all supported domains
     */
    List<ShaclValidationResult> validateAll(YNetRunner runner);

    /**
     * Gets the supported compliance domains for specification validation.
     *
     * @return Array of supported compliance domains
     */
    ComplianceDomain[] getSupportedSpecificationDomains();

    /**
     * Gets the supported compliance domains for engine validation.
     *
     * @return Array of supported compliance domains
     */
    ComplianceDomain[] getSupportedEngineDomains();

    /**
     * Checks if a compliance domain is supported for specification validation.
     *
     * @param domain The compliance domain to check
     * @return true if supported, false otherwise
     */
    boolean supportsSpecificationDomain(ComplianceDomain domain);

    /**
     * Checks if a compliance domain is supported for engine validation.
     *
     * @param domain The compliance domain to check
     * @return true if supported, false otherwise
     */
    boolean supportsEngineDomain(ComplianceDomain domain);

    /**
     * Gets the validation performance metrics.
     *
     * @return Map of performance metrics
     */
    java.util.Map<String, Object> getPerformanceMetrics();

    /**
     * Resets the performance metrics.
     */
    void resetPerformanceMetrics();
}