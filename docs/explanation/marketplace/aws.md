# YAWL on Amazon Web Services (AWS)

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Overview

YAWL Workflow Engine is available on AWS Marketplace, providing enterprise workflow automation with seamless AWS integration. This document covers AWS-specific features, pricing, and deployment options.

### 1.1 Key Features on AWS

- **AWS Marketplace**: One-click deployment
- **Amazon EKS**: Optimized for Elastic Kubernetes Service
- **Amazon RDS**: Managed PostgreSQL with Multi-AZ
- **Amazon ElastiCache**: Managed Redis cluster
- **AWS Secrets Manager**: Secure secrets management
- **CloudWatch**: Native logging and monitoring
- **IAM Integration**: Role-based access control
- **VPC Isolation**: Private networking options

### 1.2 Architecture on AWS

```
                    +-------------------+
                    |  Route 53         |
                    +--------+----------+
                             |
                    +--------v----------+
                    |  ALB/NLB          |
                    |  (TLS Termination)|
                    +--------+----------+
                             |
         +-------------------+-------------------+
         |                   |                   |
    +----v------------+ +----v------------+ +----v------------+
    | EKS Fargate/    | | EKS Fargate/    | | EKS Fargate/    |
    | Managed Node    | | Managed Node    | | Managed Node    |
    | Engine Pod 1    | | Engine Pod 2    | | Engine Pod N    |
    +-----------------+ +-----------------+ +-----------------+
         |                   |                   |
         +-------------------+-------------------+
                             |
              +--------------v--------------+
              |     Amazon RDS              |
              |     Multi-AZ PostgreSQL     |
              |     + Read Replicas         |
              +-------------+---------------+
                            |
              +-------------v---------------+
              |     ElastiCache Redis       |
              |     Cluster Mode            |
              +-----------------------------+
```

---

## 2. AWS Marketplace Listing

### 2.1 Product Information

| Field | Value |
|-------|-------|
| **Product Name** | YAWL Workflow Engine |
| **Short Description** | Enterprise workflow automation with Petri Net formal foundation |
| **Categories** | Business Applications, Developer Tools |
| **Pricing Model** | Hourly (infrastructure) + BYOL |
| **Delivery Methods** | Amazon EKS, CloudFormation |
| **Support** | Community + Commercial options |

### 2.2 Pricing

| Component | Instance Type | Hourly Cost | Monthly Est. |
|-----------|---------------|-------------|--------------|
| EKS Cluster | - | $0.10/cluster | $73 |
| Worker Nodes (3x) | m6i.xlarge | $0.192/hr each | $420 |
| RDS Multi-AZ | db.r6g.xlarge | $0.60/hr | $438 |
| ElastiCache | cache.r6g.xlarge | $0.182/hr | $132 |
| ALB | - | $0.0225/hr | $16 |
| **Total** | | | **~$1,100/mo** |

### 2.3 Free Trial

- 14-day free trial on AWS Marketplace
- Limited to t3.medium instances
- Includes all features
- No commitment required

---

## 3. AWS Services Integration

### 3.1 Required AWS Services

| Service | Purpose | Configuration |
|---------|---------|---------------|
| **EKS** | Container orchestration | 1.28+ |
| **RDS for PostgreSQL** | Database | 15+ with Multi-AZ |
| **ElastiCache** | Redis cache | Cluster mode |
| **VPC** | Network isolation | Multi-AZ subnets |
| **IAM** | Access control | Service accounts |
| **Secrets Manager** | Secrets | Auto-rotation |
| **CloudWatch** | Logs & Metrics | Container Insights |

### 3.2 Optional AWS Services

| Service | Purpose | Benefit |
|---------|---------|---------|
| **EFS** | Shared storage | Persistent volumes |
| **S3** | Backups | Backup storage |
| **CloudFront** | CDN | Global distribution |
| **WAF** | Security | Web application firewall |
| **Shield** | DDoS | DDoS protection |
| **X-Ray** | Tracing | Distributed tracing |
| **EventBridge** | Events | Event-driven workflows |

