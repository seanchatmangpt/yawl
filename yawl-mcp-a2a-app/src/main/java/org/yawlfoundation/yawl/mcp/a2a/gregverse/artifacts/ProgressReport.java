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
 * Progress Report artifact for GregVerse OT services.
 *
 * <p>This artifact represents a comprehensive progress report for completed occupational
 * therapy services. It includes outcomes achieved, progress summary, recommendations,
 * and next steps. It is generated after therapy plan completion and published to the
 * marketplace.</p>
 *
 * <h2>Report Components</h2>
 * <ul>
 *   <li>Service summary and duration</li>
 *   <li>Outcomes achieved and progress metrics</li>
 *   <li>Goals achievement analysis</li>
 *   <li>Intervention effectiveness review</li>
 *   <li>Client feedback and satisfaction</li>
 *   <li>Recommendations for continued care</li>
 *   <li>Next steps and follow-up planning</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ProgressReport implements Artifact {

    private String id;
    private String serviceId;
    private String clientId;
    private String content;
    private LocalDateTime generatedAt;
    private LocalDateTime nextReviewDate;
    private Map<String, Object> metrics;
    private Map<String, Object> recommendations;
    private String status;
    private Map<String, Object> metadata;

    public ProgressReport() {
        this.metrics = new HashMap<>();
        this.recommendations = new HashMap<>();
        this.metadata = new HashMap<>();
        this.status = "final";
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return generatedAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.generatedAt = createdAt;
    }

    public LocalDateTime getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(LocalDateTime nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }

    public Map<String, Object> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(Map<String, Object> recommendations) {
        this.recommendations = recommendations;
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
     * Builder for ProgressReport.
     */
    public static class Builder {
        private ProgressReport report;

        public Builder() {
            report = new ProgressReport();
        }

        public Builder id(String id) {
            report.id = id;
            return this;
        }

        public Builder serviceId(String serviceId) {
            report.serviceId = serviceId;
            return this;
        }

        public Builder clientId(String clientId) {
            report.clientId = clientId;
            return this;
        }

        public Builder content(String content) {
            report.content = content;
            return this;
        }

        public Builder generatedAt(LocalDateTime generatedAt) {
            report.generatedAt = generatedAt;
            return this;
        }

        public Builder nextReviewDate(LocalDateTime nextReviewDate) {
            report.nextReviewDate = nextReviewDate;
            return this;
        }

        public Builder metrics(String key, Object value) {
            report.metrics.put(key, value);
            return this;
        }

        public Builder recommendations(String key, Object value) {
            report.recommendations.put(key, value);
            return this;
        }

        public Builder status(String status) {
            report.status = status;
            return this;
        }

        public Builder metadata(String key, Object value) {
            report.metadata.put(key, value);
            return this;
        }

        public ProgressReport build() {
            if (report.id == null) {
                report.id = java.util.UUID.randomUUID().toString();
            }
            if (report.generatedAt == null) {
                report.generatedAt = LocalDateTime.now();
            }
            return report;
        }
    }

    /**
     * Add a progress metric to the report.
     */
    public void addMetric(String name, Object value, String unit) {
        if (metrics == null) {
            metrics = new HashMap<>();
        }
        Map<String, Object> metricData = new HashMap<>();
        metricData.put("value", value);
        metricData.put("unit", unit);
        metrics.put(name, metricData);
    }

    /**
     * Add a recommendation to the report.
     */
    public void addRecommendation(String category, String recommendation, String priority) {
        if (recommendations == null) {
            recommendations = new HashMap<>();
        }
        Map<String, Object> recData = new HashMap<>();
        recData.put("recommendation", recommendation);
        recData.put("priority", priority);
        recommendations.put(category, recData);
    }

    /**
     * Generate a summary of the progress report.
     */
    public String generateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Progress Report Summary\n");
        summary.append("======================\n");
        summary.append("Report ID: ").append(id).append("\n");
        summary.append("Service ID: ").append(serviceId).append("\n");
        summary.append("Client ID: ").append(clientId).append("\n");
        summary.append("Generated: ").append(generatedAt).append("\n");
        summary.append("Status: ").append(status).append("\n");

        if (nextReviewDate != null) {
            summary.append("Next Review: ").append(nextReviewDate).append("\n");
        }

        // Display key metrics
        if (metrics != null && !metrics.isEmpty()) {
            summary.append("\nKey Metrics:\n");
            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metricData = (Map<String, Object>) value;
                    Object metricValue = metricData.get("value");
                    Object unit = metricData.get("unit");
                    summary.append("- ").append(entry.getKey())
                           .append(": ").append(metricValue)
                           .append(unit != null ? " " + unit : "").append("\n");
                } else {
                    summary.append("- ").append(entry.getKey())
                           .append(": ").append(value).append("\n");
                }
            }
        }

        // Display recommendations
        if (recommendations != null && !recommendations.isEmpty()) {
            summary.append("\nRecommendations:\n");
            for (Map.Entry<String, Object> entry : recommendations.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> recData = (Map<String, Object>) value;
                    String recommendation = (String) recData.get("recommendation");
                    String priority = (String) recData.get("priority");
                    summary.append("- [").append(priority).append("] ")
                           .append(entry.getKey()).append(": ")
                           .append(recommendation).append("\n");
                }
            }
        }

        return summary.toString();
    }

    /**
     * Calculate overall progress score.
     */
    public double calculateProgressScore() {
        if (metrics == null || metrics.isEmpty()) {
            return 0.0;
        }

        double totalScore = 0.0;
        int metricCount = 0;

        for (Object value : metrics.values()) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metricData = (Map<String, Object>) value;
                Object metricValue = metricData.get("value");

                if (metricValue instanceof Number) {
                    totalScore += ((Number) metricValue).doubleValue();
                    metricCount++;
                }
            }
        }

        return metricCount > 0 ? totalScore / metricCount : 0.0;
    }

    /**
     * Check if the report indicates successful outcomes.
     */
    public boolean isSuccessful() {
        double progressScore = calculateProgressScore();
        return progressScore >= 0.7; // 70% threshold for success
    }

    /**
     * Get recommendations by priority.
     */
    public Map<String, Object> getRecommendationsByPriority(String priority) {
        if (recommendations == null) {
            return new HashMap<>();
        }

        Map<String, Object> filtered = new HashMap<>();
        for (Map.Entry<String, Object> entry : recommendations.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> recData = (Map<String, Object>) value;
                if (priority.equals(recData.get("priority"))) {
                    filtered.put(entry.getKey(), value);
                }
            }
        }
        return filtered;
    }

    /**
     * Set next review date based on current progress.
     */
    public void scheduleNextReview() {
        if (generatedAt == null) {
            generatedAt = LocalDateTime.now();
        }

        // Schedule next review in 30 days by default
        this.nextReviewDate = generatedAt.plusDays(30);

        // Adjust based on progress score
        double progressScore = calculateProgressScore();
        if (progressScore < 0.5) {
            // Poor progress - review sooner
            this.nextReviewDate = generatedAt.plusDays(14);
        } else if (progressScore > 0.9) {
            // Excellent progress - can wait longer
            this.nextReviewDate = generatedAt.plusDays(60);
        }
    }

    @Override
    public String toString() {
        return "ProgressReport{" +
                "id='" + id + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", clientId='" + clientId + '\'' +
                ", generatedAt=" + generatedAt +
                ", nextReviewDate=" + nextReviewDate +
                ", status='" + status + '\'' +
                ", metrics=" + metrics +
                ", recommendations=" + recommendations +
                '}';
    }
}