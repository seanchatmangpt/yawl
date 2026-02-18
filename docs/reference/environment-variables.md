# YAWL Environment Variables Reference

**Document type:** Reference
**Audience:** AI coding agents working on the YAWL codebase
**Purpose:** Complete lookup table for all environment variables used by YAWL Docker and Kubernetes deployments. Consult this when configuring containers, writing Kubernetes manifests, or debugging deployment issues.

Sources authoritative for this document:
- `/home/user/yawl/.env.example`
- `/home/user/yawl/.env.production.example`
- `/home/user/yawl/.env.staging.example`
- `/home/user/yawl/docker/.env.example`
- `/home/user/yawl/docker-compose.yml`
- `/home/user/yawl/containerization/Dockerfile.engine`
- `/home/user/yawl/containerization/Dockerfile.resourceService`
- `/home/user/yawl/containerization/Dockerfile.workletService`
- `/home/user/yawl/Dockerfile`
- `System.getenv()` calls in integration source files

---

## Notation

- **Required** means the service refuses to start or a feature is non-functional if the variable is absent.
- **Optional** means the variable has a safe default or gates an optional feature.
- **Property mapping** identifies the Spring Boot property or Java code path that consumes the variable.

---

## 1. Engine Container

Variables consumed by the engine Tomcat/Spring container, sourced from `Dockerfile.engine` `ENV` declarations and `docker-compose.yml` `environment` blocks.

