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

/**
 * Enumeration of compliance domains supported by SHACL validation.
 *
 * <p>Each compliance domain represents a specific regulatory framework
 * that YAWL workflows must comply with:</p>
 *
 * <ul>
 *   <li><b>SOX</b> - Sarbanes-Oxley Act for financial audit trails</li>
 *   <li><b>GDPR</b> - General Data Protection Regulation for personal data</li>
 *   <li><b>HIPAA</b> - Health Insurance Portability and Accountability Act</li>
 * </ul>
 */
public enum ComplianceDomain {
    /**
     * Sarbanes-Oxley compliance for financial workflows.
     * Ensures proper audit trails, financial controls, and accountability.
     */
    SOX("SOX", "Sarbanes-Oxley Act", "Financial audit and controls compliance"),

    /**
     * GDPR compliance for workflows handling personal data.
     * Ensures data protection, privacy, and consent management.
     */
    GDPR("GDPR", "General Data Protection Regulation", "Personal data protection compliance"),

    /**
     * HIPAA compliance for healthcare workflows.
     * Ensures patient privacy and healthcare data security.
     */
    HIPAA("HIPAA", "Health Insurance Portability and Accountability Act", "Healthcare data privacy compliance");

    private final String code;
    private final String name;
    private final String description;

    ComplianceDomain(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    /**
     * Gets the compliance domain code.
     */
    public String getCode() {
        return code;
    }

    /**
     * Gets the compliance domain name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the compliance domain description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the compliance domain from code.
     */
    public static ComplianceDomain fromCode(String code) {
        for (ComplianceDomain domain : values()) {
            if (domain.code.equals(code)) {
                return domain;
            }
        }
        throw new IllegalArgumentException("Unknown compliance domain: " + code);
    }

    /**
     * Gets all compliance domains that support specifications.
     */
    public static ComplianceDomain[] getSpecificationDomains() {
        return new ComplianceDomain[]{SOX, GDPR};
    }

    /**
     * Gets all compliance domains that support engine validation.
     */
    public static ComplianceDomain[] getEngineDomains() {
        return new ComplianceDomain[]{SOX, HIPAA};
    }

    /**
     * Gets the SHACL shape file for this domain.
     */
    public String getShapeFile() {
        return "yawl-compliance-" + code.toLowerCase() + "-shapes.ttl";
    }
}