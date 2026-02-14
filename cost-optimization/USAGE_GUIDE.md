# YAWL Cost Optimization - Practical Usage Guide

A step-by-step guide to implementing cost optimization strategies for YAWL deployments.

## Table of Contents

1. [Initial Setup](#initial-setup)
2. [Running Cost Analysis](#running-cost-analysis)
3. [Implementing Reserved Instances](#implementing-reserved-instances)
4. [Deploying Spot Instance Fleets](#deploying-spot-instance-fleets)
5. [Setting Up Monitoring](#setting-up-monitoring)
6. [Generating Reports](#generating-reports)
7. [Troubleshooting](#troubleshooting)

---

## Initial Setup

### Step 1: Install Dependencies

```bash
# Navigate to cost-optimization directory
cd /home/user/yawl/cost-optimization

# Install Python dependencies
pip install -r requirements.txt

# Install Infracost CLI
curl -fsSL https://raw.githubusercontent.com/infracost/infracost/master/scripts/install.sh | sh

# Verify installation
python --version
infracost --version
terraform --version
```

### Step 2: Configure AWS Credentials

```bash
# Method 1: Using AWS CLI (recommended)
aws configure
# Enter your AWS Access Key ID
# Enter your AWS Secret Access Key
# Enter default region (e.g., us-east-1)
# Enter output format (json)

# Method 2: Using environment variables
export AWS_ACCESS_KEY_ID="AKIAIOSFODNN7EXAMPLE"
export AWS_SECRET_ACCESS_KEY="wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
export AWS_DEFAULT_REGION="us-east-1"

# Method 3: Using IAM role (EC2 instance)
# IAM role will be automatically detected
```

### Step 3: Configure Infracost API Key

```bash
# Sign up at https://www.infracost.io
# Get API key from dashboard

# Set environment variable
export INFRACOST_API_KEY="your-api-key-from-dashboard"

# Verify configuration
infracost auth login
infracost configure
```

### Step 4: Terraform Configuration

```bash
# Initialize Terraform
terraform init

# Create terraform.tfvars with your settings
cat > terraform.tfvars << EOF
environment = "prod"
aws_region = "us-east-1"
enable_reserved_instances = true
reserved_instance_term = 12
EOF

# Verify Terraform configuration
terraform validate
```

---

## Running Cost Analysis

### Option 1: Quick Cost Analysis

```bash
# Run the cost calculator with sample data
python cost-calculator.py

# Expected output:
# ======================================================================
# YAWL COST OPTIMIZATION REPORT
# ======================================================================
#
# Current On-Demand Costs:
#   - Monthly: $6,542.00
#   - Annual: $78,504.00
# ...
```

### Option 2: Export to JSON for Processing

```bash
# Get detailed JSON output
python cost-calculator.py --json > cost-analysis.json

# Parse JSON results
cat cost-analysis.json | jq '.potential_savings'

# Output:
# {
#   "monthly": 2647.00,
#   "annual": 31764.00,
#   "percentage": 40.5
# }
```

### Option 3: Analyze Terraform Changes with Infracost

```bash
# Get cost breakdown of current Terraform
infracost breakdown --path reserved-instances.tf --format table

# Compare cost impact of proposed changes
git checkout feature-branch
infracost diff --path reserved-instances.tf --compare-to main

# Export detailed cost report
infracost breakdown --path . --format json > infracost-report.json
```

### Option 4: Analyze AWS Resources Directly

```bash
# Get current AWS costs from AWS CLI
aws ce get-cost-and-usage \
  --time-period Start=2024-01-01,End=2024-02-01 \
  --granularity MONTHLY \
  --metrics "UnblendedCost" \
  --group-by Type=DIMENSION,Key=SERVICE \
  --region us-east-1

# Output shows costs by service (EC2, RDS, etc.)
```

---

## Implementing Reserved Instances

### Step 1: Review RI Recommendations

```bash
# Run cost calculator to see RI savings opportunities
python cost-calculator.py

# Look for output similar to:
# Top Recommendations:
# 1. [MEDIUM] Purchase 1-year reserved instance
#    Resource: yawl-api-server-1
#    Potential Annual Savings: $1,234.56
```

### Step 2: Plan Terraform Changes

```bash
# Review reserved-instances.tf configuration
cat reserved-instances.tf | head -100

# Check what will be created
terraform plan \
  -var environment=prod \
  -var aws_region=us-east-1 \
  -var enable_reserved_instances=true

# Output shows resources to be created:
# Plan: 4 to add, 0 to change, 0 to destroy
```

### Step 3: Estimate Cost Impact

```bash
# Use Infracost to see exact cost impact
infracost breakdown \
  --path reserved-instances.tf \
  --format table

# Output:
# aws_ec2_reserved_instances.api_servers
#  └─ Reserved instance usage (All Upfront, 1yr)
#     AWS savings: -$100/month
```

### Step 4: Purchase Reserved Instances

```bash
# Apply Terraform configuration to purchase RIs
terraform apply \
  -var environment=prod \
  -var aws_region=us-east-1 \
  -var enable_reserved_instances=true

# Confirm the apply:
# Type "yes" and press Enter

# View purchased RI details
terraform output reserved_instances_summary

# Output:
# {
#   "api_servers" = "ri-0abc12def3456ghi",
#   "database" = "ri-0jkl45mno6789pqr",
#   ...
# }
```

### Step 5: Verify RI Purchase

```bash
# Check AWS console
aws ec2 describe-reserved-instances \
  --region us-east-1 \
  --query 'ReservedInstances[*].[ReservedInstancesId,InstanceType,State,Tags]'

# Output shows purchased RIs and their status

# Check utilization
aws ec2 describe-reserved-instances-offerings \
  --region us-east-1 \
  --query 'ReservedInstancesOfferings[*].[InstanceType,Scope]'
```

### Step 6: Monitor RI Utilization

```bash
# View RI coverage percentage
aws ec2 describe-reserved-instances \
  --region us-east-1 \
  --query 'ReservedInstances[*].[InstanceType,Start,End,State]'

# Set up CloudWatch alarms via Terraform
terraform output cloudwatch_alarms

# Check alarm status
aws cloudwatch describe-alarms \
  --alarm-names yawl-ri-underutilized
```

---

## Deploying Spot Instance Fleets

### Step 1: Review Spot Configuration

```bash
# Check current spot configuration
cat spot-instances-config.yaml | grep -A 20 "spot_fleets:"

# Key parameters:
# - target_capacity: 3
# - on_demand_base_capacity: 1
# - on_demand_percentage: 33
# - max_price_percent: 0.35
```

### Step 2: Check Current Spot Pricing

```bash
# Get latest spot prices
aws ec2 describe-spot-price-history \
  --instance-types t3.xlarge c5.2xlarge m5.xlarge \
  --start-time 2024-02-14T00:00:00Z \
  --end-time 2024-02-14T23:59:59Z \
  --region us-east-1 \
  --max-results 10

# Output shows current and historical prices:
# {
#   "SpotPriceHistory": [
#     {
#       "InstanceType": "t3.xlarge",
#       "SpotPrice": "0.0664",
#       "Timestamp": "2024-02-14T15:30:00.000Z"
#     },
#     ...
#   ]
# }
```

### Step 3: Create Spot Fleet Request

```bash
# First, create a launch template
aws ec2 create-launch-template \
  --launch-template-name yawl-api-template \
  --version-description "YAWL API server" \
  --launch-template-data '{
    "ImageId": "ami-0c55b159cbfafe1f0",
    "InstanceType": "t3.xlarge",
    "KeyName": "my-key-pair",
    "Monitoring": {"Enabled": true}
  }'

# Create spot fleet request configuration
cat > spot-fleet-request.json << 'EOF'
{
  "IamFleetRole": "arn:aws:iam::123456789012:role/ec2-spot-fleet-tagging-role",
  "AllocationStrategy": "price-capacity-optimized",
  "TargetCapacity": 3,
  "SpotPrice": "0.35",
  "LaunchSpecifications": [
    {
      "ImageId": "ami-0c55b159cbfafe1f0",
      "InstanceType": "t3.xlarge",
      "KeyName": "my-key-pair",
      "SpotPrice": "0.0664"
    },
    {
      "ImageId": "ami-0c55b159cbfafe1f0",
      "InstanceType": "t3a.xlarge",
      "KeyName": "my-key-pair",
      "SpotPrice": "0.0611"
    }
  ]
}
EOF

# Create the spot fleet
aws ec2 request-spot-fleet \
  --spot-fleet-request-config file://spot-fleet-request.json \
  --region us-east-1
```

### Step 4: Monitor Spot Fleet

```bash
# Check fleet status
aws ec2 describe-spot-fleet-requests \
  --region us-east-1 \
  --query 'SpotFleetRequestConfigs[*].[SpotFleetRequestId,SpotFleetRequestState]'

# Get detailed fleet information
aws ec2 describe-spot-fleet-instances \
  --spot-fleet-request-id sfr-12345678-1234-1234-1234-123456789012 \
  --region us-east-1

# Check for interruptions
aws ec2 describe-spot-fleet-request-history \
  --spot-fleet-request-id sfr-12345678-1234-1234-1234-123456789012 \
  --region us-east-1 \
  --max-results 10
```

### Step 5: Configure Auto-Scaling

```bash
# Create Auto Scaling Group for spot instances
aws autoscaling create-auto-scaling-group \
  --auto-scaling-group-name yawl-spot-asg \
  --launch-template LaunchTemplateName=yawl-api-template,Version='$Latest' \
  --min-size 1 \
  --max-size 10 \
  --desired-capacity 3 \
  --availability-zones us-east-1a us-east-1b us-east-1c \
  --region us-east-1

# Set up target tracking scaling
aws autoscaling put-scaling-policy \
  --auto-scaling-group-name yawl-spot-asg \
  --policy-name target-tracking-scaling \
  --policy-type TargetTrackingScaling \
  --target-tracking-configuration '{
    "TargetValue": 70.0,
    "PredefinedMetricSpecification": {
      "PredefinedMetricType": "ASGAverageCPUUtilization"
    }
  }'
```

### Step 6: Handle Interruptions

```bash
# Create SNS topic for interruption notices
aws sns create-topic --name yawl-spot-interruptions

# Create Lambda function to handle interruptions
cat > handle-interruption.py << 'EOF'
import boto3
import json

ec2 = boto3.client('ec2')
sns = boto3.client('sns')

def lambda_handler(event, context):
    print("Spot interruption event:", json.dumps(event))

    # Instance being interrupted
    instance_id = event['detail']['instance-id']

    # Log the interruption
    print(f"Instance {instance_id} is being interrupted")

    # Optional: Trigger replacement
    # trigger_replacement(instance_id)

    return {
        'statusCode': 200,
        'body': json.dumps('Interruption handled')
    }

def trigger_replacement(instance_id):
    """Trigger launch of replacement instance"""
    asg = boto3.client('autoscaling')
    asg.set_desired_capacity(
        AutoScalingGroupName='yawl-spot-asg',
        DesiredCapacity=3,  # Maintain desired capacity
        HonorCooldown=False
    )
EOF

# Deploy Lambda function
aws lambda create-function \
  --function-name handle-spot-interruption \
  --runtime python3.9 \
  --role arn:aws:iam::123456789012:role/lambda-execution-role \
  --handler handle-interruption.lambda_handler \
  --zip-file fileb://handle-interruption.zip
```

---

## Setting Up Monitoring

### Step 1: Create CloudWatch Dashboard

```bash
# Create custom dashboard for cost monitoring
aws cloudwatch put-dashboard \
  --dashboard-name YAWL-Cost-Optimization \
  --dashboard-body file://dashboard-config.json
```

Create `dashboard-config.json`:
```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/EC2", "CPUUtilization", {"stat": "Average"}],
          ["AWS/EC2", "NetworkIn"],
          ["AWS/EC2", "NetworkOut"]
        ],
        "period": 300,
        "stat": "Average",
        "region": "us-east-1",
        "title": "Instance Metrics"
      }
    }
  ]
}
```

### Step 2: Set Up Cost Alerts

```bash
# Create SNS topic for cost alerts
aws sns create-topic --name yawl-cost-alerts

# Subscribe to alerts
aws sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:123456789012:yawl-cost-alerts \
  --protocol email \
  --notification-endpoint devops@example.com

# Create CloudWatch alarm for daily cost
aws cloudwatch put-metric-alarm \
  --alarm-name yawl-daily-cost-alert \
  --alarm-description "Alert if daily cost exceeds $150" \
  --metric-name EstimatedCharges \
  --namespace AWS/Billing \
  --statistic Maximum \
  --period 86400 \
  --threshold 150 \
  --comparison-operator GreaterThanThreshold \
  --alarm-actions arn:aws:sns:us-east-1:123456789012:yawl-cost-alerts
```

### Step 3: Enable Cost Anomaly Detection

```bash
# Create anomaly detector
aws ce put-anomaly-monitor \
  --anomaly-monitor '{
    "MonitorName": "YAWL-Anomaly-Detection",
    "MonitorType": "DIMENSIONAL",
    "MonitorDimension": "SERVICE",
    "MonitorSpecification": "{ \"Or\": [ { \"Dimensions\": { \"Key\": \"SERVICE\", \"Values\": [ \"Amazon Elastic Compute Cloud - Compute\" ] } } ] }"
  }'

# Create anomaly alert
aws ce put-anomaly-subscription \
  --anomaly-subscription '{
    "SubscriptionName": "YAWL-Cost-Anomaly-Alert",
    "Threshold": 10,
    "Frequency": "DAILY",
    "MonitorArnList": ["arn:aws:ce:us-east-1:123456789012:anomaly-monitor/..."],
    "SubscriptionArn": "arn:aws:sns:us-east-1:123456789012:yawl-cost-alerts"
  }'
```

---

## Generating Reports

### Step 1: Generate Cost Analysis Report

```bash
# Run cost calculator
python cost-calculator.py --json > analysis.json

# Generate markdown report
python << 'EOF'
import json
from datetime import datetime

with open('analysis.json') as f:
    data = json.load(f)

report = f"""# YAWL Cost Analysis Report
**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

## Summary
- **Current Monthly Cost:** ${data['current_costs']['monthly']:,.2f}
- **Optimized Monthly Cost:** ${data['optimized_costs']['monthly']:,.2f}
- **Monthly Savings:** ${data['potential_savings']['monthly']:,.2f}
- **Annual Savings:** ${data['potential_savings']['annual']:,.2f} ({data['potential_savings']['percentage']:.1f}%)

## Top Recommendations
"""

for i, rec in enumerate(data['recommendations'][:5], 1):
    report += f"\n{i}. **{rec['recommendation']}**\n"
    report += f"   - Savings: ${rec['potential_savings']:,.2f}/year\n"
    report += f"   - Priority: {rec['priority']}\n"

with open('cost-analysis-report.md', 'w') as f:
    f.write(report)

print("Report generated: cost-analysis-report.md")
EOF
```

### Step 2: Generate Infracost Report

```bash
# Generate HTML report
infracost breakdown \
  --path . \
  --format html \
  --out-file cost-infrastructure-report.html

# Open in browser
open cost-infrastructure-report.html

# Generate JSON for further processing
infracost breakdown \
  --path . \
  --format json \
  --out-file cost-infrastructure-report.json
```

### Step 3: Export AWS Cost and Usage Reports

```bash
# Query Cost Explorer API
aws ce get-cost-and-usage \
  --time-period Start=2024-01-01,End=2024-02-01 \
  --granularity MONTHLY \
  --metrics "UnblendedCost" "UsageQuantity" \
  --group-by Type=DIMENSION,Key=SERVICE \
  --region us-east-1 \
  --output json > cost-and-usage.json

# Format for reporting
python << 'EOF'
import json
import csv

with open('cost-and-usage.json') as f:
    data = json.load(f)

with open('cost-by-service.csv', 'w', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(['Service', 'Cost', 'Usage'])

    for result in data['ResultsByTime']:
        for group in result['Groups']:
            service = group['Keys'][0]
            cost = group['Metrics']['UnblendedCost']['Amount']
            usage = group['Metrics']['UsageQuantity']['Amount']
            writer.writerow([service, cost, usage])

print("Report exported to cost-by-service.csv")
EOF
```

---

## Troubleshooting

### Issue: AWS Authentication Error

**Symptoms:** `Unable to locate credentials`

**Solution:**
```bash
# Verify AWS credentials
aws sts get-caller-identity

# If error, configure credentials
aws configure

# Or set environment variables
export AWS_ACCESS_KEY_ID="YOUR_KEY"
export AWS_SECRET_ACCESS_KEY="YOUR_SECRET"
```

### Issue: Infracost API Key Error

**Symptoms:** `Failed to authenticate with Infracost Cloud API`

**Solution:**
```bash
# Check API key is set
echo $INFRACOST_API_KEY

# If empty, set it
export INFRACOST_API_KEY="your-api-key"

# Verify with login
infracost auth login
```

### Issue: Terraform Plan Fails

**Symptoms:** `Error: provider not configured`

**Solution:**
```bash
# Initialize Terraform
terraform init

# Validate configuration
terraform validate

# Check for missing variables
terraform plan -var-file=terraform.tfvars
```

### Issue: Spot Fleet Request Fails

**Symptoms:** `InvalidSpotFleetRequestConfig`

**Solution:**
```bash
# Verify IAM role exists
aws iam get-role --role-name ec2-spot-fleet-tagging-role

# If not, create it
aws iam create-role \
  --role-name ec2-spot-fleet-tagging-role \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "spotfleet.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'

# Attach policy
aws iam attach-role-policy \
  --role-name ec2-spot-fleet-tagging-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonEC2SpotFleetTaggingRole
```

### Issue: Cost Report Shows $0 for Some Resources

**Symptoms:** Resources not showing cost data

**Solution:**
```bash
# Check Infracost supported resources
infracost breakdown --path . --format json | jq '.resources[] | select(.resourceType) | .resourceType' | sort -u

# Add usage estimation file
cat > infracost-usage.yml << 'EOF'
version: 0.1
resources:
  - name: aws_instance.example
    values:
      operating_system: linux
      cpu_credits: null
EOF

# Re-run analysis
infracost breakdown --path . --usage-file infracost-usage.yml
```

---

## Next Steps

1. **Implement Quick Wins:** Start with Reserved Instances (easiest, fastest savings)
2. **Test Spot Instances:** Deploy on non-critical workloads first
3. **Monitor Continuously:** Set up dashboards and alerts
4. **Review Monthly:** Generate reports and adjust strategies
5. **Scale Optimization:** Apply learnings to other environments

## Support

For issues or questions:
- Check the main [README.md](README.md)
- Review [Infracost documentation](https://www.infracost.io/docs)
- Check [AWS cost optimization guide](https://aws.amazon.com/aws-cost-management/)
