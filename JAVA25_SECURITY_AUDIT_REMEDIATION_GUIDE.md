# Java 25 Security Audit - Remediation Implementation Guide

**Purpose**: Provide concrete code examples and step-by-step remediation for critical findings.

---

## 1. Critical Finding #1: Virtual Thread Pinning via Synchronized Methods

### Problem
YTask and related classes use `synchronized` methods, which pin virtual threads to carrier OS threads. This defeats the 1000:1 virtual-to-carrier thread scaling benefit of Java 25.

### Current Code Example
```java
// src/org/yawlfoundation/yawl/elements/YTask.java
public class YTask {
    public synchronized List<YIdentifier> t_fire(YPersistenceManager pmgr) {
        // Method body
    }

    public synchronized YIdentifier t_add(YPersistenceManager pmgr, ...) {
        // Method body
    }

    public synchronized void cancel(YPersistenceManager pmgr) throws YPersistenceException {
        // Method body
    }
}
```

### Impact
- Virtual thread spawned to execute case → calls t_fire() → pins to carrier thread
- Carrier thread stays pinned for entire critical section duration
- If critical section contains I/O (database, RPC), virtual thread is blocked
- System degrades to 50-200 concurrent threads (OS thread limit)

### Remediation

#### Step 1: Identify All Synchronized Methods
```bash
grep -r "public synchronized\|private synchronized\|protected synchronized" \
  src/org/yawlfoundation/yawl/elements/ --include="*.java" | \
  grep -E "(YTask|YAtomicTask|YCompositeTask|YCondition)"
```

#### Step 2: Add ReentrantLock Field
```java
import java.util.concurrent.locks.ReentrantLock;

public class YTask {
    // Add one lock per class (or one per instance if per-task locking needed)
    private final ReentrantLock taskLock = new ReentrantLock();

    // Constructor - ensure lock is initialized before use
    public YTask() {
        // ... existing init code ...
    }
}
```

#### Step 3: Convert Synchronized Methods
**Before**:
```java
public synchronized List<YIdentifier> t_fire(YPersistenceManager pmgr) {
    // 30 lines of complex logic
    // ...
    return result;
}
```

**After**:
```java
public List<YIdentifier> t_fire(YPersistenceManager pmgr) {
    taskLock.lock();
    try {
        // Identical 30 lines of complex logic
        // ...
        return result;
    } finally {
        taskLock.unlock();
    }
}
```

#### Step 4: Handle InterruptedException Properly
```java
public List<YIdentifier> t_fire(YPersistenceManager pmgr) throws InterruptedException {
    if (!taskLock.tryLock(5, TimeUnit.SECONDS)) {
        throw new InterruptedException("Failed to acquire task lock within 5 seconds");
    }
    try {
        // Method body
    } finally {
        taskLock.unlock();
    }
}
```

#### Step 5: Apply to All Synchronized Methods
List of methods to convert:
- `YTask.t_fire()`
- `YTask.t_add()`
- `YTask.t_isExitEnabled()`
- `YTask.t_complete()`
- `YTask.t_start()`
- `YTask.t_exit()` [private]
- `YTask.t_enabled()`
- `YTask.t_isBusy()`
- `YTask.cancel()`
- `YAtomicTask.cancel()` [both variants]
- `YCompositeTask.startOne()`
- `YCompositeTask.cancel()`
- `YCondition.removeAll()`

#### Step 6: Testing
```java
// Test that lock prevents concurrent access
@Test
public void testConcurrentTaskFireIsSerializable() throws Exception {
    YTask task = new YTask();
    List<YIdentifier> results = Collections.synchronizedList(new ArrayList<>());

    ExecutorService executor = Executors.newFixedThreadPool(10);
    for (int i = 0; i < 10; i++) {
        executor.submit(() -> {
            try {
                results.add(task.t_fire(pmgr));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);

    // Verify serialization occurred (one fire at a time)
    assertEquals(10, results.size());
}

// Test virtual thread scalability
@Test
public void testVirtualThreadScalability() throws Exception {
    YTask task = new YTask();
    int virtualThreadCount = 10000;

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        CountDownLatch latch = new CountDownLatch(virtualThreadCount);
        for (int i = 0; i < virtualThreadCount; i++) {
            executor.submit(() -> {
                try {
                    task.t_fire(pmgr);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Should complete quickly (not bottlenecked by OS thread count)
        assertTrue(latch.await(30, TimeUnit.SECONDS));
    }
}
```

