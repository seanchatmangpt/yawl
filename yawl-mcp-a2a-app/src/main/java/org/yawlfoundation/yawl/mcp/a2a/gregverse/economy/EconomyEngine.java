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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.economy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * GregVerse Economy Engine - Central coordinator for marketplace transactions.
 *
 * <p>Orchestrates all economic activities in the GregVerse marketplace, including
 * service provider (OT) listing, consumer booking, dynamic pricing, reputation
 * management, and contract execution. Uses Java 25 features for high-performance
 * concurrent operations.</p>
 *
 * <h2>Core Features</h2>
 * <ul>
 *   <li>Service marketplace with dynamic pricing</li>
 *   <li>Multi-dimensional reputation system</li>
 *   <li>Immutable transaction ledger</li>
 *   <li>Smart contract management</li>
 *   <li>Virtual thread-based concurrent operations</li>
 *   <li>Real-time analytics and monitoring</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * EconomyEngine engine = new EconomyEngine();
 *
 * // Provider lists a service
 * ServiceContract.Template template = ServiceContract.Template.createStandardTherapyContract();
 * ServiceContract contract = engine.listService(
 *     "ot-001", "client-001", "assessment", 250.00, template,
 *     ZonedDateTime.now().plusDays(1), Duration.ofHours(2)
 * );
 *
 * // Consumer discovers and books
 * List<ServiceContract> available = engine.discoverServices("assessment", 200.0, 300.0);
 * ServiceContract booking = engine.bookService("client-001", contract.getContractId());
 *
 * // Complete service delivery
 * engine.markDeliverableCompleted(booking.getContractId(), "initial-assessment");
 * ReputationSystem.ServiceRating rating = new ReputationSystem.ServiceRating(
 *     "rating-001", "provider-001", "client-001", booking.getServiceId(),
 *     new ReputationSystem.MultiDimensionalRating(5, 5, 5, 5), Instant.now()
 * );
 * engine.completeContract(booking.getContractId(), rating);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class EconomyEngine {

    private static final Logger logger = LoggerFactory.getLogger(EconomyEngine.class);

    // Core components
    private final TransactionLedger transactionLedger;
    private final PricingEngine pricingEngine;
    private final ReputationSystem reputationSystem;
    private final Map<String, ServiceContract> activeContracts;
    private final Map<String, ProviderProfile> providerProfiles;
    private final Map<String, ConsumerProfile> consumerProfiles;

    // Performance optimization
    private final ExecutorService virtualThreadExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    private final AtomicLong operationCounter;
    private final Instant engineStartTime;

    // Monitoring metrics
    private final ConcurrentMap<String, BigDecimal> performanceMetrics;
    private final ConcurrentMap<String, Long> operationCounts;

    /**
     * Creates a new economy engine.
     */
    public EconomyEngine() {
        this.transactionLedger = new TransactionLedger();
        this.pricingEngine = new PricingEngine();
        this.reputationSystem = new ReputationSystem();
        this.activeContracts = new ConcurrentHashMap<>();
        this.providerProfiles = new ConcurrentHashMap<>();
        this.consumerProfiles = new ConcurrentHashMap<>();
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.scheduledExecutor = Executors.newScheduledThreadPool(4);
        this.operationCounter = new AtomicLong(0);
        this.engineStartTime = Instant.now();
        this.performanceMetrics = new ConcurrentHashMap<>();
        this.operationCounts = new ConcurrentHashMap<>();

        // Initialize performance monitoring
        startPerformanceMonitoring();

        logger.info("Initialized GregVerse Economy Engine with virtual thread support");
    }

    /**
     * Lists a service for an OT provider.
     *
     * @param providerId the provider ID
     * @param clientId the client ID
     * @param serviceId the service ID
     * @param basePrice the base price
     * @param template the contract template
     * @param scheduledStart scheduled start time
     * @param duration service duration
     * @return created service contract
     */
    public ServiceContract listService(String providerId, String clientId, String serviceId,
                                     double basePrice, ServiceContract.Template template,
                                     ZonedDateTime scheduledStart, Duration duration) {
        Objects.requireNonNull(providerId, "Provider ID must not be null");
        Objects.requireNonNull(clientId, "Client ID must not be null");
        Objects.requireNonNull(serviceId, "Service ID must not be null");
        Objects.requireNonNull(template, "Template must not be null");
        Objects.requireNonNull(scheduledStart, "Scheduled start must not be null");
        Objects.requireNonNull(duration, "Duration must not be null");

        // Create provider profile if needed
        createProviderProfileIfNotExists(providerId);

        // Calculate optimal price
        MarketplaceCurrency optimalPrice = pricingEngine.calculateOptimalPrice(
            basePrice, serviceId, providerId, transactionLedger
        );

        // Create contract
        ServiceContract contract = new ServiceContract.Builder(
            generateContractId(), providerId, clientId, serviceId, template, optimalPrice,
            scheduledStart, duration
        ).build();

        // Store contract
        activeContracts.put(contract.getContractId(), contract);

        // Record listing transaction (no payment yet, just tracking)
        transactionLedger.recordTransaction(
            "system", providerId, serviceId + "-listing", MarketplaceCurrency.ZERO
        );

        incrementOperation("listService");

        logger.info("Listed service {} for provider {} at price {}",
            serviceId, providerId, optimalPrice.format());

        return contract;
    }

    /**
     * Books a service contract.
     *
     * @param clientId the client ID
     * @param contractId the contract ID
     * @return booked contract
     * @throws IllegalArgumentException if contract not found or invalid
     */
    public ServiceContract bookService(String clientId, String contractId) {
        Objects.requireNonNull(clientId, "Client ID must not be null");
        Objects.requireNonNull(contractId, "Contract ID must not be null");

        ServiceContract contract = activeContracts.get(contractId);
        if (contract == null) {
            throw new IllegalArgumentException("Contract not found: " + contractId);
        }

        if (!contract.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Client does not own contract: " + contractId);
        }

        if (contract.getStatus() != ServiceContract.Status.DRAFT) {
            throw new IllegalStateException("Cannot book contract in status: " + contract.getStatus());
        }

        // Check client has sufficient funds
        createConsumerProfileIfNotExists(clientId);
        MarketplaceCurrency clientBalance = getBalance(clientId);
        if (clientBalance.compareTo(contract.getPrice()) < 0) {
            throw new IllegalStateException("Insufficient funds: " + clientBalance.format());
        }

        // Execute booking in virtual thread
        CompletableFuture<ServiceContract> future = CompletableFuture.supplyAsync(() -> {
            try {
                // Process payment
                MarketplaceCurrency price = contract.getPrice();
                transferFunds(clientId, contract.getProviderId(), price);

                // Record transaction
                TransactionLedger.Transaction transaction = transactionLedger.recordTransaction(
                    clientId, contract.getProviderId(), contract.getServiceId(), price
                );

                // Update contract status
                ServiceContract bookedContract = contract.accept();

                // Update profiles
                updateProviderProfileOnBooking(contract.getProviderId(), price);
                updateConsumerProfileOnBooking(clientId, price);

                incrementOperation("bookService");

                logger.info("Booked service {} for client {} - payment: {}",
                    contract.getServiceId(), clientId, price.format());

                return bookedContract;
            } catch (Exception e) {
                logger.error("Failed to book service " + contractId + " for client " + clientId, e);
                throw new CompletionException(e);
            }
        }, virtualThreadExecutor);

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Booking operation interrupted", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("Booking operation failed", e);
        }
    }

    /**
     * Discovers available services.
     *
     * @param serviceType the service type to search for
     * @param minPrice minimum price filter
     * @param maxPrice maximum price filter
     * @return list of available service contracts
     */
    public List<ServiceContract> discoverServices(String serviceType, double minPrice, double maxPrice) {
        Objects.requireNonNull(serviceType, "Service type must not be null");

        MarketplaceCurrency min = new MarketplaceCurrency(minPrice);
        MarketplaceCurrency max = new MarketplaceCurrency(maxPrice);

        return activeContracts.values().stream()
            .filter(contract -> {
                ServiceContract.Status status = contract.getStatus();
                return status == ServiceContract.Status.DRAFT || status == ServiceContract.Status.PENDING;
            })
            .filter(contract -> contract.getServiceId().toLowerCase().contains(serviceType.toLowerCase()))
            .filter(contract -> {
                MarketplaceCurrency price = contract.getPrice();
                return price.isGreaterThanOrEqualTo(min) && price.isLessThanOrEqualTo(max);
            })
            .sorted(Comparator.comparing(ServiceContract::getScheduledStart))
            .collect(Collectors.toList());
    }

    /**
     * Gets provider recommendations based on reputation and preferences.
     *
     * @param clientId the client ID
     * @param preferredServices list of preferred service types
     * @param minTrustScore minimum trust score
     * @return list of recommended provider summaries
     */
    public List<ReputationSystem.ProviderSummary> getRecommendedProviders(String clientId,
                                                                        List<String> preferredServices,
                                                                        double minTrustScore) {
        Objects.requireNonNull(clientId, "Client ID must not be null");
        Objects.requireNonNull(preferredServices, "Preferred services must not be null");

        BigDecimal trustScoreThreshold = BigDecimal.valueOf(minTrustScore);
        return reputationSystem.getRecommendedProviders(
            clientId, preferredServices, trustScoreThreshold
        );
    }

    /**
     * Marks a deliverable as completed.
     *
     * @param contractId the contract ID
     * @param deliverableId the deliverable ID
     * @return updated contract
     */
    public ServiceContract markDeliverableCompleted(String contractId, String deliverableId) {
        Objects.requireNonNull(contractId, "Contract ID must not be null");
        Objects.requireNonNull(deliverableId, "Deliverable ID must not be null");

        ServiceContract contract = activeContracts.get(contractId);
        if (contract == null) {
            throw new IllegalArgumentException("Contract not found: " + contractId);
        }

        try {
            return virtualThreadExecutor.submit(() -> {
                try {
                    ServiceContract updated = contract.markDeliverableCompleted(deliverableId);
                    activeContracts.put(contractId, updated);

                    incrementOperation("markDeliverableCompleted");

                    logger.info("Marked deliverable {} as completed for contract {}",
                        deliverableId, contractId);

                    return updated;
                } catch (Exception e) {
                    logger.error("Failed to mark deliverable " + deliverableId + " for contract " + contractId, e);
                    throw new CompletionException(e);
                }
            }).get(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException("Operation interrupted", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new CompletionException("Operation failed", e);
        }
    }

    /**
     * Completes a contract with final rating.
     *
     * @param contractId the contract ID
     * @param finalRating the final service rating
     * @return completed contract
     */
    public ServiceContract completeContract(String contractId, ReputationSystem.ServiceRating finalRating) {
        Objects.requireNonNull(contractId, "Contract ID must not be null");
        Objects.requireNonNull(finalRating, "Final rating must not be null");

        ServiceContract contract = activeContracts.get(contractId);
        if (contract == null) {
            throw new IllegalArgumentException("Contract not found: " + contractId);
        }

        try {
            return virtualThreadExecutor.submit(() -> {
                try {
                    // Add rating to reputation system
                    reputationSystem.addRating(
                        contract.getProviderId(), contract.getClientId(),
                        contract.getServiceId(),
                        finalRating.getRating().getQualityRating(),
                        finalRating.getRating().getReliabilityRating(),
                        finalRating.getRating().getCommunicationRating(),
                        finalRating.getRating().getSatisfactionRating()
                    );

                    // Complete contract
                    ServiceContract.ServiceRating contractRating = convertReputationRating(finalRating);
                    ServiceContract completed = contract.complete(contractRating);
                    activeContracts.put(contractId, completed);

                    // Update provider profile with rating
                    updateProviderProfileWithRating(contract.getProviderId(), finalRating);

                    incrementOperation("completeContract");

                    logger.info("Completed contract {} with rating {}",
                        contractId, calculateOverallRating(finalRating));

                    return completed;
                } catch (Exception e) {
                    logger.error("Failed to complete contract " + contractId, e);
                    throw new CompletionException(e);
                }
            }).get(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException("Operation interrupted", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new CompletionException("Operation failed", e);
        }
    }

    /**
     * Converts a reputation system rating to a service contract rating.
     */
    private ServiceContract.ServiceRating convertReputationRating(ReputationSystem.ServiceRating reputationRating) {
        return new ServiceContract.ServiceRating(
            reputationRating.getRatingId(),
            reputationRating.getRating().getQualityRating(),
            reputationRating.getRating().getReliabilityRating(),
            reputationRating.getRating().getCommunicationRating(),
            reputationRating.getRating().getSatisfactionRating(),
            reputationRating.getTimestamp()
        );
    }

    /**
     * Gets provider statistics.
     *
     * @param providerId the provider ID
     * @return provider statistics
     */
    public ProviderStatistics getProviderStatistics(String providerId) {
        Objects.requireNonNull(providerId, "Provider ID must not be null");

        // Get transaction history
        List<TransactionLedger.Transaction> transactions = transactionLedger.getTransactionHistory(providerId);

        // Get reputation
        Optional<ReputationSystem.ProviderReputation> reputationOpt = reputationSystem.getProviderReputation(providerId);

        // Calculate earnings
        MarketplaceCurrency totalEarnings = transactionLedger.getTotalEarned(providerId);
        MarketplaceCurrency avgEarnings = transactions.size() > 0 ?
            totalEarnings.divide(transactions.size()) : MarketplaceCurrency.ZERO;

        // Calculate average rating
        BigDecimal avgRating = reputationOpt.map(rep -> rep.getTrustScore()).orElse(BigDecimal.ZERO);

        // Count completed contracts
        long completedContracts = activeContracts.values().stream()
            .filter(contract -> contract.getProviderId().equals(providerId) &&
                               contract.getStatus() == ServiceContract.Status.COMPLETED)
            .count();

        return new ProviderStatistics(
            providerId, transactions.size(), totalEarnings, avgEarnings,
            avgRating, completedContracts
        );
    }

    /**
     * Gets marketplace statistics.
     *
     * @return marketplace statistics
     */
    public TransactionLedger.MarketplaceStatistics getMarketplaceStatistics() {
        return transactionLedger.getMarketplaceStatistics();
    }

    /**
     * Gets engine performance metrics.
     *
     * @return performance metrics
     */
    public Map<String, BigDecimal> getPerformanceMetrics() {
        return Collections.unmodifiableMap(performanceMetrics);
    }

    /**
     * Updates provider base prices.
     *
     * @param providerId the provider ID
     * @param newBasePrice the new base price
     * @return map of service IDs to updated prices
     */
    public Map<String, MarketplaceCurrency> updateProviderPrices(String providerId, double newBasePrice) {
        Objects.requireNonNull(providerId, "Provider ID must not be null");

        return pricingEngine.updateProviderPrices(providerId, newBasePrice, transactionLedger);
    }

    /**
     * Cancels a contract.
     *
     * @param contractId the contract ID
     * @param reason cancellation reason
     * @return updated contract
     */
    public ServiceContract cancelContract(String contractId, String reason) {
        Objects.requireNonNull(contractId, "Contract ID must not be null");
        Objects.requireNonNull(reason, "Reason must not be null");

        ServiceContract contract = activeContracts.get(contractId);
        if (contract == null) {
            throw new IllegalArgumentException("Contract not found: " + contractId);
        }

        try {
            return virtualThreadExecutor.submit(() -> {
                try {
                    ServiceContract cancelled = contract.cancel(reason);
                    activeContracts.put(contractId, cancelled);

                    // Refund payment if contract was booked
                    if (cancelled.getStatus() == ServiceContract.Status.CANCELLED &&
                        transactionLedger.getTransactionHistory(cancelled.getClientId()).stream()
                            .anyMatch(tx -> tx.getTransactionId().startsWith("tx-" + contractId))) {

                        MarketplaceCurrency price = cancelled.getPrice();
                        transferFunds(cancelled.getProviderId(), cancelled.getClientId(), price);

                        logger.info("Refunded {} to client {} for cancelled contract {}",
                            price.format(), cancelled.getClientId(), contractId);
                    }

                    incrementOperation("cancelContract");

                    logger.info("Cancelled contract {} due to: {}", contractId, reason);
                    return cancelled;
                } catch (Exception e) {
                    logger.error("Failed to cancel contract " + contractId, e);
                    throw new CompletionException(e);
                }
            }).get(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException("Operation interrupted", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new CompletionException("Operation failed", e);
        }
    }

    /**
     * Validates system integrity.
     *
     * @return true if system is valid
     */
    public boolean validateSystemIntegrity() {
        try {
            boolean ledgerValid = transactionLedger.validateIntegrity();
            boolean reputationValid = reputationSystem.validateIntegrity();

            if (!ledgerValid) {
                logger.error("Transaction ledger integrity check failed");
            }

            if (!reputationValid) {
                logger.error("Reputation system integrity check failed");
            }

            return ledgerValid && reputationValid;
        } catch (Exception e) {
            logger.error("System integrity check failed", e);
            return false;
        }
    }

    /**
     * Shuts down the economy engine gracefully.
     */
    public void shutdown() {
        logger.info("Shutting down Economy Engine");

        // Shutdown executors
        virtualThreadExecutor.shutdown();
        scheduledExecutor.shutdown();

        try {
            if (!virtualThreadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }

            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Economy Engine shutdown complete");
    }

    // Helper methods

    private String generateContractId() {
        return "contract-" + UUID.randomUUID().toString();
    }

    private void createProviderProfileIfNotExists(String providerId) {
        providerProfiles.computeIfAbsent(providerId, id -> {
            ProviderProfile profile = new ProviderProfile(id);
            logger.debug("Created provider profile for {}", id);
            return profile;
        });
    }

    private void createConsumerProfileIfNotExists(String clientId) {
        consumerProfiles.computeIfAbsent(clientId, id -> {
            ConsumerProfile profile = new ConsumerProfile(id);
            logger.debug("Created consumer profile for {}", id);
            return profile;
        });
    }

    private MarketplaceCurrency getBalance(String agentId) {
        // In a real implementation, this would query a wallet service
        // For now, assume all agents have sufficient funds
        return new MarketplaceCurrency(10000.0);
    }

    private void transferFunds(String fromId, String toId, MarketplaceCurrency amount) {
        // In a real implementation, this would update wallet balances
        logger.info("Transferred {} from {} to {}", amount.format(), fromId, toId);
    }

    private void updateProviderProfileOnBooking(String providerId, MarketplaceCurrency amount) {
        ProviderProfile profile = providerProfiles.get(providerId);
        if (profile != null) {
            profile.recordBooking(amount);
        }
    }

    private void updateConsumerProfileOnBooking(String clientId, MarketplaceCurrency amount) {
        ConsumerProfile profile = consumerProfiles.get(clientId);
        if (profile != null) {
            profile.recordBooking(amount);
        }
    }

    private void updateProviderProfileWithRating(String providerId, ReputationSystem.ServiceRating rating) {
        ProviderProfile profile = providerProfiles.get(providerId);
        if (profile != null) {
            profile.recordRating(calculateOverallRating(rating));
        }
    }

    private double calculateOverallRating(ReputationSystem.ServiceRating rating) {
        double total = rating.getRating().getQualityRating() + rating.getRating().getReliabilityRating() +
                       rating.getRating().getCommunicationRating() + rating.getRating().getSatisfactionRating();
        return total / 4.0;
    }

    private void incrementOperation(String operationName) {
        operationCounts.merge(operationName, 1L, Long::sum);
        operationCounter.incrementAndGet();
    }

    private void startPerformanceMonitoring() {
        // Schedule periodic performance metrics collection
        scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                collectPerformanceMetrics();
            } catch (Exception e) {
                logger.error("Error collecting performance metrics", e);
            }
        }, 60, 60, TimeUnit.SECONDS); // Every minute
    }

    private void collectPerformanceMetrics() {
        // Calculate uptime
        Duration uptime = Duration.between(engineStartTime, Instant.now());
        performanceMetrics.put("uptime_minutes", BigDecimal.valueOf(uptime.toMinutes()));

        // Calculate operation rates
        performanceMetrics.put("total_operations", BigDecimal.valueOf(operationCounter.get()));
        performanceMetrics.put("operations_per_minute",
            BigDecimal.valueOf(operationCounter.get()).max(BigDecimal.ONE)
                .divide(BigDecimal.valueOf(uptime.toMinutes()), 2, RoundingMode.HALF_UP));

        // Calculate active contracts
        performanceMetrics.put("active_contracts", BigDecimal.valueOf(activeContracts.size()));

        // Calculate success rate (approximate)
        long successfulOperations = operationCounts.values().stream()
            .mapToLong(Long::longValue)
            .sum();
        performanceMetrics.put("success_rate",
            BigDecimal.valueOf(successfulOperations).divide(BigDecimal.valueOf(operationCounter.get()).max(BigDecimal.ONE), 4, RoundingMode.HALF_UP));

        logger.debug("Performance metrics updated: {}", performanceMetrics);
    }

    // Inner classes for data structures

    /**
     * Provider profile information.
     */
    private static final class ProviderProfile {
        private final String providerId;
        private long totalBookings;
        private MarketplaceCurrency totalEarnings;
        private double averageRating;
        private Instant lastBooking;

        public ProviderProfile(String providerId) {
            this.providerId = providerId;
            this.totalBookings = 0;
            this.totalEarnings = MarketplaceCurrency.ZERO;
            this.averageRating = 0.0;
            this.lastBooking = null;
        }

        public void recordBooking(MarketplaceCurrency amount) {
            this.totalBookings++;
            this.totalEarnings = this.totalEarnings.add(amount);
            this.lastBooking = Instant.now();
        }

        public void recordRating(double rating) {
            if (this.averageRating == 0.0) {
                this.averageRating = rating;
            } else {
                this.averageRating = (this.averageRating + rating) / 2.0;
            }
        }

        // Getters
        public String getProviderId() { return providerId; }
        public long getTotalBookings() { return totalBookings; }
        public MarketplaceCurrency getTotalEarnings() { return totalEarnings; }
        public double getAverageRating() { return averageRating; }
        public Instant getLastBooking() { return lastBooking; }
    }

    /**
     * Consumer profile information.
     */
    private static final class ConsumerProfile {
        private final String clientId;
        private long totalBookings;
        private MarketplaceCurrency totalSpent;
        private Instant lastBooking;

        public ConsumerProfile(String clientId) {
            this.clientId = clientId;
            this.totalBookings = 0;
            this.totalSpent = MarketplaceCurrency.ZERO;
            this.lastBooking = null;
        }

        public void recordBooking(MarketplaceCurrency amount) {
            this.totalBookings++;
            this.totalSpent = this.totalSpent.add(amount);
            this.lastBooking = Instant.now();
        }

        // Getters
        public String getClientId() { return clientId; }
        public long getTotalBookings() { return totalBookings; }
        public MarketplaceCurrency getTotalSpent() { return totalSpent; }
        public Instant getLastBooking() { return lastBooking; }
    }

    /**
     * Provider statistics.
     */
    public static final class ProviderStatistics {
        private final String providerId;
        private final long totalTransactions;
        private final MarketplaceCurrency totalEarnings;
        private final MarketplaceCurrency averageTransactionValue;
        private final BigDecimal averageRating;
        private final long completedContracts;

        public ProviderStatistics(String providerId, long totalTransactions,
                                MarketplaceCurrency totalEarnings,
                                MarketplaceCurrency averageTransactionValue,
                                BigDecimal averageRating, long completedContracts) {
            this.providerId = Objects.requireNonNull(providerId);
            this.totalTransactions = totalTransactions;
            this.totalEarnings = Objects.requireNonNull(totalEarnings);
            this.averageTransactionValue = Objects.requireNonNull(averageTransactionValue);
            this.averageRating = Objects.requireNonNull(averageRating);
            this.completedContracts = completedContracts;
        }

        // Getters
        public String getProviderId() { return providerId; }
        public long getTotalTransactions() { return totalTransactions; }
        public MarketplaceCurrency getTotalEarnings() { return totalEarnings; }
        public MarketplaceCurrency getAverageTransactionValue() { return averageTransactionValue; }
        public BigDecimal getAverageRating() { return averageRating; }
        public long getCompletedContracts() { return completedContracts; }
    }

    /**
     * Marketplace statistics.
     */
    public static final class MarketplaceStats {
        private final long totalTransactions;
        private final MarketplaceCurrency totalVolume;
        private final MarketplaceCurrency averageTransactionValue;
        private final long activeProviders;
        private final long activeConsumers;

        public MarketplaceStats(long totalTransactions, MarketplaceCurrency totalVolume,
                              MarketplaceCurrency averageTransactionValue,
                              long activeProviders, long activeConsumers) {
            this.totalTransactions = totalTransactions;
            this.totalVolume = Objects.requireNonNull(totalVolume);
            this.averageTransactionValue = Objects.requireNonNull(averageTransactionValue);
            this.activeProviders = activeProviders;
            this.activeConsumers = activeConsumers;
        }

        // Getters
        public long getTotalTransactions() { return totalTransactions; }
        public MarketplaceCurrency getTotalVolume() { return totalVolume; }
        public MarketplaceCurrency getAverageTransactionValue() { return averageTransactionValue; }
        public long getActiveProviders() { return activeProviders; }
        public long getActiveConsumers() { return activeConsumers; }
    }
}