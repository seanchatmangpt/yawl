package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YSpecVersion;
import org.yawlfoundation.yawl.util.XNode;

/**
 * Test suite for YSpecificationID Java 25 record conversion.
 * Verifies backward compatibility and immutability guarantees.
 *
 * @author Claude Code 2026-02
 */
@Tag("unit")
public class TestYSpecificationID {

    @Test
    public void testBasicConstructor() {
        YSpecificationID specID = new YSpecificationID("id-123", "2.0", "myspec.yawl");

        assertEquals("id-123", specID.getIdentifier());
        assertEquals("2.0", specID.getVersionAsString());
        assertEquals("myspec.yawl", specID.getUri());
    }

    @Test
    public void testUriOnlyConstructor() {
        YSpecificationID specID = new YSpecificationID("oldspec.yawl");

        assertNull(specID.getIdentifier());
        assertEquals("0.1", specID.getVersionAsString());
        assertEquals("oldspec.yawl", specID.getUri());
    }

    @Test
    public void testVersionObjectConstructor() {
        YSpecVersion version = new YSpecVersion("3.1");
        YSpecificationID specID = new YSpecificationID("id-456", version, "spec.yawl");

        assertEquals("id-456", specID.getIdentifier());
        assertEquals("3.1", specID.getVersionAsString());
        assertEquals("spec.yawl", specID.getUri());
    }

    @Test
    public void testNullVersionDefaultsTo01() {
        YSpecificationID specID = new YSpecificationID("id-789", (YSpecVersion) null, "spec.yawl");

        assertEquals("0.1", specID.getVersionAsString());
    }

    @Test
    public void testGetKey() {
        YSpecificationID withId = new YSpecificationID("id-123", "2.0", "spec.yawl");
        assertEquals("id-123", withId.getKey());

        YSpecificationID withoutId = new YSpecificationID("oldspec.yawl");
        assertEquals("oldspec.yawl", withoutId.getKey());
    }

    @Test
    public void testIsValid() {
        YSpecificationID valid1 = new YSpecificationID("id-123", "2.0", "spec.yawl");
        assertTrue(valid1.isValid());

        YSpecificationID valid2 = new YSpecificationID("oldspec.yawl");
        assertTrue(valid2.isValid());

        // Invalid: null identifier with non-0.1 version
        YSpecificationID invalid = new YSpecificationID(null, "2.0", "spec.yawl");
        assertFalse(invalid.isValid());
    }

    @Test
    public void testEquals() {
        YSpecificationID spec1 = new YSpecificationID("id-123", "2.0", "spec.yawl");
        YSpecificationID spec2 = new YSpecificationID("id-123", "2.0", "different.yawl");
        YSpecificationID spec3 = new YSpecificationID("id-123", "2.1", "spec.yawl");

        assertEquals(spec1, spec2);  // Same identifier and version
        assertNotEquals(spec1, spec3);  // Different version
    }

    @Test
    public void testEqualsPreV20() {
        YSpecificationID spec1 = new YSpecificationID("spec1.yawl");
        YSpecificationID spec2 = new YSpecificationID("spec1.yawl");
        YSpecificationID spec3 = new YSpecificationID("spec2.yawl");

        assertEquals(spec1, spec2);
        assertNotEquals(spec1, spec3);
    }

    @Test
    public void testHashCode() {
        YSpecificationID spec1 = new YSpecificationID("id-123", "2.0", "spec.yawl");
        YSpecificationID spec2 = new YSpecificationID("id-123", "2.0", "different.yawl");

        assertEquals(spec1.hashCode(), spec2.hashCode());
    }

    @Test
    public void testCompareTo() {
        YSpecificationID spec1 = new YSpecificationID("id-123", "1.0", "spec.yawl");
        YSpecificationID spec2 = new YSpecificationID("id-123", "2.0", "spec.yawl");
        YSpecificationID spec3 = new YSpecificationID("id-456", "1.0", "other.yawl");

        assertTrue(spec1.compareTo(spec2) < 0);  // Earlier version
        assertTrue(spec2.compareTo(spec1) > 0);  // Later version
        assertNotEquals(0, spec1.compareTo(spec3));  // Different identifiers
    }

    @Test
    public void testIsPreviousVersionOf() {
        YSpecificationID spec1 = new YSpecificationID("id-123", "1.0", "spec.yawl");
        YSpecificationID spec2 = new YSpecificationID("id-123", "2.0", "spec.yawl");

        assertTrue(spec1.isPreviousVersionOf(spec2));
        assertFalse(spec2.isPreviousVersionOf(spec1));
    }

    @Test
    public void testHasMatchingIdentifier() {
        YSpecificationID spec1 = new YSpecificationID("id-123", "1.0", "spec.yawl");
        YSpecificationID spec2 = new YSpecificationID("id-123", "2.0", "spec.yawl");
        YSpecificationID spec3 = new YSpecificationID("id-456", "1.0", "other.yawl");

        assertTrue(spec1.hasMatchingIdentifier(spec2));
        assertFalse(spec1.hasMatchingIdentifier(spec3));
    }

    @Test
    public void testToString() {
        YSpecificationID specID = new YSpecificationID("id-123", "2.0", "spec.yawl");
        String str = specID.toString();

        assertTrue(str.contains("spec.yawl"));
        assertTrue(str.contains("2.0"));
    }

