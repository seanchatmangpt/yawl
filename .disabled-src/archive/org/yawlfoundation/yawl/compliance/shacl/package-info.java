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

/**
 * SHACL Compliance Module for YAWL
 *
 * <p>This package provides SHACL (Shapes Constraint Language) validation
 * for YAWL workflow specifications and engines against compliance requirements
 * for SOX, GDPR, and HIPAA regulations.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Validation of YAWL specifications against compliance shapes</li>
 *   <li>Support for SOX (Sarbanes-Oxley Act) financial audit requirements</li>
 *   <li>Support for GDPR (General Data Protection Regulation) privacy requirements</li>
 *   <li>Support for HIPAA (Health Insurance Portability and Accountability Act) healthcare requirements</li>
 *   <li>Performance-validated under 100ms per validation</li>
 *   <li>Caching mechanisms for improved performance</li>
 *   <li>Detailed violation reporting with severity levels</li>
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Create a SHACL validator
 * ShaclValidator validator = new ShaclValidatorImpl();
 *
 * // Validate a specification against SOX compliance
 * ShaclValidationResult result = validator.validate(specification, ComplianceDomain.SOX);
 *
 * // Check if validation passed
 * if (result.valid()) {
 *     System.out.println("SOX compliance passed");
 * } else {
 *     System.out.println("SOX compliance violations found:");
 *     for (ShaclViolation violation : result.violations()) {
 *         System.out.println("- " + violation);
 *     }
 * }
 *
 * // Validate against multiple domains
 * List<ShaclValidationResult> results = validator.validateAll(specification);
 * }</pre>
 *
 * <h2>Supported Compliance Domains</h2>
 * <ul>
 *   <li><b>SOX</b> - Financial audit trails, internal controls, data integrity</li>
 *   <li><b>GDPR</b> - Personal data protection, consent management, data subject rights</li>
 *   <li><b>HIPAA</b> - Healthcare data privacy, security safeguards, breach notification</li>
 * </ul>
 *
 * <h2>SHACL Shapes</h2>
 * <p>The compliance shapes are defined in TTL (Turtle) format in the schema/shacl directory:</p>
 * <ul>
 *   <li>yawl-compliance-sox-shapes.ttl - SOX compliance rules</li>
 *   <li>yawl-compliance-gdpr-shapes.ttl - GDPR compliance rules</li>
 *   <li>yawl-compliance-hipaa-shapes.ttl - HIPAA compliance rules</li>
 * </ul>
 *
 * @since 6.0
 * @version 1.0
 */