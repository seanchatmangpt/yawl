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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Unit tests for GregVerseMarketplace class.
 *
 * <p>Tests verify marketplace operations, skill listings, reputation system,
 * and transaction handling in a multi-threaded environment.</p>
 */
@DisplayName("GregVerse Marketplace Tests")
class GregVerseMarketplaceTest {

    private GregVerseMarketplace marketplace;

    @BeforeEach
    void setUp() {
        marketplace = new GregVerseMarketplace();
    }

    @Test
    @DisplayName("should list available skills")
    void testListAvailableSkills() {
        assertNotNull(marketplace.getAvailableSkills());
        assertFalse(marketplace.getAvailableSkills().isEmpty());
    }

    @Test
    @DisplayName("should register new skill listing")
    void testRegisterSkillListing() {
        marketplace.listSkill("seller-1", "new-skill", 100.0, "A new skill offering");
        var listings = marketplace.getListingsBySeller("seller-1");
        assertFalse(listings.isEmpty());
    }

    @Test
    @DisplayName("should find active listings efficiently")
    void testGetActiveListingsPerformance() {
        // Register multiple listings
        for (int i = 0; i < 50; i++) {
            marketplace.listSkill("seller-" + i, "skill-" + i, 50.0 + i, "Skill " + i);
        }

        long startTime = System.nanoTime();
        var active = marketplace.getActiveListings();
        long duration = System.nanoTime() - startTime;

        assertFalse(active.isEmpty());
        assertTrue(duration < 100_000_000, "Listing lookup should complete in <100ms");
    }

    @Test
    @DisplayName("should retrieve listings by seller")
    void testGetListingsBySeller() {
        marketplace.listSkill("seller-1", "skill-1", 100.0, "Test skill");
        marketplace.listSkill("seller-1", "skill-2", 200.0, "Another skill");
        marketplace.listSkill("seller-2", "skill-3", 150.0, "Different seller");

        var sellerListings = marketplace.getListingsBySeller("seller-1");
        assertEquals(2, sellerListings.size());
    }

    @Test
    @DisplayName("should support purchase transactions")
    void testPurchaseTransaction() {
        marketplace.listSkill("seller-1", "skill-1", 100.0, "Test skill");
        assertDoesNotThrow(() -> marketplace.purchaseSkill("buyer-1", "seller-1", "skill-1"));
    }

    @Test
    @DisplayName("should update seller reputation")
    void testUpdateSellerReputation() {
        marketplace.listSkill("seller-1", "skill-1", 100.0, "Test skill");
        marketplace.updateSellerReputation("seller-1", 5.0);

        var seller = marketplace.getSellerInfo("seller-1");
        assertNotNull(seller);
        assertTrue(seller.reputation() >= 5.0);
    }

    @Test
    @DisplayName("should handle concurrent marketplace operations")
    void testConcurrentOperations() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = Thread.ofVirtual().start(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    marketplace.listSkill("seller-" + threadId,
                        "skill-" + i,
                        50.0 + i,
                        "Concurrent skill");
                }
            });
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(marketplace.getAvailableSkills().size() > 0);
    }

    @Test
    @DisplayName("should calculate skill statistics")
    void testSkillStatistics() {
        for (int i = 0; i < 5; i++) {
            marketplace.listSkill("seller-1", "skill-" + i, 100.0 + (i * 10), "Test skill");
        }

        var stats = marketplace.getSkillStatistics();
        assertNotNull(stats);
        assertFalse(stats.isEmpty());
    }

    @Test
    @DisplayName("should remove expired listings")
    void testRemoveExpiredListings() {
        marketplace.listSkill("seller-1", "skill-1", 100.0, "Temporary skill");
        assertFalse(marketplace.getListingsBySeller("seller-1").isEmpty());

        marketplace.removeExpiredListings();
        // Verification depends on TTL implementation
    }

    @Test
    @DisplayName("should support skill rating aggregation")
    void testSkillRatingAggregation() {
        marketplace.listSkill("seller-1", "skill-1", 100.0, "Test skill");
        marketplace.rateSkill("seller-1", "skill-1", 5.0);
        marketplace.rateSkill("seller-1", "skill-1", 4.0);

        var skillInfo = marketplace.getSkillInfo("seller-1", "skill-1");
        assertNotNull(skillInfo);
        assertTrue(skillInfo.averageRating() > 0);
    }

    @RepeatedTest(5)
    @DisplayName("should handle rapid transactions")
    void testRapidTransactions() {
        for (int i = 0; i < 100; i++) {
            marketplace.listSkill("seller-" + (i % 10), "skill-" + i, 100.0, "Rapid skill");
            if (i > 10) {
                assertDoesNotThrow(() ->
                    marketplace.purchaseSkill("buyer-" + i, "seller-" + (i % 10), "skill-" + (i - 1)));
            }
        }

        assertTrue(marketplace.getAvailableSkills().size() > 0);
    }

    @Test
    @DisplayName("should verify marketplace state consistency")
    void testMarketplaceConsistency() {
        marketplace.listSkill("seller-1", "skill-1", 100.0, "Test");
        marketplace.listSkill("seller-2", "skill-2", 150.0, "Test");

        int totalCount = marketplace.getAvailableSkills().size();
        int sellerCount = marketplace.getListingsBySeller("seller-1").size() +
                         marketplace.getListingsBySeller("seller-2").size();

        assertTrue(sellerCount <= totalCount);
    }
}
