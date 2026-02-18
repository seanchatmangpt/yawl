# Tutorial: Docker Development Environment for YAWL

By the end of this tutorial you will have a running YAWL engine stack on your local machine using Docker Compose, with the H2 development database, an understanding of how to make a Java source change and rebuild the container image, and the ability to run integration tests against the live stack.

---

## Prerequisites

You need the following tools installed and on your PATH.

```bash
docker --version
```

Expected: `Docker version 24.x` or higher. Docker Desktop on macOS/Windows is fine; Linux needs Docker Engine 20.10+.

```bash
docker compose version
```

Expected: `Docker Compose version v2.x`. The `docker compose` subcommand (V2, without hyphen) is required. The legacy `docker-compose` command is not used in this tutorial.

```bash
java -version
```

Expected: `openjdk version "25.x.x"`. YAWL is compiled against Java 25. You need the JDK (not just JRE) to build the WAR before Docker can package it.

```bash
mvn -version
```

Expected: `Apache Maven 3.9.x` or higher.

---

## Step 1: Understand the Docker files in this repository

YAWL ships several Dockerfiles and one primary `docker-compose.yml` at the repository root. Here is what each file provides.

| File | Purpose |
|---|---|
| `Dockerfile.modernized` | Multi-stage production build (JDK 25 builder + JRE 25 runtime). **Used by `docker-compose.yml`.** |
| `Dockerfile.dev` | Development image: full JDK 25, Maven 3.9.9, remote debug on port 5005, dev-tools helper script. |
| `Dockerfile` | Basic production build using `eclipse-temurin:25-jdk-alpine`. |
| `docker-compose.yml` | Default stack: `yawl-engine` (H2 dev database, ports 8080/9090/8081). Optional `production` and `observability` profiles. |
| `docker-compose.prod.yml` | PostgreSQL-backed production override. |
| `docker-compose.simulation.yml` | Simulation mode with pre-loaded specifications. |

For local development the relevant file is `docker-compose.yml` with its default (no profile) services.

---

## Step 2: Build the application JAR

Docker Compose is configured to build the image from `Dockerfile.modernized`, which runs the Maven build inside the container. However, the build step inside Docker re-downloads all dependencies on every cold run. For a faster inner loop, pre-build the JAR on the host and mount it, or simply let Docker handle the full build.

To trigger the full Maven build on the host first (recommended for first-time setup):

```bash
mvn -T 1.5C clean package -DskipTests
```

Expected output ends with:

```
[INFO] BUILD SUCCESS
[INFO] Total time:  1:28 min
```

The shaded JAR lands at `yawl-control-panel/target/yawl-control-panel-6.0.0-Alpha.jar`. The `Dockerfile.modernized` build stage expects to find it under the same path after running its own internal `mvn clean package`.

---

## Step 3: Build the container image

Build the `yawl-engine:6.0.0-alpha` image as specified in `docker-compose.yml`:

```bash
docker build -f Dockerfile.modernized -t yawl-engine:6.0.0-alpha .
```

This is a two-stage build. The builder stage compiles the full Maven reactor. The runtime stage copies only the shaded JAR into a minimal `eclipse-temurin:25-jre-alpine` image. The build takes two to four minutes on first run; subsequent builds reuse cached layers from the dependency download step.

Expected final lines:

```
 => exporting to image                                                    0.4s
 => => writing image sha256:...                                           0.0s
 => => naming to docker.io/library/yawl-engine:6.0.0-alpha               0.0s
```

---

## Step 4: Start the development stack

```bash
docker compose up -d
```

Docker Compose starts the `yawl-engine` service (the only service active without a profile). The engine uses an embedded H2 file database stored in the `yawl_data` named volume.

The service exposes three ports:

| Host port | Container port | Purpose |
|---|---|---|
| 8080 | 8080 | YAWL engine REST API (Interface B, Interface A) |
| 9090 | 9090 | Spring Boot Actuator management + Prometheus metrics |
| 8081 | 8081 | Resource service API |

Watch startup progress:

```bash
docker compose logs -f yawl-engine
```

The engine logs `Starting YAWL Engine...` followed by Spring Boot startup banners. The container health check polls `/app/healthcheck.sh` every 30 seconds with a 120-second start period. Wait until `docker compose ps` shows `healthy`:

```
NAME          IMAGE                      STATUS
yawl-engine   yawl-engine:6.0.0-alpha   Up 2 minutes (healthy)
```

---

## Step 5: Verify the engine is healthy

```bash
curl -s http://localhost:8080/actuator/health/liveness
```

Expected response:

```json
{"status":"UP"}
```

Check readiness:

```bash
curl -s http://localhost:8080/actuator/health/readiness
```

Expected response:

```json
{"status":"UP"}
```

Check the Prometheus metrics endpoint (useful for confirming the management port is also up):

```bash
curl -s http://localhost:9090/actuator/health
```

Expected response:

```json
{"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"}}}
```

If any endpoint returns an error, check the logs:

```bash
docker compose logs yawl-engine | tail -40
```