### Core Engine

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `CATALINA_HOME` | string | `/opt/tomcat` | Required (Tomcat) | JVM system property `catalina.base` | Tomcat installation directory. Used by `HttpURLValidator` to locate config files and by `DocumentStore` to find `hibernate.properties`. |
| `JAVA_OPTS` | string | See Dockerfiles | Optional | JVM command line | JVM startup flags. Includes `-Xms`, `-Xmx`, `-XX:+UseContainerSupport`, and virtual thread settings. Override to tune heap and GC. |
| `TZ` | string | `UTC` | Optional | JVM timezone | Container timezone. Always set to `UTC` for consistent log timestamps. |
| `YAWL_VERSION` | string | `5.2` | Optional | Label only | Image version label. Informational. |
| `SPRING_PROFILES_ACTIVE` | string | `production` | Optional | `spring.profiles.active` | Active Spring Boot profiles. Controls which configuration blocks activate. Options: `development`, `staging`, `production`. |
| `SPRING_APPLICATION_NAME` | string | `yawl-engine` | Optional | `spring.application.name` | Application name in logs, metrics, and actuator info endpoint. |
| `ENABLE_PERSISTENCE` | boolean | `true` | Optional | Engine startup | Enable Hibernate-backed workflow persistence. Set `false` only for stateless testing. |
| `ENABLE_LOGGING` | boolean | `true` | Optional | Engine startup | Enable XES event logging to the database. |
| `LOG_LEVEL` | string | `WARN` | Optional | `logging.level.root` | Root logging level for the engine. Options: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`. |

### Database

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `DB_TYPE` | string | `postgres` | Optional | Engine datasource config | Database backend type. Options: `postgres`, `h2`. Use `h2` for local development only. |
| `DB_HOST` | string | `postgres` | Required (production) | `spring.datasource.url` | Hostname or IP of the PostgreSQL server. In Docker Compose, use the service name (e.g., `postgres`). |
| `DB_PORT` | int | `5432` | Optional | `spring.datasource.url` | PostgreSQL server port. |
| `DB_NAME` | string | `yawl` | Optional | `spring.datasource.url` | Database name. |
| `DB_USER` | string | `yawl` | Optional | `spring.datasource.username` | Database login username. |
| `DB_PASSWORD` | string | `yawl` | Required (production) | `spring.datasource.password` | Database login password. The Dockerfile default `yawl` must be changed before production deployment. |
| `YAWL_JDBC_DRIVER` | string | `org.postgresql.Driver` | Optional | JDBC driver class | Override the JDBC driver. Change when switching database backends. |
| `YAWL_JDBC_URL` | string | _(derived from DB_*)_ | Optional | `spring.datasource.url` | Full JDBC URL override. When set, takes precedence over `DB_HOST`/`DB_PORT`/`DB_NAME`. |
| `YAWL_JDBC_USER` | string | _(falls back to DB_USER)_ | Optional | `spring.datasource.username` | JDBC username override. Falls back to `DB_USER` when absent. |
| `YAWL_JDBC_PASSWORD` | string | _(falls back to DB_PASSWORD)_ | Optional | `spring.datasource.password` | JDBC password override. Falls back to `DB_PASSWORD` when absent. |

### HikariCP Connection Pool

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `HIKARI_MAXIMUM_POOL_SIZE` | int | `20` | Optional | `spring.datasource.hikari.maximum-pool-size` | Maximum connections in the primary pool. |
| `HIKARI_MINIMUM_IDLE` | int | `5` | Optional | `spring.datasource.hikari.minimum-idle` | Minimum idle connections. |
| `HIKARI_IDLE_TIMEOUT` | int (ms) | `300000` | Optional | `spring.datasource.hikari.idle-timeout` | Idle connection timeout. |
| `HIKARI_CONNECTION_TIMEOUT` | int (ms) | `30000` | Optional | `spring.datasource.hikari.connection-timeout` | Time to wait for a connection from the pool. |
| `DB_POOL_MAX_SIZE` | int | `20` | Optional | `spring.datasource.hikari.maximum-pool-size` | Alternate name for max pool size (used in `docker/.env.example`). |
| `DB_POOL_MIN_IDLE` | int | `5` | Optional | `spring.datasource.hikari.minimum-idle` | Alternate name for minimum idle (used in `docker/.env.example`). |
| `DB_POOL_CONNECTION_TIMEOUT` | int (ms) | `30000` | Optional | `spring.datasource.hikari.connection-timeout` | Alternate name for connection timeout. |
| `DB_POOL_IDLE_TIMEOUT` | int (ms) | `600000` | Optional | `spring.datasource.hikari.idle-timeout` | Alternate name for idle timeout. |
| `DB_POOL_MAX_LIFETIME` | int (ms) | `1800000` | Optional | `spring.datasource.hikari.max-lifetime` | Maximum connection lifetime. |

### H2 Development Database

Only used when `DB_TYPE=h2` (development profile).

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `H2_TCP_PORT` | int | `9092` | Optional | H2 server startup | H2 TCP server port for external JDBC connections. |
| `H2_WEB_PORT` | int | `8082` | Optional | H2 server startup | H2 web console port. |
| `H2_DATA_DIR` | string | `/app/data` | Optional | H2 server startup | Directory for H2 database files. |

### YAWL Engine Settings

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `YAWL_PERSISTENCE_ENABLED` | boolean | `true` | Optional | Engine config | Enable workflow persistence. Mirrors `ENABLE_PERSISTENCE`. |
| `YAWL_SPEC_CACHE_ENABLED` | boolean | `true` | Optional | Engine config | Enable in-memory caching of loaded specifications. |
| `YAWL_MAX_CONCURRENT_CASES` | int | `1000` | Optional | Engine config | Maximum number of simultaneously running workflow cases. |
| `YAWL_WORKITEM_QUEUE_CAPACITY` | int | `500` | Optional | Engine config | Work item queue capacity before new items are rejected. |
| `YAWL_AUTO_CLEANUP_ENABLED` | boolean | `true` | Optional | Engine config | Automatically remove completed case data after `YAWL_CASE_RETENTION_DAYS`. |
| `YAWL_CASE_RETENTION_DAYS` | int | `30` | Optional | Engine config | Days to retain completed case data before automatic cleanup. |
| `YAWL_SESSION_TIMEOUT_MINUTES` | int | `60` | Optional | Engine config | Engine session inactivity timeout in minutes. |
| `YAWL_SESSION_CACHE_SIZE` | int | `1000` | Optional | Engine config | Maximum number of concurrent sessions in the session cache. |
| `RESOURCE_SERVICE_URL` | string | `http://resource-service:8080/ib` | Optional | Engine config | URL of the resource service Interface B endpoint. |

