package org.yawlfoundation.yawl.rust4pm.model;

import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

/**
 * An OCEL2 object materialized from Rust-owned memory.
 *
 * <p>{@link #fromSegment(MemorySegment)} reads directly from an {@code OcelObjectC}
 * struct in native memory — zero-copy field reads with explicit UTF-8 charset.
 *
 * @param objectId   unique object identifier
 * @param objectType object type name (e.g., "Order", "Item")
 */
public record OcelObject(String objectId, String objectType) {

    /**
     * Read one OcelObjectC struct from native memory. Zero-copy field reads.
     *
     * <p>Explicit {@code UTF_8} charset is used for both string fields because
     * Rust CStrings are always UTF-8 and {@code getString(0)} without a charset
     * argument uses the platform default (which may differ on some locales).
     *
     * @param s a MemorySegment slice over exactly one OcelObjectC struct
     * @return materialized OcelObject record
     */
    public static OcelObject fromSegment(MemorySegment s) {
        MemorySegment idPtr   = (MemorySegment) rust4pm_h.OCEL_OBJECT_C_OBJECT_ID.get(s, 0L);
        MemorySegment typePtr = (MemorySegment) rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE.get(s, 0L);

        return new OcelObject(
            idPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8),
            typePtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8)
        );
    }
}
