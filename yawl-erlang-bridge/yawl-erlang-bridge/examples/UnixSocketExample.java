/**
 * Example usage of Unix domain socket transport for YAWL Erlang bridge.
 *
 * This example demonstrates:
 * 1. Creating Unix domain socket transport
 * 2. Performing RPC operations
 * 3. Health monitoring
 * 4. Connection pooling
 */
package org.yawlfoundation.yawl.bridge.erlang.examples;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.yawlfoundation.yawl.bridge.erlang.ErlAtom;
import org.yawlfoundation.yawl.bridge.erlang.ErlTerm;
import org.yawlfoundation.yawl.bridge.erlang.EiBuffer;
import org.yawlfoundation.yawl.bridge.erlang.transport.UnixSocketTransport;
import org.yawlfoundation.yawl.bridge.erlang.transport.ErlangConnectionPool;
import org.yawlfoundation.yawl.bridge.erlang.transport.BeamHealthCheck;

/**
 * Example demonstrating Unix domain socket transport usage.
 */
public class UnixSocketExample {

    public static void main(String[] args) {
        String cookie = "yawl";
        String hostname = "localhost";
        Path socketDir = Paths.get("/tmp/yawl-erlang");

        System.out.println("=== YAWL Unix Domain Socket Transport Example ===");

        try {
            // Example 1: Simple transport
            System.out.println("\n1. Creating simple transport...");
            simpleTransportExample(cookie, hostname, socketDir);

            // Example 2: Connection pool
            System.out.println("\n2. Using connection pool...");
            connectionPoolExample(cookie, hostname, socketDir);

            // Example 3: Health monitoring
            System.out.println("\n3. Health monitoring...");
            healthMonitoringExample(cookie, hostname, socketDir);

        } catch (Exception e) {
            System.err.println("Example failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Example using simple UnixSocketTransport.
     */
    private static void simpleTransportExample(String cookie, String hostname, Path socketDir)
            throws Exception {
        System.out.println("   - Creating Unix domain socket transport...");

        try (UnixSocketTransport transport = new UnixSocketTransport(cookie, hostname, socketDir)) {
            System.out.println("   - Connected to: " + transport.getNodeName());
            System.out.println("   - Socket path: " + transport.getSocketPath());

            // Check health
            System.out.println("   - Health status: " + transport.isHealthy());

            // Perform RPC operation (if Erlang node is available)
            try {
                System.out.println("   - Performing RPC call...");
                EiBuffer result = transport.rpc("erlang", "node");
                System.out.println("   - RPC result: " + result);
            } catch (Exception e) {
                System.out.println("   - RPC call failed (expected if no Erlang node): " + e.getMessage());
            }

            System.out.println("   - Transport closed successfully");
        }
    }

    /**
     * Example using connection pool for better performance.
     */
    private static void connectionPoolExample(String cookie, String hostname, Path socketDir)
            throws Exception {
        System.out.println("   - Creating connection pool (size 2)...");

        try (ErlangConnectionPool pool = new ErlangConnectionPool(cookie, 2, java.time.Duration.ofSeconds(5))) {
            System.out.println("   - Pool created with " + pool.getTotalConnectionCount() + " connections");

            // Perform multiple RPC calls
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            for (int i = 0; i < 5; i++) {
                try {
                    System.out.println("   - RPC call " + (i + 1) + "...");
                    EiBuffer result = pool.rpc("erlang", "node");
                    successCount.incrementAndGet();
                    System.out.println("   - Call " + (i + 1) + " succeeded");
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println("   - Call " + (i + 1) + " failed: " + e.getMessage());
                }

                // Small delay between calls
                Thread.sleep(100);
            }

            System.out.println("   - Results: " + successCount.get() + " successful, " +
                             failureCount.get() + " failed");

            // Check pool health
            System.out.println("   - Active connections: " + pool.getActiveConnectionCount());
            System.out.println("   - Total connections: " + pool.getTotalConnectionCount());
            System.out.println("   - All healthy: " + pool.isAllConnectionsHealthy());
        }
    }

    /**
     * Example demonstrating health monitoring.
     */
    private static void healthMonitoringExample(String cookie, String hostname, Path socketDir)
            throws Exception {
        System.out.println("   - Creating transport with health monitoring...");

        try (ErlangConnectionPool pool = new ErlangConnectionPool(cookie, 3, java.time.Duration.ofSeconds(5))) {

            // Create health checker
            try (BeamHealthCheck healthCheck = BeamHealthCheck.Factory.create(pool)) {
                System.out.println("   - Starting health monitoring...");
                healthCheck.start();

                // Monitor health for 30 seconds
                for (int i = 0; i < 6; i++) {
                    Thread.sleep(5000);

                    BeamHealthCheck.HealthStatus status = healthCheck.getHealthStatus();
                    System.out.println("   - Health status: " + status);
                    System.out.println("   - Last ping age: " + healthCheck.getLastSuccessfulPingAge());
                    System.out.println("   - Active connections: " + healthCheck.getActiveConnectionCount());

                    if (status == BeamHealthCheck.HealthStatus.HEALTHY) {
                        System.out.println("   - ✅ All systems healthy");
                    } else if (status == BeamHealthCheck.HealthStatus.DEGRADED) {
                        System.out.println("   - ⚠️  Systems degraded");
                    } else if (status == BeamHealthCheck.HealthStatus.UNHEALTHY) {
                        System.out.println("   - ❌ Systems unhealthy - triggering reconnection");
                        pool.reconnectAllFailed();
                    }
                }

                System.out.println("   - Stopping health monitoring...");
                healthCheck.stop();
            }
        }
    }
}