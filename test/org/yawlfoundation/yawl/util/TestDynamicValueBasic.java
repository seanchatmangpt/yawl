package org.yawlfoundation.yawl.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TestDynamicValueBasic {
    
    static class TestObject {
        public String getMessage() { return "hello-world"; }
        public int getValue() { return 42; }
        public boolean isActive() { return true; }
    }
    
    @Test
    void testBasicGetter() {
        TestObject target = new TestObject();
        DynamicValue dv = new DynamicValue("message", target);
        System.out.println("DEBUG: target class = " + target.getClass().getName());
        System.out.println("DEBUG: property = " + dv.getProperty());
        System.out.println("DEBUG: result = '" + dv.toString() + "'");
        assertEquals("hello-world", dv.toString());
    }
    
    @Test  
    void testIsGetter() {
        TestObject target = new TestObject();
        DynamicValue dv = new DynamicValue("active", target);
        System.out.println("DEBUG: property = " + dv.getProperty());
        System.out.println("DEBUG: result = '" + dv.toString() + "'");
        assertEquals("true", dv.toString());
    }
}
