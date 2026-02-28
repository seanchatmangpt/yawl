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

package org.yawlfoundation.yawl.safe.agents;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * Predictive sprint planning agent using 6-month velocity trend analysis.
 *
 * <p>Analyzes historical sprint performance to recommend optimal sprint capacity,
 * automatically adjusting for team velocity trends, risk factors, and upcoming
 * holidays/vacations. This eliminates manual capacity planning and provides
 * data-driven recommendations.
 *
 * <p>Features:
 * <ul>
 *   <li>Analyzes 6-month velocity trends (min, max, average, std deviation)</li>
 *   <li>Auto-recommends sprint capacity based on confidence levels</li>
 *   <li>Flags risk factors (declining velocity, high volatility, missing data)</li>
 *   <li>Adjusts recommendations for team changes and vacation days</li>
 *   <li>Provides statistical confidence interval for capacity recommendations</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
public final class PredictiveSprintAgent {

    private static final Logger logger = LogManager.getLogger(PredictiveSprintAgent.class);

    private static final int LOOKBACK_SPRINTS = 12; // 6 months at 2-week sprints
    private static final double CONSERVATIVE_FACTOR = 0.85;  // 85% of average for conservative
    private static final double MODERATE_FACTOR = 1.0;       // 100% of average for moderate
    private static final double AGGRESSIVE_FACTOR = 1.15;    // 115% of average for aggressive

    /**
     * Recommendation confidence levels for capacity planning.
     */
    public enum ConfidenceLevel {
        CONSERVATIVE("Conservative - 85% of average", CONSERVATIVE_FACTOR),
        MODERATE("Moderate - 100% of average", MODERATE_FACTOR),
        AGGRESSIVE("Aggressive - 115% of average", AGGRESSIVE_FACTOR);

        private final String description;
        private final double factor;

        ConfidenceLevel(String description, double factor) {
            this.description = description;
            this.factor = factor;
        }

        public String getDescription() {
            return description;
        }

        public double getFactor() {
            return factor;
        }
    }

    /**
     * Risk factor assessment for sprint planning.
     */
    public record RiskFactor(String name, String severity, String description) {
    }

    /**
     * Sprint capacity recommendation with statistical confidence.
     */
    public record SprintRecommendation(
            int recommendedCapacity,
            int minCapacity,
            int maxCapacity,
            ConfidenceLevel confidenceLevel,
            double averageVelocity,
            double standardDeviation,
            List<RiskFactor> riskFactors,
            String rationale
    ) {
    }

    /**
     * Velocity statistics for trend analysis.
     */
    public record VelocityStats(
            double min,
            double max,
            double average,
            double standardDeviation,
            int dataPoints,
            double trend // positive = improving, negative = declining
    ) {
    }

    private final List<SAFeSprint> historicalSprints;

    /**
     * Create agent with historical sprint data.
     *
     * @param historicalSprints list of past sprints (typically 12 most recent)
     */
    public PredictiveSprintAgent(List<SAFeSprint> historicalSprints) {
        Objects.requireNonNull(historicalSprints, "Historical sprints required");
        this.historicalSprints = new ArrayList<>(historicalSprints);
        this.historicalSprints.sort(Comparator.comparing(SAFeSprint::startDate));
    }

    /**
     * Recommend sprint capacity based on velocity trends.
     *
     * @param confidenceLevel desired confidence level (conservative/moderate/aggressive)
     * @param vacationDays number of team vacation days in planned sprint
     * @return capacity recommendation with reasoning
     */
    public SprintRecommendation recommendCapacity(
            ConfidenceLevel confidenceLevel,
            int vacationDays) {

        logger.info("Analyzing {} historical sprints for capacity recommendation",
            historicalSprints.size());

        // Calculate velocity statistics
        VelocityStats stats = calculateVelocityStats();
        List<RiskFactor> risks = assessRiskFactors(stats);

        // Adjust for vacation days (conservative: assume full loss, no scaling beyond this)
        double vacationAdjustment = calculateVacationAdjustment(vacationDays);
        logger.debug("Vacation adjustment factor: {}", vacationAdjustment);

        // Calculate base recommendation
        double baseCapacity = stats.average * confidenceLevel.getFactor();
        double adjustedCapacity = baseCapacity * vacationAdjustment;
        int recommendedCapacity = (int) Math.round(adjustedCapacity);

        // Calculate confidence interval (95% CI: ±1.96 * std deviation)
        int minCapacity = Math.max(0,
            (int) Math.floor(stats.average - 1.96 * stats.standardDeviation));
        int maxCapacity = (int) Math.ceil(stats.average + 1.96 * stats.standardDeviation);

        String rationale = buildRationale(
            stats, confidenceLevel, vacationDays, risks, adjustedCapacity);

        return new SprintRecommendation(
            recommendedCapacity,
            minCapacity,
            maxCapacity,
            confidenceLevel,
            stats.average,
            stats.standardDeviation,
            risks,
            rationale
        );
    }