#### Effort Estimate: 4-6 hours
- Search & identify: 30 min
- Refactor each method: 15 min × 15 methods = 3.75 hours
- Testing & verification: 1.5 hours
- Code review: 30 min

---

## 2. Critical Finding #2: Missing Certificate Pinning for Z.AI API

### Problem
Z.AI API calls use Java 25 HttpClient with default TLS validation. Vulnerable to MITM if attacker can modify system truststore or compromise a CA.

### Current Code
```java
// src/org/yawlfoundation/yawl/integration/zai/ZaiHttpClient.java:175-178
this.httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .executor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
        .build();
```

### Impact
- Any CA-signed certificate for api.z.ai is accepted
- Rogue CA or CA compromise = MITM attacks
- API key + request/response data compromised

### Remediation: Option A - Using OkHttp with CertificatePinner

#### Step 1: OkHttp is Already in pom.xml (v5.1.0)
```xml
<okhttp.version>5.1.0</okhttp.version>
```

#### Step 2: Create HttpClient Factory with Certificate Pinning
```java
// New file: src/org/yawlfoundation/yawl/integration/zai/ZaiHttpClientFactory.java
package org.yawlfoundation.yawl.integration.zai;

import okhttp3.CertificatePinner;
import okhttp3.OkHttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class ZaiHttpClientFactory {

    /**
     * Creates an OkHttpClient with certificate pinning for Z.AI API.
     *
     * Certificate pins are extracted from the live Z.AI API certificate:
     * openssl s_client -connect api.z.ai:443 | openssl x509 -noout -pubkey |
     *   openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
     */
    public static OkHttpClient createZaiHttpClient(Duration connectTimeout, Duration readTimeout) {
        // Pin the public key hash of Z.AI's TLS certificate (SHA256)
        // To obtain: curl -vI https://api.z.ai 2>&1 | grep -i cert
        // Then: openssl s_client -connect api.z.ai:443 -showcerts
        // Extract cert, get public key hash

        // Example pins (replace with actual Z.AI certificate pins):
        CertificatePinner certificatePinner = new CertificatePinner.Builder()
            // Primary certificate
            .add("api.z.ai", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            // Backup certificate (for rotation)
            .add("api.z.ai", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
            // Intermediate CA (optional, depends on certificate chain)
            .add("api.z.ai", "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
            .build();

        return new OkHttpClient.Builder()
            .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .certificatePinner(certificatePinner)
            .build();
    }
}
```

#### Step 3: Update ZaiHttpClient to Use OkHttp
```java
// src/org/yawlfoundation/yawl/integration/zai/ZaiHttpClient.java
package org.yawlfoundation.yawl.integration.zai;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import java.io.IOException;
import java.time.Duration;

public class ZaiHttpClient {

    private final String apiKey;
    private final String baseUrl;
    private final OkHttpClient httpClient;  // Change from java.net.http.HttpClient
    private final ObjectMapper objectMapper;
    private Duration readTimeout;

    public ZaiHttpClient(String apiKey) {
        this(apiKey, ZAI_API_BASE);
    }

    public ZaiHttpClient(String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "Z.AI API key is required. Set ZAI_API_KEY environment variable.");
        }
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.readTimeout = Duration.ofSeconds(120);
        // Use OkHttp with certificate pinning instead of java.net.http.HttpClient
        this.httpClient = ZaiHttpClientFactory.createZaiHttpClient(
            Duration.ofSeconds(30),
            readTimeout
        );
        this.objectMapper = new ObjectMapper();
    }

    private String executeWithRetry(ChatRequest request) throws IOException {
        String requestBody = buildRequestBody(request);
        IOException lastException = null;
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Build OkHttp request
                Request httpRequest = new Request.Builder()
                    .url(baseUrl + CHAT_ENDPOINT)
                    .timeout(readTimeout)  // OkHttp uses Timeout instead of Duration
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                    .build();

                Response response = httpClient.newCall(httpRequest).execute();

                if (response.code() >= 400) {
                    String body = response.body() != null ? response.body().string() : "";
                    ZaiApiError error = ZaiApiError.from(response.code(), body);
                    if (error.isRetryable() && attempt < MAX_RETRIES) {
                        sleep(backoffMs);
                        backoffMs *= 2;
                        continue;
                    }
                    throw new IOException(error.message());
                }

                assert response.body() != null;
                return response.body().string();

            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    sleep(backoffMs);
                    backoffMs *= 2;
                }
            }
        }
        throw lastException != null
            ? lastException
            : new IOException("Z.AI request failed after " + MAX_RETRIES + " attempts");
    }
}
```

