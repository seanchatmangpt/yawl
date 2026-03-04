/*
 * ErlTermCodec Test Suite
 *
 * Chicago TDD: Tests drive behavior. No mocks - real implementation or throw.
 * Tests ETF serialization roundtrip, encoding/decoding, and error handling.
 */

package org.yawlfoundation.yawl.nativebridge.erlang;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ErlTermCodec functionality.
 * Tests ETF serialization roundtrip for all Erlang term types.
 */
@EnabledIfEnvironmentVariable(named = "TEST_ERLANG", matches = "true")
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("ErlTermCodec ETF Serialization Tests")
class ErlTermCodecTest {

    @Nested
    @DisplayName("Atom Encoding/Decoding Tests")
    class AtomEncodingTests {

        @Test
        @Order(1)
        @DisplayName("Small atom UTF-8 encoding roundtrip")
        void smallAtomUtf8Roundtrip() {
            // Test small atoms (≤255 UTF-8 bytes)
            ErlTerm.ErlAtom original = ErlTerm.ErlAtom.of("test");

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Small atom should survive encode/decode roundtrip");
        }

        @Test
        @Order(2)
        @DisplayName("Large atom UTF-8 encoding roundtrip")
        void largeAtomUtf8Roundtrip() {
            // Test large atoms (>255 UTF-8 bytes)
            String largeAtomContent = "a".repeat(300);
            ErlTerm.ErlAtom original = ErlTerm.ErlAtom.of(largeAtomContent);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Large atom should survive encode/decode roundtrip");
        }

        @Test
        @Order(3)
        @DisplayName("Empty atom encoding roundtrip")
        void emptyAtomRoundtrip() {
            ErlTerm.ErlAtom original = ErlTerm.ErlAtom.of("");

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Empty atom should survive encode/decode roundtrip");
        }

        @Test
        @Order(4)
        @DisplayName("Atom with special characters encoding roundtrip")
        void specialCharactersAtomRoundtrip() {
            ErlTerm.ErlAtom original = ErlTerm.ErlAtom.of("Special chars: \u0001 \u007F \uFFFF");

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Atom with special characters should survive encode/decode roundtrip");
        }

        @Test
        @Order(5)
        @DisplayName("Null atom throws IllegalArgumentException")
        void nullAtomThrows() {
            assertThrows(IllegalArgumentException.class, () -> {
                ErlTermCodec.encode(null);
            });
        }
    }

    @Nested
    @DisplayName("Integer Encoding/Decoding Tests")
    class IntegerEncodingTests {

        @Test
        @Order(6)
        @DisplayName("Small integer (0-255) encoding roundtrip")
        void smallIntegerRoundtrip() {
            ErlTerm.ErlInteger original = ErlTerm.ErlInteger.of(42);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Small integer should survive encode/decode roundtrip");
        }

        @Test
        @Order(7)
        @DisplayName("Large integer encoding roundtrip")
        void largeIntegerRoundtrip() {
            ErlTerm.ErlInteger original = ErlTerm.ErlInteger.of(Integer.MAX_VALUE);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Large integer should survive encode/decode roundtrip");
        }

        @Test
        @Order(8)
        @DisplayName("Negative integer encoding roundtrip")
        void negativeIntegerRoundtrip() {
            ErlTerm.ErlInteger original = ErlTerm.ErlInteger.of(-42);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Negative integer should survive encode/decode roundtrip");
        }

