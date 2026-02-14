# Infracost Integration Guide for YAWL

## Overview

Infracost is an open-source tool that estimates cloud infrastructure costs in your terraform/IaC workflows. This integration enables real-time cost tracking and optimization recommendations for YAWL deployments across AWS, GCP, and Azure.

**Features:**
- Cost estimates before infrastructure changes
- Pull Request integration with automatic cost estimates
- Policy enforcement for cost overruns
- Savings opportunity identification
- Multi-cloud support (AWS, GCP, Azure)

---

## Installation

### Prerequisites
- Terraform 0.12+ (or other IaC tools)
- GitHub/GitLab/Azure DevOps account (for CI/CD integration)
- Docker (optional, for containerized execution)

### 1. Install Infracost CLI

#### macOS (Homebrew)
```bash
brew install infracost
```

#### Linux
```bash
curl -fsSL https://raw.githubusercontent.com/infracost/infracost/master/scripts/install.sh | sh
```

#### Docker
```bash
docker run --rm \
  -e INFRACOST_API_KEY=$INFRACOST_API_KEY \
  -v /path/to/code:/code \
  infracost/infracost \
  breakdown --path /code
```

### 2. Get API Key

1. Sign up at [infracost.io](https://www.infracost.io)
2. Get your API key from the dashboard
3. Set environment variable:
   ```bash
   export INFRACOST_API_KEY="your-api-key-here"
   ```

### 3. Verify Installation
```bash
infracost --version
infracost auth login
```

---

## Configuration

### Basic Setup

1. Copy the configuration file:
   ```bash
   cp infracost-config.yaml ~/.config/infracost/config.yaml
   ```

2. Update with your details:
   ```yaml
   core:
     enabled: true
     log_level: info

   providers:
     aws:
       region: us-east-1
   ```

3. Verify configuration:
   ```bash
   infracost configure
   ```

### Usage File (Optional - for detailed usage estimates)

Create `infracost-usage.yml` for accurate estimates:

```yaml
version: 0.1
resources:
  - name: aws_instance.example
    values:
      operating_system: "linux"
      compute_optimized_enabled: false
      cpu_credits: null
      reserved_instance_type: "standard"
      reserved_instance_term: "1yr"
      reserved_instance_payment_option: "all_upfront"
      vcpu_count: 4
      memory_gb: 16
      gpu_count: 0
      root_volume_size: 100
      root_volume_type: "gp3"
```

---

## Usage Examples

### 1. Basic Cost Breakdown

Analyze a single Terraform file:
```bash
infracost breakdown --path ./reserved-instances.tf
```

Output:
```
┌──────────────────────────────────────────────────────────────────┐
│ Project: /home/user/yawl/cost-optimization                       │
├──────────────────────────────────────────────────────────────────┤
│ aws_ec2_reserved_instances.api_servers                           │
│  └─ Reserved instance usage (All Upfront, 1yr)                   │
│     AWS savings plan: -$100/month                                │
│                                                                  │
│ aws_autoscaling_group.yawl_api                                   │
│  ├─ t3.xlarge (on-demand, Linux/UNIX)                           │
│  │   2x $0.17/hour = $245/month                                 │
│  └─ Network load balancer                                        │
│      $32.40/month                                                │
├──────────────────────────────────────────────────────────────────┤
│ TOTAL MONTHLY COST                                  $1,245       │
│ TOTAL ANNUAL COST                                   $14,940      │
└──────────────────────────────────────────────────────────────────┘
```

### 2. Compare Cost Changes (Git Diff)

Before/after cost comparison for a git branch:
```bash
infracost diff \
  --path ./reserved-instances.tf \
  --compare-to git diff main
```

Output:
```
Showing cost estimate changes for infrastructure code changes...

┌──────────────────────────────────────────────────────────────────┐
│ Modified: aws_instance.yawl_worker                               │
├──────────────────────────────────────────────────────────────────┤
│ - Instance type: c5.2xlarge → c5.4xlarge                        │
│ - vCPUs: 8 → 16                                                 │
│ - Cost: +$150/month (+61%)                                      │
├──────────────────────────────────────────────────────────────────┤
│ TOTAL MONTHLY COST INCREASE                        +$150         │
│ TOTAL ANNUAL COST INCREASE                         +$1,800      │
└──────────────────────────────────────────────────────────────────┘
```

### 3. Cost Breakdown by Tags

Group costs by resource tags:
```bash
infracost breakdown \
  --path . \
  --format=json | jq '.groupedCosts.tags'
```

### 4. JSON Output (for automation)

Export costs as JSON:
```bash
infracost breakdown \
  --path . \
  --format=json > cost-report.json
```

Parse the JSON:
```bash
jq '.totalMonthlyCost' cost-report.json
```

### 5. HTML Report Generation

```bash
infracost breakdown \
  --path . \
  --format=html \
  --out-file=cost-report.html
```

---

## CI/CD Integration

### GitHub Actions

Create `.github/workflows/infracost.yml`:

```yaml
name: Infracost

on:
  pull_request:
    paths:
      - 'cost-optimization/**/*.tf'
      - '.github/workflows/infracost.yml'

jobs:
  infracost:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write

    steps:
      - uses: actions/checkout@v3
        with:
          fetch-depth: 0

      - name: Run Infracost
        uses: infracost/actions/breakdown@v2
        with:
          path: cost-optimization/
          format: table
          api-key: ${{ secrets.INFRACOST_API_KEY }}

      - name: Post comment to PR
        if: github.event_name == 'pull_request'
        uses: infracost/actions/comment@v2
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}
          api-key: ${{ secrets.INFRACOST_API_KEY }}
          path: cost-optimization/
```

### GitLab CI

Create `.gitlab-ci.yml`:

```yaml
infracost:
  image: infracost/infracost:latest
  stage: analyze
  script:
    - infracost breakdown --path cost-optimization/ --format table
    - infracost breakdown --path cost-optimization/ --format json > costs.json
  artifacts:
    reports:
      dotenv: costs.json
  only:
    - merge_requests
```

### Azure Pipelines

Create `infracost-pipeline.yml`:

```yaml
trigger:
  paths:
    include:
      - cost-optimization/**

pr:
  paths:
    include:
      - cost-optimization/**

jobs:
  - job: InfracostAnalysis
    displayName: 'Infracost Cost Analysis'
    pool:
      vmImage: 'ubuntu-latest'

    steps:
      - task: UsePythonVersion@0
        inputs:
          versionSpec: '3.9'

      - script: |
          curl -fsSL https://raw.githubusercontent.com/infracost/infracost/master/scripts/install.sh | sh
          infracost breakdown --path cost-optimization/ --format json > $(Build.ArtifactStagingDirectory)/costs.json
        displayName: 'Run Infracost'
        env:
          INFRACOST_API_KEY: $(INFRACOST_API_KEY)

      - task: PublishBuildArtifacts@1
        inputs:
          pathToPublish: $(Build.ArtifactStagingDirectory)/costs.json
          artifactName: infracost-report
```

---

## Policy Enforcement

### Cost Policy Example

Create `policies/cost-threshold.hcl`:

```hcl
policy "maximum_monthly_cost" {
  description = "Prevent infrastructure changes that exceed monthly cost threshold"

  deny {
    condition = resources.total_monthly_cost > 20000
  }
}

policy "instance_size_limit" {
  description = "Limit instance sizes to cost-effective options"

  deny {
    condition     = resource.instance_type == "x1e.32xlarge"
    message       = "Use c5.2xlarge or smaller for standard workloads"
  }
}

policy "spot_instance_usage" {
  description = "Enforce spot instances for non-critical workloads"

  deny {
    condition = resource.type == "aws_instance" && resource.tenancy != "spot"
    message   = "Use spot instances for variable workloads"
  }
}
```

Run policy checks:
```bash
infracost breakdown \
  --path . \
  --config-file infracost-config.yaml \
  --check-policies
```

---

## Slack Integration

### Setup Slack Webhook

1. Create Slack App: https://api.slack.com/apps
2. Enable Incoming Webhooks
3. Get webhook URL
4. Set environment variable:
   ```bash
   export SLACK_WEBHOOK_URL="https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
   ```

### Automated Slack Notifications

Use a CI/CD workflow to send updates:

```bash
#!/bin/bash
COST_REPORT=$(infracost breakdown --path cost-optimization/ --format json)
TOTAL_COST=$(echo $COST_REPORT | jq '.totalMonthlyCost')

curl -X POST $SLACK_WEBHOOK_URL \
  -H 'Content-Type: application/json' \
  -d "{
    \"text\": \"YAWL Infrastructure Cost Report\",
    \"blocks\": [
      {
        \"type\": \"header\",
        \"text\": {
          \"type\": \"plain_text\",
          \"text\": \"📊 YAWL Cost Analysis\"
        }
      },
      {
        \"type\": \"section\",
        \"text\": {
          \"type\": \"mrkdwn\",
          \"text\": \"*Total Monthly Cost:* \\\$${TOTAL_COST}\"
        }
      }
    ]
  }"
```

---

## Cost Monitoring Dashboard

### Create Cost Dashboard

```bash
# Export costs for dashboarding
infracost breakdown \
  --path . \
  --format=json \
  --out-file=costs.json
```

### Python Script for Trend Analysis

```python
import json
from datetime import datetime, timedelta

def analyze_cost_trend(cost_reports):
    """Analyze cost trends over time"""

    costs = []
    for report_path in cost_reports:
        with open(report_path) as f:
            data = json.load(f)
            costs.append({
                'date': data['timeGenerated'],
                'total': data['totalMonthlyCost']
            })

    # Calculate trend
    if len(costs) > 1:
        change = costs[-1]['total'] - costs[-2]['total']
        pct_change = (change / costs[-2]['total']) * 100

        print(f"Cost Change: ${change:+.2f} ({pct_change:+.1f}%)")
        print(f"Previous: ${costs[-2]['total']:.2f}")
        print(f"Current: ${costs[-1]['total']:.2f}")
```

---

## Advanced Features

### Custom Cost Calculations

Add custom cost factors for RIs and Savings Plans:

```yaml
cost_modifiers:
  - resource_type: aws_instance
    multiplier: 0.64  # 1-year RI discount
    metadata:
      description: "1-year all-upfront reserved instance"

  - resource_type: aws_rds_cluster
    multiplier: 0.65  # 1-year RDS RI discount
```

### Multi-Cloud Cost Comparison

```bash
# Analyze AWS infrastructure
infracost breakdown --path aws/ --format json > aws-costs.json

# Analyze GCP infrastructure
infracost breakdown --path gcp/ --format json > gcp-costs.json

# Analyze Azure infrastructure
infracost breakdown --path azure/ --format json > azure-costs.json

# Compare total costs
jq '.totalMonthlyCost' aws-costs.json gcp-costs.json azure-costs.json
```

### Budget Tracking

```python
import json

def check_budget(cost_report_path, monthly_budget=15000):
    with open(cost_report_path) as f:
        data = json.load(f)

    total_cost = data['totalMonthlyCost']
    remaining = monthly_budget - total_cost
    percentage = (total_cost / monthly_budget) * 100

    print(f"Budget: ${monthly_budget:,.2f}")
    print(f"Spent: ${total_cost:,.2f}")
    print(f"Remaining: ${remaining:,.2f}")
    print(f"Used: {percentage:.1f}%")

    if percentage > 90:
        print("WARNING: Budget usage exceeds 90%")
        return False
    return True
```

---

## Troubleshooting

### Issue: "Unauthorized" API key error

**Solution:**
```bash
export INFRACOST_API_KEY="your-correct-api-key"
infracost auth login
```

### Issue: Terraform variables not found

**Solution:**
Create `terraform.tfvars`:
```hcl
environment = "prod"
aws_region  = "us-east-1"
```

Then run:
```bash
infracost breakdown --path . --terraform-var-file=terraform.tfvars
```

### Issue: Slow cost estimates

**Solution:**
Enable parallel processing:
```bash
infracost breakdown \
  --path . \
  --parallelism=4 \
  --cache=true
```

### Issue: No cost data for resource

**Solution:**
1. Check resource is supported: https://www.infracost.io/docs/features/supported_resources/
2. Provide usage data in `infracost-usage.yml`
3. Ensure configuration is correct

---

## Best Practices

1. **Include Infracost in every PR:** Automatic cost estimates prevent surprises
2. **Set cost policies:** Enforce cost controls through policy-as-code
3. **Monitor trends:** Track costs over time to identify anomalies
4. **Optimize regularly:** Use recommendations for continuous improvement
5. **Document changes:** Keep cost reasoning in commit messages
6. **Review with team:** Discuss cost implications before approving PRs

---

## Resources

- **Official Docs:** https://www.infracost.io/docs
- **GitHub Repository:** https://github.com/infracost/infracost
- **Supported Resources:** https://www.infracost.io/docs/features/supported_resources/
- **Community Slack:** https://infracost.io/community
- **API Documentation:** https://www.infracost.io/docs/features/cloud-api/

---

## Next Steps

1. Install Infracost CLI
2. Authenticate with API key
3. Configure `.github/workflows/infracost.yml`
4. Create policies in `policies/` directory
5. Set up Slack notifications
6. Monitor costs through dashboard
7. Implement optimization recommendations
