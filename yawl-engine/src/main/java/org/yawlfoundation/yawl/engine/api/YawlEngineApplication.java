package org.yawlfoundation.yawl.engine.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot Application entry point for the YAWL Pure Java 25 Agent Engine.
 *
 * Launches an embedded Tomcat HTTP server on port 8080 (by default).
 * Provides REST API endpoints for agent and work item management.
 *
 * Usage:
 *   java -jar yawl-engine.jar
 *   curl http://localhost:8080/agents
 *   curl http://localhost:8080/actuator/health/live
 *
 * Configuration via application.properties or application.yml:
 *   server.port=8080
 *   server.servlet.context-path=/yawl
 *   logging.level.root=INFO
 *
 * @since Java 25
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "org.yawlfoundation.yawl.engine.api.controller",
    "org.yawlfoundation.yawl.engine.api.dto"
})
public class YawlEngineApplication {

    /**
     * Main entry point for the YAWL Engine application.
     *
     * @param args Command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(YawlEngineApplication.class, args);
    }
}