### 3.3 IAM Policy

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "YAWLEKSPermissions",
      "Effect": "Allow",
      "Action": [
        "eks:DescribeCluster",
        "eks:ListClusters"
      ],
      "Resource": "*"
    },
    {
      "Sid": "YAWLSecretsManager",
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:*:*:secret:yawl/*"
    },
    {
      "Sid": "YAWLRDSAccess",
      "Effect": "Allow",
      "Action": [
        "rds-db:connect"
      ],
      "Resource": "arn:aws:rds-db:*:*:dbuser:*/yawl_*"
    }
  ]
}
```

---

## 4. Deployment Methods

### 4.1 AWS Marketplace (Recommended)

1. Navigate to [AWS Marketplace](https://aws.amazon.com/marketplace)
2. Search for "YAWL Workflow Engine"
3. Click "Continue to Subscribe"
4. Accept terms and configure:
   - Region
   - VPC
   - Instance types
5. Click "Launch"

### 4.2 CloudFormation

```bash
# Deploy using CloudFormation
aws cloudformation create-stack \
  --stack-name yawl-production \
  --template-url https://s3.amazonaws.com/yawl-marketplace/aws/cloudformation.yaml \
  --parameters ParameterKey=VpcId,ParameterValue=vpc-xxx \
               ParameterKey=SubnetIds,ParameterValue=subnet-xxx\\,subnet-yyy \
               ParameterKey=InstanceType,ParameterValue=m6i.xlarge \
               ParameterKey=DBInstanceClass,ParameterValue=db.r6g.xlarge \
  --capabilities CAPABILITY_IAM
```

### 4.3 Terraform

```bash
# Clone repository
git clone https://github.com/yawlfoundation/yawl.git
cd yawl/terraform/aws

# Configure
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars

# Deploy
terraform init
terraform apply
```

---

## 5. EKS Configuration

### 5.1 EKS Cluster Creation

```bash
# Create EKS cluster using eksctl
cat > cluster.yaml <<EOF
apiVersion: eksctl.io/v1alpha5
kind: ClusterConfig

metadata:
  name: yawl-cluster
  region: us-east-1
  version: "1.29"

vpc:
  id: vpc-xxx
  subnets:
    private:
      us-east-1a: { id: subnet-xxx }
      us-east-1b: { id: subnet-yyy }
      us-east-1c: { id: subnet-zzz }

managedNodeGroups:
  - name: yawl-workers
    instanceType: m6i.xlarge
    minSize: 3
    maxSize: 10
    desiredCapacity: 3
    privateNetworking: true
    iam:
      withAddonPolicies:
        secretsManager: true
        cloudWatch: true

cloudWatch:
  clusterLogging:
    enableTypes:
      - api
      - audit
      - authenticator
      - controllerManager
      - scheduler
EOF

eksctl create cluster -f cluster.yaml
```

### 5.2 IAM Roles for Service Accounts (IRSA)

```bash
# Create OIDC provider
eksctl utils associate-iam-oidc-provider \
  --cluster yawl-cluster \
  --approve

# Create IAM policy
aws iam create-policy \
  --policy-name YAWLEnginePolicy \
  --policy-document file://iam-policy.json

# Create IAM service account
eksctl create iamserviceaccount \
  --name yawl-engine \
  --namespace yawl \
  --cluster yawl-cluster \
  --attach-policy-arn arn:aws:iam::ACCOUNT_ID:policy/YAWLEnginePolicy \
  --approve
```

### 5.3 AWS Load Balancer Controller

```bash
# Add Helm repository
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install AWS Load Balancer Controller
helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=yawl-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

---

## 6. RDS Configuration

### 6.1 PostgreSQL Instance Creation

```bash
# Create DB subnet group
aws rds create-db-subnet-group \
  --db-subnet-group-name yawl-db-subnet \
  --db-subnet-group-description "YAWL Database Subnet Group" \
  --subnet-ids subnet-xxx subnet-yyy subnet-zzz

# Create parameter group
aws rds create-db-parameter-group \
  --db-parameter-group-name yawl-postgres-params \
  --db-parameter-group-family postgres15 \
  --description "YAWL PostgreSQL Parameters"

# Modify parameters
aws rds modify-db-parameter-group \
  --db-parameter-group-name yawl-postgres-params \
  --parameters "ParameterName=max_connections,ParameterValue=500,ApplyMethod=immediate"

# Create RDS instance
aws rds create-db-instance \
  --db-instance-identifier yawl-postgres \
  --db-instance-class db.r6g.xlarge \
  --engine postgres \
  --engine-version 15.4 \
  --master-username yawl_admin \
  --master-user-password SecurePassword123 \
  --allocated-storage 500 \
  --storage-type gp3 \
  --storage-encrypted \
  --db-subnet-group-name yawl-db-subnet \
  --vpc-security-group-ids sg-xxx \
  --db-parameter-group-name yawl-postgres-params \
  --multi-az \
  --backup-retention-period 35 \
  --deletion-protection
```

