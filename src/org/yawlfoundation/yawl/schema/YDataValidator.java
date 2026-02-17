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

import org.jdom2.Element;
import org.yawlfoundation.yawl.elements.data.YVariable;
import org.yawlfoundation.yawl.engine.core.data.YCoreDataValidator;
import org.yawlfoundation.yawl.exceptions.YDataValidationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Stateful-engine thin wrapper around {@link YCoreDataValidator}.
 *
 * <p>This class was refactored as part of Phase 1 engine deduplication
 * (EngineDedupPlan P1.4).  All validation logic now lives in
 * {@link YCoreDataValidator}; this wrapper adds only the stateful-tree-specific
 * typed overloads that accept {@code org.yawlfoundation.yawl.elements.data.YVariable}.
 * All other methods ({@code validateSchema()}, {@code getSchema()}, etc.) are
 * inherited directly.</p>
 *
 * <p>The public API is unchanged: existing callers that pass {@code YVariable}
 * or {@code Collection} arguments continue to work without modification.</p>
 *
 * @author Mike Fowler (original)
 * @author YAWL Foundation (Phase 1 deduplication, 2026)
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class YDataValidator extends YCoreDataValidator {

    /**
     * Constructs a new validator and handler.
     * @param schema a W3C XML Schema
     */
    public YDataValidator(String schema) {
        super(schema);
    }

    /**
     * Validates a single stateful data variable.
     *
     * @param variable to be validated
     * @param data XML representation of variable to be validated
     * @param source
     * @throws YDataValidationException if the data is not valid
     */
    public void validate(YVariable variable, Element data, String source)
            throws YDataValidationException {
        List<YVariable> vars = new ArrayList<>(1);
        vars.add(variable);
        validate((Collection) vars, data, source);
    }

}