### Authentication and Security

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `YAWL_USERNAME` | string | `admin` | Optional | Engine admin | YAWL admin username for client tools and integration modules. |
| `YAWL_PASSWORD` | string | _(none)_ | Required (production) | `InterfaceBWebsideController`, all launchers | YAWL admin password. Falls back to system property `yawl.engine.password`. |
| `YAWL_JWT_SECRET` | string | _(none)_ | Required | `JwtManager` | HMAC-SHA256 key for JWT session tokens. Falls back to system property `yawl.jwt.secret`. Minimum 32 bytes. Engine throws `IllegalStateException` at startup if unset. |
| `JWT_SECRET` | string | _(none)_ | Required | Session auth | JWT secret used in Docker Compose deployment. Equivalent to `YAWL_JWT_SECRET` in some configurations. |
| `JWT_SIGNING_KEY` | string | _(none)_ | Required (production) | Session auth | Alternate name for JWT signing key used in production env example. |
| `JWT_EXPIRATION` | int (seconds) | `86400` | Optional | `JwtManager` | JWT token validity period. Default is 24 hours. |
| `SESSION_TIMEOUT_MINUTES` | int | `30` | Optional | Session config | Session inactivity timeout in minutes. |
| `SERVER_SSL_ENABLED` | boolean | `false` | Optional | `server.ssl.enabled` | Enable HTTPS. Requires `SERVER_SSL_KEYSTORE_PATH` and `SERVER_SSL_KEYSTORE_PASSWORD`. |
| `SERVER_SSL_KEYSTORE_PATH` | string | `/app/config/keystore.p12` | Conditional | `server.ssl.key-store` | Path to TLS keystore. Required when `SERVER_SSL_ENABLED=true`. |
| `SERVER_SSL_KEYSTORE_PASSWORD` | string | _(none)_ | Conditional | `server.ssl.key-store-password` | Keystore password. Required when `SERVER_SSL_ENABLED=true`. |
| `SERVER_SSL_KEYSTORE_TYPE` | string | `PKCS12` | Optional | `server.ssl.key-store-type` | Keystore format. `PKCS12` preferred over `JKS`. |
| `CORS_ALLOWED_ORIGINS` | string | `*` | Optional | CORS config | Comma-separated allowed CORS origins. Set explicitly in production. |
| `CSRF_ENABLED` | boolean | `true` | Optional | Security config | Enable CSRF protection for web endpoints. |
| `SECURITY_HEADERS_ENABLED` | boolean | `true` | Optional | Security config | Add security response headers (X-Content-Type-Options, X-Frame-Options, etc.). |

### SPIFFE/SPIRE Identity

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `SPIFFE_ENABLED` | boolean | `false` | Optional | `SpiffeWorkloadApiClient` | Enable SPIFFE workload identity. Requires a SPIRE Agent running on the host. |
| `SPIFFE_ENDPOINT_SOCKET` | string | `unix:///run/spire/sockets/agent.sock` | Conditional | `SpiffeWorkloadApiClient.SOCKET_ENV_VAR` | UNIX socket path for the SPIRE Agent workload API. Required when `SPIFFE_ENABLED=true`. |
| `SPIFFE_TRUST_DOMAIN` | string | `example.org` | Conditional | `SpiffeFederationConfig.CONFIG_ENV_VAR` | Trust domain for SPIFFE IDs. Required when SPIFFE auth is active. |

### JVM and Performance

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `JVM_MAX_RAM_PERCENTAGE` | double | `75.0` | Optional | `-XX:MaxRAMPercentage` | Max heap as percentage of container memory. Passed via `JAVA_OPTS`. |
| `JVM_INITIAL_RAM_PERCENTAGE` | double | `50.0` | Optional | `-XX:InitialRAMPercentage` | Initial heap as percentage of container memory. Passed via `JAVA_OPTS`. |
| `VIRTUAL_THREAD_PARALLELISM` | int | `200` | Optional | `-Djdk.virtualThreadScheduler.parallelism` | Carrier thread count for the virtual thread scheduler. Passed via `JAVA_OPTS`. |
| `VIRTUAL_THREAD_MAX_POOL_SIZE` | int | `256` | Optional | `-Djdk.virtualThreadScheduler.maxPoolSize` | Maximum carrier thread pool. Must be >= `VIRTUAL_THREAD_PARALLELISM`. |
| `VIRTUAL_THREAD_TRACE_PINNED` | string | `short` | Optional | `-Djdk.tracePinnedThreads` | Log virtual thread pinning events. Options: `short`, `full`, absent (disabled). |
| `JVM_HEAP_DUMP_ON_OOM` | boolean | `true` | Optional | `-XX:+HeapDumpOnOutOfMemoryError` | Enable heap dump on OutOfMemoryError. |
| `JVM_HEAP_DUMP_PATH` | string | `/app/logs/heap-dump.hprof` | Optional | `-XX:HeapDumpPath` | Path for heap dump files. |
| `JVM_EXIT_ON_OOM` | boolean | `true` | Optional | `-XX:+ExitOnOutOfMemoryError` | Terminate the JVM immediately on OutOfMemoryError. Recommended; allows the orchestrator to restart the container. |
| `JVM_EXTRA_OPTS` | string | _(none)_ | Optional | `JAVA_OPTS` | Additional JVM options appended to the default `JAVA_OPTS`. |
| `CONTAINER_MEMORY` | string | `2g` | Optional | Docker resource limits | Container memory limit (informational for documentation; actual limit is set in compose/k8s). |
| `CONTAINER_CPU` | double | `2.0` | Optional | Docker resource limits | Container CPU limit (informational). |

