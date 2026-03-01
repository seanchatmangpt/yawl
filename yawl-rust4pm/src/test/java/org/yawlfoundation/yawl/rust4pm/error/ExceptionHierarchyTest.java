package org.yawlfoundation.yawl.rust4pm.error;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionHierarchyTest {

    @Test
    void ParseException_extends_ProcessMiningException() {
        assertEquals(ProcessMiningException.class, ParseException.class.getSuperclass());
    }

    @Test
    void ConformanceException_extends_ProcessMiningException() {
        assertEquals(ProcessMiningException.class, ConformanceException.class.getSuperclass());
    }

    @Test
    void ProcessMiningException_is_checked() {
        assertTrue(Exception.class.isAssignableFrom(ProcessMiningException.class));
        assertFalse(RuntimeException.class.isAssignableFrom(ProcessMiningException.class));
    }

    @Test
    void ParseException_preserves_message() {
        assertEquals("bad json", new ParseException("bad json").getMessage());
    }

    @Test
    void ConformanceException_preserves_cause() {
        RuntimeException cause = new RuntimeException("null ptr");
        ConformanceException ex = new ConformanceException("replay failed", cause);
        assertSame(cause, ex.getCause());
    }

    @Test
    void ParseException_caught_as_base_class() {
        ParseException e = assertThrows(ParseException.class, () -> { throw new ParseException("oops"); });
        assertInstanceOf(ProcessMiningException.class, e);
        assertEquals("oops", e.getMessage());
    }
}
