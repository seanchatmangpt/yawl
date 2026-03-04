/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.dspy.module;

import java.util.List;
import java.util.function.Function;

/**
 * A tool that can be used by ReAct modules.
 *
 * <p>Tools are functions that ReAct modules can call during reasoning.
 * Each tool has:
 * <ul>
 *   <li>A name for the LLM to reference</li>
 *   <li>A description explaining what it does</li>
 *   <li>An executor that takes arguments and returns a result</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface Tool {

    /**
     * Get the tool name (used in Action: name[args] format).
     */
    String name();

    /**
     * Get a description of what this tool does.
     */
    String description();

    /**
     * Execute the tool with the given arguments.
     *
     * @param args the arguments passed from the LLM
     * @return the observation string to feed back to the LLM
     */
    String execute(List<String> args);

    /**
     * Create a tool from a function.
     */
    static Tool function(String name, String description, Function<List<String>, String> executor) {
        return new Tool() {
            @Override
            public String name() { return name; }

            @Override
            public String description() { return description; }

            @Override
            public String execute(List<String> args) {
                return executor.apply(args);
            }
        };
    }

    /**
     * Create a tool that queries a YAWL case.
     */
    static Tool yawlCaseQuery(String name, String description,
                              java.util.function.BiFunction<String, String, String> queryExecutor) {
        return function(name, description, args -> {
            if (args.isEmpty()) {
                return "Error: case ID required";
            }
            String caseId = args.getFirst();
            String query = args.size() > 1 ? args.get(1) : "status";
            return queryExecutor.apply(caseId, query);
        });
    }
}
