package org.yawlfoundation.yawl.bridge.erlang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

/**
 * Comprehensive integration tests for Erlang term encoding and decoding
 *
 * Tests all Erlang term types including:
 * - Atoms
 * - Integers and floats
 * - Binaries
 * - Lists and strings
 * - Tuples
 * - Maps
 * - Nested structures
 * - Error handling
 */
class ErlangTermEncodingTest {

    @Test
    @DisplayName("ErlAtom Encoding and Decoding")
    void testErlAtomEncodingDecoding() throws ErlangException {
        ErlAtom original = ErlAtom.of("test_atom");
        byte[] encoded = original.encodeETF();
        ErlAtom decoded = (ErlAtom) ErlTerm.decodeETF(encoded);

        assertEquals(original.getValue(), decoded.getValue());
        assertEquals(original.asString(), decoded.asString());
    }

    @Test
    @DisplayName("ErlAtom Special Values")
    void testErlAtomSpecialValues() throws ErlangException {
        // Test special atoms
        ErlAtom[] specialAtoms = {
            ErlAtom.of("true"),
            ErlAtom.of("false"),
            ErlAtom.of("ok"),
            ErlAtom.of("error"),
            ErlAtom.of("undefined"),
            ErlAtom.of("null")
        };

        for (ErlAtom original : specialAtoms) {
            byte[] encoded = original.encodeETF();
            ErlAtom decoded = (ErlAtom) ErlTerm.decodeETF(encoded);
            assertEquals(original.getValue(), decoded.getValue());
        }
    }

    @Test
    @DisplayName("ErlLong Encoding and Decoding")
    void testErlLongEncodingDecoding() throws ErlangException {
        // Test various integer values
        long[] values = {
            0L,
            1L,
            -1L,
            Long.MAX_VALUE,
            Long.MIN_VALUE,
            42L
        };

        for (long value : values) {
            ErlLong original = ErlLong.of(value);
            byte[] encoded = original.encodeETF();
            ErlLong decoded = (ErlLong) ErlTerm.decodeETF(encoded);

            assertEquals(original.getValue(), decoded.getValue());
            assertEquals(original.asString(), decoded.asString());
        }
    }

    @Test
    @DisplayName("ErlDouble Encoding and Decoding")
    void testErlDoubleEncodingDecoding() throws ErlangException {
        double[] values = {
            0.0,
            1.0,
            -1.0,
            3.14159,
            Double.MAX_VALUE,
            Double.MIN_VALUE,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        };

        for (double value : values) {
            ErlDouble original = ErlDouble.of(value);
            byte[] encoded = original.encodeETF();
            ErlDouble decoded = (ErlDouble) ErlTerm.decodeETF(encoded);

            assertEquals(original.getValue(), decoded.getValue());
            assertEquals(original.asString(), decoded.asString());
        }
    }

    @Test
    @DisplayName("ErlBinary Encoding and Decoding")
    void testErlBinaryEncodingDecoding() throws ErlangException {
        // Test different binary types
        byte[][] testData = {
            "Hello".getBytes(),
            new byte[]{0x00, 0x01, 0x02, 0x03},
            new byte[0], // Empty binary
            new byte[256] // Large binary
        };

        for (byte[] data : testData) {
            ErlBinary original = ErlBinary.of(data);
            byte[] encoded = original.encodeETF();
            ErlBinary decoded = (ErlBinary) ErlTerm.decodeETF(encoded);

            assertArrayEquals(original.getBytes(), decoded.getBytes());
            assertEquals(original.asString(), decoded.asString());
        }
    }

    @Test
    @DisplayName("ErlNil Encoding and Decoding")
    void testErlNilEncodingDecoding() throws ErlangException {
        ErlNil original = ErlNil.nil();
        byte[] encoded = original.encodeETF();
        ErlNil decoded = (ErlNil) ErlTerm.decodeETF(encoded);

        assertSame(original, decoded); // Should be the same instance (singleton)
        assertEquals(original.asString(), decoded.asString());
    }

    @Test
    @DisplayName("ErlList Encoding and Decoding")
    void testErlListEncodingDecoding() throws ErlangException {
        // Test various list types
        ErlList[] testLists = {
            // Empty list
            ErlList.of(),
            // Single element list
            ErlList.of(ErlAtom.of("single")),
            // Multiple element list
            ErlList.of(
                ErlAtom.of("a"),
                ErlLong.of(1),
                ErlDouble.of(2.5),
                ErlBinary.of("test".getBytes())
            ),
            // Nested list
            ErlList.of(
                ErlList.of(ErlAtom.of("nested")),
                ErlList.of(ErlLong.of(1), ErlLong.of(2))
            )
        };

        for (ErlList original : testLists) {
            byte[] encoded = original.encodeETF();
            ErlList decoded = (ErlList) ErlTerm.decodeETF(encoded);

            assertEquals(original.getElements().size(), decoded.getElements().size());
            assertEquals(original.asString(), decoded.asString());
        }
    }

