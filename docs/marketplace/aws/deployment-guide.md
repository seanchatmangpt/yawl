# AWS Deployment Guide

**Version:** 5.2
**Last Updated:** 2026-02-14

---

## 1. Overview

This guide provides step-by-step instructions for deploying YAWL Workflow Engine on Amazon Web Services (AWS) using AWS Marketplace, EKS, CloudFormation, and Terraform.

---

## 2. Prerequisites

### 2.1 Required Tools

```bash
# Verify AWS CLI
aws --version

# Verify kubectl
kubectl version --client

# Verify Helm
helm version

# Verify eksctl (for EKS)
eksctl version

# Verify Terraform (optional)
terraform version
```

### 2.2 AWS Account Setup

```bash
# Configure AWS CLI
aws configure

# Verify credentials
aws sts get-caller-identity

# Set default region
export AWS_REGION=us-east-1
```

### 2.3 IAM Permissions

Ensure your IAM user/role has these policies:
- `AmazonEKSClusterPolicy`
- `AmazonEKSServicePolicy`
- `AmazonRDSFullAccess`
- `AmazonElastiCacheFullAccess`
- `AWSCloudFormationFullAccess`
- `IAMFullAccess` (or specific permissions)

---

## 3. Quick Start: AWS Marketplace

### 3.1 Subscribe to YAWL

