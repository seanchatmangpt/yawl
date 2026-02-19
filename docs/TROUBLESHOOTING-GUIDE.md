# YAWL v6.0.0 Troubleshooting Guide

## Quick Diagnostics

### Is Tomcat Running?
```bash
pgrep -f "catalina.startup.Bootstrap"
# If output: process ID = running
# If no output = not running
```

### Can I Reach the Application?
```bash
curl -v http://localhost:8080/yawl/ib
# 200 = healthy
# 404 = WAR not deployed or path wrong
# Connection refused = Tomcat not running
# Connection timeout = firewall blocking
```

### Is the Database Connected?
```bash
psql -U postgres yawl -c "SELECT COUNT(*) FROM workflow_case;"
# Returns number = database healthy
# Connection refused = PostgreSQL not running
# FATAL: database doesn't exist = missing database
```

---

## Common Issues and Resolutions

### Issue 1: "Connection refused" - Tomcat Not Running

**Symptoms**
- curl: (7) Failed to connect to localhost port 8080
- telnet localhost 8080: Connection refused
- No Java processes showing in ps aux

**Diagnosis**
```bash
ps aux | grep java
pgrep -f catalina
systemctl status yawl-app
```

**Quick Fix**
```bash
# Start Tomcat
systemctl start yawl-app
# or
$CATALINA_HOME/bin/startup.sh

# Wait for startup (30-60 seconds)
sleep 30

# Check logs
tail -50 $CATALINA_HOME/logs/catalina.out
```

**Detailed Fix**
```bash
# Check Tomcat installation
ls -la $CATALINA_HOME/bin/startup.sh
chmod +x $CATALINA_HOME/bin/*.sh

# Check Java installation
which java
java -version

# Check port conflicts
netstat -tlnp | grep 8080
lsof -i :8080

# If port in use, kill blocking process
lsof -ti:8080 | xargs kill -9
sleep 2

# Start fresh
$CATALINA_HOME/bin/startup.sh
tail -f $CATALINA_HOME/logs/catalina.out
```

**Prevention**
- Monitor Tomcat: systemctl enable yawl-app
- Regular health checks: bash scripts/health-check.sh
- Monitor disk space: df -h /

---

### Issue 2: "404 Not Found" - WAR Not Deployed

**Symptoms**
```
curl http://localhost:8080/yawl/ib
< HTTP/1.1 404 Not Found
< Server: Apache-Coyote/1.1
```

**Diagnosis**
```bash
# Check for deployed applications
ls -la $CATALINA_HOME/webapps/
# Expected: yawl/ and/or yawl.war

# Check if WAR file exists
find $CATALINA_HOME -name "*.war" -ls

# Check Tomcat manager (if available)
curl http://localhost:8080/manager/html
```

**Quick Fix**
```bash
# Copy WAR file to webapps
cp build/lib/yawl.war $CATALINA_HOME/webapps/

# Restart Tomcat to expand WAR
systemctl restart yawl-app

# Wait for expansion (takes 10-30 seconds)
sleep 30
ls -la $CATALINA_HOME/webapps/yawl/
```

**Detailed Fix**
```bash
# Check if WAR is valid
unzip -t build/lib/yawl.war | tail
# Should show "OK" if valid

# Remove expanded directory to force re-extraction
rm -rf $CATALINA_HOME/webapps/yawl*

# Copy fresh WAR
cp build/lib/yawl.war $CATALINA_HOME/webapps/

# Verify deployment
ls -lah $CATALINA_HOME/webapps/yawl/WEB-INF/web.xml

# Restart
systemctl restart yawl-app

# Monitor expansion
watch -n 2 "ls -la $CATALINA_HOME/webapps/yawl 2>/dev/null | wc -l"
```

**Prevention**
- Always rebuild: ant clean && ant buildAll
- Verify WAR: unzip -t build/lib/yawl.war
- Deploy via script: bash scripts/deploy.sh

---

### Issue 3: "ClassNotFoundException: jakarta.servlet.http.HttpServlet"

