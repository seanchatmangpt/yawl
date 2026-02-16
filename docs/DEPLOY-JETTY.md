# Deploying YAWL v5.2 on Eclipse Jetty 11+

**Target Runtime**: Eclipse Jetty 11.x, 12.x
**Java Version**: Java 21+ (required for Jakarta EE 10 support)
**Estimated Time**: 30-45 minutes

## Prerequisites

### System Requirements
- Java 21 JDK (or higher)
- Eclipse Jetty 11.x or 12.x
- PostgreSQL 13+ OR MySQL 8+ OR H2 2.2+
- 4GB+ RAM recommended
- 2GB free disk space

### Verify Prerequisites
```bash
# Check Java version
java -version  # Must be Java 21 or higher

# Check Jetty version (if already installed)
java -jar jetty-home/start.jar --version
```

## Installation Steps

### Step 1: Download and Install Jetty

```bash
# Download Jetty 12 (latest)
cd /opt
wget https://central.maven.org/maven2/org/eclipse/jetty/jetty-home/12.0.0/jetty-home-12.0.0.tar.gz

# Extract
tar -xzf jetty-home-12.0.0.tar.gz
ln -s jetty-home-12.0.0 jetty

# Set JETTY_HOME
export JETTY_HOME=/opt/jetty
echo 'export JETTY_HOME=/opt/jetty' >> ~/.bashrc

# Create separate base directory for instances
mkdir -p /opt/jetty-instances/yawl
cd /opt/jetty-instances/yawl
java -jar $JETTY_HOME/start.jar --add-modules=server,http,jsp,jstl
```

### Step 2: Configure JVM Parameters

**Edit `/opt/jetty-instances/yawl/start.d/jvm.ini`**:

```ini
# Server JVM Configuration
--module=jvm
-server
-Xmx2048m
-Xms1024m
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:InitiatingHeapOccupancyPercent=35
-Djava.awt.headless=true
-Dfile.encoding=UTF-8
-Duser.timezone=UTC
-Dyawl.jwt.secret=GENERATED_SECRET_HERE
-Dyawl.db.driver=org.postgresql.Driver
-Dyawl.db.url=jdbc:postgresql://localhost:5432/yawl
-Dyawl.db.username=yawluser
-Dyawl.db.password=yawlpass
```

### Step 3: Configure Jetty Modules

```bash
# From /opt/jetty-instances/yawl directory
java -jar $JETTY_HOME/start.jar --add-modules=webapp,jndi,resources

# Verify modules
java -jar $JETTY_HOME/start.jar --list-modules | grep -i "enabled"
```

### Step 4: Build YAWL WAR Files

From the YAWL source directory:

```bash
cd /home/user/yawl

# Full build
ant clean
ant buildAll

# Verify output
ls -la output/*.war
```

### Step 5: Configure Database

#### PostgreSQL Setup

```sql
-- Connect as postgres user
psql -U postgres

-- Create database
CREATE DATABASE yawl
  ENCODING 'UTF8'
  LC_COLLATE 'en_US.UTF-8'
  LC_CTYPE 'en_US.UTF-8'
  TEMPLATE template0;

-- Create user
CREATE ROLE yawluser WITH LOGIN PASSWORD 'yawlpass';

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE yawl TO yawluser;
ALTER ROLE yawluser CREATEDB;

\q
```

#### MySQL Setup

```sql
-- Connect as root
mysql -u root -p

-- Create database
CREATE DATABASE yawl
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- Create user
CREATE USER 'yawluser'@'localhost' IDENTIFIED BY 'yawlpass';

-- Grant permissions
GRANT ALL PRIVILEGES ON yawl.* TO 'yawluser'@'localhost';
FLUSH PRIVILEGES;

EXIT;
```

### Step 6: Deploy WAR Files

```bash
# Create webapps directory
mkdir -p /opt/jetty-instances/yawl/webapps

# Copy WAR files
cp /home/user/yawl/output/*.war /opt/jetty-instances/yawl/webapps/

# Verify
ls -la /opt/jetty-instances/yawl/webapps/
```

