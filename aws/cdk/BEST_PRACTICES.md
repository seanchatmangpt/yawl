# YAWL CDK Infrastructure - Best Practices and Architecture Guide

## Architecture Overview

### High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Internet / Users                          │
└────────────────┬────────────────────────────────────────────────┘
                 │
        ┌────────▼────────┐
        │   CloudFront    │  Global Content Delivery, DDoS Protection
        │  Distribution   │
        └────────┬────────┘
                 │
    ┌────────────┴────────────┐
    │                         │
    │  Static Content (S3)    │  API / Dynamic Content
    │  /static, /assets       │  via ALB
    │                         │
┌───▼───┐                ┌───▼──────────────────────────┐
│   S3  │                │  Application Load Balancer   │
│Bucket │                │       (ALB)                  │
└───────┘                └───┬──────────────────────────┘
                             │
             ┌───────────────┼───────────────┐
             │               │               │
        ┌────▼────┐    ┌────▼────┐    ┌────▼────┐
        │  ECS    │    │  ECS    │    │  ECS    │
        │ Fargate │    │ Fargate │    │ Fargate │
        │ Task 1  │    │ Task 2  │    │ Task N  │
        └────┬────┘    └────┬────┘    └────┬────┘
             │               │               │
        ┌────┴───────────────┼───────────────┘
        │                    │
        │    ┌───────────────┼───────────────┐
        │    │               │               │
    ┌───▼────▼──┐  ┌─────────▼────────┐  ┌──▼──────────┐
    │PostgreSQL │  │ ElastiCache      │  │ CloudWatch  │
    │  RDS      │  │ Redis            │  │ Logs        │
    │(Multi-AZ) │  │ (Multi-AZ)       │  │             │
    └───────────┘  └──────────────────┘  └─────────────┘

