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

package org.yawlfoundation.yawl.integration.oauth2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Role-Based Access Control (RBAC) enforcer for YAWL workflow API operations.
 *
 * <p>Maps authenticated {@link OidcUserContext} principals to permitted
 * {@link WorkflowOperation} values. An operation is permitted when the user
 * context holds at least one scope listed in the operation's required-scope set.
 * The {@code yawl:admin} scope implies permission for every operation.
 *
 * <h2>Operation-to-Scope Mapping</h2>
 * <pre>
 * LAUNCH_CASE           -> yawl:operator, yawl:admin
 * CANCEL_CASE           -> yawl:operator, yawl:admin
 * GET_CASE_STATUS       -> yawl:monitor, yawl:operator, yawl:designer, yawl:agent, yawl:admin
 * LIST_CASES            -> yawl:monitor, yawl:operator, yawl:designer, yawl:agent, yawl:admin
 * CHECKOUT_WORKITEM     -> yawl:operator, yawl:agent, yawl:admin
 * CHECKIN_WORKITEM      -> yawl:operator, yawl:agent, yawl:admin
 * COMPLETE_WORKITEM     -> yawl:operator, yawl:agent, yawl:admin
 * GET_WORKITEM_DATA     -> yawl:monitor, yawl:operator, yawl:agent, yawl:admin
 * LOAD_SPECIFICATION    -> yawl:designer, yawl:admin
 * UNLOAD_SPECIFICATION  -> yawl:designer, yawl:admin
 * LIST_SPECIFICATIONS   -> yawl:monitor, yawl:operator, yawl:designer, yawl:agent, yawl:admin
 * MANAGE_PARTICIPANTS   -> yawl:designer, yawl:admin
 * ACCESS_AUDIT_LOG      -> yawl:monitor, yawl:designer, yawl:admin
 * ACCESS_MCP_TOOLS      -> yawl:agent, yawl:admin
 * ACCESS_A2A_PROTOCOL   -> yawl:agent, yawl:admin
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class RbacAuthorizationEnforcer {

    private static final Logger log = LoggerFactory.getLogger(RbacAuthorizationEnforcer.class);

    /**
     * YAWL workflow API operations subject to RBAC enforcement.
     */
    public enum WorkflowOperation {
        LAUNCH_CASE,
        CANCEL_CASE,
        GET_CASE_STATUS,
        LIST_CASES,
        CHECKOUT_WORKITEM,
        CHECKIN_WORKITEM,
        COMPLETE_WORKITEM,
        GET_WORKITEM_DATA,
        LOAD_SPECIFICATION,
        UNLOAD_SPECIFICATION,
        LIST_SPECIFICATIONS,
        MANAGE_PARTICIPANTS,
        ACCESS_AUDIT_LOG,
        ACCESS_MCP_TOOLS,
        ACCESS_A2A_PROTOCOL
    }

    // Static permission table: operation -> set of scopes that permit it
    private static final Map<WorkflowOperation, Set<String>> PERMISSION_TABLE;

    static {
        Map<WorkflowOperation, Set<String>> table = new EnumMap<>(WorkflowOperation.class);

        table.put(WorkflowOperation.LAUNCH_CASE,
                  scopeSet(YawlOAuth2Scopes.OPERATOR, YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.CANCEL_CASE,
                  scopeSet(YawlOAuth2Scopes.OPERATOR, YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.GET_CASE_STATUS,
                  scopeSet(YawlOAuth2Scopes.MONITOR, YawlOAuth2Scopes.OPERATOR,
                           YawlOAuth2Scopes.DESIGNER, YawlOAuth2Scopes.AGENT,
                           YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.LIST_CASES,
                  scopeSet(YawlOAuth2Scopes.MONITOR, YawlOAuth2Scopes.OPERATOR,
                           YawlOAuth2Scopes.DESIGNER, YawlOAuth2Scopes.AGENT,
                           YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.CHECKOUT_WORKITEM,
                  scopeSet(YawlOAuth2Scopes.OPERATOR, YawlOAuth2Scopes.AGENT,
                           YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.CHECKIN_WORKITEM,
                  scopeSet(YawlOAuth2Scopes.OPERATOR, YawlOAuth2Scopes.AGENT,
                           YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.COMPLETE_WORKITEM,
                  scopeSet(YawlOAuth2Scopes.OPERATOR, YawlOAuth2Scopes.AGENT,
                           YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.GET_WORKITEM_DATA,
                  scopeSet(YawlOAuth2Scopes.MONITOR, YawlOAuth2Scopes.OPERATOR,
                           YawlOAuth2Scopes.AGENT, YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.LOAD_SPECIFICATION,
                  scopeSet(YawlOAuth2Scopes.DESIGNER, YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.UNLOAD_SPECIFICATION,
                  scopeSet(YawlOAuth2Scopes.DESIGNER, YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.LIST_SPECIFICATIONS,
                  scopeSet(YawlOAuth2Scopes.MONITOR, YawlOAuth2Scopes.OPERATOR,
                           YawlOAuth2Scopes.DESIGNER, YawlOAuth2Scopes.AGENT,
                           YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.MANAGE_PARTICIPANTS,
                  scopeSet(YawlOAuth2Scopes.DESIGNER, YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.ACCESS_AUDIT_LOG,
                  scopeSet(YawlOAuth2Scopes.MONITOR, YawlOAuth2Scopes.DESIGNER,
                           YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.ACCESS_MCP_TOOLS,
                  scopeSet(YawlOAuth2Scopes.AGENT, YawlOAuth2Scopes.ADMIN));

        table.put(WorkflowOperation.ACCESS_A2A_PROTOCOL,
                  scopeSet(YawlOAuth2Scopes.AGENT, YawlOAuth2Scopes.ADMIN));

        PERMISSION_TABLE = Collections.unmodifiableMap(table);
    }

    private RbacAuthorizationEnforcer() {
        throw new UnsupportedOperationException(
                "RbacAuthorizationEnforcer is a utility class and cannot be instantiated.");
    }

    /**
     * Assert that the given user context is authorized to perform the specified operation.
     *
     * @param context   validated OIDC user context (must not be null)
     * @param operation the workflow operation being attempted (must not be null)
     * @throws RbacAccessDeniedException if the user lacks the required scope
     * @throws IllegalArgumentException  if context or operation is null
     */
    public static void assertPermitted(OidcUserContext context, WorkflowOperation operation)
            throws RbacAccessDeniedException {
        if (context == null) {
            throw new IllegalArgumentException("OidcUserContext must not be null");
        }
        if (operation == null) {
            throw new IllegalArgumentException("WorkflowOperation must not be null");
        }

        if (!isPermitted(context, operation)) {
            String msg = String.format(
                    "Access denied: subject='%s' lacks required scope for operation '%s'. "
                    + "Token scopes: %s. Required one of: %s",
                    context.getSubject(), operation,
                    context.getScopes(), requiredScopes(operation));
            log.warn("RBAC denial: {}", msg);
            throw new RbacAccessDeniedException(msg, context.getSubject(), operation);
        }

        log.debug("RBAC permit: subject='{}' operation={}", context.getSubject(), operation);
    }

    /**
     * Returns whether the given user context is authorized for the operation.
     *
     * @param context   validated OIDC user context
     * @param operation the workflow operation being checked
     * @return true if authorized
     */
    public static boolean isPermitted(OidcUserContext context, WorkflowOperation operation) {
        if (context == null || operation == null) {
            return false;
        }
        // Admin scope implies everything
        if (context.getScopes().contains(YawlOAuth2Scopes.ADMIN)) {
            return true;
        }
        Set<String> required = PERMISSION_TABLE.get(operation);
        if (required == null) {
            log.error("No permission mapping for operation '{}' - denying by default", operation);
            return false;
        }
        for (String scope : required) {
            if (context.getScopes().contains(scope)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the set of scopes that permit the given operation.
     *
     * @param operation workflow operation
     * @return unmodifiable set of scope strings that permit the operation
     * @throws IllegalArgumentException if operation is null or unknown
     */
    public static Set<String> requiredScopes(WorkflowOperation operation) {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        Set<String> scopes = PERMISSION_TABLE.get(operation);
        if (scopes == null) {
            throw new IllegalArgumentException("No permission mapping for operation: " + operation);
        }
        return scopes;
    }

    private static Set<String> scopeSet(String... scopes) {
        Set<String> set = new LinkedHashSet<>(Arrays.asList(scopes));
        return Collections.unmodifiableSet(set);
    }

    /**
     * Thrown when an authenticated principal lacks the required RBAC scope.
     * Maps to HTTP 403 Forbidden.
     */
    public static final class RbacAccessDeniedException extends Exception {

        private final String            subject;
        private final WorkflowOperation operation;

        /**
         * Construct with details.
         *
         * @param message   human-readable description for logs
         * @param subject   JWT subject of the denied principal
         * @param operation the denied operation
         */
        public RbacAccessDeniedException(String message, String subject,
                                         WorkflowOperation operation) {
            super(message);
            this.subject   = subject;
            this.operation = operation;
        }

        /** Returns the JWT subject of the denied principal. */
        public String getSubject() { return subject; }

        /** Returns the operation that was denied. */
        public WorkflowOperation getOperation() { return operation; }
    }
}