### Step 7: Create Jetty Context XML

**Create `/opt/jetty-instances/yawl/etc/yawl.xml`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE Configure PUBLIC "-//Jetty//Configure//EN" "https://www.eclipse.org/jetty/configure_12_0.dtd">
<Configure id="Server" class="org.eclipse.jetty.server.Server">

  <!-- Thread Pool Configuration -->
  <Call name="setThreadPool">
    <Arg>
      <New id="threadpool" class="org.eclipse.jetty.util.thread.QueuedThreadPool">
        <Set name="maxThreads">200</Set>
        <Set name="minThreads">10</Set>
        <Set name="idleTimeout">60000</Set>
        <Set name="reservedThreads">0</Set>
      </New>
    </Arg>
  </Call>

  <!-- Database Resource via JNDI -->
  <New id="yawlDB" class="org.eclipse.jetty.plus.jndi.Resource">
    <Arg>jdbc/yawldb</Arg>
    <Arg>
      <New class="com.zaxxer.hikari.HikariDataSource">
        <Set name="driverClassName">org.postgresql.Driver</Set>
        <Set name="jdbcUrl">jdbc:postgresql://localhost:5432/yawl</Set>
        <Set name="username">yawluser</Set>
        <Set name="password">yawlpass</Set>
        <Set name="maximumPoolSize">20</Set>
        <Set name="minimumIdle">5</Set>
        <Set name="connectionTimeout">30000</Set>
        <Set name="idleTimeout">600000</Set>
        <Set name="maxLifetime">1800000</Set>
      </New>
    </Arg>
  </New>

  <!-- YAWL Web Application -->
  <New id="yawlWebApp" class="org.eclipse.jetty.webapp.WebAppContext">
    <Set name="war">/opt/jetty-instances/yawl/webapps/yawl.war</Set>
    <Set name="contextPath">/yawl</Set>
    <Set name="persistTempDir">true</Set>

    <!-- Session Configuration -->
    <Set name="sessionHandler">
      <New class="org.eclipse.jetty.server.session.SessionHandler">
        <Set name="sessionManager">
          <New class="org.eclipse.jetty.server.session.SessionManager">
            <Set name="cookieHttpOnly">true</Set>
            <Set name="cookieSecure">true</Set>
            <Set name="cookieSameSite">Strict</Set>
            <Set name="maxInactiveInterval">3600</Set>
          </New>
        </Set>
      </New>
    </Set>

    <!-- Environment Variables -->
    <Call class="org.eclipse.jetty.plus.jndi.EnvEntry" name="bind">
      <Arg>java:comp/env/JWT_SECRET</Arg>
      <Arg type="java.lang.String">YOUR_GENERATED_SECRET</Arg>
    </Call>

  </New>

  <!-- Other Service Web Apps -->
  <New class="org.eclipse.jetty.webapp.WebAppContext">
    <Set name="war">/opt/jetty-instances/yawl/webapps/resourceService.war</Set>
    <Set name="contextPath">/resourceService</Set>
  </New>

  <New class="org.eclipse.jetty.webapp.WebAppContext">
    <Set name="war">/opt/jetty-instances/yawl/webapps/workletService.war</Set>
    <Set name="contextPath">/workletService</Set>
  </New>

</Configure>
```

### Step 8: Configure HTTP Connector

**Create `/opt/jetty-instances/yawl/start.d/http.ini`**:

```ini
--module=http
jetty.http.host=0.0.0.0
jetty.http.port=8080
jetty.http.idleTimeout=30000
jetty.http.acceptorThreads=2
jetty.http.selectorThreads=-1
jetty.http.outputBufferSize=32768
jetty.http.requestHeaderSize=8192
jetty.http.responseHeaderSize=8192
```

### Step 9: Start Jetty

```bash
# From /opt/jetty-instances/yawl directory
java -jar $JETTY_HOME/start.jar

# Or in background with logging
java -jar $JETTY_HOME/start.jar > logs/jetty.log 2>&1 &

