package org.yawlfoundation.yawl.resourcing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.time.Instant;

/**
 * Synchronizes YAWL participants with LDAP/Active Directory.
 * @since YAWL 6.0
 */
public class LdapParticipantSync {
    private static final Logger LOGGER = LogManager.getLogger(LdapParticipantSync.class);
    private final String ldapUrl, baseDn, bindDn, bindPassword;

    public LdapParticipantSync() {
        this.ldapUrl = getEnv("YAWL_LDAP_URL");
        this.baseDn = getEnv("YAWL_LDAP_BASE_DN");
        this.bindDn = getEnv("YAWL_LDAP_BIND_DN");
        this.bindPassword = getEnv("YAWL_LDAP_BIND_PASSWORD");
    }

    public LdapSyncResult sync() throws LdapSyncException {
        throw new UnsupportedOperationException(
            "sync requires UnboundID LDAP SDK. Add: com.unboundid:unboundid-ldapsdk:6.0.x");
    }

    private static String getEnv(String name) {
        String value = System.getenv(name);
        if (value == null) throw new IllegalStateException("Required env var: " + name);
        return value;
    }

    public static class LdapSyncResult {
        private final Instant startTime = Instant.now();
        private Instant completionTime;
        private int created, updated, deactivated;

        public Instant getStartTime() { return startTime; }
        public Instant getCompletionTime() { return completionTime; }
        public int getCreatedCount() { return created; }
        public int getUpdatedCount() { return updated; }
        public int getDeactivatedCount() { return deactivated; }
    }
}
