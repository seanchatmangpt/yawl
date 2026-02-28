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
import org.yawlfoundation.yawl.elements.YTask;

import java.util.List;

/**
 * Evaluates data guards (preconditions) before task execution.
 *
 * <p><strong>Van der Aalst's Principle</strong>: Tasks should have data preconditions
 * that prevent execution when data is invalid. This is analogous to place guards in
 * Petri nets, but for data state instead of just token counts.
 *
 * <p>Example:
 * <pre>{@code
 * DataGuardEvaluator evaluator = new DataGuardEvaluatorImpl();
 *
 * // Add guards to a contract
 * ODCSDataContract contract = ODCSDataContract.builder()
 *     .workflowId("order-processing")
 *     .workspaceId("sales")
 *     .addTableRead("orders", List.of("total_amount", "status"))
 *     .addTableWrite("invoices", List.of("invoice_amount"))
 *     .addDataGuard(DataGuardCondition.builder()
 *         .name("order_ready")
 *         .columnName("orders.status")
 *         .condition(data -> {
 *             String status = data.getChild("status").getText();
 *             return "READY".equals(status);
 *         })
 *         .failureMessage("Order must be in READY status")
 *         .build())
 *     .addDataGuard(DataGuardCondition.builder()
 *         .name("amount_positive")
 *         .columnName("orders.total_amount")
 *         .condition(data -> {
 *             double amount = Double.parseDouble(
 *                 data.getChild("total_amount").getText()
 *             );
 *             return amount > 0;
 *         })
 *         .failureMessage("Order amount must be positive")
 *         .build())
 *     .build();
 *
 * // Before task execution, check guards
 * YTask task = ...;
 * Element caseData = ...;
 *
 * DataGuardEvaluationResult result = evaluator.evaluateGuards(task, caseData, contract);
 *
 * if (!result.allPassed()) {
 *     // Task should not execute - preconditions not met
 *     throw new DataGuardViolationException(result.getFailedGuardNames());
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public interface DataGuardEvaluator {

    /**
     * Evaluates all guards in a contract against the given case data.
     *
     * @param task the task about to execute (for context)
     * @param caseData the current case data
     * @param contract the data contract with guards
     * @return evaluation result with pass/fail for each guard
     */
    DataGuardEvaluationResult evaluateGuards(YTask task, Element caseData, ODCSDataContract contract);

    /**
     * Evaluates a single guard condition.
     *
     * @param condition the guard to evaluate
     * @param caseData the case data
     * @return true if guard passes, false if violated
     */
    boolean evaluateGuard(DataGuardCondition condition, Element caseData);

    /**
     * Result of evaluating all guards for a task.
     */
    class DataGuardEvaluationResult {
        private final String taskName;
        private final List<String> passedGuards = new java.util.ArrayList<>();
        private final List<String> failedGuards = new java.util.ArrayList<>();

        public DataGuardEvaluationResult(String taskName) {
            this.taskName = java.util.Objects.requireNonNull(taskName, "taskName required");
        }

        public String getTaskName() {
            return taskName;
        }

        public void recordPass(String guardName) {
            passedGuards.add(guardName);
        }

        public void recordFail(String guardName) {
            failedGuards.add(guardName);
        }

        public boolean allPassed() {
            return failedGuards.isEmpty();
        }

        public List<String> getPassedGuards() {
            return java.util.Collections.unmodifiableList(passedGuards);
        }

        public List<String> getFailedGuards() {
            return java.util.Collections.unmodifiableList(failedGuards);
        }

        public int getPassCount() {
            return passedGuards.size();
        }

        public int getFailCount() {
            return failedGuards.size();
        }

        @Override
        public String toString() {
            return "DataGuardEvaluationResult{" +
                "taskName='" + taskName + '\'' +
                ", passed=" + passedGuards.size() +
                ", failed=" + failedGuards.size() +
                '}';
        }
    }
}