**Symptoms**
```
java.lang.ClassNotFoundException: jakarta.servlet.http.HttpServlet
    at java.lang.ClassLoader.loadClass
    at org.apache.catalina.loader.WebappClassLoaderBase.loadClass
Exception in thread "main" java.lang.NoClassDefFoundError: jakarta/servlet/http/HttpServlet
```

**Root Cause**
- Tomcat version < 10.0 (uses javax.servlet)
- YAWL v6.0.0 requires Tomcat 10.1+ (uses jakarta.servlet)

**Diagnosis**
```bash
$CATALINA_HOME/bin/version.sh | grep "Tomcat"
# Should show: Apache Tomcat/10.1.x or higher
```

**Quick Fix - Upgrade Tomcat**
```bash
# Check current version
echo "Current: $CATALINA_HOME"

# Stop current Tomcat
systemctl stop yawl-app

# Download Tomcat 10.1
cd /tmp
wget https://archive.apache.org/dist/tomcat/tomcat-10/v10.1.13/bin/apache-tomcat-10.1.13.tar.gz
tar -xzf apache-tomcat-10.1.13.tar.gz

# Backup current and swap
mv $CATALINA_HOME $CATALINA_HOME.backup
mv apache-tomcat-10.1.13 $CATALINA_HOME

# Restore configuration
cp $CATALINA_HOME.backup/conf/*.xml $CATALINA_HOME/conf/
cp $CATALINA_HOME.backup/webapps/*.war $CATALINA_HOME/webapps/

# Start new Tomcat
systemctl start yawl-app
tail -50 $CATALINA_HOME/logs/catalina.out
```

**Prevention**
- Document environment requirements
- Pre-deployment environment check: bash scripts/verify-environment.sh
- Automated deployment: Handles version compatibility

---

### Issue 4: "HikariPool - Failed to validate connection"

**Symptoms**
```
HikariPool-1 - Failed to validate connection org.postgresql.util.PSQLException:
Connection to localhost:5432 refused
java.sql.SQLException: No database connection available
```

**Root Cause**
- PostgreSQL not running
- JDBC URL incorrect
- Network connectivity issue
- Credentials wrong

**Diagnosis**
```bash
# Check PostgreSQL running
systemctl status postgresql
pgrep postgres

# Check connection
psql -U postgres -h localhost -d yawl -c "SELECT 1;"

# Check JDBC URL
grep "jdbc.url\|hibernate.connection.url" build/properties/hibernate.properties

# Check firewall
netstat -tlnp | grep 5432
nc -zv localhost 5432
```

**Quick Fix**
```bash
# Start PostgreSQL if not running
systemctl start postgresql

# Wait for startup
sleep 5

# Test connection
psql -U postgres -d yawl -c "SELECT 1;"

# If database doesn't exist, create it
createdb -U postgres yawl

# Verify Hibernate configuration
grep -E "dialect|url|user|password" build/properties/hibernate.properties

# Restart application
systemctl restart yawl-app
tail -50 $CATALINA_HOME/logs/catalina.out
```

**Detailed Fix**
```bash
# Check PostgreSQL configuration
cat /etc/postgresql/*/main/postgresql.conf | grep "listen_addresses\|port"
# Should show: listen_addresses = 'localhost' or '*'
# Should show: port = 5432

# Check pg_hba.conf for connection method
cat /etc/postgresql/*/main/pg_hba.conf | grep "^local\|^host"
# Should allow local connections

# Restart PostgreSQL with new config
systemctl restart postgresql

# Verify JDBC URL format
# Expected: jdbc:postgresql://localhost:5432/yawl
# Alternative: jdbc:postgresql://dbserver:5432/yawl

# Update hibernate.properties if needed
cat > build/properties/hibernate.properties << EOF
hibernate.dialect = org.hibernate.dialect.PostgreSQL10Dialect
hibernate.connection.url = jdbc:postgresql://localhost:5432/yawl
hibernate.connection.username = postgres
hibernate.connection.password = [your_password]
hibernate.connection.pool_size = 20
hibernate.c3p0.max_pool_size = 20
EOF

# Test connection
ant test-db-connection

# Restart application
systemctl restart yawl-app
```

