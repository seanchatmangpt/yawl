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

package org.yawlfoundation.yawl.integration.processmining;

import java.util.Map;

/**
 * Stub implementation of XesToYawlSpecGenerator.
 * This class is required by WorkflowDNAOracle but is only used as a dependency
 * for alternative path generation functionality which is not yet implemented.
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class XesToYawlSpecGenerator {

    /**
     * Constructs a new XesToYawlSpecGenerator.
     *
     * @param version version parameter (currently ignored)
     */
    public XesToYawlSpecGenerator(int version) {
        // Stub implementation
    }

    /**
     * Generates a YAWL specification from XES data.
     * This method is not implemented and always throws UnsupportedOperationException
     * since the WorkflowDNAOracle gracefully handles failures.
     *
     * @param xesXml XES XML data
     * @param specName name for the generated specification
     * @return YAWL specification XML (not implemented)
     * @throws UnsupportedOperationException always - method not implemented
     */
    public String generate(String xesXml, String specName) {
        throw new UnsupportedOperationException(
            "XesToYawlSpecGenerator.generate() is not implemented. " +
            "This is a stub implementation required for compilation. " +
            "The full implementation exists in the yawl-integration module."
        );
    }

    /**
     * Additional methods that might be called by WorkflowDNAOracle.
     * All implementations throw UnsupportedOperationException.
     */
    public Map<String, Object> extractMetadata(String xesXml) {
        throw new UnsupportedOperationException(
            "XesToYawlSpecGenerator.extractMetadata() is not implemented"
        );
    }

    public boolean validateXes(String xesXml) {
        throw new UnsupportedOperationException(
            "XesToYawlSpecGenerator.validateXes() is not implemented"
        );
    }

    public String getSpecificationFormat() {
        throw new UnsupportedOperationException(
            "XesToYawlSpecGenerator.getSpecificationFormat() is not implemented"
        );
    }
}