# YAWL ECS Fargate Infrastructure as Code

Comprehensive Terraform configuration for deploying YAWL on AWS ECS Fargate with production-ready features including auto-scaling, monitoring, and high availability.

## Architecture Overview

This infrastructure provides:

```
┌─────────────────────────────────────────────────────────────────┐
│                        YAWL on ECS Fargate                       │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                     Internet Gateway                      │   │
│  └──────────────┬───────────────────────────────────────────┘   │
│                 │                                                │
│  ┌──────────────┴───────────────────────────────────────────┐   │
│  │            Application Load Balancer (ALB)               │   │
│  │         (Public Subnets - Multi-AZ)                       │   │
│  └──────────────┬───────────────────────────────────────────┘   │
│                 │                                                │
│  ┌──────────────┴───────────────────────────────────────────┐   │
│  │  ECS Fargate Service with Auto Scaling                   │   │
│  │  ┌──────────────────────────────────────────────────┐    │   │
│  │  │  Private Subnets (Multi-AZ)                      │    │   │
│  │  │  - YAWL Task 1                                   │    │   │
│  │  │  - YAWL Task 2                                   │    │   │
│  │  │  - YAWL Task N (auto-scaled)                     │    │   │
│  │  └──────────────────────────────────────────────────┘    │   │
│  └──────────────┬───────────────────────────────────────────┘   │
│                 │                                                │
│  ┌──────────────┴───────────────────────────────────────────┐   │
│  │         PostgreSQL RDS (Multi-AZ)                         │   │
│  │    - Automated Backups & Point-in-Time Recovery          │   │
│  │    - Enhanced Monitoring & Performance Insights          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │           CloudWatch Monitoring & Alarms                 │   │
│  │    - SNS Notifications (Email/Slack)                      │   │
│  │    - Custom Dashboards                                    │   │
│  │    - Composite Health Alarms                              │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## Module Structure

### 1. **VPC Module** (`vpc/main.tf`)
Networking infrastructure with high availability:
- VPC with configurable CIDR
- Public subnets for ALB (multi-AZ)
- Private subnets for ECS and RDS (multi-AZ)
- NAT Gateways for outbound connectivity
- Security groups for ALB, ECS, and RDS
- DB subnet group for RDS

**Key Features:**
- Multi-AZ deployment for high availability
- Private subnets for application and database tiers
- Proper security group isolation between tiers
- Internet Gateway for public subnet routing

### 2. **RDS Module** (`rds/main.tf`)
PostgreSQL database with enterprise features:
- PostgreSQL RDS instance
- Multi-AZ failover
- Automated backups with point-in-time recovery
- Performance Insights and Enhanced Monitoring
- CloudWatch alarms for CPU, connections, and latency
- Parameter group with optimized settings

**Key Features:**
- Storage encryption at rest
- Automated minor version upgrades
- Enhanced logging to CloudWatch
- Backup window customization
- Health monitoring with metric alarms

### 3. **ECS Module** (`ecs/main.tf`)
Container orchestration with auto-scaling:
- ECS Fargate cluster with Container Insights
- Task definition with proper role separation
- ECS service with ALB integration
- Auto-scaling policies (CPU and memory)
- CloudWatch log group
- IAM roles for task execution and application

**Key Features:**
- Fargate capacity provider strategy
- Automatic task restart on failure
- CloudWatch Logs integration
- S3 access for application data
- Auto-scaling based on CPU and memory metrics
- Service alarms for health monitoring

### 4. **ALB Module** (`alb/main.tf`)
Load balancing with SSL/TLS support:
- Application Load Balancer in public subnets
- HTTP and HTTPS listeners
- Health checks with configurable thresholds
- Path-based routing rules
- Access logs to S3 (optional)
- CloudWatch alarms for availability and response time

**Key Features:**
- HTTP to HTTPS redirection
- Target group with health checks
- Deregistration delay for graceful shutdown
- Cross-zone load balancing
- Multiple listener rules support

### 5. **Monitoring Module** (`monitoring/main.tf`)
Comprehensive observability stack:
- SNS topic for notifications
- CloudWatch dashboard
- Application error log group and metric filter
- Composite health alarm
- ALB anomaly detection
- Email and Slack integration

**Key Features:**
- Unified CloudWatch dashboard
- Multiple notification channels
- Anomaly detection for ALB response time
- Error tracking and alerting
- Health composite alarms

## Prerequisites

1. **AWS Account** with appropriate IAM permissions
2. **Terraform** >= 1.0 installed
3. **AWS CLI** configured with credentials
4. **Docker image** pushed to ECR (for container_image variable)
5. **SSL Certificate** (optional, for HTTPS) in ACM

## Quick Start

### 1. Setup Terraform State (Optional but Recommended)

```bash
# Create S3 bucket for state
aws s3api create-bucket \
  --bucket yawl-terraform-state-$(date +%s) \
  --region us-east-1

