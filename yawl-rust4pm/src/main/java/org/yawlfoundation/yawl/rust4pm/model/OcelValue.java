package org.yawlfoundation.yawl.rust4pm.model;

import java.time.Instant;

/**
 * Sealed interface for OCEL2 attribute values.
 * Use exhaustive pattern matching:
 * <pre>{@code
 * String display = switch (value) {
 *     case OcelValue.StringValue sv -> sv.value();
 *     case OcelValue.IntValue    iv -> String.valueOf(iv.value());
 *     case OcelValue.FloatValue  fv -> String.valueOf(fv.value());
 *     case OcelValue.Timestamp   tv -> tv.value().toString();
 * };
 * }</pre>
 */
public sealed interface OcelValue
    permits OcelValue.StringValue, OcelValue.IntValue,
            OcelValue.FloatValue, OcelValue.Timestamp {

    record StringValue(String value)  implements OcelValue {}
    record IntValue(long value)       implements OcelValue {}
    record FloatValue(double value)   implements OcelValue {}
    record Timestamp(Instant value)   implements OcelValue {}
}
