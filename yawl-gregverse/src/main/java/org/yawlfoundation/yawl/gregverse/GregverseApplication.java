package org.yawlfoundation.yawl.gregverse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * Main Spring Boot application for YAWL Gregverse.
 *
 * <p>This application provides:</p>
 * <ul>
 *   <li>Gregverse workflow patterns and extended YAWL capabilities</li>
 *   <li>Spring Data JPA persistence layer for workflow state</li>
 *   <li>REST endpoints for workflow management and monitoring</li>
 *   <li>Integration with the YAWL workflow engine</li>
 *   <li>OpenTelemetry observability and Prometheus metrics</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Application behavior is controlled via {@code application.yml}:</p>
 * <pre>{@code
 * spring:
 *   datasource:
 *     url: jdbc:h2:mem:gregverse
 *     driver-class-name: org.h2.Driver
 *   jpa:
 *     hibernate:
 *       ddl-auto: update
 * server:
 *   port: 8082
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: health,info,metrics,prometheus
 * }</pre>
 *
 * <h2>Running</h2>
 * <pre>{@code
 * # As a Spring Boot application
 * java -jar yawl-gregverse.jar
 *
 * # With custom configuration
 * java -jar yawl-gregverse.jar --spring.config.location=classpath:/application-prod.yml
 * }</pre>
 *
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 * @since 6.0.0
 */
@SpringBootApplication
public class GregverseApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(GregverseApplication.class);

        // Enable startup metrics for observability
        application.setApplicationStartup(new BufferingApplicationStartup(2048));

        application.run(args);
    }
}
