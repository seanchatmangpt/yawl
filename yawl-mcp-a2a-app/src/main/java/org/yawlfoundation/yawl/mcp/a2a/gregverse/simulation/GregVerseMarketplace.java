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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Skill marketplace for agent-to-agent transactions in the Greg-Verse simulation.
 *
 * <p>This marketplace enables agents to discover, purchase, and sell skills
 * using a credit-based economy. It provides:</p>
 * <ul>
 *   <li>Skill discovery with query-based search</li>
 *   <li>Pricing mechanism with dynamic credit valuation</li>
 *   <li>Transaction processing with atomic transfers</li>
 *   <li>Rating system for skill quality and agent reputation</li>
 *   <li>Continuous trading simulation</li>
 * </ul>
 *
 * <h2>Economy Model</h2>
 * <p>Each agent starts with a configurable credit balance. Skills are priced
 * in credits based on complexity, demand, and seller reputation. Transactions
 * transfer credits atomically between buyer and seller.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * GregVerseMarketplace marketplace = new GregVerseMarketplace();
 *
 * // Agent lists a skill for sale
 * MarketplaceEntry entry = marketplace.listSkill(
 *     "agent-001",
 *     new SkillListing("code-review", "Expert code review", 50)
 * );
 *
 * // Another agent discovers and purchases
 * List<MarketplaceEntry> found = marketplace.discoverSkills("code review");
 * Transaction tx = marketplace.purchaseSkill("agent-002", "agent-001", entry.skillId());
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class GregVerseMarketplace {

    private static final Logger LOGGER = LoggerFactory.getLogger(GregVerseMarketplace.class);
    private static final BigDecimal MINIMUM_PRICE = new BigDecimal("1");
    private static final BigDecimal MAXIMUM_PRICE = new BigDecimal("10000");
    private static final int MAX_RATING = 5;
    private static final int MIN_RATING = 1;
    private static final long DEFAULT_STARTING_CREDITS = 1000L;

    private final Map<String, MarketplaceEntry> listings = new ConcurrentHashMap<>();
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();
    private final Map<String, AgentWallet> wallets = new ConcurrentHashMap<>();
    private final Map<String, SkillRating> ratings = new ConcurrentHashMap<>();
    private final Map<String, AgentReputation> reputations = new ConcurrentHashMap<>();

    // Performance optimization: cache active listings and skill-to-seller index
    private final List<MarketplaceEntry> activeListingsCache = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> skillToSellerIndex = new ConcurrentHashMap<>();

    private final AtomicLong transactionCounter = new AtomicLong(0);
    private final AtomicLong listingCounter = new AtomicLong(0);

    private volatile boolean simulationRunning = false;
    private volatile Thread simulationThread;

    /**
     * Record representing a skill listing in the marketplace.
     *
     * @param entryId unique identifier for this listing
     * @param skillId the skill identifier
     * @param skillName human-readable skill name
     * @param description detailed skill description
     * @param sellerAgentId the agent offering this skill
     * @param priceInCredits the asking price in credits
     * @param category skill category for filtering
     * @param tags searchable tags
     * @param listedAt timestamp when listed
     * @param active whether this listing is currently active
     */
    public record MarketplaceEntry(
        String entryId,
        String skillId,
        String skillName,
        String description,
        String sellerAgentId,
        BigDecimal priceInCredits,
        String category,
        List<String> tags,
        Instant listedAt,
        boolean active
    ) {
        /**
         * Creates a new marketplace entry with generated IDs.
         */
        public MarketplaceEntry {
            Objects.requireNonNull(entryId, "entryId must not be null");
            Objects.requireNonNull(skillId, "skillId must not be null");
            Objects.requireNonNull(skillName, "skillName must not be null");
            Objects.requireNonNull(description, "description must not be null");
            Objects.requireNonNull(sellerAgentId, "sellerAgentId must not be null");
            Objects.requireNonNull(priceInCredits, "priceInCredits must not be null");
            Objects.requireNonNull(listedAt, "listedAt must not be null");
            tags = tags != null ? List.copyOf(tags) : List.of();
        }

        /**
         * Creates a builder for constructing marketplace entries.
         *
         * @return a new builder instance
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder class for MarketplaceEntry.
         */
        public static final class Builder {
            private String entryId;
            private String skillId;
            private String skillName;
            private String description;
            private String sellerAgentId;
            private BigDecimal priceInCredits;
            private String category;
            private List<String> tags;
            private Instant listedAt;
            private boolean active = true;

            public Builder entryId(String entryId) {
                this.entryId = entryId;
                return this;
            }

            public Builder skillId(String skillId) {
                this.skillId = skillId;
                return this;
            }

            public Builder skillName(String skillName) {
                this.skillName = skillName;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder sellerAgentId(String sellerAgentId) {
                this.sellerAgentId = sellerAgentId;
                return this;
            }

            public Builder priceInCredits(BigDecimal priceInCredits) {
                this.priceInCredits = priceInCredits;
                return this;
            }

            public Builder priceInCredits(long priceInCredits) {
                this.priceInCredits = new BigDecimal(priceInCredits);
                return this;
            }

            public Builder category(String category) {
                this.category = category;
                return this;
            }

            public Builder tags(List<String> tags) {
                this.tags = tags;
                return this;
            }

            public Builder listedAt(Instant listedAt) {
                this.listedAt = listedAt;
                return this;
            }

            public Builder active(boolean active) {
                this.active = active;
                return this;
            }

            public MarketplaceEntry build() {
                if (entryId == null) {
                    entryId = UUID.randomUUID().toString();
                }
                if (listedAt == null) {
                    listedAt = Instant.now();
                }
                return new MarketplaceEntry(
                    entryId, skillId, skillName, description,
                    sellerAgentId, priceInCredits, category, tags, listedAt, active
                );
            }
        }
    }

    /**
     * Record representing a completed marketplace transaction.
     *
     * @param transactionId unique transaction identifier
     * @param buyerAgentId the purchasing agent
     * @param sellerAgentId the selling agent
     * @param skillId the skill that was traded
     * @param entryId the marketplace entry ID
     * @param priceInCredits the agreed price
     * @param completedAt timestamp of completion
     * @param status transaction status
     */
    public record Transaction(
        String transactionId,
        String buyerAgentId,
        String sellerAgentId,
        String skillId,
        String entryId,
        BigDecimal priceInCredits,
        Instant completedAt,
        TransactionStatus status
    ) {
        /**
         * Creates a new transaction with validation.
         */
        public Transaction {
            Objects.requireNonNull(transactionId, "transactionId must not be null");
            Objects.requireNonNull(buyerAgentId, "buyerAgentId must not be null");
            Objects.requireNonNull(sellerAgentId, "sellerAgentId must not be null");
            Objects.requireNonNull(skillId, "skillId must not be null");
            Objects.requireNonNull(priceInCredits, "priceInCredits must not be null");
            Objects.requireNonNull(completedAt, "completedAt must not be null");
            Objects.requireNonNull(status, "status must not be null");
        }

        /**
         * Transaction status enum.
         */
        public enum TransactionStatus {
            PENDING,
            COMPLETED,
            FAILED_INSUFFICIENT_FUNDS,
            FAILED_LISTING_INACTIVE,
            FAILED_SELF_PURCHASE,
            CANCELLED
        }
    }

    /**
     * Record representing a skill rating.
     *
     * @param skillId the skill identifier
     * @param entryId the marketplace entry ID
     * @param totalRatings total number of ratings received
     * @param averageRating the average rating (1-5)
     * @param ratingDistribution count per rating level
     */
    public record SkillRating(
        String skillId,
        String entryId,
        long totalRatings,
        BigDecimal averageRating,
        Map<Integer, Long> ratingDistribution
    ) {
        /**
         * Creates a new skill rating with computed average.
         */
        public SkillRating {
            Objects.requireNonNull(skillId, "skillId must not be null");
            Objects.requireNonNull(ratingDistribution, "ratingDistribution must not be null");
            ratingDistribution = Map.copyOf(ratingDistribution);
            averageRating = averageRating != null ? averageRating : BigDecimal.ZERO;
        }

        /**
         * Creates an empty rating for a new skill.
         *
         * @param skillId the skill identifier
         * @param entryId the marketplace entry ID
         * @return a new empty rating
         */
        public static SkillRating empty(String skillId, String entryId) {
            Map<Integer, Long> distribution = new HashMap<>();
            for (int i = MIN_RATING; i <= MAX_RATING; i++) {
                distribution.put(i, 0L);
            }
            return new SkillRating(skillId, entryId, 0L, BigDecimal.ZERO, distribution);
        }

        /**
         * Adds a new rating and returns an updated rating record.
         *
         * @param newRating the rating to add (1-5)
         * @return updated rating record
         */
        public SkillRating addRating(int newRating) {
            if (newRating < MIN_RATING || newRating > MAX_RATING) {
                throw new IllegalArgumentException(
                    "Rating must be between " + MIN_RATING + " and " + MAX_RATING
                );
            }

            Map<Integer, Long> newDistribution = new HashMap<>(ratingDistribution);
            newDistribution.merge(newRating, 1L, Long::sum);

            long newTotal = totalRatings + 1;
            BigDecimal weightedSum = BigDecimal.ZERO;
            for (Map.Entry<Integer, Long> entry : newDistribution.entrySet()) {
                weightedSum = weightedSum.add(
                    BigDecimal.valueOf(entry.getKey()).multiply(BigDecimal.valueOf(entry.getValue()))
                );
            }
            BigDecimal newAverage = weightedSum.divide(
                BigDecimal.valueOf(newTotal), 2, RoundingMode.HALF_UP
            );

            return new SkillRating(skillId, entryId, newTotal, newAverage, newDistribution);
        }
    }

    /**
     * Record representing an agent's reputation in the marketplace.
     *
     * @param agentId the agent identifier
     * @param totalSales total number of successful sales
     * @param totalPurchases total number of successful purchases
     * @totalEarnings total credits earned from sales
     * @param totalSpent total credits spent on purchases
     * @param averageSellerRating average rating as seller
     * @param trustScore computed trust score (0-100)
     */
    public record AgentReputation(
        String agentId,
        long totalSales,
        long totalPurchases,
        BigDecimal totalEarnings,
        BigDecimal totalSpent,
        BigDecimal averageSellerRating,
        int trustScore
    ) {
        /**
         * Creates a new agent reputation with defaults.
         */
        public AgentReputation {
            Objects.requireNonNull(agentId, "agentId must not be null");
            totalEarnings = totalEarnings != null ? totalEarnings : BigDecimal.ZERO;
            totalSpent = totalSpent != null ? totalSpent : BigDecimal.ZERO;
            averageSellerRating = averageSellerRating != null ? averageSellerRating : BigDecimal.ZERO;
        }

        /**
         * Creates an empty reputation for a new agent.
         *
         * @param agentId the agent identifier
         * @return a new empty reputation
         */
        public static AgentReputation empty(String agentId) {
            return new AgentReputation(
                agentId, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 50
            );
        }

        /**
         * Updates reputation after a sale.
         *
         * @param amount the sale amount
         * @return updated reputation
         */
        public AgentReputation recordSale(BigDecimal amount) {
            BigDecimal newEarnings = totalEarnings.add(amount);
            long newSales = totalSales + 1;
            int newTrustScore = computeTrustScore(newSales, totalPurchases, averageSellerRating);
            return new AgentReputation(
                agentId, newSales, totalPurchases, newEarnings, totalSpent, averageSellerRating, newTrustScore
            );
        }

        /**
         * Updates reputation after a purchase.
         *
         * @param amount the purchase amount
         * @return updated reputation
         */
        public AgentReputation recordPurchase(BigDecimal amount) {
            BigDecimal newSpent = totalSpent.add(amount);
            long newPurchases = totalPurchases + 1;
            int newTrustScore = computeTrustScore(totalSales, newPurchases, averageSellerRating);
            return new AgentReputation(
                agentId, totalSales, newPurchases, totalEarnings, newSpent, averageSellerRating, newTrustScore
            );
        }

        /**
         * Updates the average seller rating.
         *
         * @param newAverage the new average rating
         * @return updated reputation
         */
        public AgentReputation withAverageRating(BigDecimal newAverage) {
            int newTrustScore = computeTrustScore(totalSales, totalPurchases, newAverage);
            return new AgentReputation(
                agentId, totalSales, totalPurchases, totalEarnings, totalSpent, newAverage, newTrustScore
            );
        }

        private int computeTrustScore(long sales, long purchases, BigDecimal avgRating) {
            long activityScore = Math.min(20, (sales + purchases) * 2);
            int ratingScore = avgRating.multiply(BigDecimal.valueOf(16)).intValue();
            return Math.min(100, (int) activityScore + ratingScore);
        }
    }

    /**
     * Internal class representing an agent's wallet.
     */
    private static final class AgentWallet {
        private final String agentId;
        private volatile BigDecimal balance;

        AgentWallet(String agentId, long initialBalance) {
            this.agentId = agentId;
            this.balance = BigDecimal.valueOf(initialBalance);
        }

        synchronized BigDecimal getBalance() {
            return balance;
        }

        synchronized boolean withdraw(BigDecimal amount) {
            if (balance.compareTo(amount) >= 0) {
                balance = balance.subtract(amount);
                return true;
            }
            return false;
        }

        synchronized void deposit(BigDecimal amount) {
            balance = balance.add(amount);
        }
    }

    /**
     * Creates a new GregVerse marketplace with default settings.
     */
    public GregVerseMarketplace() {
        LOGGER.info("Initialized GregVerse Marketplace");
    }

    /**
     * Lists a skill for sale in the marketplace.
     *
     * @param sellerAgentId the agent offering the skill
     * @param skillName the name of the skill
     * @param description detailed description
     * @param priceInCredits the asking price in credits
     * @return the created marketplace entry
     * @throws IllegalArgumentException if parameters are invalid
     */
    public MarketplaceEntry listSkill(
            String sellerAgentId,
            String skillName,
            String description,
            long priceInCredits
    ) {
        return listSkill(sellerAgentId, skillName, description, priceInCredits, null, List.of());
    }

    /**
     * Lists a skill for sale with category and tags.
     *
     * @param sellerAgentId the agent offering the skill
     * @param skillName the name of the skill
     * @param description detailed description
     * @param priceInCredits the asking price in credits
     * @param category optional category for filtering
     * @param tags searchable tags
     * @return the created marketplace entry
     * @throws IllegalArgumentException if parameters are invalid
     */
    public MarketplaceEntry listSkill(
            String sellerAgentId,
            String skillName,
            String description,
            long priceInCredits,
            String category,
            List<String> tags
    ) {
        Objects.requireNonNull(sellerAgentId, "sellerAgentId must not be null");
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(description, "description must not be null");

        if (skillName.isBlank()) {
            throw new IllegalArgumentException("skillName must not be blank");
        }
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }

        BigDecimal price = BigDecimal.valueOf(priceInCredits);
        if (price.compareTo(MINIMUM_PRICE) < 0) {
            throw new IllegalArgumentException(
                "Price must be at least " + MINIMUM_PRICE + " credits"
            );
        }
        if (price.compareTo(MAXIMUM_PRICE) > 0) {
            throw new IllegalArgumentException(
                "Price must not exceed " + MAXIMUM_PRICE + " credits"
            );
        }

        String entryId = "entry-" + listingCounter.incrementAndGet();
        String skillId = "skill-" + UUID.randomUUID().toString().substring(0, 8);

        ensureWalletExists(sellerAgentId);
        ensureReputationExists(sellerAgentId);

        MarketplaceEntry entry = MarketplaceEntry.builder()
            .entryId(entryId)
            .skillId(skillId)
            .skillName(skillName)
            .description(description)
            .sellerAgentId(sellerAgentId)
            .priceInCredits(price)
            .category(category)
            .tags(tags != null ? tags : List.of())
            .listedAt(Instant.now())
            .active(true)
            .build();

        listings.put(entryId, entry);
        ratings.put(skillId, SkillRating.empty(skillId, entryId));

        // Update performance indices
        activeListingsCache.add(entry);
        skillToSellerIndex.put(skillId, sellerAgentId);

        LOGGER.info("Listed skill '{}' by agent {} for {} credits",
            skillName, sellerAgentId, priceInCredits);

        return entry;
    }

    /**
     * Discovers skills matching a query.
     *
     * <p>Search is performed across skill name, description, category, and tags.
     * Results are sorted by relevance (matching score) and then by rating.</p>
     *
     * @param query the search query
     * @return list of matching marketplace entries
     */
    public List<MarketplaceEntry> discoverSkills(String query) {
        if (query == null || query.isBlank()) {
            return getAllActiveListings();
        }

        String lowerQuery = query.toLowerCase().trim();
        String[] queryTerms = lowerQuery.split("\\s+");

        return listings.values().stream()
            .filter(MarketplaceEntry::active)
            .map(entry -> new SearchResult(entry, computeRelevanceScore(entry, queryTerms)))
            .filter(sr -> sr.score > 0)
            .sorted(Comparator.comparingInt(SearchResult::score).reversed()
                .thenComparing(sr -> getAverageRating(sr.entry.skillId()), Comparator.reverseOrder()))
            .map(SearchResult::entry)
            .collect(Collectors.toList());
    }

    /**
     * Discovers skills by category.
     *
     * @param category the category to filter by
     * @return list of matching entries
     */
    public List<MarketplaceEntry> discoverByCategory(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }

        String lowerCategory = category.toLowerCase();
        return listings.values().stream()
            .filter(MarketplaceEntry::active)
            .filter(e -> e.category() != null && e.category().toLowerCase().contains(lowerCategory))
            .sorted(Comparator.comparing(MarketplaceEntry::listedAt).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Discovers skills by price range.
     *
     * @param minPrice minimum price (inclusive)
     * @param maxPrice maximum price (inclusive)
     * @return list of entries within price range
     */
    public List<MarketplaceEntry> discoverByPriceRange(long minPrice, long maxPrice) {
        BigDecimal min = BigDecimal.valueOf(minPrice);
        BigDecimal max = BigDecimal.valueOf(maxPrice);

        return listings.values().stream()
            .filter(MarketplaceEntry::active)
            .filter(e -> e.priceInCredits().compareTo(min) >= 0 && e.priceInCredits().compareTo(max) <= 0)
            .sorted(Comparator.comparing(MarketplaceEntry::priceInCredits))
            .collect(Collectors.toList());
    }

    /**
     * Purchases a skill from another agent.
     *
     * @param buyerAgentId the purchasing agent
     * @param sellerAgentId the selling agent
     * @param skillId the skill to purchase
     * @return the completed transaction
     * @throws InsufficientCreditsException if buyer lacks credits
     * @throws IllegalArgumentException if parameters are invalid
     */
    public Transaction purchaseSkill(
            String buyerAgentId,
            String sellerAgentId,
            String skillId
    ) {
        Objects.requireNonNull(buyerAgentId, "buyerAgentId must not be null");
        Objects.requireNonNull(sellerAgentId, "sellerAgentId must not be null");
        Objects.requireNonNull(skillId, "skillId must not be null");

        if (buyerAgentId.equals(sellerAgentId)) {
            return recordFailedTransaction(
                buyerAgentId, sellerAgentId, skillId, null, BigDecimal.ZERO,
                Transaction.TransactionStatus.FAILED_SELF_PURCHASE
            );
        }

        Optional<MarketplaceEntry> entryOpt = listings.values().stream()
            .filter(e -> e.skillId().equals(skillId) && e.sellerAgentId().equals(sellerAgentId))
            .findFirst();

        if (entryOpt.isEmpty()) {
            throw new IllegalArgumentException(
                "No listing found for skillId: " + skillId + " by seller: " + sellerAgentId
            );
        }

        MarketplaceEntry entry = entryOpt.get();

        if (!entry.active()) {
            return recordFailedTransaction(
                buyerAgentId, sellerAgentId, skillId, entry.entryId(), entry.priceInCredits(),
                Transaction.TransactionStatus.FAILED_LISTING_INACTIVE
            );
        }

        ensureWalletExists(buyerAgentId);
        ensureReputationExists(buyerAgentId);

        AgentWallet buyerWallet = wallets.get(buyerAgentId);
        AgentWallet sellerWallet = wallets.get(sellerAgentId);
        BigDecimal price = entry.priceInCredits();

        // Use per-wallet locks with ordering to prevent deadlock
        // Order locks by agent ID to ensure consistent ordering across all transactions
        String buyerId = buyerAgentId;
        String sellerId = sellerAgentId;
        if (buyerId.compareTo(sellerId) > 0) {
            AgentWallet temp = buyerWallet;
            buyerWallet = sellerWallet;
            sellerWallet = temp;
            buyerId = sellerAgentId;
            sellerId = buyerAgentId;
        }

        synchronized (buyerWallet) {
            synchronized (sellerWallet) {
                if (!wallets.get(buyerAgentId).withdraw(price)) {
                    return recordFailedTransaction(
                        buyerAgentId, sellerAgentId, skillId, entry.entryId(), price,
                        Transaction.TransactionStatus.FAILED_INSUFFICIENT_FUNDS
                    );
                }

                wallets.get(sellerAgentId).deposit(price);
            }
        }

        String transactionId = "tx-" + transactionCounter.incrementAndGet();
        Transaction transaction = new Transaction(
            transactionId,
            buyerAgentId,
            sellerAgentId,
            skillId,
            entry.entryId(),
            price,
            Instant.now(),
            Transaction.TransactionStatus.COMPLETED
        );

        transactions.put(transactionId, transaction);

        updateReputationsAfterTransaction(buyerAgentId, sellerAgentId, price);

        LOGGER.info("Transaction completed: {} bought skill {} from {} for {} credits",
            buyerAgentId, skillId, sellerAgentId, price);

        return transaction;
    }

    /**
     * Rates a skill after purchase.
     *
     * @param skillId the skill to rate
     * @param rating the rating (1-5)
     * @return the updated skill rating
     * @throws IllegalArgumentException if skill not found or rating invalid
     */
    public SkillRating rateSkill(String skillId, int rating) {
        Objects.requireNonNull(skillId, "skillId must not be null");

        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException(
                "Rating must be between " + MIN_RATING + " and " + MAX_RATING
            );
        }

        SkillRating current = ratings.get(skillId);
        if (current == null) {
            throw new IllegalArgumentException("Skill not found: " + skillId);
        }

        SkillRating updated = current.addRating(rating);
        ratings.put(skillId, updated);

        updateSellerReputation(skillId, updated.averageRating());

        LOGGER.debug("Rated skill {} with {} stars, new average: {}",
            skillId, rating, updated.averageRating());

        return updated;
    }

    /**
     * Gets the current balance for an agent.
     *
     * @param agentId the agent identifier
     * @return the current credit balance
     */
    public BigDecimal getAgentBalance(String agentId) {
        AgentWallet wallet = wallets.get(agentId);
        return wallet != null ? wallet.getBalance() : BigDecimal.ZERO;
    }

    /**
     * Adds credits to an agent's wallet.
     *
     * @param agentId the agent identifier
     * @param amount the amount to add
     */
    public void creditAgent(String agentId, long amount) {
        ensureWalletExists(agentId);
        wallets.get(agentId).deposit(BigDecimal.valueOf(amount));
        LOGGER.debug("Credited {} credits to agent {}", amount, agentId);
    }

    /**
     * Gets the reputation for an agent.
     *
     * @param agentId the agent identifier
     * @return the agent's reputation, or empty reputation if not found
     */
    public AgentReputation getAgentReputation(String agentId) {
        return reputations.getOrDefault(agentId, AgentReputation.empty(agentId));
    }

    /**
     * Gets the rating for a skill.
     *
     * @param skillId the skill identifier
     * @return the skill rating, or empty rating if not found
     */
    public BigDecimal getAverageRating(String skillId) {
        SkillRating rating = ratings.get(skillId);
        return rating != null ? rating.averageRating() : BigDecimal.ZERO;
    }

    /**
     * Gets all transactions for an agent (as buyer or seller).
     *
     * @param agentId the agent identifier
     * @return list of transactions
     */
    public List<Transaction> getAgentTransactions(String agentId) {
        return transactions.values().stream()
            .filter(t -> t.buyerAgentId().equals(agentId) || t.sellerAgentId().equals(agentId))
            .sorted(Comparator.comparing(Transaction::completedAt).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Gets a specific marketplace entry.
     *
     * @param entryId the entry identifier
     * @return the entry, or null if not found
     */
    public MarketplaceEntry getEntry(String entryId) {
        return listings.get(entryId);
    }

    /**
     * Deactivates a marketplace listing.
     *
     * @param entryId the entry to deactivate
     * @param sellerAgentId the seller requesting deactivation
     * @return true if deactivated, false if not found or not owner
     */
    public boolean deactivateListing(String entryId, String sellerAgentId) {
        MarketplaceEntry entry = listings.get(entryId);
        if (entry == null || !entry.sellerAgentId().equals(sellerAgentId)) {
            return false;
        }

        MarketplaceEntry deactivated = MarketplaceEntry.builder()
            .entryId(entry.entryId())
            .skillId(entry.skillId())
            .skillName(entry.skillName())
            .description(entry.description())
            .sellerAgentId(entry.sellerAgentId())
            .priceInCredits(entry.priceInCredits())
            .category(entry.category())
            .tags(entry.tags())
            .listedAt(entry.listedAt())
            .active(false)
            .build();

        listings.put(entryId, deactivated);
        activeListingsCache.remove(entry);  // Remove from active cache
        LOGGER.info("Deactivated listing {} by agent {}", entryId, sellerAgentId);
        return true;
    }

    /**
     * Gets all active listings.
     *
     * @return list of active listings
     */
    /**
     * Gets all active listings efficiently using cached list.
     *
     * @return list of active marketplace entries (O(1) cached retrieval)
     */
    public List<MarketplaceEntry> getAllActiveListings() {
        return new ArrayList<>(activeListingsCache);
    }

    /**
     * Gets marketplace statistics.
     *
     * @return map of statistic name to value
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalListings", listings.size());
        stats.put("activeListings", listings.values().stream().filter(MarketplaceEntry::active).count());
        stats.put("totalTransactions", transactions.size());
        stats.put("completedTransactions", transactions.values().stream()
            .filter(t -> t.status() == Transaction.TransactionStatus.COMPLETED).count());
        stats.put("totalAgents", wallets.size());
        stats.put("totalCreditsInCirculation", wallets.values().stream()
            .map(AgentWallet::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.put("simulationRunning", simulationRunning);
        return stats;
    }

    /**
     * Runs a continuous trading simulation.
     *
     * <p>This method starts a background thread that simulates agent trading
     * activity. The simulation runs until stopped via {@link #stopSimulation()}.</p>
     *
     * @param agentIds the list of participating agent IDs
     * @param intervalMs the interval between trades in milliseconds
     * @throws IllegalStateException if simulation is already running
     */
    public void runSimulation(List<String> agentIds, long intervalMs) {
        if (simulationRunning) {
            throw new IllegalStateException("Simulation is already running");
        }

        if (agentIds == null || agentIds.size() < 2) {
            throw new IllegalArgumentException("At least 2 agents required for simulation");
        }

        agentIds.forEach(this::ensureWalletExists);
        agentIds.forEach(this::ensureReputationExists);

        simulationRunning = true;
        simulationThread = Thread.ofVirtual().name("marketplace-simulation").start(() -> {
            LOGGER.info("Started marketplace simulation with {} agents", agentIds.size());

            while (simulationRunning && !Thread.currentThread().isInterrupted()) {
                attemptRandomTrade(agentIds);

                attemptRandomListing(agentIds);

                attemptRandomRating();

                attemptRandomDeactivation(agentIds);

                sleepQuietly(intervalMs);
            }

            LOGGER.info("Marketplace simulation stopped");
        });
    }

    /**
     * Stops the running simulation.
     */
    public void stopSimulation() {
        simulationRunning = false;
        if (simulationThread != null) {
            simulationThread.interrupt();
            simulationThread = null;
        }
        LOGGER.info("Simulation stop requested");
    }

    /**
     * Checks if the simulation is currently running.
     *
     * @return true if simulation is active
     */
    public boolean isSimulationRunning() {
        return simulationRunning;
    }

    /**
     * Clears all marketplace data.
     */
    public void clear() {
        if (simulationRunning) {
            stopSimulation();
        }
        listings.clear();
        transactions.clear();
        wallets.clear();
        ratings.clear();
        reputations.clear();
        transactionCounter.set(0);
        listingCounter.set(0);
        LOGGER.info("Marketplace data cleared");
    }

    private void ensureWalletExists(String agentId) {
        wallets.computeIfAbsent(agentId, id -> new AgentWallet(id, DEFAULT_STARTING_CREDITS));
    }

    private void ensureReputationExists(String agentId) {
        reputations.computeIfAbsent(agentId, AgentReputation::empty);
    }

    private Transaction recordFailedTransaction(
            String buyerAgentId,
            String sellerAgentId,
            String skillId,
            String entryId,
            BigDecimal price,
            Transaction.TransactionStatus status
    ) {
        String transactionId = "tx-" + transactionCounter.incrementAndGet();
        Transaction transaction = new Transaction(
            transactionId, buyerAgentId, sellerAgentId, skillId, entryId, price, Instant.now(), status
        );
        transactions.put(transactionId, transaction);
        LOGGER.debug("Recorded failed transaction: {} - {}", transactionId, status);
        return transaction;
    }

    private void updateReputationsAfterTransaction(
            String buyerAgentId,
            String sellerAgentId,
            BigDecimal amount
    ) {
        AgentReputation buyerRep = reputations.getOrDefault(
            buyerAgentId, AgentReputation.empty(buyerAgentId)
        );
        reputations.put(buyerAgentId, buyerRep.recordPurchase(amount));

        AgentReputation sellerRep = reputations.getOrDefault(
            sellerAgentId, AgentReputation.empty(sellerAgentId)
        );
        reputations.put(sellerAgentId, sellerRep.recordSale(amount));
    }

    /**
     * Updates seller reputation using O(1) skill-to-seller index.
     *
     * @param skillId the skill identifier
     * @param newAverageRating the new average rating
     */
    private void updateSellerReputation(String skillId, BigDecimal newAverageRating) {
        String sellerAgentId = skillToSellerIndex.get(skillId);
        if (sellerAgentId != null) {
            AgentReputation sellerRep = reputations.get(sellerAgentId);
            if (sellerRep != null) {
                reputations.put(
                    sellerAgentId,
                    sellerRep.withAverageRating(newAverageRating)
                );
            }
        }
    }

    private int computeRelevanceScore(MarketplaceEntry entry, String[] queryTerms) {
        int score = 0;
        String lowerName = entry.skillName().toLowerCase();
        String lowerDesc = entry.description().toLowerCase();
        String lowerCategory = entry.category() != null ? entry.category().toLowerCase() : "";

        for (String term : queryTerms) {
            if (lowerName.contains(term)) {
                score += 10;
            }
            if (lowerDesc.contains(term)) {
                score += 5;
            }
            if (lowerCategory.contains(term)) {
                score += 7;
            }
            for (String tag : entry.tags()) {
                if (tag.toLowerCase().contains(term)) {
                    score += 3;
                }
            }
        }

        return score;
    }

    private void attemptRandomTrade(List<String> agentIds) {
        List<MarketplaceEntry> activeListings = getAllActiveListings();
        if (activeListings.isEmpty()) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        MarketplaceEntry entry = activeListings.get(random.nextInt(activeListings.size()));

        String seller = entry.sellerAgentId();
        List<String> potentialBuyers = agentIds.stream()
            .filter(id -> !id.equals(seller))
            .collect(Collectors.toList());

        if (potentialBuyers.isEmpty()) {
            return;
        }

        String buyer = potentialBuyers.get(random.nextInt(potentialBuyers.size()));

        AgentWallet buyerWallet = wallets.get(buyer);
        if (buyerWallet != null && buyerWallet.getBalance().compareTo(entry.priceInCredits()) >= 0) {
            purchaseSkill(buyer, seller, entry.skillId());
        }
    }

    private void attemptRandomListing(List<String> agentIds) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (random.nextDouble() < 0.3) {
            String agent = agentIds.get(random.nextInt(agentIds.size()));
            String[] skillNames = {
                "code-review", "architecture-design", "testing-strategy",
                "performance-optimization", "security-audit", "documentation",
                "api-design", "data-modeling", "devops-automation"
            };
            String[] categories = {
                "development", "design", "testing", "security", "operations"
            };

            String skillName = skillNames[random.nextInt(skillNames.length)];
            long price = random.nextLong(10, 500);

            listSkill(
                agent,
                skillName,
                "Expert " + skillName.replace("-", " ") + " services",
                price,
                categories[random.nextInt(categories.length)],
                List.of(skillName, "professional", "expert")
            );
        }
    }

    private void attemptRandomRating() {
        List<Transaction> completedTxs = transactions.values().stream()
            .filter(t -> t.status() == Transaction.TransactionStatus.COMPLETED)
            .collect(Collectors.toList());

        if (!completedTxs.isEmpty() && ThreadLocalRandom.current().nextDouble() < 0.2) {
            Transaction tx = completedTxs.get(
                ThreadLocalRandom.current().nextInt(completedTxs.size())
            );
            int rating = ThreadLocalRandom.current().nextInt(MIN_RATING, MAX_RATING + 1);
            rateSkill(tx.skillId(), rating);
        }
    }

    private void attemptRandomDeactivation(List<String> agentIds) {
        if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            List<MarketplaceEntry> activeListings = getAllActiveListings();
            if (!activeListings.isEmpty()) {
                MarketplaceEntry entry = activeListings.get(
                    ThreadLocalRandom.current().nextInt(activeListings.size())
                );
                deactivateListing(entry.entryId(), entry.sellerAgentId());
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record SearchResult(MarketplaceEntry entry, int score) {}
}
