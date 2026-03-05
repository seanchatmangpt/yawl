/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.conflict;

/**
 * Exception thrown when conflict resolution fails.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ConflictResolutionException extends Exception {
    private final ConflictResolver.Strategy strategy;
    private final String conflictId;

    public ConflictResolutionException(String message, ConflictResolver.Strategy strategy, String conflictId) {
        super(message);
        this.strategy = strategy;
        this.conflictId = conflictId;
    }

    public ConflictResolutionException(String message, Throwable cause,
                                    ConflictResolver.Strategy strategy, String conflictId) {
        super(message, cause);
        this.strategy = strategy;
        this.conflictId = conflictId;
    }

    public ConflictResolver.Strategy getStrategy() {
        return strategy;
    }

    public String getConflictId() {
        return conflictId;
    }

    @Override
    public String toString() {
        return String.format("ConflictResolutionException{strategy=%s, conflictId='%s', message='%s'}",
                           strategy, conflictId, getMessage());
    }
}