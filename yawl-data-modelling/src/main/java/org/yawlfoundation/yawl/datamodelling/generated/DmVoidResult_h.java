package org.yawlfoundation.yawl.datamodelling.generated;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h.DM_VOID_RESULT_LAYOUT;

/**
 * Accessor helpers for {@code DmVoidResult} C struct.
 * <pre>
 * struct DmVoidResult {
 *   char* error;  // offset 0 — null on success
 * };
 * </pre>
 */
public final class DmVoidResult_h {
    private static final VarHandle VH_ERROR =
        DM_VOID_RESULT_LAYOUT.varHandle(groupElement("error"));

    private DmVoidResult_h() {}

    public static long error$OFFSET() { return 0L; }

    public static MemorySegment error$get(MemorySegment seg) {
        return (MemorySegment) VH_ERROR.get(seg, 0L);
    }
}