### Observability

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `ENVIRONMENT` | string | `development` | Optional | `management.metrics.tags.environment` | Deployment environment tag on all metrics. |
| `OTEL_ENABLED` | boolean | `false` | Optional | `otel.sdk.disabled` (inverted) | Enable OpenTelemetry instrumentation. |
| `OTEL_SERVICE_NAME` | string | `yawl-engine` | Optional | `otel.service.name` | Service name in all telemetry. |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | string | `http://otel-collector:4317` | Conditional | `otel.exporter.otlp.endpoint` | OTLP gRPC endpoint for traces and metrics. Required when `OTEL_ENABLED=true`. |
| `OTEL_TRACES_EXPORTER` | string | `otlp` | Optional | `otel.traces.exporter` | Trace exporter type. Options: `otlp`, `jaeger`, `zipkin`, `logging`, `none`. |
| `OTEL_METRICS_EXPORTER` | string | `prometheus` | Optional | `otel.metrics.exporter` | Metrics exporter type. Options: `otlp`, `prometheus`, `logging`, `none`. |
| `OTEL_LOGS_EXPORTER` | string | `otlp` | Optional | `otel.logs.exporter` | Log exporter type. Options: `otlp`, `logging`, `none`. |
| `OTEL_TRACES_SAMPLER_ARG` | double | `0.1` | Optional | Sampler config | Trace sampling probability. `1.0` = 100%, `0.1` = 10%. |
| `OTEL_RESOURCE_ATTRIBUTES` | string | _(none)_ | Optional | `otel.resource.attributes` | Comma-separated `key=value` resource attributes added to all telemetry. |
| `OTEL_SDK_ENABLED` | string | _(none)_ | Optional | `AgentTracer` | When set to `true`, enables OTel SDK in the order fulfilment agent tracer. |
| `METRICS_ENABLED` | boolean | `false` | Optional | Prometheus config | Enable Prometheus metrics endpoint. |
| `METRICS_PORT` | int | `9090` | Optional | Management port | Port for Prometheus metrics. |
| `PROMETHEUS_ENABLED` | boolean | `true` | Optional | Prometheus config | Expose `/actuator/prometheus` endpoint. |
| `PROMETHEUS_PATH` | string | `/actuator/prometheus` | Optional | Prometheus config | Actuator path for Prometheus scraping. |
| `MANAGEMENT_PORT` | int | `9090` | Optional | Management server | Port for the Spring Boot actuator management server (when separate from main port). |
| `MANAGEMENT_HEALTH_SHOW_DETAILS` | string | `when-authorized` | Optional | `management.endpoint.health.show-details` | Health detail visibility. Options: `never`, `when-authorized`, `always`. |
| `MANAGEMENT_HEALTH_PROBES_ENABLED` | boolean | `true` | Optional | `management.endpoint.health.probes.enabled` | Enable Kubernetes liveness and readiness probe endpoints. |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | string | `health,info,metrics,prometheus` | Optional | `management.endpoints.web.exposure.include` | Comma-separated list of exposed actuator endpoints. |

