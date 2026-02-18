# YAWL Configuration Properties Reference

**Document type:** Reference
**Audience:** AI coding agents working on the YAWL codebase
**Purpose:** Complete lookup table for all YAWL configuration properties. Consult this when setting up, deploying, or modifying engine configuration.

Sources authoritative for this document:
- `/home/user/yawl/src/main/resources/application.yml`
- `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/spring/application.yml`
- `/home/user/yawl/database/connection-pooling/hikaricp/application.yml`
- `/home/user/yawl/observability/opentelemetry/application-otel.properties`
- `/home/user/yawl/build/schedulingService/properties/yawl.properties`
- `@Value` annotations in `/home/user/yawl/src/org/yawlfoundation/yawl/engine/observability/OpenTelemetryConfig.java`
- `System.getProperty` calls in `JwtManager.java`, `InterfaceBWebsideController.java`, `MailService.java`

---

## 1. Engine Core

Properties that control the YAWL workflow engine runtime behaviour.

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `spring.application.name` | string | `yawl-engine` | Application name used in logs, metrics tags, and actuator info. | `yawl-engine` |
| `spring.profiles.active` | string | `development` | Active Spring profiles. Controls which profile-specific config blocks load. | `production` |
| `server.port` | int | `8080` | HTTP port for the engine's main REST API and actuator endpoints. | `8080` |
| `server.shutdown` | string | `graceful` | Shutdown mode. `graceful` waits for in-flight requests to complete before stopping. | `graceful` |
| `yawl.engine.password` | string | _(required)_ | Engine admin password. Read from JVM system property by `InterfaceBWebsideController`. Override with env var `YAWL_PASSWORD`. | _(set via vault)_ |
| `yawl.jwt.secret` | string | _(required)_ | HMAC-SHA256 signing key for JWT session tokens. Minimum 32 bytes. Falls back to env var `YAWL_JWT_SECRET`. Engine refuses to start if neither is set. | _(generate: `openssl rand -base64 32`)_ |

**Scheduling service client properties** (file: `build/schedulingService/properties/yawl.properties`):

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `InterfaceBClient.backEndURI` | string | `http://localhost:8080/yawl/ib` | Interface B endpoint URL used by scheduling service to reach the engine. | `http://engine:8080/yawl/ib` |
| `ResourceGatewayClient.backEndURI` | string | `http://localhost:8080/resourceService/gateway` | Resource service gateway URL. | `http://resource:8080/resourceService/gateway` |
| `ResourceCalendarGatewayClient.backEndURI` | string | `http://localhost:8080/resourceService/calendarGateway` | Resource calendar endpoint URL. | `http://resource:8080/resourceService/calendarGateway` |
| `WorkQueueGatewayClient.backEndURI` | string | `http://localhost:8080/resourceService/workqueuegateway` | Work queue gateway endpoint URL. | `http://resource:8080/resourceService/workqueuegateway` |
| `user` | string | `schedulingService` | Username the scheduling service uses to authenticate with the engine. | `schedulingService` |
| `password` | string | `yScheduling` | Password for scheduling service authentication. Change in production. | _(set via vault)_ |
| `waitSec` | int (ms) | `5000` | Milliseconds between connection retry attempts on engine unavailability. | `5000` |
| `timeoutRefreshWorkItemList` | int (ms) | `60000` | Polling interval in milliseconds for refreshing the work item list. | `60000` |
| `userCaseIdElementName` | string | `caseDescription` | XPath element name used to extract the human-readable case ID for display. | `caseDescription` |

---

## 2. Database

