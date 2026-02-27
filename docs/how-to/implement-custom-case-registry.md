# How to Implement a Custom Case Registry (e.g., Redis-Backed)

## Problem

The default `LocalCaseRegistry` stores case-to-tenant mappings in a `ConcurrentHashMap` (~120 MB for 1M cases), suitable only for single-node deployments. In multi-node Kubernetes clusters, each pod has its own separate in-memory map, causing different pods to disagree on case ownership. You need to replace it with a shared registry backed by external storage (Redis, Memcached, etc.) so all pods consult the same source of truth.

## Prerequisites

- YAWL v6.0+ (SPI introduced in v6.0)
- Maven 3.8+ (for building the adapter)
- Spring Data Redis 3.1+ (if targeting Redis; other backends can use different libraries)
- Understanding of the `GlobalCaseRegistry` interface
- Kubernetes Secret or ConfigMap with connection details (for staging credentials)

## Steps

### 1. Review the GlobalCaseRegistry Interface

The interface defines four core methods that any implementation must provide:

```java
package org.yawlfoundation.yawl.engine.spi;

import org.yawlfoundation.yawl.elements.state.YIdentifier;

/**
 * SPI for a cluster-wide registry mapping case IDs to tenant IDs.
 *
 * Default implementation: LocalCaseRegistry uses a ConcurrentHashMap
 * (1M entries × ~120 bytes ≈ 120 MB heap). Sufficient for single-node deployments.
 *
 * Optional adapter: RedisGlobalCaseRegistry uses Spring Data Redis HashOperations
 * for multi-node deployments.
 */
public interface GlobalCaseRegistry {

    /**
     * Registers a new case with its owning tenant.
     *
     * @param caseId   the case identifier as a string; must not be null
     * @param tenantId the tenant identifier; must not be null
     */
    void register(String caseId, String tenantId);

    /**
     * Looks up the tenant that owns a given case.
     *
     * @param caseId the case identifier as a string; must not be null
     * @return the tenant ID, or null if the case is not registered
     */
    String lookupTenant(String caseId);

    /**
     * Removes the registration for a completed or cancelled case.
     *
     * @param caseId the case identifier as a string; must not be null
     */
    void deregister(String caseId);

    /**
     * Returns the number of active cases in the registry.
     *
     * @return active case count
     */
    long size();

    // Convenience overloads for YIdentifier
    default void register(YIdentifier caseId, String tenantId) {
        if (caseId == null) throw new NullPointerException("caseId must not be null");
        register(caseId.toString(), tenantId);
    }

    default String lookupTenant(YIdentifier caseId) {
        if (caseId == null) throw new NullPointerException("caseId must not be null");
        return lookupTenant(caseId.toString());
    }

    default void deregister(YIdentifier caseId) {
        if (caseId == null) throw new NullPointerException("caseId must not be null");
        deregister(caseId.toString());
    }
}
```

### 2. Create a Redis-Backed Implementation

Create a new Maven module `yawl-redis-adapter` with the following structure:

```
yawl-redis-adapter/
├── pom.xml
└── src/main/java/org/yawlfoundation/yawl/engine/spi/
    ├── RedisGlobalCaseRegistry.java
    └── RedisGlobalCaseRegistryConfig.java
```

Add this `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.yawlfoundation.yawl</groupId>
    <artifactId>yawl-redis-adapter</artifactId>
    <version>6.0.0</version>
    <name>YAWL Redis Adapter</name>
    <description>GlobalCaseRegistry implementation backed by Redis</description>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
    </properties>

    <dependencies>
        <!-- YAWL Engine (core) -->
        <dependency>
            <groupId>org.yawlfoundation.yawl</groupId>
            <artifactId>yawl-engine</artifactId>
            <version>6.0.0</version>
            <scope>provided</scope>
        </dependency>

        <!-- Spring Data Redis -->
        <dependency>
            <groupId>org.springframework.data</groupId>
            <artifactId>spring-data-redis</artifactId>
            <version>3.1.0</version>
            <scope>provided</scope>
        </dependency>

        <!-- Lettuce Redis client -->
        <dependency>
            <groupId>io.lettuce</groupId>
            <artifactId>lettuce-core</artifactId>
            <version>6.2.0.RELEASE</version>
            <scope>provided</scope>
        </dependency>

        <!-- Logging -->
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-api</artifactId>
            <version>2.21.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3. Implement RedisGlobalCaseRegistry

Create `src/main/java/org/yawlfoundation/yawl/engine/spi/RedisGlobalCaseRegistry.java`:

```java
package org.yawlfoundation.yawl.engine.spi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis-backed implementation of GlobalCaseRegistry.
 *
 * Uses Redis HSET/HGET/HDEL for case ID -> tenant ID mappings.
 * All YAWL engine pods in a multi-node deployment connect to the same Redis
 * instance and agree on case ownership.
 *
 * Connection details are loaded from Spring properties:
 *  spring.data.redis.host
 *  spring.data.redis.port
 *  spring.data.redis.password (optional)
 */