The two most common startup failures are a port conflict on 8080 (another process is bound) and insufficient memory (the JVM requires at least 512 MB; the container defaults to 75% of available container RAM).

---

## Step 6: Verify the engine API responds to workflow requests

YAWL's workflow engine is accessed via Interface B. Test that the API is operational:

```bash
curl -s -X POST "http://localhost:8080/yawl/ib" \
  -d "action=connect&userid=admin&password=YAWL" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

A successful response contains a session handle:

```xml
<response><success>sessionHandleToken</success></response>
```

The session handle string is a UUID-like token you use in subsequent API calls. If you see `<failure>` instead, verify that `SPRING_PROFILES_ACTIVE=development` is set in the running container (it is the default when no profile is specified).

---

## Step 7: Make a source change and rebuild

YAWL uses a compiled image workflow rather than filesystem hot-reload, because the engine is a Spring Boot fat JAR. The rebuild cycle is:

1. Edit source files on the host.
2. Compile locally (fast):

```bash
mvn -T 1.5C clean compile -pl yawl-engine,yawl-integration
```

3. Rebuild the image:

```bash
docker build -f Dockerfile.modernized -t yawl-engine:6.0.0-alpha .
```

Docker reuses the dependency download layer (cached) and only rebuilds from the source copy layer onward. Rebuild time after a source change is typically 60-90 seconds.

4. Restart the service:

```bash
docker compose up -d --force-recreate yawl-engine
```

5. Tail the logs to watch the new version start:

```bash
docker compose logs -f yawl-engine
```

For tight edit-compile-test loops without rebuilding the image, use the development image directly. The `Dockerfile.dev` image includes Maven and maps your source tree as a volume:

```bash
docker build -f Dockerfile.dev -t yawl-dev:latest .

docker run -it --rm \
  -p 8080:8080 \
  -p 9090:9090 \
  -p 5005:5005 \
  -e JAVA_DEBUG=true \
  -v "$(pwd)/src:/app/src:ro" \
  -v "$(pwd)/yawl-engine:/app/yawl-engine:ro" \
  -v yawl-maven-cache:/root/.m2 \
  yawl-dev:latest
```

Port 5005 is the JDWP remote debug port. Connect your IDE to `localhost:5005` after the JVM prints `Listening for transport dt_socket at address: 5005`.

---

## Step 8: Run integration tests against the running stack

Maven Failsafe integration tests targeting the running stack are in `test/org/yawlfoundation/yawl/integration/**/*IT.java`. Run them while the stack is up:

```bash
mvn -pl yawl-integration failsafe:integration-test failsafe:verify \
  -DYAWL_ENGINE_URL=http://localhost:8080/yawl \
  -DYAWL_USERNAME=admin \
  -DYAWL_PASSWORD=YAWL
```

The Failsafe plugin picks up `*IT.java` test classes. These tests call the live engine endpoints and verify real workflow operations. Expected summary:

```
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

To run only unit tests (no running stack required), use the standard Surefire phase:

```bash
mvn -T 1.5C clean test
```

---

## Step 9: Explore the H2 database console

The `development` Docker Compose profile includes an H2 console service. Start it alongside the engine:

```bash
docker compose --profile development up -d
```

The H2 console becomes available at `http://localhost:8082`. Connect with:

- JDBC URL: `jdbc:h2:file:/data/yawl`
- User: `sa`
- Password: (empty)

This lets you inspect workflow case tables, work item records, and specification data directly.

---

## Step 10: Stop and clean up

Stop the running containers:

```bash
docker compose down
```

Named volumes (`yawl_specifications`, `yawl_data`, `yawl_logs`, etc.) persist after `down`. To also remove all volumes and start fresh:

```bash
docker compose down -v
```

To remove the built image:

```bash
docker rmi yawl-engine:6.0.0-alpha
```

---

## What happened

- `docker-compose.yml` defines a `yawl-engine` service built from `Dockerfile.modernized`.
- The container runs as the non-root `yawl` user (UID 1000) with ZGC generational garbage collection and virtual thread scheduler tuning (`-Djdk.virtualThreadScheduler.parallelism=200`).
- The H2 embedded database persists case and specification data in the `yawl_data` named volume at `/app/data` inside the container.
- The Spring Boot Actuator liveness and readiness probes are exposed on port 9090 and used by the Docker health check.
- The `SPRING_PROFILES_ACTIVE=development` environment variable activates the H2 datasource and verbose logging; switching to `production` activates the PostgreSQL datasource (see `docker-compose.prod.yml`).

---

## Next steps

Continue with [Tutorial 8: MCP Agent Integration](08-mcp-agent-integration.md) to learn how to connect an AI agent to the running YAWL engine through the Model Context Protocol, enabling natural language workflow invocation.

To deploy with PostgreSQL and observability (Prometheus, Grafana, Jaeger), run:

```bash
docker compose --profile production --profile observability up -d
```

The `observability` profile adds an OpenTelemetry Collector, Prometheus on port 9091, Grafana on port 3000, and Jaeger on port 16686. See `docker-compose.yml` for the full service definitions.