    /**
     * Calculate velocity statistics from historical data.
     *
     * @return statistics including trend analysis
     */
    public VelocityStats calculateVelocityStats() {
        if (historicalSprints.isEmpty()) {
            logger.warn("No historical sprint data available for analysis");
            return new VelocityStats(0, 0, 0, 0, 0, 0);
        }

        List<Double> velocities = historicalSprints.stream()
            .map(s -> (double) s.completedPoints())
            .collect(Collectors.toList());

        double min = velocities.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = velocities.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double average = velocities.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // Calculate standard deviation
        double variance = velocities.stream()
            .mapToDouble(v -> Math.pow(v - average, 2))
            .average()
            .orElse(0);
        double stdDev = Math.sqrt(variance);

        // Calculate trend (linear regression slope)
        double trend = calculateTrend(velocities);

        logger.info("Velocity stats: min={}, max={}, avg={}, stddev={}, trend={}",
            min, max, average, stdDev, trend);

        return new VelocityStats(min, max, average, stdDev, velocities.size(), trend);
    }

    /**
     * Calculate linear trend from velocity data (trend analysis).
     * Positive = improving velocity, negative = declining velocity.
     */
    private double calculateTrend(List<Double> velocities) {
        if (velocities.size() < 2) {
            return 0;
        }

        // Simple linear regression: calculate slope
        int n = velocities.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumX2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += velocities.get(i);
            sumXY += i * velocities.get(i);
            sumX2 += i * i;
        }

