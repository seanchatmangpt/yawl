# Deploying YAWL v5.2 on Apache Tomcat 10.1+

**Target Runtime**: Apache Tomcat 10.1.x, 11.x
**Java Version**: Java 21+ (required for Jakarta EE 10 support)
**Estimated Time**: 30-45 minutes

## Prerequisites

### System Requirements
- Java 21 JDK (or higher)
- Apache Tomcat 10.1.x or 11.x
- PostgreSQL 13+ OR MySQL 8+ OR H2 2.2+
- 4GB+ RAM recommended
- 2GB free disk space

### Verify Prerequisites
```bash
# Check Java version
java -version  # Must be Java 21 or higher

# Check Tomcat version (if already installed)
$CATALINA_HOME/bin/version.sh
```

## Installation Steps

### Step 1: Download and Install Tomcat

```bash
# Download Tomcat 11 (latest)
cd /opt
wget https://archive.apache.org/dist/tomcat/tomcat-11/v11.0.0/bin/apache-tomcat-11.0.0.tar.gz

# Extract
tar -xzf apache-tomcat-11.0.0.tar.gz
ln -s apache-tomcat-11.0.0 tomcat

# Set CATALINA_HOME
export CATALINA_HOME=/opt/tomcat
echo 'export CATALINA_HOME=/opt/tomcat' >> ~/.bashrc
```

### Step 2: Configure JVM Parameters

**Edit `$CATALINA_HOME/bin/catalina.sh` (Unix/Linux/macOS)**:

```bash
# Add near the beginning of the file (before "exec" statements):
export CATALINA_OPTS="
  -server
  -Xmx2048m
  -Xms1024m
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:InitiatingHeapOccupancyPercent=35
  -Djava.awt.headless=true
  -Dfile.encoding=UTF-8
  -Duser.timezone=UTC
  -Dyawl.jwt.secret=$(openssl rand -base64 32)
  -Dyawl.db.driver=org.postgresql.Driver
  -Dyawl.db.url=jdbc:postgresql://localhost:5432/yawl
  -Dyawl.db.username=yawluser
  -Dyawl.db.password=yawlpass
"
```

**Edit `$CATALINA_HOME/bin/catalina.bat` (Windows)**:

```batch
REM Add before the "goto :gotTitle" line:
set CATALINA_OPTS=-server -Xmx2048m -Xms1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Dyawl.jwt.secret=YOUR_SECRET_HERE -Dyawl.db.driver=org.postgresql.Driver -Dyawl.db.url=jdbc:postgresql://localhost:5432/yawl -Dyawl.db.username=yawluser -Dyawl.db.password=yawlpass
```

### Step 3: Build YAWL WAR Files

From the YAWL source directory:

```bash
cd /home/user/yawl

# Full build (generates all WAR files)
ant clean
ant buildAll

# Output directory: output/
ls output/*.war
```

Expected WAR files:
- `yawl.war` - Main YAWL Engine
- `resourceService.war` - Resource Service
- `workletService.war` - Worklet Service
- `schedulingService.war` - Scheduling Service
- `monitorService.war` - Monitor Service
- `costService.war` - Cost Service
- `mailService.war` - Mail Service
- And others...

### Step 4: Configure Database

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

-- Exit psql
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