#### Step 4: Extract Z.AI Certificate Pins
```bash
# Get certificate chain from Z.AI
openssl s_client -connect api.z.ai:443 -showcerts < /dev/null 2>/dev/null | \
  grep -E "^-----BEGIN CERTIFICATE-----$|^-----END CERTIFICATE-----$" -A 25 | \
  while read line; do
    if [[ "$line" == "-----BEGIN CERTIFICATE-----" ]]; then
      # Extract and hash the certificate
      echo "$line" > /tmp/cert.pem
      cert_chain=""
      while read cert_line && [[ "$cert_line" != "-----END CERTIFICATE-----" ]]; do
        echo "$cert_line" >> /tmp/cert.pem
      done
      echo "$cert_line" >> /tmp/cert.pem

      # Get SHA256 hash of public key
      openssl x509 -in /tmp/cert.pem -noout -pubkey | \
        openssl pkey -pubin -outform der | \
        openssl dgst -sha256 -binary | \
        base64
    fi
  done
```

#### Step 5: Update Configuration
```properties
# src/zai-pinning.properties
zai.api.certificate.pins[0]=sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
zai.api.certificate.pins[1]=sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=
```

#### Effort Estimate: 3-4 hours
- OkHttp integration: 1.5 hours
- Certificate extraction & pinning: 1 hour
- Testing (normal + pinning failure): 1.5 hours

---

## 3. Critical Finding #3: Jackson Deserialization Bypasses Sealed Classes

### Problem
UpgradeOutcome sealed interface can be bypassed during Jackson deserialization. Attacker can supply JSON with `@type` pointing to non-permitted class.

### Current Code
```java
// src/org/yawlfoundation/yawl/integration/memory/UpgradeMemoryStore.java:108-122
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Success.class, name = "success"),
    @JsonSubTypes.Type(value = Failure.class, name = "failure"),
    @JsonSubTypes.Type(value = Partial.class, name = "partial"),
    @JsonSubTypes.Type(value = InProgress.class, name = "inProgress")
})
public sealed interface UpgradeOutcome permits
        UpgradeMemoryStore.Success,
        UpgradeMemoryStore.Failure,
        UpgradeMemoryStore.Partial,
        UpgradeMemoryStore.InProgress {
    // ...
}
```

### Attack Scenario
```json
{
  "@type": "com.malicious.UnauthorizedOutcome",
  "data": "compromise"
}
```

Jackson will instantiate `com.malicious.UnauthorizedOutcome`, bypassing sealed interface restriction.

### Remediation

#### Step 1: Create Custom Deserializer
```java
// New file: src/org/yawlfoundation/yawl/integration/memory/SealedUpgradeOutcomeDeserializer.java
package org.yawlfoundation.yawl.integration.memory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Set;

public class SealedUpgradeOutcomeDeserializer extends JsonDeserializer<UpgradeMemoryStore.UpgradeOutcome> {

    /**
     * Whitelist of permitted type names. Must match @JsonSubTypes definitions.
     */
    private static final Set<String> PERMITTED_TYPES = Set.of(
        "success",
        "failure",
        "partial",
        "inProgress"
    );

    @Override
    public UpgradeMemoryStore.UpgradeOutcome deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {

        // Parse the JSON node
        final JsonNode node = ctxt.readTree(p);

        if (!(node instanceof ObjectNode)) {
            throw new JsonProcessingException(p, "Expected JSON object for UpgradeOutcome");
        }

        ObjectNode objectNode = (ObjectNode) node;
        String typeValue = objectNode.path("@type").asText(null);

        // **CRITICAL**: Validate that @type is in permitted set
        if (!PERMITTED_TYPES.contains(typeValue)) {
            throw new JsonProcessingException(p,
                String.format("Invalid sealed type '%s'. Must be one of: %s",
                    typeValue, PERMITTED_TYPES));
        }

        // Now safe to deserialize using Jackson's polymorphic logic
        return ctxt.readTreeAsValue(node, UpgradeMemoryStore.UpgradeOutcome.class);
    }
}
```

