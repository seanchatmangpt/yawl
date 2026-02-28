
/**
 * Example demonstrating worklet selection using DSPy.
 * 
 * This example shows how to use DSPy for intelligent worklet selection
 * in YAWL workflows.
 */
package org.yawlfoundation.yawl.dspy.examples;

import org.yawlfoundation.yawl.dspy.DspyProgram;
import org.yawlfoundation.yawl.dspy.DspyExecutionResult;
import org.yawlfoundation.yawl.dspy.PythonDspyBridge;
import org.yawlfoundation.yawl.dspy.worklets.WorkletSelection;
import org.yawlfoundation.yawl.dspy.worklets.WorkletSelectionContext;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;

import java.util.List;
import java.util.Map;

public class WorkletSelectionExample {

    public static void main(String[] args) {
        // Initialize Python execution engine
        PythonExecutionEngine engine = PythonExecutionEngine.builder()
            .contextPoolSize(4)
            .build();
        
        // Create DSPy bridge
        PythonDspyBridge dspy = new PythonDspyBridge(engine);
        
        // Create worklet selection context
        WorkletSelectionContext context = new WorkletSelectionContext(
            "Review",
            Map.of(
                "urgency", "high",
                "complexity", "medium",
                "amount", "$50,000"
            ),
            List.of("StandardTrack", "FastTrack", "ExpertTrack"),
            List.of(
                "case_123: FastTrack",
                "case_124: StandardTrack",
                "case_125: ExpertTrack"
            )
        );
        
        // Execute worklet selection
        WorkletSelection selection = dspy.selectWorklet(context);
        
        // Print results
        System.out.println("Selected Worklet: " + selection.workletId());
        System.out.println("Confidence: " + selection.confidence());
        System.out.println("Rationale: " + selection.rationale());
        
        // Alternative: Execute directly with DSPy program
        DspyProgram program = DspyProgram.builder()
            .name("worklet-selector")
            .source("""
                import dspy
                
                class WorkletSelector(dspy.Module):
                    def __init__(self):
                        self.select = dspy.ChainOfThought(
                            "task, case_data, available -> worklet, confidence, rationale"
                        )
                    
                    def forward(self, task, case_data, available):
                        result = self.select(
                            task=task,
                            case_data=case_data,
                            available=available
                        )
                        return {
                            "worklet_id": result.worklet,
                            "confidence": result.confidence,
                            "rationale": result.rationale
                        }
                """)
            .build();
        
        Map<String, Object> inputs = Map.of(
            "task", "Review",
            "case_data", Map.of("urgency", "high", "complexity", "medium"),
            "available", List.of("StandardTrack", "FastTrack", "ExpertTrack")
        );
        
        DspyExecutionResult result = dspy.execute(program, inputs);
        Map<String, Object> output = result.output();
        
        System.out.println("
Direct execution results:");
        System.out.println("Selected: " + output.get("worklet_id"));
        System.out.println("Confidence: " + output.get("confidence"));
        
        // Cleanup
        engine.close();
    }
}
