package org.yawlfoundation.yawl.containers;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton container fixtures for cross-test-class container reuse.
 * Containers are started once per JVM and reused across all tests.
 */
public final class SharedContainerFixture {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String MYSQL_IMAGE = "mysql:8.4";

    private static final Network SHARED_NETWORK = Network.newNetwork();
    private static final AtomicBoolean POSTGRES_STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean MYSQL_STARTED = new AtomicBoolean(false);

    private static volatile PostgreSQLContainer<?> postgresInstance;
    private static volatile MySQLContainer<?> mysqlInstance;

    // Prevent instantiation
    private SharedContainerFixture() {}

    /**
     * Get the shared PostgreSQL container instance.
     */
    public static PostgreSQLContainer<?> getPostgres() {
        if (postgresInstance == null) {
            synchronized (SharedContainerFixture.class) {
                if (postgresInstance == null) {
                    postgresInstance = new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
                            .withDatabase("yawl")
                            .withUsername("yawl")
                            .withPassword("yawl_test_password")
                            .withNetwork(SHARED_NETWORK)
                            .withNetworkAliases("postgres")
                            .withReuse(true)
                            .waitingFor(Wait.forListeningPort())
                            .withStartupTimeout(Duration.ofSeconds(60));
                }
            }
        }
        return postgresInstance;
    }

    /**
     * Get the shared MySQL container instance.
     */
    public static MySQLContainer<?> getMySQL() {
        if (mysqlInstance == null) {
            synchronized (SharedContainerFixture.class) {
                if (mysqlInstance == null) {
                    mysqlInstance = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
                            .withDatabase("yawl")
                            .withUsername("yawl")
                            .withPassword("yawl_test_password")
                            .withNetwork(SHARED_NETWORK)
                            .withNetworkAliases("mysql")
                            .withReuse(true)
                            .waitingFor(Wait.forListeningPort())
                            .withStartupTimeout(Duration.ofSeconds(90));
                }
            }
        }
        return mysqlInstance;
    }

    /**
     * Ensure a container is started (idempotent).
     */
    public static void ensureStarted(PostgreSQLContainer<?> container) {
        if (POSTGRES_STARTED.compareAndSet(false, true)) {
            container.start();
        }
    }

    /**
     * Ensure a container is started (idempotent).
     */
    public static void ensureStarted(MySQLContainer<?> container) {
        if (MYSQL_STARTED.compareAndSet(false, true)) {
            container.start();
        }
    }

    /**
     * Get the shared network for inter-container communication.
     */
    public static Network getSharedNetwork() {
        return SHARED_NETWORK;
    }
}