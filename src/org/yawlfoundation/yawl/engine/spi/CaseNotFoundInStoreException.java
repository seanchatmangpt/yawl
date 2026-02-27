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

package org.yawlfoundation.yawl.engine.spi;

import org.yawlfoundation.yawl.elements.state.YIdentifier;

/**
 * Thrown by {@link RunnerEvictionStore#restore} when no snapshot exists for a case.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class CaseNotFoundInStoreException extends RuntimeException {

    public CaseNotFoundInStoreException(YIdentifier caseId) {
        super("No runner snapshot found in store for case: " + caseId);
    }

    public CaseNotFoundInStoreException(String caseId) {
        super("No runner snapshot found in store for case: " + caseId);
    }
}
