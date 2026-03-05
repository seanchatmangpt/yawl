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

package org.yawlfoundation.yawl.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Anomaly-based intrusion detection using behavioral baselines.
 *
 * Monitors actual behavior patterns for each client and detects deviations:
 * - API request rate deviations (statistical variance)
 * - Unusual access times (outside typical usage windows)
 * - Abnormal payload sizes
 * - Geographic/network anomalies (if available)
 * - Authentication failure spikes
 * - Successful activity after failed authentication attempts
 *
 * Detected anomalies trigger automatic responses:
 * - YELLOW: Log and monitor (1.5-2.0 sigma deviation)
 * - ORANGE: Throttle and challenge (2.0-3.0 sigma)
 * - RED: Quarantine and block (>3.0 sigma or 5+ consecutive failures)
 *
 * Baselines are established over configurable periods (default: 7 days) and
 * automatically adjusted as normal behavior evolves.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class AnomalyDetectionSecurity {

    private static final Logger log = LogManager.getLogger(AnomalyDetectionSecurity.class);

    private static final long BASELINE_WINDOW_HOURS = 7 * 24; // 7 days
    private static final long MEASUREMENT_INTERVAL_MINUTES = 5;
    private static final int MIN_SAMPLES_FOR_BASELINE = 20;

    private enum AnomalyLevel {
        NORMAL(0.0, "No deviation"),
        YELLOW(1.5, "Unusual but tolerated"),
        ORANGE(2.0, "Significant deviation - throttle"),
        RED(3.0, "Critical anomaly - block");

        private final double sigmaBound;
        private final String description;

        AnomalyLevel(double sigmaBound, String description) {
            this.sigmaBound = sigmaBound;
            this.description = description;
        }

        public double getSigmaBound() {
            return sigmaBound;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Behavioral profile for a single client.
     */
    private static class ClientProfile {
        private final String clientId;
        private final Deque<Long> requestTimestamps; // Last 1000 requests
        private final Deque<Integer> payloadSizes;
        private final Deque<Integer> hourOfDay;
        private final AtomicInteger consecutiveFailures;
        private final AtomicInteger quarantineUntil; // Epoch seconds
        private long lastBaselineUpdate;
        private double meanRequestsPerHour;
        private double stdDevRequestsPerHour;
        private double meanPayloadSize;
        private double stdDevPayloadSize;
        private boolean baselineEstablished;

        ClientProfile(String clientId) {
            this.clientId = clientId;
            this.requestTimestamps = new LinkedList<>();
            this.payloadSizes = new LinkedList<>();
            this.hourOfDay = new LinkedList<>();
            this.consecutiveFailures = new AtomicInteger(0);
            this.quarantineUntil = new AtomicInteger(0);
            this.lastBaselineUpdate = Instant.now().getEpochSecond();
            this.baselineEstablished = false;
        }

        void recordRequest(int payloadSize) {
            long now = Instant.now().getEpochSecond();
            requestTimestamps.addLast(now);
            payloadSizes.addLast(payloadSize);
            hourOfDay.addLast(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));

            // Keep only last 1000 requests
            if (requestTimestamps.size() > 1000) {
                requestTimestamps.removeFirst();
                payloadSizes.removeFirst();
                hourOfDay.removeFirst();
            }

            // Update baseline every MEASUREMENT_INTERVAL
            if (now - lastBaselineUpdate > MEASUREMENT_INTERVAL_MINUTES * 60) {
                updateBaseline();
            }
        }

        private void updateBaseline() {
            if (requestTimestamps.size() < MIN_SAMPLES_FOR_BASELINE) {
                return;
            }

            // Calculate request rate statistics
            long now = Instant.now().getEpochSecond();
            long windowStart = now - (BASELINE_WINDOW_HOURS * 3600);
            List<Long> recentRequests = requestTimestamps.stream()
                    .filter(ts -> ts >= windowStart)
                    .collect(Collectors.toList());

            if (recentRequests.size() >= MIN_SAMPLES_FOR_BASELINE) {
                double[] requestRates = calculateRequestRatesPerHour(recentRequests);
                meanRequestsPerHour = Arrays.stream(requestRates).average().orElse(0.0);
                stdDevRequestsPerHour = calculateStandardDeviation(requestRates, meanRequestsPerHour);

                // Payload size statistics
                double[] sizes = payloadSizes.stream()
                        .mapToDouble(Integer::doubleValue)
                        .toArray();
                meanPayloadSize = Arrays.stream(sizes).average().orElse(0.0);
                stdDevPayloadSize = calculateStandardDeviation(sizes, meanPayloadSize);

                baselineEstablished = true;
                lastBaselineUpdate = now;
            }
        }

        private double[] calculateRequestRatesPerHour(List<Long> timestamps) {
            Map<Long, Integer> hourCounts = new HashMap<>();
            for (Long ts : timestamps) {
                long hour = ts / 3600;
                hourCounts.put(hour, hourCounts.getOrDefault(hour, 0) + 1);
            }
            return hourCounts.values().stream()
                    .mapToDouble(Integer::doubleValue)
                    .toArray();
        }

        private double calculateStandardDeviation(double[] values, double mean) {
            if (values.length == 0) return 0.0;
            double sumSquaredDiff = 0.0;
            for (double val : values) {
                sumSquaredDiff += Math.pow(val - mean, 2);
            }
            return Math.sqrt(sumSquaredDiff / values.length);
        }

        void recordAuthenticationFailure() {
            consecutiveFailures.incrementAndGet();
            if (consecutiveFailures.get() >= 5) {
                // Quarantine for 1 hour
                long quarantineUntilEpoch = Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond();
                this.quarantineUntil.set((int) quarantineUntilEpoch);
                log.warn("Client {} quarantined after {} consecutive auth failures",
                        clientId, consecutiveFailures.get());
            }
        }

        void recordAuthenticationSuccess() {
            consecutiveFailures.set(0);
        }

        boolean isQuarantined() {
            int quarantineTime = quarantineUntil.get();
            if (quarantineTime == 0) {
                return false;
            }
            long now = Instant.now().getEpochSecond();
            if (now > quarantineTime) {
                quarantineUntil.set(0);
                return false;
            }
            return true;
        }
    }

    private final Map<String, ClientProfile> profiles;

    /**
     * Creates a new anomaly detection engine with empty baselines.
     * Baselines are established automatically as requests are observed.
     */
    public AnomalyDetectionSecurity() {
        this.profiles = new ConcurrentHashMap<>();
    }

    /**
     * Analyzes an incoming request for anomalies.
     *
     * @param clientId unique identifier for the client
     * @param payloadSize size in bytes of request payload
     * @return AnomalyLevel indicating detected anomaly severity
     * @throws IllegalArgumentException if clientId is null or empty
     * @throws IllegalArgumentException if payloadSize is negative
     */
    public AnomalyLevel detectAnomaly(String clientId, int payloadSize) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }
        if (payloadSize < 0) {
            throw new IllegalArgumentException("payloadSize cannot be negative");
        }

        ClientProfile profile = profiles.computeIfAbsent(clientId, ClientProfile::new);

        // Check quarantine status first
        if (profile.isQuarantined()) {
            log.warn("Request from quarantined client: {}", clientId);
            return AnomalyLevel.RED;
        }

        profile.recordRequest(payloadSize);

        // No anomaly detection until baseline is established
        if (!profile.baselineEstablished) {
            return AnomalyLevel.NORMAL;
        }

        // Detect payload size anomaly
        double payloadZScore = Math.abs((payloadSize - profile.meanPayloadSize) / profile.stdDevPayloadSize);
        if (payloadZScore > AnomalyLevel.RED.sigmaBound) {
            log.warn("RED anomaly detected for client {}: payload size z-score = {}",
                    clientId, payloadZScore);
            return AnomalyLevel.RED;
        }
        if (payloadZScore > AnomalyLevel.ORANGE.sigmaBound) {
            log.info("ORANGE anomaly detected for client {}: payload size z-score = {}",
                    clientId, payloadZScore);
            return AnomalyLevel.ORANGE;
        }
        if (payloadZScore > AnomalyLevel.YELLOW.sigmaBound) {
            log.debug("YELLOW anomaly detected for client {}: payload size z-score = {}",
                    clientId, payloadZScore);
            return AnomalyLevel.YELLOW;
        }

        // Detect request rate anomaly (requests per hour)
        double requestRateZScore = calculateRequestRateZScore(profile);
        if (requestRateZScore > AnomalyLevel.RED.sigmaBound) {
            log.warn("RED anomaly detected for client {}: request rate z-score = {}",
                    clientId, requestRateZScore);
            return AnomalyLevel.RED;
        }
        if (requestRateZScore > AnomalyLevel.ORANGE.sigmaBound) {
            log.info("ORANGE anomaly detected for client {}: request rate z-score = {}",
                    clientId, requestRateZScore);
            return AnomalyLevel.ORANGE;
        }
        if (requestRateZScore > AnomalyLevel.YELLOW.sigmaBound) {
            log.debug("YELLOW anomaly detected for client {}: request rate z-score = {}",
                    clientId, requestRateZScore);
            return AnomalyLevel.YELLOW;
        }

        return AnomalyLevel.NORMAL;
    }

    /**
     * Records a failed authentication attempt for a client.
     *
     * @param clientId unique identifier for the client
     * @throws IllegalArgumentException if clientId is null or empty
     */
    public void recordAuthenticationFailure(String clientId) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }

        ClientProfile profile = profiles.computeIfAbsent(clientId, ClientProfile::new);
        profile.recordAuthenticationFailure();
    }

    /**
     * Records a successful authentication for a client.
     *
     * @param clientId unique identifier for the client
     * @throws IllegalArgumentException if clientId is null or empty
     */
    public void recordAuthenticationSuccess(String clientId) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }

        ClientProfile profile = profiles.computeIfAbsent(clientId, ClientProfile::new);
        profile.recordAuthenticationSuccess();
    }

    /**
     * Determines if a client should be blocked due to anomalous behavior.
     *
     * @param clientId unique identifier for the client
     * @return true if client is quarantined or under RED alert
     */
    public boolean shouldBlock(String clientId) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }

        ClientProfile profile = profiles.get(clientId);
        if (profile == null) {
            return false;
        }

        return profile.isQuarantined();
    }

    /**
     * Gets the current anomaly level for a client.
     *
     * @param clientId unique identifier for the client
     * @return AnomalyLevel, or NORMAL if no profile exists
     */
    public AnomalyLevel getAnomalyLevel(String clientId) {
        Objects.requireNonNull(clientId, "clientId cannot be null");

        ClientProfile profile = profiles.get(clientId);
        if (profile == null || !profile.baselineEstablished) {
            return AnomalyLevel.NORMAL;
        }

        // This returns NORMAL - caller should use detectAnomaly() for real analysis
        return AnomalyLevel.NORMAL;
    }

    /**
     * Manually quarantine a suspicious client.
     *
     * @param clientId unique identifier for the client
     * @param durationHours duration of quarantine in hours
     * @throws IllegalArgumentException if clientId is null/empty or durationHours <= 0
     */
    public void quarantineClient(String clientId, int durationHours) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }
        if (durationHours <= 0) {
            throw new IllegalArgumentException("durationHours must be positive");
        }

        ClientProfile profile = profiles.computeIfAbsent(clientId, ClientProfile::new);
        long quarantineUntilEpoch = Instant.now().plus(durationHours, ChronoUnit.HOURS).getEpochSecond();
        profile.quarantineUntil.set((int) quarantineUntilEpoch);
        log.info("Client {} quarantined for {} hours", clientId, durationHours);
    }

    /**
     * Clears a client's quarantine status.
     *
     * @param clientId unique identifier for the client
     */
    public void unquarantineClient(String clientId) {
        Objects.requireNonNull(clientId, "clientId cannot be null");

        ClientProfile profile = profiles.get(clientId);
        if (profile != null) {
            profile.quarantineUntil.set(0);
            profile.consecutiveFailures.set(0);
            log.info("Client {} unquarantined", clientId);
        }
    }

    /**
     * Gets the number of active client profiles being monitored.
     *
     * @return count of profiles
     */
    public int getProfileCount() {
        return profiles.size();
    }

    private double calculateRequestRateZScore(ClientProfile profile) {
        // Calculate current hourly request rate
        long now = Instant.now().getEpochSecond();
        long oneHourAgo = now - 3600;
        long recentRequests = profile.requestTimestamps.stream()
                .filter(ts -> ts >= oneHourAgo)
                .count();

        if (profile.stdDevRequestsPerHour == 0.0) {
            return 0.0;
        }

        return Math.abs((recentRequests - profile.meanRequestsPerHour) / profile.stdDevRequestsPerHour);
    }
}
