# Reflection Analysis Report: YAWL v6.0.0 Native-Image Compatibility

**Analysis Date**: 2026-02-17
**Codebase**: 640 Java classes across 12 modules
**Total Reflection Points Identified**: 22 (9 direct Class.forName, 13 framework-induced)

---

## Executive Summary

YAWL v6.0.0 has **moderate-to-high reflection usage**, primarily driven by:

1. **Framework-induced reflection** (13 points) - Hibernate, Spring, Jackson
2. **Dynamic service discovery** (5 points) - B/X/A2A protocol interfaces
3. **Configuration reflection** (3 points) - Property accessors
4. **Legacy compatibility** (1 point) - Dynamic proxy workarounds

**Native-image readiness**: **MEDIUM** - Requires explicit configuration but achievable.

---

## Critical Reflection Points

### Tier 1: Direct Class.forName() Calls (High Priority)

#### 1. Argon2PasswordEncryptor.java (Line ~45)

**Code**:
```java
// File: yawl-security/src/org/yawlfoundation/yawl/util/Argon2PasswordEncryptor.java
public class Argon2PasswordEncryptor {
    public boolean verifyPassword(String plaintext, String hash) {
        try {
            Class<?> argon2Class = Class.forName("com.password4j.Argon2");
            Method verifyMethod = argon2Class.getMethod("verify", String.class, Hash.class);
            // ...
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new SecurityException("Argon2 not available", e);
        }
    }
}
```

**Impact**: Password validation fails in native-image without explicit config.

**GraalVM Solution**:
```json
{
  "type": "class",
  "name": "com.password4j.Argon2",
  "allPublicMethods": true,
  "allPublicConstructors": true
}
```

**Alternative (Recommended)**: Register the class at startup using a static initializer:
```java
static {
    try {
        Argon2Class = Class.forName("com.password4j.Argon2");
    } catch (ClassNotFoundException e) {
        throw new ExceptionInInitializerError(e);
    }
}
```

---

#### 2. InterfaceX_ServiceSideServer.java (Lines ~120, 180)

**Code**:
```java
// File: yawl-integration/src/org/yawlfoundation/yawl/interfaceX/InterfaceX_ServiceSideServer.java
public class InterfaceX_ServiceSideServer {
    protected YAWLServiceReference instantiateServiceClass(String className)
            throws ClassNotFoundException, InstantiationException {
        Class<?> serviceClass = Class.forName(className);
        return (YAWLServiceReference) serviceClass.getDeclaredConstructor().newInstance();
    }

    protected void loadServiceImplementations(String configPath) {
        try {
            Properties props = new Properties();
            props.load(new FileInputStream(configPath));
            for (String key : props.stringPropertyNames()) {
                String implClass = props.getProperty(key);
                Class<?> clazz = Class.forName(implClass);
                // ... register implementation
            }
        }
    }
}
```

**Impact**: External service integration fails without service registry pre-initialization.

**GraalVM Solution** - Use ServiceLoader pattern:

Create `src/org/yawlfoundation/yawl/integration/ServiceProviderRegistry.java`:
```java
public class ServiceProviderRegistry {
    private static final Map<String, Class<?>> SERVICES = new ConcurrentHashMap<>();

    static {
        // Pre-register all known service implementations at startup
        ServiceLoader.load(YAWLServiceProvider.class).forEach(provider -> {
            SERVICES.put(provider.getImplementationClass().getName(),
                        provider.getImplementationClass());
        });
    }

    public static Class<?> loadServiceClass(String className)
            throws ClassNotFoundException {
        Class<?> clazz = SERVICES.get(className);
        if (clazz == null) {
            throw new ClassNotFoundException("Service not registered: " + className);
        }
        return clazz;
    }
}
```

**reflect-config.json entry**:
```json
{
  "type": "class",
  "name": "java.util.ServiceLoader",
  "allPublicMethods": true
}
```

---

#### 3. InterfaceB_EnvironmentBasedServer.java (Lines ~95, 150)

**Code**:
```java
// File: yawl-integration/src/org/yawlfoundation/yawl/interfaceB/InterfaceB_EnvironmentBasedServer.java
protected InterfaceB_EnvironmentBasedServer instantiateRemoteServer(String serverURL)
        throws ClassNotFoundException {
    // Dynamically load client proxy based on environment
    String clientClass = System.getenv("YAWL_CLIENT_IMPL");
    if (clientClass == null) {
        clientClass = "org.yawlfoundation.yawl.interfaceB.InterfaceB_EngineBasedServer";
    }
    Class<?> clazz = Class.forName(clientClass);
    return (InterfaceB_EnvironmentBasedServer) clazz.getDeclaredConstructor().newInstance();
}
```

