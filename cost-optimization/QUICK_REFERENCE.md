# YAWL Cost Optimization - Quick Reference Guide

Fast reference for common cost optimization tasks.

## File Quick Links

| File | Purpose | Quick Start |
|------|---------|-------------|
| **cost-calculator.py** | Analyze current costs | `python cost-calculator.py` |
| **reserved-instances.tf** | Purchase RIs | `terraform apply` |
| **spot-instances-config.yaml** | Configure spot fleets | Update and apply via AWS CLI |
| **cost-report-template.md** | Monthly reporting | Fill template with data |
| **infracost-config.yaml** | Cost estimation | `infracost breakdown --path .` |
| **INFRACOST_INTEGRATION.md** | Setup Infracost | Full integration guide |
| **README.md** | Complete overview | Project description & strategies |
| **USAGE_GUIDE.md** | Step-by-step instructions | Detailed implementation guide |

## Essential Commands

### Cost Analysis
```bash
# Quick cost analysis
python cost-calculator.py

# Export JSON
python cost-calculator.py --json

# Infracost breakdown
infracost breakdown --path reserved-instances.tf

# Compare changes
infracost diff --path reserved-instances.tf
```

### AWS CLI

```bash
# View current costs
aws ce get-cost-and-usage \
  --time-period Start=2024-02-01,End=2024-02-28 \
  --granularity MONTHLY \
  --metrics "UnblendedCost" \
  --group-by Type=DIMENSION,Key=SERVICE

# Check RI utilization
aws ec2 describe-reserved-instances \
  --query 'ReservedInstances[*].[InstanceType,State,Start,End]'

# Monitor spot prices
aws ec2 describe-spot-price-history \
  --instance-types t3.xlarge c5.2xlarge \
  --max-results 5

# Check ASG status
aws autoscaling describe-auto-scaling-groups \
  --auto-scaling-group-names yawl-api-asg
```

### Terraform

```bash
# Initialize
terraform init

# Plan changes
terraform plan -var-file=terraform.tfvars

# Apply changes
terraform apply -var-file=terraform.tfvars

# View outputs
terraform output

# Destroy resources
terraform destroy -var-file=terraform.tfvars
```

## Configuration Variables

### Terraform (`terraform.tfvars`)

```hcl
environment                = "prod"
aws_region                 = "us-east-1"
enable_reserved_instances  = true
reserved_instance_term     = 12  # 12 or 36 months
```

### Environment Variables

```bash
# AWS credentials
export AWS_ACCESS_KEY_ID="your-key"
export AWS_SECRET_ACCESS_KEY="your-secret"
export AWS_DEFAULT_REGION="us-east-1"

# Infracost
export INFRACOST_API_KEY="your-api-key"

# Slack integration
export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/..."

# Terraform Cloud (optional)
export TFC_TOKEN="your-token"
```

## Common Tasks

### Task 1: Estimate Savings from RIs

```bash
# See RI discount impact in cost-calculator output
python cost-calculator.py

# Look for "Optimized Costs" section showing 36-55% discount
```

### Task 2: Check Spot Instance Savings

```bash
# Get current spot pricing
aws ec2 describe-spot-price-history \
  --instance-types t3.xlarge \
  --max-results 1 \
  --query 'SpotPriceHistory[0].SpotPrice'

# Compare to on-demand
aws ec2 describe-instances \
  --instance-ids i-1234567890abcdef0 \
  --query 'Reservations[0].Instances[0]'
```

### Task 3: Right-Size Underutilized Instance

```bash
# Check utilization
aws cloudwatch get-metric-statistics \
  --namespace AWS/EC2 \
  --metric-name CPUUtilization \
  --dimensions Name=InstanceId,Value=i-1234567890abcdef0 \
  --start-time 2024-02-01T00:00:00Z \
  --end-time 2024-02-14T23:59:59Z \
  --period 3600 \
  --statistics Average

# If < 30%, consider downsizing
```

### Task 4: Migrate io2 Volume to gp3

```bash
# Create snapshot
SNAPSHOT_ID=$(aws ec2 create-snapshot \
  --volume-id vol-123456 \
  --query 'SnapshotId' \
  --output text)

# Wait for snapshot
aws ec2 wait snapshot-completed --snapshot-ids $SNAPSHOT_ID

# Create new gp3 volume
aws ec2 create-volume \
  --availability-zone us-east-1a \
  --snapshot-id $SNAPSHOT_ID \
  --volume-type gp3

# Attach and test, then delete old volume
```

### Task 5: Enable RI Utilization Alarm

```bash
# CloudWatch alarm for underutilized RIs
aws cloudwatch put-metric-alarm \
  --alarm-name yawl-ri-underutilized \
  --alarm-description "Alert if RI utilization below 50%" \
  --metric-name CPUUtilization \
  --namespace AWS/EC2 \
  --statistic Average \
  --period 3600 \
  --threshold 50 \
  --comparison-operator LessThanThreshold
```

## Cost Optimization Checklist

