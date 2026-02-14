# YAWL AWS CDK Infrastructure

This directory contains a production-ready AWS CDK application for deploying the YAWL Workflow Engine infrastructure on AWS.

## Architecture Overview

The CDK application creates the following AWS resources:

### Network Infrastructure (YAWLNetworkStack)
- VPC with CIDR 10.0.0.0/16
- 3 subnet types across 2 Availability Zones:
  - Public subnets (10.0.1.0/24, 10.0.2.0/24) for ALB
  - Private subnets with NAT (10.0.3.0/24, 10.0.4.0/24) for ECS
  - Isolated subnets (10.0.10.0/24, 10.0.11.0/24) for RDS
- Internet Gateway for outbound internet access
- NAT Gateway for private subnet egress
- Security groups for ALB, ECS, RDS, and Redis

### Database Infrastructure (YAWLDatabaseStack)
- RDS PostgreSQL 15.4 instance
- Multi-AZ deployment (production only)
- Automated backups with 30-day retention
- KMS encryption at rest
- Enhanced monitoring
- IAM database authentication
- CloudWatch logs exports
- Secrets Manager for credential rotation

### Cache Infrastructure (YAWLCacheStack)
- ElastiCache Redis 7.0 cluster
- Multi-AZ and automatic failover (production only)
- Encryption in transit and at rest
- Auth token enabled
- CloudWatch logs for debugging

### ECS Fargate Infrastructure (YAWLECSStack)
- ECS Fargate cluster with Container Insights monitoring
- Fargate task definition (2GB memory, 1024 CPU)
- Application Load Balancer with health checks
- Auto-scaling based on CPU (70%) and memory (80%) utilization
- Min: 2 tasks (production), 1 task (development)
- Max: 6 tasks (production), 2 tasks (development)
- CloudWatch Logs integration
- IAM roles with least privilege

### Storage Infrastructure (YAWLStorageStack)
- Backup S3 bucket with Glacier transition after 30 days
- Static content S3 bucket with CloudFront access
- Artifacts bucket for build artifacts
- Versioning enabled on all buckets
- Server-side KMS encryption
- Block Public Access policies
- CORS configuration for static content

### Content Delivery (YAWLDistributionStack)
- CloudFront distribution with multiple origins
- S3 Origin Access Control (OAC) for secure S3 access
- ALB as origin for dynamic content
- Caching policies optimized for different content types
- Request logging to S3

### Monitoring and Alarms (YAWLMonitoringStack)
- CloudWatch alarms for:
  - ALB response time and request count
  - ECS CPU and memory utilization
  - RDS CPU, connections, and free memory
  - Target health status
- SNS topic for alert notifications
- CloudWatch Dashboard with key metrics
- Alarm evaluation periods for reliability

## Prerequisites

1. **AWS Account**: You must have an AWS account with appropriate permissions
2. **AWS CLI**: Install and configure the AWS CLI
   ```bash
   aws configure
   ```
3. **Node.js**: Version 14 or higher
4. **Python**: Version 3.9 or higher
5. **AWS CDK**: Install the CDK CLI
   ```bash
   npm install -g aws-cdk
   ```

## Setup

### 1. Install Python Dependencies

```bash
cd /home/user/yawl/aws/cdk
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### 2. Bootstrap CDK (One-time per account/region)

```bash
export AWS_REGION=us-east-1
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