Properties for JDBC connection and Hibernate ORM. Loaded from `application.yml` (HikariCP profile).

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `spring.datasource.url` | string | `jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:yawl}` | JDBC connection URL. Parameterised from env vars `DB_HOST`, `DB_PORT`, `DB_NAME`. | `jdbc:postgresql://postgres:5432/yawl` |
| `spring.datasource.username` | string | `${DB_USER:yawl}` | Database username. Resolved from env var `DB_USER`. | `yawl` |
| `spring.datasource.password` | string | `${DB_PASSWORD:changeme}` | Database password. Resolved from env var `DB_PASSWORD`. Never use the default in production. | _(set via vault)_ |
| `spring.datasource.driver-class-name` | string | `org.postgresql.Driver` | JDBC driver class. Change for non-PostgreSQL databases. | `org.postgresql.Driver` |
| `spring.datasource.hikari.pool-name` | string | `YAWLConnectionPool` | Name for the primary connection pool in logs and JMX metrics. | `YAWLConnectionPool` |
| `spring.datasource.hikari.maximum-pool-size` | int | `20` | Maximum number of connections in the primary pool. Formula: `(core_count * 2) + effective_spindle_count`. | `20` |
| `spring.datasource.hikari.minimum-idle` | int | `5` | Minimum idle connections maintained in the pool. | `5` |
| `spring.datasource.hikari.connection-timeout` | int (ms) | `30000` | Maximum time to wait for a connection from the pool before throwing. | `30000` |
| `spring.datasource.hikari.idle-timeout` | int (ms) | `600000` | Time an idle connection can remain in the pool before being closed. | `600000` |
| `spring.datasource.hikari.max-lifetime` | int (ms) | `1800000` | Maximum lifetime of any connection regardless of idle/active state. | `1800000` |
| `spring.datasource.hikari.validation-timeout` | int (ms) | `5000` | Maximum time to validate a connection is alive. | `5000` |
| `spring.datasource.hikari.leak-detection-threshold` | int (ms) | `60000` | If a connection is borrowed for longer than this, a leak warning is logged. `0` disables detection. | `60000` |
| `spring.datasource.hikari.connection-test-query` | string | `SELECT 1` | SQL query used to test connection validity. | `SELECT 1` |
| `spring.datasource.hikari.connection-init-sql` | string | `SET TIME ZONE 'UTC'` | SQL executed once when each connection is created. | `SET TIME ZONE 'UTC'` |
| `spring.datasource.hikari.keepalive-time` | int (ms) | `120000` | Interval for keepalive pings to prevent idle connection timeouts. | `120000` |
| `spring.datasource.hikari.register-mbeans` | boolean | `true` | Expose pool metrics via JMX. | `true` |
| `spring.datasource.hikari.allow-pool-suspension` | boolean | `true` | Allow the pool to be suspended and resumed (useful for rolling upgrades). | `true` |
| `spring.datasource.hikari.auto-commit` | boolean | `true` | Default auto-commit mode for connections. | `true` |
| `spring.datasource.hikari.initialization-fail-timeout` | int | `1` | Milliseconds to wait for initial connection. `-1` disables fail-fast. | `1` |
| `spring.jpa.database-platform` | string | `org.hibernate.dialect.PostgreSQLDialect` | Hibernate dialect class. | `org.hibernate.dialect.PostgreSQLDialect` |
| `spring.jpa.show-sql` | boolean | `false` | Log all generated SQL to stdout. Disable in production. | `false` |
| `spring.jpa.hibernate.ddl-auto` | string | `validate` | Schema management mode. `validate` checks schema matches entities without modifying it. | `validate` |
| `spring.jpa.properties.hibernate.jdbc.batch_size` | int | `50` | Number of statements to batch before flushing to the database. | `50` |
| `spring.jpa.properties.hibernate.order_inserts` | boolean | `true` | Reorder INSERT statements to maximise batch effectiveness. | `true` |
| `spring.jpa.properties.hibernate.order_updates` | boolean | `true` | Reorder UPDATE statements to maximise batch effectiveness. | `true` |
| `spring.jpa.properties.hibernate.generate_statistics` | boolean | `false` | Emit Hibernate statistics to logs. Disable in production. | `false` |