### 6.2 Enhanced Monitoring

```bash
# Enable Enhanced Monitoring
aws rds modify-db-instance \
  --db-instance-identifier yawl-postgres \
  --monitoring-interval 60 \
  --monitoring-role-arn arn:aws:iam::ACCOUNT_ID:role/rds-monitoring-role
```

---

## 7. ElastiCache Configuration

### 7.1 Redis Cluster Creation

```bash
# Create subnet group
aws elasticache create-cache-subnet-group \
  --cache-subnet-group-name yawl-redis-subnet \
  --cache-subnet-group-description "YAWL Redis Subnet Group" \
  --subnet-ids subnet-xxx subnet-yyy subnet-zzz

# Create Redis replication group
aws elasticache create-replication-group \
  --replication-group-id yawl-redis \
  --replication-group-description "YAWL Redis Cluster" \
  --cache-node-type cache.r6g.xlarge \
  --engine redis \
  --engine-version 7.0 \
  --cache-parameter-group-name default.redis7 \
  --num-cache-clusters 3 \
  --replicas-per-node-group 1 \
  --cache-subnet-group-name yawl-redis-subnet \
  --security-group-ids sg-xxx \
  --at-rest-encryption-enabled \
  --transit-encryption-enabled \
  --auth-token YourAuthToken123 \
  --automatic-failover-enabled \
  --multi-az-enabled
```

### 7.2 Redis Configuration

```bash
# Create custom parameter group
aws elasticache create-cache-parameter-group \
  --cache-parameter-group-name yawl-redis-params \
  --cache-parameter-group-family redis7 \
  --description "YAWL Redis Parameters"

# Modify parameters
aws elasticache modify-cache-parameter-group \
  --cache-parameter-group-name yawl-redis-params \
  --parameter-name-values "ParameterName=maxmemory-policy,ParameterValue=allkeys-lru"
```

---

## 8. Secrets Management

### 8.1 Secrets Manager Configuration

```bash
# Create database credentials secret
aws secretsmanager create-secret \
  --name yawl/db-credentials \
  --secret-string '{"username":"yawl_admin","password":"SecurePassword123","host":"yawl-postgres.xxxxx.us-east-1.rds.amazonaws.com","port":5432,"database":"yawl"}'

# Create Redis auth secret
aws secretsmanager create-secret \
  --name yawl/redis-auth \
  --secret-string '{"password":"YourAuthToken123","host":"yawl-redis.xxxxx.cache.amazonaws.com","port":6379}'

# Enable automatic rotation
aws secretsmanager rotate-secret \
  --secret-id yawl/db-credentials \
  --rotation-lambda-arn arn:aws:lambda:us-east-1:ACCOUNT_ID:function:RotateSecret \
  --rotation-rules AutomaticallyAfterDays=30
```

### 8.2 External Secrets Operator

```yaml
# Install External Secrets Operator
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets -n external-secrets --create-namespace

# Create SecretStore
apiVersion: external-secrets.io/v1beta1
kind: SecretStore
metadata:
  name: aws-secretsmanager
  namespace: yawl
spec:
  provider:
    aws:
      service: SecretsManager
      region: us-east-1
      auth:
        jwt:
          serviceAccountRef:
            name: yawl-engine

---
# Create ExternalSecret
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: yawl-db-credentials
  namespace: yawl
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secretsmanager
  target:
    name: yawl-db-credentials
  data:
    - secretKey: username
      remoteRef:
        key: yawl/db-credentials
        property: username
    - secretKey: password
      remoteRef:
        key: yawl/db-credentials
        property: password
    - secretKey: host
      remoteRef:
        key: yawl/db-credentials
        property: host
```