cdk bootstrap aws://${AWS_ACCOUNT_ID}/${AWS_REGION}
```

### 3. Deploy Infrastructure

#### Development Environment
```bash
export ENVIRONMENT=development
export CONTAINER_IMAGE=public.ecr.aws/docker/library/nginx:latest
cdk deploy --require-approval=never
```

#### Production Environment
```bash
export ENVIRONMENT=production
export CONTAINER_IMAGE=<your-ecr-image-uri>
cdk deploy --require-approval=never
```

#### Deploy Specific Stacks
```bash
cdk deploy yawl-production-network yawl-production-database --require-approval=never
```

## Configuration

### Environment Variables

- **ENVIRONMENT**: Deployment environment (development, staging, production)
  - Default: `production`
  - Affects instance types, Multi-AZ, auto-scaling limits

- **CONTAINER_IMAGE**: Docker image URI for YAWL container
  - Default: `public.ecr.aws/docker/library/nginx:latest`
  - Use your own ECR repository URL for production

- **AWS_REGION**: AWS region for deployment
  - Default: `us-east-1`
  - Example: `us-west-2`, `eu-west-1`

- **AWS_ACCOUNT_ID**: AWS Account ID (optional, auto-detected if not set)

### Instance Types by Environment

| Component | Development | Staging | Production |
|-----------|-------------|---------|------------|
| RDS | db.t3.medium | db.t3.large | db.m5.large |
| ECS Desired | 1 | 2 | 2 |
| ECS Max | 2 | 4 | 6 |
| Redis | cache.t3.micro | cache.t3.small | cache.t3.small |
| Multi-AZ | No | No | Yes |

## Post-Deployment

### 1. Configure SNS Subscriptions

Subscribe to the SNS topic for alarm notifications:

```bash
export TOPIC_ARN=$(aws sns list-topics --query 'Topics[?contains(TopicArn, `yawl-alarms`)].TopicArn' --output text)
aws sns subscribe --topic-arn ${TOPIC_ARN} --protocol email --notification-endpoint your-email@example.com
```

### 2. Upload Initial Database Schema

Connect to the RDS instance and initialize the database:

```bash
# Get the database endpoint
export DB_ENDPOINT=$(aws rds describe-db-instances --query 'DBInstances[?contains(DBInstanceIdentifier, `yawl`)].Endpoint.Address' --output text)

# Connect and run initialization SQL
psql -h ${DB_ENDPOINT} -U yawlmaster -d yawl -f init-db.sql
```

### 3. Update ECS Task Definition

Replace the default NGINX image with your actual YAWL container:

```bash
export CONTAINER_IMAGE=<your-account>.dkr.ecr.<region>.amazonaws.com/yawl:latest
cdk deploy yawl-production-ecs --require-approval=never
```

### 4. Configure Domain (Optional)

Create a Route53 record or update your DNS to point to the CloudFront distribution or ALB:

```bash
# Get CloudFront domain
export CF_DOMAIN=$(aws cloudformation describe-stacks --query 'Stacks[?contains(StackName, `distribution`)].Outputs[?OutputKey==`CloudFrontDomain`].OutputValue' --output text)
echo "CloudFront Domain: ${CF_DOMAIN}"

# Get ALB domain
export ALB_DOMAIN=$(aws cloudformation describe-stacks --query 'Stacks[?contains(StackName, `ecs`)].Outputs[?OutputKey==`LoadBalancerDNS`].OutputValue' --output text)
echo "ALB Domain: ${ALB_DOMAIN}"
```

## Management Commands

### View Stack Status
```bash
aws cloudformation describe-stacks --region ${AWS_REGION} --query 'Stacks[?contains(StackName, `yawl`)].{Name:StackName,Status:StackStatus}'
```

### View Outputs
```bash
aws cloudformation describe-stacks --region ${AWS_REGION} --query 'Stacks[?contains(StackName, `yawl`)].Outputs' --output table
```

### Tail Logs
```bash
# ECS logs
aws logs tail /ecs/yawl/production --follow

# RDS logs
aws logs tail /aws/rds/instance/<instance-id>/postgresql --follow
```

### Scale ECS Service
```bash
# Set desired count to 4
aws ecs update-service --cluster yawl-cluster-production --service yawl-service-production --desired-count 4
```

### Update Auto-scaling Policy
```bash
# View current scaling policy
aws application-autoscaling describe-scaling-policies \
  --service-namespace ecs \
  --resource-id service/yawl-cluster-production/yawl-service-production