**Prevention**
- Environment setup script: scripts/setup-environment.sh
- Database health checks: Regular monitoring
- Connection pool metrics: Monitor in New Relic/Datadog

---

### Issue 5: "OutOfMemoryError: Java heap space"

**Symptoms**
```
java.lang.OutOfMemoryError: Java heap space
    at java.util.Arrays.copyOf(Arrays.java:3501)
    at [application code]
```

**Root Cause**
- Heap size too small
- Memory leak in application
- Large dataset processing without streaming
- Connection pool leak

**Diagnosis**
```bash
# Check heap allocation
ps aux | grep catalina | grep -o "\-Xmx[0-9]*m"
# Expected: -Xmx1024m or higher

# Monitor heap usage
jstat -gc -h5 <pid> 500
# Watch for full garbage collections

# Check for leaks (if available)
jmap -histo <pid> | head -20

# Monitor with jconsole
jconsole <pid>
# Watch: Memory usage trend over time
```

**Quick Fix - Increase Heap**
```bash
# Edit Tomcat startup script
nano $CATALINA_HOME/bin/setenv.sh
# or create it if not exists

cat > $CATALINA_HOME/bin/setenv.sh << 'EOF'
CATALINA_OPTS="-Xms1024m -Xmx2048m -XX:+UseG1GC -XX:+PrintGCDetails -XX:+PrintGCTimeStamps"
export CATALINA_OPTS
EOF

# Make executable
chmod +x $CATALINA_HOME/bin/setenv.sh

# Restart Tomcat
systemctl restart yawl-app

# Verify new settings
ps aux | grep catalina | grep Xmx
```

**Detailed Troubleshooting**
```bash
# 1. Identify memory leak
jstat -gcutil <pid> 1000
# If oldgen keeps growing: likely memory leak

# 2. Take heap dump
jmap -dump:live,format=b,file=heap.bin <pid>
jhat -J-Xmx512m heap.bin
# Access at http://localhost:7000

# 3. Analyze growth pattern
# Monitor heap size over time
watch -n 10 'jstat -gc <pid> | tail -1'

# 4. If leak confirmed, investigate:
# - Check for static collections
# - Look for listeners not being removed
# - Review third-party library versions
# - Search for ThreadLocal usage

# 5. If no leak, increase heap gradually
# Test with: -Xmx1536m, -Xmx2048m, -Xmx3072m

# 6. Monitor after increase
# Expected: Stop seeing OutOfMemoryError
# Watch: Garbage collection pauses
```

**Prevention**
- Set appropriate heap: -Xms1024m -Xmx2048m (at minimum)
- Monitor memory trends: Datadog, New Relic
- Regular full GC analysis: Weekly if high usage
- Code review for memory leaks: Spotbugs, SonarQube

---

### Issue 6: "Unable to find a matching constructor"

**Symptoms**
```
Caused by: java.lang.NoSuchMethodException: [ClassName].<init>()
    at java.lang.Class.getConstructor(Class.java:1615)
    at [framework code]
```

**Root Cause**
- Dependency version mismatch
- Missing constructor implementation
- Serialization/deserialization framework issue

**Diagnosis**
```bash
# Check class in JAR
jar tf build/lib/yawl.jar | grep "ClassName.class"

# Decompile to check constructor
javap -classpath build/lib/yawl.jar [ClassName]

# Look for version mismatches
grep -r "version\|revision" build/properties/
```

**Quick Fix**
```bash
# Rebuild everything clean
ant clean compile buildWebApps

# Ensure fresh dependencies
rm -rf ~/.gradle ~/.m2/repository
# (if using Gradle or Maven)

# Redeploy
cp build/lib/yawl.war $CATALINA_HOME/webapps/
systemctl restart yawl-app
```

**Prevention**
- Version lock: Specify exact versions in build.xml
- Dependency analysis: ant dependencies (if available)
- CI/CD pipeline: Catch before production

---

### Issue 7: "Transaction timeout"

