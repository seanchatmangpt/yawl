# Deploying YAWL v6.0.0 on WildFly 27+

**Target Runtime**: WildFly 27.x, 28.x
**Java Version**: Java 25 (required for Jakarta EE 10 support and virtual threads)
**Estimated Time**: 45-60 minutes

## Prerequisites

### System Requirements
- Java 25 JDK (or higher)
- WildFly 27.x or 28.x
- PostgreSQL 13+ OR MySQL 8+ OR H2 2.2+
- 6GB+ RAM recommended
- 4GB free disk space

### Verify Prerequisites
```bash
# Check Java version
java -version  # Must be Java 25+

# Check WildFly version (if installed)
$WILDFLY_HOME/bin/standalone.sh --version
```

## Installation Steps

### Step 1: Download and Install WildFly

```bash
# Download WildFly 28 (latest)
cd /opt
wget https://github.com/wildfly/wildfly/releases/download/28.0.0.Final/wildfly-28.0.0.Final.tar.gz

# Extract
tar -xzf wildfly-28.0.0.Final.tar.gz
ln -s wildfly-28.0.0.Final wildfly

# Set WILDFLY_HOME
export WILDFLY_HOME=/opt/wildfly
echo 'export WILDFLY_HOME=/opt/wildfly' >> ~/.bashrc

# Create application directory
mkdir -p /opt/wildfly-instances/yawl
```

### Step 2: Configure WildFly for YAWL

Copy configuration:

```bash
# Use full profile (includes all subsystems)
cp $WILDFLY_HOME/standalone/configuration/standalone-full.xml \
   /opt/wildfly-instances/yawl/standalone.xml
```

### Step 3: Configure JVM Parameters (Java 25 Optimized)

**Edit `/opt/wildfly-instances/yawl/standalone.conf` (Unix/Linux/macOS)**:

```bash
# Create the file
cat > /opt/wildfly-instances/yawl/standalone.conf << 'EOF'
# JVM Settings - Java 25 Optimized
JAVA_OPTS="-server"
JAVA_OPTS="$JAVA_OPTS -Xmx2048m"
JAVA_OPTS="$JAVA_OPTS -Xms1024m"

# Java 25 Features
JAVA_OPTS="$JAVA_OPTS -XX:+UseCompactObjectHeaders"

# Virtual Threads Support (Java 25)
JAVA_OPTS="$JAVA_OPTS -XX:+UseVirtualThreads"

# G1GC Configuration
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=200"
JAVA_OPTS="$JAVA_OPTS -XX:InitiatingHeapOccupancyPercent=35"

# Container startup optimization
JAVA_OPTS="$JAVA_OPTS -XX:+UseAOTCache"

# Headless mode
JAVA_OPTS="$JAVA_OPTS -Djava.awt.headless=true"
JAVA_OPTS="$JAVA_OPTS -Dfile.encoding=UTF-8"
JAVA_OPTS="$JAVA_OPTS -Duser.timezone=UTC"

# YAWL Configuration
JAVA_OPTS="$JAVA_OPTS -Dyawl.jwt.secret=$(openssl rand -base64 32)"
JAVA_OPTS="$JAVA_OPTS -Dyawl.db.driver=org.postgresql.Driver"
JAVA_OPTS="$JAVA_OPTS -Dyawl.db.url=jdbc:postgresql://localhost:5432/yawl"
JAVA_OPTS="$JAVA_OPTS -Dyawl.db.username=yawluser"
JAVA_OPTS="$JAVA_OPTS -Dyawl.db.password=yawlpass"

# Logging
JAVA_OPTS="$JAVA_OPTS -Dorg.jboss.as.logging.per-deployment=true"

export JAVA_OPTS
EOF

chmod +x /opt/wildfly-instances/yawl/standalone.conf
```

**For Windows** (`standalone.conf.bat`):