### Logging (Engine Container)

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `LOG_LEVEL` | string | `WARN` | Optional | `logging.level.root` | Root logging level. Primary variable in engine Dockerfile. |
| `LOG_LEVEL_ROOT` | string | `INFO` | Optional | `logging.level.root` | Alternate root logging level variable (used in `docker/.env.example`). |
| `LOG_LEVEL_YAWL` | string | `DEBUG` | Optional | `logging.level.org.yawlfoundation` | YAWL package logging level. |
| `LOG_LEVEL_YAWL_FOUNDATION` | string | `DEBUG` | Optional | `LOGGING_LEVEL_ORG_YAWLFOUNDATION` (compose env) | YAWL Foundation logging level, set in docker-compose.yml. |
| `LOG_LEVEL_HIBERNATE` | string | `WARN` | Optional | `logging.level.org.hibernate` | Hibernate ORM logging level. |
| `LOG_LEVEL_HIBERNATE_SQL` | string | `OFF` | Optional | `logging.level.org.hibernate.SQL` | Hibernate SQL logging level. Set to `DEBUG` to log all generated SQL. |
| `LOG_LEVEL_SPRING` | string | `WARN` | Optional | `logging.level.org.springframework` | Spring Framework logging level. |
| `LOG_LEVEL_OTEL` | string | `INFO` | Optional | `logging.level.io.opentelemetry` | OpenTelemetry SDK logging level. |
| `LOG_LEVEL_MCP` | string | `INFO` | Optional | `logging.level.org.yawlfoundation.yawl.integration.mcp` | MCP integration logging level. |
| `LOG_LEVEL_A2A` | string | `INFO` | Optional | `logging.level.org.yawlfoundation.yawl.integration.a2a` | A2A integration logging level. |
| `LOG_DIR` | string | `/app/logs` | Optional | Logging config | Log file output directory inside the container. |
| `LOG_JSON_ENABLED` | boolean | `false` | Optional | Logging config | Enable structured JSON log output for log aggregation pipelines. |
| `DEBUG_ENABLED` | boolean | `false` | Optional | Logging config | Enable verbose debug logging. Never enable in production. |

### Path Settings

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `SPEC_PATH` | string | `/app/specifications` | Optional | `OrderfulfillmentLauncher`, `PermutationRunner` | Directory for YAWL workflow specification files. Also used by launchers to locate specs for upload. |
| `DATA_PATH` | string | `/app/data` | Optional | Engine config | Data directory for file-based persistence. |
| `TEMP_PATH` | string | `/app/temp` | Optional | Engine config | Directory for temporary files (used by JVM `-Djava.io.tmpdir`). |
| `CONFIG_PATH` | string | `/app/config` | Optional | Engine config | Configuration files directory inside the container. |

---

## 2. Resource Service Container

Variables consumed by the resource service container, sourced from `Dockerfile.resourceService`.

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `CATALINA_HOME` | string | `/opt/tomcat` | Required | Tomcat | Tomcat installation directory. |
| `JAVA_OPTS` | string | `-Xms256m -Xmx1024m -Djava.awt.headless=true -Dfile.encoding=UTF-8` | Optional | JVM | JVM startup flags. Resource service has lower heap defaults than the engine (256m/1024m vs 512m/2048m). |
| `TZ` | string | `UTC` | Optional | JVM timezone | Container timezone. |
| `YAWL_VERSION` | string | `5.2` | Optional | Label | Image version label. |
| `DB_TYPE` | string | `postgres` | Optional | Datasource | Database type. |
| `DB_HOST` | string | `postgres` | Required | Datasource | PostgreSQL hostname. |
| `DB_PORT` | int | `5432` | Optional | Datasource | PostgreSQL port. |
| `DB_NAME` | string | `yawl` | Optional | Datasource | Database name. |
| `DB_USER` | string | `yawl` | Optional | Datasource | Database username. |
| `DB_PASSWORD` | string | `yawl` | Required | Datasource | Database password. Must be changed from the default. |
| `ENGINE_URL` | string | `http://engine:8080/ib` | Optional | Resource service client | URL of the engine Interface B endpoint that the resource service connects to. |
| `LOG_LEVEL` | string | `INFO` | Optional | `logging.level.root` | Root logging level. |

---

## 3. Worklet Service Container