**Symptoms**
```
org.hibernate.TransactionException: transaction not successfully started
Error executing batch on the server. Status: 504, Error Code: ..., Details: Transaction timeout
```

**Root Cause**
- Long-running database operations
- Connection pool exhausted
- Deadlock in database
- Slow queries

**Diagnosis**
```bash
# Check database active connections
psql yawl -c "SELECT datname, count(*) FROM pg_stat_activity GROUP BY datname;"

# Monitor slow queries (PostgreSQL)
psql yawl -c "SELECT * FROM pg_stat_statements ORDER BY mean_time DESC LIMIT 5;"

# Check connection pool stats
curl http://localhost:8080/yawl/admin/db-pool-stats
```

**Quick Fix**
```bash
# Increase transaction timeout
nano build/properties/hibernate.properties
# Add: hibernate.connection.connectionTimeout = 30

# Increase connection pool
# Add to hibernate.properties:
hibernate.c3p0.max_pool_size = 40
hibernate.c3p0.min_pool_size = 10
hibernate.c3p0.acquire_increment = 5

# Restart application
systemctl restart yawl-app
```

**Detailed Troubleshooting**
```bash
# 1. Identify slow queries
psql yawl << EOF
SELECT query, calls, mean_time, max_time
FROM pg_stat_statements
WHERE query NOT LIKE '%pg_stat%'
ORDER BY mean_time DESC
LIMIT 10;
EOF

# 2. Analyze query execution
EXPLAIN ANALYZE SELECT ...;

# 3. Check for locks
SELECT * FROM pg_locks WHERE NOT granted;

# 4. Identify blocking queries
SELECT
  blocked_locks.pid AS blocked_pid,
  blocked_activity.usename AS blocked_user,
  blocking_locks.pid AS blocking_pid,
  blocking_activity.usename AS blocking_user,
  blocked_activity.query AS blocked_statement,
  blocking_activity.query AS blocking_statement,
  blocked_activity.application_name AS blocked_application,
  blocking_activity.application_name AS blocking_application
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype
  AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
  AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
  AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page
  AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple
  AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid
  AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid
  AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid
  AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid
  AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid
  AND blocking_locks.pid != blocked_locks.pid
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;

# 5. Kill long-running query if necessary
SELECT pg_terminate_backend(pid) FROM pg_stat_activity
WHERE pid != pg_backend_pid() AND query_start < now() - interval '10 minutes';

# 6. Add database indexes if queries are slow
CREATE INDEX idx_workflow_case_status ON workflow_case(status);
```

**Prevention**
- Query optimization: Profile queries regularly
- Connection pool monitoring: Alert if > 80% utilized
- Timeout configuration: Set appropriate values
- Slow query logging: Enable in PostgreSQL

---

### Issue 8: "OutOfMemoryError: PermGen space" or "Metaspace"

**Symptoms**
```
java.lang.OutOfMemoryError: PermGen space (Java 8)
java.lang.OutOfMemoryError: Metaspace (Java 9+)
```

**Root Cause**
- Too many classes loaded
- Class loader leak
- JAR file explosion

**Quick Fix**
```bash
# Edit setenv.sh
cat > $CATALINA_HOME/bin/setenv.sh << 'EOF'
# Java 8 or earlier:
CATALINA_OPTS="-XX:PermSize=256m -XX:MaxPermSize=512m"

# Java 9 or later:
CATALINA_OPTS="-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m"

export CATALINA_OPTS
EOF

systemctl restart yawl-app
```

---

### Issue 9: "WARNING: Container startup failed"

**Symptoms**
```
Feb 16, 2026 2:30:15 PM org.apache.catalina.core.StandardEngine startInternal
SEVERE: Container [Catalina].[localhost].[ROOT] failed to start
```

**Diagnosis**
```bash
# Check logs in detail
tail -100 $CATALINA_HOME/logs/catalina.out
tail -100 $CATALINA_HOME/logs/localhost.log
```

**Common Causes & Fixes**

1. **Missing JAR dependency**
   ```
   ClassNotFoundException: com.example.SomeClass
   Solution: Check build.xml for missing dependencies
   ```

