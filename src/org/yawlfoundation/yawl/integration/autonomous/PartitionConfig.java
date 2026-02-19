/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import java.util.Objects;

/**
 * Configuration for agent partitioning strategy.
 *
 * <p>Enables horizontal scaling by distributing work items across multiple
 * agents using consistent hashing. Each agent processes only work items
 * assigned to its partition based on the formula: (hash % totalAgents) == agentIndex.</p>
 *
 * <h2>Partition Strategy</h2>
 * <ul>
 * <li>Even Distribution: Hash-based assignment ensures balanced workload</li>
 * <li>Consistency: Same work item always assigned to same partition</li>
 * <li>Scalability: Adding/removing agents redistributes work automatically</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>
 * PartitionConfig partitionConfig = PartitionConfig.builder()
 *     .agentIndex(0)
 *     .totalAgents(4)
 *     .build();
 *
 * AgentConfiguration config = AgentConfiguration.builder()
 *     .capability(capability)
 *     .engineUrl("http://localhost:8080/yawl")
 *     .partitionConfig(partitionConfig)
 *     // other configuration
 *     .build();
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see PollingDiscoveryStrategy#partitionFilter
 */
public final class PartitionConfig {

    private final int agentIndex;
    private final int totalAgents;

    private PartitionConfig(Builder builder) {
        this.agentIndex = builder.agentIndex;
        this.totalAgents = builder.totalAgents;
    }

    public int getAgentIndex() {
        return agentIndex;
    }

    public int getTotalAgents() {
        return totalAgents;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartitionConfig that = (PartitionConfig) o;
        return agentIndex == that.agentIndex && totalAgents == that.totalAgents;
    }

    @Override
    public int hashCode() {
        return Objects.hash(agentIndex, totalAgents);
    }

    @Override
    public String toString() {
        return "PartitionConfig{" +
                "agentIndex=" + agentIndex +
                ", totalAgents=" + totalAgents +
                '}';
    }

    public static final class Builder {
        private int agentIndex;
        private int totalAgents;

        private Builder() {
        }

        public Builder agentIndex(int agentIndex) {
            this.agentIndex = agentIndex;
            return this;
        }

        public Builder totalAgents(int totalAgents) {
            this.totalAgents = totalAgents;
            return this;
        }

        public PartitionConfig build() {
            if (agentIndex < 0) {
                throw new IllegalStateException("agentIndex must be non-negative");
            }
            if (totalAgents <= 0) {
                throw new IllegalStateException("totalAgents must be positive");
            }
            if (agentIndex >= totalAgents) {
                throw new IllegalStateException("agentIndex must be less than totalAgents");
            }
            return new PartitionConfig(this);
        }
    }
}