# Create DynamoDB table for locks
aws dynamodb create-table \
  --table-name terraform-locks \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
```

### 2. Initialize Terraform

```bash
cd terraform-aws
terraform init
```

### 3. Configure Variables

```bash
# Copy example configuration
cp terraform.tfvars.example terraform.tfvars

# Edit with your specific values
vim terraform.tfvars
```

**Required Variables:**
- `aws_account_id`: Your AWS account ID
- `environment`: dev, staging, or prod
- `container_image`: ECR image URI
- `rds_master_password`: Secure password (min 8 chars)

### 4. Plan and Apply

```bash
# Review changes
terraform plan

# Apply configuration
terraform apply

# View outputs
terraform output
```

## Configuration Guide

### Environment Variables (ECS)

Add application environment variables in `terraform.tfvars`:

```hcl
ecs_environment_variables = {
  LOG_LEVEL     = "INFO"
  APP_NAME      = "YAWL"
  DATABASE_URL  = "postgresql://user:pass@host:5432/yawldb"
}
```

### Secrets Management

Store sensitive data in AWS Secrets Manager:

```bash
# Create secret
aws secretsmanager create-secret \
  --name yawl-rds-password \
  --secret-string "YourSecurePassword123"

# Reference in terraform.tfvars
ecs_secrets = {
  DATABASE_PASSWORD = "arn:aws:secretsmanager:us-east-1:123456789012:secret:yawl-rds-password"
}
```

### Auto-Scaling Configuration

Adjust scaling behavior:

```hcl
min_capacity                = 2    # Minimum running tasks
max_capacity                = 10   # Maximum running tasks
target_cpu_utilization      = 70   # Scale at 70% CPU
target_memory_utilization   = 80   # Scale at 80% memory
desired_count               = 2    # Initial task count
```

### HTTPS Configuration

Enable HTTPS with ACM certificate:

```hcl
ssl_certificate_arn = "arn:aws:acm:us-east-1:123456789012:certificate/12345678"
```

### Monitoring and Alerts

Configure notifications:

```hcl
enable_monitoring         = true
alarm_email               = "ops@example.com"
slack_webhook_url         = "https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
monitoring_log_retention_days = 7
error_alarm_threshold     = 10
```

## Output Information

After deployment, Terraform provides:

```hcl
# Application Access
application_url          # HTTP URL
application_url_https    # HTTPS URL (if configured)

# Database Connection
rds_endpoint             # Database endpoint
rds_connection_string    # Full connection string

# Infrastructure Details
vpc_id                   # VPC ID
ecs_cluster_name         # ECS cluster name
alb_dns_name             # Load balancer DNS
sns_topic_arn            # SNS topic for alarms
cloudwatch_dashboard_url # Monitoring dashboard URL
```

## Accessing the Application

```bash
# Get ALB DNS name
terraform output alb_dns_name

# Access application
curl http://$(terraform output -raw alb_dns_name)

# View logs
aws logs tail /ecs/dev-yawl --follow

# View CloudWatch dashboard
open $(terraform output -raw cloudwatch_dashboard_url)
```

## Database Access

```bash
# Get RDS endpoint
RDS_ENDPOINT=$(terraform output -raw rds_address)