public final class RedisGlobalCaseRegistry implements GlobalCaseRegistry {

    private static final Logger log = LogManager.getLogger(RedisGlobalCaseRegistry.class);
    private static final String REGISTRY_KEY = "yawl:case:registry";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Constructs with a pre-configured RedisTemplate (injected by Spring).
     */
    public RedisGlobalCaseRegistry(RedisTemplate<String, String> redisTemplate) {
        if (redisTemplate == null) {
            throw new NullPointerException("redisTemplate must not be null");
        }
        this.redisTemplate = redisTemplate;
        log.info("RedisGlobalCaseRegistry initialized. Redis key: {}", REGISTRY_KEY);
    }

    /**
     * Registers a case with its owning tenant in Redis.
     * Uses HSET to store: key=caseId, value=tenantId in a hash named "yawl:case:registry"
     */
    @Override
    public void register(String caseId, String tenantId) {
        if (caseId == null) {
            throw new NullPointerException("caseId must not be null");
        }
        if (tenantId == null) {
            throw new NullPointerException("tenantId must not be null");
        }
        try {
            redisTemplate.opsForHash().put(REGISTRY_KEY, caseId, tenantId);
            log.debug("Registered case {} for tenant {}", caseId, tenantId);
        } catch (Exception e) {
            log.error("Failed to register case {} in Redis", caseId, e);
            throw new RuntimeException("Redis registration failed", e);
        }
    }

    /**
     * Looks up the tenant owning a case from Redis.
     * Returns null if the case is not registered (completed or not yet started).
     */
    @Override
    public String lookupTenant(String caseId) {
        if (caseId == null) {
            throw new NullPointerException("caseId must not be null");
        }
        try {
            Object value = redisTemplate.opsForHash().get(REGISTRY_KEY, caseId);
            return value != null ? (String) value : null;
        } catch (Exception e) {
            log.error("Failed to lookup tenant for case {} in Redis", caseId, e);
            throw new RuntimeException("Redis lookup failed", e);
        }
    }

    /**
     * Removes a case registration from Redis (called on case completion/cancellation).
     */
    @Override
    public void deregister(String caseId) {
        if (caseId == null) {
            throw new NullPointerException("caseId must not be null");
        }
        try {
            redisTemplate.opsForHash().delete(REGISTRY_KEY, caseId);
            log.debug("Deregistered case {}", caseId);
        } catch (Exception e) {
            log.error("Failed to deregister case {} from Redis", caseId, e);
            throw new RuntimeException("Redis deregistration failed", e);
        }
    }

    /**
     * Returns the total number of active cases in the registry.
     */
    @Override
    public long size() {
        try {
            Long size = redisTemplate.opsForHash().size(REGISTRY_KEY);
            return size != null ? size : 0;
        } catch (Exception e) {
            log.error("Failed to retrieve size from Redis", e);
            throw new RuntimeException("Redis size query failed", e);
        }
    }
}
```

### 4. Register the Implementation via ServiceLoader

Create the file `src/main/resources/META-INF/services/org.yawlfoundation.yawl.engine.spi.GlobalCaseRegistry`:

```
org.yawlfoundation.yawl.engine.spi.RedisGlobalCaseRegistry
```

This tells Java's `ServiceLoader` to load your Redis implementation instead of the default `LocalCaseRegistry`.

### 5. Create Spring Configuration (Optional but Recommended)

Create `src/main/java/org/yawlfoundation/yawl/engine/spi/RedisGlobalCaseRegistryConfig.java`:

```java
package org.yawlfoundation.yawl.engine.spi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Auto-configuration for RedisGlobalCaseRegistry.
 *
 * Activates only if:
 * 1. The JAR is on the classpath (via Maven dependency)
 * 2. spring.data.redis.* properties are configured
 * 3. No other GlobalCaseRegistry bean exists
 */
@Configuration
public class RedisGlobalCaseRegistryConfig {