2. **Port already in use**
   ```
   java.net.BindException: Address already in use
   Solution: lsof -i :8080; kill -9 <pid>
   ```

3. **Invalid XML in configuration**
   ```
   SAXParseException in web.xml
   Solution: Validate: xmllint $CATALINA_HOME/conf/server.xml
   ```

4. **Write permissions issue**
   ```
   java.io.IOException: Permission denied
   Solution: chown -R tomcat:tomcat $CATALINA_HOME/webapps
   ```

---

### Issue 10: REST API Slow (response > 2 seconds)

**Symptoms**
```bash
curl -w "@curl-format.txt" -o /dev/null -s http://localhost:8080/yawl/api/cases
# time_starttransfer: 2500ms (or higher)
```

**Diagnosis**
```bash
# Monitor CPU
top -u tomcat -p <pid>

# Monitor database queries
psql yawl -c "SELECT * FROM pg_stat_statements ORDER BY mean_time DESC LIMIT 5;"

# Check network
iftop -i eth0

# Profile with JFR (Java Flight Recorder)
jcmd <pid> JFR.start
sleep 10
jcmd <pid> JFR.stop filename=recording.jfr
jfr print recording.jfr
```

**Common Fixes**
```bash
# 1. Add database indexes
CREATE INDEX idx_case_status ON workflow_case(status);
CREATE INDEX idx_work_item_case ON work_item(case_id);

# 2. Optimize queries
# Run EXPLAIN ANALYZE on slow queries
# Add query caching in Hibernate

# 3. Increase connection pool
# Edit hibernate.properties
hibernate.c3p0.max_pool_size = 50

# 4. Enable query caching
hibernate.cache.use_second_level_cache = true
hibernate.cache.region.factory_class = org.hibernate.cache.jcache.JCacheRegionFactory

# 5. Restart after changes
systemctl restart yawl-app
```

---

## Log File Locations

```bash
# Tomcat
$CATALINA_HOME/logs/catalina.out         # Main startup logs
$CATALINA_HOME/logs/catalina.YYYY-MM-DD.log  # Daily logs
$CATALINA_HOME/logs/localhost.log        # Application-specific
$CATALINA_HOME/logs/manager.log          # Manager logs

# PostgreSQL
/var/log/postgresql/postgresql-*.log     # PostgreSQL logs

# System
/var/log/syslog                          # System events
/var/log/auth.log                        # Authentication events
```

## Log Analysis Commands

```bash
# Find errors in last hour
grep "ERROR" $CATALINA_HOME/logs/catalina.out | tail -20

# Find exceptions
grep -i "exception\|error" $CATALINA_HOME/logs/catalina.out | grep -v "WARN"

# Follow logs in real-time
tail -f $CATALINA_HOME/logs/catalina.out

# Count errors by type
grep "ERROR" $CATALINA_HOME/logs/catalina.out | cut -d: -f3 | sort | uniq -c | sort -rn

# Extract stack traces
sed -n '/Exception/,/^$/p' $CATALINA_HOME/logs/catalina.out
```

---

## Performance Tuning Checklist

- [ ] Heap size: -Xmx2048m or higher
- [ ] Garbage collection: Use -XX:+UseG1GC
- [ ] Database indexes: Verify critical tables have indexes
- [ ] Connection pool: 20-40 connections
- [ ] Cache: Enable Hibernate second-level cache
- [ ] Logging: Set INFO level in production (not DEBUG)
- [ ] Monitoring: Datadog or New Relic agents running

---

## Emergency Contacts

| Situation | Contact | Escalation |
|-----------|---------|-----------|
| Application Down | On-Call Engineer | DevOps Lead |
| Database Issue | DBA | Engineering Lead |
| Security Issue | Security Team | CISO |
| Performance Issue | Performance Team | Engineering Lead |

---

## Document Information

| Field | Value |
|-------|-------|
| Version | 1.0 |
| Last Updated | 2026-02-16 |
| Owner | DevOps/Engineering Team |
| Related | ROLLBACK-PROCEDURES.md, DEPLOYMENT-CHECKLIST.md |

