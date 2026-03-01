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
package org.yawlfoundation.yawl.erlang.schema;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares schema contracts for a workflow task's input and/or output data.
 *
 * <p>Tasks annotated with {@code @TaskSchemaContract} have their input JSON
 * validated by {@link SchemaValidationInterceptor} before execution. If the data
 * does not conform to the declared contract, a
 * {@link TaskSchemaViolationException} is thrown and a
 * {@link org.yawlfoundation.yawl.erlang.workflow.TaskSchemaViolation} event is
 * published to the {@link org.yawlfoundation.yawl.erlang.workflow.WorkflowEventBus}.</p>
 *
 * <p>Schema files are YAML files loaded from the classpath at startup by
 * {@link SchemaContractRegistry}. The format is a simplified subset of ODCS
 * (Open Data Contract Standard):
 *
 * <pre>
 *   name: OrderInput
 *   version: "2.0"
 *   properties:
 *     orderId:
 *       type: integer
 *       required: true
 *     amount:
 *       type: number
 *       required: true
 *     customerId:
 *       type: string
 *       required: false
 * </pre>
 *
 * <p>Example task annotation:
 * <pre>
 *   &#64;TaskSchemaContract(
 *       input  = "contracts/validate-order-input-v2.yaml",
 *       output = "contracts/validate-order-result-v1.yaml"
 *   )
 *   public class ValidateOrderTask { ... }
 * </pre>
 *
 * <p>Schema migration: use {@code inputFallback} to accept two versions during
 * gradual migration of upstream services:
 * <pre>
 *   &#64;TaskSchemaContract(
 *       input         = "contracts/order-input-v3.yaml",
 *       inputFallback = "contracts/order-input-v2.yaml"
 *   )
 *   public class RouteOrderTask { ... }
 * </pre>
 *
 * @see SchemaContractRegistry
 * @see SchemaValidationInterceptor
 * @see TaskSchemaViolationException
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TaskSchemaContract {

    /**
     * Classpath path to the YAML schema for the task's input data.
     *
     * <p>Leave empty if the task has no input contract to enforce.</p>
     */
    String input() default "";

    /**
     * Classpath path to the YAML schema for the task's output data.
     *
     * <p>Leave empty if the task has no output contract to enforce.</p>
     */
    String output() default "";

    /**
     * Optional fallback schema for input data, accepted during schema migration.
     *
     * <p>When set, the interceptor accepts input conforming to either
     * {@link #input()} or {@code inputFallback}. A
     * {@link org.yawlfoundation.yawl.erlang.workflow.TaskSchemaViolation} event
     * is published when the fallback (rather than the primary) schema matches,
     * enabling migration progress monitoring.</p>
     *
     * <p>Leave empty if no migration is in progress.</p>
     */
    String inputFallback() default "";
}