**Read-only replica** (optional, for read scaling):

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `spring.read-datasource.url` | string | `jdbc:postgresql://${DB_READ_HOST:localhost}:${DB_READ_PORT:5433}/${DB_NAME:yawl}` | JDBC URL for the read-only replica. Resolved from `DB_READ_HOST`, `DB_READ_PORT`. | `jdbc:postgresql://replica:5433/yawl` |
| `spring.read-datasource.username` | string | `${DB_READ_USER:yawl_readonly}` | Username for read-only replica. | `yawl_readonly` |
| `spring.read-datasource.password` | string | `${DB_READ_PASSWORD:changeme}` | Password for read-only replica. | _(set via vault)_ |
| `spring.read-datasource.hikari.pool-name` | string | `YAWLReadOnlyPool` | Name for the read-only connection pool. | `YAWLReadOnlyPool` |
| `spring.read-datasource.hikari.maximum-pool-size` | int | `10` | Maximum connections in read-only pool. | `10` |
| `spring.read-datasource.hikari.minimum-idle` | int | `2` | Minimum idle connections in read-only pool. | `2` |
| `spring.read-datasource.hikari.read-only` | boolean | `true` | Marks all connections in this pool as read-only. | `true` |

---

## 3. Security

Properties for TLS, authentication, and session management.

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `yawl.jwt.secret` | string | _(required)_ | JVM system property for JWT HMAC-SHA256 signing key. Minimum 32 bytes / 256 bits. Falls back to env var `YAWL_JWT_SECRET`. Engine throws `IllegalStateException` at startup if unset. | _(generate: `openssl rand -base64 32`)_ |
| `server.ssl.enabled` | boolean | `false` | Enable HTTPS. Requires `server.ssl.key-store` and `server.ssl.key-store-password`. | `true` |
| `server.ssl.key-store` | string | _(none)_ | Path to keystore file (PKCS12 or JKS). | `/app/config/keystore.p12` |
| `server.ssl.key-store-type` | string | `PKCS12` | Keystore format. `PKCS12` is preferred over `JKS`. | `PKCS12` |
| `server.ssl.key-store-password` | string | _(none)_ | Keystore decryption password. Do not commit to source control. | _(set via vault)_ |

**Mail service system properties** (read by `MailService.java` and `MailServiceGateway.java`):

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `yawl.mail.<name>` | string | _(none)_ | Generic mail service property. Replace `<name>` with a specific setting name (e.g., `host`, `port`, `user`, `password`). | `yawl.mail.host=smtp.example.com` |

---

## 4. Resource Service

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `RESOURCE_SERVICE_URL` | string | `http://resource-service:8080/ib` | Engine-side URL of the resource service Interface B endpoint. Resolved from env var in `Dockerfile.engine`. | `http://resource-service:8080/ib` |
| `ENGINE_URL` | string | `http://engine:8080/ib` | Resource service's URL of the engine Interface B endpoint. Resolved from env var in `Dockerfile.resourceService`. | `http://engine:8080/ib` |

---

## 5. MCP Integration

Properties for the YAWL Model Context Protocol server (`YawlMcpServer`, Spring variant at `YawlMcpSpringApplication`).

