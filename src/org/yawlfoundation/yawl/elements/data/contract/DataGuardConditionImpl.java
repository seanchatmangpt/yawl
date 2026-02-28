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

package org.yawlfoundation.yawl.elements.data.contract;

import org.jdom2.Element;
import java.util.Objects;

/**
 * Concrete implementation of DataGuardCondition.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
final class DataGuardConditionImpl implements DataGuardCondition {

    private final String name;
    private final String columnName;
    private final Predicate predicate;
    private final String failureMessage;

    DataGuardConditionImpl(Builder builder) {
        this.name = builder.name;
        this.columnName = builder.columnName;
        this.predicate = builder.predicate;
        this.failureMessage = builder.failureMessage;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getColumnName() {
        return columnName;
    }

    @Override
    public boolean evaluate(Element data) {
        Objects.requireNonNull(data, "data required");
        try {
            return predicate.test(data);
        } catch (Exception e) {
            // Guard evaluation failed - treat as violation
            return false;
        }
    }

    @Override
    public String getFailureMessage() {
        return failureMessage;
    }

    @Override
    public String toString() {
        return "DataGuardCondition{" +
            "name='" + name + '\'' +
            ", columnName='" + columnName + '\'' +
            ", failureMessage='" + failureMessage + '\'' +
            '}';
    }
}
