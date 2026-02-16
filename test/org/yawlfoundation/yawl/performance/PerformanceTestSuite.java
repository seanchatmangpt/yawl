package org.yawlfoundation.yawl.performance;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Complete performance test suite for YAWL Engine.
 * 
 * Includes:
 * - Baseline measurements (latency, throughput, memory)
 * - Load tests (sustained, burst, ramp-up)
 * - Scalability tests
 * 
 * Usage:
 *   ant test -Dtest.suite=org.yawlfoundation.yawl.performance.PerformanceTestSuite
 * 
 * @author YAWL Performance Team
 * @version 5.2
 * @since 2026-02-16
 */
public class PerformanceTestSuite {
    
    public static Test suite() {
        TestSuite suite = new TestSuite("YAWL Performance Test Suite");
        
        // Print header
        EnginePerformanceBaseline.printBaselineSummary();
        
        // Baseline measurements
        suite.addTestSuite(EnginePerformanceBaseline.class);
        
        // Load tests
        suite.addTestSuite(LoadTestSuite.class);
        
        // Scalability tests
        suite.addTestSuite(ScalabilityTest.class);
        
        return suite;
    }
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
