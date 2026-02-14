# YAWL ECS Fargate Deployment Guide

Complete step-by-step guide for deploying YAWL on AWS ECS Fargate using Terraform.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [AWS Account Setup](#aws-account-setup)
3. [Prepare Container Image](#prepare-container-image)
4. [Terraform Configuration](#terraform-configuration)
5. [Deployment Steps](#deployment-steps)
6. [Post-Deployment Verification](#post-deployment-verification)
7. [Troubleshooting](#troubleshooting)

## Prerequisites

### Software Requirements

- Terraform >= 1.0
- AWS CLI v2
- Docker (for building container images)
- Git
- bash/zsh shell

### Installation

```bash
# Install Terraform
curl -fsSL https://apt.releases.hashicorp.com/gpg | sudo apt-key add -
sudo apt-add-repository "deb [arch=amd64] https://apt.releases.hashicorp.com $(lsb_release -cs) main"
sudo apt-get update && sudo apt-get install terraform

# Install AWS CLI
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip && sudo ./aws/install

# Verify installations
terraform version
aws --version
```

### AWS Permissions

Required IAM permissions for Terraform execution:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ec2:*",
        "rds:*",
        "ecs:*",
        "elasticloadbalancing:*",
        "cloudwatch:*",
        "logs:*",
        "sns:*",
        "iam:*",
        "s3:*",
        "kms:*"
      ],
      "Resource": "*"
    }
  ]
}
```

## AWS Account Setup

### 1. Create IAM User for Terraform

```bash
# Create user
aws iam create-user --user-name yawl-terraform

# Attach policy
aws iam attach-user-policy \
  --user-name yawl-terraform \
  --policy-arn arn:aws:iam::aws:policy/AdministratorAccess

# Create access keys
aws iam create-access-key --user-name yawl-terraform
```

Save the Access Key ID and Secret Access Key for later use.

### 2. Configure AWS CLI

```bash
aws configure
# AWS Access Key ID: <your-access-key>
# AWS Secret Access Key: <your-secret-key>
# Default region: us-east-1
# Default output format: json
```

### 3. Create S3 Bucket for Terraform State (Optional but Recommended)

```bash
# Create bucket
aws s3api create-bucket \
  --bucket yawl-terraform-state-$(date +%s) \
  --region us-east-1 \
  --create-bucket-configuration LocationConstraint=us-east-1

# Enable versioning
aws s3api put-bucket-versioning \
  --bucket yawl-terraform-state-$(date +%s) \
  --versioning-configuration Status=Enabled

# Block public access
aws s3api put-public-access-block \
  --bucket yawl-terraform-state-$(date +%s) \
  --public-access-block-configuration \
  "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

# Enable encryption
aws s3api put-bucket-encryption \
  --bucket yawl-terraform-state-$(date +%s) \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {
        "SSEAlgorithm": "AES256"
      }
    }]
  }'
```

### 4. Create DynamoDB Table for State Locking (Optional)

```bash
aws dynamodb create-table \
  --table-name terraform-locks \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1
```

## Prepare Container Image

### 1. Build Docker Image

```bash
# Navigate to YAWL source directory
cd /path/to/yawl

# Build Docker image
docker build -t yawl:latest -f Dockerfile .

# Verify image
docker images | grep yawl
```

### 2. Create ECR Repository

```bash
# Set variables
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
AWS_REGION=us-east-1
ECR_REPO_NAME=yawl

# Create repository
aws ecr create-repository \
  --repository-name $ECR_REPO_NAME \
  --region $AWS_REGION

# Get repository URI
ECR_URI=$(aws ecr describe-repositories \
  --repository-names $ECR_REPO_NAME \
  --region $AWS_REGION \
  --query 'repositories[0].repositoryUri' \
  --output text)

echo "ECR URI: $ECR_URI"
```

### 3. Push Image to ECR

```bash
# Login to ECR
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Tag image
docker tag yawl:latest $ECR_URI:latest
docker tag yawl:latest $ECR_URI:$(date +%Y%m%d-%H%M%S)

# Push images
docker push $ECR_URI:latest
docker push $ECR_URI:$(date +%Y%m%d-%H%M%S)

# Verify
aws ecr describe-images --repository-name $ECR_REPO_NAME --region $AWS_REGION
```

## Terraform Configuration

### 1. Clone/Copy Terraform Files

```bash
# Copy terraform-aws directory to your workspace
cp -r terraform-aws /path/to/your/workspace/
cd /path/to/your/workspace/terraform-aws
```

### 2. Initialize Terraform

```bash
# Initialize Terraform
terraform init

