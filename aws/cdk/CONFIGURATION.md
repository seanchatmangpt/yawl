# YAWL CDK Configuration Guide

This document describes how to configure the YAWL AWS CDK infrastructure for different environments and use cases.

## Table of Contents

1. [Environment Variables](#environment-variables)
2. [Stack Configuration](#stack-configuration)
3. [Environment-Specific Settings](#environment-specific-settings)
4. [Advanced Configuration](#advanced-configuration)
5. [Cost Optimization](#cost-optimization)
6. [Security Configuration](#security-configuration)

## Environment Variables

### Core Configuration

```bash
# Deployment environment
export ENVIRONMENT=production  # development, staging, production

# AWS Configuration
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=123456789012  # Optional, auto-detected

# Container Image
export CONTAINER_IMAGE=123456789012.dkr.ecr.us-east-1.amazonaws.com/yawl:latest

# AWS CLI Profile
export AWS_PROFILE=production  # If using named profiles
```

### All Available Environment Variables

```bash
# Core
ENVIRONMENT              # deployment environment
AWS_REGION              # AWS region
AWS_ACCOUNT_ID          # AWS account ID
AWS_PROFILE             # AWS CLI profile
CONTAINER_IMAGE         # Docker image URI

# Optional CDK-specific
CDK_DEFAULT_REGION      # Default region for CDK
CDK_DEFAULT_ACCOUNT     # Default account for CDK
```

## Stack Configuration

### Modifying Stack Parameters

Edit `app.py` to adjust stack creation:

```python
# Example: Change desired count for ECS service
ecs_stack = YAWLECSStack(
    app,
    f"{stack_prefix}-ecs",
    vpc=network_stack.vpc,
    alb_sg=network_stack.alb_sg,
    ecs_sg=network_stack.ecs_sg,
    db_secret=database_stack.db_secret,
    environment=environment_name,
    container_image=container_image,
    env=aws_env,
    description=f"YAWL ECS Stack - {environment_name}",
)
```

### Modifying Stack Details in stacks.py

Edit specific stack classes to change configurations:

#### Network Configuration

```python
# In YAWLNetworkStack.__init__
self.vpc = ec2.Vpc(
    self,
    "YAWLVpc",
    cidr="10.0.0.0/16",  # Change VPC CIDR
    max_azs=2,           # Change number of AZs
    nat_gateways=1,      # Change number of NAT gateways
)
```

#### Database Configuration

```python
# In YAWLDatabaseStack.__init__
self.database = rds.DatabaseInstance(
    self,
    "YAWLDatabase",
    instance_type=ec2.InstanceType.of(
        ec2.InstanceClass.T3, ec2.InstanceSize.MEDIUM  # Change instance type
    ),
    allocated_storage=100,  # Change storage size
    backup_retention=Duration.days(30),  # Change backup retention
)
```

#### ECS Configuration

```python
# In YAWLECSStack.__init__
task_definition = ecs.FargateTaskDefinition(
    self,
    "YAWLTaskDefinition",
    memory_limit_mib=2048,  # Change memory (512, 1024, 2048, 4096)
    cpu=1024,               # Change CPU (256, 512, 1024, 2048)
)
```

## Environment-Specific Settings

### Development Environment

```bash
#!/bin/bash
export ENVIRONMENT=development
export AWS_REGION=us-east-1
export CONTAINER_IMAGE=public.ecr.aws/docker/library/nginx:latest

# Deploy
cdk deploy --require-approval=never
```

Development settings in code:
- RDS: db.t3.medium, single-AZ, minimal backups
- ECS: 1 desired count, max 2
- Redis: cache.t3.micro
- No Multi-AZ
- Faster deployments, lower costs

### Staging Environment

```bash
#!/bin/bash
export ENVIRONMENT=staging
export AWS_REGION=us-east-1
export CONTAINER_IMAGE=<account>.dkr.ecr.us-east-1.amazonaws.com/yawl:staging

# Deploy
cdk deploy --require-approval=never
```

Staging settings in code:
- RDS: db.t3.large, Multi-AZ disabled
- ECS: 2 desired count, max 4
- Redis: cache.t3.small
- Enhanced monitoring enabled

### Production Environment

```bash
#!/bin/bash
export ENVIRONMENT=production
export AWS_REGION=us-east-1
export CONTAINER_IMAGE=<account>.dkr.ecr.us-east-1.amazonaws.com/yawl:latest

# Deploy
cdk deploy --require-approval=never
```

Production settings in code:
- RDS: db.m5.large, Multi-AZ enabled, 30-day backups
- ECS: 2 desired count, max 6, aggressive auto-scaling
- Redis: cache.t3.small, Multi-AZ enabled
- All monitoring and security features enabled
- Deletion protection enabled

## Advanced Configuration

### Using Context Values

CDK context values in `cdk.json`:

```json
{
  "context": {
    "@aws-cdk/core:enableStackNameDuplicates": true,
    "custom-vpc-cidr": "10.0.0.0/16",
    "custom-db-instance": "db.t3.large"
  }
}
```

Access in code:

```python
custom_cidr = self.node.try_get_context("custom-vpc-cidr")
```

### Custom VPC CIDR

```python
# In YAWLNetworkStack.__init__
cidr_block = self.node.try_get_context("custom-vpc-cidr") or "10.0.0.0/16"
self.vpc = ec2.Vpc(
    self,
    "YAWLVpc",
    cidr=cidr_block,
    # ...
)
```

### Using Existing VPC

To use an existing VPC instead of creating a new one:

1. Modify `YAWLNetworkStack` to import existing VPC:

```python
# Option 1: Look up existing VPC by tag
self.vpc = ec2.Vpc.from_lookup(
    self,
    "ExistingVpc",
    is_default=False,
    tags={"Name": "my-existing-vpc"}
)

# Option 2: Look up by VPC ID
self.vpc = ec2.Vpc.from_lookup(
    self,
    "ExistingVpc",
    vpc_id="vpc-12345678"
)
```

2. Update security group creation to reference existing security groups if needed

### Custom Database Backup Strategy

```python
# In YAWLDatabaseStack.__init__
self.database = rds.DatabaseInstance(
    self,
    "YAWLDatabase",
    backup_retention=Duration.days(90),  # Increase retention
    copy_logs_to_cloudwatch=True,        # Enable log copying
    cloudwatch_logs_exports=["postgresql", "upgrade"],  # Additional logs
    enable_cloudwatch_logs_exports=True,
    # ... other config
)
```

### Custom Auto-scaling Rules

```python
# In YAWLECSStack.__init__
scaling = self.service.auto_scale_task_count(
    min_capacity=1,
    max_capacity=10,
)

# Scale on custom metric
scaling.scale_on_metric(
    "CustomMetricScaling",
    metric=cloudwatch.Metric(
        namespace="YAWLApp",
        metric_name="WorkflowQueueDepth",
        statistic="Average",
    ),
    scaling_steps=[
        autoscaling.ScalingInterval(change=1, lower=0, upper=50),
        autoscaling.ScalingInterval(change=2, lower=50, upper=100),
        autoscaling.ScalingInterval(change=-1, lower=None, upper=20),
    ],
    adjustment_type=autoscaling.AdjustmentType.CHANGE_IN_CAPACITY,
)
```

## Cost Optimization

### Instance Type Selection

```bash
# Development (lowest cost)
Instance Types:
  RDS: db.t3.micro ($0.017/hour)
  ECS: 256 CPU, 512 memory
  Redis: cache.t3.micro

# Staging (balanced)
Instance Types:
  RDS: db.t3.small ($0.034/hour)
  ECS: 512 CPU, 1024 memory
  Redis: cache.t3.small

# Production (optimized)
Instance Types:
  RDS: db.t3.small ($0.034/hour) for baseline
  RDS: db.m5.large ($0.192/hour) for high-load
  ECS: 1024 CPU, 2048 memory
  Redis: cache.t3.small
```

### Reserved Instances

Purchase RDS Reserved Instances for 1-3 years:

```bash
# For production RDS (db.t3.small, Multi-AZ)
# 1-year commitment: ~60% savings
# 3-year commitment: ~70% savings
```

### Spot Instances for ECS

To use Spot instances with ECS:

```python
# In YAWLECSStack.__init__
self.service = ecs.FargateService(
    self,
    "YAWLService",
    # ... existing config
    spot=True,  # Use Spot instances (~70% cost savings)
)
```

### S3 Lifecycle Management

```python
# S3 bucket transitions (already configured)
lifecycle_rules=[
    s3.LifecycleRule(
        transitions=[
            s3.Transition(
                storage_class=s3.StorageClass.GLACIER,
                transition_after=Duration.days(30),  # Adjust timing
            ),
        ],
    ),
]
```

### CloudFront Caching

```python
# Adjust cache policies in YAWLDistributionStack
cache_policy=cloudfront.CachePolicy.CACHING_OPTIMIZED,  # For static content
cache_policy=cloudfront.CachePolicy.CACHING_DISABLED,   # For dynamic content
```

## Security Configuration

### Enable VPC Flow Logs

```python
# In YAWLNetworkStack.__init__
self.vpc.add_flow_log(
    "VpcFlowLogs",
    traffic_type=ec2.FlowLogTrafficType.ALL,
    destination=ec2.FlowLogDestination.to_cloud_watch_logs(),
    log_retention=logs.RetentionDays.ONE_MONTH,
)
```

### Enable CloudTrail Logging

```python
# Add to app.py
from aws_cdk import aws_cloudtrail as cloudtrail

trail = cloudtrail.Trail(
    self,
    "YAWLTrail",
    bucket=s3.Bucket(self, "TrailBucket"),
    is_multi_region_trail=True,
)
```

### Enable WAF for CloudFront

```python
# In YAWLDistributionStack.__init__
from aws_cdk import aws_wafv2 as wafv2

web_acl = wafv2.CfnWebACL(
    self,
    "WAF",
    default_action=wafv2.CfnWebACL.DefaultActionProperty(allow={}),
    visibility_config=wafv2.CfnWebACL.VisibilityConfigProperty(
        sampled_requests_enabled=True,
        cloud_watch_metrics_enabled=True,
        metric_name="YAWL-WAF",
    ),
    scope="CLOUDFRONT",
    rules=[
        # Add WAF rules here
    ],
)
```

### Database Authentication

```python
# Enable IAM Database Authentication
self.database = rds.DatabaseInstance(
    self,
    "YAWLDatabase",
    enable_iam_authentication=True,  # Already enabled
    # ...
)

# Use IAM auth token
# aws rds-db auth token get
```

### Secrets Rotation

```python
# In YAWLDatabaseStack
from aws_cdk import aws_secretsmanager as secretsmanager

self.db_secret.add_rotation_rules(
    automatically_after=Duration.days(30)
)

# Lambda function for rotation
rotation_lambda = lambda_.Function(
    self,
    "RotationLambda",
    code=lambda_.Code.from_asset("rotation_lambda"),
    handler="index.lambda_handler",
    runtime=lambda_.Runtime.PYTHON_3_11,
)

# Add rotation
self.db_secret.add_rotation_schedule(
    "RotationSchedule",
    automatically_after=Duration.days(30),
    hosted_rotation=secretsmanager.HostedRotation.postgres_single_user(
        secret=self.db_secret,
    ),
)
```

### Network Segmentation

```python
# Already configured with:
# - Public subnets for ALB
# - Private subnets with NAT for ECS
# - Isolated subnets for RDS
# - Different security groups per layer
```

### Encryption Configuration

```python
# Already enabled:
# - RDS: KMS encryption at rest
# - S3: KMS encryption at rest
# - Redis: Encryption at rest and in transit
# - CloudWatch Logs: KMS encryption
# - EBS volumes: Encryption at rest
```

## Deployment Examples

### Deploy with Custom Configuration

```bash
#!/bin/bash
set -e

# Set environment
export ENVIRONMENT=production
export AWS_REGION=us-west-2
export CONTAINER_IMAGE=my-account.dkr.ecr.us-west-2.amazonaws.com/yawl:v1.0.0

# Activate venv
source venv/bin/activate

# Show what will be deployed
cdk diff

# Deploy with approval
read -p "Deploy? (yes/no): " confirm
if [ "$confirm" = "yes" ]; then
    cdk deploy --require-approval=never
fi
```

### Deploy to Multiple Regions

```bash
#!/bin/bash

for REGION in us-east-1 eu-west-1 ap-southeast-1; do
    export AWS_REGION=$REGION
    export ENVIRONMENT=production

    echo "Deploying to $REGION..."
    cdk bootstrap aws://$(aws sts get-caller-identity --query Account --output text)/$REGION
    cdk deploy --require-approval=never
done
```

### Conditional Deployment

```bash
#!/bin/bash

# Only deploy monitoring if environment is production
if [ "$ENVIRONMENT" = "production" ]; then
    cdk deploy yawl-production-monitoring --require-approval=never
fi
```

## Troubleshooting Configuration

### Verify Configuration

```bash
# Show all context values
cdk context

# List all stacks
cdk list

# Synthesize to view CloudFormation
cdk synth
```

### Debug Stack Creation

```bash
# Detailed output
cdk deploy --require-approval=never --verbose

# Keep CloudFormation template
cdk deploy --require-approval=never --save-stack-outputs
```

### Check Parameter Values

```python
# In stacks.py, add debug output
print(f"Environment: {environment}")
print(f"VPC CIDR: {vpc_cidr}")
print(f"RDS Instance: {db_instance_type}")
```

## Next Steps

1. Review and adjust configurations based on your requirements
2. Test in development environment first
3. Validate security and compliance requirements
4. Document any customizations
5. Test disaster recovery procedures
