
/**
 * Example demonstrating anomaly forensics using DSPy.
 * 
 * This example shows how to analyze workflow anomalies and perform
 * root cause analysis using DSPy MultiChainComparison.
 */
package org.yawlfoundation.yawl.dspy.examples;

import org.yawlfoundation.yawl.dspy.DspyProgram;
import org.yawlfoundation.yawl.dspy.DspyExecutionResult;
import org.yawlfoundation.yawl.dspy.PythonDspyBridge;
import org.yawlfoundation.yawl.dspy.forensics.AnomalyContext;
import org.yawlfoundation.yawl.dspy.forensics.ForensicsReport;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;

import java.util.List;
import java.util.Map;

public class AnomalyForensicsExample {

    public static void main(String[] args) {
        // Initialize Python execution engine
        PythonExecutionEngine engine = PythonExecutionEngine.builder()
            .contextPoolSize(4)
            .build();
        
        // Create DSPy bridge
        PythonDspyBridge dspy = new PythonDspyBridge(engine);
        
        // Simulate anomaly detection
        // Task processing latency spiked from 150ms average to 480ms (320% deviation)
        AnomalyContext anomaly = new AnomalyContext(
            "task_processing_latency",
            5000L,  // anomaly persisted for 5 seconds
            3.2,    // 320% deviation from baseline
            List.of(
                145L, 152L, 148L,   // normal baseline
                160L, 210L,         // beginning spike
                475L, 510L, 495L    // sustained spike
            ),
            8       // 8 concurrent cases during spike
        );
        
        // Run forensics analysis
        ForensicsReport report = dspy.runForensics(anomaly);
        
        // Print analysis results
        System.out.println("=== Anomaly Forensics Analysis ===");
        System.out.println("Metric: " + anomaly.metricName());
        System.out.println("Deviation: " + (anomaly.deviationFactor() * 100) + "% above baseline");
        System.out.println("Duration: " + anomaly.durationMs() + "ms");
        System.out.println("Concurrent Cases: " + anomaly.concurrentCases());
        System.out.println();
        
        System.out.println("Root Cause: " + report.rootCause());
        System.out.println("Confidence: " + (report.confidence() * 100) + "%");
        System.out.println("Evidence Chain:");
        for (String evidence : report.evidenceChain()) {
            System.out.println("  - " + evidence);
        }
        System.out.println("Recommendation: " + report.recommendation());
        
        // Alternative: Execute directly with DSPy program
        DspyProgram program = DspyProgram.builder()
            .name("anomaly-forensics")
            .source("""
                import dspy
                import json
                
                class AnomalyRootCause(dspy.Module):
                    def __init__(self):
                        self.analyze = dspy.ChainOfThought(
                            "metric, duration, deviation, samples, cases -> root_cause, confidence, evidence, recommendation"
                        )
                    
                    def forward(self, metric, duration, deviation, samples, cases):
                        result = self.analyze(
                            metric=metric,
                            duration=duration,
                            deviation=deviation,
                            samples=samples,
                            cases=cases
                        )
                        
                        # Generate evidence chain
                        evidence = []
                        if deviation > 2.0:
                            evidence.append(f"Metric spike {deviation*100:.0f}% above baseline")
                        if duration > 3000:
                            evidence.append(f"Anomaly persisted for {duration}ms")
                        if cases > 5:
                            evidence.append(f"High concurrency: {cases} cases")
                        
                        return {
                            "root_cause": result.root_cause,
                            "confidence": result.confidence,
                            "evidence_chain": evidence,
                            "recommendation": result.recommendation
                        }
                """)
            .build();
        
        Map<String, Object> inputs = Map.of(
            "metric", "task_processing_latency",
            "duration", 5000,
            "deviation", 3.2,
            "samples", List.of(145L, 152L, 148L, 160L, 210L, 475L, 510L, 495L),
            "cases", 8
        );
        
        DspyExecutionResult result = dspy.execute(program, inputs);
        Map<String, Object> output = result.output();
        
        System.out.println("
=== Direct DSPy Execution ===");
        System.out.println("Root Cause: " + output.get("root_cause"));
        System.out.println("Confidence: " + output.get("confidence"));
        System.out.println("Recommendation: " + output.get("recommendation"));
        
        // Cleanup
        engine.close();
    }
}
