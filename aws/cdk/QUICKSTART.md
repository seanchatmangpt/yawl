# YAWL AWS CDK - Quick Start Guide

Get your YAWL infrastructure running on AWS in 10 minutes.

## Prerequisites (5 minutes)

### 1. Install Tools

```bash
# AWS CLI v2
curl "https://awscli.amazonaws.com/AWSCLIV2.pkg" -o "AWSCLIV2.pkg"
sudo installer -pkg AWSCLIV2.pkg -target /

# Node.js (for CDK)
brew install node  # macOS
# or visit https://nodejs.org/

# Python 3.9+
brew install python@3.11  # macOS
# or visit https://www.python.org/

# AWS CDK
npm install -g aws-cdk
```

### 2. Configure AWS

```bash
aws configure
# Enter: Access Key ID
# Enter: Secret Access Key
# Enter: Default region (e.g., us-east-1)
# Enter: Default output format (json)

# Verify
aws sts get-caller-identity
```

## Deploy (5 minutes)

### Step 1: Setup

```bash
cd /home/user/yawl/aws/cdk

# Create and activate virtual environment
python3 -m venv venv
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Bootstrap CDK
cdk bootstrap
```

### Step 2: Deploy

**Development**:
```bash
export ENVIRONMENT=development
cdk deploy --require-approval=never
```

**Production**:
```bash
export ENVIRONMENT=production
export CONTAINER_IMAGE=<your-account>.dkr.ecr.us-east-1.amazonaws.com/yawl:latest
cdk deploy --require-approval=never
```

Or use the helper script:
```bash
./deploy.sh deploy production
```

### Step 3: Verify

```bash
# Check deployment status
aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE

# Get load balancer URL
./deploy.sh outputs production

# Test access
curl http://<ALB-DNS>/resourceService/
```

## After Deployment

### Subscribe to Alarms

```bash
./deploy.sh outputs production  # Get SNS Topic ARN
# Open email and confirm subscription
```

### Initialize Database

```bash
# Get database endpoint
aws rds describe-db-instances \
  --query 'DBInstances[?contains(DBInstanceIdentifier, `yawl`)].Endpoint.Address' \
  --output text
```

### Configure Domain

Add DNS CNAME record:
```
Name: yawl.example.com
Type: CNAME
Value: <CloudFront-Domain>
```

## Common Commands

```bash
# View infrastructure
./deploy.sh status production

# See what changed
./deploy.sh diff production

# Tail logs
./deploy.sh logs production

# Clean up
./deploy.sh cleanup production
```

## Architecture

```
CloudFront CDN
    ↓
Application Load Balancer
    ↓
ECS Fargate Cluster (2-6 tasks)
    ↓
┌───────────────┬──────────────┐
RDS PostgreSQL  Redis Cache    S3 Storage
(Multi-AZ)      (Multi-AZ)     (Backups)
```

## Key Outputs

After deployment, key resources are created:

- **Load Balancer**: `http://yawl-alb-<env>-<hash>.elb.amazonaws.com`
- **Database**: `yawl-db-<env>.<hash>.rds.amazonaws.com:5432`
- **Cache**: `yawl-redis-<env>.<hash>.cache.amazonaws.com:6379`
- **CloudFront**: `d<hash>.cloudfront.net`
- **S3 Buckets**: `yawl-backups-<account>-<env>`, `yawl-static-<account>-<env>`

## Troubleshooting

### Stack Creation Failed
```bash
# Check error details
aws cloudformation describe-stack-events \
  --stack-name yawl-production-network \
  --query 'StackEvents[?ResourceStatus==`CREATE_FAILED`]'
```

### No Healthy Targets
```bash
# Check ECS tasks
aws ecs describe-services \
  --cluster yawl-cluster-production \
  --services yawl-service-production

# Check logs
aws logs tail /ecs/yawl/production --follow
```

### Can't Connect to Database
```bash
# Verify security group allows ECS → RDS
aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=*Database*"

# Check RDS status
aws rds describe-db-instances \
  --db-instance-identifier yawl-db-production
```

## Cost Estimate

**Development** (~$150-200/month):
- ECS: 1 task × $0.045/hour
- RDS: db.t3.medium
- Redis: cache.t3.micro
- Storage: Minimal

**Production** (~$400-600/month):
- ECS: 2-6 tasks × $0.045/hour
- RDS: db.m5.large with Multi-AZ
- Redis: cache.t3.small with Multi-AZ
- CloudFront: ~$50/month for typical traffic
- Storage: ~$50-100/month

## Next Steps

1. **Read Documentation**:
   - [README.md](README.md) - Full documentation
   - [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) - Detailed deployment instructions
   - [CONFIGURATION.md](CONFIGURATION.md) - Configuration options
   - [BEST_PRACTICES.md](BEST_PRACTICES.md) - Architecture and best practices

2. **Configure Application**:
   - Update container image to your YAWL build
   - Configure database connection parameters
   - Set environment variables

3. **Setup Monitoring**:
   - Subscribe to SNS alerts
   - Create custom CloudWatch dashboards
   - Integrate with your monitoring system

4. **Implement Security**:
   - Enable WAF for CloudFront
   - Configure VPC Flow Logs
   - Setup CloudTrail logging
   - Rotate secrets regularly

5. **Optimize Costs**:
   - Monitor CloudWatch metrics
   - Right-size instances as needed
   - Consider Reserved Instances for RDS
   - Optimize CloudFront caching

## Support

- **AWS Documentation**: https://docs.aws.amazon.com/cdk/
- **YAWL Documentation**: http://www.yawlfoundation.org/
- **AWS Support**: https://console.aws.amazon.com/support/

## Full Documentation Index

- `README.md` - Complete infrastructure documentation
- `DEPLOYMENT_GUIDE.md` - Step-by-step deployment guide
- `CONFIGURATION.md` - Configuration and customization
- `BEST_PRACTICES.md` - Architecture and operational best practices
- `QUICKSTART.md` - This quick start guide
- `app.py` - Main CDK application
- `stacks.py` - Infrastructure stack definitions
- `requirements.txt` - Python dependencies
- `cdk.json` - CDK configuration
- `deploy.sh` - Deployment helper script

---

**Total Setup Time**: ~15 minutes
**First Deployment Time**: 20-30 minutes
**Cost**: Free tier eligible, ~$200-500/month for production
