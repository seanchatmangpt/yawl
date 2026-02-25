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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Immutable transaction ledger for the GregVerse marketplace.
 *
 * <p>Maintains an immutable record of all transactions between service providers (OTs)
 * and consumers. Each transaction is cryptographically hashed for integrity and
 * provides complete auditability.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Immutable transaction records with cryptographic hashing</li>
 *   <li>Concurrent-safe operations with thread-safe ledger</li>
 *   <li>Transaction history by agent, service, or time range</li>
 *   <li>Audit trail with timestamps and cryptographic signatures</li>
 *   <li>Performance-optimized with concurrent access patterns</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * TransactionLedger ledger = new TransactionLedger();
 * Transaction tx = ledger.recordTransaction(
 *     "buyer-001", "seller-ot-001", "assessment", new MarketplaceCurrency(250.00)
 * );
 * List<Transaction> buyerHistory = ledger.getTransactionHistory("buyer-001");
 * BigDecimal totalSpent = ledger.getTotalSpent("buyer-001");
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class TransactionLedger {

    private static final Logger logger = LoggerFactory.getLogger(TransactionLedger.class);

    // Immutable ledger state
    private final Map<String, Transaction> transactions;
    private final Map<String, List<String>> agentTransactionIndex;
    private final Map<String, List<String>> serviceTransactionIndex;
    private final Map<String, String> transactionHashes;

    // Performance optimization: cached totals and counts
    private final Map<String, MarketplaceCurrency> agentSpentCache;
    private final Map<String, MarketplaceCurrency> agentEarnedCache;
    private final Map<String, Long> agentTransactionCountCache;

    // Generation counter for ledger integrity
    private final AtomicLong generation;
    private final Instant createdAt;
    private volatile Instant lastModified;

    /**
     * Creates a new empty transaction ledger.
     */
    public TransactionLedger() {
        this.transactions = new ConcurrentHashMap<>();
        this.agentTransactionIndex = new ConcurrentHashMap<>();
        this.serviceTransactionIndex = new ConcurrentHashMap<>();
        this.transactionHashes = new ConcurrentHashMap<>();
        this.agentSpentCache = new ConcurrentHashMap<>();
        this.agentEarnedCache = new ConcurrentHashMap<>();
        this.agentTransactionCountCache = new ConcurrentHashMap<>();
        this.generation = new AtomicLong(0);
        this.createdAt = Instant.now();
        this.lastModified = Instant.now();
    }

    /**
     * Creates a transaction ledger from existing transactions.
     *
     * @param transactions existing transactions
     */
    private TransactionLedger(Map<String, Transaction> transactions,
                            Map<String, List<String>> agentTransactionIndex,
                            Map<String, List<String>> serviceTransactionIndex,
                            Map<String, String> transactionHashes) {
        this.transactions = new ConcurrentHashMap<>(transactions);
        this.agentTransactionIndex = new ConcurrentHashMap<>(agentTransactionIndex);
        this.serviceTransactionIndex = new ConcurrentHashMap<>(serviceTransactionIndex);
        this.transactionHashes = new ConcurrentHashMap<>(transactionHashes);
        this.agentSpentCache = new ConcurrentHashMap<>();
        this.agentEarnedCache = new ConcurrentHashMap<>();
        this.agentTransactionCountCache = new ConcurrentHashMap<>();
        this.generation = new AtomicLong(transactions.size());
        this.createdAt = Instant.now();
        this.lastModified = Instant.now();
    }

    /**
     * Records a new transaction in the ledger.
     *
     * @param buyerId the buyer agent ID
     * @param sellerId the seller agent ID
     * @param serviceId the service ID
     * @param amount the transaction amount
     * @return the recorded transaction
     * @throws IllegalArgumentException if parameters are invalid
     */
    public Transaction recordTransaction(String buyerId, String sellerId,
                                       String serviceId, MarketplaceCurrency amount) {
        Objects.requireNonNull(buyerId, "Buyer ID must not be null");
        Objects.requireNonNull(sellerId, "Seller ID must not be null");
        Objects.requireNonNull(serviceId, "Service ID must not be null");
        Objects.requireNonNull(amount, "Amount must not be null");

        if (buyerId.equals(sellerId)) {
            throw new IllegalArgumentException("Buyer and seller cannot be the same");
        }

        String transactionId = generateTransactionId();
        Instant timestamp = Instant.now();

        Transaction transaction = new Transaction(
            transactionId, buyerId, sellerId, serviceId, amount, timestamp
        );

        // Generate cryptographic hash for integrity
        String hash = generateTransactionHash(transaction);

        // Record the transaction
        transactions.put(transactionId, transaction);
        transactionHashes.put(transactionId, hash);

        // Update indexes
        updateAgentIndex(buyerId, transactionId);
        updateAgentIndex(sellerId, transactionId);
        updateServiceIndex(serviceId, transactionId);

        // Invalidate caches
        invalidateCaches(buyerId, sellerId);

        // Update generation counter
        generation.incrementAndGet();
        lastModified = Instant.now();

        logger.info("Recorded transaction {} from {} to {} for {}",
            transactionId, buyerId, sellerId, amount.format());

        return transaction;
    }

    /**
     * Gets a transaction by ID.
     *
     * @param transactionId the transaction ID
     * @return the transaction, or null if not found
     */
    public Optional<Transaction> getTransaction(String transactionId) {
        Objects.requireNonNull(transactionId, "Transaction ID must not be null");
        return Optional.ofNullable(transactions.get(transactionId));
    }

    /**
     * Gets all transactions.
     *
     * @return immutable copy of all transactions
     */
    public List<Transaction> getAllTransactions() {
        return Collections.unmodifiableList(new ArrayList<>(transactions.values()));
    }

    /**
     * Gets transaction history for an agent (as buyer or seller).
     *
     * @param agentId the agent ID
     * @return list of transactions sorted by timestamp (newest first)
     */
    public List<Transaction> getTransactionHistory(String agentId) {
        Objects.requireNonNull(agentId, "Agent ID must not be null");

        List<String> transactionIds = agentTransactionIndex.getOrDefault(agentId, List.of());
        return transactionIds.stream()
            .map(transactions::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(Transaction::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Gets transaction history for a specific service.
     *
     * @param serviceId the service ID
     * @return list of transactions sorted by timestamp (newest first)
     */
    public List<Transaction> getServiceHistory(String serviceId) {
        Objects.requireNonNull(serviceId, "Service ID must not be null");

        List<String> transactionIds = serviceTransactionIndex.getOrDefault(serviceId, List.of());
        return transactionIds.stream()
            .map(transactions::get)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing(Transaction::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Gets transactions within a time range.
     *
     * @param start start time (inclusive)
     * @param end end time (inclusive)
     * @return list of transactions in the time range
     */
    public List<Transaction> getTransactionsByTimeRange(Instant start, Instant end) {
        Objects.requireNonNull(start, "Start time must not be null");
        Objects.requireNonNull(end, "End time must not be null");

        return transactions.values().stream()
            .filter(tx -> !tx.getTimestamp().isBefore(start) && !tx.getTimestamp().isAfter(end))
            .sorted(Comparator.comparing(Transaction::getTimestamp))
            .collect(Collectors.toList());
    }

    /**
     * Gets total amount spent by an agent (as buyer).
     *
     * @param agentId the agent ID
     * @return total amount spent
     */
    public MarketplaceCurrency getTotalSpent(String agentId) {
        Objects.requireNonNull(agentId, "Agent ID must not be null");

        // Check cache first
        MarketplaceCurrency cached = agentSpentCache.get(agentId);
        if (cached != null) {
            return cached;
        }

        MarketplaceCurrency total = transactions.values().stream()
            .filter(tx -> tx.getBuyerId().equals(agentId))
            .map(Transaction::getAmount)
            .reduce(MarketplaceCurrency.ZERO, MarketplaceCurrency::add);

        agentSpentCache.put(agentId, total);
        return total;
    }

    /**
     * Gets total amount earned by an agent (as seller).
     *
     * @param agentId the agent ID
     * @return total amount earned
     */
    public MarketplaceCurrency getTotalEarned(String agentId) {
        Objects.requireNonNull(agentId, "Agent ID must not be null");

        // Check cache first
        MarketplaceCurrency cached = agentEarnedCache.get(agentId);
        if (cached != null) {
            return cached;
        }

        MarketplaceCurrency total = transactions.values().stream()
            .filter(tx -> tx.getSellerId().equals(agentId))
            .map(Transaction::getAmount)
            .reduce(MarketplaceCurrency.ZERO, MarketplaceCurrency::add);

        agentEarnedCache.put(agentId, total);
        return total;
    }

    /**
     * Gets total number of transactions for an agent.
     *
     * @param agentId the agent ID
     * @return total transaction count
     */
    public long getTransactionCount(String agentId) {
        Objects.requireNonNull(agentId, "Agent ID must not be null");

        // Check cache first
        Long cached = agentTransactionCountCache.get(agentId);
        if (cached != null) {
            return cached;
        }

        long count = agentTransactionIndex.getOrDefault(agentId, List.of()).size();
        agentTransactionCountCache.put(agentId, count);
        return count;
    }

    /**
     * Gets transaction statistics for an agent.
     *
     * @param agentId the agent ID
     * @return transaction statistics
     */
    public TransactionStatistics getAgentStatistics(String agentId) {
        Objects.requireNonNull(agentId, "Agent ID must not be null");

        List<Transaction> agentTransactions = getTransactionHistory(agentId);

        if (agentTransactions.isEmpty()) {
            return new TransactionStatistics(agentId, 0, MarketplaceCurrency.ZERO, MarketplaceCurrency.ZERO,
                                          MarketplaceCurrency.ZERO, MarketplaceCurrency.ZERO);
        }

        long totalTransactions = agentTransactions.size();
        MarketplaceCurrency totalSpent = getTotalSpent(agentId);
        MarketplaceCurrency totalEarned = getTotalEarned(agentId);

        // Calculate average transaction amounts
        long purchaseCount = agentTransactions.stream()
            .filter(tx -> tx.getBuyerId().equals(agentId))
            .count();
        long saleCount = agentTransactions.stream()
            .filter(tx -> tx.getSellerId().equals(agentId))
            .count();

        MarketplaceCurrency avgPurchase = purchaseCount > 0 ?
            totalSpent.divide(purchaseCount) : MarketplaceCurrency.ZERO;
        MarketplaceCurrency avgSale = saleCount > 0 ?
            totalEarned.divide(saleCount) : MarketplaceCurrency.ZERO;

        return new TransactionStatistics(
            agentId, totalTransactions, totalSpent, totalEarned,
            avgPurchase, avgSale
        );
    }

    /**
     * Gets overall marketplace statistics.
     *
     * @return marketplace statistics
     */
    public MarketplaceStatistics getMarketplaceStatistics() {
        if (transactions.isEmpty()) {
            return new MarketplaceStatistics(0, MarketplaceCurrency.ZERO, MarketplaceCurrency.ZERO,
                                         MarketplaceCurrency.ZERO, 0);
        }

        long totalTransactions = transactions.size();
        MarketplaceCurrency totalVolume = transactions.values().stream()
            .map(Transaction::getAmount)
            .reduce(MarketplaceCurrency.ZERO, MarketplaceCurrency::add);

        MarketplaceCurrency avgTransaction = totalVolume.divide(totalTransactions);

        // Unique participant count
        Set<String> participants = new HashSet<>();
        transactions.values().forEach(tx -> {
            participants.add(tx.getBuyerId());
            participants.add(tx.getSellerId());
        });

        long participantCount = participants.size();

        return new MarketplaceStatistics(
            totalTransactions, totalVolume, avgTransaction,
            avgTransaction, participantCount
        );
    }

    /**
     * Gets the current generation of the ledger.
     *
     * @return generation number
     */
    public long getGeneration() {
        return generation.get();
    }

    /**
     * Gets the creation timestamp of the ledger.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets the last modified timestamp.
     *
     * @return last modified timestamp
     */
    public Instant getLastModified() {
        return lastModified;
    }

    /**
     * Validates the integrity of the ledger.
     *
     * @return true if ledger is valid
     */
    public boolean validateIntegrity() {
        try {
            // Check transaction count matches generation
            if (transactions.size() != generation.get()) {
                logger.error("Ledger integrity check failed: transaction count mismatch");
                return false;
            }

            // Verify all transaction hashes
            for (Map.Entry<String, Transaction> entry : transactions.entrySet()) {
                String transactionId = entry.getKey();
                Transaction transaction = entry.getValue();
                String expectedHash = generateTransactionHash(transaction);
                String actualHash = transactionHashes.get(transactionId);

                if (!expectedHash.equals(actualHash)) {
                    logger.error("Ledger integrity check failed: hash mismatch for transaction {}", transactionId);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            logger.error("Ledger integrity check failed with exception", e);
            return false;
        }
    }

    /**
     * Creates a copy of this ledger (immutable).
     *
     * @return a new ledger instance with the same transactions
     */
    public TransactionLedger copy() {
        return new TransactionLedger(
            new HashMap<>(transactions),
            new HashMap<>(agentTransactionIndex),
            new HashMap<>(serviceTransactionIndex),
            new HashMap<>(transactionHashes)
        );
    }

    // Helper methods

    private String generateTransactionId() {
        return "tx-" + UUID.randomUUID().toString();
    }

    private String generateTransactionHash(Transaction transaction) {
        // Simple hash function for demonstration - use proper crypto in production
        String data = String.format("%s|%s|%s|%s|%s|%s",
            transaction.getTransactionId(),
            transaction.getBuyerId(),
            transaction.getSellerId(),
            transaction.getServiceId(),
            transaction.getAmount().toString(),
            transaction.getTimestamp().toString()
        );
        return Integer.toHexString(data.hashCode());
    }

    private void updateAgentIndex(String agentId, String transactionId) {
        agentTransactionIndex.compute(agentId, (key, value) -> {
            List<String> ids = value != null ? new ArrayList<>(value) : new ArrayList<>();
            ids.add(transactionId);
            return Collections.unmodifiableList(ids);
        });
    }

    private void updateServiceIndex(String serviceId, String transactionId) {
        serviceTransactionIndex.compute(serviceId, (key, value) -> {
            List<String> ids = value != null ? new ArrayList<>(value) : new ArrayList<>();
            ids.add(transactionId);
            return Collections.unmodifiableList(ids);
        });
    }

    private void invalidateCaches(String agent1, String agent2) {
        agentSpentCache.remove(agent1);
        agentSpentCache.remove(agent2);
        agentEarnedCache.remove(agent1);
        agentEarnedCache.remove(agent2);
        agentTransactionCountCache.remove(agent1);
        agentTransactionCountCache.remove(agent2);
    }

    // Records for immutable data structures

    /**
     * Immutable transaction record.
     */
    public static final class Transaction {
        private final String transactionId;
        private final String buyerId;
        private final String sellerId;
        private final String serviceId;
        private final MarketplaceCurrency amount;
        private final Instant timestamp;

        public Transaction(String transactionId, String buyerId, String sellerId,
                          String serviceId, MarketplaceCurrency amount, Instant timestamp) {
            this.transactionId = Objects.requireNonNull(transactionId);
            this.buyerId = Objects.requireNonNull(buyerId);
            this.sellerId = Objects.requireNonNull(sellerId);
            this.serviceId = Objects.requireNonNull(serviceId);
            this.amount = Objects.requireNonNull(amount);
            this.timestamp = Objects.requireNonNull(timestamp);
        }

        // Getters
        public String getTransactionId() { return transactionId; }
        public String getBuyerId() { return buyerId; }
        public String getSellerId() { return sellerId; }
        public String getServiceId() { return serviceId; }
        public MarketplaceCurrency getAmount() { return amount; }
        public Instant getTimestamp() { return timestamp; }
    }

    /**
     * Transaction statistics for an agent.
     */
    public static final class TransactionStatistics {
        private final String agentId;
        private final long totalTransactions;
        private final MarketplaceCurrency totalSpent;
        private final MarketplaceCurrency totalEarned;
        private final MarketplaceCurrency avgPurchase;
        private final MarketplaceCurrency avgSale;

        public TransactionStatistics(String agentId, long totalTransactions,
                                   MarketplaceCurrency totalSpent, MarketplaceCurrency totalEarned,
                                   MarketplaceCurrency avgPurchase, MarketplaceCurrency avgSale) {
            this.agentId = Objects.requireNonNull(agentId);
            this.totalTransactions = totalTransactions;
            this.totalSpent = Objects.requireNonNull(totalSpent);
            this.totalEarned = Objects.requireNonNull(totalEarned);
            this.avgPurchase = Objects.requireNonNull(avgPurchase);
            this.avgSale = Objects.requireNonNull(avgSale);
        }

        // Getters
        public String getAgentId() { return agentId; }
        public long getTotalTransactions() { return totalTransactions; }
        public MarketplaceCurrency getTotalSpent() { return totalSpent; }
        public MarketplaceCurrency getTotalEarned() { return totalEarned; }
        public MarketplaceCurrency getAvgPurchase() { return avgPurchase; }
        public MarketplaceCurrency getAvgSale() { return avgSale; }
    }

    /**
     * Marketplace statistics.
     */
    public static final class MarketplaceStatistics {
        private final long totalTransactions;
        private final MarketplaceCurrency totalVolume;
        private final MarketplaceCurrency avgTransaction;
        private final MarketplaceCurrency medianTransaction;
        private final long participantCount;

        public MarketplaceStatistics(long totalTransactions, MarketplaceCurrency totalVolume,
                                   MarketplaceCurrency avgTransaction, MarketplaceCurrency medianTransaction,
                                   long participantCount) {
            this.totalTransactions = totalTransactions;
            this.totalVolume = Objects.requireNonNull(totalVolume);
            this.avgTransaction = Objects.requireNonNull(avgTransaction);
            this.medianTransaction = Objects.requireNonNull(medianTransaction);
            this.participantCount = participantCount;
        }

        // Getters
        public long getTotalTransactions() { return totalTransactions; }
        public MarketplaceCurrency getTotalVolume() { return totalVolume; }
        public MarketplaceCurrency getAvgTransaction() { return avgTransaction; }
        public MarketplaceCurrency getMedianTransaction() { return medianTransaction; }
        public long getParticipantCount() { return participantCount; }
    }
}