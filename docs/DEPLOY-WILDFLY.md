# Deploying YAWL v5.2 on WildFly 27+

**Target Runtime**: WildFly 27.x, 28.x
**Java Version**: Java 21+ (required for Jakarta EE 10 support)
**Estimated Time**: 45-60 minutes

## Prerequisites

### System Requirements
- Java 21 JDK (or higher)
- WildFly 27.x or 28.x
- PostgreSQL 13+ OR MySQL 8+ OR H2 2.2+
- 6GB+ RAM recommended
- 4GB free disk space

### Verify Prerequisites
```bash
# Check Java version
java -version  # Must be Java 21+

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

### Step 3: Configure JVM Parameters

**Edit `/opt/wildfly-instances/yawl/standalone.conf` (Unix/Linux/macOS)**:

```bash
# Create the file
cat > /opt/wildfly-instances/yawl/standalone.conf << 'EOF'
# JVM Settings
JAVA_OPTS="-server"
JAVA_OPTS="$JAVA_OPTS -Xmx2048m"
JAVA_OPTS="$JAVA_OPTS -Xms1024m"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=200"
JAVA_OPTS="$JAVA_OPTS -XX:InitiatingHeapOccupancyPercent=35"
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
set JAVA_OPTS=!JAVA_OPTS! -XX:+UseG1GC
set JAVA_OPTS=!JAVA_OPTS! -XX:MaxGCPauseMillis=200
set JAVA_OPTS=!JAVA_OPTS! -Djava.awt.headless=true
set JAVA_OPTS=!JAVA_OPTS! -Dfile.encoding=UTF-8
set JAVA_OPTS=!JAVA_OPTS! -Dyawl.jwt.secret=YOUR_SECRET_HERE

REM Rest of config...
```

### Step 4: Build YAWL WAR Files

```bash
cd /home/user/yawl

# Build with WildFly compatibility
ant clean
ant buildAll

# Verify
ls -la output/*.war
```

### Step 5: Configure Database

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

### Step 6: Configure WildFly Datasource

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

### Step 7: Deploy WAR Files

```bash
# Copy WAR files to WildFly deployments directory
cp /home/user/yawl/output/*.war $WILDFLY_HOME/standalone/deployments/

# Create deployment scanner marker files
for war in $WILDFLY_HOME/standalone/deployments/*.war; do
  touch "${war}.dodeploy"
done
```

### Step 8: Configure WildFly XML (Advanced)

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

### Step 9: Start WildFly

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

### Step 10: Verify Deployment

Wait 60-90 seconds for full startup:

```bash
# Check WildFly Admin Console
# http://localhost:9990

# Check YAWL Engine via REST
curl -v http://localhost:8080/yawl/api/ib/workitems

# Expected: 200 OK with JSON array
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
<!-- In <subsystem xmlns="urn:jboss:domain:web:15.0"> -->
<https-listener
  name="https"
  socket-binding="https"
  security-realm="ApplicationRealm"
  enabled-cipher-suites="HIGH:!aNULL:!MD5"/>

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
<subsystem xmlns="urn:jboss:domain:web:15.0">
  <buffer-cache name="default"/>
  <server name="default-server">
    <http-listener name="default" socket-binding="http" enable-http2="true"/>
    <https-listener name="https" socket-binding="https" security-realm="ApplicationRealm"/>
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
<subsystem xmlns="urn:jboss:domain:web:15.0">
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

- [ ] Java 21+ JDK installed
- [ ] WildFly 27+ installed and configured
- [ ] Standalone datasource configured
- [ ] WAR files built and deployed
- [ ] Admin console secured with strong password
- [ ] SSL/TLS certificate installed
- [ ] JVM memory tuned for environment
- [ ] Connection pool configured
- [ ] Database backups enabled
- [ ] Access logging enabled
- [ ] Health check endpoint verified
- [ ] Monitoring configured
- [ ] Firewall rules configured (9990 restricted)

## Additional Resources

- [WildFly Documentation](https://docs.wildfly.org/)
- [WildFly Configuration Guide](https://docs.wildfly.org/28.0/Getting_Started_Guide.html)
- [YAWL Engine Configuration](../README.md)
- [Database Setup Guide](./DEPLOY-DATABASE.md)

## Support

For YAWL engine issues:
- Check `$WILDFLY_HOME/standalone/log/server.log`
- Review YAWL documentation at https://yawlfoundation.org

For WildFly-specific issues:
- Review [WildFly troubleshooting](https://docs.wildfly.org/28.0/)
