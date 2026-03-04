import org.yawlfoundation.yawl.pi.rag.stub.ProcessMiningFacade;

public class TestPerformanceResult {
    public static void main(String[] args) {
        // Test PerformanceResult access
        ProcessMiningFacade.PerformanceResult result = 
            new ProcessMiningFacade.PerformanceResult(100, 1500.0, 2.5, null, "{}");
        
        // Test field access
        double avgTime = result.avgFlowTimeMs;
        double throughput = result.throughputPerHour;
        
        System.out.println("Avg time: " + avgTime);
        System.out.println("Throughput: " + throughput);
    }
}
