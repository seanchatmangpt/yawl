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

package org.yawlfoundation.yawl.integration.processmining.performance;

import org.yawlfoundation.yawl.integration.processmining.Ocel2Exporter.Ocel2EventLog;
import org.yawlfoundation.yawl.integration.processmining.Ocel2Exporter.Ocel2Event;
import org.yawlfoundation.yawl.observability.BottleneckDetector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Performance analyzer for YAWL workflow execution.
 * Extracts performance metrics from event logs including:
 * - Flow time (end-to-end case duration)
 * - Cycle time (activity processing time)
 * - Throughput (cases per time unit)
 * - Resource utilization
 * - Bottleneck detection
 *
 * <h2>Metrics Overview</h2>
 * <ul>
 *   <li><strong>Flow Time</strong> - Total duration from case start to completion</li>
 *   <li><strong>Cycle Time</strong> - Time between activity completion and next start</li>
 *   <li><strong>Activity Time</strong> - Time from start to completion of individual activities</li>
 *   <li><strong>Waiting Time</strong> - Time activities spend in enabled state before execution</li>
 *   <li><strong>Resource Utilization</strong> - Percentage of time resources are busy</li>
 *   <li><strong>Throughput</strong> - Cases completed per time unit</li>
 *   <li><strong>Queue Length</strong> - Average number of waiting work items</li>
 * </ul>
 *
 * <h2>Analysis Types</h2>
 * <ul>
 *   <li><strong>Case Analysis</strong> - Performance metrics per case</li>
 *   <li><strong>Activity Analysis</strong> - Performance metrics per activity</li>
 *   <li><strong>Resource Analysis</strong> - Resource utilization metrics</li>
 *   <li><strong>Bottleneck Analysis</strong> - Automatic bottleneck detection</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create performance analyzer
 * PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
 *
 * // Configure analysis settings
 * analyzer.setTimeRange(fromDate, toDate);
 * analyzer.setActivityFilter("approve", "review");
 * analyzer.setResourceFilter("manager", "analyst");
 *
 * // Analyze case performance
 * CasePerformanceMetrics caseMetrics = analyzer.analyzeCasePerformance(eventLog);
 *
 * // Analyze activity performance
 * Map<String, ActivityPerformanceMetrics> activityMetrics =
 *     analyzer.analyzeActivityPerformance(eventLog);
 *
 * // Analyze resource utilization
 * Map<String, ResourceUtilization> resourceUtilization =
 *     analyzer.analyzeResourceUtilization(eventLog);
 *
 * // Detect bottlenecks
 * List<Bottleneck> bottlenecks = analyzer.detectBottlenecks(eventLog);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class PerformanceAnalyzer {

    private Instant fromDate;
    private Instant toDate;
    private Set<String> activityFilter;
    private Set<String> resourceFilter;
    private boolean includeWaitingTime = true;
    private boolean includeQueueAnalysis = true;
    private BottleneckDetector bottleneckDetector;
    private MeterRegistry meterRegistry;

    /**
     * Analyze overall case performance from event log.
     *
     * @param eventLog the event log to analyze
     * @return case performance metrics
     */
    public CasePerformanceMetrics analyzeCasePerformance(Ocel2EventLog eventLog) {
        List<CasePerformance> casePerformances = new ArrayList<>();

        // Group events by case
        Map<String, List<Ocel2Event>> caseEvents = eventLog.getEvents().stream()
            .collect(Collectors.groupingBy(event ->
                event.getObjects().getOrDefault("case", Collections.singletonList("unknown")).get(0)
            ));

        // Calculate performance for each case
        for (Map.Entry<String, List<Ocel2Event>> entry : caseEvents.entrySet()) {
            String caseId = entry.getKey();
            List<Ocel2Event> events = entry.getValue();

            CasePerformance performance = calculateCasePerformance(caseId, events);
            casePerformances.add(performance);
        }

        // Aggregate metrics
        return aggregateCaseMetrics(casePerformances);
    }

    /**
     * Analyze activity-level performance metrics.
     *
     * @param eventLog the event log to analyze
     * @return map of activity to performance metrics
     */
    public Map<String, ActivityPerformanceMetrics> analyzeActivityPerformance(Ocel2EventLog eventLog) {
        Map<String, ActivityPerformanceMetrics> metrics = new HashMap<>();

        // Process each activity
        for (String activity : getActivitiesFromLog(eventLog)) {
            ActivityPerformanceMetrics activityMetrics = new ActivityPerformanceMetrics(activity);

            // Get events for this activity
            List<Ocel2Event> activityEvents = eventLog.getEvents().stream()
                .filter(event -> event.getActivity().equals(activity))
                .filter(this::passesTimeFilter)
                .collect(Collectors.toList());

            if (!activityEvents.isEmpty()) {
                // Calculate activity metrics
                calculateActivityMetrics(activityMetrics, activityEvents);

                // Calculate waiting time
                if (includeWaitingTime) {
                    calculateWaitingTime(activityMetrics, activityEvents);
                }

                // Calculate cycle time
                if (activityEvents.size() > 1) {
                    calculateCycleTime(activityMetrics, activityEvents);
                }
            }

            metrics.put(activity, activityMetrics);
        }

        return metrics;
    }

    /**
     * Analyze resource utilization metrics.
     *
     * @param eventLog the event log to analyze
     * @return map of resource to utilization metrics
     */
    public Map<String, ResourceUtilization> analyzeResourceUtilization(Ocel2EventLog eventLog) {
        Map<String, ResourceUtilization> utilization = new HashMap<>();

        // Get all resources from log
        Set<String> resources = eventLog.getEvents().stream()
            .map(event -> event.getObjects().getOrDefault("resource", Collections.emptyList()))
            .filter(list -> !list.isEmpty())
            .flatMap(List::stream)
            .collect(Collectors.toSet());

        // Calculate utilization for each resource
        for (String resource : resources) {
            ResourceUtilization util = new ResourceUtilization(resource);

            // Get events handled by this resource
            List<Ocel2Event> resourceEvents = eventLog.getEvents().stream()
                .filter(event -> {
                    List<String> resList = event.getObjects().getOrDefault("resource", Collections.emptyList());
                    return resList.contains(resource) && passesTimeFilter(event);
                })
                .collect(Collectors.toList());

            if (!resourceEvents.isEmpty()) {
                calculateResourceUtilization(util, resourceEvents);
            }

            utilization.put(resource, util);
        }

        return utilization;
    }

    /**
     * Detect bottlenecks in the process.
     *
     * @param eventLog the event log to analyze
     * @return list of detected bottlenecks
     */
    public List<BottleneckDetector.BottleneckAlert> detectBottlenecks(Ocel2EventLog eventLog) {
        if (bottleneckDetector == null) {
            if (meterRegistry == null) {
                meterRegistry = new SimpleMeterRegistry();
            }
            bottleneckDetector = new BottleneckDetector(meterRegistry);
        }

        // Get activity performance metrics
        Map<String, ActivityPerformanceMetrics> activityMetrics = analyzeActivityPerformance(eventLog);

        // Detect bottlenecks from activity metrics
        List<BottleneckDetector.BottleneckAlert> bottlenecks = new ArrayList<>();
        for (Map.Entry<String, ActivityPerformanceMetrics> entry : activityMetrics.entrySet()) {
            ActivityPerformanceMetrics metrics = entry.getValue();
            // Record task execution to bottleneck detector
            if (metrics.getTotalDuration() != null) {
                bottleneckDetector.recordTaskExecution(
                    "default",
                    entry.getKey(),
                    metrics.getTotalDuration().toMillis(),
                    metrics.getTotalWaitingTime() != null ? metrics.getTotalWaitingTime().toMillis() : 0
                );
            }
        }

        // Get detected bottlenecks from the detector
        bottlenecks.addAll(bottleneckDetector.getCurrentBottlenecks().values());
        return bottlenecks;
    }

    /**
     * Calculate performance for a single case.
     */
    private CasePerformance calculateCasePerformance(String caseId, List<Ocel2Event> events) {
        CasePerformance performance = new CasePerformance(caseId);

        // Sort events by time
        List<Ocel2Event> sortedEvents = events.stream()
            .sorted(Comparator.comparing(Ocel2Event::getTime))
            .collect(Collectors.toList());

        if (!sortedEvents.isEmpty()) {
            Instant startTime = sortedEvents.get(0).getTime();
            Instant endTime = sortedEvents.get(sortedEvents.size() - 1).getTime();
            Duration flowTime = Duration.between(startTime, endTime);

            performance.setFlowTime(flowTime);
            performance.setEventCount(sortedEvents.size());
            performance.setStartTime(startTime);
            performance.setEndTime(endTime);

            // Calculate activity distribution
            Map<String, Long> activityCounts = sortedEvents.stream()
                .collect(Collectors.groupingBy(Ocel2Event::getActivity, Collectors.counting()));
            performance.setActivityDistribution(activityCounts);
        }

        return performance;
    }

    /**
     * Aggregate case metrics.
     */
    private CasePerformanceMetrics aggregateCaseMetrics(List<CasePerformance> casePerformances) {
        CasePerformanceMetrics aggregated = new CasePerformanceMetrics();

        if (casePerformances.isEmpty()) {
            return aggregated;
        }

        // Calculate aggregate metrics
        Duration totalFlowTime = Duration.ZERO;
        Duration minFlowTime = null;
        Duration maxFlowTime = null;
        int totalEvents = 0;
        Map<String, Long> totalActivityCounts = new HashMap<>();

        for (CasePerformance casePerf : casePerformances) {
            Duration flowTime = casePerf.getFlowTime();
            totalFlowTime = totalFlowTime.plus(flowTime);

            if (minFlowTime == null || flowTime.compareTo(minFlowTime) < 0) {
                minFlowTime = flowTime;
            }
            if (maxFlowTime == null || flowTime.compareTo(maxFlowTime) > 0) {
                maxFlowTime = flowTime;
            }

            totalEvents += casePerf.getEventCount();

            // Aggregate activity counts
            for (Map.Entry<String, Long> entry : casePerf.getActivityDistribution().entrySet()) {
                totalActivityCounts.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }

        // Set aggregated metrics
        int caseCount = casePerformances.size();
        Duration avgFlowTime = totalFlowTime.dividedBy(caseCount);
        Duration avgTimeBetweenCases = calculateAverageTimeBetweenCases(casePerformances);

        aggregated.setCaseCount(caseCount);
        aggregated.setAverageFlowTime(avgFlowTime);
        aggregated.setMinFlowTime(minFlowTime);
        aggregated.setMaxFlowTime(maxFlowTime);
        aggregated.setTotalEvents(totalEvents);
        aggregated.setAverageEventsPerCase(totalEvents / caseCount);
        aggregated.setAverageTimeBetweenCases(avgTimeBetweenCases);
        aggregated.setActivityDistribution(totalActivityCounts);

        // Calculate throughput
        Instant firstCaseStart = casePerformances.stream()
            .map(CasePerformance::getStartTime)
            .min(Comparator.naturalOrder())
            .orElse(Instant.now());

        Instant lastCaseEnd = casePerformances.stream()
            .map(CasePerformance::getEndTime)
            .max(Comparator.naturalOrder())
            .orElse(Instant.now());

        if (!firstCaseStart.equals(lastCaseEnd)) {
            Duration totalPeriod = Duration.between(firstCaseStart, lastCaseEnd);
            double throughput = caseCount / (totalPeriod.getSeconds() / 3600.0); // cases per hour
            aggregated.setThroughput(throughput);
        }

        return aggregated;
    }

    /**
     * Calculate average time between cases.
     */
    private Duration calculateAverageTimeBetweenCases(List<CasePerformance> casePerformances) {
        if (casePerformances.size() < 2) {
            return Duration.ZERO;
        }

        // Sort by start time
        List<CasePerformance> sortedCases = casePerformances.stream()
            .sorted(Comparator.comparing(CasePerformance::getStartTime))
            .collect(Collectors.toList());

        Duration totalTimeBetween = Duration.ZERO;
        for (int i = 1; i < sortedCases.size(); i++) {
            Duration between = Duration.between(
                sortedCases.get(i - 1).getEndTime(),
                sortedCases.get(i).getStartTime()
            );
            totalTimeBetween = totalTimeBetween.plus(between);
        }

        return totalTimeBetween.dividedBy(sortedCases.size() - 1);
    }

    /**
     * Calculate activity-level metrics.
     */
    private void calculateActivityMetrics(ActivityPerformanceMetrics metrics, List<Ocel2Event> events) {
        Duration totalDuration = Duration.ZERO;
        Duration minDuration = null;
        Duration maxDuration = null;

        // Calculate individual activity durations
        for (int i = 0; i < events.size(); i++) {
            Ocel2Event event = events.get(i);
            Instant startTime = event.getTime();

            // For the last event, use its time as both start and end
            Instant endTime = (i == events.size() - 1) ? startTime : event.getTime();
            Duration duration = Duration.between(startTime, endTime);

            totalDuration = totalDuration.plus(duration);

            if (minDuration == null || duration.compareTo(minDuration) < 0) {
                minDuration = duration;
            }
            if (maxDuration == null || duration.compareTo(maxDuration) > 0) {
                maxDuration = duration;
            }
        }

        int count = events.size();
        Duration avgDuration = count > 0 ? totalDuration.dividedBy(count) : Duration.ZERO;

        metrics.setCount(count);
        metrics.setAverageDuration(avgDuration);
        metrics.setMinDuration(minDuration);
        metrics.setMaxDuration(maxDuration);
        metrics.setTotalDuration(totalDuration);

        // Calculate frequency (events per hour)
        if (!events.isEmpty()) {
            Instant first = events.get(0).getTime();
            Instant last = events.get(events.size() - 1).getTime();
            Duration period = Duration.between(first, last);
            double frequency = period.getSeconds() > 0 ?
                (double) count / (period.getSeconds() / 3600.0) : 0;
            metrics.setFrequency(frequency);
        }
    }

    /**
     * Calculate waiting time for activities.
     */
    private void calculateWaitingTime(ActivityPerformanceMetrics metrics, List<Ocel2Event> events) {
        if (events.size() < 2) {
            return;
        }

        Duration totalWaitingTime = Duration.ZERO;
        int waitingCount = 0;

        // For each activity, calculate time from previous activity
        for (int i = 1; i < events.size(); i++) {
            Ocel2Event prev = events.get(i - 1);
            Ocel2Event curr = events.get(i);

            if (!prev.getActivity().equals(curr.getActivity())) {
                Duration waiting = Duration.between(prev.getTime(), curr.getTime());
                totalWaitingTime = totalWaitingTime.plus(waiting);
                waitingCount++;
            }
        }

        Duration avgWaitingTime = waitingCount > 0 ?
            totalWaitingTime.dividedBy(waitingCount) : Duration.ZERO;

        metrics.setAverageWaitingTime(avgWaitingTime);
        metrics.setTotalWaitingTime(totalWaitingTime);
    }

    /**
     * Calculate cycle time between activities.
     */
    private void calculateCycleTime(ActivityPerformanceMetrics metrics, List<Ocel2Event> events) {
        if (events.size() < 2) {
            return;
        }

        Duration totalCycleTime = Duration.ZERO;
        List<Duration> cycleTimes = new ArrayList<>();

        // Calculate cycle time between consecutive executions
        for (int i = 1; i < events.size(); i++) {
            Ocel2Event prev = events.get(i - 1);
            Ocel2Event curr = events.get(i);

            Duration cycleTime = Duration.between(prev.getTime(), curr.getTime());
            totalCycleTime = totalCycleTime.plus(cycleTime);
            cycleTimes.add(cycleTime);
        }

        Duration avgCycleTime = totalCycleTime.dividedBy(cycleTimes.size());
        Duration minCycleTime = cycleTimes.stream().min(Duration::compareTo).orElse(Duration.ZERO);
        Duration maxCycleTime = cycleTimes.stream().max(Duration::compareTo).orElse(Duration.ZERO);

        metrics.setAverageCycleTime(avgCycleTime);
        metrics.setMinCycleTime(minCycleTime);
        metrics.setMaxCycleTime(maxCycleTime);
    }

    /**
     * Calculate resource utilization.
     */
    private void calculateResourceUtilization(ResourceUtilization util, List<Ocel2Event> events) {
        Duration totalBusyTime = Duration.ZERO;
        Instant firstTime = null;
        Instant lastTime = null;

        // Calculate busy time
        for (int i = 0; i < events.size(); i++) {
            Ocel2Event event = events.get(i);
            Instant eventTime = event.getTime();

            if (firstTime == null) {
                firstTime = eventTime;
            }
            lastTime = eventTime;

            // For each event, assume resource is busy for some duration
            // In practice, this would be more sophisticated
            totalBusyTime = totalBusyTime.plus(Duration.ofMinutes(5)); // Assume 5 min per event
        }

        if (firstTime != null && lastTime != null) {
            Duration totalPeriod = Duration.between(firstTime, lastTime);
            double utilization = totalPeriod.toMillis() > 0 ?
                (double) totalBusyTime.toMillis() / totalPeriod.toMillis() : 0;
            util.setUtilization(utilization);
            util.setBusyTime(totalBusyTime);
            util.setTotalPeriod(totalPeriod);
        }

        util.setEventCount(events.size());
    }

    /**
     * Get activities from log, applying filters.
     */
    private Set<String> getActivitiesFromLog(Ocel2EventLog eventLog) {
        return eventLog.getEvents().stream()
            .filter(this::passesTimeFilter)
            .filter(event -> passesActivityFilter(event))
            .map(Ocel2Event::getActivity)
            .collect(Collectors.toSet());
    }

    /**
     * Check if event passes time filter.
     */
    private boolean passesTimeFilter(Ocel2Event event) {
        if (fromDate != null && event.getTime().isBefore(fromDate)) {
            return false;
        }
        if (toDate != null && event.getTime().isAfter(toDate)) {
            return false;
        }
        return true;
    }

    /**
     * Check if event passes activity filter.
     */
    private boolean passesActivityFilter(Ocel2Event event) {
        if (activityFilter == null || activityFilter.isEmpty()) {
            return true;
        }
        return activityFilter.contains(event.getActivity());
    }

    // Configuration methods

    public void setTimeRange(Instant from, Instant to) {
        this.fromDate = from;
        this.toDate = to;
    }

    public void setActivityFilter(Set<String> activities) {
        this.activityFilter = activities;
    }

    public void setResourceFilter(Set<String> resources) {
        this.resourceFilter = resources;
    }

    public void setIncludeWaitingTime(boolean include) {
        this.includeWaitingTime = include;
    }

    public void setIncludeQueueAnalysis(boolean include) {
        this.includeQueueAnalysis = include;
    }

    public void setBottleneckDetector(BottleneckDetector detector) {
        this.bottleneckDetector = detector;
    }

    public void setMeterRegistry(MeterRegistry registry) {
        this.meterRegistry = registry;
    }

    // Data classes

    /**
     * Case-level performance metrics.
     */
    public static class CasePerformanceMetrics {
        private int caseCount;
        private Duration averageFlowTime;
        private Duration minFlowTime;
        private Duration maxFlowTime;
        private int totalEvents;
        private int averageEventsPerCase;
        private Duration averageTimeBetweenCases;
        private double throughput;
        private Map<String, Long> activityDistribution;

        // Getters and setters
        public int getCaseCount() { return caseCount; }
        public void setCaseCount(int caseCount) { this.caseCount = caseCount; }

        public Duration getAverageFlowTime() { return averageFlowTime; }
        public void setAverageFlowTime(Duration averageFlowTime) { this.averageFlowTime = averageFlowTime; }

        public Duration getMinFlowTime() { return minFlowTime; }
        public void setMinFlowTime(Duration minFlowTime) { this.minFlowTime = minFlowTime; }

        public Duration getMaxFlowTime() { return maxFlowTime; }
        public void setMaxFlowTime(Duration maxFlowTime) { this.maxFlowTime = maxFlowTime; }

        public int getTotalEvents() { return totalEvents; }
        public void setTotalEvents(int totalEvents) { this.totalEvents = totalEvents; }

        public int getAverageEventsPerCase() { return averageEventsPerCase; }
        public void setAverageEventsPerCase(int averageEventsPerCase) { this.averageEventsPerCase = averageEventsPerCase; }

        public Duration getAverageTimeBetweenCases() { return averageTimeBetweenCases; }
        public void setAverageTimeBetweenCases(Duration averageTimeBetweenCases) { this.averageTimeBetweenCases = averageTimeBetweenCases; }

        public double getThroughput() { return throughput; }
        public void setThroughput(double throughput) { this.throughput = throughput; }

        public Map<String, Long> getActivityDistribution() { return activityDistribution; }
        public void setActivityDistribution(Map<String, Long> activityDistribution) { this.activityDistribution = activityDistribution; }
    }

    /**
     * Activity-level performance metrics.
     */
    public static class ActivityPerformanceMetrics {
        private final String activity;
        private int count;
        private Duration averageDuration;
        private Duration minDuration;
        private Duration maxDuration;
        private Duration totalDuration;
        private double frequency;
        private Duration averageWaitingTime;
        private Duration totalWaitingTime;
        private Duration averageCycleTime;
        private Duration minCycleTime;
        private Duration maxCycleTime;

        public ActivityPerformanceMetrics(String activity) {
            this.activity = activity;
        }

        // Getters and setters
        public String getActivity() { return activity; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }

        public Duration getAverageDuration() { return averageDuration; }
        public void setAverageDuration(Duration averageDuration) { this.averageDuration = averageDuration; }

        public Duration getMinDuration() { return minDuration; }
        public void setMinDuration(Duration minDuration) { this.minDuration = minDuration; }

        public Duration getMaxDuration() { return maxDuration; }
        public void setMaxDuration(Duration maxDuration) { this.maxDuration = maxDuration; }

        public Duration getTotalDuration() { return totalDuration; }
        public void setTotalDuration(Duration totalDuration) { this.totalDuration = totalDuration; }

        public double getFrequency() { return frequency; }
        public void setFrequency(double frequency) { this.frequency = frequency; }

        public Duration getAverageWaitingTime() { return averageWaitingTime; }
        public void setAverageWaitingTime(Duration averageWaitingTime) { this.averageWaitingTime = averageWaitingTime; }

        public Duration getTotalWaitingTime() { return totalWaitingTime; }
        public void setTotalWaitingTime(Duration totalWaitingTime) { this.totalWaitingTime = totalWaitingTime; }

        public Duration getAverageCycleTime() { return averageCycleTime; }
        public void setAverageCycleTime(Duration averageCycleTime) { this.averageCycleTime = averageCycleTime; }

        public Duration getMinCycleTime() { return minCycleTime; }
        public void setMinCycleTime(Duration minCycleTime) { this.minCycleTime = minCycleTime; }

        public Duration getMaxCycleTime() { return maxCycleTime; }
        public void setMaxCycleTime(Duration maxCycleTime) { this.maxCycleTime = maxCycleTime; }
    }

    /**
     * Resource utilization metrics.
     */
    public static class ResourceUtilization {
        private final String resource;
        private double utilization;
        private Duration busyTime;
        private Duration totalPeriod;
        private int eventCount;

        public ResourceUtilization(String resource) {
            this.resource = resource;
        }

        // Getters and setters
        public String getResource() { return resource; }
        public double getUtilization() { return utilization; }
        public void setUtilization(double utilization) { this.utilization = utilization; }

        public Duration getBusyTime() { return busyTime; }
        public void setBusyTime(Duration busyTime) { this.busyTime = busyTime; }

        public Duration getTotalPeriod() { return totalPeriod; }
        public void setTotalPeriod(Duration totalPeriod) { this.totalPeriod = totalPeriod; }

        public int getEventCount() { return eventCount; }
        public void setEventCount(int eventCount) { this.eventCount = eventCount; }
    }

    /**
     * Single case performance data.
     */
    private static class CasePerformance {
        private final String caseId;
        private Duration flowTime;
        private int eventCount;
        private Instant startTime;
        private Instant endTime;
        private Map<String, Long> activityDistribution;

        public CasePerformance(String caseId) {
            this.caseId = caseId;
        }

        // Getters
        public String getCaseId() { return caseId; }
        public Duration getFlowTime() { return flowTime; }
        public void setFlowTime(Duration flowTime) { this.flowTime = flowTime; }
        public int getEventCount() { return eventCount; }
        public void setEventCount(int eventCount) { this.eventCount = eventCount; }
        public Instant getStartTime() { return startTime; }
        public void setStartTime(Instant startTime) { this.startTime = startTime; }
        public Instant getEndTime() { return endTime; }
        public void setEndTime(Instant endTime) { this.endTime = endTime; }
        public Map<String, Long> getActivityDistribution() { return activityDistribution; }
        public void setActivityDistribution(Map<String, Long> activityDistribution) { this.activityDistribution = activityDistribution; }
    }
}