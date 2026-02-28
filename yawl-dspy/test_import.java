import org.yawlfoundation.yawl.worklet.RdrSet;
import org.yawlfoundation.yawl.dspy.worklets.DspyWorkletSelector;

public class test_import {
    public static void main(String[] args) {
        // Test if we can import the classes
        System.out.println("RdrSet imported successfully: " + RdrSet.class.getName());
        System.out.println("DspyWorkletSelector.RdrEvaluator interface: " +
            DspyWorkletSelector.RdrEvaluator.class.getName());
    }
}