        // Slope of trend line
        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        return slope;
    }

    /**
     * Assess risk factors affecting sprint capacity.
     */
    private List<RiskFactor> assessRiskFactors(VelocityStats stats) {
        List<RiskFactor> risks = new ArrayList<>();

        // Risk 1: Declining velocity trend
        if (stats.trend < -2.0) {
            risks.add(new RiskFactor(
                "Declining Velocity",
                "HIGH",
                String.format("Velocity declining at %.2f points/sprint. Investigate blockers.", -stats.trend)
            ));
        } else if (stats.trend < -0.5) {
            risks.add(new RiskFactor(
                "Slight Velocity Decline",
                "MEDIUM",
                String.format("Minor declining trend (%.2f points/sprint)", -stats.trend)
            ));
        }

        // Risk 2: High volatility (coefficient of variation > 30%)
        double cv = stats.average > 0 ? (stats.standardDeviation / stats.average) : 0;
        if (cv > 0.3) {
            risks.add(new RiskFactor(
                "High Velocity Volatility",
                "MEDIUM",
                String.format("High variance (%.0f%% CV) suggests inconsistent completion", cv * 100)
            ));
        }

        // Risk 3: Insufficient data
        if (stats.dataPoints < 6) {
            risks.add(new RiskFactor(
                "Limited Historical Data",
                "LOW",
                "Less than 6 sprints of data; recommendation confidence is lower"
            ));
        }

        // Risk 4: Extreme volatility (> 50%)
        if (cv > 0.5) {
            risks.add(new RiskFactor(
                "Extreme Volatility",
                "HIGH",
                "High unpredictability; consider implementing risk buffer"
            ));
        }

        return risks;
    }

    /**
     * Calculate vacation adjustment factor.
     * Assumes 5-day work weeks and scales down capacity accordingly.
     *
     * @param vacationDays number of vacation days in sprint
     * @return adjustment factor (0.8 for 2 days vacation in 10-day sprint, etc)
     */
    private double calculateVacationAdjustment(int vacationDays) {
        if (vacationDays <= 0) {
            return 1.0;
        }

        // Assume 2-week sprint (10 working days for typical team)
        // Each vacation day = ~1/10 capacity loss
        int sprintWorkingDays = 10;
        return Math.max(0.5, 1.0 - (vacationDays / (double) sprintWorkingDays));
    }

    /**
     * Build detailed rationale for capacity recommendation.
     */
    private String buildRationale(
            VelocityStats stats,
            ConfidenceLevel confidence,
            int vacationDays,
            List<RiskFactor> risks,
            double adjustedCapacity) {

        StringBuilder rationale = new StringBuilder();
        rationale.append("Capacity recommendation based on: ");
        rationale.append(String.format("%.1f avg velocity", stats.average));

        if (vacationDays > 0) {
            rationale.append(String.format(" adjusted for %d vacation days", vacationDays));
        }

        rationale.append(String.format(" at %s confidence (%.1f points). ",
            confidence.getDescription(), adjustedCapacity));

        if (!risks.isEmpty()) {
            rationale.append(String.format("NOTE: %d risk factor(s) detected - ",
                risks.size()));
            String riskSummary = risks.stream()
                .map(r -> String.format("%s (%s)", r.name(), r.severity()))
                .collect(Collectors.joining(", "));
            rationale.append(riskSummary);
        } else {
            rationale.append("No significant risk factors.");
        }

        return rationale.toString();
    }

    /**
     * Auto-adjust commitment based on real-time progress.
     * Call this during sprint to recommend scope adjustments.
     *
     * @param currentSprint the active sprint
     * @param daysElapsed days into sprint
     * @param pointsCompleted points completed so far
     * @return recommendation to continue, reduce, or expand scope
     */
    public String autoAdjustCommitment(
            SAFeSprint currentSprint,
            int daysElapsed,
            int pointsCompleted) {

        if (daysElapsed == 0 || currentSprint.committedPoints() == 0) {
            return "NOT_APPLICABLE";
        }

        // Calculate burn-down rate
        double ratePerDay = (double) pointsCompleted / daysElapsed;
        double projectedCompletion = ratePerDay * 10; // Assume 10-day sprint

        double completionPercentage = (projectedCompletion / currentSprint.committedPoints()) * 100;

        logger.debug("Sprint burn-down: {:.0f}% of committed points projected",
            completionPercentage);

        if (completionPercentage >= 110) {
            return "EXPAND_SCOPE"; // Team is ahead; can take more
        } else if (completionPercentage >= 95) {
            return "MAINTAIN_SCOPE"; // On track
        } else if (completionPercentage >= 80) {
            return "MONITOR_CLOSELY"; // Slightly behind, monitor
        } else {
            return "REDUCE_SCOPE"; // Significantly behind, de-commit low-priority stories
        }
    }

    /**
     * Analyze team composition changes and their velocity impact.
     *
     * @param historicalTeamSize team size in past sprints
     * @param newTeamSize planned team size for upcoming sprint
     * @return velocity adjustment factor (1.0 = no adjustment)
     */
    public double estimateTeamSizeImpact(int historicalTeamSize, int newTeamSize) {
        if (historicalTeamSize == 0) {
            return 1.0;
        }

        // Assume linear scaling with team size
        // But account for onboarding overhead for new members
        double sizeRatio = (double) newTeamSize / historicalTeamSize;

        if (sizeRatio < 1.0) {
            // Team shrinking: 1:1 loss (losing person = losing 1x velocity)
            return sizeRatio;
        } else if (sizeRatio > 1.0) {
            // Team growing: new members contribute at ~70% due to onboarding
            double newMembers = newTeamSize - historicalTeamSize;
            double onboardingFactor = 0.7;
            double gainedVelocity = (newMembers * onboardingFactor) / historicalTeamSize;
            return 1.0 + gainedVelocity;
        }

        return 1.0;
    }

    /**
     * Generate a detailed sprint capacity report.
     *
     * @param upcomingSprint the sprint to analyze
     * @param vacationDays vacation days in sprint
     * @param teamSize team member count
     * @return human-readable report
     */
    public String generateCapacityReport(
            SAFeSprint upcomingSprint,
            int vacationDays,
            int teamSize) {

        SprintRecommendation moderateRec = recommendCapacity(
            ConfidenceLevel.MODERATE, vacationDays);
        SprintRecommendation conservativeRec = recommendCapacity(
            ConfidenceLevel.CONSERVATIVE, vacationDays);
        SprintRecommendation aggressiveRec = recommendCapacity(
            ConfidenceLevel.AGGRESSIVE, vacationDays);

        StringBuilder report = new StringBuilder();
        report.append("SPRINT CAPACITY ANALYSIS\n");
        report.append("========================\n\n");
        report.append(String.format("Sprint: %s (%s to %s)\n",
            upcomingSprint.id(), upcomingSprint.startDate(), upcomingSprint.endDate()));
        report.append(String.format("Team Size: %d | Vacation Days: %d\n\n", teamSize, vacationDays));

        report.append("RECOMMENDATIONS:\n");
        report.append(String.format("  Conservative:  %d points (%s)\n",
            conservativeRec.recommendedCapacity(), conservativeRec.confidenceLevel().getDescription()));
        report.append(String.format("  Moderate:      %d points (%s) [RECOMMENDED]\n",
            moderateRec.recommendedCapacity(), moderateRec.confidenceLevel().getDescription()));
        report.append(String.format("  Aggressive:    %d points (%s)\n",
            aggressiveRec.recommendedCapacity(), aggressiveRec.confidenceLevel().getDescription()));

        report.append(String.format("\nCONFIDENCE INTERVAL: %d - %d points (95%% CI)\n",
            moderateRec.minCapacity(), moderateRec.maxCapacity()));

        if (!moderateRec.riskFactors().isEmpty()) {
            report.append("\nRISK FACTORS:\n");
            for (RiskFactor risk : moderateRec.riskFactors()) {
                report.append(String.format("  • [%s] %s: %s\n",
                    risk.severity(), risk.name(), risk.description()));
            }
        }

        report.append("\nRATIONALE:\n");
        report.append("  " + moderateRec.rationale());

        return report.toString();
    }

    /**
     * Estimate sprint duration needed to complete a backlog.
     *
     * @param backlogPoints total points in backlog
     * @return estimated number of sprints needed
     */
    public int estimateSprintsNeeded(int backlogPoints) {
        VelocityStats stats = calculateVelocityStats();
        if (stats.average == 0) {
            return Integer.MAX_VALUE; // No historical data
        }
        return (int) Math.ceil(backlogPoints / stats.average);
    }
}