        @Test
        @Order(9)
        @DisplayName("BigInteger encoding roundtrip")
        void bigIntegerRoundtrip() {
            // Test with BigInteger value
            ErlTerm.ErlInteger original = ErlTerm.ErlInteger.of(new java.math.BigInteger("12345678901234567890"));

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "BigInteger should survive encode/decode roundtrip");
        }
    }

    @Nested
    @DisplayName("Float Encoding/Decoding Tests")
    class FloatEncodingTests {

        @Test
        @Order(10)
        @DisplayName("Float encoding roundtrip")
        void floatRoundtrip() {
            ErlTerm.ErlFloat original = ErlTerm.ErlFloat.of(3.14159);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Float should survive encode/decode roundtrip");
        }

        @Test
        @Order(11)
        @DisplayName("Special float values encoding roundtrip")
        void specialFloatsRoundtrip() {
            float[] specialValues = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN};

            for (float value : specialValues) {
                ErlTerm.ErlFloat original = ErlTerm.ErlFloat.of(value);

                byte[] encoded = ErlTermCodec.encode(original);
                ErlTerm decoded = ErlTermCodec.decode(encoded);

                assertEquals(original, decoded,
                    "Special float should survive encode/decode roundtrip");
            }
        }
    }

    @Nested
    @DisplayName("Binary Encoding/Decoding Tests")
    class BinaryEncodingTests {

        @Test
        @Order(12)
        @DisplayName("Binary encoding roundtrip")
        void binaryRoundtrip() {
            byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
            ErlTerm.ErlBinary original = ErlTerm.ErlBinary.of(data);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Binary should survive encode/decode roundtrip");
        }

        @Test
        @Order(13)
        @DisplayName("Empty binary encoding roundtrip")
        void emptyBinaryRoundtrip() {
            ErlTerm.ErlBinary original = ErlTerm.ErlBinary.of(new byte[0]);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Empty binary should survive encode/decode roundtrip");
        }

        @Test
        @Order(14)
        @DisplayName("Large binary encoding roundtrip")
        void largeBinaryRoundtrip() {
            byte[] largeData = new byte[10000];
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i % 256);
            }

            ErlTerm.ErlBinary original = ErlTerm.ErlBinary.of(largeData);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Large binary should survive encode/decode roundtrip");
        }
    }

    @Nested
    @DisplayName("List Encoding/Decoding Tests")
    class ListEncodingTests {

        @Test
        @Order(15)
        @DisplayName("List encoding roundtrip")
        void listRoundtrip() {
            List<ErlTerm> elements = List.of(
                ErlTerm.ErlAtom.of("a"),
                ErlTerm.ErlInteger.of(1),
                ErlTerm.ErlAtom.of("b"),
                ErlTerm.ErlInteger.of(2)
            );
            ErlTerm.ErlList original = new ErlTerm.ErlList(elements, ErlTerm.Nil());

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "List should survive encode/decode roundtrip");
        }

        @Test
        @Order(16)
        @DisplayName("Empty list encoding roundtrip")
        void emptyListRoundtrip() {
            ErlTerm.ErlList original = new ErlTerm.ErlList(List.of(), ErlTerm.Nil());

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Empty list should survive encode/decode roundtrip");
        }

        @Test
        @Order(17)
        @DisplayName("Nested list encoding roundtrip")
        void nestedListRoundtrip() {
            List<ErlTerm> innerList = List.of(
                ErlTerm.ErlAtom.of("inner"),
                ErlTerm.ErlInteger.of(42)
            );
            List<ErlTerm> outerList = List.of(
                ErlTerm.ErlAtom.of("outer"),
                new ErlTerm.ErlList(innerList, ErlTerm.Nil()),
                ErlTerm.ErlInteger.of(100)
            );
            ErlTerm.ErlList original = new ErlTerm.ErlList(outerList, ErlTerm.Nil());

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Nested list should survive encode/decode roundtrip");
        }
    }

    @Nested
    @DisplayName("Tuple Encoding/Decoding Tests")
    class TupleEncodingTests {

        @Test
        @Order(18)
        @DisplayName("Small tuple encoding roundtrip")
        void smallTupleRoundtrip() {
            List<ErlTerm> elements = List.of(
                ErlTerm.ErlAtom.of("test"),
                ErlTerm.ErlInteger.of(42),
                ErlTerm.ErlAtom.of("tuple")
            );
            ErlTerm.ErlTuple original = new ErlTerm.ErlTuple(elements);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Small tuple should survive encode/decode roundtrip");
        }

        @Test
        @Order(19)
        @DisplayName("Large tuple encoding roundtrip")
        void largeTupleRoundtrip() {
            List<ErlTerm> elements = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                elements.add(ErlTerm.ErlInteger.of(i));
            }
            ErlTerm.ErlTuple original = new ErlTerm.ErlTuple(elements);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Large tuple should survive encode/decode roundtrip");
        }

        @Test
        @Order(20)
        @DisplayName("Nested tuple encoding roundtrip")
        void nestedTupleRoundtrip() {
            List<ErlTerm> innerTuple = List.of(
                ErlTerm.ErlAtom.of("inner"),
                ErlTerm.ErlInteger.of(42)
            );
            List<ErlTerm> outerTuple = List.of(
                ErlTerm.ErlAtom.of("outer"),
                new ErlTerm.ErlTuple(innerTuple),
                ErlTerm.ErlInteger.of(100)
            );
            ErlTerm.ErlTuple original = new ErlTerm.ErlTuple(outerTuple);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Nested tuple should survive encode/decode roundtrip");
        }
    }

    @Nested
    @DisplayName("Map Encoding/Decoding Tests")
    class MapEncodingTests {

        @Test
        @Order(21)
        @DisplayName("Map encoding roundtrip")
        void mapRoundtrip() {
            Map<ErlTerm, ErlTerm> map = new HashMap<>();
            map.put(ErlTerm.ErlAtom.of("key1"), ErlTerm.ErlAtom.of("value1"));
            map.put(ErlTerm.ErlInteger.of(2), ErlTerm.ErlInteger.of(200));
            map.put(ErlTerm.ErlAtom.of("key3"), ErlTerm.ErlAtom.of("value3"));

            ErlTerm.ErlMap original = new ErlTerm.ErlMap(map);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Map should survive encode/decode roundtrip");
        }

        @Test
        @Order(22)
        @DisplayName("Empty map encoding roundtrip")
        void emptyMapRoundtrip() {
            ErlTerm.ErlMap original = new ErlTerm.ErlMap(new HashMap<>());

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Empty map should survive encode/decode roundtrip");
        }

        @Test
        @Order(23)
        @DisplayName("Complex map encoding roundtrip")
        void complexMapRoundtrip() {
            Map<ErlTerm, ErlTerm> map = new HashMap<>();
            map.put(ErlTerm.ErlAtom.of("simple"), ErlTerm.ErlInteger.of(1));

            // Add a list as a value
            List<ErlTerm> listElements = List.of(
                ErlTerm.ErlAtom.of("item1"),
                ErlTerm.ErlInteger.of(2),
                ErlTerm.ErlAtom.of("item3")
            );
            map.put(ErlTerm.ErlAtom.of("list"), new ErlTerm.ErlList(listElements, ErlTerm.Nil()));

            // Add a tuple as a value
            List<ErlTerm> tupleElements = List.of(
                ErlTerm.ErlAtom.of("nested"),
                ErlTerm.ErlInteger.of(42)
            );
            map.put(ErlTerm.ErlAtom.of("tuple"), new ErlTerm.ErlTuple(tupleElements));

            ErlTerm.ErlMap original = new ErlTerm.ErlMap(map);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "Complex map should survive encode/decode roundtrip");
        }
    }

    @Nested
    @DisplayName("Encoding/Decoding Utilities Tests")
    class UtilsTests {

        @Test
        @Order(24)
        @DisplayName("encodeArgs produces valid argument list")
        void encodeArgsProducesValidList() {
            List<ErlTerm> args = List.of(
                ErlTerm.ErlAtom.of("first"),
                ErlTerm.ErlInteger.of(42),
                ErlTerm.ErlAtom.of("last")
            );

            byte[] encoded = ErlTermCodec.encodeArgs(args);

            // Should not contain version byte (131)
            assertNotEquals(131, encoded[0] & 0xFF,
                "encodeArgs should not include version byte");

            // Should decode to a proper list
            ErlTerm decoded = ErlTermCodec.decode(encoded);
            assertTrue(decoded instanceof ErlTerm.ErlList,
                "encodeArgs result should decode to a list");
        }

        @Test
        @Order(25)
        @DisplayName("decodeRpcResult handles rex tuple")
        void decodeRpcResultHandlesRexTuple() throws Exception {
            // Create a {rex, Result} tuple
            List<ErlTerm> rexElements = List.of(
                ErlTerm.ErlAtom.of("rex"),
                ErlTerm.ErlInteger.of(42)
            );
            ErlTerm.ErlTuple rexTuple = new ErlTerm.ErlTuple(rexElements);

            byte[] encoded = ErlTermCodec.encode(rexTuple);
            ErlTerm result = ErlTermCodec.decodeRpcResult(encoded);

            // Should unwrap the rex tuple and return the inner result
            assertEquals(ErlTerm.ErlInteger.of(42), result,
                "decodeRpcResult should unwrap rex tuple and return result");
        }

        @Test
        @Order(26)
        @DisplayName("decodeRpcResult handles badrpc tuple")
        void decodeRpcResultHandlesBadrpcTuple() throws Exception {
            // Create a {badrpc, Reason} tuple
            List<ErlTerm> badrpcElements = List.of(
                ErlTerm.ErlAtom.of("badrpc"),
                ErlTerm.ErlAtom.of("timeout")
            );
            ErlTerm.ErlTuple badrpcTuple = new ErlTerm.ErlTuple(badrpcElements);

            byte[] encoded = ErlTermCodec.encode(badrpcTuple);

            // Should throw ErlangRpcException
            assertThrows(ErlangException.class, () -> {
                ErlTermCodec.decodeRpcResult(encoded);
            });
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @Order(27)
        @DisplayName("Null input throws exception")
        void nullInputThrows() {
            assertThrows(Exception.class, () -> {
                ErlTermCodec.decode(null);
            });
        }

        @Test
        @Order(28)
        @DisplayName("Invalid version byte throws exception")
        void invalidVersionByteThrows() {
            byte[] invalidData = {0x00}; // Invalid version byte

            assertThrows(Exception.class, () -> {
                ErlTermCodec.decode(invalidData);
            });
        }

        @Test
        @Order(29)
        @DisplayName("Truncated data throws exception")
        void truncatedDataThrows() {
            // Create valid encoded data but truncate it
            ErlTerm.ErlAtom atom = ErlTerm.ErlAtom.of("test");
            byte[] fullEncoded = ErlTermCodec.encode(atom);
            byte[] truncated = new byte[fullEncoded.length - 1];
            System.arraycopy(fullEncoded, 0, truncated, 0, truncated.length);

            assertThrows(Exception.class, () -> {
                ErlTermCodec.decode(truncated);
            });
        }

        @Test
        @Order(30)
        @DisplayName("Malformed tag throws exception")
        void malformedTagThrows() {
            // Create malformed ETF data with invalid tag
            byte[] malformedData = {
                (byte) 131, // Version byte
                (byte) 255  // Invalid tag
            };

            assertThrows(Exception.class, () -> {
                ErlTermCodec.decode(malformedData);
            });
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @Order(31)
        @DisplayName("Very deep nesting")
        void deepNesting() {
            // Create deeply nested structure
            ErlTerm current = ErlTerm.ErlInteger.of(1);

            for (int i = 0; i < 10; i++) {
                List<ErlTerm> elements = List.of(
                    ErlTerm.ErlAtom.of("level" + i),
                    current
                );
                current = new ErlTerm.ErlTuple(elements);
            }

            byte[] encoded = ErlTermCodec.encode(current);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(current, decoded,
                "Deeply nested structure should survive encode/decode");
        }

        @Test
        @Order(32)
        @DisplayName("Maximum size structures")
        void maxSizeStructures() {
            // Test maximum reasonable size structures
            List<ErlTerm> largeElements = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                largeElements.add(ErlTerm.ErlInteger.of(i));
            }

            ErlTerm.ErlList largeList = new ErlTerm.ErlList(largeElements, ErlTerm.Nil());

            byte[] encoded = ErlTermCodec.encode(largeList);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(largeList, decoded,
                "Large structure should survive encode/decode");
        }

        @ParameterizedTest
        @Order(33)
        @ValueSource(strings = {"", "a", "ab", "abc", "very_long_string_to_test_utf8_encoding"})
        @DisplayName("Various string lengths")
        void variousStringLengths(String testString) {
            ErlTerm.ErlAtom original = ErlTerm.ErlAtom.of(testString);

            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);

            assertEquals(original, decoded,
                "String of length " + testString.length() + " should survive encode/decode");
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @Order(34)
        @DisplayName("Encoding performance is acceptable")
        void encodingPerformance() {
            ErlTerm.ErlList list = new ErlTerm.ErlList(
                List.of(ErlTerm.ErlAtom.of("performance"), ErlTerm.ErlInteger.of(42)),
                ErlTerm.Nil()
            );

            long startTime = System.nanoTime();
            byte[] encoded = ErlTermCodec.encode(list);
            long endTime = System.nanoTime();

            long durationUs = (endTime - startTime) / 1000;

            assertTrue(encoded.length > 0, "Encoded data should not be empty");
            assertTrue(durationUs < 100, "Encoding should take under 100µs");
        }

        @Test
        @Order(35)
        @DisplayName("Decoding performance is acceptable")
        void decodingPerformance() {
            ErlTerm.ErlList original = new ErlTerm.ErlList(
                List.of(ErlTerm.ErlAtom.of("performance"), ErlTerm.ErlInteger.of(42)),
                ErlTerm.Nil()
            );
            byte[] encoded = ErlTermCodec.encode(original);

            long startTime = System.nanoTime();
            ErlTerm decoded = ErlTermCodec.decode(encoded);
            long endTime = System.nanoTime();

            long durationUs = (endTime - startTime) / 1000;

            assertEquals(original, decoded, "Decoded data should match original");
            assertTrue(durationUs < 100, "Decoding should take under 100µs");
        }

        @Test
        @Order(36)
        @DisplayName("Roundtrip performance is acceptable")
        void roundtripPerformance() {
            ErlTerm original = new ErlTerm.ErlList(
                List.of(ErlTerm.ErlAtom.of("roundtrip"), ErlTerm.ErlInteger.of(42)),
                ErlTerm.Nil()
            );

            long startTime = System.nanoTime();
            byte[] encoded = ErlTermCodec.encode(original);
            ErlTerm decoded = ErlTermCodec.decode(encoded);
            long endTime = System.nanoTime();

            long durationUs = (endTime - startTime) / 1000;

            assertEquals(original, decoded, "Roundtrip should preserve data");
            assertTrue(durationUs < 200, "Roundtrip should take under 200µs");
        }
    }
}