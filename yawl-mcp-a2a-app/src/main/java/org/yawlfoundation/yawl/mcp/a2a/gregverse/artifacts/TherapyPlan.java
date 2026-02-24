/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.artifacts;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * Therapy Plan artifact for GregVerse OT services.
 *
 * <p>This artifact represents a comprehensive occupational therapy plan that includes
 * assessment findings, intervention strategies, measurable goals, and progress tracking
 * methodology. It is created by the OT ServiceProviderAgent and published to the
 * marketplace.</p>
 *
 * <h2>Plan Components</h2>
 * <ul>
 *   <li>Assessment summary and findings</li>
 *   <li>Intervention strategies and activities</li>
 *   <li>Measurable short-term and long-term goals</li>
 *   <li>Progress tracking methodology</li>
 *   <li>Timeline and frequency of sessions</li>
 *   <li>Equipment and resource needs</li>
 *   <li>Collaborative care coordination</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class TherapyPlan implements Artifact {

    private String id;
    private String serviceId;
    private String clientId;
    private String serviceType;
    private String tier;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String status;
    private Map<String, Object> metadata;

    public TherapyPlan() {
        this.metadata = new HashMap<>();
        this.status = "active";
    }

    // Getters and setters
    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // Builder pattern for easy construction
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for TherapyPlan.
     */
    public static class Builder {
        private TherapyPlan plan;

        public Builder() {
            plan = new TherapyPlan();
        }

        public Builder id(String id) {
            plan.id = id;
            return this;
        }

        public Builder serviceId(String serviceId) {
            plan.serviceId = serviceId;
            return this;
        }

        public Builder clientId(String clientId) {
            plan.clientId = clientId;
            return this;
        }

        public Builder serviceType(String serviceType) {
            plan.serviceType = serviceType;
            return this;
        }

        public Builder tier(String tier) {
            plan.tier = tier;
            return this;
        }

        public Builder content(String content) {
            plan.content = content;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            plan.createdAt = createdAt;
            return this;
        }

        public Builder completedAt(LocalDateTime completedAt) {
            plan.completedAt = completedAt;
            return this;
        }

        public Builder status(String status) {
            plan.status = status;
            return this;
        }

        public Builder metadata(String key, Object value) {
            plan.metadata.put(key, value);
            return this;
        }

        public TherapyPlan build() {
            if (plan.id == null) {
                plan.id = java.util.UUID.randomUUID().toString();
            }
            if (plan.createdAt == null) {
                plan.createdAt = LocalDateTime.now();
            }
            return plan;
        }
    }

    /**
     * Generate a summary of the therapy plan.
     */
    public String generateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Therapy Plan Summary\n");
        summary.append("====================\n");
        summary.append("Service ID: ").append(serviceId).append("\n");
        summary.append("Client ID: ").append(clientId).append("\n");
        summary.append("Service Type: ").append(serviceType).append("\n");
        summary.append("Tier: ").append(tier).append("\n");
        summary.append("Status: ").append(status).append("\n");

        if (content != null && !content.isEmpty()) {
            // Extract first few lines of content as preview
            String[] lines = content.split("\n");
            summary.append("Content Preview:\n");
            for (int i = 0; i < Math.min(3, lines.length); i++) {
                summary.append("- ").append(lines[i]).append("\n");
            }
            if (lines.length > 3) {
                summary.append("  ... (truncated)\n");
            }
        }

        return summary.toString();
    }

    /**
     * Check if the plan is completed.
     */
    public boolean isCompleted() {
        return "completed".equals(status) && completedAt != null;
    }

    /**
     * Get the plan duration in days.
     */
    public long getDurationInDays() {
        if (createdAt == null || completedAt == null) {
            return 0;
        }
        return java.time.Duration.between(createdAt, completedAt).toDays();
    }

    /**
     * Add assessment findings to the plan.
     */
    public void addAssessmentFindings(String findings) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put("assessmentFindings", findings);
    }

    /**
     * Add intervention goals to the plan.
     */
    public void addInterventionGoals(String goals) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put("interventionGoals", goals);
    }

    /**
     * Add progress tracking methodology.
     */
    public void addProgressTracking(String methodology) {
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        metadata.put("progressTracking", methodology);
    }

    @Override
    public String toString() {
        return "TherapyPlan{" +
                "id='" + id + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", clientId='" + clientId + '\'' +
                ", serviceType='" + serviceType + '\'' +
                ", tier='" + tier + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                ", completedAt=" + completedAt +
                '}';
    }
}