package org.yawlfoundation.yawl.rust4pm.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OcelValueTest {

    @Test
    void StringValue_accessible() {
        OcelValue v = new OcelValue.StringValue("hello");
        assertEquals("hello", ((OcelValue.StringValue) v).value());
    }

    @Test
    void IntValue_accessible() {
        OcelValue v = new OcelValue.IntValue(42L);
        assertEquals(42L, ((OcelValue.IntValue) v).value());
    }

    @Test
    void FloatValue_accessible() {
        OcelValue v = new OcelValue.FloatValue(3.14);
        assertEquals(3.14, ((OcelValue.FloatValue) v).value(), 0.001);
    }

    @Test
    void Timestamp_accessible() {
        Instant now = Instant.now();
        OcelValue v = new OcelValue.Timestamp(now);
        assertEquals(now, ((OcelValue.Timestamp) v).value());
    }

    @Test
    void sealed_switch_over_all_variants() {
        List<OcelValue> values = List.of(
            new OcelValue.StringValue("x"),
            new OcelValue.IntValue(1L),
            new OcelValue.FloatValue(0.5),
            new OcelValue.Timestamp(Instant.EPOCH)
        );
        for (OcelValue v : values) {
            String tag = switch (v) {
                case OcelValue.StringValue sv -> "string";
                case OcelValue.IntValue    iv -> "int";
                case OcelValue.FloatValue  fv -> "float";
                case OcelValue.Timestamp   tv -> "timestamp";
            };
            assertFalse(tag.isEmpty());
        }
    }
}