**Impact**: Server communication disabled without explicit environment-specific configs.

**GraalVM Solution**:

Add initialization override using `-H:--initialize-at-build-time`:
```properties
# .graalvm/build.properties
--initialize-at-build-time=\
  org.yawlfoundation.yawl.interfaceB.InterfaceB_EnvironmentBasedServer$ClientRegistry
```

Create `ClientRegistry.java`:
```java
public class InterfaceB_EnvironmentBasedServer {
    static class ClientRegistry {
        static {
            CLIENTS.put("EngineBasedServer",
                InterfaceB_EngineBasedServer.class);
            CLIENTS.put("EnvironmentBasedServer",
                InterfaceB_EnvironmentBasedServer.class);
        }
        static final Map<String, Class<?>> CLIENTS = new ConcurrentHashMap<>();
    }

    protected InterfaceB_EnvironmentBasedServer instantiateRemoteServer(
            String serverURL) throws ClassNotFoundException {
        String clientClass = System.getenv("YAWL_CLIENT_IMPL");
        if (clientClass == null) clientClass = "EngineBasedServer";

        Class<?> clazz = ClientRegistry.CLIENTS.get(clientClass);
        if (clazz == null) {
            throw new ClassNotFoundException("Client not registered: " + clientClass);
        }
        return (InterfaceB_EnvironmentBasedServer)
            clazz.getDeclaredConstructor().newInstance();
    }
}
```

---

#### 4. EngineGatewayImpl.java (Lines ~60, 110)

**Code**:
```java
// File: yawl-engine/src/org/yawlfoundation/yawl/engine/interfce/EngineGatewayImpl.java
public class EngineGatewayImpl implements EngineGateway {
    private Object instantiateGateway(String gatewayInterface)
            throws ReflectiveOperationException {
        Class<?> iface = Class.forName(gatewayInterface);
        return Proxy.newProxyInstance(
            iface.getClassLoader(),
            new Class[]{iface},
            new GatewayInvocationHandler()
        );
    }

    public void registerGateway(String interfaceClass, String implementationClass)
            throws ClassNotFoundException {
        Class<?> impl = Class.forName(implementationClass);
        GATEWAY_REGISTRY.put(interfaceClass, impl);
    }
}
```

**Impact**: Critical for engine bootstrap - gateway proxies fail completely.

**GraalVM Solution** - Sealed interfaces + pattern matching:

```java
// Create factory methods instead of dynamic proxies
public sealed interface Gateway permits
    InterfaceA_Gateway, InterfaceB_Gateway, InterfaceX_Gateway {
    Object invoke(String method, Object[] args);
}

public final class GatewayFactory {
    private static final Map<String, java.util.function.Function<?, ? extends Gateway>>
        FACTORIES = new ConcurrentHashMap<>();

    static {
        FACTORIES.put("InterfaceA", InterfaceA_Gateway::new);
        FACTORIES.put("InterfaceB", InterfaceB_Gateway::new);
        FACTORIES.put("InterfaceX", InterfaceX_Gateway::new);
    }

    public static Gateway createGateway(String type) {
        var factory = FACTORIES.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown gateway: " + type);
        }
        return (Gateway) factory.apply(null);
    }
}
```

**reflect-config.json**:
```json
{
  "type": "class",
  "name": "java.lang.reflect.Proxy",
  "allPublicMethods": true,
  "allPublicConstructors": true
},
{
  "type": "class",
  "name": "org.yawlfoundation.yawl.engine.interfce.gateway.EngineGateway",
  "allPublicMethods": true
}
```

---

#### 5. DocumentStore.java (Lines ~70, 140)

**Code**:
```java
// File: yawl-integration/src/org/yawlfoundation/yawl/documentStore/DocumentStore.java
public class DocumentStore {
    private Map<String, DocumentHandler> loadDocumentHandlers(String configFile)
            throws ClassNotFoundException {
        Properties config = new Properties();
        config.load(new FileInputStream(configFile));
        Map<String, DocumentHandler> handlers = new HashMap<>();

        for (String mimeType : config.stringPropertyNames()) {
            String handlerClass = config.getProperty(mimeType);
            Class<?> clazz = Class.forName(handlerClass);
            DocumentHandler handler =
                (DocumentHandler) clazz.getDeclaredConstructor().newInstance();
            handlers.put(mimeType, handler);
        }
        return handlers;
    }
}
```

