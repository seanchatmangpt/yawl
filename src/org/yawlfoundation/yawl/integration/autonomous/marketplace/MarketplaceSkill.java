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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import java.util.List;
import java.util.Objects;

/**
 * An A2A skill descriptor derived from the marketplace schema graph.
 *
 * <p>Corresponds to one marketplace operation exposed as an A2A protocol skill.
 * Each instance is converted to an {@code io.a2a.spec.AgentSkill} when building
 * the marketplace agent card.</p>
 *
 * @param id          unique A2A skill identifier (kebab-case)
 * @param name        human-readable skill name
 * @param description human-readable description of what the skill does
 * @param tags        classification tags (e.g. {@code ["marketplace", "agents"]})
 * @since YAWL 6.0
 */
public record MarketplaceSkill(
        String id,
        String name,
        String description,
        List<String> tags) {

    public MarketplaceSkill {
        Objects.requireNonNull(id,          "id must not be null");
        Objects.requireNonNull(name,        "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(tags,        "tags must not be null");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        tags = List.copyOf(tags);
    }
}
