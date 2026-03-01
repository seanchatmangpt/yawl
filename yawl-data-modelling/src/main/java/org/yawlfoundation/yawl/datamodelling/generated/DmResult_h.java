package org.yawlfoundation.yawl.datamodelling.generated;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static org.yawlfoundation.yawl.datamodelling.generated.data_modelling_ffi_h.DM_RESULT_LAYOUT;

/**
 * Accessor helpers for {@code DmResult} C struct.
 * <pre>
 * struct DmResult {
 *   char* data;   // offset 0
 *   char* error;  // offset 8
 * };
 * </pre>
 */
public final class DmResult_h {
    private static final VarHandle VH_DATA =
        DM_RESULT_LAYOUT.varHandle(groupElement("data"));
    private static final VarHandle VH_ERROR =
        DM_RESULT_LAYOUT.varHandle(groupElement("error"));

    private DmResult_h() {}

    public static long data$OFFSET()  { return 0L; }
    public static long error$OFFSET() { return 8L; }

    public static MemorySegment data$get(MemorySegment seg) {
        return (MemorySegment) VH_DATA.get(seg, 0L);
    }
    public static MemorySegment error$get(MemorySegment seg) {
        return (MemorySegment) VH_ERROR.get(seg, 0L);
    }
}
