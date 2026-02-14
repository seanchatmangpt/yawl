# YAWL AWS CDK Deployment Guide

Complete step-by-step guide for deploying YAWL infrastructure using AWS CDK.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Initial Setup](#initial-setup)
3. [Environment Configuration](#environment-configuration)
4. [Deployment Steps](#deployment-steps)
5. [Post-Deployment Configuration](#post-deployment-configuration)
6. [Verification](#verification)
7. [Troubleshooting](#troubleshooting)

## Prerequisites

### 1. AWS Account Setup

Ensure you have:
- An AWS account with billing enabled
- Appropriate IAM permissions (ideally administrator or equivalent)
- Access to the AWS Management Console

### 2. Install Required Tools

#### AWS CLI (v2 or higher)
```bash
# macOS
curl "https://awscli.amazonaws.com/AWSCLIV2.pkg" -o "AWSCLIV2.pkg"
sudo installer -pkg AWSCLIV2.pkg -target /

# Linux
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Windows (using chocolatey)
choco install awscli
```

Verify installation:
```bash
aws --version
```

#### Node.js and NPM (v14+)
```bash
# macOS (using homebrew)
brew install node

# Linux (using apt)
sudo apt-get install nodejs npm

# Verify installation
node --version
npm --version
```

#### Python (3.9+)
```bash
# macOS
brew install python@3.11

# Linux
sudo apt-get install python3.11 python3.11-venv

# Windows
# Download from https://www.python.org/

# Verify installation
python3 --version
```

#### AWS CDK CLI
```bash
npm install -g aws-cdk

# Verify installation
cdk --version
```

### 3. Configure AWS Credentials

```bash
aws configure
```

You'll be prompted for:
- AWS Access Key ID
- AWS Secret Access Key
- Default region (e.g., `us-east-1`)
- Default output format (e.g., `json`)

Or set environment variables:
```bash
export AWS_ACCESS_KEY_ID=your_access_key_id
export AWS_SECRET_ACCESS_KEY=your_secret_access_key
export AWS_REGION=us-east-1
```

Verify configuration:
```bash
aws sts get-caller-identity
```

Output should show your AWS account details.

## Initial Setup

### 1. Clone/Navigate to CDK Directory

```bash
cd /home/user/yawl/aws/cdk
```

### 2. Create Python Virtual Environment

```bash
python3 -m venv venv

# Activate virtual environment
# On macOS/Linux:
source venv/bin/activate

# On Windows:
venv\Scripts\activate
```

### 3. Install Dependencies

```bash
pip install --upgrade pip
pip install -r requirements.txt
```

Verify installation:
```bash
python3 -c "import aws_cdk; print(aws_cdk.__version__)"
```

### 4. Bootstrap CDK (One-time per account/region)

This creates the S3 bucket and IAM roles needed by CDK.

```bash
# Get your AWS account ID
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export AWS_REGION=us-east-1

# Bootstrap
cdk bootstrap aws://${AWS_ACCOUNT_ID}/${AWS_REGION}
```

You should see output similar to:
```
✓ Environment aws://123456789012/us-east-1 bootstrapped.
```

## Environment Configuration

### 1. Set Environment Variables

For **Development** deployment:
```bash
export ENVIRONMENT=development
export CONTAINER_IMAGE=public.ecr.aws/docker/library/nginx:latest
export AWS_REGION=us-east-1
```

For **Production** deployment:
```bash
export ENVIRONMENT=production
export CONTAINER_IMAGE=<your-aws-account-id>.dkr.ecr.us-east-1.amazonaws.com/yawl:latest
export AWS_REGION=us-east-1
```

### 2. Verify Configuration

```bash
# Check which stacks will be created
cdk list

# Preview the CloudFormation template
cdk synth

# See what will be deployed
cdk diff
```

## Deployment Steps

### Step 1: Deploy Network Stack (5-10 minutes)

```bash
cdk deploy yawl-${ENVIRONMENT}-network --require-approval=never
```

This creates:
- VPC with subnets
- Internet Gateway
- NAT Gateway
- Security Groups

Wait for completion, then verify:
```bash
aws ec2 describe-vpcs --filters "Name=tag:Name,Values=yawl-vpc-${ENVIRONMENT}"
```

### Step 2: Deploy Database Stack (15-20 minutes)

```bash
cdk deploy yawl-${ENVIRONMENT}-database --require-approval=never
```

This creates:
- RDS PostgreSQL instance
- Database secret in Secrets Manager
- KMS encryption key
- Enhanced monitoring role

Monitor the deployment:
```bash
aws cloudformation describe-stack-resources --stack-name yawl-${ENVIRONMENT}-database --query 'StackResources[?ResourceStatus==`CREATE_IN_PROGRESS`]'
```

### Step 3: Deploy Cache Stack (5-10 minutes)

```bash
cdk deploy yawl-${ENVIRONMENT}-cache --require-approval=never
```

This creates:
- ElastiCache Redis cluster
- Subnet group for Redis
- CloudWatch log group

### Step 4: Deploy Storage Stack (5-10 minutes)

```bash
cdk deploy yawl-${ENVIRONMENT}-storage --require-approval=never
```

This creates:
- S3 backup bucket
- S3 static content bucket
- S3 artifacts bucket
- Lifecycle policies and versioning

Verify buckets were created:
```bash
aws s3 ls | grep yawl
```

### Step 5: Deploy ECS Stack (10-15 minutes)

```bash
cdk deploy yawl-${ENVIRONMENT}-ecs --require-approval=never
```

This creates:
- ECS Fargate cluster
- Application Load Balancer
- CloudWatch Logs
- Auto-scaling configuration

Monitor deployment:
```bash
aws ecs describe-clusters --clusters yawl-cluster-${ENVIRONMENT}
```

### Step 6: Deploy CloudFront Distribution (5 minutes)

```bash
cdk deploy yawl-${ENVIRONMENT}-distribution --require-approval=never
```

This creates:
- CloudFront distribution
- Origin Access Control for S3
- CloudFront logs bucket

Verify distribution:
```bash
aws cloudfront list-distributions --query "DistributionList.Items[?Comment=='YAWL Distribution - ${ENVIRONMENT}']"
```

### Step 7: Deploy Monitoring Stack (5 minutes)

```bash
cdk deploy yawl-${ENVIRONMENT}-monitoring --require-approval=never
```

This creates:
- CloudWatch alarms
- SNS topic for notifications
- CloudWatch dashboard

### Deploy All Stacks at Once

After your first deployment, you can deploy all stacks together:

```bash
cdk deploy --require-approval=never
```

## Post-Deployment Configuration

### 1. Subscribe to Alarms

```bash
# Get SNS topic ARN
export TOPIC_ARN=$(aws sns list-topics --query "Topics[?contains(TopicArn, 'yawl-alarms-${ENVIRONMENT}')].TopicArn" --output text)

# Subscribe email
aws sns subscribe \
  --topic-arn ${TOPIC_ARN} \
  --protocol email \
  --notification-endpoint your-email@example.com

echo "Check your email to confirm SNS subscription"
```

### 2. Get Database Credentials

```bash
# Get secret ARN
export SECRET_ARN=$(aws secretsmanager list-secrets --query "SecretList[?contains(Name, 'YawlDatabase')].ARN" --output text)

# Retrieve password
aws secretsmanager get-secret-value --secret-id ${SECRET_ARN} --query SecretString --output text | jq .
```

### 3. Initialize Database

```bash
# Get database endpoint
export DB_ENDPOINT=$(aws rds describe-db-instances \
  --db-instance-identifier yawl-db-${ENVIRONMENT} \
  --query 'DBInstances[0].Endpoint.Address' \
  --output text)

# Get database password from Secrets Manager
export DB_PASSWORD=$(aws secretsmanager get-secret-value \
  --secret-id yawl/YawlDatabaseSecret \
  --query SecretString \
  --output text | jq -r '.password')

# Connect to database (requires psql installed)
# psql -h ${DB_ENDPOINT} -U yawlmaster -d yawl -W
```

### 4. Configure Container Registry (Production Only)

If using your own container image:

```bash
# Create ECR repository
aws ecr create-repository --repository-name yawl --region ${AWS_REGION}

# Get repository URI
export REPO_URI=$(aws ecr describe-repositories \
  --repository-names yawl \
  --query 'repositories[0].repositoryUri' \
  --output text)

# Build and push image
docker build -t ${REPO_URI}:latest .
aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${REPO_URI}
docker push ${REPO_URI}:latest

# Update ECS task definition
export CONTAINER_IMAGE=${REPO_URI}:latest
cdk deploy yawl-${ENVIRONMENT}-ecs --require-approval=never
```

### 5. Configure Domain (Optional)

#### Using Route53:

```bash
# Create or update Route53 record
export CF_DOMAIN=$(aws cloudformation describe-stacks \
  --stack-name yawl-${ENVIRONMENT}-distribution \
  --query 'Stacks[0].Outputs[?OutputKey==`CloudFrontDomain`].OutputValue' \
  --output text)

aws route53 change-resource-record-sets \
  --hosted-zone-id YOUR_ZONE_ID \
  --change-batch '{
    "Changes": [{
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "yawl.example.com",
        "Type": "CNAME",
        "TTL": 300,
        "ResourceRecords": [{"Value": "'${CF_DOMAIN}'"}]
      }
    }]
  }'
```

#### Using external DNS provider:

Create a CNAME record pointing to the CloudFront domain:
- Name: `yawl.example.com`
- Type: CNAME
- Value: (CloudFront domain name from stack outputs)

## Verification

### 1. Check Stack Status

```bash
# List all stacks
aws cloudformation list-stacks \
  --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE \
  --query "StackSummaries[?contains(StackName, 'yawl-${ENVIRONMENT}')].StackName"
```

### 2. Verify Network

```bash
# Check VPC
aws ec2 describe-vpcs --filters "Name=tag:Name,Values=yawl-vpc-${ENVIRONMENT}"

# Check subnets
aws ec2 describe-subnets --filters "Name=vpc-id,Values=<vpc-id>"

# Check security groups
aws ec2 describe-security-groups --filters "Name=vpc-id,Values=<vpc-id>"
```

### 3. Verify Database

```bash
# Check RDS instance status
aws rds describe-db-instances \
  --db-instance-identifier yawl-db-${ENVIRONMENT} \
  --query 'DBInstances[0].[DBInstanceStatus,MultiAZ,StorageEncrypted]'
```

### 4. Verify ECS

```bash
# Check cluster
aws ecs describe-clusters --clusters yawl-cluster-${ENVIRONMENT}

# Check service
aws ecs describe-services \
  --cluster yawl-cluster-${ENVIRONMENT} \
  --services yawl-service-${ENVIRONMENT} \
  --query 'services[0].[ServiceName,DesiredCount,RunningCount]'

# Check tasks
aws ecs list-tasks --cluster yawl-cluster-${ENVIRONMENT}
```

### 5. Verify Load Balancer

```bash
# Get ALB DNS
export ALB_DNS=$(aws cloudformation describe-stacks \
  --stack-name yawl-${ENVIRONMENT}-ecs \
  --query 'Stacks[0].Outputs[?OutputKey==`LoadBalancerDNS`].OutputValue' \
  --output text)

# Test connectivity
curl -I http://${ALB_DNS}
```

### 6. Verify CloudFront

```bash
# Get distribution domain
export CF_DOMAIN=$(aws cloudformation describe-stacks \
  --stack-name yawl-${ENVIRONMENT}-distribution \
  --query 'Stacks[0].Outputs[?OutputKey==`CloudFrontDomain`].OutputValue' \
  --output text)

# Test connectivity
curl -I https://${CF_DOMAIN}
```

### 7. Check CloudWatch Dashboard

```bash
echo "https://console.aws.amazon.com/cloudwatch/home?region=${AWS_REGION}#dashboards:name=yawl-infrastructure-${ENVIRONMENT}"
```

## Troubleshooting

### Deployment Fails: Access Denied

**Solution**: Verify IAM permissions
```bash
aws iam get-user --query 'User.Arn'
# Ensure you have permissions for EC2, RDS, ECS, S3, CloudFront, etc.
```

### Deployment Fails: CDK Bootstrap Not Found

**Solution**: Run bootstrap
```bash
cdk bootstrap aws://${AWS_ACCOUNT_ID}/${AWS_REGION}
```

### ECS Tasks Not Running

```bash
# Check task definition
aws ecs describe-task-definition \
  --task-definition yawl-task-${ENVIRONMENT} \
  --query 'taskDefinition.containerDefinitions[0].image'

# Check logs
aws logs tail /ecs/yawl/${ENVIRONMENT} --follow

# Check task status
aws ecs describe-tasks \
  --cluster yawl-cluster-${ENVIRONMENT} \
  --tasks $(aws ecs list-tasks --cluster yawl-cluster-${ENVIRONMENT} --query 'taskArns[0]' --output text) \
  --query 'tasks[0].lastStatus'
```

### No Healthy Targets in ALB

```bash
# Check target group health
export TG_ARN=$(aws elbv2 describe-target-groups \
  --load-balancer-arn $(aws elbv2 describe-load-balancers \
    --names yawl-alb-${ENVIRONMENT} \
    --query 'LoadBalancers[0].LoadBalancerArn' \
    --output text) \
  --query 'TargetGroups[0].TargetGroupArn' \
  --output text)

aws elbv2 describe-target-health --target-group-arn ${TG_ARN}

# Check security group rules
aws ec2 describe-security-groups \
  --group-ids $(aws ec2 describe-security-groups \
    --filters "Name=group-name,Values=*ECS*" \
    --query 'SecurityGroups[0].GroupId' \
    --output text)
```

### Database Connection Refused

```bash
# Verify security group
aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=*Database*"

# Verify RDS is running
aws rds describe-db-instances \
  --db-instance-identifier yawl-db-${ENVIRONMENT} \
  --query 'DBInstances[0].DBInstanceStatus'

# Check RDS logs
aws logs tail /aws/rds/instance/yawl-db-${ENVIRONMENT}/postgresql --follow
```

### High Costs

1. Check running instances:
   ```bash
   aws ec2 describe-instances --filters "Name=instance-state-name,Values=running"
   ```

2. Review RDS instance class:
   ```bash
   aws rds describe-db-instances --query 'DBInstances[0].DBInstanceClass'
   ```

3. Check data transferred:
   ```bash
   aws cloudwatch get-metric-statistics \
     --namespace AWS/ApplicationELB \
     --metric-name ProcessedBytes \
     --start-time $(date -u -d '30 days ago' +%Y-%m-%dT%H:%M:%S) \
     --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
     --period 86400 \
     --statistics Sum
   ```

## Next Steps

After successful deployment:

1. **Configure Monitoring**: Set up additional CloudWatch alarms or integrate with third-party monitoring
2. **Implement Backups**: Set up automated backup procedures and test restore
3. **Security Hardening**:
   - Enable WAF on CloudFront
   - Configure VPC Flow Logs
   - Enable CloudTrail logging
4. **Performance Optimization**: Run load tests and optimize capacity
5. **Documentation**: Document your specific deployment details and procedures

## Getting Help

- Check CloudFormation events: `aws cloudformation describe-stack-events --stack-name <stack-name>`
- Review CloudWatch Logs: `aws logs describe-log-groups`
- Check AWS Status: https://status.aws.amazon.com/
- AWS Support: https://console.aws.amazon.com/support/