```

## Monitoring

### CloudWatch Dashboard

Access the CloudWatch dashboard:
```bash
export REGION=${AWS_REGION:-us-east-1}
echo "https://console.aws.amazon.com/cloudwatch/home?region=${REGION}#dashboards:name=yawl-infrastructure-production"
```

### Key Metrics to Monitor

1. **ALB Metrics**
   - Target Response Time: Should be < 1 second
   - Healthy Host Count: Should be > 0
   - Request Count: Monitor for unusual spikes

2. **ECS Metrics**
   - CPU Utilization: Target < 70%
   - Memory Utilization: Target < 80%
   - Task Count: Ensure desired == running

3. **RDS Metrics**
   - CPU Utilization: Target < 80%
   - Database Connections: Monitor for leaks
   - Freeable Memory: Should remain healthy
   - Read/Write Latency: Monitor for performance

4. **Cache Metrics**
   - CPU Utilization
   - Evictions
   - Connection Count

## Backup and Disaster Recovery

### Database Backups
- Automated backups: 30-day retention
- Multi-AZ failover: Automatic in production
- Manual snapshot: `aws rds create-db-snapshot --db-instance-identifier yawl-db-production`

### S3 Backups
- Backup bucket has versioning enabled
- Lifecycle policies move old versions to Glacier
- Enable S3 Cross-Region Replication for DR:
  ```bash
  aws s3api put-bucket-replication --bucket yawl-backups-<account>-production --replication-configuration file://replication.json
  ```

### Recovery Procedures
1. **RDS**: Restore from automated backup or snapshot
2. **S3**: Recover from versioning or cross-region replica
3. **ECS**: Automatic recovery through auto-scaling and load balancer health checks

## Troubleshooting

### Stack Creation Failed
```bash
# View events for debugging
aws cloudformation describe-stack-events --stack-name yawl-production-network --query 'StackEvents[?ResourceStatus==`CREATE_FAILED`]'
```

### No Healthy Targets
1. Check ECS task health:
   ```bash
   aws ecs describe-tasks --cluster yawl-cluster-production --tasks $(aws ecs list-tasks --cluster yawl-cluster-production --query 'taskArns' --output text)
   ```
2. Check ALB target group health:
   ```bash
   aws elbv2 describe-target-health --target-group-arn <tg-arn>
   ```
3. Check security group rules

### Database Connection Issues
1. Verify security group rules allow ECS SG to access DB SG
2. Check database credentials in Secrets Manager
3. Verify ECS task has IAM permission to read secret

### High CPU/Memory
1. Check ECS task metrics in CloudWatch
2. Increase task CPU/memory if needed
3. Verify container is not misconfigured

## Cost Optimization

1. **Development**: Use smaller instance types (t3.micro for Redis, t3.medium for RDS)
2. **Reserved Instances**: Purchase RIs for stable workloads
3. **Savings Plans**: Consider compute savings plans for ECS Fargate
4. **S3 Storage Classes**: Move old backups to Glacier/Deep Archive
5. **CloudFront**: Benefits from caching to reduce ALB load

## Security Best Practices

1. **Secrets**: Rotate database password regularly in Secrets Manager
2. **Network**: Use VPC endpoints for AWS services to avoid internet exposure
3. **Encryption**: All data encrypted at rest (KMS) and in transit (TLS)
4. **IAM**: Review IAM roles and policies periodically
5. **Monitoring**: Set up CloudTrail for API audit logging
6. **Backups**: Test restore procedures regularly

## Cleanup

### Delete Everything
```bash
cdk destroy --require-approval=never
```

### Delete Specific Stack
```bash
cdk destroy yawl-production-ecs --require-approval=never
```

**WARNING**: This will delete all resources and data. S3 buckets with versioning enabled must be emptied first.

## Updating Infrastructure

### Update Configuration
Edit `app.py` or `stacks.py` and redeploy:
```bash
cdk diff  # Preview changes
cdk deploy --require-approval=never  # Apply changes
```

### Update Container Image
```bash
export CONTAINER_IMAGE=<new-image-uri>
cdk deploy yawl-production-ecs --require-approval=never
```

## CDK Commands Reference

```bash
# List all stacks
cdk list

# Synthesize CloudFormation template
cdk synth

# Preview changes
cdk diff

# Deploy all stacks
cdk deploy

# Deploy specific stack
cdk deploy yawl-production-network

# Destroy all stacks
cdk destroy

# View documentation
cdk docs
```

## Contributing

When modifying the CDK infrastructure:
1. Update the relevant stack in `stacks.py`
2. Test locally: `cdk synth` and `cdk diff`
3. Deploy to development first: `ENVIRONMENT=development cdk deploy`
4. Verify functionality
5. Deploy to production: `ENVIRONMENT=production cdk deploy`

## Support and Documentation

- [AWS CDK Documentation](https://docs.aws.amazon.com/cdk/)
- [AWS CDK Python Reference](https://docs.aws.amazon.com/cdk/api/latest/python/)
- [YAWL Documentation](http://www.yawlfoundation.org/)
- [AWS Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/)

## License

Same as YAWL project license
