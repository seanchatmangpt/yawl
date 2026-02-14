# YAWL Operations and Management Guide

## Table of Contents

1. [Daily Operations](#daily-operations)
2. [Monitoring and Health Checks](#monitoring-and-health-checks)
3. [Backup and Recovery](#backup-and-recovery)
4. [Performance Tuning](#performance-tuning)
5. [Scaling Operations](#scaling-operations)
6. [User and Role Management](#user-and-role-management)
7. [Workflow Deployment](#workflow-deployment)
8. [Case Management](#case-management)
9. [Maintenance Tasks](#maintenance-tasks)
10. [Incident Response](#incident-response)

## Daily Operations

### Morning Checklist

```bash
# 1. System Health Check (8:00 AM)
# Run before business hours

echo "=== YAWL Operational Health Check ==="
date

# Check all services are running
echo "Service Status:"
docker-compose ps
# Expected: All services "Up"

# Check database connectivity
echo -e "\nDatabase Health:"
docker exec yawl-postgres pg_isready -U yawl
# Expected: "accepting connections"

# Check Redis availability
echo -e "\nCache Health:"
docker exec yawl-redis redis-cli ping
# Expected: "PONG"

# Check logs for errors (last hour)
echo -e "\nRecent Errors:"
docker logs yawl-engine --since 1h | grep -i "error\|exception" | tail -5

# Check disk usage
echo -e "\nDisk Usage:"
df -h | grep -E "Mounted|/"

# Check memory usage
echo -e "\nMemory Usage:"
docker stats --no-stream

# Check open connections
echo -e "\nDatabase Connections:"
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT datname, count(*) FROM pg_stat_activity GROUP BY datname;"
```

### Hourly Monitoring

```bash
# Run every hour via cron or monitoring system

#!/bin/bash
# File: monitor-yawl.sh

# 1. Check service availability
RESPONSE=$(curl -s -w "%{http_code}" -o /dev/null http://localhost:8080/resourceService/)
if [ "$RESPONSE" != "200" ]; then
    echo "ALERT: YAWL Engine returned HTTP $RESPONSE"
    # Send alert notification
fi

# 2. Check active cases
ACTIVE_CASES=$(docker exec yawl-postgres psql -U yawl -d yawl -t -c \
  "SELECT count(*) FROM ypcase WHERE status='ACTIVE';")
echo "Active cases: $ACTIVE_CASES"

# 3. Check for stuck tasks
STUCK_TASKS=$(docker exec yawl-postgres psql -U yawl -d yawl -t -c \
  "SELECT count(*) FROM ypworkitem WHERE status='ALLOCATED' \
   AND created_time < now() - interval '24 hours';")
if [ "$STUCK_TASKS" -gt 10 ]; then
    echo "ALERT: $STUCK_TASKS tasks stuck for > 24 hours"
fi

# 4. Check database size
DB_SIZE=$(docker exec yawl-postgres psql -U yawl -d yawl -t -c \
  "SELECT pg_size_pretty(pg_database_size('yawl'));")
echo "Database size: $DB_SIZE"
```

### Log Review Procedures

```bash
# Daily log review (5-10 minutes)

# 1. Search for errors
docker logs yawl-engine --since 24h | grep -i "ERROR\|EXCEPTION" | sort | uniq -c

# 2. Check for authentication failures
docker logs yawl-engine --since 24h | grep -i "unauthorized\|forbidden" | wc -l

# 3. Review warning messages
docker logs yawl-engine --since 24h | grep -i "WARNING" | head -20

# 4. Monitor database locks
docker exec yawl-postgres psql -U yawl -d yawl -c \
  "SELECT * FROM pg_locks JOIN pg_stat_activity ON pg_locks.pid = pg_stat_activity.pid;"

# 5. Export logs for archive
mkdir -p logs-archive/$(date +%Y-%m-%d)
docker logs yawl-engine > logs-archive/$(date +%Y-%m-%d)/yawl-engine.log
docker logs yawl-postgres > logs-archive/$(date +%Y-%m-%d)/postgres.log
```

## Monitoring and Health Checks

### Prometheus Metrics

```bash
# Access Prometheus directly
curl http://localhost:9090/api/v1/query?query=up

# Query response time
curl http://localhost:9090/api/v1/query?query=http_request_duration_seconds

# Query memory usage
curl http://localhost:9090/api/v1/query?query=jvm_memory_used_bytes

# Query database connections
curl 'http://localhost:9090/api/v1/query?query=pg_stat_activity_count'
```

### Grafana Dashboard Setup

```bash
# 1. Access Grafana
# URL: http://localhost:3000
# Default credentials: admin / admin

# 2. Add Prometheus data source
# Configuration > Data Sources > Add Data Source
# - Name: Prometheus
# - URL: http://prometheus:9090
# - Access: Server
# - Click "Save & Test"

# 3. Create custom dashboard
# Create Dashboard > Add Panel
# Metric: rate(http_requests_total[5m])
# Title: Request Rate (requests/sec)

# 4. Set up alerts
# Alert Rules > New Alert Rule
# Condition: avg(http_response_time_ms) > 1000
# For: 5 minutes
# Notify: Slack channel

# 5. Pre-built dashboard templates
# Dashboard > Import > Search for YAWL/Spring Boot
# Import ID: 4701 (JVM Micrometer)
# Import ID: 1860 (Node Exporter)
```

### Custom Health Check Script

```bash
#!/bin/bash
# File: health-check.sh
# Comprehensive YAWL health assessment

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

check_service() {
    local service=$1
    local port=$2
    local path=${3:-"/"}

    echo -n "Checking $service... "

    response=$(curl -s -w "%{http_code}" -o /dev/null http://localhost:$port$path)

    if [ "$response" = "200" ]; then
        echo -e "${GREEN}✓ UP${NC}"
        return 0
    else
        echo -e "${RED}✗ DOWN (HTTP $response)${NC}"
        return 1
    fi
}

check_database() {
    echo -n "Checking database... "

    if docker exec yawl-postgres pg_isready -U yawl &>/dev/null; then
        echo -e "${GREEN}✓ UP${NC}"
        return 0
    else
        echo -e "${RED}✗ DOWN${NC}"
        return 1
    fi
}

check_redis() {
    echo -n "Checking Redis... "

    if docker exec yawl-redis redis-cli ping | grep -q "PONG"; then
        echo -e "${GREEN}✓ UP${NC}"
        return 0
    else
        echo -e "${RED}✗ DOWN${NC}"
        return 1
    fi
}

check_disk_space() {
    echo -n "Checking disk space... "

    usage=$(df / | awk 'NR==2 {print $5}' | sed 's/%//')

    if [ "$usage" -lt 80 ]; then
        echo -e "${GREEN}✓ OK (${usage}%)${NC}"
        return 0
    elif [ "$usage" -lt 90 ]; then
        echo -e "${YELLOW}⚠ WARNING (${usage}%)${NC}"
        return 1
    else
        echo -e "${RED}✗ CRITICAL (${usage}%)${NC}"
        return 1
    fi
}

check_memory() {
    echo -n "Checking memory usage... "

    mem_total=$(free | awk 'NR==2 {print $2}')
    mem_used=$(free | awk 'NR==2 {print $3}')
    mem_percent=$((mem_used * 100 / mem_total))

    if [ "$mem_percent" -lt 70 ]; then
        echo -e "${GREEN}✓ OK (${mem_percent}%)${NC}"
        return 0
    elif [ "$mem_percent" -lt 85 ]; then
        echo -e "${YELLOW}⚠ WARNING (${mem_percent}%)${NC}"
        return 1
    else
        echo -e "${RED}✗ CRITICAL (${mem_percent}%)${NC}"
        return 1
    fi
}

# Run checks
echo "========================================="
echo "YAWL Health Check - $(date)"
echo "========================================="
echo

check_service "YAWL Engine" 8080 "/resourceService/"
check_service "Grafana" 3000
check_service "Prometheus" 9090
check_service "Kibana" 5601

echo
check_database
check_redis

echo
check_disk_space
check_memory

echo
echo "========================================="
echo "Health check complete"
echo "========================================="
```

## Backup and Recovery

### Automated Backup Strategy

```bash
#!/bin/bash
# File: backup-yawl.sh
# Daily backup with retention

BACKUP_DIR="/backups/yawl"
RETENTION_DAYS=30
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Create backup directory
mkdir -p $BACKUP_DIR

# 1. Database backup (full dump)
echo "Backing up database..."
docker exec yawl-postgres pg_dump -U yawl yawl | \
  gzip > $BACKUP_DIR/yawl_db_$TIMESTAMP.sql.gz

# 2. Database backup (pg_basebackup for point-in-time recovery)
docker exec yawl-postgres pg_basebackup \
  -U yawl -D $BACKUP_DIR/yawl_backup_$TIMESTAMP -Ft -z

# 3. Application data backup
echo "Backing up application data..."
tar -czf $BACKUP_DIR/yawl_data_$TIMESTAMP.tar.gz \
  /data/yawl_cases/ \
  /data/yawl_documents/

# 4. Configuration backup
tar -czf $BACKUP_DIR/yawl_config_$TIMESTAMP.tar.gz \
  /etc/yawl/ \
  docker-compose.yml \
  .env

# 5. Clean old backups
echo "Cleaning backups older than $RETENTION_DAYS days..."
find $BACKUP_DIR -type f -mtime +$RETENTION_DAYS -delete

# 6. Verify backups
echo "Backup sizes:"
du -sh $BACKUP_DIR/*

# 7. Send to off-site storage (e.g., S3)
echo "Uploading to S3..."
aws s3 cp $BACKUP_DIR/yawl_db_$TIMESTAMP.sql.gz \
  s3://yawl-backups/daily/

echo "Backup complete!"
```

### Backup Schedule Configuration

```bash
# Add to crontab for daily backups at 2 AM UTC
# crontab -e

# Daily backup
0 2 * * * /home/yawl/scripts/backup-yawl.sh >> /var/log/yawl-backup.log 2>&1

# Weekly full backup
0 3 * * 0 /home/yawl/scripts/backup-yawl-full.sh >> /var/log/yawl-backup.log 2>&1

# Monthly archive
0 4 1 * * /home/yawl/scripts/backup-yawl-archive.sh >> /var/log/yawl-backup.log 2>&1
```

### Recovery Procedures

```bash
# 1. Point-in-time recovery (from most recent backup)
# First, stop YAWL services
docker-compose stop yawl

# Stop database to perform recovery
docker-compose stop postgres

# Restore from backup
docker exec yawl-postgres pg_restore -U yawl \
  -C -d yawl /backups/yawl_db_20260214_020000.sql.gz

# Restart services
docker-compose up -d

# 2. Recovery from specific backup
# List available backups
ls -ltr /backups/yawl/

# Create temporary database
docker exec yawl-postgres createdb -U yawl yawl_restore

# Restore to temporary database
docker exec yawl-postgres pg_restore -U yawl \
  -d yawl_restore /backups/yawl_db_20260213_020000.sql.gz

# Validate recovery
docker exec yawl-postgres psql -U yawl -d yawl_restore -c "SELECT count(*) FROM yptask;"

# If successful, swap databases
docker exec yawl-postgres psql -U postgres -c \
  "ALTER DATABASE yawl RENAME TO yawl_old;"
docker exec yawl-postgres psql -U postgres -c \
  "ALTER DATABASE yawl_restore RENAME TO yawl;"

# 3. Test backup integrity weekly
test-backup-recovery() {
  # Restore to test database
  docker exec yawl-postgres createdb -U yawl yawl_test
  docker exec yawl-postgres pg_restore -U yawl \
    -d yawl_test /backups/yawl_db_latest.sql.gz

  # Run integrity checks
  docker exec yawl-postgres psql -U yawl -d yawl_test << EOF
    SELECT count(*) FROM yptask;
    SELECT count(*) FROM ypcase;
    SELECT count(*) FROM ypworkitem;
    SELECT count(*) FROM ypresource;
  EOF

  # Cleanup
  docker exec yawl-postgres dropdb -U yawl yawl_test
}
```

## Performance Tuning

### Database Optimization

```bash
# 1. Check query performance
docker exec yawl-postgres psql -U yawl -d yawl << EOF
-- Enable query analysis
EXPLAIN ANALYZE
SELECT * FROM ypcase WHERE status = 'ACTIVE'
  AND created_time > now() - interval '30 days'
ORDER BY created_time DESC;

-- Check missing indexes
SELECT * FROM pg_stat_user_indexes
ORDER BY idx_blks_read DESC;

-- Check table sizes
SELECT schemaname, tablename,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
EOF

# 2. Create recommended indexes
docker exec yawl-postgres psql -U yawl -d yawl << EOF
-- For case lookups
CREATE INDEX IF NOT EXISTS idx_ypcase_status
  ON ypcase(status) WHERE status != 'COMPLETED';

CREATE INDEX IF NOT EXISTS idx_ypcase_created
  ON ypcase(created_time DESC);

-- For work item lookups
CREATE INDEX IF NOT EXISTS idx_ypworkitem_allocated
  ON ypworkitem(allocated_to, status);

CREATE INDEX IF NOT EXISTS idx_ypworkitem_task_status
  ON ypworkitem(taskid, status);

-- For audit trail queries
CREATE INDEX IF NOT EXISTS idx_ypauditlog_timestamp
  ON ypauditlog(timestamp DESC);

-- Analyze query planner statistics
ANALYZE;
EOF

# 3. Vacuum and optimize
docker exec yawl-postgres psql -U yawl -d yawl << EOF
-- Full vacuum (locks table, run during maintenance window)
VACUUM FULL;

-- Reindex all tables
REINDEX DATABASE yawl;

-- Checkpoint and sync
CHECKPOINT;
EOF

# 4. Connection pool tuning
# Edit Tomcat context.xml
# <Resource name="jdbc/yawlDS"
#   maxActive="30"        # Increase if pool exhausted
#   maxIdle="10"          # Keep some connections alive
#   maxWait="30000"       # 30 second timeout
#   validation query="SELECT 1"
# />

# 5. Check slow queries (enable query log)
docker exec -it yawl-postgres psql -U postgres << EOF
ALTER SYSTEM SET log_min_duration_statement = 1000;  -- Log queries > 1 second
SELECT pg_reload_conf();
EOF
```

### Application Tuning

```bash
# 1. JVM Heap optimization
# In docker-compose.yml:
environment:
  JAVA_OPTS: |
    -Xms1024m -Xmx2048m           # Heap size
    -XX:+UseG1GC                   # Garbage collector
    -XX:MaxGCPauseMillis=200       # Max GC pause
    -XX:+ParallelRefProcEnabled    # Parallel GC
    -XX:+AlwaysPreTouch            # Pre-allocate memory
    -XX:+PrintGCDetails            # Log GC events
    -XX:+PrintGCDateStamps
    -Xloggc:/usr/local/tomcat/logs/gc.log

# 2. Thread pool optimization
# Edit Tomcat server.xml
# <Executor name="tomcatThreadPool"
#   namePrefix="catalina-exec-"
#   maxThreads="200"              # Increase for high concurrency
#   minSpareThreads="50"
#   maxIdleTime="60000"
# />

# 3. Cache configuration
# Edit application properties:
# spring.cache.type=redis          # Use Redis instead of in-memory
# spring.redis.timeout=2000
# spring.redis.jedis.pool.max-active=20

# 4. Connection pooling
# Edit Tomcat context.xml:
# connectionProperties="cachePrepStmts=true;prepStmtCacheSize=250;prepStmtCacheSqlLimit=2048"

# 5. Monitor GC performance
watch 'tail -20 /data/logs/gc.log | grep "G1 Pause"'
```

### Cache Optimization

```bash
# 1. Monitor Redis memory
docker exec yawl-redis redis-cli INFO memory

# Expected output:
# used_memory_human: 500M
# used_memory_peak_human: 600M
# maxmemory_human: 1G

# 2. Analyze key usage
docker exec yawl-redis redis-cli --scan | head -100

# 3. Clear unused cache
docker exec yawl-redis redis-cli FLUSHDB  # Clear current database
docker exec yawl-redis redis-cli EVAL "return redis.call('keys','*')" 0 | wc -l

# 4. Set expiration policies
docker exec yawl-redis redis-cli << EOF
-- Expire keys after 24 hours
EXPIRE session:* 86400
EXPIRE cache:* 3600

-- Check expiration times
TTL session:user123
TTL cache:workitem456
EOF

# 5. Monitor eviction policy
docker exec yawl-redis redis-cli CONFIG GET maxmemory-policy
# Expected: allkeys-lru or volatile-lru
```

## Scaling Operations

### Horizontal Scaling (Docker Compose)

```bash
# 1. Scale YAWL Engine instances
docker-compose up -d --scale yawl=5

# 2. Verify scaling
docker-compose ps | grep yawl
# Should show 5 instances running

# 3. Configure load balancer (Nginx)
# Edit docker/nginx.conf:
upstream yawl_backend {
    server yawl:8080;
    server yawl_2:8080;
    server yawl_3:8080;
    server yawl_4:8080;
    server yawl_5:8080;
}

server {
    listen 80;
    location / {
        proxy_pass http://yawl_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Session affinity
        proxy_cookie_path / "/";
        proxy_buffering off;
    }
}

# 4. Monitor scale-up
watch 'docker-compose ps | grep yawl'
```

### Vertical Scaling

```bash
# 1. Increase resource allocation
docker update --cpus 4 --memory 4g yawl-engine
docker update --cpus 2 --memory 2g yawl-postgres

# 2. Restart with new resources
docker-compose restart yawl

# 3. Monitor resource usage
watch 'docker stats --no-stream | grep yawl'
```

### Kubernetes Scaling

```bash
# 1. Manual scaling
kubectl scale deployment yawl --replicas=5 -n yawl

# 2. Configure autoscaling
kubectl autoscale deployment yawl \
  --min=3 \
  --max=10 \
  --cpu-percent=70 \
  -n yawl

# 3. Monitor scaling
kubectl get hpa -n yawl -w
kubectl top pods -n yawl

# 4. View scaling history
kubectl describe hpa -n yawl
```

## User and Role Management

### User Management CLI

```bash
#!/bin/bash
# File: manage-users.sh

# Add new user
add_user() {
    local username=$1
    local password=$2
    local email=$3

    curl -X POST http://localhost:8080/resourceService/users \
      -H "Content-Type: application/xml" \
      --data "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
      <user>
        <userid>$username</userid>
        <firstname>$(echo $username | cut -d. -f1)</firstname>
        <lastname>$(echo $username | cut -d. -f2)</lastname>
        <email>$email</email>
        <password>$password</password>
      </user>"

    echo "User $username created"
}

# Reset password
reset_password() {
    local username=$1
    local new_password=$2

    curl -X PUT http://localhost:8080/resourceService/users/$username/password \
      -H "Content-Type: application/json" \
      --data "{\"password\": \"$new_password\"}"

    echo "Password reset for $username"
}

# Disable user
disable_user() {
    local username=$1

    curl -X PUT http://localhost:8080/resourceService/users/$username/status \
      -H "Content-Type: application/json" \
      --data "{\"status\": \"DISABLED\"}"

    echo "User $username disabled"
}

# List all users
list_users() {
    curl -s http://localhost:8080/resourceService/users | \
      grep -oP '(?<=<userid>)[^<]*'
}

# Usage examples
add_user "john.doe" "SecurePassword123!" "john.doe@example.com"
reset_password "john.doe" "NewPassword456!"
disable_user "john.doe"
list_users
```

### Role and Permission Management

```bash
#!/bin/bash
# File: manage-roles.sh

# Create role
create_role() {
    local role_name=$1
    local description=$2

    curl -X POST http://localhost:8080/resourceService/roles \
      -H "Content-Type: application/xml" \
      --data "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
      <role>
        <name>$role_name</name>
        <description>$description</description>
      </role>"

    echo "Role $role_name created"
}

# Assign role to user
assign_role() {
    local username=$1
    local role=$2

    curl -X POST http://localhost:8080/resourceService/users/$username/roles \
      -H "Content-Type: application/xml" \
      --data "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
      <role>$role</role>"

    echo "Role $role assigned to $username"
}

# List user permissions
list_permissions() {
    local username=$1

    curl -s http://localhost:8080/resourceService/users/$username/permissions | \
      grep -oP '(?<=<permission>)[^<]*'
}

# Grant permission
grant_permission() {
    local username=$1
    local permission=$2

    curl -X POST http://localhost:8080/resourceService/users/$username/permissions \
      -H "Content-Type: application/xml" \
      --data "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
      <permission>$permission</permission>"

    echo "Permission $permission granted to $username"
}

# Usage examples
create_role "workflow_designer" "Users who can design workflows"
create_role "workflow_executor" "Users who execute work items"
create_role "system_admin" "System administrators"

assign_role "john.doe" "workflow_designer"
grant_permission "john.doe" "create_workflow"
grant_permission "john.doe" "deploy_workflow"
```

## Workflow Deployment

### Workflow Upload and Validation

```bash
#!/bin/bash
# File: deploy-workflow.sh

# Deploy workflow specification
deploy_workflow() {
    local workflow_file=$1
    local workflow_id=$2

    echo "Deploying workflow: $workflow_id"

    # Validate XML schema
    if ! xmllint --noout --schema /etc/yawl/YAWL_Schema4.0.xsd "$workflow_file"; then
        echo "ERROR: Workflow validation failed"
        return 1
    fi

    # Upload to YAWL Engine
    curl -X POST http://localhost:8080/engine \
      -H "Content-Type: application/xml" \
      -d @"$workflow_file" \
      -o deploy_response.xml

    # Parse response
    if grep -q "success" deploy_response.xml; then
        echo "Workflow deployed successfully"
        grep -oP '(?<=<id>)[^<]*' deploy_response.xml
    else
        echo "Deployment failed:"
        cat deploy_response.xml
        return 1
    fi
}

# Enable workflow
enable_workflow() {
    local workflow_id=$1

    curl -X POST http://localhost:8080/engine/workflow/$workflow_id/enable \
      -H "Content-Type: application/xml"

    echo "Workflow $workflow_id enabled"
}

# Disable workflow
disable_workflow() {
    local workflow_id=$1

    curl -X POST http://localhost:8080/engine/workflow/$workflow_id/disable \
      -H "Content-Type: application/xml"

    echo "Workflow $workflow_id disabled"
}

# Unload workflow
unload_workflow() {
    local workflow_id=$1

    curl -X DELETE http://localhost:8080/engine/workflow/$workflow_id \
      -H "Content-Type: application/xml"

    echo "Workflow $workflow_id unloaded"
}

# List deployed workflows
list_workflows() {
    curl -s http://localhost:8080/engine/workflows | \
      grep -oP '(?<=<id>)[^<]*'
}

# Usage examples
deploy_workflow "order-fulfillment.yawl" "order_fulfillment_1"
enable_workflow "order_fulfillment_1"
list_workflows
```

### Workflow Versioning and Rollback

```bash
# Version control for workflows
git init workflows/
cd workflows/

# Add workflow to git
git add order-fulfillment.yawl
git commit -m "Deploy order fulfillment workflow v1.0"
git tag -a v1.0 -m "Production release v1.0"

# Track changes
git log --oneline

# Rollback to previous version
git checkout v0.9
# Redeploy previous version using deploy-workflow.sh

# Create branches for different environments
git branch development
git branch staging
git branch production

# Merge from staging to production
git checkout production
git merge staging
```

## Case Management

### Case Lifecycle Operations

```bash
#!/bin/bash
# File: case-operations.sh

# Start new case
start_case() {
    local workflow_id=$1
    local case_data=$2

    curl -X POST http://localhost:8080/engine/cases \
      -H "Content-Type: application/xml" \
      --data "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
      <case>
        <workflow>$workflow_id</workflow>
        <data>$case_data</data>
      </case>"
}

# Get case details
get_case() {
    local case_id=$1

    curl -s http://localhost:8080/engine/cases/$case_id | \
      xmllint --format -
}

# List active cases
list_active_cases() {
    curl -s http://localhost:8080/engine/cases?status=ACTIVE | \
      grep -oP '(?<=<id>)[^<]*'
}

# Complete case prematurely
force_complete_case() {
    local case_id=$1

    curl -X POST http://localhost:8080/engine/cases/$case_id/complete \
      -H "Content-Type: application/xml"

    echo "Case $case_id completed"
}

# Cancel case
cancel_case() {
    local case_id=$1

    curl -X POST http://localhost:8080/engine/cases/$case_id/cancel \
      -H "Content-Type: application/xml"

    echo "Case $case_id cancelled"
}

# Archive case
archive_case() {
    local case_id=$1

    curl -X POST http://localhost:8080/engine/cases/$case_id/archive \
      -H "Content-Type: application/xml"

    echo "Case $case_id archived"
}

# Get case history
case_history() {
    local case_id=$1

    curl -s http://localhost:8080/engine/cases/$case_id/history | \
      xmllint --format -
}

# Usage examples
start_case "order_fulfillment_1" "<order><items>5</items></order>"
list_active_cases
cancel_case "case_12345"
```

## Maintenance Tasks

### Regular Maintenance Schedule

```bash
#!/bin/bash
# File: maintenance-schedule.sh

# Daily (02:00 UTC)
daily_maintenance() {
    echo "Daily maintenance starting..."

    # Backup database
    /scripts/backup-yawl.sh

    # Archive old cases (older than 90 days)
    docker exec yawl-postgres psql -U yawl -d yawl << EOF
    INSERT INTO ypcase_archive
    SELECT * FROM ypcase
    WHERE completed_time < now() - interval '90 days'
      AND status = 'COMPLETED';
    DELETE FROM ypcase
    WHERE completed_time < now() - interval '90 days'
      AND status = 'COMPLETED';
    VACUUM ANALYZE;
    EOF
}

# Weekly (Sunday 03:00 UTC)
weekly_maintenance() {
    echo "Weekly maintenance starting..."

    # Full vacuum and reindex
    docker exec yawl-postgres psql -U yawl -d yawl << EOF
    VACUUM FULL ANALYZE;
    REINDEX DATABASE yawl;
    EOF

    # Update statistics
    docker exec yawl-postgres psql -U yawl -d yawl -c "ANALYZE;"
}

# Monthly (1st of month, 04:00 UTC)
monthly_maintenance() {
    echo "Monthly maintenance starting..."

    # Full system backup
    tar -czf /backups/yawl_full_$(date +%Y%m%d).tar.gz \
      /data/yawl /etc/yawl

    # Upload to cold storage (S3)
    aws s3 cp /backups/yawl_full_$(date +%Y%m%d).tar.gz \
      s3://yawl-backups/monthly/ --storage-class GLACIER
}

# Create cron jobs
setup_maintenance_cron() {
    (crontab -l 2>/dev/null; echo "0 2 * * * /scripts/maintenance-schedule.sh daily") | crontab -
    (crontab -l 2>/dev/null; echo "0 3 * * 0 /scripts/maintenance-schedule.sh weekly") | crontab -
    (crontab -l 2>/dev/null; echo "0 4 1 * * /scripts/maintenance-schedule.sh monthly") | crontab -
}
```

### Log Rotation Configuration

```bash
# File: /etc/logrotate.d/yawl

/data/logs/yawl*.log {
    daily                    # Rotate daily
    rotate 30                # Keep 30 old logs
    compress                 # Gzip old logs
    delaycompress            # Delay compression
    missingok                # Don't error if missing
    notifempty               # Don't rotate if empty
    create 0640 tomcat tomcat
    sharedscripts

    postrotate
        docker exec yawl-engine /usr/local/tomcat/bin/shutdown.sh
        sleep 5
        docker exec yawl-engine /usr/local/tomcat/bin/startup.sh
    endscript
}

# Test logrotate
logrotate -d /etc/logrotate.d/yawl
```

## Incident Response

### Common Incidents and Resolution

```bash
# ============ INCIDENT 1: Service Down ============
# Symptoms: YAWL Engine unreachable
incident_service_down() {
    echo "Attempting to recover YAWL service..."

    # 1. Check service status
    curl -i http://localhost:8080/resourceService/

    # 2. Check logs for errors
    docker logs yawl-engine | tail -50

    # 3. Restart service
    docker-compose restart yawl
    sleep 30

    # 4. Verify recovery
    curl -i http://localhost:8080/resourceService/

    # 5. If still down, check dependencies
    docker-compose ps
    docker exec yawl-postgres pg_isready -U yawl
    docker exec yawl-redis redis-cli ping
}

# ============ INCIDENT 2: High Database Load ============
incident_high_db_load() {
    echo "Investigating high database load..."

    # 1. Identify slow queries
    docker exec yawl-postgres psql -U yawl -d yawl << EOF
    SELECT query, calls, total_time, mean_time
    FROM pg_stat_statements
    ORDER BY total_time DESC
    LIMIT 10;
    EOF

    # 2. Check active connections
    docker exec yawl-postgres psql -U yawl -d yawl -c \
      "SELECT usename, count(*) FROM pg_stat_activity GROUP BY usename;"

    # 3. Kill long-running queries (if necessary)
    docker exec yawl-postgres psql -U postgres << EOF
    SELECT pg_terminate_backend(pid) FROM pg_stat_activity
    WHERE duration > interval '30 minutes' AND pid <> pg_backend_pid();
    EOF

    # 4. Rebuild indexes
    docker exec yawl-postgres psql -U yawl -d yawl -c "REINDEX DATABASE yawl;"
}

# ============ INCIDENT 3: Memory Leak ============
incident_memory_leak() {
    echo "Investigating memory leak..."

    # 1. Monitor memory over time
    for i in {1..10}; do
        docker stats --no-stream yawl-engine | tail -1
        sleep 60
    done

    # 2. Dump heap
    docker exec yawl-engine jmap -dump:live,format=b,file=/tmp/heap.bin $(pgrep -f tomcat)

    # 3. Analyze heap (requires jhat or Eclipse MAT)
    docker exec yawl-engine jhat /tmp/heap.bin

    # 4. Restart service to release memory
    docker-compose restart yawl
}

# ============ INCIDENT 4: Workflow Stuck ============
incident_workflow_stuck() {
    local case_id=$1

    echo "Investigating stuck workflow: $case_id"

    # 1. Check case status
    docker exec yawl-postgres psql -U yawl -d yawl -c \
      "SELECT * FROM ypcase WHERE id = $case_id;"

    # 2. Check work items
    docker exec yawl-postgres psql -U yawl -d yawl -c \
      "SELECT * FROM ypworkitem WHERE idcase = $case_id ORDER BY created_time;"

    # 3. Resume/restart workflow
    curl -X POST http://localhost:8080/engine/cases/$case_id/resume \
      -H "Content-Type: application/xml"

    # 4. If still stuck, force complete task
    docker exec yawl-postgres psql -U yawl -d yawl << EOF
    UPDATE ypworkitem
    SET status = 'COMPLETED',
        completion_time = now()
    WHERE idcase = $case_id
      AND status = 'ALLOCATED'
      AND enabled_time < now() - interval '24 hours';
    EOF
}
```

### Incident Documentation Template

```bash
#!/bin/bash
# File: incident-report-template.sh

create_incident_report() {
    local incident_id=$1
    local severity=$2      # Critical/High/Medium/Low
    local description=$3

    cat > incident_$incident_id.md << EOF
# Incident Report: $incident_id

**Date/Time**: $(date)
**Severity**: $severity
**Status**: Open

## Description
$description

## Timeline
- $(date): Incident detected
- Action items:
  - [ ] Root cause identified
  - [ ] Mitigation applied
  - [ ] Service restored
  - [ ] Post-mortem completed

## System State at Time of Incident

### Services Status
\`\`\`
$(docker-compose ps)
\`\`\`

### Recent Errors
\`\`\`
$(docker logs yawl-engine --since 1h | grep -i error | tail -20)
\`\`\`

### Database Connections
\`\`\`
$(docker exec yawl-postgres psql -U yawl -d yawl -c "SELECT * FROM pg_stat_activity;")
\`\`\`

### Resource Usage
\`\`\`
$(docker stats --no-stream)
\`\`\`

## Root Cause Analysis
TBD

## Resolution Steps
TBD

## Prevention Measures
TBD

## Lessons Learned
TBD
EOF

    echo "Incident report created: incident_$incident_id.md"
}

# Usage
create_incident_report "INC-2026-001" "High" "YAWL Engine service became unresponsive"
```

---

**Operations Guide Version**: 1.0
**Last Updated**: 2026-02-14
**Maintained By**: YAWL Foundation
