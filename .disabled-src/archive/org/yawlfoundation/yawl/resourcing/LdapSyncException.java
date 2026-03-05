package org.yawlfoundation.yawl.resourcing;

/**
 * Thrown when LDAP synchronization fails.
 * @since YAWL 6.0
 */
public class LdapSyncException extends Exception {
    public LdapSyncException(String message) { super(message); }
    public LdapSyncException(String message, Throwable cause) { super(message, cause); }
}
