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

package org.yawlfoundation.yawl.schema;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares ODCS schema contracts for a YAWL task implementation class.
 *
 * <p>When placed on a YAWL codelet class, the schema contracts will be loaded from
 * the classpath and validated at task execution boundaries. This annotation is an
 * alternative to declaring contracts as task decomposition attributes
 * ({@code schema.input} / {@code schema.output}) in the YAWL specification XML.</p>
 *
 * <p>Contract YAML files are classpath-relative paths to ODCS schemas. They are loaded
 * once at startup via {@code DataModellingService.parseOdcsYaml(String)} and cached
 * for the lifetime of the engine.</p>
 *
 * <h2>Example</h2>
 * <pre>
 * {@code @TaskSchemaContract(input = "contracts/orders-v2.yaml", output = "contracts/fulfillment-v1.yaml")}
 * public class OrderProcessingCodelet implements YCodelet { ... }
 * </pre>
 *
 * @see SchemaContractRegistry
 * @see SchemaValidationInterceptor
 * @since 6.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TaskSchemaContract {

    /**
     * Classpath-relative path to the ODCS YAML file defining the required input fields.
     * Empty string means no input contract (validation skipped for task input).
     */
    String input() default "";

    /**
     * Classpath-relative path to the ODCS YAML file defining the required output fields.
     * Empty string means no output contract (validation skipped for task output).
     */
    String output() default "";
}