1. Navigate to [AWS Marketplace](https://aws.amazon.com/marketplace)
2. Search for "YAWL Workflow Engine"
3. Click "Continue to Subscribe"
4. Accept terms and conditions
5. Click "Continue to Configuration"

### 3.2 Configure Deployment

| Parameter | Value | Description |
|-----------|-------|-------------|
| Region | us-east-1 | Target AWS region |
| VPC | vpc-xxx | Existing VPC ID |
| Subnets | subnet-xxx,subnet-yyy | Private subnets |
| Instance Type | m6i.xlarge | EC2 instance type |
| DB Instance | db.r6g.xlarge | RDS instance class |
| Redis Nodes | cache.r6g.xlarge | ElastiCache node type |

### 3.3 Launch

1. Review configuration
2. Click "Launch"
3. Wait for CloudFormation stack to complete (15-30 minutes)
4. Access YAWL via the stack outputs

---

## 4. Manual Deployment: EKS

### 4.1 Create VPC

```bash
# Create VPC using CloudFormation
aws cloudformation create-stack \
  --stack-name yawl-vpc \
  --template-url https://s3.amazonaws.com/aws-quickstart/templates/vpc/vpc.yaml \
  --parameters ParameterKey=AvailabilityZones,ParameterValue=us-east-1a\\,us-east-1b\\,us-east-1c \
               ParameterKey=VPCCIDR,ParameterValue=10.0.0.0/16 \
               ParameterKey=PrivateSubnet1CIDR,ParameterValue=10.0.1.0/24 \
               ParameterKey=PrivateSubnet2CIDR,ParameterValue=10.0.2.0/24 \
               ParameterKey=PrivateSubnet3CIDR,ParameterValue=10.0.3.0/24 \
  --capabilities CAPABILITY_IAM

# Get VPC ID
VPC_ID=$(aws cloudformation describe-stacks \
  --stack-name yawl-vpc \
  --query 'Stacks[0].Outputs[?OutputKey==`VpcId`].OutputValue' \
  --output text)

# Get subnet IDs
PRIVATE_SUBNETS=$(aws cloudformation describe-stacks \
  --stack-name yawl-vpc \
  --query 'Stacks[0].Outputs[?contains(OutputKey, `PrivateSubnet`)].OutputValue' \
  --output text | tr '\n' ',' | sed 's/,$//')
```

### 4.2 Create EKS Cluster

```bash
# Create cluster config
cat > eks-cluster.yaml <<EOF
apiVersion: eksctl.io/v1alpha5
kind: ClusterConfig

metadata:
  name: yawl-cluster
  region: us-east-1
  version: "1.29"

vpc:
  id: $VPC_ID
  subnets:
    private:
      us-east-1a: { id: $(echo $PRIVATE_SUBNETS | cut -d, -f1) }
      us-east-1b: { id: $(echo $PRIVATE_SUBNETS | cut -d, -f2) }
      us-east-1c: { id: $(echo $PRIVATE_SUBNETS | cut -d, -f3) }

managedNodeGroups:
  - name: yawl-workers
    instanceType: m6i.xlarge
    minSize: 3
    maxSize: 10
    desiredCapacity: 3
    privateNetworking: true
    volumeSize: 100
    volumeType: gp3
    iam:
      withAddonPolicies:
        secretsManager: true
        cloudWatch: true
        ebs: true
        alb: true

iam:
  withOIDC: true

cloudWatch:
  clusterLogging:
    enableTypes: ["api", "audit", "authenticator"]
EOF

# Create cluster
eksctl create cluster -f eks-cluster.yaml

# Update kubeconfig
aws eks update-kubeconfig \
  --name yawl-cluster \
  --region us-east-1
```

### 4.3 Create RDS PostgreSQL

```bash
# Create DB subnet group
aws rds create-db-subnet-group \
  --db-subnet-group-name yawl-db-subnet \
  --db-subnet-group-description "YAWL Database" \
  --subnet-ids $(echo $PRIVATE_SUBNETS | tr ',' ' ')

# Create security group for RDS
RDS_SG=$(aws ec2 create-security-group \
  --group-name yawl-rds-sg \
  --description "YAWL RDS Security Group" \
  --vpc-id $VPC_ID \
  --query 'GroupId' --output text)

# Get EKS security group
EKS_SG=$(aws eks describe-cluster \
  --name yawl-cluster \
  --query 'cluster.resourcesVpcConfig.clusterSecurityGroupId' \
  --output text)

# Allow EKS to access RDS
aws ec2 authorize-security-group-ingress \
  --group-id $RDS_SG \
  --protocol tcp \
  --port 5432 \
  --source-group $EKS_SG

# Create RDS instance
aws rds create-db-instance \
  --db-instance-identifier yawl-postgres \
  --db-instance-class db.r6g.xlarge \
  --engine postgres \
  --engine-version 15.4 \
  --master-username yawl_admin \
  --master-user-password $(openssl rand -base64 24) \
  --allocated-storage 500 \
  --storage-type gp3 \
  --storage-encrypted \
  --db-subnet-group-name yawl-db-subnet \
  --vpc-security-group-ids $RDS_SG \
  --multi-az \
  --backup-retention-period 35 \
  --deletion-protection \
  --tags Key=Application,Value=YAWL

# Wait for RDS to be available
aws rds wait db-instance-available \
  --db-instance-identifier yawl-postgres

# Get RDS endpoint
RDS_ENDPOINT=$(aws rds describe-db-instances \
  --db-instance-identifier yawl-postgres \
  --query 'DBInstances[0].Endpoint.Address' \
  --output text)

# Create database
PGPASSWORD=$(aws rds describe-db-instances \
  --db-instance-identifier yawl-postgres \
  --query 'DBInstances[0].MasterUserPassword' \
  --output text) psql \
  -h $RDS_ENDPOINT \
  -U yawl_admin \
  -d postgres \
  -c "CREATE DATABASE yawl;"
```

### 4.4 Create ElastiCache Redis

```bash
# Create subnet group
aws elasticache create-cache-subnet-group \
  --cache-subnet-group-name yawl-redis-subnet \
  --cache-subnet-group-description "YAWL Redis" \
  --subnet-ids $(echo $PRIVATE_SUBNETS | tr ',' ' ')

# Create security group for Redis
REDIS_SG=$(aws ec2 create-security-group \
  --group-name yawl-redis-sg \
  --description "YAWL Redis Security Group" \
  --vpc-id $VPC_ID \
  --query 'GroupId' --output text)

# Allow EKS to access Redis
aws ec2 authorize-security-group-ingress \
  --group-id $REDIS_SG \
  --protocol tcp \
  --port 6379 \
  --source-group $EKS_SG

# Create Redis replication group
aws elasticache create-replication-group \
  --replication-group-id yawl-redis \
  --replication-group-description "YAWL Redis Cluster" \
  --cache-node-type cache.r6g.xlarge \
  --engine redis \
  --engine-version 7.0 \
  --num-cache-clusters 3 \
  --cache-subnet-group-name yawl-redis-subnet \
  --security-group-ids $REDIS_SG \
  --at-rest-encryption-enabled \
  --transit-encryption-enabled \
  --auth-token $(openssl rand -base64 32) \
  --automatic-failover-enabled \
  --multi-az-enabled

# Wait for Redis to be available
aws elasticache wait replication-group-available \
  --replication-group-id yawl-redis

# Get Redis endpoint
REDIS_ENDPOINT=$(aws elasticache describe-replication-groups \
  --replication-group-id yawl-redis \
  --query 'ReplicationGroups[0].PrimaryEndpoint.Address' \
  --output text)
```

### 4.5 Store Secrets

```bash
# Store DB credentials
aws secretsmanager create-secret \
  --name yawl/db-credentials \
  --secret-string "{\"username\":\"yawl_admin\",\"password\":\"$(aws rds describe-db-instances --db-instance-identifier yawl-postgres --query 'DBInstances[0].MasterUserPassword' --output text)\",\"host\":\"$RDS_ENDPOINT\",\"port\":5432,\"database\":\"yawl\"}"

# Store Redis auth
aws secretsmanager create-secret \
  --name yawl/redis-auth \
  --secret-string "{\"host\":\"$REDIS_ENDPOINT\",\"port\":6379,\"auth_token\":\"$(aws elasticache describe-replication-groups --replication-group-id yawl-redis --query 'ReplicationGroups[0].AuthToken' --output text)\"}"
```

### 4.6 Install AWS Load Balancer Controller

```bash
# Create IAM policy
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.5.0/docs/install/iam_policy.json

aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json

# Create service account
eksctl create iamserviceaccount \
  --cluster=yawl-cluster \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --attach-policy-arn=arn:aws:iam::$(aws sts get-caller-identity --query Account --output text):policy/AWSLoadBalancerControllerIAMPolicy \
  --approve

# Install controller
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=yawl-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### 4.7 Deploy YAWL

```bash
# Create namespace
kubectl create namespace yawl

# Create service account with IAM role
eksctl create iamserviceaccount \
  --cluster=yawl-cluster \
  --namespace=yawl \
  --name=yawl-engine \
  --attach-policy-arn=arn:aws:iam::aws:policy/SecretsManagerReadWrite \
  --approve \
  --override-existing-serviceaccounts

# Add YAWL Helm repository
helm repo add yawl https://helm.yawl.io
helm repo update

# Create values file
cat > values-aws.yaml <<EOF
global:
  environment: production
  imageRegistry: 123456789012.dkr.ecr.us-east-1.amazonaws.com/yawl
  imageTag: "5.2.0"

engine:
  replicaCount: 2
  serviceAccount:
    create: false
    name: yawl-engine
  extraEnv:
    - name: YAWL_DB_HOST
      valueFrom:
        secretKeyRef:
          name: yawl-db-credentials
          key: host
    - name: AWS_REGION
      value: us-east-1

resourceService:
  replicaCount: 2

workletService:
  replicaCount: 1

ingress:
  enabled: true
  className: alb
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/ssl-redirect: "443"
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:ACCOUNT_ID:certificate/CERT_ID
  hosts:
    - host: yawl.yourdomain.com
      paths:
        - path: /
          pathType: Prefix

externalDatabase:
  existingSecret: yawl-db-credentials

externalRedis:
  existingSecret: yawl-redis-auth
EOF

# Deploy
helm upgrade --install yawl yawl/yawl-stack \
  --namespace yawl \
  --values values-aws.yaml \
  --timeout 15m
```

---

## 5. Terraform Deployment

### 5.1 Configure Variables

```hcl
# terraform.tfvars
region = "us-east-1"

# VPC
vpc_cidr           = "10.0.0.0/16"
availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]