# If using remote state, uncomment backend in main.tf first
# Then run:
# terraform init -backend-config="bucket=your-bucket" \
#                -backend-config="key=yawl/terraform.tfstate" \
#                -backend-config="region=us-east-1" \
#                -backend-config="dynamodb_table=terraform-locks"
```

### 3. Create terraform.tfvars File

```bash
# Copy example configuration
cp terraform.tfvars.example terraform.tfvars

# Edit with your values
cat > terraform.tfvars << EOF
# AWS Configuration
aws_region     = "us-east-1"
aws_account_id = "$(aws sts get-caller-identity --query Account --output text)"

# Environment
environment = "dev"

# VPC Configuration (customize as needed)
vpc_cidr           = "10.0.0.0/16"
availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]
public_subnets    = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
private_subnets   = ["10.0.11.0/24", "10.0.12.0/24", "10.0.13.0/24"]

# RDS Configuration
rds_allocated_storage      = 100
rds_instance_class         = "db.t3.medium"
rds_engine_version         = "15.3"
rds_master_username        = "yawlroot"
rds_master_password        = "$(openssl rand -base64 12)"
rds_multi_az               = true
rds_backup_retention_period = 7
rds_deletion_protection    = true

# ECS Configuration
container_image  = "$ECR_URI:latest"
container_port   = 8080
task_cpu         = 1024
task_memory      = 2048
desired_count    = 2
min_capacity     = 2
max_capacity     = 10

# ECS Environment Variables
ecs_environment_variables = {
  LOG_LEVEL = "INFO"
  APP_NAME  = "YAWL"
  DEBUG     = "false"
}

# ALB Configuration
alb_target_port           = 8080
health_check_path        = "/"
health_check_matcher     = "200-299"

# Monitoring
enable_monitoring         = true
alarm_email               = "your-email@example.com"
monitoring_log_retention_days = 7

# Tags
tags = {
  Team       = "DevOps"
  CostCenter = "Engineering"
}
EOF
```

### 4. Validate Configuration

```bash
# Validate Terraform syntax
terraform validate

# Format Terraform files
terraform fmt -recursive

# Lint with tflint (optional)
tflint .
```

## Deployment Steps

### 1. Review Terraform Plan

```bash
# Generate execution plan
terraform plan -out=tfplan

# Review the plan
terraform show tfplan

# Check for any warnings or errors
```

### 2. Apply Terraform Configuration

```bash
# Apply with saved plan
terraform apply tfplan

# OR apply directly
terraform apply

# When prompted, type 'yes' to confirm
```

This will:
- Create VPC and networking resources
- Create security groups
- Create RDS database
- Create ECS cluster and service
- Create ALB and target groups
- Create CloudWatch monitoring

### 3. Monitor Deployment Progress

```bash
# Watch ECS service stabilization
watch -n 5 'aws ecs describe-services \
  --cluster dev-yawl-cluster \
  --services dev-yawl-service \
  --query "services[0].[serviceName,status,desiredCount,runningCount]"'

# Check task status
aws ecs list-tasks --cluster dev-yawl-cluster --query 'taskArns[]' | \
  xargs -I {} aws ecs describe-tasks \
  --cluster dev-yawl-cluster \
  --tasks {} \
  --query 'tasks[].[taskArn,lastStatus,containers[0].lastStatus]'
```

## Post-Deployment Verification

### 1. Verify Infrastructure Components

```bash
# Get outputs
terraform output

# Check VPC
aws ec2 describe-vpcs --query 'Vpcs[0].[VpcId,CidrBlock]'

# Check RDS instance
aws rds describe-db-instances \
  --db-instance-identifier dev-yawl-db \
  --query 'DBInstances[0].[DBInstanceIdentifier,DBInstanceStatus,Endpoint]'

# Check ECS cluster
aws ecs describe-clusters \
  --clusters dev-yawl-cluster \
  --query 'clusters[0].[clusterName,status,registeredContainerInstancesCount]'

# Check ALB
aws elbv2 describe-load-balancers \
  --query 'LoadBalancers[0].[LoadBalancerName,State.Code,DNSName]'
```

### 2. Verify Application Health

```bash
# Get ALB DNS name
ALB_DNS=$(terraform output -raw alb_dns_name)

# Test HTTP connectivity
curl -v http://$ALB_DNS/

# Test with longer timeout (app might be starting)
for i in {1..30}; do
  echo "Attempt $i..."
  curl -v http://$ALB_DNS/ && break
  sleep 10
done
```

### 3. Check Application Logs

```bash
# Stream logs
aws logs tail /ecs/dev-yawl --follow

# Search for errors
aws logs filter-log-events \
  --log-group-name /ecs/dev-yawl \
  --filter-pattern "ERROR"
