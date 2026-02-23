#!/bin/bash

# Test script for GregVerse Analytics Reporter

echo "Testing GregVerse Analytics Reporter..."

# Check if we can compile the analytics classes
echo "1. Compiling analytics classes..."
javac -cp "$(find ~/.m2/repository -name "*.jar" | tr '\n' ':')" \
    src/main/java/org/yawlfoundation/yawl/mcp/a2a/gregverse/analytics/*.java

if [ $? -eq 0 ]; then
    echo "✓ Analytics classes compiled successfully"
else
    echo "✗ Analytics compilation failed"
    exit 1
fi

echo ""
echo "2. Creating test runner..."

# Create a simple test runner
cat > AnalyticsTestRunner.java << 'EOF'
import org.yawlfoundation.yawl.mcp.a2a.gregverse.analytics.*;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

public class AnalyticsTestRunner {
    public static void main(String[] args) {
        try {
            System.out.println("Starting GregVerse Analytics Test...");

            // Create analytics reporter
            GregVerseAnalyticsReporter analytics = new GregVerseAnalyticsReporter(false);
            analytics.start();

            // Record some test transactions
            System.out.println("Recording test transactions...");
            analytics.recordTransaction("provider-001", "consumer-001", 150.0, "consulting", 500, 5, "US").get();
            analytics.recordTransaction("provider-002", "consumer-002", 200.0, "development", 400, 4, "CA").get();
            analytics.recordTransaction("provider-003", "consumer-003", 100.0, "consulting", 300, 3, "US").get();

            // Get dashboard data
            DashboardDataProvider.DashboardData data = analytics.getDashboardData();
            System.out.println("Dashboard Data:");
            System.out.println("  Total Transactions: " + data.getTotalTransactions());
            System.out.println("  Average Transaction Value: $" + data.getAverageTransactionValue());
            System.out.println("  Average Response Time: " + data.getAverageResponseTime() + "ms");
            System.out.println("  Active Providers: " + data.getActiveProviders());
            System.out.println("  Active Consumers: " + data.getActiveConsumers());

            // Test reports
            System.out.println("\nGenerating reports...");
            GregVerseAnalyticsReporter.MetricAggregation aggregation =
                analytics.aggregateMetrics(a -> true).get();

            System.out.println("Metric Aggregation:");
            System.out.println("  Total Transactions: " + aggregation.getTotalTransactions());
            System.out.println("  Average Value: $" + aggregation.getAverageTransactionValue());

            // Test health metrics
            DashboardDataProvider.HealthMetrics health = analytics.getHealthMetrics();
            System.out.println("\nHealth Metrics:");
            System.out.println("  Provider Health Score: " + health.getProviderHealthScore());
            System.out.println("  Market Activity Score: " + health.getMarketActivityScore());

            // Test forecasts
            DashboardDataProvider.ForecastMetrics forecast = analytics.getForecast(24);
            System.out.println("\n24-Hour Forecast:");
            System.out.println("  Projected Transactions: " + forecast.getProjectedTransactions());
            System.out.println("  Projected Value: $" + forecast.getProjectedValue());
            System.out.println("  Confidence: " + forecast.getConfidence() + "%");

            // Cleanup
            analytics.stop();
            System.out.println("\n✓ All tests completed successfully!");

        } catch (Exception e) {
            System.err.println("✗ Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
EOF

echo ""
echo "3. Running test..."

# Run the test
javac -cp "$(find ~/.m2/repository -name "*.jar" | tr '\n' ':'):target/classes" AnalyticsTestRunner.java
java -cp "$(find ~/.m2/repository -name "*.jar" | tr '\n' ':'):target/classes:." AnalyticsTestRunner

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Test completed successfully!"
else
    echo ""
    echo "✗ Test failed"
fi

# Cleanup
rm -f AnalyticsTestRunner.java AnalyticsTestRunner.class