package org.yawlfoundation.yawl.resourcing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Synchronizes YAWL participants with LDAP/Active Directory.
 * Uses Spring LDAP for proper integration instead of throwing UnsupportedOperationException.
 *
 * @since YAWL 6.0
 */
public class LdapParticipantSync {
    private static final Logger LOGGER = LogManager.getLogger(LdapParticipantSync.class);
    private final String ldapUrl, baseDn, bindDn, bindPassword;
    private final LdapTemplate ldapTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    public LdapParticipantSync() {
        this.ldapUrl = getEnv("YAWL_LDAP_URL");
        this.baseDn = getEnv("YAWL_LDAP_BASE_DN");
        this.bindDn = getEnv("YAWL_LDAP_BIND_DN");
        this.bindPassword = getEnv("YAWL_LDAP_BIND_PASSWORD");

        // Initialize Spring LDAP template
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl(ldapUrl);
        contextSource.setBase(baseDn);
        contextSource.setUserDn(bindDn);
        contextSource.setPassword(bindPassword);
        contextSource.setPooled(true);

        this.ldapTemplate = new LdapTemplate(contextSource);

        // Test connection on startup
        try {
            ldapTemplate.authenticate("", "(objectClass=*)", bindPassword);
            LOGGER.info("Successfully connected to LDAP server: {}", ldapUrl);
        } catch (Exception e) {
            LOGGER.error("Failed to connect to LDAP server: {}", e.getMessage(), e);
            throw new IllegalStateException("LDAP connection failed", e);
        }
    }

    @Transactional
    public LdapSyncResult sync() throws LdapSyncException {
        LOGGER.info("Starting LDAP synchronization from {}", baseDn);
        LdapSyncResult result = new LdapSyncResult();

        try {
            // Find all user objects in LDAP
            List<LdapUser> ldapUsers = ldapTemplate.search(
                "",
                "(objectClass=user)",
                (ctx, controls) -> {
                    // Extract user attributes
                    String username = ctx.getStringAttribute("sAMAccountName");
                    String fullName = ctx.getStringAttribute("displayName");
                    String email = ctx.getStringAttribute("mail");
                    String department = ctx.getStringAttribute("department");
                    String title = ctx.getStringAttribute("title");

                    return new LdapUser(username, fullName, email, department, title);
                }
            );

            // Get existing YAWL participants
            List<Participant> existingParticipants = entityManager.createQuery(
                "SELECT p FROM Participant p", Participant.class).getResultList();

            // Sync participants
            for (LdapUser ldapUser : ldapUsers) {
                Participant existing = existingParticipants.stream()
                    .filter(p -> p.getUserId().equals(ldapUser.getUsername()))
                    .findFirst()
                    .orElse(null);

                if (existing == null) {
                    // Create new participant
                    Participant newParticipant = createParticipantFromLdapUser(ldapUser);
                    entityManager.persist(newParticipant);
                    result.created++;
                    LOGGER.info("Created new participant: {}", ldapUser.getUsername());
                } else {
                    // Update existing participant
                    updateParticipantFromLdapUser(existing, ldapUser);
                    result.updated++;
                    LOGGER.info("Updated participant: {}", ldapUser.getUsername());
                }
            }

            // Deactivate participants not in LDAP
            int deactivated = existingParticipants.stream()
                .filter(p -> !ldapUsers.stream().anyMatch(u -> u.getUsername().equals(p.getUserId())))
                .mapToInt(p -> {
                    p.setActive(false);
                    p.setDeactivatedTime(Instant.now());
                    return 1;
                })
                .sum();

            result.deactivated = deactivated;
            LOGGER.info("Synchronization complete. Created: {}, Updated: {}, Deactivated: {}",
                result.created, result.updated, result.deactivated);

            return result;
        } catch (Exception e) {
            LOGGER.error("LDAP synchronization failed: {}", e.getMessage(), e);
            throw new LdapSyncException("Failed to synchronize LDAP participants: " + e.getMessage(), e);
        }
    }

    private static String getEnv(String name) {
        String value = System.getenv(name);
        if (value == null) throw new IllegalStateException("Required env var: " + name);
        return value;
    }

    private Participant createParticipantFromLdapUser(LdapUser ldapUser) {
        Participant participant = new Participant();
        participant.setUserId(ldapUser.getUsername());
        participant.setUserName(ldapUser.getFullName());
        participant.setEmail(ldapUser.getEmail());
        participant.setDepartment(ldapUser.getDepartment());
        participant.setTitle(ldapUser.getTitle());
        participant.setActive(true);
        participant.setCreatedTime(Instant.now());
        participant.setLastUpdated(Instant.now());
        return participant;
    }

    private void updateParticipantFromLdapUser(Particle existing, LdapUser ldapUser) {
        existing.setUserName(ldapUser.getFullName());
        existing.setEmail(ldapUser.getEmail());
        existing.setDepartment(ldapUser.getDepartment());
        existing.setTitle(ldapUser.getTitle());
        existing.setActive(true);
        existing.setLastUpdated(Instant.now());
    }

    private static class LdapUser {
        private final String username;
        private final String fullName;
        private final String email;
        private final String department;
        private final String title;

        public LdapUser(String username, String fullName, String email, String department, String title) {
            this.username = username;
            this.fullName = fullName;
            this.email = email;
            this.department = department;
            this.title = title;
        }

        public String getUsername() { return username; }
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
        public String getDepartment() { return department; }
        public String getTitle() { return title; }
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