VPC: 10.0.0.0/16
├─ Public Subnets (ALB)
├─ Private Subnets (ECS)
└─ Isolated Subnets (RDS, Redis)
```

## Core Components

### 1. Network Infrastructure (VPC)

**Purpose**: Isolated network environment with proper segmentation

**Design Decisions**:
- **CIDR Block**: 10.0.0.0/16 (65,536 addresses)
- **Availability Zones**: 2 AZs for high availability
- **Subnet Configuration**:
  - Public (10.0.1.0/24, 10.0.2.0/24): ALB and NAT Gateway
  - Private (10.0.3.0/24, 10.0.4.0/24): ECS tasks with NAT egress
  - Isolated (10.0.10.0/24, 10.0.11.0/24): RDS and Redis (no internet access)

**Best Practices**:
- Use least privilege security groups
- Enable VPC Flow Logs for debugging
- Use Security Group descriptions for documentation
- Regularly audit ingress/egress rules

```python
# Security Group Best Practice
alb_sg.add_ingress_rule(
    peer=ec2.Peer.any_ipv4(),
    connection=ec2.Port.tcp(80),
    description="Allow HTTP from internet",  # Always document
)
```

### 2. Database Layer (RDS PostgreSQL)

**Purpose**: Persistent data storage with high availability

**Configuration**:
- **Engine**: PostgreSQL 15.4
- **Backup**: 30-day retention, automated snapshots
- **Encryption**: KMS encryption at rest
- **Multi-AZ**: Enabled for production
- **Monitoring**: Enhanced monitoring and CloudWatch logs

**Best Practices**:

1. **Connection Management**:
   ```python
   # Use connection pooling at application level
   # Never leave connections open indefinitely
   # Set connection pool size based on expected load
   ```

2. **Backup Strategy**:
   ```bash
   # Test backups regularly
   aws rds describe-db-snapshots --db-instance-identifier yawl-db-production

   # Restore to new instance for testing
   aws rds restore-db-instance-from-db-snapshot \
     --db-instance-identifier yawl-db-test \
     --db-snapshot-identifier <snapshot-id>
   ```

3. **Performance Tuning**:
   ```sql
   -- Enable query logging for slow queries
   ALTER SYSTEM SET log_min_duration_statement = 1000;

   -- Monitor with CloudWatch Logs Insights
   fields @timestamp, @message, duration
   | stats avg(duration), max(duration) by @message
   ```

4. **Credential Rotation**:
   - Credentials stored in AWS Secrets Manager
   - Rotate every 30 days (already configured)
   - Update application before expiration

### 3. Cache Layer (ElastiCache Redis)

**Purpose**: Session storage, caching, and real-time data

**Configuration**:
- **Engine**: Redis 7.0
- **Node Type**: cache.t3.small (production), cache.t3.micro (development)
- **Multi-AZ**: Enabled for production
- **Encryption**: In transit (TLS) and at rest
- **Auth**: Token-based authentication

**Best Practices**:

1. **Memory Management**:
   ```bash
   # Monitor memory usage
   aws elasticache describe-cache-clusters \
     --cache-cluster-id yawl-redis-prod \
     --show-cache-node-info

   # Set appropriate eviction policy
   # maxmemory-policy: allkeys-lru (for caching)
   # maxmemory-policy: volatile-lru (for sessions)
   ```

2. **Connection Pooling**:
   ```python
   # YAWL Application Configuration
   # Redis connection pool size: 10-20
   # Timeout: 5 seconds
   # Retry: 3 times
   ```

3. **Monitoring**:
   - CPU Utilization: Target < 70%
   - Evictions: Monitor for memory pressure
   - Connection Count: Ensure no connection leaks

### 4. Application Layer (ECS Fargate)

**Purpose**: Containerized YAWL Workflow Engine

**Configuration**:
- **CPU**: 1024 (1 vCPU)
- **Memory**: 2048 MB
- **Task Count**: 2-6 based on demand
- **Auto-scaling**: CPU 70%, Memory 80% thresholds
- **Health Checks**: /resourceService/ endpoint, 30s interval

**Best Practices**:

1. **Container Image Management**:
   ```bash
   # Use specific image tags, never use 'latest' in production
   CONTAINER_IMAGE=<account>.dkr.ecr.us-east-1.amazonaws.com/yawl:v1.2.3

   # Build with version tags
   docker build -t ${REPO}:v1.2.3 .
   docker tag ${REPO}:v1.2.3 ${REPO}:latest
   docker push ${REPO}:v1.2.3
   ```

2. **Task Definition Best Practices**:
   ```python
   # Set memory/CPU limits appropriately
   task_definition = ecs.FargateTaskDefinition(
       self,
       "YAWLTaskDefinition",
       memory_limit_mib=2048,  # Monitor and adjust
       cpu=1024,               # 256-4096 in increments of 256
   )

   # Set resource reservations for reliability
   container.add_port_mapping(
       container_port=8080,
       protocol=ecs.Protocol.TCP
   )
   ```

3. **Environment Variables**:
   ```python
   # Use Secrets Manager for sensitive data
   secrets={
       "YAWL_DB_PASSWORD": ecs.Secret.from_secrets_manager(
           db_secret, "password"
       ),
   }

   # Use environment variables for non-sensitive config
   environment={
       "YAWL_HEAP_SIZE": "1024m",
       "LOG_LEVEL": "INFO",
   }
   ```

4. **Logging**:
   ```python
   # Configure CloudWatch Logs
   logging=ecs.LogDriver.aws_logs(
       stream_prefix="yawl",
       log_group=log_group,
   )

   # Query logs with CloudWatch Logs Insights
   fields @timestamp, @message, @logStream
   | filter @message like /ERROR/
   | stats count() by @logStream
   ```

### 5. Load Balancer (ALB)

**Purpose**: Distribute traffic across ECS tasks

**Configuration**:
- **Type**: Application Load Balancer (Layer 7)
- **Health Check**: HTTP GET /resourceService/, 30s interval
- **Deregistration Delay**: 30 seconds (connection draining)
- **Scheme**: Internet-facing

**Best Practices**:

1. **Health Check Configuration**:
   ```python
   health_check=elbv2.HealthCheck(
       path="/resourceService/",
       interval=Duration.seconds(30),
       timeout=Duration.seconds(10),
       healthy_threshold_count=2,    # 2 successful checks = healthy
       unhealthy_threshold_count=3,  # 3 failed checks = unhealthy
   )
   ```

2. **Target Group Settings**:
   ```python
   target_group = elbv2.ApplicationTargetGroup(
       self,
       "YAWLTargetGroup",
       deregistration_delay=Duration.seconds(30),  # Graceful shutdown
       target_type=elbv2.TargetType.IP,
   )
   ```

3. **Monitoring ALB Health**:
   ```bash
   # Check unhealthy targets
   aws elbv2 describe-target-health \
     --target-group-arn <arn> \
     --query 'TargetHealthDescriptions[?TargetHealth.State==`unhealthy`]'

   # Common causes:
   # - Application not responding
   # - Security group rules
   # - Health check path incorrect
   # - Task still initializing
   ```

### 6. Content Delivery (CloudFront)

**Purpose**: Global content delivery with caching

**Configuration**:
- **Origins**:
  - ALB for dynamic content
  - S3 for static assets
- **Caching**:
  - Static content: 1 day (configurable)
  - Dynamic content: 0 (no cache)
- **Security**: HTTPS, Origin Access Control (OAC) for S3

**Best Practices**:

1. **Caching Strategy**:
   ```python
   # Static content: aggressive caching
   cache_policy=cloudfront.CachePolicy.CACHING_OPTIMIZED,

   # Dynamic content: no caching
   cache_policy=cloudfront.CachePolicy.CACHING_DISABLED,

   # API responses: short TTL
   cloudfront.CachePolicies.CachingCustom(
       default_ttl=Duration.minutes(5),
       max_ttl=Duration.minutes(30),
   )
   ```

2. **Origin Access Control**:
   ```python
   # Use OAC instead of OAI (newer approach)
   oac = cloudfront.OriginAccessControl(
       self,
       "S3OAC",
   )

   # Ensures S3 bucket is not publicly accessible
   ```

3. **Compression**:
   - CloudFront automatically compresses text, CSS, JavaScript
   - Reduces bandwidth by 50-80%

4. **Query String Handling**:
   ```python
   # Cache based on query parameters
   origin_request_policy=cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
   ```

### 7. Storage (S3)

**Purpose**: Backups, static content, artifacts

**Buckets**:
1. **Backup Bucket**:
   - Versioning enabled
   - Lifecycle: Glacier after 30 days, Deep Archive after 90 days
   - Retention: 365 days for noncurrent versions

2. **Static Content Bucket**:
   - Public read access via CloudFront
   - CORS configured for browser access
   - Server-side KMS encryption

3. **Artifacts Bucket**:
   - Build artifacts and deployment packages
   - Retention: 90 days
   - Versioning enabled

**Best Practices**:

1. **Bucket Policies**:
   ```python
   # Deny unencrypted uploads
   bucket.add_to_resource_policy(
       iam.PolicyStatement(
           effect=iam.Effect.DENY,
           principals=[iam.AnyPrincipal()],
           actions=["s3:PutObject"],
           resources=[bucket.arn_for_objects("*")],
           conditions={
               "StringNotEquals": {
                   "s3:x-amz-server-side-encryption": "aws:kms"
               }
           }
       )
   )
   ```

2. **Lifecycle Policies**:
   ```bash
   # Archive old data to save costs
   # S3: $0.023/GB/month
   # Glacier: $0.004/GB/month
   # Deep Archive: $0.00099/GB/month
   ```

3. **Monitoring Bucket Size**:
   ```bash
   # CloudWatch metric: BucketSizeBytes
   aws cloudwatch get-metric-statistics \
     --namespace AWS/S3 \
     --metric-name BucketSizeBytes \
     --dimensions Name=BucketName,Value=yawl-backups \
     --start-time 2024-01-01T00:00:00Z \
     --end-time 2024-02-01T00:00:00Z \
     --period 86400 \
     --statistics Average
   ```

### 8. Monitoring and Alarms

**Purpose**: Visibility into infrastructure health and performance

**Key Metrics**:

1. **ALB Metrics**:
   - TargetResponseTime: P50, P99
   - HealthyHostCount: Should be > 0
   - RequestCount: Total requests
   - HTTP 5xx: Server errors

2. **ECS Metrics**:
   - CPU Utilization: Target 70%
   - Memory Utilization: Target 80%
   - TaskCount: Running vs desired

3. **RDS Metrics**:
   - CPU Utilization: Target 80%
   - DatabaseConnections: Monitor for leaks
   - ReadLatency/WriteLatency: P99 < 5ms

4. **Custom Application Metrics**:
   ```python
   # Application should emit custom metrics
   # e.g., Workflow Execution Time, Queue Depth

   cloudwatch.Metric(
       namespace="YAWLApp",
       metric_name="WorkflowExecutionTime",
       statistic="Average",
       period=Duration.minutes(5),
   )
   ```

**Best Practices**:

1. **Alarm Configuration**:
   ```python
   # Avoid alert fatigue
   # Use meaningful thresholds
   # Set evaluation periods (2+ consecutive periods)

   cloudwatch.Alarm(
       self,
       "HighCPUAlarm",
       metric=ecs_cpu,
       threshold=80,
       evaluation_periods=3,  # 15 minutes = 3 × 5-min periods
       alarm_description="Alert when ECS CPU > 80% for 15 minutes",
   )
   ```

2. **SNS Notifications**:
   ```python
   # Subscribe to SNS topic for alerts
   alarm.add_alarm_action(cloudwatch.SnsAction(alarm_topic))

   # Implement notification routing:
   # - High severity (database down) → on-call engineer
   # - Medium severity (high CPU) → team chat
   # - Low severity (minor issues) → logs
   ```

3. **Dashboard Creation**:
   ```python
   # Group related metrics
   # Use different time ranges for drilling down
   # Pin to team dashboard

   dashboard.add_widgets(
       cloudwatch.GraphWidget(
           title="Application Metrics",
           left=[alb_response_time, ecs_cpu],
           right=[request_count],
       ),
   )
   ```

## Security Best Practices

### 1. Network Security

```python
# Principle of least privilege
alb_sg.add_ingress_rule(
    peer=ec2.Peer.any_ipv4(),
    connection=ec2.Port.tcp(80),  # Restrict if possible
)