    @Test
    public void testToKeyString() {
        YSpecificationID specID = new YSpecificationID("id-123", "2.0", "spec.yawl");
        assertEquals("id-123:2.0", specID.toKeyString());

        YSpecificationID preV20 = new YSpecificationID("oldspec.yawl");
        assertEquals("oldspec.yawl:0.1", preV20.toKeyString());
    }

    @Test
    public void testToFullString() {
        YSpecificationID specID = new YSpecificationID("id-123", "2.0", "spec.yawl");
        assertEquals("id-123:2.0:spec.yawl", specID.toFullString());

        YSpecificationID preV20 = new YSpecificationID("oldspec.yawl");
        assertEquals(":0.1:oldspec.yawl", preV20.toFullString());
    }

    @Test
    public void testFromFullString() {
        YSpecificationID specID = YSpecificationID.fromFullString("id-123:2.0:spec.yawl");
        assertEquals("id-123", specID.getIdentifier());
        assertEquals("2.0", specID.getVersionAsString());
        assertEquals("spec.yawl", specID.getUri());
    }

    @Test
    public void testFromFullStringPreV20() {
        YSpecificationID specID = YSpecificationID.fromFullString("oldspec.yawl");
        assertNull(specID.getIdentifier());
        assertEquals("0.1", specID.getVersionAsString());
        assertEquals("oldspec.yawl", specID.getUri());
    }

    @Test
    public void testFromFullStringInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            YSpecificationID.fromFullString("invalid:format");
        });
    }

    @Test
    public void testToXNode() {
        YSpecificationID specID = new YSpecificationID("id-123", "2.0", "spec.yawl");
        XNode node = specID.toXNode();

        assertEquals("id-123", node.getChildText("identifier"));
        assertEquals("2.0", node.getChildText("version"));
        assertEquals("spec.yawl", node.getChildText("uri"));
    }

    @Test
    public void testFromXNode() {
        XNode node = new XNode("specificationid");
        node.addChild("identifier", "id-123");
        node.addChild("version", "2.0");
        node.addChild("uri", "spec.yawl");

        YSpecificationID specID = YSpecificationID.fromXNode(node);
        assertEquals("id-123", specID.getIdentifier());
        assertEquals("2.0", specID.getVersionAsString());
        assertEquals("spec.yawl", specID.getUri());
    }

    @Test
    public void testXNodeConstructor() {
        XNode node = new XNode("specificationid");
        node.addChild("identifier", "id-123");
        node.addChild("version", "2.0");
        node.addChild("uri", "spec.yawl");

        YSpecificationID specID = new YSpecificationID(node);
        assertEquals("id-123", specID.getIdentifier());
        assertEquals("2.0", specID.getVersionAsString());
        assertEquals("spec.yawl", specID.getUri());
    }

    @Test
    public void testToMap() {
        YSpecificationID specID = new YSpecificationID("id-123", "2.0", "spec.yawl");
        var map = specID.toMap();

        assertEquals("id-123", map.get("specidentifier"));
        assertEquals("2.0", map.get("specversion"));
        assertEquals("spec.yawl", map.get("specuri"));
    }

    @Test
    public void testToXML() {
        YSpecificationID specID = new YSpecificationID("id-123", "2.0", "spec.yawl");
        String xml = specID.toXML();

        assertTrue(xml.contains("id-123"));
        assertTrue(xml.contains("2.0"));
        assertTrue(xml.contains("spec.yawl"));
    }

    @Test
    public void testImmutabilityWithIdentifier() {
        YSpecificationID original = new YSpecificationID("id-123", "2.0", "spec.yawl");
        YSpecificationID modified = original.withIdentifier("id-456");

        assertEquals("id-123", original.getIdentifier());
        assertEquals("id-456", modified.getIdentifier());
        assertEquals("2.0", modified.getVersionAsString());
        assertEquals("spec.yawl", modified.getUri());
    }

    @Test
    public void testImmutabilityWithVersion() {
        YSpecificationID original = new YSpecificationID("id-123", "2.0", "spec.yawl");
        YSpecificationID modified = original.withVersion("3.0");

        assertEquals("2.0", original.getVersionAsString());
        assertEquals("3.0", modified.getVersionAsString());
        assertEquals("id-123", modified.getIdentifier());
        assertEquals("spec.yawl", modified.getUri());
    }

    @Test
    public void testImmutabilityWithUri() {
        YSpecificationID original = new YSpecificationID("id-123", "2.0", "spec.yawl");
        YSpecificationID modified = original.withUri("newspec.yawl");

        assertEquals("spec.yawl", original.getUri());
        assertEquals("newspec.yawl", modified.getUri());
        assertEquals("id-123", modified.getIdentifier());
        assertEquals("2.0", modified.getVersionAsString());
    }

    @Test
    public void testRecordAccessors() {
        YSpecificationID specID = new YSpecificationID("id-123", "2.0", "spec.yawl");

        // Record generates accessors without "get" prefix
        assertEquals("id-123", specID.identifier());
        assertEquals("spec.yawl", specID.uri());
        assertNotNull(specID.version());

        // But backward-compatible getters still work
        assertEquals("id-123", specID.getIdentifier());
        assertEquals("spec.yawl", specID.getUri());
        assertNotNull(specID.getVersion());
    }
}