    @Bean
    @ConditionalOnMissingBean(GlobalCaseRegistry.class)
    public GlobalCaseRegistry globalCaseRegistry(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();

        return new RedisGlobalCaseRegistry(template);
    }
}
```

### 6. Update the Main Engine pom.xml to Include the Adapter

In the `yawl-engine/pom.xml`, add an optional dependency on the Redis adapter:

```xml
<!-- Optional: Redis-backed case registry for multi-node deployments -->
<dependency>
    <groupId>org.yawlfoundation.yawl</groupId>
    <artifactId>yawl-redis-adapter</artifactId>
    <version>6.0.0</version>
    <optional>true</optional>
</dependency>
```

### 7. Configure Spring Properties for Redis Connection

In `application.properties` (or `application-redis.yml`):

```properties
# Redis connection (only needed if using Redis adapter)
spring.data.redis.host=redis.yawl.svc.cluster.local
spring.data.redis.port=6379
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.timeout=2000ms
spring.data.redis.jedis.pool.max-active=20
spring.data.redis.jedis.pool.max-idle=5
```

Or via environment variables in the Kubernetes deployment:

```yaml
env:
  - name: SPRING_DATA_REDIS_HOST
    value: redis.yawl.svc.cluster.local
  - name: SPRING_DATA_REDIS_PORT
    value: "6379"
  - name: SPRING_DATA_REDIS_PASSWORD
    valueFrom:
      secretKeyRef:
        name: redis-credentials
        key: password
```

### 8. Build and Deploy

Build the adapter JAR:

```bash
cd yawl-redis-adapter
mvn clean package -DskipTests
```

Add it to the engine classpath (either in the base image or via initContainer/volume mount). Then deploy:

```bash
kubectl set image deployment/yawl-engine -n yawl \
  yawl-engine=yawl/engine:6.0.0-redis
kubectl rollout status deployment/yawl-engine -n yawl
```

## Verification

### 1. Confirm the Redis Adapter Is Loaded

Check the engine startup logs for the ServiceLoader confirmation:

```bash
kubectl logs deployment/yawl-engine -n yawl | grep -i "rediscaseregistry\|globalcaseregistry"
```

Expected output:
```
[INFO] org.yawlfoundation.yawl.engine.YEngine: GlobalCaseRegistry implementation:
  org.yawlfoundation.yawl.engine.spi.RedisGlobalCaseRegistry
```

### 2. Launch a Case and Verify Cross-Pod Registration

Launch a case on pod #1:

```bash
CASE_ID=$(curl -s "http://yawl-engine.yawl.svc.cluster.local:8080/yawl/ib?action=launchCase&specID=TestSpec_1_0&sessionHandle=..." | grep -o '"case_id":"[^"]*' | cut -d'"' -f4)
echo "Launched case: $CASE_ID"
```

Then query a different pod (#2) to confirm it can look up the tenant:

```bash
POD_2=$(kubectl get pods -n yawl -l app.kubernetes.io/name=yawl-engine --sort-by=metadata.creationTimestamp | tail -1 | awk '{print $1}')
kubectl exec -it -n yawl $POD_2 -- \
  curl -s "http://localhost:8080/yawl/ib?action=getCaseInfo&caseID=$CASE_ID&sessionHandle=..."
```

Both pods should return the same case info, confirming the shared registry works.

### 3. Inspect Redis Directly

Connect to Redis and view the registry:

```bash
kubectl exec -it redis-0 -- redis-cli
HGETALL yawl:case:registry
```

Should display case IDs and their corresponding tenant IDs.

## Troubleshooting

### `ServiceLoader did not find GlobalCaseRegistry implementations`

The adapter JAR is not on the classpath. Verify:

```bash
kubectl exec -it deployment/yawl-engine -- \
  find /opt/yawl -name "*redis-adapter*.jar"
```

If the JAR is not present, rebuild the container image with the adapter included.

### `ConnectionRefused: Connection to Redis refused`

Redis is not reachable. Verify connectivity:

```bash
kubectl exec -it deployment/yawl-engine -- \
  nc -zv redis.yawl.svc.cluster.local 6379
```

If connection fails, check DNS and network policies:

```bash
kubectl get svc redis -n yawl
kubectl get networkpolicies -n yawl
```

### `LocalCaseRegistry still in use instead of Redis`

The ServiceLoader found no provider (adapter JAR not in classpath), so it fell back to the default. Check the engine startup logs:

```bash
kubectl logs deployment/yawl-engine -n yawl | grep -i "localcaseregistry"
```

If present, the adapter is missing. Add it to the image build.

### `Redis connection timeout`

The engine is trying to connect but Redis is slow. Increase the timeout in `application.properties`:

```properties
spring.data.redis.timeout=5000ms
```

Or scale up Redis replica count to handle the load.