```batch
@echo off
setlocal enabledelayedexpansion

set JAVA_OPTS=-server
set JAVA_OPTS=!JAVA_OPTS! -Xmx2048m
set JAVA_OPTS=!JAVA_OPTS! -Xms1024m
set JAVA_OPTS=!JAVA_OPTS! -XX:+UseCompactObjectHeaders
set JAVA_OPTS=!JAVA_OPTS! -XX:+UseVirtualThreads
set JAVA_OPTS=!JAVA_OPTS! -XX:+UseG1GC
set JAVA_OPTS=!JAVA_OPTS! -XX:MaxGCPauseMillis=200
set JAVA_OPTS=!JAVA_OPTS! -Djava.awt.headless=true
set JAVA_OPTS=!JAVA_OPTS! -Dfile.encoding=UTF-8
set JAVA_OPTS=!JAVA_OPTS! -Dyawl.jwt.secret=YOUR_SECRET_HERE

REM Rest of config...
```

### Step 4: Enable Virtual Threads in WildFly

Edit `standalone.xml` to enable virtual threads for the thread pool subsystem:

```xml
<!-- In <subsystem xmlns="urn:jboss:domain:threads:3.0"> -->
<subsystem xmlns="urn:jboss:domain:threads:3.0">
    <virtual-thread-executor name="yawl-virtual-threads"/>

    <thread-factory name="yawl-thread-factory"
                    group-name="yawl-threads"
                    thread-name-pattern="yawl-%t"
                    priority="5"/>

    <bounded-queue-thread-pool name="yawl-thread-pool"
                               thread-factory="yawl-thread-factory"
                               max-threads="200"
                               core-threads="10"
                               queue-length="100"
                               virtual-threads="true"/>
</subsystem>
```

### Step 5: Build YAWL WAR Files

```bash
cd /home/user/yawl

# Fast build using agent DX loop
bash scripts/dx.sh all

# Or full Maven build
mvn -T 1.5C clean package

# Verify
ls -la output/*.war
```

### Step 6: Configure Database

#### PostgreSQL Setup

```sql
psql -U postgres

CREATE DATABASE yawl
  ENCODING 'UTF8'
  LC_COLLATE 'en_US.UTF-8'
  LC_CTYPE 'en_US.UTF-8'
  TEMPLATE template0;

CREATE ROLE yawluser WITH LOGIN PASSWORD 'yawlpass';

GRANT ALL PRIVILEGES ON DATABASE yawl TO yawluser;
ALTER ROLE yawluser CREATEDB;

\q
```

#### MySQL Setup

```sql
mysql -u root -p

CREATE DATABASE yawl
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER 'yawluser'@'localhost' IDENTIFIED BY 'yawlpass';

GRANT ALL PRIVILEGES ON yawl.* TO 'yawluser'@'localhost';
FLUSH PRIVILEGES;

EXIT;
```

### Step 7: Configure WildFly Datasource

Use WildFly CLI to configure datasource:

```bash
# Start WildFly in admin mode (if not already running)
$WILDFLY_HOME/bin/standalone.sh -c standalone-full.xml &

# Wait for startup...
sleep 10

# Use jboss-cli to add datasource
$WILDFLY_HOME/bin/jboss-cli.sh --controller=localhost:9990 << 'EOF'
connect

# Add PostgreSQL driver
module add --name=org.postgresql \
  --resources=/path/to/postgresql-42.7.4.jar \
  --dependencies=jakarta.api,sun.jdk

# Add datasource
data-source add \
  --name=YawlDS \
  --jndi-name=java:jboss/datasources/YawlDS \
  --driver-name=postgresql \
  --connection-url=jdbc:postgresql://localhost:5432/yawl \
  --user-name=yawluser \
  --password=yawlpass \
  --max-pool-size=20 \
  --min-pool-size=5 \
  --flush-strategy=FailingConnectionOnly \
  --validate-on-match=true \
  --check-valid-connection-sql="SELECT 1"

# Verify
/subsystem=datasources/data-source=YawlDS:read-resource
EOF
```

### Step 8: Deploy WAR Files

```bash
# Copy WAR files to WildFly deployments directory
cp /home/user/yawl/output/*.war $WILDFLY_HOME/standalone/deployments/

# Create deployment scanner marker files
for war in $WILDFLY_HOME/standalone/deployments/*.war; do
  touch "${war}.dodeploy"
done
```

### Step 9: Configure WildFly XML (Advanced)

**Edit the datasource directly in `standalone-full.xml`**:

```xml
<!-- Add to <subsystem xmlns="urn:jboss:domain:datasources:7.1"> -->
<datasources>
  <datasource jndi-name="java:jboss/datasources/YawlDS" pool-name="YawlDS_Pool" enabled="true" use-java-context="true">
    <connection-url>jdbc:postgresql://localhost:5432/yawl</connection-url>
    <driver-name>postgresql</driver-name>
    <security>
      <user-name>yawluser</user-name>
      <password>yawlpass</password>
    </security>
    <pool>
      <min-pool-size>5</min-pool-size>
      <max-pool-size>20</max-pool-size>
      <prefill>false</prefill>
      <use-strict-min>false</use-strict-min>
      <flush-strategy>FailingConnectionOnly</flush-strategy>
    </pool>
    <validation>
      <check-valid-connection-sql>SELECT 1</check-valid-connection-sql>
      <validate-on-match>true</validate-on-match>
      <background-validation>true</background-validation>
      <background-validation-millis>120000</background-validation-millis>
    </validation>
    <statement>
      <track-statements>NOW_CLOSED_AFTER_USE</track-statements>
      <prepared-statement-cache-size>32</prepared-statement-cache-size>
      <share-prepared-statements>true</share-prepared-statements>
    </statement>
  </datasource>

  <drivers>
    <driver name="postgresql" module="org.postgresql">
      <xa-datasource-class>org.postgresql.xa.PGXADataSource</xa-datasource-class>
    </driver>
  </drivers>
</datasources>
```

### Step 10: Start WildFly

```bash
# Start standalone server
$WILDFLY_HOME/bin/standalone.sh \
  -c standalone-full.xml \
  -b 0.0.0.0 \
  -bmanagement 0.0.0.0

# OR in background
nohup $WILDFLY_HOME/bin/standalone.sh \
  -c standalone-full.xml \
  -b 0.0.0.0 \
  -bmanagement 0.0.0.0 \
  > $WILDFLY_HOME/standalone/log/server.log 2>&1 &

# Monitor startup
tail -f $WILDFLY_HOME/standalone/log/server.log
```

### Step 11: Verify Deployment

Wait 60-90 seconds for full startup:

```bash
# Check WildFly Admin Console
# http://localhost:9990

# Check YAWL Engine via REST
curl -v http://localhost:8080/yawl/api/ib/workitems

# Expected: 200 OK with JSON array
```

## Virtual Threads Configuration (Java 25)

### Enable Virtual Threads for Request Handling

WildFly 27+ supports virtual threads via configuration. Add to `standalone.xml`:

```xml
<!-- Enable virtual threads for undertow subsystem -->
<subsystem xmlns="urn:jboss:domain:undertow:14.0">
    <server name="default-server">
        <http-listener name="default"
                       socket-binding="http"
                       enable-http2="true"
                       virtual-threads="true"/>
    </server>
</subsystem>

<!-- Or via CLI -->
/subsystem=undertow/server=default-server/http-listener=default:write-attribute(name=virtual-threads,value=true)
```

### Virtual Thread Pool for EJB

```xml
<!-- For EJB async execution -->
<subsystem xmlns="urn:jboss:domain:ejb3:10.0">
    <async thread-pool-name="yawl-async"/>
    <thread-pools>
        <thread-pool name="yawl-async"
                     max-threads="200"
                     core-threads="10"
                     virtual-threads="true"/>
    </thread-pools>
</subsystem>
```

### Benefits of Virtual Threads

| Metric | Platform Threads | Virtual Threads |
|--------|-----------------|-----------------|
| Memory per thread | ~2MB | ~1KB |
| 1000 concurrent requests | ~2GB | ~1MB |
| Thread creation | ~1ms | ~1us |
| Max concurrent threads | ~10,000 | ~1,000,000+ |

### Monitoring Virtual Threads

```bash
# JFR recording for virtual thread events
jcmd <pid> JFR.start settings=virtual-threads

# Thread dump includes virtual thread carriers
jcmd <pid> Thread.dump_to_file -format=json threads.json
```

## Database Initialization

Hibernate will create tables automatically. To verify:

```bash
psql -U yawluser -d yawl << 'EOF'
\dt
\q
EOF
```

## Configuration

### WildFly Management

Access management console:
```
http://localhost:9990/console
```

Default credentials (if not configured):
- Username: admin
- Password: admin

Change these immediately in production:

```bash
$WILDFLY_HOME/bin/add-user.sh
```

### YAWL Configuration via JNDI