# EKS
eks_cluster_name    = "yawl-cluster"
eks_cluster_version = "1.29"
eks_node_type       = "m6i.xlarge"
eks_node_count      = 3
eks_node_min        = 3
eks_node_max        = 10

# RDS
rds_instance_class = "db.r6g.xlarge"
rds_storage        = 500
rds_username       = "yawl_admin"
rds_password       = "SecurePassword123!"
rds_multi_az       = true

# ElastiCache
redis_node_type = "cache.r6g.xlarge"
redis_nodes     = 3

# YAWL
yawl_version    = "5.2.0"
yawl_domain     = "yawl.yourdomain.com"
```

### 5.2 Deploy

```bash
cd terraform/aws
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

---

## 6. Post-Deployment

### 6.1 Configure DNS

```bash
# Get ALB DNS name
ALB_DNS=$(kubectl get ingress -n yawl -o jsonpath='{.items[0].status.loadBalancer.ingress[0].hostname}')

# Create Route 53 record
aws route53 change-resource-record-sets \
  --hosted-zone-id Z1234567890ABC \
  --change-batch "{
    \"Changes\": [{
      \"Action\": \"CREATE\",
      \"ResourceRecordSet\": {
        \"Name\": \"yawl.yourdomain.com\",
        \"Type\": \"CNAME\",
        \"TTL\": 300,
        \"ResourceRecords\": [{\"Value\": \"$ALB_DNS\"}]
      }
    }]
  }"
```

### 6.2 Verify Deployment

```bash
# Check pods
kubectl get pods -n yawl

# Check services
kubectl get svc -n yawl

# Check ingress
kubectl get ingress -n yawl

# Test endpoint
curl -k https://yawl.yourdomain.com/ib/api/health
```

---

## 7. Troubleshooting

### 7.1 Common Issues

| Issue | Symptoms | Resolution |
|-------|----------|------------|
| EKS node not ready | Nodes NotReady | Check VPC CNI plugin |
| RDS connection failed | Connection timeout | Check security groups |
| Redis auth failed | NOAUTH error | Verify auth token |
| ALB not created | Ingress pending | Check LB controller logs |

### 7.2 Diagnostic Commands

```bash
# EKS cluster info
eksctl get cluster --name yawl-cluster

# RDS status
aws rds describe-db-instances --db-instance-identifier yawl-postgres

# Redis status
aws elasticache describe-replication-groups --replication-group-id yawl-redis

# Pod logs
kubectl logs -f -l app.kubernetes.io/name=yawl-engine -n yawl
```

---

## 8. Cleanup

```bash
# Delete Helm release
helm uninstall yawl -n yawl

# Delete EKS cluster
eksctl delete cluster --name yawl-cluster

# Delete RDS
aws rds delete-db-instance \
  --db-instance-identifier yawl-postgres \
  --skip-final-snapshot

# Delete Redis
aws elasticache delete-replication-group \
  --replication-group-id yawl-redis

# Delete secrets
aws secretsmanager delete-secret \
  --secret-id yawl/db-credentials \
  --force-delete-without-recovery

# Delete VPC (if CloudFormation)
aws cloudformation delete-stack --stack-name yawl-vpc
```