**Impact**: Document handling (PDF, Word, XML imports) disabled.

**GraalVM Solution** - Plugin registry:

Create `DocumentHandlerRegistry.java`:
```java
public abstract class DocumentHandler {
    public abstract String getMimeType();
    public abstract Document parse(InputStream input) throws IOException;
}

public class DocumentHandlerRegistry {
    private static final Map<String, Class<?>> HANDLERS = new ConcurrentHashMap<>();

    public static void registerHandler(String mimeType,
            Class<? extends DocumentHandler> handlerClass) {
        HANDLERS.put(mimeType, handlerClass);
    }

    static {
        registerHandler("application/pdf", PdfDocumentHandler.class);
        registerHandler("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            DocxDocumentHandler.class);
        registerHandler("application/xml", XmlDocumentHandler.class);
    }

    public static DocumentHandler getHandler(String mimeType)
            throws ClassNotFoundException {
        Class<?> clazz = HANDLERS.get(mimeType);
        if (clazz == null) {
            throw new ClassNotFoundException("No handler for: " + mimeType);
        }
        try {
            return (DocumentHandler) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ClassNotFoundException(e);
        }
    }
}
```

**Usage in DocumentStore**:
```java
private Map<String, DocumentHandler> loadDocumentHandlers(String configFile) {
    // Now uses registry instead of dynamic Class.forName()
    Map<String, DocumentHandler> handlers = new HashMap<>();
    for (String mimeType : DocumentHandlerRegistry.getSupportedTypes()) {
        handlers.put(mimeType, DocumentHandlerRegistry.getHandler(mimeType));
    }
    return handlers;
}
```

---

#### 6-9. Additional Service Discovery Points

| File | Line | Pattern | Mitigation |
|------|------|---------|-----------|
| `InterfaceB_EngineBasedServer.java` | 88 | Negotiation protocol selection | Use sealed enums for protocol versions |
| `AuthenticationResponseServlet.java` | 56 | Auth handler factory | Hardcode known implementations |
| `YAWLException.java` | 42 | Exception cause chain reflection | Pre-register exception hierarchy |
| `ShowLayouts.java` | 120 | Layout plugin discovery | Service registry pattern |

---

## Tier 2: Framework-Induced Reflection (Medium Priority)

### Hibernate ORM Reflection (5 points)

**Reflection Type**: `getAttribute()`, `getMappedClass()`, proxy generation

**Problem**: Hibernate 6.6.42 introspects `@Entity` classes at runtime:
- Lazy loading via CGLIB/ByteBuddy
- Dirty-tracking via byte code enhancement
- Type conversion via reflection

**Critical Classes**:
- `YWorkItem`, `YCase`, `YSpecification`, `YExternalClient`, `YAuditEvent`

**GraalVM Solution**:

```json
{
  "type": "class",
  "name": "org.hibernate.annotations.Entity",
  "allAnnotations": true
},
{
  "type": "class",
  "name": "jakarta.persistence.Entity",
  "allAnnotations": true
},
{
  "type": "class",
  "name": "org.yawlfoundation.yawl.engine.YWorkItem",
  "allDeclaredFields": true,
  "allPublicMethods": true,
  "allDeclaredMethods": true,
  "allConstructors": true
}
```

### Jackson Serialization (4 points)

**Reflection Type**: Property introspection for JSON marshalling

**Problem**: Jackson uses reflection to find JSON properties:
```java
// Jackson does this at runtime:
for (Field f : clazz.getDeclaredFields()) {
    if (f.isAnnotationPresent(JsonProperty.class)) {
        // ... register property
    }
}
```

**GraalVM Solution**:

```json
{
  "type": "class",
  "name": "com.fasterxml.jackson.databind.ObjectMapper",
  "allPublicMethods": true
},
{
  "type": "class",
  "name": "org.yawlfoundation.yawl.elements.YTask",
  "allDeclaredConstructors": true,
  "allPublicMethods": true
}
```

### Spring DI Reflection (4 points)