    @Test
    @DisplayName("ErlList String Conversion")
    void testErlListStringConversion() throws ErlangException {
        // Test string from char list
        ErlString charList = new ErlString("hello");
        ErlList list = ErlList.of(
            ErlAtom.of("h"),
            ErlAtom.of("e"),
            ErlAtom.of("l"),
            ErlAtom.of("l"),
            ErlAtom.of("o")
        );

        assertEquals(list.asString(), charList.asString());
    }

    @Test
    @DisplayName("ErlTuple Encoding and Decoding")
    void testErlTupleEncodingDecoding() throws ErlangException {
        // Test various tuple types
        ErlTuple[] testTuples = {
            // Empty tuple
            ErlTuple.of(),
            // Single element tuple
            ErlTuple.of(ErlAtom.of("single")),
            // Multiple element tuple
            ErlTuple.of(
                ErlAtom.of("ok"),
                ErlLong.of(42),
                ErlList.of(ErlAtom.of("item1"), ErlAtom.of("item2"))
            ),
            // Nested tuple
            ErlTuple.of(
                ErlTuple.of(ErlAtom.of("nested")),
                ErlList.of(ErlLong.of(1), ErlLong.of(2))
            )
        };

        for (ErlTuple original : testTuples) {
            byte[] encoded = original.encodeETF();
            ErlTuple decoded = (ErlTuple) ErlTerm.decodeETF(encoded);

            assertEquals(original.getElements().size(), decoded.getElements().size());
            assertEquals(original.asString(), decoded.asString());
        }
    }

    @Test
    @DisplayName("ErlMap Encoding and Decoding")
    void testErlMapEncodingDecoding() throws ErlangException {
        // Test various map types
        Map<ErlTerm, ErlTerm>[] testMaps = new Map[]{
            // Empty map
            Map.of(),
            // Simple map
            Map.of(
                ErlAtom.of("name"), ErlAtom.of("John"),
                ErlAtom.of("age"), ErlLong.of(30)
            ),
            // Complex map with nested types
            Map.of(
                ErlAtom.of("data"), ErlList.of(
                    ErlAtom.of("item1"),
                    ErlLong.of(1),
                    ErlBinary.of(new byte[]{1, 2, 3})
                ),
                ErlAtom.of("metadata"), ErlTuple.of(
                    ErlAtom.of("timestamp"),
                    ErlDouble.of(System.currentTimeMillis() / 1000.0)
                )
            )
        };

        for (Map<ErlTerm, ErlTerm> mapData : testMaps) {
            ErlMap original = ErlMap.of(mapData);
            byte[] encoded = original.encodeETF();
            ErlMap decoded = (ErlMap) ErlTerm.decodeETF(encoded);

            assertEquals(original.size(), decoded.size());
            assertEquals(original.asString(), decoded.asString());

            // Verify all key-value pairs
            for (Map.Entry<ErlTerm, ErlTerm> entry : mapData.entrySet()) {
                ErlTerm decodedValue = decoded.get(entry.getKey());
                assertNotNull(decodedValue);
                assertEquals(entry.getValue().asString(), decodedValue.asString());
            }
        }
    }

    @Test
    @DisplayName("Complex Nested Structure")
    void testComplexNestedStructure() throws ErlangException {
        // Create a complex nested structure like a real Erlang response
        ErlTuple complex = ErlTuple.of(
            ErlAtom.of("result"),
            ErlLong.of(200),
            ErlMap.of(
                ErlAtom.of("data"),
                ErlList.of(
                    ErlAtom.of("item1"),
                    ErlLong.of(1),
                    ErlBinary.of(new byte[]{1, 2, 3})
                )
            ),
            ErlMap.of(
                ErlAtom.of("metadata"),
                ErlTuple.of(
                    ErlAtom.of("timestamp"),
                    ErlDouble.of(System.currentTimeMillis() / 1000.0)
                )
            )
        );

        byte[] encoded = complex.encodeETF();
        ErlTuple decoded = (ErlTuple) ErlTerm.decodeETF(encoded);

        assertEquals(complex.asString(), decoded.asString());
        assertEquals(complex.type(), decoded.type());
    }