# Restrict egress to necessary services only
ecs_sg.add_egress_rule(
    peer=db_sg,
    connection=ec2.Port.tcp(5432),
)
```

### 2. Data Encryption

```python
# All data encrypted at rest
rds_instance = rds.DatabaseInstance(
    kms_key=kms_key,  # KMS encryption
    storage_encrypted=True,
)

# All data encrypted in transit
redis_cluster = elasticache.CfnReplicationGroup(
    transit_encryption_enabled=True,  # TLS
    at_rest_encryption_enabled=True,   # KMS
)
```

### 3. Access Control

```python
# Use IAM roles, not API keys
task_role = iam.Role(
    assumed_by=iam.ServicePrincipal("ecs-tasks.amazonaws.com"),
)

# Grant least privilege permissions
task_role.add_to_policy(
    iam.PolicyStatement(
        effect=iam.Effect.ALLOW,
        actions=["s3:GetObject"],
        resources=[backup_bucket.arn_for_objects("*")],
    )
)
```

### 4. Secrets Management

```python
# Store credentials in Secrets Manager
db_secret = secretsmanager.Secret(
    generate_secret_string=...,
    removal_policy=RemovalPolicy.RETAIN,  # Don't lose credentials
)

# Automatic rotation
db_secret.add_rotation_rules(
    automatically_after=Duration.days(30)
)
```

### 5. Logging and Audit Trail

```python
# Enable all relevant logs
# CloudWatch Logs for application logs
# RDS logs for database queries
# VPC Flow Logs for network traffic
# CloudTrail for API calls