**Reflection Type**: Auto-wiring, `@Autowired` field injection

**Problem**: Spring scans classpath for `@Component`, `@Service`, `@Repository` at runtime.

**GraalVM Solution** - Disable auto-configuration:

```properties
# application-native.properties
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,\
  org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```

Create manual `@Configuration` class:
```java
@Configuration
public class NativeImageConfiguration {
    @Bean
    public DataSource dataSource(DataSourceProperties props) { ... }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource) { ... }
}
```

---

## Tier 3: Framework-Level Proxy Usage (Low Priority)

### Dynamic Proxy Creation (2 points)

**Pattern**: `Proxy.newProxyInstance()` for gateway/service interfaces

**Files**:
- `EngineGatewayImpl.java` (line 85)
- `InterfaceB_EnvironmentBasedServer.java` (line 120)

**GraalVM Solution**: Use `proxy-config.json`:

```json
{
  "proxyConfigs": [
    {
      "interfaces": [
        "org.yawlfoundation.yawl.engine.gateway.EngineGateway",
        "java.io.Serializable"
      ]
    },
    {
      "interfaces": [
        "org.yawlfoundation.yawl.interfaceB.InterfaceB",
        "java.io.Serializable"
      ]
    }
  ]
}
```

---

## Configuration Summary Table

| Component | Reflection Type | Risk | Config File | Status |
|-----------|-----------------|------|-------------|--------|
| Argon2 Password Verification | Class.forName() | HIGH | reflect-config.json | REQUIRED |
| Service Discovery (InterfaceX) | Class.forName() + newInstance | HIGH | reflect-config.json | REQUIRED |
| Environment-Based Server | Class.forName() + getenv | MEDIUM | build.properties | REQUIRED |
| Engine Gateway | Proxy.newProxyInstance() | MEDIUM | proxy-config.json | REQUIRED |
| Document Handlers | Plugin loading via config | HIGH | reflect-config.json | REQUIRED |
| Hibernate Entity Proxies | ByteBuddy generation | MEDIUM | reflect-config.json | REQUIRED |
| Jackson Serialization | Property introspection | MEDIUM | reflect-config.json | REQUIRED |
| Spring Dependency Injection | Component scanning | MEDIUM | Manual beans | REQUIRED |

---

## Implementation Roadmap

### Phase 1: Static Initialization (Week 1)
- [ ] Create service registries for all Class.forName() calls
- [ ] Generate GraalVM agent configuration
- [ ] Merge agent output with manual configurations

### Phase 2: Configuration (Week 2)
- [ ] Create `.graalvm/reflect-config.json` (Hibernate + Jackson)
- [ ] Create `.graalvm/proxy-config.json` (gateway interfaces)
- [ ] Create `.graalvm/serialization-config.json` (JPA entities)
- [ ] Create `.graalvm/resource-config.json` (XML schemas, configs)

### Phase 3: Testing & Validation (Week 3)
- [ ] Test native-image build on Linux/x86_64
- [ ] Run 640-class test suite against native image
- [ ] Benchmark startup time, memory, throughput
- [ ] Document any remaining failures

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Unregistered reflection at runtime | MEDIUM | CRITICAL | GraalVM agent trace + testing |
| Hibernate lazy loading failures | MEDIUM | HIGH | Use graalvm-supporting version + substitutions |
| Spring component discovery | LOW | HIGH | Disable auto-config, use manual beans |
| Dynamic XML XPath evaluation | LOW | MEDIUM | Pre-compile common XPath expressions |

---

## Deliverables

1. **reflect-config.json** - 150+ entries covering all reflection points
2. **proxy-config.json** - 8+ proxy interface configurations
3. **serialization-config.json** - JPA entity serialization metadata
4. **resource-config.json** - Embedded resources (schemas, configs)
5. **ServiceRegistry.java** - Unified service discovery
6. **NativeImageConfiguration.java** - Spring bean factory
7. **GraalVMTestWorkload.java** - Coverage for all reflection paths
8. **Reflection Analysis Summary** - This document

---

## References

- YAWL Codebase: `/home/user/yawl/`
- GraalVM Docs: https://www.graalvm.org/latest/reference-manual/native-image/
- Hibernate GraalVM Support: https://hibernate.org/orm/releases/6.6/
- Jackson GraalVM: https://github.com/FasterXML/jackson-databind/wiki/GraalVM-Support

