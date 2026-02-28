# Tutorial: YAWL Web Applications Getting Started

By the end of this tutorial you will have a running YAWL Engine Webapp (war artifact), understand how web applications in YAWL are structured, configure it for your deployment target, and execute your first workflow through the web interface.

---

## What are YAWL Web Applications?

YAWL Web Applications are Java servlet-based web services that expose the YAWL workflow engine and related services via REST API and web UI. The primary application is `yawl-engine-webapp`, which provides:

- **REST API**: JSON-based workflow operations (case management, task execution)
- **Web UI**: Browser-based interface for case management
- **Resource Service**: Participant and role management
- **Authentication**: User login and authorization

---

## Prerequisites

```bash
java -version
```

Expected: Java 25+ (JDK required, not just JRE).

```bash
mvn -version
```

Expected: Maven 3.9+.

```bash
# Choose your servlet container
# Option 1: Jetty (embedded)
mvn org.eclipse.jetty:jetty-maven-plugin:help

# Option 2: Tomcat (local installation)
ls -la /opt/tomcat/bin/catalina.sh

# Option 3: WildFly (local installation)
ls -la /opt/wildfly/bin/standalone.sh
```

---

## Step 1: Build the WAR Artifact

Build the YAWL project with WAR packaging:

```bash
cd /path/to/yawl
mvn clean package -DskipTests -T 1.5C -pl yawl-webapps,yawl-webapps/yawl-engine-webapp
```

The WAR artifact is created at:

```
yawl-webapps/yawl-engine-webapp/target/yawl-engine-webapp-6.0.0-GA.war
```

---

## Step 2: Run with Embedded Jetty (Simplest)

For quick local testing, run the webapp with Maven's Jetty plugin:

```bash
cd yawl-webapps/yawl-engine-webapp
mvn jetty:run
```

Expected output:

```
[INFO] Starting Jetty server...
[INFO] Jetty server started at http://localhost:8080/yawl-engine-webapp
```

Open your browser and navigate to:

```
http://localhost:8080/yawl-engine-webapp
```

---

## Step 3: Deploy to Tomcat (Standard Production)

### 3.1: Install Tomcat

```bash
# Download Tomcat 10+
wget https://archive.apache.org/dist/tomcat/tomcat-10/v10.1.x/bin/apache-tomcat-10.1.x.tar.gz
tar xzf apache-tomcat-10.1.x.tar.gz -C /opt
export CATALINA_HOME=/opt/apache-tomcat-10.1.x
```

### 3.2: Copy WAR to webapps

```bash
cp yawl-webapps/yawl-engine-webapp/target/yawl-engine-webapp-6.0.0-GA.war \
   $CATALINA_HOME/webapps/yawl-engine.war
```

### 3.3: Configure YAWL Properties

Create `$CATALINA_HOME/conf/yawl.properties`:

```properties
# Database Connection
yawl.db.driver=org.postgresql.Driver
yawl.db.url=jdbc:postgresql://localhost:5432/yawl
yawl.db.username=yawl_user
yawl.db.password=secret_password

# Engine Configuration
yawl.engine.persistence=true
yawl.engine.log_level=INFO

# Resource Service
yawl.resource.service.enabled=true
yawl.resource.service.host=localhost
yawl.resource.service.port=8081
```

### 3.4: Start Tomcat

```bash
$CATALINA_HOME/bin/startup.sh

# Monitor logs
tail -f $CATALINA_HOME/logs/catalina.out
```

Navigate to:

```
http://localhost:8080/yawl-engine
```

---

## Step 4: Configure for Production

### 4.1: Database Setup

Create PostgreSQL database for YAWL:

```bash
createdb -U postgres yawl
psql -U postgres yawl < /path/to/yawl/schema/postgresql-schema.sql
```

### 4.2: Application Properties

Configure `$CATALINA_HOME/conf/yawl-application.properties`:

```properties
# Server Configuration
server.port=8080
server.servlet.context-path=/yawl-engine
server.shutdown=graceful
server.shutdown.wait-time=60s

# Database
spring.datasource.url=jdbc:postgresql://db.example.com:5432/yawl_prod
spring.datasource.username=yawl_prod_user
spring.datasource.password=${DB_PASSWORD}
spring.datasource.hikari.maximum-pool-size=20

# Persistence
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQL10Dialect

# Logging
logging.level.root=INFO
logging.level.org.yawlfoundation.yawl=DEBUG
logging.file.name=/var/log/yawl/application.log

# Security
server.ssl.enabled=true
server.ssl.key-store=/etc/yawl/keystore.p12
server.ssl.key-store-password=${KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=yawl-server
```

### 4.3: Resource Service Configuration

Configure `$CATALINA_HOME/conf/resource-service.properties`:

```properties
resource.service.url=http://resource-service:8081
resource.service.password=secure_password
resource.service.admin.username=admin
resource.service.admin.password=admin_password
```

---

## Step 5: Deploy a Workflow Specification

### 5.1: Access the Web UI

Navigate to `http://localhost:8080/yawl-engine` and log in with default credentials:

```
Username: admin
Password: YAWL
```

