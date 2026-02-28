
/**
 * Example demonstrating basic DSPy program execution.
 * 
 * This example shows how to create and execute a sentiment analysis DSPy program.
 */
package org.yawlfoundation.yawl.dspy.examples;

import org.yawlfoundation.yawl.dspy.DspyProgram;
import org.yawlfoundation.yawl.dspy.DspyExecutionResult;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.dspy.PythonDspyBridge;

import java.util.Map;

public class SentimentAnalysisExample {

    public static void main(String[] args) {
        // Initialize Python execution engine
        PythonExecutionEngine engine = PythonExecutionEngine.builder()
            .contextPoolSize(2)
            .build();
        
        // Create DSPy bridge
        PythonDspyBridge dspy = new PythonDspyBridge(engine);
        
        // Define DSPy program for sentiment analysis
        DspyProgram program = DspyProgram.builder()
            .name("sentiment-analyzer")
            .source("""
                import dspy
                
                class SentimentAnalyzer(dspy.Module):
                    def __init__(self):
                        self.classify = dspy.ChainOfThought("text -> sentiment")
                    
                    def forward(self, text):
                        return self.classify(text=text)
                """)
            .description("Analyzes text sentiment using DSPy ChainOfThought")
            .build();
        
        // Execute the program
        Map<String, Object> inputs = Map.of("text", "YAWL workflow engine is amazing!");
        DspyExecutionResult result = dspy.execute(program, inputs);
        
        // Access results
        Map<String, Object> output = result.output();
        String sentiment = (String) output.get("sentiment");
        Double confidence = (Double) output.get("confidence");
        
        System.out.println("Sentiment: " + sentiment);
        System.out.println("Confidence: " + confidence);
        System.out.println("Execution time: " + result.metrics().executionTimeMs() + "ms");
        
        // Cleanup
        engine.close();
    }
}
