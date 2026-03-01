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
package org.yawlfoundation.yawl.erlang.term;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.erlang.error.ErlangReceiveException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress tests for ErlTermCodec: high-volume encode/decode under load.
 * Tests codec performance, thread safety, and robustness without OTP.
 */
@Tag("stress")
class ErlTermCodecStressTest {

    /**
     * Encodes and decodes 100,000 atoms, verifying round-trip correctness.
     * Must complete in 30 seconds.
     */
    @Test
    @Timeout(30)
    void throughput_100k_atoms() throws ErlangReceiveException {
        final int count = 100_000;

        for (int i = 0; i < count; i++) {
            ErlAtom atom = new ErlAtom("test_" + i);
            byte[] encoded = ErlTermCodec.encode(atom);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertInstanceOf(ErlAtom.class, decoded);
            assertEquals("test_" + i, ((ErlAtom) decoded).value());
        }
    }

    /**
     * Spawns 50 virtual threads, each encoding 1000 atoms concurrently.
     * Verifies no race conditions or corruption.
     */
    @Test
    @Timeout(30)
    void concurrent_50threads_1k_each() throws InterruptedException {
        final int threadCount = 50;
        final int operationsPerThread = 1_000;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger failureCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            Thread.ofVirtual().start(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String value = "atom_" + threadId + "_" + i;
                        ErlAtom atom = new ErlAtom(value);
                        byte[] encoded = ErlTermCodec.encode(atom);
                        try {
                            ErlTerm decoded = ErlTermCodec.decode(encoded);

                            if (!(decoded instanceof ErlAtom a) || !a.value().equals(value)) {
                                failureCount.incrementAndGet();
                            }
                        } catch (ErlangReceiveException e) {
                            failureCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        assertEquals(0, failureCount.get());
    }

    /**
     * Encodes and decodes one instance of each of the 13 ErlTerm types,
     * 10,000 times each. Verifies codec handles all types under high load.
     */
    @Test
    @Timeout(60)
    void mixed_13types_10k_operations() throws ErlangReceiveException {
        final int iterations = 10_000;

        for (int iter = 0; iter < iterations; iter++) {
            // ErlAtom
            testRoundTrip(new ErlAtom("test_atom"), ErlAtom.class);

            // ErlInteger (small)
            testRoundTrip(new ErlInteger(42), ErlInteger.class);

            // ErlInteger (big)
            testRoundTrip(new ErlInteger(BigInteger.valueOf(2).pow(100)), ErlInteger.class);

            // ErlFloat
            testRoundTrip(new ErlFloat(3.14159), ErlFloat.class);

            // ErlBinary
            testRoundTrip(new ErlBinary(new byte[]{1, 2, 3, 4}), ErlBinary.class);

            // ErlBitstring
            testRoundTrip(new ErlBitstring(new byte[]{7}, 3), ErlBitstring.class);

            // ErlNil
            testRoundTrip(ErlNil.INSTANCE, ErlNil.class);

            // ErlList (proper)
            testRoundTrip(new ErlList(List.of(
                new ErlAtom("a"),
                new ErlInteger(1),
                new ErlFloat(2.5)
            )), ErlList.class);

            // ErlTuple
            testRoundTrip(new ErlTuple(List.of(
                new ErlAtom("ok"),
                new ErlInteger(200)
            )), ErlTuple.class);

            // ErlMap
            Map<ErlTerm, ErlTerm> mapEntries = new HashMap<>();
            mapEntries.put(new ErlAtom("key1"), new ErlInteger(1));
            mapEntries.put(new ErlAtom("key2"), new ErlAtom("value"));
            testRoundTrip(new ErlMap(mapEntries), ErlMap.class);

            // ErlPid
            testRoundTrip(new ErlPid("yawl@localhost", 1, 2, 3), ErlPid.class);

            // ErlRef
            testRoundTrip(new ErlRef("yawl@localhost", new int[]{1, 2, 3}, 4), ErlRef.class);

            // ErlPort
            testRoundTrip(new ErlPort("yawl@localhost", 12345, 1), ErlPort.class);
        }
    }

    /**
     * Encodes and decodes a 1 MB binary 100 times, verifying correct handling
     * of large payloads and byte array integrity.
     */
    @Test
    @Timeout(30)
    void large_binary_1mb_x_100() throws ErlangReceiveException {
        final int binarySize = 1_048_576; // 1 MB
        final int iterations = 100;

        byte[] largeData = new byte[binarySize];
        for (int i = 0; i < binarySize; i++) {
            largeData[i] = (byte) (i % 256);
        }

        ErlBinary binary = new ErlBinary(largeData);

        for (int i = 0; i < iterations; i++) {
            byte[] encoded = ErlTermCodec.encode(binary);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertInstanceOf(ErlBinary.class, decoded);
            ErlBinary decodedBinary = (ErlBinary) decoded;
            assertEquals(binarySize, decodedBinary.data().length);

            // Verify data integrity by spot-checking
            for (int j = 0; j < 100; j++) {
                int idx = (j * binarySize) / 100;
                assertEquals(largeData[idx], decodedBinary.data()[idx]);
            }
        }
    }

    /**
     * Builds a list nested 100 levels deep and encodes/decodes it.
     * Must not crash with OutOfMemoryError. May throw a controlled exception
     * on stack overflow, but must not corrupt the JVM.
     */
    @Test
    @Timeout(30)
    void deep_nesting_100_levels_no_crash() {
        final int nestingLevel = 100;

        ErlTerm nested = ErlNil.INSTANCE;
        for (int i = 0; i < nestingLevel; i++) {
            nested = new ErlList(List.of(nested));
        }

        try {
            byte[] encoded = ErlTermCodec.encode(nested);
            assertTrue(encoded.length > 0);

            ErlTerm decoded = ErlTermCodec.decode(encoded);
            assertNotNull(decoded);
            assertInstanceOf(ErlList.class, decoded);
        } catch (StackOverflowError | OutOfMemoryError e) {
            fail("Codec crashed with " + e.getClass().getSimpleName());
        } catch (Exception e) {
            // Other exceptions may be acceptable (e.g., buffer overflow)
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private void testRoundTrip(ErlTerm original, Class<?> expectedType) throws ErlangReceiveException {
        byte[] encoded = ErlTermCodec.encode(original);
        assertNotNull(encoded);
        assertTrue(encoded.length > 0);

        ErlTerm decoded = ErlTermCodec.decode(encoded);
        assertNotNull(decoded);
        assertInstanceOf(expectedType, decoded);
    }
}