rds_instance = rds.DatabaseInstance(
    cloudwatch_logs_exports=["postgresql"],
    enable_iam_authentication=True,  # Audit who accessed database
)
```

## Operational Best Practices

### 1. Deployment Process

```bash
# Development → Staging → Production

# 1. Test in development
export ENVIRONMENT=development
cdk deploy --require-approval=never

# 2. Validate in staging
export ENVIRONMENT=staging
cdk deploy --require-approval=never

# 3. Deploy to production with approval
export ENVIRONMENT=production
cdk diff  # Review changes
cdk deploy  # Interactive approval
```

### 2. Infrastructure as Code

```python
# Use meaningful stack names
stack_prefix = f"yawl-{environment_name}"

# Tag all resources for cost tracking
Tags.of(resource).add("Environment", environment_name)
Tags.of(resource).add("CostCenter", "engineering")
Tags.of(resource).add("Owner", "platform-team")
```

### 3. Disaster Recovery

```bash
# RDS: Automated backups with 30-day retention
# S3: Versioning enabled, cross-region replication
# ECS: Auto-scaling ensures availability

# Test recovery procedures monthly:
# 1. Restore database from snapshot
# 2. Restore from S3 backups
# 3. Failover to standby AZ
```

### 4. Scaling Considerations

```python
# Horizontal scaling (ECS auto-scaling)
scaling = service.auto_scale_task_count(
    min_capacity=2,
    max_capacity=6,
)