### 5.2: Deploy a Specification

1. Go to **Admin → Specifications → Upload New**
2. Upload your YAWL specification XML file
3. Click **Start Engine** to activate the specification

### 5.3: Create and Run a Case

1. Go to **Case Management**
2. Click **Create New Case** for your specification
3. Provide case input data
4. Monitor case execution in real-time

---

## Step 6: Call the REST API

The webapp exposes a REST API for programmatic access:

```bash
# Get session token
curl -X POST http://localhost:8080/yawl-engine/api/session/start \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"YAWL"}'

# Response:
# {"sessionHandle":"....","userId":"admin"}

# List cases
curl http://localhost:8080/yawl-engine/api/cases \
  -H "Cookie: JSESSIONID=..." \
  -H "X-YAWL-UserID: admin"

# Create case
curl -X POST http://localhost:8080/yawl-engine/api/cases \
  -H "Content-Type: application/json" \
  -d '{
    "specificationID":"MySpecURI",
    "specificationVersion":"1.0",
    "caseLabel":"Case-001",
    "inputData":"<data/>"
  }' \
  -H "X-YAWL-UserID: admin"
```

For complete API reference, see [reference/api-reference.md](../reference/api-reference.md).

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│  yawl-engine-webapp (WAR)                              │
├─────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────┐│
│ │  REST API (Jersey JAX-RS)                           ││
│ │  - /api/cases       (case management)               ││
│ │  - /api/tasks       (task management)               ││
│ │  - /api/session     (authentication)                ││
│ └──────────────────────────────────────────────────────┘│
│ ┌──────────────────────────────────────────────────────┐│
│ │  YEngine (Persistence-based)                        ││
│ │  - Case execution                                   ││
│ │  - Task enablement                                  ││
│ │  - Event notification                              ││
│ └──────────────────────────────────────────────────────┘│
│ ┌──────────────────────────────────────────────────────┐│
│ │  Authentication Service                            ││
│ │  - User login/logout                               ││
│ │  - Session management                              ││
│ └──────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────┘
         ↓
┌──────────────────┐
│  PostgreSQL      │
│  or H2 (dev)     │
└──────────────────┘
```

---

## Configuration Options

### Environment Variables

Configure via environment variables in Tomcat startup script:

```bash
export YAWL_DB_URL=jdbc:postgresql://localhost:5432/yawl
export YAWL_DB_USER=yawl
export YAWL_DB_PASS=password
export YAWL_LOG_LEVEL=INFO
export YAWL_PORT=8080
```

### web.xml Deployment Descriptor

The WAR contains `WEB-INF/web.xml` with servlet configuration. Key sections:

```xml
<servlet>
    <servlet-name>YawlEngineServlet</servlet-name>
    <servlet-class>org.yawlfoundation.yawl.engine.YawlEngineServlet</servlet-class>
    <init-param>
        <param-name>engine.class</param-name>
        <param-value>org.yawlfoundation.yawl.engine.YEngine</param-value>
    </init-param>
</servlet>
```

---

## Performance Tuning

### Tomcat JVM Settings

Edit `$CATALINA_HOME/bin/setenv.sh`:

```bash
export CATALINA_OPTS="
  -Xms2g
  -Xmx4g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -Dcom.sun.management.jmxremote
  -Dcom.sun.management.jmxremote.port=9010
  -Dcom.sun.management.jmxremote.authenticate=false
  -Dcom.sun.management.jmxremote.ssl=false
"
```

### Database Connection Pooling

In `yawl-application.properties`:

```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

---

## Troubleshooting

### WAR Deployment Fails

**Problem**: `java.lang.ClassNotFoundException: jakarta.servlet.ServletException`

**Solution**: Verify Tomcat version is 10+. Earlier versions use `javax.servlet`:

```bash
$CATALINA_HOME/bin/version.sh
```

### Database Connection Error

**Problem**: `org.postgresql.util.PSQLException: Connection refused`

**Solution**: Verify database is running and properties are correct:

```bash
psql -U yawl -h localhost -d yawl -c "SELECT 1"
```

### Out of Memory During Case Execution

**Problem**: `java.lang.OutOfMemoryError: Java heap space`

**Solution**: Increase heap size in `setenv.sh` and enable periodic cleanup:

```bash
export CATALINA_OPTS="-Xmx8g -XX:+DisableExplicitGC"
```

---

## Next Steps

1. **Deploy to Production**: [how-to/deployment/production.md](../how-to/deployment/production.md)
2. **Configure Resource Service**: [how-to/configure-resource-service.md](../how-to/configure-resource-service.md)
3. **Enable HTTPS/TLS**: [how-to/deployment/enable-tls.md](../how-to/deployment/enable-tls.md)
4. **Integrate with External Systems**: [how-to/integration/external-systems.md](../how-to/integration/external-systems.md)

---

## Further Reading

- **[REST API Reference](../reference/api-reference.md)**: Complete API documentation
- **[Deployment Guide](../how-to/deployment/guide.md)**: Multi-environment deployment
- **[Interface Architecture](../explanation/interface-architecture.md)**: Interface A, B, E, X overview
