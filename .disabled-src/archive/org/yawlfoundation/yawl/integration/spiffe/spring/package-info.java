/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

/**
 * Spring Framework integration for SPIFFE/SPIRE identity.
 *
 * <p>This package provides Spring bean definitions for SPIFFE components,
 * enabling dependency injection of workload identity credentials in
 * Spring-based deployments.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.spiffe.spring.SpiffeConfiguration} -
 *       Configuration class for SPIFFE Spring beans</li>
 * </ul>
 *
 * <p>Note: YAWL does not currently use Spring Framework by default. This package
 * provides integration templates for Spring-based deployments. For non-Spring
 * applications, use direct instantiation of SPIFFE components.</p>
 */
package org.yawlfoundation.yawl.integration.spiffe.spring;