# Check logs
tail -f logs/jetty.log
```

### Step 10: Verify Deployment

Wait 30-60 seconds for startup:

```bash
# Check YAWL Engine
curl -v http://localhost:8080/yawl/api/ib/workitems

# Expected: 200 OK with JSON
```

## Database Initialization

On first startup, Hibernate will create tables automatically. To manually initialize:

```bash
export YAWL_DB_INITIALIZE=true
java -jar $JETTY_HOME/start.jar
```

## Configuration Files

### Jetty Configuration

Key files in `/opt/jetty-instances/yawl/`:

```
start.d/
├── jvm.ini              # JVM parameters
├── http.ini             # HTTP connector
├── https.ini            # HTTPS (if using SSL)
├── jndi.ini             # JNDI configuration
└── deploy.ini           # Deployment options

etc/
├── jetty.xml            # Main server config
├── yawl.xml             # YAWL application config
└── webdefault.xml       # Web app defaults

webapps/
├── yawl.war
├── resourceService.war
└── (other services)
```

## SSL/TLS Configuration

### Generate Self-Signed Certificate

```bash
# From /opt/jetty-instances/yawl
java -jar $JETTY_HOME/start.jar --add-modules=https

keytool -keystore etc/keystore.jks \
  -alias jetty \
  -genkey -keyalg RSA \
  -validity 365 \
  -storepass changeit \
  -keypass changeit \
  -dname "CN=localhost, OU=Development, O=YAWL, C=US"
```

**Create `start.d/https.ini`**:

```ini
--module=https
jetty.https.host=0.0.0.0
jetty.https.port=8443
jetty.sslContext.keyStorePath=etc/keystore.jks
jetty.sslContext.keyStorePassword=changeit
jetty.sslContext.keyManagerPassword=changeit
jetty.sslContext.trustStorePath=etc/keystore.jks
jetty.sslContext.trustStorePassword=changeit
```

### Using Let's Encrypt

```bash
# Install Certbot
sudo apt-get install certbot

# Get certificate
sudo certbot certonly --standalone -d yourdomain.com

# Convert to JKS
openssl pkcs12 -export \
  -in /etc/letsencrypt/live/yourdomain.com/fullchain.pem \
  -inkey /etc/letsencrypt/live/yourdomain.com/privkey.pem \
  -out keystore.p12 \
  -name jetty \
  -passout pass:changeit

keytool -importkeystore \
  -srckeystore keystore.p12 \
  -srcstoretype PKCS12 \
  -srcstorepass changeit \
  -destkeystore etc/keystore.jks \
  -deststoretype JKS \
  -deststorepass changeit
```

## Performance Tuning

### Thread Pool Optimization

**In `start.d/jvm.ini`**:

```ini
# For high-concurrency scenarios (100+ concurrent users)
-Dyawl.jetty.threads.max=500
-Dyawl.jetty.threads.min=50
-Dyawl.jetty.threads.timeout=60000
```

### Connector Tuning

**In `start.d/http.ini`**:

```ini
jetty.http.acceptorThreads=4
jetty.http.selectorThreads=4
jetty.http.outputBufferSize=65536
jetty.http.requestHeaderSize=16384
jetty.http.responseHeaderSize=16384
```

### Memory Configuration

For large deployments:

```bash
# In start.d/jvm.ini
-Xmx8192m
-Xms4096m
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-Xloggc:logs/gc.log
```

## Monitoring and Health Checks

### Health Endpoint

```bash
curl -v http://localhost:8080/yawl/api/ib/health
```

### Access Logging

**Edit `/opt/jetty-instances/yawl/etc/jetty.xml`** (add to Server):

```xml
<Call name="addBean">
  <Arg>
    <New class="org.eclipse.jetty.server.handler.RequestLogHandler">
      <Set name="requestLog">
        <New class="org.eclipse.jetty.server.CustomRequestLog">
          <Arg>
            <New class="org.eclipse.jetty.server.Slf4jRequestLogWriter"/>
          </Arg>
          <Arg>%h %l %u %t "%r" %s %B "%{Referer}i" "%{User-Agent}i"</Arg>
        </New>
      </Set>
    </New>
  </Arg>
