/**
 * Copyright 2004-2026 YAWL Foundation
 *
 * This package provides execution sandbox capabilities for YAWL workflow instances with
 * container-based isolation and comprehensive security features.
 *
 * <p>The sandbox package leverages Java 25 features including:
 * <ul>
 *   <li><strong>Virtual Threads</strong>: For lightweight concurrent management of isolated
 *       YAWL case executions</li>
 *   <li><strong>Foreign Function & Memory API</strong>: Secure interaction with Docker engine</li>
 *   <li><strong>Sealed Classes for Security Policies</strong>: Enforced access control rules</li>
 * </ul>
 *
 * <p>Key components:
 * <ul>
 *   <li>Docker container isolation for workflow execution</li>
 *   <li>Resource quota enforcement and monitoring</li>
 *   <li>Network access control and security policies</li>
 *   <li>File system isolation with read-only base images</li>
 *   <li>Secure logging and audit trail maintenance</li>
 * </ul>
 *
 * <p>Security features:
 * <ul>
 *   <li>Capability-based security model</li>
 *   <li>Mandatory access control</li>
 *   <li>Execution timeout enforcement</li>
 *   <li>Memory and CPU quota management</li>
 * </ul>
 *
 * <p>Since: 6.0.0-GA
 */
package org.yawlfoundation.yawl.ggen.sandbox;