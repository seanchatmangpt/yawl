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

package org.yawlfoundation.yawl.schema;

import org.yawlfoundation.yawl.datamodelling.model.OdcsTable;
import org.yawlfoundation.yawl.datamodelling.model.WorkspaceModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compares actual task data fields against an ODCS contract ({@link WorkspaceModel}).
 *
 * <p>Validation is <em>lenient by default</em>: fields present in task data but not
 * declared in the contract do not trigger violations. Only missing required fields
 * (those declared in the contract) are reported.</p>
 *
 * <p>A contract with no tables, or tables with no columns, imposes no constraints
 * and always validates successfully.</p>
 *
 * @since 6.0.0
 */
public final class SchemaContractValidator {

    private SchemaContractValidator() {}

    /**
     * Validates actual task data fields against the ODCS contract.
     *
     * <p>For each column declared across all tables in the contract, checks that the
     * column name exists as a key in the actual data. Missing columns are reported as
     * {@link ViolationType#MISSING_FIELD} violations.</p>
     *
     * @param actual   field name → value map extracted from the task data element
     * @param contract the ODCS workspace model declaring required fields
     * @return list of violations; empty list means the data is valid against the contract
     */
    public static List<SchemaViolation> validate(Map<String, String> actual,
                                                  WorkspaceModel contract) {
        List<SchemaViolation> violations = new ArrayList<>();
        for (OdcsTable table : contract.tables()) {
            for (String column : table.columns()) {
                if (!actual.containsKey(column)) {
                    violations.add(SchemaViolation.missingField(column));
                }
            }
        }
        return violations;
    }
}