</Call>
```

View logs:
```bash
tail -f /opt/jetty-instances/yawl/logs/jetty.log
```

## Clustering

### Enable Session Persistence

**In `start.d/deploy.ini`**:

```ini
--module=sessions-jdbc

# Database for session storage
jetty.session.jdbc.url=jdbc:postgresql://localhost:5432/yawl_sessions
jetty.session.jdbc.user=yawluser
jetty.session.jdbc.password=yawlpass
```

Create sessions table:
```sql
CREATE TABLE jetty_sessions (
  sessionid VARCHAR(120) NOT NULL PRIMARY KEY,
  contextpath VARCHAR(60) NOT NULL,
  vhost VARCHAR(60),
  accesstime BIGINT,
  lastmodtime BIGINT,
  expirytime BIGINT,
  maxinterval BIGINT,
  sessiondata BYTEA
);
```

## Troubleshooting

### Jetty Won't Start

1. Check Java version:
   ```bash
   java -version  # Must be Java 21+
   ```

2. Check for port conflicts:
   ```bash
   netstat -tulpn | grep 8080
   ```

3. Review logs:
   ```bash
   tail -f /opt/jetty-instances/yawl/logs/jetty.log
   ```

### OutOfMemory Error

Increase heap in `start.d/jvm.ini`:
```ini
-Xmx4096m
-Xms2048m
```

### Database Connection Issues

Verify PostgreSQL is running:
```bash
psql -U yawluser -d yawl -h localhost -c "SELECT 1;"
```

Check connection pool settings in `etc/yawl.xml`.

### Slow Performance

1. Check GC logs:
   ```bash
   tail -f /opt/jetty-instances/yawl/logs/gc.log
   ```

2. Monitor thread usage:
   ```bash
   jvisualvm  # Java Visual VM
   ```

3. Verify database query performance:
   ```bash
   psql -d yawl -c "EXPLAIN ANALYZE SELECT * FROM ..."
   ```

## Upgrading YAWL

### From 5.1 to 5.2

1. Backup database:
   ```bash
   pg_dump -U yawluser yawl > yawl_backup.sql
   ```

2. Stop Jetty:
   ```bash
   # Find PID
   ps aux | grep start.jar
   # Kill gracefully
   kill -TERM <PID>
   ```

3. Replace WAR files:
   ```bash
   rm /opt/jetty-instances/yawl/webapps/*.war
   cp /home/user/yawl/output/*.war /opt/jetty-instances/yawl/webapps/
   ```

4. Restart:
   ```bash
   java -jar $JETTY_HOME/start.jar
   ```

5. Verify:
   ```bash
   tail -f /opt/jetty-instances/yawl/logs/jetty.log
   ```

## Production Checklist

- [ ] Java 21+ installed
- [ ] Jetty 11+ installed and configured
- [ ] Database created and configured
- [ ] WAR files built and deployed
- [ ] SSL/TLS certificate installed
- [ ] JVM memory tuned
- [ ] Thread pool configured
- [ ] Database backups enabled
- [ ] Access logging configured
- [ ] Health endpoint verified
- [ ] Session persistence enabled (if clustering)
- [ ] Monitoring configured

## Additional Resources

- [Eclipse Jetty Documentation](https://eclipse.dev/jetty/documentation/)
- [Jetty Module System](https://eclipse.dev/jetty/documentation/jetty-12/programming-guide/server-architecture/modules.html)
- [YAWL Engine Configuration](../README.md)
- [Database Setup Guide](./DEPLOY-DATABASE.md)

## Support

For YAWL engine issues:
- Check `/opt/jetty-instances/yawl/logs/jetty.log`
- Review YAWL documentation at https://yawlfoundation.org

For Jetty-specific issues:
- Review [Jetty troubleshooting](https://eclipse.dev/jetty/documentation/jetty-12/)
