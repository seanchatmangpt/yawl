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
package org.yawlfoundation.yawl.erlang.hotreload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a YAWL task class as dispatching its execution logic to an Erlang module.
 *
 * <p>Tasks annotated with {@code @ErlangTaskModule} call the specified Erlang
 * module and function via the {@link org.yawlfoundation.yawl.erlang.processmining.ErlangBridge}
 * instead of executing business logic in Java. The Erlang function receives the
 * task's input data as an ETF term and returns the output data as an ETF term.</p>
 *
 * <p>Business rules implemented in Erlang modules can be updated at runtime via
 * {@link HotReloadService#loadModule(String, byte[])} without restarting the JVM
 * or interrupting in-flight workflow cases — enabling zero-downtime rule evolution.</p>
 *
 * <p>Example:
 * <pre>
 *   &#64;ErlangTaskModule(module = "yawl_order_routing", function = "route")
 *   public class RouteOrderTask {
 *       private final ErlangBridge bridge;
 *
 *       public String execute(String inputJson) {
 *           var input  = ErlTerm.fromJson(inputJson);  // not yet in API — illustrative
 *           var result = bridge.rpcAsJson("yawl_order_routing", "route", input);
 *           return result;
 *       }
 *   }
 * </pre>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ErlangTaskModule {

    /**
     * The Erlang module atom name that implements the task's business logic.
     *
     * <p>Must match the module name used in {@link HotReloadService#loadModule}
     * calls (e.g., {@code "yawl_order_routing"}).</p>
     */
    String module();

    /**
     * The exported Erlang function to call within the module.
     *
     * <p>The function will be called with the task's input data as a single
     * argument (the arity is 1).</p>
     */
    String function();
}