All properties in the `yawl.mcp.*` namespace are read from `src/org/yawlfoundation/yawl/integration/mcp/spring/application.yml`.

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `yawl.mcp.enabled` | boolean | `true` | Enable or disable the YAWL MCP integration. | `true` |
| `yawl.mcp.engine-url` | string | `${YAWL_ENGINE_URL:http://localhost:8080/yawl}` | Base URL of the YAWL engine REST API. Resolved from env var `YAWL_ENGINE_URL`. | `http://engine:8080/yawl` |
| `yawl.mcp.username` | string | `${YAWL_USERNAME:admin}` | Engine admin username for MCP operations. Resolved from env var `YAWL_USERNAME`. | `admin` |
| `yawl.mcp.password` | string | `${YAWL_PASSWORD:YAWL}` | Engine admin password for MCP operations. Resolved from env var `YAWL_PASSWORD`. Never use the default in production. | _(set via vault)_ |
| `yawl.mcp.transport` | string | `stdio` | MCP transport protocol. Options: `stdio` (for Claude Desktop), `http` (for networked deployments). | `stdio` |
| `yawl.mcp.http.enabled` | boolean | `false` | Enable HTTP transport. Requires `yawl.mcp.transport=http`. | `true` |
| `yawl.mcp.http.port` | int | `8081` | HTTP transport listen port. | `8081` |
| `yawl.mcp.http.path` | string | `/mcp` | HTTP transport endpoint path. | `/mcp` |
| `yawl.mcp.zai.enabled` | boolean | `true` | Enable Z.AI natural language workflow integration. | `true` |
| `yawl.mcp.zai.api-key` | string | `${ZAI_API_KEY:}` | Z.AI API key. Also checks env var `ZHIPU_API_KEY` as fallback. Empty string disables Z.AI. | _(set via vault)_ |
| `yawl.mcp.connection.retry-attempts` | int | `3` (dev: `1`, prod: `5`) | Number of connection retry attempts to the engine on failure. | `3` |
| `yawl.mcp.connection.retry-delay-ms` | int (ms) | `1000` (prod: `2000`) | Delay between consecutive retry attempts. | `1000` |
| `yawl.mcp.connection.timeout-ms` | int (ms) | `5000` (prod: `10000`) | HTTP connection and read timeout for engine API calls. | `5000` |

**Resilience4j circuit breaker** (MCP → engine calls):

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `resilience4j.circuitbreaker.instances.yawlEngine.slidingWindowSize` | int | `10` | Number of calls in the sliding window for failure rate calculation. | `10` |
| `resilience4j.circuitbreaker.instances.yawlEngine.minimumNumberOfCalls` | int | `5` | Minimum calls before circuit breaker evaluates failure rate. | `5` |
| `resilience4j.circuitbreaker.instances.yawlEngine.failureRateThreshold` | int (%) | `50` | Percentage failure rate that opens the circuit. | `50` |
| `resilience4j.circuitbreaker.instances.yawlEngine.waitDurationInOpenState` | duration | `10s` | Time circuit stays open before transitioning to half-open. | `10s` |
| `resilience4j.retry.instances.yawlEngine.maxAttempts` | int | `3` | Maximum retry attempts for failed engine calls. | `3` |
| `resilience4j.retry.instances.yawlEngine.waitDuration` | duration | `1000ms` | Base wait between retries. | `1000ms` |
| `resilience4j.retry.instances.yawlEngine.exponentialBackoffMultiplier` | double | `2` | Multiplier for exponential backoff between retries. | `2` |

---

## 6. A2A Integration

Properties for the YAWL Agent-to-Agent server (`YawlA2AServer`). All configuration is read from environment variables; no `application.yml` is used for A2A.

| Property Key (env var) | Type | Default | Description | Example |
|---|---|---|---|---|
| `A2A_PORT` | int | `8081` | TCP port the A2A HTTP server listens on. | `8081` |
| `A2A_JWT_SECRET` | string | _(required if using JWT auth)_ | HMAC-SHA256 signing key for JWT Bearer token authentication. Minimum 32 ASCII characters. Generate with `openssl rand -base64 32`. | _(set via vault)_ |
| `A2A_JWT_ISSUER` | string | _(none)_ | Expected `iss` claim value in JWT tokens. When set, tokens with a different issuer are rejected. Recommended in multi-tenant deployments. | `yawl-auth` |
| `A2A_JWT_VALIDITY_MS` | int (ms) | `3600000` | Validity period for server-issued JWT tokens. Default is 1 hour. | `3600000` |
| `A2A_API_KEY_MASTER` | string | _(required if using API key auth)_ | Master HMAC key used to derive stored API key digests. Minimum 16 characters. Changing this key invalidates all registered API keys. | _(set via vault)_ |
| `A2A_API_KEY` | string | _(required if using API key auth)_ | Default API key auto-registered as principal `api-key-client`. Clients send this in the `X-API-Key` header. | _(set via vault)_ |
| `A2A_SPIFFE_TRUST_DOMAIN` | string | `yawl.cloud` | SPIFFE trust domain to accept client certificates from. Used by `SpiffeAuthenticationProvider`. | `yawl.cloud` |
| `SPIFFE_ENDPOINT_SOCKET` | string | `unix:///run/spire/sockets/agent.sock` | SPIRE Agent socket path for SVID retrieval. | `unix:///run/spire/sockets/agent.sock` |
| `YAWL_ENGINE_URL` | string | _(required)_ | Base URL of the YAWL engine for A2A workflow operations. | `http://localhost:8080/yawl` |
| `YAWL_USERNAME` | string | `admin` | Engine admin username for A2A workflow operations. | `admin` |
| `YAWL_PASSWORD` | string | _(required)_ | Engine admin password for A2A workflow operations. | _(set via vault)_ |

