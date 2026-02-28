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

/**
 * A data guard condition that must be satisfied before a task executes.
 *
 * <p><strong>Van der Aalst's Principle</strong>: Tasks should have preconditions on their
 * data. For example, a task that calculates shipping cost might require that the
 * order amount is present and positive.
 *
 * <p>Example:
 * <pre>{@code
 * DataGuardCondition guard = DataGuardCondition.builder()
 *     .name("order_amount_valid")
 *     .columnName("orders.total_amount")
 *     .condition(data -> {
 *         // Parse total_amount from data Element
 *         String amountStr = data.getChild("total_amount").getText();
 *         double amount = Double.parseDouble(amountStr);
 *         return amount > 0;
 *     })
 *     .failureMessage("Order amount must be positive")
 *     .build();
 *
 * if (!guard.evaluate(caseData)) {
 *     throw new DataGuardViolationException(guard.getFailureMessage());
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public interface DataGuardCondition {

    /**
     * Gets the name of this guard.
     * @return guard name (e.g., "order_amount_valid")
     */
    String getName();

    /**
     * Gets the ODCS column(s) this guard constrains.
     * @return column identifier (table.column or column name)
     */
    String getColumnName();

    /**
     * Evaluates this guard against the given data.
     *
     * @param data the case data Element containing variables
     * @return true if guard condition is satisfied, false otherwise
     */
    boolean evaluate(Element data);

    /**
     * Gets the failure message to display if guard is violated.
     * @return failure message
     */
    String getFailureMessage();

    /**
     * Creates a builder for constructing DataGuardCondition instances.
     * @return builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DataGuardCondition instances.
     */
    class Builder {
        private String name;
        private String columnName;
        private Predicate predicate;
        private String failureMessage;

        public Builder name(String name) {
            this.name = java.util.Objects.requireNonNull(name, "name required");
            return this;
        }

        public Builder columnName(String columnName) {
            this.columnName = java.util.Objects.requireNonNull(columnName, "columnName required");
            return this;
        }

        public Builder condition(Predicate predicate) {
            this.predicate = java.util.Objects.requireNonNull(predicate, "predicate required");
            return this;
        }

        public Builder failureMessage(String failureMessage) {
            this.failureMessage = java.util.Objects.requireNonNull(failureMessage, "failureMessage required");
            return this;
        }

        public DataGuardCondition build() {
            java.util.Objects.requireNonNull(name, "name required");
            java.util.Objects.requireNonNull(columnName, "columnName required");
            java.util.Objects.requireNonNull(predicate, "condition required");
            java.util.Objects.requireNonNull(failureMessage, "failureMessage required");
            return new DataGuardConditionImpl(this);
        }
    }

    /**
     * Functional interface for guard predicate evaluation.
     */
    @FunctionalInterface
    interface Predicate {
        /**
         * Evaluates the guard predicate.
         * @param data the case data Element
         * @return true if condition is satisfied
         */
        boolean test(Element data);
    }
}