#### Step 2: Register Deserializer with ObjectMapper
```java
// src/org/yawlfoundation/yawl/integration/memory/UpgradeMemoryStore.java:583-601
private static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    // Register subtypes for polymorphic UpgradeOutcome deserialization
    mapper.registerSubtypes(
            new NamedType(Success.class, "success"),
            new NamedType(Failure.class, "failure"),
            new NamedType(Partial.class, "partial"),
            new NamedType(InProgress.class, "inProgress")
    );

    // **NEW**: Register custom deserializer that validates sealed permits
    SimpleModule module = new SimpleModule();
    module.addDeserializer(
        UpgradeOutcome.class,
        new SealedUpgradeOutcomeDeserializer()
    );
    mapper.registerModule(module);

    return mapper;
}
```

#### Step 3: Test the Mitigation
```java
// New file: src/test/.../SealedDeserializationTest.java
@Test
public void testSealedUpgradeOutcomeRejectsInvalidType() throws Exception {
    ObjectMapper mapper = UpgradeMemoryStore.createObjectMapper();

    // Attempt to deserialize with unauthorized @type
    String maliciousJson = """
    {
      "@type": "com.malicious.UnauthorizedOutcome",
      "data": "compromise"
    }
    """;

    assertThrows(
        JsonMappingException.class,
        () -> mapper.readValue(maliciousJson, UpgradeMemoryStore.UpgradeOutcome.class),
        "Expected JsonMappingException for invalid sealed type"
    );
}

@Test
public void testSealedUpgradeOutcomeAcceptsValidType() throws Exception {
    ObjectMapper mapper = UpgradeMemoryStore.createObjectMapper();

    String validJson = """
    {
      "@type": "success",
      "message": "Upgrade completed successfully"
    }
    """;

    UpgradeMemoryStore.UpgradeOutcome outcome = mapper.readValue(
        validJson,
        UpgradeMemoryStore.UpgradeOutcome.class
    );

    assertTrue(outcome.isSuccessful());
}
```

#### Effort Estimate: 2 hours
- Custom deserializer implementation: 1 hour
- Registration & testing: 1 hour

---

## 4. Critical Finding #4: No CVE Scanning

### Problem
No OWASP Dependency Check results; unknown vulnerabilities in transitive dependencies.

### Remediation

#### Step 1: Verify OWASP Dependency Check in pom.xml
```xml
<!-- In root pom.xml, verify this exists in <plugins> section -->
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>${owasp.dependency.check.version}</version>
    <configuration>
        <format>HTML,JSON</format>
        <outputDirectory>target/dependency-check-report</outputDirectory>
        <failBuildOnCVSS>7.0</failBuildOnCVSS>  <!-- Fail on CVSS 7+
    </configuration>
</plugin>
```

#### Step 2: Run Scan
```bash
# Full scan
mvn clean verify -P analysis

# Or dependency-check only
mvn org.owasp:dependency-check-maven:check

# Check results
ls -la target/dependency-check-report/
```

#### Step 3: Review Report
```bash
# Open HTML report
open target/dependency-check-report/dependency-check-report.html
```

#### Step 4: Suppress False Positives (if necessary)
```xml
<!-- Create owasp-suppressions.xml (already exists in repo) -->
<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/schema/suppression/1.3.0">
    <suppress>
        <notes>This vulnerability does not affect YAWL because...</notes>
        <cve>CVE-2025-XXXXX</cve>
    </suppress>
</suppressions>
```

