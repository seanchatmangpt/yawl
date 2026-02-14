# YAWL ECS Fargate on AWS - START HERE

Welcome! This directory contains production-ready Terraform infrastructure for deploying YAWL on AWS ECS Fargate.

## What You Have

A complete, modular Terraform infrastructure with:
- 5 reusable modules (VPC, RDS, ECS, ALB, Monitoring)
- Multi-AZ high availability configuration
- Auto-scaling, monitoring, and disaster recovery
- Comprehensive documentation
- Quick-start automation

## In 5 Minutes

### Option 1: Automated Setup
```bash
chmod +x quickstart.sh
./quickstart.sh
```

### Option 2: Manual Setup
1. Copy `terraform.tfvars.example` to `terraform.tfvars`
2. Edit `terraform.tfvars` with your AWS details
3. Run:
   ```bash
   terraform init
   terraform plan
   terraform apply
   ```

## Documentation Map

```
Start Here (This file)
  ↓
README.md (Architecture & Configuration)
  ↓
Choose your path:
  ├─ Deploy? → DEPLOYMENT.md
  ├─ Troubleshoot? → TROUBLESHOOTING.md
  ├─ Understand architecture? → STRUCTURE.md
  ├─ Need help navigating? → INDEX.md
  └─ Quick reference? → SUMMARY.txt
```

## File Organization

```
terraform-aws/
├─ 00-START-HERE.md          ← You are here
├─ README.md                 ← Read this next
├─ DEPLOYMENT.md             ← Follow this to deploy
├─ STRUCTURE.md              ← Understand the design
├─ TROUBLESHOOTING.md        ← Fix problems
├─ INDEX.md                  ← Navigate all files
├─ SUMMARY.txt               ← Quick reference
├─ quickstart.sh             ← Automate setup
│
├─ main.tf                   ← Main configuration
├─ variables.tf              ← Input variables
├─ outputs.tf                ← Output values
├─ providers.tf              ← AWS provider setup
├─ terraform.tfvars.example  ← Config template
│
├─ vpc/                      ← VPC module (networking)
│  ├─ main.tf
│  ├─ variables.tf
│  └─ outputs.tf
│
├─ rds/                      ← RDS module (database)
│  ├─ main.tf
│  ├─ variables.tf
│  └─ outputs.tf
│
├─ ecs/                      ← ECS module (containers)
│  ├─ main.tf
│  ├─ variables.tf
│  └─ outputs.tf
│
├─ alb/                      ← ALB module (load balancer)
│  ├─ main.tf
│  ├─ variables.tf
│  └─ outputs.tf
│
└─ monitoring/               ← Monitoring module
   ├─ main.tf
   ├─ variables.tf
   └─ outputs.tf
```

## Quick Facts

| Aspect | Details |
|--------|---------|
| **Cloud Provider** | AWS |
| **Container Platform** | ECS Fargate |
| **Database** | PostgreSQL RDS |
| **Load Balancer** | Application Load Balancer |
| **Infrastructure Code** | 19 Terraform files, ~2,500 lines |
| **Documentation** | 6 guides, ~3,350 lines |
| **Modules** | 5 (VPC, RDS, ECS, ALB, Monitoring) |
| **AWS Resources** | ~50+ resources |
| **Availability** | Multi-AZ (3 zones) |
| **Scaling** | Auto-scaling ECS tasks (2-10) |
| **Monitoring** | CloudWatch, SNS, custom dashboards |

## Before You Start

You need:
1. **AWS Account** with permissions to create VPC, RDS, ECS, ALB resources
2. **AWS CLI** configured with credentials
3. **Terraform** >= 1.0 installed
4. **Docker image** of YAWL ready to push to ECR
5. (Optional) **SSL certificate** in ACM for HTTPS

## Getting Your ECR Image Ready

```bash
# Build your YAWL image
docker build -t yawl:latest .

# Create ECR repo
aws ecr create-repository --repository-name yawl --region us-east-1

# Login and push
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com

docker tag yawl:latest ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/yawl:latest
docker push ACCOUNT_ID.dkr.ecr.us-east-1.amazonaws.com/yawl:latest
```

## Three Deployment Paths

### Path 1: Automated (Easiest)
```bash
./quickstart.sh
# Follows you through the entire setup
```

### Path 2: Step-by-Step (Recommended)
1. Read [README.md](./README.md) - 5 minutes
2. Follow [DEPLOYMENT.md](./DEPLOYMENT.md) - 30 minutes
3. Deploy with confidence