**A2A authentication note:** At least one of `A2A_JWT_SECRET`, `A2A_API_KEY_MASTER`+`A2A_API_KEY`, or SPIRE-backed mTLS must be configured. The server refuses to start if none are set. There is no anonymous fallback.

---

## 7. Observability and Logging

### Spring Boot Actuator

Properties from `src/main/resources/application.yml`:

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `management.endpoints.web.base-path` | string | `/actuator` | Base path for all actuator endpoints. | `/actuator` |
| `management.endpoints.web.exposure.include` | string | `health,metrics,prometheus,info,env` | Comma-separated list of exposed actuator endpoints. | `health,metrics,prometheus` |
| `management.endpoint.health.enabled` | boolean | `true` | Enable the health endpoint. | `true` |
| `management.endpoint.health.show-details` | string | `always` | When to show health details. Options: `never`, `when-authorized`, `always`. | `when-authorized` |
| `management.endpoint.health.probes.enabled` | boolean | `true` | Enable Kubernetes-style liveness and readiness probes at `/actuator/health/liveness` and `/actuator/health/readiness`. | `true` |
| `management.health.diskspace.enabled` | boolean | `true` | Include disk space check in health status. | `true` |
| `management.health.diskspace.threshold` | string | `10MB` | Minimum free disk space before health reports DOWN. | `10MB` |
| `management.metrics.export.prometheus.enabled` | boolean | `true` | Enable Prometheus metrics scraping endpoint at `/actuator/prometheus`. | `true` |
| `management.metrics.export.prometheus.step` | duration | `1m` | Prometheus metrics publication interval. | `1m` |
| `management.metrics.tags.application` | string | `${spring.application.name}` | Tag added to all metrics for application identification. | `yawl-engine` |
| `management.metrics.tags.environment` | string | `${ENVIRONMENT:development}` | Tag added to all metrics for environment identification. | `production` |
| `management.metrics.tags.version` | string | `5.2` | Tag added to all metrics for version identification. | `5.2` |

### OpenTelemetry