-- Exit mysql
EXIT;
```

#### H2 Setup (Embedded, Development Only)

H2 is configured automatically if using the H2 JDBC driver. No separate setup needed.

```bash
# In Tomcat, the H2 database file will be created at:
# $CATALINA_HOME/webapps/yawl/WEB-INF/data/yawldb
```

### Step 5: Deploy WAR Files to Tomcat

```bash
# Copy all WAR files to webapps directory
cp /home/user/yawl/output/*.war $CATALINA_HOME/webapps/

# Verify
ls $CATALINA_HOME/webapps/*.war
```

### Step 6: Configure Tomcat Context (Optional but Recommended)

Create `$CATALINA_HOME/conf/Catalina/localhost/yawl.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Context
  docBase="${catalina.home}/webapps/yawl.war"
  privileged="true"
  antiResourceLocking="false"
  antiJARLocking="true">

  <!-- Database Resource -->
  <Resource
    name="jdbc/yawldb"
    auth="Container"
    type="javax.sql.DataSource"
    driverClassName="org.postgresql.Driver"
    url="jdbc:postgresql://localhost:5432/yawl"
    username="yawluser"
    password="yawlpass"
    maxActive="20"
    maxIdle="10"
    maxWait="30000"
    removeAbandoned="true"
    removeAbandonedTimeout="60"
    validationQuery="SELECT 1"
    testOnBorrow="true"
  />

  <!-- Environment Variables -->
  <Environment
    name="yawl/JWT_SECRET"
    value="YOUR_GENERATED_SECRET"
    type="java.lang.String"
    override="false"
  />

  <!-- Session Configuration -->
  <SessionCookieConfig
    httpOnly="true"
    secure="true"
    sameSite="Strict"
  />

</Context>
```

### Step 7: Start Tomcat

```bash
# Start in foreground (for testing)
$CATALINA_HOME/bin/catalina.sh run

# OR start in background
$CATALINA_HOME/bin/startup.sh

# Check logs
tail -f $CATALINA_HOME/logs/catalina.out
```

### Step 8: Verify Deployment

Wait 30-60 seconds for Tomcat to fully start, then:

```bash
# Check if YAWL is running
curl -v http://localhost:8080/yawl/api/ib/workitems

# Expected response: 200 OK with JSON array
```

## Database Initialization

On first startup, YAWL will automatically create necessary database tables via Hibernate.

To manually initialize (if needed):

```bash
# Run YAWL with initialization flag
export CATALINA_OPTS="$CATALINA_OPTS -Dyawl.db.initialize=true"
$CATALINA_HOME/bin/catalina.sh run
```

## Configuration Files

### YAWL Configuration

Most settings are configurable via:
- `$CATALINA_HOME/conf/Catalina/localhost/yawl.xml` (Tomcat context)
- Environment variables with `YAWL_*` prefix
- `$CATALINA_HOME/webapps/yawl/WEB-INF/classes/application.properties` (if deployed WAR contains it)

### Recommended Settings

```properties
# Database
yawl.db.driver=org.postgresql.Driver
yawl.db.url=jdbc:postgresql://localhost:5432/yawl
yawl.db.username=yawluser
yawl.db.password=yawlpass
yawl.db.pool.size=20

# Security
yawl.jwt.secret=YOUR_GENERATED_SECRET
yawl.jwt.expiration=3600000
yawl.cors.allowed-origins=http://localhost:3000,https://yourapp.com

# Engine
yawl.engine.max-participants=100
yawl.engine.persist-on-shutdown=true

# Logging
logging.level.org.yawlfoundation.yawl=INFO
logging.level.org.hibernate=WARN
```

## SSL/TLS Configuration (Production)

### Using Tomcat's Built-in SSL

```xml
<!-- Edit $CATALINA_HOME/conf/server.xml -->
<Connector
  port="8443"
  protocol="org.apache.coyote.http11.Http11NioProtocol"
  maxThreads="150"
  scheme="https"
  secure="true"
  keystoreFile="/path/to/keystore.jks"
  keystorePass="keystorepassword"
  keyAlias="tomcat"
  SSLEnabled="true"
  sslProtocol="TLSv1.2"
  ciphers="HIGH:!aNULL:!MD5"
/>
```

### Using Let's Encrypt with Certbot

```bash
# Install Certbot
sudo apt-get install certbot python3-certbot-apache

# Obtain certificate (requires valid domain)
sudo certbot certonly --standalone -d yourdomain.com

# Configure Tomcat to use certificate
# See: https://certbot.eff.org/instructions?ws=apache&os=ubuntu
```

## Performance Tuning

### JVM Heap Configuration

```bash
# For production (32GB+ RAM available)
export CATALINA_OPTS="
  -Xmx16384m
  -Xms8192m
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=200
  -XX:+PrintGCDetails
  -XX:+PrintGCDateStamps
  -Xloggc:$CATALINA_HOME/logs/gc.log
"
```

### Connection Pool Tuning

In `yawl.xml`:

```xml
<Resource
  name="jdbc/yawldb"
  ...
  maxActive="50"
  maxIdle="20"
  maxWait="60000"
  initialSize="10"
  testOnBorrow="true"
  testWhileIdle="true"
  timeBetweenEvictionRunsMillis="300000"
/>
```

### Thread Pool Configuration

Edit `$CATALINA_HOME/conf/server.xml`:

```xml
<Executor
  name="tomcatThreadPool"
  namePrefix="catalina-exec-"
  maxThreads="200"
  minSpareThreads="10"
  maxIdleTime="60000"
/>

<Connector
  executor="tomcatThreadPool"
  port="8080"
  protocol="HTTP/1.1"
/>
```

## Monitoring and Health Checks

### Health Check Endpoint

```bash
# Check YAWL Engine health
curl -v http://localhost:8080/yawl/api/ib/health

# Check specific service
curl -v http://localhost:8080/resourceService/api/health
```

### Access Logs

Enable detailed access logging in `$CATALINA_HOME/conf/server.xml`:

```xml
<Valve
  className="org.apache.catalina.valves.AccessLogValve"
  directory="logs"
  pattern="%h %l %u %t &quot;%r&quot; %s %b"
  buffered="false"
/>
```

View logs:
```bash
tail -f $CATALINA_HOME/logs/localhost_access_log.*.txt
```

## Troubleshooting

### YAWL Won't Start

1. Check Java version:
   ```bash
   java -version  # Must be Java 21+
   ```

2. Check Tomcat logs:
   ```bash
   tail -f $CATALINA_HOME/logs/catalina.out
   tail -f $CATALINA_HOME/logs/localhost.*.log
   ```

3. Verify database connectivity:
   ```bash
   psql -U yawluser -d yawl -h localhost -c "SELECT 1;"
   ```

### OutOfMemory Error

Increase heap in `catalina.sh`:
```bash
export CATALINA_OPTS="-Xmx4096m -Xms2048m $CATALINA_OPTS"
```

### Port Already in Use

Change Tomcat port in `$CATALINA_HOME/conf/server.xml`:
```xml
<Connector port="8081" protocol="HTTP/1.1" />
```

### Database Connection Failures

Verify credentials and connectivity:
```bash
# Test PostgreSQL
psql -U yawluser -d yawl -h localhost -c "SELECT 1;"

# Test MySQL
mysql -u yawluser -p -h localhost yawl -e "SELECT 1;"
```

## Upgrading YAWL

### From Version 5.1 to 5.2

1. Backup database:
   ```bash
   pg_dump -U yawluser yawl > yawl_5.1_backup.sql
   ```

2. Stop Tomcat:
   ```bash
   $CATALINA_HOME/bin/shutdown.sh
   ```

3. Remove old WAR files:
   ```bash
   rm $CATALINA_HOME/webapps/*.war
   rm -rf $CATALINA_HOME/webapps/yawl*
   rm -rf $CATALINA_HOME/webapps/resourceService*
   ```

4. Deploy new WAR files:
   ```bash
   cp /home/user/yawl/output/*.war $CATALINA_HOME/webapps/
   ```

5. Restart Tomcat:
   ```bash
   $CATALINA_HOME/bin/startup.sh
   ```

6. Monitor startup:
   ```bash
   tail -f $CATALINA_HOME/logs/catalina.out
   ```

## Production Checklist

- [ ] Java 21+ JDK installed
- [ ] Tomcat 10.1+ installed
- [ ] Database created and user configured
- [ ] WAR files built and deployed
- [ ] SSL/TLS certificate installed
- [ ] JVM memory tuned for environment
- [ ] Database backups configured
- [ ] Health check endpoint verified
- [ ] Application logs verified
- [ ] Monitoring/alerting configured
- [ ] Database connection pooling tuned
- [ ] Session persistence enabled
- [ ] Tomcat clustering configured (if needed)

## Additional Resources

- [Apache Tomcat 11 Documentation](https://tomcat.apache.org/tomcat-11.0-doc/)
- [YAWL Engine Configuration](../README.md)
- [Database Setup Guide](./DEPLOY-DATABASE.md)
- [Monitoring and Logging](../observability/MONITORING.md)

## Support

For issues with YAWL engine specifically:
- Check `$CATALINA_HOME/logs/catalina.out` for engine errors
- Review YAWL documentation at https://yawlfoundation.org
- Submit issues to the YAWL Foundation

For Tomcat-specific issues:
- Check `$CATALINA_HOME/logs/catalina.out`
- Review [Apache Tomcat troubleshooting guide](https://tomcat.apache.org/tomcat-11.0-doc/troubleshooting.html)