#### Effort Estimate: 2-4 hours
- Run scan: 10 min
- Review findings: 1-2 hours
- Remediate (update versions, add suppressions): 1-2 hours

---

## 5. High Priority: Sealed Deserialization - Generic Pattern

For any sealed interface using Jackson, apply this pattern:

```java
public class SealedInterfaceDeserializer<T> extends JsonDeserializer<T> {
    private final Set<String> permittedTypes;

    public SealedInterfaceDeserializer(Set<String> permittedTypes) {
        this.permittedTypes = permittedTypes;
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = ctxt.readTree(p);

        if (!(node instanceof ObjectNode)) {
            throw new JsonMappingException(p, "Expected JSON object");
        }

        String typeValue = ((ObjectNode) node).path("@type").asText(null);

        if (!permittedTypes.contains(typeValue)) {
            throw new JsonMappingException(p,
                String.format("Invalid sealed type '%s'. Permitted: %s",
                    typeValue, permittedTypes));
        }

        return ctxt.readTreeAsValue(node, this.valueType);
    }
}
```

---

## 6. High Priority: Rate Limiting

Add Resilience4j rate limiter (already in pom.xml):

```java
// New class: ZaiApiRateLimiter.java
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.decorators.Decorators;
import java.time.Duration;

public class ZaiApiRateLimiter {

    private static final RateLimiter rateLimiter = RateLimiter.of("zai-api",
        RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .limitForPeriod(100)  // 100 requests per minute per client
            .timeoutDuration(Duration.ofSeconds(5))
            .build()
    );

    public static <T> T executeWithRateLimit(ThrowingSupplier<T> supplier) throws IOException {
        try {
            return Decorators.ofRateLimiter(rateLimiter)
                .executeSupplier(supplier::get);
        } catch (RequestNotPermitted e) {
            throw new IOException("Z.AI rate limit enforced: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws IOException;
    }
}
```

Usage:
```java
public ChatResponse createChatCompletionRecord(ChatRequest request) throws IOException {
    return ZaiApiRateLimiter.executeWithRateLimit(
        () -> createChatCompletionRecordInternal(request)
    );
}
```

---

## 7. High Priority: Jitter in Exponential Backoff

Prevent thundering herd problem:

```java
private static final Random RANDOM = new Random();

private String executeWithRetry(ChatRequest request) throws IOException {
    long backoffMs = INITIAL_BACKOFF_MS;

    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
        try {
            // ... request code ...
        } catch (IOException e) {
            if (attempt < MAX_RETRIES) {
                // Add jitter: ±10% randomness
                long jitter = (long)(backoffMs * 0.1 * (RANDOM.nextDouble() - 0.5));
                long actualBackoff = backoffMs + jitter;
                sleep(Math.max(0, actualBackoff));
                backoffMs *= 2;
            }
        }
    }
}
```

---

## Summary of Remediation Effort

| Finding | Effort | Priority | Status |
|---------|--------|----------|--------|
| Virtual thread pinning | 4-6 hrs | CRITICAL | Architectural |
| Certificate pinning | 3-4 hrs | CRITICAL | Dependency update |
| Sealed deserialization | 2 hrs | CRITICAL | Code addition |
| CVE scanning | 2-4 hrs | CRITICAL | Build config |
| Rate limiting | 1-2 hrs | HIGH | Code addition |
| Exponential backoff jitter | 30 min | HIGH | Code change |
| Exception logging | 2-3 hrs | HIGH | Code audit |
| **Total Estimate** | **15-24 hrs** | | **Pre-GA Requirement** |

---

## Verification Checklist

- [ ] All synchronized methods converted to ReentrantLock
- [ ] OkHttp certificate pinning configured with valid Z.AI pins
- [ ] Custom SealedUpgradeOutcomeDeserializer registered
- [ ] OWASP Dependency Check run and report reviewed
- [ ] No CVEs with CVSS 7+
- [ ] Rate limiter integrated (100 req/min)
- [ ] Jitter added to exponential backoff
- [ ] Exception logging audit completed
- [ ] All tests passing
- [ ] Security headers verified on all responses
- [ ] Audit logging verified for security events

---

**End of Remediation Guide**