Variables consumed by the worklet service container, sourced from `Dockerfile.workletService`.

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `CATALINA_HOME` | string | `/opt/tomcat` | Required | Tomcat | Tomcat installation directory. |
| `JAVA_OPTS` | string | `-Xms256m -Xmx1024m -Djava.awt.headless=true -Dfile.encoding=UTF-8` | Optional | JVM | JVM startup flags. |
| `TZ` | string | `UTC` | Optional | JVM timezone | Container timezone. |
| `YAWL_VERSION` | string | `5.2` | Optional | Label | Image version label. |
| `DB_TYPE` | string | `postgres` | Optional | Datasource | Database type. |
| `DB_HOST` | string | `postgres` | Required | Datasource | PostgreSQL hostname. |
| `DB_PORT` | int | `5432` | Optional | Datasource | PostgreSQL port. |
| `DB_NAME` | string | `yawl` | Optional | Datasource | Database name. |
| `DB_USER` | string | `yawl` | Optional | Datasource | Database username. |
| `DB_PASSWORD` | string | `yawl` | Required | Datasource | Database password. Must be changed from the default. |
| `ENGINE_URL` | string | `http://engine:8080/ib` | Optional | Worklet client | URL of the engine Interface B endpoint. |
| `LOG_LEVEL` | string | `INFO` | Optional | `logging.level.root` | Root logging level. |
| `WORKLET_ENABLED` | boolean | `true` | Optional | Worklet service | Enable the worklet exception handling service. |
| `WORKLET_EXCEPTION_HANDLING` | string | `log` | Optional | Worklet service | Exception handling mode. Options: `log`, `queue`, `reject`. |

---

## 4. PostgreSQL Container

Variables consumed by the `postgres:16-alpine` database container in docker-compose.yml.

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `POSTGRES_USER` | string | `yawl` | Optional | PostgreSQL init | Database superuser name created on first startup. |
| `POSTGRES_PASSWORD` | string | _(none)_ | Required | PostgreSQL init | Superuser password. Must be set before `docker compose up`. |
| `POSTGRES_DB` | string | `yawl` | Optional | PostgreSQL init | Database name created on first startup. |
| `PGDATA` | string | `/var/lib/postgresql/data/pgdata` | Optional | PostgreSQL data | Directory for PostgreSQL data files inside the container. |

---

## 5. MCP Server

Variables consumed by `YawlMcpServer` and `YawlMcpSpringApplication`.

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `YAWL_ENGINE_URL` | string | `http://localhost:8080/yawl` | Required | `YawlMcpServer`, `YawlMcpSpringApplication` | Base URL of the YAWL engine. Also consumed by launchers and order fulfilment agents. |
| `YAWL_USERNAME` | string | `admin` | Optional | `YawlMcpSpringApplication` | Engine admin username for MCP operations. |
| `YAWL_PASSWORD` | string | _(none)_ | Required | `YawlMcpSpringApplication` | Engine admin password for MCP operations. |
| `ZAI_API_KEY` | string | _(none)_ | Conditional | `ZaiService`, `ZaiFunctionService`, `ZaiHttpClient` | Z.AI API key. Required when `ZAI_ENABLED=true` or `yawl.mcp.zai.enabled=true`. Also read as `ZHIPU_API_KEY`. |
| `ZHIPU_API_KEY` | string | _(none)_ | Conditional | `ZaiService`, `YawlMcpConfiguration` | Alternative name for Z.AI API key. Checked when `ZAI_API_KEY` is not set. |
| `ZAI_MODEL` | string | _(none)_ | Optional | `ZaiService`, `ZaiFunctionService` | Z.AI model identifier to use. Defaults to a built-in value when unset. |
| `MCP_ENABLED` | boolean | `true` | Optional | `PartyAgent` | Enable MCP client within the order fulfilment agent. |
| `MCP_SERVER_URL` | string | _(none)_ | Conditional | Integration tests | MCP SSE server URL. Required in MCP HTTP transport integration tests. |
| `MCP_TRANSPORT` | string | `stdio` | Optional | MCP config | MCP transport type. Options: `stdio`, `http`. |
| `MCP_HTTP_PORT` | int | `8082` | Optional | MCP config | HTTP transport port. Used when `MCP_TRANSPORT=http`. |

---

## 6. A2A Server

