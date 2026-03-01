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

import org.jdom2.Document;
import org.yawlfoundation.yawl.engine.YWorkItem;

/**
 * SPI for synchronous schema validation at YAWL task execution boundaries.
 *
 * <p>Implementations are registered with {@link org.yawlfoundation.yawl.engine.YNetRunner}
 * and invoked synchronously before task announcement and after task completion.
 * Throwing {@link RuntimeException} from either method will abort the respective
 * task lifecycle transition.</p>
 *
 * <p>Thread safety: implementations must be thread-safe; a single interceptor instance
 * may be called concurrently from multiple virtual threads processing parallel task firings.</p>
 *
 * @see org.yawlfoundation.yawl.engine.YNetRunner#registerSchemaInterceptor(TaskSchemaInterceptor)
 * @since 6.0.0
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface TaskSchemaInterceptor {

    /**
     * Called synchronously before a task's announcement to the work list handler.
     * The work item already has its input data element and decomposition attributes set.
     *
     * <p>Access input data: {@link YWorkItem#getDataElement()}
     * Access contract path: {@code item.getAttributes().get("schema.input")}</p>
     *
     * @param item the work item being enabled (status: Enabled)
     * @throws RuntimeException to abort the task enablement (e.g., TaskSchemaViolationException)
     */
    void beforeTaskExecution(YWorkItem item);

    /**
     * Called synchronously after task completion, before the net progresses.
     * The work item's attributes are still available for contract path lookup.
     *
     * <p>Access contract path: {@code item.getAttributes().get("schema.output")}</p>
     *
     * @param item       the completing work item
     * @param outputData the data document returned by the task implementation
     * @throws RuntimeException to abort the task completion (e.g., TaskSchemaViolationException)
     */
    void afterTaskCompletion(YWorkItem item, Document outputData);
}