### Path 3: Manual (Full Control)
```bash
# Initialize
terraform init

# Configure
cp terraform.tfvars.example terraform.tfvars
vim terraform.tfvars  # Edit your values

# Review
terraform plan -out=tfplan

# Deploy
terraform apply tfplan

# Verify
terraform output
```

## After Deployment

Your infrastructure is ready when:

1. **ECS Service Running**
   ```bash
   aws ecs describe-services \
     --cluster $(terraform output -raw ecs_cluster_name) \
     --services $(terraform output -raw ecs_service_name)
   ```

2. **Load Balancer Healthy**
   ```bash
   aws elbv2 describe-target-health \
     --target-group-arn $(terraform output -raw target_group_arn)
   ```

3. **Application Accessible**
   ```bash
   curl http://$(terraform output -raw alb_dns_name)
   ```

4. **Database Connected**
   ```bash
   # Check logs
   aws logs tail /ecs/$(terraform output -raw environment)-yawl --follow
   ```

## Accessing Your Application

```bash
# Get the URL
terraform output application_url

# Open in browser
# http://your-alb-dns-name
```

## Monitoring Your Infrastructure

```bash
# View CloudWatch dashboard
open "$(terraform output -raw cloudwatch_dashboard_url)"

# Watch application logs
aws logs tail /ecs/dev-yawl --follow

# Check service status
aws ecs describe-services \
  --cluster dev-yawl-cluster \
  --services dev-yawl-service
```

## Common Commands

```bash
# View all infrastructure details
terraform output

# Get specific values
terraform output -raw alb_dns_name          # Application URL
terraform output -raw rds_address           # Database host
terraform output application_url            # Full HTTP URL

# Deploy changes
terraform plan
terraform apply

# Scale the application
terraform apply -var="desired_count=5"      # Scale to 5 tasks

# Increase database size
terraform apply -var="rds_instance_class=db.t3.large"

# Destroy everything
terraform destroy
```

## Troubleshooting

**Tasks won't start?**
→ Check [TROUBLESHOOTING.md](./TROUBLESHOOTING.md#ecs-containers)

**Database not connecting?**
→ Check [TROUBLESHOOTING.md](./TROUBLESHOOTING.md#rds-database)

**Load balancer unhealthy?**
→ Check [TROUBLESHOOTING.md](./TROUBLESHOOTING.md#load-balancer)

**Terraform errors?**
→ Check [TROUBLESHOOTING.md](./TROUBLESHOOTING.md#terraform-issues)

## What Gets Created

### Networking ($)
- 1 VPC with public/private subnets
- NAT Gateways for internet access
- Security groups for each tier

### Database ($$)
- PostgreSQL RDS instance
- Multi-AZ failover
- Automated backups
- Performance monitoring

### Application ($)
- ECS Fargate cluster
- Auto-scaling service (2-10 tasks)
- CloudWatch logs
- Health checks

### Load Balancer ($)
- Application Load Balancer
- HTTPS support (optional)
- Health checks
- Access logs (optional)

### Monitoring ($)
- CloudWatch dashboard
- SNS notifications
- Email/Slack alerts
- Health alarms

**Est. Cost: $100-800/month** depending on environment

## Next Steps

1. **Choose your setup path** above
2. **Read README.md** for overview (5 min)
3. **Configure terraform.tfvars** (5 min)
4. **Deploy infrastructure** (15-20 min)
5. **Verify application** is running (5 min)
6. **Setup monitoring** alerts (5 min)
7. **Configure custom domain** (optional)
8. **Setup CI/CD pipeline** (optional)

## Key Resources

| Document | Purpose |
|----------|---------|
| [README.md](./README.md) | Main guide - architecture, config, best practices |
| [DEPLOYMENT.md](./DEPLOYMENT.md) | Step-by-step deployment instructions |
| [STRUCTURE.md](./STRUCTURE.md) | Deep dive into module design |
| [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) | Issue diagnosis and solutions |
| [INDEX.md](./INDEX.md) | Navigation guide for all files |

## Support

- Check the relevant .md file in this directory
- See [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) for common issues
- AWS documentation links in [README.md](./README.md)

## You're Ready!

Everything is set up and documented. Choose your path above and get started!

**Recommended:** Start with `README.md` then follow `DEPLOYMENT.md`

---

**Time to First Deployment:** ~30 minutes
**Infrastructure Ready Time:** ~15-20 minutes
**Application Startup Time:** ~30-60 seconds

Let's go! 🚀