Variables consumed by `YawlA2AServer`.

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `A2A_PORT` | int | `8081` | Optional | `YawlA2AServer` | TCP port for the A2A HTTP REST server. |
| `A2A_ENABLED` | boolean | `false` | Optional | A2A config | Enable the A2A server. |
| `YAWL_ENGINE_URL` | string | _(none)_ | Required | `YawlA2AServer` | Engine base URL for workflow operations. |
| `YAWL_USERNAME` | string | `admin` | Optional | `YawlA2AServer` | Engine username. |
| `YAWL_PASSWORD` | string | _(none)_ | Required | `YawlA2AServer` | Engine password. |
| `A2A_JWT_SECRET` | string | _(none)_ | Conditional | `JwtAuthenticationProvider` | HMAC-SHA256 signing key for JWT Bearer authentication. Minimum 32 bytes. Generate with `openssl rand -base64 32`. Required unless mTLS or API key auth is configured. |
| `A2A_JWT_ISSUER` | string | _(none)_ | Optional | `JwtAuthenticationProvider` | Expected `iss` claim. When set, tokens with a different issuer are rejected. |
| `A2A_JWT_VALIDITY_MS` | int (ms) | `3600000` | Optional | `JwtAuthenticationProvider` | Server-issued JWT validity period (1 hour default). |
| `A2A_API_KEY_MASTER` | string | _(none)_ | Conditional | `ApiKeyAuthenticationProvider` | HMAC master key for API key derivation. Minimum 16 characters. Required when using API key auth. |
| `A2A_API_KEY` | string | _(none)_ | Conditional | `ApiKeyAuthenticationProvider` | Default API key sent by clients in the `X-API-Key` header. Required when using API key auth. |
| `A2A_SPIFFE_TRUST_DOMAIN` | string | `yawl.cloud` | Optional | `SpiffeAuthenticationProvider` | SPIFFE trust domain to accept client certificates from. Active when SPIRE is available. |
| `SPIFFE_ENDPOINT_SOCKET` | string | `unix:///run/spire/sockets/agent.sock` | Optional | `SpiffeWorkloadApiClient` | SPIRE Agent socket path. |

**Critical note:** At least one of `A2A_JWT_SECRET`, `A2A_API_KEY_MASTER`+`A2A_API_KEY`, or SPIRE mTLS must be configured. The A2A server will not start if all three are absent.

---

## 7. Integration and Agent Variables

Variables used by integration launchers, order fulfilment agents, and CI/CD pipelines.

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `AGENT_PORT` | int | _(none)_ | Optional | `PartyAgent` | Port the agent HTTP listener binds to. |
| `AGENT_CAPABILITY` | string | _(none)_ | Optional | `AgentCapability` | Agent capability identifier string. |
| `AGENT_PEERS` | string | _(none)_ | Optional | `PartyAgent` | Comma-separated URLs of peer agents for A2A communication. |
| `A2A_AGENT_URL` | string | _(none)_ | Optional | Integration tests | A2A agent URL used in integration test self-play scenarios. |
| `ORDER_AGENT_URL` | string | _(none)_ | Optional | `SelfPlayTest` | Order agent URL for integration testing. |
| `INVENTORY_AGENT_URL` | string | _(none)_ | Optional | `SelfPlayTest` | Inventory agent URL for integration testing. |
| `PM4PY_AGENT_URL` | string | _(none)_ | Optional | `Pm4PyClient` | Process mining agent URL for XES export integration. |
| `OUTPUT_PATH` | string | _(none)_ | Optional | `XesExportLauncher` | File path for XES log export output. |
| `TIMEOUT_SEC` | int | _(none)_ | Optional | `OrderfulfillmentLauncher` | Case execution timeout in seconds for launchers. |
| `UPLOAD_SPEC` | boolean | `true` | Optional | `OrderfulfillmentLauncher`, `PermutationRunner` | Whether launchers upload the specification before launching cases. Set to `false` when spec is already loaded. |
| `SPEC_PATH` | string | _(none)_ | Optional | `OrderfulfillmentLauncher`, `PermutationRunner` | Path to the YAWL specification file for launchers. |
| `PERMUTATION_IDS` | string | _(none)_ | Optional | `PermutationRunner` | Comma-separated list of permutation IDs to run. Runs all permutations when unset. |
| `PERMUTATION_CONFIG` | string | _(none)_ | Optional | `PermutationRunner` | JSON permutation configuration override. |
| `OTEL_SDK_ENABLED` | boolean | `false` | Optional | `AgentTracer` | Enable OTel SDK in the order fulfilment agent tracer. |

---

## 8. Production Monitoring (Grafana/Prometheus)

