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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Concrete implementation of DataGuardEvaluator.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class DataGuardEvaluatorImpl implements DataGuardEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DataGuardEvaluatorImpl.class);

    @Override
    public DataGuardEvaluationResult evaluateGuards(YTask task, Element caseData, ODCSDataContract contract) {
        DataGuardEvaluationResult result = new DataGuardEvaluationResult(task.getName());

        List<DataGuardCondition> guards = contract.getDataGuards();

        if (guards.isEmpty()) {
            log.debug("No guards to evaluate for task {}", task.getName());
            return result;
        }

        for (DataGuardCondition guard : guards) {
            if (evaluateGuard(guard, caseData)) {
                result.recordPass(guard.getName());
                log.debug("Guard '{}' PASSED for task {}", guard.getName(), task.getName());
            } else {
                result.recordFail(guard.getName());
                log.warn("Guard '{}' FAILED for task {}: {}",
                    guard.getName(), task.getName(), guard.getFailureMessage());
            }
        }

        return result;
    }

    @Override
    public boolean evaluateGuard(DataGuardCondition condition, Element caseData) {
        java.util.Objects.requireNonNull(condition, "condition required");
        java.util.Objects.requireNonNull(caseData, "caseData required");

        return condition.evaluate(caseData);
    }
}