Add environment variables via `standalone-full.xml`:

```xml
<subsystem xmlns="urn:jboss:domain:naming:2.0">
  <bindings>
    <simple name="java:jboss/env/yawl/JWT_SECRET" value="YOUR_SECRET"/>
    <simple name="java:jboss/env/yawl/DB_POOL_SIZE" value="20"/>
  </bindings>
</subsystem>
```

## SSL/TLS Configuration

### Generate Certificate

```bash
# Generate keystore
keytool -genkey -alias wildfly \
  -keyalg RSA \
  -keysize 2048 \
  -validity 365 \
  -keystore $WILDFLY_HOME/standalone/configuration/wildfly.jks \
  -storepass changeit \
  -keypass changeit \
  -dname "CN=localhost, OU=Development, O=YAWL, C=US"
```

### Configure HTTPS in standalone-full.xml

```xml
<!-- In <subsystem xmlns="urn:jboss:domain:undertow:14.0"> -->
<https-listener
  name="https"
  socket-binding="https"
  security-realm="ApplicationRealm"
  enabled-cipher-suites="TLS_AES_256_GCM_SHA384,TLS_CHACHA20_POLY1305_SHA256,TLS_AES_128_GCM_SHA256"
  enabled-protocols="TLSv1.3"/>

<!-- In <security-realms> -->
<security-realm name="ApplicationRealm">
  <server-identities>
    <ssl>
      <keystore path="wildfly.jks"
        relative-to="jboss.server.config.dir"
        keystore-password="changeit"
        alias="wildfly"
        key-password="changeit"/>
    </ssl>
  </server-identities>
</security-realm>
```

### TLS 1.3 Configuration (Required for Production)

```xml
<!-- Disable TLS 1.2 and below -->
<socket-binding name="https" port="${jboss.https.port:8443}"/>

<https-listener name="https"
               socket-binding="https"
               security-realm="ApplicationRealm"
               enabled-protocols="TLSv1.3"/>
```

### Using Let's Encrypt

```bash
# Get certificate
sudo certbot certonly --standalone -d yourdomain.com

# Convert to JKS
openssl pkcs12 -export \
  -in /etc/letsencrypt/live/yourdomain.com/fullchain.pem \
  -inkey /etc/letsencrypt/live/yourdomain.com/privkey.pem \
  -out wildfly.p12 \
  -name wildfly \
  -passout pass:changeit

keytool -importkeystore \
  -srckeystore wildfly.p12 \
  -srcstoretype PKCS12 \
  -srcstorepass changeit \
  -destkeystore $WILDFLY_HOME/standalone/configuration/wildfly.jks \
  -deststoretype JKS \
  -deststorepass changeit
```

## Performance Tuning

### For High Throughput

**In `standalone.conf`**:

```bash
JAVA_OPTS="$JAVA_OPTS -Xmx8192m"
JAVA_OPTS="$JAVA_OPTS -Xms4096m"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=100"
JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDetails"
JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDateStamps"
JAVA_OPTS="$JAVA_OPTS -Xloggc:$WILDFLY_HOME/standalone/log/gc.log"
```

### Connection Pool Tuning

In datasource XML:

```xml
<pool>
  <min-pool-size>10</min-pool-size>
  <max-pool-size>50</max-pool-size>
  <idle-timeout-minutes>10</idle-timeout-minutes>
  <allocation-retry>2</allocation-retry>
  <allocation-retry-wait-millis>5000</allocation-retry-wait-millis>
</pool>
```

### Web Subsystem Tuning

In `standalone-full.xml`:

```xml
<subsystem xmlns="urn:jboss:domain:undertow:14.0">
  <buffer-cache name="default"/>
  <server name="default-server">
    <http-listener name="default" socket-binding="http" enable-http2="true" virtual-threads="true"/>
    <https-listener name="https" socket-binding="https" security-realm="ApplicationRealm" enabled-protocols="TLSv1.3"/>
    <host name="default-host" alias="localhost">
      <location name="/" handler="welcome-content"/>
    </host>
  </server>
</subsystem>
```

## Monitoring

### Enable Access Logging

In `standalone-full.xml`:

```xml
<subsystem xmlns="urn:jboss:domain:undertow:14.0">
  <server name="default-server">
    <host name="default-host" alias="localhost">
      <access-log directory="${jboss.server.log.dir}" pattern="%h %l %u %t &quot;%r&quot; %s %b"/>
    </host>
  </server>
</subsystem>
```

### Health Check

```bash
curl -v http://localhost:8080/yawl/api/ib/health
```

### Server Metrics via JMX

```bash
# Available via JConsole
jconsole localhost:9999
```

## Clustering (HA Setup)

For high availability, configure domain mode:

```bash
# Start in domain mode
$WILDFLY_HOME/bin/domain.sh

# OR configure via CLI
$WILDFLY_HOME/bin/jboss-cli.sh

# Create cluster profile
/profile=full-ha:add
/profile=full-ha/subsystem=infinispan:add
# ... additional configuration
```

## Troubleshooting

### WildFly Won't Start

1. Check Java version:
   ```bash
   java -version
   ```

2. Check logs:
   ```bash
   tail -f $WILDFLY_HOME/standalone/log/server.log
   ```

3. Check for port conflicts:
   ```bash
   netstat -tulpn | grep -E "8080|8443|9990"
   ```

### Database Connection Issues

1. Test database:
   ```bash
   psql -U yawluser -d yawl -h localhost -c "SELECT 1;"
   ```

2. Check datasource via CLI:
   ```bash
   $WILDFLY_HOME/bin/jboss-cli.sh
   connect
   /subsystem=datasources/data-source=YawlDS:test-connection-in-pool
   ```

### OutOfMemory Error

Increase heap in `standalone.conf`:

```bash
JAVA_OPTS="$JAVA_OPTS -Xmx4096m"
JAVA_OPTS="$JAVA_OPTS -Xms2048m"
```

### Virtual Thread Pinning Warnings

If you see pinning warnings:

```bash
# Enable pinning diagnostics
JAVA_OPTS="$JAVA_OPTS -Djdk.tracePinnedThreads=full"
```

Replace synchronized blocks with ReentrantLock in custom code.

### Slow Deployment

Check for:
```bash
# War file size
ls -lh $WILDFLY_HOME/standalone/deployments/*.war

# Deployment logs
tail -f $WILDFLY_HOME/standalone/log/server.log | grep -i "deployment"
```

## Upgrading YAWL

### From 5.1 to 5.2

1. Backup database:
   ```bash
   pg_dump -U yawluser yawl > yawl_backup.sql
   ```

2. Stop WildFly:
   ```bash
   $WILDFLY_HOME/bin/jboss-cli.sh --controller=localhost:9990
   shutdown
   ```

3. Replace WAR files:
   ```bash
   rm $WILDFLY_HOME/standalone/deployments/*.war
   rm $WILDFLY_HOME/standalone/deployments/*.dodeploy
   cp /home/user/yawl/output/*.war $WILDFLY_HOME/standalone/deployments/
   touch $WILDFLY_HOME/standalone/deployments/*.dodeploy
   ```

4. Restart:
   ```bash
   $WILDFLY_HOME/bin/standalone.sh -c standalone-full.xml
   ```

## Production Checklist

- [ ] Java 25+ JDK installed
- [ ] WildFly 27+ installed and configured
- [ ] Standalone datasource configured
- [ ] WAR files built and deployed
- [ ] Admin console secured with strong password
- [ ] SSL/TLS 1.3 certificate installed
- [ ] JVM memory tuned with compact object headers
- [ ] Virtual threads enabled
- [ ] Connection pool configured
- [ ] Database backups enabled
- [ ] Access logging enabled
- [ ] Health check endpoint verified
- [ ] Monitoring configured
- [ ] Firewall rules configured (9990 restricted)

## Additional Resources

- [WildFly Documentation](https://docs.wildfly.org/)
- [WildFly Configuration Guide](https://docs.wildfly.org/28.0/Getting_Started_Guide.html)
- [Java 25 Virtual Threads Guide](https://openjdk.org/jeps/444)
- [YAWL Engine Configuration](../README.md)
- [Database Setup Guide](./DEPLOY-DATABASE.md)

## Support

For YAWL engine issues:
- Check `$WILDFLY_HOME/standalone/log/server.log`
- Review YAWL documentation at https://yawlfoundation.org

For WildFly-specific issues:
- Review [WildFly troubleshooting](https://docs.wildfly.org/28.0/)