Variables used by the observability stack in `docker-compose.yml` and `.env.production.example`.

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `GRAFANA_ADMIN_USER` | string | `admin` | Optional | Grafana `GF_SECURITY_ADMIN_USER` | Grafana admin username. Change from default. |
| `GRAFANA_ADMIN_PASSWORD` | string | `admin` | Required | Grafana `GF_SECURITY_ADMIN_PASSWORD` | Grafana admin password. Must be changed in production. |
| `PROMETHEUS_RETENTION` | string | `30d` | Optional | Prometheus config | Prometheus TSDB data retention period. |
| `PROMETHEUS_STORAGE_SIZE` | string | `10GB` | Optional | Prometheus config | Prometheus storage volume size. |
| `LOKI_RETENTION` | string | `31d` | Optional | Loki config | Loki log retention period. |
| `SLACK_WEBHOOK_URL` | string | _(none)_ | Optional | Alertmanager config | Slack webhook for alert notifications. |
| `SMTP_HOST` | string | _(none)_ | Optional | Alertmanager SMTP | SMTP server host for email alerts. |
| `SMTP_PORT` | int | `587` | Optional | Alertmanager SMTP | SMTP server port. |
| `SMTP_USER` | string | _(none)_ | Optional | Alertmanager SMTP | SMTP authentication username. |
| `SMTP_PASSWORD` | string | _(none)_ | Optional | Alertmanager SMTP | SMTP authentication password. |
| `SMTP_FROM` | string | _(none)_ | Optional | Alertmanager SMTP | Sender email address for alerts. |
| `PAGERDUTY_SERVICE_KEY` | string | _(none)_ | Optional | Alertmanager config | PagerDuty integration key for critical alerts. |
| `JAEGER_AGENT_HOST` | string | _(none)_ | Optional | Tracing config (staging) | Jaeger collector host for trace export. |
| `JAEGER_AGENT_PORT` | int | `6831` | Optional | Tracing config (staging) | Jaeger agent UDP port. |

---

## 9. Mail and SMS Services

Variables consumed by legacy YAWL mail and SMS integration modules.

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `CATALINA_HOME` | string | _(none)_ | Required | `MailSender`, `DocumentStore` | Tomcat home directory. Used to construct paths for mail attachment storage. |
| `SMS_USERNAME` | string | _(none)_ | Conditional | SMSSender | SMS gateway authentication username. Required when SMS integration is active. |
| `SMS_PASSWORD` | string | _(none)_ | Conditional | SMSSender | SMS gateway authentication password. |
| `SMS_SEND_URI` | string | _(none)_ | Conditional | SMSSender | SMS gateway send endpoint URL. |
| `SMS_RECEIVE_URI` | string | _(none)_ | Conditional | SMSSender | SMS gateway receive endpoint URL. |

---

## 10. Backup Settings

Variables for the optional automated backup system.

| Variable | Type | Default | Required | Property / Code | Description |
|---|---|---|---|---|---|
| `BACKUP_ENABLED` | boolean | `false` | Optional | Backup config | Enable automatic scheduled backups. |
| `BACKUP_SCHEDULE` | string | `0 2 * * *` | Optional | Backup config | Cron expression for backup schedule. Default: daily at 2 AM. |
| `BACKUP_RETENTION_DAYS` | int | `7` | Optional | Backup config | Days to retain backup files. |
| `BACKUP_PATH` | string | `/app/backups` | Optional | Backup config | Container path for backup storage. |
| `BACKUP_TYPE` | string | `full` | Optional | Backup config | Backup scope. Options: `full`, `database`, `files`. |
| `BACKUP_COMPRESS` | boolean | `true` | Optional | Backup config | Compress backup archives with gzip. |
| `BACKUP_ENCRYPT` | boolean | `false` | Optional | Backup config | Encrypt backups with GPG. Requires `BACKUP_GPG_KEY`. |
| `BACKUP_GPG_KEY` | string | _(none)_ | Conditional | Backup config | GPG key ID for backup encryption. |
| `BACKUP_S3_BUCKET` | string | _(none)_ | Optional | Backup config | S3 bucket name for offsite backup uploads. |
| `BACKUP_SFTP_HOST` | string | _(none)_ | Optional | Backup config | SFTP host for offsite backup uploads. |
