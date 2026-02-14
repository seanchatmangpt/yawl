package org.yawlfoundation.yawl.integration.config;

/**
 * Spring Boot Integration Configuration for MCP and A2A (2026 Best Practices)
 *
 * This configuration demonstrates enterprise-grade integration patterns:
 * - Automatic bean discovery for MCP tools
 * - A2A agent exposure via Spring annotations
 * - OAuth 2.1 resource server configuration
 * - Circuit breaker and resilience patterns
 * - Observability (metrics, tracing, logging)
 * - Multi-transport server configuration
 *
 * Usage:
 * 1. Add @EnableMCP and @EnableA2A to your Spring Boot application
 * 2. Annotate service beans with @Tool or @Agent
 * 3. Configure application.yml with security and transport settings
 *
 * Example application.yml:
 * ```yaml
 * mcp:
 *   server:
 *     name: yawl-mcp-server
 *     port: 3000
 *     transport: streamable-http
 *   security:
 *     oauth2:
 *       issuer-uri: https://auth.example.com
 *       audience: mcp-server-yawl
 *
 * a2a:
 *   server:
 *     name: YAWL Workflow Engine
 *     json-rpc-port: 8080
 *     grpc-port: 9090
 *   security:
 *     oauth2:
 *       issuer-uri: https://auth.example.com
 *       audience: a2a-server-yawl
 * ```
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpringIntegrationConfig {

    /* COMMENTED OUT - Requires Spring Boot dependencies

    // ==================== MCP Configuration ====================

    @Configuration
    @EnableMCP  // Enable MCP support
    public static class McpConfiguration {

        @Bean
        public McpServer mcpServer(
            @Value("${mcp.server.name}") String serverName,
            @Value("${mcp.server.port}") int port
        ) {
            return McpServer.builder()
                .serverInfo(McpServerInfo.builder()
                    .name(serverName)
                    .version("5.2.0")
                    .protocolVersion("2025-11-25")
                    .build())
                .transport(StreamableHttpTransport.builder()
                    .port(port)
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(5))
                    .enableSSE(true)
                    .build())
                .build();
        }

        // Auto-discover tools from Spring beans
        @Bean
        public ToolRegistry toolRegistry() {
            return new AnnotationToolRegistry();
        }

        // Resource registry
        @Bean
        public ResourceRegistry resourceRegistry() {
            return new DefaultResourceRegistry();
        }

        // Prompt registry
        @Bean
        public PromptRegistry promptRegistry() {
            return new DefaultPromptRegistry();
        }
    }

    // ==================== MCP Security Configuration ====================

    @Configuration
    @EnableWebSecurity
    public static class McpSecurityConfig {

        @Bean
        public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                .securityMatcher("/mcp/**")
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())
                    )
                )
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/mcp/tools/**").hasAuthority("SCOPE_mcp.tools")
                    .requestMatchers("/mcp/resources/**").hasAuthority("SCOPE_mcp.resources")
                    .requestMatchers("/mcp/prompts/**").hasAuthority("SCOPE_mcp.prompts")
                    .anyRequest().authenticated()
                );

            return http.build();
        }

        @Bean
        public JwtDecoder jwtDecoder() {
            String jwkSetUri = environment.getProperty("mcp.security.oauth2.jwk-set-uri");

            NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .build();

            // Validate audience claim
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(
                    environment.getProperty("mcp.security.oauth2.issuer-uri")
                ),
                new JwtClaimValidator<>("aud", aud ->
                    aud.contains(environment.getProperty("mcp.security.oauth2.audience"))
                )
            ));

            return decoder;
        }

        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
            JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
            grantedAuthoritiesConverter.setAuthorityPrefix("SCOPE_");

            JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
            return converter;
        }
    }

    // ==================== A2A Configuration ====================

    @Configuration
    @EnableA2A  // Enable A2A support
    public static class A2AConfiguration {

        @Bean
        public A2AServer a2aServer(
            @Value("${a2a.server.name}") String serverName,
            @Value("${a2a.server.json-rpc-port}") int jsonRpcPort,
            @Value("${a2a.server.grpc-port}") int grpcPort
        ) {
            AgentCard card = createAgentCard(serverName);

            return A2AServerBuilder.create()
                .card(card)
                .executor(new SpringAgentExecutor())
                .taskStore(new RedisTaskStore())  // Distributed task store
                .transports(Arrays.asList(
                    new JsonRpcTransport(jsonRpcPort),
                    new GrpcTransport(grpcPort)
                ))
                .build();
        }

        private AgentCard createAgentCard(String serverName) {
            return AgentCard.builder()
                .name(serverName)
                .version("5.2")
                // ... (full AgentCard configuration)
                .build();
        }

        // Spring bean-based agent executor
        @Bean
        public SpringAgentExecutor springAgentExecutor(
            ApplicationContext applicationContext
        ) {
            return new SpringAgentExecutor(applicationContext);
        }
    }

    // ==================== A2A Security Configuration ====================

    @Configuration
    public static class A2ASecurityConfig {

        @Bean
        public SecurityFilterChain a2aSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                .securityMatcher("/a2a/**")
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                        .decoder(a2aJwtDecoder())
                    )
                )
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/a2a/agent-card").permitAll()
                    .requestMatchers("/.well-known/agent-card.json").permitAll()
                    .requestMatchers("/a2a/message/send").hasAuthority("SCOPE_agent:execute")
                    .requestMatchers("/a2a/message/stream").hasAuthority("SCOPE_agent:execute")
                    .requestMatchers("/a2a/tasks/cancel").hasAuthority("SCOPE_agent:execute")
                    .anyRequest().authenticated()
                );

            return http.build();
        }

        @Bean
        public JwtDecoder a2aJwtDecoder() {
            // Similar to MCP JWT decoder
            // ... implementation
        }
    }

    // ==================== Resilience Configuration ====================

    @Configuration
    public static class ResilienceConfiguration {

        @Bean
        public CircuitBreakerRegistry circuitBreakerRegistry() {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .slidingWindowSize(10)
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();

            return CircuitBreakerRegistry.of(config);
        }

        @Bean
        public RetryRegistry retryRegistry() {
            RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofSeconds(2))
                .retryExceptions(TransientException.class, TimeoutException.class)
                .ignoreExceptions(ValidationException.class, AuthenticationException.class)
                .build();

            return RetryRegistry.of(config);
        }

        @Bean
        public TimeLimiterRegistry timeLimiterRegistry() {
            TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(30))
                .build();

            return TimeLimiterRegistry.of(config);
        }

        // Apply resilience to MCP tools
        @Aspect
        @Component
        public class McpToolResilienceAspect {

            @Autowired
            private CircuitBreakerRegistry circuitBreakerRegistry;

            @Autowired
            private RetryRegistry retryRegistry;

            @Around("@annotation(tool)")
            public Object applyResilience(
                ProceedingJoinPoint pjp,
                Tool tool
            ) throws Throwable {
                String toolName = tool.name();

                // Get or create circuit breaker for this tool
                CircuitBreaker circuitBreaker = circuitBreakerRegistry
                    .circuitBreaker("tool-" + toolName);

                // Get or create retry for this tool
                Retry retry = retryRegistry
                    .retry("tool-" + toolName);

                // Wrap execution with resilience patterns
                Supplier<Object> decoratedSupplier = Decorators
                    .ofSupplier(() -> {
                        try {
                            return pjp.proceed();
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate();

                return decoratedSupplier.get();
            }
        }
    }

    // ==================== Observability Configuration ====================

    @Configuration
    public static class ObservabilityConfiguration {

        @Bean
        public MeterRegistry meterRegistry() {
            return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        }

        // Metrics for MCP tools
        @Aspect
        @Component
        public class McpToolMetricsAspect {

            @Autowired
            private MeterRegistry registry;

            @Around("@annotation(tool)")
            public Object measureToolInvocation(
                ProceedingJoinPoint pjp,
                Tool tool
            ) throws Throwable {
                Timer.Sample sample = Timer.start(registry);

                try {
                    Object result = pjp.proceed();

                    sample.stop(Timer.builder("mcp.tool.invocation")
                        .tag("tool", tool.name())
                        .tag("status", "success")
                        .register(registry));

                    return result;

                } catch (Exception e) {
                    sample.stop(Timer.builder("mcp.tool.invocation")
                        .tag("tool", tool.name())
                        .tag("status", "error")
                        .tag("error", e.getClass().getSimpleName())
                        .register(registry));

                    throw e;
                }
            }
        }

        // Metrics for A2A agent operations
        @Aspect
        @Component
        public class A2AAgentMetricsAspect {

            @Autowired
            private MeterRegistry registry;

            @Around("@annotation(action)")
            public Object measureAgentAction(
                ProceedingJoinPoint pjp,
                Action action
            ) throws Throwable {
                Timer.Sample sample = Timer.start(registry);

                try {
                    Object result = pjp.proceed();

                    sample.stop(Timer.builder("a2a.agent.action")
                        .tag("action", action.id())
                        .tag("status", "success")
                        .register(registry));

                    return result;

                } catch (Exception e) {
                    sample.stop(Timer.builder("a2a.agent.action")
                        .tag("action", action.id())
                        .tag("status", "error")
                        .tag("error", e.getClass().getSimpleName())
                        .register(registry));

                    throw e;
                }
            }
        }

        // Distributed tracing
        @Bean
        public Tracer tracer() {
            return OpenTelemetry.noop().getTracer("yawl-integration");
        }
    }

    // ==================== Example Service with MCP Tools ====================

    @Service
    public class YawlWorkflowServiceWithMCP {

        @Autowired
        private WorkflowEngine workflowEngine;

        // Expose as MCP tool
        @Tool(
            name = "start_workflow",
            description = "Start a new YAWL workflow instance"
        )
        @CircuitBreaker(name = "workflow-operations")
        @Retry(name = "workflow-operations")
        public ToolResult startWorkflow(
            @Parameter(description = "Workflow specification ID") String workflowId,
            @Parameter(description = "Initial workflow data") Map<String, Object> inputData
        ) {
            try {
                String caseId = workflowEngine.startWorkflow(workflowId, inputData);

                return ToolResult.success(Map.of(
                    "caseId", caseId,
                    "status", "running",
                    "startTime", Instant.now()
                ));

            } catch (WorkflowNotFoundException e) {
                return ToolResult.error(
                    "WORKFLOW_NOT_FOUND",
                    "Workflow not found: " + workflowId,
                    Map.of("availableWorkflows", workflowEngine.listWorkflows())
                );
            }
        }

        @Tool(
            name = "get_workflow_status",
            description = "Get workflow execution status"
        )
        public ToolResult getWorkflowStatus(
            @Parameter(description = "Workflow case ID") String caseId
        ) {
            WorkflowStatus status = workflowEngine.getStatus(caseId);

            return ToolResult.success(Map.of(
                "caseId", caseId,
                "status", status.getState(),
                "completedTasks", status.getCompletedTasks(),
                "pendingTasks", status.getPendingTasks(),
                "progress", status.getProgress()
            ));
        }

        // Streaming tool with reactive support
        @Tool(
            name = "execute_task",
            description = "Execute workflow task with progress streaming"
        )
        public Flux<TaskUpdate> executeTask(
            @Parameter String taskId,
            @Parameter Map<String, Object> taskData
        ) {
            return workflowEngine.executeTaskWithProgress(taskId, taskData)
                .map(progress -> TaskUpdate.builder()
                    .taskId(taskId)
                    .progress(progress.getPercent())
                    .status(progress.getStatus())
                    .build());
        }
    }

    // ==================== Example Service with A2A Agent ====================

    @Service
    @Agent(name = "YAWL Workflow Engine")
    public class YawlWorkflowServiceWithA2A {

        @Autowired
        private WorkflowEngine workflowEngine;

        @Action(
            id = "startWorkflow",
            description = "Start a new workflow instance"
        )
        public WorkflowResponse startWorkflow(
            @Param("workflowId") String workflowId,
            @Param("inputData") Map<String, Object> inputData
        ) {
            String caseId = workflowEngine.startWorkflow(workflowId, inputData);

            return WorkflowResponse.builder()
                .caseId(caseId)
                .status("running")
                .build();
        }

        @Action(
            id = "getWorkflowStatus",
            description = "Get workflow status"
        )
        public StatusResponse getStatus(@Param("caseId") String caseId) {
            WorkflowStatus status = workflowEngine.getStatus(caseId);

            return StatusResponse.builder()
                .caseId(caseId)
                .status(status.getState())
                .completedTasks(status.getCompletedTasks())
                .pendingTasks(status.getPendingTasks())
                .build();
        }

        // Streaming action
        @Action(
            id = "executeTask",
            description = "Execute workflow task",
            streaming = true
        )
        public Stream<TaskUpdate> executeTask(
            @Param("taskId") String taskId,
            @Param("taskData") Map<String, Object> taskData
        ) {
            return workflowEngine.executeTaskWithProgress(taskId, taskData)
                .map(progress -> TaskUpdate.builder()
                    .taskId(taskId)
                    .progress(progress.getPercent())
                    .status(progress.getStatus())
                    .build());
        }
    }

    // ==================== Health Checks ====================

    @Component
    public class McpHealthIndicator implements HealthIndicator {

        @Autowired
        private McpServer mcpServer;

        @Override
        public Health health() {
            if (mcpServer.isRunning()) {
                return Health.up()
                    .withDetail("server", "running")
                    .withDetail("tools", mcpServer.getTools().size())
                    .withDetail("resources", mcpServer.getResources().size())
                    .build();
            } else {
                return Health.down()
                    .withDetail("server", "stopped")
                    .build();
            }
        }
    }

    @Component
    public class A2AHealthIndicator implements HealthIndicator {

        @Autowired
        private A2AServer a2aServer;

        @Override
        public Health health() {
            if (a2aServer.isRunning()) {
                return Health.up()
                    .withDetail("server", "running")
                    .withDetail("skills", a2aServer.getAgentCard().getSkills().size())
                    .withDetail("transports", Arrays.asList("json-rpc", "grpc"))
                    .build();
            } else {
                return Health.down()
                    .withDetail("server", "stopped")
                    .build();
            }
        }
    }

    */

    // Placeholder main to allow compilation
    public static void main(String[] args) {
        System.out.println("Spring Integration Configuration");
        System.out.println("This configuration demonstrates enterprise-grade MCP and A2A integration patterns.");
        System.out.println("\nTo use:");
        System.out.println("1. Add Spring Boot dependencies to pom.xml");
        System.out.println("2. Uncomment the configuration classes above");
        System.out.println("3. Configure application.yml with your settings");
        System.out.println("4. Annotate your services with @Tool or @Agent");
    }
}