# Vertical scaling (instance upgrades)
# RDS: Upgrade instance type during maintenance window
# Redis: Scale node type up or add read replicas

# Database capacity
# Monitor: CPU, Connections, Storage
# Plan: 1TB RDS ≈ 2-3 years of growth
```

## Cost Optimization

### 1. Compute Costs

```
ECS Fargate pricing (us-east-1):
- 1 vCPU: $0.04048/hour
- 2GB memory: $0.004445/hour
- Total per task: ~$0.045/hour

2 tasks × 24 hours × 30 days = $64.80/month
6 tasks during peak = $194.40/month
```

### 2. Database Costs

```
RDS PostgreSQL pricing (us-east-1):
- db.t3.small: $0.034/hour = $247.68/month
- Multi-AZ: 2x cost = $495.36/month
- Storage: $0.115/GB/month
```

### 3. Data Transfer Costs

```
CloudFront reduces costs:
- ALB: $0.0075/GB → Expensive for high traffic
- CloudFront: $0.085/GB → Cheaper for repeated requests
- S3: $0.09/GB → Expensive origin charges avoided

Example:
- 1TB monthly traffic
- Without CloudFront: 1TB × $0.0075 = $7.50
- With CloudFront (100% hit): 1TB × $0.085 = $85
- But origin traffic: 1TB × $0.09 = $90 → Saves $5 from reduced origin
```

### 4. Reserved Capacity

```bash
# RDS Reserved Instances
# db.t3.small, Multi-AZ, 1-year: ~60% discount
# 3-year: ~70% discount

# Save ~$300/month with reserved instances
```

## Monitoring and Observability

### Key Metrics Dashboard

```python
# Create comprehensive dashboard
dashboard = cloudwatch.Dashboard(
    self,
    "YAWLDashboard",
)

dashboard.add_widgets(
    # Application Performance
    cloudwatch.GraphWidget(
        title="Response Time (P50, P99)",
        left=[metric_p50, metric_p99],
    ),
    # Availability
    cloudwatch.GraphWidget(
        title="Error Rate (%)",
        left=[metric_error_rate],
    ),
    # Capacity
    cloudwatch.GraphWidget(
        title="Resource Utilization",
        left=[cpu, memory, disk],
    ),
)
```

## Compliance and Governance

### 1. Resource Tagging

```python
# Tag all resources for compliance
Tags.of(stack).add("Environment", environment)
Tags.of(stack).add("Owner", "platform-team")
Tags.of(stack).add("Compliance", "pci-dss")  # If applicable
Tags.of(stack).add("CostCenter", "engineering")
Tags.of(stack).add("BackupPolicy", "daily")
```

### 2. Data Protection

```python
# GDPR/CCPA compliance
# - Encryption at rest and in transit
# - Access logs and audit trails
# - Data retention policies
# - Right to deletion (can delete backups)
```

### 3. Backup and Retention

```python
# RDS: 30-day retention, automated backups
# S3: Versioning enabled, lifecycle policies
# Regular backup testing
```

## Troubleshooting Guide

See [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#troubleshooting) for detailed troubleshooting steps.

## Summary

This CDK infrastructure provides:
- ✓ Highly available, multi-AZ deployment
- ✓ Auto-scaling based on demand
- ✓ Encrypted data at rest and in transit
- ✓ Comprehensive monitoring and alerting
- ✓ Disaster recovery capabilities
- ✓ Cost-optimized resource allocation
- ✓ Security best practices implemented
- ✓ Operational procedures documented

For questions or improvements, refer to [README.md](README.md) and [CONFIGURATION.md](CONFIGURATION.md).