---

## 9. CloudWatch Integration

### 9.1 Container Insights

```bash
# Enable Container Insights
aws eks update-cluster-config \
  --name yawl-cluster \
  --logging '{"clusterLogging":[{"types":["api","audit","authenticator","controllerManager","scheduler"],"enabled":true}]}'

# Install CloudWatch Agent
helm repo add aws-observability https://aws-observability.github.io/helm-charts
helm upgrade --install aws-for-fluent-bit aws-observability/aws-for-fluent-bit \
  -n aws-observability --create-namespace \
  -f fluent-bit-values.yaml
```

### 9.2 CloudWatch Alarms

```yaml
# CloudWatch alarms configuration
Resources:
  HighCPUCAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: YAWL-HighCPU
      AlarmDescription: High CPU utilization
      Namespace: ContainerInsights
      MetricName: node_cpu_utilization
      Dimensions:
        - Name: ClusterName
          Value: yawl-cluster
      Statistic: Average
      Period: 300
      EvaluationPeriods: 2
      Threshold: 80
      ComparisonOperator: GreaterThanThreshold
      AlarmActions:
        - arn:aws:sns:us-east-1:ACCOUNT_ID:yawl-alerts

  HighMemoryAlarm:
    Type: AWS::CloudWatch::Alarm
    Properties:
      AlarmName: YAWL-HighMemory
      AlarmDescription: High memory utilization
      Namespace: ContainerInsights
      MetricName: node_memory_utilization
      Dimensions:
        - Name: ClusterName
          Value: yawl-cluster
      Statistic: Average
      Period: 300
      EvaluationPeriods: 2
      Threshold: 80
      ComparisonOperator: GreaterThanThreshold
      AlarmActions:
        - arn:aws:sns:us-east-1:ACCOUNT_ID:yawl-alerts
```

---

## 10. Security Configuration

### 10.1 Security Groups

```bash
# Create security group for ALB
ALB_SG=$(aws ec2 create-security-group \
  --group-name yawl-alb-sg \
  --description "YAWL ALB Security Group" \
  --vpc-id vpc-xxx \
  --query 'GroupId' --output text)

# Allow HTTPS from internet
aws ec2 authorize-security-group-ingress \
  --group-id $ALB_SG \
  --protocol tcp \
  --port 443 \
  --cidr 0.0.0.0/0

# Create security group for EKS
EKS_SG=$(aws ec2 create-security-group \
  --group-name yawl-eks-sg \
  --description "YAWL EKS Security Group" \
  --vpc-id vpc-xxx \
  --query 'GroupId' --output text)

# Allow traffic from ALB
aws ec2 authorize-security-group-ingress \
  --group-id $EKS_SG \
  --protocol tcp \
  --port 8080 \
  --source-group $ALB_SG
```

### 10.2 WAF Integration

```bash
# Create WAF Web ACL
aws wafv2 create-web-acl \
  --name yawl-web-acl \
  --scope REGIONAL \
  --default-action Allow={} \
  --rules file://waf-rules.json \
  --visibility-config SampledRequestsEnabled=true,CloudWatchMetricsEnabled=true,MetricName=YAWLWebACL
```

---

## 11. Cost Optimization

### 11.1 Reserved Instances

| Commitment | Discount |
|------------|----------|
| 1 Year No Upfront | ~20% |
| 1 Year All Upfront | ~30% |
| 3 Years All Upfront | ~50% |

### 11.2 Savings Plans

```bash
# View savings plans recommendations
aws ce get-savings-plans-purchase-recommendation \
  --lookback-period-in-days 30 \
  --term-in-years ONE_YEAR \
  --payment-option ALL_UPFRONT \
  --savings-plans-type COMPUTE
```

### 11.3 Cost Monitoring

```bash
# Create cost budget
aws budgets create-budget \
  --account-id ACCOUNT_ID \
  --budget file://budget.json
```

---

## 12. Next Steps

1. [Deploy on AWS](deployment-guide.md)
2. [Configure Security](../security/security-overview.md)
3. [Set up Operations](../operations/scaling-guide.md)
4. [Getting Started](../user-guide/getting-started.md)