```

### 4. Verify Database Connection

```bash
# Get RDS endpoint
RDS_HOST=$(terraform output -raw rds_address)

# Connect from ECS task (if you have exec enabled)
# Or use bastion host or RDS Proxy for testing

echo "Database endpoint: $RDS_HOST"
```

### 5. Monitor CloudWatch Dashboard

```bash
# Get dashboard URL
terraform output cloudwatch_dashboard_url

# Open in browser to monitor metrics
```

## Troubleshooting

### Tasks Not Starting

```bash
# Check task definition
aws ecs describe-task-definition \
  --task-definition dev-yawl:1 \
  --query 'taskDefinition.[family,revision,status,containerDefinitions]'

# Describe service for events
aws ecs describe-services \
  --cluster dev-yawl-cluster \
  --services dev-yawl-service \
  | jq '.services[0].events[0:10]'

# Check task logs
TASK_ID=$(aws ecs list-tasks --cluster dev-yawl-cluster --query 'taskArns[0]' --output text | cut -d'/' -f3)
aws logs tail /ecs/dev-yawl --follow

# Inspect running task
aws ecs describe-tasks \
  --cluster dev-yawl-cluster \
  --tasks $TASK_ID \
  --query 'tasks[0].[taskArn,lastStatus,stoppedCode,stoppedReason]'
```

### ALB Health Check Failures

```bash
# Check target health
aws elbv2 describe-target-health \
  --target-group-arn $(terraform output -raw target_group_arn) \
  --query 'TargetHealthDescriptions[*].[Target.Id,TargetHealth.State,TargetHealth.Reason]'

# Verify health check configuration
aws elbv2 describe-target-groups \
  --target-group-arns $(terraform output -raw target_group_arn) \
  --query 'TargetGroups[0].[HealthCheckPath,HealthCheckProtocol,HealthCheckIntervalSeconds]'

# Check security groups allow traffic
aws ec2 describe-security-groups \
  --group-ids $(terraform output -raw ecs_tasks_security_group_id) \
  --query 'SecurityGroups[0].IpPermissions'
```

### Database Connection Issues

```bash
# Check RDS security group
aws ec2 describe-security-groups \
  --group-ids $(terraform output -raw rds_security_group_id) \
  --query 'SecurityGroups[0].[GroupId,IpPermissions]'

# Verify RDS is accessible
aws rds describe-db-instances \
  --db-instance-identifier dev-yawl-db \
  --query 'DBInstances[0].[DBInstanceStatus,PubliclyAccessible,VpcSecurityGroups]'

# Check network ACLs
aws ec2 describe-network-acls \
  --filters "Name=vpc-id,Values=$(terraform output -raw vpc_id)" \
  --query 'NetworkAcls[0].Entries[].[RuleNumber,Protocol,PortRange,CidrBlock,Egress]'
```

### Terraform State Issues

```bash
# List Terraform state
terraform state list

# Show specific resource
terraform state show 'module.ecs.aws_ecs_service.main'

# Backup state before making changes
terraform state pull > terraform.tfstate.backup

# If state is corrupted
terraform state rm 'module.ecs.aws_ecs_service.main'
terraform apply  # Will recreate the resource
```

### Cost Issues

```bash
# Estimate costs
terraform estimate
# (Requires Terraform Cloud with cost estimation)

# Review AWS billing
aws ce get-cost-and-usage \
  --time-period Start=2024-01-01,End=2024-01-31 \
  --granularity MONTHLY \
  --metrics BlendedCost
```

## Cleanup

To remove all resources:

```bash
# Review what will be destroyed
terraform plan -destroy

# Destroy resources
terraform destroy

# Verify deletion
aws ec2 describe-vpcs --query 'Vpcs[].VpcId'
aws ecs describe-clusters --query 'clusters[].clusterName'
```

## Next Steps

1. **Configure Custom Domain**: Update ALB with Route53 DNS record
2. **Enable Auto-Scaling**: Customize target metrics and thresholds
3. **Setup Backups**: Configure backup retention and cross-region replication
4. **Implement CI/CD**: Automate deployments with CodePipeline
5. **Add Monitoring**: Configure custom CloudWatch dashboards
6. **Security Hardening**: Enable WAF, shield, and GuardDuty
7. **Disaster Recovery**: Setup multi-region failover

## Support

For issues or questions:
1. Check CloudWatch logs
2. Review Terraform error messages
3. Consult AWS documentation
4. Check YAWL project documentation

## Additional Resources

- [Terraform AWS Provider Documentation](https://registry.terraform.io/providers/hashicorp/aws/latest)
- [AWS ECS Fargate Best Practices](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-best-practices.html)
- [YAWL Documentation](https://github.com/yawlfoundation/yawl/wiki)
