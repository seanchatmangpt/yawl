package org.yawlfoundation.yawl.integration.mcp.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enable YAWL MCP Spring integration.
 *
 * <p>Add this annotation to a Spring {@code @Configuration} class to enable
 * automatic configuration of YAWL MCP server with Spring-managed tools and resources.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @SpringBootApplication
 * @EnableYawlMcp
 * public class YawlMcpApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(YawlMcpApplication.class, args);
 *     }
 * }
 * }</pre>
 *
 * <h2>What This Enables</h2>
 * <ul>
 *   <li>Automatic configuration of {@link YawlMcpConfiguration}</li>
 *   <li>Registration of all YAWL MCP beans (clients, session manager, server)</li>
 *   <li>Discovery and registration of custom {@link YawlMcpTool} beans</li>
 *   <li>Discovery and registration of custom {@link YawlMcpResource} beans</li>
 *   <li>Lifecycle management (connect on startup, disconnect on shutdown)</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Configure via {@code application.yml} or {@code application.properties}:</p>
 * <pre>{@code
 * yawl:
 *   mcp:
 *     enabled: true
 *     engine-url: http://localhost:8080/yawl
 *     username: admin
 *     password: YAWL
 *     transport: stdio
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see YawlMcpConfiguration
 * @see YawlMcpProperties
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnableYawlMcp {

    /**
     * Base packages to scan for custom MCP tools and resources.
     * Default: scan all packages.
     *
     * @return base package names for component scanning
     */
    String[] basePackages() default {};

    /**
     * Enable auto-configuration of YAWL MCP server.
     * Set to false to disable automatic server creation (for custom configuration).
     *
     * @return true to enable auto-configuration (default: true)
     */
    boolean autoConfiguration() default true;
}