### Week 1: Quick Wins (1-2 days work)
- [ ] Run cost-calculator.py to understand baseline
- [ ] Review top recommendations
- [ ] Purchase 1-year RIs for stable workloads (~35% savings)
- [ ] Set up CloudWatch cost monitoring

**Expected Savings: ~$2,500/month**

### Week 2-3: Medium Effort (2-3 days work)
- [ ] Migrate io2 → gp3 storage volumes
- [ ] Right-size underutilized instances
- [ ] Configure spot instance fleet (requires testing)
- [ ] Set up cost alerts and Slack notifications

**Additional Savings: ~$1,000-1,500/month**

### Month 2: Advanced (1 week work)
- [ ] Implement read replicas for databases
- [ ] Review Multi-AZ requirements
- [ ] Evaluate single-AZ vs Multi-AZ trade-offs
- [ ] Implement data lifecycle policies (archive old logs/backups)

**Additional Savings: ~$500-800/month**

### Month 3+: Continuous Optimization
- [ ] Monthly cost reviews
- [ ] Quarterly RI/Savings Plan analysis
- [ ] Adjust instance types based on workload changes
- [ ] Explore new instance types (Graviton, etc.)

**Ongoing Savings: $50-200/month**

## Estimated Savings Breakdown

| Strategy | Effort | Savings | Timeline |
|----------|--------|---------|----------|
| Reserved Instances | Low | 35-40% on baseline | 1-2 days |
| Right-sizing | Medium | 20-40% per instance | 2-4 days |
| Spot Instances | Medium | 65-90% per workload | 3-5 days |
| Storage Optimization | Low | 25-30% per volume | 2-4 hours |
| Database Optimization | Medium | 30-50% potential | 2-3 days |
| **TOTAL POTENTIAL** | **High** | **40-50%** | **2-3 weeks** |

## Monitoring & Alerting

### Key Metrics to Track

```bash
# Daily cost trend
aws ce get-cost-and-usage --time-period Start=2024-02-14,End=2024-02-14 ...

# RI coverage
aws ce get-reservation-coverage --time-period Start=2024-02-01,End=2024-02-14 ...

# Spot interruption rate
aws ec2 describe-spot-fleet-request-history ...

# Instance utilization
aws cloudwatch get-metric-statistics --metric-name CPUUtilization ...
```

### Alert Thresholds

| Alert | Threshold | Action |
|-------|-----------|--------|
| Daily Cost | > $150 | Notify #devops |
| RI Utilization | < 50% | Review & adjust |
| Spot Interruptions | > 10% | Increase on-demand |
| CPU Utilization | < 30% | Review sizing |
| Storage Growth | > 20% MoM | Investigate |

## Slack Integration Template

```bash
curl -X POST $SLACK_WEBHOOK_URL \
  -H 'Content-Type: application/json' \
  -d '{
    "text": "YAWL Daily Cost Report",
    "blocks": [
      {
        "type": "section",
        "text": {
          "type": "mrkdwn",
          "text": "*Daily Cost:* $150\n*RI Savings:* $2,400/month\n*Potential Savings:* $1,200/month"
        }
      }
    ]
  }'
```

## Troubleshooting Quick Fixes

```bash
# Reset Infracost credentials
rm ~/.config/infracost/config.yml
infracost auth login

# Reload AWS credentials
unset AWS_ACCESS_KEY_ID
unset AWS_SECRET_ACCESS_KEY
aws configure

# Clear Terraform state
terraform refresh

# Check resource costs
aws ec2 describe-instances \
  --query 'Reservations[0].Instances[0].[InstanceId,InstanceType,State.Name]'

# View cost anomalies
aws ce get-anomalies --frequency DAILY
```

## Documentation Index

1. **README.md** - Project overview and strategies
2. **INFRACOST_INTEGRATION.md** - Full Infracost setup
3. **USAGE_GUIDE.md** - Step-by-step implementation
4. **QUICK_REFERENCE.md** - This file (common tasks)
5. **cost-calculator.py** - Python cost analysis tool
6. **reserved-instances.tf** - Terraform RI configuration
7. **spot-instances-config.yaml** - Spot fleet configuration
8. **cost-report-template.md** - Monthly report template
9. **infracost-config.yaml** - Infracost configuration
10. **requirements.txt** - Python dependencies

## Getting Help

```bash
# Infracost help
infracost breakdown --help
infracost auth --help

# Terraform help
terraform help
terraform plan --help

# AWS CLI help
aws ce help
aws ec2 help

# Python script help
python cost-calculator.py --help
```

## Links & Resources

- **YAWL Project:** https://github.com/yawl/yawl
- **AWS Cost Optimization:** https://aws.amazon.com/aws-cost-management/
- **Infracost:** https://www.infracost.io
- **Terraform:** https://www.terraform.io
- **AWS Pricing:** https://aws.amazon.com/pricing/

## Contact & Support

- **Email:** devops@example.com
- **Slack:** #cost-optimization
- **On-call:** For critical issues, page infrastructure team