    @Test
    @DisplayName("Round Trip Consistency")
    void testRoundTripConsistency() throws ErlangException {
        // Test that encode->decode preserves exact values
        ErlTerm[] terms = {
            ErlAtom.of("test"),
            ErlLong.of(Integer.MAX_VALUE),
            ErlLong.of(Long.MAX_VALUE),
            ErlDouble.of(Double.MAX_VALUE),
            ErlBinary.of("UTF-8 string".getBytes()),
            ErlList.of(ErlAtom.of("nested"), ErlLong.of(123)),
            ErlMap.of(ErlAtom.of("key"), ErlLong.of(42))
        };

        for (ErlTerm original : terms) {
            byte[] encoded = original.encodeETF();
            ErlTerm decoded = ErlTerm.decodeETF(encoded);

            assertEquals(original.type(), decoded.type());
            assertEquals(original.asString(), decoded.asString());
        }
    }

    @Test
    @DisplayName("Encoding Decoding Performance")
    void testEncodingDecodingPerformance() throws ErlangException {
        // Test performance of encoding/decoding large structures
        ErlList largeList = ErlList.of();
        for (int i = 0; i < 1000; i++) {
            largeList = largeList.append(ErlAtom.of("item_" + i));
        }

        long startTime = System.nanoTime();
        byte[] encoded = largeList.encodeETF();
        long encodeTime = System.nanoTime() - startTime;

        startTime = System.nanoTime();
        ErlList decoded = (ErlList) ErlTerm.decodeETF(encoded);
        long decodeTime = System.nanoTime() - startTime;

        assertEquals(largeList.getElements().size(), decoded.getElements().size());
        assertEquals(largeList.asString(), decoded.asString());

        // Performance assertions (adjust as needed)
        assertTrue(encodeTime < 10_000_000); // Less than 10ms
        assertTrue(decodeTime < 10_000_000); // Less than 10ms
    }

    @Test
    @DisplayName("Error Handling for Invalid ETF")
    void testErrorHandlingForInvalidETF() {
        // Test with invalid byte sequences
        byte[] invalidETF = new byte[]{
            // Invalid ETF marker
            0xFF,
            0x00,
            0x00,
            0x00
        };

        assertThrows(ErlangException.class, () -> {
            ErlTerm.decodeETF(invalidETF);
        });
    }

    @Test
    @DisplayName("Error Handling for Corrupted Data")
    void testErrorHandlingForCorruptedData() throws ErlangException {
        // Create a valid encoding and corrupt it
        ErlAtom original = ErlAtom.of("test");
        byte[] encoded = original.encodeETF();

        // Corrupt the data by flipping bits
        encoded[0] ^= 0xFF;

        assertThrows(ErlangException.class, () -> {
            ErlTerm.decodeETF(encoded);
        });
    }

    @Test
    @DisplayName("Term Type Identification")
    void testTermTypeIdentification() throws ErlangException {
        ErlTerm[] testTerms = {
            ErlAtom.of("atom"),
            ErlLong.of(123),
            ErlDouble.of(1.23),
            ErlBinary.of("binary".getBytes()),
            ErlList.of(),
            ErlTuple.of(),
            ErlMap.of()
        };

        for (ErlTerm term : testTerms) {
            byte[] encoded = term.encodeETF();
            ErlTerm decoded = ErlTerm.decodeETF(encoded);

            // Test type identification
            assertEquals(term.type(), decoded.type());
            assertFalse(decoded.isAtom() || decoded.isLong() || decoded.isDouble() ||
                      decoded.isBinary() || decoded.isList() || decoded.isTuple() || decoded.isMap());
        }
    }

    @Test
    @DisplayName("Memory Efficiency")
    void testMemoryEfficiency() throws ErlangException {
        // Test that encoding/decoding doesn't leak memory
        Runtime runtime = Runtime.getRuntime();

        // Get initial memory
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // Create and encode/decode many terms
        List<byte[]> encodedTerms = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            ErlTerm term = ErlAtom.of("memory_test_" + i);
            byte[] encoded = term.encodeETF();
            encodedTerms.add(encoded);

            ErlTerm decoded = ErlTerm.decodeETF(encoded);
        }

        // Get final memory
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        // Memory increase should be reasonable (less than 1MB)
        assertTrue(memoryIncrease < 1_000_000);
    }

    @Test
    @DisplayName("Concurrent Encoding/Decoding")
    void testConcurrentEncodingDecoding() throws InterruptedException {
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        ErlTerm[][] results = new ErlTerm[threadCount][100];

        // Create threads that perform concurrent encoding/decoding
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 100; i++) {
                    ErlTerm original = ErlAtom.of("concurrent_" + threadId + "_" + i);
                    byte[] encoded = original.encodeETF();
                    results[threadId][i] = ErlTerm.decodeETF(encoded);
                }
            });
            threads[t].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify all results
        for (int t = 0; t < threadCount; t++) {
            for (int i = 0; i < 100; i++) {
                ErlTerm original = ErlAtom.of("concurrent_" + t + "_" + i);
                ErlTerm decoded = results[t][i];
                assertEquals(original.getValue(), ((ErlAtom) decoded).getValue());
            }
        }
    }
}