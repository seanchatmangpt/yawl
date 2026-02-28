package org.yawlfoundation.yawl.engine.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yawlfoundation.yawl.engine.api.dto.HealthDTO;

/**
 * REST API controller for health checks.
 * Implements Kubernetes-style liveness and readiness probes.
 *
 * Base path: /actuator/health
 */
@RestController
@RequestMapping("/actuator/health")
public class HealthController {

    /**
     * Liveness probe endpoint.
     * Reports whether the JVM is alive and responsive.
     *
     * GET /actuator/health/live
     *
     * Kubernetes will restart the container if this returns non-200.
     *
     * @return Health status (UP if JVM is alive)
     */
    @GetMapping("/live")
    public ResponseEntity<HealthDTO> liveness() {
        HealthDTO health = HealthDTO.liveness();
        if (health.isHealthy()) {
            return ResponseEntity.ok(health);
        }
        return ResponseEntity.internalServerError().body(health);
    }

    /**
     * Readiness probe endpoint.
     * Reports whether the engine is ready to accept work requests.
     *
     * GET /actuator/health/ready
     *
     * Kubernetes will remove the pod from load balancer if this returns non-200.
     *
     * @return Health status (UP if engine is ready)
     */
    @GetMapping("/ready")
    public ResponseEntity<HealthDTO> readiness() {
        // In a real implementation, this would check:
        // - Database connectivity
        // - Agent availability
        // - Message queue connectivity
        // For now, we assume the engine is ready
        boolean isReady = true;
        int agentCount = 0;  // Would be fetched from agent registry

        HealthDTO health = HealthDTO.readiness(agentCount, isReady);
        if (health.isHealthy()) {
            return ResponseEntity.ok(health);
        }
        return ResponseEntity.internalServerError().body(health);
    }

    /**
     * Generic health endpoint.
     *
     * GET /actuator/health
     *
     * @return Overall health status
     */
    @GetMapping
    public ResponseEntity<HealthDTO> health() {
        HealthDTO health = HealthDTO.up();
        if (health.isHealthy()) {
            return ResponseEntity.ok(health);
        }
        return ResponseEntity.internalServerError().body(health);
    }
}