# Connect to database (from ECS task or bastion host)
psql -h $RDS_ENDPOINT -U yawlroot -d yawldb
```

## Scaling Operations

### Manual Scaling

```bash
# Update desired count
terraform apply -var="desired_count=5"
```

### Auto-Scaling Behavior

The infrastructure includes CPU and memory-based auto-scaling:
- Scales up when avg CPU or memory exceeds target
- Scales down when utilization decreases
- Minimum 2 tasks, maximum 10 tasks

## Monitoring and Troubleshooting

### View Application Logs

```bash
# Real-time logs
aws logs tail /ecs/dev-yawl --follow

# Last 100 lines
aws logs tail /ecs/dev-yawl --max-items 100
```

### Check Service Health

```bash
# Describe service
aws ecs describe-services \
  --cluster dev-yawl-cluster \
  --services dev-yawl-service \
  --query 'services[0].[serviceName,status,desiredCount,runningCount]'

# List running tasks
aws ecs list-tasks --cluster dev-yawl-cluster
```

### View Alarms

```bash
# List all alarms
aws cloudwatch describe-alarms

# Describe specific alarm
aws cloudwatch describe-alarms \
  --alarm-names dev-ecs-cpu-utilization
```

## Maintenance

### RDS Backup and Recovery

```bash
# List snapshots
aws rds describe-db-snapshots \
  --db-instance-identifier dev-yawl-db

# Restore from snapshot
aws rds restore-db-instance-from-db-snapshot \
  --db-instance-identifier dev-yawl-db-restored \
  --db-snapshot-identifier <snapshot-id>
```

### Update Container Image

```bash
# Update task definition with new image
terraform apply -var="container_image=<new-image-uri>"
```

### SSL/TLS Certificate Rotation

```bash
# Update certificate ARN
terraform apply -var="ssl_certificate_arn=<new-arn>"
```

## Cost Optimization

### Fargate Spot Instances

Enable cost savings with Spot:

```hcl
fargate_spot_weight = 2  # Use Spot instances
```

### RDS Instance Downsizing

For development environments:

```hcl
rds_instance_class = "db.t3.micro"
rds_multi_az       = false
```

## Cleanup

To destroy all resources:

```bash
terraform destroy
```

**Warning**: This will delete:
- ECS cluster and services
- RDS database (unless deletion protection is enabled)
- ALB and target groups
- VPC and all networking resources
- CloudWatch logs and alarms

## Best Practices

1. **Use Remote State**: Store tfstate in S3 with encryption
2. **Lock Terraform State**: Use DynamoDB for state locking
3. **Separate Environments**: Use different tfvars per environment
4. **Enable MFA Delete**: Protect S3 state bucket
5. **Monitor Costs**: Set up AWS Budgets and Cost Explorer alerts
6. **Regular Backups**: RDS automated backups are configured
7. **Log Retention**: Configure appropriate log retention periods
8. **SSL/TLS**: Use HTTPS for production deployments
9. **Security Groups**: Regularly audit ingress/egress rules
10. **Auto-Scaling**: Monitor auto-scaling metrics and adjust thresholds

## Troubleshooting

### Tasks Failing to Start

```bash
# Check task definition
aws ecs describe-task-definition \
  --task-definition dev-yawl:1

# Check service events
aws ecs describe-services \
  --cluster dev-yawl-cluster \
  --services dev-yawl-service \
  --query 'services[0].events[0:5]'

# Check CloudWatch logs
aws logs tail /ecs/dev-yawl --follow
```

### ALB Not Healthy

```bash
# Check target health
aws elbv2 describe-target-health \
  --target-group-arn <target-group-arn>

# Check health check configuration
aws elbv2 describe-target-groups \
  --target-group-arns <target-group-arn>
```

### Database Connection Issues

```bash
# Check security group rules
aws ec2 describe-security-groups \
  --group-ids <rds-sg-id>

# Test connectivity from ECS task
aws ecs execute-command \
  --cluster dev-yawl-cluster \
  --task <task-id> \
  --container yawl \
  --interactive \
  --command "/bin/sh -c 'psql -h RDS_ENDPOINT -U yawlroot -c \"SELECT 1\"'"
```

## Support and Documentation

- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest)
- [AWS ECS Fargate](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/)
- [AWS RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html)
- [AWS Application Load Balancer](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/)
- [YAWL Project](https://github.com/yawlfoundation/yawl)

## License

This Terraform configuration is provided as part of the YAWL project.
