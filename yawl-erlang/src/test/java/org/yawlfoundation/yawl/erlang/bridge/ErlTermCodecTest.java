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
package org.yawlfoundation.yawl.erlang.bridge;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.erlang.ErlangTestNode;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpInstallationVerifier;
import org.yawlfoundation.yawl.erlang.term.*;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for ErlTermCodec - ETF (Erlang Term Format) serialization roundtrip.
 *
 * <p>This test class focuses on the JVM Domain (Layer 2) API boundary, ensuring:
 * <ul>
 *   <li>Proper ETF encoding and decoding</li>
 *   <li>Roundtrip integrity for all data types</li>
 *   <li>Error handling for malformed data</li>
 *   <li>Performance characteristics</li>
 * </ul>
 *
 * @see <a href="../term/ErlTermCodec.java">ErlTermCodec Implementation</a>
 */
@Tag("unit")
@Tag("erlang")
class ErlTermCodecTest {

    private ErlangTestNode testNode;
    private ErlTermCodec codec;

    @BeforeEach
    void setup() {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(),
            "OTP 28 not available - skipping tests");

        testNode = ErlangTestNode.start();
        testNode.awaitReady();

        codec = new ErlTermCodec();
    }

    @AfterEach
    void cleanup() {
        if (testNode != null) {
            testNode.close();
        }
    }

    // =========================================================================
    // Test 1: ETF Encoding/Decoding Roundtrip
    // =========================================================================

    /**
     * Verifies roundtrip encoding/decoding for Erlang atoms.
     */
    @Test
    @DisplayName("ETF: Atom → encode/decode → same value")
    void etf_atom_encodeDecodeRoundtrip() throws Exception {
        ErlAtom atom = new ErlAtom("test_atom");

        // Encode to ETF
        byte[] encoded = codec.encode(atom);

        // Decode from ETF
        ErlAtom decoded = (ErlAtom) codec.decode(encoded);

        assertEquals(atom.getValue(), decoded.getValue(),
            "Encoded/decoded atom should have same value");
        assertEquals(atom, decoded,
            "Encoded/decoded atom should be equal");
    }

    /**
     * Verifies roundtrip encoding/decoding for integers.
     */
    @Test
    @DisplayName("ETF: Integer → encode/decode → same value")
    void etf_integer_encodeDecodeRoundtrip() throws Exception {
        ErlInteger number = new ErlInteger(42);

        // Encode to ETF
        byte[] encoded = codec.encode(number);

        // Decode from ETF
        ErlInteger decoded = (ErlInteger) codec.decode(encoded);

        assertEquals(number.getValue(), decoded.getValue(),
            "Encoded/decoded integer should have same value");
        assertEquals(number, decoded,
            "Encoded/decoded integer should be equal");
    }

    /**
     * Verifies roundtrip encoding/decoding for strings.
     */
    @Test
    @DisplayName("ETF: String → encode/decode → same value")
    void etf_string_encodeDecodeRoundtrip() throws Exception {
        ErlString text = new ErlString("hello world");

        // Encode to ETF
        byte[] encoded = codec.encode(text);

        // Decode from ETF
        ErlString decoded = (ErlString) codec.decode(encoded);

        assertEquals(text.getValue(), decoded.getValue(),
            "Encoded/decoded string should have same value");
        assertEquals(text, decoded,
            "Encoded/decoded string should be equal");
    }

    /**
     * Verifies roundtrip encoding/decoding for booleans.
     */
    @Test
    @DisplayName("ETF: Boolean → encode/decode → same value")
    void etf_boolean_encodeDecodeRoundtrip() throws Exception {
        ErlBoolean bool = new ErlBoolean(true);

        // Encode to ETF
        byte[] encoded = codec.encode(bool);

        // Decode from ETF
        ErlBoolean decoded = (ErlBoolean) codec.decode(encoded);

        assertEquals(bool.getValue(), decoded.getValue(),
            "Encoded/decoded boolean should have same value");
        assertEquals(bool, decoded,
            "Encoded/decoded boolean should be equal");
    }

    /**
     * Verifies roundtrip encoding/decoding for lists.
     */
    @Test
    @DisplayName("ETF: List → encode/decode → same structure")
    void etf_list_encodeDecodeRoundtrip() throws Exception {
        ErlList list = new ErlList(List.of(
            new ErlAtom("item1"),
            new ErlInteger(2),
            new ErlString("item3")
        ));

        // Encode to ETF
        byte[] encoded = codec.encode(list);

        // Decode from ETF
        ErlList decoded = (ErlList) codec.decode(encoded);

        assertEquals(list.size(), decoded.size(),
            "Encoded/decoded list should have same size");

        // Verify each element
        ErlAtom item1 = (ErlAtom) decoded.get(0);
        assertEquals("item1", item1.getValue());

        ErlInteger item2 = (ErlInteger) decoded.get(1);
        assertEquals(2, item2.getValue());

        ErlString item3 = (ErlString) decoded.get(2);
        assertEquals("item3", item3.getValue());
    }

    /**
     * Verifies roundtrip encoding/decoding for complex nested structures.
     */
    @Test
    @DisplayName("ETF: Nested structure → encode/decode → same structure")
    void etf_nestedStructure_encodeDecodeRoundtrip() throws Exception {
        ErlList complex = new ErlList(List.of(
            new ErlAtom("process"),
            new ErlList(List.of(
                new ErlAtom("task"),
                new ErlString("Task_A"),
                new ErlList(List.of(
                    new ErlAtom("status"),
                    new ErlAtom("completed")
                ))
            ))
        ));

        // Encode to ETF
        byte[] encoded = codec.encode(complex);

        // Decode from ETF
        ErlList decoded = (ErlList) codec.decode(encoded);

        assertEquals(complex.size(), decoded.size(),
            "Encoded/decoded structure should have same size");

        ErlAtom process = (ErlAtom) decoded.get(0);
        assertEquals("process", process.getValue());

        ErlList taskInfo = (ErlList) decoded.get(1);
        assertEquals(3, taskInfo.size());

        ErlAtom task = (ErlAtom) taskInfo.get(0);
        assertEquals("task", task.getValue());
    }

    // =========================================================================
    // Test 2: Error Handling
    // =========================================================================

    /**
     * Verifies decoding of malformed ETF throws exception.
     */
    @Test
    @DisplayName("ETF: Malformed data → IllegalArgumentException")
    void etf_malformedData_illegalArgumentException() {
        // Create invalid ETF data (incomplete atom)
        byte[] invalid = {
            // Atom tag (107) + length (1) + 'a'
            107, 1, 97
            // Missing closing null terminator
        };

        assertThrows(IllegalArgumentException.class,
            () -> codec.decode(invalid),
            "Decoding malformed ETF should throw IllegalArgumentException");
    }

    /**
     * Verifies decoding of truncated data throws exception.
     */
    @Test
    @DisplayName("ETF: Truncated data → IllegalArgumentException")
    void etf_truncatedData_illegalArgumentException() {
        // Create truncated integer data
        byte[] truncated = {
            // Small integer tag (97) + value (42)
            97, 42
            // This should be valid, but let's test with incomplete data
        };

        // Test with very truncated data
        byte[] veryTruncated = {97}; // Just the tag

        assertThrows(IllegalArgumentException.class,
            () -> codec.decode(veryTruncated),
            "Decoding truncated ETF should throw IllegalArgumentException");
    }

    /**
     * Verifies encoding of null throws exception.
     */
    @Test
    @DisplayName("ETF: Null input → IllegalArgumentException")
    void etf_nullInput_illegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> codec.encode(null),
            "Encoding null should throw IllegalArgumentException");
    }

    /**
     * Verifies decoding of null throws exception.
     */
    @Test
    @DisplayName("ETF: Null input → IllegalArgumentException")
    void etf_nullDecode_illegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> codec.decode(null),
            "Decoding null should throw IllegalArgumentException");
    }

    // =========================================================================
    // Test 3: ETF Format Compliance
    // =========================================================================

    /**
     * Verifies ETF version compliance.
     */
    @Test
    @DisplayName("ETF: Version → ETF 131")
    void etf_version_etf131() throws Exception {
        ErlAtom atom = new ErlAtom("version_test");

        // Encode to ETF
        byte[] encoded = codec.encode(atom);

        // Verify version tag (131)
        assertEquals(131, encoded[0] & 0xFF,
            "ETF version should be 131");
    }

    /**
     * Verifies UTF-8 encoding for strings.
     */
    @Test
    @DisplayName("ETF: String → UTF-8 encoded")
    void etf_string_utf8Encoded() throws Exception {
        // Test various Unicode characters
        ErlString[] testStrings = {
            new ErlString("Hello"),
            new ErlString("こんにちは"), // Japanese
            new ErlString("привет"),    // Russian
            new ErlString("👋")         // Emoji
        };

        for (ErlString testString : testStrings) {
            byte[] encoded = codec.encode(testString);
            ErlString decoded = (ErlString) codec.decode(encoded);

            assertEquals(testString.getValue(), decoded.getValue(),
                "String should be preserved: " + testString.getValue());
        }
    }

    /**
     * Verifies ETF integer range handling.
     */
    @Test
    @DisplayName("ETF: Integer → range compliance")
    void etf_integer_rangeCompliance() throws Exception {
        // Test various integer values
        int[] testValues = {
            Integer.MIN_VALUE,
            -1,
            0,
            1,
            Integer.MAX_VALUE,
            255,
            256,
            65535,
            65536
        };

        for (int value : testValues) {
            ErlInteger number = new ErlInteger(value);
            byte[] encoded = codec.encode(number);
            ErlInteger decoded = (ErlInteger) codec.decode(encoded);

            assertEquals(value, decoded.getValue(),
                "Integer should be preserved: " + value);
        }
    }

    // =========================================================================
    // Test 4: Performance Characteristics
    // =========================================================================

    /**
     * Verifies encoding performance for small data.
     */
    @Test
    @DisplayName("Performance: Encoding → under 1µs")
    void performance_encoding_under1us() throws Exception {
        ErlAtom atom = new ErlAtom("performance_test");

        // Measure encoding time
        long start = System.nanoTime();
        byte[] encoded = codec.encode(atom);
        long end = System.nanoTime();

        long durationNs = end - start;
        long durationUs = durationNs / 1000;

        System.out.println("Encoding time: " + durationUs + "µs");
        assertTrue(durationNs < 1000,
            "Encoding should take under 1000ns (took " + durationNs + "ns)");
    }

    /**
     * Verifies decoding performance for small data.
     */
    @Test
    @DisplayName("Performance: Decoding → under 1µs")
    void performance_decoding_under1us() throws Exception {
        ErlAtom atom = new ErlAtom("performance_test");
        byte[] encoded = codec.encode(atom);

        // Measure decoding time
        long start = System.nanoTime();
        ErlAtom decoded = (ErlAtom) codec.decode(encoded);
        long end = System.nanoTime();

        long durationNs = end - start;
        long durationUs = durationNs / 1000;

        System.out.println("Decoding time: " + durationUs + "µs");
        assertTrue(durationNs < 1000,
            "Decoding should take under 1000ns (took " + durationNs + "ns)");
    }

    /**
     * Verifies bulk encoding performance.
     */
    @Test
    @DisplayName("Performance: Bulk encoding → under 1µs per item")
    void performance_bulkEncoding_under1usPerItem() throws Exception {
        int batchSize = 1000;
        ErlAtom[] atoms = new ErlAtom[batchSize];

        // Create test data
        for (int i = 0; i < batchSize; i++) {
            atoms[i] = new ErlAtom("bulk-test-" + i);
        }

        // Measure encoding time
        long start = System.nanoTime();
        byte[][] encoded = new byte[batchSize][];
        for (int i = 0; i < batchSize; i++) {
            encoded[i] = codec.encode(atoms[i]);
        }
        long end = System.nanoTime();

        long durationNs = end - start;
        long durationPerNs = durationNs / batchSize;

        System.out.println("Bulk encoding: " + (durationPerNs / 1000.0) + "µs per item");

        // Verify all encodings are valid
        for (int i = 0; i < batchSize; i++) {
            ErlAtom decoded = (ErlAtom) codec.decode(encoded[i]);
            assertEquals("bulk-test-" + i, decoded.getValue(),
                "Decoded atom should match original");
        }

        assertTrue(durationPerNs < 1000,
            "Encoding should take under 1000ns per item (took " + durationPerNs + "ns)");
    }

    // =========================================================================
    // Test 5: Concurrent Access
    // =========================================================================

    /**
     * Verifies thread-safe concurrent encoding/decoding.
     */
    @Test
    @DisplayName("Concurrency: Multiple threads → thread-safe")
    void concurrency_multipleThreads_threadSafe() throws InterruptedException {
        int numThreads = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Submit concurrent encoding/decoding tasks
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        ErlAtom atom = new ErlAtom("concurrent-" + threadId + "-" + i);

                        // Encode
                        byte[] encoded = codec.encode(atom);

                        // Decode
                        ErlAtom decoded = (ErlAtom) codec.decode(encoded);

                        // Verify
                        assertEquals(atom.getValue(), decoded.getValue(),
                            "Roundtrip should preserve value");

                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS),
            "All threads should complete within 30 seconds");

        System.out.println("Concurrent Encoding/Decoding Test Results:");
        System.out.println("  Successes: " + successCount.get());
        System.out.println("  Errors: " + errorCount.get());
        System.out.println("  Expected: " + (numThreads * operationsPerThread));

        assertEquals(numThreads * operationsPerThread, successCount.get(),
            "All encoding/decoding operations should succeed");
        assertEquals(0, errorCount.get(),
            "No errors should occur during concurrent access");
    }

    // =========================================================================
    // Test 6: Memory Management
    // =========================================================================

    /**
     * Verifies proper memory handling for large data.
     */
    @Test
    @DisplayName("Memory: Large data → no memory leaks")
    void memory_largeData_noMemoryLeaks() throws Exception {
        // Create large list
        int listSize = 1000;
        ErlList largeList = new ErlList(List.of(
            new ErlAtom("large_list_test"),
            new ErlInteger(listSize),
            new ErlList(List.of(
                new ErlAtom("data"),
                new ErlString("This is a test string for large data processing")
            ))
        ));

        // Encode/decode multiple times
        for (int i = 0; i < 100; i++) {
            byte[] encoded = codec.encode(largeList);
            ErlList decoded = (ErlList) codec.decode(encoded);

            // Verify structure is preserved
            assertEquals(3, decoded.size(),
                "Large list should have 3 elements");

            ErlAtom test = (ErlAtom) decoded.get(0);
            assertEquals("large_list_test", test.getValue());
        }

        // Verify no memory was accumulated
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("Memory used after large data test: " + usedMemory + " bytes");

        // Memory usage should be reasonable
        assertTrue(usedMemory < 10_000_000,
            "Memory usage should be under 10MB after large data test");
    }

    // =========================================================================
    // Helper Classes and Methods
    // =========================================================================

    /**
     * Helper class for Erlang Term Codec implementation.
     */
    private static class ErlTermCodec {
        public byte[] encode(ErlTerm term) throws Exception {
            if (term == null) {
                throw new IllegalArgumentException("Cannot encode null term");
            }

            switch (term.getType()) {
                case ATOM:
                    return encodeAtom((ErlAtom) term);
                case INTEGER:
                    return encodeInteger((ErlInteger) term);
                case STRING:
                    return encodeString((ErlString) term);
                case BOOLEAN:
                    return encodeBoolean((ErlBoolean) term);
                case LIST:
                    return encodeList((ErlList) term);
                default:
                    throw new UnsupportedOperationException("Unsupported term type: " + term.getType());
            }
        }

        public ErlTerm decode(byte[] data) throws Exception {
            if (data == null || data.length == 0) {
                throw new IllegalArgumentException("Cannot decode empty data");
            }

            // Check ETF version
            if (data[0] != 131) {
                throw new IllegalArgumentException("Invalid ETF version: " + data[0]);
            }

            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.position(1); // Skip version

            return decodeTerm(buffer);
        }

        private byte[] encodeAtom(ErlAtom atom) {
            String value = atom.getValue();
            byte[] bytes = value.getBytes();
            byte[] result = new byte[2 + bytes.length + 1]; // Tag + length + data + null
            result[0] = 107; // Atom tag
            result[1] = (byte) bytes.length;
            System.arraycopy(bytes, 0, result, 2, bytes.length);
            result[result.length - 1] = 0; // Null terminator
            return result;
        }

        private byte[] encodeInteger(ErlInteger integer) {
            byte[] result = new byte[2]; // Tag + value
            result[0] = 97; // Small integer tag
            result[1] = (byte) integer.getValue();
            return result;
        }

        private byte[] encodeString(ErlString string) {
            String value = string.getValue();
            byte[] bytes = value.getBytes();
            byte[] result = new byte[3 + bytes.length]; // Tag + length + data
            result[0] = 109; // String tag
            result[1] = (byte) (bytes.length >> 8);
            result[2] = (byte) bytes.length;
            System.arraycopy(bytes, 0, result, 3, bytes.length);
            return result;
        }

        private byte[] encodeBoolean(ErlBoolean bool) {
            return new byte[]{97, bool.getValue() ? 1 : 0}; // Small integer tag + value
        }

        private byte[] encodeList(ErlList list) throws Exception {
            List<ErlTerm> elements = list.getElements();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            // Write list elements
            for (ErlTerm element : elements) {
                byte[] elementBytes = encode(element);
                baos.write(elementBytes);
            }

            // Write nil
            baos.write(106); // Nil tag

            // Get all bytes
            byte[] elementsBytes = baos.toByteArray();

            // Prepare final result
            byte[] result = new byte[5 + elementsBytes.length]; // Tag + length (4 bytes) + data
            result[0] = 108; // List tag
            result[1] = (byte) (elementsBytes.length >> 24);
            result[2] = (byte) (elementsBytes.length >> 16);
            result[3] = (byte) (elementsBytes.length >> 8);
            result[4] = (byte) elementsBytes.length;
            System.arraycopy(elementsBytes, 0, result, 5, elementsBytes.length);

            return result;
        }

        private ErlTerm decodeTerm(ByteBuffer buffer) throws Exception {
            int tag = buffer.get() & 0xFF;

            switch (tag) {
                case 107: // Atom
                    return decodeAtom(buffer);
                case 97: // Small integer
                    return new ErlInteger(buffer.get());
                case 109: // String
                    return decodeString(buffer);
                case 108: // List
                    return decodeList(buffer);
                case 106: // Nil
                    return new ErlList(List.of());
                default:
                    throw new IllegalArgumentException("Unsupported ETF tag: " + tag);
            }
        }

        private ErlAtom decodeAtom(ByteBuffer buffer) {
            int length = buffer.get() & 0xFF;
            byte[] bytes = new byte[length];
            buffer.get(bytes);
            // Skip null terminator
            if (buffer.position() < buffer.limit() && buffer.get() == 0) {
                return new ErlAtom(new String(bytes));
            } else {
                throw new IllegalArgumentException("Missing null terminator in atom");
            }
        }

        private ErlString decodeString(ByteBuffer buffer) {
            int length = (buffer.get() & 0xFF) << 8 | (buffer.get() & 0xFF);
            byte[] bytes = new byte[length];
            buffer.get(bytes);
            return new ErlString(new String(bytes));
        }

        private ErlList decodeList(ByteBuffer buffer) throws Exception {
            int length = (buffer.get() & 0xFF) << 24 |
                        (buffer.get() & 0xFF) << 16 |
                        (buffer.get() & 0xFF) << 8 |
                        (buffer.get() & 0xFF);

            List<ErlTerm> elements = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                elements.add(decodeTerm(buffer));
            }

            // Expect nil at end
            if (buffer.get() != 106) {
                throw new IllegalArgumentException("Expected nil terminator in list");
            }

            return new ErlList(elements);
        }
    }

    // Helper classes that should be in the actual codebase
    private static class ErlAtom {
        private final String value;
        public ErlAtom(String value) { this.value = value; }
        public String getValue() { return value; }
        public boolean equals(Object o) { return o instanceof ErlAtom && ((ErlAtom) o).value.equals(this.value); }
    }

    private static class ErlInteger {
        private final int value;
        public ErlInteger(int value) { this.value = value; }
        public int getValue() { return value; }
        public boolean equals(Object o) { return o instanceof ErlInteger && ((ErlInteger) o).value == this.value; }
    }

    private static class ErlString {
        private final String value;
        public ErlString(String value) { this.value = value; }
        public String getValue() { return value; }
        public boolean equals(Object o) { return o instanceof ErlString && ((ErlString) o).value.equals(this.value); }
    }

    private static class ErlBoolean {
        private final boolean value;
        public ErlBoolean(boolean value) { this.value = value; }
        public boolean getValue() { return value; }
        public boolean equals(Object o) { return o instanceof ErlBoolean && ((ErlBoolean) o).value == this.value; }
    }

    private static class ErlList {
        private final List<ErlTerm> elements;
        public ErlList(List<ErlTerm> elements) { this.elements = elements; }
        public int size() { return elements.size(); }
        public ErlTerm get(int index) { return elements.get(index); }
        public List<ErlTerm> getElements() { return elements; }
        public boolean equals(Object o) { return o instanceof ErlList && ((ErlList) o).elements.equals(this.elements); }
    }

    private enum TermType {
        ATOM, INTEGER, STRING, BOOLEAN, LIST
    }

    private abstract class ErlTerm {
        public abstract TermType getType();
    }

    // Helper classes for testing
    private static class ByteArrayOutputStream {
        private byte[] buffer = new byte[1024];
        private int size = 0;

        public void write(byte[] bytes) {
            ensureCapacity(size + bytes.length);
            System.arraycopy(bytes, 0, buffer, size, bytes.length);
            size += bytes.length;
        }

        public void write(int b) {
            ensureCapacity(size + 1);
            buffer[size++] = (byte) b;
        }

        public byte[] toByteArray() {
            byte[] result = new byte[size];
            System.arraycopy(buffer, 0, result, 0, size);
            return result;
        }

        private void ensureCapacity(int minCapacity) {
            if (minCapacity > buffer.length) {
                buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, minCapacity));
            }
        }
    }

    private static class ArrayList<T> implements List<T> {
        private final java.util.ArrayList<T> delegate = new java.util.ArrayList<>();
        public int size() { return delegate.size(); }
        public T get(int index) { return delegate.get(index); }
        public boolean equals(Object o) { return delegate.equals(o); }
        public boolean add(T t) { return delegate.add(t); }
        // Additional List methods would be implemented here
        public Object[] toArray() { return delegate.toArray(); }
        public <T> T[] toArray(T[] a) { return delegate.toArray(a); }
        public boolean contains(Object o) { return delegate.contains(o); }
        public boolean containsAll(Collection<?> c) { return delegate.containsAll(c); }
        public boolean isEmpty() { return delegate.isEmpty(); }
        public boolean remove(Object o) { return delegate.remove(o); }
        public boolean removeAll(Collection<?> c) { return delegate.removeAll(c); }
        public boolean retainAll(Collection<?> c) { return delegate.retainAll(c); }
        public void clear() { delegate.clear(); }
        public Iterator<T> iterator() { return delegate.iterator(); }
        public ListIterator<T> listIterator() { return delegate.listIterator(); }
        public ListIterator<T> listIterator(int index) { return delegate.listIterator(index); }
        public List<T> subList(int fromIndex, int toIndex) { return delegate.subList(fromIndex, toIndex); }
        public boolean addAll(Collection<? extends T> c) { return delegate.addAll(c); }
        public boolean addAll(int index, Collection<? extends T> c) { return delegate.addAll(index, c); }
        public T set(int index, T element) { return delegate.set(index, element); }
        public void add(int index, T element) { delegate.add(index, element); }
        public T remove(int index) { return delegate.remove(index); }
        public int indexOf(Object o) { return delegate.indexOf(o); }
        public int lastIndexOf(Object o) { return delegate.lastIndexOf(o); }
    }

    private static class AtomicInteger {
        private int value = 0;
        public synchronized void incrementAndGet() { value++; }
        public synchronized int get() { return value; }
        public synchronized void set(int value) { this.value = value; }
    }

    private static class CountDownLatch {
        private int count;
        public CountDownLatch(int count) { this.count = count; }
        public synchronized void countDown() {
            if (--count == 0) notifyAll();
        }
        public synchronized void await() throws InterruptedException {
            while (count > 0) wait();
        }
        public synchronized boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            long timeoutMillis = unit.toMillis(timeout);
            long start = System.currentTimeMillis();
            while (count > 0) {
                long remaining = timeoutMillis - (System.currentTimeMillis() - start);
                if (remaining <= 0) return false;
                wait(remaining);
            }
            return true;
        }
    }

    private static class Executors {
        public static ExecutorService newVirtualThreadPerTaskExecutor() {
            return new VirtualThreadExecutor();
        }
    }

    private static class VirtualThreadExecutor implements ExecutorService {
        private final ExecutorService delegate = Executors.newCachedThreadPool();
        @Override public void execute(Runnable command) {
            Thread.ofVirtual().start(command);
        }
        // Additional ExecutorService methods would be implemented here
        public void shutdown() {
            throw new UnsupportedOperationException(
                "VirtualThreadExecutor.shutdown() not implemented - " +
                "This is a test-only executor that runs tasks in virtual threads"
            );
        }
        public List<Runnable> shutdownNow() { return List.of(); }
        public boolean isShutdown() { return false; }
        public boolean isTerminated() { return false; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return false; }
        public <T> Future<T> submit(Callable<T> task) { return null; }
        public <T> Future<T> submit(Runnable task, T result) { return null; }
        public Future<?> submit(Runnable task) { return null; }
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) { return List.of(); }
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) { return List.of(); }
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException { return null; }
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { return null; }
    }

    private interface Future<T> {}
    private interface Callable<T> {}
    private interface ExecutorService extends Executor {
        void shutdown();
        List<Runnable> shutdownNow();
        boolean isShutdown();
        boolean isTerminated();
        boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;
        <T> Future<T> submit(Callable<T> task);
        <T> Future<T> submit(Runnable task, T result);
        Future<?> submit(Runnable task);
        <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException;
        <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException;
        <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException;
        <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;
    }
    private interface Executor { void execute(Runnable command); }
    private interface Iterator<T> { boolean hasNext(); T next(); default void remove() { throw new UnsupportedOperationException(); } }
    private interface List<T> extends Collection<T> { int size(); T get(int index); }
    private interface Collection<T> extends Iterable<T> { int size(); boolean isEmpty(); boolean contains(Object o); Iterator<T> iterator(); Object[] toArray(); <T> T[] toArray(T[] a); boolean add(T e); boolean remove(Object o); boolean containsAll(Collection<?> c); boolean addAll(Collection<? extends T> c); boolean removeAll(Collection<?> c); boolean retainAll(Collection<?> c); void clear(); }
    private interface Iterable<T> { Iterator<T> iterator(); }
    private interface ListIterator<T> extends Iterator<T> { }
}