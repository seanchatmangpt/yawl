package org.yawlfoundation.yawl.mcp.a2a;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * Main Spring Boot application for YAWL MCP-A2A integration.
 *
 * <p>This application provides:</p>
 * <ul>
 *   <li>Model Context Protocol (MCP) server exposing YAWL workflow tools</li>
 *   <li>Agent-to-Agent (A2A) protocol support for inter-agent communication</li>
 *   <li>REST endpoints for health checks and metrics</li>
 *   <li>Integration with the YAWL workflow engine</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Application behavior is controlled via {@code application.yml}:</p>
 * <pre>{@code
 * yawl:
 *   mcp:
 *     enabled: true
 *     transport: stdio
 *   a2a:
 *     enabled: true
 *     agent-name: "yawl-workflow-agent"
 * }</pre>
 *
 * <h2>Running</h2>
 * <pre>{@code
 * # As a Spring Boot application
 * java -jar yawl-mcp-a2a-app.jar
 *
 * # With custom configuration
 * java -jar yawl-mcp-a2a-app.jar --spring.config.location=classpath:/application-prod.yml
 * }</pre>
 *
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 * @since 6.0.0
 */
@SpringBootApplication
public class YawlMcpA2aApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(YawlMcpA2aApplication.class);

        // Enable startup metrics for observability
        application.setApplicationStartup(new BufferingApplicationStartup(2048));

        application.run(args);
    }
}