Properties from `observability/opentelemetry/application-otel.properties` and `@Value` annotations in `OpenTelemetryConfig.java`:

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `yawl.observability.enabled` | boolean | `true` | Master switch for YAWL observability instrumentation. | `true` |
| `yawl.observability.service.name` | string | `yawl-engine` | OpenTelemetry `service.name` resource attribute. | `yawl-engine` |
| `yawl.observability.service.version` | string | `5.2` | OpenTelemetry `service.version` resource attribute. | `5.2` |
| `yawl.observability.service.namespace` | string | `yawl-workflows` | OpenTelemetry `service.namespace` resource attribute. | `yawl-workflows` |
| `yawl.observability.deployment.environment` | string | `production` | OpenTelemetry `deployment.environment` resource attribute. | `production` |
| `yawl.observability.otlp.endpoint` | string | `http://localhost:4317` | OTLP gRPC endpoint for trace and metric export. | `http://otel-collector:4317` |
| `yawl.observability.traces.sampler.ratio` | double | `0.1` | Fraction of traces to sample. `1.0` = 100%, `0.1` = 10%. | `0.1` |
| `yawl.observability.metrics.export.interval` | int (seconds) | `60` | Metrics export interval in seconds. | `60` |
| `yawl.observability.prometheus.port` | int | `9464` | Prometheus pull port when using Prometheus exporter. | `9464` |
| `yawl.observability.exporter.type` | string | `otlp` | Exporter backend. Options: `otlp`, `logging`, `prometheus`. | `otlp` |
| `otel.sdk.disabled` | boolean | `false` | Disable the OTel SDK entirely. | `false` |
| `otel.traces.exporter` | string | `otlp` | Trace exporter. Options: `otlp`, `jaeger`, `logging`, `none`. | `otlp` |
| `otel.metrics.exporter` | string | `otlp,prometheus` | Metrics exporter. Multiple values comma-separated. | `otlp,prometheus` |
| `otel.logs.exporter` | string | `otlp` | Log exporter. Options: `otlp`, `logging`, `none`. | `otlp` |
| `otel.resource.attributes` | string | _(none)_ | Comma-separated `key=value` pairs added to all telemetry. | `service.name=yawl-engine,deployment.environment=production` |
| `otel.service.name` | string | `yawl-engine` | JVM system property alternative to `otel.resource.attributes`. Consumed by `OpenTelemetryInitializer`. | `yawl-engine` |
| `otel.service.version` | string | `6.0.0` | JVM system property for service version in telemetry. | `6.0.0` |
| `otel.exporter.otlp.endpoint` | string | _(none)_ | JVM system property override for OTLP endpoint. Consumed by `OpenTelemetryInitializer`. | `http://otel-collector:4317` |
| `management.tracing.enabled` | boolean | `true` | Enable Micrometer distributed tracing integration. | `true` |
| `management.tracing.sampling.probability` | double | `0.1` | Trace sampling probability via Micrometer. | `0.1` |

### Logging

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `logging.level.root` | string | `INFO` | Root logging level. Options: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `OFF`. | `WARN` |
| `logging.level.org.yawlfoundation.yawl.engine` | string | `INFO` | YAWL engine package logging level. | `DEBUG` |
| `logging.level.org.yawlfoundation.yawl.integration.mcp` | string | `INFO` | MCP integration logging level. | `INFO` |
| `logging.level.com.zaxxer.hikari` | string | `INFO` | HikariCP connection pool logging level. | `INFO` |
| `logging.level.org.hibernate.SQL` | string | `WARN` | Hibernate SQL statement logging level. Set to `DEBUG` to log all SQL. | `WARN` |
| `logging.pattern.console` | string | `%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n` | Console log output pattern. | _(default)_ |

---

## 8. Performance

JVM system properties that affect virtual thread scheduling and memory:

| Property Key | Type | Default | Description | Example |
|---|---|---|---|---|
| `jdk.virtualThreadScheduler.parallelism` | int | `200` | Number of carrier threads for the virtual thread scheduler. Increase for workloads with many concurrent cases. | `200` |
| `jdk.virtualThreadScheduler.maxPoolSize` | int | `256` | Maximum carrier thread pool size. Must be >= `parallelism`. | `256` |
| `jdk.tracePinnedThreads` | string | `short` | Log thread-pinning events. `short` = abbreviated, `full` = full stack traces, absent = disabled. | `short` |
| `XX:+UseContainerSupport` | flag | enabled | JVM detects container CPU/memory limits instead of host values. Always enable in Docker/Kubernetes. | _(JVM flag)_ |
| `XX:MaxRAMPercentage` | double | `75.0` | Maximum heap as percentage of container memory. | `75.0` |
| `XX:InitialRAMPercentage` | double | `50.0` | Initial heap as percentage of container memory. | `50.0` |
| `XX:+UseZGC -XX:+ZGenerational` | flag | _(off)_ | Generational ZGC garbage collector. Recommended for production; provides low-latency GC with large heaps. | _(JVM flag)_ |
| `XX:+UseCompactObjectHeaders` | flag | _(off)_ | Java 25 compact object headers. Saves 4-8 bytes per object (~5-10% throughput improvement). | _(JVM flag)_ |
