package org.yawlfoundation.yawl.integration.a2a.skills;

import java.util.Map;
import java.util.Set;

/**
 * Interface for A2A Skills in the YAWL self-upgrading codebase.
 *
 * <p>Skills are the building blocks of the A2A protocol, enabling
 * agents to perform specific operations on the codebase. Each skill
 * has a unique ID, name, description, and required permissions.
 *
 * <p><b>Skill Lifecycle:</b>
 * <ol>
 *   <li>Request received with parameters</li>
 *   <li>Permissions verified against AuthenticatedPrincipal</li>
 *   <li>Skill executed with parameters</li>
 *   <li>Result returned (success or error)</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public interface A2ASkill {

    /**
     * Get the unique skill identifier.
     *
     * @return skill ID (e.g., "introspect_codebase")
     */
    String getId();

    /**
     * Get the human-readable skill name.
     *
     * @return skill name (e.g., "Introspect Codebase")
     */
    String getName();

    /**
     * Get the skill description for agent discovery.
     *
     * @return detailed description of what the skill does
     */
    String getDescription();

    /**
     * Get the permissions required to execute this skill.
     *
     * @return set of permission strings (e.g., "code:read", "code:write")
     */
    Set<String> getRequiredPermissions();

    /**
     * Execute the skill with the given request.
     *
     * @param request the skill request containing parameters
     * @return the skill result (success or error)
     */
    SkillResult execute(SkillRequest request);

    /**
     * Check if this skill can be executed given the available permissions.
     *
     * @param availablePermissions the permissions granted to the caller
     * @return true if all required permissions are satisfied
     */
    default boolean canExecute(Set<String> availablePermissions) {
        return getRequiredPermissions().stream()
            .allMatch(perm -> availablePermissions.contains("*") ||
                             availablePermissions.contains(perm));
    }
}
