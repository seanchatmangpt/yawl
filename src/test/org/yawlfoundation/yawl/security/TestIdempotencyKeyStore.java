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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IdempotencyKeyStore.
 */
@DisplayName("Idempotency Key Store Tests")
class TestIdempotencyKeyStore {

    private IdempotencyKeyStore store;

    @BeforeEach
    void setUp() {
        store = new IdempotencyKeyStore(24, TimeUnit.HOURS);
    }

    @Test
    @DisplayName("Store and retrieve cached response")
    void testStoreAndRetrieve() {
        String key = "request-12345";
        String response = "{\"caseId\": \"case-001\"}";

        store.store(key, response, 201);

        Optional<IdempotencyKeyStore.CachedResponse> cached = store.retrieve(key);
        assertTrue(cached.isPresent());
        assertEquals(response, cached.get().response());
        assertEquals(201, cached.get().statusCode());
    }

    @Test
    @DisplayName("Null key rejected")
    void testNullKeyRejected() {
        assertThrows(NullPointerException.class,
                () -> store.store(null, "response", 200));

        assertThrows(NullPointerException.class,
                () -> store.retrieve(null));

        assertThrows(NullPointerException.class,
                () -> store.exists(null));
    }

    @Test
    @DisplayName("Empty key rejected")
    void testEmptyKeyRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> store.store("", "response", 200));

        assertThrows(IllegalArgumentException.class,
                () -> store.retrieve(""));
    }

    @Test
    @DisplayName("Key too long rejected")
    void testKeyTooLong() {
        String longKey = "k".repeat(513);
        assertThrows(IllegalArgumentException.class,
                () -> store.store(longKey, "response", 200));
    }

    @Test
    @DisplayName("Exists returns true for cached key")
    void testExists() {
        String key = "request-67890";
        assertFalse(store.exists(key));

        store.store(key, "response", 200);
        assertTrue(store.exists(key));
    }

    @Test
    @DisplayName("Remove deletes cached response")
    void testRemove() {
        String key = "request-remove-test";
        store.store(key, "response", 200);

        assertTrue(store.remove(key));
        assertFalse(store.exists(key));
        assertFalse(store.remove(key));
    }

    @Test
    @DisplayName("Clear removes all entries")
    void testClear() {
        store.store("key1", "response1", 200);
        store.store("key2", "response2", 201);
        store.store("key3", "response3", 202);

        assertEquals(3, store.size());
        store.clear();
        assertEquals(0, store.size());
    }

    @Test
    @DisplayName("Invalid HTTP status code rejected")
    void testInvalidStatusCode() {
        String key = "test-key";
        String response = "test response";

        assertThrows(IllegalArgumentException.class,
                () -> store.store(key, response, 99)); // Too low

        assertThrows(IllegalArgumentException.class,
                () -> store.store(key, response, 600)); // Too high
    }

    @Test
    @DisplayName("Multiple responses stored separately")
    void testMultipleResponses() {
        store.store("key1", "response1", 200);
        store.store("key2", "response2", 201);
        store.store("key3", "response3", 202);

        assertEquals(3, store.size());

        assertEquals("response1", store.retrieve("key1").get().response());
        assertEquals(200, store.retrieve("key1").get().statusCode());

        assertEquals("response3", store.retrieve("key3").get().response());
        assertEquals(202, store.retrieve("key3").get().statusCode());
    }

    @Test
    @DisplayName("TTL configuration")
    void testTtlConfiguration() {
        IdempotencyKeyStore shortTtlStore = new IdempotencyKeyStore(1, TimeUnit.SECONDS);
        assertEquals(TimeUnit.SECONDS.toMillis(1), shortTtlStore.getTtlMillis());
        assertEquals(TimeUnit.MINUTES.toMillis(0), shortTtlStore.getTtlMinutes()); // Truncates to 0
    }

    @Test
    @DisplayName("Idempotent duplicate detection")
    void testIdempotentDuplicateDetection() {
        String key = "duplicate-request-123";

        // First request
        assertFalse(store.exists(key));
        store.store(key, "case-created", 201);
        assertTrue(store.exists(key));

        // Duplicate request - should find cached response
        assertTrue(store.exists(key));
        Optional<IdempotencyKeyStore.CachedResponse> cached = store.retrieve(key);
        assertTrue(cached.isPresent());
        assertEquals("case-created", cached.get().response());
    }

    @Test
    @DisplayName("Overwrite existing key")
    void testOverwriteKey() {
        String key = "request-overwrite";

        store.store(key, "response-v1", 200);
        assertEquals("response-v1", store.retrieve(key).get().response());

        store.store(key, "response-v2", 200);
        assertEquals("response-v2", store.retrieve(key).get().response());
    }
}
